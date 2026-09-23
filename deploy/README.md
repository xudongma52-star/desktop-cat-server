# Docker 部署

生产环境使用一个 Docker Compose 项目运行 PostgreSQL、Redis、Java 后端、Python 知识检索和 Nginx。只有 Nginx 的 80/443 对公网开放；数据库、Redis、Java 和 Python 只映射到服务器回环地址，方便本机检查和 SSH 隧道访问。

## 目录约定

每次发布放在 `/opt/desktop-cat/releases/<release-id>`，`/opt/desktop-cat/current` 指向当前版本。Compose 文件位于当前版本的 `deploy` 目录，并依赖同一发布目录中的：

- `../services/server/target/desktop-cat-server-0.1.0-SNAPSHOT.jar`
- `../services/rag-service/requirements.txt`
- `../services/rag-service/rag_service`

照片继续保存在 `/opt/desktop-cat/data`，网页及下载文件继续保存在 `/www/wwwroot/maxmeme.cn`，证书继续保存在 `/etc/letsencrypt`。PostgreSQL 和 Redis 使用 Docker 命名卷。

把 `.env.example` 复制为 `.env` 并填写数据库密码、Remember-Me 密钥和火山方舟 API Key。`.env` 不得提交到 Git。`ARK_MODEL` 默认使用 `doubao-seed-2-1-turbo-260628`；未提供 `ARK_API_KEY` 时仍可检索原文，只是不生成回答。

```bash
cd /opt/desktop-cat/current/deploy
docker compose config --quiet
docker compose up -d
docker compose ps
```

## 检查与日志

```bash
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 rag
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8090/health
curl -fsS https://maxmeme.cn/actuator/health
```

Certbot 仍由服务器定时器管理，证书写入宿主机 `/etc/letsencrypt`，Nginx 以只读方式挂载该目录。服务器已经配置续期后的 Nginx 容器重载脚本。当前证书由手动 DNS 验证签发；在证书到期前，需要接入域名服务商的 DNS API 自动验证，或者在域名 HTTP 验证不再被拦截后切换为 Webroot 验证。
