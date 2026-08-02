# 生物竞技场 (80,71,16): INF / MOB / REINF。普通生物带 AI(会动)；围栏圈住；铁傀儡给 MOB-009(会追打僵尸玩家)。
# MOB-011 伪装交易：一个【牧师村民(有职业+交易)】站着不动(NoAI,免得怕僵尸跑掉)——戴面具右键它能开交易(卖伪装面具/收腐肉换绿宝石)，摘面具则被门禁拦。另留一个流浪商人备用。
# 注：亡灵不攻击僵尸玩家；普通村民会怕僵尸而躲(这是 MOB-011 的设计)，所以交易演示用上面那个 NoAI 牧师。
fill 64 70 0 96 70 32 minecraft:grass_block
fill 70 71 6 90 71 6 minecraft:oak_fence
fill 70 71 26 90 71 26 minecraft:oak_fence
fill 70 71 6 70 71 26 minecraft:oak_fence
fill 90 71 6 90 71 26 minecraft:oak_fence
summon minecraft:villager 78 71 14 {PersistenceRequired:1b}
summon minecraft:pig 82 71 14 {PersistenceRequired:1b}
summon minecraft:pig 84 71 14 {PersistenceRequired:1b}
summon minecraft:horse 86 71 14 {PersistenceRequired:1b}
summon minecraft:wandering_trader 80 71 20 {PersistenceRequired:1b}
summon minecraft:villager 80 71 18 {VillagerData:{type:"minecraft:plains",profession:"minecraft:cleric",level:5},VillagerDataFinalized:1b,Offers:{Recipes:[{buy:{id:"minecraft:emerald",count:1},sell:{id:"iamzombieq:disguise_mask",count:1},maxUses:999,xp:1,priceMultiplier:0.0f},{buy:{id:"minecraft:rotten_flesh",count:32},sell:{id:"minecraft:emerald",count:1},maxUses:999,xp:1,priceMultiplier:0.0f}]},PersistenceRequired:1b,NoAI:1b}
summon minecraft:iron_golem 80 71 24 {PersistenceRequired:1b}
