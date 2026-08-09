# 实际建造，由 setup_all 延迟 100t 调用(确保 forceload 的远处区块已加载，否则远处平台是空的)。
# 它以【服务器】身份运行，所以发道具/传送用 @a(全体玩家)而非 @s。
kill @e[type=!minecraft:player,x=0,y=55,z=0,dx=432,dy=45,dz=32]
fill 0 59 0 432 59 32 minecraft:bedrock
function iamzombieq:test/plot_coffin
function iamzombieq:test/plot_arena
function iamzombieq:test/plot_food
function iamzombieq:test/plot_drowning
function iamzombieq:test/plot_sunlight
function iamzombieq:test/plot_herobrine
function iamzombieq:test/plot_mounts
function iamzombieq:test/kit
forceload remove all
tp @a 16 72 16
say [iamzombieq] 测试世界建好！7 区 @ x=16..416,z=0..32；日晒台已设真·沙漠。换形态靠死亡触发(见 docs/manual-test-world.md §1)。
