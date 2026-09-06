// 测试夹具仅供 Vitest 使用；不注入生产入口。
import { afterEach, vi } from 'vitest'
afterEach(() => { vi.restoreAllMocks() })
