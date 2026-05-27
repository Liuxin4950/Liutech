import { ref, reactive, onMounted, type Ref } from 'vue'
import { message } from 'ant-design-vue'

/**
 * 分页配置接口
 */
export interface PaginationConfig {
  current: number
  pageSize: number
  total: number
  showSizeChanger: boolean
  showQuickJumper: boolean
  showTotal: (total: number) => string
}

/**
 * API 响应标准格式
 */
interface ApiResponse<T = any> {
  code: number
  message?: string
  data: {
    records: T[]
    total: number
    [key: string]: any
  }
}

/**
 * useTablePage 配置选项
 */
export interface UseTablePageOptions<T, P extends Record<string, any>> {
  /** 加载列表数据的 API 函数 */
  loadFn: (params: Record<string, any>) => Promise<ApiResponse<T>>
  /** 默认搜索参数 */
  defaultSearchParams: P
  /** 默认每页条数 */
  defaultPageSize?: number
  /** 加载失败提示 */
  loadErrorMessage?: string
  /** 是否在 onMounted 时自动加载 */
  autoLoad?: boolean
  /** 自定义搜索参数预处理，返回传给 loadFn 的最终搜索参数 */
  transformSearchParams?: (params: P) => Record<string, any>
}

/**
 * 统一的表格页面组合式函数
 * 封装了管理后台列表页的通用逻辑：加载、分页、搜索、选择
 *
 * @example
 * const { dataSource, pagination, loading, selectedRowKeys, searchParams,
 *         load, handleSearch, handleReset, handleTableChange, onSelectChange
 * } = useTablePage({
 *   loadFn: CategoriesService.getCategoryList,
 *   defaultSearchParams: { name: '', includeDeleted: false },
 *   loadErrorMessage: '加载分类失败'
 * })
 */
export function useTablePage<T extends Record<string, any>, P extends Record<string, any>>(
  options: UseTablePageOptions<T, P>
) {
  const {
    loadFn,
    defaultSearchParams,
    defaultPageSize = 10,
    loadErrorMessage = '加载数据失败',
    autoLoad = true,
    transformSearchParams
  } = options

  // 加载状态
  const loading = ref(false)

  // 数据源
  const dataSource = ref<T[]>([]) as Ref<T[]>

  // 选中的行 key
  const selectedRowKeys = ref<number[]>([])

  // 搜索参数（可由外部传入初始值）
  const searchParams = ref<P>({ ...defaultSearchParams }) as Ref<P>

  // 分页配置
  const pagination: PaginationConfig = reactive({
    current: 1,
    pageSize: defaultPageSize,
    total: 0,
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (total: number) => `共 ${total} 条记录`
  })

  /**
   * 加载数据
   */
  const load = async () => {
    try {
      loading.value = true
      const baseParams = transformSearchParams
        ? transformSearchParams(searchParams.value)
        : { ...searchParams.value }

      const params = {
        page: pagination.current,
        size: pagination.pageSize,
        ...baseParams
      }

      const res = await loadFn(params)
      if (res.code === 200) {
        dataSource.value = res.data.records
        pagination.total = res.data.total
      } else {
        message.error(res.message || loadErrorMessage)
      }
    } catch (e) {
      message.error(loadErrorMessage)
    } finally {
      loading.value = false
    }
  }

  /**
   * 搜索（重置到第一页后加载）
   */
  const handleSearch = () => {
    pagination.current = 1
    load()
  }

  /**
   * 重置搜索参数并重新加载
   */
  const handleReset = () => {
    searchParams.value = { ...defaultSearchParams }
    pagination.current = 1
    load()
  }

  /**
   * 表格分页、排序、筛选变化处理
   */
  const handleTableChange = (p: any) => {
    pagination.current = p.current
    pagination.pageSize = p.pageSize
    load()
  }

  /**
   * 行选择变化处理
   */
  const onSelectChange = (keys: number[]) => {
    selectedRowKeys.value = keys
  }

  /**
   * 清空选择
   */
  const clearSelection = () => {
    selectedRowKeys.value = []
  }

  // 自动加载
  if (autoLoad) {
    onMounted(() => {
      load()
    })
  }

  return {
    // 状态
    loading,
    dataSource,
    selectedRowKeys,
    searchParams,
    pagination,

    // 方法
    load,
    handleSearch,
    handleReset,
    handleTableChange,
    onSelectChange,
    clearSelection
  }
}

