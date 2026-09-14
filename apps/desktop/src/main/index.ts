//主进程负责访问 Windows 系统能力。、
/**
 * - existsSync：检查位置文件是否存在
 * - mkdirSync：创建用户数据目录
 * - readFileSync：读取上次窗口位置
 * - writeFileSync：保存窗口位置
 * - dirname：取得文件所在目录
 * - join：安全拼接路径
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
/**
 * app	Electron 程序生命周期
 * BrowserWindow	创建桌面窗口
 * ipcMain	接收 Vue 发来的 IPC 消息
 * Menu	创建托盘菜单
 * nativeImage	创建 Windows 能使用的图标
 * screen	获取显示器尺寸
 * Tray	创建系统托盘图标
 * Rectangle	显示器矩形区域的 TypeScript 类型
 */
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

//窗口常量小猫的大小
const WINDOW_WIDTH = 280
const WINDOW_HEIGHT = 320
const MIN_VISIBLE_SIZE = 48

//小猫窗口的位置
type WindowPosition = {
  x: number
  y: number
}

//保存小猫对象
let catWindow: BrowserWindow | null = null
//保存托盘对象
let tray: Tray | null = null
//区分两种行为 1.用户关闭窗口 2.用户真要退出程序
let isQuitting = false
//保存位置时的防抖定时器
let savePositionTimer: NodeJS.Timeout | undefined

//获取文件地址
function getWindowStatePath(): string {
  //app.getPath('userData') 返回当前应用的用户数据目录。
  return join(app.getPath('userData'), 'window-state.json')
}

//文件保存位置
function readSavedPosition(): WindowPosition | null {
  const statePath = getWindowStatePath()
  if (!existsSync(statePath)) {
    return null
  }

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

//检查窗口和屏幕是否相交
//workArea 是显示器可用区域，通常已经排除了任务栏。
function intersectsEnough(position: WindowPosition, workArea: Rectangle): boolean {
  const overlapWidth = Math.min(position.x + WINDOW_WIDTH, workArea.x + workArea.width)
    - Math.max(position.x, workArea.x)
  const overlapHeight = Math.min(position.y + WINDOW_HEIGHT, workArea.y + workArea.height)
    - Math.max(position.y, workArea.y)

  return overlapWidth >= MIN_VISIBLE_SIZE && overlapHeight >= MIN_VISIBLE_SIZE
}

//检查所有显示器
function isPositionVisible(position: WindowPosition): boolean {
  //some() 表示只要小猫在任意一个显示器里，就认为位置有效。
  return screen.getAllDisplays().some((display) => intersectsEnough(position, display.workArea))
}

//默认位置
function getDefaultPosition(): WindowPosition {
  const { workArea } = screen.getPrimaryDisplay()
  return {
    x: workArea.x + workArea.width - WINDOW_WIDTH - 24,
    y: workArea.y + workArea.height - WINDOW_HEIGHT - 24,
  }
}

//决定启动位置
function getInitialPosition(): WindowPosition {
  const savedPosition = readSavedPosition()
  return savedPosition && isPositionVisible(savedPosition) ? savedPosition : getDefaultPosition()
}

//保存位置
function saveWindowPosition(): void {
  if (!catWindow || catWindow.isDestroyed()) {
    return
  }
//保存前确认窗口存在并且没有被销毁
  const [x, y] = catWindow.getPosition()
  const statePath = getWindowStatePath()

  try {
    mkdirSync(dirname(statePath), { recursive: true })
    writeFileSync(statePath, JSON.stringify({ x, y }, null, 2), 'utf8')
  } catch (error) {
    console.error('Failed to save the desktop cat window position.', error)
  }
}

//保存位置防抖
//拖动窗口时会产生很多 moved 事件。
// 如果每次都写文件，会在一秒内写很多次。因此每次移动时取消旧任务，等用户停止移动 150 毫秒后再写一次。
function schedulePositionSave(): void {
  if (savePositionTimer) {
    clearTimeout(savePositionTimer)
  }
  savePositionTimer = setTimeout(saveWindowPosition, 150)
}


// 确保小猫没有跑出屏幕
function ensureWindowIsVisible(): void {
  if (!catWindow || catWindow.isDestroyed()) {
    return
  }

  const [x, y] = catWindow.getPosition()
  if (!isPositionVisible({ x, y })) {
    const defaultPosition = getDefaultPosition()
    catWindow.setPosition(defaultPosition.x, defaultPosition.y)
  }
}

//显示小猫
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
//创建窗口
  /**
   * show: false	页面加载好之前暂不显示，避免白屏闪烁
   * frame: false	删除 Windows 标题栏
   * transparent: true	支持透明背景
   * resizable: false	禁止改变尺寸
   * maximizable: false	禁止最大化
   * minimizable: false	禁止最小化
   * fullscreenable: false	禁止全屏
   * alwaysOnTop: true	保持置顶
   * skipTaskbar: true	不显示在任务栏
   * hasShadow: false	不绘制矩形窗口阴影
   * backgroundColor	完全透明 ARGB 颜色
   * autoHideMenuBar	隐藏默认菜单栏
   */
  catWindow = new BrowserWindow({
    width: WINDOW_WIDTH,
    height: WINDOW_HEIGHT,
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

  //页面准备好再显示
  catWindow.once('ready-to-show', () => {
    catWindow?.showInactive()
  })

  //监听移动 窗口每次移动后，触发防抖保存。
  catWindow.on('moved', schedulePositionSave)

  //关闭时隐藏
  catWindow.on('close', (event) => {
    if (!isQuitting) {
      event.preventDefault()
      catWindow?.hide()
    }
  })
  //窗口销毁后清理引用
  catWindow.on('closed', () => {
    catWindow = null
  })

  if (process.env.ELECTRON_RENDERER_URL) {
    void catWindow.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    void catWindow.loadFile(join(__dirname, '../renderer/index.html'))
  }
}

//生成托盘图标
function createTrayIcon(): Electron.NativeImage {
  const size = 32
  const pixels = Buffer.alloc(size * size * 4)

  const setPixel = (x: number, y: number, red: number, green: number, blue: number, alpha = 255): void => {
    if (x < 0 || y < 0 || x >= size || y >= size) {
      return
    }
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

      if (face || leftEar || rightEar) {
        setPixel(x, y, 246, 169, 120)
      }
    }
  }

  for (const [x, y] of [[12, 17], [20, 17]] as const) {
    setPixel(x, y, 66, 55, 52)
    setPixel(x, y + 1, 66, 55, 52)
  }
  setPixel(16, 21, 234, 111, 118)
  setPixel(15, 22, 66, 55, 52)
  setPixel(17, 22, 66, 55, 52)

  return nativeImage.createFromBitmap(pixels, { width: size, height: size })
}

//创建托盘菜单
function createTray(): void {
  tray = new Tray(createTrayIcon())
  tray.setToolTip('猫的角落')

  const menu = Menu.buildFromTemplate([
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
  ])

  tray.setContextMenu(menu)
  tray.on('click', showCat)
}

//注册 IPC
function registerIpcHandlers(): void {
  ipcMain.on('desktop-cat:set-ignore-mouse-events', (event, ignore: unknown) => {
    if (typeof ignore !== 'boolean') {
      return
    }

    const senderWindow = BrowserWindow.fromWebContents(event.sender)
    if (!senderWindow || senderWindow !== catWindow) {
      return
    }

    senderWindow.setIgnoreMouseEvents(ignore, { forward: ignore })
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
    createCatWindow()
    createTray()

    screen.on('display-removed', ensureWindowIsVisible)
    screen.on('display-metrics-changed', ensureWindowIsVisible)
  })
}

app.on('before-quit', () => {
  isQuitting = true
  if (savePositionTimer) {
    clearTimeout(savePositionTimer)
  }
  saveWindowPosition()
})

app.on('window-all-closed', () => {
  // Keep the process alive so the cat can be restored from the tray.
})
