package dev.molang.iamzombieq.mixin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ItemStackMixinSourceTest {
    @Test
    void zombifiedPiglinGoldDurabilityReductionIsWiredIntoItemDamage() throws IOException {
        String mixins = Files.readString(Path.of("src/main/resources/iamzombieq.mixins.json"));
        String source = Files.readString(Path.of("src/main/java/dev/molang/iamzombieq/mixin/ItemStackMixin.java"));

        assertTrue(mixins.contains("ItemStackMixin"), "ItemStack mixin should be registered");
        assertTrue(source.contains("hurtAndBreak"), "mixin should intercept item durability damage");
        assertTrue(source.contains("ItemTags.PIGLIN_LOVED"), "gold-like piglin-loved items should be targeted");
        assertTrue(source.contains("ZombieForm.ZOMBIFIED_PIGLIN"), "only zombified piglin form should reduce gold durability");
        assertTrue(source.contains("goldDurabilityConsumptionMultiplier"), "rule-layer multiplier should control the reduction");
        assertTrue(source.contains("int originalAmount"), "ModifyVariable handler should include the original amount arg expected by runtime mixin validation");
    }
}
