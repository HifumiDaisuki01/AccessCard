/*
 * AccessCard - Minecraft 门禁系统
 * Keran Technology (c) 2026  http://tech.keran.cc
 *
 * 本文件为 AccessCard 插件源码的一部分。
 * 版权归 Keran Technology 所有。
 */

package com.keran.accesscard.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 门禁卡名称解析。
 * <p>
 * 卡名通常形如：<br>
 * <code>§7[消耗]§f远航者钥匙卡 §8(剩余次数：10)</code>
 * <p>
 * 由于名称里可能混入颜色代码、Oraxen/ItemsAdder 的自定义字体乱码、
 * 各种装饰性符号，这里采用「多级匹配 + 倒序兜底」的策略：
 * <ol>
 *   <li>优先匹配带引导词的括号形式：剩余次数/使用次数/次数/uses<br>
 *       支持全角（）与半角()，支持中英文冒号</li>
 *   <li>其次匹配任意括号内以数字结尾的形式</li>
 *   <li>兜底：<b>倒着扫描名称，取遇到的第一个连续数字串</b></li>
 * </ol>
 * 命中后只替换那一段数字，其余字符（含 §x 颜色代码与乱码）一律原样保留。
 */
public final class CardNameParser {

    private CardNameParser() {
    }

    /** 第 1 级：带引导词的括号，如 （剩余次数：10） (使用次数: 3) (uses 12) */
    private static final Pattern GUIDED = Pattern.compile(
            "[（(]\\s*(?:剩余|使用|可用|剩余使用)?\\s*(?:次数|数量|使用次数|剩余次数|uses?|left|remain)\\s*[:：\\s]\\s*(\\d+)\\s*[）)]",
            Pattern.CASE_INSENSITIVE);

    /** 第 2 级：任意括号内、以数字结尾，如 （10） (x8) (- 3) */
    private static final Pattern BRACKET_TAIL = Pattern.compile(
            "[（(][^（()）]{0,24}?(\\d+)\\s*[）)]");

    /** 兜底级别用到的数字串 */
    private static final Pattern ANY_DIGITS = Pattern.compile("\\d+");

    /**
     * 解析结果。
     */
    public static class Result {
        /** 是否解析成功 */
        public final boolean success;
        /** 解析出的次数，失败时为 -1 */
        public final int value;
        /** 匹配到的数字在原名中的起止下标，失败时为 -1 */
        public final int start;
        public final int end;
        /** 命中的级别，便于打日志定位 */
        public final String level;

        Result(boolean success, int value, int start, int end, String level) {
            this.success = success;
            this.value = value;
            this.start = start;
            this.end = end;
            this.level = level;
        }

        static Result fail() {
            return new Result(false, -1, -1, -1, "none");
        }
    }

    /**
     * 从显示名中解析剩余次数。
     */
    public static Result parse(String displayName) {
        if (displayName == null || displayName.isEmpty()) return Result.fail();

        // 去掉颜色代码后再分析，但记录下标映射以便回原串替换
        String plain = stripColor(displayName);
        int[] map = buildIndexMap(displayName);

        Result r = tryPattern(GUIDED, plain, map, "guided");
        if (r.success) return r;

        r = tryPattern(BRACKET_TAIL, plain, map, "bracket");
        if (r.success) return r;

        // 兜底：倒着找第一个连续数字
        r = scanBackwards(plain, map);
        if (r.success) return r;

        return Result.fail();
    }

    /**
     * 在纯文本上跑正则，成功则把下标映射回原始字符串。
     */
    private static Result tryPattern(Pattern p, String plain, int[] map, String level) {
        Matcher m = p.matcher(plain);
        if (m.find()) {
            String num = m.group(1);
            int plainStart = m.start(1);
            int plainEnd = m.end(1);
            int[] mapped = mapRange(map, plainStart, plainEnd, plain.length());
            if (mapped == null) return Result.fail();
            Integer v = safeInt(num);
            if (v == null) return Result.fail();
            return new Result(true, v, mapped[0], mapped[1], level);
        }
        return Result.fail();
    }

    /**
     * 兜底策略：倒着扫描，取第一个连续数字串。
     * 这样即使卡名格式完全没规律，只要末尾附近有次数数字就能拿到。
     */
    private static Result scanBackwards(String plain, int[] map) {
        Matcher m = ANY_DIGITS.matcher(plain);
        int lastStart = -1, lastEnd = -1;
        String lastNum = null;
        while (m.find()) {
            lastStart = m.start();
            lastEnd = m.end();
            lastNum = m.group();
        }
        if (lastStart < 0 || lastNum == null) return Result.fail();

        int[] mapped = mapRange(map, lastStart, lastEnd, plain.length());
        if (mapped == null) return Result.fail();
        Integer v = safeInt(lastNum);
        if (v == null) return Result.fail();
        return new Result(true, v, mapped[0], mapped[1], "fallback");
    }

    /**
     * 生成纯文本下标 -> 原始字符串下标的映射。
     * map[i] = 原始串中对应第 i 个可见字符的下标。
     */
    private static int[] buildIndexMap(String original) {
        int[] tmp = new int[original.length() + 1];
        int count = 0;
        int i = 0;
        int n = original.length();
        while (i < n) {
            char c = original.charAt(i);
            if (c == '\u00A7' && i + 1 < n) {
                // 跳过 §x 颜色代码（含 §x§R§R§G§G§B§B 形式）
                char code = Character.toLowerCase(original.charAt(i + 1));
                if (code == 'x' && i + 13 < n) {
                    i += 14;
                } else {
                    i += 2;
                }
                continue;
            }
            tmp[count++] = i;
            i++;
        }
        int[] map = new int[count + 1];
        System.arraycopy(tmp, 0, map, 0, count);
        map[count] = n; // 末尾哨兵
        return map;
    }

    private static int[] mapRange(int[] map, int start, int end, int plainLen) {
        if (start < 0 || start >= map.length || end > map.length) return null;
        int s = map[start];
        // end 是独占下标，映射到"最后一个被包含字符的位置 + 1"
        int e;
        if (end - 1 < map.length && end - 1 >= 0) {
            e = (end - 1 == plainLen) ? map[plainLen] : map[end - 1] + 1;
        } else {
            e = map[map.length - 1];
        }
        if (s < 0 || e <= s) return null;
        return new int[]{s, e};
    }

    /**
     * 去掉 § 颜色/格式代码。保留其它所有字符（含外来字体乱码）。
     */
    public static String stripColor(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\u00A7' && i + 1 < n) {
                char code = Character.toLowerCase(s.charAt(i + 1));
                if (code == 'x' && i + 13 < n) {
                    i += 14;
                } else {
                    i += 2;
                }
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * 把原显示名中的次数替换为新值，其余字符原样保留。
     *
     * @return 替换后的名称；若失败返回 null
     */
    public static String replaceValue(String displayName, Result r, int newValue) {
        if (displayName == null || r == null || !r.success) return null;
        if (r.start < 0 || r.end > displayName.length() || r.end <= r.start) return null;
        return displayName.substring(0, r.start)
                + newValue
                + displayName.substring(r.end);
    }

    /**
     * 递减一次，返回新名称。
     * <p>
     * 按需求：只做数字递减，<b>不重排颜色代码</b>，因为替换时
     * 只动数字那一段，§ 代码永远在原位。
     *
     * @return 新名称；解析失败返回 null
     */
    public static String decrement(String displayName) {
        Result r = parse(displayName);
        if (!r.success) return null;
        int next = Math.max(0, r.value - 1);
        return replaceValue(displayName, r, next);
    }

    private static Integer safeInt(String s) {
        try {
            long v = Long.parseLong(s);
            if (v < 0 || v > Integer.MAX_VALUE) return null;
            return (int) v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
