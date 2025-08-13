package net.runelite.client.plugins.microbot.bga.autoessencemining;

import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;


public class EssenceMiningScript extends Script {

    public static final String version = "1.0.0";
    private static final WorldPoint AUBURY_LOCATION = new WorldPoint(3253, 3399, 0);
    private static final int ESSENCE_MINE_REGION = 11595; // Rune essence mine region ID
    
    private EssenceMiningState state = EssenceMiningState.WALKING_TO_AUBURY;
    private boolean hasTeleportedWithAubury = false;
    private boolean isInEssenceMine = false;
    private boolean needsToBank = false;

    public boolean run(EssenceMiningConfig config) {
        initialPlayerLocation = Rs2Player.getWorldLocation();
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.1;
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

                isInEssenceMine = (Rs2Player.getWorldLocation().getRegionID() == ESSENCE_MINE_REGION);
                needsToBank = Rs2Inventory.isFull();

                if (isInEssenceMine) {
                    if (!needsToBank) {
                        hasTeleportedWithAubury = true;
                        state = EssenceMiningState.MINING_ESSENCE;
                    } else {
                        state = EssenceMiningState.USING_PORTAL;
                    }
                } else {
                    if (needsToBank) {
                        state = EssenceMiningState.BANKING;
                    } else {
                        if (Rs2Player.getWorldLocation().distanceTo(AUBURY_LOCATION) <= 8) {
                            if (hasTeleportedWithAubury) {
                                hasTeleportedWithAubury = false;
                            }
                            state = EssenceMiningState.TELEPORTING_WITH_AUBURY;
                        } else {
                            state = EssenceMiningState.WALKING_TO_AUBURY;
                        }
                    }
                }

                switch (state) {
                    case WALKING_TO_AUBURY:
                        handleWalkingToAubury();
                        break;
                    case TELEPORTING_WITH_AUBURY:
                        handleTeleportingWithAubury();
                        break;
                    case MINING_ESSENCE:
                        handleMiningEssence();
                        break;
                    case USING_PORTAL:
                        handleUsingPortal();
                        break;
                    case BANKING:
                        handleBanking();
                        break;
                }
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleWalkingToAubury() {
        Microbot.status = "Walking to Aubury";
        
        if (!Rs2Walker.walkTo(AUBURY_LOCATION)) {
            Microbot.log("Failed to walk to Aubury");
        }
    }

    private void handleTeleportingWithAubury() {
        Microbot.status = "Teleporting with Aubury";
        
        Rs2NpcModel aubury = Rs2Npc.getNpc("Aubury");
        if (aubury != null) {
            if (Rs2Npc.interact(aubury, "Teleport")) {
                Rs2Player.waitForAnimation(3000);
                hasTeleportedWithAubury = true;
            }
        } else {
            Microbot.log("Aubury not found");
        }
    }

    private void handleMiningEssence() {
        Microbot.status = "Mining essence";
        
        GameObject essenceRock = Rs2GameObject.getGameObject("Rune Essence", false);
        
        if (essenceRock != null) {
            if (Rs2GameObject.interact(essenceRock, "Mine")) {
                Rs2Player.waitForXpDrop(Skill.MINING, true);
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        } else {
            Microbot.log("No essence rocks found nearby");
            Microbot.log("Current region: " + Rs2Player.getWorldLocation().getRegionID());
        }
    }

    private void handleUsingPortal() {
        Microbot.status = "Using portal to exit";

        boolean interacted = false;

        GameObject portal = Rs2GameObject.getGameObject("Portal", false);

        if (portal != null) {
            if (Rs2GameObject.interact(portal)) {
                interacted = true;
            }

            if (interacted) {
                Rs2Player.waitForAnimation(3000);
                hasTeleportedWithAubury = false;
            } else {
                Microbot.log("Portal not found or interaction failed");
            }
        }
    }


    private void handleBanking() {
        Microbot.status = "Banking at Varrock East";
        
        WorldPoint varrockEastBank = new WorldPoint(3253, 3420, 0);
        
        if (!Rs2Bank.isOpen()) {
            if (Rs2Player.getWorldLocation().distanceTo(varrockEastBank) > 10) {
                Rs2Walker.walkTo(varrockEastBank);
                return;
            }
            
            if (!Rs2Bank.walkToBankAndUseBank()) {
                return;
            }
        }

        if (Rs2Bank.isOpen()) {
            Rs2Bank.depositAllExcept("pickaxe");
            Rs2Bank.closeBank();
            
            hasTeleportedWithAubury = false;
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }
}