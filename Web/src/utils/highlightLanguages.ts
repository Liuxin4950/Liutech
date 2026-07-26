/**
 * 代码高亮语言注册（集中管理，避免多处重复注册）
 *
 * highlight.js 按需注册语言：覆盖 TinyMCE codesample 编辑器可选的常用语言，
 * 避免引入全量包导致体积膨胀。模块加载时自动注册，ES module 单例保证只执行一次。
 *
 * 与编辑器对齐：codesample 的 'HTML/XML' 值为 'markup'，这里给 xml 注册
 * 'markup'/'html' 别名，确保 pre.language-markup 能被正确识别为 xml 高亮。
 */
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import java from 'highlight.js/lib/languages/java'
import python from 'highlight.js/lib/languages/python'
import css from 'highlight.js/lib/languages/css'
import sql from 'highlight.js/lib/languages/sql'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import markdown from 'highlight.js/lib/languages/markdown'
import php from 'highlight.js/lib/languages/php'
import go from 'highlight.js/lib/languages/go'
import rust from 'highlight.js/lib/languages/rust'
import c from 'highlight.js/lib/languages/c'
import cpp from 'highlight.js/lib/languages/cpp'
import csharp from 'highlight.js/lib/languages/csharp'
import ruby from 'highlight.js/lib/languages/ruby'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('java', java)
hljs.registerLanguage('python', python)
hljs.registerLanguage('css', css)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
// 'markup'/'html' 对齐 TinyMCE codesample 的 HTML/XML 选项；vue/jsx 无独立包，近似到 xml/javascript
hljs.registerAliases(['markup', 'html', 'vue'], { languageName: 'xml' })
hljs.registerAliases(['jsx'], { languageName: 'javascript' })
hljs.registerLanguage('markdown', markdown)
hljs.registerLanguage('php', php)
hljs.registerLanguage('go', go)
hljs.registerLanguage('rust', rust)
hljs.registerLanguage('c', c)
hljs.registerLanguage('cpp', cpp)
hljs.registerLanguage('csharp', csharp)
hljs.registerLanguage('ruby', ruby)

export { hljs }
