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
  <div class="page">
    <header class="header">
      <a class="brand" href="/" aria-label="猫的角落首页"><span class="brand-mark">🐾</span> 猫的角落</a>
      <span class="header-note">一个慢慢长大的个人空间</span>
    </header>

    <main>
      <section class="intro">
        <div>
          <p class="eyebrow">MY LITTLE CORNER / 001</p>
          <h1>把生活的碎片，<br />留在一个温暖的地方。</h1>
          <p class="intro-text">记下今天，找回回忆。以后，还会有你的小猫陪在这里。<br />今天先走一小步：让这个空间真正运行起来。</p>
        </div>
        <div class="cat-card" aria-hidden="true">
          <span class="cat-face">🐱</span>
          <span>小猫正在来的路上</span>
          <span class="cat-caption">未来的桌面伙伴</span>
        </div>
      </section>

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

      <section class="profile-settings" aria-labelledby="profile-title">
        <div class="section-top">
          <div><p class="eyebrow">第二步 · 认识</p><h2 id="profile-title">给你的小猫一个名字</h2></div>
          <span v-if="profile" class="muted">资料 #{{ profile.profileId }} · v{{ profile.version }}</span>
        </div>
        <p class="section-description">这里保存的名字会同步到桌面小猫。最多 20 个字符，以后还可以在同一份资料里加入生日、性格和外观。</p>
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

      <section class="future" aria-labelledby="future-title">
        <div class="section-top"><h2 id="future-title">慢慢填满这个角落</h2><span class="muted">接下来的方向</span></div>
        <div class="feature-grid">
          <article><span class="feature-number">01 / 记录</span><h3>今天，想留下什么？</h3><p>日记、心得，或是实习中终于弄明白的一件事。</p><span class="planned">待开发</span></article>
          <article><span class="feature-number">02 / 回忆</span><h3>重温那些小小的温暖</h3><p>喜欢的话、照片和回忆，在疲惫时陪你歇一会儿。</p><span class="planned">待开发</span></article>
          <article><span class="feature-number">03 / 陪伴</span><h3>一只只属于你的小猫</h3><p>摸摸头、喂一块冻干，也帮你记住今天的事情。</p><span class="planned">待开发</span></article>
        </div>
      </section>
    </main>
    <footer>不用一下子做完，今天也有一点点进展。<span>Vue 3 + Spring Boot 3 · 起步框架</span></footer>
  </div>
</template>
