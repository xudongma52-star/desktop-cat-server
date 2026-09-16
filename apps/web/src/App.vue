<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink class="brand" to="/" aria-label="猫的角落首页">
        <span class="brand-mark">🐾</span>
        <span>猫的角落</span>
      </RouterLink>
      <nav class="site-nav" aria-label="主要导航">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/records">我的记录</RouterLink>
        <RouterLink to="/emotions">今天的内心</RouterLink>
        <RouterLink class="nav-action" to="/records/new">写下今天</RouterLink>
      </nav>
    </header>

    <main class="site-main">
      <RouterView />
    </main>

    <footer class="site-footer">
      <span>不用一下子做完，今天也有一点点进展。</span>
      <span>Vue 3 + Spring Boot 3 · 猫的角落</span>
    </footer>
  </div>
</template>
