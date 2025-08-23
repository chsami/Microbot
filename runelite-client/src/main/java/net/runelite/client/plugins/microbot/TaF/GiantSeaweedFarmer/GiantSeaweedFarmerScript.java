package net.runelite.client.plugins.microbot.TaF.GiantSeaweedFarmer;

import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.VERY_LOW;

public class GiantSeaweedFarmerScript extends Script {
    public static final String VERSION = "1.0";
    public static final int UNDERWATER_ANCHOR = 30948;
    public static final int BOAT = 30919;
    // Critical section flag to prevent spore looting during compost+plant sequence
    public static volatile boolean inCriticalSection = false;
    private final GiantSeaweedFarmerPlugin giantSeaweedPlugin;
    private final LockCondition lookCondition;
    public GiantSeaweedFarmerStatus BOT_STATE = GiantSeaweedFarmerStatus.BANKING;
    private TileObject currentPatch;
    private List<Integer> handledPatches = new ArrayList<>();
    private List<Integer> patches = List.of(30500, 30501);

    {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = false;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2Antiban.setActivityIntensity(VERY_LOW);
    }

    @Inject
    public GiantSeaweedFarmerScript(GiantSeaweedFarmerPlugin giantSeaweedPlugin) {
        this.giantSeaweedPlugin = giantSeaweedPlugin;
        this.lookCondition = giantSeaweedPlugin.getLockCondition(giantSeaweedPlugin.getStopCondition());
    }

    private static void getToFossilIsland() {
        if (Rs2Player.isNearArea(BankLocation.FOSSIL_ISLAND_WRECK.getWorldPoint(), 25)) {
            return;
        }
        if (!Rs2Inventory.hasItem("Digsite pendant", false)) {
            Rs2Bank.walkToBankAndUseBank();
            sleepUntil(Rs2Bank::isOpen);
            sleep(100, 200);
            Rs2Bank.withdrawOne("Digsite pendant", false);
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen());
            sleep(100, 200);
        }
    }

    // Track last varbit value to reduce log spam
    private static int lastVarbitValue = -1;
    
    // Using official RuneLite varbit ranges for seaweed patches
    private static String getSeaweedPatchState(TileObject rs2TileObject) {
        var game_obj = Rs2GameObject.convertToObjectComposition(rs2TileObject, true);
        var varbitValue = Microbot.getVarbitValue(game_obj.getVarbitId());
        
        // Only log when varbit value changes
        if (varbitValue != lastVarbitValue) {
            Microbot.log("Seaweed patch varbit value changed: " + lastVarbitValue + " -> " + varbitValue);
            lastVarbitValue = varbitValue;
        }

        // Official RuneLite varbit ranges for SEAWEED patches from PatchImplementation.java
        // Note: varbit 3 means fully raked (0 rakes remaining) so it's ready for planting
        if (varbitValue == 3) {
            return "Empty";  // Fully raked, ready for planting
        }
        
        if ((varbitValue >= 0 && varbitValue <= 2) || (varbitValue >= 17 && varbitValue <= 255)) {
            return "Weeds";  // Needs raking (0=full weeds, 1=partial, 2=almost done)
        }
        
        if (varbitValue >= 4 && varbitValue <= 7) {
            return "Growing";
        }
        
        if (varbitValue >= 8 && varbitValue <= 10) {
            return "Harvestable";
        }
        
        if (varbitValue >= 11 && varbitValue <= 13) {
            return "Diseased";
        }
        
        if (varbitValue >= 14 && varbitValue <= 16) {
            return "Dead";  // Needs clearing - Dead seaweed objects 30497,30498,30499
        }

        return "Empty";
    }

    public boolean run(GiantSeaweedFarmerConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                // Respect pause state from other scripts (like spore looting)
                if (Microbot.pauseAllScripts.get()) return;

                switch (BOT_STATE) {
                    case BANKING:
                        getToFossilIsland();
                        if (handleBanking(config)) {
                            BOT_STATE = GiantSeaweedFarmerStatus.WALKING_TO_PATCH;
                        }
                        break;
                    case WALKING_TO_PATCH:
                        handleDiving();
                        break;
                    case FARMING:
                        handleFarming(config);
                        break;
                    case RETURN_TO_BANK:
                        returnToBank();
                        break;
                }
            } catch (Exception ex) {
                System.out.println("Exception message: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void returnToBank() {
        Rs2Walker.walkTo(3731, 10280, 1); // Get to anchor
        
        // Brief pause to allow spore detection before climbing
        sleep(300, 500);
        
        Rs2GameObject.interact(UNDERWATER_ANCHOR, "Climb");
        sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 0, 7000);
        if (Rs2Player.getWorldLocation().getPlane() != 0) {
            Microbot.log("We failed to get back to the surface");
            return;
        }
        lookCondition.unlock();
        Microbot.log("We're back at the bank, stopping the script");
        if (giantSeaweedPlugin != null) {
            giantSeaweedPlugin.reportFinished("Giant Seaweed Farming script finished successfully.", true);
        }
        this.shutdown();
    }

    private void handleDiving() {
        Rs2GameObject.interact(BOAT, "Dive");
        sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 1, 5000);
        if (Rs2Player.getWorldLocation().getPlane() != 1) {
            Microbot.log("We failed to get underwater - Make sure to handle the warning dialog manually once");
            lookCondition.unlock();
            giantSeaweedPlugin.reportFinished("Giant Seaweed Farming script error. We failed to get underwater", false);
            this.shutdown();
            return;
        }
        lookCondition.lock();
        Rs2Walker.walkTo(3731, 10273, 1); // Patch
        BOT_STATE = GiantSeaweedFarmerStatus.FARMING;
    }

    private boolean handleBanking(GiantSeaweedFarmerConfig config) {
        lookCondition.unlock();
        Rs2Bank.walkToBank(BankLocation.FOSSIL_ISLAND_WRECK);
        if (!Rs2Bank.openBank()) {
            return false;
        }

        // Just deposit all items to ensure low weight when under water
        Rs2Bank.depositAll();
        Rs2Bank.depositEquipment();

        // Essential farming equipment
        Rs2Bank.withdrawX("seaweed spore", 2);
        Rs2Inventory.waitForInventoryChanges(600);
        Rs2Bank.withdrawAndEquip("Fishbowl helmet");
        Rs2Inventory.waitForInventoryChanges(600);
        Rs2Bank.withdrawAndEquip("Diving apparatus");
        Rs2Inventory.waitForInventoryChanges(600);
        Rs2Bank.withdrawAndEquip("Flippers");
        Rs2Inventory.waitForInventoryChanges(600);
        Rs2Bank.withdrawOne("seed dibber", true);
        Rs2Inventory.waitForInventoryChanges(600);
        Rs2Bank.withdrawOne("rake", true);
        Rs2Inventory.waitForInventoryChanges(600);
        Rs2Bank.withdrawOne("spade", true);
        Rs2Inventory.waitForInventoryChanges(600);

        // Handle compost based on configuration
        if (config.compostType() != GiantSeaweedFarmerConfig.CompostType.NONE) {
            switch (config.compostType()) {
                case COMPOST:
                    Rs2Bank.withdrawX("Compost", 2);
                    break;
                case SUPERCOMPOST:
                    Rs2Bank.withdrawX("Supercompost", 2);
                    break;
                case ULTRACOMPOST:
                    Rs2Bank.withdrawX("Ultracompost", 2);
                    break;
                case BOTTOMLESS_COMPOST_BUCKET:
                    Rs2Bank.withdrawOne("Bottomless compost bucket");
                    break;
            }
        }

        Rs2Bank.closeBank();

        // Validate inventory and equipment
        boolean hasHelmet = Rs2Equipment.isWearing("Fishbowl helmet");
        boolean hasApparatus = Rs2Equipment.isWearing("Diving apparatus");
        boolean hasSpores = Rs2Inventory.contains("seaweed spore");
        boolean hasTools = Rs2Inventory.contains("seed dibber") &&
                Rs2Inventory.contains("rake") &&
                Rs2Inventory.contains("spade");

        boolean readyToFarm = hasHelmet && hasApparatus && hasSpores && hasTools;
        if (!readyToFarm) {
            StringBuilder missingItems = new StringBuilder("Missing required items: ");

            if (!hasHelmet) missingItems.append("Fishbowl helmet, ");
            if (!hasApparatus) missingItems.append("Diving apparatus, ");
            if (!hasSpores) missingItems.append("Seaweed spores, ");

            if (!hasTools) {
                if (!Rs2Inventory.contains("seed dibber")) missingItems.append("Seed dibber, ");
                if (!Rs2Inventory.contains("rake")) missingItems.append("Rake, ");
                if (!Rs2Inventory.contains("spade")) missingItems.append("Spade, ");
            }

            // Remove trailing comma and space if present
            String missingItemsStr = missingItems.toString();
            if (missingItemsStr.endsWith(", ")) {
                missingItemsStr = missingItemsStr.substring(0, missingItemsStr.length() - 2);
            }

            Microbot.log(missingItemsStr + " - shutting down");
            giantSeaweedPlugin.reportFinished("Giant Seaweed Farming script error.\nWe failed to get items from the bank:\n\t" + missingItemsStr, false);
            this.shutdown();
        }

        return readyToFarm;
    }

    private void handleFarming(GiantSeaweedFarmerConfig config) {
        Integer patchToFarm = patches.stream()
                .filter(patch -> !handledPatches.contains(patch))
                .findFirst()
                .orElse(null);

        Microbot.log("Attempting to farm patch: " + patchToFarm + ", handled patches: " + handledPatches);
        
        if (patchToFarm == null) {
            if (config.returnToBank()) {
                BOT_STATE = GiantSeaweedFarmerStatus.RETURN_TO_BANK;
                Microbot.log("Finished farming, returning to wreck");
                return;
            }
            Microbot.log("Finished farming, stopping...");
            lookCondition.unlock();
            giantSeaweedPlugin.reportFinished("Giant Seaweed Farming script, finished farming not returning to bank", true);
            shutdown();
            return;
        }

        var handledPatch = handlePatch(patchToFarm);
        if (handledPatch) {
            handledPatches.add(patchToFarm);
        }
    }

    private boolean handlePatch(int patchId) {
        if (Rs2Inventory.isFull()) {
            Rs2NpcModel leprechaun = Rs2Npc.getNpc("Tool leprechaun");
            if (leprechaun != null) {
                Rs2ItemModel unNoted = Rs2Inventory.getUnNotedItem("Giant seaweed", true);
                Rs2Inventory.use(unNoted);
                Rs2Npc.interact(leprechaun, "Talk-to");
                Rs2Inventory.waitForInventoryChanges(10000);
            }
            return false;
        }

        Integer[] ids = {
                patchId
        };
        var obj = Rs2GameObject.findObject(ids);
        
        // If not found by ID, look for patch objects by name
        if (obj == null) {
            obj = Rs2GameObject.getGameObjects()
                    .stream()
                    .filter(o -> {
                        var objComp = Rs2GameObject.convertToObjectComposition(o, false);
                        if (objComp == null || objComp.getName() == null) return false;
                        String name = objComp.getName();
                        // Look for seaweed patch objects or dead seaweed
                        return name.equalsIgnoreCase("Dead seaweed") || 
                               name.equalsIgnoreCase("Seaweed patch") ||
                               (name.equalsIgnoreCase("Seaweed") && objComp.getId() == patchId);
                    })
                    .findFirst()
                    .orElse(null);
        }
        
        if (obj == null) return false;
        
        // Make final reference for lambda usage
        final var patchObj = obj;
        var state = getSeaweedPatchState(patchObj);
        Microbot.log("Patch state detected as: " + state);
        switch (state) {
            case "Empty":
                // Enter critical section to prevent spore looting interruption
                inCriticalSection = true;
                try {
                    // Verify we have materials before starting atomic operation
                    boolean hasCompost = Rs2Inventory.contains("compost") || 
                                        Rs2Inventory.contains("Supercompost") ||
                                        Rs2Inventory.contains("Ultracompost") ||
                                        Rs2Inventory.contains("Bottomless compost bucket");
                    
                    if (hasCompost) {
                        Rs2Inventory.use("compost");
                        Rs2GameObject.interact(patchObj, "Compost");
                        Rs2Player.waitForXpDrop(Skill.FARMING);
                    }
                    
                    // Always attempt planting if we have spores
                    if (Rs2Inventory.contains("seaweed spore")) {
                        Rs2Inventory.use(" spore");
                        Rs2GameObject.interact(patchObj, "Plant");
                        sleepUntil(() -> getSeaweedPatchState(patchObj).equals("Growing"), 10000);
                    }
                    return true;
                } finally {
                    // Always release critical section, even if error occurs
                    inCriticalSection = false;
                }
            case "Harvestable":
                Rs2GameObject.interact(patchObj, "Pick");
                sleepUntil(() -> {
                    // Re-find the patch object at the same location to get updated state
                    var currentPatch = Rs2GameObject.getGameObjects()
                        .stream()
                        .filter(o -> o.getWorldLocation().equals(patchObj.getWorldLocation()))
                        .findFirst()
                        .orElse(null);
                    if (currentPatch == null) return false;
                    String currentState = getSeaweedPatchState(currentPatch);
                    // Harvesting is complete when patch becomes empty or inventory is full
                    return currentState.equals("Empty") || Rs2Inventory.isFull();
                }, 20000);
                return false; // Don't mark as handled - needs planting after harvesting
            case "Weeds":
                Rs2GameObject.interact(patchObj, "Rake");
                sleepUntil(() -> {
                    // Re-find the patch object at the same location to get updated state
                    var currentPatch = Rs2GameObject.getGameObjects()
                        .stream()
                        .filter(o -> o.getWorldLocation().equals(patchObj.getWorldLocation()))
                        .findFirst()
                        .orElse(null);
                    if (currentPatch == null) return false;
                    String currentState = getSeaweedPatchState(currentPatch);
                    return !currentState.equals("Weeds");
                }, 10000);
                return false; // Don't mark as handled - needs planting after raking
            case "Dead":
                Rs2GameObject.interact(patchObj, "Clear");
                sleepUntil(() -> {
                    // Re-find the patch object at the same location to get updated state
                    var currentPatch = Rs2GameObject.getGameObjects()
                        .stream()
                        .filter(o -> o.getWorldLocation().equals(patchObj.getWorldLocation()))
                        .findFirst()
                        .orElse(null);
                    if (currentPatch == null) return false;
                    String currentState = getSeaweedPatchState(currentPatch);
                    return !currentState.equals("Dead");
                }, 10000);
                return false; // Don't mark as handled - needs planting after clearing
            default:
                currentPatch = null;
                return true;
        }
    }

    @Override
    public void shutdown() {
        BOT_STATE = GiantSeaweedFarmerStatus.BANKING;
        handledPatches = new ArrayList<>();
        lookCondition.unlock();
        inCriticalSection = false; // Ensure flag is cleared on shutdown
        super.shutdown();
    }
}
