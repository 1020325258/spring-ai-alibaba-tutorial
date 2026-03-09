package com.yycome.sremate.infrastructure.util;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 时间格式化工具类
 * 统一数据库时间字段的展示格式
 */
public final class DateTimeUtil {

    /**
     * 标准时间格式：yyyy-MM-dd HH:mm:ss
     */
    public static final String STANDARD_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 紧凑时间格式：yyyyMMddHHmmss
     */
    public static final String COMPACT_PATTERN = "yyyyMMddHHmmss";

    private static final DateTimeFormatter STANDARD_FORMATTER = DateTimeFormatter.ofPattern(STANDARD_PATTERN);
    private static final DateTimeFormatter COMPACT_FORMATTER = DateTimeFormatter.ofPattern(COMPACT_PATTERN);

    private DateTimeUtil() {
    }

    /**
     * 格式化时间对象为标准格式字符串
     * 支持：Timestamp, Date, LocalDateTime, Long(毫秒时间戳), String
     *
     * @param time 时间对象
     * @return 格式化后的字符串，如 "2024-01-15 10:30:00"；若无法解析则返回 null 或原字符串
     */
    public static String format(Object time) {
        if (time == null) {
            return null;
        }

        // 已经是字符串，尝试解析后重新格式化
        if (time instanceof String str) {
            if (str.isBlank()) {
                return null;
            }
            // 尝试解析各种常见格式
            return formatString(str);
        }

        // Timestamp
        if (time instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(STANDARD_FORMATTER);
        }

        // Date
        if (time instanceof Date date) {
            return new Timestamp(date.getTime()).toLocalDateTime().format(STANDARD_FORMATTER);
        }

        // LocalDateTime
        if (time instanceof LocalDateTime ldt) {
            return ldt.format(STANDARD_FORMATTER);
        }

        // Long 毫秒时间戳
        if (time instanceof Long millis) {
            return new Timestamp(millis).toLocalDateTime().format(STANDARD_FORMATTER);
        }

        // Number 类型（可能是秒或毫秒）
        if (time instanceof Number num) {
            long value = num.longValue();
            // 判断是秒还是毫秒（毫秒值通常大于 1e12）
            if (value > 1_000_000_000_000L) {
                return new Timestamp(value).toLocalDateTime().format(STANDARD_FORMATTER);
            } else {
                // 秒级时间戳
                return new Timestamp(value * 1000).toLocalDateTime().format(STANDARD_FORMATTER);
            }
        }

        // 其他类型，尝试 toString
        return time.toString();
    }

    /**
     * 尝试解析字符串时间并格式化
     */
    private static String formatString(String str) {
        str = str.trim();

        // 已经是标准格式 yyyy-MM-dd HH:mm:ss
        if (str.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            // 去除可能的毫秒部分
            return str.substring(0, 19);
        }

        // 带毫秒格式 yyyy-MM-dd HH:mm:ss.SSS
        if (str.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+")) {
            return str.substring(0, 19);
        }

        // 紧凑格式 yyyyMMddHHmmss
        if (str.matches("\\d{14}")) {
            LocalDateTime ldt = LocalDateTime.parse(str, COMPACT_FORMATTER);
            return ldt.format(STANDARD_FORMATTER);
        }

        // 纯数字（可能是时间戳）
        if (str.matches("\\d+")) {
            try {
                long value = Long.parseLong(str);
                if (value > 1_000_000_000_000L) {
                    return new Timestamp(value).toLocalDateTime().format(STANDARD_FORMATTER);
                } else {
                    return new Timestamp(value * 1000).toLocalDateTime().format(STANDARD_FORMATTER);
                }
            } catch (NumberFormatException e) {
                return str;
            }
        }

        // 无法解析，返回原字符串
        return str;
    }

    /**
     * 安全格式化，失败时返回默认值
     *
     * @param time        时间对象
     * @param defaultValue 默认值
     * @return 格式化后的字符串
     */
    public static String formatOrDefault(Object time, String defaultValue) {
        String result = format(time);
        return result != null ? result : defaultValue;
    }
}
