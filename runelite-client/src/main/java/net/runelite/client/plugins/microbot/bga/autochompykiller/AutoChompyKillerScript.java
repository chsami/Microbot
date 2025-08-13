package net.runelite.client.plugins.microbot.bga.autochompykiller;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameObject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class AutoChompyKillerScript extends Script {
    public static double version = 1.0;

    public static int chompyKills = 0;
    public static long startTime = 0;
    public AutoChompyKillerState state = AutoChompyKillerState.FILLING_BELLOWS;

    private boolean isBloatedToadOnGround() {
        Stream<Rs2NpcModel> npcs = Rs2Npc.getNpcs();
        long toadCount = npcs.filter(element -> element.getWorldLocation().equals(Rs2Player.getWorldLocation()) && element.getId() == NpcID.BLOATED_TOAD).count();

        return toadCount > 0;
    }

    private boolean isDeadChompyNearby() {
        Stream<Rs2NpcModel> npcs = Rs2Npc.getNpcs();
        long deadChompyCount = npcs.filter(element -> element.getId() == NpcID.CHOMPYBIRD_DEAD && Rs2Player.getWorldLocation().distanceTo(element.getWorldLocation()) <= 5).count();

        return deadChompyCount > 0;
    }

    private Rs2NpcModel getNearestReachableNpc(int npcId) {
        Rs2WorldPoint playerLocation = new Rs2WorldPoint(Rs2Player.getWorldLocation());
        return Rs2Npc.getNpcs(npc -> npc.getId() == npcId)
                .min(java.util.Comparator.comparingInt(npc -> 
                    playerLocation.distanceToPath(npc.getWorldLocation())))
                .orElse(null);
    }

    public boolean run(AutoChompyKillerConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (Rs2Player.isMoving() || (Rs2Player.isAnimating() && state != AutoChompyKillerState.INFLATING) || 
                    (Rs2Player.isInteracting() && state != AutoChompyKillerState.INFLATING)) {
                    return;
                }

                if (!Rs2Equipment.isWearing(EquipmentInventorySlot.AMMO)) {
                    Microbot.showMessage("No ammo - stopping");
                    sleep(3000);
                    state = AutoChompyKillerState.STOPPED;
                }

                if (!Rs2Equipment.isWearing(ItemID.OGRE_BOW) && !Rs2Equipment.isWearing(ItemID.ZOGRE_BOW)) {
                    Microbot.showMessage("No ogre bow equipped - stopping");
                    sleep(3000);
                    state = AutoChompyKillerState.STOPPED;
                }

                switch (state) {
                    case FILLING_BELLOWS:
                        Rs2GameObject.interact(ObjectID.SWAMPBUBBLES, "Suck");
                        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
                        state = AutoChompyKillerState.INFLATING;
                        break;

                    case INFLATING:
                        if (isDeadChompyNearby()) {
                            state = AutoChompyKillerState.PLUCKING;
                            break;
                        }
                        
                        Rs2NpcModel chompyBird = getNearestReachableNpc(NpcID.CHOMPYBIRD);
                        if (chompyBird != null && Rs2Npc.interact(chompyBird, "Attack")) {
                            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting());
                            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
                            break;
                        } else if (Rs2Inventory.hasItem(ItemID.BLOATED_TOAD) && !isBloatedToadOnGround()) {
                            Rs2Inventory.drop(ItemID.BLOATED_TOAD);
                            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting());
                            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
                        } else {
                            if (!(Rs2Inventory.hasItem(ItemID.FILLED_OGRE_BELLOW1) || Rs2Inventory.hasItem(ItemID.FILLED_OGRE_BELLOW2) || Rs2Inventory.hasItem(ItemID.FILLED_OGRE_BELLOW3))) {
                                if (Rs2Inventory.hasItem(ItemID.EMPTY_OGRE_BELLOWS)) {
                                    state = AutoChompyKillerState.FILLING_BELLOWS;
                                } else {
                                    Microbot.showMessage("You need bellows - aborting...");
                                    sleep(10000);
                                    state = AutoChompyKillerState.STOPPED;
                                }
                            } else {
                                Rs2NpcModel swampToad = getNearestReachableNpc(NpcID.TOAD);
                                if (swampToad == null || !Rs2Npc.interact(swampToad, "Inflate")) {
                                    Microbot.showMessage("Could not find toads - aborting...");
                                    sleep(10000);
                                    state = AutoChompyKillerState.STOPPED;
                                } else {
                                    sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting());
                                    sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
                                }
                            }
                        }
                        break;
                    case PLUCKING:
                        Rs2NpcModel deadChompy = getNearestReachableNpc(NpcID.CHOMPYBIRD_DEAD);
                        if (deadChompy != null && Rs2Npc.interact(deadChompy, "Pluck")) {
                            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting());
                            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
                            state = AutoChompyKillerState.INFLATING;
                        } else {
                            state = AutoChompyKillerState.INFLATING;
                        }
                        break;
                    case STOPPED:
                        return;
                }


            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public void startup() {
        chompyKills = 0;
        startTime = System.currentTimeMillis();
    }

    public void incrementChompyKills() {
        chompyKills += 1;
    }

    public void handleNotMyChompy() {
        state = AutoChompyKillerState.STOPPED;
        Microbot.showMessage("Someone else is hunting chompys in this world - aborting...");
    }

    public void handleBowNotPowerfulEnough() {
        state = AutoChompyKillerState.STOPPED;
        Microbot.showMessage("Your bow isn't powerful enough for those arrows - aborting...");
    }

    public void handleCantReachBubbles() {
        List<GameObject> bubbles = Rs2GameObject.getGameObjects(obj -> obj.getId() == ObjectID.SWAMPBUBBLES);
        if (bubbles != null && !bubbles.isEmpty()) {
            Random rand = new Random();
            GameObject bubble = bubbles.get(rand.nextInt(bubbles.size()));
            
            if (bubble != null) {
                Rs2GameObject.interact(bubble, "Suck");
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
                state = AutoChompyKillerState.INFLATING;
            } else {
                Microbot.showMessage("No bubbles available - stopping");
                state = AutoChompyKillerState.STOPPED;
            }
        } else {
            Microbot.showMessage("No bubbles found - stopping");
            state = AutoChompyKillerState.STOPPED;
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}