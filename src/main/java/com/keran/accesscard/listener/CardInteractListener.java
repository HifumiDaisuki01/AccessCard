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
import com.keran.accesscard.door.DoorManager;
import com.keran.accesscard.door.DoorService;
import com.keran.accesscard.util.CardNameParser;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 监听按钮点击，分发到密码门 / 门禁卡门流程。
 */
public class CardInteractListener implements Listener {

    private final AccessCardPlugin plugin;

    public CardInteractListener(AccessCardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return; // 只处理主手，避免触发两次

        Block block = e.getClickedBlock();
        if (block == null) return;
        if (!isButton(block.getType())) return;

        Player player = e.getPlayer();
        if (!DoorService.canUse(player)) return;
        if (!player.hasPermission("accesscard.use")) {
            plugin.getMessages().send(player, "no-permission");
            return;
        }

        DoorManager dm = plugin.getDoorManager();
        Door door = dm.getByBlock(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ());
        if (door == null) return; // 未绑定的按钮，放行原版行为

        // 校验按钮面（若配置了）
        if (door.getFace() != null && e.getBlockFace() != door.getFace()) {
            return;
        }

        if (plugin.throttled(player)) return;

        // 阻止按钮本身的红石行为（避免重复触发）
        e.setCancelled(true);

        DoorService svc = plugin.getDoorService();

        // 通用校验
        DoorService.CheckResult cr = svc.check(player, door);
        if (!cr.ok()) {
            svc.notifyFailed(player, door, cr);
            return;
        }

        if (door.isCard()) {
            handleCard(player, door, svc);
        } else {
            handlePassword(player, door, svc);
        }
    }

    /* ================= 密码门 ================= */

    private void handlePassword(Player player, Door door, DoorService svc) {
        plugin.getMessages().send(player, "password.prompt", "{door}", door.getDisplayName());
        plugin.openSession(player, door.getId());
    }

    /* ================= 门禁卡门 ================= */

    private void handleCard(Player player, Door door, DoorService svc) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            plugin.getMessages().send(player, "card.no-item");
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            plugin.getMessages().send(player, "card.wrong-item");
            return;
        }

        // 1. 匹配物品类型（若配置）
        String wantType = door.getCardMaterial();
        if (!wantType.isBlank()) {
            Material want;
            try {
                want = Material.valueOf(wantType.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                want = null;
            }
            if (want != null && item.getType() != want) {
                plugin.getMessages().send(player, "card.wrong-item");
                return;
            }
        }

        // 2. 匹配名称关键字（大小写不敏感，忽略颜色代码）
        String keyword = door.getCardKeyword();
        if (!keyword.isBlank()) {
            String plain = CardNameParser.stripColor(meta.getDisplayName());
            if (!plain.toLowerCase().contains(keyword.toLowerCase())) {
                plugin.getMessages().send(player, "card.wrong-item");
                return;
            }
        }

        // 3. 解析剩余次数（不读 lore）
        CardNameParser.Result r = CardNameParser.parse(meta.getDisplayName());
        if (!r.success) {
            plugin.getMessages().send(player, "card.parse-failed");
            plugin.getLogger().warning("门禁卡名称无法解析次数: " + meta.getDisplayName()
                    + " (门=" + door.getId() + ", 玩家=" + player.getName() + ")");
            return;
        }
        if (r.value <= 0) {
            plugin.getMessages().send(player, "card.broken");
            return;
        }

        // 4. 扣次数
        if (plugin.getMessages().cardConsumeEnabled()) {
            String newName = CardNameParser.replaceValue(meta.getDisplayName(), r, r.value - 1);
            if (newName == null) {
                plugin.getMessages().send(player, "card.parse-failed");
                return;
            }
            ItemMeta newMeta = item.getItemMeta();
            if (newMeta != null) {
                newMeta.setDisplayName(newName);
                item.setItemMeta(newMeta);
            }
        }

        // 5. 开门
        svc.onOpenSuccess(player, door);
    }

    /* ================= 工具 ================= */

    public static boolean isButton(Material m) {
        if (m == null) return false;
        String n = m.name();
        return n.endsWith("_BUTTON") || n.equals("STONE_BUTTON")
                || n.equals("LEVER") || n.equals("LIGHT_WEIGHTED_PRESSURE_PLATE")
                || n.equals("HEAVY_WEIGHTED_PRESSURE_PLATE");
    }
}
