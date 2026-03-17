# SREmate 集成测试执行报告

> 最后更新: 2026-03-17 23:11:24

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## PersonalQuoteToolIT

### ✅ personalQuoteKeyword_withBillCode_shouldCallQueryContractPersonalData

- **输入:** 826031210000003581下GBILL260312104241050001的个性化报价
- **输出:** {"code":7000,"message":"查询团装报价单附件为空，请重试或联系客服","data":null,"success":false}
- **耗时:** 3474ms
- **工具调用:**
  - `queryContractPersonalData` ✓ 1420ms

---

