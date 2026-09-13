/*
 * AccessCard - Minecraft 门禁系统
 * Keran Technology (c) 2026  http://tech.keran.cc
 *
 * 本文件为 AccessCard 插件源码的一部分。
 * 版权归 Keran Technology 所有。
 */

package com.keran.accesscard.listener;

import com.keran.accesscard.AccessCardPlugin;
import com.keran.accesscard.door.Door;
import com.keran.accesscard.door.DoorService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 拦截聊天，处理密码门的密码输入。
 * <p>
 * 使用 Paper 的 AsyncChatEvent；同时保留旧事件兼容写法见下方注释说明。
 */
public class ChatInputListener implements Listener {

    private final AccessCardPlugin plugin;

    public ChatInputListener(AccessCardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player player = e.getPlayer();
        AccessCardPlugin.PasswordSession session = plugin.getSession(player);
        if (session == null) return;

        String input = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();

        // 无论成败都先结束本次会话，避免刷屏
        plugin.closeSession(player);

        // 阻止消息发出去（防止密码泄露到公屏）
        if (plugin.getMessages().passwordSilent()) {
            e.setCancelled(true);
        }

        // 取消输入
        if (plugin.getMessages().passwordAllowCancel()
                && (input.equalsIgnoreCase("cancel") || input.equals("取消"))) {
            plugin.getMessages().send(player, "password.cancelled");
            return;
        }

        Door door = plugin.getDoorManager().get(session.doorId);
        if (door == null) {
            plugin.getMessages().send(player, "door.not-found", "{door}", session.doorId);
            return;
        }

        DoorService svc = plugin.getDoorService();

        // 逐个字符比较，避免时序攻击（虽然对这类场景意义有限，但成本极低）
        if (constantTimeEquals(door.getPassword(), input)) {
            // 重新校验一次冷却/供电，防止输入期间状态变化
            DoorService.CheckResult cr = svc.check(player, door);
            if (!cr.ok()) {
                svc.notifyFailed(player, door, cr);
                return;
            }
            svc.onPasswordCorrect(player, door);
        } else {
            svc.onPasswordWrong(player, input, door);
        }
    }

    /**
     * 定长比较，避免因提前返回导致的时序差异。
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] y = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }
}
