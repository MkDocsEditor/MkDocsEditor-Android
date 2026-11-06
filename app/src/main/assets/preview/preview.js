/*
 * The app's side of the markdown preview: renders with the renderer of MkDocsEditor-Web
 * (mkdocs-renderer.js, see `make updatePreview`), hands links to the app and marks missing images.
 * It also tells the app where the page is scrolled to, and scrolls to the source line of the editor.
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

  /** the page offset of a source line with a fraction, between the tops of the blocks around it */
  function offsetOfLine(line) {
    var list = sourceBlocks()
    var next = firstIndex(list, function (block) { return lineOf(block) > line })
    var start = next - 1
    // the first block of a line: a block and the first block in it start at the same line and place
    while (start > 0 && lineOf(list[start - 1]) === lineOf(list[start])) start--
    var fromLine = start >= 0 ? lineOf(list[start]) : 0
    var fromTop = start >= 0 ? list[start].getBoundingClientRect().top + window.scrollY : 0
    var toLine = next < list.length ? lineOf(list[next]) : lineCount
    var toTop = next < list.length
      ? list[next].getBoundingClientRect().top + window.scrollY
      : document.documentElement.scrollHeight
    if (toLine <= fromLine) return fromTop
    return fromTop + ((line - fromLine) / (toLine - fromLine)) * (toTop - fromTop)
  }

  // the line the app wants at the top, kept for a new render; null once the user scrolls the page
  var syncLine = null
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
    // a little of the way each frame: the editor reports one row at a time, this keeps it from stepping
    window.scrollTo(0, current + distance * 0.35)
    window.requestAnimationFrame(step)
  }

  function scrollToSyncLine(smooth) {
    if (syncLine === null) return
    var top = Math.min(Math.max(0, offsetOfLine(syncLine)), maxScroll())
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
      atTop: scrollY <= 0,
      atBottom: scrollY > 0 && scrollY >= maxScroll() - 1,
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
      syncLine = null
      target = null
    }
    scheduleReport()
  }, { passive: true })
  window.addEventListener('resize', function () {
    fitTables()
    scrollToSyncLine(false)
    scheduleReport()
  })

  window.MkDocsPreview = {
    render: function (source) {
      var content = document.getElementById('content')
      try {
        content.innerHTML = MkDocsRenderer.renderMarkdown(source)
      } catch (e) {
        content.textContent = String(e)
      }
      fitTables()
      blocks = null
      lineCount = source.split('\n').length
      scrollToSyncLine(false)
      scheduleReport()
    },
    /**
     * Scrolls the source line to the top of the view. A line between the starts of two blocks is between their tops.
     *
     * @param line 0-based, with a fraction for a part of the way to the next line
     * @param smooth moves there over a few frames
     */
    scrollToLine: function (line, smooth) {
      syncLine = line
      scrollToSyncLine(!!smooth)
    },
  }

  var content = document.getElementById('content')
  if (window.ResizeObserver && content) {
    new ResizeObserver(function () {
      // images that load later move the blocks
      scrollToSyncLine(false)
      scheduleReport()
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
