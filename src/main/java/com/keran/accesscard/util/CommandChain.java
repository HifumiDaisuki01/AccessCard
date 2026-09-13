package com.keran.accesscard.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 指令链执行器。
 * <p>
 * 支持的行格式（每行一条，按顺序执行）：
 * <pre>
 *   console:setblock 66 66 11 world1 air          - 以控制台身份执行
 *   player:say hi                                 - 以玩家身份执行
 *   tell:你的位置暴露了                            - 私聊给触发玩家
 *   broadcast:{player} 打开了 {door}！             - 全服广播
 *   message:xxx                                   - 同 tell
 *   title:主标题|副标题                            - 发送标题
 *   actionbar:xxx                                 - 发送物品栏上方文字
 *   sound:BLOCK_NOTE_BLOCK_PLING|1|1               - 播放音效
 *   wait:2.5                                      - 单纯等待 2.5 秒
 *   任意其他文本                                   - 等同 console:
 * </pre>
 * 每一行末尾可追加 <code>-2.2s</code> 或 <code>+2.2s</code>，
 * 表示“本条执行完毕后，延迟该秒数再执行下一条”。
 * 不写即立即执行下一条。
 */
public class CommandChain {

    private static final String PREFIX_CONSOLE = "console:";
    private static final String PREFIX_PLAYER = "player:";
    private static final String PREFIX_TELL = "tell:";
    private static final String PREFIX_MESSAGE = "message:";
    private static final String PREFIX_BROADCAST = "broadcast:";
    private static final String PREFIX_TITLE = "title:";
    private static final String PREFIX_ACTIONBAR = "actionbar:";
    private static final String PREFIX_SOUND = "sound:";
    private static final String PREFIX_WAIT = "wait:";

    private final Plugin plugin;

    public CommandChain(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 执行一整条指令链。
     *
     * @param lines  指令行列表
     * @param player 触发玩家，可为 null（则 player:/tell: 会退化）
     * @param ph     占位符键值对，成对传入，如 "{player}", "Steve"
     */
    public void run(List<String> lines, Player player, Map<String, String> ph) {
        if (lines == null || lines.isEmpty()) return;
        List<Step> steps = parse(lines, ph);
        // 用调度器串行推进，保证延迟准确且不阻塞主线程
        executeStep(steps, 0, player);
    }

    private void executeStep(List<Step> steps, int index, Player player) {
        if (index >= steps.size()) return;
        Step step = steps.get(index);
        // 先执行本条动作
        dispatch(step.body, player);
        // 再按本条携带的延迟推进到下一条
        if (step.delayAfterTicks > 0) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> executeStep(steps, index + 1, player), step.delayAfterTicks);
        } else {
            executeStep(steps, index + 1, player);
        }
    }

    private List<Step> parse(List<String> lines, Map<String, String> ph) {
        List<Step> steps = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) continue; // 注释

            Parsed p = extractDelay(line);
            String body = applyPlaceholders(p.body, ph);
            steps.add(new Step(body, p.delayAfterTicks));
        }
        return steps;
    }

    /**
     * 从行尾抽取延迟标记。
     * 形如 "console:xxx -2.2s" / "tell:hi +1.5s" / "wait:3"
     */
    private Parsed extractDelay(String line) {
        Parsed out = new Parsed();
        out.body = line;

        // wait: 是显式等待，本身不执行任何动作
        if (line.toLowerCase().startsWith(PREFIX_WAIT)) {
            String arg = line.substring(PREFIX_WAIT.length()).trim();
            double sec = parseSeconds(arg);
            out.body = "";               // 无动作
            out.delayAfterTicks = toTicks(sec);
            return out;
        }

        // 末尾 "-2.2s" / "+2.2s" / "-2.2 s"
        int lastSpace = line.lastIndexOf(' ');
        if (lastSpace > 0) {
            String tail = line.substring(lastSpace + 1).trim();
            if (tail.length() >= 3 && (tail.startsWith("-") || tail.startsWith("+"))) {
                String numPart = tail.substring(1).trim();
                if (numPart.endsWith("s") || numPart.endsWith("S")) {
                    numPart = numPart.substring(0, numPart.length() - 1).trim();
                }
                Double sec = tryParseDouble(numPart);
                if (sec != null) {
                    out.body = line.substring(0, lastSpace).trim();
                    out.delayAfterTicks = toTicks(sec);
                    return out;
                }
            }
        }
        return out;
    }

    private static double parseSeconds(String s) {
        Double d = tryParseDouble(s.replace("s", "").replace("S", "").trim());
        return d == null ? 0D : d;
    }

    private static Double tryParseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 秒 -> tick，向上取整，最少 1tick（0 视为不延迟） */
    private static int toTicks(double seconds) {
        if (seconds <= 0) return 0;
        return Math.max(1, (int) Math.ceil(seconds * 20.0D));
    }

    private String applyPlaceholders(String s, Map<String, String> ph) {
        if (s == null) return "";
        if (ph == null || ph.isEmpty()) return s;
        String out = s;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            out = out.replace(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /* ================= 实际派发 ================= */

    private void dispatch(String body, Player player) {
        if (body == null || body.isEmpty()) return;
        String lower = body.toLowerCase();

        try {
            if (lower.startsWith(PREFIX_TELL) || lower.startsWith(PREFIX_MESSAGE)) {
                String msg = body.substring(body.indexOf(':') + 1).trim();
                if (player != null && player.isOnline()) {
                    player.sendMessage(com.keran.accesscard.config.Messages.color(msg));
                }
                return;
            }
            if (lower.startsWith(PREFIX_BROADCAST)) {
                String msg = body.substring(PREFIX_BROADCAST.length()).trim();
                Bukkit.broadcastMessage(com.keran.accesscard.config.Messages.color(msg));
                return;
            }
            if (lower.startsWith(PREFIX_TITLE)) {
                String msg = body.substring(PREFIX_TITLE.length()).trim();
                String main = msg;
                String sub = "";
                int bar = msg.indexOf('|');
                if (bar >= 0) {
                    main = msg.substring(0, bar).trim();
                    sub = msg.substring(bar + 1).trim();
                }
                if (player != null && player.isOnline()) {
                    player.sendTitle(
                            com.keran.accesscard.config.Messages.color(main),
                            com.keran.accesscard.config.Messages.color(sub),
                            10, 50, 15);
                }
                return;
            }
            if (lower.startsWith(PREFIX_ACTIONBAR)) {
                String msg = body.substring(PREFIX_ACTIONBAR.length()).trim();
                if (player != null && player.isOnline()) {
                    player.sendActionBar(com.keran.accesscard.config.Messages.color(msg));
                }
                return;
            }
            if (lower.startsWith(PREFIX_SOUND)) {
                String arg = body.substring(PREFIX_SOUND.length()).trim();
                String[] parts = arg.split("\\|");
                if (player != null && player.isOnline() && parts.length >= 1) {
                    String key = parts[0].trim();
                    float vol = parts.length > 1 ? safeFloat(parts[1], 1f) : 1f;
                    float pitch = parts.length > 2 ? safeFloat(parts[2], 1f) : 1f;
                    playSound(player, key, vol, pitch);
                }
                return;
            }
            if (lower.startsWith(PREFIX_PLAYER)) {
                String cmd = body.substring(PREFIX_PLAYER.length()).trim();
                if (player != null && player.isOnline()) {
                    Bukkit.dispatchCommand(player, cmd);
                }
                return;
            }
            // console: 或裸命令
            String cmd = lower.startsWith(PREFIX_CONSOLE)
                    ? body.substring(PREFIX_CONSOLE.length()).trim()
                    : body;
            if (!cmd.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripLeadingSlash(cmd));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("执行指令链出错 [" + body + "]: " + ex.getMessage());
        }
    }

    private static String stripLeadingSlash(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }

    private static float safeFloat(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void playSound(Player player, String key, float vol, float pitch) {
        try {
            org.bukkit.Sound sound = resolveSound(key);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, vol, pitch);
            }
        } catch (Exception ignored) {
            // 音效名不合法，忽略
        }
    }

    /** 兼容 1.20.1 的 Sound 枚举与 1.21+ 的 Sound 注册表 */
    @SuppressWarnings("deprecation")
    private org.bukkit.Sound resolveSound(String key) {
        try {
            java.lang.reflect.Method m = org.bukkit.Sound.class.getMethod("valueOf", String.class);
            Object v = m.invoke(null, key.toUpperCase().replace('.', '_'));
            if (v instanceof org.bukkit.Sound) return (org.bukkit.Sound) v;
        } catch (Exception ignored) {
        }
        return null;
    }

    /* ================= 内部结构 ================= */

    private static class Parsed {
        String body;
        int delayAfterTicks;
    }

    private static class Step {
        final String body;
        final int delayAfterTicks;

        Step(String body, int delayAfterTicks) {
            this.body = body;
            this.delayAfterTicks = delayAfterTicks;
        }
    }
}
