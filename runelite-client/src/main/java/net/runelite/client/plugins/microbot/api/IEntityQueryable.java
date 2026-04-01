package net.runelite.client.plugins.microbot.api;

import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

import java.util.List;
import java.util.function.Predicate;

public interface IEntityQueryable<Q extends IEntityQueryable<Q, E>, E extends IEntity> {
    Q fromWorldView();
    Q where(Predicate<E> predicate);

    Q within(int distance);

    Q within(WorldPoint anchor, int distance);

    Q withName(String name);

    Q withNames(String... names);

    Q withId(int id);

    Q withIds(int... ids);

    E first();

    E firstReachable();

    E nearest();
    E nearestReachable();
    E nearestReachable(int maxDistance);
    E nearest(int maxDistance);

    E nearest(WorldPoint anchor, int maxDistance);

    List<E> toList();

    int count();

    E firstOnClientThread();

    E nearestOnClientThread();

    E nearestOnClientThread(int maxDistance);

    E nearestOnClientThread(WorldPoint anchor, int maxDistance);

    List<E> toListOnClientThread();

    boolean interact();

    boolean interact(String action);

    boolean interact(String action, int maxDistance);

    boolean interact(int id);

    boolean interact(int id, String action);

    boolean interact(int id, String action, int maxDistance);
}
