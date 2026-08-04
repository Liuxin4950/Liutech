/**
 * 分类图标映射（分类页 / 侧栏分类组件共用，避免两份映射漂移）
 * 按分类名包含匹配（如"前端开发"命中"前端"），未匹配回退默认文件夹图标
 */
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
  '移动': 'smartphone',
  'AI': 'bot',
  '人工智能': 'bot',
  '机器学习': 'brain',
  '项目': 'rocket',
  '分享': 'message',
  '安全': 'shield',
  '娱乐': 'game'
}

/** 获取分类图标名；未匹配任何关键词时回退 folder（Icon.vue 中保证存在） */
export function getCategoryIcon(categoryName: string): string {
  for (const key of Object.keys(iconMap)) {
    if (categoryName.includes(key)) return iconMap[key]
  }
  return 'folder'
}
