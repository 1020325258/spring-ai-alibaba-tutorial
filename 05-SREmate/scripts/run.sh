#!/bin/bash
# SREmate 应用启动脚本
# 启动前自动清理占用端口

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

PORT=8089

echo "======================================"
echo "  SREmate 启动"
echo "======================================"

# 清理占用端口
if lsof -i :$PORT > /dev/null 2>&1; then
    echo "⚠️  端口 $PORT 被占用，正在清理..."
    kill -9 $(lsof -t -i :$PORT) 2>/dev/null
    sleep 1
    echo "✓ 端口已释放"
fi

echo "启动应用..."
cd "$PROJECT_DIR"

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn spring-boot:run -q
