import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

const webRequire = createRequire(new URL('../../Web/package.json', import.meta.url))
const vue = webRequire('vue')
const { parse, compileScript, compileTemplate } = webRequire('@vue/compiler-sfc')
const ts = webRequire('typescript')
const source = readFileSync(new URL('../src/views/admin/MusicManagement.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'music-editor-regression' })
const compiled = ts.transpileModule(script.content, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
const service = { updateMusic: vi.fn(), updateSortOrder: vi.fn(), uploadMusic: vi.fn() }
const upload = vi.fn()
const scopes: { stop(): void }[] = []
const players: any[] = []
const record = (id = 1) => ({ id, title: `歌曲 ${id}`, artist: '作者', coverUrl: '/uploads/images/old.png', fullAudioUrl: '/uploads/music/full-old.mp3', vocalUrl: '/uploads/music/vocal-old.mp3', sortOrder: 0, status: 1 })

function createPage() {
  let options: any
  let onDeactivate: (() => void) | undefined
  const submit = vi.fn()
  const load = vi.fn().mockResolvedValue(undefined)
  const module = { exports: {} as any }
  const imports: Record<string, unknown> = {
    vue: { ...vue, onUnmounted() {}, onDeactivated(callback: () => void) { onDeactivate = callback } },
    'ant-design-vue': { message: { error: vi.fn(), warning: vi.fn(), success: vi.fn() } },
    '@ant-design/icons-vue': {},
    '@/components/LtFilterBar.vue': {},
    '@/components/LtTableCard.vue': {},
    '@/composables': {
      useTablePage: () => ({ loading: vue.ref(false), selectedRowKeys: vue.ref([]), dataSource: vue.ref([]), searchParams: vue.ref({ status: undefined, keyword: '' }), pagination: {}, load }),
      useCrudActions: () => ({}),
      useModalForm: (value: any) => {
        options = value
        return { modalVisible: vue.ref(false), confirmLoading: vue.ref(false), formRef: vue.ref(null), formModel: vue.ref(value.defaultForm()), isEdit: vue.ref(false), editingId: vue.ref(null), handleOk: submit }
      },
    },
    '@/composables/useTableColumnPrefs': { useTableColumnPrefs: () => ({ prefColumns: vue.ref([]) }) },
    '@/composables/useTableExport': { useTableExport: () => ({}) },
    '@/components/TableColumnSettings.vue': {}, '@/components/TableExportButton.vue': {},
    '../../services/music': { __esModule: true, default: service },
    '../../utils/utils': { formatDateTime: (value: string) => value },
    '../../services/upload': { ImageUploadService: { uploadImage: upload }, pickUploadFile: (value: any) => value.file },
  }
  new Function('require', 'module', 'exports', compiled)((name: string) => {
    if (!(name in imports)) throw new Error(`Unexpected component dependency: ${name}`)
    return imports[name]
  }, module, module.exports)
  const scope = vue.effectScope()
  scopes.push(scope)
  const page = scope.run(() => module.exports.default.setup({}, { expose() {} }))
  return { page, options, submit, load, deactivate: () => onDeactivate?.() }
}
function deferred() {
  let resolve!: (value: unknown) => void
  const promise = new Promise(res => { resolve = res })
  return { promise, resolve }
}
beforeEach(() => {
  vi.resetAllMocks()
  players.length = 0
  vi.stubGlobal('Audio', class {
    src = ''; onended = null; onplaying = null; onpause = null; onerror = null
    pause = vi.fn(); play = vi.fn().mockResolvedValue(undefined)
    constructor() { players.push(this) }
  })
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:cover-preview')
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
})
afterEach(() => { for (const scope of scopes.splice(0)) scope.stop(); vi.restoreAllMocks(); vi.unstubAllGlobals() })

it('真实编辑模板可编译，仅替换已选择的音轨并携带当前封面', async () => {
  expect(compileTemplate({ source: descriptor.template.content, filename: 'MusicManagement.vue', id: 'music-editor-regression' }).errors).toEqual([])
  const { page, options } = createPage()
  page.openEdit(record())
  const full = { name: 'new-full.mp3', type: 'audio/mpeg' }
  page.handleEditAudioChange({ file: full }, 'full')
  await options.updateFn(1, page.editForm.value)
  expect(service.updateMusic).toHaveBeenCalledWith(1, { title: '歌曲 1', artist: '作者', sortOrder: 0, status: 1, cover: '/uploads/images/old.png', fullAudio: full, vocalAudio: undefined })
  page.editFullAudio.value = null
  const vocal = { name: 'new-vocal.mp3', type: 'audio/mpeg' }
  page.handleEditAudioChange({ file: vocal }, 'vocal')
  await options.updateFn(1, page.editForm.value)
  expect(service.updateMusic.mock.calls[1][1]).toMatchObject({ fullAudio: undefined, vocalAudio: vocal })
  expect(page.editExistingAudio.value).toEqual({ full: '/uploads/music/full-old.mp3', vocal: '/uploads/music/vocal-old.mp3' })
})

it('封面上传失败保留旧封面，上传期间阻止保存', async () => {
  const pending = deferred()
  upload.mockReturnValue(pending.promise)
  const { page, submit } = createPage()
  page.openEdit(record())
  const change = page.handleEditCoverChange({ file: { name: 'cover.png', type: 'image/png' } })
  page.handleEditSubmit()
  expect(submit).not.toHaveBeenCalled()
  pending.resolve({ fileUrl: '/uploads/images/new.png' })
  await change
  expect(page.editForm.value.coverUrl).toBe('/uploads/images/new.png')
  upload.mockRejectedValue(new Error('upload failed'))
  await page.handleEditCoverChange({ file: { name: 'bad.png', type: 'image/png' } })
  expect(page.editForm.value.coverUrl).toBe('/uploads/images/new.png')
  expect(page.editCoverPreview.value).toBe('blob:cover-preview')
})

it('切换编辑歌曲后过时封面响应不能写入新表单，音轨选择也不会串歌', async () => {
  const pending = deferred()
  upload.mockReturnValue(pending.promise)
  const { page } = createPage()
  page.openEdit(record(1))
  page.handleEditAudioChange({ file: { name: 'first.mp3', type: 'audio/mpeg' } }, 'full')
  const change = page.handleEditCoverChange({ file: { name: 'first.png', type: 'image/png' } })
  page.openEdit(record(2))
  pending.resolve({ fileUrl: '/uploads/images/first-new.png' })
  await change
  expect(page.editForm.value.title).toBe('歌曲 2')
  expect(page.editForm.value.coverUrl).toBe('/uploads/images/old.png')
  expect(page.editFullAudio.value).toBeNull()
  expect(page.editCoverUploading.value).toBe(false)
})

it('完整音轨及人声试听切换使用各自地址，上传缺少人声时拒绝提交', async () => {
  const { page } = createPage()
  await page.playPreview(record())
  expect(players[0].src).toBe('/uploads/music/full-old.mp3')
  page.previewTrack.value = 'vocal'
  await vue.nextTick()
  await page.playPreview(record())
  expect(players[0].src).toBe('/uploads/music/vocal-old.mp3')
  page.uploadForm.value = { title: '新歌曲', artist: '', coverUrl: '/cover.png', fullAudio: { name: 'full.mp3' }, vocalAudio: null }
  await page.handleUpload()
  expect(service.uploadMusic).not.toHaveBeenCalled()
})

it('状态、关键词筛选及列表加载期间拒绝移动排序，不提交部分歌单', async () => {
  const { page } = createPage()
  page.dataSource.value = [record(1), record(2), record(3)]
  page.searchParams.value.status = 0
  await page.handleSortChange(2, 'up')
  page.searchParams.value.status = undefined
  page.searchParams.value.keyword = '歌曲'
  await page.handleSortChange(2, 'up')
  page.searchParams.value.keyword = ''
  page.loading.value = true
  await page.handleSortChange(2, 'up')
  expect(service.updateSortOrder).not.toHaveBeenCalled()
  expect(page.dataSource.value.map((item: any) => item.id)).toEqual([1, 2, 3])
})

it('排序保存及重载期间锁定重复移动，保存完成后释放锁', async () => {
  const save = deferred()
  const reload = deferred()
  service.updateSortOrder.mockReturnValueOnce(save.promise)
  const { page, load } = createPage()
  load.mockReturnValueOnce(reload.promise)
  page.dataSource.value = [record(1), record(2), record(3)]
  const sorting = page.handleSortChange(2, 'up')
  expect(page.sortLoading.value).toBe(true)
  expect(service.updateSortOrder).toHaveBeenCalledWith([2, 1, 3])
  await page.handleSortChange(3, 'up')
  expect(service.updateSortOrder).toHaveBeenCalledTimes(1)
  save.resolve(undefined)
  await vi.waitFor(() => expect(load).toHaveBeenCalledOnce())
  expect(page.sortLoading.value).toBe(true)
  reload.resolve(undefined)
  await sorting
  expect(page.sortLoading.value).toBe(false)
  await page.handleSortChange(3, 'up')
  expect(service.updateSortOrder).toHaveBeenCalledTimes(2)
  expect(page.sortLoading.value).toBe(false)
})

it('KeepAlive 失活暂停程序试听及弹窗原生双轨，清理播放状态', async () => {
  const { page, deactivate } = createPage()
  const nativeFull = { pause: vi.fn() }
  const nativeVocal = { pause: vi.fn() }
  const anotherAudio = { pause: vi.fn() }
  page.editFullPlayer.value = nativeFull
  page.editVocalPlayer.value = nativeVocal
  page.musicPage.value = { querySelectorAll: vi.fn().mockReturnValue([anotherAudio]) }
  await page.playPreview(record())
  page.isPlaying.value = true
  players[0].pause.mockClear()
  deactivate()
  expect(players[0].pause).toHaveBeenCalledOnce()
  expect(nativeFull.pause).toHaveBeenCalledOnce()
  expect(nativeVocal.pause).toHaveBeenCalledOnce()
  expect(anotherAudio.pause).toHaveBeenCalledOnce()
  expect(page.currentPlayingId.value).toBeNull()
  expect(page.previewLoadingId.value).toBeNull()
  expect(page.isPlaying.value).toBe(false)
})
