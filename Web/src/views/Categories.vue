<template>
    <div class="categories-page content">
        <!-- 页面头部 -->
        <div class="card bg-card mb-16 shadow-sm">
            <div class="page-title">
                <span class="title-badge"><Icon name="folder" size="12" /> Categories</span>
                <h1 class="title-heading">文章<span class="title-highlight">分类</span></h1>
                <p class="title-desc">浏览不同主题的文章内容，找到你感兴趣的话题</p>
                <div class="title-meta">
                    <span class="badge">共 {{ categories.length }} 个分类</span>
                    <span class="badge">{{ totalPosts }} 篇文章</span>
                </div>
            </div>
        </div>

        <!-- 分类网格 -->
        <div class="card shadow-sm mb-16">
            <!-- 搜索框 -->
            <div class="search-box mb-16">
                <Icon name="search" size="16" class="search-icon" />
                <input v-model="searchKeyword" type="text" placeholder="搜索分类..." class="search-input" />
            </div>

            <!-- 加载异常处理 -->
            <div v-if="loading" class="loading-text text-sm">加载中...</div>
            <div v-else-if="error" class="loading-text text-primary text-sm">
                <p>{{ error }}</p>
                <button @click="loadCategories"
                    class="bg-primary text-sm font-medium p-8 rounded transition mt-8">重试</button>
            </div>

            <div v-else-if="filteredCategories.length === 0" class="text-center p-20 flex flex-col flex-ac text-sm">
                <h3 class="text-base font-semibold">{{ searchKeyword ? '未找到相关分类' : '暂无分类' }}</h3>
                <p class="text-muted text-sm mb-0">{{ searchKeyword ? '尝试使用其他关键词搜索' : '还没有创建任何分类' }}</p>
                <img src="@/assets/image/扑到.png" alt="" class="fit-err">
            </div>
            
            <div v-else class="grid gap-20">
                <div v-for="category in filteredCategories" :key="category.id"
                    class="category-card bg-card card cursor-pointer relative"
                    @click="goToCategory(category.id)">
                    <!-- 装饰性背景渐变 -->
                    <div class="category-bg"></div>
                    
                    <div class="flex flex-col gap-16 relative">
                        <!-- 分类图标和标题 -->
                        <div class="flex flex-ac gap-16">
                            <div class="category-icon flex flex-jc shadow-sm">
                                <Icon :name="getCategoryIcon(category.name)" size="24" />
                            </div>
                            <div class="flex-1">
                                <h3 class="text-lg font-bold text-primary category-title">{{ category.name }}</h3>
                                <div class="flex flex-ac gap-8">
                                    <span class="badge text-primary text-xs font-medium">
                                        {{ category.postCount || 0 }} 篇文章
                                    </span>
                                </div>
                            </div>
                        </div>

                        <!-- 分类描述 -->
                        <div class="category-description">
                            <p v-if="category.description" class="text-muted text-sm mb-0 line-clamp-2 leading-relaxed">
                                {{ category.description }}
                            </p>
                            <p v-else class="text-muted text-sm mb-0 opacity-60 italic">
                                暂无描述，点击查看该分类下的精彩内容
                            </p>
                        </div>

                        <!-- 底部信息栏 -->
                        <div class="flex flex-sb flex-ac">
                            <div class="category-meta flex flex-ac text-xs text-muted">
                                <Icon name="calendar" size="14" />
                                <span>最近更新</span>
                            </div>
                            <div class="category-arrow flex flex-ac text-primary text-sm link">
                                <span>查看文章</span>
                                <Icon name="chevronRight" size="14" class="arrow-icon" />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 热门分类 -->
        <div v-if="popularCategories.length > 0" class="card bg-card shadow-sm mb-16">
            <div class="flex flex-col gap-16">
                <h2 class="text-lg font-semibold text-primary mb-0 flex flex-ac gap-8">
                    <Icon name="fire" size="20" /> 热门分类
                </h2>
                <div class="flex-fw gap-12">
                    <div v-for="category in popularCategories" :key="category.id"
                        class="tag flex flex-ac gap-8 transition link"
                        @click="goToCategory(category.id)">
                        <Icon :name="getCategoryIcon(category.name)" size="14" />
                        <span class="text-sm font-medium">{{ category.name }}</span>
                        <span class="text-xs text-muted">({{ category.postCount || 0 }})</span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getCategoryIcon } from "@/utils/categoryIcons"
import { useRouter } from 'vue-router'
import { useCategoryStore } from '@/stores/category'
import { useErrorHandler } from '@/composables/useErrorHandler'
import type { Category } from '@/services/category'
import Icon from '@/components/Icon.vue'

const router = useRouter()
const categoryStore = useCategoryStore()
const { handleAsync } = useErrorHandler()

// 响应式数据
const categories = ref<Category[]>([])
const loading = ref(false)
const error = ref('')
const searchKeyword = ref('')

// 计算属性
const totalPosts = computed(() => {
    return categories.value.reduce((total, category) => total + (category.postCount || 0), 0)
})

const filteredCategories = computed(() => {
    if (!searchKeyword.value.trim()) return categories.value
    const kw = searchKeyword.value.trim().toLowerCase()
    return categories.value.filter(c =>
        c.name.toLowerCase().includes(kw) ||
        (c.description && c.description.toLowerCase().includes(kw))
    )
})

const popularCategories = computed(() => {
    return categories.value
        .filter(category => (category.postCount || 0) > 0)
        .sort((a, b) => (b.postCount || 0) - (a.postCount || 0))
        .slice(0, 6)
})

// 加载分类列表
const loadCategories = async () => {
    await handleAsync(async () => {
        loading.value = true
        error.value = ''

        const result = await categoryStore.fetchCategories()
        categories.value = result || []
    }, {
        onError: () => {
            error.value = '加载分类失败，请稍后重试'
        },
        onFinally: () => {
            loading.value = false
        }
    })
}

// 跳转到分类文章页面
const goToCategory = (categoryId: number) => {
    router.push(`/category-detail/${categoryId}`)
}

// 组件挂载时加载数据
onMounted(() => {
    loadCategories()
})
</script>

<style scoped>
/* 分类页面样式优化 */
.categories-page {
}

/* 分类卡片样式 */
.category-card {
    transition: all 0.2s ease;
    overflow: hidden;
}

/* 装饰性背景圆（原全局 .absolute/.top-0/.right-0/.rounded-full 移入） */
.category-bg {
    position: absolute;
    top: 0;
    right: 0;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    opacity: 0.1;
}

/* 分类图标：主题渐变块 + 白图标 */
.category-icon {
    width: 50px;
    height: 50px;
    border-radius: 12px;
    color: #fff;
    background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
}

/* 分类标题（原全局 .mb-4 移入） */
.category-title {
    margin-bottom: 4px;
}

/* 卡片内徽章（原全局 .bg-primary-light/.px-8/.py-4/.rounded-8 移入） */
.category-card .badge {
    background-color: var(--color-primary-light);
    padding: 4px 8px;
    border-radius: 8px;
}

/* 空状态标题（原全局 .mb-8 移入） */
.text-center h3 {
    margin-bottom: 8px;
}

/* 底部信息行（原全局 .gap-6/.pt-12 移入） */
.category-meta,
.category-arrow {
    gap: 6px;
}

.category-card .flex-sb {
    padding-top: 12px;
}

/* 暂无描述（原全局 .opacity-60/.italic 移入） */
.category-description p:last-child {
    opacity: 0.6;
    font-style: italic;
}

/* 装饰性背景圆 */
.category-bg {
    width: 20px;
    height: 20px;
    opacity: 0.1;
}

/* 分类图标 */
.category-icon {
    width: 50px;
    height: 50px;
}

.category-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* 分类标题样式 */
.category-title {
    color: var(--color-primary);
    transition: color 0.2s ease;
}

/* 箭头样式 */
.arrow-icon {
    transition: transform 0.2s ease;
}

/* 描述文本样式 */
.category-description {
    min-height: 40px;
    display: flex;
    align-items: center;
}

.leading-relaxed {
    line-height: 1.6;
}

.line-clamp-2 {
    display: -webkit-box;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

/* 徽章样式 */
.badge {
    transition: all 0.2s ease;
}

.category-card:hover .badge {
    background: var(--color-primary);
    color: white ;
}
/* 响应式设计 */
@include respond(md) {
    .categories-page {
        padding: 15px;
    }
    
    .category-card {
        padding: 16px;
    }
    
    .category-icon {
        width: 40px;
        height: 40px;
        font-size: 1rem;
    }
}
</style>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));

  @include respond(md) {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}

.category-card {
  @include respond(sm) {
    .flex.flex-ac.gap-16 {
      flex-direction: column;
      align-items: flex-start;
      gap: 12px;
    }
    
    .category-icon {
      width: 40px;
      height: 40px;
    }
  }
}
</style>