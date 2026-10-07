import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// 复用 Web 已安装的 Vitest；Admin 不重复引入一套测试依赖。
const webRequire = createRequire(new URL('../Web/package.json', import.meta.url))
const root = fileURLToPath(new URL('.', import.meta.url))

export default {
  root,
  resolve: {
    alias: {
      '@': resolve(root, 'src'),
      vitest: resolve(dirname(webRequire.resolve('vitest/package.json')), 'dist/index.js'),
    },
  },
  test: {
    environment: 'node',
    include: ['tests/**/*.test.ts'],
  },
}
