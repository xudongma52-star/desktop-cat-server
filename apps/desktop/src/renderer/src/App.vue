<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  CAT_ACTIVITY_DEFINITIONS,
  CAT_ACTIVITY_IDS,
  type CatActivityId,
  type CatActivitySnapshot,
} from '../../shared/cat-activity'
import runningCatUrl from './assets/cat/cat-running-v2.png'
import sleepingCatUrl from './assets/cat/cat-sleeping-v2.png'
import meowUrl from './assets/audio/happy-cat-meow.mp3'

const activityOptions = CAT_ACTIVITY_IDS.map((activityId) => CAT_ACTIVITY_DEFINITIONS[activityId])
const activity = ref<CatActivitySnapshot>({
  id: 'idle',
  startedAt: Date.now(),
  endsAt: Date.now() + 60_000,
  durationMinutes: 1,
  facing: 'right',
})
const message = ref(CAT_ACTIVITY_DEFINITIONS.idle.defaultMessage)
const now = ref(Date.now())
const isReacting = ref(false)
const isDragging = ref(false)
const isActivityMenuOpen = ref(false)
const isRequestingActivity = ref(false)
const decisionKind = ref<'none' | 'accepted' | 'refused' | 'error'>('none')
const decisionMessage = ref('选一个活动，看看小猫愿不愿意。')

const currentDefinition = computed(() => CAT_ACTIVITY_DEFINITIONS[activity.value.id])
const usesSleepingImage = computed(() => (
  activity.value.id === 'idle'
  || activity.value.id === 'sleeping'
  || activity.value.id === 'grooming'
))
const catImageUrl = computed(() => usesSleepingImage.value ? sleepingCatUrl : runningCatUrl)
const catLabel = computed(() => `正在${currentDefinition.value.label}的黑色小猫`)
const remainingSeconds = computed(() => Math.max(0, Math.ceil((activity.value.endsAt - now.value) / 1_000)))
const remainingLabel = computed(() => {
  const minutes = Math.floor(remainingSeconds.value / 60)
  const seconds = remainingSeconds.value % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
})

let meowAudio: HTMLAudioElement | null = null
let clockTimer: number | undefined
let reactionTimer: number | undefined
let messageTimer: number | undefined
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

function showTemporaryMessage(nextMessage: string): void {
  clearMessageTimer()
  message.value = nextMessage
  messageTimer = window.setTimeout(() => {
    message.value = currentDefinition.value.defaultMessage
  }, 3_200)
}

function applyActivitySnapshot(nextActivity: CatActivitySnapshot): void {
  const activityChanged = nextActivity.id !== activity.value.id
    || nextActivity.startedAt !== activity.value.startedAt
  activity.value = nextActivity
  now.value = Date.now()
  if (activityChanged && !isRequestingActivity.value) {
    showTemporaryMessage(CAT_ACTIVITY_DEFINITIONS[nextActivity.id].automaticMessage)
  }
}

function syncMovementPause(): void {
  window.desktopCat.setMovementPaused(isActivityMenuOpen.value || isDragging.value)
}

function setActivityMenuOpen(open: boolean): void {
  if (closeMenuTimer) {
    window.clearTimeout(closeMenuTimer)
    closeMenuTimer = undefined
  }
  isActivityMenuOpen.value = open
  if (open) {
    decisionKind.value = 'none'
    decisionMessage.value = '选一个活动，看看小猫愿不愿意。'
    setMousePassThrough(false)
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
      closeMenuTimer = window.setTimeout(() => setActivityMenuOpen(false), 1_100)
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
  showTemporaryMessage('喵～ 摸到了！')
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
  setActivityMenuOpen(true)
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
  clockTimer = window.setInterval(() => {
    now.value = Date.now()
  }, 1_000)
  removeActivityListener = window.desktopCat.onActivityChanged(applyActivitySnapshot)

  try {
    applyActivitySnapshot(await window.desktopCat.getActivity())
    message.value = currentDefinition.value.defaultMessage
  } catch (error) {
    console.error('Failed to load the current cat activity.', error)
    message.value = '我刚刚走神了，再等等我～'
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('mouseleave', handleMouseLeave)
  removeActivityListener?.()
  window.desktopCat.setMovementPaused(false)
  if (clockTimer) window.clearInterval(clockTimer)
  if (reactionTimer) window.clearTimeout(reactionTimer)
  if (closeMenuTimer) window.clearTimeout(closeMenuTimer)
  clearMessageTimer()
  if (meowAudio) {
    meowAudio.pause()
    meowAudio.src = ''
  }
})
</script>

<template>
  <main class="desktop-pet" :class="`state-${activity.id}`">
    <p v-if="!isActivityMenuOpen" class="speech" role="status" data-interactive title="按住气泡拖动小猫">
      <span>{{ message }}</span>
      <small>{{ currentDefinition.icon }} {{ currentDefinition.label }} · {{ remainingLabel }}</small>
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
        <img class="cat-image" :src="catImageUrl" :alt="catLabel" draggable="false" />
      </span>
    </button>

    <div v-if="activity.id === 'running'" class="speed-lines" aria-hidden="true"><i /><i /><i /></div>
    <div v-if="activity.id === 'sleeping'" class="sleep-marks" aria-hidden="true"><i>z</i><i>Z</i></div>
    <div v-if="activity.id === 'grooming'" class="activity-mark grooming-mark" aria-hidden="true">✦</div>
    <div v-if="activity.id === 'playing'" class="activity-mark play-ball" aria-hidden="true" />
    <div v-if="activity.id === 'walking'" class="activity-mark paw-marks" aria-hidden="true">···</div>

    <section v-if="isActivityMenuOpen" class="activity-panel" data-interactive aria-label="选择小猫活动">
      <header>
        <strong>想让小猫做什么？</strong>
        <button type="button" aria-label="关闭活动选择" @click="setActivityMenuOpen(false)">×</button>
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
          <small>{{ option.minMinutes }}–{{ option.maxMinutes }} 分钟</small>
        </button>
      </div>

      <button class="pet-button" type="button" @click="reactToTouch">摸摸它，暂时不换活动</button>
    </section>
  </main>
</template>

<style scoped>
.desktop-pet {
  position: relative;
  width: 100%;
  height: 100%;
}

.speech {
  position: absolute;
  z-index: 6;
  top: 4px;
  left: 50%;
  display: grid;
  min-width: 142px;
  max-width: 198px;
  margin: 0;
  padding: 7px 11px 6px;
  transform: translateX(-50%);
  border: 1px solid rgb(206 168 119 / 62%);
  border-radius: 15px;
  background: rgb(255 250 239 / 94%);
  box-shadow: 0 4px 12px rgb(38 29 28 / 16%);
  color: #564441;
  font-size: 11px;
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
  font-size: 9px;
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
  top: 45px;
  left: 50px;
  width: 120px;
  height: 27px;
  border-radius: 10px;
  cursor: grab;
  -webkit-app-region: drag;
  app-region: drag;
}

.cat-button {
  position: absolute;
  z-index: 3;
  bottom: 9px;
  left: 5px;
  width: 210px;
  height: 154px;
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
.cat-button.running,
.cat-button.walking,
.cat-button.playing { clip-path: polygon(0 15%, 28% 3%, 47% 15%, 56% 0, 93% 8%, 100% 45%, 93% 95%, 57% 100%, 37% 88%, 10% 97%); }
.cat-button.sleeping,
.cat-button.idle,
.cat-button.grooming { clip-path: ellipse(49% 43% at 50% 57%); }
.cat-button:focus-visible { filter: drop-shadow(0 0 6px rgb(255 211 98 / 90%)); }

.cat-visual {
  display: block;
  width: 100%;
  height: 100%;
  transform: scaleX(1);
  transition: transform 140ms ease;
}

.cat-visual.mirrored { transform: scaleX(-1); }

.cat-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  pointer-events: none;
  user-select: none;
}

.cat-button.running .cat-image { animation: run-cycle 520ms cubic-bezier(.45, 0, .55, 1) infinite; }
.cat-button.walking .cat-image { animation: walk-cycle 1.05s ease-in-out infinite; }
.cat-button.playing .cat-image { animation: play-pounce 1.35s ease-in-out infinite; }
.cat-button.sleeping .cat-image { transform-origin: 55% 78%; animation: sleep-breathe 3.6s ease-in-out infinite; }
.cat-button.idle .cat-image { animation: idle-look 4.8s ease-in-out infinite; }
.cat-button.grooming .cat-image { transform-origin: 62% 72%; animation: groom-sway 1.8s ease-in-out infinite; }
.cat-button.reacting .cat-image { animation: touch-pop 480ms cubic-bezier(.2, .85, .3, 1); }

.speed-lines {
  position: absolute;
  z-index: 2;
  bottom: 62px;
  left: 5px;
  width: 48px;
  pointer-events: none;
}

.speed-lines i {
  display: block;
  width: 32px;
  height: 2px;
  margin-top: 8px;
  border-radius: 2px;
  background: rgb(238 179 74 / 52%);
  animation: speed-line 760ms ease-out infinite;
}

.speed-lines i:nth-child(2) { width: 44px; animation-delay: -240ms; }
.speed-lines i:nth-child(3) { width: 24px; animation-delay: -480ms; }

.sleep-marks {
  position: absolute;
  z-index: 5;
  top: 65px;
  right: 18px;
  color: rgb(243 184 70 / 88%);
  font-family: Georgia, serif;
  font-weight: 700;
  pointer-events: none;
}

.sleep-marks i { position: absolute; font-style: normal; animation: float-z 2.4s ease-in-out infinite; }
.sleep-marks i:first-child { top: 18px; right: 12px; font-size: 14px; }
.sleep-marks i:last-child { top: 0; right: 0; font-size: 20px; animation-delay: -1.2s; }

.activity-mark { position: absolute; z-index: 5; pointer-events: none; }
.grooming-mark { right: 30px; bottom: 44px; color: #f1bd55; font-size: 22px; animation: sparkle 1.2s ease-in-out infinite; }
.play-ball { right: 23px; bottom: 25px; width: 24px; height: 24px; border-radius: 50%; background: repeating-linear-gradient(45deg, #e6a64d 0 5px, #f6d184 5px 10px); box-shadow: 0 3px 7px rgb(60 40 31 / 20%); animation: ball-hop 1.35s ease-in-out infinite; }
.paw-marks { right: 18px; bottom: 28px; color: rgb(229 169 65 / 75%); font-size: 25px; letter-spacing: 3px; animation: paw-trail 1.6s linear infinite; }

.activity-panel {
  position: absolute;
  z-index: 20;
  inset: 4px;
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

.activity-panel header { display: flex; align-items: center; justify-content: space-between; height: 23px; }
.activity-panel header strong { font-size: 12px; }
.activity-panel header button { width: 23px; height: 23px; padding: 0; border: 0; border-radius: 50%; background: #f3e3cd; color: #705950; cursor: pointer; }

.decision {
  min-height: 24px;
  margin: 2px 0 4px;
  color: #92705b;
  font-size: 9px;
  line-height: 1.25;
}
.decision.accepted { color: #667c51; }
.decision.refused { color: #b3665f; }
.decision.error { color: #a04d4d; }

.activity-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 5px; }
.activity-grid button {
  display: grid;
  gap: 1px;
  min-height: 32px;
  padding: 4px 6px;
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
.activity-grid small { color: #9a7d69; font-size: 8px; }

.pet-button {
  width: 100%;
  margin-top: 4px;
  padding: 3px;
  border: 0;
  border-radius: 8px;
  background: #f3e3cd;
  color: #72594d;
  font-size: 9px;
  cursor: pointer;
}

@keyframes run-cycle { 0%, 100% { transform: translate(-3px, 1px) rotate(-1deg); } 50% { transform: translate(4px, -5px) rotate(1.5deg) scale(1.015); } }
@keyframes walk-cycle { 0%, 100% { transform: translate(-1px, 1px) rotate(-.5deg); } 50% { transform: translate(2px, -2px) rotate(.7deg); } }
@keyframes play-pounce { 0%, 100% { transform: translate(0, 2px) rotate(0); } 45% { transform: translate(8px, -9px) rotate(3deg) scale(1.02); } }
@keyframes sleep-breathe { 0%, 100% { transform: translateY(1px) scale(1); } 50% { transform: translateY(3px) scale(1.012, .988); } }
@keyframes idle-look { 0%, 70%, 100% { transform: translateX(0); } 78% { transform: translateX(-3px) rotate(-1deg); } 88% { transform: translateX(3px) rotate(1deg); } }
@keyframes groom-sway { 0%, 100% { transform: rotate(-1deg); } 50% { transform: translateY(2px) rotate(2deg); } }
@keyframes touch-pop { 0% { transform: translateY(0) scale(1); } 45% { transform: translateY(-10px) scale(1.04) rotate(-2deg); } 75% { transform: translateY(-3px) scale(.99) rotate(1deg); } 100% { transform: translateY(0) scale(1); } }
@keyframes speed-line { from { transform: translateX(18px) scaleX(.45); opacity: 0; } 35% { opacity: .8; } to { transform: translateX(-8px) scaleX(1); opacity: 0; } }
@keyframes float-z { 0%, 100% { transform: translate(0, 3px) scale(.9); opacity: .25; } 50% { transform: translate(4px, -5px) scale(1.08); opacity: 1; } }
@keyframes sparkle { 0%, 100% { transform: scale(.7) rotate(0); opacity: .35; } 50% { transform: scale(1.1) rotate(25deg); opacity: 1; } }
@keyframes ball-hop { 0%, 100% { transform: translate(0, 0) rotate(0); } 50% { transform: translate(-16px, -17px) rotate(160deg); } }
@keyframes paw-trail { from { transform: translateX(10px); opacity: 0; } 45% { opacity: .8; } to { transform: translateX(-14px); opacity: 0; } }

@media (prefers-reduced-motion: reduce) {
  .cat-image,
  .speed-lines i,
  .sleep-marks i,
  .activity-mark { animation: none !important; }
}
</style>
