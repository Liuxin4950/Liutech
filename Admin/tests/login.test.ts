import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

const webRequire = createRequire(new URL('../../Web/package.json', import.meta.url))
const vue = webRequire('vue')
const { parse, compileScript, compileTemplate } = webRequire('@vue/compiler-sfc')
const ts = webRequire('typescript')
const source = readFileSync(new URL('../src/views/Login.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'login-behavior-regression' })
const compiled = ts.transpileModule(script.content, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
const key = 'lt-admin-remembered-username'
const storage = { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() }
const success = vi.fn()
const scopes: { stop(): void }[] = []

function createPage(redirect: unknown = undefined) {
  const userStore = vue.reactive({ isLoading: false, login: vi.fn().mockResolvedValue(true) })
  const router = { push: vi.fn(), currentRoute: vue.ref({ query: { redirect } }) }
  const imports: Record<string, unknown> = {
    vue,
    'vue-router': { useRouter: () => router },
    '@/stores/user': { useUserStore: () => userStore },
    'ant-design-vue': { message: { success } },
    '@ant-design/icons-vue': {},
  }
  const module = { exports: {} as any }
  new Function('require', 'module', 'exports', compiled)((name: string) => {
    if (!(name in imports)) throw new Error(`Unexpected component dependency: ${name}`)
    return imports[name]
  }, module, module.exports)
  const scope = vue.effectScope()
  scopes.push(scope)
  const page = scope.run(() => module.exports.default.setup({}, { expose() {} }))
  return { page, userStore, router }
}

beforeEach(() => { vi.resetAllMocks(); vi.stubGlobal('localStorage', storage) })
afterEach(() => { for (const scope of scopes.splice(0)) scope.stop(); vi.unstubAllGlobals() })

it('真实登录模板可编译，记住用户名自动回填且不回填或持久化密码', async () => {
  expect(compileTemplate({ source: descriptor.template.content, filename: 'Login.vue', id: 'login-behavior-regression' }).errors).toEqual([])
  storage.getItem.mockReturnValue('liuxin')
  const { page, userStore } = createPage()
  expect(page.loginForm.username).toBe('liuxin')
  expect(page.loginForm.password).toBe('')
  expect(page.rememberMe.value).toBe(true)
  page.loginForm.username = '  liuxin  '
  page.loginForm.password = ' never-persist-this-password '
  await page.handleLogin()
  expect(userStore.login).toHaveBeenCalledWith('liuxin', ' never-persist-this-password ')
  expect(storage.getItem).toHaveBeenCalledWith(key)
  expect(storage.setItem).toHaveBeenCalledExactlyOnceWith(key, 'liuxin')
  expect(storage.setItem.mock.calls.flat()).not.toContain(page.loginForm.password)
})

it('取消记住用户名成功后只移除用户名记忆，失败登录不变更存储或跳转', async () => {
  storage.getItem.mockReturnValue('liuxin')
  const { page, userStore, router } = createPage()
  page.rememberMe.value = false
  page.loginForm.password = 'test-password'
  userStore.login.mockResolvedValueOnce(false)
  await page.handleLogin()
  expect(storage.removeItem).not.toHaveBeenCalled()
  expect(router.push).not.toHaveBeenCalled()
  await page.handleLogin()
  expect(storage.removeItem).toHaveBeenCalledExactlyOnceWith(key)
  expect(storage.setItem).not.toHaveBeenCalled()
})

it('已有登录请求时拒绝重复提交，空用户名和密码也不调用登录', async () => {
  const { page, userStore } = createPage()
  await page.handleLogin()
  expect(userStore.login).not.toHaveBeenCalled()
  page.loginForm.username = 'liuxin'
  page.loginForm.password = 'test-password'
  userStore.isLoading = true
  await page.handleLogin()
  expect(userStore.login).not.toHaveBeenCalled()
  userStore.isLoading = false
  let finish!: () => void
  userStore.login.mockImplementationOnce(() => {
    userStore.isLoading = true
    return new Promise<boolean>(resolve => { finish = () => { userStore.isLoading = false; resolve(true) } })
  })
  const first = page.handleLogin()
  await page.handleLogin()
  expect(userStore.login).toHaveBeenCalledOnce()
  finish()
  await first
})

it('内部 redirect 保留查询及锚点', async () => {
  const { page, router } = createPage('/resources?page=2#details')
  page.loginForm.username = 'liuxin'
  page.loginForm.password = 'test-password'
  await page.handleLogin()
  expect(router.push).toHaveBeenCalledWith('/resources?page=2#details')
})

it.each(['https://evil.example/path', '//evil.example/path', 'javascript:alert(1)', '/\\evil.example/path', ['/', '/resources']])('外部或非字符串 redirect %j 回到首页', async redirect => {
  const { page, router } = createPage(redirect)
  page.loginForm.username = 'liuxin'
  page.loginForm.password = 'test-password'
  await page.handleLogin()
  expect(router.push).toHaveBeenCalledWith('/')
})

it('localStorage 读取及写入抛错仍能成功登录并跳转', async () => {
  storage.getItem.mockImplementation(() => { throw new Error('storage unavailable') })
  storage.setItem.mockImplementation(() => { throw new Error('storage quota unavailable') })
  const { page, router } = createPage('/comments')
  expect(page.loginForm.username).toBe('')
  page.loginForm.username = 'liuxin'
  page.loginForm.password = 'test-password'
  page.rememberMe.value = true
  await page.handleLogin()
  expect(success).toHaveBeenCalledWith('登录成功')
  expect(router.push).toHaveBeenCalledWith('/comments')
})
