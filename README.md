# I Am Zombie? / 我是僵尸？

> A Minecraft mod where you play as the zombie: beds explode, you sleep in coffins, villagers flee.

[English](#english) | [中文](#中文)

## English


You aren't fighting the zombie, you are the zombie. Once you turn, the survival rulebook flips: sunlight burns you, villagers run from you, beds explode under you, and you get by on rotten flesh, a wall-climbing spider, and a coffin.

### Start and world rules

- The first time you become a zombie you get a small starting stash: **8** rotten flesh by default (configurable, range 0–64).
- Peaceful difficulty is locked, its button is hidden, and the world is forced to Easy or higher.
- Creative still runs these rules; only Spectator is exempt.

### Five forms

There are five zombie forms: Normal, Drowned, Husk, Zombified Piglin, and Giant.

- **Innate armor**: Normal / Drowned / Zombified Piglin have 2 points each, Husk has 4, Giant has 0.
- **Baby form**: half scale and +50% movement speed; after going hungry long enough it grows into an adult.

Per-form perks:

- **Husk**: a melee hit applies Hunger II for 15 seconds; does not burn in sunlight.
- **Drowned**: full mining speed underwater, built-in night vision, never drowns.
- **Zombified Piglin**: permanent fire resistance, gold gear loses durability at 1/4 the normal rate, and nearby zombified piglins defend you when you are hit.
- Normal and Drowned burn in sunlight; Husk and Zombified Piglin do not. A pumpkin or a damageable helmet blocks the sun; the disguise mask does not.

### How you die decides your evolution

The way you die determines what you become:

- Starve → revert to baby form
- Drown → Drowned (a Husk that drowns reverts to Normal)
- Burned to death by sun in a desert → Husk
- Die in Nether lava → Zombified Piglin

Each form gives a one-time reward the **first** time it is unlocked: Drowned gives a trident, Husk gives a batch of desert drops (2–4 randomized stacks), Zombified Piglin gives an enchanted golden sword; each also grants a matching advancement.

Evolving heals roughly half your max health and tops up a little food. An ordinary death (not an evolution) reverts you to the normal adult form, keeps your inventory and XP, and respawns you in place.

### Combat

- Damage scales with difficulty: Peaceful / Easy ×1.10, Normal ×1.25, Hard ×1.50.
- Breaking a wooden door empty-handed is 3× faster.
- Whatever you hit hits back.
- On Hard with mob spawning enabled, a hurt zombie player can call same-form reinforcements (with anti-farm decay of −0.05 per successful spawn, up to 50 spawn-position attempts per hit; the Giant gets no reinforcements).

### Coffin and sleep

- Beds explode for zombie players: default power **5.0** and they start fire (both configurable).
- The coffin has 12 wood variants, crafted as "planks planks planks / white wool, rotten flesh, white wool / planks planks planks". The first time you turn, all 12 recipes unlock.
- At night a coffin only sets your respawn point.
- During the day, with no hostiles nearby, you can actually sleep: a multi-tick nap that advances while the screen gradually darkens.
- Sleep runs a per-dimension vote using the sleep-skip percentage gamerule; once it passes, it skips to night.
- Your respawn point is set the moment you lie down.
- Taking damage, a hostile showing up nearby (re-checked once per second), or the coffin being destroyed all interrupt sleep.

### Disguise

- Disguise mask: rotten flesh + leather ×2 + string, shapeless, **15** durability.
- Wearing it makes villagers and wandering traders trade with you; each successful trade costs 1 durability.
- The mask **only** gates trading. It does not fool combat mobs: iron golems and the like still attack you.
- The mask does not block the sun.
- Without a disguise, villagers and wandering traders flee at 8 blocks.
- Killing a villager has a chance to turn it into a zombie villager (keeping its profession and trades).

### Relations with mobs

- **Always attack you (all forms)**: Iron Golem, Snow Golem, Zoglin, Goat, Creeper. The Endermite is grouped with Creeper and also attacks all forms.
- **Conditional**: Trader Llama attacks every form except Zombified Piglin; Axolotl attacks only the Drowned form.
- **Left to vanilla, never force-targeted onto you**: bosses like the Warden and Wither; Enderman and Polar Bear only retaliate when provoked.
- Undead kin and passive animals leave you alone by default; Drowned do not friendly-fire each other and have their own rally behavior.
- Your tamed spider and the big zombie you ride never attack you.

### Food

- Rotten flesh and raw meat are safe to eat, but rotten flesh **no longer grants any buff** (no Strength, no Saturation).
- Cooked human food gives Hunger II (360 ticks) plus Nausea (240 ticks); sweets (cookie / cake / pumpkin pie) add Slowness on top.
- Spider eye gives Night Vision (900 ticks / 45 s by default); glow berries give a short Night Vision (120 ticks / 6 s).
- Cod and salmon give Water Breathing (400 ticks by default); tropical fish gives 300 ticks.
- Raw rabbit gives Speed (160 ticks) and Saturation (80 ticks).
- Pufferfish gives Absorption and Regeneration (durations and levels configurable).
- Super rotten flesh gives Strength (900 ticks) and Saturation (160 ticks), and also restores a baby to adult.
- The golden apple is nerfed: it only gives Absorption (1200 ticks of Absorption I by default, not healing) and tacks on a Hunger penalty, unlike vanilla healing.
- A poisonous potato grants a random positive effect: Speed, Haste, or Luck.
- Some foods (pufferfish, spider eye, poisonous potato, super rotten flesh) can be eaten even with a full hunger bar.
- Debuffs you already had before eating (vanilla Hunger / Nausea / Poison, plus golden-apple Regeneration / Absorption / Resistance / Fire Resistance) are preserved and restored afterward.

### Mounts

- **Spider**: tame it **progressively** by feeding rotten flesh, spider eye, or super rotten flesh (100 points to tame; rotten flesh +20, spider eye +35, super rotten flesh +60). Feeding also heals it (rotten flesh 4, spider eye 6, super rotten flesh 10). Once tamed it is rideable and climbs walls, ridden speed defaults to 0.30 (configurable), and it does not despawn.
- **Baby-only mounts**: a baby zombie can right-click a chicken empty-handed to ride it, or ride an adult big zombie.
- **Big zombie**: mount with an empty-handed right-click, control with WASD, with native jumping (it mirrors your jump key). While ridden it auto-attacks: your last target → your last attacker → a proximity scan (villager > golem > monster). A big zombie you just provoked cannot be mounted.
- **Undead horses**: a wild zombie or skeleton horse is auto-tamed and owned when a zombie player interacts with it; rotten flesh heals 4, super rotten flesh heals 10.
- Killing a normal horse as a zombie player has a difficulty-based chance to turn it into a zombie horse; killing a normal nautilus likewise turns it into a rideable zombie nautilus.

### Giant

Kill the vanilla Giant in Creative to transform into the Giant form.

- 100 health, 6× scale (1.0 base + 5.0 modifier).
- 27-block block reach, 18-block entity reach, 3.6-block step height, +3.0 safe-fall bonus.
- A punch deals 55 fixed damage (not difficulty-scaled).
- Stomp aura: 10 damage per second within 5 blocks (every 20 ticks).
- Walking crushes soft blocks along the way (body box widened 2.0 horizontally and vertically, hardness limit 2.0, up to 256 blocks per tick) and it is immune to suffocation.
- Left-click swing: a 17×17×17 region, up to 200 blocks per swing (hardness limit 5.0), 25-tick cooldown.
- It cannot break containers, bedrock, or obsidian.
- The Giant gets no reinforcements.

### Misc

- 14 advancements in total.
- Configurable options include: starting rotten flesh, bed explosion power and whether it starts fire, spider mount speed, and the client skin.

## 中文


你不是在打僵尸，你就是那个僵尸。变成僵尸后，整套生存规则都换了一套：太阳会烧你，村民见你就跑，床躺下会炸，而你靠腐肉、爬墙蜘蛛和棺材活下去。

### 开局与世界规则

- 第一次变成僵尸时，给你一点起步资源：默认 **8** 个腐肉（可在配置里改，范围 0–64）。
- 和平难度被锁住、对应按钮被藏起来，世界强制为简单及以上。
- 创造模式照样跑这套规则；只有旁观模式豁免。

### 五种形态

僵尸有五种形态：普通、溺尸、尸壳、僵尸猪灵、巨人。

- **固有护甲**：普通 / 溺尸 / 僵尸猪灵各 2 点，尸壳 4 点，巨人 0 点。
- **幼年形态**：体型缩小一半，移动速度 +50%，挨饿一段时间后会长大成年。

各形态的特长：

- **尸壳**：近战命中会给目标 15 秒的饥饿 II；在阳光下不会着火。
- **溺尸**：水下满速挖掘、自带夜视、不会淹死。
- **僵尸猪灵**：永久抗火，金质装备耐久消耗只有 1/4，被打时附近的僵尸猪灵会帮你。
- 普通和溺尸在阳光下会着火；尸壳和僵尸猪灵不会。南瓜或可受损的头盔能挡太阳，伪装面具不能。

### 死法决定进化

你怎么死，决定你变成什么：

- 饿死 → 退回幼年形态
- 淹死 → 溺尸（尸壳淹死则退回普通）
- 沙漠里被太阳烤死 → 尸壳
- 下界岩浆里死 → 僵尸猪灵

每种形态**第一次**解锁时给一次性奖励：溺尸给三叉戟，尸壳给一批沙漠掉落物（2–4 组随机），僵尸猪灵给一把附魔金剑；同时记一个对应进度。

进化时大约回满一半生命，并补一点饱食度。普通死亡（非进化）会退回普通成年形态，保留背包和经验，原地复活。

### 战斗

- 伤害随难度提升：和平 / 简单 ×1.10，普通 ×1.25，困难 ×1.50。
- 空手砸木门的速度是平时的 3 倍。
- 你打谁，谁就会还手。
- 在困难难度且开启生物生成时，被打的僵尸玩家可能召来同形态增援（带防刷衰减，每次成功生成后概率 −0.05，每次受伤最多尝试 50 个生成点；巨人没有增援）。

### 棺材与睡眠

- 床对僵尸玩家会爆炸：默认威力 **5.0**，并会引燃火（两者都可配置）。
- 棺材有 12 种木质变体，配方为「木板 木板 木板 / 白羊毛 腐肉 白羊毛 / 木板 木板 木板」。第一次变僵尸时解锁全部 12 个配方。
- 夜里在棺材里只设置重生点。
- 白天且附近没有敌对生物时可以真正睡觉，多 tick 小睡推进，画面逐渐变暗。
- 睡眠按维度发起投票，沿用「睡眠跳过百分比」游戏规则，达标后跳到夜晚。
- 躺下的瞬间就设置重生点。
- 受到伤害、附近出现敌对生物（每秒重新检测一次）、或棺材被破坏都会打断睡眠。

### 伪装

- 伪装面具：腐肉 + 皮革 ×2 + 线，无序合成，**15** 点耐久。
- 戴上后村民和流浪商人愿意跟你交易，每次成功交易扣 1 点耐久。
- 面具**只**放行交易，不会骗过战斗类生物：铁傀儡之类照样打你。
- 面具不挡太阳。
- 没伪装时村民和流浪商人会在 8 格距离逃开。
- 杀死村民有概率把它变成僵尸村民（保留职业与交易）。

### 与生物的关系

- **无条件攻击你（所有形态）**：铁傀儡、雪傀儡、僵尸疣猪兽、山羊、苦力怕。终界螨归在苦力怕一类，也攻击所有形态。
- **有条件**：商队羊驼除僵尸猪灵外都打；美西螈只打溺尸形态。
- **交给原版判定，从不强制锁定你**：监守者、凋灵这类首领；终界人、北极熊只在被激怒时反击。
- 同类亡灵和被动动物默认不惹你；溺尸之间不互相误伤，并有自己的集结行为。
- 你驯服的蜘蛛、骑乘的大僵尸不会打你。

### 食物

- 腐肉和生肉吃了没事，但腐肉**不再给任何增益**（既不给力量也不给饱食）。
- 熟的人类食物会给饥饿 II（360 tick）+ 反胃（240 tick）；甜食（曲奇 / 蛋糕 / 南瓜派）额外加缓慢。
- 蜘蛛眼给夜视（默认 900 tick / 45 秒）；发光浆果给短暂夜视（120 tick / 6 秒）。
- 鳕鱼和鲑鱼给水下呼吸（默认 400 tick），热带鱼给 300 tick。
- 生兔肉给速度（160 tick）和饱食（80 tick）。
- 河豚给吸收和恢复（时长与等级可配）。
- 超级腐肉给力量（900 tick）和饱食（160 tick），还能把幼年催熟成成年。
- 金苹果被削弱了：只给吸收（默认 1200 tick 的吸收 I，不是回血），还附带饥饿惩罚，不像原版那样治疗。
- 毒马铃薯吃下后随机给一个正面效果：速度、急迫或幸运。
- 有些食物（河豚、蜘蛛眼、毒马铃薯、超级腐肉）即使饱食满了也能吃。
- 吃东西前已有的减益（原版的饥饿 / 反胃 / 中毒，以及金苹果的恢复 / 吸收 / 抗性 / 抗火）会被保留并在之后还原。

### 坐骑

- **蜘蛛**：用腐肉、蜘蛛眼或超级腐肉喂养来**逐步**驯服（满 100 点驯服；腐肉 +20、蜘蛛眼 +35、超级腐肉 +60）。喂食同时回血（腐肉 4、蜘蛛眼 6、超级腐肉 10）。驯服后可骑乘并爬墙，移动速度默认 0.30 可配，且不会消失。
- **幼年专属坐骑**：幼年僵尸可以空手右键骑鸡，或骑成年的大僵尸。
- **大僵尸**：空手右键骑乘，WASD 控制，可原生跳跃（镜像你的跳跃键）。骑着时它会自动攻击：优先你最近的目标 → 你最近的攻击者 → 就近扫描（村民 > 傀儡 > 怪物）。刚被你激怒的大僵尸骑不上去。
- **亡灵马**：野生的僵尸马 / 骷髅马被僵尸玩家交互时自动驯服并归你；喂腐肉回 4、超级腐肉回 10。
- 用僵尸玩家杀死普通马有概率（按难度）把它变成僵尸马；杀死普通鹦鹉螺同理变成可骑的僵尸鹦鹉螺。

### 巨人

在创造模式下击杀原版巨人即可变身为巨人形态。

- 100 点生命，体型 6 倍（基础 1.0 + 5.0 修饰）。
- 方块互动距离 27 格，实体互动距离 18 格，跨步高度 3.6 格，额外安全坠落 3.0 格。
- 一拳 55 点固定伤害（不随难度缩放）。
- 踩踏光环：5 格半径内每秒造成 10 点伤害（20 tick 一次）。
- 走路时碾碎沿途的软方块（横纵各扩 2.0，硬度上限 2.0，每 tick 最多 256 个），并免疫窒息伤害。
- 左键挥击：17×17×17 的范围，每次最多 200 个方块（硬度上限 5.0），25 tick 冷却。
- 砸不开容器、基岩和黑曜石。
- 巨人没有增援。

### 杂项

- 共 14 个进度。
- 可配置项包括：开局腐肉数量、床爆炸威力与是否引燃、蜘蛛坐骑速度，以及客户端皮肤。

## License

Released under the MIT License. See [LICENSE](LICENSE).
