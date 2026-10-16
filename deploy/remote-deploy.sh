#!/bin/bash
#
# 在【服务器】上执行：替换 jar、重启、等待就绪、验证，失败自动回滚。
# 由 deploy/deploy.ps1 上传并调用，也可手动：
#     scp target/xxx.jar root@服务器:/tmp/app.jar.new
#     bash /tmp/remote-deploy.sh <新jar的sha256>
#
set -e

JAR=/opt/shiguang/app.jar
NEW_JAR=/tmp/app.jar.new
SERVICE=shiguang-blog
EXPECTED_SHA="$1"

if [ -z "$EXPECTED_SHA" ]; then
  echo "用法: bash remote-deploy.sh <新jar的sha256>"
  exit 1
fi

echo "=== 1. 校验上传的 jar 完整性 ==="
if [ ! -f "$NEW_JAR" ]; then
  echo "  !! $NEW_JAR 不存在"
  exit 1
fi
ACTUAL_SHA=$(sha256sum "$NEW_JAR" | awk '{print $1}')
echo "  期望 ${EXPECTED_SHA:0:16}..."
echo "  实际 ${ACTUAL_SHA:0:16}..."
if [ "$EXPECTED_SHA" != "$ACTUAL_SHA" ]; then
  echo "  !! 校验不一致，中止（文件可能没传完）"
  exit 1
fi
echo "  一致"

echo ""
echo "=== 2. 备份当前 jar ==="
TS=$(date +%Y%m%d-%H%M%S)
BACKUP="/opt/shiguang/jar-backup-$TS.jar"
cp -p "$JAR" "$BACKUP"
echo "  $BACKUP"
# 只保留最近 5 个备份
ls -1t /opt/shiguang/jar-backup-*.jar 2>/dev/null | tail -n +6 | xargs -r rm -f

echo ""
echo "=== 3. 停止服务 ==="
systemctl stop "$SERVICE"
sleep 2

echo ""
echo "=== 4. 替换 jar ==="
cp -p "$NEW_JAR" "$JAR"
chown shiguang:shiguang "$JAR"
chmod 644 "$JAR"
ls -lh "$JAR" | sed 's/^/  /'

echo ""
echo "=== 5. 启动服务 ==="
systemctl start "$SERVICE"

echo ""
echo "=== 6. 等待就绪（最多 90 秒）==="
READY=0
for i in $(seq 1 90); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8080/hello 2>/dev/null || echo 000)
  if [ "$CODE" = "200" ]; then
    echo "  第 ${i} 秒就绪"
    READY=1
    break
  fi
  sleep 1
done

if [ "$READY" != "1" ]; then
  echo "  !! 90 秒内未就绪，开始回滚"
  systemctl stop "$SERVICE"
  cp -p "$BACKUP" "$JAR"
  chown shiguang:shiguang "$JAR"
  systemctl start "$SERVICE"
  sleep 15
  echo -n "  回滚后状态: "; systemctl is-active "$SERVICE"
  echo "  失败原因见日志："
  journalctl -u "$SERVICE" -n 30 --no-pager | grep -iE 'error|exception|caused by' | tail -8 | sed 's/^/    /'
  exit 1
fi

echo ""
echo "=== 7. 接口验证 ==="
FAIL=0
for p in / /index.html /post.html /admin.html /songs.html /css/style.css /js/theme.js /js/songs.js \
         /hello /article/list /song/today /song/list /setting/site /user/me /user/quota; do
  C=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:8080$p")
  printf "  %-18s HTTP %s\n" "$p" "$C"
  if [ "$C" != "200" ]; then FAIL=1; fi
done

echo ""
echo "=== 8. 写接口鉴权（应全部 401）==="
for p in /article/publish /article/update /article/delete /song/upload /song/delete \
         /comment/add /comment/delete /upload/image /user/avatar /user/nickname \
         /admin/mute /setting/bg /setting/theme; do
  C=$(curl -s -o /dev/null -w '%{http_code}' -X POST "http://127.0.0.1:8080$p")
  printf "  %-18s HTTP %s\n" "$p" "$C"
  if [ "$C" != "401" ]; then FAIL=1; fi
done

echo ""
echo "=== 8b. 浏览上报接口（公开接口，必须匿名可访问）==="
# 0 号文章不存在，期望拿回业务错误 code 400：
#   若返回 401 说明被拦截器误拦了；若 404 说明路由没注册
VIEW_RESP=$(curl -s --max-time 5 -X POST "http://127.0.0.1:8080/article/0/view")
printf "  %-18s %s\n" "/article/0/view" "$VIEW_RESP"
case "$VIEW_RESP" in
  *'"code":400'*) : ;;
  *) echo "  !! 浏览上报接口异常（期望匿名可访问并返回 code 400）"; FAIL=1 ;;
esac

echo ""
echo "=== 9. 日志里的异常 ==="
ERRS=$(journalctl -u "$SERVICE" --since "2 min ago" --no-pager | grep -icE 'error|exception' || true)
echo "  错误条数: $ERRS"

echo ""
if [ "$FAIL" = "1" ]; then
  echo "!! 有接口验证未通过，请检查（jar 已更新，如需回滚：cp -p $BACKUP $JAR && systemctl restart $SERVICE）"
  exit 1
fi
echo "部署成功"
