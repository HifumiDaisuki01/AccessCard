/*
 * AccessCard - Minecraft 门禁系统
 * Keran Technology (c) 2026  http://tech.keran.cc
 *
 * 本文件为 AccessCard 插件源码的一部分。
 * 版权归 Keran Technology 所有。
 */

package com.keran.accesscard.command;

import com.keran.accesscard.AccessCardPlugin;
import com.keran.accesscard.door.Door;
import com.keran.accesscard.door.DoorManager;
import com.keran.accesscard.door.DoorService;
import com.keran.accesscard.door.Notifier;
import com.keran.accesscard.listener.CardInteractListener;
import com.keran.accesscard.util.PasswordGen;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /acd 主命令。
 */
public class AcdCommand implements CommandExecutor, TabCompleter {

    private final AccessCardPlugin plugin;

    public AcdCommand(AccessCardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
                help(sender);
                return true;

            case "create":
                return cmdCreate(sender, args);

            case "bind":
                return cmdBind(sender, args);

            case "open":
                return cmdOpen(sender, args);

            case "resetpw":
                return cmdResetPw(sender, args);

            case "randompw":
            case "random":
                return cmdRandomPw(sender, args);

            case "power":
                return cmdPower(sender, args);

            case "remove":
            case "delete":
                return cmdRemove(sender, args);

            case "list":
                return cmdList(sender, args);

            case "info":
                return cmdInfo(sender, args);

            case "reload":
                return cmdReload(sender);

            case "selftest":
                return cmdSelfTest(sender);

            case "setpw":
                return cmdSetPw(sender, args);

            default:
                plugin.getMessages().send(sender, "unknown-subcommand", "{input}", args[0]);
                return true;
        }
    }

    /* ================= create ================= */

    private boolean cmdCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accesscard.create")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            plugin.getMessages().send(sender, "usage.create");
            return true;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        if (!type.equals("pw") && !type.equals("card")) {
            plugin.getMessages().send(sender, "create.bad-type", "{type}", args[1]);
            return true;
        }
        String id = args[2];
        if (!isValidId(id)) {
            plugin.getMessages().send(sender, "create.bad-id", "{id}", id);
            return true;
        }
        DoorManager dm = plugin.getDoorManager();
        if (dm.exists(id)) {
            plugin.getMessages().send(sender, "create.exists", "{door}", id);
            return true;
        }

        Door.Type t = type.equals("card") ? Door.Type.CARD : Door.Type.PASSWORD;
        Door door = new Door(id, t);
        door.setDisplayName(id);
        if (t == Door.Type.PASSWORD) {
            String pw = (args.length >= 4) ? args[3] : randomPassword();
            door.setPassword(pw);
            dm.register(door);
            dm.save();
            plugin.getMessages().send(sender, "create.success-pw",
                    "{door}", id, "{pw}", pw);
        } else {
            String keyword = (args.length >= 4) ? args[3] : "";
            door.setCardKeyword(keyword);
            dm.register(door);
            dm.save();
            plugin.getMessages().send(sender, "create.success-card",
                    "{door}", id, "{keyword}", keyword.isEmpty() ? "(未设置)" : keyword);
        }
        plugin.getMessages().send(sender, "create.next-tip", "{door}", id);
        return true;
    }

    /* ================= bind ================= */

    private boolean cmdBind(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accesscard.bind")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "usage.bind");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "player-only");
            return true;
        }
        Door door = plugin.getDoorManager().get(args[1]);
        if (door == null) {
            plugin.getMessages().send(sender, "door-not-found", "{door}", args[1]);
            return true;
        }

        Block target = findTargetButton(player);
        if (target == null) {
            plugin.getMessages().send(sender, "bind.no-target");
            return true;
        }
        if (!CardInteractListener.isButton(target.getType())) {
            plugin.getMessages().send(sender, "bind.not-button",
                    "{block}", target.getType().name());
            return true;
        }

        // 检查是否已被其他门占用
        Door occupied = plugin.getDoorManager().getByBlock(
                target.getWorld().getName(), target.getX(), target.getY(), target.getZ());
        if (occupied != null && !occupied.getId().equals(door.getId())) {
            plugin.getMessages().send(sender, "bind.occupied",
                    "{door}", occupied.getId());
            return true;
        }

        // 先注销旧位置索引
        plugin.getDoorManager().unregister(door.getId());
        door.setLocation(target.getLocation());
        door.setFace(null); // 绑定后不限制面，任何一面点击都生效
        plugin.getDoorManager().register(door);
        plugin.getDoorManager().save();

        plugin.getMessages().send(sender, "bind.success",
                "{door}", door.getDisplayName(),
                "{world}", target.getWorld().getName(),
                "{x}", String.valueOf(target.getX()),
                "{y}", String.valueOf(target.getY()),
                "{z}", String.valueOf(target.getZ()));
        return true;
    }

    /* ================= open ================= */

    private boolean cmdOpen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accesscard.open")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "usage.open");
            return true;
        }
        Door door = plugin.getDoorManager().get(args[1]);
        if (door == null) {
            plugin.getMessages().send(sender, "door-not-found", "{door}", args[1]);
            return true;
        }

        Player target = null;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.getMessages().send(sender, "player-not-found", "{player}", args[2]);
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        }
        // 控制台调用且未指定玩家时，target 保持为 null：
        // 此时仍会执行整条指令链（{player} 替换为空），便于 Quest / 命令方块等
        // 第三方调度在「适当的时间」触发开门效果。

        // 强制开门：跳过冷却/供电校验，直接走成功流程（供第三方插件调度使用）
        plugin.getDoorService().onOpenSuccess(target, door);
        if (target != null) {
            plugin.getMessages().send(sender, "open.forced",
                    "{door}", door.getDisplayName(), "{player}", target.getName());
        } else {
            plugin.getMessages().send(sender, "open.forced-console",
                    "{door}", door.getDisplayName());
        }
        return true;
    }

    /* ================= resetpw / setpw ================= */

    private boolean cmdResetPw(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accesscard.resetpw")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "usage.resetpw");
            return true;
        }
        Door door = plugin.getDoorManager().get(args[1]);
        if (door == null) {
            plugin.getMessages().send(sender, "door-not-found", "{door}", args[1]);
            return true;
        }
        if (door.isCard()) {
            plugin.getMessages().send(sender, "resetpw.not-password", "{door}", args[1]);
            return true;
        }
        String pw = (args.length >= 3) ? args[2] : randomPassword();
        door.setPassword(pw);
        plugin.getDoorManager().save();
        plugin.getMessages().send(sender, "resetpw.success",
                "{door}", door.getDisplayName(), "{pw}", pw);
        return true;
    }

    /* ================= randompw ================= */

    /**
     * {@code /acd randompw <门名> [长度]}
     * <p>
     * 把指定门的密码重置为「指定长度的纯数字随机密码」，专供密室逃脱类玩法使用：
     * 第三方插件可在每局开始时调用，把密码拆成若干位线索分发给玩家，
     * 玩家集齐所有位后拼出完整密码才能开门。
     * <p>
     * 长度不填时默认 4 位。
     */
    private boolean cmdRandomPw(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accesscard.resetpw")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "usage.randompw");
            return true;
        }
        Door door = plugin.getDoorManager().get(args[1]);
        if (door == null) {
            plugin.getMessages().send(sender, "door-not-found", "{door}", args[1]);
            return true;
        }
        if (door.isCard()) {
            plugin.getMessages().send(sender, "resetpw.not-password", "{door}", args[1]);
            return true;
        }

        int length = defaultRandomLength();
        if (args.length >= 3) {
            try {
                length = Integer.parseInt(args[2].trim());
            } catch (NumberFormatException e) {
                plugin.getMessages().send(sender, "randompw.bad-length",
                        "{input}", args[2],
                        "{min}", String.valueOf(PasswordGen.MIN_LENGTH),
                        "{max}", String.valueOf(PasswordGen.MAX_LENGTH));
                return true;
            }
        }

        int clamped = PasswordGen.clampLength(length);
        String pw = PasswordGen.randomDigits(clamped);
        door.setPassword(pw);
        plugin.getDoorManager().save();

        plugin.getMessages().send(sender, "randompw.success",
                "{door}", door.getDisplayName(),
                "{pw}", pw,
                "{length}", String.valueOf(pw.length()));

        // 若传入了越界长度，额外提示一次实际使用的长度
        if (clamped != length) {
            plugin.getMessages().send(sender, "randompw.clamped",
                    "{min}", String.valueOf(PasswordGen.MIN_LENGTH),
                    "{max}", String.valueOf(PasswordGen.MAX_LENGTH),
                    "{length}", String.valueOf(clamped));
        }

        // 广播（可配置，默认关闭；密室场景通常不希望公屏暴露线索）
        broadcastRandomPw(door, pw);
        return true;
    }

    private void broadcastRandomPw(Door door, String pw) {
        if (!plugin.getMessages().randomPwBroadcast()) return;
        String tpl = plugin.getMessages().raw("randompw.broadcast");
        if (tpl == null || tpl.isEmpty()) return;
        String msg = tpl.replace("{door}", door.getDisplayName()).replace("{pw}", pw);
        plugin.getServer().broadcastMessage(com.keran.accesscard.config.Messages.color(msg));
    }

    /** 未指定长度时的默认位数 */
    private int defaultRandomLength() {
        return PasswordGen.clampLength(plugin.getMessages().randomPwDefaultLength());
    }

    private boolean cmdSetPw(CommandSender sender, String[] args) {
        // setpw 与 resetpw 同义，但强制要求提供密码
        if (!sender.hasPermission("accesscard.resetpw")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            plugin.getMessages().send(sender, "usage.setpw");
            return true;
        }
        Door door = plugin.getDoorManager().get(args[1]);
        if (door == null) {
            plugin.getMessages().send(sender, "door-not-found", "{door}", args[1]);
            return true;
        }
        if (door.isCard()) {
            plugin.getMessages().send(sender, "resetpw.not-password", "{door}", args[1]);
            return true;
        }
        door.setPassword(args[2]);
        plugin.getDoorManager().save();
        plugin.getMessages().send(sender, "resetpw.success",
                "{door}", door.getDisplayName(), "{pw}", args[2]);
        return true;
    }

    /* ================= power ================= */

    private boolean cmdPower(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accesscard.power")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            plugin.getMessages().send(sender, "usage.power");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Door door = plugin.getDoorManager().get(args[2]);
        if (door == null) {
            plugin.getMessages().send(sender, "door-not-found", "{door}", args[2]);
            return true;
        }

        switch (action) {
            case "on": {
                long seconds = args.length >= 4 ? parseDuration(args[3]) : 0L;
                if (seconds <= 0) {
                    plugin.getMessages().send(sender, "usage.power");
                    return true;
                }
                // 未启用的门自动启用电源系统，方便运营直接 /acd power on
                door.setPowerEnabled(true);
                door.applyPower(seconds);
                plugin.getDoorManager().save();
                plugin.getMessages().send(sender, "power.on",
                        "{door}", door.getDisplayName(),
                        "{time}", DoorService.formatSeconds(seconds));
                broadcastPower(door, true, seconds);
                return true;
            }
            case "off": {
                door.clearPower();
                plugin.getMessages().send(sender, "power.off",
                        "{door}", door.getDisplayName());
                broadcastPower(door, false, 0);
                return true;
            }
            case "mode": {
                // power mode [门] on/off  —— 切换该门是否启用电源系统
                if (args.length < 4) {
                    plugin.getMessages().send(sender, "usage.power");
                    return true;
                }
                boolean enable = args[3].equalsIgnoreCase("on")
                        || args[3].equalsIgnoreCase("true")
                        || args[3].equals("1");
                door.setPowerEnabled(enable);
                if (!enable) door.clearPower();
                plugin.getDoorManager().save();
                plugin.getMessages().send(sender,
                        enable ? "power.mode-on" : "power.mode-off",
                        "{door}", door.getDisplayName());
                return true;
            }
            default:
                plugin.getMessages().send(sender, "usage.power");
                return true;
        }
    }

    private void broadcastPower(Door door, boolean on, long seconds) {
        // 供电广播默认全服，门可用 announce.power 收窄为「仅附近 / 仅自己」
        plugin.getNotifier().sendWithDefault(
                door, null, Notifier.POWER,
                com.keran.accesscard.util.Announce.MODE_ALL,
                on ? "power.broadcast-on" : "power.broadcast-off",
                "{door}", door.getDisplayName(),
                "{time}", DoorService.formatSeconds(seconds));
    }

    /* ================= remove ================= */

    private boolean cmdRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accesscard.remove")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "usage.remove");
            return true;
        }
        Door door = plugin.getDoorManager().get(args[1]);
        if (door == null) {
            plugin.getMessages().send(sender, "door-not-found", "{door}", args[1]);
            return true;
        }
        plugin.getDoorManager().unregister(args[1]);
        plugin.getDoorManager().save();
        plugin.getMessages().send(sender, "remove.success", "{door}", door.getDisplayName());
        return true;
    }

    /* ================= list ================= */

    private boolean cmdList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accesscard.list")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        if (plugin.getDoorManager().size() == 0) {
            plugin.getMessages().send(sender, "list.empty");
            return true;
        }
        plugin.getMessages().send(sender, "list.header",
                "{count}", String.valueOf(plugin.getDoorManager().size()));
        for (Door d : plugin.getDoorManager().all()) {
            String loc = d.hasLocation()
                    ? d.getWorldName() + " " + d.getX() + "," + d.getY() + "," + d.getZ()
                    : "(未绑定)";
            plugin.getMessages().send(sender, "list.entry",
                    "{door}", d.getId(),
                    "{type}", d.isCard() ? "门禁卡" : "密码",
                    "{loc}", loc);
        }
        return true;
    }

    /* ================= info ================= */

    private boolean cmdInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accesscard.list")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "usage.info");
            return true;
        }
        Door door = plugin.getDoorManager().get(args[1]);
        if (door == null) {
            plugin.getMessages().send(sender, "door-not-found", "{door}", args[1]);
            return true;
        }
        DoorService svc = plugin.getDoorService();
        sender.sendMessage(com.keran.accesscard.config.Messages.color("&8&m                                        "));
        sender.sendMessage(com.keran.accesscard.config.Messages.color("&6门禁 &f" + door.getId()));
        sender.sendMessage(com.keran.accesscard.config.Messages.color("&7类型: &f" + (door.isCard() ? "门禁卡" : "密码")));
        if (!door.isCard()) {
            sender.sendMessage(com.keran.accesscard.config.Messages.color("&7密码: &f" + door.getPassword()));
        } else {
            sender.sendMessage(com.keran.accesscard.config.Messages.color("&7卡关键字: &f" + door.getCardKeyword()));
            sender.sendMessage(com.keran.accesscard.config.Messages.color("&7卡类型: &f"
                    + (door.getCardMaterial().isBlank() ? "(不限)" : door.getCardMaterial())));
        }
        sender.sendMessage(com.keran.accesscard.config.Messages.color("&7位置: &f" + (door.hasLocation()
                ? door.getWorldName() + " " + door.getX() + "," + door.getY() + "," + door.getZ()
                : "(未绑定)")));
        sender.sendMessage(com.keran.accesscard.config.Messages.color("&7个人冷却: &f"
                + durLabel(svc.effectivePersonalCooldown(door))
                + " &7全局冷却: &f" + durLabel(svc.effectiveGlobalCooldown(door))));
        sender.sendMessage(com.keran.accesscard.config.Messages.color("&7尝试次数: &f"
                + svc.effectiveMaxAttempts(door) + " &7锁定: &f"
                + DoorService.formatSeconds(svc.effectiveAttemptCooldown(door))));
        if (door.isPowerEnabled()) {
            long left = door.powerRemaining();
            sender.sendMessage(com.keran.accesscard.config.Messages.color("&7供电: &f"
                    + (left > 0 ? "剩余 " + DoorService.formatSeconds(left / 1000.0) : "&c已断电")));
        } else {
            sender.sendMessage(com.keran.accesscard.config.Messages.color("&7供电: &8未启用"));
        }
        sender.sendMessage(com.keran.accesscard.config.Messages.color("&7全局冷却状态: &f"
                + (door.isGlobalCooling()
                ? DoorService.formatSeconds(door.globalCooldownRemaining() / 1000.0)
                : "就绪")));
        sender.sendMessage(com.keran.accesscard.config.Messages.color("&7指令链: &f"
                + door.getCommands().size() + " 条"));

        // 前缀与提示范围
        String pfx = door.getPrefix().isBlank() ? "&8(用全局)" : "&f" + door.getPrefix();
        sender.sendMessage(com.keran.accesscard.config.Messages.color("&7提示前缀: &r" + pfx));
        StringBuilder ann = new StringBuilder();
        for (String t : Notifier.ALL_TYPES) {
            String eff = plugin.getNotifier().rawScope(door, t);
            boolean own = door.hasAnnounce(t);
            ann.append(own ? "&f" : "&8").append(t).append("=&r").append(eff).append(" ");
        }
        sender.sendMessage(com.keran.accesscard.config.Messages.color("&7提示范围: " + ann));
        return true;
    }

    private static String durLabel(long sec) {
        return sec <= 0 ? "无" : DoorService.formatSeconds(sec);
    }

    /* ================= reload ================= */

    private boolean cmdReload(CommandSender sender) {
        if (!sender.hasPermission("accesscard.reload")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        plugin.reload();
        plugin.getMessages().send(sender, "reload.success",
                "{count}", String.valueOf(plugin.getDoorManager().size()));
        return true;
    }

    /* ================= selftest ================= */

    private boolean cmdSelfTest(CommandSender sender) {
        if (!sender.hasPermission("accesscard.reload")) {
            plugin.getMessages().send(sender, "no-permission");
            return true;
        }
        new SelfTestCommand(plugin).run(sender, new String[0]);
        return true;
    }

    /* ================= 辅助 ================= */

    private void help(CommandSender sender) {
        for (String line : plugin.getMessages().textList("help")) {
            sender.sendMessage(line);
        }
    }

    /** 取玩家视线 6 格内瞄准的方块 */
    private Block findTargetButton(Player player) {
        RayTraceResult r = player.rayTraceBlocks(6);
        if (r == null) return null;
        return r.getHitBlock();
    }

    private static boolean isValidId(String id) {
        if (id == null || id.isEmpty() || id.length() > 32) return false;
        // 允许中文、字母、数字、下划线、短横线
        return id.matches("[\\w\\u4e00-\\u9fa5\\-]+");
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /** 支持 300s / 5m / 1h / 90 等写法，返回秒数 */
    public static long parseDuration(String raw) {
        if (raw == null) return 0;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return 0;
        try {
            if (s.endsWith("ms")) return Long.parseLong(s.substring(0, s.length() - 2)) / 1000;
            if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1));
            if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60;
            if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3600;
            if (s.endsWith("d")) return Long.parseLong(s.substring(0, s.length() - 1)) * 86400;
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            // 形如 1.5m
            try {
                if (s.endsWith("m")) return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 60);
                if (s.endsWith("h")) return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 3600);
            } catch (NumberFormatException ignored) {
            }
            return 0;
        }
    }

    /* ================= Tab 补全 ================= */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            return filter(Arrays.asList("help", "create", "bind", "open", "resetpw", "setpw",
                    "randompw", "power", "remove", "list", "info", "reload", "selftest"), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create":
                if (args.length == 2) return filter(Arrays.asList("pw", "card"), args[1]);
                if (args.length == 4) {
                    return filter(Arrays.asList("<密码>", "<卡名关键字>"), args[3]);
                }
                break;
            case "bind":
            case "resetpw":
            case "setpw":
            case "randompw":
            case "random":
            case "remove":
            case "delete":
            case "info":
                if (args.length == 2) return filter(doorIds(), args[1]);
                if (sub.equals("setpw") && args.length == 3) return List.of("<新密码>");
                if ((sub.equals("randompw") || sub.equals("random")) && args.length == 3) {
                    return filter(Arrays.asList("3", "4", "5", "6"), args[2]);
                }
                break;
            case "open":
                if (args.length == 2) return filter(doorIds(), args[1]);
                if (args.length == 3) return filter(onlineNames(), args[2]);
                break;
            case "power":
                if (args.length == 2) return filter(Arrays.asList("on", "off", "mode"), args[1]);
                if (args.length == 3) return filter(doorIds(), args[2]);
                if (args.length == 4) {
                    if (args[1].equalsIgnoreCase("mode")) {
                        return filter(Arrays.asList("on", "off"), args[3]);
                    }
                    return filter(Arrays.asList("300s", "5m", "10m", "1h", "30m"), args[3]);
                }
                break;
            default:
                break;
        }
        return out;
    }

    private List<String> doorIds() {
        return plugin.getDoorManager().all().stream()
                .map(Door::getId)
                .collect(Collectors.toList());
    }

    private List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p))
                .collect(Collectors.toList());
    }
}
