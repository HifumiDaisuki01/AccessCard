package com.keran.accesscard.listener;

import com.keran.accesscard.AccessCardPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家退出时清理会话与缓存状态。
 */
public class PlayerListener implements Listener {

    private final AccessCardPlugin plugin;

    public PlayerListener(AccessCardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.closeSession(e.getPlayer());
        plugin.clearThrottle(e.getPlayer().getUniqueId());
        plugin.getDoorManager().clearPlayer(e.getPlayer().getUniqueId());
    }
}
