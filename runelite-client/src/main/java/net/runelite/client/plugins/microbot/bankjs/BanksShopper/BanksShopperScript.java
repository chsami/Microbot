package net.runelite.client.plugins.microbot.bankjs.BanksShopper;

import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

enum ShopperState {
    SHOPPING,
    BANKING,
    HOPPING
}

public class BanksShopperScript extends Script {

    public static String version = "1.4.0";

    private final BanksShopperPlugin plugin;
    private ShopperState state = ShopperState.SHOPPING;

    public BanksShopperScript(final BanksShopperPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run(BanksShopperConfig config) {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        Microbot.enableAutoRunOn = false;
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn() || Rs2AntibanSettings.actionCooldownActive) return;

                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                switch (state) {
                    case SHOPPING:
                        boolean missingAllRequiredItems = plugin.getItemNames().stream().noneMatch((itemName) -> {
                            if (itemName == null || itemName.isEmpty()) return false;
                            if (itemName.matches("\\d+")) {
                                return Rs2Inventory.hasItem(Integer.parseInt(itemName));
                            } else {
                                return Rs2Inventory.hasItem(itemName);
                            }
                        });

                        if (missingAllRequiredItems && plugin.getSelectedAction() == Actions.SELL) {
                            Microbot.status = "[Shutting down] - Reason: Not enough supplies.";
                            Microbot.showMessage(Microbot.status);
                            shutdown();
                            return;
                        }

                        sleepUntil(() -> Rs2Shop.openShop(plugin.getNpcName(), plugin.isUseExactNaming()), 5000);

                        boolean successfullAction = false;

                        if (Rs2Shop.isOpen()) {
                            for (String itemName : plugin.getItemNames()) {
                                if (!isRunning() || Microbot.pauseAllScripts.get()) break;
                                if (itemName.length() <= 1) continue;

                                switch (plugin.getSelectedAction()) {
                                    case BUY:
                                        // Check if name is purely numeric or alphanumeric
                                        if (itemName.matches("\\d+")) {
                                            if (!Rs2Shop.hasMinimumStock(Integer.parseInt(itemName), plugin.getMinStock())) continue;
                                            successfullAction = processBuyAction(Integer.parseInt(itemName), plugin.getSelectedQuantity().toString());
                                        } else {
                                            if (!Rs2Shop.hasMinimumStock(itemName, plugin.getMinStock())) continue;
                                            successfullAction = processBuyAction(itemName, plugin.getSelectedQuantity().toString());
                                        }
                                        break;
                                    case SELL:
                                        if (Rs2Shop.isFull()) continue;
                                        // Check if name is purely numeric or alphanumeric
                                        if (itemName.matches("\\d+")) {
                                            while(isRunning() && processSellAction(Integer.parseInt(itemName), plugin.getSelectedQuantity().toString())){
                                                sleepGaussian(200, 40);
                                                if (Rs2Shop.hasMinimumStock(Integer.parseInt(itemName), plugin.getMinStock())){
                                                    System.out.println("Stop selling over the minimum stock for item: " + itemName);
                                                    successfullAction = true;
                                                    break;
                                                }
                                            }
                                        } else {
                                            while(isRunning() && processSellAction(itemName, plugin.getSelectedQuantity().toString())){
                                                sleepGaussian(200, 40);
                                                if (Rs2Shop.hasMinimumStock(itemName, plugin.getMinStock())){
                                                    System.out.println("Stop selling over the minimum stock for item: " + itemName);
                                                    successfullAction = true;
                                                    break;
                                                }
                                            }
                                        }
                                        break;
                                    default:
                                        System.out.println("Invalid action specified in config.");
                                }
                            }

                            Rs2Shop.closeShop();
                            if (successfullAction) {
                                state = ShopperState.HOPPING;
                                return;
                            }
                        }
                        break;
                    case BANKING:
                        if (!Rs2Bank.bankItemsAndWalkBackToOriginalPosition(plugin.getItemNames(), initialPlayerLocation))
                            return;
                        state = ShopperState.SHOPPING;
                        break;
                    case HOPPING:
                        hopWorld();
                        state = ShopperState.SHOPPING;
                        break;
                }
            } catch (Exception ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        if (Rs2Shop.isOpen()) {
            Rs2Shop.closeShop();
        }

        if (plugin.isUseLogout()) {
            Rs2Player.logout();
        }

        state = ShopperState.SHOPPING; // Reset state to SHOPPING for next run
        initialPlayerLocation = null; // Reset initial player location

        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    /**
     * Hops to a new world
     */
    private void hopWorld() {
        System.out.println("Hopping worlds...");
        Rs2Random.waitEx(3200, 800); // this sleep is required to avoid the message: please finish what you're doing before using the world switcher.

        int world = plugin.isUseNextWorld() ? Login.getNextWorld(Rs2Player.isMember()) : Login.getRandomWorld(Rs2Player.isMember());
        sleepUntil(() -> Microbot.hopToWorld(world), 15000);
        System.out.println("Successfully hopped to world: " + world);
    }


    /**
     * Processes the buy action for the specified item.
     * @param itemName The name of the item to buy.
     * @param quantity The quantity of the item to buy.
     * @return true if bought successfully, false otherwise.
     */
    private boolean processBuyAction(String itemName, String quantity) {
        if (Rs2Inventory.isFull()) {
            System.out.println("Avoid buying item - Inventory is full");
            return false;
        }

        boolean boughtItem = Rs2Shop.buyItem(itemName, quantity);

        if (boughtItem){
            Rs2Inventory.waitForInventoryChanges(3000);
        }

        System.out.println(boughtItem ? "Successfully bought " + quantity + " item: " + itemName : "Failed to buy " + quantity + " item ID: " + itemName);
        return boughtItem;
    }


    /**
     * Processes the buy action for the specified item.
     * @param itemID The ID of the item to buy.
     * @param quantity The quantity of the item to buy.
     * @return true if bought successfully, false otherwise.
     */
    private boolean processBuyAction(int itemID, String quantity) {
        if (Rs2Inventory.isFull()) {
            System.out.println("Avoid buying item - Inventory is full");
            return false;
        }

        boolean boughtItem = Rs2Shop.buyItem(itemID, quantity);

        if (boughtItem){
            Rs2Inventory.waitForInventoryChanges(3000);
        }

        System.out.println(boughtItem ? "Successfully bought " + quantity + " item ID: " + itemID : "Failed to buy " + quantity + " item ID: " + itemID);
        return boughtItem;
    }

    /**
     * Processes the sell action for the specified item.
     * @param itemName The name of the item to sell.
     * @param quantity The quantity of the item to sell.
     * @return true if sold successfully, false otherwise.
     */
    private boolean processSellAction(String itemName, String quantity) {
        if (Rs2Inventory.hasItem(itemName)) {
            boolean soldItem = Rs2Inventory.sellItem(itemName, quantity);
            System.out.println(soldItem ? "Successfully sold " + quantity + " " + itemName : "Failed to sell " + quantity + " " + itemName);
            return soldItem;
        }
        System.out.println("Item " + itemName + " not found in inventory.");
        return false;
    }

    /**
     * Processes the sell action for the specified item.
     * @param itemID The name of the item to sell.
     * @param quantity The quantity of the item to sell.
     * @return true if sold successfully, false otherwise.
     */
    private boolean processSellAction(int itemID, String quantity) {
        if (Rs2Inventory.hasItem(itemID)) {
            boolean soldItem = Rs2Inventory.sellItem(itemID, quantity);
            System.out.println(soldItem ? "Successfully sold " + quantity + ", item ID:" + itemID : "Failed to sell " + quantity + ", item ID: " + itemID);
            return soldItem;
        }
        System.out.println("Item ID" + itemID + " not found in inventory.");
        return false;
    }
}
