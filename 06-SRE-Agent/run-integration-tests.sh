#!/bin/bash
# 快速入口：直接运行所有测试（单元 + 集成）
# 完整脚本见 scripts/run-integration-tests.sh

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$SCRIPT_DIR/scripts/run-integration-tests.sh"
