package net.runelite.client.plugins.microbot.mining;

import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

enum State {
    MINING,
    RESETTING,
}

public class AutoMiningScript extends Script {

    public static String version = "1.4.2";
    State state = State.MINING;

    public boolean run(AutoMiningConfig config) {
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.1;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                if (!config.ORE().hasRequiredLevel()) {
                    Microbot.log("You do not have the required mining level to mine this ore.");
                    return;
                }

                if (Rs2Equipment.isWearing("Dragon pickaxe"))
                    Rs2Combat.setSpecState(true, 1000);

                if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Microbot.pauseAllScripts) return;

                switch (state) {
                    case MINING:
                        if (Rs2Inventory.isFull()) {
                            state = State.RESETTING;
                            return;
                        }

                        GameObject rock = Rs2GameObject.findObject(config.ORE().getName(), true, config.distanceToStray(), true, initialPlayerLocation);

                        if (rock != null) {
                            if (Rs2GameObject.interact(rock)) {
                                Rs2Player.waitForXpDrop(Skill.MINING, true);
                                Rs2Antiban.actionCooldown();
                                Rs2Antiban.takeMicroBreakByChance();
                            }
                        }
                        break;
                    case RESETTING:
                        List<String> itemNames = Arrays.stream(config.itemsToBank().split(",")).map(String::toLowerCase).collect(Collectors.toList());

                        if (config.useBank()) {
                            if (!Rs2Bank.bankItemsAndWalkBackToOriginalPosition(itemNames, initialPlayerLocation, 0, config.distanceToStray()))
                                return;
                        } else {
                            Rs2Inventory.dropAllExcept("pickaxe");
                        }

                        state = State.MINING;
                        break;
                }
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }
}
