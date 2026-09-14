/*
 * AccessCard - Minecraft 门禁系统
 * Keran Technology (c) 2026  http://tech.keran.cc
 *
 * 本文件为 AccessCard 插件源码的一部分。
 * 版权归 Keran Technology 所有。
 */

package com.keran.accesscard.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * 提示可见范围。
 * <p>
 * 三个写法，全局默认与每个门都能用：
 * <pre>
 *   player       仅开门玩家可见
 *   nearby:15    门禁按钮 15 格以内的玩家可见（自动限定同一世界）
 *   all          全服可见
 * </pre>
 * 解析失败时退化为 {@link #PLAYER}，保证不会因为写错一个词就静默丢提示。
 */
public final class Announce {

    private Announce() {
    }

    /** 仅开门玩家 */
    public static final String MODE_PLAYER = "player";
    /** 全服 */
    public static final String MODE_ALL = "all";
    /** 附近范围前缀 */
    public static final String MODE_NEARBY = "nearby";

    /** 附近范围上限，防止填个超大数字导致遍历全服 */
    public static final int MAX_RADIUS = 512;

    /** 解析出的范围配置 */
    public static final class Scope {
        /** player / all / nearby */
        public final String mode;
        /** 仅 nearby 有效，单位格 */
        public final int radius;
        /** 原始写法，便于 /acd info 回显 */
        public final String raw;

        Scope(String mode, int radius, String raw) {
            this.mode = mode;
            this.radius = radius;
            this.raw = raw;
        }

        public boolean isAll() {
            return MODE_ALL.equals(mode);
        }

        public boolean isNearby() {
            return MODE_NEARBY.equals(mode);
        }

        @Override
        public String toString() {
            return raw;
        }
    }

    private static final Scope PLAYER = new Scope(MODE_PLAYER, 0, MODE_PLAYER);
    private static final Scope ALL = new Scope(MODE_ALL, 0, MODE_ALL);

    /**
     * 解析范围写法，无法识别时退化为「仅开门玩家」。
     *
     * @param raw 例如 null / "" / "player" / "all" / "nearby:15" / "15"
     */
    public static Scope parse(String raw) {
        if (raw == null || raw.isBlank()) return PLAYER;
        String s = raw.trim().toLowerCase();

        if (s.equals(MODE_ALL) || s.equals("所有人") || s.equals("全服")) return ALL;
        if (s.equals(MODE_PLAYER) || s.equals("玩家") || s.equals("自己")) return PLAYER;

        // nearby:15 / nearby 15 / near:15
        String numPart = null;
        if (s.startsWith(MODE_NEARBY) || s.startsWith("near")) {
            int colon = s.indexOf(':');
            if (colon < 0) colon = s.indexOf(' ');
            if (colon >= 0) {
                numPart = s.substring(colon + 1).trim();
            }
        } else if (s.chars().allMatch(Character::isDigit)) {
            // 裸数字也当作 nearby 半径，写起来更省事
            numPart = s;
        }

        if (numPart != null && !numPart.isEmpty()) {
            try {
                int r = Integer.parseInt(numPart);
                if (r <= 0) return PLAYER;
                return new Scope(MODE_NEARBY, Math.min(r, MAX_RADIUS), raw.trim());
            } catch (NumberFormatException ignored) {
                // 落到下方兜底
            }
        }
        return PLAYER;
    }

    /**
     * 把一条消息发给应该看到的人。
     *
     * @param scope  可见范围
     * @param actor  开门/触发的玩家，可为 null（第三方代开）
     * @param center 判定中心（门禁按钮位置），可为 null
     *              —— 此时 nearby 退化为「以 actor 所在位置为中心」
     * @param message 已经过颜色翻译的最终文本
     * @return 实际收到消息的玩家数
     */
    public static int send(Scope scope, Player actor, Location center, String message) {
        if (message == null || message.isEmpty()) return 0;

        if (scope.isAll()) {
            Bukkit.broadcastMessage(message);
            return Bukkit.getOnlinePlayers().size();
        }

        // 仅开门玩家
        if (!scope.isNearby()) {
            if (actor != null && actor.isOnline()) {
                actor.sendMessage(message);
                return 1;
            }
            // 没有触发者（控制台代开）时退化为全服，避免提示凭空消失
            Bukkit.broadcastMessage(message);
            return Bukkit.getOnlinePlayers().size();
        }

        // 附近范围：以门禁按钮位置为中心
        Location origin = center;
        if (origin == null && actor != null) origin = actor.getLocation();
        if (origin == null) {
            // 没有可用中心点，退化处理
            if (actor != null) {
                actor.sendMessage(message);
                return 1;
            }
            return 0;
        }

        World world = origin.getWorld();
        if (world == null) return 0;

        double rSq = (double) scope.radius * scope.radius;
        int count = 0;
        // 遍历该世界在线玩家，天然实现多世界隔离
        for (Player p : world.getPlayers()) {
            if (!p.isOnline()) continue;
            // 跨世界玩家本就不在 world.getPlayers() 里，这里再判一次更保险
            if (!p.getWorld().equals(world)) continue;
            if (p.getLocation().distanceSquared(origin) <= rSq) {
                p.sendMessage(message);
                count++;
            }
        }

        // 边界情况：开门玩家自己恰好不在范围内（例如门在世界边界外），至少让他看到
        if (actor != null && actor.isOnline() && count == 0) {
            actor.sendMessage(message);
            count = 1;
        }
        return count;
    }

    /**
     * 便捷重载：直接传一组玩家集合（用于需要按玩家列表发送的场景）。
     */
    public static void sendTo(Collection<Player> targets, String message) {
        if (message == null || message.isEmpty()) return;
        for (Player p : targets) {
            if (p != null && p.isOnline()) p.sendMessage(message);
        }
    }
}
