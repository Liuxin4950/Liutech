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
  EditOutlined
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'

// 统计数据
const dashboardStats = ref<DashboardStats | null>(null)
const loading = ref(true)
const currentDate = dayjs().format('YYYY年MM月DD日 dddd')

// 统计卡片配置
const getStatConfig = (key: string) => {
  const configs: Record<string, { title: string; icon: any; color: string; bg: string }> = {
    postCount: { title: '文章总数', icon: FileTextOutlined, color: '#1677ff', bg: '#e6f4ff' },
    publishedPostCount: { title: '已发布', icon: CheckCircleOutlined, color: '#52c41a', bg: '#f6ffed' },
    draftPostCount: { title: '草稿箱', icon: EditOutlined, color: '#faad14', bg: '#fffbe6' },
    userCount: { title: '用户总数', icon: UserOutlined, color: '#722ed1', bg: '#f9f0ff' },
    categoryCount: { title: '分类总数', icon: FolderOutlined, color: '#13c2c2', bg: '#e6fffb' },
    tagCount: { title: '标签总数', icon: TagsOutlined, color: '#eb2f96', bg: '#fff0f6' },
    commentCount: { title: '评论总数', icon: CommentOutlined, color: '#fa541c', bg: '#fff2e8' },
    totalViews: { title: '总浏览量', icon: EyeOutlined, color: '#2f54eb', bg: '#f0f5ff' }
  }
  return configs[key] || configs.postCount
}

// 需要展示的统计卡片键值
const statKeys = ['postCount', 'publishedPostCount', 'userCount', 'totalViews']
const secondaryStatKeys = ['draftPostCount', 'categoryCount', 'tagCount', 'commentCount']

/**
 * 加载仪表盘统计数据
 */
const loadDashboardStats = async () => {
  try {
    loading.value = true
    const res = await getDashboardStats()
    // 修复：检查 code 为 200
    if (res.code === 200 && res.data) {
      dashboardStats.value = res.data
      
      // 修复：活跃用户按文章数降序排序
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
  <div class="dashboard-container">
    <!-- 顶部欢迎栏 -->
    <div class="welcome-section">
      <div class="welcome-text">
        <h1>早安，管理员</h1>
        <p class="date-text">{{ currentDate }} | 准备好开始一天的工作了吗？</p>
      </div>
      <a-button type="primary" shape="round" :loading="loading" @click="handleRefresh">
        <template #icon><ReloadOutlined /></template>
        刷新数据
      </a-button>
    </div>

    <div v-if="dashboardStats" class="dashboard-content">
      <!-- 核心指标卡片 -->
      <a-row :gutter="[24, 24]" class="mb-24">
        <a-col :xs="24" :sm="12" :md="6" v-for="key in statKeys" :key="key">
          <div class="stat-card main-stat">
            <div class="stat-icon-wrapper" :style="{ background: getStatConfig(key).bg, color: getStatConfig(key).color }">
              <component :is="getStatConfig(key).icon" />
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ dashboardStats.basicStats?.[key as keyof BasicStats] || 0 }}</div>
              <div class="stat-label">{{ getStatConfig(key).title }}</div>
            </div>
          </div>
        </a-col>
      </a-row>

      <!-- 次要指标卡片 (更紧凑) -->
      <a-row :gutter="[16, 16]" class="mb-24">
        <a-col :xs="12" :sm="6" :md="3" v-for="key in secondaryStatKeys" :key="key">
          <div class="stat-card mini-stat">
            <div class="mini-stat-header">
              <span class="mini-label">{{ getStatConfig(key).title }}</span>
              <component :is="getStatConfig(key).icon" :style="{ color: getStatConfig(key).color }" />
            </div>
            <div class="mini-value">{{ dashboardStats.basicStats?.[key as keyof BasicStats] || 0 }}</div>
          </div>
        </a-col>
      </a-row>

      <a-row :gutter="[24, 24]">
        <!-- 左侧栏：趋势与分布 -->
        <a-col :xs="24" :lg="16">
          <div class="content-card mb-24">
            <div class="card-header">
              <h3><RiseOutlined /> 访问与发布趋势</h3>
            </div>
            <div class="trend-charts">
              <!-- 文章发布趋势 -->
              <div class="chart-section">
                <h4>文章发布 (近7天)</h4>
                <div class="bar-chart">
                  <div v-for="item in dashboardStats.postTrend" :key="item.date" class="bar-item">
                    <div class="bar-track">
                      <div class="bar-fill post-bar" :style="{ height: `${Math.min(item.count * 20 + 5, 100)}%` }" :title="`${item.count} 篇`">
                        <span class="bar-value" v-if="item.count > 0">{{ item.count }}</span>
                      </div>
                    </div>
                    <div class="bar-date">{{ dayjs(item.date).format('MM-DD') }}</div>
                  </div>
                </div>
              </div>
              
              <!-- 用户增长趋势 -->
              <div class="chart-section">
                <h4>新增用户 (近7天)</h4>
                <div class="bar-chart">
                  <div v-for="item in dashboardStats.userTrend" :key="item.date" class="bar-item">
                    <div class="bar-track">
                      <div class="bar-fill user-bar" :style="{ height: `${Math.min(item.count * 30 + 5, 100)}%` }" :title="`${item.count} 人`">
                        <span class="bar-value" v-if="item.count > 0">{{ item.count }}</span>
                      </div>
                    </div>
                    <div class="bar-date">{{ dayjs(item.date).format('MM-DD') }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 热门文章 -->
          <div class="content-card">
            <div class="card-header">
              <h3>热门文章 TOP 5</h3>
            </div>
            <a-list :data-source="dashboardStats.topPosts" :split="false">
              <template #renderItem="{ item, index }">
                <a-list-item class="rank-item">
                  <div class="rank-index" :class="{ 'top-3': index < 3 }">{{ index + 1 }}</div>
                  <div class="rank-content">
                    <div class="rank-title">{{ item.title }}</div>
                    <div class="rank-meta">
                      <span><EyeOutlined /> {{ item.viewCount }}</span>
                      <span><CommentOutlined /> {{ item.commentCount }}</span>
                    </div>
                  </div>
                </a-list-item>
              </template>
            </a-list>
          </div>
        </a-col>

        <!-- 右侧栏：分布与活跃用户 -->
        <a-col :xs="24" :lg="8">
          <!-- 文章状态分布 -->
          <div class="content-card mb-24">
            <div class="card-header">
              <h3>文章状态分布</h3>
            </div>
            <div class="status-list">
              <div v-for="item in dashboardStats.postStatusDistribution" :key="item.status" class="status-row">
                <div class="status-info">
                  <span class="status-name">{{ item.displayName }}</span>
                  <span class="status-num">{{ item.count }}篇</span>
                </div>
                <a-progress 
                  :percent="item.percentage" 
                  :stroke-color="item.status === 'published' ? '#52c41a' : '#faad14'"
                  :show-info="false" 
                  size="small"
                />
              </div>
            </div>
          </div>

          <!-- 活跃用户 -->
          <div class="content-card">
            <div class="card-header">
              <h3>活跃创作者</h3>
            </div>
            <a-list :data-source="dashboardStats.topAuthors" :split="false">
              <template #renderItem="{ item, index }">
                <a-list-item class="author-item">
                  <a-avatar :style="{ backgroundColor: index < 3 ? 'var(--color-primary)' : '#d9d9d9' }">
                    {{ (item.nickname || item.username).substring(0, 1) }}
                  </a-avatar>
                  <div class="author-info">
                    <div class="author-name">{{ item.nickname || item.username }}</div>
                    <div class="author-stats">发布 {{ item.postCount }} 篇文章</div>
                  </div>
                  <div class="author-rank" v-if="index < 3">TOP {{ index + 1 }}</div>
                </a-list-item>
              </template>
            </a-list>
          </div>
        </a-col>
      </a-row>
    </div>

    <div v-else-if="!loading" class="empty-state">
      <a-empty description="暂无数据" />
    </div>
  </div>
</template>

<style scoped>
.dashboard-container {
  padding: 0;
  color: var(--text-main);
}

.welcome-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.welcome-text h1 {
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--text-main);
}

.date-text {
  color: var(--text-tertiary);
  font-size: 14px;
}

.mb-24 {
  margin-bottom: 24px;
}

/* 统计卡片样式 */
.stat-card {
  background: var(--bg-card);
  border-radius: 16px;
  padding: 24px;
  transition: all 0.3s ease;
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-xs);
  height: 100%;
}

.stat-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.main-stat {
  display: flex;
  align-items: center;
  gap: 20px;
}

.stat-icon-wrapper {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--text-main);
  font-family: 'Inter', sans-serif;
}

.stat-label {
  color: var(--text-secondary);
  font-size: 14px;
  margin-top: 4px;
}

/* 小统计卡片 */
.mini-stat {
  padding: 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.mini-stat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--text-secondary);
}

.mini-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-main);
}

/* 内容卡片通用样式 */
.content-card {
  background: var(--bg-card);
  border-radius: 16px;
  padding: 24px;
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-xs);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.card-header h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 趋势图表 */
.trend-charts {
  display: flex;
  gap: 32px;
  flex-wrap: wrap;
}

.chart-section {
  flex: 1;
  min-width: 280px;
}

.chart-section h4 {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 16px;
  font-weight: normal;
}

.bar-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 160px;
  padding-bottom: 24px; /* 为日期留出空间 */
}

.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  height: 100%;
  position: relative;
}

.bar-track {
  width: 8px;
  background: #f5f5f5;
  border-radius: 4px;
  height: 100%;
  position: relative;
  display: flex;
  align-items: flex-end;
}

.bar-fill {
  width: 100%;
  border-radius: 4px;
  transition: height 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

.post-bar { background: var(--color-primary); }
.user-bar { background: #722ed1; }

.bar-value {
  position: absolute;
  top: -20px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  color: var(--text-secondary);
}

.bar-date {
  position: absolute;
  bottom: -24px;
  font-size: 12px;
  color: var(--text-tertiary);
  transform: scale(0.9);
}

/* 排名列表 */
.rank-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--border-light);
}

.rank-item:last-child {
  border-bottom: none;
}

.rank-index {
  width: 24px;
  height: 24px;
  background: #f0f0f0;
  color: #8c8c8c;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 12px;
  margin-right: 16px;
}

.rank-index.top-3 {
  background: #fff1f0;
  color: #ff4d4f;
}

.rank-content {
  flex: 1;
  overflow: hidden;
}

.rank-title {
  font-size: 15px;
  color: var(--text-main);
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-meta {
  font-size: 12px;
  color: var(--text-tertiary);
  display: flex;
  gap: 16px;
}

/* 状态分布 */
.status-row {
  margin-bottom: 20px;
}

.status-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
}

.status-name { color: var(--text-secondary); }
.status-num { font-weight: 600; color: var(--text-main); }

/* 活跃作者 */
.author-item {
  display: flex;
  align-items: center;
  padding: 12px 0;
}

.author-info {
  margin-left: 12px;
  flex: 1;
}

.author-name {
  font-weight: 500;
  color: var(--text-main);
}

.author-stats {
  font-size: 12px;
  color: var(--text-tertiary);
}

.author-rank {
  font-size: 12px;
  color: var(--color-warning);
  font-weight: 600;
  background: #fffbe6;
  padding: 2px 8px;
  border-radius: 10px;
}

@media (max-width: 768px) {
  .welcome-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
  
  .trend-charts {
    flex-direction: column;
  }
  
  .stat-card.main-stat {
    padding: 16px;
  }
  
  .stat-icon-wrapper {
    width: 48px;
    height: 48px;
    font-size: 20px;
  }
  
  .stat-value {
    font-size: 24px;
  }
}
</style>
