package dev.molang.iamzombieq.state;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import dev.molang.iamzombieq.IAmZombieMod;
import dev.molang.iamzombieq.rules.core.ZombieForm;
import dev.molang.iamzombieq.rules.core.ZombieSize;
import dev.molang.iamzombieq.rules.core.ZombieState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class IAmZombieAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IAmZombieMod.MOD_ID);

    // NOTE: deliberately NOT .copyOnDeath(). On an ordinary death the new player must reset to the normal
    // zombie form (spec form.main_reset); copyOnDeath would carry the evolved form across respawn (and on some
    // orderings overwrite the reset). The onPlayerClone handler reads the original's data and reapplies it via
    // resetStateForOrdinaryDeath(), which resets form/size to DEFAULT while preserving the first-evolution reward flags.
    public static final Supplier<AttachmentType<PlayerZombieData>> PLAYER_ZOMBIE =
            ATTACHMENTS.register("player_zombie", () -> AttachmentType
                    .builder(() -> PlayerZombieData.DEFAULT)
                    .serialize(new PlayerZombieDataSerializer())
                    .sync(PlayerZombieDataSync.INSTANCE)
                    .build());

    // .sync()'d so the owning UUID reaches the client. Without it, the client-side EntityInteract path reads
    // an untamed (DEFAULT) SpiderMountData and the ride is blocked client-side before the server is consulted.
    // The ride itself stays server-authoritative: onEntityMount re-checks canMount with the server's data.
    public static final Supplier<AttachmentType<SpiderMountData>> SPIDER_MOUNT =
            ATTACHMENTS.register("spider_mount", () -> AttachmentType
                    .builder(() -> SpiderMountData.DEFAULT)
                    .serialize(new SpiderMountDataSerializer())
                    .sync(SpiderMountDataSync.INSTANCE)
                    .build());

    // Durable, server-only mirror of HerobrineEvents' in-memory PendingRespawn snapshot. Serialized so a
    // player who quits at the Herobrine death screen (or a dedicated restart) is still restored on relogin +
    // respawn — read ONLY as a fallback when the in-memory map entry is gone (post-server-stop recovery).
    // Deliberately NOT .sync()'d (never read client-side) and NOT .copyOnDeath() (the clone handler reads
    // event.getOriginal()'s data directly, matching the PLAYER_ZOMBIE pattern).
    public static final Supplier<AttachmentType<HerobrineRespawnSnapshot>> HEROBRINE_PENDING_RESPAWN =
            ATTACHMENTS.register("herobrine_pending_respawn", () -> AttachmentType
                    .builder(() -> HerobrineRespawnSnapshot.EMPTY)
                    .serialize(new HerobrineRespawnSnapshotSerializer())
                    .build());

    // Durable per-player Herobrine dread state (was an in-memory ENCOUNTERS map). Serialized onto the
    // player NBT so sightings/timings AND escalatedBefore survive logout + server restart, honoring the
    // documented "once Herobrine has killed you it stays lethal (veteran forever)" rule. Deliberately NOT
    // .sync()'d (server-only) and NOT .copyOnDeath() (HerobrineEvents.onPlayerClone reads the original's
    // data and re-sets it on death, matching the PLAYER_ZOMBIE/HEROBRINE_PENDING_RESPAWN pattern).
    public static final Supplier<AttachmentType<HerobrineEncounterState>> HEROBRINE_ENCOUNTER =
            ATTACHMENTS.register("herobrine_encounter", () -> AttachmentType
                    .builder(HerobrineEncounterState::new)
                    .serialize(new HerobrineEncounterStateSerializer())
                    .build());

    private IAmZombieAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    private static final class PlayerZombieDataSerializer implements IAttachmentSerializer<PlayerZombieData> {
        @Override
        public PlayerZombieData read(IAttachmentHolder holder, ValueInput input) {
            ZombieForm form = ZombieForm.byId(input.getStringOr("form", ZombieForm.NORMAL.id()));
            ZombieSize size = ZombieSize.byId(input.getStringOr("size", ZombieSize.ADULT.id()));
            boolean receivedDrownedReward = input.getBooleanOr("receivedFirstDrownedReward", false);
            boolean receivedHuskReward = input.getBooleanOr("receivedFirstHuskReward", false);
            boolean receivedPiglinReward = input.getBooleanOr("receivedFirstZombifiedPiglinReward", false);
            return new PlayerZombieData(
                    new ZombieState(form, size),
                    receivedDrownedReward,
                    receivedHuskReward,
                    receivedPiglinReward
            );
        }

        @Override
        public boolean write(PlayerZombieData attachment, ValueOutput output) {
            output.putString("form", attachment.state().form().id());
            output.putString("size", attachment.state().size().id());
            output.putBoolean("receivedFirstDrownedReward", attachment.receivedFirstDrownedReward());
            output.putBoolean("receivedFirstHuskReward", attachment.receivedFirstHuskReward());
            output.putBoolean("receivedFirstZombifiedPiglinReward", attachment.receivedFirstZombifiedPiglinReward());
            return true;
        }
    }

    private static final class PlayerZombieDataSync implements StreamCodec<RegistryFriendlyByteBuf, PlayerZombieData> {
        private static final PlayerZombieDataSync INSTANCE = new PlayerZombieDataSync();

        @Override
        public PlayerZombieData decode(RegistryFriendlyByteBuf input) {
            ZombieForm form = input.readEnum(ZombieForm.class);
            ZombieSize size = input.readEnum(ZombieSize.class);
            boolean receivedDrownedReward = input.readBoolean();
            boolean receivedHuskReward = input.readBoolean();
            boolean receivedPiglinReward = input.readBoolean();
            return new PlayerZombieData(
                    new ZombieState(form, size),
                    receivedDrownedReward,
                    receivedHuskReward,
                    receivedPiglinReward
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf output, PlayerZombieData value) {
            output.writeEnum(value.state().form());
            output.writeEnum(value.state().size());
            output.writeBoolean(value.receivedFirstDrownedReward());
            output.writeBoolean(value.receivedFirstHuskReward());
            output.writeBoolean(value.receivedFirstZombifiedPiglinReward());
        }
    }

    private static final class SpiderMountDataSerializer implements IAttachmentSerializer<SpiderMountData> {
        @Override
        public SpiderMountData read(IAttachmentHolder holder, ValueInput input) {
            String owner = input.getStringOr("owner", "");
            // tameProgress is a new field (B1): default 0 for old saves. An owner present in an old save (no
            // tameProgress key) means the spider was tamed under the previous instant mechanic, so it is
            // treated as fully tamed. getIntOr supplies that default via the single-arg compat constructor.
            int defaultProgress = owner.isBlank() ? 0 : dev.molang.iamzombieq.rules.mount.ZombieMountRules.SPIDER_TAME_PROGRESS_THRESHOLD;
            int progress = input.getIntOr("tameProgress", defaultProgress);
            return new SpiderMountData(owner, progress);
        }

        @Override
        public boolean write(SpiderMountData attachment, ValueOutput output) {
            output.putString("owner", attachment.ownerUuid());
            output.putInt("tameProgress", attachment.tameProgress());
            return true;
        }
    }

    private static final class SpiderMountDataSync implements StreamCodec<RegistryFriendlyByteBuf, SpiderMountData> {
        private static final SpiderMountDataSync INSTANCE = new SpiderMountDataSync();

        @Override
        public SpiderMountData decode(RegistryFriendlyByteBuf input) {
            String owner = input.readUtf();
            int progress = input.readVarInt();
            return new SpiderMountData(owner, progress);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf output, SpiderMountData value) {
            output.writeUtf(value.ownerUuid());
            output.writeVarInt(value.tameProgress());
        }
    }

    private static final class HerobrineRespawnSnapshotSerializer
            implements IAttachmentSerializer<HerobrineRespawnSnapshot> {
        @Override
        public HerobrineRespawnSnapshot read(IAttachmentHolder holder, ValueInput input) {
            // Absence marker: written true only for a real pending snapshot. Old saves / never-written
            // attachments default to false → EMPTY, so readers treat them as "no pending snapshot".
            if (!input.getBooleanOr("present", false)) {
                return HerobrineRespawnSnapshot.EMPTY;
            }
            double x = input.getDoubleOr("x", 0.0);
            double y = input.getDoubleOr("y", 0.0);
            double z = input.getDoubleOr("z", 0.0);
            float yRot = input.getFloatOr("yRot", 0.0F);
            float xRot = input.getFloatOr("xRot", 0.0F);
            int experienceLevel = input.getIntOr("experienceLevel", 0);
            float experienceProgress = input.getFloatOr("experienceProgress", 0.0F);
            int totalExperience = input.getIntOr("totalExperience", 0);

            // Slot-faithful inventory restore mirroring vanilla Inventory.load: each entry carries its
            // slot index, so non-empty slots land back at their exact index and empty slots stay empty.
            // inventorySize records the full container size so the rebuilt list matches the snapshot shape.
            int inventorySize = input.getIntOr("inventorySize", 0);
            List<ItemStack> inventory = new ArrayList<>(inventorySize);
            for (int slot = 0; slot < inventorySize; slot++) {
                inventory.add(ItemStack.EMPTY);
            }
            for (ItemStackWithSlot entry : input.listOrEmpty("inventory", ItemStackWithSlot.CODEC)) {
                if (entry.slot() >= 0 && entry.slot() < inventory.size()) {
                    inventory.set(entry.slot(), entry.stack());
                }
            }
            return new HerobrineRespawnSnapshot(true, x, y, z, yRot, xRot, inventory,
                    experienceLevel, experienceProgress, totalExperience);
        }

        @Override
        public boolean write(HerobrineRespawnSnapshot attachment, ValueOutput output) {
            // Skip writing the EMPTY sentinel entirely so a cleared attachment leaves no stale NBT.
            if (!attachment.isPresent()) {
                return false;
            }
            output.putBoolean("present", true);
            output.putDouble("x", attachment.x());
            output.putDouble("y", attachment.y());
            output.putDouble("z", attachment.z());
            output.putFloat("yRot", attachment.yRot());
            output.putFloat("xRot", attachment.xRot());
            output.putInt("experienceLevel", attachment.experienceLevel());
            output.putFloat("experienceProgress", attachment.experienceProgress());
            output.putInt("totalExperience", attachment.totalExperience());

            // Write only non-empty slots (by index), mirroring vanilla Inventory.save. inventorySize lets
            // read() rebuild a same-size slot-indexed list so the restored shape matches the snapshot.
            List<ItemStack> inventory = attachment.inventory();
            output.putInt("inventorySize", inventory.size());
            ValueOutput.TypedOutputList<ItemStackWithSlot> list = output.list("inventory", ItemStackWithSlot.CODEC);
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty()) {
                    list.add(new ItemStackWithSlot(slot, stack));
                }
            }
            return true;
        }
    }

    private static final class HerobrineEncounterStateSerializer
            implements IAttachmentSerializer<HerobrineEncounterState> {
        @Override
        public HerobrineEncounterState read(IAttachmentHolder holder, ValueInput input) {
            int sightings = input.getIntOr("sightings", 0);
            long lastSightingTick = input.getLongOr("lastSightingTick", Long.MIN_VALUE);
            long lastLethalTick = input.getLongOr("lastLethalTick", -1L);
            boolean escalatedBefore = input.getBooleanOr("escalatedBefore", false);
            return new HerobrineEncounterState(sightings, lastSightingTick, lastLethalTick, escalatedBefore);
        }

        @Override
        public boolean write(HerobrineEncounterState attachment, ValueOutput output) {
            output.putInt("sightings", attachment.sightings);
            output.putLong("lastSightingTick", attachment.lastSightingTick);
            output.putLong("lastLethalTick", attachment.lastLethalTick);
            output.putBoolean("escalatedBefore", attachment.escalatedBefore);
            return true;
        }
    }
}
