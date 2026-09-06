import { get, post } from './api'
import type { PageResponse } from './post'

export interface Achievement {
  code: string
  title: string
  progress: number
  target: number
  rewardPoints: number
  status: 'in_progress' | 'claimable' | 'claimed'
  claimedAt: string | null
}
export interface UserActivity {
  id: string
  type: 'comment' | 'favorite' | 'view' | 'checkin' | 'achievement' | 'register' | 'post'
  occurredAt: string
  title: string
  targetType: string | null
  targetId: number | null
}
export const getAchievements = async () => (await get<Achievement[]>('/user/achievements')).data
export const claimAchievement = async (code: string) => (await post<{ achievement: Achievement; points: number }>(`/user/achievements/${encodeURIComponent(code)}/claim`)).data
export const getActivities = async (page = 1) => (await get<PageResponse<UserActivity>>('/user/activities', { page, size: 10 })).data
