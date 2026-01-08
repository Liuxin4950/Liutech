<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getDashboardStats, type DashboardStats, type BasicStats } from '@/services/dashboard'
import { message } from 'ant-design-vue'
import {
  FileTextOutlined,
  UserOutlined,
  TagsOutlined,
  FolderOutlined,
  EyeOutlined,
  CommentOutlined,
  ReloadOutlined,
  RiseOutlined,
  CheckCircleOutlined,
  EditOutlined,
  FireOutlined,
  TeamOutlined,
  BarChartOutlined
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'

// 统计数据
const dashboardStats = ref<DashboardStats | null>(null)
const loading = ref(true)
const currentDate = dayjs().format('YYYY年MM月DD日 dddd')

// 统计卡片配置 - 简化颜色，使用统一风格
const getStatConfig = (key: string) => {
  const configs: Record<string, { title: string; icon: any; color: string }> = {
    postCount: { title: '文章总数', icon: FileTextOutlined, color: '#1677ff' },
    publishedPostCount: { title: '已发布', icon: CheckCircleOutlined, color: '#52c41a' },
    draftPostCount: { title: '草稿箱', icon: EditOutlined, color: '#faad14' },
    userCount: { title: '用户总数', icon: UserOutlined, color: '#722ed1' },
    categoryCount: { title: '分类', icon: FolderOutlined, color: '#13c2c2' },
    tagCount: { title: '标签', icon: TagsOutlined, color: '#eb2f96' },
    commentCount: { title: '评论', icon: CommentOutlined, color: '#fa541c' },
    totalViews: { title: '总浏览', icon: EyeOutlined, color: '#2f54eb' }
  }
  return configs[key] || configs.postCount
}

// 核心指标
const statKeys = ['postCount', 'publishedPostCount', 'userCount', 'totalViews']
// 次要指标
const secondaryStatKeys = ['draftPostCount', 'categoryCount', 'tagCount', 'commentCount']

/**
 * 加载仪表盘统计数据
 */
const loadDashboardStats = async () => {
  try {
    loading.value = true
    const res = await getDashboardStats()
    if (res.code === 200 && res.data) {
      dashboardStats.value = res.data
      
      // 活跃用户按文章数降序排序
      if (dashboardStats.value.topAuthors) {
        dashboardStats.value.topAuthors.sort((a, b) => b.postCount - a.postCount)
      }
    } else {
      message.error(res.message || '加载统计数据失败')
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
    message.error('加载统计数据失败')
  } finally {
    loading.value = false
  }
}

const handleRefresh = () => {
  loadDashboardStats()
}

onMounted(() => {
  loadDashboardStats()
})
</script>

<template>
  <div class="p-24 dashboard-container">
    <!-- 顶部欢迎栏 -->
    <div class="flex justify-between items-center mb-24">
      <div>
        <h1 class="text-2xl font-bold text-gray-800 m-0">早安，管理员</h1>
        <p class="text-gray-500 mt-4 mb-0">{{ currentDate }} | 祝你今天心情愉快</p>
      </div>
      <a-button type="text" :loading="loading" @click="handleRefresh" class="refresh-btn">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <div v-if="dashboardStats" class="dashboard-content">
      <!-- 核心指标卡片 -->
      <a-row :gutter="24" class="mb-24">
        <a-col :xs="24" :sm="12" :md="6" v-for="key in statKeys" :key="key">
          <a-card :bordered="false" class="stat-card hover-shadow">
            <div class="flex justify-between items-start">
              <div>
                <p class="stat-label">{{ getStatConfig(key).title }}</p>
                <h2 class="stat-value">{{ dashboardStats.basicStats?.[key as keyof BasicStats] || 0 }}</h2>
              </div>
              <div class="stat-icon" :style="{ color: getStatConfig(key).color, background: `${getStatConfig(key).color}15` }">
                <component :is="getStatConfig(key).icon" />
              </div>
            </div>
          </a-card>
        </a-col>
      </a-row>

      <!-- 次要指标 -->
      <a-row :gutter="24" class="mb-24">
        <a-col :xs="12" :sm="6" :md="3" v-for="key in secondaryStatKeys" :key="key">
          <a-card :bordered="false" class="mini-stat-card hover-shadow" :body-style="{ padding: '16px' }">
            <div class="flex flex-col items-center justify-center">
              <component :is="getStatConfig(key).icon" class="mb-8" :style="{ color: getStatConfig(key).color, fontSize: '20px' }" />
              <span class="mini-stat-value">{{ dashboardStats.basicStats?.[key as keyof BasicStats] || 0 }}</span>
              <span class="mini-stat-label">{{ getStatConfig(key).title }}</span>
            </div>
          </a-card>
        </a-col>
      </a-row>

      <a-row :gutter="24">
        <!-- 左侧栏：趋势与内容 -->
        <a-col :xs="24" :lg="16">
          <!-- 趋势图表 -->
          <a-card :bordered="false" class="mb-24 content-card">
            <template #title>
              <div class="card-title">
                <BarChartOutlined class="mr-8" />
                <span>数据趋势</span>
              </div>
            </template>
            <div class="trend-grid">
              <!-- 文章趋势 -->
              <div class="chart-box">
                <div class="chart-header">
                  <span class="chart-title">近7日文章发布</span>
                  <span class="chart-total">{{ dashboardStats.postTrend.reduce((acc, cur) => acc + cur.count, 0) }} 篇</span>
                </div>
                <div class="simple-bar-chart">
                  <div v-for="item in dashboardStats.postTrend" :key="item.date" class="bar-col">
                    <div class="bar-bg">
                      <div class="bar-fill" :style="{ height: `${Math.min(item.count * 20, 100)}%` }"></div>
                    </div>
                    <span class="bar-label">{{ dayjs(item.date).format('MM-DD') }}</span>
                  </div>
                </div>
              </div>
              <!-- 用户趋势 -->
              <div class="chart-box">
                <div class="chart-header">
                  <span class="chart-title">近7日新增用户</span>
                  <span class="chart-total">{{ dashboardStats.userTrend.reduce((acc, cur) => acc + cur.count, 0) }} 人</span>
                </div>
                <div class="simple-bar-chart">
                  <div v-for="item in dashboardStats.userTrend" :key="item.date" class="bar-col">
                    <div class="bar-bg">
                      <div class="bar-fill user-fill" :style="{ height: `${Math.min(item.count * 30, 100)}%` }"></div>
                    </div>
                    <span class="bar-label">{{ dayjs(item.date).format('MM-DD') }}</span>
                  </div>
                </div>
              </div>
            </div>
          </a-card>

          <!-- 热门文章 -->
          <a-card :bordered="false" class="content-card">
            <template #title>
              <div class="card-title">
                <FireOutlined class="mr-8 text-red-500" />
                <span>热门文章 TOP 5</span>
              </div>
            </template>
            <a-list :data-source="dashboardStats.topPosts" :split="false">
              <template #renderItem="{ item, index }">
                <div class="rank-item hover:bg-gray-50 transition-all p-12 rounded-lg cursor-pointer mb-4">
                  <div class="flex items-center">
                    <div class="rank-badge" :class="index < 3 ? `rank-${index + 1}` : 'rank-normal'">
                      {{ index + 1 }}
                    </div>
                    <div class="flex-1 min-w-0 ml-16">
                      <div class="text-base font-medium text-gray-800 truncate">{{ item.title }}</div>
                      <div class="text-xs text-gray-400 mt-4 flex gap-16">
                        <span><EyeOutlined /> {{ item.viewCount }}</span>
                        <span><CommentOutlined /> {{ item.commentCount }}</span>
                        <span>{{ dayjs(item.createdAt).format('MM-DD') }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </template>
            </a-list>
          </a-card>
        </a-col>

        <!-- 右侧栏：分布与活跃用户 -->
        <a-col :xs="24" :lg="8">
          <!-- 状态分布 -->
          <a-card :bordered="false" class="mb-24 content-card">
            <template #title>
              <div class="card-title">
                <RiseOutlined class="mr-8" />
                <span>文章分布</span>
              </div>
            </template>
            <div class="flex flex-col gap-16">
              <div v-for="item in dashboardStats.postStatusDistribution" :key="item.status" class="distribution-item">
                <div class="flex justify-between mb-4">
                  <span class="text-sm text-gray-500">{{ item.displayName }}</span>
                  <span class="text-sm font-bold text-gray-700">{{ item.count }}</span>
                </div>
                <a-progress 
                  :percent="item.percentage" 
                  :stroke-color="item.status === 'published' ? '#52c41a' : '#faad14'"
                  :show-info="false" 
                  size="small"
                  :stroke-width="6"
                />
              </div>
            </div>
          </a-card>

          <!-- 活跃创作者 -->
          <a-card :bordered="false" class="content-card">
            <template #title>
              <div class="card-title">
                <TeamOutlined class="mr-8" />
                <span>活跃创作者</span>
              </div>
            </template>
            <a-list :data-source="dashboardStats.topAuthors" :split="false">
              <template #renderItem="{ item, index }">
                <div class="flex items-center py-12 border-b border-gray-100 last:border-0">
                  <a-avatar :size="40" :src="item.avatar" :style="{ backgroundColor: index < 3 ? '#e6f4ff' : '#f5f5f5', color: index < 3 ? '#1677ff' : '#999' }">
                    {{ (item.nickname || item.username).substring(0, 1).toUpperCase() }}
                  </a-avatar>
                  <div class="ml-12 flex-1">
                    <div class="font-medium text-gray-800">{{ item.nickname || item.username }}</div>
                    <div class="text-xs text-gray-400 mt-2">发布 {{ item.postCount }} 篇文章</div>
                  </div>
                  <div v-if="index < 3" class="text-xs font-bold text-blue-500 bg-blue-50 px-8 py-2 rounded-full">
                    TOP {{ index + 1 }}
                  </div>
                </div>
              </template>
            </a-list>
          </a-card>
        </a-col>
      </a-row>
    </div>

    <div v-else-if="!loading" class="flex justify-center items-center h-64">
      <a-empty description="暂无数据" />
    </div>
  </div>
</template>

<style scoped>
/* 通用样式 */
.dashboard-container { padding: 24px; }
.mb-24 { margin-bottom: 24px; }
.mb-8 { margin-bottom: 8px; }
.mt-4 { margin-top: 4px; }
.ml-12 { margin-left: 12px; }
.ml-16 { margin-left: 16px; }
.mr-8 { margin-right: 8px; }
.text-2xl { font-size: 1.5rem; }
.font-bold { font-weight: 700; }
.text-gray-800 { color: #1f2937; }
.text-gray-500 { color: #6b7280; }
.text-gray-400 { color: #9ca3af; }
.text-blue-500 { color: #3b82f6; }
.bg-blue-50 { background-color: #eff6ff; }
.hover-shadow { transition: box-shadow 0.3s; }
.hover-shadow:hover { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05); }

/* 卡片样式 */
.stat-card {
  border-radius: 12px;
  overflow: hidden;
}

.stat-label {
  font-size: 14px;
  color: #8c8c8c;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #262626;
  margin: 0;
  line-height: 1.2;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.mini-stat-card {
  border-radius: 12px;
}

.mini-stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #262626;
  line-height: 1.2;
}

.mini-stat-label {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 4px;
}

/* 内容卡片 */
.content-card {
  border-radius: 12px;
}

.card-title {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  color: #262626;
}

/* 图表样式 */
.trend-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
}

.chart-box {
  background: #f9fafb;
  border-radius: 12px;
  padding: 16px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 16px;
}

.chart-title {
  font-size: 14px;
  color: #6b7280;
}

.chart-total {
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}

.simple-bar-chart {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  height: 120px;
}

.bar-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  gap: 8px;
}

.bar-bg {
  width: 6px;
  height: 100px;
  background: #e5e7eb;
  border-radius: 3px;
  position: relative;
  overflow: hidden;
}

.bar-fill {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  background: #3b82f6;
  border-radius: 3px;
  transition: height 0.5s ease;
}

.user-fill {
  background: #8b5cf6;
}

.bar-label {
  font-size: 10px;
  color: #9ca3af;
}

/* 排名样式 */
.rank-badge {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}

.rank-1 { background: #fee2e2; color: #ef4444; }
.rank-2 { background: #ffedd5; color: #f97316; }
.rank-3 { background: #fef3c7; color: #f59e0b; }
.rank-normal { background: #f3f4f6; color: #6b7280; }

@media (max-width: 768px) {
  .trend-grid {
    grid-template-columns: 1fr;
  }
}
</style>
