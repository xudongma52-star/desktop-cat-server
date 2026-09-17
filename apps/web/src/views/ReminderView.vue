<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ApiError, describeApiError } from '../api/http'
import {
  completeReminder,
  createReminder,
  deleteReminder,
  getReminders,
  updateReminder,
} from '../api/reminders'
import type { Reminder, ReminderScope } from '../api/reminders'

const reminders = ref<Reminder[]>([])
const scope = ref<ReminderScope>('TODAY')
const content = ref('')
const remindAt = ref(defaultReminderTime())
const editing = ref<Reminder | null>(null)
const loading = ref(true)
const saving = ref(false)
const actionId = ref<number | null>(null)
const error = ref('')
const formError = ref('')
let loadGeneration = 0

const heading = computed(() => scope.value === 'TODAY' ? '今天要记得的事' : '还没有完成的事')
const emptyText = computed(() => scope.value === 'TODAY'
  ? '今天暂时没有提醒，给未来的自己留一句话吧。'
  : '没有积压的待办，今天已经做得很好啦。')

function defaultReminderTime(): string {
  const date = new Date(Date.now() + 60 * 60_000)
  date.setMinutes(Math.ceil(date.getMinutes() / 5) * 5, 0, 0)
  return toLocalInput(date)
}

function toLocalInput(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatReminderTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

async function loadReminders(): Promise<void> {
  const generation = ++loadGeneration
  loading.value = true
  error.value = ''
  try {
    const result = await getReminders(scope.value)
    if (generation === loadGeneration) reminders.value = result
  } catch (caught) {
    if (generation === loadGeneration) {
      error.value = describeApiError(caught, '提醒暂时没有加载成功，请稍后再试。')
    }
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

function resetForm(): void {
  editing.value = null
  content.value = ''
  remindAt.value = defaultReminderTime()
  formError.value = ''
}

function startEditing(reminder: Reminder): void {
  editing.value = reminder
  content.value = reminder.content
  remindAt.value = toLocalInput(new Date(reminder.remindAt))
  formError.value = ''
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function saveReminder(): Promise<void> {
  if (saving.value) return
  formError.value = ''
  const normalizedContent = content.value.trim()
  if (!normalizedContent) {
    formError.value = '先写下要提醒自己的事情。'
    return
  }
  if (!remindAt.value) {
    formError.value = '请选择提醒时间。'
    return
  }
  const selectedTime = new Date(remindAt.value)
  if (Number.isNaN(selectedTime.getTime()) || selectedTime.getTime() < Date.now()) {
    formError.value = '提醒时间不能早于当前时间。'
    return
  }

  saving.value = true
  try {
    const draft = {
      content: normalizedContent,
      remindAt: selectedTime.toISOString(),
    }
    if (editing.value) {
      await updateReminder(editing.value, draft)
    } else {
      await createReminder(draft)
    }
    resetForm()
    await loadReminders()
  } catch (caught) {
    if (caught instanceof ApiError && caught.status === 409) {
      resetForm()
      formError.value = '这条提醒刚刚发生了变化，列表已刷新，请重新确认。'
      await loadReminders()
    } else if (caught instanceof ApiError && caught.code === 'REMINDER_TIME_IN_PAST') {
      formError.value = '提醒时间不能早于当前时间。'
    } else {
      formError.value = describeApiError(caught, '提醒没有保存成功，请稍后再试。')
    }
  } finally {
    saving.value = false
  }
}

async function markCompleted(reminder: Reminder): Promise<void> {
  if (actionId.value !== null) return
  actionId.value = reminder.reminderId
  error.value = ''
  try {
    await completeReminder(reminder)
    await loadReminders()
  } catch (caught) {
    error.value = describeApiError(caught, '暂时无法完成这条提醒。')
    if (caught instanceof ApiError && caught.status === 409) await loadReminders()
  } finally {
    actionId.value = null
  }
}

async function removeReminder(reminder: Reminder): Promise<void> {
  if (actionId.value !== null) return
  if (!window.confirm(`确定删除“${reminder.content}”吗？`)) return
  actionId.value = reminder.reminderId
  error.value = ''
  try {
    await deleteReminder(reminder)
    if (editing.value?.reminderId === reminder.reminderId) resetForm()
    await loadReminders()
  } catch (caught) {
    error.value = describeApiError(caught, '提醒没有删除成功，请稍后再试。')
    if (caught instanceof ApiError && caught.status === 409) await loadReminders()
  } finally {
    actionId.value = null
  }
}

function changeScope(nextScope: ReminderScope): void {
  if (scope.value === nextScope) return
  scope.value = nextScope
  void loadReminders()
}

onMounted(() => void loadReminders())
onBeforeUnmount(() => {
  loadGeneration += 1
})
</script>

<template>
  <div class="reminder-page content-page">
    <section class="page-heading reminder-heading">
      <div>
        <p class="eyebrow">A LITTLE NUDGE</p>
        <h1>让小猫替你记住。</h1>
        <p>写下一件要做的事，到了时间，它会从桌面上提醒你。</p>
      </div>
      <span class="reminder-hero-icon" aria-hidden="true">⏰</span>
    </section>

    <section class="reminder-editor" aria-labelledby="reminder-form-title">
      <div>
        <p class="eyebrow">{{ editing ? '正在修改' : '新的提醒' }}</p>
        <h2 id="reminder-form-title">{{ editing ? '换个时间或说法' : '有什么事情不能忘记？' }}</h2>
      </div>
      <form class="reminder-form" @submit.prevent="saveReminder">
        <label class="field">
          <span>提醒内容 <b>*</b></span>
          <input v-model="content" maxlength="200" placeholder="例如：站起来活动一下" autocomplete="off" />
        </label>
        <label class="field">
          <span>提醒时间 <b>*</b></span>
          <input v-model="remindAt" type="datetime-local" />
        </label>
        <div class="reminder-form-actions">
          <button v-if="editing" type="button" class="button secondary" @click="resetForm">取消修改</button>
          <button type="submit" :disabled="saving">{{ saving ? '保存中…' : editing ? '保存修改' : '交给小猫' }}</button>
        </div>
      </form>
      <p v-if="formError" class="field-error reminder-form-error" role="alert">{{ formError }}</p>
    </section>

    <section class="reminder-list-panel" aria-labelledby="reminder-list-title">
      <div class="reminder-list-top">
        <div class="reminder-list-identity">
          <svg class="reminder-cat-doodle" viewBox="0 0 120 100" aria-hidden="true">
            <path class="cat-tail" d="M37 82C19 91 10 75 18 63C23 55 32 58 33 66" />
            <path class="cat-body" d="M42 64C33 71 32 84 35 92H85C88 82 85 70 77 64" />
            <path class="cat-head" d="M31 42C28 30 31 19 39 13L50 25C56 22 64 22 70 25L81 13C89 20 92 31 89 42C94 57 84 69 60 71C36 69 26 57 31 42Z" />
            <path d="M44 45L50 47M76 45L70 47" />
            <path d="M57 53L60 55L63 53M60 55V59M60 59C56 63 52 61 51 59M60 59C64 63 68 61 69 59" />
            <path d="M43 54L25 51M43 59L23 62M77 54L95 51M77 59L97 62" />
            <path d="M47 92V78M73 92V78" />
            <circle class="cat-blush" cx="42" cy="57" r="3" />
            <circle class="cat-blush" cx="78" cy="57" r="3" />
          </svg>
          <div>
            <p class="eyebrow">MY REMINDERS</p>
            <h2 id="reminder-list-title">{{ heading }}</h2>
          </div>
        </div>
        <div class="reminder-tabs" role="group" aria-label="提醒范围">
          <button type="button" :class="{ active: scope === 'TODAY' }" @click="changeScope('TODAY')">今天</button>
          <button type="button" :class="{ active: scope === 'PENDING' }" @click="changeScope('PENDING')">全部未完成</button>
        </div>
      </div>

      <div v-if="loading && reminders.length === 0" class="state-panel" role="status">小猫正在翻找备忘录…</div>
      <div v-else-if="error && reminders.length === 0" class="state-panel error" role="alert">
        <p>{{ error }}</p>
        <button type="button" class="button secondary" @click="loadReminders">重新加载</button>
      </div>
      <template v-else>
        <p v-if="error" class="inline-alert" role="alert">{{ error }}</p>
        <ul v-if="reminders.length" class="reminder-list" :class="{ muted: loading }">
          <li v-for="reminder in reminders" :key="reminder.reminderId" :class="{ completed: reminder.status === 'COMPLETED' }">
            <button
              v-if="reminder.status === 'PENDING'"
              type="button"
              class="reminder-check"
              :disabled="actionId === reminder.reminderId"
              :aria-label="`完成提醒：${reminder.content}`"
              @click="markCompleted(reminder)"
            >✓</button>
            <span v-else class="reminder-check done" aria-label="已完成">✓</span>
            <div class="reminder-copy">
              <strong>{{ reminder.content }}</strong>
              <time :datetime="reminder.remindAt">{{ formatReminderTime(reminder.remindAt) }}</time>
            </div>
            <span class="reminder-status">{{ reminder.status === 'PENDING' ? '待完成' : '已完成' }}</span>
            <div class="reminder-actions">
              <button v-if="reminder.status === 'PENDING'" type="button" @click="startEditing(reminder)">编辑</button>
              <button type="button" class="delete" :disabled="actionId === reminder.reminderId" @click="removeReminder(reminder)">删除</button>
            </div>
          </li>
        </ul>
        <div v-else class="state-panel reminder-empty">
          <span class="empty-icon" aria-hidden="true">♡</span>
          <p>{{ emptyText }}</p>
        </div>
      </template>
    </section>
  </div>
</template>
