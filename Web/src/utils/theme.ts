/**
 * 主题管理模块
 * 提供主题切换、初始化和状态管理功能
 */
const theme = {
  // 当前主题状态
  current: 'light',
  
  /**
   * 切换主题
   * 在浅色和深色主题之间切换，并保存到本地存储
   */
  toggle: function() {
    this.current = this.current == 'light' ? 'dark' : 'light';
    // 将主题类名应用到根元素，实现主题样式切换
    document.documentElement.className = this.current;
    // 将当前主题保存到localStorage，实现主题持久化存储
    localStorage.setItem('theme', this.current);
  },
  
  /**
   * 初始化主题
   * 从本地存储读取主题设置，如果没有则默认为浅色主题
   */
  init: function() {
    const saved = localStorage.getItem('theme') || 'light';
    this.current = saved;
    document.documentElement.className = saved;
  }
};

export default theme;