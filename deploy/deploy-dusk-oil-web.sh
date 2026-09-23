#!/usr/bin/env bash
set -Eeuo pipefail
release_id=dusk-oil-20260922
web_root=/www/wwwroot/maxmeme.cn
upload_root=/opt/desktop-cat/uploads/dusk-oil-20260922
backup_root=/opt/desktop-cat/backups/dusk-oil-20260922
test -d "$web_root/assets"
test -f "$web_root/index.html"
test ! -e "$backup_root"
mkdir -p "$backup_root" "$upload_root/unpacked"
tar -czf "$backup_root/web-before.tar.gz" -C "$web_root" index.html assets
cp "$web_root/index.html" "$backup_root/index.html"
rollback() {
  cp "$backup_root/index.html" "$web_root/index.html.rollback"
  mv -f "$web_root/index.html.rollback" "$web_root/index.html"
  echo "Previous homepage restored."
}
trap rollback ERR
tar -xzf "$upload_root/web.tar.gz" -C "$upload_root/unpacked"
# 保留历史哈希资源，已打开的页面在发布后仍可正常使用。
cp -a "$upload_root/unpacked/assets/." "$web_root/assets/"
cp "$upload_root/unpacked/index.html" "$web_root/index.html.next"
chmod 644 "$web_root/index.html.next"
mv -f "$web_root/index.html.next" "$web_root/index.html"
curl -fsS --resolve maxmeme.cn:443:127.0.0.1 https://maxmeme.cn/ | grep -q 'index-D9RC8-Go.js'
for asset in dusk-writer-round-CPYa_ypq.webp dusk-window-frame-DKDp0XbP.webp index-f3FZgF80.css; do
  curl -fsS --resolve maxmeme.cn:443:127.0.0.1 "https://maxmeme.cn/assets/$asset" -o /dev/null
done
curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'
trap - ERR
printf 'Frontend deployed: %s\nBackup: %s\n' "$release_id" "$backup_root"
