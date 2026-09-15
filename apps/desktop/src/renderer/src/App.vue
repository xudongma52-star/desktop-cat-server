<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  CAT_ACTIVITY_DEFINITIONS,
  CAT_ACTIVITY_IDS,
  type CatActivityId,
  type CatActivitySnapshot,
} from '../../shared/cat-activity'
import { getAmbientMessages, TOUCH_MESSAGES } from './cat-dialogue'
import eatingCatUrl from './assets/cat/cat-eat-pixel-v5.png'
import groomingCatUrl from './assets/cat/cat-groom-pixel-v5.png'
import idleCatUrl from './assets/cat/cat-idle-pixel-v5.png'
import playingCatUrl from './assets/cat/cat-play-pixel-v5.png'
import runningCatUrl from './assets/cat/cat-run-pixel-v5.png'
import sleepingCatUrl from './assets/cat/cat-sleep-pixel-v5.png'
import walkingCatUrl from './assets/cat/cat-walk-pixel-v5.png'
import meowUrl from './assets/audio/happy-cat-meow.mp3'

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
const isReacting = ref(false)
const isDragging = ref(false)
const isActivityMenuOpen = ref(false)
const activityPanelSide = ref<'left' | 'right'>('right')
const isRequestingActivity = ref(false)
const decisionKind = ref<'none' | 'accepted' | 'refused' | 'error'>('none')
const decisionMessage = ref('选一个活动，看看小猫愿不愿意。')

const currentDefinition = computed(() => CAT_ACTIVITY_DEFINITIONS[activity.value.id])
const catImageUrl = computed(() => catSpriteUrls[activity.value.id])
const catLabel = computed(() => `正在${currentDefinition.value.label}的黑色小猫`)

let meowAudio: HTMLAudioElement | null = null
let reactionTimer: number | undefined
let messageTimer: number | undefined
let conversationTimer: number | undefined
let closeMenuTimer: number | undefined
let removeActivityListener: (() => void) | undefined
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

function playMeow(): void {
  meowAudio ??= new Audio(meowUrl)
  meowAudio.pause()
  meowAudio.currentTime = 0
  meowAudio.volume = 0.58
  void meowAudio.play().catch((error: unknown) => {
    console.warn('Failed to play the cat meow.', error)
  })
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
    message.value = pickAmbientMessage()
    scheduleConversation()
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
    decisionMessage.value = '选一个活动，看看小猫愿不愿意。'
    setMousePassThrough(false)
  } else {
    isActivityMenuOpen.value = false
    await window.desktopCat.setActivityPanelOpen(false)
  }
  syncMovementPause()
}

async function chooseActivity(activityId: CatActivityId): Promise<void> {
  if (isRequestingActivity.value) return
  isRequestingActivity.value = true
  decisionKind.value = 'none'
  decisionMessage.value = '小猫正在考虑……'

  try {
    const result = await window.desktopCat.requestActivity(activityId)
    applyActivitySnapshot(result.snapshot)
    decisionKind.value = result.accepted ? 'accepted' : 'refused'
    decisionMessage.value = result.message
    showTemporaryMessage(result.message)

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
  playMeow()
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
  playMeow()
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

  try {
    activity.value = await window.desktopCat.getActivity()
    message.value = pickAmbientMessage()
  } catch (error) {
    console.error('Failed to load the current cat activity.', error)
    message.value = '我刚刚走神了一小会儿，现在回来陪你啦。'
  }
  scheduleConversation(8_000)
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('mouseleave', handleMouseLeave)
  removeActivityListener?.()
  window.desktopCat.setMovementPaused(false)
  if (isActivityMenuOpen.value) void window.desktopCat.setActivityPanelOpen(false)
  if (reactionTimer) window.clearTimeout(reactionTimer)
  if (closeMenuTimer) window.clearTimeout(closeMenuTimer)
  clearMessageTimer()
  clearConversationTimer()
  if (meowAudio) {
    meowAudio.pause()
    meowAudio.src = ''
  }
})
</script>

<template>
  <main
    class="desktop-pet"
    :class="[`state-${activity.id}`, { 'menu-open': isActivityMenuOpen, [`panel-${activityPanelSide}`]: isActivityMenuOpen }]"
  >
    <div class="cat-stage">
      <p v-if="!isActivityMenuOpen" class="speech" role="status" data-interactive title="按住气泡拖动小猫">
        <span>{{ message }}</span>
        <small>{{ currentDefinition.icon }} {{ currentDefinition.label }}</small>
      </p>

      <div v-if="!isActivityMenuOpen" class="drag-handle" data-interactive title="按住这里拖动小猫" />

      <button
        class="cat-button"
        :class="[activity.id, { reacting: isReacting, dragging: isDragging }]"
        type="button"
        data-interactive
        :aria-label="catLabel"
        title="拖动身体移动；单击选择小猫活动"
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

    <section v-if="isActivityMenuOpen" class="activity-panel" data-interactive aria-label="选择小猫活动">
      <header>
        <strong>想让小猫做什么？</strong>
        <button type="button" aria-label="关闭活动选择" @click="void setActivityMenuOpen(false)">×</button>
      </header>

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

      <button class="pet-button" type="button" @click="reactToTouch">摸摸它，听它说句话</button>
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
  height: 212px;
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
