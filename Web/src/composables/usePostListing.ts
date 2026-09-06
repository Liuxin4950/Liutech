import { computed, onScopeDispose, ref, watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PostService, type PostListItem } from '@/services/post'

/** 分类/标签的查询参数是列表状态来源；每次查询只允许最新请求提交结果。 */
export function usePostListing(filter: 'categoryId' | 'tagId', id: Ref<number>) {
  const route = useRoute()
  const router = useRouter()
  const posts = ref<PostListItem[]>([])
  const loading = ref(false)
  const error = ref('')
  const total = ref(0)
  const page = computed(() => {
    const value = Number(route.query.page)
    return Number.isSafeInteger(value) && value > 0 ? value : 1
  })
  const sortBy = computed<'latest' | 'popular'>(() => route.query.sort === 'popular' ? 'popular' : 'latest')
  const pagination = computed(() => ({ current: page.value, size: 10, total: total.value, pages: Math.ceil(total.value / 10) }))
  let generation = 0

  const loadPosts = async () => {
    const request = ++generation
    posts.value = []
    total.value = 0
    error.value = ''
    if (!Number.isSafeInteger(id.value) || id.value <= 0) {
      loading.value = false
      error.value = '无效的分类或标签'
      return
    }
    loading.value = true
    try {
      const result = await PostService.getPostList({ [filter]: id.value, page: page.value, size: 10, sortBy: sortBy.value })
      if (request !== generation) return
      posts.value = result.records
      total.value = result.total
    } catch {
      if (request === generation) error.value = '加载文章失败，请稍后重试'
    } finally {
      if (request === generation) loading.value = false
    }
  }
  const changeSort = (value: 'latest' | 'popular') => router.push({ query: { ...route.query, sort: value, page: undefined } })
  const changePage = (value: number) => {
    if (value < 1 || value > pagination.value.pages) return
    void router.push({ query: { ...route.query, page: value === 1 ? undefined : String(value) } })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
  watch([id, page, sortBy], loadPosts, { immediate: true })
  onScopeDispose(() => { generation++ })
  return { posts, loading, error, total, pagination, sortBy, loadPosts, changeSort, changePage }
}
