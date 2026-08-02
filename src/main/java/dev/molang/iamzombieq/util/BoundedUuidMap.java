package dev.molang.iamzombieq.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Factory for the mod's transient, server-thread-only bounded maps (A5). Several gameplay event classes keep a
 * short-lived {@code Map<UUID, ?>} of in-flight state (pending horse-death snapshots, conversion-grace markers,
 * player grudges) that would otherwise leak an entry whenever the keyed entity dies from an unrelated cause. Each
 * used an identical anonymous {@link LinkedHashMap} subclass overriding {@link LinkedHashMap#removeEldestEntry}
 * with a fixed capacity; this factory collapses that duplicated boilerplate into one place.
 *
 * <p>Behaviour is byte-identical to the previous anonymous subclasses: a {@code LinkedHashMap(16, 0.75F, false)}
 * with <b>insertion-order</b> eviction (accessOrder=false), whose {@code removeEldestEntry} drops the eldest
 * (least-recently-inserted) entry once the map exceeds {@code cap}. Each map's cap value, cleanup timing, and
 * ownership are unchanged; this only removes the repeated subclass.
 */
public final class BoundedUuidMap {

    private BoundedUuidMap() {
    }

    /**
     * A new insertion-order {@link LinkedHashMap} that evicts its eldest entry once {@code size() > cap}. Same
     * initial capacity / load factor / order as the anonymous subclasses it replaces.
     */
    public static <K, V> Map<K, V> newBounded(int cap) {
        return new LinkedHashMap<>(16, 0.75F, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > cap;
            }
        };
    }
}
