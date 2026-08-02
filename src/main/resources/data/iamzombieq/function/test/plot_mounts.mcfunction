# 坐骑栏 (400,71,16): MNT 蜘蛛/亡灵马/鸡/大僵尸(=普通僵尸,只 BABY 能骑)。
# 生物带正常 AI(能骑/能动)；亡灵不攻击僵尸玩家。加顶棚(不然大僵尸白天会烧死) + 西墙(蜘蛛爬墙 MNT-005 用)。
# GIANT 形态测：创造里自己 /summon minecraft:giant 再杀(它太高，不预放)。
fill 384 70 0 416 70 32 minecraft:cobblestone
fill 384 75 0 416 75 32 minecraft:cobblestone
fill 384 71 0 384 74 32 minecraft:cobblestone
summon minecraft:spider 394 71 16 {PersistenceRequired:1b}
summon minecraft:zombie_horse 398 71 16 {PersistenceRequired:1b}
summon minecraft:chicken 402 71 16 {PersistenceRequired:1b}
summon minecraft:zombie 406 71 16 {PersistenceRequired:1b}
