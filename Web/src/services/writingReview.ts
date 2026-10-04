import { computed, ref, shallowRef, watch } from 'vue'
import DOMPurify from 'dompurify'
import type { WritingFieldUpdatePayload } from './writingStream'

/** 一轮写作只产生待采纳建议。完整成功、草稿未变化时才允许整轮应用。 */
export interface WritingDraftSnapshot {
  postId?: number | null
  title?: string
  summary?: string
  content?: string
  categoryId?: number | string
  tagIds?: number[]
  status?: string
  coverImage?: string
  thumbnail?: string
}

const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value))
const signature = (draft: WritingDraftSnapshot) => JSON.stringify([
  draft.postId ?? null, draft.title ?? '', draft.summary ?? '', draft.content ?? '',
  String(draft.categoryId ?? ''), draft.tagIds ?? [], draft.status ?? '',
  draft.coverImage ?? '', draft.thumbnail ?? ''
])

const TEXT_TAGS = ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'div', 'span', 'section', 'article',
  'br', 'hr', 'strong', 'b', 'em', 'i', 'u', 's', 'del', 'blockquote', 'pre', 'code', 'ul', 'ol', 'li',
  'dl', 'dt', 'dd', 'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'a', 'figure', 'figcaption',
  'details', 'summary', 'mark', 'sup', 'sub']
const TEXT_ATTRIBUTES = ['href', 'title', 'target', 'rel', 'class', 'id', 'colspan', 'rowspan', 'dir', 'lang', 'style']
const MEDIA_ELEMENTS = 'audio,video,iframe,svg,math'
const FORBIDDEN_ELEMENTS = 'script,style,link,base,meta,object,embed,template,noscript'
const RESOURCE_ATTRIBUTES = ['srcset', 'poster', 'background', 'ping']
// 编辑器的排版属性可以保留；不允许 background-image、外部字体定义等加载资源的 CSS。
const SAFE_STYLE_PROPERTIES = new Set([
  'color', 'background-color', 'font-family', 'font-size', 'font-weight', 'font-style', 'font-variant',
  'line-height', 'text-align', 'text-decoration', 'text-decoration-line', 'text-decoration-color',
  'text-indent', 'text-transform', 'vertical-align', 'white-space', 'word-break', 'overflow-wrap',
  'letter-spacing', 'word-spacing', 'width', 'height', 'min-width', 'max-width', 'min-height', 'max-height',
  'margin', 'margin-top', 'margin-right', 'margin-bottom', 'margin-left',
  'padding', 'padding-top', 'padding-right', 'padding-bottom', 'padding-left',
  'border', 'border-width', 'border-style', 'border-color', 'border-radius', 'border-collapse', 'border-spacing',
  'border-top', 'border-right', 'border-bottom', 'border-left', 'table-layout', 'caption-side',
  'list-style-type', 'list-style-position', 'float', 'clear', 'display', 'object-fit'
])

const sanitizeStyle = (element: Element) => {
  const input = document.createElement('span').style
  input.cssText = element.getAttribute('style') || ''
  const output = document.createElement('span').style
  for (const property of Array.from(input)) {
    const value = input.getPropertyValue(property)
    if (SAFE_STYLE_PROPERTIES.has(property) && !/[\\<>]|url|image-set|expression|var\s*\(|@import|(?:https?|data|javascript)\s*:/i.test(value)) {
      output.setProperty(property, value)
    }
  }
  if (output.cssText) element.setAttribute('style', output.cssText)
  else element.removeAttribute('style')
}

/** 原稿媒体仍需去脚本/事件；记录净化后的键，使父编辑器再次净化时保持幂等。 */
const sanitizeExistingMedia = (element: Element): Element | null => {
  const template = document.createElement('template')
  template.innerHTML = DOMPurify.sanitize(element.outerHTML, {
    ADD_TAGS: ['iframe'],
    ADD_ATTR: ['allow', 'allowfullscreen', 'frameborder', 'scrolling', 'sandbox'],
    FORBID_TAGS: FORBIDDEN_ELEMENTS.split(','),
    FORBID_ATTR: ['srcdoc', 'ping'],
    ALLOW_DATA_ATTR: false
  })
  template.content.querySelectorAll('*').forEach(sanitizeStyle)
  return template.content.firstElementChild
}

/** 原稿媒体在纯文本预览中隐藏，仍需明确告知本轮整文替换是否省略了它们。 */
export function countMissingWritingMedia(originalContent: string, proposedHtml: string): number {
  const original = document.createElement('template')
  original.innerHTML = originalContent
  const proposed = document.createElement('template')
  proposed.innerHTML = proposedHtml
  const keys = new Map<string, string>()
  const remaining = new Map<string, number>()
  const roots = (content: DocumentFragment) => Array.from(content.querySelectorAll(`${MEDIA_ELEMENTS},img`))
    .filter(element => !element.parentElement?.closest(MEDIA_ELEMENTS))
  for (const element of roots(original.content)) {
    const key = element.tagName === 'IMG' ? `img:${element.getAttribute('src')?.trim()}` : element.outerHTML
    remaining.set(key, (remaining.get(key) || 0) + 1)
    keys.set(element.outerHTML, key)
    if (element.tagName !== 'IMG') {
      const safe = sanitizeExistingMedia(element)
      if (safe) keys.set(safe.outerHTML, key)
    }
  }
  for (const element of roots(proposed.content)) {
    const key = element.tagName === 'IMG' ? `img:${element.getAttribute('src')?.trim()}` : keys.get(element.outerHTML)
    if (key && remaining.has(key)) remaining.set(key, Math.max(0, remaining.get(key)! - 1))
  }
  return Array.from(remaining.values()).reduce((sum, count) => sum + count, 0)
}

/** 只用于 AI 写作；template 内容不进入活动 DOM，先移除自动资源，再交 DOMPurify。 */
export function sanitizeWritingHtml(html: string, originalContent = '', preview = false): string {
  const original = document.createElement('template')
  original.innerHTML = originalContent
  const existingImages = new Set(Array.from(original.content.querySelectorAll('img[src]'))
    .map(image => image.getAttribute('src')?.trim()))
  const existingMedia = new Map<string, Element>()
  original.content.querySelectorAll(MEDIA_ELEMENTS).forEach(element => {
    const safe = sanitizeExistingMedia(element)
    if (safe) {
      existingMedia.set(element.outerHTML, safe)
      existingMedia.set(safe.outerHTML, safe)
    }
  })
  const template = document.createElement('template')
  template.innerHTML = html
  const preserved = new Set<Element>()
  const mediaTags = new Set<string>()
  const mediaAttributes = new Set<string>()
  template.content.querySelectorAll(MEDIA_ELEMENTS).forEach(element => {
    if (!template.content.contains(element) || preserved.has(element)) return
    const known = existingMedia.get(element.outerHTML)
    if (preview || !known) {
      element.replaceWith(document.createTextNode(preview ? '[媒体在预览中隐藏]' : '[新媒体请手动添加]'))
      return
    }
    const safe = known.cloneNode(true) as Element
    element.replaceWith(safe)
    for (const node of [safe, ...safe.querySelectorAll('*')]) {
      preserved.add(node)
      mediaTags.add(node.tagName.toLowerCase())
      Array.from(node.attributes).forEach(attribute => mediaAttributes.add(attribute.name))
    }
  })
  template.content.querySelectorAll(FORBIDDEN_ELEMENTS).forEach(element => element.remove())
  template.content.querySelectorAll('source,track').forEach(element => { if (!preserved.has(element)) element.remove() })
  template.content.querySelectorAll('img').forEach(image => {
    if (preserved.has(image)) return
    const source = image.getAttribute('src')?.trim()
    if (preview || !source || !existingImages.has(source)) {
      image.replaceWith(document.createTextNode(preview ? '[图片在预览中隐藏]' : '[新图片请手动添加]'))
    }
  })
  template.content.querySelectorAll('*').forEach(element => {
    sanitizeStyle(element)
    if (!preserved.has(element)) {
      RESOURCE_ATTRIBUTES.forEach(attribute => element.removeAttribute(attribute))
      if (element.tagName !== 'IMG') element.removeAttribute('src')
    }
  })
  const sanitized = DOMPurify.sanitize(template.innerHTML, {
    ALLOWED_TAGS: preview ? TEXT_TAGS : [...TEXT_TAGS, 'img', ...mediaTags],
    ALLOWED_ATTR: preview ? TEXT_ATTRIBUTES : [...TEXT_ATTRIBUTES, 'src', 'alt', 'width', 'height', ...mediaAttributes],
    ALLOW_DATA_ATTR: false,
    FORBID_ATTR: ['srcdoc', 'ping']
  })
  if (!preview) return sanitized
  // 预览不暴露可被浏览器预取的外部 href；经净化的地址只在明确点击/键盘操作后打开。
  template.innerHTML = sanitized
  template.content.querySelectorAll('a[href]').forEach(link => {
    link.setAttribute('data-writing-href', link.getAttribute('href') || '')
    link.removeAttribute('href')
    link.setAttribute('role', 'link')
    link.setAttribute('tabindex', '0')
  })
  return template.innerHTML
}

export const sanitizeWritingPreview = (html: string) => sanitizeWritingHtml(html, '', true)

export function openWritingPreviewLink(event: Event): void {
  if (event.type !== 'click' && !(event instanceof KeyboardEvent && (event.key === 'Enter' || event.key === ' '))) return
  const target = event.target instanceof Element ? event.target.closest('a[data-writing-href]') : null
  if (!target || !(event.currentTarget instanceof Element) || !event.currentTarget.contains(target)) return
  const url = target.getAttribute('data-writing-href')
  if (!url) return
  event.preventDefault()
  window.open(url, '_blank', 'noopener,noreferrer')
}

export interface WritingUndoEntry {
  field: string
  oldValue: any
}

/** 两个父编辑器共用撤销归属和字段边界；整轮撤销不触碰 AI 未修改的字段。 */
export function useWritingUndo(
  getPostId: () => number | null | undefined,
  restoreField: (entry: WritingUndoEntry) => void,
  clearSuggestions: (field?: string) => void
) {
  const entries = ref<WritingUndoEntry[]>([])
  let owner: number | null = null
  const reset = () => { entries.value = []; owner = null; clearSuggestions() }
  const record = (original: WritingDraftSnapshot, updates: WritingUndoEntry[]) => {
    if ((original.postId ?? null) !== (getPostId() ?? null)) return
    owner = original.postId ?? null
    entries.value = copy(updates)
  }
  const undoField = (field: string) => {
    if (owner !== (getPostId() ?? null)) { reset(); return false }
    const index = entries.value.findIndex(entry => entry.field === field)
    if (index < 0) return false
    restoreField(copy(entries.value[index]))
    entries.value.splice(index, 1)
    clearSuggestions(field)
    return true
  }
  const undoRound = () => {
    if (owner === (getPostId() ?? null)) entries.value.forEach(entry => restoreField(copy(entry)))
    reset()
  }
  watch(getPostId, reset, { flush: 'sync' })
  return { entries, record, undoField, undoRound, reset }
}

export function useWritingReview<T extends WritingDraftSnapshot>(
  getDraft: () => T,
  onApply: (update: WritingFieldUpdatePayload, original: T) => void
) {
  const original = shallowRef<T | null>(null)
  const pending = ref<WritingFieldUpdatePayload>({})
  const succeeded = ref(false)
  let failed = false
  const applied = ref(false)
  const hasChanges = computed(() => Object.keys(pending.value).some(key => key !== 'fields'))
  const missingMedia = computed(() => pending.value.contentHtml === undefined ? 0
    : countMissingWritingMedia(original.value?.content || '', pending.value.contentHtml))
  const conflict = computed(() => original.value !== null && signature(original.value) !== signature(getDraft()))
  const canApply = computed(() => succeeded.value && hasChanges.value && !applied.value && !conflict.value)

  const begin = () => {
    original.value = copy(getDraft())
    pending.value = {}
    succeeded.value = false
    failed = false
    applied.value = false
    return copy(original.value)
  }
  const stage = (update: WritingFieldUpdatePayload) => {
    if (applied.value) return
    const values = Object.fromEntries(Object.entries(update).filter(([key, value]) => key !== 'fields' && value !== undefined && value !== null))
    pending.value = { ...pending.value, ...copy(values) }
  }
  const complete = () => { if (!failed) succeeded.value = true }
  const fail = () => { failed = true; succeeded.value = false }
  const apply = () => {
    if (!canApply.value || !original.value) return
    // 每轮只触发一次父编辑器写入；原始快照同时交给父页，整轮撤销不会被流式片段覆盖。
    applied.value = true
    const update = copy(pending.value)
    if (update.contentHtml !== undefined) update.contentHtml = sanitizeWritingHtml(update.contentHtml, original.value.content)
    onApply(update, copy(original.value))
  }
  return { pending, succeeded, applied, hasChanges, missingMedia, conflict, canApply, begin, stage, complete, fail, apply }
}
