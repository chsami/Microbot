package net.runelite.client.plugins.microbot.util;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Global per-tick value cache.
 *
 * <p>Each entry stores its own populator. Calling {@link #getValue()} returns the
 * cached value when the stored tick matches the current game tick; otherwise the
 * populator is invoked, the result is stored with the current tick, and then returned.
 *
 * <p>Usage:
 * <pre>{@code
 * List<NPC> npcs = Rs2Cache.NPCS.getValue();
 * Player local = Rs2Cache.LOCAL_PLAYER.getValue();
 * }</pre>
 */
public enum Rs2Cache {

    LOCAL_PLAYER_POSITION(Rs2Player::getWorldLocation_Internal),
    LOCAL_PLAYER_WORLD_VIEW(Rs2Player::getWorldView_Internal),
    ;

    // ---------------------------------------------------------------------------
    // Internal state
    // ---------------------------------------------------------------------------

    private static final Map<Rs2Cache, Entry> CACHE = new EnumMap<>(Rs2Cache.class);

    private final Supplier<Object> populator;

    Rs2Cache(Supplier<Object> populator) {
        this.populator = populator;
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Returns the cached value for this entry.
     *
     * <p>If the value was already computed on the current game tick it is returned
     * directly. Otherwise the populator is called, the result is cached alongside
     * the current tick, and then returned.
     *
     * @param <T> expected return type
     * @return the (possibly freshly populated) cached value
     */
    @SuppressWarnings("unchecked")
    public <T> T getValue() {
        int currentTick = Microbot.getClient().getTickCount();
        Entry entry = CACHE.get(this);
        if (entry != null && entry.tick == currentTick) {
            return (T) entry.value;
        }
        Object value = populator.get();
        CACHE.put(this, new Entry(value, currentTick));
        return (T) value;
    }

    /**
     * Invalidates the cached value for this entry, forcing a fresh call to the
     * populator on the next {@link #getValue()} invocation.
     */
    public void invalidate() {
        CACHE.remove(this);
    }

    /**
     * Invalidates all cached entries.
     */
    public static void invalidateAll() {
        CACHE.clear();
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private static final class Entry {
        final Object value;
        final int tick;

        Entry(Object value, int tick) {
            this.value = value;
            this.tick = tick;
        }
    }
}
