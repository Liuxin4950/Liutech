<template>
  <div class="series-page content">
    <!-- 页面头部 -->
    <div class="card bg-card mb-16 shadow-sm">
      <div class="page-title">
        <span class="title-badge"><Icon name="book" size="12" /> Series</span>
        <h1 class="title-heading">文章<span class="title-highlight">系列</span></h1>
        <p class="title-desc">按系列浏览连载文章，从第一篇开始系统学习</p>
        <div class="title-meta">
          <span class="badge">共 {{ seriesList.length }} 个系列</span>
        </div>
      </div>
    </div>

    <!-- 系列网格 -->
    <div class="card shadow-sm mb-16">
      <div v-if="loading" class="loading-text text-sm">加载中...</div>
      <div v-else-if="error" class="loading-text text-primary text-sm">
        <p>{{ error }}</p>
        <button @click="loadSeries" class="bg-primary text-sm font-medium p-8 rounded transition mt-8">重试</button>
      </div>
      <div v-else-if="seriesList.length === 0" class="text-center p-20 flex flex-col flex-ac text-sm">
        <h3 class="text-base font-semibold">暂无系列</h3>
        <p class="text-muted text-sm mb-0">还没有创建任何文章系列</p>
        <img src="@/assets/image/扑到.png" alt="" class="fit-err">
      </div>
      <div v-else class="grid gap-20">
        <div v-for="s in seriesList" :key="s.id"
          class="series-card bg-card card cursor-pointer"
          @click="goToDetail(s.id)">
          <div v-if="s.coverImage" class="series-cover">
            <img :src="s.coverImage" :alt="s.name" />
          </div>
          <div class="flex flex-col gap-12">
            <h3 class="text-lg font-bold text-primary mb-0">{{ s.name }}</h3>
            <p v-if="s.description" class="text-muted text-sm mb-0 line-clamp-2 leading-relaxed">{{ s.description }}</p>
            <p v-else class="text-muted text-sm mb-0 series-desc-empty">暂无描述，点击查看该系列下的文章</p>
            <div class="flex flex-sb flex-ac">
              <span class="badge">{{ s.postCount || 0 }} 篇文章</span>
              <span class="series-link text-primary text-sm link flex flex-ac">
                查看系列 <Icon name="chevronRight" size="14" class="arrow-icon" />
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { SeriesService, type PostSeries } from '@/services/series'
import { useErrorHandler } from '@/composables/useErrorHandler'
import Icon from '@/components/Icon.vue'

const router = useRouter()
const { handleAsync } = useErrorHandler()

const seriesList = ref<PostSeries[]>([])
const loading = ref(false)
const error = ref('')

const loadSeries = async () => {
  await handleAsync(async () => {
    loading.value = true
    error.value = ''
    const result = await SeriesService.getSeriesList()
    seriesList.value = result || []
  }, {
    onError: () => { error.value = '加载系列失败，请稍后重试' },
    onFinally: () => { loading.value = false }
  })
}

const goToDetail = (id: number) => router.push(`/series-detail/${id}`)

onMounted(loadSeries)
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.series-page { padding: 20px; }

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  @include respond(md) { grid-template-columns: 1fr; }
}

.series-card {
  transition: all 0.2s ease;
  padding: 0;
  overflow: hidden;
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    .arrow-icon { transform: translateX(3px); }
  }
  > .flex { padding: 16px; }
}

.series-cover {
  height: 150px;
  overflow: hidden;
  img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.3s; }
}
.series-card:hover .series-cover img { transform: scale(1.05); }

.arrow-icon { transition: transform 0.2s ease; }

/* 查看系列链接与底部行（原全局 .gap-6/.pt-12 移入） */
.series-link { gap: 6px; }

.series-card .flex-sb { padding-top: 12px; }

/* 暂无描述（原全局 .opacity-60/.italic 移入） */
.series-desc-empty {
  opacity: 0.6;
  font-style: italic;
}

/* 空状态标题（原全局 .mb-8 移入） */
.text-center h3 { margin-bottom: 8px; }

.line-clamp-2 {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}
.leading-relaxed { line-height: 1.6; }

@include respond(md) {
  .series-page { padding: 15px; }
}
</style>
