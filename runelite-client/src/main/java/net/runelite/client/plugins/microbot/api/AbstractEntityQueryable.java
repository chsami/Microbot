package net.runelite.client.plugins.microbot.api;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractEntityQueryable<
        Q extends IEntityQueryable<Q, E>,
        E extends IEntity
        >
        implements IEntityQueryable<Q, E> {

    protected Stream<E> source;

    protected AbstractEntityQueryable() {
        this.source = initialSource();
    }

    protected abstract Stream<E> initialSource();

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
            this.source = Stream.empty();
            return (Q) this;
        }

        this.source = this.source
                .filter(o -> o.getWorldLocation().distanceTo(playerLoc) <= distance);

        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Q within(WorldPoint anchor, int distance) {
        if (anchor == null) {
            this.source = Stream.empty();
            return (Q) this;
        }

        this.source = this.source
                .filter(o -> o.getWorldLocation().distanceTo(anchor) <= distance);

        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Q withName(String name) {
        if (name == null) {
            this.source = Stream.empty();
            return (Q) this;
        }

        this.source = this.source.filter(x -> {
            String n = x.getName();
            return n != null && n.equalsIgnoreCase(name);
        });

        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Q withNames(String... names) {
        if (names == null || names.length == 0) {
            this.source = Stream.empty();
            return (Q) this;
        }

        this.source = this.source.filter(x -> {
            String n = x.getName();
            if (n == null) return false;
            return Arrays.stream(names).anyMatch(n::equalsIgnoreCase);
        });

        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Q withId(int id) {
        this.source = this.source.filter(x -> x.getId() == id);
        return (Q) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Q withIds(int... ids) {
        if (ids == null || ids.length == 0) {
            this.source = Stream.empty();
            return (Q) this;
        }

        this.source = this.source.filter(x -> {
            int entityId = x.getId();
            for (int id : ids) {
                if (entityId == id) return true;
            }
            return false;
        });

        return (Q) this;
    }

    @Override
    public E first() {
        return source.findFirst().orElse(null);
    }

    @Override
    public E nearest() {
        return nearest(Integer.MAX_VALUE);
    }

    @Override
    public E nearest(int maxDistance) {
        WorldPoint playerLoc = getPlayerLocation();
        if (playerLoc == null) {
            return null;
        }

        return nearest(playerLoc, maxDistance);
    }

    @Override
    public E nearest(WorldPoint anchor, int maxDistance) {
        if (anchor == null) {
            return null;
        }

        return source
                .map(entity -> {
                    WorldPoint loc = entity.getWorldLocation();
                    int distance = (loc != null) ? loc.distanceTo(anchor) : Integer.MAX_VALUE;
                    return new EntityDistance<>(entity, distance);
                })
                .filter(pair -> pair.distance <= maxDistance)
                .min(Comparator.comparingInt(pair -> pair.distance))
                .map(pair -> pair.entity)
                .orElse(null);
    }

    @Override
    public List<E> toList() {
        return source.collect(Collectors.toList());
    }

    public E firstOnClientThread() {
        return Microbot.getClientThread().invoke(() -> first());
    }

    public E nearestOnClientThread() {
        return Microbot.getClientThread().invoke(() -> nearest());
    }

    public E nearestOnClientThread(int maxDistance) {
        return Microbot.getClientThread().invoke(() -> nearest(maxDistance));
    }

    public E nearestOnClientThread(WorldPoint anchor, int maxDistance) {
        return Microbot.getClientThread().invoke(() -> nearest(anchor, maxDistance));
    }

    public List<E> toListOnClientThread() {
        return Microbot.getClientThread().invoke(() -> toList());
    }
}



class EntityDistance<E> {
    final E entity;
    final int distance;

    EntityDistance(E entity, int distance) {
        this.entity = entity;
        this.distance = distance;
    }
}