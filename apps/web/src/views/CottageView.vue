<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getSystemStatus } from '../api/system'
import type { SystemStatus } from '../api/system'
import { ApiError, getCatProfile, updateCatName } from '../api/cat-profile'
import type { CatProfile } from '../api/cat-profile'

const checking = ref(false)
const status = ref<SystemStatus | null>(null)
const error = ref('')
const profile = ref<CatProfile | null>(null)
const catName = ref('')
const profileError = ref('')
const profileNotice = ref('')
const savingProfile = ref(false)
const connectionLabel = computed(() => checking.value ? '正在连接' : status.value ? '前后端已连通' : '等待连接')

async function checkConnection() {
  if (checking.value) return
  checking.value = true
  error.value = ''
  status.value = null
  try {
    status.value = await getSystemStatus()
  } catch {
    error.value = '还没有连接到后端。请确认 Spring Boot 已在 8080 端口启动，再试一次。'
  } finally {
    checking.value = false
  }
}

async function loadProfile() {
  profileError.value = ''
  try {
    profile.value = await getCatProfile()
    catName.value = profile.value.catName
  } catch {
    profileError.value = '暂时读不到小猫资料，请确认后端已经启动。'
  }
}

async function saveCatName() {
  if (!profile.value || savingProfile.value) return
  profileError.value = ''
  profileNotice.value = ''
  savingProfile.value = true
  try {
    profile.value = await updateCatName(profile.value, catName.value)
    catName.value = profile.value.catName
    profileNotice.value = `已经记住啦，桌面上的${profile.value.catName}很快就会收到新名字。`
  } catch (caught) {
    if (caught instanceof ApiError && caught.code === 'CAT_PROFILE_VERSION_CONFLICT') {
      profileError.value = '资料已在别处更新，已为你载入最新内容，请再确认一次。'
      await loadProfile()
    } else if (caught instanceof ApiError) {
      profileError.value = `${caught.message}（请求编号：${caught.requestId}）`
    } else {
      profileError.value = '保存失败，请稍后再试。'
    }
  } finally {
    savingProfile.value = false
  }
}

onMounted(() => {
  void checkConnection()
  void loadProfile()
})
</script>

<template>
  <div class="content-page narrow-page cottage-page">
    <header class="page-heading"><div><p class="eyebrow">属于你的小小天地</p><h1>我的小屋</h1><p>照顾小猫，也照顾自己的生活。</p></div></header>
    <div class="cottage-links"><RouterLink to="/emotions">今天的内心 ↗</RouterLink><RouterLink to="/reminders">我的提醒 ↗</RouterLink><a href="/downloads/desktop-cat-windows-x64-setup.exe" download>下载桌面猫 ↓</a></div>
      <section class="profile-settings" aria-labelledby="profile-title">
        <div class="section-top">
          <div><p class="eyebrow">陪在身边的小伙伴</p><h2 id="profile-title">给你的小猫一个名字</h2></div>
        </div>
        <p class="section-description">取一个喜欢的名字，桌面上的小猫也会记住。最多 20 个字符。</p>
        <form class="profile-form" @submit.prevent="saveCatName">
          <label for="cat-name">小猫名字</label>
          <div class="profile-input-row">
            <input id="cat-name" v-model="catName" maxlength="20" :disabled="!profile || savingProfile" placeholder="例如：小饼干" autocomplete="off" />
            <button type="submit" :disabled="!profile || savingProfile">{{ savingProfile ? '保存中…' : '保存名字' }}</button>
          </div>
          <p v-if="profileError" class="form-message error" role="alert">{{ profileError }}</p>
          <p v-else-if="profileNotice" class="form-message success" role="status">{{ profileNotice }}</p>
          <p v-else class="form-message">{{ profile ? `当前名字：${profile.catName}` : '正在读取小猫资料…' }}</p>
        </form>
      </section>


    <details class="connection-details"><summary>连接状态与诊断</summary>
      <section class="connection" aria-labelledby="connection-title">
        <div class="section-top">
          <div><p class="eyebrow">第一步 · 连接</p><h2 id="connection-title">从一个真实的回应开始</h2></div>
          <span class="badge" :class="{ online: status, pending: checking }" role="status"><span class="dot"></span>{{ connectionLabel }}</span>
        </div>
        <p class="section-description">这个页面会向 Java 后端发送请求，下方展示的是实际返回的信息。</p>
        <div v-if="status" class="runtime-grid">
          <div><span>应用</span><strong>{{ status.application }}</strong></div>
          <div><span>Spring Boot</span><strong>{{ status.springBootVersion }}</strong></div>
          <div><span>Java</span><strong>{{ status.javaVersion }}</strong></div>
        </div>
        <p v-else class="empty-message" :class="{ error }" role="status">{{ error || '正在等待后端的第一声回应…' }}</p>
        <div class="connection-footer">
          <span v-if="status">最近回应：{{ new Date(status.timestamp).toLocaleString('zh-CN') }}</span>
          <span v-else>Vue → /api/system/status → Spring Boot</span>
          <button type="button" :disabled="checking" @click="checkConnection">{{ checking ? '连接中…' : '重新检查连接' }} <span aria-hidden="true">↗</span></button>
        </div>
      </section>


    </details>
  </div>
</template>
