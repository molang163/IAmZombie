plugins {
    id("dev.kikugie.stonecutter")
    alias(libs.plugins.moddev) apply false
}

stonecutter active "26.2.x" /* [SC] DO NOT EDIT */

// Source is authored in 26.2 form. Complete constant tokens keep the reversible swaps from
// corrupting legitimate singular APIs such as EntityType.getKey(...).
stonecutter parameters {
    replacements {
        string(current.parsed < "26.2") {
            replace("EntityTypes.AXOLOTL", "EntityType.AXOLOTL")
            replace("EntityTypes.CHICKEN", "EntityType.CHICKEN")
            replace("EntityTypes.DROWNED", "EntityType.DROWNED")
            replace("EntityTypes.GIANT", "EntityType.GIANT")
            replace("EntityTypes.HORSE", "EntityType.HORSE")
            replace("EntityTypes.HUSK", "EntityType.HUSK")
            replace("EntityTypes.IRON_GOLEM", "EntityType.IRON_GOLEM")
            replace("EntityTypes.ITEM", "EntityType.ITEM")
            replace("EntityTypes.NAUTILUS", "EntityType.NAUTILUS")
            replace("EntityTypes.PIG", "EntityType.PIG")
            replace("EntityTypes.SKELETON_HORSE", "EntityType.SKELETON_HORSE")
            replace("EntityTypes.SKELETON", "EntityType.SKELETON")
            replace("EntityTypes.SPIDER", "EntityType.SPIDER")
            replace("EntityTypes.TRADER_LLAMA", "EntityType.TRADER_LLAMA")
            replace("EntityTypes.VILLAGER", "EntityType.VILLAGER")
            replace("EntityTypes.WANDERING_TRADER", "EntityType.WANDERING_TRADER")
            replace("EntityTypes.ZOMBIE_HORSE", "EntityType.ZOMBIE_HORSE")
            replace("EntityTypes.ZOMBIE_NAUTILUS", "EntityType.ZOMBIE_NAUTILUS")
            replace("EntityTypes.ZOMBIE_VILLAGER", "EntityType.ZOMBIE_VILLAGER")
            replace("EntityTypes.ZOMBIFIED_PIGLIN", "EntityType.ZOMBIFIED_PIGLIN")
            replace("EntityTypes.ZOMBIE", "EntityType.ZOMBIE")
            replace("BlockEntityTypes.SKULL", "BlockEntityType.SKULL")
            replace(
                "new TestEnvironmentDefinition.SetDifficulty(Difficulty.HARD)",
                "new RestoringHardDifficultyEnvironment()"
            )
        }
        string(current.parsed < "26.1") {
            replace(
                "Holder<TestEnvironmentDefinition<?>>",
                "Holder<TestEnvironmentDefinition>"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace("\\bIdentifier\\b", "ResourceLocation", "\\bResourceLocation\\b", "Identifier")
        }
        // 1.21.11 moved GameRules and introduced the generic get/set API.
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.level\\.gamerules\\.GameRules\\b",
                "net.minecraft.world.level.GameRules",
                "\\bnet\\.minecraft\\.world\\.level\\.GameRules\\b",
                "net.minecraft.world.level.gamerules.GameRules"
            )
            replace(
                "\\blevel\\.getGameRules\\(\\)\\.get\\(GameRules\\.PLAYERS_SLEEPING_PERCENTAGE\\)",
                "level.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE)",
                "\\blevel\\.getGameRules\\(\\)\\.getInt\\(GameRules\\.RULE_PLAYERS_SLEEPING_PERCENTAGE\\)",
                "level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE)"
            )
            replace(
                "\\blevel\\.getGameRules\\(\\)\\.get\\(GameRules\\.ADVANCE_TIME\\)",
                "level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)",
                "\\blevel\\.getGameRules\\(\\)\\.getBoolean\\(GameRules\\.RULE_DAYLIGHT\\)",
                "level.getGameRules().get(GameRules.ADVANCE_TIME)"
            )
            replace(
                "\\blevel\\.getGameRules\\(\\)\\.get\\(GameRules\\.ADVANCE_WEATHER\\)",
                "level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)",
                "\\blevel\\.getGameRules\\(\\)\\.getBoolean\\(GameRules\\.RULE_WEATHER_CYCLE\\)",
                "level.getGameRules().get(GameRules.ADVANCE_WEATHER)"
            )
            replace(
                "\\blevel\\.getGameRules\\(\\)\\.get\\(GameRules\\.PVP\\)",
                "level.getServer().isPvpAllowed()",
                "\\blevel\\.getServer\\(\\)\\.isPvpAllowed\\(\\)",
                "level.getGameRules().get(GameRules.PVP)"
            )
            replace(
                "\\blevel\\(\\)\\.isPvpAllowed\\(\\)",
                "level().getServer().isPvpAllowed()",
                "\\blevel\\(\\)\\.getServer\\(\\)\\.isPvpAllowed\\(\\)",
                "level().isPvpAllowed()"
            )
            replace(
                "\\blevel\\.getGameRules\\(\\)\\.set\\((\\s*)GameRules\\.PLAYERS_SLEEPING_PERCENTAGE, (100|sleepingPercentage), level\\.getServer\\(\\)\\)",
                "level.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(${'$'}1${'$'}2, level.getServer())",
                "\\blevel\\.getGameRules\\(\\)\\.getRule\\(GameRules\\.RULE_PLAYERS_SLEEPING_PERCENTAGE\\)\\.set\\((\\s*)(100|sleepingPercentage), level\\.getServer\\(\\)\\)",
                "level.getGameRules().set(${'$'}1GameRules.PLAYERS_SLEEPING_PERCENTAGE, ${'$'}2, level.getServer())"
            )
            replace(
                "\\blevel\\.getGameRules\\(\\)\\.set\\((\\s*)GameRules\\.ADVANCE_TIME, (true|advanceTime), level\\.getServer\\(\\)\\)",
                "level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(${'$'}1${'$'}2, level.getServer())",
                "\\blevel\\.getGameRules\\(\\)\\.getRule\\(GameRules\\.RULE_DAYLIGHT\\)\\.set\\((\\s*)(true|advanceTime), level\\.getServer\\(\\)\\)",
                "level.getGameRules().set(${'$'}1GameRules.ADVANCE_TIME, ${'$'}2, level.getServer())"
            )
        }
        // 1.21.11 reorganized entity classes into narrower packages. Full class tokens and
        // explicit reverse patterns avoid collisions such as Zombie versus ZombieVillager.
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.chicken\\.Chicken\\b",
                "net.minecraft.world.entity.animal.Chicken",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.Chicken\\b",
                "net.minecraft.world.entity.animal.chicken.Chicken"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.equine\\.AbstractHorse\\b",
                "net.minecraft.world.entity.animal.horse.AbstractHorse",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.horse\\.AbstractHorse\\b",
                "net.minecraft.world.entity.animal.equine.AbstractHorse"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.equine\\.Horse\\b",
                "net.minecraft.world.entity.animal.horse.Horse",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.horse\\.Horse\\b",
                "net.minecraft.world.entity.animal.equine.Horse"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.equine\\.SkeletonHorse\\b",
                "net.minecraft.world.entity.animal.horse.SkeletonHorse",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.horse\\.SkeletonHorse\\b",
                "net.minecraft.world.entity.animal.equine.SkeletonHorse"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.equine\\.TraderLlama\\b",
                "net.minecraft.world.entity.animal.horse.TraderLlama",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.horse\\.TraderLlama\\b",
                "net.minecraft.world.entity.animal.equine.TraderLlama"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.equine\\.ZombieHorse\\b",
                "net.minecraft.world.entity.animal.horse.ZombieHorse",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.horse\\.ZombieHorse\\b",
                "net.minecraft.world.entity.animal.equine.ZombieHorse"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.golem\\.IronGolem\\b",
                "net.minecraft.world.entity.animal.IronGolem",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.IronGolem\\b",
                "net.minecraft.world.entity.animal.golem.IronGolem"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.golem\\.SnowGolem\\b",
                "net.minecraft.world.entity.animal.SnowGolem",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.SnowGolem\\b",
                "net.minecraft.world.entity.animal.golem.SnowGolem"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.pig\\.Pig\\b",
                "net.minecraft.world.entity.animal.Pig",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.Pig\\b",
                "net.minecraft.world.entity.animal.pig.Pig"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.polarbear\\.PolarBear\\b",
                "net.minecraft.world.entity.animal.PolarBear",
                "\\bnet\\.minecraft\\.world\\.entity\\.animal\\.PolarBear\\b",
                "net.minecraft.world.entity.animal.polarbear.PolarBear"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.monster\\.skeleton\\.Skeleton\\b",
                "net.minecraft.world.entity.monster.Skeleton",
                "\\bnet\\.minecraft\\.world\\.entity\\.monster\\.Skeleton\\b",
                "net.minecraft.world.entity.monster.skeleton.Skeleton"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.monster\\.spider\\.Spider\\b",
                "net.minecraft.world.entity.monster.Spider",
                "\\bnet\\.minecraft\\.world\\.entity\\.monster\\.Spider\\b",
                "net.minecraft.world.entity.monster.spider.Spider"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.monster\\.zombie\\.Drowned\\b",
                "net.minecraft.world.entity.monster.Drowned",
                "\\bnet\\.minecraft\\.world\\.entity\\.monster\\.Drowned\\b",
                "net.minecraft.world.entity.monster.zombie.Drowned"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.monster\\.zombie\\.Zombie\\b",
                "net.minecraft.world.entity.monster.Zombie",
                "\\bnet\\.minecraft\\.world\\.entity\\.monster\\.Zombie\\b",
                "net.minecraft.world.entity.monster.zombie.Zombie"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.monster\\.zombie\\.ZombieVillager\\b",
                "net.minecraft.world.entity.monster.ZombieVillager",
                "\\bnet\\.minecraft\\.world\\.entity\\.monster\\.ZombieVillager\\b",
                "net.minecraft.world.entity.monster.zombie.ZombieVillager"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.monster\\.zombie\\.ZombifiedPiglin\\b",
                "net.minecraft.world.entity.monster.ZombifiedPiglin",
                "\\bnet\\.minecraft\\.world\\.entity\\.monster\\.ZombifiedPiglin\\b",
                "net.minecraft.world.entity.monster.zombie.ZombifiedPiglin"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.npc\\.villager\\.AbstractVillager\\b",
                "net.minecraft.world.entity.npc.AbstractVillager",
                "\\bnet\\.minecraft\\.world\\.entity\\.npc\\.AbstractVillager\\b",
                "net.minecraft.world.entity.npc.villager.AbstractVillager"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.npc\\.villager\\.Villager\\b",
                "net.minecraft.world.entity.npc.Villager",
                "\\bnet\\.minecraft\\.world\\.entity\\.npc\\.Villager\\b",
                "net.minecraft.world.entity.npc.villager.Villager"
            )
        }
        regex(current.parsed < "1.21.11") {
            replace(
                "\\bnet\\.minecraft\\.world\\.entity\\.npc\\.wanderingtrader\\.WanderingTrader\\b",
                "net.minecraft.world.entity.npc.WanderingTrader",
                "\\bnet\\.minecraft\\.world\\.entity\\.npc\\.WanderingTrader\\b",
                "net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader"
            )
        }
    }
}

// This is an artifact aggregation gate only. Per-node tests and runtimes are intentionally
// executed after switching the active project so source-reading tests see the matching form.
tasks.register("chiseledBuild") {
    group = "project"
    description = "Builds and collects the formal JAR from every frozen version node."
    dependsOn(stonecutter.tasks.named("buildAndCollect").map { it.values })
}
