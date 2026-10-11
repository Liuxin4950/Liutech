<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardStats, type DashboardStats } from '@/services/dashboard'
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
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import * as echarts from 'echarts'
import type { ECharts, EChartsOption } from 'echarts'
import { useSettingsStore } from '@/stores/settings'

const router = useRouter()
const settings = useSettingsStore()

const dashboardStats = ref<DashboardStats | null>(null)
const loading = ref(true)
/** 加载失败原因。用于把「请求失败」和「后端确实没有数据」区分开——
    两者都会让 dashboardStats 为 null，此前一律显示"暂无数据"，误导排查方向。 */
const loadError = ref('')
/** 时钟驱动源。若直接写 dayjs() 的 computed，没有任何响应式依赖，
    求值一次后就永久缓存，页面上的"当前时间"会冻结在挂载那一刻。 */
const now = ref(dayjs())
let clockTimer: ReturnType<typeof setInterval> | undefined
/** 最近一次成功加载的时刻（HH:mm），比写死的"系统运行正常"更可信。 */
const lastUpdated = ref('')
const currentDate = computed(() => now.value.locale('zh-cn').format('YYYY年MM月DD日 dddd'))
const greeting = computed(() => {
  const h = now.value.hour()
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
 * 主 KPI 配置：value / label / icon / sparkline 数据源函数
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
    const second = sum(arr, [half, half * 2])
    if (first === 0) return second > 0 ? null : 0
    return Math.round(((second - first) / first) * 100)
  }
  return [
    {
      key: 'posts' as KpiKey,
      label: '文章总数',
      value: s.basicStats.postCount,
      icon: FileTextOutlined,
      trend: trend7.map((t) => t.count),
      delta: delta(trend7),
      hasDelta: delta(trend7) !== null,
      deltaLabel: delta(trend7) === null ? '前期为零，本期有新增' : '近 3 天较前 3 天（不含今日）',
      to: '/posts',
    },
    {
      key: 'users' as KpiKey,
      label: '用户总数',
      value: s.basicStats.userCount,
      icon: UserOutlined,
      trend: userTrend7.map((t) => t.count),
      delta: delta(userTrend7),
      hasDelta: delta(userTrend7) !== null,
      deltaLabel: delta(userTrend7) === null ? '前期为零，本期有新增' : '近 3 天较前 3 天（不含今日）',
      to: '/users',
    },
    {
      key: 'views' as KpiKey,
      label: '总浏览量',
      value: s.basicStats.totalViews,
      icon: EyeOutlined,
      trend: (s.topPosts || []).slice(0, 7).map((p) => p.viewCount),
      // 这张卡的迷你图画的是 TOP 文章的浏览量——分类数据，不是时间序列，
      // 不存在"环比"。宁可没有徽章，也不给出编造的持平/涨跌。
      delta: 0,
      hasDelta: false,
      deltaLabel: 'TOP 文章浏览分布',
      to: '/posts',
    },
    {
      key: 'comments' as KpiKey,
      label: '评论总数',
      value: s.basicStats.commentCount,
      icon: CommentOutlined,
      trend: (s.topPosts || []).slice(0, 7).map((p) => p.commentCount),
      // 同上：TOP 文章评论数分布，非时间序列，不提供环比徽章
      delta: 0,
      hasDelta: false,
      deltaLabel: 'TOP 文章评论分布',
      to: '/comments',
    },
  ]
})

const secondaryStats = computed(() => {
  const s = dashboardStats.value
  if (!s) return [] as any[]
  return [
    { label: '已发布', value: s.basicStats.publishedPostCount, icon: CheckCircleOutlined, to: '/posts' },
    { label: '草稿箱', value: s.basicStats.draftPostCount, icon: EditOutlined, to: '/posts' },
    { label: '分类', value: s.basicStats.categoryCount, icon: FolderOutlined, to: '/categories' },
    { label: '标签', value: s.basicStats.tagCount, icon: TagsOutlined, to: '/tags' },
  ]
})

const topPosts = computed(() => dashboardStats.value?.topPosts || [])
const topAuthors = computed(() => dashboardStats.value?.topAuthors || [])

/** 趋势图至少要有两天数据才有意义，否则用空状态代替空坐标系 */
const hasTrend = computed(() => (dashboardStats.value?.postTrend?.length ?? 0) > 1
  && [...(dashboardStats.value?.postTrend || []), ...(dashboardStats.value?.userTrend || [])].some(it => it.count > 0))
/** 各状态全为 0 时饼图无扇区可画，同样落空状态 */
const hasStatusDistribution = computed(() =>
  (dashboardStats.value?.postStatusDistribution || []).some((it) => it.count > 0)
)

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

/** 渲染 KPI 卡片的迷你 sparkline（统一用主色，保持克制的蓝灰基调） */
function renderSparklines() {
  for (const [key, chart] of sparklines) {
    const el = sparklineRefs.value[key]
    const kpi = kpis.value.find((k) => k.key === key)
    if (!el || !kpi) {
      chart.dispose()
      sparklines.delete(key)
    }
  }
  const color = cssVar('--lt-color-primary')
  for (const kpi of kpis.value) {
    if (kpi.trend.length < 2) continue
    const el = sparklineRefs.value[kpi.key]
    if (!el) continue
    let chart = sparklines.get(kpi.key)
    if (!chart) {
      chart = echarts.init(el)
      sparklines.set(kpi.key, chart)
    }
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
            { offset: 0, color: color + '33' },
            { offset: 1, color: color + '00' },
          ]),
        },
      }],
    })
  }
}

function initTrendChart() {
  if (!dashboardStats.value) return
  if (!trendChartRef.value) {
    // 容器被 v-if 收起（趋势数据不足）时释放旧实例，避免泄漏与 resize 打到已销毁实例
    trendChart.value?.dispose()
    trendChart.value = null
    return
  }
  trendChart.value?.dispose()
  trendChart.value = echarts.init(trendChartRef.value)

  const c = chartCommon()
  const s = dashboardStats.value
  const dates = s.postTrend.map((it) => dayjs(it.date).format('MM-DD'))
  const postCounts = s.postTrend.map((it) => it.count)
  const userCounts = s.userTrend.map((it) => it.count)
  // 蓝 + 灰的双序列配色，贴合克制的蓝灰基调
  const chartPost = cssVar('--lt-color-chart-1')
  const chartUser = cssVar('--lt-color-neutral-7')

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
        lineStyle: { color: chartPost, width: 2 },
        itemStyle: { color: chartPost },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: chartPost + '33' },
            { offset: 1, color: chartPost + '00' },
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
      },
    ],
  }
  trendChart.value.setOption(option)
}

function initPieChart() {
  if (!dashboardStats.value) return
  if (!pieChartRef.value) {
    // 同 initTrendChart：容器收起时释放旧实例
    pieChart.value?.dispose()
    pieChart.value = null
    return
  }
  pieChart.value?.dispose()
  pieChart.value = echarts.init(pieChartRef.value)

  const c = chartCommon()
  const data = dashboardStats.value.postStatusDistribution.map((it) => ({
    name: it.displayName,
    value: it.count,
  }))
  // 蓝色渐层 + 灰阶，替代此前无关语义的绿/橙/蓝拼色
  const palette = [
    cssVar('--lt-color-chart-1'),
    cssVar('--lt-color-brand-3'),
    cssVar('--lt-color-neutral-6'),
    cssVar('--lt-color-neutral-8'),
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
      radius: ['52%', '76%'],
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
      loadError.value = ''
      lastUpdated.value = dayjs().format('HH:mm')
      loading.value = false
      await nextTick()
      renderAll()
    } else {
      loadError.value = res.message || '加载统计数据失败'
      message.error(loadError.value)
    }
  } catch (error: any) {
    loadError.value = error?.message || '加载统计数据失败'
    if (!error?.isBusiness) message.error(loadError.value)
  } finally {
    loading.value = false
  }
}

const handleRefresh = () => loadDashboardStats()

let unwatch: (() => void) | null = null
onMounted(() => {
  loadDashboardStats()
  window.addEventListener('resize', handleResize)
  // 分钟级显示，30s 跳一次足够；页面在 KeepAlive 缓存中时也只是更新一个 ref
  clockTimer = setInterval(() => { now.value = dayjs() }, 30_000)
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
  if (clockTimer) clearInterval(clockTimer)
  unwatch?.()
  trendChart.value?.dispose()
  pieChart.value?.dispose()
  for (const s of sparklines.values()) s.dispose()
  sparklines.clear()
})

/** 变化方向图标与色板 */
function deltaClass(v: number) {
  if (v > 0) return 'lt-delta--up'
  if (v < 0) return 'lt-delta--down'
  return 'lt-delta--flat'
}
</script>

<template>
  <div class="lt-home">
    <!-- 顶部欢迎栏 -->
    <header class="lt-home__hero">
      <div>
        <h1 class="lt-home__title">{{ greeting }}，管理员</h1>
        <p class="lt-home__subtitle">
          {{ currentDate }}
          <template v-if="lastUpdated"> · 数据更新于 {{ lastUpdated }}</template>
        </p>
      </div>
      <a-button :loading="loading" @click="handleRefresh">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </header>

    <a-spin :spinning="loading && !dashboardStats">
      <!-- 首次加载：骨架屏（保持布局稳定，避免整页跳动） -->
      <div v-if="loading && !dashboardStats" class="lt-home__body">
        <a-row :gutter="[12, 12]">
          <a-col v-for="i in 4" :key="`sk-kpi-${i}`" :xs="24" :sm="12" :lg="6">
            <a-card :bordered="false" :body-style="{ padding: '16px' }">
              <div class="lt-skel lt-skel--label" />
              <div class="lt-skel lt-skel--value" />
              <div class="lt-skel lt-skel--foot" />
              <div class="lt-skel lt-skel--spark" />
            </a-card>
          </a-col>
        </a-row>
        <a-row :gutter="[12, 12]">
          <a-col v-for="i in 4" :key="`sk-mini-${i}`" :xs="12" :sm="6">
            <a-card :bordered="false" :body-style="{ padding: '12px 16px' }">
              <div class="lt-skel lt-skel--mini-value" />
              <div class="lt-skel lt-skel--mini-label" />
            </a-card>
          </a-col>
        </a-row>
        <a-row :gutter="[12, 12]">
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
        <!-- 已有数据时刷新失败：保留上一次的数据继续展示，只用横幅提示，
             不要因为一次网络抖动就把整页打成空白。 -->
        <a-alert
          v-if="loadError"
          type="warning"
          show-icon
          closable
          :message="`刷新失败，当前展示的是上一次成功加载的数据：${loadError}`"
          @close="loadError = ''"
        />

        <!-- 主 KPI 卡（4 张） -->
        <a-row :gutter="[12, 12]">
          <a-col v-for="kpi in kpis" :key="kpi.key" :xs="24" :sm="12" :lg="6">
            <a-card
              :bordered="false"
              class="lt-kpi"
              :body-style="{ padding: '16px' }"
              @click="goto(kpi.to)"
            >
              <div class="lt-kpi__header">
                <span class="lt-kpi__label">{{ kpi.label }}</span>
                <span class="lt-kpi__icon"><component :is="kpi.icon" /></span>
              </div>
              <div class="lt-kpi__value">{{ kpi.value.toLocaleString() }}</div>
              <div class="lt-kpi__footer">
                <span v-if="kpi.hasDelta" class="lt-delta" :class="deltaClass(kpi.delta)">
                  <ArrowUpOutlined v-if="kpi.delta > 0" />
                  <ArrowDownOutlined v-else-if="kpi.delta < 0" />
                  <MinusOutlined v-else />
                  {{ kpi.delta !== 0 ? `${Math.abs(kpi.delta)}%` : '持平' }}
                </span>
                <span class="lt-kpi__foot-label">{{ kpi.deltaLabel }}</span>
                <ArrowRightOutlined class="lt-kpi__goto" />
              </div>
              <div
                v-if="kpi.trend.length > 1"
                :ref="(el) => (sparklineRefs[kpi.key] = el as HTMLElement)"
                class="lt-kpi__spark"
              />
            </a-card>
          </a-col>
        </a-row>

        <!-- 次要统计（4 张紧凑，可点击） -->
        <a-row :gutter="[12, 12]">
          <a-col v-for="stat in secondaryStats" :key="stat.label" :xs="12" :sm="6">
            <a-card
              :bordered="false"
              class="lt-mini"
              :body-style="{ padding: '12px 16px' }"
              @click="goto(stat.to)"
            >
              <div class="lt-mini__inner">
                <div class="lt-mini__icon"><component :is="stat.icon" /></div>
                <div class="lt-mini__body">
                  <div class="lt-mini__value">{{ stat.value.toLocaleString() }}</div>
                  <div class="lt-mini__label">{{ stat.label }}</div>
                </div>
              </div>
            </a-card>
          </a-col>
        </a-row>

        <!-- 图表区：左侧双趋势 + 右侧饼图 -->
        <a-row :gutter="[12, 12]">
          <a-col :xs="24" :lg="16">
            <a-card :bordered="false">
              <template #title>
                <span class="lt-card-title"><BarChartOutlined /> 近 7 日数据趋势</span>
              </template>
              <div v-if="hasTrend" ref="trendChartRef" class="lt-echarts lt-echarts--tall" />
              <a-empty v-else description="近 7 天暂无新增文章或用户" class="lt-empty" />
            </a-card>
          </a-col>
          <a-col :xs="24" :lg="8">
            <a-card :bordered="false">
              <template #title>
                <span class="lt-card-title"><PieChartOutlined /> 文章状态</span>
              </template>
              <div v-if="hasStatusDistribution" ref="pieChartRef" class="lt-echarts lt-echarts--tall" />
              <a-empty v-else description="暂无文章数据" class="lt-empty" />
            </a-card>
          </a-col>
        </a-row>

        <!-- 排行榜区：热门文章 + 活跃创作者（同一套列表节奏） -->
        <a-row :gutter="[12, 12]">
          <a-col :xs="24" :lg="14">
            <a-card :bordered="false">
              <template #title>
                <span class="lt-card-title"><FireOutlined /> 热门文章</span>
              </template>
              <template #extra>
                <a class="lt-card-more" @click="goto('/posts')">查看全部 <ArrowRightOutlined /></a>
              </template>
              <div v-if="topPosts.length" class="lt-list">
                <div
                  v-for="(item, idx) in topPosts"
                  :key="item.id"
                  class="lt-list__row"
                  @click="goto('/posts')"
                >
                  <span class="lt-list__index" :class="{ 'lt-list__index--top': idx === 0 }">{{ idx + 1 }}</span>
                  <div class="lt-list__main">
                    <div class="lt-list__title" :title="item.title">{{ item.title }}</div>
                    <div class="lt-list__meta">
                      评论 {{ item.commentCount.toLocaleString() }} · 点赞 {{ item.likeCount.toLocaleString() }}
                    </div>
                  </div>
                  <span class="lt-list__value"><EyeOutlined />{{ item.viewCount.toLocaleString() }}</span>
                </div>
              </div>
              <a-empty v-else description="暂无文章数据" class="lt-empty" />
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
              <div v-if="topAuthors.length" class="lt-list">
                <div
                  v-for="(item, idx) in topAuthors"
                  :key="item.id"
                  class="lt-list__row lt-list__row--author"
                  @click="goto('/users')"
                >
                  <span class="lt-list__index" :class="{ 'lt-list__index--top': idx === 0 }">{{ idx + 1 }}</span>
                  <a-avatar :size="28" :src="item.avatar" class="lt-list__avatar">
                    {{ (item.nickname || item.username).substring(0, 1).toUpperCase() }}
                  </a-avatar>
                  <div class="lt-list__main">
                    <div class="lt-list__title">{{ item.nickname || item.username }}</div>
                    <div v-if="item.nickname && item.nickname !== item.username" class="lt-list__meta">
                      @{{ item.username }}
                    </div>
                  </div>
                  <span class="lt-list__value">{{ item.postCount.toLocaleString() }} 篇</span>
                </div>
              </div>
              <a-empty v-else description="暂无创作者数据" class="lt-empty" />
            </a-card>
          </a-col>
        </a-row>
      </div>

      <!-- 加载失败：必须与「后端确实没有数据」区分开，否则排查方向会被误导。
           此前失败只弹一个 toast，dashboardStats 仍为 null，页面落到"暂无数据"。 -->
      <a-result
        v-else-if="!loading && loadError"
        status="error"
        title="统计数据加载失败"
        :sub-title="loadError"
      >
        <template #extra>
          <a-button type="primary" @click="handleRefresh">
            <template #icon><ReloadOutlined /></template>
            重试
          </a-button>
        </template>
      </a-result>

      <a-empty
        v-else-if="!loading"
        description="暂无数据"
        class="lt-empty lt-empty--page"
      />
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
  gap: var(--lt-space-lg);
}

/* ===== 主 KPI 卡 ===== */
.lt-kpi {
  position: relative;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow var(--lt-duration-base) var(--lt-ease-in-out),
              transform var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-kpi:hover {
  box-shadow: var(--lt-shadow-sm);
  transform: translateY(-1px);
}
.lt-kpi:hover .lt-kpi__goto {
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
/* 图标底色统一为中性填充：KPI 卡不靠颜色区分，靠数值层级 */
.lt-kpi__icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--lt-radius-md);
  background: var(--lt-color-fill-secondary);
  color: var(--lt-color-text-secondary);
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
.lt-delta--up { color: var(--lt-color-success-text); background: var(--lt-color-success-bg); }
.lt-delta--down { color: var(--lt-color-error-text); background: var(--lt-color-error-bg); }
.lt-delta--flat { color: var(--lt-color-text-tertiary); background: var(--lt-color-fill-secondary); }

/* ===== 次要统计卡 ===== */
.lt-mini {
  cursor: pointer;
  transition: box-shadow var(--lt-duration-base) var(--lt-ease-in-out),
              transform var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-mini:hover {
  box-shadow: var(--lt-shadow-sm);
  transform: translateY(-1px);
}
.lt-mini__inner {
  display: flex;
  align-items: center;
  gap: var(--lt-space-md);
}
.lt-mini__icon {
  font-size: var(--lt-font-size-lg);
  color: var(--lt-color-text-secondary);
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

/* ===== 排行榜列表（热门文章 / 活跃创作者共用） ===== */
.lt-list { display: flex; flex-direction: column; }
.lt-list__row {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  align-items: center;
  column-gap: var(--lt-space-md);
  padding: var(--lt-space-sm);
  margin: 0 calc(var(--lt-space-sm) * -1);
  border-radius: var(--lt-radius-md);
  border-bottom: 1px solid var(--lt-color-border-secondary);
  cursor: pointer;
  transition: background var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-list__row--author {
  grid-template-columns: 22px 28px minmax(0, 1fr) auto;
}
.lt-list__row:last-child { border-bottom-color: transparent; }
.lt-list__row:hover { background: var(--lt-color-hover-bg); }
/* 名次徽章：中性填充，只有第一名用主色低饱和底做轻微强调 */
.lt-list__index {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--lt-radius-sm);
  background: var(--lt-color-fill-secondary);
  color: var(--lt-color-text-tertiary);
  font-size: 11px;
  font-weight: var(--lt-font-weight-semibold);
  font-variant-numeric: tabular-nums;
}
.lt-list__index--top {
  background: var(--lt-color-primary-bg);
  color: var(--lt-color-primary);
}
.lt-list__avatar {
  background: var(--lt-color-fill-secondary);
  color: var(--lt-color-text-secondary);
}
.lt-list__main { min-width: 0; }
.lt-list__title {
  font-size: var(--lt-font-size-base);
  font-weight: var(--lt-font-weight-medium);
  color: var(--lt-color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lt-list__meta {
  margin-top: 2px;
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
}
.lt-list__value {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-2xs);
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-secondary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* ===== 空状态 ===== */
.lt-empty { padding: var(--lt-space-xl) 0; }
.lt-empty--page { padding: var(--lt-space-4xl) 0; }

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

@media (max-width: 576px) {
  .lt-list__row,
  .lt-list__row--author {
    grid-template-columns: 22px minmax(0, 1fr) auto;
  }
  .lt-list__row--author .lt-list__avatar { display: none; }
}
</style>
