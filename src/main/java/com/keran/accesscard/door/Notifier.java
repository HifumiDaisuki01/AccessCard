/*
 * AccessCard - Minecraft 门禁系统
 * Keran Technology (c) 2026  http://tech.keran.cc
 *
 * 本文件为 AccessCard 插件源码的一部分。
 * 版权归 Keran Technology 所有。
 */

package com.keran.accesscard.door;

import com.keran.accesscard.AccessCardPlugin;
import com.keran.accesscard.config.Messages;
import com.keran.accesscard.util.Announce;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 门禁提示分发器。
 * <p>
 * 所有面向玩家的提示都经过这里，统一处理两件事：
 * <ol>
 *   <li><b>前缀</b> —— 先取门自己的 {@code prefix}，没有则用全局默认，
 *       拼在消息最前面，例如 {@code [天穹银行] 金库大门已开启}</li>
 *   <li><b>可见范围</b> —— 先取门对该提示类型的 {@code announce} 配置，
 *       没有则用全局默认，再决定发给谁（自己 / 附近 N 格 / 全服）</li>
 * </ol>
 * 前缀与范围都是可选的，不配置就走全局，不会报错。
 */
public class Notifier {

    private final AccessCardPlugin plugin;

    public Notifier(AccessCardPlugin plugin) {
        this.plugin = plugin;
    }

    /* ================= 提示类型常量 ================= */

    /** 开门成功 */
    public static final String OPENED = "opened";
    /** 密码错误 */
    public static final String WRONG = "wrong";
    /** 密码输入超时 */
    public static final String TIMEOUT = "timeout";
    /** 尝试次数用尽被锁定 */
    public static final String LOCKED = "locked";
    /** 冷却中（个人 / 全局 / 尝试锁定） */
    public static final String COOLDOWN = "cooldown";
    /** 无供电 */
    public static final String NO_POWER = "noPower";
    /** 供电恢复 / 中断 */
    public static final String POWER = "power";

    /** 全部可配置类型，供自检与文档使用 */
    public static final String[] ALL_TYPES = {
            OPENED, WRONG, TIMEOUT, LOCKED, COOLDOWN, NO_POWER, POWER
    };

    /* ================= 发送入口 ================= */

    /**
     * 按门禁配置发送一条提示。
     *
     * @param door    门，可为 null（此时全走全局默认，且不加门前缀）
     * @param actor   触发者，可为 null（第三方代开）
     * @param type    提示类型，见上面的常量
     * @param path    config.yml 中 messages 下的消息键，如 "door.opened"
     * @param pairs   {key} 替换对
     */
    public void send(Door door, Player actor, String type, String path, String... pairs) {
        Messages msg = plugin.getMessages();
        String text = msg.get(path, pairs);
        if (text.isEmpty()) return;
        sendRaw(door, actor, type, text);
    }

    /**
     * 直接发送一段已渲染好的文本（仍会套用前缀与范围）。
     */
    public void sendRaw(Door door, Player actor, String type, String text) {
        if (text == null || text.isEmpty()) return;
        String finalText = withPrefix(door, text);
        Announce.Scope scope = scopeOf(door, type);
        Announce.send(scope, actor, centerOf(door), finalText);
    }

    /**
     * 供供电广播这类「本来是全服」的场景使用：
     * 若门/全局没有单独配置范围，则沿用传入的默认范围。
     */
    public void sendWithDefault(Door door, Player actor, String type,
                                String defaultScope, String path, String... pairs) {
        Messages msg = plugin.getMessages();
        String text = msg.get(path, pairs);
        if (text.isEmpty()) return;
        String finalText = withPrefix(door, text);
        Announce.Scope scope = scopeOfOrDefault(door, type, defaultScope);
        Announce.send(scope, actor, centerOf(door), finalText);
    }

    /* ================= 前缀 ================= */

    /**
     * 拼接前缀。门没配就用全局前缀；两者都为空则原样返回。
     */
    public String withPrefix(Door door, String text) {
        String p = door == null ? "" : door.getPrefix();
        if (p == null || p.isBlank()) {
            p = plugin.getMessages().defaultPrefix();
        }
        if (p == null || p.isBlank()) return text;
        return Messages.color(p) + " " + text;
    }

    /* ================= 可见范围 ================= */

    /**
     * 取得某条提示实际生效的范围写法：
     * 门配置优先，其次是全局默认，最后兜底为「仅开门玩家」。
     */
    public String rawScope(Door door, String type) {
        if (door != null && door.hasAnnounce(type)) {
            return door.getAnnounce(type);
        }
        String g = plugin.getMessages().defaultAnnounce(type);
        if (g == null || g.isBlank()) return Announce.MODE_PLAYER;
        return g;
    }

    public Announce.Scope scopeOf(Door door, String type) {
        return Announce.parse(rawScope(door, type));
    }

    private Announce.Scope scopeOfOrDefault(Door door, String type, String fallback) {
        if (door != null && door.hasAnnounce(type)) {
            return Announce.parse(door.getAnnounce(type));
        }
        String g = plugin.getMessages().defaultAnnounce(type);
        if (g != null && !g.isBlank()) return Announce.parse(g);
        if (fallback != null && !fallback.isBlank()) return Announce.parse(fallback);
        return Announce.parse(Announce.MODE_PLAYER);
    }

    /* ================= 中心点 ================= */

    /**
     * 附近范围的判定中心：门禁按钮所在位置。
     * 门没绑定方块时返回 null，由 Announce 退化为以玩家位置为中心。
     */
    private Location centerOf(Door door) {
        if (door == null) return null;
        Location loc = door.toLocation();
        if (loc == null) return null;
        // 用方块中心而非角点，避免距离判断偏半格
        return loc.add(0.5, 0.5, 0.5);
    }
}
