package net.runelite.client.plugins.microbot.api.npc;

import net.runelite.client.plugins.microbot.api.AbstractEntityQueryable;
import net.runelite.client.plugins.microbot.api.IEntityQueryable;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

import java.util.stream.Stream;

public final class Rs2NpcQueryable extends AbstractEntityQueryable<Rs2NpcQueryable, Rs2NpcModel>
        implements IEntityQueryable<Rs2NpcQueryable, Rs2NpcModel> {

    public Rs2NpcQueryable() {
        super();
    }

    @Override
    protected Stream<Rs2NpcModel> initialSource() {
        return Rs2NpcCache.getNpcsStream();
    }
}
