import mewFoodUrl from './assets/audio/cat-mew-food.wav'
import mewPurrUrl from './assets/audio/cat-mew-purr.wav'
import mewPurrSecondUrl from './assets/audio/cat-mew-purr-2.wav'
import siameseMeowUrl from './assets/audio/cat-siamese-meow.wav'
import softMewUrl from './assets/audio/cat-soft-mew.wav'

export type CatSoundCue = 'greeting' | 'touch' | 'reminder' | 'happy' | 'protest'

type CatSoundVariant = {
  url: string
  volume: number
  playbackRate: number
}

const SOUND_VARIANTS: Record<CatSoundCue, readonly CatSoundVariant[]> = {
  greeting: [
    { url: siameseMeowUrl, volume: 0.44, playbackRate: 1.06 },
    { url: softMewUrl, volume: 0.38, playbackRate: 1.08 },
    { url: mewPurrUrl, volume: 0.4, playbackRate: 1.04 },
  ],
  touch: [
    { url: mewPurrUrl, volume: 0.36, playbackRate: 1.08 },
    { url: mewPurrSecondUrl, volume: 0.34, playbackRate: 1.1 },
    { url: softMewUrl, volume: 0.36, playbackRate: 1.12 },
  ],
  reminder: [
    { url: mewFoodUrl, volume: 0.48, playbackRate: 1 },
    { url: siameseMeowUrl, volume: 0.46, playbackRate: 0.98 },
  ],
  happy: [
    { url: softMewUrl, volume: 0.38, playbackRate: 1.12 },
    { url: mewPurrUrl, volume: 0.4, playbackRate: 1.1 },
    { url: mewPurrSecondUrl, volume: 0.36, playbackRate: 1.14 },
  ],
  protest: [
    { url: mewFoodUrl, volume: 0.4, playbackRate: 0.92 },
    { url: siameseMeowUrl, volume: 0.4, playbackRate: 0.9 },
  ],
}

let activeAudio: HTMLAudioElement | undefined
const fadingAudios = new Set<HTMLAudioElement>()
const fadeTimers = new Set<number>()
const lastVariantIndex = new Map<CatSoundCue, number>()

function pickVariant(cue: CatSoundCue): CatSoundVariant {
  const variants = SOUND_VARIANTS[cue]
  const previousIndex = lastVariantIndex.get(cue)
  let nextIndex = Math.floor(Math.random() * variants.length)
  if (variants.length > 1 && nextIndex === previousIndex) {
    nextIndex = (nextIndex + 1 + Math.floor(Math.random() * (variants.length - 1))) % variants.length
  }
  lastVariantIndex.set(cue, nextIndex)
  return variants[nextIndex]
}

function fadeOut(audio: HTMLAudioElement): void {
  fadingAudios.add(audio)
  const startingVolume = audio.volume
  let step = 0
  const timer = window.setInterval(() => {
    step += 1
    audio.volume = Math.max(0, startingVolume * (1 - step / 4))
    if (step < 4) return
    window.clearInterval(timer)
    fadeTimers.delete(timer)
    fadingAudios.delete(audio)
    audio.pause()
    audio.src = ''
  }, 12)
  fadeTimers.add(timer)
}

export function playCatSound(cue: CatSoundCue): void {
  if (activeAudio) fadeOut(activeAudio)

  const variant = pickVariant(cue)
  const audio = new Audio(variant.url)
  audio.volume = variant.volume
  audio.playbackRate = variant.playbackRate * (0.98 + Math.random() * 0.04)
  audio.preservesPitch = false
  activeAudio = audio
  audio.addEventListener('ended', () => {
    if (activeAudio === audio) activeAudio = undefined
    audio.src = ''
  }, { once: true })
  void audio.play().catch((error: unknown) => {
    if (activeAudio === audio) activeAudio = undefined
    console.warn('Failed to play the cat sound.', error)
  })
}

export function disposeCatSounds(): void {
  if (activeAudio) {
    activeAudio.pause()
    activeAudio.src = ''
    activeAudio = undefined
  }
  fadeTimers.forEach((timer) => window.clearInterval(timer))
  fadeTimers.clear()
  fadingAudios.forEach((audio) => {
    audio.pause()
    audio.src = ''
  })
  fadingAudios.clear()
  lastVariantIndex.clear()
}
