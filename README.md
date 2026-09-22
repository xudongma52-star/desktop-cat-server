# 猫的角落 · 个人知识库与桌面猫

[![CI](https://github.com/xudongma52-star/desktop-cat-server/actions/workflows/ci.yml/badge.svg)](https://github.com/xudongma52-star/desktop-cat-server/actions/workflows/ci.yml)

Vue 3 + Electron + Spring Boot 3 + JDK 21 + Python 3.12，使用原生 MyBatis。当前已实现带登录注册的个人文章网站、年度写作足迹、文章与照片回忆轮播、每日情绪站、待办提醒、第一版个人知识检索，以及一个可独立运行的 Windows 桌面猫。

## 当前范围

- 已建立 Vue、TypeScript、Vite、Router 和 Pinia 前端，并完成首页、文章列表、创建、详情、编辑和删除页面。
- 已建立 Spring Boot 3.5.16 后端，加入 Validation、Actuator、MyBatis Starter 3.0.5、Flyway、PostgreSQL、Spring Session 和 Redis。
- 默认连接本机 PostgreSQL 与 Redis；应用启动时由 Flyway 依次执行数据库迁移。需要脱离数据库开发时可显式启用 `local` 配置。
- 已完成统一身份登录闭环。Web 使用 Redis 持久化 `JSESSIONID` 对应的 12 小时会话，并使用 30 天签名 Remember-Me Cookie；桌面猫通过系统浏览器和 PKCE 获得设备凭证，再静默换取 15 分钟 Access Token。一次性授权码和 Access Token 只以摘要作为 Redis 键并按有效期自动清理；密码使用 BCrypt 哈希，长期设备凭证只保存 SHA-256 摘要。除认证入口、运行状态和健康检查外，`/api/**` 均要求登录。
- 网页提供 `/login` 和 `/register` 页面，Pinia 保存当前用户状态，路由守卫会把未登录访问重定向到登录页，并在登录后返回原目标页面。
- 已建立 Electron 桌面端：透明无边框窗口、置顶显示、透明区域鼠标穿透、拖动、真实猫叫、活动状态、自由移动、主动陪伴对话、托盘显隐与退出、窗口位置记忆和 Windows 安装包。
- 当前 Q 版黑猫根据自己的猫照片生成，保留纯黑毛、圆脸、厚爪与琥珀眼；多组透明 PNG 精灵图配合 CSS 表现发呆、睡觉、舔毛、玩耍、吃冻干、散步和奔跑七种活动。
- 已完成 `cat_profile` 资料表，可在网页修改小猫名字，并由 Java 通过 SSE 通知 Electron 实时同步；桌面端保留离线缓存。
- 已完成 `personal_record` 文章表和完整 CRUD：可管理日记、心得与实习笔记，按类型筛选和分页，并分别设置是否加入温馨回忆、是否允许进入知识检索。
- 文章和每日情绪均按当前登录用户隔离；历史数据迁移时会归入既有 `MaxCat` 账号，避免升级后成为无主数据。
- 首页写作足迹按日期统计最近 365 天的日记篇数，显示 53 周热力图、累计篇数和写作天数；统计直接聚合现有 `personal_record` 数据，不建立冗余统计表。
- 已完成 `daily_emotion` 每日情绪表：可从桌面小猫的单一文本框快速记录一句话，并在网站按日期追溯当天和过去的内容。创建日期始终由 Java 按上海时区当天生成，不能补写过去或预写明天；情绪碎片与文章分开保存，不要求选择情绪类型，也不触发即时 AI 回复。
- 已完成 `reminder` 待办提醒表和网站管理页：可创建、修改、完成和删除一次性提醒，并查看今天或全部未完成事项。提醒变更通过 SSE 实时同步到网页和桌面猫，每 5 分钟低频校准一次；桌面猫每 5 秒在本地检查到期时间，断网时继续使用缓存，到点会出现并喵一声，可直接标记完成。
- 已完成无 API Key 的第一版知识检索：Java 只读取当前用户最近 100 篇记录中主动开启 `rag_enabled` 的正文，Python FastAPI 使用中文字符级 TF-IDF、文本切片和余弦相似度返回最多 5 个相关片段，Vue `/knowledge` 页面展示相关度并可跳回原文。当前版本只返回检索结果，不生成大模型回答，也不保存向量索引。
- 首页文章温馨回忆轮播会读取主动开启 `recall_enabled` 的记录，支持上一条、下一条、暂停和自动轮播；目前只包含文字文章。
- 首页照片回忆轮播按账号管理照片，支持 JPG、PNG、WebP 原图选择、4:3 拖动缩放裁剪、上传、删除、手动切换及每 3 秒自动轮播。浏览器统一导出 1200 × 900 WebP，服务端再次校验格式、尺寸和 2 MB 上限；数据库只保存元数据和相对存储键，图片文件写入可配置目录。
- 大模型整理回答、持久化向量索引、重复提醒、拖拽投喂冻干和开机自启尚未实现；相关接入位置作为后续扩展边界。
- 开发服务与 Redis 端口都只绑定本机地址，当前框架用于本地学习；正式发布前仍需补齐 HTTPS 与部署配置。

## 环境

JDK 21、Node.js 22.12+（建议 24 LTS）、pnpm 11.19.0、Python 3.12。后端使用 Maven Wrapper。

如果你的终端找不到 `pnpm`，可把下方命令中的 `pnpm` 替换为 `npx.cmd --yes pnpm@11.19.0`，例如 `npx.cmd --yes pnpm@11.19.0 dev`。

## 启动

首次在根目录安装前端依赖：

```powershell
cd D:\repository
pnpm install
```

确认本机 PostgreSQL 已启动，默认连接参数为 `127.0.0.1:5432/postgres`、用户 `postgres`、密码 `postgres`。同时确认 Docker Redis 容器已启动；当前开发容器名为 `desktop-cat-redis`，只映射到 `127.0.0.1:6380`，未配置账号密码：

```powershell
docker start desktop-cat-redis
docker exec desktop-cat-redis redis-cli ping
```

看到 `PONG` 后，打开第一个终端启动后端：

```powershell
cd D:\repository\services\server
.\mvnw.cmd spring-boot:run
```

打开第二个终端，启动前端：

```powershell
cd D:\repository
pnpm dev
```

第一次使用知识检索时，先创建 Python 虚拟环境并安装依赖：

```powershell
cd D:\repository\services\rag-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

随后打开第三个终端启动知识检索服务：

```powershell
cd D:\repository
pnpm dev:rag
```

打开 [http://127.0.0.1:5173](http://127.0.0.1:5173)，首次使用时在注册页创建用户名和密码；注册成功后会自动登录并进入首页。已有账号可直接登录。启动任一服务的终端按 `Ctrl+C` 可停止该服务。

无需登录即可访问：[认证状态](http://127.0.0.1:8080/api/auth/status)、[运行状态](http://127.0.0.1:8080/api/system/status)和[健康检查](http://127.0.0.1:8080/actuator/health)。小猫资料、文章、情绪、提醒和 SSE 等业务接口需要先建立登录会话。请求字段、响应示例和错误码见 [接口文档](./接口文档.md)。

单独开发桌面猫：

```powershell
cd D:\repository
pnpm dev:desktop
```

桌面猫首次启动时通过系统默认浏览器连接当前 Web 账号；浏览器已有登录状态时无需再次输入密码。设备凭证由 Electron `safeStorage` 加密保存，后续启动会静默换取临时 Access Token；托盘菜单可以重新登录或撤销本机授权。随后桌面猫读取资料并保持一条带身份的 SSE 连接；收到资料或提醒变更事件时才重新读取对应数据。断线后按 1、2、5、10、30 秒逐级重连，并每 5 分钟低频校准一次，避免漏掉离线期间的更新。后端不可用时读取本地缓存并继续运行。桌面猫使用 `reminder_id:version` 标记已经展示的提醒版本；编辑提醒会生成新版本并重新进入到期检查，断网时仍可执行已缓存的提醒。网页订阅同一事件流，桌面猫完成提醒后会自动刷新列表。远程部署后通过 `DESKTOP_CAT_API_URL` 指定服务地址，并通过 `DESKTOP_CAT_WEB_URL` 指定统一登录网页地址。单击小猫会播放真实猫叫并打开活动面板，可以请求发呆、睡觉、舔毛、玩耍、吃冻干、散步或奔跑；小猫可能按活动设定接受或拒绝，活动结束后会自主选择下一项。玩耍、散步和奔跑时，窗口会在当前显示器可用区域内自由移动、随机转向并在屏幕边缘折返；打开选择面板或拖动小猫时会暂停自动移动。活动面板右上角的记录按钮会打开一个文本框，按 Enter 发送、Shift+Enter 换行；发送失败时草稿保留在本地。

小猫会结合早晨、中午、下午、傍晚和夜间主动说不同的陪伴话语，通常每 16–32 秒更新一次，并尽量避免最近出现过的内容。摸摸小猫时会随机回应并暂时保留这句话，随后继续日常对话。活动面板只展示当前状态和可选活动，不显示倒计时及活动时长。

关闭窗口会隐藏到系统托盘，从托盘菜单可以重新显示或彻底退出。透明区域会穿透鼠标，不遮挡下面的桌面内容。

生成 Windows 安装程序：

```powershell
cd D:\repository
pnpm dist:desktop
```

安装程序位于 `apps/desktop/release/猫的角落-0.3.0-x64-setup.exe`。免安装运行文件位于 `apps/desktop/release/win-unpacked/猫的角落.exe`。安装包暂未购买代码签名证书，Windows 可能显示未知发布者提示。

## 请求是怎么走的

登录注册、Redis Session、Remember-Me 和 Electron PKCE 的逐段源码说明见 [登录注册与认证源码解析](./登录注册与认证源码解析.md)。

登录注册链路如下：

```text
LoginView.vue / RegisterView.vue
  → Pinia auth store
  → src/api/auth.ts
  → AuthController
  → Spring Security 会话与 CSRF 校验
  → Spring Session Redis 保存会话
  → AuthApplicationService + AppUserDao
  → PostgreSQL app_user
```

网页启动时先调用 `GET /api/auth/status` 恢复登录状态。注册或登录成功会在 Redis 建立 12 小时空闲会话，并签发 30 天 HttpOnly Remember-Me Cookie；浏览器关闭、后端重启或内存释放后仍可恢复登录。后续请求携带 `JSESSIONID`，非安全方法同时从 `XSRF-TOKEN` Cookie 读取令牌并写入 `X-XSRF-TOKEN` 请求头。密码只以 BCrypt 哈希写入数据库，不保存或返回明文。

桌面猫生成 PKCE verifier，在 `127.0.0.1` 随机端口监听一次性回调，然后打开 `/desktop/connect`。Web 复用当前登录状态签发两分钟授权码；桌面猫校验回调 `state` 并用 verifier 换取设备凭证和 15 分钟 Access Token。一次性授权码和临时 Token 存入 Redis 并设置对应 TTL，因此后端重启不会使仍在有效期内的状态丢失。设备凭证有效期 90 天，明文只进入系统安全存储，数据库 `auth_device` 只保存摘要；临时 Token 失效后，桌面猫会使用设备凭证自动恢复。

```text
RecordEditorView.vue
  → src/api/records.ts 的 POST 或 PUT 请求
  → Vite 开发代理转到 127.0.0.1:8080
  → PersonalRecordController
  → PersonalRecordService 接口
  → PersonalRecordServiceImpl 校验并编排业务
  → PersonalRecordDao + PersonalRecordDao.xml 访问 PostgreSQL
  → Vue 展示保存结果或结构化错误
```

首页的 `WritingActivityHeatmap.vue` 通过 `GET /api/records/activity` 读取按日期聚合的日记篇数，`ArticleRecallCarousel.vue` 通过 `GET /api/records/recalls` 读取允许回忆展示的文章。Vite 代理只用于开发；构建生成的静态页面需要由正式反向代理把 `/api` 路径转发给后端。

照片回忆由 `PhotoCarousel.vue` 在浏览器内完成选择、4:3 裁剪和 WebP 编码，再通过 `POST /api/carousel/photos` 上传。`PhotoServiceImpl` 负责当前用户校验与数据库事务，`PhotoStorageService` 负责文件校验和落盘；列表、内容读取和逻辑删除均同时限定当前用户，不能访问其他账号的照片。

每日情绪的写入链路为 Electron 渲染进程 → preload 受限 API → Electron 主进程 → `POST /api/emotions` → Java 分层 → PostgreSQL。主进程负责网络请求，渲染进程不直接访问数据库。网站的 `/emotions` 页面通过 `GET /api/emotions` 读取当天内容。

提醒的管理链路为 `ReminderView.vue` → `/api/reminders` → `ReminderController` → `ReminderService` 接口 → `ReminderServiceImpl` → `ReminderDao` + `ReminderDao.xml` → PostgreSQL。桌面端主进程读取 `scope=PENDING`，把未完成提醒缓存到用户目录并把到期事项发送给渲染进程；用户点击“我做完了”后由主进程调用完成接口。

第一版知识检索链路为 `KnowledgeView.vue` → `POST /api/knowledge/retrieve` → Java 校验当前用户并复用文章分页和详情服务 → Python FastAPI 切片与计算相关度 → Java 补回可信的文章类型、标题和日期 → Vue 展示原文来源。Python 不连接业务数据库，也不接收登录凭据。

主要接口如下：

| 方法与路径 | 用途 |
| --- | --- |
| `GET /api/auth/status` | 查询当前登录状态，匿名用户也可访问 |
| `POST /api/auth/register` | 注册账号并自动登录；用户名 3–32 个字符，密码至少 6 个字符且不超过 72 个 UTF-8 字节 |
| `POST /api/auth/login` | 使用用户名和密码登录 |
| `POST /api/auth/logout` | 退出登录并清理会话 Cookie |
| `POST /api/auth/desktop/authorize` | 已登录网页为桌面猫签发一次性 PKCE 授权码 |
| `POST /api/auth/desktop/exchange` | 桌面猫用授权码和 verifier 换取设备凭证及临时 Token |
| `POST /api/auth/desktop/refresh` | 使用设备凭证静默换取新的临时 Token |
| `POST /api/auth/desktop/revoke` | 撤销本机设备凭证 |
| `POST /api/records` | 创建文章 |
| `GET /api/records?page=1&pageSize=12&recordType=` | 分页查询文章，可按类型筛选 |
| `GET /api/records/activity?startDate=&endDate=&recordType=DIARY` | 按日期统计每天的日记篇数 |
| `GET /api/records/{recordId}` | 查看文章详情 |
| `PUT /api/records/{recordId}` | 按版本更新文章 |
| `DELETE /api/records/{recordId}?version=` | 按版本逻辑删除文章 |
| `GET /api/records/recalls?limit=10` | 查询允许展示的文章回忆 |
| `POST /api/knowledge/retrieve` | 从当前用户允许检索的记录中返回相关原文片段 |
| `POST /api/carousel/photos` | 上传一张 1200 × 900、最大 2 MB 的 WebP 轮播照片 |
| `GET /api/carousel/photos` | 查询当前用户的照片列表 |
| `GET /api/carousel/photos/{photoId}/content` | 读取当前用户的照片内容 |
| `DELETE /api/carousel/photos/{photoId}` | 将当前用户的照片移出轮播 |
| `POST /api/emotions` | 保存一条当天情绪 |
| `GET /api/emotions?date=2026-09-16` | 按日期查询情绪；不传日期时查询今天 |
| `POST /api/reminders` | 创建一次性提醒 |
| `GET /api/reminders?scope=TODAY` | 查询今天的提醒；`PENDING` 查询全部未完成提醒 |
| `PUT /api/reminders/{reminderId}` | 按版本修改未完成提醒 |
| `PATCH /api/reminders/{reminderId}/complete` | 按版本完成提醒 |
| `DELETE /api/reminders/{reminderId}?version=` | 按版本逻辑删除提醒 |

## 项目目录

```text
apps/web/                    Vue 前端
  src/api/                   认证与业务 HTTP 请求、CSRF 请求头处理
  src/components/            登录场景、文章与照片温馨回忆轮播等组件
  src/router/                页面路由
  src/stores/                Pinia 登录状态
  src/views/                 登录、注册、桌面授权、首页、文章、当天内心及提醒管理页面
apps/desktop/                Electron 桌面猫
  src/main/                  窗口、托盘、位置保存和 IPC
  src/preload/               受限的渲染进程桥接 API
  src/shared/                主进程和 Vue 共用的活动、情绪、提醒类型与规则
  src/renderer/              Vue 小猫界面与交互
    src/assets/cat/          根据自己的猫生成的透明 Q 版状态图
    src/assets/audio/        本地猫叫资源
  build/                     应用图标
services/server/             Java 后端
  src/main/java/             启动类及 identity、cat、record、emotion、photo、reminder 等业务模块
    .../identity/            账号、Web 会话、桌面设备授权、CSRF 与安全配置
    .../photo/               照片元数据、文件存储及轮播接口
    .../record/controller/   个人文章 HTTP 接口
    .../record/dto/          Controller 与 Service 共用的传输对象
    .../record/service/      Service 接口与 impl 实现
    .../record/dao/          MyBatis DAO 接口
      dataobject/            personal_record 数据库映射对象
  src/main/resources/        配置、MyBatis XML 与 Flyway 迁移
  src/test/java/             后端集成测试
services/rag-service/        Python FastAPI 知识检索服务
  rag_service/               HTTP 接口、文本切片和 TF-IDF 检索
  tests/                     Python 检索核心测试
```

小猫资料、个人文章、每日情绪和待办提醒后端都按 `controller`、`service` 接口、`service.impl`、`dao` 分层。请求和返回对象放在各模块的 `dto` 包；数据库对象使用 `DO` 后缀，与对应的 `Dao` 接口放在同一 `dao` 包，仅由 `service.impl` 和 `dao` 使用。具体 SQL 由 `mappers` 下与 Dao 同名的 XML 实现，不建立 DAO Impl；迁移位于 `db/migration`。后续数据库访问继续复用已有 DAO 能力，并遵守新增 Mapper/SQL 的审批约定。

## 构建和测试

```powershell
cd D:\repository
pnpm build
pnpm test:rag
pnpm typecheck:desktop
pnpm build:desktop
cd services\server
.\mvnw.cmd verify
```

网页产物位于 `apps/web/dist/`，桌面端产物位于 `apps/desktop/out/`，后端产物位于 `services/server/target/desktop-cat-server-0.1.0-SNAPSHOT.jar`。

2026-09-14 已验证：网页类型检查和生产构建通过；Maven Wrapper 构建、1 项后端集成测试与 JAR 打包通过；浏览器实际显示后端 Spring Boot 3.5.16、Java 21.0.12，重新检查连接后响应时间更新。桌面端类型检查、生产构建和 NSIS 打包通过，解包后的 `猫的角落.exe` 已实际启动；活动接受、拒绝、随机时长和真实窗口位移通过运行验证，选择面板通过截图检查。

2026-09-15 已更新桌面猫的时段陪伴对话、触摸回应与活动文案，并移除界面中的倒计时、活动时长和奔跑速度线；桌面端 Node 与 Vue 类型检查、生产构建均已通过。

2026-09-16 已验证：网页个人文章和当天内心页面生产构建、桌面端类型检查与生产构建通过；Maven Wrapper `verify` 通过 11 项后端测试并完成 JAR 打包。应用已连接 PostgreSQL 16.13 正常启动，Flyway V1、V2、V3 均已执行。真实 HTTP 请求已覆盖文章 CRUD、文字回忆，以及每日情绪的创建和按天查询；情绪验证数据已清理。MyBatis XML 数据库读写已实际验证。浏览器已检查桌面宽度和 390px 窄屏的文章相关页面，控制台无错误。

2026-09-17 已验证：首页年度写作足迹生产构建通过，Maven Wrapper 测试共 13 项通过；真实 PostgreSQL 聚合查询返回正确的日记总数、写作天数和每日篇数。浏览器已检查 53 周格子、月份标签、当天标记和日期点击反馈，控制台无警告或错误。

2026-09-17 已验证：提醒管理网页生产构建、桌面端类型检查与生产构建通过；Maven Wrapper `verify` 共 15 项测试通过并完成 JAR 打包。PostgreSQL 16.13 已执行 Flyway V4，真实 HTTP 请求覆盖提醒创建、今天与未完成查询、修改、完成和逻辑删除，验证数据已清理。浏览器已检查提醒页的桌面布局、表单、筛选和空状态。

2026-09-18 已验证：登录、注册、退出、登录状态恢复、会话保护及 CSRF 请求处理已接入网页和后端；网页生产构建通过，Maven Wrapper 测试共 20 项通过。Flyway V5 新增 `app_user` 用户表，认证相关测试覆盖注册成功、重复用户名、参数校验、登录成功、错误凭据、匿名状态和受保护接口。

2026-09-20 已验证：Spring Session 已连接 Docker Redis，Actuator 健康状态为 `UP`，Lettuce 客户端实际建立连接；Web 会话、桌面端两分钟授权码和 15 分钟 Access Token 均已改由 Redis 承载。Maven Wrapper 测试共 27 项通过。

2026-09-21 已验证：桌面浏览器授权、账号数据隔离和照片回忆轮播已接入；网页生产构建、桌面端 Node 与 Vue 类型检查通过，Maven Wrapper 测试共 28 项通过。照片存储测试覆盖 WebP 格式、1200 × 900 尺寸、2 MB 上限及文件读写边界。

2026-09-22 已验证：无 API Key 的第一版知识检索已接入 Vue、Java 和 Python；Python 单元测试与真实 HTTP 请求通过，中文问题可以返回相关原文及来源。Java 继续负责登录与数据隔离，Python 不直接连接 PostgreSQL。

## PostgreSQL 连接配置

默认配置可直接连接当前本机数据库：

```text
jdbc:postgresql://127.0.0.1:5432/postgres
username: postgres
password: postgres
```

需要覆盖默认配置时，在启动后端的终端设置：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'postgres'
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5432/postgres'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = '替换为本地数据库密码'
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '6380'
$env:PHOTO_STORAGE_ROOT = 'D:\repository\services\server\data'
.\mvnw.cmd spring-boot:run
```

`PHOTO_STORAGE_ROOT` 可省略，默认使用后端工作目录下的 `./data`；该目录保存照片原文件，不应提交到 Git，部署和迁移时需要与 PostgreSQL 数据一起备份。当前本地 Redis 未启用认证并且只监听宿主机回环地址。若将 Redis 暴露到其他机器或部署到服务器，必须先由项目维护者定义 Redis 账号密码，再补充安全连接配置；不得把正式凭据提交到仓库。服务器部署时还必须通过环境变量提供真实数据库密码和稳定随机的 `AUTH_REMEMBER_ME_KEY`，HTTPS 部署同时设置 `AUTH_SECURE_COOKIES=true`。

应用首次启动时，Flyway 会在目标数据库创建 `flyway_schema_history`、`cat_profile`、`personal_record`、`daily_emotion`、`reminder`、`app_user`、`auth_device` 和 `photo`；业务主键使用带含义的字段，不使用裸 `id`。V1 创建小猫资料，V2 创建文章记录，V3 创建每日情绪及日期时间索引，V4 创建待办提醒及有效提醒索引，V5 创建用户表及唯一用户名约束，V6 创建设备授权表及有效设备索引，V7 创建图片记录及回忆索引，V8 为文章和每日情绪补充用户归属及按用户查询索引。

需要临时使用不连接数据库的内存模式时：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
.\mvnw.cmd spring-boot:run
```

`local` 配置只用于脱离数据库检查基础应用，认证接口不会启用，因此不能配合完整网页流程使用。

## 常见问题

- 后端启动失败：先检查 `java -version` 是 JDK 21，再确认 PostgreSQL 与 `desktop-cat-redis` 均已启动，并查看终端首个错误。
- 页面连接失败：检查后端是否启动、8080 是否被占用。普通页面需要前后端两个进程，知识检索还需要 Python 服务。
- 知识检索提示 Python 服务未启动：确认已经安装 Python 3.12、创建 `services/rag-service/.venv`，然后在项目根目录运行 `pnpm dev:rag`。
- 页面一直停留在登录页：确认后端使用默认 `postgres` 配置启动、数据库迁移已完成、Redis 可返回 `PONG`，并检查浏览器是否允许 `127.0.0.1` 的 Cookie。
- 照片上传失败：原图需为 JPG、PNG 或 WebP 且不超过 20 MB；网页裁剪后会生成 1200 × 900 WebP，最终文件必须不超过 2 MB。还应确认 `PHOTO_STORAGE_ROOT` 对后端进程可写。
- 桌面猫没有弹出登录页：检查 `DESKTOP_CAT_WEB_URL` 是否指向可访问的 Web 地址；本地开发默认是 `http://127.0.0.1:5173`。
- 写请求返回 403：刷新页面重新获取 CSRF Cookie；开发时应始终通过 `http://127.0.0.1:5173` 访问网页，避免混用 `localhost` 和 `127.0.0.1` 导致 Cookie 会话不一致。
- Flyway 报 `relation personal_record already exists`：说明该表曾在 Flyway 之外手工创建，但 V2 尚未登记。先备份并核对数据，再让表结构与迁移历史恢复一致；不要直接修改已经提交的 V2 文件。
- 5173 已被占用：停止原有前端进程后重启，Vite 不自动切换端口，避免访问错项目。
- 桌面猫消失：单击系统托盘的小猫图标，或在托盘菜单中选择“显示小猫（主屏幕）”，小猫会回到主屏幕右下角。
- 听不到猫叫：检查 Windows 当前输出设备和应用音量。猫叫使用随应用分发的本地真实录音，不读取麦克风，也不在运行时访问网络；来源和许可证见 `apps/desktop/THIRD_PARTY_NOTICES.md`。
- 首次依赖下载需要网络；依赖解析后的前端版本固定在 `pnpm-lock.yaml` 中。

完整规划见 [产品功能与技术蓝图](./猫的角落-产品功能与技术蓝图.md)。

## 参与贡献

欢迎通过 Fork 创建功能分支并提交 Pull Request。提交前请至少运行与改动相关的类型检查、构建和后端测试。

本项目采用 [MIT License](./LICENSE)。
