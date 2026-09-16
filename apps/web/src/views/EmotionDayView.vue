<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { describeApiError } from '../api/http'
import { getEmotions, todayInLocalTime } from '../api/emotions'
import type { Emotion } from '../api/emotions'

const recordDate = todayInLocalTime()
const emotions = ref<Emotion[]>([])
const loading = ref(true)
const error = ref('')

function formatTime(value: string): string {
  return new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

async function loadEmotions() {
  loading.value = true
  error.value = ''
  try {
    emotions.value = await getEmotions(recordDate)
  } catch (caught) {
    error.value = describeApiError(caught, '今天的内容暂时没有加载成功，请稍后再试。')
  } finally {
    loading.value = false
  }
}

onMounted(() => void loadEmotions())
</script>

<template>
  <div class="emotion-page content-page narrow-page">
    <section class="page-heading emotion-heading">
      <div>
        <p class="eyebrow">TODAY WITH MY CAT</p>
        <h1>今天的内心</h1>
        <p>不用整理成文章，说给小猫听的话都会安静地留在这里。</p>
      </div>
      <time :datetime="recordDate">{{ new Date(`${recordDate}T00:00:00`).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }) }}</time>
    </section>

    <section class="emotion-panel" aria-labelledby="emotion-list-title">
      <div class="emotion-panel-title">
        <h2 id="emotion-list-title">今天和小猫说过的话</h2>
        <span>{{ emotions.length }} 条</span>
      </div>

      <div v-if="loading" class="state-panel" role="status">正在翻开今天的心事…</div>
      <div v-else-if="error" class="state-panel error" role="alert">
        <p>{{ error }}</p>
        <button type="button" class="button secondary" @click="loadEmotions">重新加载</button>
      </div>
      <div v-else-if="emotions.length === 0" class="state-panel emotion-empty">
        <span class="empty-icon" aria-hidden="true">🐾</span>
        <div>
          <h3>今天还没有说什么</h3>
          <p>想到什么时，直接告诉桌面上的小猫就好。</p>
        </div>
      </div>
      <ol v-else class="emotion-timeline">
        <li v-for="emotion in emotions" :key="emotion.emotionId">
          <time :datetime="emotion.createdAt">{{ formatTime(emotion.createdAt) }}</time>
          <p>{{ emotion.content }}</p>
        </li>
      </ol>
    </section>
  </div>
</template>
