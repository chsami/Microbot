package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;

import java.util.stream.Collectors;

public class FlowersEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;
    public FlowersEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        var flowers = Rs2Npc.getNpcs(npc ->
                npc.getName() != null && isFloweringBush(npc.getId())
        ).collect(Collectors.toList());
        if (flowers.isEmpty()) {
            return false; // No flowering bushes found, cannot proceed with the event
        }
        return false;
    }

    @Override
    public boolean execute() {
        // TODO
        // Implement execution logic for the event
        return false; // Placeholder return value
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL; // Set appropriate priority for this event
    }

    private static boolean isFloweringBush(int npcId)
    {
        return npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL01 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL02 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL03 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL04 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL05 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL06 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL07 ||
                npcId == NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL08;
    }
}
