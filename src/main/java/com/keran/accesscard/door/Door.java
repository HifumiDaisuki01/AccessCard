package com.keran.accesscard.door;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 门禁数据模型。
 * <p>
 * 一个门禁 = 一个可交互按钮（世界 + 坐标 + 面朝向）+ 类型（密码 / 门禁卡）+ 一组行为配置。
 */
public class Door {

    /** 门禁类型 */
    public enum Type {
        /** 密码门：玩家在聊天框输入密码 */
        PASSWORD,
        /** 门禁卡门：玩家手持特定名称的物品点击 */
        CARD;

        public static Type parse(String raw) {
            if (raw == null) return PASSWORD;
            String s = raw.trim().toLowerCase();
            if (s.startsWith("card") || s.startsWith("c ") || s.equals("c")) return CARD;
            return PASSWORD;
        }

        public String key() {
            return this == CARD ? "card" : "pw";
        }
    }

    /** 门的唯一代号，作为 doors.yml 的键 */
    private final String id;

    private Type type;
    /** 展示名称，用于提示与广播；默认与 id 相同 */
    private String displayName;

    /** 绑定的按钮位置 */
    private String worldName;
    private int x;
    private int y;
    private int z;
    /** 按钮所在的面，可为 null（不校验面） */
    private BlockFace face;

    /* ---------------- 密码门 ---------------- */
    /** 明文密码 */
    private String password;

    /* ---------------- 门禁卡门 ---------------- */
    /** 卡名必须包含的关键字（大小写不敏感） */
    private String cardKeyword;
    /** 卡物品类型（Material 名），可为空表示不限制类型 */
    private String cardMaterial;

    /* ---------------- 通用行为 ---------------- */
    /** 个人冷却（秒），-1 表示使用全局默认 */
    private long personalCooldown = -1;
    /** 全局冷却（秒），-1 表示使用全局默认 */
    private long globalCooldown = -1;
    /** 允许尝试次数，-1 表示使用全局默认 */
    private int maxAttempts = -1;
    /** 尝试次数耗尽后的个人冷却（秒），-1 表示使用全局默认 */
    private long attemptCooldown = -1;

    /** 是否启用电源系统 */
    private boolean powerEnabled = false;

    /** 开门成功后执行的指令链 */
    private List<String> commands = new ArrayList<>();

    /** 密码错误时的惩罚指令链（可选） */
    private List<String> punishCommands = new ArrayList<>();

    /** 因断电被拒绝时的指令链（可选） */
    private List<String> noPowerCommands = new ArrayList<>();

    /* ---------------- 运行时状态（不写入磁盘） ---------------- */
    /** 全局冷却截止时间戳（毫秒） */
    private long globalCooldownUntil = 0L;
    /** 供电截止时间戳（毫秒），0 表示无供电 */
    private long powerUntil = 0L;

    public Door(String id, Type type) {
        this.id = id;
        this.type = type;
        this.displayName = id;
    }

    /* ================= 便捷判断 ================= */

    public boolean isCard() {
        return type == Type.CARD;
    }

    public boolean hasLocation() {
        return worldName != null;
    }

    public World getWorld() {
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }

    public Location toLocation() {
        World w = getWorld();
        if (w == null) return null;
        return new Location(w, x, y, z);
    }

    public void setLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        this.worldName = loc.getWorld().getName();
        this.x = loc.getBlockX();
        this.y = loc.getBlockY();
        this.z = loc.getBlockZ();
    }

    /**
     * 是否与给定的按钮方块匹配。
     */
    public boolean matchesBlock(String world, int bx, int by, int bz) {
        return worldName != null
                && worldName.equals(world)
                && x == bx && y == by && z == bz;
    }

    /* ================= 冷却 ================= */

    public long globalCooldownRemaining() {
        long left = globalCooldownUntil - System.currentTimeMillis();
        return left > 0 ? left : 0;
    }

    public void applyGlobalCooldown(long seconds) {
        if (seconds <= 0) return;
        this.globalCooldownUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    public void clearGlobalCooldown() {
        this.globalCooldownUntil = 0L;
    }

    public boolean isGlobalCooling() {
        return globalCooldownRemaining() > 0;
    }

    /* ================= 供电 ================= */

    public long powerRemaining() {
        long left = powerUntil - System.currentTimeMillis();
        return left > 0 ? left : 0;
    }

    public boolean hasPower() {
        if (!powerEnabled) return true; // 未启用电源系统的门无视供电
        return powerRemaining() > 0;
    }

    public void applyPower(long seconds) {
        if (seconds <= 0) return;
        this.powerUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    public void clearPower() {
        this.powerUntil = 0L;
    }

    /* ================= 序列化 ================= */

    public static Door fromSection(String id, ConfigurationSection s) {
        Type t = Type.parse(s.getString("type", "pw"));
        Door d = new Door(id, t);
        d.displayName = s.getString("display-name", id);
        d.worldName = s.getString("button.world");
        d.x = s.getInt("button.x");
        d.y = s.getInt("button.y");
        d.z = s.getInt("button.z");
        String f = s.getString("button.face");
        d.face = parseFace(f);

        d.password = s.getString("password", "");
        d.cardKeyword = s.getString("card.keyword", "");
        d.cardMaterial = s.getString("card.material", "");

        d.personalCooldown = s.getLong("cooldown.personal", -1);
        d.globalCooldown = s.getLong("cooldown.global", -1);
        d.maxAttempts = s.getInt("attempts.max", -1);
        d.attemptCooldown = s.getLong("attempts.cooldown", -1);

        d.powerEnabled = s.getBoolean("power.enabled", false);

        d.commands = new ArrayList<>(s.getStringList("commands"));
        d.punishCommands = new ArrayList<>(s.getStringList("punish-commands"));
        d.noPowerCommands = new ArrayList<>(s.getStringList("no-power-commands"));
        return d;
    }

    public void write(ConfigurationSection s) {
        s.set("type", type.key());
        s.set("display-name", displayName);
        s.set("button.world", worldName);
        s.set("button.x", x);
        s.set("button.y", y);
        s.set("button.z", z);
        s.set("button.face", face == null ? null : face.name());

        s.set("password", password);
        s.set("card.keyword", cardKeyword);
        s.set("card.material", cardMaterial);

        s.set("cooldown.personal", personalCooldown);
        s.set("cooldown.global", globalCooldown);
        s.set("attempts.max", maxAttempts);
        s.set("attempts.cooldown", attemptCooldown);

        s.set("power.enabled", powerEnabled);

        s.set("commands", commands);
        s.set("punish-commands", punishCommands);
        s.set("no-power-commands", noPowerCommands);
    }

    private static BlockFace parseFace(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return BlockFace.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /* ================= Getter / Setter ================= */

    public String getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getDisplayName() {
        return displayName == null ? id : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public BlockFace getFace() {
        return face;
    }

    public void setFace(BlockFace face) {
        this.face = face;
    }

    public String getPassword() {
        return password == null ? "" : password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCardKeyword() {
        return cardKeyword == null ? "" : cardKeyword;
    }

    public void setCardKeyword(String cardKeyword) {
        this.cardKeyword = cardKeyword;
    }

    public String getCardMaterial() {
        return cardMaterial == null ? "" : cardMaterial;
    }

    public void setCardMaterial(String cardMaterial) {
        this.cardMaterial = cardMaterial;
    }

    public long getPersonalCooldown() {
        return personalCooldown;
    }

    public void setPersonalCooldown(long personalCooldown) {
        this.personalCooldown = personalCooldown;
    }

    public long getGlobalCooldown() {
        return globalCooldown;
    }

    public void setGlobalCooldown(long globalCooldown) {
        this.globalCooldown = globalCooldown;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getAttemptCooldown() {
        return attemptCooldown;
    }

    public void setAttemptCooldown(long attemptCooldown) {
        this.attemptCooldown = attemptCooldown;
    }

    public boolean isPowerEnabled() {
        return powerEnabled;
    }

    public void setPowerEnabled(boolean powerEnabled) {
        this.powerEnabled = powerEnabled;
    }

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(List<String> commands) {
        this.commands = commands == null ? new ArrayList<>() : commands;
    }

    public List<String> getPunishCommands() {
        return punishCommands;
    }

    public void setPunishCommands(List<String> punishCommands) {
        this.punishCommands = punishCommands == null ? new ArrayList<>() : punishCommands;
    }

    public List<String> getNoPowerCommands() {
        return noPowerCommands;
    }

    public void setNoPowerCommands(List<String> noPowerCommands) {
        this.noPowerCommands = noPowerCommands == null ? new ArrayList<>() : noPowerCommands;
    }
}
