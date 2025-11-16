package net.runelite.client.plugins.microbot.api.tileobject;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.AbstractEntityQueryable;
import net.runelite.client.plugins.microbot.api.IEntityQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Rs2TileObjectQueryable extends AbstractEntityQueryable<Rs2TileObjectQueryable, Rs2TileObjectModel>
        implements IEntityQueryable<Rs2TileObjectQueryable, Rs2TileObjectModel> {

    public Rs2TileObjectQueryable() {
        super();
    }

    @Override
    protected Stream<Rs2TileObjectModel> initialSource() {
        return Rs2TileObjectCache.getObjectsStream();
    }
}
