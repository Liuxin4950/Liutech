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
  onRefresh: () => void | Promise<void>
  /** 清空选择的回调 */
  clearSelection?: () => void
  /** 实体名称（用于提示信息） */
  entityName?: string
  /** 删除模式：'soft'(默认) 软删除可恢复，'hard' 物理删除不可恢复 */
  mode?: 'soft' | 'hard'
  /** 撤销窗口毫秒数，默认 5000。设 0 关闭撤销功能 */
  undoWindowMs?: number
}

/** 部分管理接口以 code=200/data=false 表达未完成写操作，不能当作成功。 */
async function callMutation<T>(operation: (value: T) => Promise<any>, value: T) {
  const result = await operation(value)
  if (result === false || result?.data === false) throw new Error('操作未完成')
  return result
}

/**
 * 弹出带"撤销"按钮的 message。
 * onUndo 在用户点击撤销时调用。窗口关闭（用户点撤销或计时结束）后 message 自动消失。
 * 返回 undefined 表示当前正在执行其它操作，保留撤销入口以便稍后点击。
 */
function showUndoMessage(text: string, undoWindowMs: number, onUndo: () => Promise<void> | undefined) {
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
              const operation = onUndo()
              if (!operation) return
              undone = true
              message.destroy(key)
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

  const undoEnabled = mode === 'soft' && !!restoreFn
  const batchUndoEnabled = mode === 'soft' && !!(batchRestoreFn || restoreFn)

  /** 撤销也占用操作锁；繁忙时不消费撤销按钮。 */
  const startUndo = (operation: () => Promise<void>) => {
    if (loading.value) return
    loading.value = true
    return operation().finally(() => { loading.value = false })
  }

  /** 单条恢复的 fallback 必须按结果提示，不能把失败当成功。 */
  const restoreMany = async (ids: number[], undo: boolean) => {
    try {
      if (batchRestoreFn) {
        await callMutation(batchRestoreFn, ids)
        // 现有批量 API 不返回逐项结果，不能据 ID 数量声称全部恢复。
        message.success(undo ? '已撤销删除' : '批量恢复成功')
        if (!undo) clearSelection?.()
        await onRefresh()
        return
      }

      const results = await Promise.allSettled(ids.map((id) => callMutation(restoreFn!, id)))
      const restored = results.filter(result => result.status === 'fulfilled').length
      const failures = results.filter((result): result is PromiseRejectedResult => result.status === 'rejected')
      if (!failures.length) {
        message.success(undo ? `已撤销删除，共恢复 ${restored} 条` : `共恢复 ${restored} 条`)
        if (!undo) clearSelection?.()
      } else if (restored) {
        message.warning(`已恢复 ${restored} 条，${failures.length} 条${undo ? '撤销' : '恢复'}失败`)
      } else if (failures.some(result => !result.reason?.isBusiness)) {
        message.error(`${undo ? '撤销' : '批量恢复'}失败，${failures.length} 条均未恢复`)
      }
      // 部分成功同样要刷新，让界面反映已经恢复的记录。
      if (restored) await onRefresh()
    } catch (e: any) {
      if (!e?.isBusiness) message.error(undo ? '撤销失败' : '批量恢复失败，请重试')
    }
  }

  /**
   * 删除（软删或硬删取决于 mode）
   */
  const handleDelete = async (id: number) => {
    if (loading.value) return
    if (!deleteFn) {
      console.warn('[useCrudActions] deleteFn 未配置')
      return
    }
    try {
      loading.value = true
      await callMutation(deleteFn, id)
      if (undoEnabled) {
        showUndoMessage(`${entityName}已删除`, undoWindowMs, () => startUndo(async () => {
          try {
            await callMutation(restoreFn!, id)
            message.success('已撤销删除')
            await onRefresh()
          } catch (e: any) {
            // 业务错误已由拦截器提示，这里只兜底非业务错误（网络等）
            if (!e?.isBusiness) message.error('撤销失败')
          }
        }))
      } else {
        message.success(mode === 'hard' ? '彻底删除成功' : '删除成功')
      }
      await onRefresh()
    } catch (e: any) {
      console.error('[useCrudActions] 删除失败:', e)
      if (!e?.isBusiness) message.error('删除失败，请重试')
    } finally {
      loading.value = false
    }
  }

  /**
   * 批量删除（软删或硬删取决于 mode）
   */
  const handleBatchDelete = async (selectedKeys: number[]) => {
    if (loading.value) return
    if (!batchDeleteFn) {
      console.warn('[useCrudActions] batchDeleteFn 未配置')
      return
    }
    if (!selectedKeys.length) {
      message.warning('请选择要删除的' + entityName)
      return
    }
    // 复制 ID 列表：clearSelection 会清空 selectedKeys 引用
    const idsSnapshot = [...new Set(selectedKeys)]
    try {
      loading.value = true
      await callMutation(batchDeleteFn, idsSnapshot)
      if (batchUndoEnabled) {
        showUndoMessage('批量删除成功', undoWindowMs, () => startUndo(() => restoreMany(idsSnapshot, true)))
      } else {
        message.success(mode === 'hard' ? '批量彻底删除成功' : '批量删除成功')
      }
      clearSelection?.()
      await onRefresh()
    } catch (e: any) {
      console.error('[useCrudActions] 批量删除失败:', e)
      if (!e?.isBusiness) message.error('批量删除失败，请重试')
    } finally {
      loading.value = false
    }
  }

  /**
   * 恢复删除
   */
  const handleRestore = async (id: number) => {
    if (loading.value) return
    if (!restoreFn) {
      console.warn('[useCrudActions] restoreFn 未配置')
      return
    }
    try {
      loading.value = true
      await callMutation(restoreFn, id)
      message.success('恢复成功')
      await onRefresh()
    } catch (e: any) {
      console.error('[useCrudActions] 恢复失败:', e)
      if (!e?.isBusiness) message.error('恢复失败，请重试')
    } finally {
      loading.value = false
    }
  }

  /** 批量恢复复用相同的结果统计与操作锁。 */
  const handleBatchRestore = async (selectedKeys: number[]) => {
    if (loading.value) return
    if (!batchRestoreFn && !restoreFn) {
      console.warn('[useCrudActions] restoreFn/batchRestoreFn 未配置')
      return
    }
    if (!selectedKeys.length) {
      message.warning('请选择要恢复的' + entityName)
      return
    }
    const idsSnapshot = [...new Set(selectedKeys)]
    try {
      loading.value = true
      await restoreMany(idsSnapshot, false)
    } finally {
      loading.value = false
    }
  }

  /**
   * 彻底删除
   */
  const handlePermanentDelete = async (id: number) => {
    if (loading.value) return
    if (!permanentDeleteFn) {
      console.warn('[useCrudActions] permanentDeleteFn 未配置')
      return
    }
    try {
      loading.value = true
      await callMutation(permanentDeleteFn, id)
      message.success('彻底删除成功')
      await onRefresh()
    } catch (e: any) {
      console.error('[useCrudActions] 彻底删除失败:', e)
      if (!e?.isBusiness) message.error('彻底删除失败，请重试')
    } finally {
      loading.value = false
    }
  }

  /**
   * 批量彻底删除
   */
  const handleBatchPermanentDelete = async (selectedKeys: number[]) => {
    if (loading.value) return
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
      await callMutation(batchPermanentDeleteFn, [...new Set(selectedKeys)])
      message.success('批量彻底删除成功')
      clearSelection?.()
      await onRefresh()
    } catch (e: any) {
      console.error('[useCrudActions] 批量彻底删除失败:', e)
      if (!e?.isBusiness) message.error('批量彻底删除失败，请重试')
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    handleDelete,
    handleBatchDelete,
    handleRestore,
    handleBatchRestore,
    handlePermanentDelete,
    handleBatchPermanentDelete
  }
}
