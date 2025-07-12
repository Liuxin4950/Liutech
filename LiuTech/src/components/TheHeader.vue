<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import theme from '../utils/theme.ts';

const router = useRouter();
const isMenuOpen = ref(false);

/**
 * 切换移动端菜单显示状态
 */
const toggleMenu = () => {
  isMenuOpen.value = !isMenuOpen.value;
};

/**
 * 导航到指定路由并关闭菜单
 * @param path 路由路径
 */
const navigateTo = (path: string) => {
  router.push(path);
  isMenuOpen.value = false;
};
</script>

<template>
  <header class="header">
    <div class="container">
      <div class="logo" @click="navigateTo('/')">
        <h1>MyBlog</h1>
      </div>
      
      <!-- 桌面端导航 -->
      <nav class="desktop-nav">
        <ul>
          <li><router-link to="/">首页</router-link></li>
          <li><router-link to="/">登录</router-link></li>
        </ul>
      </nav>
      
      <!-- 主题切换按钮 -->
      <button @click="theme.toggle" class="theme-toggle-btn">
        {{ theme.current === 'light' ? '🌙' : '☀️' }}
      </button>
      
      <!-- 移动端菜单按钮 -->
      <button class="mobile-menu-btn" @click="toggleMenu">
        <span></span>
        <span></span>
        <span></span>
      </button>
      
      <!-- 移动端菜单 -->
      <div class="mobile-menu" :class="{ 'is-open': isMenuOpen }">
        <ul>
          <li @click="navigateTo('/')">首页</li>
          <li @click="navigateTo('/login')">登录</li>
        </ul>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  background-color: var(--bg-color);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
  transition: background-color 0.3s;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  cursor: pointer;
}

.logo h1 {
  font-size: 1.5rem;
  margin: 0;
  color: var(--primary-color);
}

.desktop-nav ul {
  display: flex;
  list-style: none;
  margin: 0;
  padding: 0;
  gap: 30px;
}

.desktop-nav a {
  color: var(--text-color);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.3s;
  padding: 8px 0;
  position: relative;
}

.desktop-nav a:hover,
.desktop-nav a.router-link-active {
  color: var(--primary-color);
}

.desktop-nav a.router-link-active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background-color: var(--primary-color);
}

.theme-toggle-btn {
  background: none;
  border: none;
  font-size: 1.2rem;
  cursor: pointer;
  padding: 8px;
  border-radius: 50%;
  transition: background-color 0.3s;
}

.theme-toggle-btn:hover {
  background-color: var(--hover-color);
}

.mobile-menu-btn {
  display: none;
  flex-direction: column;
  justify-content: space-between;
  width: 30px;
  height: 20px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}

.mobile-menu-btn span {
  width: 100%;
  height: 2px;
  background-color: var(--text-color);
  transition: all 0.3s;
}

.mobile-menu {
  display: none;
  position: absolute;
  top: 70px;
  left: 0;
  width: 100%;
  background-color: var(--bg-color);
  box-shadow: 0 5px 10px rgba(0, 0, 0, 0.1);
  transform: translateY(-100%);
  opacity: 0;
  transition: transform 0.3s, opacity 0.3s;
  z-index: 99;
}

.mobile-menu.is-open {
  transform: translateY(0);
  opacity: 1;
}

.mobile-menu ul {
  list-style: none;
  margin: 0;
  padding: 20px;
}

.mobile-menu li {
  padding: 15px 0;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-color);
  cursor: pointer;
}

.mobile-menu li:last-child {
  border-bottom: none;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .desktop-nav {
    display: none;
  }
  
  .mobile-menu-btn {
    display: flex;
  }
  
  .mobile-menu {
    display: block;
  }
}
</style>