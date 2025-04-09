package net.runelite.client.plugins.microbot.smelting;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.smelting.enums.Ores;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class AutoSmeltingScript extends Script {

    public static String version = "1.0.3";

    public boolean run(AutoSmeltingConfig config) {
        initialPlayerLocation = null;
        Rs2Walker.disableTeleports = true;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applySmithingSetup();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (config.SELECTED_BAR_TYPE().getRequiredSmithingLevel() > Rs2Player.getBoostedSkillLevel(Skill.SMITHING)) {
                    Microbot.showMessage("Your smithing level isn't high enough for " + config.SELECTED_BAR_TYPE().toString());
                    super.shutdown();
                    return;
                }

                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating(6500) || Microbot.pauseAllScripts) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                // walk to bank until it's open then deposit everything and withdraw materials
                if (!inventoryHasMaterialsForOneBar(config)) {
                    if (!Rs2Bank.isOpen()) {
                        Rs2Bank.walkToBankAndUseBank();
                        return;
                    }
                    Rs2Bank.depositAll();
                    if (config.SELECTED_BAR_TYPE().getId() == ItemID.IRON_BAR && Rs2Bank.hasItem(ItemID.RING_OF_FORGING) && !Rs2Equipment.isWearing(ItemID.RING_OF_FORGING)) {
                        Rs2Bank.withdrawAndEquip(ItemID.RING_OF_FORGING);
                        return;
                    }
                    withdrawRightAmountOfMaterials(config);
                    return;
                }

                // walk to the initial position (near furnace)
                if (initialPlayerLocation.distanceTo(Rs2Player.getWorldLocation()) > 4) {
                    if (Rs2Bank.isOpen())
                        Rs2Bank.closeBank();
                    Rs2Walker.walkTo(initialPlayerLocation, 4);
                    return;
                }

                // interact with the furnace until the smelting dialogue opens in chat, click the selected bar icon
                GameObject furnace = Rs2GameObject.findObject("furnace", true, 10, false, initialPlayerLocation);
                if (furnace != null) {
                    Rs2GameObject.interact(furnace, "smelt");
                    Rs2Widget.sleepUntilHasWidgetText("What would you like to smelt?", 270, 5, false, 5000);
                    Rs2Widget.clickWidget(config.SELECTED_BAR_TYPE().getName());
                    Rs2Widget.sleepUntilHasNotWidgetText("What would you like to smelt?", 270, 5, false, 5000);
                    Rs2Antiban.actionCooldown();
                    Rs2Antiban.takeMicroBreakByChance();
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    private boolean inventoryHasMaterialsForOneBar(AutoSmeltingConfig config) {
        for (Map.Entry<Ores, Integer> requiredMaterials : config.SELECTED_BAR_TYPE().getRequiredMaterials().entrySet()) {
            Integer amount = requiredMaterials.getValue();
            String name = requiredMaterials.getKey().toString();
            if (!Rs2Inventory.hasItemAmount(name, amount, false, true)) {
                return false;
            }
        }
        return true;
    }
    private void withdrawRightAmountOfMaterials(AutoSmeltingConfig config) {
        for (Map.Entry<Ores, Integer> requiredMaterials : config.SELECTED_BAR_TYPE().getRequiredMaterials().entrySet()) {
            Integer amountForOne = requiredMaterials.getValue();
            String name = requiredMaterials.getKey().toString();
            int totalAmount = config.SELECTED_BAR_TYPE().maxBarsForFullInventory() * amountForOne;
            if (!Rs2Bank.hasBankItem(name, totalAmount, true)) {
                Microbot.showMessage(MessageFormat.format("Required Materials not in bank. You need {1} {0}.", name, totalAmount));
                super.shutdown();
            }
            Rs2Bank.withdrawX(name, totalAmount, true);
            sleepUntil(() -> Rs2Inventory.hasItemAmount(name, totalAmount, false, true), 3500);

            // Exit if we did not end up finding it.
            if (!Rs2Inventory.hasItemAmount(name, totalAmount, false, true)) {
                Microbot.showMessage("Could not find item in bank.");
                shutdown();
            }
        }
    }
}
