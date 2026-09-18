<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { describeAuthError } from '../api/auth'
import AuthLayout from '../components/AuthLayout.vue'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const error = ref('')

const connectionNotice = computed(() => auth.initializationFailed
  ? '暂时没有连接到后端，启动服务后就可以登录。'
  : '')

function destination(): string {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  return redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/'
}

async function submit() {
  if (submitting.value) return
  error.value = ''

  if (!username.value.trim() || !password.value) {
    error.value = '请把用户名和密码都填写完整。'
    return
  }

  submitting.value = true
  try {
    await auth.login({ username: username.value, password: password.value })
    await router.replace(destination())
  } catch (caught) {
    error.value = describeAuthError(caught, '登录没有成功，请稍后再试。')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout
    kicker="回到你的角落"
    title="欢迎回来。"
    description="你的记录都在这里。"
  >
    <p v-if="connectionNotice" class="auth-notice" role="status">{{ connectionNotice }}</p>

    <form class="auth-form" novalidate @submit.prevent="submit">
      <label class="auth-field" for="login-username">
        <span>用户名</span>
        <input
          id="login-username"
          v-model="username"
          name="username"
          type="text"
          maxlength="32"
          autocomplete="username"
          placeholder="输入你的用户名"
          :aria-invalid="Boolean(error)"
          autofocus
        />
      </label>

      <label class="auth-field" for="login-password">
        <span>密码</span>
        <span class="password-input">
          <input
            id="login-password"
            v-model="password"
            name="password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password"
            placeholder="输入你的密码"
            :aria-invalid="Boolean(error)"
          />
          <button
            class="password-toggle"
            type="button"
            :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            @click="showPassword = !showPassword"
          >{{ showPassword ? '隐藏' : '显示' }}</button>
        </span>
      </label>

      <p v-if="error" class="auth-error" role="alert">{{ error }}</p>

      <button class="auth-submit" type="submit" :disabled="submitting">
        <span>{{ submitting ? '正在回到小窝…' : '进入我的小窝' }}</span>
        <span aria-hidden="true">→</span>
      </button>
    </form>

    <template #footer>
      <span>第一次来到这里？</span>
      <RouterLink to="/register">创建一个账号</RouterLink>
    </template>
  </AuthLayout>
</template>
