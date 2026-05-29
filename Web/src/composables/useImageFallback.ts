import errImg from '@/assets/image/err.png'

export { errImg }

export function handleImageError(e: Event) {
  const img = e.target as HTMLImageElement
  img.src = errImg
}
