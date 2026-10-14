#!/usr/bin/env bash
#
# 一键部署：在【本地开发机】上执行，构建 jar 并推送到服务器重启服务。
#
# 用法：
#   1. 改下面两行 SERVER / REMOTE_DIR
#   2. chmod +x deploy/deploy.sh
#   3. ./deploy/deploy.sh
#
set -euo pipefail

SERVER="root@你的服务器IP"          # ← 改成你的服务器
REMOTE_DIR="/opt/shiguang"          # ← 和 systemd 里的 WorkingDirectory 保持一致

cd "$(dirname "$0")/.."

echo "==> 1/4 构建 jar"
# -DskipTests：contextLoads 集成测试需要连数据库，构建机上没有 MySQL 会失败
./mvnw clean package -DskipTests

JAR=$(ls target/*.jar | grep -v '\.original$' | head -n1)
echo "    产物：$JAR"

echo "==> 2/4 上传到服务器"
# 先传成 .new，避免覆盖正在运行的 jar 导致文件损坏
scp "$JAR" "$SERVER:$REMOTE_DIR/app.jar.new"

echo "==> 3/4 替换并重启"
ssh "$SERVER" "
  set -e
  mv $REMOTE_DIR/app.jar.new $REMOTE_DIR/app.jar
  chown shiguang:shiguang $REMOTE_DIR/app.jar
  systemctl restart shiguang-blog
"

echo "==> 4/4 等待启动并检查"
sleep 6
ssh "$SERVER" "
  systemctl is-active shiguang-blog
  curl -s -o /dev/null -w '首页 HTTP %{http_code}\n' http://localhost:8080/
"

echo
echo "完成。看日志：ssh $SERVER 'journalctl -u shiguang-blog -f'"
