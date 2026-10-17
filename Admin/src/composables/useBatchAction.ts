import { ref } from 'vue'
import { message } from 'ant-design-vue'

/**
 * useBatchAction 配置选项
 */
export interface UseBatchActionOptions {
  /** 批量执行函数（入参为去重后的 id 列表） */
  action: (ids: number[]) => Promise<any>
  /** 成功提示文案 */
  successMessage: string
  /** 空选择提示文案 */
  emptyMessage?: string
  /** 失败兜底提示文案（业务错误已由拦截器提示，这里只兜网络等非业务错误） */
  failMessage?: string
  /** 操作成功后刷新数据的回调 */
  onRefresh: () => void | Promise<void>
  /** 清空选择的回调 */
  clearSelection?: () => void
}

/**
 * 统一的领域批量动作组合式函数
 *
 * 封装删除/恢复之外的自定义批量操作（如批量启用、批量禁用）的通用骨架：
 * 空选择提示、操作锁、id 去重快照、成功提示、清空选择、刷新列表。
 * 删除/恢复/彻底删除等高危动作请继续使用 useCrudActions（含撤销机制）。
 */
export function useBatchAction(options: UseBatchActionOptions) {
  const { action, successMessage, onRefresh, clearSelection } = options
  const emptyMessage = options.emptyMessage ?? '请先选择要操作的记录'
  const failMessage = options.failMessage ?? '批量操作失败，请重试'

  /** 操作加载状态，防止重复点击 */
  const loading = ref(false)

  const run = async (selectedKeys: number[]) => {
    if (loading.value) return
    if (!selectedKeys.length) {
      message.warning(emptyMessage)
      return
    }
    // 复制 ID 列表：clearSelection 会清空 selectedKeys 引用
    const idsSnapshot = [...new Set(selectedKeys)]
    try {
      loading.value = true
      const result = await action(idsSnapshot)
      // 部分管理接口以 code=200/data=false 表达未完成写操作，不能当作成功
      if (result === false || result?.data === false) throw new Error('操作未完成')
      message.success(successMessage)
      clearSelection?.()
      await onRefresh()
    } catch (e: any) {
      if (!e?.isBusiness) message.error(failMessage)
    } finally {
      loading.value = false
    }
  }

  return { loading, run }
}
