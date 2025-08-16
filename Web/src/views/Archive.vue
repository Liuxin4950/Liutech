<template>
  <div class="content max-w-1200 mx-auto p-20">
    <!-- 页面标题 -->
    <div class="text-center mb-30">
      <h1 class="text-3xl font-bold mb-16">文章归档</h1>
      <p class="text-base text-gray-600">按时间浏览所有文章</p>
    </div>

    <!-- 统计信息 -->
    <div class="bg-card border rounded-lg p-20 mb-30 text-center">
      <div class="flex flex-jc gap-30">
        <div class="stat-item">
          <div class="text-2xl font-bold text-primary">{{ totalPosts }}</div>
          <div class="text-sm text-gray-600">篇文章</div>
        </div>
        <div class="stat-item">
          <div class="text-2xl font-bold text-primary">{{ monthCount }}</div>
          <div class="text-sm text-gray-600">个月份</div>
        </div>
        <div class="stat-item">
          <div class="text-2xl font-bold text-primary">{{ yearCount }}</div>
          <div class="text-sm text-gray-600">个年份</div>
        </div>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="text-center p-20">
      <div class="loading-spinner"></div>
      <p class="mt-12">正在加载归档数据...</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="text-center p-20">
      <p class="text-red-500 mb-12">{{ error }}</p>
      <button @click="loadArchiveData" class="btn btn-primary">重试</button>
    </div>

    <!-- 归档列表 -->
    <div v-else class="archive-list">
      <div v-for="yearData in groupedArchive" :key="yearData.year" class="year-group mb-30">
        <!-- 年份标题 -->
        <div class="year-header flex flex-ac gap-12 mb-20">
          <h2 class="text-2xl font-bold">{{ yearData.year }}</h2>
          <span class="text-sm text-gray-600">({{ yearData.totalPosts }}篇文章)</span>
        </div>

        <!-- 月份列表 -->
        <div class="month-list">
          <div v-for="monthData in yearData.months" :key="monthData.month" class="month-group mb-20">
            <!-- 月份标题 -->
            <div class="month-header flex flex-ac gap-12 mb-12 cursor-pointer" 
                 @click="toggleMonth(yearData.year, monthData.month)">
              <h3 class="text-lg font-semibold">{{ monthData.monthName }}</h3>
              <span class="text-sm text-gray-600">({{ monthData.posts.length }}篇)</span>
              <span class="ml-auto text-gray-400">
                {{ expandedMonths.has(`${yearData.year}-${monthData.month}`) ? '▼' : '▶' }}
              </span>
            </div>

            <!-- 文章列表 -->
            <div v-show="expandedMonths.has(`${yearData.year}-${monthData.month}`)" 
                 class="posts-list ml-20">
              <div v-for="post in monthData.posts" :key="post.id" 
                   class="post-item flex flex-ac gap-12 p-12 border-l-2 border-gray-200 hover:border-primary transition cursor-pointer"
                   @click="goToPost(post.id)">
                <div class="post-date text-sm text-gray-500 w-60 flex-shrink-0">
                  {{ formatDate(post.createdAt, 'MM-dd') }}
                </div>
                <div class="post-info flex-1">
                  <h4 class="post-title text-base font-medium hover:text-primary transition">{{ post.title }}</h4>
                  <div class="post-meta flex flex-ac gap-12 mt-4 text-xs text-gray-500">
                    <span>{{ post.category.name }}</span>
                    <span v-if="post.tags && post.tags.length > 0">
                      {{ post.tags.map(tag => tag.name).join(', ') }}
                    </span>
                    <span>{{ post.viewCount }} 次阅读</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && !error && archiveData.length === 0" class="text-center p-20">
      <div class="text-6xl mb-20">📝</div>
      <h3 class="text-xl font-semibold mb-12">暂无文章</h3>
      <p class="text-base text-gray-600 mb-20">还没有发布任何文章</p>
      <router-link to="/create" class="btn btn-primary">发布第一篇文章</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { PostService, type PostListItem } from '@/services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'

const router = useRouter()
const { handleAsync } = useErrorHandler()

// 响应式数据
const loading = ref(false)
const error = ref('')
const archiveData = ref<PostListItem[]>([])
const expandedMonths = ref(new Set<string>())

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
  if (expandedMonths.value.has(key)) {
    expandedMonths.value.delete(key)
  } else {
    expandedMonths.value.add(key)
  }
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
  router.push(`/post/${postId}`)
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
          expandedMonths.value.add(`${latestYear.year}-${month.month}`)
        })
      }
    }
  }, {
    onError: (err) => {
      error.value = '加载归档数据失败，请稍后重试'
      console.error('加载归档数据失败:', err)
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
</script>

<style scoped>
.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid var(--border-color);
  border-top: 4px solid var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.post-item:hover {
  background-color: var(--hover-bg-color, rgba(0, 0, 0, 0.02));
}

.month-header:hover {
  color: var(--primary-color);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .content {
    padding: 16px;
  }
  
  .posts-list {
    margin-left: 12px;
  }
  
  .post-date {
    width: 50px;
    font-size: 11px;
  }
  
  .stat-item {
    text-align: center;
  }
}
</style>