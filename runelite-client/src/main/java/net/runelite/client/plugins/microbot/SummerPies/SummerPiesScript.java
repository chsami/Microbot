package net.runelite.client.plugins.microbot.SummerPies;

import java.util.concurrent.TimeUnit;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.PluginDescriptor.Bank;

public class SummerPiesScript extends Script {
    private static final int PIE_SHELL = 2315;
    private static final int STRAWBERRY = 5504;
    private static final int WATERMELON = 5982;
    private static final int COOKING_APPLE = 1955;
    private static final int STRAWBERRY_PIE = 7212;
    private static final int WATERMELON_PIE = 7214;
    private static final int SUMMER_PIE = 7218;
    private static final int REQUIRED_QUANTITY = 7;

    public SummerPiesScript() {
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        this.mainScheduledFuture = this.scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }

                if (!super.run()) {
                    return;
                }

                if (this.hasEnoughSummerPies()) {
                    System.out.println("Detectadas 7 tartas de verano, abriendo banco...");
                    this.openBankToDepositAndRestart();
                }

                if (!this.isInventoryReady() || !this.hasRequiredMaterials()) {
                    System.out.println("Apertura de banco para preparar inventario...");
                    this.manageBankAndWithdrawMaterials();
                }

                if (this.hasRequiredMaterials()) {
                    this.createBatchOfSummerPies();
                }
            } catch (Exception var2) {
                Exception ex = var2;
                System.out.println("Error: " + ex.getMessage());
            }

        }, 0L, 1000L, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean hasEnoughSummerPies() {
        return Rs2Inventory.hasItem(new Integer[]{7218, 7});
    }

    private boolean isInventoryReady() {
        return Rs2Inventory.hasItem(new Integer[]{2315, 7}) && Rs2Inventory.hasItem(new Integer[]{5504, 7}) && Rs2Inventory.hasItem(new Integer[]{5982, 7}) && Rs2Inventory.hasItem(new Integer[]{1955, 7});
    }

    private boolean hasRequiredMaterials() {
        return Rs2Inventory.hasItem(new Integer[]{2315, 7}) && Rs2Inventory.hasItem(new Integer[]{5504, 7}) && Rs2Inventory.hasItem(new Integer[]{5982, 7}) && Rs2Inventory.hasItem(new Integer[]{1955, 7});
    }

    private void manageBankAndWithdrawMaterials() {
        Rs2Bank.openBank();
        Rs2Bank.depositAll();
        this.sleepUntil(Rs2Inventory::isEmpty, 5000);
        Rs2Bank.withdrawX(2315, 7);
        this.sleepUntil(() -> {
            return Rs2Inventory.hasItem(2315);
        }, 5000);
        Rs2Bank.withdrawX(5504, 7);
        this.sleepUntil(() -> {
            return Rs2Inventory.hasItem(5504);
        }, 5000);
        Rs2Bank.withdrawX(5982, 7);
        this.sleepUntil(() -> {
            return Rs2Inventory.hasItem(5982);
        }, 5000);
        Rs2Bank.withdrawX(1955, 7);
        this.sleepUntil(() -> {
            return Rs2Inventory.hasItem(1955);
        }, 5000);
        Rs2Bank.closeBank();
    }

    private void createBatchOfSummerPies() {
        System.out.println("Creando un lote de 7 tartas de verano...");

        for (int i = 0; i < 7; ++i) {
            if (Rs2Inventory.hasItem(2315) && Rs2Inventory.hasItem(5504)) {
                this.combineTwoItems(2315, 5504);
                this.sleepUntil(() -> {
                    return Rs2Inventory.hasItem(7212);
                }, 8000);
            }

            if (Rs2Inventory.hasItem(7212) && Rs2Inventory.hasItem(5982)) {
                this.combineTwoItems(7212, 5982);
                this.sleepUntil(() -> {
                    return Rs2Inventory.hasItem(7214);
                }, 8000);
            }

            if (Rs2Inventory.hasItem(7214) && Rs2Inventory.hasItem(1955)) {
                this.combineTwoItems(7214, 1955);
                this.sleepUntil(() -> {
                    return Rs2Inventory.hasItem(7218);
                }, 1000);
            }
        }

        this.openBankToDepositAndRestart();
    }

    private void combineTwoItems(int item1, int item2) {
        if (Rs2Inventory.hasItem(item1) && Rs2Inventory.hasItem(item2)) {
            Rs2Inventory.combineClosest(item1, item2);
            this.sleepUntil(() -> {
                return Rs2Widget.isWidgetVisible(17694734);
            }, 8000);
            this.confirmCombination();
        }

    }

    private void confirmCombination() {
        Rs2Keyboard.keyPress(32);
        this.sleep(3000, 3500);
        Rs2Keyboard.keyRelease(32);
        this.sleep(5000, 6000);
    }

    private void openBankToDepositAndRestart() {
        Rs2Bank.openBank();
        Rs2Bank.depositAll(7218);
        this.sleepUntil(() -> {
            return !Rs2Inventory.hasItem(7218);
        }, 1000);
        this.manageBankAndWithdrawMaterials();
    }

    public void shutdown() {
        super.shutdown();
    }
}