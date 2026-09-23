#!/usr/bin/env bash
set -Eeuo pipefail
release_id=dusk-20260922
web_root=/www/wwwroot/maxmeme.cn
upload_root=/opt/desktop-cat/uploads/dusk-20260922
backup_root=/opt/desktop-cat/backups/dusk-20260922
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
# 保留旧的内容哈希资源，避免已打开页面在发布过程中请求不到旧文件。
cp -a "$upload_root/unpacked/assets/." "$web_root/assets/"
cp "$upload_root/unpacked/index.html" "$web_root/index.html.next"
chmod 644 "$web_root/index.html.next"
mv -f "$web_root/index.html.next" "$web_root/index.html"
curl -fsS --resolve maxmeme.cn:443:127.0.0.1 https://maxmeme.cn/ | grep -q 'index-BUsXqts4.js'
curl -fsS --resolve maxmeme.cn:443:127.0.0.1 https://maxmeme.cn/assets/dusk-room-R5HaPAc4.webp -o /dev/null
curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'
trap - ERR
printf 'Frontend deployed: %s\nBackup: %s\n' "$release_id" "$backup_root"
