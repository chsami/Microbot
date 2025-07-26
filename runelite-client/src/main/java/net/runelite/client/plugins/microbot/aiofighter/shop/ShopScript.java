package net.runelite.client.plugins.microbot.aiofighter.shop;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterPlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.slayer.Rs2Slayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class ShopScript extends Script {
    public static ShopType shopType = ShopType.GRAND_EXCHANGE;
    public static List<ShopItem> shopItems = new ArrayList<>();

    public boolean run(AIOFighterConfig config) {

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {

                if (!super.run()) {
                    return;
                }
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if (! AIOFighterPlugin.needShopping) {
                    return;
                }

                int totalCost = 0;
                log.info("ShopScript: Calculating total cost for shop items.");
                for (ShopItem item : shopItems) {
                    if (item.getShopType() != ShopType.SLAYER_SHOP) continue;
                    int itemPrice = Microbot.getRs2ItemManager().getPrice(item.getItemId());
                    int itemCost = itemPrice * item.getQuantity();
                    log.info("ShopScript: Item: {}, Quantity: {}, Unit Price: {}, Cost: {}",
                            item.getName(), item.getQuantity(), itemPrice, itemCost);
                    totalCost += itemCost;
                }
                log.info("ShopScript: Total cost calculated: {}", totalCost);

                int currentCoins = Rs2Inventory.itemQuantity(ItemID.COINS_995);
                log.info("ShopScript: Current coins in inventory: {}", currentCoins);
                if (totalCost > currentCoins) {
                    log.info("ShopScript: Insufficient coins. Needed: {}, Available: {}", totalCost, currentCoins);
                    if (Rs2Bank.walkToBankAndUseBank()) {
                        int withdrawAmount = (int) (totalCost * 1.5);
                        log.info("ShopScript: Withdrawing {} coins from bank.", withdrawAmount);
                        Rs2Bank.withdrawX(true, ItemID.COINS_995, withdrawAmount);
                        log.info("ShopScript: Waiting for inventory changes after bank withdrawal.");
                        Rs2Inventory.waitForInventoryChanges(5000);
                    } else {
                        log.info("ShopScript: Failed to walk to bank.");
                    }
                }

                switch (shopType) {
                    case SLAYER_SHOP:
                        log.info("ShopScript: Processing SLAYER_SHOP branch.");
                        if (Rs2Slayer.walkToSlayerMaster(config.slayerMaster())) {
                            log.info("ShopScript: Arrived at slayer master: {}", config.slayerMaster().getName());
                            if (Rs2Shop.openShop(config.slayerMaster().getName())) {
                                log.info("ShopScript: Shop opened successfully for slayer master: {}", config.slayerMaster().getName());
                                Rs2Random.waitEx(1800, 300);
                                // filtered list
                                List<ShopItem> filteredList = shopItems.stream()
                                        .filter(item -> item.getShopType() == ShopType.SLAYER_SHOP)
                                        .collect(Collectors.toList());
                                for (ShopItem item : filteredList) {
                                    log.info("ShopScript: Attempting to buy item: {} with quantity: {}", item.getName(), item.getQuantity());
                                    if (Rs2Shop.hasMinimumStock(item.getName(), item.getQuantity())) {
                                        log.info("ShopScript: Item {} has minimum stock available. Buying now.", item.getName());
                                        Rs2Shop.buyItemOptimally(item.getName(), item.getQuantity());
                                        Rs2Random.waitEx(1800, 300);
                                    } else {
                                        log.info("ShopScript: Insufficient stock for item: {}", item.getName());
                                    }
                                }
                                 AIOFighterPlugin.needShopping = false;
                                shopItems.clear();
                                log.info("ShopScript: SLAYER_SHOP purchases complete. Shop items list cleared.");
                            } else {
                                log.info("ShopScript: Failed to open shop for slayer master: {}", config.slayerMaster().getName());
                            }
                        } else {
                            log.info("ShopScript: Failed to walk to slayer master: {}", config.slayerMaster().getName());
                        }
                        break;
                    case GRAND_EXCHANGE:
                        log.info("ShopScript: Processing GRAND_EXCHANGE branch.");
                        if (Rs2GrandExchange.walkToGrandExchange()) {
                            log.info("ShopScript: Arrived at Grand Exchange.");
                            if (Rs2GrandExchange.openExchange()) {
                                log.info("ShopScript: Grand Exchange interface opened successfully.");
                                Rs2Random.waitEx(1800, 300);
                                // filtered list
                                List<ShopItem> filteredList = shopItems.stream()
                                        .filter(item -> item.getShopType() == ShopType.GRAND_EXCHANGE)
                                        .collect(Collectors.toList());
                                if (!hasFreeSlots()) {
                                    log.info("ShopScript: No free GE slots available.");
                                    if (canFreeUpSlots()) {
                                        log.info("ShopScript: Freeing up GE slots by collecting items to bank.");
                                        Rs2GrandExchange.collectAllToBank();
                                    } else {
                                        log.info("ShopScript: Cannot free up GE slots. Aborting all offers.");
                                        Rs2GrandExchange.abortAllOffers(true);
                                    }
                                }
                                for (ShopItem item : filteredList) {
                                    log.info("ShopScript: Attempting GE purchase for item: {} with quantity: {}", item.getName(), item.getQuantity());
                                    if (!hasFreeSlots()) {
                                        log.info("ShopScript: Checking free GE slots before purchase.");
                                        if (canFreeUpSlots()) {
                                            log.info("ShopScript: Freeing up GE slots before purchase.");
                                            Rs2GrandExchange.collectAllToBank();
                                        } else {
                                            log.info("ShopScript: Unable to free up GE slots.");
                                        }
                                    }
                                    int gePrice = Microbot.getRs2ItemManager().getGEPrice(item.getName());
                                    int maxPrice = (int) (gePrice * 1.1);
                                    log.info("ShopScript: Buying item: {} at max price: {}", item.getName(), maxPrice);
                                    boolean result = Rs2GrandExchange.buyItem(item.getName(), maxPrice, item.getQuantity());
                                    if (result) {
                                        log.info("ShopScript: Successfully placed GE order for {}", item.getName());
                                        Rs2Random.waitEx(1800, 300);
                                    } else {
                                        log.info("ShopScript: Failed to place GE order for {}", item.getName());
                                    }
                                }
                                log.info("ShopScript: Waiting for GE offers to complete.");
                                sleepUntil(Rs2GrandExchange::hasFinishedBuyingOffers,30000);
                                Rs2GrandExchange.collectAllToBank();
                                 AIOFighterPlugin.needShopping = false;
                                shopItems.clear();
                                log.info("ShopScript: GRAND_EXCHANGE purchases complete. Shop items list cleared.");
                            } else {
                                log.info("ShopScript: Failed to open Grand Exchange interface.");
                            }
                        } else {
                            log.info("ShopScript: Failed to walk to Grand Exchange.");
                        }
                        break;
                    default:
                        log.info("ShopScript: Unknown shop type: {}", shopType);
                        break;
                }
            } catch (Exception ex) {
                log.error("ShopScript: Exception occurred - {}", ex.getMessage(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }
    @Override
    public void shutdown() {
        super.shutdown();
         AIOFighterPlugin.needShopping = false;
        log.info("ShopScript: Shutdown initiated. needShopping flag set to false.");
    }

    private boolean canFreeUpSlots() {
        boolean canFree = Rs2GrandExchange.hasSoldOffer() || Rs2GrandExchange.hasBoughtOffer();
        log.info("ShopScript: Checking if GE slots can be freed up: {}", canFree);
        return canFree;
    }

    private boolean hasFreeSlots() {
        int freeSlots = Rs2GrandExchange.getAvailableSlotsCount();
        log.info("ShopScript: Number of free GE slots: {}", freeSlots);
        return freeSlots > 0;
    }
}
