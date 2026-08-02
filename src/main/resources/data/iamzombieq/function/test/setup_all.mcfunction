# 一键建立手测世界。
# 先决条件（手动一次——gamerule/difficulty 不能进函数，是建世界的事）：
#   1) 新建【超平坦 + 创造】世界
#   2) /gamerule doMobSpawning false
#   3) /gamerule doDaylightCycle true      # 棺材「白天小睡→跳夜」(SLEEP-002) 要靠它推进时间
#   4) /gamerule keepInventory true
#   5) /difficulty hard
# 然后执行：/function iamzombieq:test/setup_all
# 注意：远处平台的区块需要先加载，所以本函数会先 forceload、再延迟 100t(~5秒)自动建造——
#       这 5 秒里【站着别走远】，建好后会把你 tp 到棺材区。
forceload add 0 0 432 32
schedule function iamzombieq:test/_build 100t
say [iamzombieq] 正在加载区块... 约 5 秒后自动建造 7 个区，请站着别走远。
