package com.yycome.sremate.types.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * 合同类型枚举
 * 对应 contract_type 字段
 */
public enum ContractTypeEnum {

    SUBSCRIBE((byte) 1, "认购合同", "认购", "定金"),
    DESIGN((byte) 2, "设计合同", "设计"),
    PACKAGE_FORMAL((byte) 3, "正式套餐合同", "正签", "正式合同", "套餐正式", "正签合同"),
    PACKAGE_CHANGE((byte) 4, "套餐变更协议", "变更", "套餐变更", "变更合同", "套餐变更合同"),
    FIRST_PAYMENT((byte) 5, "首期款合同", "首期款", "首期款协议", "首期合同"),
    WHOLE_PACKAGE_FIRST((byte) 6, "整装首期款合同", "整装首期", "整装首期款"),
    DRAWING((byte) 7, "套餐施工图纸", "图纸"),
    PERSONAL((byte) 8, "销售合同", "销售", "个性化"),
    DESIGN_CHANGE((byte) 11, "设计变更协议", "设计变更"),
    SUPPLEMENT((byte) 29, "补充协议", "补充"),
    SETTLEMENT((byte) 30, "和解协议", "和解");

    private final byte code;
    private final String name;
    private final String[] aliases;

    ContractTypeEnum(byte code, String name, String... aliases) {
        this.code = code;
        this.name = name;
        this.aliases = aliases;
    }

    public byte getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    /**
     * 用户输入到枚举的映射（支持名称和别名）
     */
    private static final Map<String, Byte> NAME_TO_CODE_MAP = new HashMap<>();

    static {
        for (ContractTypeEnum type : values()) {
            // 映射正式名称
            NAME_TO_CODE_MAP.put(type.name, type.code);
            // 映射枚举名称（小写）
            NAME_TO_CODE_MAP.put(type.name().toLowerCase(), type.code);
            // 映射别名
            for (String alias : type.aliases) {
                NAME_TO_CODE_MAP.put(alias, type.code);
                NAME_TO_CODE_MAP.put(alias.toLowerCase(), type.code);
            }
        }
    }

    /**
     * 根据用户输入获取合同类型代码
     *
     * @param input 用户输入的合同类型名称（如"正签"、"设计"、"认购"等）
     * @return 合同类型代码，未匹配返回 null
     */
    public static Byte getCodeByInput(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.trim().toLowerCase();
        return NAME_TO_CODE_MAP.get(normalized);
    }

    /**
     * 根据代码获取名称
     */
    public static String getNameByCode(Byte code) {
        if (code == null) {
            return null;
        }
        for (ContractTypeEnum type : values()) {
            if (type.code == code) {
                return type.name;
            }
        }
        return null;
    }
}
