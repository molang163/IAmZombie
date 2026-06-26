package dev.molang.iamzombieq.tags;
import dev.molang.iamzombieq.util.ModIds;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Custom block tags driving the giant's contact destruction (设计指南 §4.1/§9.5). Pure hardness cannot tell a
 * village (cobblestone 2.0) from natural terrain (stone 1.5), so a {@code GIANT_SOFT} whitelist decides what the
 * walking giant crushes, and a {@code GIANT_IMMUNE} blacklist is the absolute no-crush list (bedrock/obsidian/…).
 * Both are datapack-backed (data/iamzombieq/tags/block/*.json); these constants are the compiled references.
 */
public final class IAmZombieBlockTags {
    public static final TagKey<Block> GIANT_SOFT =
            TagKey.create(Registries.BLOCK, ModIds.id("giant_soft"));
    public static final TagKey<Block> GIANT_IMMUNE =
            TagKey.create(Registries.BLOCK, ModIds.id("giant_immune"));

    private IAmZombieBlockTags() {
    }
}
