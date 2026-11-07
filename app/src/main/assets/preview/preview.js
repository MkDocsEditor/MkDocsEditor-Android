/*
 * The app's side of the markdown preview: renders with the renderer of MkDocsEditor-Web
 * (mkdocs-renderer.js, see `make updatePreview`), hands links to the app and marks missing images.
 * It also tells the app where the page is scrolled to and where its blocks are, and puts the source line of the
 * editor at a height of the view.
 */
(function () {
  'use strict'

  var bridge = window.MkDocsPreviewBridge
  // grows with its content in a scrolling column of the app, and never scrolls itself (preview.css)
  var embedded = document.documentElement.classList.contains('embedded')

  /** an embedded page can't scroll sideways, so a table that is too wide with its words wrapped breaks them too */
  function fitTables() {
    if (!embedded) return
    var content = document.getElementById('content')
    var tables = content.querySelectorAll('table')
    for (var i = 0; i < tables.length; i++) {
      var table = tables[i]
      table.classList.remove('preview-table-squeezed')
      if (table.getBoundingClientRect().width > content.getBoundingClientRect().width + 0.5) {
        table.classList.add('preview-table-squeezed')
      }
    }
  }

  document.addEventListener('click', function (event) {
    var anchor = event.target.closest && event.target.closest('a')
    var href = anchor && anchor.getAttribute('href')
    if (!href || event.defaultPrevented) return
    if (href.charAt(0) === '#') return
    event.preventDefault()
    if (bridge) bridge.openLink(href)
  })

  window.addEventListener(
    'error',
    function (event) {
      var img = event.target
      if (img && img.tagName === 'IMG') img.classList.add('preview-image-missing')
    },
    true
  )

  // the blocks with a source line, in document order, so their lines and their tops only grow
  var blocks = null
  var lineCount = 1

  function sourceBlocks() {
    if (!blocks) blocks = Array.prototype.slice.call(document.querySelectorAll('[data-source-line]'))
    return blocks
  }

  function lineOf(block) {
    return Number(block.getAttribute('data-source-line'))
  }

  /** the index of the first block for which the test holds, the test holds for all behind it */
  function firstIndex(list, test) {
    var low = 0
    var high = list.length
    while (low < high) {
      var middle = (low + high) >> 1
      if (test(list[middle])) high = middle
      else low = middle + 1
    }
    return low
  }

  function maxScroll() {
    return Math.max(0, document.documentElement.scrollHeight - window.innerHeight)
  }

  // the blocks as [line, top from the start of the page], until the content or the size changes
  var layout = null

  function layoutBlocks() {
    if (!layout) {
      var scrollY = window.scrollY
      layout = sourceBlocks().map(function (block) {
        return [lineOf(block), block.getBoundingClientRect().top + scrollY]
      })
    }
    return layout
  }

  /**
   * The page offset of a source line with a fraction, piecewise-linear: each block runs from its line to the line of
   * the next one, and from its top to the top of the next one. Line 0 is at the start of the page, the end of the text
   * at its end. A nested block that starts at the line of the one before, or above it, adds nothing. The app maps the
   * other way with the same points (SourceHeightMap), and the web client does the same.
   */
  function offsetOfLine(line) {
    var end = document.documentElement.scrollHeight
    var fromLine = 0
    var fromTop = 0
    var blocks = layoutBlocks()
    for (var i = 0; i < blocks.length; i++) {
      var blockLine = blocks[i][0]
      var blockTop = blocks[i][1]
      if (blockLine <= fromLine || blockLine >= lineCount || blockTop < fromTop) continue
      if (blockLine > line) return between(line, fromLine, fromTop, blockLine, blockTop)
      fromLine = blockLine
      fromTop = blockTop
    }
    return between(line, fromLine, fromTop, Math.max(lineCount, fromLine + 1), Math.max(end, fromTop))
  }

  function between(line, fromLine, fromTop, toLine, toTop) {
    var part = Math.min(Math.max((line - fromLine) / (toLine - fromLine), 0), 1)
    return fromTop + part * (toTop - fromTop)
  }

  var reportedLayout = null

  function reportLayout() {
    if (!bridge || !bridge.layout) return
    var report = JSON.stringify({ blocks: layoutBlocks(), scrollHeight: document.documentElement.scrollHeight })
    if (report === reportedLayout) return
    reportedLayout = report
    bridge.layout(report)
  }

  // the source line the app wants at a height of the view, kept for a new render; null once the user scrolls the page
  var syncAnchor = null
  var target = null
  var animating = false

  function step() {
    if (target === null) {
      animating = false
      return
    }
    var current = window.scrollY
    var distance = target - current
    if (Math.abs(distance) < 1) {
      window.scrollTo(0, target)
      target = null
      animating = false
      return
    }
    // a little of the way each frame: the caret moves a line at a time, this keeps the page from stepping
    window.scrollTo(0, current + distance * 0.35)
    window.requestAnimationFrame(step)
  }

  function scrollToSyncAnchor(smooth) {
    if (syncAnchor === null) return
    var height = offsetOfLine(syncAnchor.line) - syncAnchor.viewportFraction * window.innerHeight
    var top = Math.min(Math.max(0, height), maxScroll())
    if (!smooth) {
      target = null
      window.scrollTo(0, top)
      return
    }
    target = top
    if (!animating) {
      animating = true
      window.requestAnimationFrame(step)
    }
  }

  // a finger or a wheel moves the page, until it rests: a fling goes on after the finger is up
  var touching = false
  var userScrolling = false
  var restTimer = null

  function userMoves() {
    userScrolling = true
    if (restTimer) window.clearTimeout(restTimer)
    restTimer = window.setTimeout(function () {
      restTimer = null
      if (!touching) userScrolling = false
    }, 250)
  }

  window.addEventListener('touchstart', function () {
    touching = true
    target = null
    userMoves()
  }, { passive: true })
  window.addEventListener('touchend', function () {
    touching = false
    userMoves()
  }, { passive: true })
  window.addEventListener('touchcancel', function () {
    touching = false
    userMoves()
  }, { passive: true })
  window.addEventListener('wheel', userMoves, { passive: true })

  var reportPending = false

  function report() {
    reportPending = false
    if (!bridge || !bridge.scrolled) return
    var list = sourceBlocks()
    var height = window.innerHeight
    // the last block that starts above the view, all that start in it, and the first one below
    var start = Math.max(0, firstIndex(list, function (block) { return block.getBoundingClientRect().top > 0 }) - 1)
    var visible = []
    for (var i = start; i < list.length; i++) {
      var rect = list[i].getBoundingClientRect()
      visible.push([lineOf(list[i]), rect.top, rect.bottom])
      if (rect.top >= height) break
    }
    var scrollY = window.scrollY
    bridge.scrolled(JSON.stringify({
      blocks: visible,
      height: height,
      scrollY: scrollY,
      scrollHeight: document.documentElement.scrollHeight,
      byUser: userScrolling,
    }))
  }

  function scheduleReport() {
    if (reportPending) return
    reportPending = true
    window.requestAnimationFrame(report)
  }

  window.addEventListener('scroll', function () {
    if (userScrolling) {
      userMoves()
      // the user takes over, the editor no longer says where to be
      syncAnchor = null
      target = null
    }
    scheduleReport()
  }, { passive: true })
  /** the blocks moved: keeps the source line of the app in place and tells the app */
  function layoutChanged() {
    layout = null
    // an embedded page never scrolls, so there is nothing to keep in place or to report
    if (embedded) return
    scrollToSyncAnchor(false)
    reportLayout()
    scheduleReport()
  }

  window.addEventListener('resize', function () {
    fitTables()
    layoutChanged()
  })

  var reportedMeta = null

  /** tells the app the front matter of the page, only when it changed */
  function reportMeta(meta) {
    if (!bridge || !bridge.pageMeta) return
    var json = JSON.stringify(meta || {})
    if (json === reportedMeta) return
    reportedMeta = json
    bridge.pageMeta(json)
  }

  window.MkDocsPreview = {
    render: function (source) {
      var content = document.getElementById('content')
      try {
        var page = MkDocsRenderer.renderPage(source)
        content.innerHTML = page.html
        reportMeta(page.meta)
      } catch (e) {
        content.textContent = String(e)
      }
      fitTables()
      blocks = null
      lineCount = source.split('\n').length
      layoutChanged()
    },
    /**
     * Scrolls a source line to a height of the view, as far as the page can scroll, and keeps it there when the page
     * renders again or changes its size, until the user scrolls.
     *
     * @param line 0-based, with a fraction for a part of the way to the next line, the line count for the end
     * @param viewportFraction the height of the view, 0 for its top, 1 for its bottom
     * @param smooth moves there over a few frames
     */
    scrollToLine: function (line, viewportFraction, smooth) {
      if (embedded) return
      syncAnchor = { line: line, viewportFraction: viewportFraction }
      scrollToSyncAnchor(!!smooth)
    },
    /** Reads only the front matter, for a page that nobody sees while the text changes. */
    readMeta: function (source) {
      try {
        reportMeta(MkDocsRenderer.parseFrontMatter(source).meta)
      } catch (e) {
        // parseFrontMatter does not throw, and a page without the renderer has no meta to tell
      }
    },
    /** Lets go of the source line of the app, so that the page stays where it is when it renders again. */
    stopSync: function () {
      syncAnchor = null
      target = null
    },
  }

  var content = document.getElementById('content')
  if (window.ResizeObserver && content) {
    new ResizeObserver(function () {
      // images that load later move the blocks
      layoutChanged()
      // a preview without its own scrolling grows with its content, so the app needs the height
      if (bridge && bridge.contentHeight) {
        // the whole body with its padding, unrounded: the app rounds up, so not a pixel is left to scroll
        var style = window.getComputedStyle(document.body)
        var height = document.body.getBoundingClientRect().bottom + window.scrollY + parseFloat(style.marginBottom || '0')
        bridge.contentHeight(height)
      }
    }).observe(content)
  }

  // the last script of the page: from here on the app can render
  if (bridge) bridge.pageReady()
})()
