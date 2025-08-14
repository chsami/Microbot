package net.runelite.client.plugins.microbot.herbrun;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingHandler;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingWorld;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;

import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import static net.runelite.client.plugins.microbot.Microbot.log;

@Slf4j
public class HerbrunScript extends Script {
    @Inject
    private ConfigManager configManager;
    @Inject
    private FarmingWorld farmingWorld;
    private FarmingHandler farmingHandler;
    private final HerbrunPlugin plugin;
    private final HerbrunConfig config;
    private HerbPatch currentPatch;
    @Inject
    ClientThread clientThread;
    private boolean initialized = false;

    @Inject
    public HerbrunScript(HerbrunPlugin plugin, HerbrunConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    private final List<HerbPatch> herbPatches = new ArrayList<>();

    public boolean run() {        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;
            if (!initialized) {
                initialized = true;
                HerbrunPlugin.status = "Gearing up";
                populateHerbPatches();
                if (herbPatches.isEmpty()) {                                        
                    plugin.reportFinished("No herb patches ready to farm",true);
                    this.shutdown();
                    return;
                }
                
                if (config.useInventorySetup()) {
                    var inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                    if (!inventorySetup.doesInventoryMatch() || !inventorySetup.doesEquipmentMatch()) {
                        Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
                        if (!inventorySetup.loadEquipment() || !inventorySetup.loadInventory()) {                        
                            plugin.reportFinished("Failed to load inventory setup",false);
                            return;
                        }
                        Rs2Bank.closeBank();
                    }
                } else {
                    // Auto banking mode
                    if (!setupAutoInventory()) {
                        plugin.reportFinished("Failed to setup inventory",false);
                        return;
                    }
                }

                log("Will visit " + herbPatches.size() + " herb patches");
            }
            

            if (Rs2Inventory.hasItem("Weeds")) {
                Rs2Inventory.drop("Weeds");
            }
            if (currentPatch == null) getNextPatch();
            if (currentPatch == null) {
                HerbrunPlugin.status = "Finishing up";
                if (config.goToBank()) {
                    Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint());
                    if (!Rs2Bank.isOpen()) Rs2Bank.openBank();
                    Rs2Bank.depositAll();
                }
                HerbrunPlugin.status = "Finished";
                plugin.reportFinished("Herb run finished",true);
                this.shutdown();
                
            }

            // Skip patch if it's been disabled while running
            if (!currentPatch.isEnabled()) {
                currentPatch = null;
                return;
            }

            if (!currentPatch.isInRange(10)) {
                HerbrunPlugin.status = "Walking to " + currentPatch.getRegionName();
                Rs2Walker.walkTo(currentPatch.getLocation(), 20);

            }

            HerbrunPlugin.status = "Farming " + currentPatch.getRegionName();
            if (handleHerbPatch()) getNextPatch();


        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    private void populateHerbPatches() {
        this.farmingHandler = new FarmingHandler(Microbot.getClient(), configManager);
        herbPatches.clear();
        clientThread.runOnClientThreadOptional(() -> {
            for (FarmingPatch patch : farmingWorld.getTabs().get(Tab.HERB)) {
                HerbPatch _patch = new HerbPatch(patch, config, farmingHandler);
                if (_patch.getPrediction() != CropState.GROWING && _patch.isEnabled()) herbPatches.add(_patch);
            }
            return true;
        });
    }

    private void getNextPatch() {
        if (currentPatch == null) {
            if (herbPatches.isEmpty()) {
                return;
            }

            // Start with weiss, getNearestBank doesn't like that area!
            currentPatch = herbPatches.stream()
                    .filter(patch -> patch.isEnabled() && Objects.equals(patch.getRegionName(), "Weiss"))
                    .findFirst()
                    .orElseGet(() -> herbPatches.stream()
                            .filter(HerbPatch::isEnabled)
                            .findFirst()
                            .orElse(null));
            
            if (currentPatch != null) {
                herbPatches.remove(currentPatch);
            }
        }
    }

    private boolean handleHerbPatch() {
        if (Rs2Inventory.isFull()) {
            Rs2NpcModel leprechaun = Rs2Npc.getNpc("Tool leprechaun");
            if (leprechaun != null) {
                Rs2ItemModel unNoted = Rs2Inventory.getUnNotedItem("Grimy", false);
                Rs2Inventory.use(unNoted);
                Rs2Npc.interact(leprechaun, "Talk-to");
                Rs2Inventory.waitForInventoryChanges(10000);
            }
            return false;
        }

        Integer[] ids = {
                ObjectID.MYARM_HERBPATCH,
                ObjectID.FARMING_HERB_PATCH_2,
                ObjectID.FARMING_HERB_PATCH_4,
                ObjectID.FARMING_HERB_PATCH_8,
                ObjectID.FARMING_HERB_PATCH_6,
                ObjectID.FARMING_HERB_PATCH_3,
                ObjectID.FARMING_HERB_PATCH_1,
                ObjectID.FARMING_HERB_PATCH_7,
                ObjectID.MY2ARM_HERBPATCH,
                ObjectID.FARMING_HERB_PATCH_5
        };
        var obj = Rs2GameObject.findObject(ids);
        if (obj == null) return false;
        var state = getHerbPatchState(obj);
        switch (state) {
            case "Empty":
                // Apply compost if configured
                if (config.useCompost() && config.compostType() != CompostType.NONE) {
                    Rs2Inventory.use("compost");
                    Rs2GameObject.interact(obj, "Compost");
                    Rs2Player.waitForXpDrop(Skill.FARMING);
                    
                    // Drop empty bucket if configured (not for bottomless bucket)
                    if (config.dropEmptyBuckets() && !config.compostType().isBottomless()) {
                        Rs2Inventory.drop(ItemID.BUCKET_EMPTY);
                    }
                }
                Rs2Inventory.use(" seed");
                Rs2GameObject.interact(obj, "Plant");
                sleepUntil(() -> getHerbPatchState(obj).equals("Growing"));
                return false;
            case "Harvestable":
                Rs2GameObject.interact(obj, "Pick");
                sleepUntil(() -> getHerbPatchState(obj).equals("Empty") || Rs2Inventory.isFull(), 20000);
                return false;
            case "Weeds":
                Rs2GameObject.interact(obj);
                Rs2Player.waitForAnimation(10000);
                return false;
            case "Dead":
                Rs2GameObject.interact(obj, "Clear");
                sleepUntil(() -> getHerbPatchState(obj).equals("Empty"));
                return false;
            default:
                currentPatch = null;
                return true;
        }
    }

    private static String getHerbPatchState(TileObject rs2TileObject) {
        var game_obj = Rs2GameObject.convertToObjectComposition(rs2TileObject, true);
        var varbitValue = Microbot.getVarbitValue(game_obj.getVarbitId());

        if ((varbitValue >= 0 && varbitValue < 3) ||
                (varbitValue >= 60 && varbitValue <= 67) ||
                (varbitValue >= 173 && varbitValue <= 191) ||
                (varbitValue >= 204 && varbitValue <= 219) ||
                (varbitValue >= 221 && varbitValue <= 255)) {
            return "Weeds";
        }

        if ((varbitValue >= 4 && varbitValue <= 7) ||
                (varbitValue >= 11 && varbitValue <= 14) ||
                (varbitValue >= 18 && varbitValue <= 21) ||
                (varbitValue >= 25 && varbitValue <= 28) ||
                (varbitValue >= 32 && varbitValue <= 35) ||
                (varbitValue >= 39 && varbitValue <= 42) ||
                (varbitValue >= 46 && varbitValue <= 49) ||
                (varbitValue >= 53 && varbitValue <= 56) ||
                (varbitValue >= 68 && varbitValue <= 71) ||
                (varbitValue >= 75 && varbitValue <= 78) ||
                (varbitValue >= 82 && varbitValue <= 85) ||
                (varbitValue >= 89 && varbitValue <= 92) ||
                (varbitValue >= 96 && varbitValue <= 99) ||
                (varbitValue >= 103 && varbitValue <= 106) ||
                (varbitValue >= 192 && varbitValue <= 195)) {
            return "Growing";
        }

        if ((varbitValue >= 8 && varbitValue <= 10) ||
                (varbitValue >= 15 && varbitValue <= 17) ||
                (varbitValue >= 22 && varbitValue <= 24) ||
                (varbitValue >= 29 && varbitValue <= 31) ||
                (varbitValue >= 36 && varbitValue <= 38) ||
                (varbitValue >= 43 && varbitValue <= 45) ||
                (varbitValue >= 50 && varbitValue <= 52) ||
                (varbitValue >= 57 && varbitValue <= 59) ||
                (varbitValue >= 72 && varbitValue <= 74) ||
                (varbitValue >= 79 && varbitValue <= 81) ||
                (varbitValue >= 86 && varbitValue <= 88) ||
                (varbitValue >= 93 && varbitValue <= 95) ||
                (varbitValue >= 100 && varbitValue <= 102) ||
                (varbitValue >= 107 && varbitValue <= 109) ||
                (varbitValue >= 196 && varbitValue <= 197)) {
            return "Harvestable";
        }

        if ((varbitValue >= 128 && varbitValue <= 169) ||
                (varbitValue >= 198 && varbitValue <= 200)) {
            return "Diseased";
        }

        if ((varbitValue >= 170 && varbitValue <= 172) ||
                (varbitValue >= 201 && varbitValue <= 203)) {
            return "Dead";
        }

        return "Empty";
    }

    private boolean setupAutoInventory() {
        // Walk to nearest bank
        Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
        
        // Open bank
        if (!Rs2Bank.openBank()) {
            log("Failed to open bank");
            return false;
        }
        sleepUntil(Rs2Bank::isOpen);
        
        // Deposit all except equipped items
        Rs2Bank.depositAllExcept(true);
        Rs2Inventory.waitForInventoryChanges(5000);
        
        // Count enabled patches to know how many seeds/compost we need
        int patchCount = (int) herbPatches.stream().filter(HerbPatch::isEnabled).count();
        
        // Withdraw farming tools
        Rs2Bank.withdrawX(ItemID.RAKE, 1);
        Rs2Bank.withdrawX(ItemID.SPADE, 1);
        Rs2Bank.withdrawX(ItemID.DIBBER, 1);
        
        // Withdraw magic secateurs if available
        if (Rs2Bank.hasItem(ItemID.FAIRY_ENCHANTED_SECATEURS)) {
            Rs2Bank.withdrawX(ItemID.FAIRY_ENCHANTED_SECATEURS, 1);
        }
        
        // Withdraw teleportation runes
        Rs2Bank.withdrawX(ItemID.LAWRUNE, 20);
        Rs2Bank.withdrawX(ItemID.AIRRUNE, 50);
        Rs2Bank.withdrawX(ItemID.EARTHRUNE, 50);
        Rs2Bank.withdrawX(ItemID.FIRERUNE, 50);
        Rs2Bank.withdrawX(ItemID.WATERRUNE, 50);
        
        // Withdraw Ectophial if Morytania is enabled
        if (config.enableMorytania() && Rs2Bank.hasItem(ItemID.ECTOPHIAL)) {
            Rs2Bank.withdrawX(ItemID.ECTOPHIAL, 1);
        }
        
        // Withdraw herb seeds
        HerbSeedType seedType = config.herbSeedType();
        int seedsNeeded = patchCount; // 1 seed per patch
        if (!Rs2Bank.withdrawX(seedType.getItemId(), seedsNeeded)) {
            log("Failed to withdraw " + seedsNeeded + " " + seedType.getSeedName());
            return false;
        }
        
        // Withdraw compost if enabled
        if (config.useCompost()) {
            CompostType compostType = config.compostType();
            if (compostType != CompostType.NONE) {
                if (compostType.isBottomless()) {
                    // For bottomless bucket, just withdraw the bucket itself
                    if (!Rs2Bank.withdrawX(compostType.getItemId(), 1)) {
                        log("Failed to withdraw bottomless compost bucket");
                        return false;
                    }
                } else {
                    // For regular compost, withdraw 1 bucket per patch
                    int compostNeeded = patchCount;
                    if (!Rs2Bank.withdrawX(compostType.getItemId(), compostNeeded)) {
                        log("Failed to withdraw " + compostNeeded + " " + compostType.getCompostName());
                        return false;
                    }
                }
            }
        }
        
        // Close bank
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        
        log("Inventory setup complete - starting herb run");
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        initialized = false;
    }
}
