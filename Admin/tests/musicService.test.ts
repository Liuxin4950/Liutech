import { File } from 'node:buffer'
import { beforeEach, expect, it, vi } from 'vitest'
import musicService from '../src/services/music'
import { post, put } from '../src/services/api'

vi.mock('../src/services/api', () => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))
beforeEach(() => vi.clearAllMocks())

it('编辑用 multipart PUT 提交封面及双轨，保留合法的空艺术家、零排序和禁用状态', () => {
  const full = new File(['full track'], 'full.mp3', { type: 'audio/mpeg' })
  const vocal = new File(['vocal track'], 'vocal.mp3', { type: 'audio/mpeg' })
  musicService.updateMusic(42, {
    title: '新歌曲', artist: '', cover: '/uploads/images/new.jpg', sortOrder: 0, status: 0,
    fullAudio: full as unknown as globalThis.File, vocalAudio: vocal as unknown as globalThis.File,
  })
  const [url, form, config] = vi.mocked(put).mock.calls[0]
  expect(url).toBe('/admin/music/42')
  expect(form).toBeInstanceOf(FormData)
  expect(form.get('title')).toBe('新歌曲')
  expect(form.get('artist')).toBe('')
  expect(form.get('coverUrl')).toBe('/uploads/images/new.jpg')
  expect(form.get('sortOrder')).toBe('0')
  expect(form.get('status')).toBe('0')
  expect(form.get('fullAudio').name).toBe('full.mp3')
  expect(form.get('vocalAudio').name).toBe('vocal.mp3')
  expect(config).toEqual({ timeout: 120000 })
})

it('仅编辑状态不发送原音轨、封面或其他元数据，从而保留数据库现有值', () => {
  musicService.updateMusic(42, { status: 0 })
  const form = vi.mocked(put).mock.calls[0][1] as FormData
  expect(Array.from(form.keys())).toEqual(['status'])
  expect(form.get('status')).toBe('0')
})

it('上传保持 fullAudio 与 vocalAudio 的既有字段，不设置会破坏 boundary 的请求头', () => {
  const full = new File(['full'], 'full.mp3', { type: 'audio/mpeg' })
  const vocal = new File(['vocal'], 'vocal.mp3', { type: 'audio/mpeg' })
  musicService.uploadMusic({ title: '歌曲', cover: '/uploads/images/cover.jpg', fullAudio: full as any, vocalAudio: vocal as any })
  const [url, form, config] = vi.mocked(post).mock.calls[0]
  expect(url).toBe('/admin/music')
  expect(form.get('coverUrl')).toBe('/uploads/images/cover.jpg')
  expect(form.get('fullAudio').name).toBe('full.mp3')
  expect(form.get('vocalAudio').name).toBe('vocal.mp3')
  expect(config).toEqual({ timeout: 60000 })
})

it('排序顺序编码为重复 ids 查询参数，批删继续发送 ID 数组', () => {
  musicService.updateSortOrder([9, 7, 3])
  expect(put).toHaveBeenCalledWith('/admin/music/sort?ids=9&ids=7&ids=3')
  musicService.batchDelete([7, 9])
  expect(post).toHaveBeenCalledWith('/admin/music/batch', [7, 9])
})
