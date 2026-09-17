<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  CAT_ACTIVITY_DEFINITIONS,
  CAT_ACTIVITY_IDS,
  type CatActivityId,
  type CatActivitySnapshot,
} from '../../shared/cat-activity'
import type { Reminder } from '../../shared/reminder'
import { getAmbientMessages, TOUCH_MESSAGES } from './cat-dialogue'
import { disposeCatSounds, playCatSound } from './cat-sounds'
import eatingCatUrl from './assets/cat/cat-eat-pixel-v5.png'
import groomingCatUrl from './assets/cat/cat-groom-pixel-v5.png'
import idleCatUrl from './assets/cat/cat-idle-pixel-v5.png'
import playingCatUrl from './assets/cat/cat-play-pixel-v5.png'
import runningCatUrl from './assets/cat/cat-run-pixel-v5.png'
import sleepingCatUrl from './assets/cat/cat-sleep-pixel-v5.png'
import walkingCatUrl from './assets/cat/cat-walk-pixel-v5.png'

const catName = ref('小饼干')
const activityOptions = CAT_ACTIVITY_IDS.map((activityId) => CAT_ACTIVITY_DEFINITIONS[activityId])
const catSpriteUrls: Record<CatActivityId, string> = {
  idle: idleCatUrl,
  sleeping: sleepingCatUrl,
  grooming: groomingCatUrl,
  playing: playingCatUrl,
  eating: eatingCatUrl,
  walking: walkingCatUrl,
  running: runningCatUrl,
}
const activity = ref<CatActivitySnapshot>({
  id: 'idle',
  startedAt: Date.now(),
  endsAt: Date.now() + 60_000,
  durationMinutes: 1,
  facing: 'right',
})
const message = ref('我来啦，今天也陪着你。')
const companionDays = ref(1)
const isReacting = ref(false)
const isDragging = ref(false)
const isActivityMenuOpen = ref(false)
const activityPanelSide = ref<'left' | 'right'>('right')
const isRequestingActivity = ref(false)
const decisionKind = ref<'none' | 'accepted' | 'refused' | 'error'>('none')
const decisionMessage = ref(`选一个活动，看看${catName.value}愿不愿意。`)
const EMOTION_DRAFT_KEY = 'desktop-cat:emotion-draft'
const isEmotionInputOpen = ref(false)
const isSavingEmotion = ref(false)
const emotionDraft = ref(window.localStorage.getItem(EMOTION_DRAFT_KEY) ?? '')
const emotionError = ref('')
const emotionInput = ref<HTMLTextAreaElement | null>(null)
const dueReminder = ref<Reminder | null>(null)
const isCompletingReminder = ref(false)

const currentDefinition = computed(() => CAT_ACTIVITY_DEFINITIONS[activity.value.id])
const catImageUrl = computed(() => catSpriteUrls[activity.value.id])
const catLabel = computed(() => `正在${currentDefinition.value.label}的${catName.value}`)

let reactionTimer: number | undefined
let messageTimer: number | undefined
let conversationTimer: number | undefined
let closeMenuTimer: number | undefined
let companionTimer: number | undefined
let removeActivityListener: (() => void) | undefined
let removeProfileListener: (() => void) | undefined
let removeReminderListener: (() => void) | undefined
let isIgnoringMouseEvents = false
let activePointerId: number | undefined
let dragStartScreenX = 0
let dragStartScreenY = 0
let lastPointerScreenX = 0
let lastPointerScreenY = 0
let suppressNextClick = false

const DRAG_THRESHOLD_PX = 4
const MIN_CONVERSATION_DELAY_MS = 16_000
const MAX_CONVERSATION_DELAY_MS = 32_000
const RECENT_MESSAGE_LIMIT = 5
const recentMessages: string[] = []

function setMousePassThrough(ignore: boolean): void {
  if (ignore === isIgnoringMouseEvents) return
  isIgnoringMouseEvents = ignore
  window.desktopCat.setIgnoreMouseEvents(ignore)
}

function handleMouseMove(event: MouseEvent): void {
  const target = event.target
  const isInteractive = target instanceof Element && target.closest('[data-interactive]') !== null
  setMousePassThrough(!isInteractive)
}

function handleMouseLeave(): void {
  setMousePassThrough(true)
}

function clearMessageTimer(): void {
  if (!messageTimer) return
  window.clearTimeout(messageTimer)
  messageTimer = undefined
}

function clearConversationTimer(): void {
  if (!conversationTimer) return
  window.clearTimeout(conversationTimer)
  conversationTimer = undefined
}

async function refreshCompanionDays(): Promise<void> {
  try {
    companionDays.value = (await window.desktopCat.getCompanionInfo()).days
  } catch (error) {
    console.error('Failed to load the companion day count.', error)
  }

  const nextDay = new Date()
  nextDay.setHours(24, 0, 1, 0)
  companionTimer = window.setTimeout(() => {
    void refreshCompanionDays()
  }, nextDay.getTime() - Date.now())
}

function pickDialogue(candidates: readonly string[]): string {
  const unseenMessages = candidates.filter((candidate) => !recentMessages.includes(candidate))
  const pool = unseenMessages.length > 0 ? unseenMessages : candidates
  const nextMessage = pool[Math.floor(Math.random() * pool.length)]
  recentMessages.push(nextMessage)
  if (recentMessages.length > RECENT_MESSAGE_LIMIT) recentMessages.shift()
  return nextMessage
}

function pickAmbientMessage(): string {
  return pickDialogue(getAmbientMessages())
}

function formatDueReminderMessage(reminder: Reminder): string {
  const characters = Array.from(reminder.content)
  const summary = characters.length > 42 ? `${characters.slice(0, 42).join('')}…` : reminder.content
  return `到时间啦：${summary}`
}

function scheduleConversation(delayMs?: number): void {
  clearConversationTimer()
  const nextDelay = delayMs ?? Math.floor(
    MIN_CONVERSATION_DELAY_MS + Math.random() * (MAX_CONVERSATION_DELAY_MS - MIN_CONVERSATION_DELAY_MS),
  )
  conversationTimer = window.setTimeout(() => {
    conversationTimer = undefined
    if (!isActivityMenuOpen.value && !isDragging.value && !isRequestingActivity.value) {
      clearMessageTimer()
      message.value = pickAmbientMessage()
    }
    scheduleConversation()
  }, nextDelay)
}

function showTemporaryMessage(nextMessage: string, holdMs = 4_800): void {
  clearMessageTimer()
  clearConversationTimer()
  message.value = nextMessage
  messageTimer = window.setTimeout(() => {
    messageTimer = undefined
    if (dueReminder.value) {
      message.value = formatDueReminderMessage(dueReminder.value)
    } else {
      message.value = pickAmbientMessage()
      scheduleConversation()
    }
  }, holdMs)
}

function applyActivitySnapshot(nextActivity: CatActivitySnapshot): void {
  const activityChanged = nextActivity.id !== activity.value.id
    || nextActivity.startedAt !== activity.value.startedAt
  activity.value = nextActivity
  if (activityChanged && !isRequestingActivity.value) {
    showTemporaryMessage(pickDialogue(CAT_ACTIVITY_DEFINITIONS[nextActivity.id].automaticMessages))
  }
}

function syncMovementPause(): void {
  window.desktopCat.setMovementPaused(isActivityMenuOpen.value || isDragging.value)
}

watch(emotionDraft, (value) => {
  if (value) window.localStorage.setItem(EMOTION_DRAFT_KEY, value)
  else window.localStorage.removeItem(EMOTION_DRAFT_KEY)
})

async function openEmotionInput(): Promise<void> {
  emotionError.value = ''
  isEmotionInputOpen.value = true
  await nextTick()
  emotionInput.value?.focus()
}

function handleEmotionKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  void submitEmotion()
}

async function submitEmotion(): Promise<void> {
  if (isSavingEmotion.value) return
  const content = emotionDraft.value.trim()
  if (!content) {
    emotionError.value = '写点什么再告诉我吧。'
    return
  }

  isSavingEmotion.value = true
  emotionError.value = ''
  try {
    await window.desktopCat.createEmotion(content)
    emotionDraft.value = ''
    isEmotionInputOpen.value = false
    await setActivityMenuOpen(false)
    showTemporaryMessage('记下了。', 3_600)
  } catch (error) {
    console.error('Failed to save the emotion.', error)
    emotionError.value = '刚才没有保存成功，内容还在这里。'
  } finally {
    isSavingEmotion.value = false
  }
}

async function handleDueReminder(reminder: Reminder): Promise<void> {
  if (isActivityMenuOpen.value) await setActivityMenuOpen(false)
  dueReminder.value = reminder
  showTemporaryMessage(formatDueReminderMessage(reminder), 60_000)
  playCatSound('reminder')
  setMousePassThrough(false)
}

async function completeDueReminder(): Promise<void> {
  if (!dueReminder.value || isCompletingReminder.value) return
  isCompletingReminder.value = true
  try {
    await window.desktopCat.completeReminder(
      dueReminder.value.reminderId,
      dueReminder.value.version,
    )
    dueReminder.value = null
    showTemporaryMessage('完成啦！辛苦了，休息一下吧。', 7_000)
    playCatSound('happy')
  } catch (error) {
    console.error('Failed to complete the reminder.', error)
    showTemporaryMessage('刚才没有记成功，再点一次试试。', 7_000)
  } finally {
    isCompletingReminder.value = false
  }
}

async function setActivityMenuOpen(open: boolean): Promise<void> {
  if (closeMenuTimer) {
    window.clearTimeout(closeMenuTimer)
    closeMenuTimer = undefined
  }
  if (open) {
    window.desktopCat.setMovementPaused(true)
    activityPanelSide.value = await window.desktopCat.setActivityPanelOpen(true)
    isActivityMenuOpen.value = true
    decisionKind.value = 'none'
    decisionMessage.value = `选一个活动，看看${catName.value}愿不愿意。`
    setMousePassThrough(false)
  } else {
    isEmotionInputOpen.value = false
    isActivityMenuOpen.value = false
    await window.desktopCat.setActivityPanelOpen(false)
  }
  syncMovementPause()
}

async function chooseActivity(activityId: CatActivityId): Promise<void> {
  if (isRequestingActivity.value) return
  isRequestingActivity.value = true
  decisionKind.value = 'none'
  decisionMessage.value = `${catName.value}正在考虑……`

  try {
    const result = await window.desktopCat.requestActivity(activityId)
    applyActivitySnapshot(result.snapshot)
    decisionKind.value = result.accepted ? 'accepted' : 'refused'
    decisionMessage.value = result.message
    showTemporaryMessage(result.message)
    playCatSound(result.accepted ? 'happy' : 'protest')

    if (result.accepted) {
      closeMenuTimer = window.setTimeout(() => void setActivityMenuOpen(false), 1_100)
    }
  } catch (error) {
    console.error('Failed to request a cat activity.', error)
    decisionKind.value = 'error'
    decisionMessage.value = '刚才没听清，再选一次吧。'
  } finally {
    isRequestingActivity.value = false
  }
}

function reactToTouch(): void {
  playCatSound('touch')
  isReacting.value = false
  if (reactionTimer) window.clearTimeout(reactionTimer)
  const touchMessage = pickDialogue(TOUCH_MESSAGES)
  decisionKind.value = 'accepted'
  decisionMessage.value = touchMessage
  showTemporaryMessage(touchMessage, 6_000)
  window.requestAnimationFrame(() => {
    isReacting.value = true
  })
  reactionTimer = window.setTimeout(() => {
    isReacting.value = false
  }, 480)
}

function handleCatClick(): void {
  if (suppressNextClick) {
    suppressNextClick = false
    return
  }
  playCatSound('greeting')
  void setActivityMenuOpen(true)
}

function handleCatPointerDown(event: PointerEvent): void {
  if (event.button !== 0 || activePointerId !== undefined) return
  activePointerId = event.pointerId
  dragStartScreenX = event.screenX
  dragStartScreenY = event.screenY
  lastPointerScreenX = event.screenX
  lastPointerScreenY = event.screenY
  isDragging.value = false
  window.desktopCat.setMovementPaused(true)
  setMousePassThrough(false)
  if (event.currentTarget instanceof Element) event.currentTarget.setPointerCapture(event.pointerId)
}

function handleCatPointerMove(event: PointerEvent): void {
  if (event.pointerId !== activePointerId) return
  const totalX = event.screenX - dragStartScreenX
  const totalY = event.screenY - dragStartScreenY
  if (!isDragging.value && Math.hypot(totalX, totalY) < DRAG_THRESHOLD_PX) return

  isDragging.value = true
  const deltaX = event.screenX - lastPointerScreenX
  const deltaY = event.screenY - lastPointerScreenY
  lastPointerScreenX = event.screenX
  lastPointerScreenY = event.screenY
  if (deltaX !== 0 || deltaY !== 0) window.desktopCat.moveWindowBy(deltaX, deltaY)
}

function finishCatDrag(event: PointerEvent): void {
  if (event.pointerId !== activePointerId) return
  const target = event.currentTarget
  if (target instanceof Element && target.hasPointerCapture(event.pointerId)) {
    target.releasePointerCapture(event.pointerId)
  }

  if (isDragging.value) {
    suppressNextClick = true
    window.setTimeout(() => {
      suppressNextClick = false
    }, 0)
  }
  activePointerId = undefined
  isDragging.value = false
  syncMovementPause()
}

onMounted(async () => {
  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('mouseleave', handleMouseLeave)
  removeActivityListener = window.desktopCat.onActivityChanged(applyActivitySnapshot)
  removeProfileListener = window.desktopCat.onCatProfileChanged((profile) => {
    catName.value = profile.catName
  })
  removeReminderListener = window.desktopCat.onReminderDue((reminder) => {
    void handleDueReminder(reminder)
  })

  try {
    catName.value = (await window.desktopCat.getCatProfile()).catName
    activity.value = await window.desktopCat.getActivity()
    message.value = pickAmbientMessage()
  } catch (error) {
    console.error('Failed to load the current cat activity.', error)
    message.value = '我刚刚走神了一小会儿，现在回来陪你啦。'
  }
  await refreshCompanionDays()
  scheduleConversation(8_000)
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('mouseleave', handleMouseLeave)
  removeActivityListener?.()
  removeProfileListener?.()
  removeReminderListener?.()
  window.desktopCat.setMovementPaused(false)
  if (isActivityMenuOpen.value) void window.desktopCat.setActivityPanelOpen(false)
  if (reactionTimer) window.clearTimeout(reactionTimer)
  if (closeMenuTimer) window.clearTimeout(closeMenuTimer)
  if (companionTimer) window.clearTimeout(companionTimer)
  clearMessageTimer()
  clearConversationTimer()
  disposeCatSounds()
})
</script>

<template>
  <main
    class="desktop-pet"
    :class="[`state-${activity.id}`, { 'menu-open': isActivityMenuOpen, [`panel-${activityPanelSide}`]: isActivityMenuOpen }]"
  >
    <div class="cat-stage">
      <div v-if="!isActivityMenuOpen" class="speech" role="status" data-interactive :title="`按住气泡拖动${catName}`">
        <span>{{ message }}</span>
        <button
          v-if="dueReminder"
          class="reminder-complete"
          type="button"
          data-interactive
          :disabled="isCompletingReminder"
          @click="completeDueReminder"
        >{{ isCompletingReminder ? '正在完成…' : '✓ 完成这件事' }}</button>
        <small v-else>{{ currentDefinition.icon }} {{ currentDefinition.label }}</small>
      </div>

      <div v-if="!isActivityMenuOpen" class="drag-handle" data-interactive :title="`按住这里拖动${catName}`" />

      <button
        class="cat-button"
        :class="[activity.id, { reacting: isReacting, dragging: isDragging }]"
        type="button"
        data-interactive
        :aria-label="catLabel"
        :title="`拖动身体移动；单击选择${catName}的活动`"
        @pointerdown="handleCatPointerDown"
        @pointermove="handleCatPointerMove"
        @pointerup="finishCatDrag"
        @pointercancel="finishCatDrag"
        @click="handleCatClick"
      >
        <span class="cat-visual" :class="{ mirrored: activity.facing === 'left' }">
          <span class="cat-sprite-viewport">
            <img class="cat-sprite-sheet" :src="catImageUrl" :alt="catLabel" draggable="false" />
          </span>
        </span>
      </button>

      <div v-if="activity.id === 'sleeping'" class="sleep-marks" aria-hidden="true"><i>z</i><i>Z</i></div>
      <div v-if="activity.id === 'grooming'" class="activity-mark grooming-mark" aria-hidden="true">✦</div>
      <div v-if="activity.id === 'playing'" class="activity-mark play-ball" aria-hidden="true" />
      <div v-if="activity.id === 'walking'" class="activity-mark paw-marks" aria-hidden="true">···</div>
      <div v-if="activity.id === 'eating'" class="activity-mark treat-mark" aria-hidden="true">♡</div>
    </div>

    <section v-if="isActivityMenuOpen" class="activity-panel" data-interactive :aria-label="isEmotionInputOpen ? `告诉${catName}一件事` : `选择${catName}的活动`">
      <header>
        <strong>{{ isEmotionInputOpen ? '想说什么就说吧' : `你想让${catName}干什么？` }}</strong>
        <span class="panel-header-actions">
          <button v-if="!isEmotionInputOpen" type="button" title="跟小猫说句话" aria-label="跟小猫说句话" @click="openEmotionInput">✎</button>
          <button type="button" aria-label="关闭面板" @click="void setActivityMenuOpen(false)">×</button>
        </span>
      </header>

      <form v-if="isEmotionInputOpen" class="emotion-form" @submit.prevent="submitEmotion">
        <textarea
          ref="emotionInput"
          v-model="emotionDraft"
          rows="5"
          placeholder="不用整理，想到什么就说什么……"
          :disabled="isSavingEmotion"
          @keydown="handleEmotionKeydown"
        ></textarea>
        <p class="emotion-help">Enter 发送 · Shift + Enter 换行</p>
        <p v-if="emotionError" class="emotion-error" role="alert">{{ emotionError }}</p>
        <button class="emotion-send" type="submit" :disabled="isSavingEmotion">
          {{ isSavingEmotion ? '正在记下…' : '告诉小猫' }}
        </button>
      </form>

      <template v-else>
      <p class="decision" :class="decisionKind" role="status">{{ decisionMessage }}</p>

      <div class="activity-grid">
        <button
          v-for="option in activityOptions"
          :key="option.id"
          type="button"
          :class="{ current: option.id === activity.id }"
          :disabled="isRequestingActivity"
          @click="chooseActivity(option.id)"
        >
          <span>{{ option.icon }} {{ option.label }}</span>
        </button>
      </div>

      <button
        class="pet-button"
        type="button"
        :title="`摸摸${catName}，听它说句话`"
        :aria-label="`摸摸${catName}，听它说句话`"
        @click="reactToTouch"
      >
        <strong>{{ catName }}</strong>
        <small>已经陪伴了你 {{ companionDays }} 天</small>
      </button>
      </template>
    </section>
  </main>
</template>

<style scoped>
.desktop-pet {
  position: relative;
  width: 100%;
  height: 100%;
}

.cat-stage {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 176px;
  height: 176px;
  transition: left 140ms ease;
}

.desktop-pet.menu-open.panel-left .cat-stage { left: 224px; }

.speech {
  position: absolute;
  z-index: 6;
  top: 4px;
  left: 50%;
  display: grid;
  min-width: 122px;
  max-width: 164px;
  margin: 0;
  padding: 6px 9px 5px;
  transform: translateX(-50%);
  border: 1px solid rgb(206 168 119 / 62%);
  border-radius: 15px;
  background: rgb(255 250 239 / 94%);
  box-shadow: 0 4px 12px rgb(38 29 28 / 16%);
  color: #564441;
  font-size: 10px;
  line-height: 1.25;
  text-align: center;
  cursor: grab;
  pointer-events: auto;
  -webkit-app-region: drag;
  app-region: drag;
}

.speech small {
  margin-top: 3px;
  color: #9a765d;
  font-size: 8px;
}

.reminder-complete {
  justify-self: center;
  width: 100%;
  min-height: 24px;
  margin-top: 6px;
  padding: 5px 9px;
  border: 1px solid #536d50;
  border-radius: 9px;
  color: #fff;
  background: #657d5d;
  box-shadow: 0 2px 6px rgb(64 86 61 / 22%);
  font-size: 9px;
  font-weight: 650;
  cursor: pointer;
  pointer-events: auto;
  -webkit-app-region: no-drag;
  app-region: no-drag;
}

.reminder-complete:hover { background: #435e42; }
.reminder-complete:disabled { cursor: wait; opacity: .6; }

.speech:active { cursor: grabbing; }

.speech::after {
  position: absolute;
  bottom: -6px;
  left: calc(50% - 6px);
  width: 10px;
  height: 10px;
  transform: rotate(45deg);
  border-right: 1px solid rgb(206 168 119 / 62%);
  border-bottom: 1px solid rgb(206 168 119 / 62%);
  background: rgb(255 250 239 / 94%);
  content: "";
}

.drag-handle {
  position: absolute;
  z-index: 7;
  top: 40px;
  left: 34px;
  width: 108px;
  height: 22px;
  border-radius: 10px;
  cursor: grab;
  -webkit-app-region: drag;
  app-region: drag;
}

.cat-button {
  position: absolute;
  z-index: 3;
  bottom: 0;
  left: 8px;
  width: 160px;
  height: 142px;
  margin: 0;
  padding: 0;
  border: 0;
  outline: 0;
  background: transparent;
  cursor: grab;
  touch-action: none;
  -webkit-app-region: no-drag;
  app-region: no-drag;
}

.cat-button.dragging { cursor: grabbing; }
.cat-button:focus-visible { filter: drop-shadow(0 0 6px rgb(255 211 98 / 90%)); }

.cat-visual {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  width: 100%;
  height: 100%;
  transform: scaleX(1);
  transition: transform 140ms ease;
}

.cat-visual.mirrored { transform: scaleX(-1); }

.cat-sprite-viewport {
  position: relative;
  display: block;
  width: 142px;
  height: 142px;
  overflow: hidden;
  transform-origin: 50% 82%;
}

.cat-sprite-sheet {
  position: absolute;
  top: 0;
  left: 0;
  display: block;
  width: 800%;
  max-width: none;
  height: auto;
  animation: sprite-cycle var(--sprite-duration, 1600ms) steps(8, end) infinite;
  image-rendering: crisp-edges;
  image-rendering: pixelated;
  pointer-events: none;
  user-select: none;
}

.cat-button.running .cat-sprite-sheet { --sprite-duration: 560ms; }
.cat-button.walking .cat-sprite-sheet { --sprite-duration: 1120ms; }
.cat-button.playing .cat-sprite-sheet { --sprite-duration: 1360ms; }
.cat-button.sleeping .cat-sprite-sheet { --sprite-duration: 3200ms; }
.cat-button.idle .cat-sprite-sheet { --sprite-duration: 2800ms; }
.cat-button.grooming .cat-sprite-sheet { --sprite-duration: 2100ms; }
.cat-button.eating .cat-sprite-sheet { --sprite-duration: 1800ms; }
.cat-button.reacting .cat-sprite-viewport { animation: touch-pop 480ms cubic-bezier(.2, .85, .3, 1); }
.cat-button.dragging .cat-sprite-sheet { animation-play-state: paused; }

.sleep-marks {
  position: absolute;
  z-index: 5;
  top: 57px;
  right: 11px;
  color: rgb(243 184 70 / 88%);
  font-family: Georgia, serif;
  font-weight: 700;
  pointer-events: none;
}

.sleep-marks i { position: absolute; font-style: normal; animation: float-z 2.4s ease-in-out infinite; }
.sleep-marks i:first-child { top: 18px; right: 12px; font-size: 14px; }
.sleep-marks i:last-child { top: 0; right: 0; font-size: 20px; animation-delay: -1.2s; }

.activity-mark { position: absolute; z-index: 5; pointer-events: none; }
.grooming-mark { right: 22px; bottom: 34px; color: #f1bd55; font-size: 18px; animation: sparkle 1.2s ease-in-out infinite; }
.play-ball { right: 17px; bottom: 20px; width: 19px; height: 19px; border-radius: 50%; background: repeating-linear-gradient(45deg, #e6a64d 0 4px, #f6d184 4px 8px); box-shadow: 0 3px 7px rgb(60 40 31 / 20%); animation: ball-hop 1.35s ease-in-out infinite; }
.paw-marks { right: 13px; bottom: 22px; color: rgb(229 169 65 / 75%); font-size: 20px; letter-spacing: 3px; animation: paw-trail 1.6s linear infinite; }
.treat-mark { right: 18px; bottom: 30px; color: #e9b45e; font-size: 15px; animation: sparkle 1.4s steps(2, end) infinite; }

.activity-panel {
  position: absolute;
  z-index: 20;
  top: 4px;
  width: 216px;
  height: 222px;
  padding: 9px;
  overflow: hidden;
  border: 1px solid rgb(205 162 105 / 72%);
  border-radius: 18px;
  background: rgb(255 250 239 / 97%);
  box-shadow: 0 8px 24px rgb(38 29 28 / 22%);
  color: #55423d;
  -webkit-app-region: no-drag;
  app-region: no-drag;
}

.panel-right .activity-panel { left: 180px; }
.panel-left .activity-panel { left: 4px; }

.activity-panel header { display: flex; align-items: center; justify-content: space-between; height: 23px; }
.activity-panel header strong { font-size: 12px; }
.activity-panel header button { width: 23px; height: 23px; padding: 0; border: 0; border-radius: 50%; background: #f3e3cd; color: #705950; cursor: pointer; }
.panel-header-actions { display: flex; gap: 4px; }

.emotion-form { display: grid; gap: 6px; margin-top: 10px; }
.emotion-form textarea { width: 100%; min-height: 102px; padding: 9px 10px; resize: none; border: 1px solid #ead2b4; border-radius: 10px; outline: none; color: #55423d; background: #fffdf8; font: inherit; font-size: 10px; line-height: 1.55; }
.emotion-form textarea:focus { border-color: #d69c55; box-shadow: 0 0 0 2px rgb(214 156 85 / 18%); }
.emotion-help { margin: 0; color: #a08673; font-size: 8px; }
.emotion-error { min-height: 12px; margin: 0; color: #a04d4d; font-size: 9px; }
.emotion-send { justify-self: end; min-width: 76px; padding: 6px 10px; border: 0; border-radius: 9px; background: #72594d; color: #fffaf1; font-size: 9px; cursor: pointer; }
.emotion-send:disabled { cursor: wait; opacity: .6; }

.decision {
  min-height: 20px;
  margin: 2px 0 3px;
  color: #92705b;
  font-size: 9px;
  line-height: 1.25;
}
.decision.accepted { color: #667c51; }
.decision.refused { color: #b3665f; }
.decision.error { color: #a04d4d; }

.activity-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 4px; }
.activity-grid button {
  display: flex;
  align-items: center;
  min-height: 27px;
  padding: 2px 6px;
  border: 1px solid #ead2b4;
  border-radius: 9px;
  background: #fffdf8;
  color: #5c4942;
  text-align: left;
  cursor: pointer;
}
.activity-grid button:hover { border-color: #dbad72; background: #fff4e2; }
.activity-grid button.current { border-color: #d69c55; box-shadow: inset 0 0 0 1px #efd1a8; }
.activity-grid button:disabled { cursor: wait; opacity: .62; }
.activity-grid span { font-size: 10px; font-weight: 700; }

.pet-button {
  display: grid;
  justify-items: center;
  width: 100%;
  margin-top: 3px;
  padding: 2px;
  border: 0;
  border-radius: 8px;
  background: #f3e3cd;
  color: #72594d;
  font-size: 9px;
  cursor: pointer;
}

.pet-button strong { font-size: 9px; line-height: 1.1; }
.pet-button small { margin-top: 1px; color: #987864; font-size: 8px; line-height: 1.1; }

@keyframes sprite-cycle { to { transform: translateX(-100%); } }
@keyframes touch-pop { 0% { transform: translateY(0) scale(1); } 45% { transform: translateY(-10px) scale(1.04) rotate(-2deg); } 75% { transform: translateY(-3px) scale(.99) rotate(1deg); } 100% { transform: translateY(0) scale(1); } }
@keyframes float-z { 0%, 100% { transform: translate(0, 3px) scale(.9); opacity: .25; } 50% { transform: translate(4px, -5px) scale(1.08); opacity: 1; } }
@keyframes sparkle { 0%, 100% { transform: scale(.7) rotate(0); opacity: .35; } 50% { transform: scale(1.1) rotate(25deg); opacity: 1; } }
@keyframes ball-hop { 0%, 100% { transform: translate(0, 0) rotate(0); } 50% { transform: translate(-16px, -17px) rotate(160deg); } }
@keyframes paw-trail { from { transform: translateX(10px); opacity: 0; } 45% { opacity: .8; } to { transform: translateX(-14px); opacity: 0; } }

@media (prefers-reduced-motion: reduce) {
  .cat-sprite-sheet,
  .sleep-marks i,
  .activity-mark { animation: none !important; }
}
</style>
