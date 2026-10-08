#!/usr/bin/env bash
set -euo pipefail

# HTTPS terminates at the existing host Nginx; the project container serves HTTP.
/www/server/nginx/sbin/nginx -t -c /www/server/nginx/conf/nginx.conf
/www/server/nginx/sbin/nginx -s reload -c /www/server/nginx/conf/nginx.conf
