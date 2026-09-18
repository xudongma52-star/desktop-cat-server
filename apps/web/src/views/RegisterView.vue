<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { describeAuthError } from '../api/auth'
import AuthLayout from '../components/AuthLayout.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const showPassword = ref(false)
const submitting = ref(false)
const error = ref('')

function validate(): string {
  const normalizedUsername = username.value.trim()
  const usernameLength = [...normalizedUsername].length
  if (!normalizedUsername) return '请给自己取一个用户名。'
  if (usernameLength < 3) return '用户名至少需要 3 个字符。'
  if (usernameLength > 32) return '用户名不能超过 32 个字符。'
  if (!password.value) return '请设置一个密码。'
  if ([...password.value].length < 6) return '密码至少需要 6 个字符。'
  if (new TextEncoder().encode(password.value).length > 72) return '密码包含的内容太长了，请稍微精简一些。'
  if (password.value !== confirmPassword.value) return '两次输入的密码不一致。'
  return ''
}

async function submit() {
  if (submitting.value) return
  error.value = validate()
  if (error.value) return

  submitting.value = true
  try {
    await auth.register({ username: username.value, password: password.value })
    await router.replace('/')
  } catch (caught) {
    error.value = describeAuthError(caught, '账号暂时没有创建成功，请稍后再试。')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AuthLayout
    kicker="从今天开始"
    title="从一个名字开始。"
    description="不用准备很多，用户名和密码就够了。"
  >
    <form class="auth-form" novalidate @submit.prevent="submit">
      <label class="auth-field" for="register-username">
        <span>用户名 <small>3～32 个字符，保留大小写</small></span>
        <input
          id="register-username"
          v-model="username"
          name="username"
          type="text"
          maxlength="32"
          autocomplete="username"
          placeholder="例如：MaxCat"
          :aria-invalid="Boolean(error)"
          autofocus
        />
      </label>

      <label class="auth-field" for="register-password">
        <span>密码 <small>至少 6 个字符</small></span>
        <span class="password-input">
          <input
            id="register-password"
            v-model="password"
            name="password"
            :type="showPassword ? 'text' : 'password'"
            autocomplete="new-password"
            placeholder="设置一个容易记住的密码"
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

      <label class="auth-field" for="register-confirm-password">
        <span>再输入一次密码</span>
        <input
          id="register-confirm-password"
          v-model="confirmPassword"
          name="confirmPassword"
          :type="showPassword ? 'text' : 'password'"
          autocomplete="new-password"
          placeholder="确认刚才的密码"
          :aria-invalid="Boolean(error)"
        />
      </label>

      <p v-if="error" class="auth-error" role="alert">{{ error }}</p>

      <button class="auth-submit" type="submit" :disabled="submitting">
        <span>{{ submitting ? '正在准备你的小窝…' : '创建我的小窝' }}</span>
        <span aria-hidden="true">→</span>
      </button>
      <p class="auth-assurance"><span aria-hidden="true">◌</span> 密码只会以加密后的形式保存。</p>
    </form>

    <template #footer>
      <span>已经有账号了？</span>
      <RouterLink to="/login">回到登录</RouterLink>
    </template>
  </AuthLayout>
</template>
