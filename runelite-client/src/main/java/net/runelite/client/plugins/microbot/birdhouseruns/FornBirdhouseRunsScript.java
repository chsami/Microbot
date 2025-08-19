package net.runelite.client.plugins.microbot.birdhouseruns;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;
import net.runelite.client.config.Notification;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.birdhouseruns.FornBirdhouseRunsInfo.states;
import net.runelite.client.plugins.microbot.birdhouseruns.enums.Log;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.birdhouseruns.FornBirdhouseRunsInfo.*;

@Slf4j
public class FornBirdhouseRunsScript extends Script {
    private static final WorldPoint birdhouseLocation1 = new WorldPoint(3763, 3755, 0);
    private static final WorldPoint birdhouseLocation2 = new WorldPoint(3768, 3761, 0);
    private static final WorldPoint birdhouseLocation3 = new WorldPoint(3677, 3882, 0);
    private static final WorldPoint birdhouseLocation4 = new WorldPoint(3679, 3815, 0);
    public static double version = 1.0;
    private boolean initialized;
    private String setupErrorMessage = "";
    @Inject
    private Notifier notifier;
    private final FornBirdhouseRunsPlugin plugin;
    private final FornBirdhouseRunsConfig config;

    @Inject
    FornBirdhouseRunsScript(FornBirdhouseRunsPlugin plugin, FornBirdhouseRunsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = true;
        botStatus = states.GEARING;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
             
                if (!initialized) {
                    if (Rs2Player.getQuestState(Quest.BONE_VOYAGE) != QuestState.FINISHED) {
                        log.error("Birdhouse run failed, you need to finish the quest 'BONE VOYAGE'");
                        plugin.reportFinished("Birdhouse run failed, you need to finish the quest 'BONE VOYAGE'", false);
                        this.shutdown();
                        return;
                    }
                    initialized = true;
                    
                    if (config.useInventorySetup()) {
                        boolean hasInventorySetup = config.inventorySetup() != null && Rs2InventorySetup.isInventorySetup(config.inventorySetup().getName());
                        if (hasInventorySetup) {
                            var inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                            if (!inventorySetup.doesInventoryMatch() || !inventorySetup.doesEquipmentMatch()) {
                                Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
                                if (!inventorySetup.loadEquipment() || !inventorySetup.loadInventory()) {
                                    log.error("Birdhouse run failed to load inventory setup");
                                    plugin.reportFinished("Birdhouse run failed to load inventory setup", false);
                                    this.shutdown();
                                    return;
                                }
                                if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
                            }
                        } else {
                            log.error("Failed to load inventory, inventory setup not found: {}", config.inventorySetup());
                            plugin.reportFinished("Birdhouse run failed to load inventory setup", false);
                            this.shutdown();
                            return;
                        }
                    } else {
                        // Auto bank withdrawal
                        if (!setupManualInventory()) {
                            log.error("Birdhouse run failed: {}", setupErrorMessage);
                            plugin.reportFinished("Birdhouse run failed: " + setupErrorMessage, false);
                            this.shutdown();
                            return;
                        }
                    }
                    botStatus = states.TELEPORTING;
                }
                if (!super.run()) return;

                switch (botStatus) {
                    case TELEPORTING:
                        Rs2Walker.walkTo(new WorldPoint(3764, 3869, 1), 5);
                        botStatus = states.VERDANT_TELEPORT;
                        break;
                    case VERDANT_TELEPORT:
                        interactWithObject(30920);
                        sleepUntil(() -> Rs2Widget.findWidget("Mycelium Transportation System") != null);
                        Rs2Widget.clickWidget(39845895);
                        sleepUntil(() -> Rs2Player.distanceTo(birdhouseLocation1) < 20);
                        botStatus = states.DISMANTLE_HOUSE_1;
                        break;
                    case DISMANTLE_HOUSE_1:
                        dismantleBirdhouse(30568, states.BUILD_HOUSE_1);
                        break;
                    case BUILD_HOUSE_1:
                        buildBirdhouse(birdhouseLocation1, states.SEED_HOUSE_1);
                        break;
                    case SEED_HOUSE_1:
                        seedHouse(birdhouseLocation1, states.DISMANTLE_HOUSE_2);
                    case DISMANTLE_HOUSE_2:
                        dismantleBirdhouse(30567, states.BUILD_HOUSE_2);
                        break;
                    case BUILD_HOUSE_2:
                        buildBirdhouse(birdhouseLocation2, states.SEED_HOUSE_2);
                        break;
                    case SEED_HOUSE_2:
                        seedHouse(birdhouseLocation2, states.MUSHROOM_TELEPORT);
                        break;
                    case MUSHROOM_TELEPORT:
                        interactWithObject(30924);
                        sleepUntil(() -> Rs2Widget.findWidget("Mycelium Transportation System") != null);
                        Rs2Widget.clickWidget(39845903);
                        sleepUntil(() -> Rs2Player.distanceTo(birdhouseLocation3) < 20);
                        botStatus = states.DISMANTLE_HOUSE_3;
                        break;
                    case DISMANTLE_HOUSE_3:
                        dismantleBirdhouse(30565, states.BUILD_HOUSE_3);
                        break;
                    case BUILD_HOUSE_3:
                        buildBirdhouse(birdhouseLocation3, states.SEED_HOUSE_3);
                        break;
                    case SEED_HOUSE_3:
                        seedHouse(birdhouseLocation3, states.DISMANTLE_HOUSE_4);
                        break;
                    case DISMANTLE_HOUSE_4:
                        Rs2Walker.walkTo(new WorldPoint(3680, 3813, 0));
                        dismantleBirdhouse(30566, states.BUILD_HOUSE_4);
                        break;
                    case BUILD_HOUSE_4:
                        buildBirdhouse(birdhouseLocation4, states.SEED_HOUSE_4);
                        break;
                    case SEED_HOUSE_4:
                        seedHouse(birdhouseLocation4, states.FINISHING);
                        break;
                    case FINISHING:
                        emptyNests();
                        
                        if (config.goToBank()) {
                            Rs2Walker.walkTo(BankLocation.FOSSIL_ISLAND_WRECK.getWorldPoint());
                            if (!Rs2Bank.isOpen()) Rs2Bank.openBank();
                            Rs2Bank.depositAll();
                        }

                        botStatus = states.FINISHED;
                        notifier.notify(Notification.ON, "Birdhouse run is finished.");
                        plugin.reportFinished("Birdhouse run finished",true);
                        this.shutdown();
                        break;
                    case FINISHED:

                }

            } catch (Exception ex) {
                log.error("Error in birdhouse run script", ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void emptyNests() {
        var ids = List.of(
                ItemID.BIRD_NEST_EGG_RED,
                ItemID.BIRD_NEST_EGG_GREEN,
                ItemID.BIRD_NEST_EGG_BLUE,
                ItemID.BIRD_NEST_SEEDS,
                ItemID.BIRD_NEST_RING,
                ItemID.BIRD_NEST_SEEDS_JAN2019,
                ItemID.BIRD_NEST_DECENTSEEDS_JAN2019
        );

        Rs2Inventory.items().forEachOrdered(item -> {
            if (ids.contains(item.getId())) {
                Rs2Inventory.interact(item, "Search");
            }
        });
    }

    @Override
    public void shutdown() {
        super.shutdown();
        initialized = false;
        botStatus = states.TELEPORTING;
    }

    private boolean interactWithObject(int objectId) {
        Rs2GameObject.interact(objectId);
        sleepUntil(Rs2Player::isInteracting);
        sleepUntil(() -> !Rs2Player.isInteracting());
        return true;
    }

    private void seedHouse(WorldPoint worldPoint, states status) {
        Rs2Inventory.use(" seed");
        sleepUntil(Rs2Inventory::isItemSelected);
        Rs2GameObject.interact(worldPoint);
        sleepUntil(() -> Rs2Widget.findWidget("full of seed") != null, 1000);
        botStatus = status;
    }

    private void buildBirdhouse(WorldPoint worldPoint, states status) {
        if (!Rs2Inventory.hasItem("bird house") && Rs2Inventory.hasItem(ItemID.POH_CLOCKWORK_MECHANISM)) {
            Rs2Inventory.use(ItemID.HAMMER);
            Rs2Inventory.use(" logs");
            Rs2Inventory.waitForInventoryChanges(5000);
        }
        Rs2GameObject.interact(worldPoint, "Build");
        sleepUntil(Rs2Player::isAnimating);
        botStatus = status;
    }

    private void dismantleBirdhouse(int itemId, states status) {
        Rs2GameObject.interact(itemId, "Empty");
        Rs2Player.waitForXpDrop(Skill.HUNTER);
        botStatus = status;
    }

    private boolean setupManualInventory() {
        // Walk to nearest bank
        Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
        
        // Open bank
        if (!Rs2Bank.openBank()) {
            setupErrorMessage = "Could not open bank";
            log.error(setupErrorMessage);
            return false;
        }
        sleepUntil(Rs2Bank::isOpen);
        
        // Deposit all
        Rs2Bank.depositAll();
        Rs2Inventory.waitForInventoryChanges(5000);
        
        // Withdraw chisel
        if (!Rs2Bank.withdrawX(ItemID.CHISEL, 1)) {
            setupErrorMessage = "Missing chisel in bank";
            log.error(setupErrorMessage);
            return false;
        }
        
        // Withdraw hammer
        if (!Rs2Bank.withdrawX(ItemID.HAMMER, 1)) {
            setupErrorMessage = "Missing hammer in bank";
            log.error(setupErrorMessage);
            return false;
        }
        
        // Withdraw digsite pendant (prefer lower charges)
        boolean pendantWithdrawn = false;
        List<Integer> pendantIds = Arrays.asList(
            ItemID.NECKLACE_OF_DIGSITE_1,
            ItemID.NECKLACE_OF_DIGSITE_2,
            ItemID.NECKLACE_OF_DIGSITE_3,
            ItemID.NECKLACE_OF_DIGSITE_4,
            ItemID.NECKLACE_OF_DIGSITE_5
        );
        
        for (int pendantId : pendantIds) {
            if (!isRunning()) break;
            if (Rs2Bank.withdrawX(pendantId, 1)) {
                pendantWithdrawn = true;
                break;
            }
        }
        
        if (!pendantWithdrawn) {
            setupErrorMessage = "Missing digsite pendant in bank";
            log.error(setupErrorMessage);
            return false;
        }
        
        // Withdraw logs
		Log selectedLogType = config.logType();
        // Check if bank has enough logs first
        int logCount = Rs2Bank.count(selectedLogType.getItemId());
        if (logCount < 4) {
            setupErrorMessage = "Need 4 " + selectedLogType.getItemName().toLowerCase() + " but only have " + logCount + " in bank";
            log.error(setupErrorMessage);
            return false;
        }
        if (!Rs2Bank.withdrawX(selectedLogType.getItemId(), 4)) {
            setupErrorMessage = "Failed to withdraw " + selectedLogType.getItemName().toLowerCase();
            log.error(setupErrorMessage);
            return false;
        }
        
        // Withdraw seeds (smart selection)
        boolean seedsWithdrawn = withdrawSeeds();
        if (!seedsWithdrawn) {
            // setupErrorMessage is set in withdrawSeeds
            return false;
        }
        
        // Close bank
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        
        log.info("Inventory setup complete - starting birdhouse run");
        return true;
    }

    private boolean withdrawSeeds() {
        // Priority list of seeds for birdhouses
        List<Integer> seedIds = Arrays.asList(
            ItemID.POTATO_SEED,
            ItemID.ONION_SEED,
            ItemID.CABBAGE_SEED,
            ItemID.TOMATO_SEED,
            ItemID.BARLEY_SEED,
            ItemID.HAMMERSTONE_HOP_SEED,
            ItemID.YANILLIAN_HOP_SEED,
            ItemID.KRANDORIAN_HOP_SEED
        );
        
        for (int seedId : seedIds) {
            if (!isRunning()) break;
            // Check if bank has enough BEFORE trying to withdraw
            if (Rs2Bank.count(seedId) >= 40) {
                if (Rs2Bank.withdrawX(seedId, 40)) {
                    log.info("Withdrew 40 of seed ID: {}", seedId);
                    return true;
                }
            }
        }
        
        // If we get here, no seed type had 40+ available
        setupErrorMessage = "Need 40 seeds but no birdhouse seed type has 40+ in bank";
        log.error(setupErrorMessage);
        return false;
    }
}
