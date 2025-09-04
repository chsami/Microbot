package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.Actor;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.Rs2ObjectCache;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.skills.woodcutting.Rs2Woodcutting;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import lombok.extern.slf4j.Slf4j;
@Slf4j
public class RootEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;
    public RootEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        try{
            if (plugin == null || !Microbot.isPluginEnabled(plugin)) return false;
            if (Microbot.getClient() == null || !Microbot.isLoggedIn()) return false;
            var root = Rs2ObjectCache.getClosestObjectById(ObjectID.GATHERING_EVENT_RISING_ROOTS).orElse(null);
            var specialRoot = Rs2ObjectCache.getClosestObjectById(ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL).orElse(null);

            // Is the hasAction Check needed?
            // If special root is present
            if (specialRoot != null)
                return Rs2GameObject.hasAction(specialRoot.getObjectComposition(), "Chop down");
            // If regular root is present
            if (root != null)
                return (Rs2GameObject.hasAction(root.getObjectComposition(), "Chop down"));

            return false; // No roots found
        } catch (Exception e) {
            log.error("RootEvent: Exception in validate method", e);
            return false;
        }

    }

    @Override
    public boolean execute() {
        Microbot.log("RootEvent: Executing Root event");
        plugin.currentForestryEvent = ForestryEvents.TREE_ROOT;
        Rs2Walker.setTarget(null); // stop walking, stop moving to bank for example
        while (this.validate()) {
            var root = Rs2ObjectCache.getClosestObjectById(ObjectID.GATHERING_EVENT_RISING_ROOTS).orElse(null);
            var specialRoot = Rs2ObjectCache.getClosestObjectById(ObjectID.GATHERING_EVENT_RISING_ROOTS_SPECIAL).orElse(null);

            // Use special attack if available
            if (Rs2Woodcutting.isWearingAxeWithSpecialAttack())
                Rs2Combat.setSpecState(true, 1000);

            // If special root is present
            if (specialRoot != null) {

                // Check if the player is already interacting with the special root
                if (Rs2Player.isInteracting() && Rs2Player.getInteracting() != null) {
                    Actor interactingNpc = Microbot.getClient().getLocalPlayer().getInteracting();
                    if (interactingNpc.getWorldLocation().equals(specialRoot.getWorldLocation())) {
                        continue;
                    }
                }
                // Interact with the special root
                Microbot.log("RootEvent: Interacting with special root at " + specialRoot.getWorldLocation());
                Rs2GameObject.interact(specialRoot.getTileObject(), "Chop down");
                Rs2Player.waitForAnimation(5000);
                sleepUntil(() -> !Rs2Player.isInteracting(), 40000);
            }
            // If regular root is present
            else if (root != null) {

                // Check if the player is already interacting with the root
                if (Rs2Player.isInteracting() && Rs2Player.getInteracting() != null) {
                    Actor interactingNpc = Microbot.getClient().getLocalPlayer().getInteracting();
                    if (interactingNpc.getWorldLocation().equals(root.getWorldLocation())) {
                        continue;
                    }
                }
                // Interact with the regular root
                Microbot.log("RootEvent: Interacting with regular root at " + root.getWorldLocation());
                Rs2GameObject.interact(root.getTileObject(), "Chop down");
                Rs2Player.waitForAnimation(5000);
                sleepUntil(() -> !Rs2Player.isInteracting(), 40000);
            }
        }
        plugin.incrementForestryEventCompleted();
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
