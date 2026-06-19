/**
 * Vitest 全局测试设置
 * - mock localStorage / sessionStorage
 * - mock window.getComputedStyle
 * - suppress console noise
 */

import { vi } from 'vitest'
import { config } from '@vue/test-utils'

// jsdom 已提供 localStorage / sessionStorage，但部分方法可能缺失
if (!globalThis.localStorage) {
  const store: Record<string, string> = {}
  globalThis.localStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = String(v) },
    removeItem: (k: string) => { delete store[k] },
    clear: () => { for (const k in store) delete store[k] },
    get length() { return Object.keys(store).length },
    key: (i: number) => Object.keys(store)[i] ?? null
  }
}

if (!globalThis.sessionStorage) {
  const store: Record<string, string> = {}
  globalThis.sessionStorage = {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = String(v) },
    removeItem: (k: string) => { delete store[k] },
    clear: () => { for (const k in store) delete store[k] },
    get length() { return Object.keys(store).length },
    key: (i: number) => Object.keys(store)[i] ?? null
  }
}

// mock getComputedStyle (TheHeader 使用)
if (!window.getComputedStyle) {
  window.getComputedStyle = () => ({
    paddingLeft: '0px',
    getPropertyValue: () => ''
  } as any)
}

// suppress console.error / console.warn noise in test output
vi.spyOn(console, 'error').mockImplementation(() => {})
vi.spyOn(console, 'warn').mockImplementation(() => {})

// 全局 stub 组件 (TheHeader 依赖 Icon、router-link 等)
config.global.stubs = {
  'router-link': { template: '<a><slot /></a>' },
  'router-view': { template: '<slot />' },
  Transition: { template: '<slot />' }
}

// Mock SweetAlert2 (被 errorHandler 使用)
vi.mock('sweetalert2', () => ({
  default: {
    fire: vi.fn().mockResolvedValue({ isConfirmed: true }),
    close: vi.fn()
  }
}))

// Mock marked 以避免 DOM 依赖
vi.mock('marked', () => {
  class MockRenderer {
    code = vi.fn((code: string, lang?: string) => `<pre><code>${code}</code></pre>`)
    link = vi.fn((href: string, title: string, text: string) => `<a href="${href}">${text}</a>`)
    image = vi.fn((href: string, title: string, text: string) => `<img src="${href}" alt="${text}">`)
  }
  return {
    marked: {
      parse: vi.fn((content: string) => `<p>${content}</p>`),
      setOptions: vi.fn(),
      Renderer: MockRenderer,
      use: vi.fn()
    }
  }
})

// Mock DOMPurify
vi.mock('dompurify', () => ({
  default: {
    sanitize: vi.fn((html: string) => html)
  }
}))

// Mock highlight.js
vi.mock('highlight.js/lib/core', () => ({
  default: {
    registerLanguage: vi.fn(),
    getLanguage: vi.fn().mockReturnValue(true),
    highlight: vi.fn((code: string) => ({ value: code }))
  }
}))

// Mock highlight.js language modules
const hlModule = { default: {} }
vi.mock('highlight.js/lib/languages/javascript', () => hlModule)
vi.mock('highlight.js/lib/languages/typescript', () => hlModule)
vi.mock('highlight.js/lib/languages/java', () => hlModule)
vi.mock('highlight.js/lib/languages/python', () => hlModule)
vi.mock('highlight.js/lib/languages/css', () => hlModule)
vi.mock('highlight.js/lib/languages/sql', () => hlModule)
vi.mock('highlight.js/lib/languages/bash', () => hlModule)
vi.mock('highlight.js/lib/languages/json', () => hlModule)
vi.mock('highlight.js/lib/languages/xml', () => hlModule)
vi.mock('highlight.js/lib/languages/markdown', () => hlModule)
