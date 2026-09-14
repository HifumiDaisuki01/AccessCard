/*
 * AccessCard - Minecraft 门禁系统
 * Keran Technology (c) 2026  http://tech.keran.cc
 *
 * 本文件为 AccessCard 插件源码的一部分。
 * 版权归 Keran Technology 所有。
 */

package com.keran.accesscard.util;

import java.security.SecureRandom;

/**
 * 密码生成与按位读取工具。
 * <p>
 * 面向「密室逃脱」场景：第三方插件需要把密码拆成若干条线索分发给玩家，
 * 因此除了整体密码，还需要能单独读取某一位。
 */
public final class PasswordGen {

    private PasswordGen() {
    }

    private static final SecureRandom RND = new SecureRandom();

    /** 长度下限，防止出现空密码或 1 位密码导致门形同虚设 */
    public static final int MIN_LENGTH = 1;
    /** 长度上限，防止误传超大数字拖垮服务器 */
    public static final int MAX_LENGTH = 32;

    /**
     * 生成指定长度的纯数字随机密码。
     * <p>
     * 每一位独立取 0-9，因此首位可以是 0（例如 "0421"），
     * 这样 4 位密码共有 10000 种组合，读位线索时不会因首位非零而露馅。
     *
     * @param length 密码长度，会被约束到 [1, 32]
     * @return 纯数字字符串
     */
    public static String randomDigits(int length) {
        int len = clampLength(length);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) ('0' + RND.nextInt(10)));
        }
        return sb.toString();
    }

    /**
     * 取密码的第 index 位（从 1 开始计数）。
     *
     * @param password 密码，可为 null
     * @param index    第几位，从 1 开始
     * @return 该位字符；越界或密码为空时返回空字符串
     */
    public static String digitAt(String password, int index) {
        if (password == null || index < 1 || index > password.length()) return "";
        return String.valueOf(password.charAt(index - 1));
    }

    /**
     * 取密码的最后一位。密码为空时返回空字符串。
     */
    public static String lastDigit(String password) {
        return digitAt(password, password == null ? 0 : password.length());
    }

    /** 判断字符串是否全为数字且非空 */
    public static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /** 把长度约束到合法区间 */
    public static int clampLength(int length) {
        if (length < MIN_LENGTH) return MIN_LENGTH;
        return Math.min(length, MAX_LENGTH);
    }
}
