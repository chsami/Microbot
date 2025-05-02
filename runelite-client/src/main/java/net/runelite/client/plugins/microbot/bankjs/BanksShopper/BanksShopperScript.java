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

import java.util.concurrent.TimeUnit;

enum ShopperState {
    SHOPPING,
    BANKING,
}

public class BanksShopperScript extends Script {

    public static String version = "1.3.0";

    private final BanksShopperPlugin plugin;
    private ShopperState state = ShopperState.SHOPPING;

    public BanksShopperScript(final BanksShopperPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run(BanksShopperConfig config) {
        Microbot.pauseAllScripts = false;
        Microbot.enableAutoRunOn = false;
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Microbot.pauseAllScripts) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }

                if (hasStateChanged()) {
                    state = updateState();
                }

                if (state == null) {
                    Microbot.showMessage("Unable to determine state!");
                    shutdown();
                    return;
                }

                switch (state) {
                    case SHOPPING:
                        boolean missingAllRequiredItems = plugin.getItemNames().stream().noneMatch(Rs2Inventory::hasItem);

                        if (missingAllRequiredItems && plugin.getSelectedAction() == Actions.SELL) {
                            Microbot.status = "[Shutting down] - Reason: Not enough supplies.";
                            Microbot.showMessage(Microbot.status);
                            shutdown();
                            return;
                        }

                        Rs2Shop.openShop(plugin.getNpcName(), plugin.isUseExactNaming());
                        Rs2Random.waitEx(1800, 300); // this wait is required ensure that Rs2Shop.shopItems has been updated before we proceed.

                        boolean allOutOfStock = true;
                        boolean successfullAction = false;

                        if (Rs2Shop.isOpen()) {
                            for (String itemName : plugin.getItemNames()) {
                                if (!isRunning() || Microbot.pauseAllScripts) break;

                                switch (plugin.getSelectedAction()) {
                                    case BUY:
                                        if (!Rs2Shop.hasMinimumStock(itemName, plugin.getMinStock())) continue;
                                        successfullAction = processBuyAction(itemName, plugin.getSelectedQuantity().toString());
                                        break;
                                    case SELL:
                                        if (Rs2Shop.isFull()) continue;
                                        if (Rs2Shop.hasMinimumStock(itemName, plugin.getMinStock())) continue;
                                        successfullAction = processSellAction(itemName, plugin.getSelectedQuantity().toString());
                                        break;
                                    default:
                                        System.out.println("Invalid action specified in config.");
                                }

                                if (successfullAction) {
                                    allOutOfStock = false;
                                    Rs2Random.waitEx(900, 300);
                                }
                            }

                            // If no successful actions occurred, trigger a world hop
                            if (allOutOfStock) {
                                hopWorld();
                            }
                        }
                        break;
                    case BANKING:
                        if (!Rs2Bank.bankItemsAndWalkBackToOriginalPosition(plugin.getItemNames(), initialPlayerLocation))
                            return;
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

        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }


    private boolean hasStateChanged() {
        if (state == ShopperState.SHOPPING && plugin.getSelectedAction() == Actions.BUY && Rs2Inventory.isFull())
            return true;
        return state == ShopperState.BANKING && plugin.getSelectedAction() == Actions.BUY && Rs2Player.distanceTo(initialPlayerLocation) < 6;
    }

    private ShopperState updateState() {
        if (state == ShopperState.SHOPPING && plugin.getSelectedAction() == Actions.BUY && Rs2Inventory.isFull())
            return ShopperState.BANKING;
        if (state == ShopperState.BANKING && plugin.getSelectedAction() == Actions.BUY && (Rs2Player.distanceTo(initialPlayerLocation) < 6))
            return ShopperState.SHOPPING;
        return null;
    }

    /**
     * Hops to a new world
     */
    private void hopWorld() {
        // Stock level dropped below minimum, pause or stop execution
        System.out.println("Stock level dropped below minimum threshold.");
        Rs2Shop.closeShop();
        Rs2Random.waitEx(3200, 800); // this sleep is required to avoid the message: please finish what you're doing before using the world switcher.
        // This is where we need to hop worlds.
        int world = plugin.isUseNextWorld() ? Login.getNextWorld(Rs2Player.isMember()) : Login.getRandomWorld(Rs2Player.isMember());
        boolean isHopped = Microbot.hopToWorld(world);
        if (!isHopped) return;
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
    }


    private boolean processBuyAction(String itemName, String quantity) {
        if (Rs2Inventory.isFull()) {
            System.out.println("Avoid buying item - Inventory is full");
            return false;
        }
        if (Rs2Shop.hasStock(itemName)) {
            boolean boughtItem = Rs2Shop.buyItem(itemName, quantity);
            System.out.println(boughtItem ? "Successfully bought " + quantity + " " + itemName : "Failed to buy " + quantity + " " + itemName);
            return boughtItem;
        }
        System.out.println(itemName + " is not in stock");
        return false;
    }

    private boolean processSellAction(String itemName, String quantity) {
        if (Rs2Inventory.hasItem(itemName, quantity)) {
            boolean soldItem = Rs2Inventory.sellItem(itemName, quantity);
            System.out.println(soldItem ? "Successfully sold " + quantity + " " + itemName : "Failed to sell " + quantity + " " + itemName);
            return soldItem;
        }
        System.out.println("Item " + itemName + " not found in inventory.");
        return false;
    }
}
