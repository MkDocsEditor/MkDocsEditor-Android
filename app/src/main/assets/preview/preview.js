/*
 * The app's side of the markdown preview: renders with the renderer of MkDocsEditor-Web
 * (mkdocs-renderer.js, see `make updatePreview`), hands links to the app and marks missing images.
 */
(function () {
  'use strict'

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
        content.innerHTML = MkDocsRenderer.renderMarkdown(source)
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
  }
})()
