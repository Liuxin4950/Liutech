<template>
    <div class="categories-page content">
        <!-- 页面头部 -->
        <div class="card bg-card mb-16 shadow-sm">
            <div class="flex flex-col gap-16">
                <div class="flex flex-col gap-12">
                    <h1 class="text-2xl font-bold text-primary mb-0 flex flex-ac gap-8">
                        <Icon name="folder" size="24" /> 文章分类
                    </h1>
                    <p class="text-muted text-base mb-0">
                        浏览不同主题的文章内容，找到你感兴趣的话题
                    </p>
                    <div class="flex flex-ac gap-8">
                        <span class="badge">共 {{ categories.length }} 个分类</span>
                        <span class="badge">{{ totalPosts }} 篇文章</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- 分类网格 -->
        <div class="card shadow-sm mb-16">
            <!-- 加载异常处理 -->
            <div v-if="loading" class="loading-text text-sm">加载中...</div>
            <div v-else-if="error" class="loading-text text-primary text-sm">
                <p>{{ error }}</p>
                <button @click="loadCategories"
                    class="bg-primary text-sm font-medium p-8 rounded transition hover-lift mt-8">重试</button>
            </div>

            <div v-else-if="categories.length === 0" class="text-center p-20 flex flex-col flex-ac text-sm">
                <h3 class="text-base font-semibold mb-8">暂无分类</h3>
                <p class="text-muted text-sm mb-0">还没有创建任何分类</p>
                <img src="@/assets/image/扑到.png" alt="" class="fit-err">
            </div>
            
            <div v-else class="grid gap-20">
                <div v-for="category in categories" :key="category.id"
                    class="category-card bg-card card transition-all hover-lift cursor-pointer relative overflow-hidden"
                    @click="goToCategory(category.id)">
                    <!-- 装饰性背景渐变 -->
                    <div class="category-bg absolute top-0 right-0 w-20 h-20 opacity-10 rounded-full"></div>
                    
                    <div class="flex flex-col gap-16 relative z-10">
                        <!-- 分类图标和标题 -->
                        <div class="flex flex-ac gap-16">
                            <div class="category-icon w-50 h-50 rounded-12 flex flex-ct text-white shadow-sm">
                                <Icon :name="getCategoryIcon(category.name)" size="24" />
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
                        <div class="flex flex-sb  flex-ac pt-12 ">
                            <div class="flex flex-ac gap-6 text-xs text-muted">
                                <Icon name="calendar" size="14" />
                                <span>最近更新</span>
                            </div>
                            <div class="category-arrow flex flex-ac gap-6 text-primary text-sm link">
                                <span>查看文章</span>
                                <Icon name="arrow-right" size="14" class="arrow-icon transition-all" />
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
                <div class="flex flex-wrap gap-12">
                    <div v-for="category in popularCategories" :key="category.id"
                        class="tag  flex flex-ac gap-8 px-12 py-8 rounded-8  transition link"
                        @click="goToCategory(category.id)">
                        <Icon :name="getCategoryIcon(category.name)" size="14" />
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
import Icon from '@/components/Icon.vue'

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
        '技术': 'code',
        '前端': 'layout',
        '后端': 'cog',
        '数据库': 'database',
        '算法': 'square',
        '生活': 'layers',
        '随笔': 'pen',
        '教程': 'book',
        '工具': 'wrench',
        '框架': 'building',
        'Vue': 'layers',
        'React': 'atom',
        'JavaScript': 'square',
        'TypeScript': 'square',
        'Java': 'coffee',
        'Python': 'python',
        'Node.js': 'square',
        '移动开发': 'smartphone',
        '人工智能': 'bot',
        '机器学习': 'brain'
    }

    return iconMap[categoryName] || 'folder'
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
    padding: 20px;
}

/* 分类卡片样式 */
.category-card {
    transition: all 0.2s ease;
}

.category-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* 分类标题样式 */
.category-title {
    color: var(--text-color);
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
    
    .gap-20 {
        gap: 16px;
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