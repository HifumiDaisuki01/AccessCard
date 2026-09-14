/*
 * AccessCard - Minecraft 门禁系统
 * Keran Technology (c) 2026  http://tech.keran.cc
 *
 * 本文件为 AccessCard 插件源码的一部分。
 * 版权归 Keran Technology 所有。
 */

package com.keran.accesscard.config;

import com.keran.accesscard.AccessCardPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * config.yml 封装。文本中的 & 会被翻译成颜色代码。
 */
public class Messages {

    private final AccessCardPlugin plugin;

    public Messages(AccessCardPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    /** 取一条消息并翻译颜色代码 */
    public String raw(String path) {
        String s = cfg().getString("messages." + path);
        return s == null ? "" : color(s);
    }

    /** 取一条消息，replace("a","b","c","d"...) 成对替换 */
    public String get(String path, String... placeholders) {
        String s = raw(path);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            s = s.replace(placeholders[i], placeholders[i + 1]);
        }
        return s;
    }

    public void send(CommandSender to, String path, String... placeholders) {
        String s = get(path, placeholders);
        if (s.isEmpty()) return;
        to.sendMessage(s);
    }

    /**
     * 取一个字符串列表并逐行翻译颜色代码。
     */
    public java.util.List<String> textList(String path) {
        java.util.List<String> raw = cfg().getStringList("messages." + path);
        java.util.List<String> out = new java.util.ArrayList<>(raw.size());
        for (String s : raw) {
            out.add(color(s));
        }
        return out;
    }

    public static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /* ================= 全局默认值 ================= */

    public long defaultPersonalCooldown() {
        return cfg().getLong("defaults.personal-cooldown", 0L);
    }

    public long defaultGlobalCooldown() {
        return cfg().getLong("defaults.global-cooldown", 0L);
    }

    public int defaultMaxAttempts() {
        return cfg().getInt("defaults.max-attempts", 5);
    }

    public long defaultAttemptCooldown() {
        return cfg().getLong("defaults.attempt-cooldown", 180L);
    }

    /** 密码输入超时（秒），超时后退出开门流程 */
    public long passwordTimeout() {
        return cfg().getLong("password.timeout", 30L);
    }

    /** 密码输入是否屏蔽聊天广播（默认 true，防止密码泄露） */
    public boolean passwordSilent() {
        return cfg().getBoolean("password.silent", true);
    }

    /** 是否允许玩家在密码门前输入 "cancel" / "取消" 退出 */
    public boolean passwordAllowCancel() {
        return cfg().getBoolean("password.allow-cancel", true);
    }

    /** 门禁卡递减：是否启用 */
    public boolean cardConsumeEnabled() {
        return cfg().getBoolean("card.consume", true);
    }

    /** 门禁卡递减时，低于该值视为损坏 */
    public boolean cardPrefersOffhand() {
        return cfg().getBoolean("card.allow-offhand", true);
    }

    /** 交互冷却（毫秒），防止连点 */
    public long interactThrottleMs() {
        return cfg().getLong("anti-spam.interact-throttle-ms", 300L);
    }

    /* ================= 随机密码（密室逃脱） ================= */

    /** /acd randompw 未指定长度时使用的默认位数 */
    public int randomPwDefaultLength() {
        return cfg().getInt("random-password.default-length", 4);
    }

    /** 随机密码生成后是否全服广播（默认关闭，避免密室谜底泄露） */
    public boolean randomPwBroadcast() {
        return cfg().getBoolean("random-password.broadcast", false);
    }

    /* ================= 密码位占位符 ================= */

    /**
     * 是否允许占位符输出密码。
     * <p>
     * 默认 true。之所以允许，是因为密室逃脱玩法需要第三方插件按位分发线索；
     * 若服务器不希望密码可被任意读取，可在 config 中关闭，
     * 关闭后所有 {@code %acd_pwd_*%} 与 {@code %acd_pwdlen_*%} 一律返回空串。
     */
    public boolean passwordPlaceholderEnabled() {
        return cfg().getBoolean("password-placeholder.enabled", true);
    }

    /**
     * 占位符输出的掩码字符。为空表示输出真实数字。
     * <p>
     * 用于「只告诉玩家位数、不告诉具体数字」的场景，
     * 例如填入 {@code -} 后 {@code %acd_pwd_金库_1%} 只会显示 {@code -}。
     */
    public String passwordPlaceholderMask() {
        return cfg().getString("password-placeholder.mask", "");
    }

    /* ================= 前缀与提示范围 ================= */

    /**
     * 全局默认前缀，例如 {@code "[门禁]"}。
     * 门没有单独配 prefix 时用这个；为空则不加前缀。
     */
    public String defaultPrefix() {
        return cfg().getString("announce.prefix", "");
    }

    /**
     * 某条提示的全局默认可见范围写法。
     * 形如 {@code announce.default.opened: "nearby:15"}。
     * 未配置时返回空串，由调用方兜底为「仅开门玩家」。
     */
    public String defaultAnnounce(String type) {
        if (type == null || type.isEmpty()) return "";
        return cfg().getString("announce.default." + type, "");
    }
}
