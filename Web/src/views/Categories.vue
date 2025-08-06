<template>
    <div class="categories-page content">
        <!-- 页面头部 -->
        <div class="card mb-16 shadow-sm">
            <div class="flex flex-col gap-16">
                <div class="flex flex-col gap-12">
                    <h1 class="text-2xl font-bold text-primary mb-0 flex flex-ac gap-8">
                        <span class="text-3xl">📂</span> 文章分类
                    </h1>
                    <p class="text-muted text-base mb-0">
                        浏览不同主题的文章内容，找到你感兴趣的话题
                    </p>
                    <div class="flex flex-ac gap-8">
                        <span class="badge bg-primary">共 {{ categories.length }} 个分类</span>
                        <span class="badge bg-secondary">{{ totalPosts }} 篇文章</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- 分类网格 -->
        <div class="card shadow-sm">
            <!-- 加载异常处理 -->
            <div v-if="loading" class="loading-text">加载中...</div>
            <div v-else-if="error" class="loading-text text-primary">
                <p>{{ error }}</p>
                <button @click="loadCategories"
                    class="bg-primary text-sm font-medium p-8 rounded transition hover-lift mt-8">重试</button>
            </div>

            <div v-else-if="categories.length === 0" class="text-center p-20">
                <div class="text-lg mb-8">📂</div>
                <h3 class="text-base font-semibold mb-8">暂无分类</h3>
                <p class="text-muted text-sm mb-0">还没有创建任何分类</p>
            </div>
            
            <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-20">
                <div v-for="category in categories" :key="category.id"
                    class="category-card card transition-all hover-lift cursor-pointer relative overflow-hidden"
                    @click="goToCategory(category.id)">
                    <!-- 装饰性背景渐变 -->
                    <div class="category-bg absolute top-0 right-0 w-20 h-20 opacity-10 rounded-full"></div>
                    
                    <div class="flex flex-col gap-16 relative z-10">
                        <!-- 分类图标和标题 -->
                        <div class="flex flex-ac gap-16">
                            <div class="category-icon w-50 h-50 bg-primary rounded-12 flex flex-ct text-white text-xl font-bold shadow-sm">
                                {{ getCategoryIcon(category.name) }}
                            </div>
                            <div class="flex-1">
                                <h3 class="text-lg font-bold text-primary mb-4 category-title">{{ category.name }}</h3>
                                <div class="flex flex-ac gap-8">
                                    <span class="badge bg-primary-light text-primary text-xs font-medium px-8 py-4 rounded-8">
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
                        <div class="flex flex-jb flex-ac pt-12 border-t border-color">
                            <div class="flex flex-ac gap-6 text-xs text-muted">
                                <span class="text-sm">📅</span>
                                <span>最近更新</span>
                            </div>
                            <div class="category-arrow flex flex-ac gap-6 text-primary text-sm font-semibold transition-all">
                                <span>查看文章</span>
                                <span class="arrow-icon transition-all">→</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 热门分类 -->
        <div v-if="popularCategories.length > 0" class="card shadow-sm mt-16">
            <div class="flex flex-col gap-16">
                <h2 class="text-lg font-semibold text-primary mb-0 flex flex-ac gap-8">
                    <span class="text-xl">🔥</span> 热门分类
                </h2>
                <div class="flex flex-wrap gap-12">
                    <div v-for="category in popularCategories" :key="category.id"
                        class="flex flex-ac gap-8 bg-hover px-12 py-8 rounded-8 cursor-pointer transition hover-lift"
                        @click="goToCategory(category.id)">
                        <span class="text-sm">{{ getCategoryIcon(category.name) }}</span>
                        <span class="text-sm font-medium">{{ category.name }}</span>
                        <span class="text-xs text-muted">({{ category.postCount }})</span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCategoryStore } from '@/stores/category'
import { useErrorHandler } from '@/composables/useErrorHandler'
import type { Category } from '@/services/category'

const router = useRouter()
const categoryStore = useCategoryStore()
const { handleAsync } = useErrorHandler()

// 响应式数据
const categories = ref<Category[]>([])
const loading = ref(false)
const error = ref('')

// 计算属性
const totalPosts = computed(() => {
    return categories.value.reduce((total, category) => total + (category.postCount || 0), 0)
})

const popularCategories = computed(() => {
    return categories.value
        .filter(category => (category.postCount || 0) > 0)
        .sort((a, b) => (b.postCount || 0) - (a.postCount || 0))
        .slice(0, 6)
})

// 获取分类图标
const getCategoryIcon = (categoryName: string): string => {
    const iconMap: Record<string, string> = {
        '技术': '💻',
        '前端': '🎨',
        '后端': '⚙️',
        '数据库': '🗄️',
        '算法': '🧮',
        '生活': '🌱',
        '随笔': '✍️',
        '教程': '📚',
        '工具': '🔧',
        '框架': '🏗️',
        'Vue': '💚',
        'React': '⚛️',
        'JavaScript': '🟨',
        'TypeScript': '🔷',
        'Java': '☕',
        'Python': '🐍',
        'Node.js': '🟢',
        '移动开发': '📱',
        '人工智能': '🤖',
        '机器学习': '🧠'
    }

    return iconMap[categoryName] || '📂'
}

// 加载分类列表
const loadCategories = async () => {
    await handleAsync(async () => {
        loading.value = true
        error.value = ''

        const result = await categoryStore.fetchCategories()
        categories.value = result || []
    }, {
        onError: (err) => {
            error.value = '加载分类失败，请稍后重试'
            console.error('加载分类失败:', err)
        },
        onFinally: () => {
            loading.value = false
        }
    })
}

// 跳转到分类文章页面
const goToCategory = (categoryId: number) => {
    router.push(`/category/${categoryId}`)
}

// 组件挂载时加载数据
onMounted(() => {
    loadCategories()
})
</script>

<style scoped>
/* 分类页面样式优化 */
.categories-page {
    margin: 0 auto;
    padding: 20px;
}

/* 分类卡片样式 */
.category-card {
    border-left: 4px solid var(--primary-color);
    transition: all 0.2s ease;
}

.category-card:hover {
    transform: translateY(-2px);
    border-left-color: var(--primary-color);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* 分类图标样式 */
.category-icon {
    background: var(--primary-color);
    transition: all 0.2s ease;
}

.category-card:hover .category-icon {
    background: var(--primary-color);
}

/* 分类标题样式 */
.category-title {
    color: var(--text-color);
    transition: color 0.2s ease;
}

.category-card:hover .category-title {
    color: var(--primary-color);
}

/* 装饰性背景 */
.category-bg {
    background: var(--primary-color);
    opacity: 0.05;
}

/* 箭头样式 */
.arrow-icon {
    transition: transform 0.2s ease;
}

.category-card:hover .arrow-icon {
    transform: translateX(2px);
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
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

/* 徽章样式 */
.badge {
    transition: all 0.2s ease;
}

.category-card:hover .badge {
    background: var(--primary-color) !important;
    color: white !important;
}

/* 缺失的工具类 */
.w-50 {
    width: 50px;
}

.h-50 {
    height: 50px;
}

.w-20 {
    width: 20px;
}

.z-10 {
    z-index: 10;
}

.italic {
    font-style: italic;
}

.overflow-hidden {
    overflow: hidden;
}

.px-8 {
    padding-left: 8px;
    padding-right: 8px;
}

.py-4 {
    padding-top: 4px;
    padding-bottom: 4px;
}

.pt-12 {
    padding-top: 12px;
}

.gap-6 {
    gap: 6px;
}

.flex-jb {
    justify-content: space-between;
}

/* 响应式设计 */
@media (max-width: 768px) {
    .categories-page {
        padding: 15px;
    }

    .grid {
        grid-template-columns: 1fr;
    }
    
    .category-card {
        padding: 16px;
    }
    
    .category-icon {
        width: 40px;
        height: 40px;
        font-size: 1rem;
    }
    
    .gap-20 {
        gap: 16px;
    }
}

@media (min-width: 769px) and (max-width: 1024px) {
    .grid {
        grid-template-columns: repeat(2, 1fr);
    }
}

@media (min-width: 1025px) {
    .grid {
        grid-template-columns: repeat(3, 1fr);
    }
}
</style>