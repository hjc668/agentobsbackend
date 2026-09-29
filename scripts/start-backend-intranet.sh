#!/usr/bin/env sh

# 行内容器启动入口：基础镜像已有 OpenJDK 8，镜像中预先放入可执行 JAR。
# 首期固定使用 mock 认证，不在容器内构建项目。

set -eu

APP_NAME="langfuse-web-service"
APP_JAR=/home/icbc/langfuse-web-service.jar

if [ ! -f "$APP_JAR" ] || [ ! -r "$APP_JAR" ]; then
    echo "[$APP_NAME] 启动失败：JAR 不存在或不可读：$APP_JAR" >&2
    exit 1
fi

echo "[$APP_NAME] 以 mock 模式启动：$APP_JAR"

# 不使用 nohup 或后台运行；exec 让 Java 成为容器 PID 1，从而正确接收停止信号。
exec java -Dcom.alibaba.polardbx.core.cj.disableAbandonedConnectionCleanup=true \
    -jar "$APP_JAR" --app.auth.mode=mock
