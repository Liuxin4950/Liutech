import { get } from './api'
import type { PageResponse } from './post'

export interface PurchasedResource {
  id: number
  resourceId: number
  resourceName: string
  resourceType: 'file' | 'link' | 'both' | null
  pointsUsed: number
  purchasedAt: string
  postId: number | null
  postTitle: string | null
  available: boolean
}

export async function getPurchasedResources(page = 1, size = 5): Promise<PageResponse<PurchasedResource>> {
  return (await get<PageResponse<PurchasedResource>>('/resource/purchases', { page, size })).data
}
