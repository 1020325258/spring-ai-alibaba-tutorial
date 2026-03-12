# 个性化报价查询接口设计

**日期：** 2026-03-12
**状态：** 已批准

---

## 背景

用户需要通过 SREmate 查询订单下对应单据的个性化报价数据。后端接口已存在，需要为 Agent 增加相应的工具能力。

---

## 接口信息

```
GET http://utopia-nrs-sales-project.${env}.ttb.test.ke.com/api/contract/tool/getContractPersonalData
    ?projectOrderId=${projectOrderId}
    &subOrderNoList=${subOrderNoList}
    &billCodeList=${billCodeList}
    &changeOrderId=${changeOrderId}
```

**Headers：** `X-NRS-User-Id: 1000000000000000`

**参数约束：**
- `projectOrderId`：必填，纯数字
- `subOrderNoList`、`billCodeList`、`changeOrderId`：至少填一个
  - S单格式：`S15260312120004471`
  - 报价单格式：`GBILL260312104241050001`
  - 变更单格式：与订单号类似

---

## 方案决策

选择**新建 `PersonalQuoteTool` 类**（方案 A），理由：
- 三种单据跨越现有 `BudgetBillTool` 和 `SubOrderTool` 的边界
- 新建类职责单一，符合 CLAUDE.md 工具类按业务领域拆分的规范

---

## 实现设计

### 1. 接口模板

文件：`src/main/resources/endpoints/contract-endpoints.yml`

追加 endpoint id `contract-personal-data`，GET 方法，四个参数（`projectOrderId` 必填，其余三个可选）。

### 2. Tool 类

新建 `src/main/java/com/yycome/sremate/trigger/agent/PersonalQuoteTool.java`

- `@Tool` 方法：`queryContractPersonalData(projectOrderId, subOrderNoList, changeOrderId, billCodeList)`
- `@DataQueryTool` 注解：结果直接输出，绕过 LLM 归纳
- 全空校验：三个可选参数全空时返回提示语，引导用户补充单据号

### 3. 系统提示词

文件：`src/main/resources/prompts/sre-agent.md`

- 关键词映射表新增：`个性化报价` → `queryContractPersonalData`
- 快速决策表新增：`{订单号}个性化报价` → `queryContractPersonalData`
- 可用工具列表新增：`2f. queryContractPersonalData` 说明

---

## 测试覆盖

1. **意图识别**：输入含"个性化报价"时，Agent 调用 `queryContractPersonalData`
2. **关键词触发**：订单号 + S单/报价单/变更单 + "个性化报价" 均能触发
3. **互斥验证**：输入"报价单"时不触发 `queryContractPersonalData`，触发 `queryBudgetBillList`
