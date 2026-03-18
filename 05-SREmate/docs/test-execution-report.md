# SREmate 集成测试执行报告

> 最后更新: 2026-03-17 23:36:43

> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`

---

## PersonalQuoteToolIT

### ✅ personalQuoteKeyword_withBillCode_shouldCallQueryPersonalQuote

- **输入:** 826031210000003581下GBILL260312104241050001的个性化报价
- **输出:** {"queryEntity":"Order","queryValue":"826031210000003581","records":[{"projectOrderId":"826031210000003581","personalQuotes":[{"projectOrderId":"826031210000003581","subOrderNoList":"","billCodeList":"GBILL260312104241050001","changeOrderId":"","_rawData":"{\"code\":2000,\"message\":\"操作成功\",\"data\"...
- **耗时:** 3538ms
- **工具调用:**
  - `queryPersonalQuote` ✓ 1368ms

### ✅ personalQuoteKeyword_withSubOrder_shouldCallQueryPersonalQuote

- **输入:** 826031210000003581下S15260312120004471的个性化报价
- **输出:** {"queryEntity":"Order","queryValue":"826031210000003581","records":[{"projectOrderId":"826031210000003581","personalQuotes":[{"projectOrderId":"826031210000003581","subOrderNoList":"S15260312120004471","billCodeList":"","changeOrderId":"","_rawData":"{\"code\":2000,\"message\":\"操作成功\",\"data\":{\"p...
- **耗时:** 2527ms
- **工具调用:**
  - `queryPersonalQuote` ✓ 1368ms

### ✅ personalQuoteKeyword_shouldNotCallBudgetBillTool

- **输入:** 826031210000003581下S15260312120004471的个性化报价
- **输出:** {"queryEntity":"Order","queryValue":"826031210000003581","records":[{"projectOrderId":"826031210000003581","personalQuotes":[{"projectOrderId":"826031210000003581","subOrderNoList":"S15260312120004471","billCodeList":"","changeOrderId":"","_rawData":"{\"code\":2000,\"message\":\"操作成功\",\"data\":{\"p...
- **耗时:** 2410ms
- **工具调用:**
  - `queryPersonalQuote` ✓ 1368ms

---

