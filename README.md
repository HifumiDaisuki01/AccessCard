# AccessCard

适用于 **Minecraft 1.20.1 Paper** 的门禁系统插件。

支持**密码门**与**门禁卡门**两类交互，带个人/全局冷却、尝试次数限制、供电系统和延迟指令链。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://papermc.io)
[![Paper](https://img.shields.io/badge/Paper-1.20.1-blue.svg)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 特性

- **两种门禁类型** — 密码门（聊天框输入密码）、门禁卡门（手持特定名称物品刷卡）
- **延迟指令链** — 开门后按顺序执行一大串命令，支持 `-2.2s` 这样的精确延迟
- **冷却系统** — 每个门可独立配置个人冷却与全局冷却
- **尝试次数限制** — 输错 N 次锁定 M 分钟，到期自动清零重获机会
- **供电系统（可选）** — 默认断电不可用，`/acd power on 门 300s` 临时供电
- **门禁卡次数递减** — 自动把卡名里的 `（剩余次数：10）` 减成 `9`，颜色代码与乱码完全不动
- **第三方插件对接** — `/acd open` 命令 + 11 个 PlaceholderAPI 占位符
- **内置自检** — `/acd selftest` 一次性验证 45 项功能
- **零前置依赖** — PlaceholderAPI 与多世界插件均为可选软依赖

---

## 安装

1. 下载 `AccessCard-1.0.0.jar`（或自行编译，见下方）
2. 放入服务端 `plugins/` 目录
3. 重启服务端

首次启动会生成 `plugins/AccessCard/`，内含 `config.yml`（全局配置）与 `doors.yml`（每个门的记录）。

**环境要求**：Paper 1.20.1 + Java 17

---

## 快速上手

```
# 创建密码门，密码 abc123
/acd create pw 东大门 abc123

# 看向要当门禁的按钮，执行绑定
/acd bind 东大门

# 玩家右键按钮 → 聊天框输入 abc123 → 开门
```

门禁卡门：

```
# 卡名必须包含「钥匙卡」三个字
/acd create card 仓库门 钥匙卡
/acd bind 仓库门
```

---

## 命令

| 命令 | 说明 |
|---|---|
| `/acd create pw <名称> [密码]` | 创建密码门，不填密码则随机生成 |
| `/acd create card <名称> [关键字]` | 创建门禁卡门 |
| `/acd bind <名称>` | 看向按钮执行，绑定门禁 |
| `/acd open <名称> [玩家]` | 强制开门，供第三方插件调用 |
| `/acd resetpw <名称> [新密码]` | 重置密码 |
| `/acd setpw <名称> <密码>` | 设置指定密码 |
| `/acd power on <名称> [时长]` | 供电，如 `300s` / `5m` / `1h` |
| `/acd power off <名称>` | 切断供电 |
| `/acd power mode <名称> <on\|off>` | 切换是否启用电源系统 |
| `/acd info <名称>` | 查看门禁详情 |
| `/acd list` | 列出全部门禁 |
| `/acd remove <名称>` | 删除门禁 |
| `/acd reload` | 重载配置 |
| `/acd selftest` | 插件自检（45 项） |

全部子命令支持 Tab 补全。

---

## 延迟指令链

在 `doors.yml` 里为每个门配置 `commands`，**每行一条，按顺序执行**：

```yaml
doors:
  东大门:
    type: pw
    password: abc123
    commands:
      - "console:setblock 66 66 11 world1 air -2.2s"
      - "console:effect give {player} glowing 20 -0.5s"
      - "tell:你的位置暴露了"
      - "broadcast:{player} 打开了 {door}！"
```

### 指令前缀

| 前缀 | 作用 |
|---|---|
| `console:` | 以控制台身份执行 |
| `player:` | 以触发玩家身份执行 |
| `tell:` / `message:` | 私聊触发玩家 |
| `broadcast:` | 全服广播 |
| `title:` | 发送标题（`\|` 分隔主副标题） |
| `actionbar:` | 物品栏上方文字 |
| `sound:` | 播放音效（`\|` 分隔音量与音调） |
| `wait:` | 单纯等待 |
| 无前缀 | 等同 `console:` |

### 延迟语法

在那行末尾追加 **`-Xs`** 或 **`+Xs`**，表示「本条执行完毕后延迟 X 秒再执行下一条」：

```yaml
- "console:say 第一步"          # 立即执行
- "console:say 第二步 -1.5s"    # 执行完后等 1.5 秒
- "console:say 第三步 -0.5s"    # 执行完后等 0.5 秒
- "tell:全部完成"
```

支持小数（`-2.2s`）。不写延迟即立即执行下一条，`-` 与 `+` 含义相同。

### 占位符

| 占位符 | 替换为 |
|---|---|
| `{player}` | 触发玩家名 |
| `{door}` | 门的展示名 |

---

## 冷却

每个门独立配置，`-1` 表示沿用 `config.yml` 默认值：

```yaml
cooldown:
  personal: 30    # 同一个人开过后等 30 秒
  global: 5       # 任何人开过后全服等 5 秒
```

冷却期间交互会提示剩余时间。

---

## 尝试次数

```yaml
attempts:
  max: 5          # 允许输错 5 次
  cooldown: 180   # 第 5 次错误后锁定 180 秒
```

**行为**：同一玩家在同一门累计输错达到 `max` 次 → 个人锁定 `cooldown` 秒 → 锁定期间无法交互该门 → **锁定结束后次数自动清零**，恢复完整的 `max` 次机会。

### 输错惩罚（可选）

不配置则完全不生效：

```yaml
punish-commands:
  - "console:effect give {player} slowness 5 1"
  - "tell:密码错误！"
```

额外可用占位符：`{left}` `{used}` `{max}` `{input}`

---

## 门禁卡

### 检测规则

1. 物品类型匹配（可选，`card.material` 留空则不限）
2. 名称包含关键字（大小写不敏感，自动忽略颜色代码）
3. 解析出的次数 > 0
4. **不检测 lore**

### 次数递减原理

卡名形如 `§7[消耗]§f远航者钥匙卡 §8(剩余次数：10)`。插件采用**多级匹配 + 倒序兜底**提取数字：

| 级别 | 匹配形式 | 例子 |
|---|---|---|
| 1 | 带引导词的括号 | `(剩余次数：10)`、`（使用次数:3）`、`(uses 12)` |
| 2 | 任意括号内以数字结尾 | `（10）`、`(x8)` |
| 3 | **倒序扫描取最后一个连续数字** | `钥匙卡 x12`、`Card - 8` |

命中后**只替换那一段数字**，其余字符（含 `§` 颜色代码、Oraxen/ItemsAdder 自定义字体乱码）完全原样保留。

```
§7[消耗]§f钥匙卡 §8(剩余次数：10)
                    ↓
§7[消耗]§f钥匙卡 §8(剩余次数：9)
```

次数归零时提示「该卡已损坏」。

---

## 供电系统（可选）

```yaml
power:
  enabled: true
```

启用后该门**默认断电不可用**，交互提示需要恢复供电。管理用指令临时供电：

```
/acd power on 东大门 300s
```

300 秒内可正常交互，超时后自动断电。

### 断电提示（可选）

```yaml
no-power-commands:
  - "tell:这里一片漆黑，似乎没有电力。"
  - "sound:BLOCK_BEACON_DEACTIVATE|1|0.5"
```

---

## 第三方插件对接

### 命令方式

```
/acd open 东大门 Steve
```

指定玩家时 `{player}` 替换为该玩家；**控制台调用不指定玩家时依然执行整条指令链**，`{player}` 替换为空。适合 Quest 插件、命令方块、脚本定时触发。

### PlaceholderAPI

| 占位符 | 返回 |
|---|---|
| `%acd_doors%` | 门禁总数 |
| `%acd_type_<门>%` | `pw` / `card` |
| `%acd_name_<门>%` | 展示名 |
| `%acd_friendly_<门>%` | `密码门` / `门禁卡门` |
| `%acd_personal_<门>%` | 个人冷却剩余（秒） |
| `%acd_global_<门>%` | 全局冷却剩余（秒） |
| `%acd_attempts_<门>%` | 已失败次数 |
| `%acd_attemptcooldown_<门>%` | 尝试锁定剩余（秒） |
| `%acd_power_<门>%` | 供电剩余（秒），`-1` 为未启用 |
| `%acd_ready_<门>%` | 能否开门：`true` / `false` |
| `%acd_reason_<门>%` | 不能开门的原因 |

`reason` 返回值：`ok` / `personal` / `global` / `attempt` / `power` / `offline`

中文门名直接写即可，如 `%acd_ready_东大门%`。

---

## 编译

需要 **JDK 17** 与 **Maven**：

```bash
git clone https://github.com/HifumiDaisuki01/AccessCard.git
cd AccessCard
mvn package
# 产物：target/AccessCard-1.0.0.jar
```

### 项目结构

```
src/main/java/com/keran/accesscard/
├── AccessCardPlugin.java          主类，生命周期与会话管理
├── command/
│   ├── AcdCommand.java            /acd 全部子命令与 Tab 补全
│   └── SelfTestCommand.java       45 项自检
├── config/
│   └── Messages.java              提示文本与全局默认值
├── door/
│   ├── Door.java                  门数据模型
│   ├── DoorManager.java           注册表、双索引、持久化
│   └── DoorService.java           校验与开门流程
├── listener/
│   ├── CardInteractListener.java  按钮交互分发
│   ├── ChatInputListener.java     密码输入拦截
│   └── PlayerListener.java        退出清理
├── hook/
│   └── AcdPlaceholder.java        PlaceholderAPI 扩展
└── util/
    ├── CardNameParser.java        卡名次数解析
    └── CommandChain.java          指令链与延迟调度
```

---

## 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| `accesscard.use` | 所有人 | 使用门禁 |
| `accesscard.admin` | OP | 包含下列全部 |
| `accesscard.create` | OP | 创建门禁 |
| `accesscard.bind` | OP | 绑定按钮 |
| `accesscard.remove` | OP | 删除门禁 |
| `accesscard.open` | OP | 远程开门 |
| `accesscard.resetpw` | OP | 重置密码 |
| `accesscard.power` | OP | 控制供电 |
| `accesscard.list` | OP | 查看列表详情 |
| `accesscard.reload` | OP | 重载与自检 |

---

## 常见问题

**Q：门绑错按钮了怎么办？**
`/acd bind <门名>` 重新指向正确按钮，会自动解绑旧位置。

**Q：一个按钮能绑两个门吗？**
不能。绑定时若按钮已被占用会提示。

**Q：密码是明文存的吗？**
是，`doors.yml` 里直接写明文，方便随手修改。

**Q：重启后冷却还在吗？**
不在。冷却与供电属于运行时状态，重启清空。门的配置会持久化。

**Q：卡名解析失败了怎么办？**
插件会提示无法识别并记录完整卡名。兜底逻辑已覆盖绝大多数格式（取最后一个连续数字），仍不匹配可提 Issue 附上卡名。

---

## License

[MIT](LICENSE)
