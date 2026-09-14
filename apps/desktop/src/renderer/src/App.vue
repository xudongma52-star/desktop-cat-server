//小猫样式和互动
<script setup lang="ts">
/**
 * ref：创建响应式变量
 * onMounted：组件显示后注册事件
 * onBeforeUnmount：组件销毁前清理资源
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'

//message 控制气泡文字。
//isReacting 控制是否添加：
const message = ref('摸摸我，我会喵～')
const isReacting = ref(false)

let audioContext: AudioContext | null = null
let reactionTimer: number | undefined
let messageTimer: number | undefined
let isIgnoringMouseEvents = false

function setMousePassThrough(ignore: boolean): void {
  if (ignore === isIgnoringMouseEvents) {
    return
  }

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
  audioContext ??= new AudioContext()
  void audioContext.resume()

  const now = audioContext.currentTime
  const volume = audioContext.createGain()
  const voice = audioContext.createOscillator()
  const overtone = audioContext.createOscillator()
  const overtoneVolume = audioContext.createGain()

  voice.type = 'triangle'
  voice.frequency.setValueAtTime(520, now)
  voice.frequency.exponentialRampToValueAtTime(760, now + 0.11)
  voice.frequency.exponentialRampToValueAtTime(390, now + 0.43)

  overtone.type = 'sine'
  overtone.frequency.setValueAtTime(1040, now)
  overtone.frequency.exponentialRampToValueAtTime(1420, now + 0.12)
  overtone.frequency.exponentialRampToValueAtTime(720, now + 0.4)

  volume.gain.setValueAtTime(0.0001, now)
  volume.gain.exponentialRampToValueAtTime(0.16, now + 0.025)
  volume.gain.setValueAtTime(0.16, now + 0.16)
  volume.gain.exponentialRampToValueAtTime(0.0001, now + 0.46)

  overtoneVolume.gain.setValueAtTime(0.025, now)
  overtoneVolume.gain.exponentialRampToValueAtTime(0.0001, now + 0.39)

  voice.connect(volume)
  overtone.connect(overtoneVolume)
  overtoneVolume.connect(volume)
  volume.connect(audioContext.destination)

  voice.start(now)
  overtone.start(now)
  voice.stop(now + 0.47)
  overtone.stop(now + 0.42)
}

function reactToTouch(): void {
  playMeow()
  message.value = '喵～ 今天也辛苦啦'
  isReacting.value = false

  if (reactionTimer) {
    window.clearTimeout(reactionTimer)
  }
  if (messageTimer) {
    window.clearTimeout(messageTimer)
  }

  window.requestAnimationFrame(() => {
    isReacting.value = true
  })

  reactionTimer = window.setTimeout(() => {
    isReacting.value = false
  }, 520)

  messageTimer = window.setTimeout(() => {
    message.value = '摸摸我，我会喵～'
  }, 2200)
}

onMounted(() => {
  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('mouseleave', handleMouseLeave)
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('mouseleave', handleMouseLeave)
  if (reactionTimer) {
    window.clearTimeout(reactionTimer)
  }
  if (messageTimer) {
    window.clearTimeout(messageTimer)
  }
  void audioContext?.close()
})
</script>

<template>
  <main class="desktop-pet">
    <p class="speech" role="status">{{ message }}</p>

    <div
      class="drag-handle"
      data-interactive
      title="按住小猫耳朵附近拖动"
      aria-label="拖动小猫"
    />

    <button
      class="cat-button"
      :class="{ reacting: isReacting }"
      type="button"
      data-interactive
      aria-label="摸摸小猫"
      title="点击摸摸小猫"
      @click="reactToTouch"
    >
      <svg class="cat-art" viewBox="0 0 260 270" role="img" aria-label="一只橘色 Q 版小猫">
        <defs>
          <linearGradient id="fur" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#ffd19d" />
            <stop offset="1" stop-color="#ee9868" />
          </linearGradient>
          <linearGradient id="cream" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="#fffaf0" />
            <stop offset="1" stop-color="#ffe8ce" />
          </linearGradient>
          <filter id="soft-shadow" x="-30%" y="-30%" width="160%" height="160%">
            <feDropShadow dx="0" dy="5" stdDeviation="5" flood-color="#72554a" flood-opacity=".22" />
          </filter>
        </defs>

        <ellipse cx="130" cy="250" rx="82" ry="12" fill="#66504a" opacity=".16" />

        <g filter="url(#soft-shadow)">
          <path
            class="tail"
            d="M61 184C22 172 18 219 47 226C65 230 70 212 58 205C48 199 42 210 48 216"
            fill="none"
            stroke="#ee9868"
            stroke-linecap="round"
            stroke-width="20"
          />
          <ellipse cx="133" cy="178" rx="69" ry="73" fill="url(#fur)" />
          <ellipse cx="133" cy="190" rx="43" ry="50" fill="url(#cream)" opacity=".92" />

          <path d="M74 75L83 20L121 61Z" fill="url(#fur)" />
          <path d="M186 75L177 20L139 61Z" fill="url(#fur)" />
          <path d="M84 56L89 33L107 59Z" fill="#ef9aa0" opacity=".82" />
          <path d="M176 56L171 33L153 59Z" fill="#ef9aa0" opacity=".82" />

          <ellipse cx="130" cy="91" rx="70" ry="59" fill="url(#fur)" />
          <ellipse cx="130" cy="107" rx="48" ry="36" fill="url(#cream)" opacity=".93" />

          <path d="M91 58C105 48 112 51 122 61" fill="none" stroke="#d98056" stroke-linecap="round" stroke-width="6" />
          <path d="M169 58C155 48 148 51 138 61" fill="none" stroke="#d98056" stroke-linecap="round" stroke-width="6" />
          <path d="M130 43V58" fill="none" stroke="#d98056" stroke-linecap="round" stroke-width="6" />

          <ellipse class="eye eye-left" cx="103" cy="91" rx="8" ry="11" fill="#493b39" />
          <ellipse class="eye eye-right" cx="157" cy="91" rx="8" ry="11" fill="#493b39" />
          <circle cx="106" cy="87" r="2.4" fill="#fff" />
          <circle cx="160" cy="87" r="2.4" fill="#fff" />

          <path d="M124 106Q130 100 136 106Q130 114 124 106Z" fill="#ed7d86" />
          <path d="M130 112V116" stroke="#493b39" stroke-linecap="round" stroke-width="2.5" />
          <path d="M130 116Q122 124 115 117" fill="none" stroke="#493b39" stroke-linecap="round" stroke-width="2.5" />
          <path d="M130 116Q138 124 145 117" fill="none" stroke="#493b39" stroke-linecap="round" stroke-width="2.5" />

          <g stroke="#8d6659" stroke-linecap="round" stroke-width="2" opacity=".72">
            <path d="M113 110L77 103" />
            <path d="M112 116L73 118" />
            <path d="M147 110L183 103" />
            <path d="M148 116L187 118" />
          </g>

          <ellipse cx="102" cy="230" rx="28" ry="22" fill="#ffd4a7" />
          <ellipse cx="164" cy="230" rx="28" ry="22" fill="#ffd4a7" />
          <path d="M92 226V235M102 224V235M154 224V235M164 226V235" stroke="#d98a62" stroke-linecap="round" stroke-width="2.5" />
        </g>
      </svg>
    </button>

    <span class="drag-tip">拖住耳朵移动 · 点击身体摸摸</span>
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
  z-index: 5;
  top: 4px;
  left: 50%;
  min-width: 158px;
  max-width: 230px;
  margin: 0;
  padding: 9px 14px;
  transform: translateX(-50%);
  border: 1px solid rgb(221 171 139 / 65%);
  border-radius: 18px;
  background: rgb(255 251 244 / 92%);
  box-shadow: 0 5px 16px rgb(91 65 56 / 14%);
  color: #66504a;
  font-size: 13px;
  line-height: 1.25;
  text-align: center;
  pointer-events: none;
}

.speech::after {
  position: absolute;
  bottom: -7px;
  left: calc(50% - 7px);
  width: 13px;
  height: 13px;
  transform: rotate(45deg);
  border-right: 1px solid rgb(221 171 139 / 65%);
  border-bottom: 1px solid rgb(221 171 139 / 65%);
  background: rgb(255 251 244 / 92%);
  content: "";
}

.drag-handle {
  position: absolute;
  z-index: 4;
  top: 54px;
  left: 53px;
  width: 174px;
  height: 54px;
  cursor: grab;
  app-region: drag;
}

.drag-handle:active {
  cursor: grabbing;
}

.cat-button {
  position: absolute;
  z-index: 2;
  bottom: 15px;
  left: 10px;
  width: 260px;
  height: 270px;
  margin: 0;
  padding: 0;
  transform-origin: 50% 82%;
  border: 0;
  outline: 0;
  background: transparent;
  cursor: pointer;
  app-region: no-drag;
  clip-path: polygon(17% 18%, 33% 4%, 47% 14%, 53% 14%, 67% 4%, 83% 18%, 90% 43%, 79% 59%, 87% 87%, 71% 98%, 31% 98%, 13% 88%, 20% 62%, 7% 76%, 3% 64%, 15% 48%, 10% 34%);
  animation: breathe 3.2s ease-in-out infinite;
}

.cat-button:focus-visible {
  filter: drop-shadow(0 0 8px rgb(255 255 255 / 95%));
}

.cat-button.reacting {
  animation: happy-bounce 520ms cubic-bezier(.2, .8, .25, 1);
}

.cat-art {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}

.eye {
  transform-box: fill-box;
  transform-origin: center;
  animation: blink 5.4s infinite;
}

.tail {
  transform-box: fill-box;
  transform-origin: right center;
  animation: tail-sway 2.7s ease-in-out infinite;
}

.drag-tip {
  position: absolute;
  z-index: 5;
  bottom: 1px;
  left: 50%;
  padding: 4px 10px;
  transform: translateX(-50%);
  border-radius: 12px;
  background: rgb(79 60 55 / 72%);
  color: rgb(255 255 255 / 92%);
  font-size: 10px;
  line-height: 1;
  white-space: nowrap;
  pointer-events: none;
}

@keyframes breathe {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(2px) scale(1.008, .995);
  }
}

@keyframes happy-bounce {
  0% {
    transform: translateY(0) rotate(0);
  }
  35% {
    transform: translateY(-13px) rotate(-3deg);
  }
  65% {
    transform: translateY(-5px) rotate(3deg);
  }
  100% {
    transform: translateY(0) rotate(0);
  }
}

@keyframes blink {
  0%,
  44%,
  48%,
  100% {
    transform: scaleY(1);
  }
  46% {
    transform: scaleY(.08);
  }
}

@keyframes tail-sway {
  0%,
  100% {
    transform: rotate(-3deg);
  }
  50% {
    transform: rotate(7deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .cat-button,
  .eye,
  .tail {
    animation: none;
  }
}
</style>
