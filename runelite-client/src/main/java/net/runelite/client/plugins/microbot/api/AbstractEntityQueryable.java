package net.runelite.client.plugins.microbot.api;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic abstract implementation of {@link IEntityQueryable} to reduce duplication.
 *
 * @param <Q> concrete queryable type (self type)
 * @param <E> entity model type
 */
public abstract class AbstractEntityQueryable<
        Q extends IEntityQueryable<Q, E>,
        E extends IEntity
        >
        implements IEntityQueryable<Q, E> {

    protected Stream<E> source;

    protected AbstractEntityQueryable() {
        this.source = initialSource();
    }

    /**
     * Provide the initial stream to query against.
     */
    protected abstract Stream<E> initialSource();


    /**
     * Player location used for proximity queries.
     */
    protected WorldPoint getPlayerLocation() {
        return Rs2Player.getWorldLocation();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Q where(Predicate<E> predicate) {
        source = source.filter(predicate);
        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Q within(int distance) {
        WorldPoint playerLoc = getPlayerLocation();
        if (playerLoc == null) {
            return null;
        }

        this.source = this.source
                .filter(o -> o.getWorldLocation().distanceTo(playerLoc) <= distance);

        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Q within(WorldPoint anchor, int distance) {
        if (anchor == null) {
            return null;
        }

        this.source = this.source
                .filter(o -> o.getWorldLocation().distanceTo(anchor) <= distance);

        return (Q) this;
    }

    @Override
    public E first() {
        return source.findFirst().orElse(null);
    }

    @Override
    public E nearest() {
        WorldPoint playerLoc = getPlayerLocation();
        if (playerLoc == null) {
            return null;
        }
        return source
                .min(java.util.Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(playerLoc)))
                .orElse(null);
    }

    @Override
    public E nearest(int maxDistance) {
        WorldPoint playerLoc = getPlayerLocation();
        if (playerLoc == null) {
            return null;
        }
        return source
                .filter(x -> {
                    WorldPoint loc = x.getWorldLocation();
                    return loc != null && loc.distanceTo(playerLoc) <= maxDistance;
                })
                .min(java.util.Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(playerLoc)))
                .orElse(null);
    }

    @Override
    public E nearest(WorldPoint anchor, int maxDistance) {
        if (anchor == null) {
            return null;
        }
        return source
                .filter(x -> {
                    WorldPoint loc = x.getWorldLocation();
                    return loc != null && loc.distanceTo(anchor) <= maxDistance;
                })
                .min(java.util.Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(anchor)))
                .orElse(null);
    }

    @Override
    public E withName(String name) {
        if (name == null) return null;
        return Microbot.getClientThread().invoke(() -> {
            return source.filter(x -> {
                        String n = x.getName();
                        return n != null && n.equalsIgnoreCase(name);
                    })
                    .min(java.util.Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                    .orElse(null);
        });
    }

    @Override
    public E withNames(String... names) {
        if (names == null || names.length == 0) return null;
        return source.filter(x -> {
                    String n = x.getName();
                    if (n == null) return false;
                    return Arrays.stream(names).anyMatch(n::equalsIgnoreCase);
                }).min(java.util.Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                .orElse(null);
    }

    @Override
    public E withId(int id) {
        return source.filter(x -> x.getId() == id).min(java.util.Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                .orElse(null);
    }

    @Override
    public E withIds(int... ids) {
        if (ids == null || ids.length == 0) return null;
        return source.filter(x -> {
                    int entityId = x.getId();
                    for (int id : ids) {
                        if (entityId == id) return true;
                    }
                    return false;
                }).min(java.util.Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                .orElse(null);
    }

    @Override
    public List<E> toList() {
        return source.collect(Collectors.toList());
    }
}
