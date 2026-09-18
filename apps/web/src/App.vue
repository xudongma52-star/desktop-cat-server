<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loggingOut = ref(false)
const authLayout = computed(() => route.meta.layout === 'auth')

async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await auth.logout()
    await router.replace('/login')
  } finally {
    loggingOut.value = false
  }
}
</script>

<template>
  <div class="app-shell" :class="{ 'auth-mode': authLayout }">
    <header v-if="!authLayout" class="site-header">
      <RouterLink class="brand" to="/" aria-label="猫的角落首页">
        <span class="brand-mark">🐾</span>
        <span>猫的角落</span>
      </RouterLink>
      <nav class="site-nav" aria-label="主要导航">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/records">我的记录</RouterLink>
        <RouterLink to="/emotions">今天的内心</RouterLink>
        <RouterLink to="/reminders">提醒</RouterLink>
        <RouterLink class="nav-action" to="/records/new">写下今天</RouterLink>
        <span v-if="auth.user" class="nav-user" :title="auth.user.username">{{ auth.user.username }}</span>
        <button class="nav-logout" type="button" :disabled="loggingOut" @click="logout">
          {{ loggingOut ? '退出中' : '退出' }}
        </button>
      </nav>
    </header>

    <main class="site-main">
      <RouterView />
    </main>

    <footer v-if="!authLayout" class="site-footer">
      <span>不用一下子做完，今天也有一点点进展。</span>
      <span>Vue 3 + Spring Boot 3 · 猫的角落</span>
    </footer>
  </div>
</template>
