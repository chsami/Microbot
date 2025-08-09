package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.Rs2NpcCache;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Comparator;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class EggEvent implements BlockingEvent {
    @Override
    public boolean validate() {
        var pheasantNests = Rs2GameObject.getGameObjects(Rs2GameObject.nameMatches("pheasant nest", false));
        if (pheasantNests == null) return false;
        return !pheasantNests.isEmpty();
    }

    @Override
    public boolean execute() {

        Microbot.log("EggEvent: Executing Egg event");
        var forester = Rs2NpcCache.getClosestNpcByGameId(NpcID.GATHERING_EVENT_PHEASANT_FORESTER);
        if (forester.isEmpty()) {
            Microbot.log("EggEvent: Forester not found, cannot proceed with egg event.");
            return false; // If the forester is not found, we cannot proceed with the event
        }

        while (this.validate()) {

            // If we have an egg, interact with the forester
            if (Rs2Inventory.contains("Pheasant egg")) {
                Microbot.log("EggEvent: Interacting with the forester to give the egg.");
                Rs2Npc.interact(forester.get(), "Talk-to");
                sleepUntil(() -> Rs2Widget.findWidget("Freaky Forester", null, false) != null, 5000);
                continue;
            }

            // If we don't have an egg, interact with the pheasant nest
            var nests = Rs2GameObject.getGameObjects(ObjectID.GATHERING_EVENT_PHEASANT_NEST02);
            if (nests.isEmpty()) {
                Microbot.log("EggEvent: No pheasant nests found, cannot proceed with egg event.");
                continue;
            }
            Microbot.log("EggEvent: Interacting with the pheasant nest to collect an egg.");
            var closestNest = nests.stream().filter(Rs2GameObject::isReachable).
                    min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                    .orElse(null);

            Rs2GameObject.interact(closestNest, "Retrieve-egg");
            Rs2Player.waitForAnimation();
        }
        Microbot.log("EggEvent: Ending Egg event.");
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
