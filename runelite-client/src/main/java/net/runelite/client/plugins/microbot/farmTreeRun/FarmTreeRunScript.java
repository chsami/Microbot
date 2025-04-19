package net.runelite.client.plugins.microbot.farmTreeRun;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.farmTreeRun.enums.FarmTreeRunState;
import net.runelite.client.plugins.microbot.farmTreeRun.enums.FruitTreeEnum;
import net.runelite.client.plugins.microbot.farmTreeRun.enums.TreeEnums;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.farmTreeRun.enums.FarmTreeRunState.*;


/**
 * There are two tree types:
 * 1. 'Tree' refers to regular trees (e.g., maple, yew).
 * 2. 'Fruit Tree' refers to fruit trees (e.g., apple, banana).
 */
public class FarmTreeRunScript extends Script {
    public static FarmTreeRunState botStatus;
    public static boolean test = false;
    public static Integer compostItemId = null;
    private List<FarmingItem> items = new ArrayList<>();

    private enum TreeKind {
        FRUIT_TREE,
        TREE
    }

    private enum PaymentKind {
        PROTECT,
        CLEAR
    }

    @Getter
    @RequiredArgsConstructor
    public enum Patch {
        GNOME_STRONGHOLD_FRUIT_TREE_PATCH(7962, new WorldPoint(2473, 3446, 0), TreeKind.FRUIT_TREE, 1, 0),
        GNOME_STRONGHOLD_TREE_PATCH(19147, new WorldPoint(2437, 3417, 0), TreeKind.TREE, 1, 0),
        TREE_GNOME_VILLAGE_FRUIT_TREE_PATCH(7963, new WorldPoint(2490, 3181, 0), TreeKind.FRUIT_TREE, 1, 0),
        FARMING_GUILD_TREE_PATCH(33732, new WorldPoint(1234, 3734, 0), TreeKind.TREE, 65, 0),
        FARMING_GUILD_FRUIT_TREE_PATCH(34007, new WorldPoint(1244, 3757, 0), TreeKind.FRUIT_TREE, 85, 0),
        TAVERLEY_TREE_PATCH(8388, new WorldPoint(2936, 3440, 0), TreeKind.TREE, 1, 0),
        FALADOR_TREE_PATCH(8389, new WorldPoint(3001, 3374, 0), TreeKind.TREE, 1, 0),
        LUMBRIDGE_TREE_PATCH(8391, new WorldPoint(3195, 3228, 0), TreeKind.TREE, 1, 0),
        VARROCK_TREE_PATCH(8390, new WorldPoint(3226, 3458, 0), TreeKind.TREE, 1, 0),
        BRIMHAVEN_FRUIT_TREE_PATCH(7964, new WorldPoint(2765, 3213, 0), TreeKind.FRUIT_TREE, 1, 0),
        CATHERBY_FRUIT_TREE_PATCH(7965, new WorldPoint(2858, 3432, 0), TreeKind.FRUIT_TREE, 1, 0),
        LLETYA_FRUIT_TREE_PATCH(0000000, new WorldPoint(2345, 3163, 0), TreeKind.FRUIT_TREE, 1, 0);

        private final int id;
        private final WorldPoint location;
        private final TreeKind kind;
        private final int farmingLevel;
        private final int leprechaunId;

        public boolean hasRequiredLevel() {
            if (Rs2Player.getSkillRequirement(Skill.FARMING, this.farmingLevel))
                return true;
            Microbot.showMessage(this.name() + " requires level " + this.farmingLevel + " farming.");
            return false;
        }
    }

    public boolean run(FarmTreeRunConfig config) {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;
        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);

        botStatus = BANKING;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                long startTime = System.currentTimeMillis();
                if (Rs2AntibanSettings.actionCooldownActive) return;

                calculatePatches(config);
                checkSaplingLevelRequirement(config);

                dropEmptyPlantPots();
                Patch patch = null;
                boolean handledPatch = false;

                switch (botStatus) {
                    case BANKING:
                        if (config.banking()) {
                            bank(config);
                        } else {
                            if (isCompostEnabled(config)) {
                                compostItemId = ItemID.BOTTOMLESS_COMPOST_BUCKET_22997;
                            }
                            botStatus = HANDLE_GNOME_STRONGHOLD_FRUIT_PATCH;
                        }
                        break;
                    case HANDLE_GNOME_STRONGHOLD_FRUIT_PATCH:
                        patch = Patch.GNOME_STRONGHOLD_FRUIT_TREE_PATCH;
                        if (config.gnomeStrongholdFruitTreePatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch)
                                return;
                        }
                        botStatus = HANDLE_GNOME_STRONGHOLD_TREE_PATCH;
                        break;
                    case HANDLE_GNOME_STRONGHOLD_TREE_PATCH:
                        patch = Patch.GNOME_STRONGHOLD_TREE_PATCH;
                        if (config.gnomeStrongholdTreePatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch)
                                return;
                        }
                        botStatus = HANDLE_TREE_GNOME_VILLAGE_FRUIT_TREE_PATCH;
                        break;
                    case HANDLE_TREE_GNOME_VILLAGE_FRUIT_TREE_PATCH:
                        patch = Patch.TREE_GNOME_VILLAGE_FRUIT_TREE_PATCH;
                        if (config.treeGnomeVillageFruitTreePatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch)
                                return;
                        }
                        botStatus = HANDLE_FARMING_GUILD_TREE_PATCH;
                        break;
                    case HANDLE_FARMING_GUILD_TREE_PATCH:
                        patch = Patch.FARMING_GUILD_TREE_PATCH;
                        if (config.farmingGuildTreePatch() && patch.hasRequiredLevel()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch)
                                return;
                        }
                        botStatus = HANDLE_FARMING_GUILD_FRUIT_PATCH;
                        break;
                    case HANDLE_FARMING_GUILD_FRUIT_PATCH:
                        patch = Patch.FARMING_GUILD_FRUIT_TREE_PATCH;
                        if (config.farmingGuildFruitTreePatch() && patch.hasRequiredLevel()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch)
                                return;
                        }
                        botStatus = HANDLE_TAVERLEY_TREE_PATCH;
                        break;
                    case HANDLE_TAVERLEY_TREE_PATCH:
                        patch = Patch.TAVERLEY_TREE_PATCH;
                        if (config.taverleyTreePatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch) return;
                        }
                        botStatus = HANDLE_FALADOR_TREE_PATCH;
                        break;
                    case HANDLE_FALADOR_TREE_PATCH:
                        patch = Patch.FALADOR_TREE_PATCH;
                        if (config.faladorTreePatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch) return;
                        }
                        botStatus = HANDLE_LUMBRIDGE_TREE_PATCH;
                        break;
                    case HANDLE_LUMBRIDGE_TREE_PATCH:
                        patch = Patch.LUMBRIDGE_TREE_PATCH;
                        if (config.lumbridgeTreePatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch)
                                return;
                        }
                        botStatus = HANDLE_VARROCK_TREE_PATCH;
                        break;
                    case HANDLE_VARROCK_TREE_PATCH:
                        patch = Patch.VARROCK_TREE_PATCH;
                        if (config.varrockTreePatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch)
                                return;
                        }
                        botStatus = HANDLE_BRIMHAVEN_FRUIT_TREE_PATCH;
                        break;
                    case HANDLE_BRIMHAVEN_FRUIT_TREE_PATCH:
                        patch = Patch.BRIMHAVEN_FRUIT_TREE_PATCH;
                        if (config.brimhavenFruitTreePatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch)
                                return;
                        }
                        botStatus = HANDLE_CATHERBY_FRUIT_TREE_PATCH;
                        break;
                    case HANDLE_CATHERBY_FRUIT_TREE_PATCH:
                        patch = Patch.CATHERBY_FRUIT_TREE_PATCH;
                        if (config.catherbyFruitTreePatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch)
                                return;
                        }
                        botStatus = HANDLE_LLETYA_FRUIT_TREE_PATCH;
                        break;
                    case HANDLE_LLETYA_FRUIT_TREE_PATCH:
                        patch = Patch.LLETYA_FRUIT_TREE_PATCH;
                        if (config.lletyaFruitTreePatch()) {
                            if (walkToLocation(patch.getLocation())) {
                                handledPatch = handlePatch(config, patch);
                            }
                            if (!handledPatch)
                                return;
                        }
                        botStatus = FINISHED;
                        break;
                    case FINISHED:
                        Microbot.getClientThread().runOnClientThreadOptional(() -> {
                                    Microbot.getClient().addChatMessage(ChatMessageType.ENGINE, "", "Tree run completed.", "Acun", false);
                                    Microbot.getClient().addChatMessage(ChatMessageType.ENGINE, "", "Made with love by Acun.", "Acun", false);
                                    return null;
                                }
                        );
                        shutdown();
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void calculatePatches(FarmTreeRunConfig config) {
        if (getSelectedTreePatches(config).isEmpty() && getSelectedFruitTreePatches(config).isEmpty()) {
            Microbot.showMessage("You must select at least one patch. Shut down.");
            shutdown();
        }
    }

    private void checkSaplingLevelRequirement(FarmTreeRunConfig config) {
        if (!getSelectedTreePatches(config).isEmpty())
            config.selectedTree().hasRequiredLevel();

        if (!getSelectedFruitTreePatches(config).isEmpty())
            config.selectedFruitTree().hasRequiredLevel();
    }

    private void dropEmptyPlantPots() {
        int emptyPlantPot = ItemID.EMPTY_PLANT_POT;
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving() && !Rs2Player.isInteracting()) {
            if (Rs2Inventory.hasItem(emptyPlantPot)) {
                Rs2Inventory.dropAll(emptyPlantPot);
                sleepUntil(() -> !Rs2Inventory.hasItem(emptyPlantPot), 8000);
            }
        }
    }

    private boolean walkToLocation(WorldPoint location) {
        if (!Rs2Player.isAnimating()) {
            Rs2Walker.walkTo(location);
            sleepUntil(() -> Rs2Player.distanceTo(location) < 16);
            return Rs2Player.distanceTo(location) < 16;
        }
        return false;
    }

    private void bank(FarmTreeRunConfig config) {
        if (Rs2Bank.openBank() || Rs2Bank.walkToBank()) {
            sleepUntil(() -> !Rs2Player.isAnimating());
            if (!Rs2Bank.isOpen())
                return;
            sleep(600, 2200);

            if (!Rs2Inventory.isEmpty()) {
                Rs2Bank.depositAll();
                Rs2Inventory.waitForInventoryChanges(3000);
            }

            if (config.useGraceful() && !alreadyWearingGraceful() && !Rs2Equipment.isNaked()) {
                Rs2Bank.depositEquipment();
                sleepUntil(Rs2Equipment::isNaked);
                sleep(500, 2200);
            }

            if (config.useGraceful())
                equipGraceful();


            // Add must have items
            items.add(new FarmingItem(ItemID.COINS_995, 5000));
            items.add(new FarmingItem(ItemID.SPADE, 1));
            items.add(new FarmingItem(ItemID.RAKE, 1));
            items.add(new FarmingItem(ItemID.SEED_DIBBER, 1));

            if (isCompostEnabled(config)) {
                if (Rs2Bank.hasItem(ItemID.BOTTOMLESS_COMPOST_BUCKET_22997)) {
                    compostItemId = ItemID.BOTTOMLESS_COMPOST_BUCKET_22997;
                    items.add(new FarmingItem(compostItemId, 1));
                } else {
                    Microbot.log("Only bottomless compost is supported. Skipping composting.");
                }
            }

            if (config.farmingGuildTreePatch() || config.farmingGuildFruitTreePatch()) {
                if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE2)) {
                    items.add(new FarmingItem(ItemID.SKILLS_NECKLACE2, 1));
                } else if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE3)) {
                    items.add(new FarmingItem(ItemID.SKILLS_NECKLACE3, 1));
                } else if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE4)) {
                    items.add(new FarmingItem(ItemID.SKILLS_NECKLACE4, 1));
                } else if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE5)) {
                    items.add(new FarmingItem(ItemID.SKILLS_NECKLACE5, 1));
                } else {
                    items.add(new FarmingItem(ItemID.SKILLS_NECKLACE6, 1));
                }
            }

            TreeEnums selectedTree = config.selectedTree();
            FruitTreeEnum selectedFruitTree = config.selectedFruitTree();

            int treeSaplingsCount = getSelectedTreePatches(config).size();
            int fruitTreeSaplingsCount = getSelectedFruitTreePatches(config).size();

            if (treeSaplingsCount > 0)
                items.add(new FarmingItem(selectedTree.getSaplingId(), treeSaplingsCount));

            if (fruitTreeSaplingsCount > 0)
                items.add(new FarmingItem(selectedFruitTree.getSaplingId(), fruitTreeSaplingsCount));

            if (config.protectTrees())
                items.add(new FarmingItem(selectedTree.getPaymentId(), selectedTree.getPaymentAmount() * treeSaplingsCount, true));

            if (config.protectFruitTrees())
                items.add(new FarmingItem(selectedFruitTree.getPaymentId(), selectedFruitTree.getPaymentAmount() * fruitTreeSaplingsCount, true));

            if (config.taverleyTreePatch())
                items.add(new FarmingItem(ItemID.TAVERLEY_TELEPORT, 1, false, true));

            if (config.lletyaFruitTreePatch()) {
                if (Rs2Bank.hasItem(ItemID.TELEPORT_CRYSTAL_1)) {
                    items.add(new FarmingItem(ItemID.TELEPORT_CRYSTAL_1, 1));
                } else if (Rs2Bank.hasItem(ItemID.TELEPORT_CRYSTAL_2)) {
                    items.add(new FarmingItem(ItemID.TELEPORT_CRYSTAL_2, 1));
                } else if (Rs2Bank.hasItem(ItemID.TELEPORT_CRYSTAL_3)) {
                    items.add(new FarmingItem(ItemID.TELEPORT_CRYSTAL_3, 1));
                } else if (Rs2Bank.hasItem(ItemID.TELEPORT_CRYSTAL_4)) {
                    items.add(new FarmingItem(ItemID.TELEPORT_CRYSTAL_4, 1));
                } else {
                    items.add(new FarmingItem(ItemID.TELEPORT_CRYSTAL_5, 1));
                }
            }

            items.add(new FarmingItem(ItemID.LAW_RUNE, 10));
            items.add(new FarmingItem(ItemID.FIRE_RUNE, 30));
            items.add(new FarmingItem(ItemID.AIR_RUNE, 30));
            items.add(new FarmingItem(ItemID.EARTH_RUNE, 30));
            items.add(new FarmingItem(ItemID.WATER_RUNE, 30));


//              TODO: Need to handle what happens if a required item does not exist

            // Loop through the items and perform withdrawals
            for (FarmingItem item : items) {
                if (this.items.isEmpty())
                    break;
                int itemId = item.getItemId();
                int quantity = item.getQuantity();
                boolean noted = item.isNoted();

                if (quantity <= 0)
                    continue;

//              Handle items which require to be noted
                if (noted && !Rs2Bank.hasWithdrawAsNote()) {
                    Rs2Bank.setWithdrawAsNote();
                    sleep(500, 1200);
                } else if (!noted && Rs2Bank.hasWithdrawAsNote()) { // Disables 'Note' toggle
                    Rs2Bank.setWithdrawAsItem();
                }

                if (quantity == 1) {
                    checkIfPlayerHasItem(item);
                    Rs2Bank.withdrawOne(itemId);
                } else {
                    checkIfPlayerHasItem(item);
                    Rs2Bank.withdrawX(itemId, quantity);
                }

                sleep(250, 1200);
            }

            Rs2Bank.closeBank();
            botStatus = HANDLE_GNOME_STRONGHOLD_FRUIT_PATCH;
        }
    }

    private void checkIfPlayerHasItem(FarmingItem item) {
        if (!Rs2Bank.hasItem(new int[]{item.getItemId()}, item.getQuantity()) && !item.isOptional()) {
            Microbot.showMessage("Not enough items: " + Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(item.getItemId()).getName()) + ". " +
                    "Need " + item.getQuantity() + ". Shut down.");
            shutdown();
        }
    }

    private boolean handlePatch(FarmTreeRunConfig config, Patch patch) {
        String[] possibleActions = {"Check", "Chop", "Pick", "Rake", "Clear", "Inspect"};
        GameObject treePatch = null;
        String foundAction = null;
        String exactAction = null;

        // Loop through the possible actions and try to find the tree patch with any valid action
        for (String action : possibleActions) {
            treePatch = Rs2GameObject.findObjectByImposter(patch.getId(), action, false);  // Find object by patchId and action
            if (treePatch != null) {
                foundAction = action;
                if (!foundAction.contains("Inspect")) {
                    break;
                }
            }
        }

        // Gagex named actions differently, sometimes it's Pick-fruit and sometimes Pick-banana.
        // Also seen "Chop down" and "Chop-down".
        List<String> exactTreeActions = Arrays.stream(Rs2GameObject.findObjectComposition(treePatch.getId()).getImpostor().getActions()).collect(Collectors.toList());
        for (String action : exactTreeActions) {
            if (action == null)
                continue;
            if (action.startsWith(foundAction) || action.equals(foundAction)) {
                exactAction = action;
                break;
            }
        }

        // If no tree patch is, print an error and return
        if (treePatch == null) {
            System.out.println("Tree patch not found with any of the possible actions. Report this in Discord: " + patch.getId());
            return false;
        }

        boolean done = false;
        boolean treePlanted = false;
        boolean protectionHandled = false;

        // Handle the patch based on the action found
        switch (foundAction) {
            case "Check":
                handleCheckHealth(treePatch);
                break;
            case "Chop":
                handlePayment(config, patch, PaymentKind.CLEAR);
                break;
            case "Pick":
                handlePickingFruit(treePatch, patch, exactAction);
                break;
            case "Rake":
                handleRakeAction(treePatch);
                break;
            case "Clear":
                handleClearAction(treePatch);
                break;
            case "Inspect":
                if (handlePlantingTree(treePatch, patch, config))
                    treePlanted = true;
                if (treePlanted && handlePayment(config, patch, PaymentKind.PROTECT))
                    protectionHandled = true;
                if (treePlanted && protectionHandled)
                    done = true;
                break;
            default:
                System.out.println("Unexpected action found on tree patch: " + foundAction);
                break;
        }
        return done;
    }

    private void handleNotingFruit(Patch patch) {
        // Array of fruit item IDs
        int[] fruitIds = {
                ItemID.COOKING_APPLE,
                ItemID.BANANA,
                ItemID.ORANGE,
                ItemID.CURRY_LEAF,
                ItemID.PINEAPPLE,
                ItemID.PAPAYA_FRUIT,
                ItemID.COCONUT,
                ItemID.DRAGONFRUIT
        };

        if (!Rs2Inventory.hasItem(fruitIds) || Rs2Player.isAnimating()) return;

        // Iterate through the fruit IDs
        for (int fruitId : fruitIds) {
            if (Rs2Inventory.hasItem(fruitId)) {
                // Interact with the specific fruit found
                Rs2Inventory.useItemOnNpc(fruitId, patch.getLeprechaunId());
                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(5000));
                return; // Return false if any fruit is found and interacted with
            }
        }
    }

    /**
     * Handles tree clearing and protecting payments
     *
     * @param config
     * @return {@code true} if payment was successful, else {@code false}
     */
    private boolean handlePayment(FarmTreeRunConfig config, Patch patch, PaymentKind action) {
        if (isTreePatch(patch) && !isPatchEmpty(patch) && !shouldProtectTree(config) && action != PaymentKind.CLEAR)
            return true;

        if (isFruitTreePatch(patch) && !isPatchEmpty(patch) && !shouldProtectFruitTree(config) && action != PaymentKind.CLEAR)
            return true;


        Rs2NpcModel treeGardener = null;
        treeGardener = Rs2Npc.getNearestNpcWithAction("Pay");
        Rs2Npc.interact(treeGardener, "Pay");

        if (treeGardener == null) {
            handleExoticGardeners();
        }

        sleepUntil(Rs2Dialogue::isInDialogue, 5000);
        sleep(500, 1500);
        if (!Rs2Dialogue.hasSelectAnOption()) {
            return Rs2Dialogue.hasDialogueText("Leave it with me") || Rs2Dialogue.hasDialogueText("already looking after that patch");
        }
        Rs2Dialogue.clickContinue();
        sleep(500, 850);

        if (Rs2Dialogue.hasSelectAnOption()) {
            Rs2Dialogue.clickOption("Yes");
            sleepUntil(() -> isPatchEmpty(patch), 6000);
            if (isPatchEmpty(patch)) {
                return true;
            }
            shutdown();
            System.out.println("Failed gardener money payment.");
            return false;
        } else {
            System.out.println("Failed gardener payment.");
        }

        return false;
    }

    private boolean handlePlantingTree(GameObject treePatch, Patch patch, FarmTreeRunConfig config) {
        // Skip if patch is not empty
        if (!isPatchEmpty(patch))
            return true;

        int saplingToUse = getSaplingToUse(patch, config);

        if (useCompostOnPatch(config, patch)) {
            Rs2Inventory.useItemOnObject(compostItemId, treePatch.getId());
            Rs2Player.waitForXpDrop(Skill.FARMING, 2000);
            sleep(550, 2200);
        }

        sleep(250, 1000);
        if (!Rs2GameObject.hasAction(Rs2GameObject.findObjectComposition(patch.id), "Rake")) {
            Rs2Inventory.useItemOnObject(saplingToUse, treePatch.getId());
            Rs2Inventory.waitForInventoryChanges(3000);
            sleep(750, 2400);
            return !isPatchEmpty(patch);
        }
        Rs2Inventory.deselect();
        return false;
    }

    private void handlePickingFruit(GameObject fruitTreePatch, Patch patch, String exactAction) {
        System.out.println("Picking fruit...");
        Rs2GameObject.interact(fruitTreePatch, exactAction);
        // Wait for the picking to complete (player stops animating and patch no longer has the "Pick" action)
        sleepUntil(() -> !Rs2GameObject.hasAction(Rs2GameObject.findObjectComposition(fruitTreePatch.getId()), exactAction), 12000);
        sleep(400, 1500);
        handleNotingFruit(patch);
    }

    private void handleCheckHealth(GameObject treePatch) {
        System.out.println("Checking health...");

        // Rake the patch
        Rs2GameObject.interact(treePatch, "Check-health");
        Rs2Player.waitForXpDrop(Skill.FARMING);
        sleep(250, 2500);
    }

    private void handleRakeAction(GameObject treePatch) {
        System.out.println("Raking the patch...");

        // Rake the patch
        Rs2GameObject.interact(treePatch, "rake");

        Rs2Player.waitForAnimation();
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());

        // Drop the weeds (assuming weeds are added to the inventory)
        if (!Rs2Player.isMoving() &&
                !Rs2Player.isAnimating() &&
                !Rs2Player.isInteracting() && !Rs2Player.isMoving()) {
            System.out.println("Dropping weeds...");
            Rs2Inventory.dropAll(ItemID.WEEDS);
            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
        }
    }

    private void handleClearAction(GameObject treePatch) {
        System.out.println("Clearing dead tree...");

        // Try to interact with the patch using the "clear" action
        boolean interactionSuccess = Rs2GameObject.interact(treePatch, "clear");
        Rs2Player.waitForAnimation();
        sleepUntil(() -> !Rs2Player.isAnimating());

        if (!interactionSuccess) {
            System.out.println("Failed to interact with the tree patch to clear it.");
            return;
        }

        // Wait for the clearing animation to finish
        Rs2Player.waitForAnimation();
        sleepUntil(() -> !Rs2Player.isAnimating() && Rs2Player.isInteracting() && Rs2Player.isMoving());
    }

    private void equipGraceful() {
        checkBeforeWithdrawAndEquip("GRACEFUL GLOVES");
        checkBeforeWithdrawAndEquip("GRACEFUL LEGS");
        checkBeforeWithdrawAndEquip("GRACEFUL CAPE");
        checkBeforeWithdrawAndEquip("GRACEFUL BOOTS");
        checkBeforeWithdrawAndEquip("GRACEFUL HOOD");
        checkBeforeWithdrawAndEquip("GRACEFUL TOP");
    }

    private void checkBeforeWithdrawAndEquip(String itemName) {
        if (!Rs2Equipment.isWearing(itemName)) {
            Rs2Bank.withdrawAndEquip(itemName);
            sleep(500, 1000);
        }
    }

    private boolean alreadyWearingGraceful() {
        return Rs2Equipment.isWearing("GRACEFUL LEGS")
                && Rs2Equipment.isWearing("GRACEFUL TOP")
                && Rs2Equipment.isWearing("GRACEFUL HOOD")
                && Rs2Equipment.isWearing("GRACEFUL BOOTS")
                && Rs2Equipment.isWearing("GRACEFUL GLOVES")
                && Rs2Equipment.isWearing("GRACEFUL CAPE");
    }

    private boolean shouldProtectTree(FarmTreeRunConfig config) {
        return config.protectTrees();
    }

    private boolean shouldProtectFruitTree(FarmTreeRunConfig config) {
        return config.protectFruitTrees();
    }

    private boolean isTreePatch(Patch patch) {
        return patch.kind == TreeKind.TREE;
    }

    private boolean isFruitTreePatch(Patch patch) {
        return patch.kind == TreeKind.FRUIT_TREE;
    }

    private boolean useCompostOnPatch(FarmTreeRunConfig config, Patch patch) {
        if (!config.useCompost() || compostItemId == null)
            return false;

        if (!config.protectTrees() && patch.kind == TreeKind.TREE)
            return true;

        return !config.protectFruitTrees() && patch.kind == TreeKind.FRUIT_TREE;
    }

    private List<BooleanSupplier> getSelectedTreePatches(FarmTreeRunConfig config) {
        // Create a list of all possible tree patches
        List<BooleanSupplier> allTreePatches = List.of(
                config::faladorTreePatch,
                config::gnomeStrongholdTreePatch,
                config::lumbridgeTreePatch,
                config::taverleyTreePatch,
                config::varrockTreePatch,
                config::farmingGuildTreePatch
        );

        // Filter the patches to include only those that return true
        return allTreePatches.stream()
                .filter(BooleanSupplier::getAsBoolean) // Filter patches that return true
                .collect(Collectors.toList()); // Collect into a new list
    }

    private List<BooleanSupplier> getSelectedFruitTreePatches(FarmTreeRunConfig config) {
        // Create a list of all possible fruit tree patches
        List<BooleanSupplier> allFruitTreePatches = List.of(
                config::brimhavenFruitTreePatch,
                config::catherbyFruitTreePatch,
                config::farmingGuildFruitTreePatch,
                config::lletyaFruitTreePatch,
                config::gnomeStrongholdFruitTreePatch,
                config::treeGnomeVillageFruitTreePatch
        );

        // Filter the patches to include only those that return true
        return allFruitTreePatches.stream()
                .filter(BooleanSupplier::getAsBoolean)
                .collect(Collectors.toList());
    }

    /**
     * Method to check whether player wants to use compost
     *
     * @param config
     * @return true if configured by player, else false
     */
    private boolean isCompostEnabled(FarmTreeRunConfig config) {
        if (!config.useCompost())
            return false;

        if (!getSelectedTreePatches(config).isEmpty() && !config.protectTrees())
            return true;

        return !getSelectedFruitTreePatches(config).isEmpty() && !config.protectFruitTrees();
    }

    private boolean isPatchEmpty(Patch patch) {
        String name = Rs2GameObject.getObjectComposition(patch.getId()).getName().toLowerCase();
        return name.endsWith("patch");
    }

    private static int getSaplingToUse(Patch patch, FarmTreeRunConfig config) {
        return patch.kind == TreeKind.TREE ?
                config.selectedTree().getSaplingId() :
                config.selectedFruitTree().getSaplingId();
    }

    /**
     * Handles gardeners at new location because Gagex is not consistent,
     * and they introduce new action for each new gardener.
     * TODO: This method can be replaced/improved if we hardcode each gardener's id inside patch enum.
     * Note that Gagex is also not consistent with action names
     *
     * @return true if gardener interaction successful, else false
     */
    private void handleExoticGardeners() {
        // Nikkie: Farming guild fruit tree gardener
        Rs2NpcModel nikkie = Rs2Npc.getNpc("Nikkie");

        // Rosie: Farming guild tree patch gardener
        Rs2NpcModel rosie = Rs2Npc.getNpc("Rosie");

        Rs2NpcModel npcToInteract = null;
        String paymentAction = "";

        // Rosie and Nikkie are close together.
        // We need to check their distance to make sure we got the correct gardener.
        if (rosie == null && nikkie == null) {
            Microbot.log("Gardeners in farming guild not found. Report this bug.");
            shutdown();
        } else if (nikkie != null && Rs2Player.distanceTo(nikkie.getWorldLocation()) <= 10) {
            npcToInteract = nikkie;
            paymentAction = "Pay (Fruit tree)";
        } else if (rosie != null && Rs2Player.distanceTo(rosie.getWorldLocation()) <= 10) {
            npcToInteract = rosie;
            paymentAction = "Pay (tree patch)";
        }

        Rs2Npc.interact(npcToInteract, paymentAction);
    }

    @Override
    public void shutdown() {
        items.clear();
        super.shutdown();
    }
}

/**
 * Tracks the farming patches from TimeTrackingPlugin.
 * Checks if specified patches are fully grown before going for run.
 * specified in the {@code ikiFarmConfig} object.
 *
 * @param config Configurations for this plugin. It should not be {@code null}.
 * @return {@code true} if all specified farming patches are fully grown and to check health,
 * {@code false} otherwise.
 */
//    private boolean trackFarmingPatches(ikiFarmConfig config) {
/// /        If fruit tree patch -> use compost (Can we use compost on weeded patch???)
/// /        If !weed on patch -> Use sapling on tree patch
/// /        Check if patch contains the planted tree. Can we use Runelite time tracking plugin?
//
//
//        TimeTrackingPlugin timeTrackingPlugin = (TimeTrackingPlugin) Microbot.getPlugin(TimeTrackingPlugin.class.getName());
//
//        FarmingWorld farmingWorld = timeTrackingPlugin.farmingTracker.farmingWorld;
//        WorldPoint location = client.getLocalPlayer().getWorldLocation();
//        Collection<FarmingRegion> newRegions = farmingWorld.getRegionsForLocation(location);
//        for (FarmingRegion region : newRegions) {
//            for (FarmingPatch patch : region.getPatches()) {
//                PatchPrediction prediction = timeTrackingPlugin.farmingTracker.predictPatch(patch);
//                Microbot.log(String.valueOf(prediction.getProduce()));
//                Microbot.log(String.valueOf(prediction.getCropState()));
//                Microbot.log(prediction.getStage() + "/" + prediction.getStages());
//            }
//        }
//
//        return true;
//    }