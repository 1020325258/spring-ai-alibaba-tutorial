#!/bin/bash
# SRE-Agent 端到端测试脚本
# 运行 06-SRE-Agent 的端到端测试，验证问题排查和数据查询能力

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$PROJECT_DIR")"

echo "======================================"
echo "  SRE-Agent 端到端测试"
echo "======================================"
echo ""

# 检查配置文件
if [ ! -f "$PROJECT_DIR/src/main/resources/application-local.yml" ]; then
    echo "⚠️  警告: application-local.yml 不存在，使用默认配置"
fi

echo "【端到端测试】验证问题排查和数据查询能力..."
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test \
  -f "$PROJECT_ROOT/pom.xml" \
  -pl 06-SRE-Agent \
  -Dtest="InvestigateAgentIT,QueryAgentIT,SkillMechanismIT" \
  -Dsurefire.failIfNoSpecifiedTests=false

echo ""
echo "======================================"
echo "  端到端测试通过 ✓"
echo "======================================"
echo ""
echo "测试报告已生成: 06-SRE-Agent/docs/test-execution-report.md"