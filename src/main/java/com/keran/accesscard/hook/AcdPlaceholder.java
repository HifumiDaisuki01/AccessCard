/*
 * AccessCard - Minecraft 门禁系统
 * Keran Technology (c) 2026  http://tech.keran.cc
 *
 * 本文件为 AccessCard 插件源码的一部分。
 * 版权归 Keran Technology 所有。
 */

package com.keran.accesscard.hook;

import com.keran.accesscard.AccessCardPlugin;
import com.keran.accesscard.door.Door;
import com.keran.accesscard.door.DoorService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI 扩展。
 * <p>
 * 可用占位符（{door} 换成门的代号）：
 * <pre>
 *   %acd_doors%                 门禁总数
 *   %acd_type_{door}%           门类型：pw / card
 *   %acd_personal_{door}%       当前玩家在该门的个人冷却剩余（秒）
 *   %acd_global_{door}%         该门的全局冷却剩余（秒）
 *   %acd_attempts_{door}%       当前玩家在该门已失败次数
 *   %acd_attemptcooldown_{door}% 尝试次数锁定剩余（秒）
 *   %acd_power_{door}%          供电剩余（秒），-1 表示未启用电源
 *   %acd_ready_{door}%          当前玩家能否开门：true / false
 *   %acd_reason_{door}%         不能开门的原因（ok / personal / global / attempt / power）
 *   %acd_face_{door}%           门类型友好名（密码门 / 门禁卡门）
 * </pre>
 */
public class AcdPlaceholder extends PlaceholderExpansion {

    private final AccessCardPlugin plugin;

    public AcdPlaceholder(AccessCardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "acd";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Keran";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String p = params.toLowerCase();

        if (p.equals("doors")) {
            return String.valueOf(plugin.getDoorManager().size());
        }

        int idx = p.indexOf('_');
        if (idx < 0) return null;
        String key = p.substring(0, idx);
        String doorId = params.substring(idx + 1); // 保留原始大小写，中文门名也能对上

        Door door = plugin.getDoorManager().get(doorId);
        if (door == null) return null;

        switch (key) {
            case "type":
                return door.getType().key();
            case "name":
                return door.getDisplayName();
            case "friendly":
                return door.isCard() ? "门禁卡门" : "密码门";
            case "global":
                return String.valueOf(door.globalCooldownRemaining() / 1000);
            case "power": {
                if (!door.isPowerEnabled()) return "-1";
                return String.valueOf(door.powerRemaining() / 1000);
            }
            default:
                break;
        }

        // 以下占位符需要玩家上下文
        Player online = player == null ? null : player.getPlayer();
        if (online == null) {
            switch (key) {
                case "personal":
                case "attempts":
                case "attemptcooldown":
                    return "0";
                case "ready":
                    return "false";
                case "reason":
                    return "offline";
                default:
                    return null;
            }
        }

        switch (key) {
            case "personal":
                return String.valueOf(
                        plugin.getDoorManager().personalRemaining(online.getUniqueId(), doorId) / 1000);
            case "attempts":
                return String.valueOf(
                        plugin.getDoorManager().getAttempts(online.getUniqueId(), doorId));
            case "attemptcooldown":
                return String.valueOf(
                        plugin.getDoorManager().attemptCooldownRemaining(online.getUniqueId(), doorId) / 1000);
            case "ready":
                return plugin.getDoorService().check(online, door).ok() ? "true" : "false";
            case "reason": {
                DoorService.CheckResult cr = plugin.getDoorService().check(online, door);
                switch (cr.result) {
                    case OK: return "ok";
                    case PERSONAL_COOLDOWN: return "personal";
                    case GLOBAL_COOLDOWN: return "global";
                    case ATTEMPT_COOLDOWN: return "attempt";
                    case NO_POWER: return "power";
                    default: return "failed";
                }
            }
            default:
                return null;
        }
    }
}
