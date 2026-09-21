<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

type SceneKey = 'morning' | 'afternoon' | 'dusk' | 'night'
type MoodKey = 'smug' | 'cute' | 'innocent' | 'angry'

interface DayScene {
  key: SceneKey
  label: string
  time: string
  message: string
  mood: MoodKey
}

interface CatMood {
  key: MoodKey
  label: string
  description: string
}

const emit = defineEmits<{
  phaseChange: [phase: SceneKey]
}>()

const scenes: DayScene[] = [
  { key: 'morning', label: '清晨', time: '10:10', message: '刚把毛线球藏好。', mood: 'smug' },
  { key: 'afternoon', label: '中午', time: '14:30', message: '从软垫后面探出脑袋。', mood: 'cute' },
  { key: 'dusk', label: '傍晚', time: '18:40', message: '戴着徽章等你回家。', mood: 'innocent' },
  { key: 'night', label: '深夜', time: '23:10', message: '还不睡，它有点生气。', mood: 'angry' },
]

const moods: CatMood[] = [
  { key: 'smug', label: '得意', description: '眯起眼睛吐舌头' },
  { key: 'cute', label: '卖萌', description: '回头露出大眼睛' },
  { key: 'innocent', label: '无辜', description: '戴着百分百笨蛋徽章' },
  { key: 'angry', label: '生气', description: '皱眉并冒出怒气符号' },
]

function sceneIndexForHour(hour: number): number {
  if (hour < 12) return 0
  if (hour < 17) return 1
  if (hour < 20) return 2
  return 3
}

const activeIndex = ref(sceneIndexForHour(new Date().getHours()))
const activeMood = ref<MoodKey>(scenes[activeIndex.value].mood)
const blinking = ref(false)
const activeScene = computed(() => scenes[activeIndex.value])
const activeMoodInfo = computed(() => moods.find(mood => mood.key === activeMood.value) ?? moods[0])

let sceneTimer: ReturnType<typeof setInterval> | undefined
let blinkTimer: ReturnType<typeof setTimeout> | undefined
let blinkEndTimer: ReturnType<typeof setTimeout> | undefined
let motionPreference: MediaQueryList | undefined
let reduceMotion = false

function selectScene(index: number) {
  activeIndex.value = index
  activeMood.value = scenes[index].mood
  startSceneRotation()
}

function startSceneRotation() {
  if (sceneTimer) clearInterval(sceneTimer)
  if (reduceMotion) return
  sceneTimer = setInterval(() => {
    activeIndex.value = (activeIndex.value + 1) % scenes.length
    activeMood.value = scenes[activeIndex.value].mood
  }, 8000)
}

function scheduleBlink() {
  if (blinkTimer) clearTimeout(blinkTimer)
  if (reduceMotion) return
  blinkTimer = setTimeout(() => {
    blinking.value = true
    blinkEndTimer = setTimeout(() => {
      blinking.value = false
      scheduleBlink()
    }, 135)
  }, 2600 + Math.random() * 2800)
}

function handleMotionPreference(event: MediaQueryListEvent) {
  reduceMotion = event.matches
  blinking.value = false
  startSceneRotation()
  scheduleBlink()
}

watch(activeScene, scene => emit('phaseChange', scene.key))

onMounted(() => {
  emit('phaseChange', activeScene.value.key)
  motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')
  reduceMotion = motionPreference.matches
  motionPreference.addEventListener('change', handleMotionPreference)
  startSceneRotation()
  scheduleBlink()
})

onBeforeUnmount(() => {
  if (sceneTimer) clearInterval(sceneTimer)
  if (blinkTimer) clearTimeout(blinkTimer)
  if (blinkEndTimer) clearTimeout(blinkEndTimer)
  motionPreference?.removeEventListener('change', handleMotionPreference)
})
</script>

<template>
  <div
    class="auth-day-scene"
    :class="[`phase-${activeScene.key}`, `mood-${activeMood}`, { blinking }]"
    :aria-label="`${activeScene.label}${activeScene.time}，${activeScene.message}，猫咪当前是${activeMoodInfo.label}状态。`"
  >
    <div class="day-scene-canvas">
      <svg viewBox="0 0 800 800" role="img" aria-labelledby="cat-scene-title cat-scene-desc">
        <title id="cat-scene-title">温馨小屋里的灰白色团子猫</title>
        <desc id="cat-scene-desc">{{ activeScene.label }}时分，猫咪正在{{ activeMoodInfo.description }}。</desc>

        <defs>
          <filter id="paperTexture" x="-8%" y="-8%" width="116%" height="116%">
            <feTurbulence type="fractalNoise" baseFrequency="0.72" numOctaves="2" seed="12" result="noise" />
            <feColorMatrix in="noise" type="saturate" values="0" result="monoNoise" />
            <feComponentTransfer in="monoNoise" result="softNoise">
              <feFuncA type="table" tableValues="0 0.055" />
            </feComponentTransfer>
            <feBlend in="SourceGraphic" in2="softNoise" mode="multiply" />
          </filter>
          <filter id="roomShadow" x="-30%" y="-30%" width="160%" height="170%">
            <feDropShadow dx="0" dy="18" stdDeviation="16" flood-color="#273b32" flood-opacity=".22" />
          </filter>
          <filter id="lampGlow" x="-120%" y="-120%" width="340%" height="340%">
            <feGaussianBlur stdDeviation="18" />
          </filter>
          <filter id="sunlightBlur" x="-35%" y="-20%" width="170%" height="150%">
            <feGaussianBlur stdDeviation="22" />
          </filter>
          <clipPath id="roomClip">
            <rect x="34" y="28" width="732" height="724" rx="82" />
          </clipPath>
        </defs>

        <rect class="room-shadow" x="34" y="38" width="732" height="724" rx="82" filter="url(#roomShadow)" />
        <g id="cozy-room" clip-path="url(#roomClip)" filter="url(#paperTexture)">
          <rect class="room-wall" x="34" y="28" width="732" height="510" />
          <path class="room-floor" d="M34 512c176-14 555-12 732 8v232H34z" />
          <path class="floor-seam" d="M35 546c167-12 546-10 730 8M42 643c184-13 520-8 717 9" />

          <g id="room-window">
            <rect class="window-shadow" x="82" y="78" width="265" height="242" rx="35" />
            <rect class="window-frame" x="70" y="65" width="265" height="242" rx="35" />
            <rect class="window-sky" x="91" y="86" width="223" height="198" rx="20" />
            <circle class="window-sun-glow" cx="150" cy="139" r="47" filter="url(#lampGlow)" />
            <circle class="window-sun" cx="150" cy="139" r="25" />
            <g class="window-moon">
              <circle cx="257" cy="132" r="25" />
              <circle class="moon-cut" cx="268" cy="121" r="23" />
            </g>
            <g class="window-stars">
              <path d="M130 118l5 11 11 5-11 5-5 11-5-11-11-5 11-5z" />
              <circle cx="224" cy="105" r="4" />
              <circle cx="278" cy="183" r="3" />
              <circle cx="180" cy="208" r="3" />
            </g>
            <path class="window-hill far" d="M91 224c42-35 69-35 105-5 32-45 78-38 118 4v61H91z" />
            <path class="window-hill near" d="M91 251c48-31 92-23 129 5 33-24 62-19 94 4v24H91z" />
            <path class="window-cross" d="M203 79v214M81 190h244" />
            <path class="curtain curtain-left" d="M68 71c-27 76-31 168 2 247 28-9 45-25 49-47-25-57-17-137 6-190-17-11-36-14-57-10z" />
            <path class="curtain curtain-right" d="M336 72c24 73 28 163-1 242-24-8-39-23-43-43 20-59 15-132-5-188 14-10 30-14 49-11z" />
            <path class="curtain-tie" d="M88 216c-10 10-12 25-4 39m231-39c10 10 12 25 4 39" />
            <path class="window-sill" d="M58 306h291" />
          </g>

          <path class="room-sunbeam" d="M92 92h105l286 568H188z" filter="url(#sunlightBlur)" />

          <g id="room-shelf">
            <path class="shelf-board" d="M536 132h154l8 18H527z" />
            <path class="book book-one" d="M551 82h25v50h-25z" />
            <path class="book book-two" d="M580 74h28v58h-28z" />
            <path class="book book-three" d="M612 88h22v44h-22z" />
            <path class="tiny-plant-pot" d="M653 103h35l-5 29h-25z" />
            <path class="tiny-plant" d="M670 103c-2-31-21-36-27-25 1 17 13 25 27 25zm1 0c4-30 24-34 29-21-3 15-15 22-29 21z" />
          </g>

          <g id="room-lamp">
            <circle class="lamp-aura" cx="648" cy="347" r="90" filter="url(#lampGlow)" />
            <path class="lamp-shade" d="M603 286c18-25 72-25 91 0l-13 55h-66z" />
            <path class="lamp-stem" d="M648 341v91m-42 5h84" />
            <path class="lamp-switch" d="M681 326v25" />
          </g>

          <g id="room-details">
            <path class="basket" d="M92 526h109l-15 86H108z" />
            <path class="basket-weave" d="M104 554h86m-83 25h79m-52-53 7 83m30-83-7 83" />
            <circle class="yarn-ball" cx="159" cy="523" r="34" />
            <path class="yarn-line" d="M135 507c26 2 40 16 43 37m-51-19c19-10 39-10 61 0m-28-33c-16 18-22 36-17 61" />
            <path class="yarn-loose" d="M190 541c38 4 35 34 72 30" />
          </g>

          <ellipse class="rug-shadow" cx="422" cy="654" rx="262" ry="79" />
          <ellipse class="rug" cx="415" cy="641" rx="264" ry="78" />
          <path class="rug-line" d="M222 643c82-28 300-27 390 3M271 673c83 18 219 17 298-1" />

          <g id="cat-character">
            <g id="state-smug" class="cat-state state-smug" :aria-hidden="activeMood !== 'smug'">
              <g class="cat-motion">
                <g id="tail-smug" class="cat-tail">
                  <path class="tail-outline" d="M317 594c-91 8-111-64-72-108 25-28 65-13 60 23-3 23-25 29-43 18" />
                  <path class="tail-fill" d="M317 594c-91 8-111-64-72-108 25-28 65-13 60 23-3 23-25 29-43 18" />
                </g>
                <g id="body-smug" class="cat-body">
                  <path class="cat-gray cat-outline" d="M291 489c34-43 184-44 230 7 25 28 26 117 4 153-14 23-42 15-52-13-42 8-91 8-133-2-10 30-41 37-55 14-22-36-20-126 6-159z" />
                  <path class="belly-white" d="M345 534c31-24 107-24 138 3 8 25 7 68-2 91-39 10-94 9-133-1-11-26-12-67-3-93z" />
                </g>
                <g id="head-smug" class="cat-head">
                  <path class="cat-gray cat-outline" d="M279 378l28-70 67 42c31-9 65-7 93 4l70-43 14 79c31 28 40 77 23 118-22 52-85 73-156 69-71 5-137-16-157-70-14-39-5-86 23-113z" />
                  <path class="ear-pink" d="M311 333l45 28-59 22zM485 365l47-29 1 54z" />
                  <path class="muzzle-white" d="M276 473c68 33 219 32 289-4-5 70-69 104-148 108-77 0-132-31-141-104z" />
                </g>
                <g id="eyes-smug" class="cat-eyes">
                  <path class="eye-black" d="M306 435c30-34 76-42 115-18-6 47-44 78-82 65-21-7-33-24-33-47z" />
                  <path class="eye-black" d="M456 418c39-25 83-15 108 20-2 26-17 43-41 48-37 7-65-24-67-68z" />
                  <path class="eye-lime" d="M320 451c23 27 65 26 87-3M470 449c20 27 60 28 80 3" />
                </g>
                <g id="tongue-smug" class="cat-tongue">
                  <path class="tongue-shape" d="M407 528c20 1 38-3 54-11-2 28-15 42-32 41-16-1-24-13-22-30z" />
                </g>
                <g id="symbols-smug" class="cat-symbols">
                  <path class="smile-line" d="M374 516c13 10 27 11 43 2m44-1c11 5 22 4 31-5" />
                  <path class="spark spark-one" d="M587 387l8 20 20 8-20 8-8 20-8-20-20-8 20-8z" />
                  <path class="spark spark-two" d="M603 457l5 12 12 5-12 5-5 12-5-12-12-5 12-5z" />
                </g>
              </g>
            </g>

            <g id="state-cute" class="cat-state state-cute" :aria-hidden="activeMood !== 'cute'">
              <g class="cat-motion">
                <g id="tail-cute" class="cat-tail">
                  <path class="tail-outline" d="M504 545c75-46 79-119 38-140-34-18-64 16-41 46 15 19 36 8 42-8" />
                  <path class="tail-fill" d="M504 545c75-46 79-119 38-140-34-18-64 16-41 46 15 19 36 8 42-8" />
                </g>
                <g id="body-cute" class="cat-body">
                  <path class="cat-gray cat-outline" d="M342 470c64-41 179-12 210 67 17 42 9 111-10 128-17 15-42 2-49-29-49 9-98 7-144-2-19 32-49 42-63 22-20-29-13-140 56-186z" />
                  <path class="belly-white" d="M375 499c63-28 140 9 157 72 8 30 2 64-9 79-13 2-25-5-30-23-49 9-98 7-144-2-9 15-20 25-33 29-9-51 1-126 59-155z" />
                  <path class="butt-mark" d="M435 523l19 19m0-19-19 19" />
                </g>
                <g id="head-cute" class="cat-head">
                  <g class="peek-head">
                    <path class="cat-gray cat-outline" d="M230 405l10-74 63 39c30-12 64-11 92 1l64-47 5 82c25 26 31 69 14 105-23 48-81 67-141 61-61-3-108-31-119-79-8-34 0-65 20-86z" />
                    <path class="ear-pink" d="M248 353l45 28-51 24zM409 379l39-31 2 53z" />
                    <path class="muzzle-white" d="M230 491c49 25 157 29 239-7-9 57-66 88-134 87-58-5-95-31-105-80z" />
                  </g>
                </g>
                <g id="eyes-cute" class="cat-eyes cute-eyes">
                  <ellipse class="eye-lime-fill" cx="295" cy="449" rx="57" ry="70" />
                  <ellipse class="eye-black-fill" cx="300" cy="454" rx="48" ry="61" />
                  <ellipse class="eye-lime-fill" cx="409" cy="442" rx="57" ry="71" />
                  <ellipse class="eye-black-fill" cx="414" cy="448" rx="48" ry="62" />
                  <ellipse class="eye-shine" cx="283" cy="430" rx="16" ry="20" />
                  <ellipse class="eye-shine" cx="397" cy="423" rx="16" ry="20" />
                </g>
                <g id="tongue-cute" class="cat-tongue"><path class="tiny-nose" d="M348 522l13-1-6 11z" /></g>
                <g id="symbols-cute" class="cat-symbols">
                  <path class="tiny-mouth" d="M355 533c-4 9-13 11-20 4m20-4c5 8 13 9 20 3" />
                  <path class="heart" d="M574 368c10-18 38-4 27 17-7 13-28 25-28 25s-17-17-18-28c-2-17 14-24 19-14z" />
                </g>
              </g>
            </g>

            <g id="state-innocent" class="cat-state state-innocent" :aria-hidden="activeMood !== 'innocent'">
              <g class="cat-motion">
                <g id="tail-innocent" class="cat-tail">
                  <path class="tail-outline" d="M305 606c-77 16-105-41-80-84 18-31 56-25 64 6" />
                  <path class="tail-fill" d="M305 606c-77 16-105-41-80-84 18-31 56-25 64 6" />
                </g>
                <g id="body-innocent" class="cat-body">
                  <path class="cat-gray cat-outline" d="M330 517c40-36 128-35 170 1 25 22 30 104 11 139-13 24-40 19-50-8-29 9-62 9-91 0-10 27-39 32-52 8-19-35-13-116 12-140z" />
                  <path class="belly-white" d="M374 536c23-13 61-14 85 1 13 29 12 79-2 106-27 8-56 8-84 0-13-29-13-80 1-107z" />
                </g>
                <g id="head-innocent" class="cat-head">
                  <path class="cat-gray cat-outline" d="M250 371l23-72 69 45c45-14 102-13 146 4l69-43 12 80c28 28 36 76 19 119-22 58-91 85-177 81-84 3-153-24-173-84-14-43-3-95 26-123z" />
                  <path class="ear-pink" d="M277 324l48 30-58 25zM506 357l47-29 2 57z" />
                  <path class="muzzle-white" d="M243 491c74 41 261 42 340-3-9 67-81 97-172 97-88-1-155-31-168-94z" />
                </g>
                <g id="eyes-innocent" class="cat-eyes innocent-eyes">
                  <ellipse class="eye-lime-fill" cx="330" cy="465" rx="69" ry="80" />
                  <ellipse class="eye-black-fill" cx="335" cy="468" rx="59" ry="70" />
                  <ellipse class="eye-lime-fill" cx="493" cy="463" rx="69" ry="82" />
                  <ellipse class="eye-black-fill" cx="498" cy="467" rx="59" ry="72" />
                  <ellipse class="eye-shine large" cx="312" cy="438" rx="20" ry="25" />
                  <ellipse class="eye-shine large" cx="475" cy="436" rx="20" ry="25" />
                  <circle class="eye-shine small" cx="351" cy="490" r="9" />
                  <circle class="eye-shine small" cx="514" cy="491" r="9" />
                </g>
                <g id="tongue-innocent" class="cat-tongue"><path class="tiny-nose" d="M405 539l15-1-7 12z" /></g>
                <g id="symbols-innocent" class="cat-symbols">
                  <g class="badge">
                    <path d="M330 289c2-54 49-91 104-85 57 6 91 49 82 103-9 51-52 82-107 75-52-6-81-42-79-93z" />
                    <text x="422" y="273" text-anchor="middle">100%</text>
                    <text x="422" y="326" text-anchor="middle">笨蛋</text>
                  </g>
                  <path class="tiny-mouth" d="M413 552c-5 8-13 10-20 4m20-4c5 8 13 9 20 2" />
                </g>
              </g>
            </g>

            <g id="state-angry" class="cat-state state-angry" :aria-hidden="activeMood !== 'angry'">
              <g class="cat-motion angry-motion">
                <g id="tail-angry" class="cat-tail">
                  <path class="tail-outline" d="M315 590c-86 8-107-62-73-109 25-35 72-18 67 22-3 26-29 33-48 17" />
                  <path class="tail-fill" d="M315 590c-86 8-107-62-73-109 25-35 72-18 67 22-3 26-29 33-48 17" />
                </g>
                <g id="body-angry" class="cat-body">
                  <path class="cat-gray cat-outline" d="M287 500c38-42 185-47 234 0 32 31 28 122 5 153-16 21-44 11-52-16-43 9-89 8-132-2-10 29-41 36-56 13-20-32-23-118 1-148z" />
                  <path class="belly-white" d="M337 548c36-25 112-25 148 1 7 26 6 58-2 80-41 9-95 8-135-2-10-22-14-53-11-79z" />
                </g>
                <g id="head-angry" class="cat-head">
                  <path class="cat-gray cat-outline" d="M270 381l27-74 70 43c32-10 69-9 101 3l73-43 12 83c30 30 38 77 20 116-24 52-88 71-158 68-72 4-137-17-157-70-14-39-4-91 25-118z" />
                  <path class="ear-pink" d="M301 332l48 28-61 25zM488 364l50-30 1 58z" />
                  <path class="muzzle-white" d="M273 475c71 31 223 32 293-6-8 70-72 103-151 108-77 0-132-31-142-102z" />
                </g>
                <g id="eyes-angry" class="cat-eyes">
                  <path class="eye-black" d="M296 420c37-13 84-4 121 25-13 40-49 59-84 43-23-11-36-35-37-68z" />
                  <path class="eye-black" d="M455 445c36-29 82-39 117-25 0 34-14 58-38 68-35 15-68-4-79-43z" />
                  <path class="eye-lime" d="M315 456c23 22 60 24 86 2M470 458c24 22 59 20 84-4" />
                </g>
                <g id="tongue-angry" class="cat-tongue"><path class="tiny-nose" d="M408 516l16-1-8 12z" /></g>
                <g id="symbols-angry" class="cat-symbols">
                  <path class="angry-mouth" d="M380 548c21-13 47-13 70 0" />
                  <g class="anger-mark">
                    <path d="M489 364c12-2 20-10 20-23m7-5c1 15 9 22 22 23m7 7c-14 1-22 9-23 22m-8 6c0-14-7-22-20-23" />
                  </g>
                </g>
              </g>
            </g>
          </g>

          <rect class="time-wash" x="34" y="28" width="732" height="724" />
        </g>
        <rect class="room-border" x="34" y="28" width="732" height="724" rx="82" />
      </svg>

      <Transition name="cat-scene">
        <div :key="activeScene.key" class="cat-sprite-layer" :class="`sprite-${activeMood}`" aria-hidden="true">
          <span class="cat-sprite"><i v-for="frame in 5" :key="frame" class="sprite-frame"></i></span>
        </div>
      </Transition>
    </div>

    <div class="scene-caption" aria-live="polite">
      <div>
        <span class="scene-period">{{ activeScene.label }}</span>
        <time>{{ activeScene.time }}</time>
      </div>
      <p>{{ activeScene.message }}</p>
    </div>

    <div class="scene-timeline" aria-label="切换一天中的时段">
      <button
        v-for="(scene, index) in scenes"
        :key="scene.key"
        type="button"
        :class="{ active: index === activeIndex }"
        :aria-label="`切换到${scene.label}`"
        :aria-pressed="index === activeIndex"
        @click="selectScene(index)"
      >
        <span></span>
        <small>{{ scene.label }}</small>
      </button>
    </div>
  </div>
</template>

<style scoped>
.auth-day-scene {
  --room-wall: #ddd2ba;
  --room-floor: #ad8767;
  --window-sky: #e9b38e;
  --hill-far: #9b8273;
  --hill-near: #667b6a;
  --curtain: #b27668;
  --light-wash: rgba(255, 224, 173, .08);
  --sunbeam: rgba(255, 215, 146, .12);
  --lamp-alpha: .65;
  position: relative;
  z-index: 1;
  width: min(510px, 100%);
  margin: clamp(18px, 3vh, 30px) auto 0;
  color: #fffaf0;
}

.phase-morning {
  --room-wall: #e6d8bd;
  --room-floor: #ba8f68;
  --window-sky: #f1c995;
  --hill-far: #9aa687;
  --hill-near: #6f8871;
  --curtain: #bd7e69;
  --light-wash: rgba(255, 227, 166, .11);
  --sunbeam: rgba(255, 213, 132, .3);
  --lamp-alpha: .08;
}

.phase-afternoon {
  --room-wall: #dfe0c8;
  --room-floor: #ae8462;
  --window-sky: #75c7d2;
  --hill-far: #72a28c;
  --hill-near: #497663;
  --curtain: #a96d63;
  --light-wash: rgba(220, 245, 230, .035);
  --sunbeam: rgba(255, 244, 195, .045);
  --lamp-alpha: .04;
}

.phase-dusk {
  --room-wall: #cdbba9;
  --room-floor: #99715e;
  --window-sky: #b87978;
  --hill-far: #796779;
  --hill-near: #54596b;
  --curtain: #8d5f66;
  --light-wash: rgba(110, 69, 86, .12);
  --sunbeam: rgba(255, 183, 126, .08);
  --lamp-alpha: .78;
}

.phase-night {
  --room-wall: #8f938b;
  --room-floor: #6e645f;
  --window-sky: #344d57;
  --hill-far: #405b60;
  --hill-near: #314a4c;
  --curtain: #6f5864;
  --light-wash: rgba(31, 45, 55, .28);
  --sunbeam: rgba(255, 215, 150, 0);
  --lamp-alpha: 1;
}

.day-scene-canvas {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  filter: drop-shadow(0 20px 22px rgba(21, 35, 29, .15));
}

.day-scene-canvas svg { display: block; width: 100%; height: 100%; overflow: visible; }
.room-shadow { fill: rgba(26, 38, 32, .18); }
.room-wall { fill: var(--room-wall); transition: fill 1.2s ease; }
.room-floor { fill: var(--room-floor); transition: fill 1.2s ease; }
.floor-seam { fill: none; stroke: rgba(84, 61, 53, .18); stroke-width: 5; stroke-linecap: round; }
.window-shadow { fill: rgba(73, 52, 46, .15); transform: translateY(9px); }
.window-frame { fill: #f0e7d0; stroke: #292522; stroke-width: 10; stroke-linejoin: round; }
.window-sky { fill: var(--window-sky); transition: fill 1.2s ease; }
.window-sun, .window-sun-glow { fill: #ffe59b; transform-box: fill-box; transform-origin: center; transition: fill 1.2s ease, opacity 1.2s ease, transform 1.2s ease; }
.window-sun-glow { opacity: .62; }
.phase-morning .window-sun, .phase-morning .window-sun-glow { fill: #ffd98a; transform: translate(-7px, 16px) scale(.92); }
.phase-morning .window-sun-glow { opacity: .82; }
.phase-afternoon .window-sun, .phase-afternoon .window-sun-glow { fill: #ffe89d; transform: translate(54px, -18px) scale(1.08); }
.phase-afternoon .window-sun-glow { opacity: .5; }
.window-moon { fill: #f6e8b6; opacity: 0; transition: opacity 1.2s ease, transform 1.2s ease; transform: translateY(22px); }
.moon-cut { fill: var(--window-sky); transition: fill 1.2s ease; }
.window-stars { fill: #fff1b7; opacity: 0; transition: opacity 1.2s ease; }
.phase-dusk .window-stars { opacity: .34; }
.phase-night .window-stars { opacity: .92; animation: star-twinkle 2.8s ease-in-out infinite alternate; }
.phase-dusk .window-sun, .phase-dusk .window-sun-glow { transform: translate(105px, 74px); opacity: .72; }
.phase-night .window-sun, .phase-night .window-sun-glow { opacity: 0; transform: translate(140px, 100px); }
.phase-night .window-moon { opacity: 1; transform: translateY(0); }
.window-hill { transition: fill 1.2s ease; }
.window-hill.far { fill: var(--hill-far); }
.window-hill.near { fill: var(--hill-near); }
.window-cross, .window-sill { fill: none; stroke: #eee4cd; stroke-width: 12; stroke-linecap: round; }
.room-sunbeam { fill: var(--sunbeam); pointer-events: none; transform-box: fill-box; transform-origin: top left; transition: fill 1.2s ease, opacity 1.2s ease, transform 1.2s ease; }
.phase-morning .room-sunbeam { transform: translate(-4px, 7px) rotate(-2deg); }
.phase-afternoon .room-sunbeam { transform: translate(72px, -22px) rotate(7deg); }
.curtain { fill: var(--curtain); stroke: #292522; stroke-width: 10; stroke-linejoin: round; transition: fill 1.2s ease; }
.curtain-tie { fill: none; stroke: #e7c69a; stroke-width: 12; stroke-linecap: round; }
.shelf-board { fill: #7f5c49; stroke: #292522; stroke-width: 8; stroke-linejoin: round; }
.book { stroke: #292522; stroke-width: 6; stroke-linejoin: round; }
.book-one { fill: #869a7c; }
.book-two { fill: #c08070; }
.book-three { fill: #d3ae6f; }
.tiny-plant-pot { fill: #b9795e; stroke: #292522; stroke-width: 6; stroke-linejoin: round; }
.tiny-plant { fill: #6e8e71; stroke: #292522; stroke-width: 5; stroke-linejoin: round; }
.lamp-aura { fill: #ffd985; opacity: var(--lamp-alpha); transition: opacity 1.2s ease; }
.lamp-shade { fill: #f5d28b; stroke: #292522; stroke-width: 10; stroke-linejoin: round; }
.lamp-stem, .lamp-switch { fill: none; stroke: #292522; stroke-width: 10; stroke-linecap: round; }
.lamp-switch { stroke-width: 5; }
.basket { fill: #bd8d61; stroke: #292522; stroke-width: 9; stroke-linejoin: round; }
.basket-weave { fill: none; stroke: #8e684e; stroke-width: 5; stroke-linecap: round; }
.yarn-ball { fill: #6f91a0; stroke: #292522; stroke-width: 9; }
.yarn-line, .yarn-loose { fill: none; stroke: #314d59; stroke-width: 6; stroke-linecap: round; }
.rug-shadow { fill: rgba(50, 43, 41, .2); transform: translateY(10px); }
.rug { fill: #c69a75; }
.rug-line { fill: none; stroke: #a87d62; stroke-width: 7; stroke-linecap: round; opacity: .65; }
.time-wash { fill: var(--light-wash); pointer-events: none; transition: fill 1.2s ease; }
.room-border { fill: none; stroke: #f0e8d6; stroke-width: 14; }

#cat-character { display: none; }

.cat-sprite-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  clip-path: inset(3.5% round 10.25%);
  pointer-events: none;
  transform-box: fill-box;
  transform-origin: center bottom;
  animation: sprite-float 2.8s ease-in-out infinite alternate;
}

.cat-scene-enter-active,
.cat-scene-leave-active {
  transition: opacity .42s ease, filter .42s ease;
}

.cat-scene-enter-from,
.cat-scene-leave-to {
  opacity: 0;
  filter: blur(5px);
}

.cat-sprite {
  position: absolute;
  bottom: 7.5%;
  left: 50%;
  width: 64%;
  aspect-ratio: 1;
  opacity: 1;
  filter: drop-shadow(0 10px 7px rgba(45, 36, 34, .2));
  transform: translateX(-50%);
  transform-origin: center bottom;
  transition: transform .18s ease, filter 1.2s ease;
}

.sprite-smug { --sprite-image: url('../assets/cat-smug-strip.png'); }
.sprite-cute { --sprite-image: url('../assets/cat-cute-strip.png'); }
.sprite-innocent { --sprite-image: url('../assets/cat-innocent-strip.png'); }
.sprite-angry { --sprite-image: url('../assets/cat-angry-strip.png'); }

.sprite-frame {
  position: absolute;
  inset: 0;
  display: block;
  opacity: 0;
  background-image: var(--sprite-image);
  background-repeat: no-repeat;
  background-size: 500% 100%;
  animation: sprite-frame-fade 1.8s ease-in-out 1 both;
}

.sprite-frame:nth-child(1) { background-position: 0 0; animation-name: sprite-frame-first; animation-delay: 0s; }
.sprite-frame:nth-child(2) { background-position: 25% 0; animation-delay: 1.5s; }
.sprite-frame:nth-child(3) { background-position: 50% 0; animation-delay: 3s; }
.sprite-frame:nth-child(4) { background-position: 75% 0; animation-delay: 4.5s; }
.sprite-frame:nth-child(5) { background-position: 100% 0; animation-name: sprite-frame-last; animation-delay: 6s; }

.mood-angry .cat-sprite-layer { animation: sprite-angry-shake 1.1s ease-in-out infinite alternate; }
.phase-night .cat-sprite { filter: brightness(.86) saturate(.82) drop-shadow(0 10px 7px rgba(27, 29, 34, .26)); }

.blinking .cat-sprite { transform: translateX(-50%) translateY(1px) scale(1, .99); }

.cat-state {
  opacity: 0;
  pointer-events: none;
  transform: translateY(12px) scale(.94) rotate(-2deg);
  transform-box: fill-box;
  transform-origin: center bottom;
  transition: opacity .28s ease, transform .36s cubic-bezier(.2, .82, .2, 1);
}

.mood-smug .state-smug,
.mood-cute .state-cute,
.mood-innocent .state-innocent,
.mood-angry .state-angry {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0) scale(1) rotate(0);
}

.cat-motion {
  transform-box: fill-box;
  transform-origin: center bottom;
  animation: cat-float 2.8s ease-in-out infinite alternate;
}

.cat-outline { stroke: #171615; stroke-width: 14; stroke-linecap: round; stroke-linejoin: round; }
.cat-gray { fill: #d2d3d2; }
.belly-white, .muzzle-white { fill: #f7f4ec; }
.ear-pink, .tongue-shape, .tiny-nose { fill: #efa9b7; stroke: #171615; stroke-width: 10; stroke-linejoin: round; }
.tail-outline, .tail-fill { fill: none; stroke-linecap: round; stroke-linejoin: round; }
.tail-outline { stroke: #171615; stroke-width: 66; }
.tail-fill { stroke: #aaa8bb; stroke-width: 43; }
.cat-tail { transform-box: fill-box; transform-origin: right bottom; animation: tail-sway 2.4s ease-in-out infinite alternate; }
.cat-eyes { transform-box: fill-box; transform-origin: center; transition: transform .075s ease; }
.blinking .cat-eyes { transform: scaleY(.09); }
.eye-black, .eye-black-fill { fill: #090909; stroke: #171615; stroke-width: 8; stroke-linejoin: round; }
.eye-lime { fill: none; stroke: #efff72; stroke-width: 12; stroke-linecap: round; }
.eye-lime-fill { fill: #efff72; stroke: #171615; stroke-width: 10; }
.eye-shine { fill: #fff; }
.smile-line, .tiny-mouth, .angry-mouth { fill: none; stroke: #171615; stroke-width: 10; stroke-linecap: round; }
.tongue-shape { transform-box: fill-box; transform-origin: center top; animation: tongue-peek 4.8s ease-in-out infinite; }
.spark { fill: #fbef66; stroke: #171615; stroke-width: 7; stroke-linejoin: round; transform-box: fill-box; transform-origin: center; animation: sparkle 1.8s ease-in-out infinite alternate; }
.spark-two { animation-delay: -.7s; }
.peek-head { transform-box: fill-box; transform-origin: right bottom; animation: peek-wiggle 2.6s ease-in-out infinite alternate; }
.butt-mark { fill: none; stroke: #b96f82; stroke-width: 10; stroke-linecap: round; }
.heart { fill: #e78b98; stroke: #171615; stroke-width: 7; stroke-linejoin: round; transform-box: fill-box; transform-origin: center; animation: heart-pop 1.6s ease-in-out infinite alternate; }
.badge path { fill: #bfe4f5; stroke: #171615; stroke-width: 12; stroke-linejoin: round; }
.badge text { fill: #171615; font-family: 'Microsoft YaHei', sans-serif; font-size: 40px; font-weight: 900; letter-spacing: 2px; }
.anger-mark { transform-box: fill-box; transform-origin: center; animation: anger-jitter .55s steps(2, end) infinite; }
.anger-mark path { fill: none; stroke: #ef3e4d; stroke-width: 14; stroke-linecap: round; }
.angry-motion { animation: angry-shake .48s steps(2, end) infinite; }

.scene-caption {
  min-height: 46px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  margin: 8px 31px 0;
}

.scene-caption > div { display: flex; align-items: baseline; gap: 8px; }
.scene-period { font-family: Georgia, 'Microsoft YaHei', serif; font-size: 18px; font-weight: 650; }
.scene-caption time { color: rgba(255, 250, 240, .67); font-size: 10px; letter-spacing: .08em; }
.scene-caption p { margin: 0; color: rgba(255, 250, 240, .78); font-size: 11px; line-height: 1.7; }

.scene-timeline {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin: 9px 30px 0;
}

.scene-timeline::before { position: absolute; top: 5px; right: 10%; left: 10%; height: 1px; background: rgba(255, 255, 255, .2); content: ''; }
.scene-timeline button { position: relative; display: grid; justify-items: center; gap: 7px; padding: 0; color: rgba(255, 250, 240, .48); background: transparent; font: inherit; }
.scene-timeline button:hover, .scene-timeline button.active { color: #fffaf0; }
.scene-timeline button > span { z-index: 1; width: 10px; height: 10px; border: 2px solid rgba(255, 250, 240, .55); border-radius: 50%; background: rgba(54, 76, 65, .86); transition: transform .35s ease, border-color .35s ease, background .35s ease; }
.scene-timeline button.active > span { border-color: #f2db9e; background: #f2db9e; box-shadow: 0 0 0 5px rgba(242, 219, 158, .12); transform: scale(1.08); }
.scene-timeline small { font-size: 9px; font-weight: 500; }

@keyframes cat-float { to { transform: translateY(-6px) scale(1.012, .988); } }
@keyframes tail-sway { to { transform: rotate(-7deg); } }
@keyframes tongue-peek { 0%, 12%, 80%, 100% { transform: scaleY(.72); } 22%, 65% { transform: scaleY(1); } }
@keyframes sparkle { from { opacity: .58; transform: scale(.82) rotate(-5deg); } to { opacity: 1; transform: scale(1.08) rotate(4deg); } }
@keyframes peek-wiggle { to { transform: rotate(-2deg) translate(-2px, -3px); } }
@keyframes heart-pop { to { transform: translateY(-5px) scale(1.08) rotate(4deg); } }
@keyframes anger-jitter { 0%, 100% { transform: translate(0); } 50% { transform: translate(3px, -2px) rotate(3deg); } }
@keyframes angry-shake { 0%, 100% { transform: translateX(-2px) rotate(-.4deg); } 50% { transform: translateX(2px) rotate(.4deg); } }
@keyframes star-twinkle { from { opacity: .45; } to { opacity: .95; } }
@keyframes sprite-frame-fade {
  0% { opacity: 0; filter: blur(3px); transform: translateY(3px) scale(.988); }
  15%, 75% { opacity: 1; filter: blur(0); transform: translateY(0) scale(1); }
  100% { opacity: 0; filter: blur(2.5px); transform: translateY(-2px) scale(1.006); }
}
@keyframes sprite-frame-first {
  0%, 75% { opacity: 1; filter: blur(0); transform: translateY(0) scale(1); }
  100% { opacity: 0; filter: blur(2.5px); transform: translateY(-2px) scale(1.006); }
}
@keyframes sprite-frame-last {
  0% { opacity: 0; filter: blur(3px); transform: translateY(3px) scale(.988); }
  15%, 100% { opacity: 1; filter: blur(0); transform: translateY(0) scale(1); }
}
@keyframes sprite-float { to { transform: translateY(-5px) scale(1.008, .992); } }
@keyframes sprite-angry-shake {
  from { transform: translateX(-.6px) translateY(.2px) rotate(-.08deg); }
  to { transform: translateX(.6px) translateY(-.2px) rotate(.08deg); }
}

@media (max-height: 900px) and (min-width: 701px) {
  .auth-day-scene { width: min(430px, 92%); margin-top: 10px; }
  .scene-caption { min-height: 40px; margin-top: 4px; }
  .scene-timeline { margin-top: 4px; }
}

@media (prefers-reduced-motion: reduce) {
  .auth-day-scene *,
  .auth-day-scene *::before,
  .auth-day-scene *::after {
    animation: none !important;
    transition-duration: .01ms !important;
  }

  .sprite-frame:first-child { opacity: 1; }
}
</style>
