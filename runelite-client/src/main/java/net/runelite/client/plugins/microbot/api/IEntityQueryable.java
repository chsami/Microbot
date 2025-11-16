package net.runelite.client.plugins.microbot.api;

import net.runelite.api.coords.WorldPoint;

public interface IEntityQueryable<Q extends IEntityQueryable<Q, E>, E> {
    Q where(java.util.function.Predicate<E> predicate);
    Q within(int distance);
    Q within(WorldPoint anchor, int distance);
    E first();
    E nearest();
    E nearest(int maxDistance);
    E nearest(WorldPoint anchor, int maxDistance);
    E withName(String name);
    E withNames(String...names);
    E withId(int id);
    E withIds(int...ids);
    java.util.List<E> toList();
}
