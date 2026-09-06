<template>
  <div class="category-posts content">
    <p v-if="metadataLoading" class="text-center p-20">正在加载分类信息...</p>
    <p v-else-if="metadataError" class="empty-text" role="alert">{{ metadataError }}</p>
    <div v-if="category" class="listing-header">
      <h2>相关文章</h2>
      <PostSortSelect :model-value="sortBy" @update:model-value="changeSort" />
    </div>

    <!-- 文章列表 -->
    <ArticleList
      v-if="category"
      :posts="posts"
      :loading="loading"
      :error="error"
      :pagination="pagination"
      @post-click="goToPost"
      @page-change="changePage"
      @retry="loadPosts"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onScopeDispose } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Category } from '@/services/category'
import { useCategoryStore } from '@/stores/category'
import { useBannerStore } from '@/stores/banner'
import { usePostListing } from '@/composables/usePostListing'
import bannerFallback from '@/assets/image/banner/banner0.png'
import ArticleList from '@/components/ArticleList.vue'
import PostSortSelect from '@/components/PostSortSelect.vue'

const route = useRoute()
const router = useRouter()
const bannerStore = useBannerStore()
const categoryStore = useCategoryStore()
const category = ref<Category | null>(null)
const metadataLoading = ref(false)
const metadataError = ref('')
const categoryId = computed(() => Number(route.params.id))
const { posts, loading, error, pagination, sortBy, loadPosts, changeSort, changePage } = usePostListing('categoryId', categoryId)
let metadataGeneration = 0
watch(categoryId, async value => {
  const request = ++metadataGeneration
  category.value = null
  metadataError.value = ''
  metadataLoading.value = true
  try {
    if (!Number.isSafeInteger(value) || value <= 0) throw new Error('invalid id')
    const result = await categoryStore.fetchCategoryById(value)
    if (request !== metadataGeneration) return
    if (!result) throw new Error('not found')
    category.value = result
    route.meta.title = `${result.name} - 分类文章`
    bannerStore.setBanner({
      slides: [{ title: result.name, description: `${result.postCount || 0} 篇文章`, imageUrl: bannerFallback, sortOrder: 0, status: 1 }],
      badgeText: 'Category', titleAs: 'h1', titleHighlight: '分类', mode: 'subheader'
    })
  } catch {
    if (request === metadataGeneration) metadataError.value = '分类信息暂时无法加载，请刷新重试'
  } finally {
    if (request === metadataGeneration) metadataLoading.value = false
  }
}, { immediate: true })
onScopeDispose(() => { metadataGeneration++ })
const goToPost = (postId: number) => router.push({ path: `/post/${postId}`, query: { from: 'categories', categoryId: String(categoryId.value), categoryName: category.value?.name } })
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;
.listing-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }
.listing-header h2 { font-size: 20px; margin: 0; }
.category-posts { padding: 20px; }

.create-btn.outline {
  background: transparent;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
}

.create-btn.outline:hover {
  background: var(--color-primary);
  color: white;
}

@include respond(md) {
  .category-posts { padding: 15px; }
}
</style>