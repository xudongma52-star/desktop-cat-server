# 参与开发

感谢你愿意一起建设“猫的角落”。这个项目使用 `main` 保存稳定版本，使用 `dev` 汇总日常开发。

## 开发流程

1. Fork 本仓库，并把自己的分支建立在最新的 `dev` 上。
2. 功能分支使用 `feature/功能名`，修复分支使用 `fix/问题名`。
3. 一次提交只解决一个清晰的问题，同时补充必要的测试或验证说明。
4. 向本仓库的 `dev` 分支提交 Pull Request，不要直接向 `main` 提交日常功能。
5. `main` 只接收维护者从 `dev` 发起的发布 Pull Request。

```bash
git fetch origin
git switch dev
git pull --ff-only origin dev
git switch -c feature/reminder
```

## 提交信息

建议使用下面的格式：

```text
feat: 增加日记记录功能
fix: 修复小猫名字没有及时刷新
docs: 补充本地启动说明
refactor: 整理提醒任务逻辑
test: 补充资料更新测试
chore: 更新开发工具配置
```

## 提交前检查

```bash
pnpm install --frozen-lockfile
pnpm typecheck
pnpm build
pnpm typecheck:desktop
pnpm build:desktop

cd services/server
./mvnw verify
```

Windows PowerShell 请将最后一条命令写成 `.\mvnw.cmd verify`。

不要提交真实密码、Token、服务器密钥、个人日记或其他隐私数据。数据库配置请通过环境变量传入。
