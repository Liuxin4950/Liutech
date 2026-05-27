import { ref } from 'vue'
import { message } from 'ant-design-vue'

/**
 * useCrudActions 配置选项
 */
export interface UseCrudActionsOptions {
  /** 删除 API */
  deleteFn?: (id: number) => Promise<any>
  /** 批量删除 API */
  batchDeleteFn?: (ids: number[]) => Promise<any>
  /** 恢复 API */
  restoreFn?: (id: number) => Promise<any>
  /** 彻底删除 API */
  permanentDeleteFn?: (id: number) => Promise<any>
  /** 批量彻底删除 API */
  batchPermanentDeleteFn?: (ids: number[]) => Promise<any>
  /** 操作成功后刷新数据的回调 */
  onRefresh: () => void
  /** 清空选择的回调 */
  clearSelection?: () => void
  /** 实体名称（用于提示信息） */
  entityName?: string
  /** 删除模式：'soft'(默认) 软删除可恢复，'hard' 物理删除不可恢复 */
  mode?: 'soft' | 'hard'
}

/**
 * 统一的 CRUD 操作组合式函数
 * 封装了管理后台的通用增删改查操作：删除、恢复、彻底删除、批量操作
 */
export function useCrudActions(options: UseCrudActionsOptions) {
  const {
    deleteFn,
    batchDeleteFn,
    restoreFn,
    permanentDeleteFn,
    batchPermanentDeleteFn,
    onRefresh,
    clearSelection,
    entityName = '记录',
    mode = 'soft'
  } = options

  /** 操作加载状态，防止重复点击 */
  const loading = ref(false)

  /**
   * 删除（软删或硬删取决于 mode）
   */
  const handleDelete = async (id: number) => {
    if (!deleteFn) {
      console.warn('[useCrudActions] deleteFn 未配置')
      return
    }
    try {
      loading.value = true
      const res = await deleteFn(id)
      if (res.code === 200) {
        message.success(mode === 'hard' ? '彻底删除成功' : '删除成功')
        onRefresh()
      } else {
        message.error(res.message || '删除失败')
      }
    } catch (e: any) {
      console.error('[useCrudActions] 删除失败:', e)
      message.error('删除失败：' + (e.message || '网络错误'))
    } finally {
      loading.value = false
    }
  }

  /**
   * 批量删除（软删或硬删取决于 mode）
   */
  const handleBatchDelete = async (selectedKeys: number[]) => {
    if (!batchDeleteFn) {
      console.warn('[useCrudActions] batchDeleteFn 未配置')
      return
    }
    if (!selectedKeys.length) {
      message.warning('请选择要删除的' + entityName)
      return
    }
    try {
      loading.value = true
      const res = await batchDeleteFn(selectedKeys)
      if (res.code === 200) {
        message.success(mode === 'hard' ? '批量彻底删除成功' : '批量删除成功')
        clearSelection?.()
        onRefresh()
      } else {
        message.error(res.message || '批量删除失败')
      }
    } catch (e: any) {
      console.error('[useCrudActions] 批量删除失败:', e)
      message.error('批量删除失败：' + (e.message || '网络错误'))
    } finally {
      loading.value = false
    }
  }

  /**
   * 恢复删除
   */
  const handleRestore = async (id: number) => {
    if (!restoreFn) {
      console.warn('[useCrudActions] restoreFn 未配置')
      return
    }
    try {
      loading.value = true
      const res = await restoreFn(id)
      if (res.code === 200) {
        message.success('恢复成功')
        onRefresh()
      } else {
        message.error(res.message || '恢复失败')
      }
    } catch (e: any) {
      console.error('[useCrudActions] 恢复失败:', e)
      message.error('恢复失败：' + (e.message || '网络错误'))
    } finally {
      loading.value = false
    }
  }

  /**
   * 彻底删除
   */
  const handlePermanentDelete = async (id: number) => {
    if (!permanentDeleteFn) {
      console.warn('[useCrudActions] permanentDeleteFn 未配置')
      return
    }
    try {
      loading.value = true
      const res = await permanentDeleteFn(id)
      if (res.code === 200) {
        message.success('彻底删除成功')
        onRefresh()
      } else {
        message.error(res.message || '彻底删除失败')
      }
    } catch (e: any) {
      console.error('[useCrudActions] 彻底删除失败:', e)
      message.error('彻底删除失败：' + (e.message || '网络错误'))
    } finally {
      loading.value = false
    }
  }

  /**
   * 批量彻底删除
   */
  const handleBatchPermanentDelete = async (selectedKeys: number[]) => {
    if (!batchPermanentDeleteFn) {
      console.warn('[useCrudActions] batchPermanentDeleteFn 未配置')
      return
    }
    if (!selectedKeys.length) {
      message.warning('请选择要彻底删除的' + entityName)
      return
    }
    try {
      loading.value = true
      const res = await batchPermanentDeleteFn(selectedKeys)
      if (res.code === 200) {
        message.success('批量彻底删除成功')
        clearSelection?.()
        onRefresh()
      } else {
        message.error(res.message || '批量彻底删除失败')
      }
    } catch (e: any) {
      console.error('[useCrudActions] 批量彻底删除失败:', e)
      message.error('批量彻底删除失败：' + (e.message || '网络错误'))
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    handleDelete,
    handleBatchDelete,
    handleRestore,
    handlePermanentDelete,
    handleBatchPermanentDelete
  }
}
