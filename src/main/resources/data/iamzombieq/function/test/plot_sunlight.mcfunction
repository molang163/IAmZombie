# 日晒台 (272,71,16): SUN-001/005/006/007/008 + FORM-003(沙漠晒死→尸壳)。
# 关键：fillbiome 把这块设成【真·minecraft:desert】，HUSK 进化判定才会触发(badlands/savanna 不算)。
# 注意：原版没有 /setbiome，是 /fillbiome（1.19.3+，26.x 有）。
fill 256 70 0 288 70 32 minecraft:sandstone
fillbiome 256 64 0 288 80 32 minecraft:desert
# 角落一个遮阳棚，便于 SUN-005/008 对比(站棚下不烧)
fill 258 73 2 264 73 8 minecraft:sandstone
setblock 258 71 2 minecraft:sandstone
setblock 258 72 2 minecraft:sandstone
