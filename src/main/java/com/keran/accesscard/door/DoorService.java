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
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 开门流程服务：所有校验（冷却 / 供电 / 尝试次数）与成功后的动作都收在这里，
 * 保证物理交互和 {@code /acd open} 两条入口行为一致。
 */
public class DoorService {

    public enum Result {
        /** 全部通过，可以开门 */
        OK,
        /** 个人冷却中 */
        PERSONAL_COOLDOWN,
        /** 全局冷却中 */
        GLOBAL_COOLDOWN,
        /** 尝试次数用尽，冷却中 */
        ATTEMPT_COOLDOWN,
        /** 无供电 */
        NO_POWER,
        /** 玩家无权限 */
        NO_PERMISSION,
        /** 其他 */
        FAILED
    }

    public static class CheckResult {
        public final Result result;
        public final long remainingMs;

        public CheckResult(Result result, long remainingMs) {
            this.result = result;
            this.remainingMs = remainingMs;
        }

        public boolean ok() {
            return result == Result.OK;
        }
    }

    private final AccessCardPlugin plugin;

    public DoorService(AccessCardPlugin plugin) {
        this.plugin = plugin;
    }

    /* ================= 通用校验 ================= */

    /**
     * 检查一个玩家当前能否操作这个门（不含密码/卡的正确性）。
     */
    public CheckResult check(Player player, Door door) {
        Messages msg = plugin.getMessages();
        DoorManager dm = plugin.getDoorManager();

        // 供电
        if (door.isPowerEnabled() && !door.hasPower()) {
            return new CheckResult(Result.NO_POWER, 0);
        }

        // 尝试次数冷却
        long ac = dm.attemptCooldownRemaining(player.getUniqueId(), door.getId());
        if (ac > 0) {
            return new CheckResult(Result.ATTEMPT_COOLDOWN, ac);
        }

        // 个人冷却
        long pc = dm.personalRemaining(player.getUniqueId(), door.getId());
        if (pc > 0) {
            return new CheckResult(Result.PERSONAL_COOLDOWN, pc);
        }

        // 全局冷却
        if (door.isGlobalCooling()) {
            return new CheckResult(Result.GLOBAL_COOLDOWN, door.globalCooldownRemaining());
        }

        return new CheckResult(Result.OK, 0);
    }

    /**
     * 把校验失败的结果播报给玩家。
     */
    public void notifyFailed(Player player, Door door, CheckResult cr) {
        Notifier n = plugin.getNotifier();
        switch (cr.result) {
            case PERSONAL_COOLDOWN:
                n.send(door, player, Notifier.COOLDOWN, "cooldown.personal",
                        "{time}", formatSeconds(cr.remainingMs / 1000.0));
                break;
            case GLOBAL_COOLDOWN:
                n.send(door, player, Notifier.COOLDOWN, "cooldown.global",
                        "{time}", formatSeconds(cr.remainingMs / 1000.0));
                break;
            case ATTEMPT_COOLDOWN:
                n.send(door, player, Notifier.COOLDOWN, "attempt.cooldown",
                        "{time}", formatSeconds(cr.remainingMs / 1000.0));
                break;
            case NO_POWER:
                n.send(door, player, Notifier.NO_POWER, "power.no-power");
                runNoPowerCommands(player, door);
                break;
            case NO_PERMISSION:
                plugin.getMessages().send(player, "no-permission");
                break;
            default:
                break;
        }
    }

    /* ================= 开门成功 ================= */

    /**
     * 执行开门成功流程：冷却 + 指令链 + 广播。
     *
     * @param player 触发者，可为 null（第三方插件代开）
     * @param door   门
     */
    public void onOpenSuccess(Player player, Door door) {
        DoorManager dm = plugin.getDoorManager();

        // 应用冷却
        long pc = effectivePersonalCooldown(door);
        long gc = effectiveGlobalCooldown(door);
        if (player != null && pc > 0) {
            dm.applyPersonalCooldown(player.getUniqueId(), door.getId(), pc);
        }
        if (gc > 0) {
            door.applyGlobalCooldown(gc);
        }

        // 重置该玩家在此门的失败次数
        if (player != null) {
            dm.resetAttempts(player.getUniqueId(), door.getId());
        }

        // 执行指令链
        Map<String, String> ph = plugin.placeholders(player, door.getDisplayName());
        plugin.getCommandChain().run(door.getCommands(), player, ph);

        // 开门提示：可见范围由门/全局配置决定，未配置时默认仅触发者可见
        plugin.getNotifier().send(door, player, Notifier.OPENED, "door.opened",
                "{door}", door.getDisplayName());
    }

    /**
     * 密码正确时的处理。
     */
    public void onPasswordCorrect(Player player, Door door) {
        onOpenSuccess(player, door);
    }

    /**
     * 密码错误时的处理：累计次数 -> 触发惩罚/冷却。
     */
    public void onPasswordWrong(Player player, String input, Door door) {
        DoorManager dm = plugin.getDoorManager();

        int max = effectiveMaxAttempts(door);
        int used = dm.incrementAttempts(player.getUniqueId(), door.getId());

        // 惩罚指令链
        Map<String, String> ph = plugin.placeholders(player, door.getDisplayName());
        ph.put("{attempt}", String.valueOf(used));
        ph.put("{input}", input == null ? "" : input);
        plugin.getCommandChain().run(door.getPunishCommands(), player, ph);

        if (used >= max) {
            long cd = effectiveAttemptCooldown(door);
            dm.applyAttemptCooldown(player.getUniqueId(), door.getId(), cd);
            dm.resetAttempts(player.getUniqueId(), door.getId());
            plugin.getNotifier().send(door, player, Notifier.LOCKED, "attempt.locked",
                    "{time}", formatSeconds(cd));
        } else {
            plugin.getNotifier().send(door, player, Notifier.WRONG, "password.wrong",
                    "{left}", String.valueOf(max - used),
                    "{used}", String.valueOf(used),
                    "{max}", String.valueOf(max));
        }
    }

    /* ================= 生效参数 ================= */

    public long effectivePersonalCooldown(Door door) {
        long v = door.getPersonalCooldown();
        return v < 0 ? plugin.getMessages().defaultPersonalCooldown() : v;
    }

    public long effectiveGlobalCooldown(Door door) {
        long v = door.getGlobalCooldown();
        return v < 0 ? plugin.getMessages().defaultGlobalCooldown() : v;
    }

    public int effectiveMaxAttempts(Door door) {
        int v = door.getMaxAttempts();
        return v < 0 ? plugin.getMessages().defaultMaxAttempts() : v;
    }

    public long effectiveAttemptCooldown(Door door) {
        long v = door.getAttemptCooldown();
        return v < 0 ? plugin.getMessages().defaultAttemptCooldown() : v;
    }

    /* ================= 辅助 ================= */

    public void runNoPowerCommands(Player player, Door door) {
        if (door.getNoPowerCommands().isEmpty()) return;
        Map<String, String> ph = plugin.placeholders(player, door.getDisplayName());
        plugin.getCommandChain().run(door.getNoPowerCommands(), player, ph);
    }

    /** 秒数格式化：123 -> "2分3秒" */
    public static String formatSeconds(double seconds) {
        long total = (long) Math.ceil(seconds);
        if (total <= 0) return "0秒";
        if (total < 60) return total + "秒";
        long min = total / 60;
        long sec = total % 60;
        if (sec == 0) return min + "分";
        return min + "分" + sec + "秒";
    }

    public static boolean canUse(Player player) {
        if (player == null) return false;
        GameMode gm = player.getGameMode();
        return gm != GameMode.SPECTATOR;
    }

    /** 供第三方插件调用：按 id 开门 */
    public boolean openById(Player player, Door door) {
        CheckResult cr = check(player, door);
        if (!cr.ok()) {
            notifyFailed(player, door, cr);
            return false;
        }
        onOpenSuccess(player, door);
        return true;
    }

    /** 广播辅助（供指令链使用） */
    public void broadcast(String message) {
        Bukkit.broadcastMessage(Messages.color(message));
    }

    /* ================= 随机密码 API（供第三方插件调用） ================= */

    /**
     * 把门的密码重置为指定位数的随机数字，并返回新密码。
     * <p>
     * 供密室逃脱类第三方插件在每局开始时调用；调用方拿到返回值后
     * 可自行决定把每一位线索分发给谁。
     * 门不存在、是门禁卡门、或长度为非法值时返回 null。
     *
     * @param doorId 门代号
     * @param length 位数（1-32，越界自动约束）
     * @return 新密码，失败返回 null
     */
    public String randomizePassword(String doorId, int length) {
        Door door = plugin.getDoorManager().get(doorId);
        if (door == null || door.isCard()) return null;
        String pw = com.keran.accesscard.util.PasswordGen.randomDigits(
                com.keran.accesscard.util.PasswordGen.clampLength(length));
        door.setPassword(pw);
        plugin.getDoorManager().save();
        return pw;
    }

    /**
     * 读取门密码的第 index 位（从 1 开始），越界返回空串。
     * 供第三方插件在代码里直接取值（不必绕占位符）。
     */
    public String passwordDigit(String doorId, int index) {
        Door door = plugin.getDoorManager().get(doorId);
        if (door == null || door.isCard()) return "";
        return com.keran.accesscard.util.PasswordGen.digitAt(door.getPassword(), index);
    }

    /**
     * 读取门密码的位数；门不存在或为门禁卡门时返回 0。
     */
    public int passwordLength(String doorId) {
        Door door = plugin.getDoorManager().get(doorId);
        if (door == null || door.isCard()) return 0;
        return door.getPassword().length();
    }

    /**
     * 读取门的完整密码；门不存在或为门禁卡门时返回空串。
     */
    public String passwordOf(String doorId) {
        Door door = plugin.getDoorManager().get(doorId);
        if (door == null || door.isCard()) return "";
        return door.getPassword();
    }
}
