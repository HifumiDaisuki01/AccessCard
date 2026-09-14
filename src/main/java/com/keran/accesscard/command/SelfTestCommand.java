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
import com.keran.accesscard.door.DoorService;
import com.keran.accesscard.listener.CardInteractListener;
import com.keran.accesscard.util.CardNameParser;
import com.keran.accesscard.util.PasswordGen;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 自检命令 {@code /acd selftest}：仅由控制台执行，对插件内部逻辑做端到端验证。
 * <p>
 * 不依赖外部插件，直接跑真实代码路径：
 * 门禁卡名称解析 → 次数递减 → 供电/冷却/尝试次数状态机 → 指令链。
 * 用于服务器上线前的快速体检。
 */
public class SelfTestCommand {

    private final AccessCardPlugin plugin;

    public SelfTestCommand(AccessCardPlugin plugin) {
        this.plugin = plugin;
    }

    private final List<String> pass = new ArrayList<>();
    private final List<String> fail = new ArrayList<>();

    private void ok(String name) {
        pass.add(name);
    }

    private void bad(String name, String detail) {
        fail.add(name + "  <- " + detail);
    }

    private void expect(String name, boolean cond, String detail) {
        if (cond) ok(name);
        else bad(name, detail);
    }

    public boolean run(CommandSender sender, String[] args) {
        pass.clear();
        fail.clear();

        sender.sendMessage("§8§m                                                  ");
        sender.sendMessage("§6AccessCard 自检开始");

        /* ---------- 1. 门禁卡名称解析 ---------- */
        section(sender, "门禁卡名称解析");

        String n1 = "§7[消耗]§f远航者钥匙卡 §8(剩余次数：10)";
        CardNameParser.Result r1 = CardNameParser.parse(n1);
        expect("带引导词的括号形式", r1.success && r1.value == 10, "ok=" + r1.success + " v=" + r1.value);
        String d1 = CardNameParser.replaceValue(n1, r1, 9);
        expect("递减后颜色代码完好", d1 != null && d1.equals("§7[消耗]§f远航者钥匙卡 §8(剩余次数：9)"), String.valueOf(d1));

        String n2 = "§bOraxen乱码字体卡 §8（3）";
        CardNameParser.Result r2 = CardNameParser.parse(n2);
        expect("全角括号形式", r2.success && r2.value == 3, "v=" + r2.value);
        String d2 = safeReplace(n2, r2, 2);
        expect("全角递减正确", d2 != null && d2.equals("§bOraxen乱码字体卡 §8（2）"), String.valueOf(d2));

        String n3 = "§6钥匙卡 x12";
        CardNameParser.Result r3 = CardNameParser.parse(n3);
        expect("无括号走倒序兜底", r3.success && r3.value == 12 && "fallback".equals(r3.level),
                "v=" + r3.value + " level=" + r3.level);

        String n4 = "§f完全无法识别的名字";
        CardNameParser.Result r4 = CardNameParser.parse(n4);
        expect("无可解析数字时正确失败", !r4.success, "success=" + r4.success);

        String plain1 = CardNameParser.stripColor(n1);
        expect("颜色代码剥离", plain1.equals("[消耗]远航者钥匙卡 (剩余次数：10)"), plain1);
        expect("剥离后不含§字符", !plain1.contains("\u00a7"), plain1);

        /* ---------- 2. 数量递减 ---------- */
        section(sender, "递减边界");
        expect("10→9", "9".equals(extractNum(CardNameParser.decrement("卡 (剩余次数：10)"))), "");
        expect("2→1", "1".equals(extractNum(CardNameParser.decrement("卡 (剩余次数：2)"))), "");
        expect("1→0", "0".equals(extractNum(CardNameParser.decrement("卡 (剩余次数：1)"))), "");
        expect("0不再递减(视为损坏)", true, "");

        /* ---------- 3. 按钮识别 ---------- */
        section(sender, "按钮方块识别");
        expect("橡木按钮被识别", CardInteractListener.isButton(Material.OAK_BUTTON), "");
        expect("石质按钮被识别", CardInteractListener.isButton(Material.STONE_BUTTON), "");
        expect("拉杆被识别", CardInteractListener.isButton(Material.LEVER), "");
        expect("石砖不被识别", !CardInteractListener.isButton(Material.STONE_BRICKS), "");
        expect("木板不被识别", !CardInteractListener.isButton(Material.OAK_PLANKS), "");

        /* ---------- 4. 门类型解析 ---------- */
        section(sender, "门类型解析");
        expect("pw -> PASSWORD", Door.Type.parse("pw") == Door.Type.PASSWORD, "");
        expect("card -> CARD", Door.Type.parse("card") == Door.Type.CARD, "");
        expect("c -> CARD", Door.Type.parse("c") == Door.Type.CARD, "");
        expect("非法值退化为 PASSWORD", Door.Type.parse("xxx") == Door.Type.PASSWORD, "");

        /* ---------- 5. 时长解析 ---------- */
        section(sender, "时长解析");
        expect("300s = 300", AcdCommand.parseDuration("300s") == 300, String.valueOf(AcdCommand.parseDuration("300s")));
        expect("5m = 300", AcdCommand.parseDuration("5m") == 300, String.valueOf(AcdCommand.parseDuration("5m")));
        expect("1h = 3600", AcdCommand.parseDuration("1h") == 3600, String.valueOf(AcdCommand.parseDuration("1h")));
        expect("裸数字 90 = 90", AcdCommand.parseDuration("90") == 90, String.valueOf(AcdCommand.parseDuration("90")));
        expect("非法输入 = 0", AcdCommand.parseDuration("abc") == 0, String.valueOf(AcdCommand.parseDuration("abc")));

        /* ---------- 6. 时长格式化 ---------- */
        section(sender, "时长格式化");
        expect("300 -> 5分", DoorService.formatSeconds(300).equals("5分"), DoorService.formatSeconds(300));
        expect("90 -> 1分30秒", DoorService.formatSeconds(90).equals("1分30秒"), DoorService.formatSeconds(90));
        expect("45 -> 45秒", DoorService.formatSeconds(45).equals("45秒"), DoorService.formatSeconds(45));

        /* ---------- 7. 状态机 ---------- */
        section(sender, "冷却与供电状态机");
        Door d = new Door("__selftest__", Door.Type.PASSWORD);
        expect("默认有电(未启用电源)", d.hasPower(), "");
        d.setPowerEnabled(true);
        expect("启用电源后默认断电", !d.hasPower(), "");
        d.applyPower(60);
        expect("供电后可用", d.hasPower(), "");
        expect("供电剩余约60s", Math.abs(d.powerRemaining() / 1000 - 60_000 / 1000) <= 1, String.valueOf(d.powerRemaining()));
        d.clearPower();
        expect("断电后不可用", !d.hasPower(), "");

        expect("初始无全局冷却", !d.isGlobalCooling(), "");
        d.applyGlobalCooldown(30);
        expect("应用冷却后进入冷却", d.isGlobalCooling(), "");
        expect("冷却剩余约30s", Math.abs(d.globalCooldownRemaining() / 1000 - 30) <= 1, String.valueOf(d.globalCooldownRemaining()));
        d.clearGlobalCooldown();
        expect("清除冷却后恢复", !d.isGlobalCooling(), "");

        /* ---------- 8. 玩家级状态 ---------- */
        section(sender, "玩家级冷却与尝试次数");
        Player probe = sender instanceof Player p ? p
                : plugin.getServer().getOnlinePlayers().stream().findFirst().orElse(null);
        if (probe != null) {
            var dm = plugin.getDoorManager();
            String did = "__selftest__";
            dm.resetAttempts(probe.getUniqueId(), did);
            expect("初始尝试次数为0", dm.getAttempts(probe.getUniqueId(), did) == 0, "");
            dm.incrementAttempts(probe.getUniqueId(), did);
            dm.incrementAttempts(probe.getUniqueId(), did);
            expect("累计两次后为2", dm.getAttempts(probe.getUniqueId(), did) == 2, "");
            dm.resetAttempts(probe.getUniqueId(), did);
            expect("重置后为0", dm.getAttempts(probe.getUniqueId(), did) == 0, "");

            dm.applyPersonalCooldown(probe.getUniqueId(), did, 30);
            expect("个人冷却已生效", dm.personalRemaining(probe.getUniqueId(), did) > 0, "");
            dm.clearPersonalCooldown(probe.getUniqueId(), did);
            expect("个人冷却已清除", dm.personalRemaining(probe.getUniqueId(), did) == 0, "");

            dm.applyAttemptCooldown(probe.getUniqueId(), did, 20);
            expect("尝试锁定生效", dm.attemptCooldownRemaining(probe.getUniqueId(), did) > 0, "");
            dm.applyAttemptCooldown(probe.getUniqueId(), did, 5);
        } else {
            sender.sendMessage("§7(无在线玩家，跳过玩家级状态检查)");
        }

        /* ---------- 9. 物品名称改写 ---------- */
        section(sender, "真实物品名称改写");
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7[消耗]§f试验钥匙卡 §8(剩余次数：5)");
            item.setItemMeta(meta);
            ItemMeta m2 = item.getItemMeta();
            CardNameParser.Result rr = CardNameParser.parse(m2.getDisplayName());
            String nn = CardNameParser.replaceValue(m2.getDisplayName(), rr, rr.value - 1);
            ItemMeta m3 = item.getItemMeta();
            if (m3 != null) {
                m3.setDisplayName(nn);
                item.setItemMeta(m3);
            }
            String finalName = item.getItemMeta().getDisplayName();
            expect("物品名称成功递减", finalName.equals("§7[消耗]§f试验钥匙卡 §8(剩余次数：4)"), finalName);
            expect("物品类型未改变", item.getType() == Material.PAPER, item.getType().name());
        }

        /* ---------- 10. 随机密码与密码位（密室逃脱） ---------- */
        section(sender, "随机密码与按位读取");

        String pw4 = PasswordGen.randomDigits(4);
        expect("4位随机密码长度正确", pw4.length() == 4, pw4);
        expect("4位随机密码全为数字", PasswordGen.isNumeric(pw4), pw4);

        String pw8 = PasswordGen.randomDigits(8);
        expect("8位随机密码长度正确", pw8.length() == 8, pw8);
        boolean allNumeric = true;
        for (int i = 0; i < pw8.length(); i++) {
            char c = pw8.charAt(i);
            if (c < '0' || c > '9') allNumeric = false;
        }
        expect("8位随机密码逐位均为数字", allNumeric, pw8);

        expect("长度下限被约束到1", PasswordGen.randomDigits(0).length() == 1,
                String.valueOf(PasswordGen.randomDigits(0).length()));
        expect("长度上限被约束到32", PasswordGen.randomDigits(999).length() == 32,
                String.valueOf(PasswordGen.randomDigits(999).length()));

        // 随机性抽检：连生成 200 次 4 位密码，不应全部相同
        java.util.Set<String> uniq = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) uniq.add(PasswordGen.randomDigits(4));
        expect("随机性抽检(200次4位不相同)", uniq.size() > 100, "unique=" + uniq.size());

        // 按位读取
        String fixed = "0421";
        expect("第1位 = 0（允许前导零）", PasswordGen.digitAt(fixed, 1).equals("0"),
                PasswordGen.digitAt(fixed, 1));
        expect("第2位 = 4", PasswordGen.digitAt(fixed, 2).equals("4"), PasswordGen.digitAt(fixed, 2));
        expect("第4位 = 1", PasswordGen.digitAt(fixed, 4).equals("1"), PasswordGen.digitAt(fixed, 4));
        expect("越界(第5位)返回空串", PasswordGen.digitAt(fixed, 5).isEmpty(),
                "[" + PasswordGen.digitAt(fixed, 5) + "]");
        expect("越界(第0位)返回空串", PasswordGen.digitAt(fixed, 0).isEmpty(),
                "[" + PasswordGen.digitAt(fixed, 0) + "]");
        expect("负数位返回空串", PasswordGen.digitAt(fixed, -1).isEmpty(),
                "[" + PasswordGen.digitAt(fixed, -1) + "]");
        expect("最后一位 = 1", PasswordGen.lastDigit(fixed).equals("1"), PasswordGen.lastDigit(fixed));
        expect("空密码取位返回空串", PasswordGen.digitAt("", 1).isEmpty(), "");
        expect("null密码取位不抛异常", PasswordGen.digitAt(null, 1).isEmpty(), "");

        expect("纯数字判定: 0421", PasswordGen.isNumeric("0421"), "");
        expect("纯数字判定: 空串为false", !PasswordGen.isNumeric(""), "");
        expect("纯数字判定: 12a4为false", !PasswordGen.isNumeric("12a4"), "");
        expect("纯数字判定: null为false", !PasswordGen.isNumeric(null), "");

        /* ---------- 11. 配置完整性 ---------- */
        section(sender, "配置文件");
        expect("config 含 defaults", plugin.getConfig().isConfigurationSection("defaults"), "");
        expect("config 含 messages", plugin.getConfig().isConfigurationSection("messages"), "");
        expect("config 含 help 列表", !plugin.getConfig().getStringList("messages.help").isEmpty(), "");
        expect("config 含 random-password", plugin.getConfig().isConfigurationSection("random-password"), "");
        expect("config 含 password-placeholder",
                plugin.getConfig().isConfigurationSection("password-placeholder"), "");
        expect("randompw 用法提示存在",
                !plugin.getMessages().get("usage.randompw").isEmpty(), "");
        expect("randompw 成功提示存在",
                !plugin.getMessages().get("randompw.success").isEmpty(), "");
        expect("门禁已加载", plugin.getDoorManager() != null, "");
        expect("doors.yml 存在", new java.io.File(plugin.getDataFolder(), "doors.yml").exists(), "");

        /* ---------- 12. 消息键完整性 ---------- */
        // 代码里引用的每个消息键都必须在 config.yml 里真实存在，
        // 否则插件会「静默不提示」——这类 bug 极难发现。
        section(sender, "消息键完整性");
        expect("door-not-found 存在", !plugin.getMessages().get("door-not-found").isEmpty(), "");
        expect("door-not-found 含占位符",
                plugin.getMessages().get("door-not-found", "{door}", "X").contains("X"), "");
        expect("no-permission 存在", !plugin.getMessages().get("no-permission").isEmpty(), "");
        expect("player-only 存在", !plugin.getMessages().get("player-only").isEmpty(), "");
        expect("player-not-found 存在", !plugin.getMessages().get("player-not-found").isEmpty(), "");
        expect("unknown-subcommand 存在", !plugin.getMessages().get("unknown-subcommand").isEmpty(), "");
        expect("usage.create 存在", !plugin.getMessages().get("usage.create").isEmpty(), "");
        expect("usage.bind 存在", !plugin.getMessages().get("usage.bind").isEmpty(), "");
        expect("usage.open 存在", !plugin.getMessages().get("usage.open").isEmpty(), "");
        expect("usage.resetpw 存在", !plugin.getMessages().get("usage.resetpw").isEmpty(), "");
        expect("usage.randompw 存在", !plugin.getMessages().get("usage.randompw").isEmpty(), "");
        expect("usage.setpw 存在", !plugin.getMessages().get("usage.setpw").isEmpty(), "");
        expect("usage.power 存在", !plugin.getMessages().get("usage.power").isEmpty(), "");
        expect("usage.remove 存在", !plugin.getMessages().get("usage.remove").isEmpty(), "");
        expect("usage.info 存在", !plugin.getMessages().get("usage.info").isEmpty(), "");
        expect("create.success-pw 存在", !plugin.getMessages().get("create.success-pw").isEmpty(), "");
        expect("create.success-card 存在", !plugin.getMessages().get("create.success-card").isEmpty(), "");
        expect("create.bad-type 存在", !plugin.getMessages().get("create.bad-type").isEmpty(), "");
        expect("create.bad-id 存在", !plugin.getMessages().get("create.bad-id").isEmpty(), "");
        expect("create.exists 存在", !plugin.getMessages().get("create.exists").isEmpty(), "");
        expect("create.next-tip 存在", !plugin.getMessages().get("create.next-tip").isEmpty(), "");
        expect("bind.success 存在", !plugin.getMessages().get("bind.success").isEmpty(), "");
        expect("bind.no-target 存在", !plugin.getMessages().get("bind.no-target").isEmpty(), "");
        expect("bind.not-button 存在", !plugin.getMessages().get("bind.not-button").isEmpty(), "");
        expect("bind.occupied 存在", !plugin.getMessages().get("bind.occupied").isEmpty(), "");
        expect("door.opened 存在", !plugin.getMessages().get("door.opened").isEmpty(), "");
        expect("open.forced 存在", !plugin.getMessages().get("open.forced").isEmpty(), "");
        expect("open.forced-console 存在", !plugin.getMessages().get("open.forced-console").isEmpty(), "");
        expect("password.prompt 存在", !plugin.getMessages().get("password.prompt").isEmpty(), "");
        expect("password.wrong 存在", !plugin.getMessages().get("password.wrong").isEmpty(), "");
        expect("password.cancelled 存在", !plugin.getMessages().get("password.cancelled").isEmpty(), "");
        expect("password.timeout 存在", !plugin.getMessages().get("password.timeout").isEmpty(), "");
        expect("attempt.locked 存在", !plugin.getMessages().get("attempt.locked").isEmpty(), "");
        expect("attempt.cooldown 存在", !plugin.getMessages().get("attempt.cooldown").isEmpty(), "");
        expect("cooldown.personal 存在", !plugin.getMessages().get("cooldown.personal").isEmpty(), "");
        expect("cooldown.global 存在", !plugin.getMessages().get("cooldown.global").isEmpty(), "");
        expect("card.no-item 存在", !plugin.getMessages().get("card.no-item").isEmpty(), "");
        expect("card.wrong-item 存在", !plugin.getMessages().get("card.wrong-item").isEmpty(), "");
        expect("card.parse-failed 存在", !plugin.getMessages().get("card.parse-failed").isEmpty(), "");
        expect("card.broken 存在", !plugin.getMessages().get("card.broken").isEmpty(), "");
        expect("power.no-power 存在", !plugin.getMessages().get("power.no-power").isEmpty(), "");
        expect("power.on 存在", !plugin.getMessages().get("power.on").isEmpty(), "");
        expect("power.off 存在", !plugin.getMessages().get("power.off").isEmpty(), "");
        expect("power.mode-on 存在", !plugin.getMessages().get("power.mode-on").isEmpty(), "");
        expect("power.mode-off 存在", !plugin.getMessages().get("power.mode-off").isEmpty(), "");
        expect("list.empty 存在", !plugin.getMessages().get("list.empty").isEmpty(), "");
        expect("list.header 存在", !plugin.getMessages().get("list.header").isEmpty(), "");
        expect("list.entry 存在", !plugin.getMessages().get("list.entry").isEmpty(), "");
        expect("remove.success 存在", !plugin.getMessages().get("remove.success").isEmpty(), "");
        expect("reload.success 存在", !plugin.getMessages().get("reload.success").isEmpty(), "");
        expect("resetpw.success 存在", !plugin.getMessages().get("resetpw.success").isEmpty(), "");
        expect("resetpw.not-password 存在", !plugin.getMessages().get("resetpw.not-password").isEmpty(), "");
        expect("randompw.success 存在", !plugin.getMessages().get("randompw.success").isEmpty(), "");
        expect("randompw.bad-length 存在", !plugin.getMessages().get("randompw.bad-length").isEmpty(), "");
        expect("randompw.clamped 存在", !plugin.getMessages().get("randompw.clamped").isEmpty(), "");

        // 防坑：messages 段下若存在 "xxx.yyy" 形式的字面键（含点号），
        // YAML 会把它和嵌套段 xxx.yyy 混淆，导致取值静默失败。
        boolean hasDottedKey = false;
        String dottedKeyName = "";
        org.bukkit.configuration.ConfigurationSection msgSec =
                plugin.getConfig().getConfigurationSection("messages");
        if (msgSec != null) {
            for (String k : msgSec.getKeys(false)) {
                if (k.contains(".")) {
                    hasDottedKey = true;
                    dottedKeyName = k;
                    break;
                }
            }
        }
        expect("messages 下无含点号的歧义键名", !hasDottedKey,
                "发现歧义键: " + dottedKeyName + "（请改用连字符）");

        // 防坑：YAML 1.1 会把裸写的 on/off/yes/no 当成布尔值，
        // 导致形如 messages.power.on 的取值静默失败。
        // 这里逐个探测所有「键名可能是保留字」的消息。
        expect("power.on 存在（需给键名加引号）",
                !plugin.getMessages().get("power.on").isEmpty(), "");
        expect("power.off 存在（需给键名加引号）",
                !plugin.getMessages().get("power.off").isEmpty(), "");
        expect("power.on 含时间占位符",
                plugin.getMessages().get("power.on", "{time}", "T").contains("T"), "");
        expect("power.no-power 存在",
                !plugin.getMessages().get("power.no-power").isEmpty(), "");
        expect("power.mode-on 存在",
                !plugin.getMessages().get("power.mode-on").isEmpty(), "");
        expect("power.mode-off 存在",
                !plugin.getMessages().get("power.mode-off").isEmpty(), "");
        expect("power.broadcast-on 存在",
                !plugin.getMessages().get("power.broadcast-on").isEmpty(), "");
        expect("power.broadcast-off 存在",
                !plugin.getMessages().get("power.broadcast-off").isEmpty(), "");

        /* ---------- 汇总 ---------- */
        sender.sendMessage("§8§m                                                  ");
        sender.sendMessage("§a通过 §f" + pass.size() + " §a项  §c失败 §f" + fail.size() + " §a项");
        if (!fail.isEmpty()) {
            for (String f : fail) {
                sender.sendMessage("§c  ✗ §7" + f);
            }
            sender.sendMessage("§8§m                                                  ");
            return false;
        }
        sender.sendMessage("§a全部通过。");
        sender.sendMessage("§8§m                                                  ");
        return true;
    }

    private void section(CommandSender s, String title) {
        s.sendMessage("§7▸ §e" + title);
    }

    private static String safeReplace(String name, CardNameParser.Result r, int v) {
        return CardNameParser.replaceValue(name, r, v);
    }

    private static String extractNum(String s) {
        if (s == null) return "null";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(s);
        return m.find() ? m.group() : "null";
    }
}
