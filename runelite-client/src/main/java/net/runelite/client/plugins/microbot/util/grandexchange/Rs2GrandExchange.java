package net.runelite.client.plugins.microbot.util.grandexchange;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.NumberExtractor;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.apache.commons.lang3.tuple.Pair;

import java.awt.*;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.runelite.client.plugins.microbot.util.Global.*;

public class Rs2GrandExchange {

    public static final int GRAND_EXCHANGE_OFFER_CONTAINER_QTY_10 = 30474265;
    public static final int GRAND_EXCHANGE_OFFER_CONTAINER_QTY_100 = 30474265;
    public static final int GRAND_EXCHANGE_OFFER_CONTAINER_QTY_1000 = 30474265;
    public static final int GRAND_EXCHANGE_OFFER_CONTAINER_QTY_X = 30474265;
    public static final int GRAND_EXCHANGE_OFFER_CONTAINER_QTY_1 = 30474265;
    public static final int COLLECT_BUTTON = 30474246;
    private static final String GE_TRACKER_API_URL = "https://www.ge-tracker.com/api/items/";

    /**
     * close the grand exchange interface
     */
    public static void closeExchange() {
        Microbot.status = "Closing Grand Exchange";
        if (!isOpen()) return;
        Rs2Widget.clickChildWidget(30474242, 11);
        sleepUntilOnClientThread(() -> Rs2Widget.getWidget(30474242) == null);
    }

    /**
     * Back button. Goes back from buy/sell offer screen to all slots overview.
     */
    public static void backToOverview() {
        Microbot.status = "Back to overview";
        if (!isOpen() && !isOfferScreenOpen()) return;
        Rs2Widget.clickWidget(30474244);
        sleepUntilOnClientThread(() -> !isOfferScreenOpen());
    }

    /**
     * check if the grand exchange screen is open
     *
     * @return
     */
    public static boolean isOpen() {
        return !Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Widget.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER) == null
                || Rs2Widget.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER).isHidden()).orElse(false);
    }

    /**
     * check if the ge offerscreen is open
     *
     * @return
     */
    public static boolean isOfferScreenOpen() {
        Microbot.status = "Checking if Offer is open";
        return Rs2Widget.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER) != null;
    }

    /**
     * Opens the grand exchange
     *
     * @return
     */
    public static boolean openExchange() {
        Microbot.status = "Opening Grand Exchange";
        try {
            if (Rs2Inventory.isItemSelected())
                Microbot.getMouse().click();
            if (isOpen()) return true;
            Rs2NpcModel npc = Rs2Npc.getNpc("Grand Exchange Clerk");
            if (npc == null) return false;
            Rs2Npc.interact(npc, "exchange");
            sleepUntil(Rs2GrandExchange::isOpen, 5000);
            return false;
        } catch (Exception ex) {
            Microbot.logStackTrace("Rs2GrandExchange", ex);
        }
        return false;
    }

    /**
     * @param itemName
     * @param price
     * @param quantity
     * @return true if item has been bought succesfully
     */
    public static boolean buyItem(String itemName, int price, int quantity) {
        return buyItem(itemName, itemName, price, quantity);
    }

    /**
     * @param itemName   name of the item
     * @param searchTerm search term
     * @param price      price of the item to buy
     * @param quantity   quantity of item to buy
     * @return true if item has been bought succesfully
     */
    public static boolean buyItem(String itemName, String searchTerm, int price, int quantity) {
        try {
            if (useGrandExchange()) return false;

            Pair<GrandExchangeSlots, Integer> slot = getAvailableSlot();
            if (slot.getLeft() == null) {
                if (hasBoughtOffer()) {
                    collectToBank();
                }
                return false;
            }
            Widget buyOffer = getOfferBuyButton(slot.getLeft());
            if (buyOffer == null) return false;

            Rs2Widget.clickWidgetFast(buyOffer);
            sleepUntil(Rs2GrandExchange::isOfferTextVisible, 5000);
            sleepUntil(() -> Rs2Widget.hasWidget("What would you like to buy?"));
            Rs2Keyboard.typeString(searchTerm);
            sleepUntil(() -> !Rs2Widget.hasWidget("Start typing the name"), 5000); //GE Search Results
            sleep(1200);
            Pair<Widget, Integer> itemResult = getSearchResultWidget(itemName);
            if (itemResult != null) {
                Rs2Widget.clickWidgetFast(itemResult.getLeft(), itemResult.getRight(), 1);
                sleepUntil(() -> getPricePerItemButton_X() != null);
            }
            Widget pricePerItemButtonX = getPricePerItemButton_X();
            if (pricePerItemButtonX != null) {
                System.out.println("tried to click widget");
                sleep(2000);
                Microbot.getMouse().click(pricePerItemButtonX.getBounds());
                Microbot.getMouse().click(pricePerItemButtonX.getBounds());
                sleepUntil(() -> Rs2Widget.getWidget(162, 41) != null, 5000); //GE Enter Price
                sleep(1000);
                Rs2Keyboard.typeString(Integer.toString(price));
                Rs2Keyboard.enter();
                sleep(2000);
                setQuantity(quantity);
                confirm();
                return true;
            } else {
                System.out.println("unable to find widget setprice.");
            }

            return false;
        } catch (Exception ex) {
            Microbot.logStackTrace("Rs2GrandExchange", ex);
        }
        return false;
    }

    private static void confirm() {
        Microbot.getMouse().click(getConfirm().getBounds());
        sleepUntil(() -> Rs2Widget.hasWidget("Your offer is much higher"), 2000);
        if (Rs2Widget.hasWidget("Your offer is much higher")) {
            Rs2Widget.clickWidget("Yes");
        }
    }

    private static void setQuantity(int quantity) {
        if (quantity > 1) {
            Widget quantityButtonX = getQuantityButton_X();
            Microbot.getMouse().click(quantityButtonX.getBounds());
            sleepUntil(() -> Rs2Widget.getWidget(162, 41) != null); //GE Enter Price/Quantity
            sleep(600, 1000);
            Rs2Keyboard.typeString(Integer.toString(quantity));
            sleep(500, 750);
            Rs2Keyboard.enter();
            sleep(1000);
        }
    }

    public static boolean buyItemAbove5Percent(String itemName, int quantity) {
        return buyItemAbove5Percent(itemName, quantity, 1);
    }

    private static boolean searchItemAndSetQuantity(String itemName, int quantity) {
        if (!isOpen()) {
            openExchange();
        }
        Pair<GrandExchangeSlots, Integer> slot = getAvailableSlot();
        Widget buyOffer = getOfferBuyButton(slot.getLeft());

        if (buyOffer == null) return false;

        Microbot.getMouse().click(buyOffer.getBounds());
        sleepUntil(Rs2GrandExchange::isOfferTextVisible);
        sleepUntil(() -> Rs2Widget.hasWidget("What would you like to buy?"));
        if (Rs2Widget.hasWidget("What would you like to buy?"))
            Rs2Keyboard.typeString(itemName);
        sleepUntil(() -> Rs2Widget.hasWidget(itemName) || Rs2Widget.hasWidget("No matches found.")); //GE Search Results
        sleep(1200, 1600);
        if (Rs2Widget.hasWidget("No matches found.")) {
            System.out.println("Unable to find item in GE.");
            return false;
        }
        Pair<Widget, Integer> itemResult = getSearchResultWidget(itemName);
        if (itemResult != null) {
            Rs2Widget.clickWidgetFast(itemResult.getLeft(), itemResult.getRight(), 1);
            sleepUntil(() -> !Rs2Widget.hasWidget("Choose an item..."));
            sleep(600, 1600);
        }
        setQuantity(quantity);

        return true;
    }

    /**
     * Buys item from the grand exchange and increases the price by custom percent
     *
     * @param itemName
     * @param quantity
     * @param percent the percentage to increase
     * @return
     */
    public static boolean buyItemAboveXPercent(String itemName, int quantity, int percent) {
        try {
            if (!searchItemAndSetQuantity(itemName, quantity))
                return false;

            Widget pricePerItemButtonXPercent = getPricePerItemButton_PlusXPercent();
            if (pricePerItemButtonXPercent != null) {
                int basePrice = getItemPrice();
                int currentPercent = NumberExtractor.extractNumber(pricePerItemButtonXPercent.getText());

                // Update Price per item custom percentage if it doesn't match
                if (currentPercent != percent) {
                    // If current percentage is empty (indicated by +X%)
                    if (currentPercent == -1) {
                        Microbot.getMouse().click(pricePerItemButtonXPercent.getBounds());
                    } else {
                        Microbot.doInvoke(new NewMenuEntry("Customise", 15, pricePerItemButtonXPercent.getId(), MenuAction.CC_OP.getId(), 2, -1, ""), new Rectangle(pricePerItemButtonXPercent.getBounds()));
                    }
                    sleep(300, 1200);
                    sleepUntil(() -> Rs2Widget.hasWidget("Set a percentage to decrease/increase"), 2000);
                    Rs2Keyboard.typeString(Integer.toString(percent));
                    Rs2Keyboard.enter();
                    sleepUntil(() -> currentPercent == NumberExtractor.extractNumber(pricePerItemButtonXPercent.getText()), 2000);
                    sleep(300, 1200);
                }

                Microbot.getMouse().click(pricePerItemButtonXPercent.getBounds());
                sleepUntil(() -> hasOfferPriceChanged(basePrice), 2000);

                confirm();
                return true;
            }

        } catch (Exception ex) {
            Microbot.logStackTrace("Rs2GrandExchange", ex);
        }
        return false;
    }

    /**
     * Buys item from the grand exchange 5% above the average price
     *
     * @param itemName
     * @param quantity
     * @param timesToIncreasePrice the amount to click +5% price increase
     * @return
     */
    public static boolean buyItemAbove5Percent(String itemName, int quantity, int timesToIncreasePrice) {
        try {
            if (!searchItemAndSetQuantity(itemName, quantity))
                return false;

            if (buyItemAbove5Percent(timesToIncreasePrice)) {
                return true;
            }

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return false;
    }

    private static boolean buyItemAbove5Percent(int timesToIncreasePrice) {
        Widget pricePerItemButton5Percent = getPricePerItemButton_Plus5Percent();
        if (pricePerItemButton5Percent != null) {
            int basePrice = getItemPrice();
            // Call click() as many times as the value of count
            IntStream.range(0, timesToIncreasePrice).forEach(i -> {
                Microbot.getMouse().click(pricePerItemButton5Percent.getBounds());
                sleepUntil(() -> hasOfferPriceChanged(basePrice), 1600);
            });

            confirm();
            return true;
        } else {
            System.out.println("unable to find widget setprice.");
            return false;
        }
    }

    private static boolean useGrandExchange() {
        if (!isOpen()) {
            boolean hasExchangeOpen = openExchange();
            if (!hasExchangeOpen) {
                boolean isAtGe = walkToGrandExchange();
                return !isAtGe;
            }
        }
        return false;
    }

    /**
     * Sell item to the grand exchange
     *
     * @param itemName name of the item to sell
     * @param quantity quantity of the item to sell
     * @param price    price of the item to sell
     * @return
     */
    public static boolean sellItem(String itemName, int quantity, int price) {
        try {
            if (!Rs2Inventory.hasItem(itemName)) return false;

            if (useGrandExchange()) return false;

            Pair<GrandExchangeSlots, Integer> slot = getAvailableSlot();
            Widget sellOffer = getOfferSellButton(slot.getLeft());

            if (sellOffer == null) return false;

            Microbot.getMouse().click(sellOffer.getBounds());
            sleepUntil(Rs2GrandExchange::isOfferTextVisible, 5000);
            Rs2Inventory.interact(itemName, "Offer");
            sleepUntil(() -> Rs2Widget.hasWidget("actively traded price"));
            sleep(300, 600);
            Widget pricePerItemButtonX = getPricePerItemButton_X();
            if (pricePerItemButtonX != null) {
                Microbot.getMouse().click(pricePerItemButtonX.getBounds());
                sleepUntil(() -> Rs2Widget.getWidget(162, 41) != null, 5000); //GE Enter Price
                sleep(1000);
                Rs2Keyboard.typeString(Integer.toString(price));
                Rs2Keyboard.enter();
                sleep(300, 500);
                setQuantity(quantity);
                Microbot.getMouse().click(getConfirm().getBounds());
                sleepUntil(() -> !isOfferTextVisible());
                return true;
            } else {
                System.out.println("unable to find widget setprice.");
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return false;
    }

    public static boolean sellItemUnder5Percent(String itemName) {
        return sellItemUnder5Percent(itemName, false);
    }

    public static boolean sellItemUnder5Percent(String itemName, boolean exact) {
        try {
            if (!Rs2Inventory.hasItem(itemName)) return false;

            if (!isOpen()) {
                openExchange();
            }
            Pair<GrandExchangeSlots, Integer> slot = getAvailableSlot();
            Widget sellOffer = getOfferSellButton(slot.getLeft());

            if (sellOffer == null) return false;

            Microbot.getMouse().click(sellOffer.getBounds());
            sleepUntil(Rs2GrandExchange::isOfferTextVisible, 5000);
            Rs2Inventory.interact(itemName, "Offer", exact);
            sleepUntil(() -> Rs2Widget.hasWidget("actively traded price"));
            sleep(300, 600);
            Widget pricePerItemButton5Percent = getPricePerItemButton_Minus_5Percent();
            if (pricePerItemButton5Percent != null) {
                Microbot.getMouse().click(pricePerItemButton5Percent.getBounds());
                Microbot.getMouse().click(getConfirm().getBounds());
                sleepUntil(() -> !isOfferTextVisible());
                return true;
            } else {
                System.out.println("unable to find widget setprice.");
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return false;
    }

    /**
     * Collect all the grand exchange slots to the bank or inventory
     *
     * @param collectToBank
     * @return
     */
    public static boolean collect(boolean collectToBank) {
        if (isAllSlotsEmpty()) {
            return true;
        }
        if (Rs2Inventory.isFull()) {
            if (Rs2Bank.useBank()) {
                Rs2Bank.depositAll();
            }
        }
        if (!isOpen()) {
            openExchange();
        }
        sleepUntil(Rs2GrandExchange::isOpen);
        Widget[] collectButton = Rs2Widget.getWidget(465, 6).getDynamicChildren();
        if (!collectButton[1].isSelfHidden()) {
            Rs2Widget.clickWidgetFast(
                    COLLECT_BUTTON, collectToBank ? 2 : 1);
            sleepUntil(() -> collectButton[1].isSelfHidden());
        }
        return collectButton[1].isSelfHidden();
    }

    public static boolean collectToInventory() {
        return collect(false);
    }

    /**
     * Collect all the grand exchange items to your bank
     *
     * @return
     */
    public static boolean collectToBank() {
        return collect(true);
    }

    /**
     * sells all the tradeable loot items from a specific npc name
     *
     * @param npcName
     * @return true if there is no more loot to sell
     */
    public static boolean sellLoot(String npcName, List<String> itemsToNotSell) {

        boolean soldAllItems = Rs2Bank.withdrawLootItems(npcName, itemsToNotSell);

        if (soldAllItems) {
            boolean isSuccess = sellInventory();

            return isSuccess;
        }


        return false;
    }

    /**
     * Sells all the tradeable items in your inventory
     *
     * @return
     */
    public static boolean sellInventory() {
        for (Rs2ItemModel item : Rs2Inventory.items()) {

            if (!item.isTradeable()) continue;

            if (Rs2GrandExchange.getAvailableSlot().getKey() == null && Rs2GrandExchange.hasSoldOffer()) {
                Rs2GrandExchange.collectToBank();
                sleep(600);
            }

            Rs2GrandExchange.sellItemUnder5Percent(item.name);
        }
        return Rs2Inventory.isEmpty();
    }

    /**
     * Aborts the offer
     *
     * @param name          name of the item to abort offer on
     * @param collectToBank collect the item to the bank
     * @return true if the offer has been aborted
     */
    public static boolean abortOffer(String name, boolean collectToBank) {
        if (useGrandExchange()) return false;
        try {
            for (GrandExchangeSlots slot : GrandExchangeSlots.values()) {
                Widget parent = getSlot(slot);
                if (parent == null) continue;
                if (isSlotAvailable(slot)) continue; // skip if slot is empty
                Widget child = parent.getChild(19);
                if (child == null) continue;
                if (child.getText().equalsIgnoreCase(name)) {
                    Microbot.doInvoke(new NewMenuEntry("Abort offer", 2, parent.getId(), MenuAction.CC_OP.getId(), 2, -1, ""), new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
                    sleep(1000);
                    collect(collectToBank);
                    return true;
                }
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return false;
    }

    /**
     * Aborts all offers
     *
     * @param collectToBank collect the items to the bank
     * @return true if all the offers have been aborted
     */
    public static boolean abortAllOffers(boolean collectToBank) {
        if (useGrandExchange()) return false;
        try {
            for (GrandExchangeSlots slot : GrandExchangeSlots.values()) {
                Widget parent = getSlot(slot);
                if (parent == null) continue;
                if (isSlotAvailable(slot)) continue; // skip if slot is empty
                Widget child = parent.getChild(19);
                if (child == null) continue;
                Microbot.doInvoke(new NewMenuEntry("Abort offer", 2, parent.getId(), MenuAction.CC_OP.getId(), 2, -1, ""), new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
            }
            sleep(1000);
            collect(collectToBank);
            return isAllSlotsEmpty();
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            return false;
        }
    }

    public static Pair<Widget, Integer> getSearchResultWidget(String search) {
        Widget parent = Microbot.getClient().getWidget(ComponentID.CHATBOX_GE_SEARCH_RESULTS);

        if (parent == null || parent.getChildren() == null) return null;

        Widget child = Arrays.stream(parent.getChildren()).filter(x -> x.getText().equalsIgnoreCase(search)).findFirst().orElse(null);

        if (child != null) {
            List<Widget> children = Arrays.stream(parent.getChildren()).collect(Collectors.toList());
            int index = children.indexOf(child);
            int originalWidgetIndex = index - 1;
            return Pair.of(children.get(originalWidgetIndex), originalWidgetIndex);
        }
        return null;
    }

    public static Pair<Widget, Integer> getSearchResultWidget(int itemId) {
        Widget parent = Microbot.getClient().getWidget(WidgetInfo.CHATBOX_GE_SEARCH_RESULTS);

        if (parent == null || parent.getChildren() == null) return null;

        Widget child = Arrays.stream(parent.getChildren()).filter(x -> x.getItemId() == itemId).findFirst().orElse(null);

        if (child != null) {
            List<Widget> children = Arrays.stream(parent.getChildren()).collect(Collectors.toList());
            int index = children.indexOf(child);
            int originalWidgetIndex = index - 2;
            return Pair.of(children.get(originalWidgetIndex), originalWidgetIndex);
        }

        return null;
    }

    private static Widget getOfferContainer() {
        return Microbot.getClient().getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
    }

    public static Widget getQuantityButton_Minus() {

        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(1)).orElse(null);
    }

    public static Widget getQuantityButton_Plus() {

        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(2)).orElse(null);
    }

    public static Widget getQuantityButton_1() {

        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(3)).orElse(null);
    }

    public static Widget getQuantityButton_10() {
        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(4)).orElse(null);
    }

    public static Widget getQuantityButton_100() {
        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(5)).orElse(null);
    }

    public static Widget getQuantityButton_1000() {
        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(6)).orElse(null);
    }

    public static Widget getQuantityButton_X() {
        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(7)).orElse(null);
    }

    public static Widget getPricePerItemButton_Minus() {
        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(8)).orElse(null);
    }

    public static Widget getPricePerItemButton_Plus() {
        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(9)).orElse(null);
    }

    public static Widget getPricePerItemButton_Minus_5Percent() {
        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(10)).orElse(null);
    }

    public static Widget getPricePerItemButton_GuidePrice() {
        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(11)).orElse(null);
    }

    public static Widget getPricePerItemButton_X() {
        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(12)).orElse(null);
    }

    public static Widget getPricePerItemButton_Plus5Percent() {
        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(13)).orElse(null);
    }

    public static Widget getPricePerItemButton_PlusXPercent() {
        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(15)).orElse(null);
    }

    public static Widget getChooseItem() {
        var parent = getOfferContainer();

        return Optional.ofNullable(parent).map(p -> p.getChild(20)).orElse(null);
    }

    public static Widget getConfirm() {
        var parent = getOfferContainer();

        return Rs2Widget.findWidget("Confirm", Arrays.stream(parent.getDynamicChildren()).collect(Collectors.toList()), true);
    }

    public static boolean isOfferTextVisible() {
        return Rs2Widget.isWidgetVisible(ComponentID.GRAND_EXCHANGE_OFFER_DESCRIPTION);
    }

    private static boolean hasOfferPriceChanged(int basePrice) {
        return basePrice != getItemPrice();
    }

    public static Widget getItemPriceWidget() {
        return Rs2Widget.getWidget(465, 27);
    }

    public static int getItemPrice() {
        return Integer.parseInt(Rs2Widget.getWidget(465, 27).getText().replace(",", ""));
    }

    public static Widget getSlot(GrandExchangeSlots slot) {
        switch (slot) {
            case ONE:
                return Rs2Widget.getWidget(465, 7);
            case TWO:
                return Rs2Widget.getWidget(465, 8);
            case THREE:
                return Rs2Widget.getWidget(465, 9);
            case FOUR:
                return Rs2Widget.getWidget(465, 10);
            case FIVE:
                return Rs2Widget.getWidget(465, 11);
            case SIX:
                return Rs2Widget.getWidget(465, 12);
            case SEVEN:
                return Rs2Widget.getWidget(465, 13);
            case EIGHT:
                return Rs2Widget.getWidget(465, 14);
            default:
                return null;
        }
    }

    public static boolean isSlotAvailable(GrandExchangeSlots slot) {
        Widget parent = getSlot(slot);
        return Optional.ofNullable(parent).map(p -> p.getChild(2).isSelfHidden()).orElse(false);
    }

    public static Widget getOfferBuyButton(GrandExchangeSlots slot) {
        Widget parent = getSlot(slot);
        return Optional.ofNullable(parent).map(p -> p.getChild(0)).orElse(null);
    }

    public static Widget getOfferSellButton(GrandExchangeSlots slot) {
        Widget parent = getSlot(slot);
        return Optional.ofNullable(parent).map(p -> p.getChild(1)).orElse(null);
    }

    public static Pair<GrandExchangeSlots, Integer> getAvailableSlot() {
        int maxSlots = getMaxSlots();
        int slotsAvailable = 0;
        GrandExchangeSlots availableSlot = null;
        for (int i = 0; i < maxSlots; i++) {
            GrandExchangeSlots slot = GrandExchangeSlots.values()[i];
            if (Rs2GrandExchange.isSlotAvailable(slot)) {
                if (availableSlot == null) {
                    availableSlot = slot;
                }
                slotsAvailable++;
            }
        }
        return Pair.of(availableSlot, slotsAvailable);
    }

    public static boolean isAllSlotsEmpty() {
        return getAvailableSlot().getRight() == Arrays.stream(GrandExchangeSlots.values()).count();
    }

    public static boolean hasBoughtOffer() {
        return Arrays.stream(Microbot.getClient().getGrandExchangeOffers()).anyMatch(x -> x.getState() == GrandExchangeOfferState.BOUGHT);
    }
    // check if all buy offers are bought, no state can be buying and at least one offer must be bought
    public static boolean hasFinishedBuyingOffers() {
        GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
        boolean hasBought = Arrays.stream(offers)
                .anyMatch(offer -> offer.getState() == GrandExchangeOfferState.BOUGHT);
        boolean isBuying = Arrays.stream(offers)
                .anyMatch(offer -> offer.getState() == GrandExchangeOfferState.BUYING);
        return hasBought && !isBuying;
    }

    public static boolean hasSoldOffer() {
        return Arrays.stream(Microbot.getClient().getGrandExchangeOffers()).anyMatch(x -> x.getState() == GrandExchangeOfferState.SOLD);
    }
    public static boolean hasFinishedSellingOffers() {
        GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
        boolean hasSold = Arrays.stream(offers)
                .anyMatch(offer -> offer.getState() == GrandExchangeOfferState.SOLD);
        boolean isSelling = Arrays.stream(offers)
                .anyMatch(offer -> offer.getState() == GrandExchangeOfferState.SELLING);
        return hasSold && !isSelling;
    }


    private static int getMaxSlots() {
        return Rs2Player.isMember() ? 8 : 3;
    }

    public static boolean walkToGrandExchange() {
        return Rs2Walker.walkTo(BankLocation.GRAND_EXCHANGE.getWorldPoint());
    }


    public static int getOfferPrice(int itemId) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GE_TRACKER_API_URL + itemId))
                .build();

        try {
            String jsonResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .join();

            JsonParser parser = new JsonParser();
            JsonObject jsonElement = parser.parse(new StringReader(jsonResponse)).getAsJsonObject();
            JsonObject data = jsonElement.getAsJsonObject("data");

            return data.get("buying").getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static int getSellPrice(int itemId) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GE_TRACKER_API_URL + itemId))
                .build();

        try {
            String jsonResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .join();

            JsonParser parser = new JsonParser();
            JsonObject jsonElement = parser.parse(new StringReader(jsonResponse)).getAsJsonObject();
            JsonObject data = jsonElement.getAsJsonObject("data");

            return data.get("selling").getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static int getPrice(int itemId) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GE_TRACKER_API_URL + itemId))
                .build();

        try {
            String jsonResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .join();

            JsonParser parser = new JsonParser();
            JsonObject jsonElement = parser.parse(new StringReader(jsonResponse)).getAsJsonObject();
            JsonObject data = jsonElement.getAsJsonObject("data");

            return data.get("overall").getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static int getBuyingVolume(int itemId) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GE_TRACKER_API_URL + itemId))
                .build();

        try {
            String jsonResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .join();

            JsonParser parser = new JsonParser();
            JsonObject jsonElement = parser.parse(new StringReader(jsonResponse)).getAsJsonObject();
            JsonObject data = jsonElement.getAsJsonObject("data");

            return data.get("buyingQuantity").getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static int getSellingVolume(int itemId) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GE_TRACKER_API_URL + itemId))
                .build();

        try {
            String jsonResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .join();

            JsonParser parser = new JsonParser();
            JsonObject jsonElement = parser.parse(new StringReader(jsonResponse)).getAsJsonObject();
            JsonObject data = jsonElement.getAsJsonObject("data");

            return data.get("sellingQuantity").getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}
