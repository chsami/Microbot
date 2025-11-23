package net.runelite.client.plugins.microbot.api.tileitem;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.AbstractEntityQueryable;
import net.runelite.client.plugins.microbot.api.IEntityQueryable; // optional
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;

import java.util.stream.Stream;

public final class Rs2TileItemQueryable extends AbstractEntityQueryable<Rs2TileItemQueryable, Rs2TileItemModel>
        implements IEntityQueryable<Rs2TileItemQueryable, Rs2TileItemModel> {

    public Rs2TileItemQueryable() {
        super();
    }

    @Override
    protected Stream<Rs2TileItemModel> initialSource() {
        return Rs2TileItemCache.getGroundItemsStream();
    }
}
