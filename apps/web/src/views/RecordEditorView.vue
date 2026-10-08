<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError, describeApiError } from '../api/http'
import { createRecord, getRecord, todayInLocalTime, updateRecord, uploadRecordImage } from '../api/records'
import type { RecordInput, RecordType } from '../api/records'
import MarkdownContent from '../components/MarkdownContent.vue'

interface RecordFormState {
  recordType: RecordType
  title: string
  content: string
  recordDate: string
  mood: string
  recallEnabled: boolean
  ragEnabled: boolean
  coverImageKey: string
  homeExcerpt: string
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
const previewing = ref(false)
const error = ref('')
const fieldError = ref('')
const imageUrl = ref('')
const imageUploading = ref(false)
let imageUpload: AbortController | undefined
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
    coverImageKey: '',
    homeExcerpt: '',
  }
}

function resetForm() {
  imageUpload?.abort()
  imageUploading.value = false
  imageUrl.value = ''
  Object.assign(form, emptyForm())
  previewing.value = false
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
    form.coverImageKey = record.coverImageKey ?? ''
    form.homeExcerpt = record.homeExcerpt ?? ''
    imageUrl.value = record.imageUrl ?? ''
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
  if (form.recallEnabled && !form.coverImageKey) {
    fieldError.value = '请为首页文章上传一张配图。'
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
    coverImageKey: form.coverImageKey,
    homeExcerpt: form.homeExcerpt,
  }
}

async function saveRecord() {
  if (saving.value || imageUploading.value || !validate()) return
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

async function chooseImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  imageUpload?.abort()
  const controller = new AbortController()
  imageUpload = controller
  imageUploading.value = true
  fieldError.value = ''
  try {
    const result = await uploadRecordImage(file, controller.signal)
    if (controller.signal.aborted) return
    form.coverImageKey = result.imageKey
    imageUrl.value = result.imageUrl
  } catch (caught) {
    if (!controller.signal.aborted) fieldError.value = describeApiError(caught, '配图上传失败。')
  } finally {
    if (imageUpload === controller) imageUploading.value = false
    input.value = ''
  }
}

watch(() => route.fullPath, () => void loadRecord(), { immediate: true })
onBeforeUnmount(() => {
  loadGeneration += 1
  imageUpload?.abort()
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
              <option value="NOTE">笔记</option>
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

        <div class="field">
          <div class="body-heading">
            <label for="record-content">正文 <b aria-hidden="true">*</b> <small>支持 Markdown</small></label>
            <button class="button secondary" type="button" :aria-pressed="previewing" :disabled="!form.content.trim()" @click="previewing = !previewing">
              {{ previewing ? '返回编辑' : '预览' }}
            </button>
          </div>
          <textarea v-show="!previewing" id="record-content" v-model="form.content" rows="14" placeholder="今天发生了什么？也可以直接粘贴 Markdown 内容。" required></textarea>
          <MarkdownContent v-if="previewing" class="body-preview" :content="form.content" />
        </div>

        <label class="field">
          <span>今天的心情 <small>选填</small></span>
          <input v-model="form.mood" type="text" maxlength="32" placeholder="例如：平静、开心、疲惫但满足" />
          <small class="field-count">{{ form.mood.length }} / 32</small>
        </label>

        <div class="article-image-field">
          <label class="article-image-upload">
            <span>{{ imageUploading ? '上传中…' : imageUrl ? '更换首页配图' : '上传首页配图' }}</span>
            <input type="file" accept="image/jpeg,image/png,image/webp" :disabled="imageUploading || saving" @change="chooseImage" />
          </label>
          <img v-if="imageUrl" :src="imageUrl" alt="首页配图预览" />
        </div>
        <label class="field">
          <span>首页摘要 <small>选填，留空时从正文生成</small></span>
          <textarea v-model="form.homeExcerpt" rows="3" placeholder="想在首页展示的一段文字" />
        </label>

        <fieldset class="preference-fields">
          <legend>让这篇记录去哪里</legend>
          <label class="toggle-field">
            <input v-model="form.recallEnabled" type="checkbox" />
            <span class="toggle-control" aria-hidden="true"></span>
            <span><strong>加入首页图文</strong><small>搭配一张图片，在首页展示这篇文章。</small></span>
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
          <button class="button prominent" type="submit" :disabled="saving || imageUploading">
            {{ saving ? '保存中…' : editing ? '保存修改' : '保存这篇记录' }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>

<style scoped>
.article-image-field { display: grid; gap: 14px; justify-items: start; }
.article-image-field img { max-width: 100%; max-height: 360px; object-fit: contain; }
.article-image-upload { cursor: pointer; color: #4e6755; }
.article-image-upload input { display: block; margin-top: 8px; max-width: 100%; }
.body-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.body-heading b { color: #a55f51; }
.body-heading small { margin-left: 5px; color: #959c94; font-weight: 400; }
.body-heading button { padding: 7px 12px; font-size: 12px; }
.body-preview { min-height: 300px; padding: 14px; border: 1px solid #d7ddd2; border-radius: 10px; background: #fbfcf8; color: #3f4d45; font-size: 15px; font-weight: 400; }
</style>
