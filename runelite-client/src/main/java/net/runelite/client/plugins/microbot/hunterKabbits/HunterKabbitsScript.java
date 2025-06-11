package net.runelite.client.plugins.microbot.hunterKabbits;

import lombok.Getter;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Enum representing the current bot state.
 */
enum State {
    CATCHING, RETRIEVING, DROPPING
}

/**
 * Automated script for falconry Kebbit hunting in Old School RuneScape.
 * Manages catching, retrieving falcon, and inventory logic.
 */
public class HunterKabbitsScript extends Script {

    // Falcon NPC IDs - use Set for multiple falcon types
    private static final int GYR_FALCON_NPC_ID = 1342;
    private static final Set<Integer> VALID_FALCON_NPC_IDS = Set.of(1342, 1343, 1344, 1345, 1346);

    public static int KebbitCaught = 0;
    public boolean hasDied;

    @Getter
    private State currentState = State.CATCHING;
    private boolean droppingInProgress = false;

    /**
     * Main method to start the script with given config and plugin context.
     */
    public void run(HunterKebbitsConfig config, HunterKebbitsPlugin plugin) {
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_HUNTER);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !this.isRunning()) return;
                if (droppingInProgress) return;

                if (isHintArrowNpcActive() && currentState != State.RETRIEVING) {
                    currentState = State.RETRIEVING;
                    return;
                }

                if (currentState == State.CATCHING && !isFalconWithPlayer()) {
                    return;
                }

                switch (currentState) {
                    case DROPPING:
                        handleDroppingState(config);
                        break;
                    case RETRIEVING:
                        handleRetrievingState(config);
                        break;
                    case CATCHING:
                    default:
                        if (Rs2Inventory.isFull() || Rs2Inventory.getEmptySlots() <= 1) {
                            currentState = State.DROPPING;
                        } else {
                            handleCatchingState(config);
                        }
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                Microbot.status = "Error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
    }

    /**
     * Handles dropping or burying items when inventory is full.
     */
    private void handleDroppingState(HunterKebbitsConfig config) {
        KebbitHunting currentKebbit = getKebbit(config);
        Integer furItemId = getSupportedFurItemId(currentKebbit);
        if (furItemId == null) {
            currentState = State.CATCHING;
            return;
        }

        while (Rs2Inventory.contains(furItemId) || Rs2Inventory.contains(ItemID.BONES)) {
            if (!isRunning()) break;

            if (Rs2Inventory.contains(furItemId)) Rs2Inventory.drop(furItemId);

            if (Rs2Inventory.contains(ItemID.BONES)) {
                if (config.buryBones()) {
                    Rs2Inventory.interact(ItemID.BONES, "Bury");
                } else {
                    Rs2Inventory.drop(ItemID.BONES);
                }
                sleep(300, 600);
            }
        }
        currentState = State.CATCHING;
    }

    /**
     * Handles retrieving the falcon from the NPC after a catch attempt.
     */
    private void handleRetrievingState(HunterKebbitsConfig config) {
        NPC hintNpc = Microbot.getClient().getHintArrowNpc();

        if (hintNpc != null && VALID_FALCON_NPC_IDS.contains(hintNpc.getId())) {
            Rs2NpcModel model = new Rs2NpcModel(hintNpc);
            boolean retrieved = false;
            for (int i = 0; i < 5; i++) {
                if (!isRunning()) break;

                if (Rs2Npc.interact(model, "Retrieve")) {
                    retrieved = true;
                    break;
                }
                sleep(200, 300);
            }

            if (retrieved) {
                sleep(config.minSleepAfterCatch(), config.maxSleepAfterCatch());
                KebbitCaught++;
                currentState = State.CATCHING;
            }
        } else {
            boolean anyFalconStillActive = Rs2Npc.getNpcs()
                    .anyMatch(npc -> VALID_FALCON_NPC_IDS.contains(npc.getId()));
            if (!anyFalconStillActive) {
                currentState = State.CATCHING;
            }
        }
    }

    /**
     * Handles interacting with a Kebbit NPC to attempt a catch.
     */
    private void handleCatchingState(HunterKebbitsConfig config) {
        String npcName = getKebbit(config).getNpcName();

        if (Rs2Npc.interact(npcName, "Catch")) {
            boolean falconActive = false;
            for (int i = 0; i < 10; i++) {
                if (!isRunning()) break;

                boolean found = Rs2Npc.getNpcs().anyMatch(npc -> VALID_FALCON_NPC_IDS.contains(npc.getId()));
                if (found || isHintArrowNpcActive()) {
                    falconActive = true;
                    break;
                }
                sleep(200, 300);
            }

            if (falconActive) {
                currentState = State.RETRIEVING;
                return;
            }
        }
        sleep(config.MinSleepAfterHuntingKebbit(), config.MaxSleepAfterHuntingKebbit());
    }

    /**
     * Returns the proper Kebbit type for the player's level or config.
     */
    private KebbitHunting getKebbit(HunterKebbitsConfig config) {
        int level = Microbot.getClient().getRealSkillLevel(Skill.HUNTER);
        if (config.progressiveHunting()) {
            if (level >= 69) return KebbitHunting.DASHING;
            if (level >= 57) return KebbitHunting.DARK;
            return KebbitHunting.SPOTTED;
        }
        return config.kebbitType() != null ? config.kebbitType() : KebbitHunting.SPOTTED;
    }

    /**
     * Returns the item ID for the fur based on Kebbit type.
     */
    private Integer getSupportedFurItemId(KebbitHunting kebbit) {
        switch (kebbit) {
            case SPOTTED:
                return ItemID.SPOTTED_KEBBIT_FUR;
            case DASHING:
                return ItemID.DASHING_KEBBIT_FUR;
            case DARK:
                return ItemID.DARK_KEBBIT_FUR;
            default:
                return null;
        }
    }

    /**
     * Checks if a hint arrow is currently pointing to an NPC.
     */
    private boolean isHintArrowNpcActive() {
        return Microbot.getClient().getHintArrowNpc() != null;
    }

    /**
     * Returns true if falcon is with player (not visible and no active hint arrow).
     */
    private boolean isFalconWithPlayer() {
        boolean falconVisible = Rs2Npc.getNpcs().anyMatch(npc -> VALID_FALCON_NPC_IDS.contains(npc.getId()));
        return !falconVisible && !isHintArrowNpcActive();
    }

    /**
     * Sets up all antiban settings.
     */
    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.actionCooldownChance = 0.1;
    }

    /**
     * Called on script shutdown, resets state and status.
     */
    @Override
    public void shutdown() {
        super.shutdown();
        KebbitCaught = 0;
        droppingInProgress = false;
        Microbot.status = "Script stopped.";
    }
}
