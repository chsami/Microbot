package net.runelite.client.plugins.microbot.api;

import net.runelite.api.coords.WorldPoint;
import java.util.List;
import java.util.function.Predicate;

public interface IEntityQueryable<Q extends IEntityQueryable<Q, E>, E extends IEntity> {
    Q where(Predicate<E> predicate);
    Q within(int distance);
    Q within(WorldPoint anchor, int distance);
    Q withName(String name);
    Q withNames(String... names);
    Q withId(int id);
    Q withIds(int... ids);
    E first();
    E nearest();
    E nearest(int maxDistance);
    E nearest(WorldPoint anchor, int maxDistance);
    List<E> toList();
    E firstOnClientThread();
    E nearestOnClientThread();
    E nearestOnClientThread(int maxDistance);
    E nearestOnClientThread(WorldPoint anchor, int maxDistance);
    List<E> toListOnClientThread();

    default boolean interact() {
        E entity = nearest();
        if (entity == null) return false;
        return entity.click();
    }

    default boolean interact(String action) {
        E entity = nearest();
        if (entity == null) return false;
        return entity.click(action);
    }

    default boolean interact(String action, int maxDistance) {
        E entity = nearest(maxDistance);
        if (entity == null) return false;
        return entity.click(action);
    }

    default boolean interact(int id) {
        E entity = ((Q) this).withId(id).nearest();
        if (entity == null) return false;
        return entity.click();
    }

    default boolean interact(int id, String action) {
        E entity = ((Q) this).withId(id).nearest();
        if (entity == null) return false;
        return entity.click(action);
    }

    default boolean interact(int id, String action, int maxDistance) {
        E entity = ((Q) this).withId(id).nearest(maxDistance);
        if (entity == null) return false;
        return entity.click(action);
    }
}
