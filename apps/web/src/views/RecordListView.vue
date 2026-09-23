<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WritingActivityHeatmap from '../components/WritingActivityHeatmap.vue'
import { RouterLink } from 'vue-router'
import { ApiError, describeApiError } from '../api/http'
import { deleteRecord, formatRecordDate, getRecords, recordTypeLabels } from '../api/records'
import type { RecordListItem, RecordPage, RecordType } from '../api/records'

const pageSize = 12
const selectedType = ref<RecordType | ''>('')
const result = ref<RecordPage | null>(null)
const loading = ref(true)
const error = ref('')
const deletingId = ref<number | null>(null)
let loadGeneration = 0
let requestedPage = 1

async function loadRecords(page = 1): Promise<RecordPage | null> {
  const generation = ++loadGeneration
  requestedPage = page
  const requestedType = selectedType.value
  loading.value = true
  error.value = ''
  try {
    const nextResult = await getRecords(page, pageSize, requestedType)
    if (generation !== loadGeneration) return null
    result.value = nextResult
    return nextResult
  } catch (caught) {
    if (generation !== loadGeneration) return null
    error.value = describeApiError(caught, '记录暂时没有加载成功，请稍后再试。')
    return null
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

async function removeRecord(record: RecordListItem) {
  if (deletingId.value !== null) return
  const displayTitle = record.title || '这篇无标题记录'
  if (!window.confirm(`确定删除“${displayTitle}”吗？删除后不会继续出现在列表和回忆中。`)) return

  deletingId.value = record.recordId
  error.value = ''
  try {
    await deleteRecord(record.recordId, record.version)
    const refreshed = await loadRecords(requestedPage)
    if (refreshed && refreshed.items.length === 0 && refreshed.page > 1) {
      await loadRecords(refreshed.page - 1)
    }
  } catch (caught) {
    if (caught instanceof ApiError && (caught.status === 409 || caught.code.includes('VERSION_CONFLICT'))) {
      const refreshed = await loadRecords(requestedPage)
      if (refreshed) {
        error.value = '这篇记录刚刚在别处更新过，列表已刷新。请确认内容后再删除。'
      }
    } else {
      error.value = describeApiError(caught, '删除失败，请稍后再试。')
    }
  } finally {
    deletingId.value = null
  }
}

function changeFilter() {
  void loadRecords(1)
}

onMounted(() => void loadRecords())
onBeforeUnmount(() => {
  loadGeneration += 1
})
</script>

<template>
  <div class="records-page content-page">
    <details class="activity-disclosure"><summary>我的写作足迹 · 展开年度回顾</summary><WritingActivityHeatmap /></details>
    <section class="page-heading">
      <div>
        <p class="eyebrow">MY STORIES</p>
        <h1>留下生活，也留下成长。</h1>
        <p>日记、心得和实习笔记，都可以安静地放在这里。</p>
      </div>
      <RouterLink class="button prominent" to="/records/new">＋ 写一篇记录</RouterLink>
    </section>

    <section class="records-panel" aria-labelledby="records-title">
      <div class="records-toolbar">
        <div>
          <h2 id="records-title">我的记录</h2>
          <p v-if="result">共 {{ result.total }} 篇</p>
        </div>
        <label class="filter-field">
          <span>按类型查看</span>
          <select v-model="selectedType" @change="changeFilter">
            <option value="">全部记录</option>
            <option value="DIARY">日记</option>
            <option value="THOUGHT">心得</option>
            <option value="WORK_NOTE">实习笔记</option>
          </select>
        </label>
      </div>

      <div v-if="loading && !result" class="state-panel" role="status">正在整理你的记录…</div>
      <div v-else-if="error && !result" class="state-panel error" role="alert">
        <p>{{ error }}</p>
        <button type="button" class="button secondary" @click="loadRecords()">重新加载</button>
      </div>
      <template v-else>
        <p v-if="error" class="inline-alert" role="alert">{{ error }}</p>
        <div v-if="result?.items.length" class="record-grid" :class="{ muted: loading }">
          <article v-for="record in result.items" :key="record.recordId" class="record-card">
            <div class="record-card-meta">
              <span class="type-chip">{{ recordTypeLabels[record.recordType] }}</span>
              <time :datetime="record.recordDate">{{ formatRecordDate(record.recordDate) }}</time>
            </div>
            <RouterLink class="record-card-title" :to="`/records/${record.recordId}`">
              <h3>{{ record.title || '没有标题的一天' }}</h3>
            </RouterLink>
            <p class="record-preview">{{ record.excerpt || record.content || '这篇记录暂时没有摘要。' }}</p>
            <div class="record-flags">
              <span v-if="record.mood">心情 · {{ record.mood }}</span>
              <span v-if="record.recallEnabled">温馨回忆</span>
              <span v-if="record.ragEnabled">允许 AI 检索</span>
            </div>
            <div class="record-card-actions">
              <RouterLink :to="`/records/${record.recordId}`">阅读全文</RouterLink>
              <RouterLink :to="`/records/${record.recordId}/edit`">编辑</RouterLink>
              <button type="button" :disabled="deletingId === record.recordId" @click="removeRecord(record)">
                {{ deletingId === record.recordId ? '删除中…' : '删除' }}
              </button>
            </div>
          </article>
        </div>
        <div v-else class="state-panel record-empty">
          <span class="empty-icon" aria-hidden="true">✎</span>
          <div>
            <h3>{{ selectedType ? '这个分类里还没有记录' : '今天想留下什么？' }}</h3>
            <p>{{ selectedType ? '可以换个分类看看，或者写下第一篇。' : '不需要写得完整，几句话也值得被记住。' }}</p>
          </div>
          <RouterLink class="button" to="/records/new">开始记录</RouterLink>
        </div>

        <nav v-if="result && result.totalPages > 1" class="pagination" aria-label="记录分页">
          <button type="button" class="button secondary" :disabled="loading || result.page <= 1" @click="loadRecords(result.page - 1)">上一页</button>
          <span>第 {{ result.page }} / {{ result.totalPages }} 页</span>
          <button type="button" class="button secondary" :disabled="loading || result.page >= result.totalPages" @click="loadRecords(result.page + 1)">下一页</button>
        </nav>
      </template>
    </section>
  </div>
</template>
