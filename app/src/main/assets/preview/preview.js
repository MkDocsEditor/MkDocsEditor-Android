/*
 * Markdown preview of the editor. Renders MkDocs flavoured markdown the same way as the web
 * client (src/markdown/render.ts and admonition.ts of MkDocsEditor-Web): markdown-it with
 * admonitions, footnotes, task lists, definition lists, marks and attributes, highlight.js for
 * code, and DOMPurify. Links are handed to the app, images load through the page's base URL.
 */
(function () {
  'use strict'

  function escapeHtml(text) {
    return text.replace(/[&<>"']/g, function (c) {
      return '&#' + c.charCodeAt(0) + ';'
    })
  }

  /** MkDocs admonitions (`!!! note "Title"`) and pymdownx.details (`??? note`, `???+ note`). */
  function admonitionPlugin(md) {
    var MARKER = /^(!!!|\?\?\?\+?)\s+([\w-]+)((?:\s+[\w-]+)*)(?:\s+"(.*)")?\s*$/

    function rule(state, startLine, endLine, silent) {
      var start = state.bMarks[startLine] + state.tShift[startLine]
      var max = state.eMarks[startLine]
      if (state.sCount[startLine] - state.blkIndent >= 4) return false
      var match = MARKER.exec(state.src.slice(start, max))
      if (!match) return false
      if (silent) return true

      var marker = match[1]
      var kind = match[2]
      var extraClasses = match[3] || ''
      var title = match[4]
      var collapsible = marker !== '!!!'
      var open = marker === '???+'

      var nextLine = startLine + 1
      var bodyIndent = state.sCount[startLine] + 4
      while (nextLine < endLine) {
        if (!state.isEmpty(nextLine) && state.sCount[nextLine] < bodyIndent) break
        nextLine++
      }
      while (nextLine > startLine + 1 && state.isEmpty(nextLine - 1)) nextLine--

      var classes = ['admonition', kind].concat(extraClasses.split(/\s+/).filter(Boolean))
      var heading = title !== undefined ? title : kind.charAt(0).toUpperCase() + kind.slice(1)
      var tag = collapsible ? 'details' : 'div'

      var openToken = state.push('admonition_open', tag, 1)
      openToken.block = true
      openToken.attrSet('class', classes.join(' '))
      if (open) openToken.attrSet('open', '')
      openToken.map = [startLine, nextLine]

      if (heading !== '') {
        var titleOpen = state.push('admonition_title_open', collapsible ? 'summary' : 'p', 1)
        titleOpen.attrSet('class', 'admonition-title')
        var inline = state.push('inline', '', 0)
        inline.content = heading
        inline.map = [startLine, startLine + 1]
        inline.children = []
        state.push('admonition_title_close', collapsible ? 'summary' : 'p', -1)
      }

      var oldParent = state.parentType
      var oldLineMax = state.lineMax
      var oldBlkIndent = state.blkIndent
      state.parentType = 'blockquote'
      state.lineMax = nextLine
      state.blkIndent = bodyIndent
      state.md.block.tokenize(state, startLine + 1, nextLine)
      state.blkIndent = oldBlkIndent
      state.lineMax = oldLineMax
      state.parentType = oldParent

      state.push('admonition_close', tag, -1).block = true
      state.line = nextLine
      return true
    }

    md.block.ruler.before('fence', 'admonition', rule, {
      alt: ['paragraph', 'reference', 'blockquote', 'list'],
    })
  }

  function createRenderer() {
    var md = window.markdownit({
      html: true,
      linkify: true,
      typographer: false,
      highlight: function (code, lang) {
        var language = (lang || '').split(/\s+/)[0] || ''
        if (language && window.hljs && hljs.getLanguage(language)) {
          var html = hljs.highlight(code, { language: language, ignoreIllegals: true }).value
          return '<pre class="hljs"><code class="language-' + escapeHtml(language) + '">' + html + '</code></pre>'
        }
        return '<pre class="hljs"><code>' + escapeHtml(code) + '</code></pre>'
      },
    })
    md.use(admonitionPlugin)
    md.use(window.markdownitFootnote)
    md.use(window.markdownitTaskLists, { enabled: false, label: true })
    md.use(window.markdownitDeflist)
    md.use(window.markdownitMark)
    md.use(window.markdownItAttrs, { allowedAttributes: ['id', 'class', 'width', 'height', 'align'] })

    // remember the source line of every block, so the preview can follow the editor
    ;['paragraph_open', 'heading_open', 'list_item_open', 'table_open', 'blockquote_open', 'admonition_open'].forEach(
      function (name) {
        var original = md.renderer.rules[name]
        md.renderer.rules[name] = function (tokens, idx, options, env, self) {
          var token = tokens[idx]
          if (token.map) token.attrSet('data-source-line', String(token.map[0]))
          return original ? original(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options)
        }
      }
    )
    return md
  }

  /** Blanks out a leading YAML front matter block; the lines stay, so line numbers match. */
  function stripFrontMatter(source) {
    var match = /^---\r?\n[\s\S]*?\r?\n(---|\.\.\.)\r?\n/.exec(source)
    if (!match) return source
    return new Array(match[0].split('\n').length).join('\n') + source.slice(match[0].length)
  }

  var renderer = null

  function renderMarkdown(source) {
    if (!renderer) renderer = createRenderer()
    var html = renderer.render(stripFrontMatter(source))
    return DOMPurify.sanitize(html, {
      ADD_ATTR: ['data-source-line', 'open', 'target'],
      ADD_TAGS: ['details', 'summary'],
    })
  }

  function isExternal(href) {
    return /^[a-z][a-z0-9+.-]*:/i.test(href) || href.indexOf('//') === 0
  }

  document.addEventListener('click', function (event) {
    var anchor = event.target.closest && event.target.closest('a')
    var href = anchor && anchor.getAttribute('href')
    if (!href || event.defaultPrevented) return
    if (href.charAt(0) === '#') return
    event.preventDefault()
    if (window.MkDocsPreviewBridge) MkDocsPreviewBridge.openLink(href)
  })

  window.addEventListener(
    'error',
    function (event) {
      var img = event.target
      if (img && img.tagName === 'IMG') img.classList.add('preview-image-missing')
    },
    true
  )

  window.MkDocsPreview = {
    render: function (source) {
      var content = document.getElementById('content')
      try {
        content.innerHTML = renderMarkdown(source)
      } catch (e) {
        content.textContent = String(e)
      }
    },
    /** scrolls to the block that contains the given source line */
    scrollToLine: function (line) {
      var target = null
      var blocks = document.querySelectorAll('[data-source-line]')
      for (var i = 0; i < blocks.length; i++) {
        if (Number(blocks[i].getAttribute('data-source-line')) <= line) target = blocks[i]
        else break
      }
      window.scrollTo(0, target ? target.offsetTop - 8 : 0)
    },
    renderMarkdown: renderMarkdown,
    isExternal: isExternal,
  }
})()
