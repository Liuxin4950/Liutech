<template>
  <div class="content">
    <!-- 加载状态 -->
    <div v-if="metadataLoading && !tagInfo" class="text-center p-20 text-sm">
      <div class="loading-spinner"></div>
      <p class="loading-text">正在加载标签信息...</p>
    </div>

    <!-- 文章列表部分 -->
    <div v-if="tagInfo" class="mb-20">
      <div class="flex flex-sb flex-ac mb-20 flex-fw gap-16">
        <h2 class="section-title text-2xl font-bold">相关文章</h2>
        <div class="flex flex-ac gap-12">
          <PostSortSelect :model-value="sortBy" @update:model-value="changeSort" />
        </div>
      </div>

      <!-- 文章列表（空状态由页面级统一展示，隐藏组件默认空态，避免双份空状态叠加） -->
      <ArticleList
        :posts="posts"
        :loading="loading"
        :error="error"
        :pagination="pagination"
        @post-click="goToPost"
        @page-change="changePage"
        @retry="loadPosts"
      />
    </div>

    <div v-if="metadataError" class="empty-text" role="alert">{{ metadataError }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onScopeDispose } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { TagService, type Tag } from '@/services/tag'
import { useBannerStore } from '@/stores/banner'
import { usePostListing } from '@/composables/usePostListing'
import bannerFallback from '@/assets/image/banner/banner0.png'
import ArticleList from '@/components/ArticleList.vue'
import PostSortSelect from '@/components/PostSortSelect.vue'

const route = useRoute()
const router = useRouter()
const bannerStore = useBannerStore()

const tagInfo = ref<Tag | null>(null)
const metadataLoading = ref(false)
const metadataError = ref('')
const tagId = computed(() => Number(route.params.id))
const { posts, loading, error, pagination, sortBy, loadPosts, changeSort, changePage } = usePostListing('tagId', tagId)
let metadataGeneration = 0
watch(tagId, async value => {
  const request = ++metadataGeneration
  tagInfo.value = null
  metadataError.value = ''
  metadataLoading.value = true
  try {
    if (!Number.isSafeInteger(value) || value <= 0) throw new Error('invalid id')
    const result = await TagService.getTagById(value)
    if (request !== metadataGeneration) return
    if (!result) throw new Error('not found')
    tagInfo.value = result
    route.meta.title = `${result.name} - 标签文章`
    bannerStore.setBanner({
      slides: [{ title: result.name, description: `${result.postCount || 0} 篇文章`, imageUrl: bannerFallback, sortOrder: 0, status: 1 }],
      badgeText: 'Tag', titleAs: 'h1', titleHighlight: '标签', mode: 'subheader'
    })
  } catch {
    if (request === metadataGeneration) metadataError.value = '标签信息暂时无法加载，请刷新重试'
  } finally {
    if (request === metadataGeneration) metadataLoading.value = false
  }
}, { immediate: true })
onScopeDispose(() => { metadataGeneration++ })
const goToPost = (postId: number) => router.push({ path: `/post/${postId}`, query: { from: 'tags', tagId: String(tagId.value), tagName: tagInfo.value?.name } })
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.section-title {
  color: var(--text-main);
  margin: 0;
}



@include respond(md) {
  .flex.flex-sb.flex-ac.mb-20.flex-fw {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}

.create-btn.outline {
  background: transparent;
  border: 1px solid var(--color-primary);
  color: var(--color-primary);
}

.create-btn.outline:hover {
  background: var(--color-primary);
  color: white;
}
</style>