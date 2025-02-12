package net.runelite.client.plugins.microbot.util.npc;

import lombok.*;
import net.runelite.api.*;
import net.runelite.client.plugins.microbot.util.ActorModel;
import org.jetbrains.annotations.Nullable;

@Getter
public class Rs2NpcModel extends ActorModel implements NPC {

    private final NPC npc;

    public Rs2NpcModel(final NPC npc) {
        super(npc);
        this.npc = npc;
    }

    @Override
    public int getId() {
        return npc.getId();
    }

    @Override
    public int getIndex() {
        return npc.getIndex();
    }

    @Override
    public NPCComposition getComposition() {
        return npc.getComposition();
    }

    @Override
    public @Nullable NPCComposition getTransformedComposition() {
        return npc.getTransformedComposition();
    }

    @Override
    public @Nullable NpcOverrides getModelOverrides() {
        return npc.getModelOverrides();
    }

    @Override
    public @Nullable NpcOverrides getChatheadOverrides() {
        return npc.getChatheadOverrides();
    }
}
