package net.runelite.client.plugins.microbot.qualityoflife;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class QoLScript extends Script {

    private final boolean bankOpen = false;

    public boolean run(QoLConfig config) {
        Microbot.enableAutoRunOn = false;
        loadNpcData();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                if (QoLPlugin.executeBankActions) {
                    handleBankActions();
                }

                if (QoLPlugin.executeFurnaceActions) {
                    handleFurnaceActions();
                }

                if (config.useDialogueAutoContinue() && Rs2Dialogue.isInDialogue()) {
                    handleDialogueContinue();
                }

            } catch (Exception ex) {
                log.error("Error in QoLScript execution: {}", ex.getMessage(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void loadNpcData() {
        try {
            Rs2NpcManager.loadJson();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load NPC data", e);
        }
    }

    // handle dialogue continue
    private void handleDialogueContinue() {
        Rs2Dialogue.clickContinue();
    }

    private void handleBankActions() {
        if (!openBank()) {
            log.warn("Bank did not open");
            QoLPlugin.executeBankActions = false;
            return;
        }

        for (NewMenuEntry menuEntry : QoLPlugin.bankMenuEntries) {
            processBankMenuEntry(menuEntry);
        }
        QoLPlugin.executeBankActions = false;
    }

    private boolean openBank() {
        sleepUntil(Rs2Bank::isOpen, 10000);
        return Rs2Bank.isOpen();
    }

    private void processBankMenuEntry(NewMenuEntry menuEntry) {
        log.info("Executing action: {} {}", menuEntry.getOption(), menuEntry.getTarget());

        if (menuEntry.getOption().contains("Withdraw")) {
            int itemTab = Rs2Bank.getItemTabForBankItem(menuEntry.getParam0());
            openAndScrollToTab(itemTab, menuEntry);
        } else if (menuEntry.getOption().contains("Deposit") &&
                !menuEntry.getOption().equals("Deposit inventory") &&
                !menuEntry.getOption().equals("Deposit worn items")) {

            if (Rs2Inventory.isSlotEmpty(menuEntry.getParam0())) {
                int nonEmptySlot = Rs2Inventory.slot(menuEntry.getItemId());
                if (nonEmptySlot != -1) {
                    menuEntry.setParam0(nonEmptySlot);
                } else {
                    log.info("No item found in inventory to deposit, skipping action");
                    return;
                }
            }
        }
        Microbot.doInvoke(menuEntry, Objects.requireNonNull(menuEntry.getWidget()).getBounds());
        Rs2Random.wait(200, 500);
    }

    private void openAndScrollToTab(int itemTab, NewMenuEntry menuEntry) {
        if (!Rs2Bank.isTabOpen(itemTab)) {
            log.info("Switching to tab: {}", itemTab);
            Rs2Bank.openTab(itemTab);
            sleepUntil(() -> Rs2Bank.isTabOpen(itemTab), 5000);
        }

        Rs2Bank.scrollBankToSlot(menuEntry.getParam0());
        Rs2Random.wait(200, 500);
        menuEntry.setWidget(Rs2Bank.getItemWidget(menuEntry.getParam0()));
    }

    private void handleFurnaceActions() {
        if (!openFurnace()) {
            log.warn("Production widget did not open");
            QoLPlugin.executeFurnaceActions = false;
            return;
        }

        for (NewMenuEntry menuEntry : QoLPlugin.furnaceMenuEntries) {
            processFurnaceMenuEntry(menuEntry);
        }
        QoLPlugin.executeFurnaceActions = false;
    }

    private boolean openFurnace() {
        sleepUntil(Rs2Widget::isProductionWidgetOpen, 10000);
        return Rs2Widget.isProductionWidgetOpen();
    }

    private void processFurnaceMenuEntry(NewMenuEntry menuEntry) {
        log.info("Executing action: {} {}", menuEntry.getOption(), menuEntry.getTarget());
        Microbot.doInvoke(menuEntry, Objects.requireNonNull(menuEntry.getWidget()).getBounds());
        Rs2Random.wait(200, 500);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        log.info("QoLScript shutdown complete.");
    }
}
