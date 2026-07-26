/**
 * 文章正文富文本渲染统一工具
 *
 * 文章内容由 TinyMCE 编辑器输出 HTML（非 Markdown），这里统一处理
 * "净化 + 代码高亮 + 复制按钮"，供文章详情(PostDetail)与编辑预览(CreatePost)共用，
 * 保证"所见即所得"——预览效果与发布后一致。
 *
 * 边界说明：AI 聊天返回的是 Markdown 文本，走 useMarkdown(marked) 链路，与本工具无关；
 * 公告/评论等非文章正文内容不在此统一范围内。
 */
import DOMPurify from 'dompurify'
import { hljs } from '@/utils/highlightLanguages'

/**
 * DOMPurify 安全配置：黑名单模式。
 * 文章内容由登录作者用 TinyMCE 产出，需保留其内联排版样式(字体/字号/颜色/对齐等)，
 * 因此采用黑名单——仅剥离脚本/表单/事件属性，其余放行。
 */
const sanitizeConfig = {
  FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'form', 'input', 'textarea', 'select', 'button'],
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur', 'onsubmit']
}

/** 净化文章正文 HTML：保留排版样式，剥离危险标签/属性 */
export const sanitizePostHtml = (html: string): string =>
  DOMPurify.sanitize(html || '', sanitizeConfig)

/** 复制按钮已绑定标记，避免重复添加 */
const COPY_BOUND_FLAG = 'liutech-copy-bound'

/**
 * 对容器内所有代码块执行 hljs 高亮，并附加复制按钮（幂等）。
 *
 * 关键点：TinyMCE 的 codesample 插件把 language-xxx 类放在 <pre> 上（不在 <code> 上），
 * 而 hljs 需在 <code> 上识别语言；这里把语言类同步到 <code>，确保按正确语言高亮，
 * 而非退化为自动检测。
 */
export const highlightCodeBlocks = (container: HTMLElement | null | undefined): void => {
  if (!container) return

  // 1. 代码高亮
  container.querySelectorAll('pre').forEach((pre) => {
    const code = pre.querySelector('code')
    if (!code) return
    const codeEl = code as HTMLElement
    if (codeEl.classList.contains('hljs')) return // 已高亮，跳过

    // 同步 <pre> 上的 language-xxx 到 <code>，让 hljs 用对语言
    const langMatch = (pre.className || '').match(/language-([\w-]+)/)
    if (langMatch && !/language-/.test(codeEl.className)) {
      codeEl.classList.add(`language-${langMatch[1]}`)
    }

    try {
      hljs.highlightElement(codeEl)
    } catch {
      // 语言未注册等情况，忽略，保持原文本
    }
  })

  // 2. 复制按钮
  container.querySelectorAll('pre').forEach((pre) => {
    const el = pre as HTMLElement
    if (el.classList.contains(COPY_BOUND_FLAG)) return
    el.classList.add(COPY_BOUND_FLAG)
    el.style.position = 'relative'

    const btn = document.createElement('button')
    btn.className = 'copy-btn'
    btn.type = 'button'
    btn.textContent = '复制'
    btn.addEventListener('click', async () => {
      const code = pre.querySelector('code')
      const text = code?.textContent || ''
      try {
        await navigator.clipboard.writeText(text)
      } catch {
        // 非安全上下文或旧浏览器回退
        const ta = document.createElement('textarea')
        ta.value = text
        ta.style.cssText = 'position:fixed;left:-9999px;opacity:0'
        document.body.appendChild(ta)
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
      }
      btn.textContent = '已复制'
      setTimeout(() => { btn.textContent = '复制' }, 1500)
    })
    el.appendChild(btn)
  })
}
