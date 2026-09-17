import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import {
  app,
  BrowserWindow,
  ipcMain,
  Menu,
  nativeImage,
  powerMonitor,
  screen,
  Tray,
  type Rectangle,
} from 'electron'
import {
  CAT_ACTIVITY_DEFINITIONS,
  CAT_ACTIVITY_IDS,
  isCatActivityId,
  type CatActivityId,
  type CatActivityRequestResult,
  type CatActivitySnapshot,
  type CatFacing,
  type CatMovementMode,
} from '../shared/cat-activity'
import type { CompanionInfo } from '../shared/companion'
import type { CatProfile } from '../shared/cat-profile'
import type { Emotion } from '../shared/emotion'
import type { Reminder } from '../shared/reminder'

const COMPACT_WINDOW_WIDTH = 176
const COMPACT_WINDOW_HEIGHT = 176
const EXPANDED_WINDOW_WIDTH = 400
const EXPANDED_WINDOW_HEIGHT = 230
const MIN_VISIBLE_SIZE = 36
const MOVEMENT_TICK_MS = 50
const PROFILE_RECONCILE_INTERVAL_MS = 5 * 60_000
const PROFILE_EVENT_RETRY_DELAYS_MS = [1_000, 2_000, 5_000, 10_000, 30_000] as const
const ASSISTANT_API_URL = process.env.DESKTOP_CAT_API_URL ?? 'http://127.0.0.1:8080'
const CAT_PROFILE_API_URL = `${ASSISTANT_API_URL}/api/cat/profile`
const ASSISTANT_EVENTS_API_URL = `${ASSISTANT_API_URL}/api/events`
const EMOTIONS_API_URL = `${ASSISTANT_API_URL}/api/emotions`
const REMINDERS_API_URL = `${ASSISTANT_API_URL}/api/reminders`
const REMINDER_SYNC_INTERVAL_MS = 5 * 60_000
const REMINDER_DUE_CHECK_INTERVAL_MS = 5_000
const DEFAULT_CAT_PROFILE: CatProfile = {
  profileId: 1,
  catName: '小饼干',
  version: 0,
  updatedAt: new Date(0).toISOString(),
}

type WindowPosition = { x: number; y: number }
type Velocity = { x: number; y: number }
type ActivityPanelSide = 'left' | 'right'
type CompanionState = { firstMetDate: string }
type ServerEvent = { event: string; data: string }
type CatProfileUpdatedEvent = { profileId: number; version: number }
type ReminderChangedEvent = { reminderId: number; version: number; action: string }
type ReminderCache = { reminders: Reminder[]; notifiedTokens: string[] }

let catWindow: BrowserWindow | null = null
let tray: Tray | null = null
let isQuitting = false
let savePositionTimer: NodeJS.Timeout | undefined
let activityEndTimer: NodeJS.Timeout | undefined
let movementTimer: NodeJS.Timeout | undefined
let movementPaused = false
let precisePosition: WindowPosition = { x: 0, y: 0 }
let velocity: Velocity = { x: 0, y: 0 }
let nextDirectionChangeAt = 0
let activityPanelSide: ActivityPanelSide = 'right'
let isActivityPanelOpen = false
let companionState: CompanionState | undefined
let catProfile: CatProfile = DEFAULT_CAT_PROFILE
let profileReconcileTimer: NodeJS.Timeout | undefined
let profileEventReconnectTimer: NodeJS.Timeout | undefined
let profileEventAbortController: AbortController | undefined
let profileEventReconnectAttempt = 0
let profileEventConnectionFailed = false
let profileSyncInFlight: Promise<void> | undefined
let profileSyncFailed = false
let reminders: Reminder[] = []
let notifiedReminderTokens = new Set<string>()
let reminderSyncTimer: NodeJS.Timeout | undefined
let reminderDueCheckTimer: NodeJS.Timeout | undefined
let reminderSyncFailed = false
let reminderSyncInFlight: Promise<void> | undefined

let currentActivity: CatActivitySnapshot = {
  id: 'idle',
  startedAt: Date.now(),
  endsAt: Date.now(),
  durationMinutes: 1,
  facing: 'right',
}

function getWindowStatePath(): string {
  return join(app.getPath('userData'), 'window-state.json')
}

function getCompanionStatePath(): string {
  return join(app.getPath('userData'), 'companion-state.json')
}

function getCatProfilePath(): string {
  return join(app.getPath('userData'), 'cat-profile.json')
}

function getReminderCachePath(): string {
  return join(app.getPath('userData'), 'reminder-cache.json')
}

function isCatProfile(value: unknown): value is CatProfile {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<CatProfile>
  return Number.isSafeInteger(candidate.profileId) && (candidate.profileId ?? 0) > 0
    && typeof candidate.catName === 'string' && candidate.catName.trim().length > 0
    && Number.isSafeInteger(candidate.version) && (candidate.version ?? -1) >= 0
    && typeof candidate.updatedAt === 'string'
}

function isEmotion(value: unknown): value is Emotion {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<Emotion>
  return Number.isSafeInteger(candidate.emotionId) && (candidate.emotionId ?? 0) > 0
    && typeof candidate.content === 'string' && candidate.content.trim().length > 0
    && typeof candidate.recordDate === 'string'
    && typeof candidate.createdAt === 'string'
}

function isReminder(value: unknown): value is Reminder {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<Reminder>
  return Number.isSafeInteger(candidate.reminderId) && (candidate.reminderId ?? 0) > 0
    && typeof candidate.content === 'string' && candidate.content.trim().length > 0
    && typeof candidate.remindAt === 'string' && !Number.isNaN(Date.parse(candidate.remindAt))
    && candidate.status === 'PENDING'
    && Number.isSafeInteger(candidate.version) && (candidate.version ?? -1) >= 0
}

function reminderToken(reminder: Reminder): string {
  // 版本进入标记后，同一版本只提醒一次；网站修改后新版本仍可按新时间再次提醒。
  return `${reminder.reminderId}:${reminder.version}`
}

function loadReminderCache(): void {
  const cachePath = getReminderCachePath()
  if (!existsSync(cachePath)) return
  try {
    const candidate = JSON.parse(readFileSync(cachePath, 'utf8')) as Partial<ReminderCache>
    reminders = Array.isArray(candidate.reminders) ? candidate.reminders.filter(isReminder) : []
    notifiedReminderTokens = new Set(
      Array.isArray(candidate.notifiedTokens)
        ? candidate.notifiedTokens.filter((token): token is string => typeof token === 'string')
        : [],
    )
  } catch (error) {
    console.error('Failed to read the cached reminders.', error)
  }
}

function saveReminderCache(): void {
  const cachePath = getReminderCachePath()
  try {
    mkdirSync(dirname(cachePath), { recursive: true })
    const cache: ReminderCache = {
      reminders,
      notifiedTokens: [...notifiedReminderTokens],
    }
    writeFileSync(cachePath, JSON.stringify(cache, null, 2), 'utf8')
  } catch (error) {
    console.error('Failed to save the reminder cache.', error)
  }
}

function checkDueReminders(): void {
  if (!catWindow || catWindow.isDestroyed() || catWindow.webContents.isLoading()) return
  const now = Date.now()
  const dueReminder = reminders.find((reminder) => {
    const token = reminderToken(reminder)
    return Date.parse(reminder.remindAt) <= now && !notifiedReminderTokens.has(token)
  })
  if (!dueReminder) return

  notifiedReminderTokens.add(reminderToken(dueReminder))
  saveReminderCache()
  showCat()
  catWindow.webContents.send('desktop-cat:reminder-due', dueReminder)
}

async function performReminderSync(): Promise<void> {
  try {
    const response = await fetch(`${REMINDERS_API_URL}?scope=PENDING`, {
      signal: AbortSignal.timeout(5_000),
    })
    if (!response.ok) throw new Error(`Reminder request returned HTTP ${response.status}.`)
    const candidate: unknown = await response.json()
    if (!Array.isArray(candidate) || !candidate.every(isReminder)) {
      throw new Error('Reminder response is invalid.')
    }

    reminders = candidate
    const activeTokens = new Set(reminders.map(reminderToken))
    // 只保留服务端仍未完成的版本，避免缓存随着已完成或已删除事项一直增长。
    notifiedReminderTokens = new Set(
      [...notifiedReminderTokens].filter((token) => activeTokens.has(token)),
    )
    saveReminderCache()
    checkDueReminders()
    if (reminderSyncFailed) console.info('Reminder synchronization recovered.')
    reminderSyncFailed = false
  } catch (error) {
    if (!reminderSyncFailed) {
      console.warn('Reminder synchronization is unavailable; using cached reminders.', error)
    }
    reminderSyncFailed = true
    checkDueReminders()
  }
}

async function syncReminders(): Promise<void> {
  if (reminderSyncInFlight) return reminderSyncInFlight
  const currentSync = performReminderSync()
  reminderSyncInFlight = currentSync
  try {
    await currentSync
  } finally {
    if (reminderSyncInFlight === currentSync) reminderSyncInFlight = undefined
  }
}

async function completeReminder(reminderId: number, version: number): Promise<Reminder> {
  const response = await fetch(`${REMINDERS_API_URL}/${reminderId}/complete`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ version }),
    signal: AbortSignal.timeout(5_000),
  })
  if (!response.ok) throw new Error(`Complete reminder request returned HTTP ${response.status}.`)
  const candidate: unknown = await response.json()
  if (!candidate || typeof candidate !== 'object') throw new Error('Complete reminder response is invalid.')
  const completed = candidate as Partial<Reminder>
  if (completed.status !== 'COMPLETED' || completed.reminderId !== reminderId) {
    throw new Error('Complete reminder response is invalid.')
  }
  reminders = reminders.filter((reminder) => reminder.reminderId !== reminderId)
  saveReminderCache()
  void syncReminders()
  return completed as Reminder
}

function startReminderSync(): void {
  void syncReminders()
  reminderSyncTimer = setInterval(() => void syncReminders(), REMINDER_SYNC_INTERVAL_MS)
  reminderDueCheckTimer = setInterval(checkDueReminders, REMINDER_DUE_CHECK_INTERVAL_MS)
}

function resumeReminderSync(): void {
  void syncReminders()
}

async function createEmotion(content: string): Promise<Emotion> {
  const response = await fetch(EMOTIONS_API_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
    signal: AbortSignal.timeout(5_000),
  })
  if (!response.ok) throw new Error(`Emotion request returned HTTP ${response.status}.`)
  const candidate: unknown = await response.json()
  if (!isEmotion(candidate)) throw new Error('Emotion response is invalid.')
  return candidate
}

function loadCatProfile(): void {
  const profilePath = getCatProfilePath()
  if (!existsSync(profilePath)) return
  try {
    const candidate: unknown = JSON.parse(readFileSync(profilePath, 'utf8'))
    if (isCatProfile(candidate)) catProfile = candidate
  } catch (error) {
    console.error('Failed to read the cached cat profile.', error)
  }
}

function saveCatProfile(): void {
  const profilePath = getCatProfilePath()
  try {
    mkdirSync(dirname(profilePath), { recursive: true })
    writeFileSync(profilePath, JSON.stringify(catProfile, null, 2), 'utf8')
  } catch (error) {
    console.error('Failed to save the cached cat profile.', error)
  }
}

async function performCatProfileSync(): Promise<void> {
  try {
    const response = await fetch(CAT_PROFILE_API_URL, { signal: AbortSignal.timeout(4_000) })
    if (!response.ok) throw new Error(`Cat profile request returned HTTP ${response.status}.`)
    const candidate: unknown = await response.json()
    if (!isCatProfile(candidate)) throw new Error('Cat profile response is invalid.')

    const changed = candidate.version !== catProfile.version || candidate.catName !== catProfile.catName
    catProfile = candidate
    if (changed) {
      saveCatProfile()
      catWindow?.webContents.send('desktop-cat:profile-changed', catProfile)
    }
    if (profileSyncFailed) console.info('Cat profile synchronization recovered.')
    profileSyncFailed = false
  } catch (error) {
    if (!profileSyncFailed) console.warn('Cat profile synchronization is unavailable; using cached data.', error)
    profileSyncFailed = true
  }
}

async function syncCatProfile(): Promise<void> {
  if (profileSyncInFlight) return profileSyncInFlight
  const currentSync = performCatProfileSync()
  profileSyncInFlight = currentSync
  try {
    await currentSync
  } finally {
    if (profileSyncInFlight === currentSync) profileSyncInFlight = undefined
  }
}

function parseServerEvent(block: string): ServerEvent | null {
  let event = 'message'
  const dataLines: string[] = []

  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith(':')) continue
    const separator = line.indexOf(':')
    const field = separator < 0 ? line : line.slice(0, separator)
    let value = separator < 0 ? '' : line.slice(separator + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    if (field === 'data') dataLines.push(value)
  }

  return dataLines.length > 0 ? { event, data: dataLines.join('\n') } : null
}

function isCatProfileUpdatedEvent(value: unknown): value is CatProfileUpdatedEvent {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<CatProfileUpdatedEvent>
  return Number.isSafeInteger(candidate.profileId) && (candidate.profileId ?? 0) > 0
    && Number.isSafeInteger(candidate.version) && (candidate.version ?? -1) >= 0
}

function isReminderChangedEvent(value: unknown): value is ReminderChangedEvent {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Partial<ReminderChangedEvent>
  return Number.isSafeInteger(candidate.reminderId) && (candidate.reminderId ?? 0) > 0
    && Number.isSafeInteger(candidate.version) && (candidate.version ?? -1) >= 0
    && typeof candidate.action === 'string' && candidate.action.length > 0
}

async function handleServerEvent(serverEvent: ServerEvent): Promise<void> {
  try {
    const candidate: unknown = JSON.parse(serverEvent.data)
    if (serverEvent.event === 'cat-profile.updated') {
      if (!isCatProfileUpdatedEvent(candidate)) throw new Error('Profile update event is invalid.')
      if (candidate.profileId === catProfile.profileId && candidate.version > catProfile.version) {
        await syncCatProfile()
      }
      return
    }
    if (serverEvent.event === 'reminder.changed') {
      if (!isReminderChangedEvent(candidate)) throw new Error('Reminder update event is invalid.')
      await syncReminders()
    }
  } catch (error) {
    console.warn('Ignored an invalid assistant server event.', error)
  }
}

async function consumeServerEvents(response: Response): Promise<void> {
  if (!response.body) throw new Error('Assistant event stream has no response body.')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    let separator = buffer.match(/\r?\n\r?\n/)
    while (separator?.index !== undefined) {
      const block = buffer.slice(0, separator.index)
      buffer = buffer.slice(separator.index + separator[0].length)
      const serverEvent = parseServerEvent(block)
      if (serverEvent) await handleServerEvent(serverEvent)
      separator = buffer.match(/\r?\n\r?\n/)
    }
  }
}

function scheduleProfileEventReconnect(): void {
  if (isQuitting || profileEventReconnectTimer) return
  const retryIndex = Math.min(profileEventReconnectAttempt, PROFILE_EVENT_RETRY_DELAYS_MS.length - 1)
  const delay = PROFILE_EVENT_RETRY_DELAYS_MS[retryIndex]
  profileEventReconnectAttempt += 1
  profileEventReconnectTimer = setTimeout(() => {
    profileEventReconnectTimer = undefined
    void connectProfileEvents()
  }, delay)
}

async function connectProfileEvents(): Promise<void> {
  if (isQuitting || profileEventAbortController) return
  const abortController = new AbortController()
  profileEventAbortController = abortController

  try {
    const response = await fetch(ASSISTANT_EVENTS_API_URL, {
      headers: { Accept: 'text/event-stream' },
      signal: abortController.signal,
    })
    if (!response.ok) throw new Error(`Assistant event stream returned HTTP ${response.status}.`)
    if (profileEventConnectionFailed) console.info('Assistant event stream reconnected.')
    profileEventConnectionFailed = false
    profileEventReconnectAttempt = 0

    // Reconcile once after every connection so events missed while offline cannot leave stale state.
    await syncCatProfile()
    await syncReminders()
    await consumeServerEvents(response)
    if (!abortController.signal.aborted) throw new Error('Assistant event stream closed unexpectedly.')
  } catch (error) {
    if (!abortController.signal.aborted && !isQuitting) {
      if (!profileEventConnectionFailed) {
        console.warn('Assistant event stream is unavailable; retrying in the background.', error)
      }
      profileEventConnectionFailed = true
    }
  } finally {
    if (profileEventAbortController === abortController) profileEventAbortController = undefined
  }

  if (!abortController.signal.aborted) scheduleProfileEventReconnect()
}

function reconnectProfileEvents(): void {
  if (profileEventReconnectTimer) clearTimeout(profileEventReconnectTimer)
  profileEventReconnectTimer = undefined
  const previousConnection = profileEventAbortController
  profileEventAbortController = undefined
  previousConnection?.abort()
  void syncCatProfile()
  void connectProfileEvents()
}

function startCatProfileSync(): void {
  void syncCatProfile()
  void connectProfileEvents()
  profileReconcileTimer = setInterval(
    () => void syncCatProfile(),
    PROFILE_RECONCILE_INTERVAL_MS,
  )
}

function toLocalDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function parseLocalDate(value: unknown): number | null {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const [year, month, day] = value.split('-').map(Number)
  const timestamp = Date.UTC(year, month - 1, day)
  const parsed = new Date(timestamp)
  if (
    parsed.getUTCFullYear() !== year
    || parsed.getUTCMonth() !== month - 1
    || parsed.getUTCDate() !== day
  ) return null
  return timestamp
}

function getInitialCompanionDate(): string {
  const windowStatePath = getWindowStatePath()
  if (!existsSync(windowStatePath)) return toLocalDate(new Date())

  try {
    const createdAt = statSync(windowStatePath).birthtime
    if (!Number.isNaN(createdAt.getTime())) return toLocalDate(createdAt)
  } catch (error) {
    console.error('Failed to read the desktop cat first launch date.', error)
  }
  return toLocalDate(new Date())
}

function loadCompanionState(): CompanionState {
  if (companionState) return companionState
  const statePath = getCompanionStatePath()

  if (existsSync(statePath)) {
    try {
      const candidate = JSON.parse(readFileSync(statePath, 'utf8')) as Partial<CompanionState>
      if (parseLocalDate(candidate.firstMetDate) !== null) {
        companionState = { firstMetDate: candidate.firstMetDate as string }
        return companionState
      }
    } catch (error) {
      console.error('Failed to read the desktop cat companion state.', error)
    }
  }

  companionState = { firstMetDate: getInitialCompanionDate() }
  try {
    mkdirSync(dirname(statePath), { recursive: true })
    writeFileSync(statePath, JSON.stringify(companionState, null, 2), 'utf8')
  } catch (error) {
    console.error('Failed to save the desktop cat companion state.', error)
  }
  return companionState
}

function getCompanionInfo(): CompanionInfo {
  const state = loadCompanionState()
  const firstDay = parseLocalDate(state.firstMetDate) as number
  const now = new Date()
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  return {
    firstMetDate: state.firstMetDate,
    days: Math.max(1, Math.floor((today - firstDay) / 86_400_000) + 1),
  }
}

function readSavedPosition(): WindowPosition | null {
  const statePath = getWindowStatePath()
  if (!existsSync(statePath)) return null

  try {
    const candidate = JSON.parse(readFileSync(statePath, 'utf8')) as Partial<WindowPosition>
    if (Number.isInteger(candidate.x) && Number.isInteger(candidate.y)) {
      return { x: candidate.x as number, y: candidate.y as number }
    }
  } catch (error) {
    console.error('Failed to read the desktop cat window position.', error)
  }

  return null
}

function getCurrentWindowSize(): { width: number; height: number } {
  return isActivityPanelOpen
    ? { width: EXPANDED_WINDOW_WIDTH, height: EXPANDED_WINDOW_HEIGHT }
    : { width: COMPACT_WINDOW_WIDTH, height: COMPACT_WINDOW_HEIGHT }
}

function normalizeScreenCoordinate(value: number): number {
  const rounded = Math.round(value)
  return Object.is(rounded, -0) ? 0 : rounded
}

function constrainPositionToDisplay(position: WindowPosition): WindowPosition {
  const { width, height } = getCurrentWindowSize()
  const center = {
    x: normalizeScreenCoordinate(position.x + width / 2),
    y: normalizeScreenCoordinate(position.y + height / 2),
  }
  const { workArea } = screen.getDisplayNearestPoint(center)
  const maximumX = Math.max(workArea.x, workArea.x + workArea.width - width)
  const maximumY = Math.max(workArea.y, workArea.y + workArea.height - height)

  return {
    x: normalizeScreenCoordinate(Math.min(Math.max(position.x, workArea.x), maximumX)),
    y: normalizeScreenCoordinate(Math.min(Math.max(position.y, workArea.y), maximumY)),
  }
}

function setCatWindowPosition(position: WindowPosition): void {
  if (!catWindow || catWindow.isDestroyed()) return
  const normalizedPosition = {
    x: normalizeScreenCoordinate(position.x),
    y: normalizeScreenCoordinate(position.y),
  }
  catWindow.setPosition(normalizedPosition.x, normalizedPosition.y, false)
  precisePosition = normalizedPosition
}

function intersectsEnough(position: WindowPosition, workArea: Rectangle): boolean {
  const { width, height } = getCurrentWindowSize()
  const overlapWidth = Math.min(position.x + width, workArea.x + workArea.width)
    - Math.max(position.x, workArea.x)
  const overlapHeight = Math.min(position.y + height, workArea.y + workArea.height)
    - Math.max(position.y, workArea.y)
  return overlapWidth >= MIN_VISIBLE_SIZE && overlapHeight >= MIN_VISIBLE_SIZE
}

function isPositionVisible(position: WindowPosition): boolean {
  return screen.getAllDisplays().some((display) => intersectsEnough(position, display.workArea))
}

function getDefaultPosition(): WindowPosition {
  const { workArea } = screen.getPrimaryDisplay()
  const { width, height } = getCurrentWindowSize()
  return {
    x: workArea.x + workArea.width - width - 16,
    y: workArea.y + workArea.height - height - 16,
  }
}

function getInitialPosition(): WindowPosition {
  const savedPosition = readSavedPosition()
  return savedPosition && isPositionVisible(savedPosition)
    ? constrainPositionToDisplay(savedPosition)
    : getDefaultPosition()
}

function saveWindowPosition(): void {
  if (!catWindow || catWindow.isDestroyed()) return
  let [x, y] = catWindow.getPosition()
  if (isActivityPanelOpen) {
    if (activityPanelSide === 'left') x += EXPANDED_WINDOW_WIDTH - COMPACT_WINDOW_WIDTH
    y += EXPANDED_WINDOW_HEIGHT - COMPACT_WINDOW_HEIGHT
  }
  const statePath = getWindowStatePath()

  try {
    mkdirSync(dirname(statePath), { recursive: true })
    writeFileSync(statePath, JSON.stringify({ x, y }, null, 2), 'utf8')
  } catch (error) {
    console.error('Failed to save the desktop cat window position.', error)
  }
}

function schedulePositionSave(): void {
  if (savePositionTimer) clearTimeout(savePositionTimer)
  savePositionTimer = setTimeout(saveWindowPosition, 150)
}

function ensureWindowIsVisible(moveToPrimaryDisplay = false): void {
  if (!catWindow || catWindow.isDestroyed()) return
  const [x, y] = catWindow.getPosition()
  const currentPosition = { x, y }
  const safePosition = moveToPrimaryDisplay
    ? getDefaultPosition()
    : constrainPositionToDisplay(currentPosition)
  if (safePosition.x === x && safePosition.y === y) return

  setCatWindowPosition(safePosition)
  schedulePositionSave()
  console.info('Moved the desktop cat back into a visible work area.', {
    from: currentPosition,
    to: safePosition,
    display: moveToPrimaryDisplay ? 'primary' : 'nearest',
  })
}

function showCat(moveToPrimaryDisplay = false): void {
  if (!catWindow || catWindow.isDestroyed()) {
    createCatWindow()
    return
  }
  ensureWindowIsVisible(moveToPrimaryDisplay)
  catWindow.showInactive()
  catWindow.moveTop()
}

function createCatWindow(): void {
  const position = getInitialPosition()
  precisePosition = position

  catWindow = new BrowserWindow({
    width: COMPACT_WINDOW_WIDTH,
    height: COMPACT_WINDOW_HEIGHT,
    x: position.x,
    y: position.y,
    show: false,
    frame: false,
    transparent: true,
    resizable: false,
    maximizable: false,
    minimizable: false,
    fullscreenable: false,
    alwaysOnTop: true,
    skipTaskbar: true,
    hasShadow: false,
    backgroundColor: '#00000000',
    autoHideMenuBar: true,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  catWindow.setMenu(null)
  catWindow.once('ready-to-show', () => catWindow?.showInactive())
  catWindow.on('moved', () => {
    if (!movementTimer) schedulePositionSave()
  })
  catWindow.on('close', (event) => {
    if (!isQuitting) {
      event.preventDefault()
      catWindow?.hide()
    }
  })
  catWindow.on('closed', () => {
    catWindow = null
  })

  if (process.env.ELECTRON_RENDERER_URL) {
    void catWindow.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    void catWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }
}

function setActivityPanelOpen(open: boolean): ActivityPanelSide {
  if (!catWindow || catWindow.isDestroyed() || open === isActivityPanelOpen) {
    return activityPanelSide
  }

  const bounds = catWindow.getBounds()
  const { workArea } = screen.getDisplayMatching(bounds)
  const extraWidth = EXPANDED_WINDOW_WIDTH - COMPACT_WINDOW_WIDTH
  const extraHeight = EXPANDED_WINDOW_HEIGHT - COMPACT_WINDOW_HEIGHT

  if (open) {
    const roomOnRight = workArea.x + workArea.width - (bounds.x + COMPACT_WINDOW_WIDTH)
    const roomOnLeft = bounds.x - workArea.x
    activityPanelSide = roomOnRight >= extraWidth || roomOnRight >= roomOnLeft ? 'right' : 'left'

    const desiredX = activityPanelSide === 'left' ? bounds.x - extraWidth : bounds.x
    const desiredY = bounds.y - extraHeight
    const x = Math.min(Math.max(desiredX, workArea.x), workArea.x + workArea.width - EXPANDED_WINDOW_WIDTH)
    const y = Math.min(Math.max(desiredY, workArea.y), workArea.y + workArea.height - EXPANDED_WINDOW_HEIGHT)
    isActivityPanelOpen = true
    catWindow.setBounds({ x, y, width: EXPANDED_WINDOW_WIDTH, height: EXPANDED_WINDOW_HEIGHT })
  } else {
    const desiredX = activityPanelSide === 'left' ? bounds.x + extraWidth : bounds.x
    const desiredY = bounds.y + extraHeight
    const x = Math.min(Math.max(desiredX, workArea.x), workArea.x + workArea.width - COMPACT_WINDOW_WIDTH)
    const y = Math.min(Math.max(desiredY, workArea.y), workArea.y + workArea.height - COMPACT_WINDOW_HEIGHT)
    isActivityPanelOpen = false
    catWindow.setBounds({ x, y, width: COMPACT_WINDOW_WIDTH, height: COMPACT_WINDOW_HEIGHT })
  }

  precisePosition = { x: catWindow.getBounds().x, y: catWindow.getBounds().y }
  schedulePositionSave()
  return activityPanelSide
}

function randomInteger(minimum: number, maximum: number): number {
  return Math.floor(Math.random() * (maximum - minimum + 1)) + minimum
}

function chooseRandom<T>(items: readonly T[]): T {
  return items[Math.floor(Math.random() * items.length)] as T
}

function createActivitySnapshot(activityId: CatActivityId): CatActivitySnapshot {
  const definition = CAT_ACTIVITY_DEFINITIONS[activityId]
  const durationMinutes = randomInteger(definition.minMinutes, definition.maxMinutes)
  const startedAt = Date.now()
  return {
    id: activityId,
    startedAt,
    endsAt: startedAt + durationMinutes * 60_000,
    durationMinutes,
    facing: currentActivity.facing,
  }
}

function broadcastActivity(): void {
  if (!catWindow || catWindow.isDestroyed()) return
  catWindow.webContents.send('desktop-cat:activity-changed', currentActivity)
}

function pickAutomaticActivity(): CatActivityId {
  const choices = CAT_ACTIVITY_IDS.filter((activityId) => activityId !== currentActivity.id)
  return chooseRandom(choices)
}

function clearActivityEndTimer(): void {
  if (!activityEndTimer) return
  clearTimeout(activityEndTimer)
  activityEndTimer = undefined
}

function stopMovement(): void {
  if (movementTimer) clearInterval(movementTimer)
  movementTimer = undefined
  velocity = { x: 0, y: 0 }
  saveWindowPosition()
}

function setFacing(nextFacing: CatFacing): void {
  if (currentActivity.facing === nextFacing) return
  currentActivity = { ...currentActivity, facing: nextFacing }
  broadcastActivity()
}

function chooseVelocity(mode: CatMovementMode): void {
  const definition = CAT_ACTIVITY_DEFINITIONS[currentActivity.id]
  const direction = Math.random() * Math.PI * 2
  const speedPerTick = definition.speedPixelsPerSecond * MOVEMENT_TICK_MS / 1_000
  const verticalScale = mode === 'run' ? 0.55 : 0.75

  velocity = {
    x: Math.cos(direction) * speedPerTick,
    y: Math.sin(direction) * speedPerTick * verticalScale,
  }

  if (Math.abs(velocity.x) < speedPerTick * 0.35) {
    velocity.x = speedPerTick * 0.35 * (Math.random() < 0.5 ? -1 : 1)
  }

  nextDirectionChangeAt = Date.now() + randomInteger(
    mode === 'run' ? 3_000 : 6_000,
    mode === 'run' ? 8_000 : 14_000,
  )
  setFacing(velocity.x < 0 ? 'left' : 'right')
}

function recoverMovementPosition(mode: CatMovementMode, reason: unknown): void {
  if (!catWindow || catWindow.isDestroyed()) return
  const bounds = catWindow.getBounds()
  const fallback = getDefaultPosition()
  const currentPosition = Number.isFinite(bounds.x) && Number.isFinite(bounds.y)
    ? { x: bounds.x, y: bounds.y }
    : fallback
  const safePosition = constrainPositionToDisplay(currentPosition)

  console.error('Recovered from an invalid desktop cat movement position.', {
    reason,
    currentPosition,
    safePosition,
    velocity,
  })

  try {
    setCatWindowPosition(safePosition)
    schedulePositionSave()
    chooseVelocity(mode)
  } catch (recoveryError) {
    if (movementTimer) clearInterval(movementTimer)
    movementTimer = undefined
    velocity = { x: 0, y: 0 }
    try {
      setCatWindowPosition(fallback)
      schedulePositionSave()
    } catch (fallbackError) {
      console.error('Failed to restore the desktop cat to the primary display.', {
        recoveryError,
        fallbackError,
      })
    }
  }
}

function moveCatOneFrame(mode: CatMovementMode): void {
  if (movementPaused || !catWindow || catWindow.isDestroyed() || !catWindow.isVisible()) return
  if (Date.now() >= nextDirectionChangeAt) chooseVelocity(mode)

  const { workArea } = screen.getDisplayMatching(catWindow.getBounds())
  let nextX = precisePosition.x + velocity.x
  let nextY = precisePosition.y + velocity.y
  const minimumX = workArea.x
  const { width, height } = getCurrentWindowSize()
  const maximumX = workArea.x + workArea.width - width
  const minimumY = workArea.y
  const maximumY = workArea.y + workArea.height - height

  if (![nextX, nextY, minimumX, maximumX, minimumY, maximumY].every(Number.isFinite)) {
    recoverMovementPosition(mode, 'Non-finite movement coordinate')
    return
  }

  if (nextX <= minimumX || nextX >= maximumX) {
    nextX = Math.min(Math.max(nextX, minimumX), maximumX)
    velocity.x *= -1
    setFacing(velocity.x < 0 ? 'left' : 'right')
  }
  if (nextY <= minimumY || nextY >= maximumY) {
    nextY = Math.min(Math.max(nextY, minimumY), maximumY)
    velocity.y *= -1
  }

  const targetX = normalizeScreenCoordinate(nextX)
  const targetY = normalizeScreenCoordinate(nextY)
  precisePosition = { x: nextX, y: nextY }

  try {
    catWindow.setPosition(targetX, targetY, false)
  } catch (error) {
    recoverMovementPosition(mode, error)
  }
}

function startMovement(mode: CatMovementMode): void {
  stopMovement()
  if (mode === 'still') return

  if (catWindow && !catWindow.isDestroyed()) {
    const [x, y] = catWindow.getPosition()
    precisePosition = { x, y }
  }
  chooseVelocity(mode)
  movementTimer = setInterval(() => moveCatOneFrame(mode), MOVEMENT_TICK_MS)
}

function startActivity(activityId: CatActivityId): CatActivitySnapshot {
  clearActivityEndTimer()
  currentActivity = createActivitySnapshot(activityId)
  startMovement(CAT_ACTIVITY_DEFINITIONS[activityId].movement)
  broadcastActivity()

  activityEndTimer = setTimeout(() => {
    startActivity(pickAutomaticActivity())
  }, currentActivity.endsAt - Date.now())
  return currentActivity
}

function requestActivity(activityId: CatActivityId): CatActivityRequestResult {
  const definition = CAT_ACTIVITY_DEFINITIONS[activityId]
  if (Math.random() < definition.refusalChance) {
    return {
      accepted: false,
      message: chooseRandom(definition.refusalMessages),
      snapshot: currentActivity,
    }
  }

  const snapshot = startActivity(activityId)
  return {
    accepted: true,
    message: chooseRandom(definition.acceptedMessages),
    snapshot,
  }
}

function isSenderCatWindow(sender: Electron.WebContents): boolean {
  const senderWindow = BrowserWindow.fromWebContents(sender)
  return Boolean(senderWindow && senderWindow === catWindow)
}

function createTrayIcon(): Electron.NativeImage {
  const size = 32
  const pixels = Buffer.alloc(size * size * 4)
  const setPixel = (x: number, y: number, red: number, green: number, blue: number, alpha = 255): void => {
    if (x < 0 || y < 0 || x >= size || y >= size) return
    const offset = (y * size + x) * 4
    pixels[offset] = blue
    pixels[offset + 1] = green
    pixels[offset + 2] = red
    pixels[offset + 3] = alpha
  }

  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      const face = (x - 16) ** 2 + (y - 18) ** 2 <= 11 ** 2
      const leftEar = y >= 3 && y <= 13 && x >= 6 && x <= 15 - Math.floor((y - 3) / 2)
      const rightEar = y >= 3 && y <= 13 && x <= 25 && x >= 17 + Math.floor((y - 3) / 2)
      if (face || leftEar || rightEar) setPixel(x, y, 39, 33, 35)
    }
  }
  for (const [x, y] of [[12, 17], [20, 17]] as const) {
    setPixel(x, y, 243, 178, 57)
    setPixel(x, y + 1, 243, 178, 57)
  }
  setPixel(16, 21, 74, 61, 61)
  setPixel(15, 22, 118, 94, 86)
  setPixel(17, 22, 118, 94, 86)
  return nativeImage.createFromBitmap(pixels, { width: size, height: size })
}

function createTray(): void {
  tray = new Tray(createTrayIcon())
  tray.setToolTip('猫的角落')
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: '显示小猫（主屏幕）', click: () => showCat(true) },
    { label: '隐藏小猫', click: () => catWindow?.hide() },
    { type: 'separator' },
    {
      label: '退出',
      click: () => {
        isQuitting = true
        app.quit()
      },
    },
  ]))
  tray.on('click', () => showCat(true))
}

function registerIpcHandlers(): void {
  ipcMain.on('desktop-cat:set-ignore-mouse-events', (event, ignore: unknown) => {
    if (typeof ignore !== 'boolean' || !isSenderCatWindow(event.sender)) return
    catWindow?.setIgnoreMouseEvents(ignore, { forward: ignore })
  })

  ipcMain.on('desktop-cat:move-window-by', (event, deltaX: unknown, deltaY: unknown) => {
    if (
      typeof deltaX !== 'number' || typeof deltaY !== 'number'
      || !Number.isFinite(deltaX) || !Number.isFinite(deltaY)
      || Math.abs(deltaX) > EXPANDED_WINDOW_WIDTH || Math.abs(deltaY) > EXPANDED_WINDOW_HEIGHT
      || !isSenderCatWindow(event.sender) || !catWindow
    ) return

    const [currentX, currentY] = catWindow.getPosition()
    const nextPosition = constrainPositionToDisplay({
      x: currentX + Math.round(deltaX),
      y: currentY + Math.round(deltaY),
    })
    setCatWindowPosition(nextPosition)
    schedulePositionSave()
  })

  ipcMain.on('desktop-cat:set-movement-paused', (event, paused: unknown) => {
    if (typeof paused !== 'boolean' || !isSenderCatWindow(event.sender)) return
    movementPaused = paused
    if (!paused && catWindow && !catWindow.isDestroyed()) {
      const [x, y] = catWindow.getPosition()
      precisePosition = { x, y }
    }
  })

  ipcMain.handle('desktop-cat:set-activity-panel-open', (event, open: unknown) => {
    if (!isSenderCatWindow(event.sender)) throw new Error('Activity panel access denied.')
    if (typeof open !== 'boolean') throw new Error('Invalid activity panel state.')
    return setActivityPanelOpen(open)
  })

  ipcMain.handle('desktop-cat:get-activity', (event) => {
    if (!isSenderCatWindow(event.sender)) throw new Error('Activity access denied.')
    return currentActivity
  })

  ipcMain.handle('desktop-cat:get-companion-info', (event) => {
    if (!isSenderCatWindow(event.sender)) throw new Error('Companion info access denied.')
    return getCompanionInfo()
  })

  ipcMain.handle('desktop-cat:get-profile', (event) => {
    if (!isSenderCatWindow(event.sender)) throw new Error('Cat profile access denied.')
    return catProfile
  })

  ipcMain.handle('desktop-cat:create-emotion', (event, content: unknown) => {
    if (!isSenderCatWindow(event.sender)) throw new Error('Emotion access denied.')
    if (typeof content !== 'string' || content.trim().length === 0) {
      throw new Error('Emotion content is required.')
    }
    return createEmotion(content.trim())
  })

  ipcMain.handle('desktop-cat:complete-reminder', (event, reminderId: unknown, version: unknown) => {
    if (!isSenderCatWindow(event.sender)) throw new Error('Reminder access denied.')
    if (!Number.isSafeInteger(reminderId) || (reminderId as number) <= 0) {
      throw new Error('Reminder id is invalid.')
    }
    if (!Number.isSafeInteger(version) || (version as number) < 0) {
      throw new Error('Reminder version is invalid.')
    }
    return completeReminder(reminderId as number, version as number)
  })

  ipcMain.handle('desktop-cat:request-activity', (event, activityId: unknown) => {
    if (!isSenderCatWindow(event.sender)) throw new Error('Activity access denied.')
    if (!isCatActivityId(activityId)) throw new Error('Unknown cat activity.')
    return requestActivity(activityId)
  })
}

const hasSingleInstanceLock = app.requestSingleInstanceLock()

if (!hasSingleInstanceLock) {
  app.quit()
} else {
  app.on('second-instance', () => showCat(true))
  app.whenReady().then(() => {
    app.setAppUserModelId('com.desktopcat.corner')
    registerIpcHandlers()
    loadCompanionState()
    loadCatProfile()
    loadReminderCache()
    createCatWindow()
    createTray()
    startActivity('idle')
    startCatProfileSync()
    startReminderSync()
    powerMonitor.on('resume', reconnectProfileEvents)
    powerMonitor.on('resume', resumeReminderSync)
    screen.on('display-removed', () => ensureWindowIsVisible())
    screen.on('display-metrics-changed', () => ensureWindowIsVisible())
  })
}

app.on('before-quit', () => {
  isQuitting = true
  if (savePositionTimer) clearTimeout(savePositionTimer)
  if (profileReconcileTimer) clearInterval(profileReconcileTimer)
  if (profileEventReconnectTimer) clearTimeout(profileEventReconnectTimer)
  if (reminderSyncTimer) clearInterval(reminderSyncTimer)
  if (reminderDueCheckTimer) clearInterval(reminderDueCheckTimer)
  profileEventAbortController?.abort()
  powerMonitor.removeListener('resume', reconnectProfileEvents)
  powerMonitor.removeListener('resume', resumeReminderSync)
  clearActivityEndTimer()
  stopMovement()
  saveWindowPosition()
})

app.on('window-all-closed', () => {
  // Keep the process alive so the cat can be restored from the tray.
})
