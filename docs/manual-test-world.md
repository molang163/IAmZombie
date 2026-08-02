# 开发手测世界

这套函数只用于模组开发时快速搭建手测场地。它会大范围覆盖方块、清除实体、永久修改群系并影响所有在线玩家，**只能在一次性、可丢弃的超平坦创造世界中使用**。

不得在真实存档、长期开发存档或共享服务器中使用。不要尝试通过手工回滚恢复执行过这些函数的世界；测试结束后应直接丢弃整个测试世界。

## 1. 唯一推荐入口

执行者必须拥有 GameMaster 权限（单人世界启用作弊，或由服务器授予等效的命令权限）。唯一推荐的函数入口是：

```mcfunction
/function iamzombieq:test/setup_all
```

不要直接调用 `_build`、`kit` 或任一 `plot_*` 函数；它们是 `setup_all` 的内部构建步骤。`setup_all` 只负责搭建场地和发放测试物品，不会直接切换玩家形态；构建完成消息中的“换形态靠死亡触发”指模组正常的死亡/进化玩法路径，不是另一个隐藏的建图函数。

## 2. 创建一次性世界

1. 新建一个可随时删除的超平坦、创造模式世界。
2. 启用作弊，确认执行者拥有 GameMaster 权限。
3. 在这个新世界中设置：

   ```mcfunction
   /gamerule doMobSpawning false
   /gamerule doDaylightCycle true
   /gamerule keepInventory true
   /difficulty hard
   ```

4. 确认世界中没有需要保留的建筑、实体、区块强制加载配置或其他玩家。
5. 只执行 `/function iamzombieq:test/setup_all`，然后在原地等待构建完成。

## 3. `setup_all` 的加载与 100 tick 延迟链

`setup_all` 不会立即建造远处平台。它按以下顺序运行：

1. `forceload add 0 0 432 32` 强制加载整个测试带覆盖的区块。
2. `schedule function iamzombieq:test/_build 100t` 把实际建造延迟 100 tick（正常 20 TPS 下约 5 秒），确保远端区块已加载。
3. 100 tick 后，`_build` 清理区域、铺设平台，并依次调用七个 `plot_*` 函数和 `kit`。
4. `_build` 最后执行 `forceload remove all`，再用 `tp @a 16 72 16` 把所有玩家传送到棺材区。

延迟期间不要离开或关闭世界；否则 scheduled function 可能尚未执行，世界会停留在只添加了 forceload 的中间状态。

## 4. 破坏性操作

执行入口会间接触发以下操作，且没有撤销脚本：

- 固定坐标覆盖：测试带横跨 `x=0..432`、`z=0..32`。`_build` 会在 `y=59` 铺设基岩，各 `plot_*` 还会在固定高度用 `fill`/`setblock` 覆盖地形和方块。
- 杀实体：`kill @e[type=!minecraft:player,x=0,y=55,z=0,dx=432,dy=45,dz=32]` 会清除范围内除玩家以外的实体。
- 永久改群系：日晒台使用 `fillbiome 256 64 0 288 80 32 minecraft:desert`。群系变更会写入世界数据，不能靠拆除方块恢复。
- 清除全部强制加载：`forceload remove all` 不只移除本函数添加的票据，也会移除当前维度已有的其他 forceload 设置。
- 影响所有玩家：`kit` 中的 `give @a` 给所有在线玩家发放大量物品；完成时的 `tp @a` 会传送所有在线玩家。
- 生成生物和固定设施：各 `plot_*` 会召唤生物，并在预定区域放置床、棺材、围栏、水池、平台、洞穴和其他测试设施。

这些行为正是不得在真实存档或共享服务器上运行该入口的原因。

## 5. 恢复方式

唯一受支持的恢复方式是：

1. 退出测试世界；
2. 删除该一次性世界；
3. 下次需要手测时重新创建新的超平坦创造世界。

不要尝试逐块复原、重新填写旧群系、找回被杀实体、撤销 `give @a` 或恢复原 forceload 集合。手工回滚无法可靠还原所有受影响状态。

## 6. 发布与开发资源边界

十个 `data/iamzombieq/function/test/*.mcfunction` 源文件保留在 main source set 中，并由 `processResources` 复制到 `build/resources/main`，供 NeoForge ModDev 通过 main source-set output 加载。正式发布 JAR 则在 `jar` 任务的 archive CopySpec 中排除整个 `data/iamzombieq/function/test/**` 路径。

因此不要为了在发布 JAR 中寻找这些函数而修改资源目录或另建归档；它们有意仅保留在开发运行环境。
