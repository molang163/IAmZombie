package dev.molang.iamzombieq.gameplay;

import dev.molang.iamzombieq.util.ModIds;
import dev.molang.iamzombieq.util.Difficulties;

import dev.molang.iamzombieq.IAmZombieServerConfig;
import dev.molang.iamzombieq.rules.ZombieBalanceRules.AttributeDelta;
import dev.molang.iamzombieq.rules.ZombieBalanceRules.AttributeDeltaOperation;
import dev.molang.iamzombieq.rules.ZombieBalanceRules.FormAttributeKey;
import dev.molang.iamzombieq.rules.ZombieDamageRules;
import dev.molang.iamzombieq.rules.ZombieBalanceRules;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.state.PlayerZombieData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.Difficulty;

/**
 * Form-attribute application (armor/scale/speed/giant deltas) for zombie players. NOT an event class: it owns the
 * per-player {@code FORM_ATTRIBUTE_SIGNATURE} cache and is driven by the coordinating handlers in
 * {@link ZombiePlayerEvents} (per-tick refresh + forced refresh at login/clone/evolution/giant-kill). The signature
 * cache is server-side only and its cleanup ({@link #clearOnLogout}/{@link #clearOnServerStop}) is invoked from the
 * core logout / server-stop handlers.
 */
@SuppressWarnings("deprecation")
public final class ZombieFormAttributes {
    private static final Identifier INNATE_ARMOR_ID = ModIds.id("innate_armor");
    private static final Identifier BABY_SCALE_ID = ModIds.id("baby_scale");
    private static final Identifier BABY_SPEED_ID = ModIds.id("baby_speed");
    private static final Identifier DROWNED_MINING_ID = ModIds.id("drowned_submerged_mining");
    private static final Identifier GIANT_HEALTH_ID = ModIds.id("giant_health");
    private static final Identifier GIANT_SCALE_ID = ModIds.id("giant_scale");
    private static final Identifier GIANT_ENTITY_RANGE_ID = ModIds.id("giant_entity_range");
    private static final Identifier GIANT_BLOCK_RANGE_ID = ModIds.id("giant_block_range");
    private static final Identifier GIANT_STEP_HEIGHT_ID = ModIds.id("giant_step_height");
    private static final Identifier GIANT_SAFE_FALL_ID = ModIds.id("giant_safe_fall");
    private static final Identifier NON_GIANT_ATTACK_DAMAGE_ID = ModIds.id("non_giant_attack_damage");
    private static final Identifier GIANT_ATTACK_ID = ModIds.id("giant_attack");
    private static final Identifier DIFFICULTY_ATTACK_DAMAGE_ID = ModIds.id("difficulty_attack_damage");
    // Per-player packed (form, size, difficulty) signature of the form attributes last applied by
    // refreshFormAttributes. The per-tick path skips re-applying the (idempotent) modifiers when the signature is
    // unchanged — the common per-tick no-op. Event sites where the entity is fresh/modifier-less (login, respawn,
    // evolution, giant-kill) MUST route through the FORCED variant: transient attribute modifiers are NOT persisted
    // across respawn, and a same-form NORMAL->NORMAL ordinary death leaves the signature UNCHANGED, so a cache-gated
    // reapply would (incorrectly) skip restoring the fresh entity's innate armor/health/scale. Server-side only;
    // cleared on logout + server stop. Sentinel: no entry == never applied (force on first tick).
    private static final Map<UUID, Long> FORM_ATTRIBUTE_SIGNATURE = new HashMap<>();

    private ZombieFormAttributes() {
    }

    /**
     * Per-tick form-attribute refresh. Skips re-applying the (idempotent) modifiers when the player's
     * (form, size, world difficulty) signature is unchanged since the last apply — the common per-tick no-op.
     * Use {@link #refreshFormAttributesForced} at every event site where the entity is fresh / has had its
     * transient modifiers cleared (login, respawn/clone, evolution, giant-kill), because such a reapply may be
     * needed even when the signature has NOT changed.
     */
    public static void refreshFormAttributes(ServerPlayer player, PlayerZombieData data) {
        long signature = formAttributeSignature(player, data);
        Long previous = FORM_ATTRIBUTE_SIGNATURE.get(player.getUUID());
        if (previous != null && previous == signature) {
            return;
        }
        applyFormAttributes(player, data);
        FORM_ATTRIBUTE_SIGNATURE.put(player.getUUID(), signature);
    }

    /**
     * Unconditional form-attribute refresh for event sites where the entity is fresh / modifier-less or the
     * signature may not change despite needing a reapply (login, respawn/clone, in-place evolution, giant-kill).
     * Clears the cached signature first so the apply always runs, then records the fresh signature.
     */
    public static void refreshFormAttributesForced(ServerPlayer player, PlayerZombieData data) {
        FORM_ATTRIBUTE_SIGNATURE.remove(player.getUUID());
        applyFormAttributes(player, data);
        FORM_ATTRIBUTE_SIGNATURE.put(player.getUUID(), formAttributeSignature(player, data));
    }

    // Packs the (form, size, difficulty) signature that determines the form-attribute modifier values. Each
    // component is a small enum ordinal, so they pack losslessly into a single long for a cheap per-tick compare.
    private static long formAttributeSignature(ServerPlayer player, PlayerZombieData data) {
        long form = data.state().form().ordinal();
        long size = data.state().size().ordinal();
        long difficulty = player.level().getDifficulty().ordinal();
        return (form << 16) | (size << 8) | difficulty;
    }

    public static void applyFormAttributes(ServerPlayer player, PlayerZombieData data) {
        ZombieForm form = data.state().form();
        boolean giant = form == ZombieForm.GIANT;
        if (!giant) {
            // Leaving the giant form: drop the sweep anchor + swing cooldown here (this runs once per form change via
            // the signature-cached refresh) rather than leaking them in the maps until the player logs out.
            GiantPlayerEvents.cleanupOnFormLeave(player.getUUID());
        }
        double configuredArmor = configuredInnateArmor(form);
        double difficultyFraction = ZombieDamageRules.attackDamageBonusFraction(
                gameDifficulty(player.level().getDifficulty()));
        for (AttributeDelta delta : ZombieBalanceRules.formAttributeDeltas(
                form, data.state().size(), configuredArmor, difficultyFraction)) {
            applyModifier(
                    attributeFor(player, delta.key()),
                    modifierId(delta.key()),
                    delta.amount(),
                    modifierOperation(delta.operation()));
        }
    }

    private static int configuredInnateArmor(ZombieForm form) {
        return switch (form) {
            case NORMAL -> IAmZombieServerConfig.NORMAL_ZOMBIE_INNATE_ARMOR.get();
            case DROWNED -> IAmZombieServerConfig.DROWNED_INNATE_ARMOR.get();
            case HUSK -> IAmZombieServerConfig.HUSK_INNATE_ARMOR.get();
            case ZOMBIFIED_PIGLIN ->
                    IAmZombieServerConfig.ZOMBIFIED_PIGLIN_INNATE_ARMOR.get();
            case GIANT -> ZombieBalanceRules.innateArmor(ZombieForm.GIANT);
        };
    }

    private static dev.molang.iamzombieq.rules.difficulty.GameDifficulty gameDifficulty(Difficulty difficulty) {
        return Difficulties.toGameDifficulty(difficulty);
    }

    private static AttributeInstance attributeFor(ServerPlayer player, FormAttributeKey key) {
        return player.getAttribute(switch (key) {
            case INNATE_ARMOR -> Attributes.ARMOR;
            case BABY_SCALE, GIANT_SCALE -> Attributes.SCALE;
            case BABY_SPEED -> Attributes.MOVEMENT_SPEED;
            case DROWNED_SUBMERGED_MINING -> Attributes.SUBMERGED_MINING_SPEED;
            case GIANT_MAX_HEALTH -> Attributes.MAX_HEALTH;
            case GIANT_BLOCK_INTERACTION_RANGE -> Attributes.BLOCK_INTERACTION_RANGE;
            case GIANT_ENTITY_INTERACTION_RANGE -> Attributes.ENTITY_INTERACTION_RANGE;
            case GIANT_STEP_HEIGHT -> Attributes.STEP_HEIGHT;
            case GIANT_SAFE_FALL_DISTANCE -> Attributes.SAFE_FALL_DISTANCE;
            case NON_GIANT_ATTACK_DAMAGE, GIANT_ATTACK_DAMAGE, DIFFICULTY_ATTACK_DAMAGE -> Attributes.ATTACK_DAMAGE;
        });
    }

    private static Identifier modifierId(FormAttributeKey key) {
        return switch (key) {
            case INNATE_ARMOR -> INNATE_ARMOR_ID;
            case BABY_SCALE -> BABY_SCALE_ID;
            case BABY_SPEED -> BABY_SPEED_ID;
            case DROWNED_SUBMERGED_MINING -> DROWNED_MINING_ID;
            case GIANT_MAX_HEALTH -> GIANT_HEALTH_ID;
            case GIANT_SCALE -> GIANT_SCALE_ID;
            case GIANT_BLOCK_INTERACTION_RANGE -> GIANT_BLOCK_RANGE_ID;
            case GIANT_ENTITY_INTERACTION_RANGE -> GIANT_ENTITY_RANGE_ID;
            case GIANT_STEP_HEIGHT -> GIANT_STEP_HEIGHT_ID;
            case GIANT_SAFE_FALL_DISTANCE -> GIANT_SAFE_FALL_ID;
            case NON_GIANT_ATTACK_DAMAGE -> NON_GIANT_ATTACK_DAMAGE_ID;
            case GIANT_ATTACK_DAMAGE -> GIANT_ATTACK_ID;
            case DIFFICULTY_ATTACK_DAMAGE -> DIFFICULTY_ATTACK_DAMAGE_ID;
        };
    }

    private static AttributeModifier.Operation modifierOperation(AttributeDeltaOperation operation) {
        return switch (operation) {
            case ADD_VALUE -> AttributeModifier.Operation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
        };
    }

    private static void applyModifier(AttributeInstance attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
        if (attribute == null) {
            return;
        }
        if (amount == 0.0) {
            attribute.removeModifier(id);
        } else {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    /**
     * Drop the cached form-attribute signature for a disconnecting player (whose transient modifiers were cleared
     * with the old entity) so login re-applies via the forced refresh rather than trusting a stale entry. Invoked
     * from the core logout handler because this is a non-event helper class.
     */
    public static void clearOnLogout(UUID uuid) {
        FORM_ATTRIBUTE_SIGNATURE.remove(uuid);
    }

    /** Drop every cached signature on server stop. Invoked from the core server-stop handler. */
    public static void clearOnServerStop() {
        FORM_ATTRIBUTE_SIGNATURE.clear();
    }
}
