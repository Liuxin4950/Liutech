import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync, statSync } from 'node:fs'
import { dirname, extname, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const slash = (path) => path.split(sep).join('/')
const gitFiles = (args) => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).split('\0').filter(Boolean)
const roots = new Set(['README.md', 'AGENTS.md', 'CLAUDE.md', '快速部署指南.md', 'LiuTech-AI/AI接口文档.md'])
const repositoryFiles = [...new Set([
  ...gitFiles(['ls-files', '-z']),
  ...gitFiles(['ls-files', '--others', '--exclude-standard', '-z']),
])].filter((path) => existsSync(resolve(root, path)))
const exactPaths = new Set(repositoryFiles)
const casePaths = new Map(repositoryFiles.map((path) => [path.toLowerCase(), path]))
const files = repositoryFiles.filter((path) => path.endsWith('.md') && (path.startsWith('Docs/') || roots.has(path)))
const errors = []
const texts = new Map(files.map((path) => [path, readFileSync(resolve(root, path), 'utf8').replace(/^\uFEFF/, '')]))
const graph = new Map(files.map((path) => [path, new Set()]))
const anchors = new Map()

function visibleLines(text) {
  let fence = null
  return text.split(/\r?\n/).map((line) => {
    const marker = line.match(/^\s*(`{3,}|~{3,})/)
    if (marker) {
      if (!fence) fence = marker[1]
      else if (marker[1][0] === fence[0] && marker[1].length >= fence.length) fence = null
      return ''
    }
    return fence ? '' : line
  })
}

function headingAnchors(path) {
  if (anchors.has(path)) return anchors.get(path)
  const values = new Set()
  const duplicates = new Map()
  for (const line of visibleLines(texts.get(path) ?? readFileSync(resolve(root, path), 'utf8'))) {
    const heading = line.match(/^\s{0,3}#{1,6}\s+(.+?)\s*#*$/)
    if (!heading) continue
    const slug = heading[1].replace(/<[^>]+>/g, '').replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
      .toLowerCase().replace(/[^\p{L}\p{N}\p{M}_\- ]/gu, '').replace(/ /g, '-')
    const count = duplicates.get(slug) ?? 0
    duplicates.set(slug, count + 1)
    values.add(count ? `${slug}-${count}` : slug)
  }
  anchors.set(path, values)
  return values
}

for (const path of files) {
  const lines = visibleLines(texts.get(path))
  if (path.startsWith('Docs/架构/') && lines.length > 500) errors.push(`${path}: 超过 500 行，请按子领域拆分`)
  for (const [index, line] of lines.entries()) {
    for (const match of line.matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)) {
      const url = match[1].trim().replace(/^<|>$/g, '')
      if (/^[a-z][a-z\d+.-]*:|^\//i.test(url)) continue // 外部地址与站内路由示例。
      const separator = url.indexOf('#')
      const target = decodeURIComponent(separator < 0 ? url : url.slice(0, separator)).replace(/:\d+(?:-\d+)?$/, '')
      const anchor = separator < 0 ? '' : decodeURIComponent(url.slice(separator + 1))
      const destination = target ? resolve(dirname(resolve(root, path)), target) : resolve(root, path)
      const name = slash(relative(root, destination))
      if (!existsSync(destination)) {
        errors.push(`${path}:${index + 1}: 不存在的本地路径 ${url}`)
        continue
      }
      if (statSync(destination).isFile() && !exactPaths.has(name) && casePaths.has(name.toLowerCase())) {
        errors.push(`${path}:${index + 1}: 路径大小写不匹配，应为 ${casePaths.get(name.toLowerCase())}`)
      }
      if (graph.has(name)) graph.get(path).add(name)
      if (anchor && extname(destination) === '.md' && !/^L\d+(?:-L?\d+)?$/.test(anchor) && statSync(destination).isFile()) {
        if (!headingAnchors(name).has(anchor)) errors.push(`${path}:${index + 1}: 不存在的章节锚点 ${url}`)
      }
    }
  }
}

const moduleIndex = graph.get('Docs/架构/README.md') ?? new Set()
for (const path of files) {
  if (/^Docs\/架构\/(前端|后端|设计|运维)\/[^/]+\/总览\.md$/.test(path) && !moduleIndex.has(path)) {
    errors.push(`${path}: 未登记到 Docs/架构/README.md 模块索引`)
  }
}

const reachable = new Set()
const pending = ['Docs/README.md']
while (pending.length) {
  const path = pending.pop()
  if (reachable.has(path)) continue
  reachable.add(path)
  pending.push(...(graph.get(path) ?? []))
}
for (const path of files) {
  if (path.startsWith('Docs/') && !reachable.has(path)) errors.push(`${path}: 无法从 Docs/README.md 进入，请补充索引链接`)
}

if (errors.length) {
  console.error(`文档检查失败：${errors.length} 项`)
  for (const error of errors) console.error(`- ${error}`)
  process.exitCode = 1
} else {
  console.log(`文档检查通过：${files.length} 份 Markdown；本地链接、章节锚点、模块索引、文档入口与架构篇幅均有效。`)
}
