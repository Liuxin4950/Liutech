import { chromium } from '../Web/node_modules/playwright/index.mjs'
import assert from 'node:assert/strict'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'

// 只访问本地 Admin；所有业务 API 使用明确夹具，不发送真实配置或供应商请求。
const baseUrl = process.env.LIUTECH_ADMIN_SMOKE_URL || 'http://127.0.0.1:3011'
assert(['127.0.0.1', 'localhost'].includes(new URL(baseUrl).hostname), 'Smoke 只能运行本地 Admin')
const output = process.env.LIUTECH_ADMIN_SMOKE_OUTPUT || resolve('artifacts/ai-settings-smoke')
await mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true })
const jsErrors = []
const models = [1, 2, 3].map(id => ({ id, modelName: `test/model-${id}`, displayName: `模型 ${id}`, provider: 'siliconflow', isEnabled: id !== 3, isDefault: id === 1, sortOrder: id, maxTokens: 4096, contextWindow: 32768, effectiveMaxTokens: 4096, effectiveContextWindow: 32768, inputBudgetTokens: 28160, temperature: 0.7, description: '本地回归夹具' }))
const initialTts = { enabled: true, provider: 'SILICONFLOW', baseUrl: 'http://tts.local:8000', voiceModel: 'local-kept', siliconFlowModel: 'FunAudioLLM/CosyVoice2-0.5B', siliconFlowVoiceUri: 'speech:kept', responseFormat: 'mp3', sampleRate: 44100, speed: 1 }
const state = { modelsFail: false, modelSaveFail: true, modelWrites: 0, defaultWrites: 0, ttsConfigFail: false, ttsStatusFail: false, ttsSaveFail: true, ttsWrites: 0, uploadFail: true, uploads: 0, speeches: 0, speechDelay: 300, config: { ...initialTts } }
function wave() { const rate = 8000, count = rate * 15, body = Buffer.alloc(44 + count * 2); body.write('RIFF'); body.writeUInt32LE(36 + count * 2, 4); body.write('WAVEfmt ', 8); body.writeUInt32LE(16, 16); body.writeUInt16LE(1, 20); body.writeUInt16LE(1, 22); body.writeUInt32LE(rate, 24); body.writeUInt32LE(rate * 2, 28); body.writeUInt16LE(2, 32); body.writeUInt16LE(16, 34); body.write('data', 36); body.writeUInt32LE(count * 2, 40); for (let i = 0; i < count; i++) body.writeInt16LE(Math.round(500 * Math.sin(2 * Math.PI * 440 * i / rate)), 44 + i * 2); return body }
const audio = wave()
async function pageFor(path) {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  page.setDefaultTimeout(10000); page.on('pageerror', error => jsErrors.push(error.message))
  page.on('requestfailed', request => console.log('SMOKE_REQUEST_FAILED', request.url(), request.failure()?.errorText))
  await page.addInitScript(() => { localStorage.setItem('token', 'local-ai-smoke-fixture'); window.smokeAudios = []; const NativeAudio = window.Audio; window.Audio = class extends NativeAudio { constructor(...args) { super(...args); window.smokeAudios.push(this) } } })
  await page.route('**/*', async route => {
    const url = new URL(route.request().url()), method = route.request().method()
    if (!['8080', '8081'].includes(url.port) && !url.pathname.includes('/tts/audio/')) return route.continue()
    const headers = { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*', 'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS' }
    const send = async (data, status = 200) => { try { await route.fulfill({ json: data, status, headers }) } catch { /* 取消的试听请求没有回包消费者 */ } }
    const fail = () => send({ success: false, message: '本地夹具模拟请求失败', code: 503 }, 503)
    if (method === 'OPTIONS') return send({})
    const p = url.pathname
    if (p.endsWith('/user/current')) return send({ code: 200, message: '成功', data: { id: 1, username: 'smoke', role: 'admin', status: 1 } })
    if (p.endsWith('/runtime')) return send({ aiOnline: true, aiMessage: '本地夹具', defaultModel: models.find(m => m.isDefault)?.modelName, tts: { enabled: true, online: false, configured: true, onlineVerified: false } })
    if (p.endsWith('/admin/models/list')) return state.modelsFail ? fail() : send(models)
    if (p.endsWith('/admin/models/2') && method === 'PUT') { state.modelWrites++; await new Promise(r => setTimeout(r, 250)); if (state.modelSaveFail) return fail(); Object.assign(models[1], route.request().postDataJSON()); return send(models[1]) }
    if (p.endsWith('/admin/models/2/default')) { state.defaultWrites++; await new Promise(r => setTimeout(r, 250)); models.forEach(m => { m.isDefault = m.id === 2 }); return send(null) }
    if (p.endsWith('/admin/tts/config')) { if (method === 'PUT') { state.ttsWrites++; await new Promise(r => setTimeout(r, 250)); if (state.ttsSaveFail) return fail(); state.config = route.request().postDataJSON(); return send(state.config) } return state.ttsConfigFail ? fail() : send(state.config) }
    if (p.endsWith('/admin/tts/status')) return state.ttsStatusFail ? fail() : send({ ...state.config, online: false, configured: true, onlineVerified: false, siliconFlowApiKeyConfigured: true, checkedAt: Date.now(), message: '已配置，待首次真实语音确认' })
    if (p.endsWith('/admin/tts/siliconflow/voices')) return send([{ model: 'IndexTeam/IndexTTS-2', customName: '其他音色', uri: 'speech:other' }])
    if (p.endsWith('/admin/tts/voices')) return send(['local-other'])
    if (p.endsWith('/admin/tts/siliconflow/voice')) { state.uploads++; await new Promise(r => setTimeout(r, 250)); return state.uploadFail ? fail() : send({ model: 'IndexTeam/IndexTTS-2', customName: '新音色', uri: 'speech:new' }) }
    if (p.endsWith('/admin/tts/test-speech')) { state.speeches++; await new Promise(r => setTimeout(r, state.speechDelay)); return send({ audioUrl: 'tts/audio/smoke.wav', provider: state.config.provider, format: 'wav' }) }
    if (p.includes('/tts/audio/')) return route.fulfill({ body: audio, contentType: 'audio/wav', headers })
    return send({ code: 200, message: '成功', data: [] })
  })
  await page.goto(`${baseUrl}${path}`)
  await page.waitForTimeout(1200)
  if (jsErrors.length) console.log('SMOKE_PAGE_ERRORS', JSON.stringify(jsErrors))
  await page.screenshot({ path: `${output}/ai-settings-last-loaded.png` })
  return page
}
const rapidly = async locator => locator.evaluate(button => { button.click(); button.click(); button.click() })
try {
  const modelPage = await pageFor('/ai-models')
  const modelRow = () => modelPage.locator('tbody tr').filter({ hasText: 'test/model-2' })
  await modelRow().waitFor()
  assert((await modelPage.getByRole('button', { name: '新增模型' }).boundingBox()).y < 500)
  await modelPage.screenshot({ path: `${output}/ai-models-desktop.png` })
  await modelRow().getByRole('button', { name: '编辑' }).click()
  await modelPage.getByPlaceholder('例如：DeepSeek-V3.2', { exact: true }).fill('保留的编辑草稿')
  await rapidly(modelPage.locator('.ant-modal-footer .ant-btn-primary'))
  await modelPage.getByText('本地夹具模拟请求失败', { exact: true }).waitFor()
  assert.equal(state.modelWrites, 1)
  assert.equal(await modelPage.getByPlaceholder('例如：DeepSeek-V3.2', { exact: true }).inputValue(), '保留的编辑草稿')
  assert.equal(await modelPage.locator('.ant-modal-content').count(), 1)
  state.modelSaveFail = false
  await modelPage.locator('.ant-modal-footer .ant-btn-primary').click()
  await modelPage.locator('.ant-modal-content').waitFor({ state: 'hidden' })
  await rapidly(modelRow().getByRole('button', { name: '设为默认' }))
  assert.equal(await modelPage.locator('.ant-modal-confirm').count(), 1)
  await rapidly(modelPage.locator('.ant-modal-confirm .ant-btn-primary'))
  await modelPage.locator('.ant-modal-confirm').waitFor({ state: 'hidden' })
  assert.equal(state.defaultWrites, 1)
  state.modelsFail = true
  await modelPage.getByRole('button', { name: '重新读取' }).click()
  await modelPage.getByText('模型列表读取失败，已保留上次结果。重新读取成功后再操作。').waitFor()
  assert.equal(await modelRow().getByRole('button', { name: '编辑' }).isDisabled(), true)
  state.modelsFail = false
  await modelPage.getByRole('button', { name: '重新读取' }).click()
  await modelPage.getByText('模型列表读取失败，已保留上次结果。重新读取成功后再操作。').waitFor({ state: 'hidden' })
  await modelPage.close()
  console.log('MODELS_SAVE_FAILURE_DRAFT_RETRY_LOCK_OK')

  const ttsPage = await pageFor('/ai-settings')
  await ttsPage.getByText('语音 待确认', { exact: true }).waitFor()
  await ttsPage.getByText('当前音色：speech:kept', { exact: true }).waitFor()
  const save = () => ttsPage.getByRole('button', { name: '保存并应用' })
  assert.equal(await save().isDisabled(), true)
  assert((await save().boundingBox()).y < 500)
  await ttsPage.screenshot({ path: `${output}/ai-voice-desktop.png` })
  await ttsPage.getByText('高级配置 · 音频参数与音色 URI', { exact: true }).click()
  await ttsPage.getByPlaceholder('speech:…').fill('speech:draft')
  assert.equal(await ttsPage.locator('.saved-config').getByText('speech:kept', { exact: true }).count(), 1)
  state.ttsStatusFail = true
  await ttsPage.getByRole('button', { name: '检测状态' }).click()
  await ttsPage.getByText('语音 检测失败', { exact: true }).waitFor()
  assert.equal(await ttsPage.getByPlaceholder('speech:…').inputValue(), 'speech:draft')
  await ttsPage.getByRole('button', { name: '读取音色' }).click()
  assert.equal(await ttsPage.getByPlaceholder('speech:…').inputValue(), 'speech:draft')
  await rapidly(save())
  await ttsPage.getByText('本地夹具模拟请求失败', { exact: true }).last().waitFor()
  await ttsPage.waitForTimeout(400)
  assert.equal(state.ttsWrites, 1)
  assert.equal(await ttsPage.getByPlaceholder('speech:…').inputValue(), 'speech:draft')
  assert.equal(state.config.siliconFlowVoiceUri, 'speech:kept')
  state.ttsSaveFail = state.ttsStatusFail = false
  await save().click()
  await ttsPage.getByText('有未保存改动', { exact: true }).waitFor({ state: 'hidden' })
  assert.equal(state.config.siliconFlowVoiceUri, 'speech:draft')
  await ttsPage.getByRole('button', { name: '上传新音色' }).click()
  await ttsPage.getByPlaceholder('给音色起个便于识别的名称').fill('新音色')
  await ttsPage.getByPlaceholder('准确填写参考音频中说出的文字').fill('参考文本')
  await ttsPage.locator('.ant-modal-content input[type=file]').setInputFiles({ name: 'reference.wav', mimeType: 'audio/wav', buffer: audio })
  await rapidly(ttsPage.locator('.ant-modal-footer .ant-btn-primary'))
  await ttsPage.waitForTimeout(450)
  assert.equal(state.uploads, 1)
  assert.equal(await ttsPage.getByPlaceholder('给音色起个便于识别的名称').inputValue(), '新音色')
  state.uploadFail = false
  const writesBeforeUpload = state.ttsWrites
  await ttsPage.locator('.ant-modal-footer .ant-btn-primary').click()
  await ttsPage.locator('.ant-modal-content').waitFor({ state: 'hidden' })
  assert.equal(await ttsPage.getByPlaceholder('speech:…').inputValue(), 'speech:new')
  assert.equal(state.ttsWrites, writesBeforeUpload)
  assert.equal(state.config.siliconFlowVoiceUri, 'speech:draft')
  await ttsPage.getByRole('button', { name: '放弃改动' }).click()
  state.ttsConfigFail = true
  await ttsPage.getByRole('button', { name: '重新读取配置' }).click()
  await ttsPage.getByText('语音配置读取失败。当前内容不可提交，请重新读取配置。').waitFor()
  assert.equal(await save().isDisabled(), true)
  assert.equal(await ttsPage.getByRole('switch').isDisabled(), true)
  state.ttsConfigFail = false
  await ttsPage.getByRole('button', { name: '重新读取配置' }).click()
  await ttsPage.getByText('语音配置读取失败。当前内容不可提交，请重新读取配置。').waitFor({ state: 'hidden' })
  await ttsPage.setViewportSize({ width: 768, height: 1000 })
  assert((await ttsPage.getByRole('button', { name: '试听已保存配置' }).boundingBox()).y < 500)
  await ttsPage.waitForFunction(() => !document.querySelector('.ant-message-notice'), null, { timeout: 8000 })
  await ttsPage.screenshot({ path: `${output}/ai-voice-tablet.png` })
  await ttsPage.close()
  console.log('TTS_DRAFT_SAVED_STATUS_UPLOAD_BOUNDARIES_OK')

  state.config = { ...initialTts, provider: 'GPT_SOVITS' }
  const gptPage = await pageFor('/ai-settings')
  await gptPage.getByText('local-kept（当前配置）', { exact: true }).waitFor()
  assert.equal(await gptPage.getByRole('button', { name: '保存并应用' }).isDisabled(), true)
  await rapidly(gptPage.getByRole('button', { name: '试听已保存配置' }))
  await gptPage.getByRole('button', { name: '停止试听' }).waitFor()
  assert.equal(state.speeches, 1)
  assert.equal(await gptPage.evaluate(() => window.smokeAudios.filter(a => !a.paused).length), 1)
  await gptPage.getByRole('button', { name: '停止试听' }).click()
  assert.equal(await gptPage.evaluate(() => window.smokeAudios.filter(a => !a.paused).length), 0)
  state.speechDelay = 1000
  await gptPage.getByRole('button', { name: '试听已保存配置' }).click()
  await gptPage.getByRole('button', { name: '取消试听' }).click()
  await gptPage.waitForTimeout(1200)
  assert.equal(await gptPage.evaluate(() => window.smokeAudios.filter(a => !a.paused).length), 0)
  await gptPage.screenshot({ path: `${output}/ai-voice-gpt.png` })
  console.log('TTS_SINGLE_PLAYBACK_STOP_AND_CANCEL_OK')
  assert.deepEqual(jsErrors, [])
  console.log('AI_ADMIN_SMOKE_OK ' + JSON.stringify({ modelWrites: state.modelWrites, defaultWrites: state.defaultWrites, ttsWrites: state.ttsWrites, uploads: state.uploads, speeches: state.speeches, output }))
} finally { await browser.close() }
