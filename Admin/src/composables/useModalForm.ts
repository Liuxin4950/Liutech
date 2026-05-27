import { ref } from 'vue'
import { message } from 'ant-design-vue'

/**
 * useModalForm 配置选项
 */
export interface UseModalFormOptions<T> {
  /** 创建 API */
  createFn?: (data: Partial<T>) => Promise<any>
  /** 更新 API */
  updateFn?: (id: number, data: Partial<T>) => Promise<any>
  /** 创建成功后回调 */
  onCreateSuccess?: () => void
  /** 更新成功后回调 */
  onUpdateSuccess?: () => void
  /** 默认表单数据 */
  defaultForm: () => Partial<T>
  /** 实体名称（用于提示信息） */
  entityName?: string
}

/**
 * 统一的弹窗表单组合式函数
 * 封装了管理后台的弹窗表单逻辑：新建/编辑切换、表单提交、加载状态
 */
export function useModalForm<T extends Record<string, any>>(options: UseModalFormOptions<T>) {
  const {
    createFn,
    updateFn,
    onCreateSuccess,
    onUpdateSuccess,
    defaultForm,
    entityName = '记录'
  } = options

  // 弹窗状态
  const modalVisible = ref(false)
  const modalTitle = ref('新建' + entityName)
  const isEdit = ref(false)
  const editingId = ref<number | null>(null)
  const confirmLoading = ref(false)

  // 表单引用和数据
  const formRef = ref<any>(null)
  const formModel = ref<Partial<T>>(defaultForm())

  /**
   * 打开新建弹窗
   */
  const openCreate = () => {
    if (!createFn) {
      console.warn('[useModalForm] createFn 未配置，无法新建')
      return
    }
    isEdit.value = false
    modalTitle.value = '新建' + entityName
    editingId.value = null
    formModel.value = defaultForm()
    modalVisible.value = true
  }

  /**
   * 打开编辑弹窗
   */
  const openEdit = (record: T) => {
    if (!updateFn) {
      console.warn('[useModalForm] updateFn 未配置，无法编辑')
      return
    }
    isEdit.value = true
    modalTitle.value = '编辑' + entityName
    editingId.value = (record as Record<string, any>).id ?? null
    formModel.value = { ...record }
    modalVisible.value = true
  }

  /**
   * 提交表单
   */
  const handleOk = async () => {
    try {
      confirmLoading.value = true
      await formRef.value?.validate?.()

      if (isEdit.value && updateFn) {
        const res = await updateFn(editingId.value as number, formModel.value)
        if (res.code === 200) {
          message.success('更新成功')
          modalVisible.value = false
          onUpdateSuccess?.()
        } else {
          message.error(res.message || '更新失败')
        }
      } else if (!isEdit.value && createFn) {
        const res = await createFn(formModel.value)
        if (res.code === 200) {
          message.success('创建成功')
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
      // 区分校验失败和API异常
      if (e?.errorFields) {
        // 表单校验失败，antd 已显示红色提示，无需额外处理
        return
      }
      // API 异常（网络错误、500等）
      console.error('[useModalForm] 操作失败:', e)
      message.error('操作失败：' + (e.message || '未知错误'))
    } finally {
      confirmLoading.value = false
    }
  }

  /**
   * 取消弹窗
   */
  const handleCancel = () => {
    modalVisible.value = false
  }

  return {
    // 状态
    modalVisible,
    modalTitle,
    isEdit,
    editingId,
    confirmLoading,
    formRef,
    formModel,

    // 方法
    openCreate,
    openEdit,
    handleOk,
    handleCancel
  }
}
