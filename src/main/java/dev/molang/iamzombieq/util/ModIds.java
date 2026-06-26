package dev.molang.iamzombieq.util;

import dev.molang.iamzombieq.IAmZombieMod;
import net.minecraft.resources.Identifier;

/**
 * Builds {@link Identifier}s in the mod's namespace. Centralizes the
 * {@code Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path)} call that was repeated across
 * the codebase. Behaviour is identical to the inlined call.
 */
public final class ModIds {
    private ModIds() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(IAmZombieMod.MOD_ID, path);
    }
}
