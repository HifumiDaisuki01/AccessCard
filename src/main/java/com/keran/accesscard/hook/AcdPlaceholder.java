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
import com.keran.accesscard.util.PasswordGen;
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
 *   %acd_doors%                  门禁总数
 *   %acd_type_{door}%            门类型：pw / card
 *   %acd_personal_{door}%        当前玩家在该门的个人冷却剩余（秒）
 *   %acd_global_{door}%          该门的全局冷却剩余（秒）
 *   %acd_attempts_{door}%        当前玩家在该门已失败次数
 *   %acd_attemptcooldown_{door}% 尝试次数锁定剩余（秒）
 *   %acd_power_{door}%           供电剩余（秒），-1 表示未启用电源
 *   %acd_ready_{door}%           当前玩家能否开门：true / false
 *   %acd_reason_{door}%          不能开门的原因（ok/personal/global/attempt/power）
 *   %acd_face_{door}%            门类型友好名（密码门 / 门禁卡门）
 * </pre>
 *
 * <h3>密码位占位符（密室逃脱玩法）</h3>
 * <pre>
 *   %acd_pwd_{door}%             该门的完整密码
 *   %acd_pwd_{door}_{n}%         该门密码的第 n 位（n 从 1 开始）
 *   %acd_pwdlen_{door}%          该门密码的位数
 *   %acd_pwdlast_{door}%         该门密码的最后一位
 * </pre>
 * 例如 4 位密码 {@code 0421}：
 * <ul>
 *   <li>{@code %acd_pwd_金库_1%} → {@code 0}</li>
 *   <li>{@code %acd_pwd_金库_2%} → {@code 4}</li>
 *   <li>{@code %acd_pwdlen_金库%} → {@code 4}</li>
 * </ul>
 * <p>
 * <b>解析规则</b>：{@code _} 后的第一段是键名（pwd / pwdlen / ...），
 * 其后整段是门名；若门名不存在，会尝试「去掉末尾一段数字再匹配」，
 * 这样门名本身含数字（如 {@code vault1}）时 {@code %acd_pwd_vault1_2%} 依然可用。
 * <p>
 * <b>安全开关</b>：config 中 {@code password-placeholder.enabled=false} 可整体关闭；
 * {@code password-placeholder.mask} 设为某个字符后只输出掩码（只露位数不露数字）。
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
        return "Keran Technology";
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
        String rest = params.substring(idx + 1); // 保留原始大小写，中文门名也能对上

        // 密码类占位符：{door} 可以是多段，末段可能是位序
        if (key.equals("pwd") || key.equals("pwdlen") || key.equals("pwdlast")) {
            return handlePassword(key, rest);
        }

        Door door = plugin.getDoorManager().get(rest);
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
                        plugin.getDoorManager().personalRemaining(online.getUniqueId(), rest) / 1000);
            case "attempts":
                return String.valueOf(
                        plugin.getDoorManager().getAttempts(online.getUniqueId(), rest));
            case "attemptcooldown":
                return String.valueOf(
                        plugin.getDoorManager().attemptCooldownRemaining(online.getUniqueId(), rest) / 1000);
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

    /* ================= 密码位占位符 ================= */

    /**
     * 处理 {@code pwd / pwdlen / pwdlast} 三类密码占位符。
     *
     * @param key  pwd / pwdlen / pwdlast
     * @param rest {@code _} 之后的全部内容，例如 {@code 金库_2}
     */
    private String handlePassword(String key, String rest) {
        if (!plugin.getMessages().passwordPlaceholderEnabled()) return "";
        if (rest.isEmpty()) return "";

        // pwdlen / pwdlast：整段就是门名，直接查
        if (key.equals("pwdlen") || key.equals("pwdlast")) {
            Door d = plugin.getDoorManager().get(rest);
            if (d == null || d.isCard()) return "";
            return key.equals("pwdlen")
                    ? String.valueOf(d.getPassword().length())
                    : mask(PasswordGen.lastDigit(d.getPassword()));
        }

        // pwd：先整体当门名查一次，命中则返回完整密码
        Door direct = plugin.getDoorManager().get(rest);
        if (direct != null) {
            return direct.isCard() ? "" : mask(direct.getPassword());
        }

        // 未命中：尝试把末尾的 "_数字" 当作位序切出来
        int cut = rest.lastIndexOf('_');
        while (cut > 0) {
            String doorPart = rest.substring(0, cut);
            String idxPart = rest.substring(cut + 1);
            if (PasswordGen.isNumeric(idxPart) && plugin.getDoorManager().exists(doorPart)) {
                Door d = plugin.getDoorManager().get(doorPart);
                int n;
                try {
                    n = Integer.parseInt(idxPart);
                } catch (NumberFormatException e) {
                    return "";
                }
                return mask(PasswordGen.digitAt(d.getPassword(), n));
            }
            // 继续往前找，支持门名里带下划线的情况
            cut = rest.lastIndexOf('_', cut - 1);
        }
        return "";
    }

    /** 按配置决定是否输出掩码 */
    private String mask(String value) {
        String m = plugin.getMessages().passwordPlaceholderMask();
        if (m == null || m.isEmpty()) return value;
        if (value == null || value.isEmpty()) return "";
        return m;
    }
}
