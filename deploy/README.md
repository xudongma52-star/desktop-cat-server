# Docker 部署

生产环境使用一个 Docker Compose 项目运行 PostgreSQL、Redis、Java 后端、Python 知识检索和 Nginx。项目 Nginx 只映射到 `127.0.0.1:18080`；数据库、Redis、Java 和 Python 同样只映射到服务器回环地址，方便本机检查和 SSH 隧道访问。原有宿主机 Nginx 接收公网 80/443，只将 `maxmeme.cn` 和 `www.maxmeme.cn` 转发到项目容器，其他站点配置保持不变。

## 目录约定

每次发布放在 `/opt/desktop-cat/releases/<release-id>`，`/opt/desktop-cat/current` 指向当前版本。Compose 文件位于当前版本的 `deploy` 目录，并依赖同一发布目录中的：

- `../services/server/target/desktop-cat-server-0.1.0-SNAPSHOT.jar`
- `../services/rag-service/requirements.txt`
- `../services/rag-service/rag_service`

照片继续保存在 `/opt/desktop-cat/data`，网页及下载文件继续保存在 `/www/wwwroot/maxmeme.cn`，证书继续保存在 `/etc/letsencrypt`。PostgreSQL 和 Redis 使用 Docker 命名卷。

`nginx/default.conf` 是容器内部 HTTP 配置，保留入口传来的 HTTPS 协议和客户端地址，保证 Secure Cookie、登录和 SSE 正常。`nginx/maxmeme.cn.host.conf` 是宿主机该域名的入口配置，安装到 `/www/server/panel/vhost/nginx/maxmeme.cn.conf`；它负责 HTTPS 和 HTTP 跳转，并禁用宿主机继承的代理缓存。首次切换时先备份该域名配置，释放容器的公网端口并启动原有宿主机 Nginx。不要替换宿主机全局配置或其他站点配置。

日常项目发布仍使用下面的 Docker Compose 命令。修改域名入口配置时才需要单独检查并重载宿主机 Nginx。

本次切换保留了原有宿主机 Nginx 的开机启动设置，目前 `systemctl is-enabled nginx` 返回 `disabled`。服务器重启后需按原有运维方式启动入口 Nginx（当前启动命令为 `/etc/init.d/nginx start`）；Docker 容器自身的重启策略不变。

把 `.env.example` 复制为 `.env` 并填写数据库密码、Remember-Me 密钥和火山方舟 API Key。`.env` 不得提交到 Git。`ARK_MODEL` 默认使用 `doubao-seed-2-1-turbo-260628`；未提供 `ARK_API_KEY` 时仍可检索原文，只是不生成回答。

```bash
cd /opt/desktop-cat/current/deploy
docker compose config --quiet
docker compose up -d
docker compose ps
```

## 检查与日志

“猫的角落”的独立公网 HTTPS 入口为 `https://1.92.82.236:7003`。宿主机 Nginx 在 `7003` 端口复用 `maxmeme.cn` 的 HTTPS 站点及其代理规则，仍转发到 `127.0.0.1:18080`，并保留 Secure Cookie。服务器防火墙和云安全组需允许 TCP `7003`。直接通过 IP 访问时，现有域名证书与 IP 不匹配，浏览器会显示证书警告；域名入口和其他站点端口不变。

```bash
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 rag
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8090/health
curl -fsS -H 'Host: maxmeme.cn' http://127.0.0.1:18080/container-health
curl -fsS https://maxmeme.cn/actuator/health
```

Certbot 仍由服务器定时器管理，证书写入宿主机 `/etc/letsencrypt`，由宿主机 Nginx 使用；项目容器不再挂载证书。将 `nginx/renew-maxmeme.sh` 安装为 `/etc/letsencrypt/renewal-hooks/deploy/desktop-cat-nginx-reload.sh` 并保留可执行权限，续期后检查并重载宿主机 Nginx。当前证书由手动 DNS 验证签发；在证书到期前，需要接入域名服务商的 DNS API 自动验证，或者在域名 HTTP 验证不再被拦截后切换为 Webroot 验证。
