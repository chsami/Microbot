package net.runelite.client.plugins.microbot.nateplugins.skilling.natefishing;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.nateplugins.skilling.natefishing.enums.Fish;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.validateInteractable;

enum State {
    FISHING,
    RESETTING,
}

public class AutoFishingScript extends Script {

    public static String version = "1.6.2";
    private String fishAction = "";
    State state;

    public boolean run(AutoFishConfig config) {
        initialPlayerLocation = null;
        Fish fish = config.fish();

        fishAction = "";
        state = State.FISHING;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                if (config.useEchoHarpoon()) {
                    if (!Rs2Equipment.hasEquipped(ItemID.ECHO_HARPOON)) {
                        if (!Rs2Inventory.hasItem(ItemID.ECHO_HARPOON)) {
                            Microbot.showMessage("Missing Echo harpoon");
                            shutdown();
                            return;
                        }
                    }
                }

                if (!hasRequiredItems(fish)) {
                    Microbot.showMessage("You are missing the required tools to catch this fish");
                    shutdown();
                    return;
                }

                if (Rs2Player.isMoving() || Rs2Antiban.getCategory().isBusy() || Microbot.pauseAllScripts) return;

                switch (state) {
                    case FISHING:
                        Rs2NpcModel fishingSpot = getFishingSpot(fish);
                        if (fishingSpot == null || Rs2Inventory.isFull()) {
                            state = State.RESETTING;
                            return;
                        }
                        
                        if (fishAction.isEmpty()) {
                            fishAction = Rs2Npc.getAvailableAction(fishingSpot, fish.getActions());
                            if (fishAction.isEmpty()) {
                                Microbot.showMessage("Unable to find action for fishing spot!");
                                shutdown();
                            }
                        }

                        if (!Rs2Camera.isTileOnScreen(fishingSpot.getLocalLocation())) {
                            validateInteractable(fishingSpot);
                        }

                        if (fish.equals(Fish.KARAMBWAN) && Rs2Inventory.hasItem(ItemID.RAW_KARAMBWANJI)) {
                            if (Rs2Inventory.hasItem(ItemID.KARAMBWAN_VESSEL)) {
                                Rs2Inventory.waitForInventoryChanges(() -> Rs2Inventory.combineClosest(ItemID.RAW_KARAMBWANJI, ItemID.KARAMBWAN_VESSEL), 600, 5000);
                            }
                        }
                        
                        if (Rs2Npc.interact(fishingSpot, fishAction)) {
                            Rs2Antiban.actionCooldown();
                            Rs2Antiban.takeMicroBreakByChance();
                        }
                        break;
                    case RESETTING:
                        if (config.useBank()) {
                            BankLocation nearestBank = Rs2Bank.getNearestBank();
                            boolean isBankOpen = Rs2Bank.isNearBank(nearestBank, 8) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank(nearestBank);
                            if (!isBankOpen || !Rs2Bank.isOpen()) return;
                            Rs2Bank.depositAll(i -> fish.getRawNames().stream().anyMatch(fl -> i.getName().equalsIgnoreCase(fl)));
                            Rs2Inventory.waitForInventoryChanges(1800);
                            if (config.shouldBankClueBottles()) {
                                Rs2Bank.depositAll("clue bottle");
                                Rs2Inventory.waitForInventoryChanges(1800);
                            }
                            if (config.shouldBankCaskets()) {
                                Rs2Bank.depositAll("casket");
                                Rs2Inventory.waitForInventoryChanges(1800);
                            }
                            Rs2Bank.emptyFishBarrel();
                                
                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen());

                            Rs2Walker.walkTo(initialPlayerLocation);

                        } else if (config.useDepositBox()) {
                            if (Rs2DepositBox.walkToAndUseDepositBox()) {
                                Rs2DepositBox.depositAll(i -> fish.getRawNames().stream().anyMatch(fl -> i.getName().equalsIgnoreCase(fl)));
                                Rs2Inventory.waitForInventoryChanges(1800);
                                if (config.shouldBankClueBottles()) {
                                    Rs2DepositBox.depositAll("clue bottle");
                                    Rs2Inventory.waitForInventoryChanges(1800);
                                }
                                if (config.shouldBankCaskets()) {
                                    Rs2DepositBox.depositAll("casket");
                                    Rs2Inventory.waitForInventoryChanges(1800);
                                }

                                Rs2DepositBox.closeDepositBox();
                                sleepUntil(() -> !Rs2DepositBox.isOpen());
                                
                                Rs2Walker.walkTo(initialPlayerLocation);
                            }
                        } else {
                            Rs2Inventory.dropAll(i -> fish.getRawNames().stream().anyMatch(fl -> fl.equalsIgnoreCase(i.getName())), config.getDropOrder());
                        }
                        state = State.FISHING;
                        break;
                }
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean hasRequiredItems(Fish fish) {
        switch (fish) {
            case MONKFISH:
            case KARAMBWANJI:
            case SHRIMP:
                return Rs2Inventory.hasItem("small fishing net");
            case SARDINE:
            case PIKE:
                return Rs2Inventory.hasItem("fishing rod") && Rs2Inventory.hasItem("bait");
            case MACKEREL:
                return Rs2Inventory.hasItem("big fishing net");
            case TROUT:
                return Rs2Inventory.hasItem("fly fishing rod") && Rs2Inventory.hasItem("feather");
            case TUNA:
            case SHARK:
                return Rs2Inventory.hasItem("harpoon") || Rs2Equipment.isWearing("harpoon");
            case LOBSTER:
                return Rs2Inventory.hasItem("lobster pot");
            case LAVA_EEL:
                return Rs2Inventory.hasItem("oily fishing rod") && Rs2Inventory.hasItem("fishing bait");
            case CAVE_EEL:
                return Rs2Inventory.hasItem("fishing rod") && Rs2Inventory.hasItem("fishing bait");
            case ANGLERFISH:
                return Rs2Inventory.hasItem("fishing rod") && Rs2Inventory.hasItem("sandworms");
            case KARAMBWAN:
                return (Rs2Inventory.hasItem(ItemID.KARAMBWAN_VESSEL) || Rs2Inventory.hasItem(ItemID.KARAMBWAN_VESSEL_3159) && Rs2Inventory.hasItem(ItemID.RAW_KARAMBWANJI));
            default:
                return false;
        }
    }

    private Rs2NpcModel getFishingSpot(Fish fish) {
        return Arrays.stream(fish.getFishingSpot())
                .mapToObj(Rs2Npc::getNpc)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }
}
