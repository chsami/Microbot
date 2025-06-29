package net.runelite.client.plugins.microbot.TaF.GiantSeaweedFarmer;

import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.VERY_LOW;

public class GiantSeaweedFarmerScript extends Script {
    public static final String VERSION = "1.0";
    public static final int UNDERWATER_ANCHOR = 30948;
    public static final int BOAT = 30919;
    private TileObject currentPatch;
    public GiantSeaweedFarmerStatus BOT_STATE = GiantSeaweedFarmerStatus.BANKING;
    private List<Integer> handledPatches = new ArrayList<>();
    private List<Integer> patches = List.of(30500,30501);

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

    public boolean run(GiantSeaweedFarmerConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                switch (BOT_STATE) {
                    case BANKING:
                        if (handleBanking(config)) {
                            BOT_STATE = GiantSeaweedFarmerStatus.WALKING_TO_PATCH;
                        }
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
        Rs2Walker.walkTo(3731,10280,1); // Get to anchor
        Rs2GameObject.interact(UNDERWATER_ANCHOR, "Climb");
        sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 0, 7000);
        if (Rs2Player.getWorldLocation().getPlane() != 0) {
            Microbot.log("We failed to get back to the surface");
            return;
        }
        Microbot.log("We're back at the bank, stopping the script");
        shutdown();
    }

    private void handleDiving() {
        Rs2GameObject.interact(BOAT, "Dive");
        sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 1, 5000);
        if (Rs2Player.getWorldLocation().getPlane() != 1) {
            Microbot.log("We failed to get underwater - Make sure to handle the warning dialog manually once");
            shutdown();
            return;
        }
        Rs2Walker.walkTo(3731,10273,1); // Patch
        BOT_STATE = GiantSeaweedFarmerStatus.FARMING;
    }

    private boolean handleBanking(GiantSeaweedFarmerConfig config) {
        Rs2Bank.walkToBank(BankLocation.FOSSIL_ISLAND_WRECK);
        if (!Rs2Bank.openBank()) {
            return false;
        }

        // Just deposit all items to ensure low weight when under water
        Rs2Bank.depositAll();
        Rs2Bank.depositEquipment();

        // Essential farming equipment
        Rs2Bank.withdrawX("seaweed spore", 2);
        Rs2Bank.withdrawAndEquip("Fishbowl helmet");
        Rs2Bank.withdrawAndEquip("Diving apparatus");
        Rs2Bank.withdrawOne("secateurs"); // Loot is not affected by type of secateurs, so just take whatever
        Rs2Bank.withdrawOne("seed dibber");
        Rs2Bank.withdrawOne("rake");
        Rs2Bank.withdrawOne("spade");

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
        boolean hasTools = Rs2Inventory.contains("secateurs") &&
                Rs2Inventory.contains("seed dibber") &&
                Rs2Inventory.contains("rake") &&
                Rs2Inventory.contains("spade");

        boolean readyToFarm = hasHelmet && hasApparatus && hasSpores && hasTools;
        if (!readyToFarm) {
            StringBuilder missingItems = new StringBuilder("Missing required items: ");

            if (!hasHelmet) missingItems.append("Fishbowl helmet, ");
            if (!hasApparatus) missingItems.append("Diving apparatus, ");
            if (!hasSpores) missingItems.append("Seaweed spores, ");

            if (!hasTools) {
                if (!Rs2Inventory.contains("secateurs")) missingItems.append("Secateurs, ");
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
            shutdown();
        }

        return readyToFarm;
    }

    private void handleFarming(GiantSeaweedFarmerConfig config) {
        Integer patchToFarm = patches.stream()
                .filter(patch -> !handledPatches.contains(patch))
                .findFirst()
                .orElse(null);

        if (patchToFarm == null) {
            if (config.returnToBank()) {
                BOT_STATE = GiantSeaweedFarmerStatus.RETURN_TO_BANK;
                Microbot.log("Finished farming, returning to wreck");
                return;
            }
            Microbot.log("Finished farming, stopping...");
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
        if (obj == null) return false;
        var state = getSeaweedPatchState(obj);
        switch (state) {
            case "Empty":
                Rs2Inventory.use("compost");
                Rs2GameObject.interact(obj, "Compost");
                Rs2Player.waitForXpDrop(Skill.FARMING);
                Rs2Inventory.use(" spore");
                Rs2GameObject.interact(obj, "Plant");
                sleepUntil(() -> getSeaweedPatchState(obj).equals("Growing"));
                return false;
            case "Harvestable":
                Rs2GameObject.interact(obj, "Pick");
                sleepUntil(() -> getSeaweedPatchState(obj).equals("Empty") || Rs2Inventory.isFull(), 20000);
                return false;
            case "Weeds":
                Rs2GameObject.interact(obj);
                Rs2Player.waitForAnimation(10000);
                return false;
            case "Dead":
                Rs2GameObject.interact(obj, "Clear");
                sleepUntil(() -> getSeaweedPatchState(obj).equals("Empty"));
                return false;
            default:
                currentPatch = null;
                return true;
        }
    }

    // Shamelessly stolen from HerbRun script
    private static String getSeaweedPatchState(TileObject rs2TileObject) {
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

    @Override
    public void shutdown() {
        BOT_STATE = GiantSeaweedFarmerStatus.BANKING;
        handledPatches = new ArrayList<>();
        super.shutdown();
    }
}
