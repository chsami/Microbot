package net.runelite.client.plugins.microbot.SummerPies;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.runelite.api.Item;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
//import static net.runelite.client.plugins.PluginDescriptor.Bank;

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
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (this.hasEnoughSummerPies()) {
                    System.out.println("Detectadas 7 tartas de verano, abriendo banco...");
                    this.openBankToDepositAndRestart();
                }

                if (!this.hasRequiredMaterials()) {
                    System.out.println("Gestionando banco y comprando materiales si es necesario...");
                    this.manageBankAndWithdrawMaterials();
                }

                if (this.hasRequiredMaterials()) {
                    this.createBatchOfSummerPies();
                }
            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }, 0L, 1000L, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean hasEnoughSummerPies() {
        return Rs2Inventory.hasItem(SUMMER_PIE, REQUIRED_QUANTITY);
    }

    private boolean hasRequiredMaterials() {
        return Rs2Inventory.hasItem(PIE_SHELL, REQUIRED_QUANTITY)
                && Rs2Inventory.hasItem(STRAWBERRY, REQUIRED_QUANTITY)
                && Rs2Inventory.hasItem(WATERMELON, REQUIRED_QUANTITY)
                && Rs2Inventory.hasItem(COOKING_APPLE, REQUIRED_QUANTITY);
    }

    private void manageBankAndWithdrawMaterials() {
        Rs2Bank.openBank();
        Rs2Bank.depositAll();
        this.sleepUntil(Rs2Inventory::isEmpty, 5000);

        withdrawOrBuy("Pie shell", REQUIRED_QUANTITY);
        withdrawOrBuy("Strawberry", REQUIRED_QUANTITY);
        withdrawOrBuy("Watermelon", REQUIRED_QUANTITY);
        withdrawOrBuy("Cooking apple", REQUIRED_QUANTITY);

        Rs2Bank.closeBank();
    }

    private void withdrawOrBuy(String Itemname, int quantity) {
        if (Rs2Bank.hasItem(Itemname)) {
            Rs2Bank.withdrawX(Itemname, quantity);
            this.sleepUntil(() -> Rs2Inventory.hasItem(Itemname), 5000);
        } else {
            buyFromGrandExchange(Itemname, quantity);
        }
    }

    private void buyFromGrandExchange(String ItemName, int quantity) {
        System.out.println("Item no disponible en el banco. Comprando en el GE: " + ItemName);
        Rs2Bank.closeBank();
        sleep(600, 900);
        Rs2Npc.interact(2149, "Exchange");
        sleepUntil(() -> Rs2GrandExchange.isOpen(), 10000);
        Rs2GrandExchange.buyItem(ItemName, Rs2GrandExchange.getItemPrice() * 2, quantity); // Precio más alto para compra rápida
        this.sleepUntil(() -> Rs2Inventory.hasItem(ItemName), 10000);
        Rs2GrandExchange.closeExchange();
    }

    private int getMarketPrice(int itemId) {
        Map<Integer, Integer> priceMap = Map.of(
                PIE_SHELL, 500,
                STRAWBERRY, 200,
                WATERMELON, 600,
                COOKING_APPLE, 300
        );
        return priceMap.getOrDefault(itemId, 1000);
    }

    private void createBatchOfSummerPies() {
        System.out.println("Creando un lote de 7 tartas de verano...");

        for (int i = 0; i < 7; ++i) {
            if (Rs2Inventory.hasItem(PIE_SHELL) && Rs2Inventory.hasItem(STRAWBERRY)) {
                this.combineTwoItems(PIE_SHELL, STRAWBERRY);
                this.sleepUntil(() -> Rs2Inventory.hasItem(STRAWBERRY_PIE), 8000);
            }

            if (Rs2Inventory.hasItem(STRAWBERRY_PIE) && Rs2Inventory.hasItem(WATERMELON)) {
                this.combineTwoItems(STRAWBERRY_PIE, WATERMELON);
                this.sleepUntil(() -> Rs2Inventory.hasItem(WATERMELON_PIE), 8000);
            }

            if (Rs2Inventory.hasItem(WATERMELON_PIE) && Rs2Inventory.hasItem(COOKING_APPLE)) {
                this.combineTwoItems(WATERMELON_PIE, COOKING_APPLE);
                this.sleepUntil(() -> Rs2Inventory.hasItem(SUMMER_PIE), 1000);
            }
        }

        this.openBankToDepositAndRestart();
    }

    private void combineTwoItems(int item1, int item2) {
        if (Rs2Inventory.hasItem(item1) && Rs2Inventory.hasItem(item2)) {
            Rs2Inventory.combineClosest(item1, item2);
            this.sleepUntil(() -> Rs2Widget.isWidgetVisible(17694734), 8000);
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
        Rs2Bank.depositAll(SUMMER_PIE);
        this.sleepUntil(() -> !Rs2Inventory.hasItem(SUMMER_PIE), 1000);
        this.manageBankAndWithdrawMaterials();
    }

    public void shutdown() {
        super.shutdown();
    }
}
