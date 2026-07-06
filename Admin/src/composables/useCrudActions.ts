import { ref, h } from 'vue'
import { message, Button } from 'ant-design-vue'

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
  /** 批量恢复 API（可选，无则循环单条恢复） */
  batchRestoreFn?: (ids: number[]) => Promise<any>
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
  /** 撤销窗口毫秒数，默认 5000。设 0 关闭撤销功能 */
  undoWindowMs?: number
}

/**
 * 弹出带"撤销"按钮的 message，返回 close 函数。
 * onUndo 在用户点击撤销时调用。窗口关闭（用户点撤销或计时结束）后 message 自动消失。
 */
function showUndoMessage(text: string, undoWindowMs: number, onUndo: () => void) {
  if (undoWindowMs <= 0) {
    message.success(text)
    return
  }
  const durationSec = undoWindowMs / 1000
  const key = `lt-undo-${Date.now()}`
  let undone = false

  message.success({
    key,
    duration: durationSec,
    content: () =>
      h('span', { style: 'display:inline-flex;align-items:center;gap:12px' }, [
        text,
        h(
          Button as any,
          {
            type: 'link',
            size: 'small',
            style: 'padding:0;height:auto',
            onClick: () => {
              if (undone) return
              undone = true
              message.destroy(key)
              onUndo()
            },
          },
          () => '撤销',
        ),
      ]),
  })
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
    batchRestoreFn,
    permanentDeleteFn,
    batchPermanentDeleteFn,
    onRefresh,
    clearSelection,
    entityName = '记录',
    mode = 'soft',
    undoWindowMs = 5000,
  } = options

  /** 操作加载状态，防止重复点击 */
  const loading = ref(false)

  /** 撤销是否可用：软删 + 有 restoreFn */
  const undoEnabled = mode === 'soft' && !!restoreFn

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
        if (undoEnabled) {
          showUndoMessage(`${entityName}已删除`, undoWindowMs, async () => {
            const r = await restoreFn!(id).catch(() => null)
            if (r?.code === 200) {
              message.success('已撤销删除')
              onRefresh()
            } else {
              message.error('撤销失败')
            }
          })
        } else {
          message.success(mode === 'hard' ? '彻底删除成功' : '删除成功')
        }
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
    // 复制 ID 列表：clearSelection 会清空 selectedKeys 引用
    const idsSnapshot = [...selectedKeys]
    try {
      loading.value = true
      const res = await batchDeleteFn(idsSnapshot)
      if (res.code === 200) {
        if (undoEnabled) {
          showUndoMessage(`已删除 ${idsSnapshot.length} 条${entityName}`, undoWindowMs, async () => {
            try {
              if (batchRestoreFn) {
                await batchRestoreFn(idsSnapshot)
              } else {
                // 无批量恢复 API 时循环单条
                await Promise.all(idsSnapshot.map((id) => restoreFn!(id).catch(() => null)))
              }
              message.success(`已撤销删除，共恢复 ${idsSnapshot.length} 条`)
              onRefresh()
            } catch {
              message.error('撤销失败')
            }
          })
        } else {
          message.success(mode === 'hard' ? '批量彻底删除成功' : '批量删除成功')
        }
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
