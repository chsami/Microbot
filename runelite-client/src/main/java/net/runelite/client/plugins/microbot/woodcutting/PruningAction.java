package net.runelite.client.plugins.microbot.woodcutting;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

/**
 * Represents a pruning action to be performed on an entling.
 * Contains necessary information about the entling and methods to interact with it.
 */
public class PruningAction {
    @Getter
    private final Rs2NpcModel entling;
    @Getter
    private final WorldPoint location;
    @Getter
    private final String action;

    /**
     * Creates a new pruning action for the specified entling.
     *
     * @param entling The entling game object to be pruned
     */
    public PruningAction(Rs2NpcModel entling, String action) {
        this.entling = entling;
        this.location = entling.getWorldLocation();
        this.action = action;
    }

    /**
     * Checks if the entling is valid and can be pruned.
     *
     * @return true if the entling can be pruned, false otherwise
     */
    public boolean isValid() {
        return action != null && Rs2Npc.hasLineOfSight(entling);
    }

    /**
     * Executes the pruning action on the entling.
     *
     * @return true if the pruning action was successfully executed, false otherwise
     */
    public boolean execute() {
        if (!isValid()) {
            return false;
        }

        return Rs2Npc.interact(entling, action);
    }
}