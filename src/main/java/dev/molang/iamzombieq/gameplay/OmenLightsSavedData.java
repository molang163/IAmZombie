package dev.molang.iamzombieq.gameplay;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.molang.iamzombieq.util.ModIds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Per-level durable record of lit blocks Herobrine's omen has extinguished, so the reversible
 * relight survives a server restart instead of stranding the block dark forever. Held as a
 * {@link SavedData} on each {@code ServerLevel}'s data storage (obtained via
 * {@code level.getDataStorage().computeIfAbsent(TYPE)}), so the dimension is implicit in the
 * per-level storage and the map needs only a {@link BlockPos} key (the old dimension-qualified
 * OmenKey is no longer needed). Server-thread only.
 *
 * <p>Persistence is codec-based (26.2 SavedData API): {@link #CODEC} serializes the map as a list of
 * {@link Entry} records and {@link #TYPE} wires it into the data-storage lifecycle.
 */
public final class OmenLightsSavedData extends SavedData {
    /** Original blockstate to relight + the level game-time tick at which to restore it. */
    public record OmenLight(BlockState original, long restoreAt) {
    }

    // One serialized entry: position + the OmenLight payload, flattened so BlockPos and BlockState
    // each ride their own stable vanilla codec (BlockPos.CODEC / BlockState.CODEC are lossless).
    private record Entry(BlockPos pos, BlockState original, long restoreAt) {
        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(
                i -> i.group(
                        BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                        BlockState.CODEC.fieldOf("state").forGetter(Entry::original),
                        Codec.LONG.fieldOf("restoreAt").forGetter(Entry::restoreAt)
                ).apply(i, Entry::new)
        );
    }

    public static final Codec<OmenLightsSavedData> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    Entry.CODEC.listOf().optionalFieldOf("lights", List.of()).forGetter(OmenLightsSavedData::toEntries)
            ).apply(i, OmenLightsSavedData::fromEntries)
    );

    // No DataFixTypes: NeoForge's patched SavedDataType supports a null data-fixer (no DFU update
    // logic for mod data), and on version-pinned 26.2 this data is always written at the current
    // version, so no datafixer is ever expected to run. The 3-arg constructor passes null for us.
    public static final SavedDataType<OmenLightsSavedData> TYPE = new SavedDataType<>(
            ModIds.id("herobrine_omen_lights"),
            OmenLightsSavedData::new,
            CODEC
    );

    private final Map<BlockPos, OmenLight> lights = new HashMap<>();

    public OmenLightsSavedData() {
    }

    private List<Entry> toEntries() {
        return lights.entrySet().stream()
                .map(e -> new Entry(e.getKey(), e.getValue().original(), e.getValue().restoreAt()))
                .toList();
    }

    private static OmenLightsSavedData fromEntries(List<Entry> entries) {
        OmenLightsSavedData data = new OmenLightsSavedData();
        for (Entry entry : entries) {
            data.lights.put(entry.pos(), new OmenLight(entry.original(), entry.restoreAt()));
        }
        return data;
    }

    public Map<BlockPos, OmenLight> getMap() {
        return lights;
    }

    public void put(BlockPos pos, BlockState original, long restoreAt) {
        lights.put(pos.immutable(), new OmenLight(original, restoreAt));
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (lights.remove(pos) != null) {
            setDirty();
        }
    }
}
