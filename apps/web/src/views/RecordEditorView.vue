<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError, describeApiError } from '../api/http'
import { createRecord, getRecord, todayInLocalTime, updateRecord } from '../api/records'
import type { RecordInput, RecordType } from '../api/records'

interface RecordFormState {
  recordType: RecordType
  title: string
  content: string
  recordDate: string
  mood: string
  recallEnabled: boolean
  ragEnabled: boolean
}

const route = useRoute()
const router = useRouter()
const recordId = computed(() => {
  const value = Number(route.params.recordId)
  return Number.isInteger(value) && value > 0 ? value : null
})
const editing = computed(() => recordId.value !== null)
const version = ref<number | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const fieldError = ref('')
const form = reactive<RecordFormState>(emptyForm())
let loadGeneration = 0

function emptyForm(): RecordFormState {
  return {
    recordType: 'DIARY',
    title: '',
    content: '',
    recordDate: todayInLocalTime(),
    mood: '',
    recallEnabled: false,
    ragEnabled: false,
  }
}

function resetForm() {
  Object.assign(form, emptyForm())
  version.value = null
  error.value = ''
  fieldError.value = ''
}

async function loadRecord(): Promise<boolean> {
  const generation = ++loadGeneration
  resetForm()
  const requestedRecordId = recordId.value
  if (!requestedRecordId) {
    loading.value = false
    return false
  }
  loading.value = true
  try {
    const record = await getRecord(requestedRecordId)
    if (generation !== loadGeneration) return false
    form.recordType = record.recordType
    form.title = record.title ?? ''
    form.content = record.content
    form.recordDate = record.recordDate
    form.mood = record.mood ?? ''
    form.recallEnabled = record.recallEnabled
    form.ragEnabled = record.ragEnabled
    version.value = record.version
    return true
  } catch (caught) {
    if (generation !== loadGeneration) return false
    error.value = describeApiError(caught, '这篇记录暂时没有加载成功，请稍后再试。')
    return false
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

function validate(): boolean {
  fieldError.value = ''
  if (!form.content.trim()) {
    fieldError.value = '请写下一些内容后再保存。'
    return false
  }
  if (!form.recordDate) {
    fieldError.value = '请选择这篇记录对应的日期。'
    return false
  }
  return true
}

function buildInput(): RecordInput {
  return {
    recordType: form.recordType,
    title: form.title.trim() || null,
    content: form.content,
    recordDate: form.recordDate,
    mood: form.mood.trim() || null,
    recallEnabled: form.recallEnabled,
    ragEnabled: form.ragEnabled,
  }
}

async function saveRecord() {
  if (saving.value || !validate()) return
  saving.value = true
  error.value = ''
  try {
    const input = buildInput()
    const saved = editing.value && recordId.value && version.value !== null
      ? await updateRecord(recordId.value, { ...input, version: version.value })
      : await createRecord(input)
    await router.push(`/records/${saved.recordId}`)
  } catch (caught) {
    if (caught instanceof ApiError && (caught.status === 409 || caught.code.includes('VERSION_CONFLICT'))) {
      const reloaded = await loadRecord()
      if (reloaded) {
        error.value = '这篇记录已在别处更新。已重新载入最新内容，请确认后再保存。'
      }
    } else {
      error.value = describeApiError(caught, '保存失败，请稍后再试。')
      if (caught instanceof ApiError && caught.details.length) {
        fieldError.value = caught.details.map((detail) => detail.message).join('；')
      }
    }
  } finally {
    saving.value = false
  }
}

watch(() => route.fullPath, () => void loadRecord(), { immediate: true })
onBeforeUnmount(() => {
  loadGeneration += 1
})
</script>

<template>
  <div class="editor-page content-page narrow-page">
    <div class="breadcrumb">
      <RouterLink to="/records">← 返回记录</RouterLink>
      <span>/</span>
      <span>{{ editing ? '编辑记录' : '写新记录' }}</span>
    </div>

    <section class="editor-shell" aria-labelledby="editor-title">
      <div class="editor-heading">
        <p class="eyebrow">{{ editing ? 'EDIT A STORY' : 'WRITE IT DOWN' }}</p>
        <h1 id="editor-title">{{ editing ? '把想说的话再整理一下。' : '现在，想留下些什么？' }}</h1>
        <p>{{ editing ? '修改后的内容会保留在同一篇记录中。' : '不用追求完整，真实地写下来就很好。' }}</p>
      </div>

      <div v-if="loading" class="state-panel" role="status">正在打开这篇记录…</div>
      <div v-else-if="error && editing && version === null" class="state-panel error" role="alert">
        <p>{{ error }}</p>
        <button type="button" class="button secondary" @click="loadRecord">重新加载</button>
      </div>
      <form v-else class="record-form" @submit.prevent="saveRecord">
        <p v-if="error" class="inline-alert" role="alert">{{ error }}</p>

        <div class="form-grid two-columns">
          <label class="field">
            <span>记录类型 <b aria-hidden="true">*</b></span>
            <select v-model="form.recordType" required>
              <option value="DIARY">日记</option>
              <option value="THOUGHT">心得</option>
              <option value="WORK_NOTE">实习笔记</option>
            </select>
          </label>
          <label class="field">
            <span>记录日期 <b aria-hidden="true">*</b></span>
            <input v-model="form.recordDate" type="date" required />
          </label>
        </div>

        <label class="field">
          <span>标题 <small>选填</small></span>
          <input v-model="form.title" type="text" maxlength="120" placeholder="给今天起一个名字" />
          <small class="field-count">{{ form.title.length }} / 120</small>
        </label>

        <label class="field">
          <span>正文 <b aria-hidden="true">*</b></span>
          <textarea v-model="form.content" rows="14" placeholder="今天发生了什么？你有什么感受或新发现？" required></textarea>
        </label>

        <label class="field">
          <span>今天的心情 <small>选填</small></span>
          <input v-model="form.mood" type="text" maxlength="32" placeholder="例如：平静、开心、疲惫但满足" />
          <small class="field-count">{{ form.mood.length }} / 32</small>
        </label>

        <fieldset class="preference-fields">
          <legend>让这篇记录去哪里</legend>
          <label class="toggle-field">
            <input v-model="form.recallEnabled" type="checkbox" />
            <span class="toggle-control" aria-hidden="true"></span>
            <span><strong>加入温馨回忆</strong><small>它会出现在首页的文章轮播中。</small></span>
          </label>
          <label class="toggle-field">
            <input v-model="form.ragEnabled" type="checkbox" />
            <span class="toggle-control" aria-hidden="true"></span>
            <span><strong>允许 AI 检索</strong><small>为以后的知识库预留，你可以随时关闭。</small></span>
          </label>
        </fieldset>

        <p v-if="fieldError" class="field-error" role="alert">{{ fieldError }}</p>
        <div class="form-actions">
          <RouterLink class="button secondary" :to="editing && recordId ? `/records/${recordId}` : '/records'">取消</RouterLink>
          <button class="button prominent" type="submit" :disabled="saving">
            {{ saving ? '保存中…' : editing ? '保存修改' : '保存这篇记录' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
