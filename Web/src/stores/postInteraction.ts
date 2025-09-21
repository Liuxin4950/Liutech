/**
 * 文章交互状态事件存储（轻量级）
 * 用途：跨组件（如 AiChat 与 PostDetail）在点赞/收藏后同步状态
 * 作者：刘鑫
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface LikeEvent { postId: number; isLiked: boolean }
export interface FavoriteEvent { postId: number; isFavorited: boolean }

export const usePostInteractionStore = defineStore('postInteraction', () => {
  // 最近一次的点赞/收藏事件（事件风格，供订阅方 watch 即时响应）
  const lastLikeEvent = ref<LikeEvent | null>(null)
  const lastFavoriteEvent = ref<FavoriteEvent | null>(null)

  // 触发点赞事件（外部已完成接口调用成功后再触发）
  const toggleLike = (postId: number, isLiked?: boolean) => {
    const prev = lastLikeEvent.value?.postId === postId ? lastLikeEvent.value?.isLiked : false
    const nextVal = typeof isLiked === 'boolean' ? isLiked : !prev
    lastLikeEvent.value = { postId, isLiked: nextVal }
  }

  // 触发收藏事件（外部已完成接口调用成功后再触发）
  const toggleFavorite = (postId: number, isFavorited?: boolean) => {
    const prev = lastFavoriteEvent.value?.postId === postId ? lastFavoriteEvent.value?.isFavorited : false
    const nextVal = typeof isFavorited === 'boolean' ? isFavorited : !prev
    lastFavoriteEvent.value = { postId, isFavorited: nextVal }
  }

  return {
    lastLikeEvent,
    lastFavoriteEvent,
    toggleLike,
    toggleFavorite
  }
})