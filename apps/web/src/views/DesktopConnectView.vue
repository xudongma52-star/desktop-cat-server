<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { authorizeDesktop } from '../api/desktop-auth'
import { describeApiError } from '../api/http'
import AuthLayout from '../components/AuthLayout.vue'

const route = useRoute()
const authorizing = ref(false)
const error = ref('')

const deviceName = computed(() => queryValue('device_name') || '这台电脑')

function queryValue(name: string): string {
  const value = route.query[name]
  return typeof value === 'string' ? value : ''
}

async function connectDesktop(): Promise<void> {
  if (authorizing.value) return
  error.value = ''
  authorizing.value = true

  try {
    const authorization = await authorizeDesktop({
      redirectUri: queryValue('redirect_uri'),
      codeChallenge: queryValue('code_challenge'),
      state: queryValue('state'),
      deviceName: queryValue('device_name'),
      platform: queryValue('platform'),
      appVersion: queryValue('app_version') || undefined,
    })
    window.location.assign(authorization.callbackUrl)
  } catch (caught) {
    error.value = describeApiError(caught, '暂时无法连接桌面猫，请回到桌面端重试。')
    authorizing.value = false
  }
}

onMounted(() => void connectDesktop())
</script>

<template>
  <AuthLayout
    kicker="连接桌面猫"
    title="正在让小猫认出你。"
    :description="`${deviceName} 将使用当前账号，不会读取你的密码。`"
  >
    <div class="desktop-connect-status" aria-live="polite">
      <span class="desktop-connect-paw" aria-hidden="true">🐾</span>
      <p v-if="authorizing">正在安全地返回桌面猫，请稍候……</p>
      <p v-else-if="error" class="auth-error" role="alert">{{ error }}</p>
    </div>

    <button
      v-if="!authorizing"
      class="auth-submit"
      type="button"
      @click="connectDesktop"
    >
      <span>重新连接</span>
      <span aria-hidden="true">→</span>
    </button>

    <template #footer>
      <span>连接完成后，这个页面会自动关闭。</span>
    </template>
  </AuthLayout>
</template>

<style scoped>
.desktop-connect-status {
  display: grid;
  justify-items: center;
  min-height: 118px;
  padding: 24px 18px;
  border: 1px solid #e5ddcf;
  border-radius: 16px;
  background: #fffdf8;
  color: #6f786f;
  text-align: center;
}

.desktop-connect-status p { margin: 12px 0 0; line-height: 1.7; }
.desktop-connect-paw { font-size: 30px; }
</style>
