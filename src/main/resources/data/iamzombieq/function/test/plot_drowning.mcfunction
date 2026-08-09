# 溺水池 (208,71,16): FORM-002(潜水憋死→溺尸) + DROWN-001..003 + MOB-005 美西螈(带 AI 才会攻击溺尸)。
fill 192 70 0 224 70 32 minecraft:stone
fill 205 71 13 211 74 19 minecraft:stone
fill 206 72 14 210 74 18 minecraft:water
setblock 208 71 16 minecraft:air
summon minecraft:axolotl 208 73 16 {PersistenceRequired:1b}
