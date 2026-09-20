package net.runelite.client.plugins.microbot.api;

import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
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

    @SuppressWarnings("unchecked")
    @Override
    public Q fromWorldView() {
        var worldView = new Rs2PlayerModel().getWorldView();
        if (worldView == null) {
            this.source = Stream.empty();
            return (Q) this;
        }

        this.source = this.source
                .filter(o -> o.getWorldView() == worldView);

        return (Q) this;
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
        java.util.Map.Entry<WorldView, WorldPoint> origin = playerOrigin();
        WorldPoint playerLoc = origin == null ? null : origin.getValue();
        WorldView view = origin == null ? null : origin.getKey();
        this.source = this.source.filter(entity -> entity.getWorldView() == view);
        if (playerLoc == null || view == null) {
            this.source = Stream.empty();
            return (Q) this;
        }

        this.source = this.source
                .filter(o -> validDistance(o.getSceneWorldLocation(), playerLoc, distance));

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
                .filter(o -> validDistance(o.getSceneWorldLocation(), anchor, distance));

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
    public Q withNameContains(String substring) {
        if (substring == null || substring.isEmpty()) {
            this.source = Stream.empty();
            return (Q) this;
        }

        String needle = substring.toLowerCase(java.util.Locale.ROOT);
        this.source = this.source.filter(x -> {
            String n = x.getName();
            return n != null && n.toLowerCase(java.util.Locale.ROOT).contains(needle);
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
    public E firstReachable() {
        return source.filter(IEntity::isReachable).findFirst().orElse(null);
    }

    @Override
    public E nearest() {
        return nearest(Integer.MAX_VALUE);
    }

    @Override
    public E nearestReachable() {
        return nearestReachable(Integer.MAX_VALUE);
    }

    @Override
    public E nearestReachable(int maxDistance) {
        try {
            java.util.Map.Entry<WorldView, WorldPoint> origin = playerOrigin();
            WorldPoint playerLoc = origin == null ? null : origin.getValue();
            WorldView worldView = origin == null ? null : origin.getKey();
            if (playerLoc == null || worldView == null) {
                return null;
            }

            return source
                    .filter(entity -> entity.getWorldView() == worldView)
                    .filter(IEntity::isReachable)
                    .map(entity -> {
                        WorldPoint loc = entity.getSceneWorldLocation();
                        int distance = (loc != null) ? loc.distanceTo(playerLoc) : Integer.MAX_VALUE;
                        return new EntityDistance<>(entity, distance);
                    })
                    .filter(pair -> pair.distance != Integer.MAX_VALUE && pair.distance <= maxDistance)
                    .min(Comparator.comparingInt(pair -> pair.distance))
                    .map(pair -> pair.entity)
                    .orElse(null);
        } catch (RuntimeException e) {
            return returnNullIfInterrupted(e);
        }
    }

    @Override
    public E nearest(int maxDistance) {
        try {
            java.util.Map.Entry<WorldView, WorldPoint> origin = playerOrigin();
            WorldPoint playerLoc = origin == null ? null : origin.getValue();
            WorldView worldView = origin == null ? null : origin.getKey();
            if (playerLoc == null || worldView == null) {
                return null;
            }

            source = source.filter(entity -> entity.getWorldView() == worldView);
            return nearest(playerLoc, maxDistance);
        } catch (RuntimeException e) {
            return returnNullIfInterrupted(e);
        }
    }

    @Override
    public E nearest(WorldPoint anchor, int maxDistance) {
        if (anchor == null) {
            return null;
        }

        try {
            return source
                    .map(entity -> {
                        WorldPoint loc = entity.getSceneWorldLocation();
                        int distance = (loc != null) ? loc.distanceTo(anchor) : Integer.MAX_VALUE;
                        return new EntityDistance<>(entity, distance);
                    })
                    .filter(pair -> pair.distance != Integer.MAX_VALUE && pair.distance <= maxDistance)
                    .min(Comparator.comparingInt(pair -> pair.distance))
                    .map(pair -> pair.entity)
                    .orElse(null);
        } catch (RuntimeException e) {
            return returnNullIfInterrupted(e);
        }
    }

    private static java.util.Map.Entry<WorldView, WorldPoint> playerOrigin() {
        return Microbot.getClientThread().invoke(() -> {
            net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
            return player == null ? null : new java.util.AbstractMap.SimpleImmutableEntry<>(
                    player.getWorldView(), player.getWorldLocation());
        });
    }

    private static boolean validDistance(WorldPoint location, WorldPoint anchor, int bound) {
        if (location == null || anchor == null) return false;
        int distance = location.distanceTo(anchor);
        return distance != Integer.MAX_VALUE && distance <= bound;
    }

    private E returnNullIfInterrupted(RuntimeException e) {
        if (Thread.currentThread().isInterrupted() || e.getCause() instanceof InterruptedException) {
            return null;
        }
        throw e;
    }

    @Override
    public List<E> toList() {
        return source.collect(Collectors.toList());
    }

    @Override
    public int count() {
        return (int) source.count();
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

    public boolean interact() {
        E entity = nearestReachable();
        if (entity == null) return false;

        return entity.click();
    }

    public boolean interact(String action) {
        E entity = nearestReachable();
        if (entity == null) return false;
        return entity.click(action);
    }

    public boolean interact(String action, int maxDistance) {
        E entity = nearestReachable(maxDistance);
        if (entity == null) return false;
        return entity.click(action);
    }

    public boolean interact(int id) {
        E entity = this.withId(id).nearestReachable();
        if (entity == null) return false;
        return entity.click();
    }

    public boolean interact(int id, String action) {
        E entity = this.withId(id).nearestReachable();
        if (entity == null) return false;
        return entity.click(action);
    }

    public boolean interact(int id, String action, int maxDistance) {
        E entity = this.withId(id).nearestReachable(maxDistance);
        if (entity == null) return false;
        return entity.click(action);
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