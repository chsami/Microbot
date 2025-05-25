package net.runelite.client.plugins.microbot.kebbitHunter;

import lombok.Getter;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

import java.util.concurrent.TimeUnit;

/**
 * Script states for the hunting process
 */
enum State {
    CATCHING, RETRIEVING, DROPPING
}

/**
 * Main automation script for Kebbit hunting
 */
public class HunterKabbitsScript extends Script {
    public static int KebbitCaught = 0;
    public boolean hasDied;

    @Getter
    private State currentState = State.CATCHING;
    private boolean droppingInProgress = false;

    /**
     * Starts the main script execution loop
     */
    public void run(HunterKebbitsConfig config, HunterKebbitsPlugin plugin) {
        super.run();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.isRunning()) return;
                if (droppingInProgress) return;

                // State transition logic
                if (isHintArrowNpcActive() && currentState != State.RETRIEVING) {
                    currentState = State.RETRIEVING;
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
                        handleCatchingState(config);
                        break;
                }

            } catch (Exception ex) {
                System.err.println("Script error: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
    }

    private void handleDroppingState(HunterKebbitsConfig config) {
        KebbitHunting currentKebbit = getKebbit(config);
        Integer furItemId = getSupportedFurItemId(currentKebbit);

        if (furItemId != null) {
            while (Rs2Inventory.contains(furItemId)) {
                Rs2Inventory.drop(furItemId);
                sleep(300, 600);
            }
        }

        if (Rs2Inventory.contains(ItemID.BONES)) {
            Rs2Inventory.interact(ItemID.BONES, "Bury");
        }

        currentState = State.CATCHING;
    }

    private void handleRetrievingState(HunterKebbitsConfig config) {
        final int FALCON_NPC_ID = 1342;
        NPC hintNpc = Microbot.getClient().getHintArrowNpc();

        if (hintNpc != null && hintNpc.getId() == FALCON_NPC_ID) {
            Rs2NpcModel model = new Rs2NpcModel(hintNpc);
            if (Rs2Npc.interact(model, "Retrieve")) {
                sleep(config.minSleepAfterCatch(), config.maxSleepAfterCatch());
                KebbitCaught++;
                currentState = State.CATCHING;
            }
        }
    }

    private void handleCatchingState(HunterKebbitsConfig config) {
        if (Rs2Inventory.isFull()) {
            currentState = State.DROPPING;
            return;
        }

        String npcName = getKebbit(config).getNpcName();
        if (Rs2Npc.interact(npcName, "Catch")) {
            sleep(config.MinSleepAfterHuntingKebbit(), config.MaxSleepAfterHuntingKebbit());
        }
    }

    private KebbitHunting getKebbit(HunterKebbitsConfig config) {
        int level = Microbot.getClient().getRealSkillLevel(Skill.HUNTER);
        if (config.progressiveHunting()) {
            if (level >= 69) return KebbitHunting.DASHING;
            if (level >= 57) return KebbitHunting.DARK;
            return KebbitHunting.SPOTTED;
        }
        return config.kebbitType();
    }

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

    private boolean isHintArrowNpcActive() {
        return Microbot.getClient().getHintArrowNpc() != null;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        KebbitCaught = 0;
        droppingInProgress = false;
    }
}