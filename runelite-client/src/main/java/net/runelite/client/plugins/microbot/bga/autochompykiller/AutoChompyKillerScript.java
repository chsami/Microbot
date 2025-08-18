package net.runelite.client.plugins.microbot.bga.autochompykiller;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
public class AutoChompyKillerScript extends Script {
    public static String version = "1.0";

    public static int chompyKills = 0;
    public static long startTime = 0;
    public AutoChompyKillerState state = AutoChompyKillerState.FILLING_BELLOWS;
    private long stateStartTime = System.currentTimeMillis();
    private AutoChompyKillerConfig config;

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

    private void changeState(AutoChompyKillerState newState) {
        if (newState != state) {
            log.info("State change: {} -> {}", state, newState);
            state = newState;
            stateStartTime = System.currentTimeMillis();
        }
    }

    private boolean validateConfig() {
        if (config == null) {
            log.info("Config is null");
            Microbot.showMessage("Configuration error");
            return false;
        }
        log.info("Config validation complete");
        return true;
    }

    public boolean run(AutoChompyKillerConfig config) {
        this.config = config;
        
        if (!validateConfig()) {
            shutdown();
            return false;
        }
        
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) {
                    log.info("Super.run() returned false");
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    log.info("Not logged in");
                    return;
                }
                
                long startTime = System.currentTimeMillis();
                
                if (System.currentTimeMillis() - stateStartTime > 60000) {
                    log.info("State timeout - resetting to FILLING_BELLOWS");
                    changeState(AutoChompyKillerState.FILLING_BELLOWS);
                    return;
                }

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
                    log.info("No ammo equipped - shutting down");
                    Microbot.showMessage("No ammo - aborting...");
                    Microbot.status = "IDLE";
                    // wait briefly before shutdown to display message
                    sleepUntil(() -> true, 3000);
                    shutdown();
                    return;
                }

                if (!Rs2Equipment.isWearing(ItemID.OGRE_BOW) && !Rs2Equipment.isWearing(ItemID.ZOGRE_BOW)) {
                    log.info("No ogre bow equipped - shutting down");
                    Microbot.showMessage("No ogre bow equipped - aborting...");
                    Microbot.status = "IDLE";
                    // wait briefly before shutdown to display message
                    sleepUntil(() -> true, 3000);
                    shutdown();
                    return;
                }

                switch (state) {
                    case FILLING_BELLOWS:
                        log.info("State: FILLING_BELLOWS");
                        Microbot.status = "Filling bellows";
                        if (Rs2GameObject.interact(ObjectID.SWAMPBUBBLES, "Suck")) {
                            boolean completed = sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 5000);
                            if (completed) {
                                log.info("Successfully filled bellows");
                                changeState(AutoChompyKillerState.INFLATING);
                            } else {
                                log.info("Failed to fill bellows within timeout");
                            }
                        } else {
                            log.info("Failed to interact with swamp bubbles");
                        }
                        break;

                    case INFLATING:
                        log.info("State: INFLATING");
                        Microbot.status = "Inflating toads";
                        
                        Rs2NpcModel chompyBird = getNearestReachableNpc(NpcID.CHOMPYBIRD);
                        if (chompyBird != null) {
                            log.info("Chompy bird found - switching to attacking");
                            changeState(AutoChompyKillerState.ATTACKING);
                            break;
                        }
                        
                        if (config.pluckChompys() && isDeadChompyNearby()) {
                            log.info("Dead chompy nearby - switching to plucking");
                            changeState(AutoChompyKillerState.PLUCKING);
                            break;
                        }
                        
                        if (Rs2Inventory.hasItem(ItemID.BLOATED_TOAD) && !isBloatedToadOnGround()) {
                            log.info("Dropping bloated toad");
                            Rs2Inventory.drop(ItemID.BLOATED_TOAD);
                            sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 3000);
                            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 5000);
                        } else {
                            // check if we have filled bellows to use
                            if (!(Rs2Inventory.hasItem(ItemID.FILLED_OGRE_BELLOW1) || Rs2Inventory.hasItem(ItemID.FILLED_OGRE_BELLOW2) || Rs2Inventory.hasItem(ItemID.FILLED_OGRE_BELLOW3))) {
                                // check if we need to fill empty bellows
                                if (Rs2Inventory.hasItem(ItemID.EMPTY_OGRE_BELLOWS)) {
                                    log.info("Have empty bellows, need to fill them");
                                    changeState(AutoChompyKillerState.FILLING_BELLOWS);
                                } else {
                                    log.info("No bellows found in inventory, shutting down script");
                                    Microbot.status = "IDLE";
                                    // wait before shutting down to display message
                                    sleepUntil(() -> true, 10000);
                                    shutdown();
                                    return;
                                }
                            } else {
                                // find a swamp toad to inflate
                                Rs2NpcModel swampToad = getNearestReachableNpc(NpcID.TOAD);
                                if (swampToad == null) {
                                    log.info("No swamp toads found nearby, shutting down script");
                                    Microbot.status = "IDLE";
                                    // wait before shutting down to display message
                                    sleepUntil(() -> true, 10000);
                                    shutdown();
                                    return;
                                }
                                
                                log.info("Found swamp toad, attempting to inflate it");
                                if (Rs2Npc.interact(swampToad, "Inflate")) {
                                    // wait for interaction to start
                                    boolean interactionStarted = sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 3000);
                                    if (interactionStarted) {
                                        log.info("Started inflating toad");
                                    } else {
                                        log.info("Inflation interaction did not start");
                                    }
                                    
                                    // wait for animation to complete
                                    boolean completed = sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 5000);
                                    log.info("Inflation animation completed: {}", completed);
                                    
                                    // verify we have a bloated toad after inflation
                                    if (Rs2Inventory.hasItem(ItemID.BLOATED_TOAD)) {
                                        log.info("Successfully created bloated toad");
                                    } else {
                                        log.info("No bloated toad found after inflation");
                                    }
                                } else {
                                    log.info("Failed to interact with swamp toad");
                                }
                            }
                        }
                        break;
                    case ATTACKING:
                        log.info("State: ATTACKING");
                        Microbot.status = "Attacking chompy";
                        
                        if (config.pluckChompys() && !Rs2Player.isInteracting() && !Rs2Player.isAnimating() && isDeadChompyNearby()) {
                            log.info("Dead chompy nearby - switching to plucking");
                            changeState(AutoChompyKillerState.PLUCKING);
                            break;
                        }
                        
                        Rs2NpcModel targetChompy = getNearestReachableNpc(NpcID.CHOMPYBIRD);
                        if (targetChompy != null) {
                            log.info("Attacking chompy");
                            if (Rs2Npc.interact(targetChompy, "Attack")) {
                                sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 3000);
                                boolean completed = sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 10000);
                                log.info("Attack animation completed: {}", completed);
                            } else {
                                log.info("Failed to attack chompy");
                            }
                        } else {
                            log.info("No chompy found - returning to inflating");
                            changeState(AutoChompyKillerState.INFLATING);
                        }
                        break;
                    case PLUCKING:
                        log.info("State: PLUCKING");
                        Microbot.status = "Plucking chompy";
                        
                        Rs2NpcModel deadChompy = getNearestReachableNpc(NpcID.CHOMPYBIRD_DEAD);
                        if (deadChompy != null) {
                            log.info("Plucking dead chompy");
                            if (Rs2Npc.interact(deadChompy, "Pluck")) {
                                sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isInteracting(), 3000);
                                boolean completed = sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting(), 5000);
                                log.info("Plucking animation completed: {}", completed);
                            } else {
                                log.info("Failed to pluck dead chompy");
                            }
                        } else {
                            log.info("No dead chompy found");
                        }
                        
                        Rs2NpcModel nearbyChompy = getNearestReachableNpc(NpcID.CHOMPYBIRD);
                        if (nearbyChompy != null) {
                            log.info("Chompy nearby - switching to attacking");
                            changeState(AutoChompyKillerState.ATTACKING);
                        } else {
                            log.info("No chompy nearby - switching to inflating");
                            changeState(AutoChompyKillerState.INFLATING);
                        }
                        break;
                    case STOPPED:
                        log.info("State: STOPPED");
                        return;
                }
                
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                log.info("Total time for loop: {}ms", totalTime);

            } catch (Exception ex) {
                log.error("Error in main loop", ex);
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