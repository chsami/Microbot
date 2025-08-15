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
    public static String version = "1.0";

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

    private void manageRunEnergy(AutoChompyKillerConfig config) {
        if (Microbot.getClient().getEnergy() >= 20) return;
        
        AutoChompyKillerConfig.RunEnergyOption energyOption = config.runEnergyOption();
        switch (energyOption) {
            case STAMINA_POTION:
                if (Rs2Inventory.contains(ItemID._4DOSESTAMINA, ItemID._3DOSESTAMINA, ItemID._2DOSESTAMINA, ItemID._1DOSESTAMINA)) {
                    Rs2Inventory.interact(ItemID._4DOSESTAMINA, "Drink");
                    if (!Rs2Inventory.contains(ItemID._4DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._3DOSESTAMINA, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._3DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._2DOSESTAMINA, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._2DOSESTAMINA)) {
                        Rs2Inventory.interact(ItemID._1DOSESTAMINA, "Drink");
                    }
                }
                break;
            case SUPER_ENERGY_POTION:
                if (Rs2Inventory.contains(ItemID._4DOSE2ENERGY, ItemID._3DOSE2ENERGY, ItemID._2DOSE2ENERGY, ItemID._1DOSE2ENERGY)) {
                    Rs2Inventory.interact(ItemID._4DOSE2ENERGY, "Drink");
                    if (!Rs2Inventory.contains(ItemID._4DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._3DOSE2ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._3DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._2DOSE2ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._2DOSE2ENERGY)) {
                        Rs2Inventory.interact(ItemID._1DOSE2ENERGY, "Drink");
                    }
                }
                break;
            case ENERGY_POTION:
                if (Rs2Inventory.contains(ItemID._4DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._1DOSE1ENERGY)) {
                    Rs2Inventory.interact(ItemID._4DOSE1ENERGY, "Drink");
                    if (!Rs2Inventory.contains(ItemID._4DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._3DOSE1ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._3DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._2DOSE1ENERGY, "Drink");
                    }
                    if (!Rs2Inventory.contains(ItemID._2DOSE1ENERGY)) {
                        Rs2Inventory.interact(ItemID._1DOSE1ENERGY, "Drink");
                    }
                }
                break;
            case STRANGE_FRUIT:
                if (Rs2Inventory.contains(ItemID.MACRO_TRIFFIDFRUIT)) {
                    Rs2Inventory.interact(ItemID.MACRO_TRIFFIDFRUIT, "Eat");
                }
                break;
            case NONE:
            default:
                break;
        }
    }
    
    private void dropConfiguredItems(AutoChompyKillerConfig config) {
        if (config.dropEmptyVials()) {
            dropIfPresent(ItemID.VIAL_EMPTY);
        }
    }
    
    private void dropIfPresent(int... itemIds) {
        for (int itemId : itemIds) {
            if (Rs2Inventory.contains(itemId)) {
                Rs2Inventory.drop(itemId);
            }
        }
    }

    private void handleLogoutOnCompletion(String reason) {
        Microbot.showMessage(reason + " - waiting to logout...");
        Microbot.status = "Waiting to logout";
        
        sleepUntil(() -> !Rs2Player.isInCombat(), 30000);
        
        if (Rs2Player.isInCombat()) {
            Microbot.showMessage("Still in combat after 30 seconds - logging out anyway...");
        }
        
        Microbot.status = "Logging out";
        Rs2Player.logout();
        sleepUntil(() -> !Microbot.isLoggedIn(), 5000);
        
        if (Microbot.isLoggedIn()) {
            Microbot.showMessage("Logout failed - stopping script...");
        }
        
        Microbot.status = "IDLE";
    }

    private boolean checkStopConditions(AutoChompyKillerConfig config) {
        if (config.stopOnKillCount() && chompyKills >= config.killCount()) {
            String reason = "Kill count reached (" + chompyKills + "/" + config.killCount() + ")";
            if (config.logoutOnCompletion()) {
                handleLogoutOnCompletion(reason);
            } else {
                Microbot.showMessage(reason + " - stopping");
                Microbot.status = "IDLE";
            }
            return true;
        }
        
        if (config.stopOnChompyChickPet()) {
            if (Rs2Inventory.contains(ItemID.CHOMPYBIRD_PET)) {
                String reason = "Chompy chick pet received";
                if (config.logoutOnCompletion()) {
                    handleLogoutOnCompletion(reason);
                } else {
                    Microbot.showMessage(reason + " - stopping");
                    Microbot.status = "IDLE";
                }
                return true;
            }
        }
        
        return false;
    }

    public boolean run(AutoChompyKillerConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (!Rs2Player.isMoving() && !Rs2Player.isInteracting()) {
                    dropConfiguredItems(config);
                    manageRunEnergy(config);
                }
                
                if (checkStopConditions(config)) {
                    shutdown();
                    return;
                }

                if (Rs2Player.isMoving() || (Rs2Player.isAnimating() && state != AutoChompyKillerState.INFLATING && state != AutoChompyKillerState.ATTACKING) || 
                    (Rs2Player.isInteracting() && state != AutoChompyKillerState.INFLATING && state != AutoChompyKillerState.ATTACKING)) {
                    return;
                }

                if (!Rs2Equipment.isWearing(EquipmentInventorySlot.AMMO)) {
                    Microbot.showMessage("No ammo - aborting...");
                    Microbot.status = "IDLE";
                    sleep(3000);
                    shutdown();
                    return;
                }

                if (!Rs2Equipment.isWearing(ItemID.OGRE_BOW) && !Rs2Equipment.isWearing(ItemID.ZOGRE_BOW)) {
                    Microbot.showMessage("No ogre bow equipped - aborting...");
                    Microbot.status = "IDLE";
                    sleep(3000);
                    shutdown();
                    return;
                }

                switch (state) {
                    case FILLING_BELLOWS:
                        Rs2GameObject.interact(ObjectID.SWAMPBUBBLES, "Suck");
                        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
                        state = AutoChompyKillerState.INFLATING;
                        break;

                    case INFLATING:
                        Rs2NpcModel chompyBird = getNearestReachableNpc(NpcID.CHOMPYBIRD);
                        if (chompyBird != null) {
                            state = AutoChompyKillerState.ATTACKING;
                            break;
                        }
                        
                        if (config.pluckChompys() && isDeadChompyNearby()) {
                            state = AutoChompyKillerState.PLUCKING;
                            break;
                        }
                        
                        if (Rs2Inventory.hasItem(ItemID.BLOATED_TOAD) && !isBloatedToadOnGround()) {
                            Rs2Inventory.drop(ItemID.BLOATED_TOAD);
                            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting());
                            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
                        } else {
                            if (!(Rs2Inventory.hasItem(ItemID.FILLED_OGRE_BELLOW1) || Rs2Inventory.hasItem(ItemID.FILLED_OGRE_BELLOW2) || Rs2Inventory.hasItem(ItemID.FILLED_OGRE_BELLOW3))) {
                                if (Rs2Inventory.hasItem(ItemID.EMPTY_OGRE_BELLOWS)) {
                                    state = AutoChompyKillerState.FILLING_BELLOWS;
                                } else {
                                    Microbot.showMessage("Bellows missing - aborting...");
                                    Microbot.status = "IDLE";
                                    sleep(10000);
                                    shutdown();
                                    return;
                                }
                            } else {
                                Rs2NpcModel swampToad = getNearestReachableNpc(NpcID.TOAD);
                                if (swampToad == null || !Rs2Npc.interact(swampToad, "Inflate")) {
                                    Microbot.showMessage("Could not find toads - aborting...");
                                    Microbot.status = "IDLE";
                                    sleep(10000);
                                    shutdown();
                                    return;
                                } else {
                                    sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting());
                                    sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
                                }
                            }
                        }
                        break;
                    case ATTACKING:
                        if (config.pluckChompys() && !Rs2Player.isInteracting() && !Rs2Player.isAnimating() && isDeadChompyNearby()) {
                            state = AutoChompyKillerState.PLUCKING;
                            break;
                        }
                        
                        Rs2NpcModel targetChompy = getNearestReachableNpc(NpcID.CHOMPYBIRD);
                        if (targetChompy != null && Rs2Npc.interact(targetChompy, "Attack")) {
                            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting());
                            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
                        } else {
                            state = AutoChompyKillerState.INFLATING;
                        }
                        break;
                    case PLUCKING:
                        Rs2NpcModel deadChompy = getNearestReachableNpc(NpcID.CHOMPYBIRD_DEAD);
                        if (deadChompy != null && Rs2Npc.interact(deadChompy, "Pluck")) {
                            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting());
                            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
                            
                            Rs2NpcModel nearbyChompy = getNearestReachableNpc(NpcID.CHOMPYBIRD);
                            if (nearbyChompy != null) {
                                state = AutoChompyKillerState.ATTACKING;
                            } else {
                                state = AutoChompyKillerState.INFLATING;
                            }
                        } else {
                            Rs2NpcModel nearbyChompy = getNearestReachableNpc(NpcID.CHOMPYBIRD);
                            if (nearbyChompy != null) {
                                state = AutoChompyKillerState.ATTACKING;
                            } else {
                                state = AutoChompyKillerState.INFLATING;
                            }
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
        Microbot.showMessage("Someone else is hunting chompys in this world - aborting...");
        Microbot.status = "IDLE";
        shutdown();
    }

    public void handleBowNotPowerfulEnough() {
        Microbot.showMessage("Your bow isn't powerful enough for those arrows - aborting...");
        Microbot.status = "IDLE";
        shutdown();
    }

    public void handlePetReceived(boolean logoutOnCompletion) {
        String reason = "Chompy chick pet received from chat message";
        if (logoutOnCompletion) {
            handleLogoutOnCompletion(reason);
        } else {
            Microbot.showMessage(reason + " - stopping...");
            Microbot.status = "IDLE";
        }
        shutdown();
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
                Microbot.status = "IDLE";
                shutdown();
            }
        } else {
            Microbot.showMessage("No bubbles found - stopping");
            Microbot.status = "IDLE";
            shutdown();
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}