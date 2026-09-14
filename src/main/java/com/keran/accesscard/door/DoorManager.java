/*
 * AccessCard - Minecraft 门禁系统
 * Keran Technology (c) 2026  http://tech.keran.cc
 *
 * 本文件为 AccessCard 插件源码的一部分。
 * 版权归 Keran Technology 所有。
 */

package com.keran.accesscard.door;

import com.keran.accesscard.AccessCardPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 门禁注册表：负责 door 的增删改查、双索引（id / 坐标）以及持久化。
 */
public class DoorManager {

    private final AccessCardPlugin plugin;

    /** id -> Door */
    private final Map<String, Door> doors = new HashMap<>();
    /** "world:x:y:z" -> Door，用于按钮点击快速反查 */
    private final Map<String, Door> blockIndex = new HashMap<>();
    /** 玩家个人冷却：playerUUID -> (doorId -> 截止时间戳) */
    private final Map<UUID, Map<String, Long>> personalCooldown = new ConcurrentHashMap<>();
    /** 玩家尝试次数：playerUUID -> (doorId -> 已失败次数) */
    private final Map<UUID, Map<String, Integer>> attempts = new ConcurrentHashMap<>();
    /** 玩家尝试冷却：playerUUID -> (doorId -> 截止时间戳) */
    private final Map<UUID, Map<String, Long>> attemptCooldown = new ConcurrentHashMap<>();

    private File file;

    public DoorManager(AccessCardPlugin plugin) {
        this.plugin = plugin;
    }

    /* ================= 加载 / 保存 ================= */

    public void load() {
        doors.clear();
        blockIndex.clear();

        file = new File(plugin.getDataFolder(), "doors.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                if (file.createNewFile()) {
                    writeTemplate();
                }
            } catch (IOException e) {
                plugin.getLogger().warning("无法创建 doors.yml: " + e.getMessage());
            }
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("doors");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                try {
                    Door d = Door.fromSection(id, s);
                    register(d);
                } catch (Exception e) {
                    plugin.getLogger().warning("加载门禁 [" + id + "] 失败: " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info("已加载 " + doors.size() + " 个门禁。");
    }

    /**
     * 首次生成的 doors.yml。
     * <p>
     * 不预先创建任何门，避免玩家「莫名多了几个门」；
     * 只写一份带完整示例的注释头（与 save 共用同一份模板）。
     */
    private void writeTemplate() throws IOException {
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(templateHeader());
        yml.set("doors", new java.util.LinkedHashMap<>());
        yml.save(file);
    }

    public void save() {
        if (file == null) return;
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(templateHeader());
        for (Door d : doors.values()) {
            ConfigurationSection s = yml.createSection("doors." + d.getId());
            d.write(s);
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存 doors.yml 失败: " + e.getMessage());
        }
    }

    /**
     * 文件头注释。首次生成模板与后续保存共用同一份，
     * 这样玩家保存后注释不会消失（但不含示例门，避免误当成真实门）。
     */
    private static java.util.List<String> templateHeader() {
        return java.util.List.of(
                "============================================================",
                " 门禁数据文件",
                "",
                " 门的增删建议用游戏内命令，别手改这个文件以避免格式错误：",
                "   /acd create pw   金库大门         创建密码门",
                "   /acd create card 军械库门 钥匙卡    创建门禁卡门",
                "   /acd bind 金库大门               看向按钮后执行，完成绑定",
                "",
                " 每个门只有下面这些配置项，不写的全部走 config.yml 默认值。",
                " 冷却与供电状态是运行时数据，重启后清空，不写在这里。",
                "",
                " 可省略字段速查：",
                "  type              pw / card",
                "  display-name      提示里显示的名字，不写就用门名",
                "  password          密码（pw 门，明文）",
                "  card.keyword      卡名关键字（card 门）",
                "  card.material     卡物品类型，如 PAPER、TRIPWIRE_HOOK",
                "  prefix            提示前缀，如 \"[天穹银行]\"",
                "  announce          每条提示的可见范围：",
                "                      player     仅开门玩家自己",
                "                      nearby:20  按钮20格内（跨世界自动隔离）",
                "                      all        全服广播",
                "                    可只写 opened / wrong / locked / cooldown /",
                "                    noPower / power 中的某几条，其余走默认",
                "  cooldown.personal / cooldown.global     冷却秒数，-1 = 用默认",
                "  attempts.max / attempts.cooldown        输错上限与锁定秒数，-1 = 用默认",
                "  power.enabled     是否启用供电系统",
                "  commands          开门后执行的指令链，支持 -1.5s 延迟语法",
                "  punish-commands   输错密码时执行的指令链（可选）",
                "  no-power-commands 断电被拒时执行的指令链（可选）",
                "",
                " 完整写法示例（复制到 doors: 下面并去掉 # 即可用）：",
                "",
                "   金库大门:",
                "     type: pw",
                "     password: \"8888\"",
                "     prefix: \"[天穹银行]\"",
                "     announce:",
                "       opened: nearby:20",
                "       wrong: all",
                "     commands:",
                "       - \"tell:门开了\"",
                "       - \"broadcast:[天穹银行] 金库大门已被打开！\"",
                "============================================================"
        );
    }

    /* ================= 注册 / 注销 ================= */

    public void register(Door d) {
        doors.put(d.getId(), d);
        if (d.hasLocation()) {
            blockIndex.put(blockKey(d.getWorldName(), d.getX(), d.getY(), d.getZ()), d);
        }
    }

    public void unregister(String id) {
        Door d = doors.remove(id);
        if (d != null && d.hasLocation()) {
            blockIndex.remove(blockKey(d.getWorldName(), d.getX(), d.getY(), d.getZ()));
        }
        // 清理个人状态
        personalCooldown.values().forEach(m -> m.remove(id));
        attempts.values().forEach(m -> m.remove(id));
        attemptCooldown.values().forEach(m -> m.remove(id));
    }

    private static String blockKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    /* ================= 查询 ================= */

    public Door get(String id) {
        return id == null ? null : doors.get(id);
    }

    public Door getByBlock(String world, int x, int y, int z) {
        return blockIndex.get(blockKey(world, x, y, z));
    }

    public boolean exists(String id) {
        return doors.containsKey(id);
    }

    public Collection<Door> all() {
        return doors.values();
    }

    public int size() {
        return doors.size();
    }

    /* ================= 个人冷却 ================= */

    public long personalRemaining(UUID player, String doorId) {
        Map<String, Long> m = personalCooldown.get(player);
        if (m == null) return 0;
        Long until = m.get(doorId);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            m.remove(doorId);
            return 0;
        }
        return left;
    }

    public void applyPersonalCooldown(UUID player, String doorId, long seconds) {
        if (seconds <= 0) return;
        personalCooldown
                .computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(doorId, System.currentTimeMillis() + seconds * 1000L);
    }

    public void clearPersonalCooldown(UUID player, String doorId) {
        Map<String, Long> m = personalCooldown.get(player);
        if (m != null) m.remove(doorId);
    }

    /* ================= 尝试次数 ================= */

    public int getAttempts(UUID player, String doorId) {
        Map<String, Integer> m = attempts.get(player);
        if (m == null) return 0;
        return m.getOrDefault(doorId, 0);
    }

    public int incrementAttempts(UUID player, String doorId) {
        Map<String, Integer> m = attempts.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        int v = m.getOrDefault(doorId, 0) + 1;
        m.put(doorId, v);
        return v;
    }

    public void resetAttempts(UUID player, String doorId) {
        Map<String, Integer> m = attempts.get(player);
        if (m != null) m.remove(doorId);
    }

    public long attemptCooldownRemaining(UUID player, String doorId) {
        Map<String, Long> m = attemptCooldown.get(player);
        if (m == null) return 0;
        Long until = m.get(doorId);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            // 冷却结束 => 恢复尝试次数
            m.remove(doorId);
            resetAttempts(player, doorId);
            return 0;
        }
        return left;
    }

    public void applyAttemptCooldown(UUID player, String doorId, long seconds) {
        if (seconds <= 0) return;
        attemptCooldown
                .computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(doorId, System.currentTimeMillis() + seconds * 1000L);
    }

    /** 玩家退出时清理，避免内存泄漏 */
    public void clearPlayer(UUID player) {
        personalCooldown.remove(player);
        attempts.remove(player);
        attemptCooldown.remove(player);
    }
}
