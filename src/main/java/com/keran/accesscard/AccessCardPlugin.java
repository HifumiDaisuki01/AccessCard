package com.keran.accesscard;

import com.keran.accesscard.command.AcdCommand;
import com.keran.accesscard.config.Messages;
import com.keran.accesscard.door.DoorManager;
import com.keran.accesscard.door.DoorService;
import com.keran.accesscard.hook.AcdPlaceholder;
import com.keran.accesscard.listener.CardInteractListener;
import com.keran.accesscard.listener.ChatInputListener;
import com.keran.accesscard.listener.PlayerListener;
import com.keran.accesscard.util.CommandChain;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AccessCard 主类。
 */
public class AccessCardPlugin extends JavaPlugin {

    private DoorManager doorManager;
    private DoorService doorService;
    private Messages messages;
    private CommandChain commandChain;

    /** 正在等待输入密码的玩家：UUID -> 开门会话 */
    private final Map<UUID, PasswordSession> sessions = new ConcurrentHashMap<>();
    /** 交互节流：UUID -> 上次交互时间戳 */
    private final Map<UUID, Long> interactThrottle = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        this.commandChain = new CommandChain(this);
        this.doorManager = new DoorManager(this);
        this.doorManager.load();
        this.doorService = new DoorService(this);

        // 命令
        AcdCommand cmd = new AcdCommand(this);
        if (getCommand("accesscard") != null) {
            getCommand("accesscard").setExecutor(cmd);
            getCommand("accesscard").setTabCompleter(cmd);
        }

        // 监听
        Bukkit.getPluginManager().registerEvents(new CardInteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        // 占位符
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AcdPlaceholder(this).register();
            getLogger().info("已挂钩 PlaceholderAPI。");
        }

        getLogger().info("AccessCard 已启动。");
    }

    @Override
    public void onDisable() {
        if (doorManager != null) doorManager.save();
        sessions.clear();
        getLogger().info("AccessCard 已卸载。");
    }

    /* ================= 重载 ================= */

    public void reload() {
        reloadConfig();
        this.messages = new Messages(this);
        doorManager.load();
        sessions.clear();
    }

    /* ================= 密码会话 ================= */

    /** 打开一个密码输入会话 */
    public void openSession(Player player, String doorId) {
        long timeout = messages.passwordTimeout();
        PasswordSession s = new PasswordSession(doorId, System.currentTimeMillis() + timeout * 1000L);
        sessions.put(player.getUniqueId(), s);
        // 超时自动清理
        if (timeout > 0) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                PasswordSession cur = sessions.get(player.getUniqueId());
                if (cur == s) {
                    sessions.remove(player.getUniqueId());
                    if (player.isOnline()) {
                        messages.send(player, "password.timeout");
                    }
                }
            }, timeout * 20L);
        }
    }

    public PasswordSession getSession(Player player) {
        PasswordSession s = sessions.get(player.getUniqueId());
        if (s == null) return null;
        if (s.expiresAt < System.currentTimeMillis()) {
            sessions.remove(player.getUniqueId());
            return null;
        }
        return s;
    }

    public void closeSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    /** 交互节流：true 表示本次应被拦截（过于频繁） */
    public boolean throttled(Player player) {
        long now = System.currentTimeMillis();
        long interval = messages.interactThrottleMs();
        Long last = interactThrottle.get(player.getUniqueId());
        if (last != null && now - last < interval) return true;
        interactThrottle.put(player.getUniqueId(), now);
        return false;
    }

    public void clearThrottle(UUID uuid) {
        interactThrottle.remove(uuid);
    }

    /* ================= Getter ================= */

    public DoorManager getDoorManager() {
        return doorManager;
    }

    public DoorService getDoorService() {
        return doorService;
    }

    public Messages getMessages() {
        return messages;
    }

    public CommandChain getCommandChain() {
        return commandChain;
    }

    /** 构建通用占位符 */
    public Map<String, String> placeholders(Player player, String doorName) {
        Map<String, String> ph = new HashMap<>();
        ph.put("{player}", player == null ? "" : player.getName());
        ph.put("{door}", doorName == null ? "" : doorName);
        return ph;
    }

    /* ================= 密码会话对象 ================= */

    public static class PasswordSession {
        public final String doorId;
        public final long expiresAt;

        public PasswordSession(String doorId, long expiresAt) {
            this.doorId = doorId;
            this.expiresAt = expiresAt;
        }
    }
}
