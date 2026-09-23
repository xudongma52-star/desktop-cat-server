<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { describeApiError } from '../api/http'
import { getEmotions, todayInLocalTime } from '../api/emotions'
import type { Emotion } from '../api/emotions'

const route = useRoute()
const router = useRouter()
const today = todayInLocalTime()
const recordDate = ref(today)
const emotions = ref<Emotion[]>([])
const loading = ref(true)
const error = ref('')
let latestLoadId = 0
const isToday = computed(() => recordDate.value === today)
const heading = computed(() => isToday.value ? '今天的内心' : '那天的内心')
const listHeading = computed(() => isToday.value ? '今天和小猫说过的话' : '那天和小猫说过的话')
const formattedDate = computed(() => formatDate(recordDate.value))

function isValidDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  const parsed = new Date(year, month - 1, day, 12)
  return parsed.getFullYear() === year
    && parsed.getMonth() === month - 1
    && parsed.getDate() === day
}

function normalizeRouteDate(value: unknown): string {
  if (typeof value !== 'string' || !isValidDate(value) || value > today) return today
  return value
}

function addDays(value: string, days: number): string {
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(year, month - 1, day, 12)
  date.setDate(date.getDate() + days)
  const nextYear = date.getFullYear()
  const nextMonth = String(date.getMonth() + 1).padStart(2, '0')
  const nextDay = String(date.getDate()).padStart(2, '0')
  return `${nextYear}-${nextMonth}-${nextDay}`
}

function formatDate(value: string): string {
  return new Date(`${value}T12:00:00`).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  })
}

function formatTime(value: string): string {
  return new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

async function loadEmotions(date = recordDate.value) {
  const loadId = ++latestLoadId
  loading.value = true
  error.value = ''
  try {
    const result = await getEmotions(date)
    if (loadId === latestLoadId) emotions.value = result
  } catch (caught) {
    if (loadId === latestLoadId) {
      error.value = describeApiError(caught, '这一天的内容暂时没有加载成功，请稍后再试。')
    }
  } finally {
    if (loadId === latestLoadId) loading.value = false
  }
}

function openDate(date: string) {
  if (!isValidDate(date) || date > today) return
  void router.push(date === today
    ? { name: 'emotions' }
    : { name: 'emotions', query: { date } })
}

function openPreviousDay() {
  openDate(addDays(recordDate.value, -1))
}

function openNextDay() {
  if (!isToday.value) openDate(addDays(recordDate.value, 1))
}

function handleDateInput(event: Event) {
  const input = event.target as HTMLInputElement
  if (!isValidDate(input.value) || input.value > today) {
    input.value = recordDate.value
    return
  }
  openDate(input.value)
}

watch(
  () => route.query.date,
  (routeDate) => {
    const normalizedDate = normalizeRouteDate(routeDate)
    const shouldUseCanonicalTodayUrl = routeDate !== undefined && normalizedDate === today
    if (shouldUseCanonicalTodayUrl) {
      void router.replace({ name: 'emotions' })
      return
    }
    recordDate.value = normalizedDate
    void loadEmotions(normalizedDate)
  },
  { immediate: true },
)
</script>

<template>
  <div class="emotion-page content-page narrow-page">
    <section class="page-heading emotion-heading">
      <div>
        <p class="eyebrow">DAYS WITH MY CAT</p>
        <h1>{{ heading }}</h1>
        <p>不用整理成文章，说给小猫听的话都会按日期安静地留在这里。</p>
      </div>
      <time :datetime="recordDate">{{ formattedDate }}</time>
    </section>

    <nav class="emotion-date-nav" aria-label="选择要回看的日期">
      <button type="button" class="button secondary" @click="openPreviousDay">← 前一天</button>
      <label>
        <span>回看日期</span>
        <input type="date" :value="recordDate" :max="today" @change="handleDateInput" />
      </label>
      <button type="button" class="button secondary" :disabled="isToday" @click="openNextDay">后一天 →</button>
      <button v-if="!isToday" type="button" class="button text-button" @click="openDate(today)">回到今天</button>
    </nav>
    <p class="emotion-date-note">可以回看过去；新心情只会记在今天，不能补写或预写其他日期。</p>

    <section class="emotion-panel" aria-labelledby="emotion-list-title">
      <div class="emotion-panel-title">
        <h2 id="emotion-list-title">{{ listHeading }}</h2>
        <span>{{ emotions.length }} 条</span>
      </div>

      <div v-if="loading" class="state-panel" role="status">正在翻开这一天的心事…</div>
      <div v-else-if="error" class="state-panel error" role="alert">
        <p>{{ error }}</p>
        <button type="button" class="button secondary" @click="loadEmotions()">重新加载</button>
      </div>
      <div v-else-if="emotions.length === 0" class="state-panel emotion-empty">
        <span class="empty-icon" aria-hidden="true">🐾</span>
        <div>
          <h3>{{ isToday ? '今天还没有说什么' : '这一天没有留下心情' }}</h3>
          <p>{{ isToday ? '想到什么时，直接告诉桌面上的小猫就好。' : '也许那天很安静，或者还没有开始记录。' }}</p>
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
