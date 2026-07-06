import { ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import dayjs from 'dayjs'

/**
 * useModalForm 配置选项
 */
export interface UseModalFormOptions<T> {
  /** 创建 API */
  createFn?: (data: any) => Promise<any>
  /** 更新 API */
  updateFn?: (id: number, data: any) => Promise<any>
  /** 创建成功后回调 */
  onCreateSuccess?: () => void
  /** 更新成功后回调 */
  onUpdateSuccess?: () => void
  /** 默认表单数据 */
  defaultForm: () => Partial<T>
  /** 实体名称（用于提示信息） */
  entityName?: string
  /**
   * 草稿自动保存（可选）：设置了 key 后启用
   * - 新建时写入 lt-draft:<key>:new
   * - 编辑时写入 lt-draft:<key>:<id>
   * - 打开弹窗时若有草稿则弹 confirm 询问是否恢复
   * - 提交成功后自动清理
   */
  draft?: {
    key: string
    /** 防抖毫秒数，默认 800 */
    debounceMs?: number
    /** 判断表单是否有意义（避免仅打开就写空草稿），默认判断是否与 defaultForm 不同 */
    isDirty?: (form: any, defaults: any) => boolean
  }
}

interface DraftRecord {
  data: any
  savedAt: number
}

export function useModalForm<T extends Record<string, any>>(options: UseModalFormOptions<T>) {
  const {
    createFn,
    updateFn,
    onCreateSuccess,
    onUpdateSuccess,
    defaultForm,
    entityName = '记录',
    draft,
  } = options

  const modalVisible = ref(false)
  const modalTitle = ref('新建' + entityName)
  const isEdit = ref(false)
  const editingId = ref<number | null>(null)
  const confirmLoading = ref(false)

  const formRef = ref<any>(null)
  const formModel = ref<Partial<T>>(defaultForm())

  /** 草稿状态：上次保存时间，展示在 UI 上（可选消费） */
  const draftSavedAt = ref<number | null>(null)

  // ============== 草稿：读/写/清 ==============
  function draftStorageKey(): string | null {
    if (!draft) return null
    return `lt-draft:${draft.key}:${isEdit.value ? editingId.value ?? 'new' : 'new'}`
  }

  function readDraft(): DraftRecord | null {
    const k = draftStorageKey()
    if (!k) return null
    try {
      const raw = localStorage.getItem(k)
      if (!raw) return null
      const parsed = JSON.parse(raw) as DraftRecord
      return parsed?.data ? parsed : null
    } catch { return null }
  }

  function writeDraft(data: any) {
    const k = draftStorageKey()
    if (!k) return
    const rec: DraftRecord = { data, savedAt: Date.now() }
    try {
      localStorage.setItem(k, JSON.stringify(rec))
      draftSavedAt.value = rec.savedAt
    } catch { /* localStorage 可能满 */ }
  }

  function clearDraft() {
    const k = draftStorageKey()
    if (!k) return
    try { localStorage.removeItem(k) } catch { /* ignore */ }
    draftSavedAt.value = null
  }

  function isFormDirty(form: any): boolean {
    if (!draft) return false
    const defaults = defaultForm()
    if (draft.isDirty) return draft.isDirty(form, defaults)
    // 默认：任一字段值与 defaults 不同视为脏
    return Object.keys(form).some((k) => {
      const a = form[k]
      const b = (defaults as any)[k]
      if (a == null && b == null) return false
      if (Array.isArray(a) && Array.isArray(b) && a.length === 0 && b.length === 0) return false
      return a !== b
    })
  }

  // formModel 变化时防抖写入草稿
  let debounceTimer: number | null = null
  watch(formModel, (v) => {
    if (!draft || !modalVisible.value) return
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = window.setTimeout(() => {
      if (isFormDirty(v)) writeDraft({ ...v })
    }, draft.debounceMs ?? 800)
  }, { deep: true })

  /** 询问是否恢复草稿 */
  function promptRestore(rec: DraftRecord): Promise<boolean> {
    const dj = dayjs(rec.savedAt) as any
    const ago = typeof dj.fromNow === 'function' ? dj.fromNow() : dj.format('MM-DD HH:mm')
    return new Promise((resolve) => {
      Modal.confirm({
        title: '发现未保存的草稿',
        content: `上次编辑时间：${ago}，是否恢复？`,
        okText: '恢复草稿',
        cancelText: '放弃草稿',
        onOk: () => { resolve(true) },
        onCancel: () => { clearDraft(); resolve(false) },
      })
    })
  }

  const openCreate = async () => {
    if (!createFn) {
      console.warn('[useModalForm] createFn 未配置，无法新建')
      return
    }
    isEdit.value = false
    modalTitle.value = '新建' + entityName
    editingId.value = null
    formModel.value = defaultForm()
    draftSavedAt.value = null

    // 检查是否有旧草稿
    const rec = readDraft()
    if (rec && (await promptRestore(rec))) {
      formModel.value = { ...defaultForm(), ...rec.data }
      draftSavedAt.value = rec.savedAt
    }

    modalVisible.value = true
  }

  const openEdit = async (record: Partial<T> & Record<string, any>) => {
    if (!updateFn) {
      console.warn('[useModalForm] updateFn 未配置，无法编辑')
      return
    }
    isEdit.value = true
    modalTitle.value = '编辑' + entityName
    editingId.value = (record as Record<string, any>).id ?? null
    formModel.value = { ...record }
    draftSavedAt.value = null

    const rec = readDraft()
    if (rec && (await promptRestore(rec))) {
      formModel.value = { ...record, ...rec.data }
      draftSavedAt.value = rec.savedAt
    }

    modalVisible.value = true
  }

  const handleOk = async () => {
    try {
      confirmLoading.value = true
      await formRef.value?.validate?.()

      if (isEdit.value && updateFn) {
        const res = await updateFn(editingId.value as number, formModel.value)
        if (res.code === 200) {
          message.success('更新成功')
          clearDraft()
          modalVisible.value = false
          onUpdateSuccess?.()
        } else {
          message.error(res.message || '更新失败')
        }
      } else if (!isEdit.value && createFn) {
        const res = await createFn(formModel.value)
        if (res.code === 200) {
          message.success('创建成功')
          clearDraft()
          modalVisible.value = false
          onCreateSuccess?.()
        } else {
          message.error(res.message || '创建失败')
        }
      } else {
        console.warn('[useModalForm] 缺少 createFn 或 updateFn')
        message.error('操作配置错误')
      }
    } catch (e: any) {
      if (e?.errorFields) return
      console.error('[useModalForm] 操作失败:', e)
      message.error('操作失败：' + (e.message || '未知错误'))
    } finally {
      confirmLoading.value = false
    }
  }

  const handleCancel = () => {
    modalVisible.value = false
    // 关闭时不清草稿：用户可能想稍后继续。清理只发生在提交成功或明确"放弃草稿"。
  }

  return {
    modalVisible,
    modalTitle,
    isEdit,
    editingId,
    confirmLoading,
    formRef,
    formModel,
    draftSavedAt,
    openCreate,
    openEdit,
    handleOk,
    handleCancel,
    clearDraft,
  }
}
