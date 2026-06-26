# 棺材睡觉 · 设计说明（as-built）/ Coffin sleep — design (as-built)

> 本文件记录棺材"睡觉 + 跳过白天 + 天敌防扰"的**已实现**设计，与代码同源。完整"抄作业"教程见
> `~/棺材睡觉-设计与实现指南.md`。MC 26.2 / NeoForge。

## 涉及文件 / Files
- `block/CoffinBlock.java` — 方块 + 右键判定（`useWithoutItem`、`hasHostileNearby`、`canRestToNight`、`setCoffinRespawn`）。
- `gameplay/CoffinNapManager.java` — "真睡眠"驱动器（`PlayerTickEvent.Post`）：沉睡计时、多人投票、被打扰惊醒、推进到夜晚、清理。
- `rules/ZombieSleepRules.java` — 纯判定 `useCoffin(...)` + 投票数学 `coffinSleepersNeeded/enoughCoffinSleepers`。
- `rules/SleepAction.java` — 动作枚举。
- 复用（未改）：`rules/ZombieMobTargetingRules.java`（天敌矩阵）、`state/IAmZombieAttachments.PLAYER_ZOMBIE`（玩家形态）。
- 注册：`IAmZombieMod` → `NeoForge.EVENT_BUS.register(CoffinNapManager.class)`。
- 文案：`assets/iamzombieq/lang/{en_us,zh_cn}.json` 的 `iamzombieq.message.coffin.*`。

## 1. 睡觉 = 忠于原版床的"真睡眠"，但跳到夜晚（不是早晨）
僵尸昼伏夜出：白天躺进棺材 → 真正进入睡眠（睡姿 + 渐黑）→ 多人按 `players_sleeping_percentage` 投票 → 时间推进到
**夜晚** → 一起醒来。与原版床（夜→晨）镜像。

- 用底层 `LivingEntity.startSleeping(pos)`，**不是** `Player.startSleepInBed`（后者套主世界 `WHEN_DARK` 床规则，白天会拒绝）。
- 时间推进：`EventHooks.onSleepFinished(level, new ClockAdjustment.Marker(ClockTimeMarkers.NIGHT))` → `apply(level.clockManager(), defaultClock)`；尊重 `ADVANCE_TIME`，被取消（返回 null）则不改时间；下雨且 `ADVANCE_WEATHER` 则 `resetWeatherCycle()`。**不蹭**原版硬编码"睡到早晨"的循环。
- 白天边界：`Math.floorMod(getDefaultClockTime(),24000) < 12000` 才能跳；夜晚 / 无 `defaultClock`（末地等）→ 只设重生点。
- 驱动器在打扰时唤醒，避免卡死：受伤、棺材被破坏、外部唤醒、等待超时（`isSleepingLongEnough` 后再等 `MAX_WAIT_TICKS`）、登出/服务器停止清理 `NAPS`。
- 「是僵尸」用全模组约定 `!player.isSpectator()`（`ZombieForm.NORMAL` 是**普通僵尸**形态，**不可**用 `form != NORMAL` 判定）。

## 2. 防扰 = 天敌矩阵（僵尸不怕同类，只怕天敌）
关键修正：原本 `hasHostileNearby` 用原版 `Monster.isPreventingPlayerRest()`，把**同类**僵尸/骷髅/苦力怕当威胁——
对"玩家是僵尸"是反的。现改为：**只有"会主动攻击僵尸玩家"的生物在场，才不能睡**，即
`我的世界_亡灵四生物关系_完整版.md` 的 **① 主动攻击** 类，按玩家形态判定，复用 `ZombieMobTargetingRules`（与索敌同一套）。

```java
ZombieForm form = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().form();
for (Mob mob : level.getEntitiesOfClass(Mob.class, area /* 8×5×8 */)) {
    var kind = ZombieMobTargetingRules.classify(mob);
    if (kind != ZombieMobTargetingRules.MobKind.BOSS
            && ZombieMobTargetingRules.attacksZombiePlayer(kind, form)) return true;
}
return false;
```

按形态的天敌（① 主动攻击）：

| 形态 | 在旁边就不能睡 |
|---|---|
| 僵尸 NORMAL / 尸壳 HUSK | 铁傀儡、雪傀儡、疣猪兽、山羊、苦力怕、行商羊驼 |
| 溺尸 DROWNED | + 美西螈 |
| 僵尸猪灵 ZOMBIFIED_PIGLIN | 铁傀儡、雪傀儡、疣猪兽、山羊、苦力怕（行商羊驼不打猪灵 → 不算） |
| 巨人 GIANT | 同僵尸行（矩阵按形态、不按体型） |

设计取舍（为什么）：
- **Boss 排除**（`kind != BOSS`）：监守者是 ④ 有条件（被振动激怒才追）、凋灵不攻击亡灵——都不是 ① 主动攻击，故不因在场拦睡；真被监守者打到由"受伤惊醒"兜底。
- **其他玩家不算**：扫描 `Mob`，`Player` 不是 `Mob`；本模组别的玩家也是僵尸（同伴）。
- **苦力怕保留**：它主动冲向"玩家"实体爆炸，对僵尸玩家是真威胁（完整版那张表是"对僵尸**实体**"；按玩家场景保留）。
- **条件型 ④（驯服狼等）不算**：`classify` 归 `IGNORED`，只在被主人指使时打，受伤兜底。
- **不再需要友好坐骑豁免**：自己的大僵尸坐骑/驯服蜘蛛本就 `IGNORED`，矩阵天然不拦（旧 `isFriendlyMount` 已删）。
- **入睡 + 中途一致**：入睡前查一次；睡着后驱动器每 ~20 tick 用**同一** `hasHostileNearby` 复查，天敌中途靠近也会弄醒。

## 3. 文案 / Messages
- `coffin.not_safe`（有天敌时）：zh「周围有天敌徘徊，你无法安睡。」/ en "Natural enemies are prowling nearby. You can't rest now."（**不再**写"怪物/monsters"——僵尸自己就是怪物）。
- `coffin.disturbed`（中途惊醒，通用：受伤/破坏/天敌靠近）、`coffin.players_sleeping`（X/Y）、`coffin.rested`、`coffin.respawn_set_only`、`coffin.lying_down`、`coffin.not_enough`、`coffin.zombie_only`。

## 4. 验证 / Verification

### 4.1 自动化（已通过 / done）
- `./gradlew test`：`ZombieSleepRulesTest`、`CoffinBlockSourceTest`（断言天敌矩阵：`attacksZombiePlayer`/`Mob.class`/`PLAYER_ZOMBIE`/`MobKind.BOSS`、`assertFalse isPreventingPlayerRest`）、`CoffinNapManagerSourceTest`（含中途 `hasHostileNearby` 复查）、`ZombieMobTargetingRulesTest`。
- `./gradlew build` 绿。
- `./gradlew runServer` 冒烟：服务器到 `Done`、模组加载、`IAmZombieMod` 构造（注册 `CoffinNapManager`）、无异常。
- 注：`runClient` 需要可用的 GL 显示；在无头/沙箱里会因 `GLFW … X11: Failed to load Xlib` 起不来（环境限制，非代码问题）。请在带桌面的机器上做下面的 in-game 验收。

### 4.2 In-game 验收清单（在带桌面的机器跑 `./gradlew runClient`）
睡觉 / 跳过白天：
1. 白天、僵尸形态、放下棺材 → 右键：屏幕渐黑、睡几秒 → 跳到夜晚、醒来、重生点已设（死一次验证回到棺材旁）。
2. 夜晚右键 → 只提示"重生点设好了"，不跳时间、不进入睡眠。
3. 下界/末地右键 → 只设重生点（无昼夜循环）。

天敌防扰（本次重点）：
4. 棺材旁 ~8 格放 铁傀儡/山羊/苦力怕 → 右键被拒，动作栏："周围有天敌徘徊，你无法安睡。"
5. 旁边放 同类僵尸/骷髅 → 能正常睡（不拦）——"不怕同类"。
6. 旁边放 监守者或凋灵 → 能睡（Boss 不按在场拦）；被激怒的监守者打到你 → 受伤惊醒。
7. 形态相关：溺尸形态 + 美西螈在旁 → 拦；普通僵尸形态 + 美西螈在旁 → 不拦。
8. 睡到一半让 苦力怕/山羊 走近（或 summon）→ 被惊醒（"有什么惊扰了你的长眠！"）。

多人（可选）：
9. 两个僵尸玩家：默认 100% 时需都躺满约 5 秒才跳；动作栏显示 `X/Y`；都睡够 → 一起醒、跳夜晚。

命令小抄：`/summon iron_golem ~ ~ ~2`、`/summon creeper`、`/summon axolotl`、`/time set day`、`/gamerule playersSleepingPercentage 100`；变形态可用本模组进化机制（被淹死→溺尸等）。
