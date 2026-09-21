<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { describeApiError } from '../api/http'
import { formatRecordDate, getRecallRecords, recordTypeLabels } from '../api/records'
import type { RecallRecord } from '../api/records'

const records = ref<RecallRecord[]>([])
const currentIndex = ref(0)
const loading = ref(true)
const error = ref('')
const paused = ref(false)
const interacting = ref(false)
const reducedMotion = ref(false)
let timer: number | undefined
let motionQuery: MediaQueryList | undefined
let loadGeneration = 0
let disposed = false

const currentRecord = computed(() => records.value[currentIndex.value] ?? null)
const canRotate = computed(() => records.value.length > 1)
const isAutoPaused = computed(() => paused.value || interacting.value || reducedMotion.value)

function stopTimer() {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
}

function startTimer() {
  stopTimer()
  if (disposed || !canRotate.value || isAutoPaused.value) return
  timer = window.setInterval(() => {
    currentIndex.value = (currentIndex.value + 1) % records.value.length
  }, 2000)
}

function move(step: number) {
  if (!canRotate.value) return
  currentIndex.value = (currentIndex.value + step + records.value.length) % records.value.length
  startTimer()
}

function togglePause() {
  paused.value = !paused.value
  startTimer()
}

function setInteracting(value: boolean) {
  interacting.value = value
  startTimer()
}

function handleMotionChange(event: MediaQueryListEvent) {
  reducedMotion.value = event.matches
  startTimer()
}

async function loadRecalls() {
  const generation = ++loadGeneration
  if (disposed) return
  loading.value = true
  error.value = ''
  stopTimer()
  try {
    const nextRecords = await getRecallRecords(10)
    if (disposed || generation !== loadGeneration) return
    records.value = nextRecords
    currentIndex.value = 0
  } catch (caught) {
    if (disposed || generation !== loadGeneration) return
    error.value = describeApiError(caught, '温馨回忆暂时没有加载成功，请稍后再试。')
  } finally {
    if (disposed || generation !== loadGeneration) return
    loading.value = false
    startTimer()
  }
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = motionQuery.matches
  motionQuery.addEventListener('change', handleMotionChange)
  void loadRecalls()
})

onBeforeUnmount(() => {
  disposed = true
  loadGeneration += 1
  stopTimer()
  motionQuery?.removeEventListener('change', handleMotionChange)
})
</script>

<template>
  <section
    class="recall-section"
    aria-labelledby="recall-title"
    @mouseenter="setInteracting(true)"
    @mouseleave="setInteracting(false)"
    @focusin="setInteracting(true)"
    @focusout="setInteracting(false)"
  >
    <div class="section-top">
      <div>
        <p class="eyebrow">温馨回忆 · 文章</p>
        <h2 id="recall-title">偶尔回头看看，走过的路也很温柔</h2>
      </div>
      <RouterLink class="text-link" to="/records">查看全部记录 <span aria-hidden="true">→</span></RouterLink>
    </div>

    <div v-if="loading" class="state-panel" role="status">正在翻找你珍藏的文字…</div>
    <div v-else-if="error" class="state-panel error" role="alert">
      <p>{{ error }}</p>
      <button type="button" class="button secondary" @click="loadRecalls">重新加载</button>
    </div>
    <div v-else-if="!currentRecord" class="state-panel recall-empty">
      <span class="empty-icon" aria-hidden="true">🍃</span>
      <div>
        <h3>第一段回忆还在等你写下</h3>
        <p>新建文章时打开“加入温馨回忆”，它就会出现在这里。</p>
      </div>
      <RouterLink class="button" to="/records/new">写下第一篇</RouterLink>
    </div>
    <article v-else class="recall-card" aria-live="polite">
      <div class="recall-meta">
        <span>{{ recordTypeLabels[currentRecord.recordType] }}</span>
        <time :datetime="currentRecord.recordDate">{{ formatRecordDate(currentRecord.recordDate) }}</time>
        <span v-if="currentRecord.mood">今日心情 · {{ currentRecord.mood }}</span>
      </div>
      <h3>{{ currentRecord.title || '没有标题的一天' }}</h3>
      <p class="recall-excerpt">{{ currentRecord.excerpt }}</p>
      <div class="recall-footer">
        <RouterLink class="text-link strong" :to="`/records/${currentRecord.recordId}`">读完这段回忆 <span aria-hidden="true">→</span></RouterLink>
        <div v-if="canRotate" class="carousel-controls" aria-label="回忆轮播控制">
          <span class="carousel-count">{{ currentIndex + 1 }} / {{ records.length }}</span>
          <button type="button" class="icon-button" aria-label="上一篇回忆" @click="move(-1)">←</button>
          <button type="button" class="icon-button" aria-label="下一篇回忆" @click="move(1)">→</button>
          <button type="button" class="pause-button" :disabled="reducedMotion" @click="togglePause">
            {{ reducedMotion ? '已关闭自动轮播' : paused ? '继续轮播' : '暂停轮播' }}
          </button>
        </div>
      </div>
      <div v-if="canRotate" class="carousel-progress" aria-hidden="true">
        <span :style="{ width: `${((currentIndex + 1) / records.length) * 100}%` }"></span>
      </div>
    </article>
  </section>
</template>
