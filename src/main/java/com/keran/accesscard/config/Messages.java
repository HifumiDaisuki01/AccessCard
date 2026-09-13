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
}
