package com.keran.accesscard.command;

import com.keran.accesscard.AccessCardPlugin;
import com.keran.accesscard.door.Door;
import com.keran.accesscard.door.DoorService;
import com.keran.accesscard.listener.CardInteractListener;
import com.keran.accesscard.util.CardNameParser;
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

        /* ---------- 10. 配置完整性 ---------- */
        section(sender, "配置文件");
        expect("config 含 defaults", plugin.getConfig().isConfigurationSection("defaults"), "");
        expect("config 含 messages", plugin.getConfig().isConfigurationSection("messages"), "");
        expect("config 含 help 列表", !plugin.getConfig().getStringList("messages.help").isEmpty(), "");
        expect("门禁已加载", plugin.getDoorManager() != null, "");
        expect("doors.yml 存在", new java.io.File(plugin.getDataFolder(), "doors.yml").exists(), "");

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
