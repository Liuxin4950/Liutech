import { ref, computed } from 'vue'

/**
 * 通用顺序缓冲区。
 *
 * 解决问题：SSE 事件到达顺序不一定等于播放顺序。
 * 通过 seq 编号保证严格按序消费，支持乱序入队。
 *
 * 用法：
 *   const buffer = useSequencedBuffer<TtsAudioItem>()
 *   buffer.enqueue(item)      // 按 seq 入队
 *   buffer.shift()            // 取出 nextSeq 对应的项
 *   buffer.shiftBySeq(3)      // 取出指定 seq 的项
 */
export function useSequencedBuffer<T extends { seq: number }>() {
  const buffer = ref<Record<number, T>>({})
  const nextSeq = ref(1)
  const pendingCount = computed(() => Object.keys(buffer.value).length)

  /** 按 seq 入队。如果 seq 已存在会被覆盖。 */
  function enqueue(item: T) {
    if (!item || !Number.isSafeInteger(item.seq) || item.seq < nextSeq.value) return
    buffer.value[item.seq] = item
  }

  /** 按序取出下一项（seq == nextSeq）。没有则返回 null。 */
  function shift(): T | null {
    const next = buffer.value[nextSeq.value]
    if (!next) return null
    delete buffer.value[nextSeq.value]
    nextSeq.value++
    return next
  }

  /** 取出指定 seq 的项（用于跨缓冲区绑定）。没有则返回 null。 */
  function shiftBySeq(seq: number): T | null {
    const item = buffer.value[seq]
    if (!item) return null
    delete buffer.value[seq]
    // 推进游标，确保后续 shift 不会卡在已消费的 seq 上
    if (seq >= nextSeq.value) {
      nextSeq.value = seq + 1
    }
    return item
  }

  /** 清空缓冲区并重置游标。 */
  function clear() {
    buffer.value = {}
    nextSeq.value = 1
  }

  return { buffer, nextSeq, pendingCount, enqueue, shift, shiftBySeq, clear }
}
