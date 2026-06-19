/**
 * TinyMCE 编辑器内容区主题样式
 *
 * 结构：模板函数 + 亮/暗色色板。
 * TinyMCE 运行在 iframe 内，无法继承宿主页面的 CSS 变量，
 * 因此用 TypeScript 模板字符串生成完整的 <style> 内容。
 */

const SYSTEM_FONT_STACK = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Helvetica Neue', sans-serif"

interface ColorPalette {
  pageBg: string
  bodyBg: string
  bodyColor: string
  caretColor: string
  headingColor: string
  paragraphColor: string
  linkColor: string
  linkHoverColor: string
  listColor: string
  blockquoteBorder: string
  blockquoteBg: string
  blockquoteColor: string
  codeBg: string
  codeColor: string
  codeBorder: string
  preBg: string
  preBorder: string
  preColor: string
  tableBg: string
  tableBorder: string
  tableCellColor: string
  tableHeaderBg: string
  tableHeaderColor: string
  tableHeaderBorder: string
  tableRowHoverBg: string
  imgShadow: string
  hrGradient: string
  strongColor: string
  emColor: string
  delColor: string
  uColor: string
  markBg: string
  markColor: string
  placeholderColor: string
}

const lightPalette: ColorPalette = {
  pageBg: '#FFFFFF',
  bodyBg: '#FFFFFF',
  bodyColor: '#3C4043',
  caretColor: '#202124',
  headingColor: '#202124',
  paragraphColor: '#3C4043',
  linkColor: '#4A69D1',
  linkHoverColor: '#3A4F9A',
  listColor: '#3C4043',
  blockquoteBorder: '#4A69D1',
  blockquoteBg: '#F8F9FA',
  blockquoteColor: '#5F6368',
  codeBg: '#F7F9FC',
  codeColor: '#EA4335',
  codeBorder: '#F1F3F4',
  preBg: '#F8F9FA',
  preBorder: '#E8EAED',
  preColor: '#3C4043',
  tableBg: '#FFFFFF',
  tableBorder: '#E8EAED',
  tableCellColor: '#3C4043',
  tableHeaderBg: '#F8F9FA',
  tableHeaderColor: '#202124',
  tableHeaderBorder: '#4A69D1',
  tableRowHoverBg: '#F1F3F4',
  imgShadow: '0 2px 6px rgba(32, 33, 36, 0.12), 0 1px 3px rgba(32, 33, 36, 0.08)',
  hrGradient: 'linear-gradient(to right, transparent, #E8EAED, transparent)',
  strongColor: '#202124',
  emColor: '#5F6368',
  delColor: '#9AA0A6',
  uColor: '#F0B8C0',
  markBg: '#FEF7E0',
  markColor: '#3C4043',
  placeholderColor: '#7D8694',
}

const darkPalette: ColorPalette = {
  pageBg: '#202124',
  bodyBg: '#202124',
  bodyColor: '#E8EAED',
  caretColor: '#FFFFFF',
  headingColor: '#FFFFFF',
  paragraphColor: '#E8EAED',
  linkColor: '#8AB4F8',
  linkHoverColor: '#66B1FF',
  listColor: '#E8EAED',
  blockquoteBorder: '#8AB4F8',
  blockquoteBg: '#2D2F30',
  blockquoteColor: '#9AA0A6',
  codeBg: '#3C4043',
  codeColor: '#4285F4',
  codeBorder: '#5F6368',
  preBg: '#2D2F30',
  preBorder: '#5F6368',
  preColor: '#E8EAED',
  tableBg: '#2D2F30',
  tableBorder: '#5F6368',
  tableCellColor: '#E8EAED',
  tableHeaderBg: '#2D2F30',
  tableHeaderColor: '#FFFFFF',
  tableHeaderBorder: '#8AB4F8',
  tableRowHoverBg: '#3C4043',
  imgShadow: '0 4px 6px rgba(0, 0, 0, 0.3)',
  hrGradient: 'linear-gradient(to right, transparent, #5F6368, transparent)',
  strongColor: '#FFFFFF',
  emColor: '#9AA0A6',
  delColor: '#80868B',
  uColor: '#F8B4B4',
  markBg: '#856404',
  markColor: '#E8EAED',
  placeholderColor: '#A8B3C2',
}

const buildContentStyle = (p: ColorPalette) => `
  html {
    min-height: 100%;
    background-color: ${p.pageBg};
  }

  body {
    font-family: ${SYSTEM_FONT_STACK};
    font-size: 16px;
    line-height: 1.8;
    color: ${p.bodyColor};
    background-color: ${p.bodyBg};
    margin: 0;
    padding: 20px;
    position: relative;
    box-sizing: border-box;
    min-height: 100%;
    word-wrap: break-word;
    caret-color: ${p.caretColor};
  }

  body.mce-content-body[data-mce-placeholder]:not(.mce-visualblocks)::before {
    left: 20px;
    right: 20px;
    color: ${p.placeholderColor};
    opacity: 1;
    font-style: normal;
  }

  h1, h2, h3, h4, h5, h6 {
    margin: 24px 0 16px 0;
    font-weight: 600;
    color: ${p.headingColor};
    line-height: 1.4;
  }
  h1 { font-size: 2em; }
  h2 { font-size: 1.7em; }
  h3 { font-size: 1.4em; }
  h4 { font-size: 1.2em; }
  h5 { font-size: 1.1em; }
  h6 { font-size: 1em; }

  p {
    margin: 16px 0;
    color: ${p.paragraphColor};
    line-height: 1.8;
  }

  a {
    color: ${p.linkColor};
    text-decoration: none;
    transition: color 0.2s ease;
  }
  a:hover {
    color: ${p.linkHoverColor};
    text-decoration: underline;
  }

  ul, ol {
    margin: 16px 0;
    padding-left: 24px;
    color: ${p.listColor};
  }
  li {
    margin: 8px 0;
    line-height: 1.6;
  }

  blockquote {
    border-left: 4px solid ${p.blockquoteBorder};
    margin: 20px 0;
    padding: 16px 20px;
    background: ${p.blockquoteBg};
    color: ${p.blockquoteColor};
    font-style: italic;
    border-radius: 0 8px 8px 0;
  }
  blockquote p {
    margin: 0;
  }

  code {
    background: ${p.codeBg};
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    color: ${p.codeColor};
    font-size: 0.9em;
    border: 1px solid ${p.codeBorder};
  }

  pre {
    background: ${p.preBg};
    border: 1px solid ${p.preBorder};
    border-radius: 8px;
    padding: 16px;
    overflow-x: auto;
    color: ${p.preColor};
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 0.9em;
    line-height: 1.5;
  }
  pre code {
    background: none;
    border: none;
    padding: 0;
    color: inherit;
    font-size: inherit;
  }

  table {
    border-collapse: collapse;
    width: 100%;
    margin: 20px 0;
    background-color: ${p.tableBg};
    border-radius: 8px;
    overflow: hidden;
  }
  table td, table th {
    border: 1px solid ${p.tableBorder};
    padding: 12px 16px;
    color: ${p.tableCellColor};
    text-align: left;
  }
  table th {
    background-color: ${p.tableHeaderBg};
    font-weight: 600;
    color: ${p.tableHeaderColor};
    border-bottom: 2px solid ${p.tableHeaderBorder};
  }
  table tr:last-child td {
    border-bottom: none;
  }
  table tr:hover {
    background-color: ${p.tableRowHoverBg};
  }

  img {
    max-width: 100%;
    height: auto;
    border-radius: 8px;
    box-shadow: ${p.imgShadow};
    margin: 16px 0;
    display: block;
    margin-left: auto;
    margin-right: auto;
  }

  hr {
    border: none;
    height: 2px;
    background: ${p.hrGradient};
    margin: 32px 0;
  }

  strong, b {
    color: ${p.strongColor};
    font-weight: 600;
  }

  em, i {
    color: ${p.emColor};
    font-style: italic;
  }

  del, s {
    color: ${p.delColor};
    text-decoration: line-through;
  }

  u {
    text-decoration: underline;
    color: ${p.uColor};
  }

  mark {
    background-color: ${p.markBg};
    color: ${p.markColor};
    padding: 2px 4px;
    border-radius: 3px;
  }
`

export const getContentStyle = (isDark: boolean): string =>
  buildContentStyle(isDark ? darkPalette : lightPalette)
