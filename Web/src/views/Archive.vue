<template>
  <div class="content">
    <!-- 统计信息 -->
    <div class="stats-card card rounded-lg text-center">
      <div class="stats-row flex flex-jc">
        <div class="stat-item">
          <div class="stat-num text-2xl font-bold">{{ totalPosts }}</div>
          <div class="text-sm text-subtle">篇文章</div>
        </div>
        <div class="stat-item">
          <div class="stat-num text-2xl font-bold">{{ monthCount }}</div>
          <div class="text-sm text-subtle">个月份</div>
        </div>
        <div class="stat-item">
          <div class="stat-num text-2xl font-bold">{{ yearCount }}</div>
          <div class="text-sm text-subtle">个年份</div>
        </div>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading text-center p-20 text-sm">
      <div class="loading-spinner"></div>
      <p>正在加载归档数据...</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error text-center p-20 text-sm">
      <p class="mb-12">{{ error }}</p>
      <button @click="loadArchiveData" class="btn-primary">重试</button>
    </div>

    <!-- 归档列表 -->
    <div v-else class="archive-list">
      <div v-for="yearData in groupedArchive" :key="yearData.year" class="year-group">
        <!-- 年份标题 -->
        <div class="year-header flex flex-ac gap-12 mb-20">
          <h2 class="font-bold">{{ yearData.year }}</h2>
          <span class="text-sm text-subtle">({{ yearData.totalPosts }}篇文章)</span>
        </div>

        <!-- 月份列表 -->
        <div class="month-list">
          <div v-for="monthData in yearData.months" :key="monthData.month" class="month-group mb-20">
            <!-- 月份标题（展开态激活样式：主色 + 箭头旋转） -->
            <div class="month-header flex flex-ac gap-12 mb-12 cursor-pointer"
                 :class="{ active: expandedMonths[`${yearData.year}-${monthData.month}`] }"
                 @click="toggleMonth(yearData.year, monthData.month)">
              <h3 class="text-lg font-semibold">{{ monthData.monthName }}</h3>
              <span class="text-sm text-subtle">({{ monthData.posts.length }}篇)</span>
              <span class="toggle">▶</span>
            </div>

            <!-- 文章列表 -->
            <div v-show="expandedMonths[`${yearData.year}-${monthData.month}`]"
                 class="posts-list rounded-lg">
              <div v-for="post in monthData.posts" :key="post.id"
                   class="post-item flex flex-ac gap-12 transition cursor-pointer"
                   @click="goToPost(post.id)">
                <div class="post-date text-sm text-subtle">
                  {{ formatDate(post.createdAt, 'MM-dd') }}
                </div>
                <div class="post-info flex-1">
                  <h4 class="post-title text-lg font-medium transition">{{ post.title }}</h4>
                  <div class="post-meta flex flex-ac gap-12 text-xs text-subtle">
                    <span>{{ post.category?.name }}</span>
                    <span v-if="post.tags && post.tags.length > 0">
                      {{ post.tags.map(tag => tag.name).join(', ') }}
                    </span>
                    <span>{{ post.viewCount || 0 }} 次阅读</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && !error && archiveData.length === 0" class="archive-empty text-center p-20 flex flex-col flex-ac text-sm">
      <h3 class="font-semibold mb-12">暂无文章</h3>
      <p class="text-base text-subtle mb-20">还没有发布任何文章</p>
      <img src="@/assets/image/扑到.png" alt="" class="fit-err">
      <router-link to="/create" class="btn-primary">发布第一篇文章</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { PostService, type PostListItem } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useBannerStore } from '@/stores/banner'
import bannerFallback from '@/assets/image/banner/banner0.png'

const router = useRouter()
const { handleAsync } = useErrorHandler()

// 响应式数据
const loading = ref(false)
const error = ref('')
const archiveData = ref<PostListItem[]>([])
const expandedMonths = ref<Record<string, boolean>>({})

// 计算属性
const totalPosts = computed(() => archiveData.value.length)

const yearCount = computed(() => {
  const years = new Set(archiveData.value.map(post => new Date(post.createdAt).getFullYear()))
  return years.size
})

const monthCount = computed(() => {
  const months = new Set(archiveData.value.map(post => {
    const date = new Date(post.createdAt)
    return `${date.getFullYear()}-${date.getMonth() + 1}`
  }))
  return months.size
})

// 按年份和月份分组的归档数据
const groupedArchive = computed(() => {
  const groups: { [year: number]: { [month: number]: PostListItem[] } } = {}
  
  // 按年份和月份分组
  archiveData.value.forEach(post => {
    const date = new Date(post.createdAt)
    const year = date.getFullYear()
    const month = date.getMonth() + 1
    
    if (!groups[year]) {
      groups[year] = {}
    }
    if (!groups[year][month]) {
      groups[year][month] = []
    }
    groups[year][month].push(post)
  })
  
  // 转换为数组格式并排序
  const result = Object.keys(groups)
    .map(year => {
      const yearNum = parseInt(year)
      const months = Object.keys(groups[yearNum])
        .map(month => {
          const monthNum = parseInt(month)
          return {
            month: monthNum,
            monthName: getMonthName(monthNum),
            posts: groups[yearNum][monthNum].sort((a, b) => 
              new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
            )
          }
        })
        .sort((a, b) => b.month - a.month) // 月份倒序
      
      return {
        year: yearNum,
        months,
        totalPosts: months.reduce((sum, month) => sum + month.posts.length, 0)
      }
    })
    .sort((a, b) => b.year - a.year) // 年份倒序
  
  return result
})

// 获取月份名称
const getMonthName = (month: number): string => {
  const months = [
    '一月', '二月', '三月', '四月', '五月', '六月',
    '七月', '八月', '九月', '十月', '十一月', '十二月'
  ]
  return months[month - 1]
}

// 切换月份展开状态
const toggleMonth = (year: number, month: number) => {
  const key = `${year}-${month}`
  expandedMonths.value[key] = !expandedMonths.value[key]
}

// 格式化日期
const formatDate = (dateString: string, format: string = 'YYYY-MM-dd'): string => {
  const date = new Date(dateString)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  
  return format
    .replace('YYYY', year.toString())
    .replace('MM', month)
    .replace('dd', day)
}

// 跳转到文章详情
const goToPost = (postId: number) => {
  router.push(`/post/${postId}?from=archive`)
}

// 加载归档数据
const loadArchiveData = async () => {
  await handleAsync(async () => {
    loading.value = true
    error.value = ''
    
    // 获取所有已发布的文章，按创建时间倒序
    const response = await PostService.getPostList({
      page: 1,
      size: 1000, // 获取所有文章
      sortBy: 'latest'
    })
    
    archiveData.value = response.records
    
    // 默认展开最近的几个月
    if (groupedArchive.value.length > 0) {
      const latestYear = groupedArchive.value[0]
      if (latestYear.months.length > 0) {
        // 展开最新年份的前3个月
        latestYear.months.slice(0, 3).forEach(month => {
          expandedMonths.value[`${latestYear.year}-${month.month}`] = true
        })
      }
    }
  }, {
    onError: () => {
      error.value = '加载归档数据失败，请稍后重试'
    },
    onFinally: () => {
      loading.value = false
    }
  })
}

// 组件挂载时加载数据
onMounted(() => {
  loadArchiveData()
})

// Banner 页眉：文章归档（一级页面，500px hero 大横幅承担页面标题）
const bannerStore = useBannerStore()
bannerStore.setBanner({
  slides: [{
    title: '文章',
    description: '按时间浏览所有文章',
    imageUrl: bannerFallback,
    sortOrder: 0,
    status: 1
  }],
  badgeText: 'Archive',
  titleAs: 'h1',
  titleHighlight: '归档',
  mode: 'hero'
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;
.archive-list{
  margin-top: 10px;
}

/* 统计数字：深色标题色（数据数字不用主色，对齐设计规范） */
.stat-num {
  color: var(--text-title);
}

/* 年份分组标题 */
.year-header h2 {
  font-size: 1.25rem;
}

/* 月份标题：hover 与展开态均为主色，箭头旋转表达开合 */
.month-header {
  transition: color 0.2s ease;

  &:hover {
    color: var(--color-primary);
  }

  &.active {
    color: var(--color-primary);

    .toggle {
      transform: rotate(90deg);
    }
  }

  .toggle {
    margin-left: auto;
    color: var(--text-muted);
    font-size: 0.75rem;
    transition: transform 0.2s ease;
  }
}

/* 文章条目：对齐全局 .list-item 规范（10px 12px / 8px 圆角 / 底部分隔线 / hover bg-hover） */
.post-item {
  padding: 10px 12px;
  border-radius: 8px;
  border-bottom: 1px solid var(--border-light);

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: var(--bg-hover);
  }

  .post-date {
    width: 60px;
    flex-shrink: 0;
  }
}

.post-title:hover {
  color: var(--color-primary);
}

.stat-item {
  @include respond(md) {
    flex: 1;
  }
}

/* 统计卡片与行（原全局 .border/.gap-30 移入） */
.stats-card {
  border: 1px solid var(--border-base);
}

.stats-row {
  gap: 30px;

  @include respond(md) {
    flex-direction: column;
    gap: 16px;
  }
}

/* 加载 / 错误 / 空状态（原全局 .mt-12/.text-error/.text-xl 移入） */
.loading p {
  margin-top: 12px;
}

.error p {
  color: var(--color-error);
}

.archive-empty h3 {
  font-size: 1.25rem;
}



.post-item {
  @include respond(md) {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .post-date {
    @include respond(md) {
      width: auto;
    }
  }
}

.posts-list {
  margin-left: 20px;

  @include respond(md) {
    margin-left: 0;
  }
}

/* 年份分组与元信息（原全局 .mb-30/.mt-4 移入） */
.year-group {
  margin-bottom: 30px;
}

.post-meta {
  margin-top: 4px;
}
</style>