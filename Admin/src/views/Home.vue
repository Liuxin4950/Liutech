<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
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
  CheckCircleOutlined,
  EditOutlined,
  FireOutlined,
  TeamOutlined,
  BarChartOutlined,
  PieChartOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  MinusOutlined,
  ArrowRightOutlined,
  CodeOutlined,
  InfoCircleOutlined,
  ClockCircleOutlined,
  RocketOutlined,
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import * as echarts from 'echarts'
import type { ECharts, EChartsOption } from 'echarts'
import { useSettingsStore } from '@/stores/settings'

const router = useRouter()
const settings = useSettingsStore()

const dashboardStats = ref<DashboardStats | null>(null)
const loading = ref(true)
const currentDate = dayjs().format('YYYY年MM月DD日 dddd')
const greeting = computed(() => {
  const h = dayjs().hour()
  if (h < 6) return '深夜好'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const trendChart = ref<ECharts | null>(null)
const pieChart = ref<ECharts | null>(null)
const trendChartRef = ref<HTMLElement | null>(null)
const pieChartRef = ref<HTMLElement | null>(null)

/** 主 KPI 卡 sparkline 实例，按 key 存 */
const sparklines = new Map<string, ECharts>()
const sparklineRefs = ref<Record<string, HTMLElement | null>>({})

function cssVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

/**
 * 主 KPI 配置：value / label / icon / 走图表色板 / sparkline 数据源函数
 * 增量算法：把 trend 数组前后各半求和对比，得到"近半周期 vs 前半周期"的百分比变化
 */
type KpiKey = 'posts' | 'users' | 'views' | 'comments'
const kpis = computed(() => {
  const s = dashboardStats.value
  if (!s) return [] as any[]
  const trend7 = s.postTrend || []
  const userTrend7 = s.userTrend || []
  const half = Math.floor(trend7.length / 2)
  const sum = (arr: any[], slice?: [number, number]) => {
    const list = slice ? arr.slice(slice[0], slice[1]) : arr
    return list.reduce((acc, cur) => acc + (cur?.count || 0), 0)
  }
  const delta = (arr: any[]) => {
    if (arr.length < 4) return 0
    const first = sum(arr, [0, half])
    const second = sum(arr, [half, arr.length])
    if (first === 0) return second > 0 ? 100 : 0
    return Math.round(((second - first) / first) * 100)
  }
  return [
    {
      key: 'posts' as KpiKey,
      label: '文章总数',
      value: s.basicStats.postCount,
      icon: FileTextOutlined,
      token: '--lt-color-chart-1',
      trend: trend7.map((t) => t.count),
      delta: delta(trend7),
      deltaLabel: '近 7 日新增趋势',
      to: '/posts',
    },
    {
      key: 'users' as KpiKey,
      label: '用户总数',
      value: s.basicStats.userCount,
      icon: UserOutlined,
      token: '--lt-color-chart-4',
      trend: userTrend7.map((t) => t.count),
      delta: delta(userTrend7),
      deltaLabel: '近 7 日注册趋势',
      to: '/users',
    },
    {
      key: 'views' as KpiKey,
      label: '总浏览量',
      value: s.basicStats.totalViews,
      icon: EyeOutlined,
      token: '--lt-color-chart-8',
      trend: (s.topPosts || []).slice(0, 7).map((p) => p.viewCount),
      delta: 0,
      deltaLabel: 'TOP 文章浏览分布',
      to: '/posts',
    },
    {
      key: 'comments' as KpiKey,
      label: '评论总数',
      value: s.basicStats.commentCount,
      icon: CommentOutlined,
      token: '--lt-color-chart-7',
      trend: (s.topPosts || []).slice(0, 7).map((p) => p.commentCount),
      delta: 0,
      deltaLabel: 'TOP 文章评论分布',
      to: '/comments',
    },
  ]
})

const secondaryStats = computed(() => {
  const s = dashboardStats.value
  if (!s) return [] as any[]
  return [
    { label: '已发布', value: s.basicStats.publishedPostCount, icon: CheckCircleOutlined, token: '--lt-color-chart-2', to: '/posts' },
    { label: '草稿箱', value: s.basicStats.draftPostCount, icon: EditOutlined, token: '--lt-color-chart-3', to: '/posts' },
    { label: '分类', value: s.basicStats.categoryCount, icon: FolderOutlined, token: '--lt-color-chart-5', to: '/categories' },
    { label: '标签', value: s.basicStats.tagCount, icon: TagsOutlined, token: '--lt-color-chart-6', to: '/tags' },
  ]
})

function goto(path: string) {
  if (path) router.push(path)
}

function chartCommon() {
  return {
    textSecondary: cssVar('--lt-color-text-secondary'),
    textTertiary: cssVar('--lt-color-text-tertiary'),
    borderSecondary: cssVar('--lt-color-border-secondary'),
    bgElevated: cssVar('--lt-color-bg-elevated'),
  }
}

/** 渲染 KPI 卡片的迷你 sparkline */
function renderSparklines() {
  for (const [key, chart] of sparklines) {
    if (!kpis.value.find((k) => k.key === key)) {
      chart.dispose()
      sparklines.delete(key)
    }
  }
  for (const kpi of kpis.value) {
    const el = sparklineRefs.value[kpi.key]
    if (!el) continue
    let chart = sparklines.get(kpi.key)
    if (!chart) {
      chart = echarts.init(el)
      sparklines.set(kpi.key, chart)
    }
    const color = cssVar(kpi.token)
    chart.setOption({
      grid: { top: 4, right: 0, bottom: 0, left: 0 },
      xAxis: { type: 'category', show: false, boundaryGap: false, data: kpi.trend.map((_: any, i: number) => i) },
      yAxis: { type: 'value', show: false },
      tooltip: { show: false },
      series: [{
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: kpi.trend,
        lineStyle: { color, width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: color + '4d' },
            { offset: 1, color: color + '00' },
          ]),
        },
      }],
    })
  }
}

function initTrendChart() {
  if (!trendChartRef.value || !dashboardStats.value) return
  trendChart.value?.dispose()
  trendChart.value = echarts.init(trendChartRef.value)

  const c = chartCommon()
  const s = dashboardStats.value
  const dates = s.postTrend.map((it) => dayjs(it.date).format('MM-DD'))
  const postCounts = s.postTrend.map((it) => it.count)
  const userCounts = s.userTrend.map((it) => it.count)
  const chartBrand = cssVar('--lt-color-chart-1')
  const chartUser = cssVar('--lt-color-chart-4')

  const option: EChartsOption = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: c.bgElevated,
      borderColor: c.borderSecondary,
      textStyle: { color: c.textSecondary },
    },
    legend: {
      data: ['文章发布', '用户注册'],
      right: 0,
      top: 0,
      textStyle: { color: c.textTertiary, fontSize: 12 },
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
    },
    grid: { left: 8, right: 8, bottom: 8, top: 32, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: c.borderSecondary } },
      axisLabel: { color: c.textTertiary, fontSize: 11 },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: c.textTertiary, fontSize: 11 },
      splitLine: { lineStyle: { color: c.borderSecondary, type: 'dashed' } },
    },
    series: [
      {
        name: '文章发布',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: postCounts,
        lineStyle: { color: chartBrand, width: 2 },
        itemStyle: { color: chartBrand },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: chartBrand + '33' },
            { offset: 1, color: chartBrand + '00' },
          ]),
        },
      },
      {
        name: '用户注册',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: userCounts,
        lineStyle: { color: chartUser, width: 2 },
        itemStyle: { color: chartUser },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: chartUser + '33' },
            { offset: 1, color: chartUser + '00' },
          ]),
        },
      },
    ],
  }
  trendChart.value.setOption(option)
}

function initPieChart() {
  if (!pieChartRef.value || !dashboardStats.value) return
  pieChart.value?.dispose()
  pieChart.value = echarts.init(pieChartRef.value)

  const c = chartCommon()
  const data = dashboardStats.value.postStatusDistribution.map((it) => ({
    name: it.displayName,
    value: it.count,
  }))
  const palette = [
    cssVar('--lt-color-chart-2'),
    cssVar('--lt-color-chart-3'),
    cssVar('--lt-color-chart-7'),
    cssVar('--lt-color-chart-1'),
  ]

  const option: EChartsOption = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
      backgroundColor: c.bgElevated,
      borderColor: c.borderSecondary,
      textStyle: { color: c.textSecondary },
    },
    legend: {
      orient: 'vertical',
      right: 8,
      top: 'middle',
      itemWidth: 8,
      itemHeight: 8,
      icon: 'circle',
      textStyle: { color: c.textTertiary, fontSize: 12 },
    },
    color: palette,
    series: [{
      name: '文章状态',
      type: 'pie',
      radius: ['50%', '75%'],
      center: ['38%', '50%'],
      data,
      label: { show: false },
      labelLine: { show: false },
      emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.1)' } },
    }],
  }
  pieChart.value.setOption(option)
}

function renderAll() {
  initTrendChart()
  initPieChart()
  nextTick(renderSparklines)
}

async function loadDashboardStats() {
  try {
    loading.value = true
    const res = await getDashboardStats()
    if (res.code === 200 && res.data) {
      dashboardStats.value = res.data
      if (dashboardStats.value.topAuthors) {
        dashboardStats.value.topAuthors.sort((a, b) => b.postCount - a.postCount)
      }
      await nextTick()
      setTimeout(renderAll, 100)
    } else {
      message.error(res.message || '加载统计数据失败')
    }
  } catch {
    message.error('加载统计数据失败')
  } finally {
    loading.value = false
  }
}

const handleRefresh = () => loadDashboardStats()

let unwatch: (() => void) | null = null
onMounted(() => {
  loadDashboardStats()
  window.addEventListener('resize', handleResize)
  unwatch = watch(() => settings.isDark, () => {
    if (dashboardStats.value) setTimeout(renderAll, 60)
  })
})

const handleResize = () => {
  trendChart.value?.resize()
  pieChart.value?.resize()
  for (const s of sparklines.values()) s.resize()
}

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  unwatch?.()
  trendChart.value?.dispose()
  pieChart.value?.dispose()
  for (const s of sparklines.values()) s.dispose()
  sparklines.clear()
})

/** 排名徽章 preset：金银铜 + 默认 */
const rankPreset = (index: number) => ['gold', 'silver', 'bronze', 'default'][index >= 3 ? 3 : index]

/** 计算 TOP 文章浏览量占比（相对于最热的一篇） */
const topPostsWithRatio = computed(() => {
  const list = dashboardStats.value?.topPosts || []
  const max = Math.max(...list.map((p) => p.viewCount), 1)
  return list.map((p) => ({ ...p, ratio: Math.round((p.viewCount / max) * 100) }))
})

/** 变化方向图标与色板 */
function deltaClass(v: number) {
  if (v > 0) return 'lt-delta--up'
  if (v < 0) return 'lt-delta--down'
  return 'lt-delta--flat'
}

/** 技术栈：分组 + 语义色（跟随图表色板，主题切换自动适配） */
const techStack = [
  { group: '前端', items: [
    { name: 'Vue', version: __VUE_VERSION__, tone: 'chart-2' },
    { name: 'TypeScript', version: __TYPESCRIPT_VERSION__, tone: 'chart-1' },
    { name: 'Ant Design Vue', version: __ANTDV_VERSION__, tone: 'chart-1' },
    { name: 'Vite', version: __VITE_VERSION__, tone: 'chart-4' },
    { name: 'ECharts', version: __ECHARTS_VERSION__, tone: 'chart-6' },
    { name: 'Pinia', version: '', tone: 'chart-3' },
  ]},
  { group: '后端', items: [
    { name: 'Spring Boot', version: '3.5.6', tone: 'chart-2' },
    { name: 'MyBatis-Plus', version: '', tone: 'chart-2' },
    { name: 'MySQL', version: '8.0', tone: 'chart-8' },
    { name: 'Redis', version: '', tone: 'chart-7' },
    { name: 'JWT', version: '', tone: 'chart-4' },
  ]},
  { group: 'AI & 部署', items: [
    { name: 'Spring AI', version: '', tone: 'chart-6' },
    { name: 'TinyMCE', version: '7.9', tone: 'chart-3' },
    { name: 'Docker', version: '', tone: 'chart-1' },
    { name: 'Nginx', version: '', tone: 'chart-2' },
  ]},
]

/** 系统信息条目 */
const systemInfo = computed(() => {
  const buildTime = dayjs(__BUILD_TIME__)
  return [
    { icon: RocketOutlined, label: '版本', value: `v${__APP_VERSION__}` },
    { icon: ClockCircleOutlined, label: '构建时间', value: buildTime.format('YYYY-MM-DD HH:mm') },
    { icon: CodeOutlined, label: '构建工具', value: `Vite ${__VITE_VERSION__}` },
    { icon: InfoCircleOutlined, label: '当前时间', value: dayjs().format('YYYY-MM-DD HH:mm') },
  ]
})
</script>

<template>
  <div class="lt-home">
    <!-- 顶部欢迎栏 -->
    <header class="lt-home__hero">
      <div>
        <h1 class="lt-home__title">{{ greeting }}，管理员</h1>
        <p class="lt-home__subtitle">{{ currentDate }} · 系统运行正常</p>
      </div>
      <a-space>
        <a-tooltip title="刷新数据">
          <a-button :loading="loading" @click="handleRefresh">
            <template #icon><ReloadOutlined /></template>
            刷新
          </a-button>
        </a-tooltip>
      </a-space>
    </header>

    <a-spin :spinning="loading && !dashboardStats">
      <!-- 首次加载：骨架屏（保持布局稳定，避免整页跳动） -->
      <div v-if="loading && !dashboardStats" class="lt-home__body">
        <a-row :gutter="[12, 12]" class="lt-home__row">
          <a-col v-for="i in 4" :key="`sk-kpi-${i}`" :xs="24" :sm="12" :lg="6">
            <a-card :bordered="false" :body-style="{ padding: '16px' }">
              <div class="lt-skel lt-skel--label" />
              <div class="lt-skel lt-skel--value" />
              <div class="lt-skel lt-skel--foot" />
              <div class="lt-skel lt-skel--spark" />
            </a-card>
          </a-col>
        </a-row>
        <a-row :gutter="[12, 12]" class="lt-home__row">
          <a-col v-for="i in 4" :key="`sk-mini-${i}`" :xs="12" :sm="6">
            <a-card :bordered="false" :body-style="{ padding: '12px 16px' }">
              <div class="lt-skel lt-skel--mini-value" />
              <div class="lt-skel lt-skel--mini-label" />
            </a-card>
          </a-col>
        </a-row>
        <a-row :gutter="[12, 12]" class="lt-home__row">
          <a-col :xs="24" :lg="16">
            <a-card :bordered="false">
              <div class="lt-skel lt-skel--chart" />
            </a-card>
          </a-col>
          <a-col :xs="24" :lg="8">
            <a-card :bordered="false">
              <div class="lt-skel lt-skel--chart" />
            </a-card>
          </a-col>
        </a-row>
      </div>

      <div v-if="dashboardStats" class="lt-home__body">
        <!-- 主 KPI 卡（4 张） -->
        <a-row :gutter="[12, 12]" class="lt-home__row">
          <a-col v-for="kpi in kpis" :key="kpi.key" :xs="24" :sm="12" :lg="6">
            <a-card
              :bordered="false"
              class="lt-kpi lt-kpi--clickable"
              :body-style="{ padding: '16px' }"
              @click="goto(kpi.to)"
            >
              <div class="lt-kpi__header">
                <span class="lt-kpi__label">{{ kpi.label }}</span>
                <span class="lt-kpi__icon" :style="{ color: `var(${kpi.token})`, background: `color-mix(in srgb, var(${kpi.token}) 12%, transparent)` }">
                  <component :is="kpi.icon" />
                </span>
              </div>
              <div class="lt-kpi__value">{{ kpi.value.toLocaleString() }}</div>
              <div class="lt-kpi__footer">
                <span class="lt-delta" :class="deltaClass(kpi.delta)">
                  <ArrowUpOutlined v-if="kpi.delta > 0" />
                  <ArrowDownOutlined v-else-if="kpi.delta < 0" />
                  <MinusOutlined v-else />
                  {{ kpi.delta !== 0 ? `${Math.abs(kpi.delta)}%` : '持平' }}
                </span>
                <span class="lt-kpi__foot-label">{{ kpi.deltaLabel }}</span>
                <ArrowRightOutlined class="lt-kpi__goto" />
              </div>
              <div
                :ref="(el) => (sparklineRefs[kpi.key] = el as HTMLElement)"
                class="lt-kpi__spark"
              />
            </a-card>
          </a-col>
        </a-row>

        <!-- 次要统计（4 张紧凑，可点击） -->
        <a-row :gutter="[12, 12]" class="lt-home__row">
          <a-col v-for="stat in secondaryStats" :key="stat.label" :xs="12" :sm="6">
            <a-card
              :bordered="false"
              class="lt-mini lt-mini--clickable"
              :body-style="{ padding: '12px 16px' }"
              @click="goto(stat.to)"
            >
              <div class="lt-mini__inner">
                <div class="lt-mini__icon" :style="{ color: `var(${stat.token})` }">
                  <component :is="stat.icon" />
                </div>
                <div class="lt-mini__body">
                  <div class="lt-mini__value">{{ stat.value.toLocaleString() }}</div>
                  <div class="lt-mini__label">{{ stat.label }}</div>
                </div>
              </div>
            </a-card>
          </a-col>
        </a-row>

        <!-- 图表区：左侧双趋势 + 右侧饼图 -->
        <a-row :gutter="[12, 12]" class="lt-home__row">
          <a-col :xs="24" :lg="16">
            <a-card :bordered="false">
              <template #title>
                <span class="lt-card-title"><BarChartOutlined /> 近 7 日数据趋势</span>
              </template>
              <template #extra>
                <a-tag color="processing" :bordered="false">实时</a-tag>
              </template>
              <div ref="trendChartRef" class="lt-echarts lt-echarts--tall" />
            </a-card>
          </a-col>
          <a-col :xs="24" :lg="8">
            <a-card :bordered="false">
              <template #title>
                <span class="lt-card-title"><PieChartOutlined /> 文章状态</span>
              </template>
              <div ref="pieChartRef" class="lt-echarts lt-echarts--tall" />
            </a-card>
          </a-col>
        </a-row>

        <!-- 排行榜区：热门文章 + 活跃创作者 -->
        <a-row :gutter="[12, 12]" class="lt-home__row">
          <a-col :xs="24" :lg="14">
            <a-card :bordered="false">
              <template #title>
                <span class="lt-card-title"><FireOutlined /> 热门文章 TOP 5</span>
              </template>
              <template #extra>
                <a class="lt-card-more" @click="goto('/posts')">查看全部 <ArrowRightOutlined /></a>
              </template>
              <div v-if="topPostsWithRatio.length" class="lt-rank">
                <div
                  v-for="(item, idx) in topPostsWithRatio"
                  :key="item.id"
                  class="lt-rank__row lt-rank__row--clickable"
                  @click="goto('/posts')"
                >
                  <div class="lt-rank__badge" :class="`lt-rank__badge--${rankPreset(idx)}`">
                    {{ idx + 1 }}
                  </div>
                  <div class="lt-rank__body">
                    <div class="lt-rank__title" :title="item.title">{{ item.title }}</div>
                    <div class="lt-rank__meta">
                      <span><EyeOutlined /> {{ item.viewCount }}</span>
                      <span><CommentOutlined /> {{ item.commentCount }}</span>
                    </div>
                  </div>
                  <a-progress
                    class="lt-rank__bar"
                    :percent="item.ratio"
                    :show-info="false"
                    :stroke-width="6"
                    stroke-color="var(--lt-color-primary)"
                    trail-color="var(--lt-color-bg-spotlight)"
                  />
                </div>
              </div>
              <a-empty v-else description="暂无数据" style="padding: 32px 0" />
            </a-card>
          </a-col>

          <a-col :xs="24" :lg="10">
            <a-card :bordered="false">
              <template #title>
                <span class="lt-card-title"><TeamOutlined /> 活跃创作者</span>
              </template>
              <template #extra>
                <a class="lt-card-more" @click="goto('/users')">查看全部 <ArrowRightOutlined /></a>
              </template>
              <div v-if="dashboardStats.topAuthors?.length" class="lt-authors">
                <div
                  v-for="(item, idx) in dashboardStats.topAuthors"
                  :key="item.id"
                  class="lt-authors__row lt-authors__row--clickable"
                  @click="goto('/users')"
                >
                  <a-avatar
                    :size="36"
                    :src="item.avatar"
                    :style="{
                      background: idx < 3 ? 'var(--lt-color-primary-bg)' : 'var(--lt-color-neutral-3)',
                      color: idx < 3 ? 'var(--lt-color-primary)' : 'var(--lt-color-text-tertiary)',
                    }"
                  >
                    {{ (item.nickname || item.username).substring(0, 1).toUpperCase() }}
                  </a-avatar>
                  <div class="lt-authors__body">
                    <div class="lt-authors__name">{{ item.nickname || item.username }}</div>
                    <div class="lt-authors__meta">{{ item.postCount }} 篇文章</div>
                  </div>
                  <a-tag v-if="idx < 3" :color="rankPreset(idx) === 'gold' ? 'gold' : rankPreset(idx) === 'silver' ? 'default' : 'orange'" :bordered="false">
                    TOP {{ idx + 1 }}
                  </a-tag>
                </div>
              </div>
              <a-empty v-else description="暂无数据" style="padding: 32px 0" />
            </a-card>
          </a-col>
        </a-row>

        <!-- 技术栈 + 系统信息 -->
        <a-row :gutter="[12, 12]" class="lt-home__row">
          <a-col :xs="24" :lg="16">
            <a-card :bordered="false">
              <template #title>
                <span class="lt-card-title"><CodeOutlined /> 技术栈</span>
              </template>
              <div class="lt-tech">
                <div v-for="grp in techStack" :key="grp.group" class="lt-tech__group">
                  <div class="lt-tech__group-label">{{ grp.group }}</div>
                  <div class="lt-tech__list">
                    <span
                      v-for="it in grp.items"
                      :key="it.name"
                      class="lt-tech__chip"
                      :style="{
                        color: `var(--lt-color-${it.tone})`,
                        background: `color-mix(in srgb, var(--lt-color-${it.tone}) 10%, transparent)`,
                        borderColor: `color-mix(in srgb, var(--lt-color-${it.tone}) 25%, transparent)`,
                      }"
                    >
                      {{ it.name }}<span v-if="it.version" class="lt-tech__ver">{{ it.version }}</span>
                    </span>
                  </div>
                </div>
              </div>
            </a-card>
          </a-col>

          <a-col :xs="24" :lg="8">
            <a-card :bordered="false">
              <template #title>
                <span class="lt-card-title"><InfoCircleOutlined /> 系统信息</span>
              </template>
              <div class="lt-sysinfo">
                <div v-for="row in systemInfo" :key="row.label" class="lt-sysinfo__row">
                  <span class="lt-sysinfo__label">
                    <component :is="row.icon" />
                    {{ row.label }}
                  </span>
                  <span class="lt-sysinfo__value">{{ row.value }}</span>
                </div>
              </div>
            </a-card>
          </a-col>
        </a-row>
      </div>

      <a-empty v-else-if="!loading" description="暂无数据" style="padding: 64px 0" />
    </a-spin>
  </div>
</template>

<style scoped>
.lt-home {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-lg);
}

.lt-home__hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--lt-space-lg);
  padding-bottom: var(--lt-space-sm);
  border-bottom: 1px solid var(--lt-color-border-secondary);
  margin-bottom: var(--lt-space-xs);
}
.lt-home__title {
  margin: 0;
  font-size: var(--lt-font-size-xl);
  font-weight: var(--lt-font-weight-semibold);
  color: var(--lt-color-text);
  line-height: var(--lt-line-height-tight);
}
.lt-home__subtitle {
  margin: var(--lt-space-xs) 0 0;
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-sm);
}
.lt-home__body {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-lg);   /* 16px 卡片行间距 */
}

.lt-home__row {
  /* antd Row 使用负 margin 抵消 col 内边距，不要覆盖 */
}

/* ===== 主 KPI 卡 ===== */
.lt-kpi {
  position: relative;
  overflow: hidden;
  transition: box-shadow var(--lt-duration-base) var(--lt-ease-in-out),
              transform var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-kpi:hover {
  box-shadow: var(--lt-shadow-md);
  transform: translateY(-1px);
}
.lt-kpi--clickable { cursor: pointer; }
.lt-kpi--clickable:hover .lt-kpi__goto {
  opacity: 1;
  transform: translateX(0);
}
.lt-kpi__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-sm);
}
.lt-kpi__label {
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-sm);
}
.lt-kpi__icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--lt-radius-md);
  font-size: var(--lt-font-size-md);
}
.lt-kpi__value {
  margin-top: var(--lt-space-sm);
  font-size: var(--lt-font-size-2xl);
  font-weight: var(--lt-font-weight-bold);
  color: var(--lt-color-text);
  line-height: var(--lt-line-height-tight);
  font-variant-numeric: tabular-nums;
}
.lt-kpi__footer {
  margin-top: var(--lt-space-sm);
  display: flex;
  align-items: center;
  gap: var(--lt-space-sm);
  font-size: var(--lt-font-size-xs);
}
.lt-kpi__foot-label {
  color: var(--lt-color-text-tertiary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lt-kpi__goto {
  color: var(--lt-color-primary);
  font-size: var(--lt-font-size-xs);
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity var(--lt-duration-base) var(--lt-ease-in-out),
              transform var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-kpi__spark {
  margin-top: var(--lt-space-sm);
  height: 40px;
}

.lt-delta {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-weight: var(--lt-font-weight-medium);
  padding: 1px 6px;
  border-radius: var(--lt-radius-sm);
}
.lt-delta--up { color: var(--lt-color-success); background: var(--lt-color-success-bg); }
.lt-delta--down { color: var(--lt-color-error); background: var(--lt-color-error-bg); }
.lt-delta--flat { color: var(--lt-color-text-tertiary); background: var(--lt-color-bg-spotlight); }

/* ===== 次要统计卡 ===== */
.lt-mini {
  transition: box-shadow var(--lt-duration-base) var(--lt-ease-in-out),
              transform var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-mini:hover { box-shadow: var(--lt-shadow-sm); }
.lt-mini--clickable { cursor: pointer; }
.lt-mini--clickable:hover { transform: translateY(-1px); }
.lt-mini__inner {
  display: flex;
  align-items: center;
  gap: var(--lt-space-md);
}
.lt-mini__icon {
  font-size: var(--lt-font-size-xl);
  flex: 0 0 auto;
}
.lt-mini__body { min-width: 0; }
.lt-mini__value {
  font-size: var(--lt-font-size-lg);
  font-weight: var(--lt-font-weight-semibold);
  color: var(--lt-color-text);
  line-height: var(--lt-line-height-tight);
  font-variant-numeric: tabular-nums;
}
.lt-mini__label {
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
  margin-top: 2px;
}

/* ===== 卡片标题 & 查看全部 ===== */
.lt-card-title {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-sm);
  font-size: var(--lt-font-size-base);
  font-weight: var(--lt-font-weight-semibold);
  color: var(--lt-color-text);
}
.lt-card-more {
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-xs);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  transition: color var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-card-more:hover { color: var(--lt-color-primary); }

/* ===== ECharts 容器 ===== */
.lt-echarts { width: 100%; height: 240px; }
.lt-echarts--tall { height: 280px; }

/* ===== 排行榜 ===== */
.lt-rank { display: flex; flex-direction: column; gap: var(--lt-space-md); }
.lt-rank__row {
  display: grid;
  grid-template-columns: 28px 1fr 100px;
  align-items: center;
  gap: var(--lt-space-md);
  padding: var(--lt-space-xs) var(--lt-space-sm);
  border-radius: var(--lt-radius-md);
  margin: 0 calc(var(--lt-space-sm) * -1);
  transition: background var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-rank__row--clickable { cursor: pointer; }
.lt-rank__row--clickable:hover { background: var(--lt-color-hover-bg); }
.lt-rank__badge {
  width: 24px;
  height: 24px;
  border-radius: var(--lt-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--lt-font-size-xs);
  font-weight: var(--lt-font-weight-bold);
}
.lt-rank__badge--gold { background: var(--lt-color-gold-bg); color: var(--lt-color-gold); }
.lt-rank__badge--silver { background: var(--lt-color-neutral-4); color: var(--lt-color-neutral-8); }
.lt-rank__badge--bronze { background: var(--lt-color-warning-bg); color: var(--lt-color-warning); }
.lt-rank__badge--default { background: var(--lt-color-neutral-3); color: var(--lt-color-text-tertiary); }
.lt-rank__body { min-width: 0; }
.lt-rank__title {
  font-size: var(--lt-font-size-base);
  color: var(--lt-color-text);
  font-weight: var(--lt-font-weight-medium);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lt-rank__meta {
  margin-top: 2px;
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
  display: flex;
  gap: var(--lt-space-md);
}
.lt-rank__bar { margin: 0; }

/* ===== 活跃创作者 ===== */
.lt-authors { display: flex; flex-direction: column; }
.lt-authors__row {
  display: flex;
  align-items: center;
  gap: var(--lt-space-md);
  padding: var(--lt-space-sm);
  border-bottom: 1px solid var(--lt-color-border-secondary);
  margin: 0 calc(var(--lt-space-sm) * -1);
  border-radius: var(--lt-radius-md);
  transition: background var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-authors__row:last-child { border-bottom: none; }
.lt-authors__row--clickable { cursor: pointer; }
.lt-authors__row--clickable:hover { background: var(--lt-color-hover-bg); }
.lt-authors__body { flex: 1; min-width: 0; }
.lt-authors__name {
  font-weight: var(--lt-font-weight-medium);
  color: var(--lt-color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lt-authors__meta {
  margin-top: 2px;
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
}

/* ===== 骨架屏 shimmer ===== */
.lt-skel {
  background: linear-gradient(
    90deg,
    var(--lt-color-bg-spotlight) 25%,
    var(--lt-color-hover-bg) 50%,
    var(--lt-color-bg-spotlight) 75%
  );
  background-size: 200% 100%;
  border-radius: var(--lt-radius-sm);
  animation: lt-shimmer 1.4s ease-in-out infinite;
}
.lt-skel--label { width: 40%; height: 12px; }
.lt-skel--value { width: 60%; height: 28px; margin-top: var(--lt-space-sm); }
.lt-skel--foot { width: 80%; height: 12px; margin-top: var(--lt-space-sm); }
.lt-skel--spark { width: 100%; height: 40px; margin-top: var(--lt-space-sm); }
.lt-skel--mini-value { width: 50%; height: 18px; }
.lt-skel--mini-label { width: 40%; height: 12px; margin-top: 6px; }
.lt-skel--chart { width: 100%; height: 280px; }

@keyframes lt-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@media (max-width: 768px) {
  .lt-rank__row { grid-template-columns: 24px 1fr; }
  .lt-rank__bar { grid-column: 1 / -1; }
}

/* ===== 技术栈 ===== */
.lt-tech {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-md);
}
.lt-tech__group-label {
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
  font-weight: var(--lt-font-weight-medium);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: var(--lt-space-xs);
}
.lt-tech__list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--lt-space-sm);
}
.lt-tech__chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: var(--lt-radius-pill);
  font-size: var(--lt-font-size-xs);
  font-weight: var(--lt-font-weight-medium);
  border: 1px solid;
  transition: transform var(--lt-duration-fast) var(--lt-ease-in-out);
}
.lt-tech__chip:hover {
  transform: translateY(-1px);
}
.lt-tech__ver {
  font-family: var(--lt-font-family-mono);
  font-size: 11px;
  opacity: 0.8;
  font-variant-numeric: tabular-nums;
}

/* ===== 系统信息 ===== */
.lt-sysinfo {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-sm);
}
.lt-sysinfo__row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--lt-space-sm) 0;
  border-bottom: 1px solid var(--lt-color-border-secondary);
}
.lt-sysinfo__row:last-child { border-bottom: none; }
.lt-sysinfo__label {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-xs);
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-sm);
}
.lt-sysinfo__value {
  color: var(--lt-color-text);
  font-size: var(--lt-font-size-sm);
  font-variant-numeric: tabular-nums;
  font-family: var(--lt-font-family-mono);
}
</style>
