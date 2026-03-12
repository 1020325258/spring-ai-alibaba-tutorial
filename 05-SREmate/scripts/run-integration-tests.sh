#!/bin/bash
# SREmate 集成测试一键运行脚本
# 每次代码变更后执行，确保所有工具链路正常
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$PROJECT_DIR")"

echo "======================================"
echo "  SREmate 集成测试"
echo "======================================"

JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  mvn test \
  -f "$PROJECT_ROOT/pom.xml" \
  -pl 05-SREmate \
  -Dtest="StartupIT,ContractQueryToolIT,ContractInstanceToolIT,ContractConfigToolIT,SubOrderToolIT,SkillQueryToolIT,HttpEndpointToolIT,PersonalQuoteToolIT" \
  -Dsurefire.failIfNoSpecifiedTests=false

echo ""
echo "======================================"
echo "  所有集成测试通过 ✓"
echo "======================================"
