## 1. 销售项目 - SreQueryController 接口开发

**修改位置**: `/Users/zqy/work/project/nrs-sales-project/utopia-nrs-sales-project-start/src/main/java/com/ke/utopia/nrs/salesproject/controller/contract/tool/SreQueryController.java`

**设计原则**: 每张表只暴露一个查询接口

- [x] 1.1 GET /sre/contract - 合同表查询（支持 contractCode/projectOrderId）
- [x] 1.2 GET /sre/contract-node - 合同节点表查询
- [x] 1.3 GET /sre/contract-user - 签约人表查询
- [x] 1.4 GET /sre/contract-field - 合同扩展字段表查询（分表）
- [x] 1.5 GET /sre/contract-quotation-relation - 签约单据表查询
- [x] 1.6 GET /sre/project-config-snap - 配置快照表查询
- [x] 1.7 GET /sre/contract-city-company-info - 城市公司配置表查询

## 2. 销售项目 - Service 复用

- [x] 2.1 使用 ContractFieldService.getFieldMap() 查询扩展字段（已处理分表路由）

## 3. 销售项目 - 测试

- [x] 3.1 编写 SreQueryController 接口测试
- [x] 3.2 本地验证所有接口可用

## 4. SRE-Agent - 接口配置

**修改位置**: `/Users/zqy/work/AI-Project/workTree/spring-ai-alibaba-tutorial-feature-integration-test/06-SRE-Agent/`

- [x] 4.1 创建 endpoints/sre-data-endpoints.yml
- [x] 4.2 配置 7 个接口定义（与 7 张表一一对应）

## 5. SRE-Agent - Gateway 改造

- [x] 5.1 改造 ContractGateway
- [x] 5.2 改造 ContractNodeGateway
- [x] 5.3 改造 ContractFieldGateway
- [x] 5.4 改造 ContractConfigGateway（4 步查询）
- [x] 5.5 改造 ContractUserGateway
- [x] 5.6 改造 ContractQuotationRelationGateway
- [x] 5.7 改造 PersonalQuoteGateway

## 6. SRE-Agent - 清理与测试

- [x] 6.1 移除所有 Gateway 对 ContractDao 的依赖
- [x] 6.2 运行集成测试验证功能正常
- [x] 6.3 检查日志无 ERROR 错误

## 7. 环境配置优化

- [x] 7.1 EnvironmentConfig 支持 baseUrl 配置
- [x] 7.2 支持多服务域名（sales-project, order-service）
- [x] 7.3 更新所有 yml 接口模板使用 ${baseUrl} 占位符
- [x] 7.4 新增生产环境配置

## 8. 代码质量优化

- [x] 8.1 重构 ContractConfigGateway 变量命名（json1→contractJson 等）
- [x] 8.2 修复 Map.of() 类型转换问题

## 9. 文档与部署

- [ ] 9.1 更新 SRE-Agent CLAUDE.md 文档
- [ ] 9.2 更新销售项目接口文档
- [ ] 9.3 部署销售项目新接口到测试环境
- [ ] 9.4 部署 SRE-Agent 改造到测试环境
- [ ] 9.5 联调测试通过
- [ ] 9.6 部署到生产环境
