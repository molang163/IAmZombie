# 棺材睡觉 · 设计说明（as-built）/ Coffin sleep — design (as-built)

> Applies to I Am Zombie? 1.1.0 on Minecraft 26.2 / NeoForge.

> 本文件记录棺材"睡觉 + 跳过白天 + 天敌防扰"的**已实现**设计，与代码同源。

## 涉及文件 / Files
- `block/CoffinBlock.java` — 方块 + 右键判定（`useWithoutItem`、`hasHostileNearby`、`canRestToNight`、`setCoffinRespawn`）。
- `gameplay/CoffinNapManager.java` — "真睡眠"驱动器（`PlayerTickEvent.Post`）：沉睡计时、多人投票、被打扰惊醒、推进到夜晚、清理。
- `rules/sleep/ZombieSleepRules.java` — 纯判定 `useCoffin(...)` + 投票数学 `coffinSleepersNeeded/enoughCoffinSleepers`。
- `rules/sleep/SleepAction.java` — 动作枚举。
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
只把关系矩阵中的 **主动攻击** 类计为威胁，按玩家形态判定，并复用 `ZombieMobTargetingRules`（与索敌同一套）。

```java
ZombieForm form = player.getData(IAmZombieAttachments.PLAYER_ZOMBIE).state().form();
for (Mob mob : level.getEntitiesOfClass(Mob.class, area /* 8×5×8 */)) {
    var kind = ZombieMobTargetingRules.classify(mob);
    if (kind != ZombieMobTargetingRules.MobKind.BOSS
            && ZombieMobTargetingRules.attacksZombiePlayer(kind, form)) return true;
}
return false;
```

按形态的主动攻击天敌：

| 形态 | 在旁边就不能睡 |
|---|---|
| 僵尸 NORMAL / 尸壳 HUSK | 铁傀儡、雪傀儡、疣猪兽、山羊、苦力怕、行商羊驼 |
| 溺尸 DROWNED | + 美西螈 |
| 僵尸猪灵 ZOMBIFIED_PIGLIN | 铁傀儡、雪傀儡、疣猪兽、山羊、苦力怕（行商羊驼不打猪灵 → 不算） |
| 巨人 GIANT | 同僵尸行（矩阵按形态、不按体型） |

设计取舍（为什么）：
- **Boss 排除**（`kind != BOSS`）：监守者只有被振动激怒后才追击，凋灵不攻击亡灵——都不是无条件攻击者，故不因在场拦睡；真被监守者打到由"受伤惊醒"兜底。
- **其他玩家不算**：扫描 `Mob`，`Player` 不是 `Mob`；本模组别的玩家也是僵尸（同伴）。
- **苦力怕保留**：它主动冲向"玩家"实体爆炸，对僵尸玩家是真威胁（普通僵尸实体的关系不同；此处按玩家场景保留）。
- **条件型攻击者（驯服狼等）不算**：`classify` 归 `IGNORED`，只在被主人指使时打，受伤兜底。
- **不再需要友好坐骑豁免**：自己的大僵尸坐骑/驯服蜘蛛本就 `IGNORED`，矩阵天然不拦（旧 `isFriendlyMount` 已删）。
- **入睡 + 中途一致**：入睡前查一次；睡着后驱动器每 ~20 tick 用**同一** `hasHostileNearby` 复查，天敌中途靠近也会弄醒。

## 3. 文案 / Messages
- `coffin.not_safe`（有天敌时）：zh「周围有天敌徘徊，你无法安睡。」/ en "Natural enemies are prowling nearby. You can't rest now."（**不再**写"怪物/monsters"——僵尸自己就是怪物）。
- `coffin.disturbed`（中途惊醒，通用：受伤/破坏/天敌靠近）、`coffin.players_sleeping`（X/Y）、`coffin.rested`、`coffin.respawn_set_only`、`coffin.lying_down`、`coffin.not_enough`、`coffin.zombie_only`。
- `coffin.respawn_set`：夜晚 / 无昼夜循环维度（下界、末地）只设重生点时的**中性**提示（"Respawn point saved."）；区别于 `coffin.respawn_set_only` 的"but night never came"——后者用于白天已过、无法再跳到夜晚的场景。
- `coffin.recipes_unlocked`：首次成为僵尸解锁棺材配方时的一次性提示（"Need sleep? Try a coffin."），由 `ZombiePlayerEvents.unlockCoffinRecipes` 发送。
