# 猫的角落 · 个人知识库与桌面猫

Vue 3 + Electron + Spring Boot 3 + JDK 21，使用原生 MyBatis。当前已经有一条可运行的网页前后端链路，以及一个可独立运行的 Windows 桌面猫。

## 当前范围

- 已建立 Vue、TypeScript、Vite、Router 和 Pinia 前端基础。
- 已建立 Spring Boot 3.5.16 后端，加入 Validation、Actuator、MyBatis Starter 3.0.5 和 PostgreSQL 驱动。
- 默认连接本机 PostgreSQL；应用启动时由 Flyway 自动建表。需要脱离数据库开发时可显式启用 `local` 配置。
- 已建立 Electron 桌面端：透明无边框窗口、置顶显示、透明区域鼠标穿透、拖动、真实猫叫、活动状态、自由移动、主动陪伴对话、托盘显隐与退出、窗口位置记忆和 Windows 安装包。
- 当前 Q 版黑猫根据自己的猫照片生成，保留纯黑毛、圆脸、厚爪与琥珀眼；多组透明 PNG 精灵图配合 CSS 表现发呆、睡觉、舔毛、玩耍、吃冻干、散步和奔跑七种活动。
- 已完成第一张业务表 `cat_profile`、Flyway 迁移和原生 MyBatis XML，可在网页修改小猫名字，并由 Electron 定时同步和离线缓存。
- 日记、RAG、提醒、拖拽投喂冻干、登录和开机自启尚未实现；页面中的后续方向明确标注为待开发。
- 开发服务绑定本机地址，当前框架用于本地学习，正式发布前需补齐鉴权与部署配置。

## 环境

JDK 21、Node.js 22.12+（建议 24 LTS）、pnpm 11.19.0。后端使用 Maven Wrapper。

如果你的终端找不到 `pnpm`，可把下方命令中的 `pnpm` 替换为 `npx.cmd --yes pnpm@11.19.0`，例如 `npx.cmd --yes pnpm@11.19.0 dev`。

## 启动

首次在根目录安装前端依赖：

```powershell
cd D:\repository
pnpm install
```

确认本机 PostgreSQL 已启动，默认连接参数为 `127.0.0.1:5432/postgres`、用户 `postgres`、密码 `postgres`。打开第一个终端，启动后端：

```powershell
cd D:\repository\services\server
.\mvnw.cmd spring-boot:run
```

打开第二个终端，启动前端：

```powershell
cd D:\repository
pnpm dev
```

打开 [http://127.0.0.1:5173](http://127.0.0.1:5173)，应看到“前后端已连通”。启动任一服务的终端按 `Ctrl+C` 可停止该服务。

后端接口：[运行状态](http://127.0.0.1:8080/api/system/status)、[小猫资料](http://127.0.0.1:8080/api/cat/profile)、[健康检查](http://127.0.0.1:8080/actuator/health)。

单独开发桌面猫：

```powershell
cd D:\repository
pnpm dev:desktop
```

桌面猫会每 10 秒从 Java 后端同步一次名字；后端不可用时读取本地缓存并继续运行。远程部署后通过 `DESKTOP_CAT_API_URL` 指定服务地址。单击小猫会播放真实猫叫并打开活动面板，可以请求发呆、睡觉、舔毛、玩耍、吃冻干、散步或奔跑；小猫可能按活动设定接受或拒绝，活动结束后会自主选择下一项。玩耍、散步和奔跑时，窗口会在当前显示器可用区域内自由移动、随机转向并在屏幕边缘折返；打开选择面板或拖动小猫时会暂停自动移动。

小猫会结合早晨、中午、下午、傍晚和夜间主动说不同的陪伴话语，通常每 16–32 秒更新一次，并尽量避免最近出现过的内容。摸摸小猫时会随机回应并暂时保留这句话，随后继续日常对话。活动面板只展示当前状态和可选活动，不显示倒计时及活动时长。

关闭窗口会隐藏到系统托盘，从托盘菜单可以重新显示或彻底退出。透明区域会穿透鼠标，不遮挡下面的桌面内容。

生成 Windows 安装程序：

```powershell
cd D:\repository
pnpm dist:desktop
```

安装程序位于 `apps/desktop/release/猫的角落-0.3.0-x64-setup.exe`。免安装运行文件位于 `apps/desktop/release/win-unpacked/猫的角落.exe`。安装包暂未购买代码签名证书，Windows 可能显示未知发布者提示。

## 请求是怎么走的

```text
HomeView.vue
  → src/api/system.ts 的 fetch('/api/system/status')
  → Vite 开发代理转到 127.0.0.1:8080
  → SystemController 返回 JSON
  → Vue 更新界面
```

Vite 代理只用于开发。构建生成的静态页面需要由正式反向代理把 `/api` 路径转发给后端。

## 项目目录

```text
apps/web/                    Vue 前端
  src/api/                   HTTP 请求
  src/router/                页面路由
  src/views/                 页面
apps/desktop/                Electron 桌面猫
  src/main/                  窗口、托盘、位置保存和 IPC
  src/preload/               受限的渲染进程桥接 API
  src/shared/                主进程和 Vue 共用的活动类型与规则
  src/renderer/              Vue 小猫界面与交互
    src/assets/cat/          根据自己的猫生成的透明 Q 版状态图
    src/assets/audio/        本地猫叫资源
  build/                     应用图标
services/server/             Java 后端
  src/main/java/             启动类、按业务组织的代码
  src/main/resources/        配置文件
  src/test/java/             后端集成测试
```

小猫资料链路位于后端 `catprofile` 包、`mappers/CatProfileMapper.xml` 和 `db/migration`。后续数据库访问继续复用已有能力，并遵守新增 Mapper/SQL 的审批约定。

## 构建和测试

```powershell
cd D:\repository
pnpm build
pnpm typecheck:desktop
pnpm build:desktop
cd services\server
.\mvnw.cmd verify
```

网页产物位于 `apps/web/dist/`，桌面端产物位于 `apps/desktop/out/`，后端产物位于 `services/server/target/desktop-cat-server-0.1.0-SNAPSHOT.jar`。

2026-09-14 已验证：网页类型检查和生产构建通过；Maven Wrapper 构建、1 项后端集成测试与 JAR 打包通过；浏览器实际显示后端 Spring Boot 3.5.16、Java 21.0.12，重新检查连接后响应时间更新。桌面端类型检查、生产构建和 NSIS 打包通过，解包后的 `猫的角落.exe` 已实际启动；活动接受、拒绝、随机时长和真实窗口位移通过运行验证，选择面板通过截图检查。MyBatis 数据库读写尚未验证。

2026-09-15 已更新桌面猫的时段陪伴对话、触摸回应与活动文案，并移除界面中的倒计时、活动时长和奔跑速度线；桌面端 Node 与 Vue 类型检查、生产构建均已通过。

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
.\mvnw.cmd spring-boot:run
```

服务器部署时必须通过环境变量提供真实密码，不要把服务器密码提交到仓库。应用首次启动时，Flyway 会在目标数据库创建 `cat_profile` 表、`flyway_schema_history` 表并写入主资料；所有主键使用带业务含义的 `profile_id`，不使用裸 `id`。

需要临时使用不连接数据库的内存模式时：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local'
.\mvnw.cmd spring-boot:run
```

## 常见问题

- 后端启动失败：先检查 `java -version` 是 JDK 21，并查看终端首个错误。
- 页面连接失败：检查后端是否启动、8080 是否被占用。默认只需要启动前后端两个进程。
- 5173 已被占用：停止原有前端进程后重启，Vite 不自动切换端口，避免访问错项目。
- 桌面猫消失：单击系统托盘的小猫图标，或在托盘菜单中选择“显示小猫”。
- 听不到猫叫：检查 Windows 当前输出设备和应用音量；声音只在单击后播放，不读取麦克风。猫叫来源和许可证见 `apps/desktop/THIRD_PARTY_NOTICES.md`。
- 首次依赖下载需要网络；依赖解析后的前端版本固定在 `pnpm-lock.yaml` 中。

完整规划见 [技术栈与架构](./桌面猫个人助手-技术栈与架构.md)。
