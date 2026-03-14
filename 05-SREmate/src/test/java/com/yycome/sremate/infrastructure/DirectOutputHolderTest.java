package com.yycome.sremate.infrastructure;

import com.yycome.sremate.infrastructure.service.DirectOutputHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DirectOutputHolder 智能合并功能测试
 */
class DirectOutputHolderTest {

    @Test
    void testSmartMerge() {
        DirectOutputHolder holder = new DirectOutputHolder();

        // 模拟 queryContractListByOrderId 返回
        String contracts = "[{\"contract_code\":\"C1773200928952999\",\"type\":8,\"status\":6,\"platform_instance_id\":0,\"amount\":1304.00}]";

        // 模拟 queryContractSignedObjects 返回
        String relations = "[{\"contract_code\":\"C1773200928952999\",\"bill_code\":\"GBILL260310183702810022\",\"bind_type\":1},{\"contract_code\":\"C1773200928952999\",\"bill_code\":\"S15260311110004965\",\"bind_type\":3}]";

        holder.append(contracts);
        holder.append(relations);

        String result = holder.getAndClear();

        System.out.println("=== 智能合并结果 ===");
        System.out.println(result);

        // 验证：结果应该是嵌套结构
        assertNotNull(result);
        assertTrue(result.contains("\"contract_quotation_relation\""), "应该包含 contract_quotation_relation 字段");
        assertTrue(result.contains("\"bill_code\":\"GBILL260310183702810022\""), "应该包含关联的报价单数据");
        assertTrue(result.contains("\"contract_quotation_relation\":["), "应该是嵌套结构");
        // 没有调用 contract_node 和 contract_field 工具，不应该出现这些字段
        assertFalse(result.contains("\"contract_node\""), "没有调用 contract_node 工具，不应该出现");
        assertFalse(result.contains("\"contract_field\""), "没有调用 contract_field 工具，不应该出现");
    }

    @Test
    void testSingleResult() {
        DirectOutputHolder holder = new DirectOutputHolder();

        String single = "[{\"contract_code\":\"C123\",\"type\":1}]";
        holder.append(single);

        String result = holder.getAndClear();

        // 单个结果直接返回
        assertEquals(single, result);
    }

    @Test
    void testEmptyResult() {
        DirectOutputHolder holder = new DirectOutputHolder();

        assertNull(holder.getAndClear());
        assertFalse(holder.hasOutput());
    }

    @Test
    void testMultipleContracts() {
        DirectOutputHolder holder = new DirectOutputHolder();

        // 2 个合同
        String contracts = "[{\"contract_code\":\"C001\",\"type\":1},{\"contract_code\":\"C002\",\"type\":2}]";

        // 关联数据只属于 C001
        String relations = "[{\"contract_code\":\"C001\",\"bill_code\":\"GBILL001\"}]";

        holder.append(contracts);
        holder.append(relations);

        String result = holder.getAndClear();

        System.out.println("=== 多合同合并结果 ===");
        System.out.println(result);

        // C001 和 C002 都应该有 contract_quotation_relation 字段（因为调用了该工具）
        assertTrue(result.contains("\"contract_code\":\"C001\""));
        assertTrue(result.contains("\"contract_code\":\"C002\""));
        assertTrue(result.contains("\"bill_code\":\"GBILL001\""));
        // 因为调用了 queryContractSignedObjects，所以两个合同都应该有 contract_quotation_relation 字段
        assertTrue(result.contains("\"contract_quotation_relation\":["), "调用过工具，应该有嵌套字段");
    }

    @Test
    void testInvokedButEmptyResult() {
        DirectOutputHolder holder = new DirectOutputHolder();

        // 合同数据
        String contracts = "[{\"contract_code\":\"C001\",\"type\":1}]";
        // 模拟：调用了 queryContractNodes 但返回空数组
        // 需要手动标记类型（因为空数组无法自动检测类型）
        holder.append(contracts);
        holder.markTypeInvoked("contract_node");  // 标记调用过 contract_node
        String result = holder.getAndClear();

        System.out.println("=== 调用但无数据 ===");
        System.out.println(result);

        // 调用过工具，即使没数据也应该有 key
        assertTrue(result.contains("\"contract_node\":[]"), "调用过 contract_node 工具但无数据，应该保留空数组");
        // 没有调用其他工具，不应该有这些 key
        assertFalse(result.contains("\"contract_quotation_relation\""), "没有调用 contract_quotation_relation 工具，不应该出现");
        assertFalse(result.contains("\"contract_field\""), "没有调用 contract_field 工具，不应该出现");
    }
}
