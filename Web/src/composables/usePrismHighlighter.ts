/**
 * Prism.js 代码高亮管理
 * 从 PostDetail.vue 中拆分，负责动态加载 Prism.js 和代码块复制按钮
 */
export function usePrismHighlighter() {
  let prismLinkEl: HTMLLinkElement | null = null
  let prismScriptEl: HTMLScriptElement | null = null
  const prismLangScripts: HTMLScriptElement[] = []

  const addCopyButtons = () => {
    document.querySelectorAll('.markdown-content pre').forEach(pre => {
      if (pre.querySelector('.copy-btn')) return
      const btn = document.createElement('button')
      btn.className = 'copy-btn'
      btn.textContent = '复制'
      btn.addEventListener('click', () => {
        const code = pre.querySelector('code')
        const text = code?.textContent || ''
        const textarea = document.createElement('textarea')
        textarea.value = text
        textarea.style.cssText = 'position:fixed;left:-9999px;opacity:0'
        document.body.appendChild(textarea)
        textarea.select()
        document.execCommand('copy')
        document.body.removeChild(textarea)
        btn.textContent = '已复制'
        setTimeout(() => { btn.textContent = '复制' }, 1500)
      })
      ;(pre as HTMLElement).style.position = 'relative'
      pre.appendChild(btn)
    })
  }

  /** 若 Prism 已加载则高亮全部代码块并加复制按钮 */
  const highlightAll = () => {
    const prism = (window as any).Prism
    if (prism) {
      prism.highlightAll()
      addCopyButtons()
    }
  }

  const loadPrism = () => {
    if (prismScriptEl || (window as any).Prism) {
      highlightAll()
      return
    }

    const link = document.createElement('link')
    prismLinkEl = link
    link.rel = 'stylesheet'
    link.href = 'https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism.min.css'
    document.head.appendChild(link)

    const script = document.createElement('script')
    prismScriptEl = script
    script.src = 'https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/prism.min.js'
    script.onload = () => {
      setTimeout(() => {
        const codeBlocks = document.querySelectorAll('pre code[class*="language-"]')
        const languages = new Set<string>()

        codeBlocks.forEach(block => {
          const match = block.className.match(/language-(\w+)/)
          if (match) {
            languages.add(match[1])
          }
        })

        let loadedCount = 0
        const totalLanguages = languages.size

        if (totalLanguages === 0) {
          highlightAll()
          return
        }

        languages.forEach(lang => {
          const langScript = document.createElement('script')
          langScript.src = `https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-${lang}.min.js`
          langScript.onload = () => {
            loadedCount++
            if (loadedCount === totalLanguages) {
              highlightAll()
            }
          }
          document.head.appendChild(langScript)
          prismLangScripts.push(langScript)
        })
      }, 100)
    }
    document.head.appendChild(script)
  }

  const cleanup = () => {
    if (prismLinkEl?.parentNode) {
      prismLinkEl.parentNode.removeChild(prismLinkEl)
      prismLinkEl = null
    }
    if (prismScriptEl?.parentNode) {
      prismScriptEl.parentNode.removeChild(prismScriptEl)
      prismScriptEl = null
    }
    prismLangScripts.forEach(el => {
      if (el.parentNode) el.parentNode.removeChild(el)
    })
    prismLangScripts.length = 0
  }

  return { loadPrism, highlightAll, cleanup }
}
