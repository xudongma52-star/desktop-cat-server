import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import {
  app,
  BrowserWindow,
  ipcMain,
  Menu,
  nativeImage,
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

const COMPACT_WINDOW_WIDTH = 176
const COMPACT_WINDOW_HEIGHT = 176
const EXPANDED_WINDOW_WIDTH = 400
const EXPANDED_WINDOW_HEIGHT = 230
const MIN_VISIBLE_SIZE = 36
const MOVEMENT_TICK_MS = 50

type WindowPosition = { x: number; y: number }
type Velocity = { x: number; y: number }
type ActivityPanelSide = 'left' | 'right'
type CompanionState = { firstMetDate: string }

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
  return {
    x: workArea.x + workArea.width - COMPACT_WINDOW_WIDTH - 16,
    y: workArea.y + workArea.height - COMPACT_WINDOW_HEIGHT - 16,
  }
}

function getInitialPosition(): WindowPosition {
  const savedPosition = readSavedPosition()
  return savedPosition && isPositionVisible(savedPosition) ? savedPosition : getDefaultPosition()
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

function ensureWindowIsVisible(): void {
  if (!catWindow || catWindow.isDestroyed()) return
  const [x, y] = catWindow.getPosition()
  if (!isPositionVisible({ x, y })) {
    const fallback = getDefaultPosition()
    catWindow.setPosition(fallback.x, fallback.y)
    precisePosition = fallback
  }
}

function showCat(): void {
  if (!catWindow || catWindow.isDestroyed()) {
    createCatWindow()
    return
  }
  ensureWindowIsVisible()
  catWindow.showInactive()
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
  precisePosition = Number.isFinite(bounds.x) && Number.isFinite(bounds.y)
    ? { x: bounds.x, y: bounds.y }
    : fallback
  console.error('Recovered from an invalid desktop cat movement position.', {
    reason,
    precisePosition,
    velocity,
  })
  chooseVelocity(mode)
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

  const targetX = Math.round(nextX)
  const targetY = Math.round(nextY)
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
    { label: '显示小猫', click: showCat },
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
  tray.on('click', showCat)
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
    precisePosition = {
      x: currentX + Math.round(deltaX),
      y: currentY + Math.round(deltaY),
    }
    catWindow.setPosition(precisePosition.x, precisePosition.y)
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
  app.on('second-instance', showCat)
  app.whenReady().then(() => {
    app.setAppUserModelId('com.desktopcat.corner')
    registerIpcHandlers()
    loadCompanionState()
    createCatWindow()
    createTray()
    startActivity('idle')
    screen.on('display-removed', ensureWindowIsVisible)
    screen.on('display-metrics-changed', ensureWindowIsVisible)
  })
}

app.on('before-quit', () => {
  isQuitting = true
  if (savePositionTimer) clearTimeout(savePositionTimer)
  clearActivityEndTimer()
  stopMovement()
  saveWindowPosition()
})

app.on('window-all-closed', () => {
  // Keep the process alive so the cat can be restored from the tray.
})
