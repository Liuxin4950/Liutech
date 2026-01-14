<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
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
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'

// 统计数据
const dashboardStats = ref<DashboardStats | null>(null)
const loading = ref(true)
const currentDate = dayjs().format('YYYY年MM月DD日 dddd')

// ECharts 图表实例
const postTrendChart = ref<ECharts | null>(null)
const userTrendChart = ref<ECharts | null>(null)
const pieChart = ref<ECharts | null>(null)
const categoryChart = ref<ECharts | null>(null)

// 图表 DOM 引用
const postTrendChartRef = ref<HTMLElement | null>(null)
const userTrendChartRef = ref<HTMLElement | null>(null)
const pieChartRef = ref<HTMLElement | null>(null)
const categoryChartRef = ref<HTMLElement | null>(null)

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
 * 初始化文章趋势折线图
 */
const initPostTrendChart = () => {
  if (!postTrendChartRef.value || !dashboardStats.value) return

  if (postTrendChart.value) {
    postTrendChart.value.dispose()
  }

  postTrendChart.value = echarts.init(postTrendChartRef.value)

  const dates = dashboardStats.value.postTrend.map(item => dayjs(item.date).format('MM-DD'))
  const counts = dashboardStats.value.postTrend.map(item => item.count)

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: '#374151' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#6b7280', fontSize: 12 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: '#6b7280', fontSize: 12 },
      splitLine: { lineStyle: { color: '#f3f4f6', type: 'dashed' } }
    },
    series: [{
      name: '文章数',
      type: 'line',
      smooth: true,
      data: counts,
      lineStyle: {
        color: '#3b82f6',
        width: 3
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(59, 130, 246, 0.3)' },
          { offset: 1, color: 'rgba(59, 130, 246, 0.05)' }
        ])
      },
      itemStyle: { color: '#3b82f6' },
      emphasis: {
        itemStyle: {
          color: '#3b82f6',
          borderColor: '#fff',
          borderWidth: 2,
          shadowBlur: 10,
          shadowColor: 'rgba(59, 130, 246, 0.5)'
        }
      }
    }]
  }

  postTrendChart.value.setOption(option)
}

/**
 * 初始化用户增长柱状图
 */
const initUserTrendChart = () => {
  if (!userTrendChartRef.value || !dashboardStats.value) return

  if (userTrendChart.value) {
    userTrendChart.value.dispose()
  }

  userTrendChart.value = echarts.init(userTrendChartRef.value)

  const dates = dashboardStats.value.userTrend.map(item => dayjs(item.date).format('MM-DD'))
  const counts = dashboardStats.value.userTrend.map(item => item.count)

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: '#374151' },
      axisPointer: { type: 'shadow' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: { color: '#6b7280', fontSize: 12 }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: '#6b7280', fontSize: 12 },
      splitLine: { lineStyle: { color: '#f3f4f6', type: 'dashed' } }
    },
    series: [{
      name: '新增用户',
      type: 'bar',
      data: counts,
      barWidth: '50%',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#8b5cf6' },
          { offset: 1, color: '#7c3aed' }
        ]),
        borderRadius: [6, 6, 0, 0]
      },
      emphasis: {
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#a78bfa' },
            { offset: 1, color: '#8b5cf6' }
          ])
        }
      }
    }]
  }

  userTrendChart.value.setOption(option)
}

/**
 * 初始化文章状态分布饼图
 */
const initPieChart = () => {
  if (!pieChartRef.value || !dashboardStats.value) return

  if (pieChart.value) {
    pieChart.value.dispose()
  }

  pieChart.value = echarts.init(pieChartRef.value)

  const data = dashboardStats.value.postStatusDistribution.map(item => ({
    name: item.displayName,
    value: item.count
  }))

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: '#374151' },
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'horizontal',
      bottom: '0%',
      left: 'center',
      itemWidth: 12,
      itemHeight: 12,
      textStyle: { color: '#6b7280', fontSize: 12 }
    },
    series: [{
      name: '文章状态',
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      data: data,
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.1)'
        }
      },
      label: {
        show: true,
        formatter: '{d}%',
        color: '#6b7280',
        fontSize: 12
      },
      labelLine: {
        show: true,
        lineStyle: { color: '#e5e7eb' }
      }
    }],
    color: ['#52c41a', '#faad14', '#ff4d4f', '#1677ff']
  }

  pieChart.value.setOption(option)
}

/**
 * 初始化数据统计对比仪表盘
 */
const initCategoryChart = () => {
  if (!categoryChartRef.value || !dashboardStats.value) return

  if (categoryChart.value) {
    categoryChart.value.dispose()
  }

  categoryChart.value = echarts.init(categoryChartRef.value)

  const basicStats = dashboardStats.value.basicStats

  const option: echarts.EChartsOption = {
    tooltip: {
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: '#374151' }
    },
    series: [
      {
        type: 'gauge',
        center: ['30%', '55%'],
        radius: '60%',
        min: 0,
        max: Math.max(basicStats.postCount, 10),
        splitNumber: 5,
        axisLine: {
          lineStyle: {
            width: 10,
            color: [
              [0.3, '#67e0e3'],
              [0.7, '#37a2da'],
              [1, '#fd666d']
            ]
          }
        },
        pointer: {
          itemStyle: {
            color: 'auto'
          }
        },
        axisTick: {
          distance: -10,
          length: 8,
          lineStyle: {
            color: '#fff',
            width: 2
          }
        },
        splitLine: {
          distance: -10,
          length: 15,
          lineStyle: {
            color: '#fff',
            width: 3
          }
        },
        axisLabel: {
          color: 'auto',
          distance: 15,
          fontSize: 10
        },
        detail: {
          valueAnimation: true,
          formatter: '{value}',
          color: 'auto',
          fontSize: 16,
          offsetCenter: [0, '70%']
        },
        title: {
          offsetCenter: [0, '90%'],
          fontSize: 12,
          color: '#6b7280'
        },
        data: [{
          value: basicStats.postCount,
          name: '文章总数'
        }]
      },
      {
        type: 'gauge',
        center: ['70%', '55%'],
        radius: '60%',
        min: 0,
        max: Math.max(basicStats.userCount, 10),
        splitNumber: 5,
        axisLine: {
          lineStyle: {
            width: 10,
            color: [
              [0.3, '#67e0e3'],
              [0.7, '#37a2da'],
              [1, '#fd666d']
            ]
          }
        },
        pointer: {
          itemStyle: {
            color: 'auto'
          }
        },
        axisTick: {
          distance: -10,
          length: 8,
          lineStyle: {
            color: '#fff',
            width: 2
          }
        },
        splitLine: {
          distance: -10,
          length: 15,
          lineStyle: {
            color: '#fff',
            width: 3
          }
        },
        axisLabel: {
          color: 'auto',
          distance: 15,
          fontSize: 10
        },
        detail: {
          valueAnimation: true,
          formatter: '{value}',
          color: 'auto',
          fontSize: 16,
          offsetCenter: [0, '70%']
        },
        title: {
          offsetCenter: [0, '90%'],
          fontSize: 12,
          color: '#6b7280'
        },
        data: [{
          value: basicStats.userCount,
          name: '用户总数'
        }]
      }
    ]
  }

  categoryChart.value.setOption(option)
}

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

      // 初始化图表（等待 DOM 完全渲染）
      await nextTick()
      setTimeout(() => {
        initPostTrendChart()
        initUserTrendChart()
        initPieChart()
        initCategoryChart()
      }, 100)
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

// 窗口大小变化时重新调整图表大小
const handleResize = () => {
  postTrendChart.value?.resize()
  userTrendChart.value?.resize()
  pieChart.value?.resize()
  categoryChart.value?.resize()
}

onMounted(() => {
  loadDashboardStats()
  window.addEventListener('resize', handleResize)
})

// 组件卸载时清理
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  postTrendChart.value?.dispose()
  userTrendChart.value?.dispose()
  pieChart.value?.dispose()
  categoryChart.value?.dispose()
})
</script>

<template>
  <div class="p-24 dashboard-container">
    <!-- 顶部欢迎栏 -->
    <div class="flex flex-sb mb-24">
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
              <!-- 文章趋势折线图 -->
              <div class="chart-container">
                <div class="chart-header">
                  <span class="chart-title">近7日文章发布</span>
                  <span class="chart-total">{{ dashboardStats.postTrend.reduce((acc, cur) => acc + cur.count, 0) }} 篇</span>
                </div>
                <div ref="postTrendChartRef" class="echarts-box"></div>
              </div>
              <!-- 用户趋势柱状图 -->
              <div class="chart-container">
                <div class="chart-header">
                  <span class="chart-title">近7日新增用户</span>
                  <span class="chart-total">{{ dashboardStats.userTrend.reduce((acc, cur) => acc + cur.count, 0) }} 人</span>
                </div>
                <div ref="userTrendChartRef" class="echarts-box"></div>
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
          <!-- 文章状态分布饼图 -->
          <a-card :bordered="false" class="mb-24 content-card">
            <template #title>
              <div class="card-title">
                <RiseOutlined class="mr-8" />
                <span>文章分布</span>
              </div>
            </template>
            <div ref="pieChartRef" class="echarts-box-small"></div>
          </a-card>

          <!-- 核心指标仪表盘 -->
          <a-card :bordered="false" class="mb-24 content-card">
            <template #title>
              <div class="card-title">
                <RiseOutlined class="mr-8" />
                <span>核心指标</span>
              </div>
            </template>
            <div ref="categoryChartRef" class="echarts-box-small"></div>
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

.chart-container {
  background: #ffffff;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #f0f0f0;
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

/* ECharts 容器 */
.echarts-box {
  width: 100%;
  height: 240px;
  min-height: 240px;
}

.echarts-box-small {
  width: 100%;
  height: 280px;
  min-height: 280px;
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

  .echarts-box,
  .echarts-box-small {
    height: 220px;
  }
}
</style>
