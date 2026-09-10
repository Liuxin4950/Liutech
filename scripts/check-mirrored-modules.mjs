#!/usr/bin/env node
/**
 * 跨端镜像文件一致性校验
 *
 * 背景：Web 与 Admin 是两个彼此独立的 Vite 根，各自有自己的 package.json 与
 * Docker 构建上下文，当前无法共享 npm 包。因此少量「两端必须完全一致」的模块
 * （SSE 协议解析、写作助手流式客户端）采用镜像文件的方式维护。
 *
 * 镜像文件的风险是"改一边忘另一边"，本项目已经真实发生过：
 * Admin 的写作助手有 429 限流提示、会把错误码交给上层，Web 那份没有。
 * 本脚本把这种漂移变成 CI 失败，而不是等线上出问题。
 *
 * 校验两件事：
 * 1. 每对镜像文件内容逐字节一致（sha256）；
 * 2. 镜像文件不引入应用内依赖（`@/…` 或 `../…`），保证将来能整体搬进共享包。
 *
 * 用法：node scripts/check-mirrored-modules.mjs
 *
 * @author 刘鑫
 */
import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')

/**
 * 需要逐字节一致的镜像文件清单
 * [用途说明, Web 端路径, Admin 端路径]
 */
const MIRRORED_MODULES = [
  ['SSE 协议解析', 'Web/src/services/sse.ts', 'Admin/src/services/sse.ts'],
  ['写作助手流式客户端', 'Web/src/services/writingStream.ts', 'Admin/src/services/writingStream.ts']
]

/** 镜像文件里不允许出现的应用内依赖（必须自包含，才能整体迁移到共享包） */
const FORBIDDEN_IMPORT = /from\s+['"](?:@\/|\.\.\/)/

/**
 * 计算文件内容的 sha256
 * @param {string} content 文件内容
 * @returns {string} 十六进制摘要
 */
const sha256 = (content) => createHash('sha256').update(content, 'utf8').digest('hex')

/**
 * 找出两段文本第一个不同的行，用于给出可操作的提示
 * @param {string} left 左侧内容
 * @param {string} right 右侧内容
 * @returns {string} 差异描述
 */
const describeFirstDifference = (left, right) => {
  const leftLines = left.split('\n')
  const rightLines = right.split('\n')
  const max = Math.max(leftLines.length, rightLines.length)

  for (let index = 0; index < max; index += 1) {
    if (leftLines[index] !== rightLines[index]) {
      return `首个差异在第 ${index + 1} 行：\n      Web  : ${leftLines[index] ?? '(文件已结束)'}\n      Admin: ${rightLines[index] ?? '(文件已结束)'}`
    }
  }
  return '内容行一致但字节不同（可能是换行符或行尾空格差异）'
}

/** 收集到的错误 */
const failures = []

for (const [label, webPath, adminPath] of MIRRORED_MODULES) {
  const webAbsolute = resolve(ROOT, webPath)
  const adminAbsolute = resolve(ROOT, adminPath)

  let webContent
  let adminContent
  try {
    webContent = await readFile(webAbsolute, 'utf8')
    adminContent = await readFile(adminAbsolute, 'utf8')
  } catch (error) {
    failures.push(`${label}：读取失败 —— ${error.message}`)
    continue
  }

  // 1. 逐字节一致
  if (sha256(webContent) !== sha256(adminContent)) {
    failures.push(
      `${label}：${relative(ROOT, webAbsolute)} 与 ${relative(ROOT, adminAbsolute)} 内容不一致。\n` +
      `      ${describeFirstDifference(webContent, adminContent)}\n` +
      `      修复方式：改完一侧后执行 cp ${webPath} ${adminPath}（或反向），再重新跑本脚本。`
    )
  }

  // 2. 自包含：不得依赖应用内模块
  for (const [path, content] of [[webPath, webContent], [adminPath, adminContent]]) {
    if (FORBIDDEN_IMPORT.test(content)) {
      failures.push(`${label}：${path} 引入了应用内模块（@/… 或 ../…），镜像文件必须自包含。`)
    }
  }

  console.log(`✓ ${label}：${webPath} ↔ ${adminPath}`)
}

if (failures.length > 0) {
  console.error('\n✗ 跨端镜像文件校验失败：\n')
  for (const failure of failures) {
    console.error(`  - ${failure}\n`)
  }
  process.exit(1)
}

console.log(`\n✓ 跨端镜像文件校验通过（${MIRRORED_MODULES.length} 对）`)
