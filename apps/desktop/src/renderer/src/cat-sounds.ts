export type CatSoundCue = 'greeting' | 'touch' | 'reminder' | 'happy' | 'protest'

type CatSoundNote = {
  start: number
  duration: number
  fromFrequency: number
  toFrequency: number
  volume: number
  vibrato?: number
}

type CatSoundPattern = readonly CatSoundNote[]

const SOUND_PATTERNS: Record<CatSoundCue, readonly CatSoundPattern[]> = {
  greeting: [
    [
      { start: 0, duration: 0.22, fromFrequency: 610, toFrequency: 920, volume: 0.7, vibrato: 10 },
      { start: 0.16, duration: 0.26, fromFrequency: 900, toFrequency: 660, volume: 0.54, vibrato: 8 },
    ],
    [
      { start: 0, duration: 0.13, fromFrequency: 720, toFrequency: 1_080, volume: 0.52 },
      { start: 0.15, duration: 0.18, fromFrequency: 790, toFrequency: 1_160, volume: 0.64, vibrato: 12 },
    ],
    [
      { start: 0, duration: 0.3, fromFrequency: 540, toFrequency: 790, volume: 0.68, vibrato: 7 },
      { start: 0.23, duration: 0.2, fromFrequency: 760, toFrequency: 980, volume: 0.46 },
    ],
  ],
  touch: [
    [
      { start: 0, duration: 0.11, fromFrequency: 820, toFrequency: 1_080, volume: 0.48 },
      { start: 0.1, duration: 0.11, fromFrequency: 910, toFrequency: 1_180, volume: 0.55 },
      { start: 0.2, duration: 0.16, fromFrequency: 980, toFrequency: 1_280, volume: 0.62, vibrato: 14 },
    ],
    [
      { start: 0, duration: 0.16, fromFrequency: 700, toFrequency: 980, volume: 0.58, vibrato: 15 },
      { start: 0.13, duration: 0.24, fromFrequency: 960, toFrequency: 740, volume: 0.52, vibrato: 9 },
    ],
    [
      { start: 0, duration: 0.09, fromFrequency: 940, toFrequency: 1_240, volume: 0.46 },
      { start: 0.12, duration: 0.09, fromFrequency: 1_020, toFrequency: 1_330, volume: 0.52 },
      { start: 0.24, duration: 0.09, fromFrequency: 940, toFrequency: 1_260, volume: 0.48 },
    ],
  ],
  reminder: [
    [
      { start: 0, duration: 0.22, fromFrequency: 640, toFrequency: 900, volume: 0.64, vibrato: 9 },
      { start: 0.36, duration: 0.26, fromFrequency: 700, toFrequency: 1_020, volume: 0.72, vibrato: 10 },
    ],
    [
      { start: 0, duration: 0.15, fromFrequency: 760, toFrequency: 1_080, volume: 0.58 },
      { start: 0.2, duration: 0.15, fromFrequency: 830, toFrequency: 1_150, volume: 0.62 },
      { start: 0.4, duration: 0.2, fromFrequency: 900, toFrequency: 1_240, volume: 0.68, vibrato: 11 },
    ],
  ],
  happy: [
    [
      { start: 0, duration: 0.12, fromFrequency: 660, toFrequency: 790, volume: 0.46 },
      { start: 0.1, duration: 0.13, fromFrequency: 790, toFrequency: 960, volume: 0.54 },
      { start: 0.21, duration: 0.2, fromFrequency: 940, toFrequency: 1_180, volume: 0.62, vibrato: 12 },
    ],
    [
      { start: 0, duration: 0.18, fromFrequency: 720, toFrequency: 1_040, volume: 0.6, vibrato: 13 },
      { start: 0.14, duration: 0.22, fromFrequency: 980, toFrequency: 1_260, volume: 0.52, vibrato: 15 },
    ],
  ],
  protest: [
    [
      { start: 0, duration: 0.3, fromFrequency: 720, toFrequency: 430, volume: 0.58, vibrato: 8 },
      { start: 0.27, duration: 0.12, fromFrequency: 620, toFrequency: 520, volume: 0.36 },
    ],
    [
      { start: 0, duration: 0.16, fromFrequency: 680, toFrequency: 510, volume: 0.5 },
      { start: 0.19, duration: 0.2, fromFrequency: 760, toFrequency: 480, volume: 0.56, vibrato: 7 },
    ],
  ],
}

let audioContext: AudioContext | undefined
let masterGain: GainNode | undefined
let activeCueGain: GainNode | undefined
const lastPatternIndex = new Map<CatSoundCue, number>()

function getAudioGraph(): { context: AudioContext; output: GainNode } {
  audioContext ??= new AudioContext()
  if (!masterGain) {
    masterGain = audioContext.createGain()
    masterGain.gain.value = 0.2
    masterGain.connect(audioContext.destination)
  }
  return { context: audioContext, output: masterGain }
}

function pickPattern(cue: CatSoundCue): CatSoundPattern {
  const patterns = SOUND_PATTERNS[cue]
  const previousIndex = lastPatternIndex.get(cue)
  let nextIndex = Math.floor(Math.random() * patterns.length)
  if (patterns.length > 1 && nextIndex === previousIndex) {
    nextIndex = (nextIndex + 1 + Math.floor(Math.random() * (patterns.length - 1))) % patterns.length
  }
  lastPatternIndex.set(cue, nextIndex)
  return patterns[nextIndex]
}

function scheduleNote(
  context: AudioContext,
  output: AudioNode,
  note: CatSoundNote,
  pitchScale: number,
): void {
  const startsAt = context.currentTime + note.start
  const endsAt = startsAt + note.duration
  const voice = context.createOscillator()
  const overtone = context.createOscillator()
  const envelope = context.createGain()
  const overtoneEnvelope = context.createGain()
  const toneFilter = context.createBiquadFilter()

  voice.type = 'triangle'
  overtone.type = 'sine'
  voice.frequency.setValueAtTime(note.fromFrequency * pitchScale, startsAt)
  voice.frequency.exponentialRampToValueAtTime(note.toFrequency * pitchScale, endsAt)
  overtone.frequency.setValueAtTime(note.fromFrequency * pitchScale * 2.01, startsAt)
  overtone.frequency.exponentialRampToValueAtTime(note.toFrequency * pitchScale * 2.01, endsAt)

  const attackEndsAt = startsAt + Math.min(0.035, note.duration * 0.24)
  envelope.gain.setValueAtTime(0.0001, startsAt)
  envelope.gain.exponentialRampToValueAtTime(note.volume, attackEndsAt)
  envelope.gain.exponentialRampToValueAtTime(0.0001, endsAt)
  overtoneEnvelope.gain.setValueAtTime(0.0001, startsAt)
  overtoneEnvelope.gain.exponentialRampToValueAtTime(note.volume * 0.14, attackEndsAt)
  overtoneEnvelope.gain.exponentialRampToValueAtTime(0.0001, endsAt)

  toneFilter.type = 'lowpass'
  toneFilter.frequency.value = 2_400
  toneFilter.Q.value = 0.7
  voice.connect(envelope).connect(toneFilter)
  overtone.connect(overtoneEnvelope).connect(toneFilter)
  toneFilter.connect(output)

  if (note.vibrato) {
    const vibrato = context.createOscillator()
    const vibratoDepth = context.createGain()
    vibrato.frequency.value = note.vibrato
    vibratoDepth.gain.value = 13
    vibrato.connect(vibratoDepth)
    vibratoDepth.connect(voice.frequency)
    vibrato.start(startsAt)
    vibrato.stop(endsAt)
  }

  voice.start(startsAt)
  overtone.start(startsAt)
  voice.stop(endsAt)
  overtone.stop(endsAt)
}

function createCueOutput(context: AudioContext, output: AudioNode): GainNode {
  if (activeCueGain) {
    // A short fade keeps rapid interactions from stacking several bright tones or ending with a click.
    activeCueGain.gain.cancelScheduledValues(context.currentTime)
    activeCueGain.gain.setValueAtTime(Math.max(activeCueGain.gain.value, 0.0001), context.currentTime)
    activeCueGain.gain.exponentialRampToValueAtTime(0.0001, context.currentTime + 0.025)
    const previousCueGain = activeCueGain
    window.setTimeout(() => previousCueGain.disconnect(), 50)
  }

  const cueGain = context.createGain()
  cueGain.gain.value = 1
  cueGain.connect(output)
  activeCueGain = cueGain
  return cueGain
}

export function playCatSound(cue: CatSoundCue): void {
  try {
    const { context, output } = getAudioGraph()
    void context.resume().then(() => {
      const cueOutput = createCueOutput(context, output)
      const pitchScale = 0.97 + Math.random() * 0.06
      pickPattern(cue).forEach((note) => scheduleNote(context, cueOutput, note, pitchScale))
    }).catch((error: unknown) => {
      console.warn('Failed to play the cat sound.', error)
    })
  } catch (error) {
    console.warn('Failed to play the cat sound.', error)
  }
}

export function disposeCatSounds(): void {
  const context = audioContext
  audioContext = undefined
  masterGain = undefined
  activeCueGain?.disconnect()
  activeCueGain = undefined
  lastPatternIndex.clear()
  if (context && context.state !== 'closed') void context.close()
}
