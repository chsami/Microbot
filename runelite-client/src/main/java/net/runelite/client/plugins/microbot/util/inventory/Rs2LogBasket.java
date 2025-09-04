package net.runelite.client.plugins.microbot.util.inventory;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Constants;
import net.runelite.api.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * General utility for log basket operations
 * Can be used by any plugin that needs log basket functionality
 */
@Slf4j
public class Rs2LogBasket {

    public static final int LOG_BASKET_CAPACITY = 28;
    
    /**
     * check if player has log basket in inventory
     */
    public static boolean hasLogBasket() {
        return Rs2Inventory.hasItem(ItemID.LOG_BASKET) || Rs2Inventory.hasItem(ItemID.OPEN_LOG_BASKET);
    }
    
    /**
     * get current log basket item id (closed or open)
     */
    public static int getLogBasketId() {
        if (Rs2Inventory.hasItem(ItemID.OPEN_LOG_BASKET)) {
            return ItemID.OPEN_LOG_BASKET;
        }
        return ItemID.LOG_BASKET;
    }
    
    /**
     * fill log basket with logs from inventory
     */
    public static boolean fillLogBasket() {
        if (!hasLogBasket()) {
            log.warn("no log basket found in inventory");
            return false;
        }
        
        // interact with log basket to fill it
        if (Rs2Inventory.interact(getLogBasketId(), "Fill")) {
            // wait for logs to transfer from inventory to basket
            Rs2Inventory.waitForInventoryChanges(3000);
            log.info("filled log basket with logs from inventory");
            return true;
        }
        
        log.warn("failed to interact with log basket for filling");
        return false;
    }
    
    /**
     * empty log basket at bank
     */
    public static boolean emptyLogBasketAtBank() {
        if (!hasLogBasket()) return false;
        
        if (!Rs2Bank.isOpen()) {
            log.error("bank must be open to empty log basket");
            return false;
        }
        
        // use Rs2Bank utility to empty log basket
        boolean emptied = Rs2Bank.emptyLogBasket();
        if (emptied) {
            log.info("emptied log basket at bank");
        }
        return emptied;
    }
    
    /**
     * check if we should fill the log basket with specific log type
     * considers basket capacity and current contents
     * @param logId the log item id to check
     * @param minLogsToFill minimum logs in inventory before filling
     */
    public static boolean shouldFillBasket(int logId, int minLogsToFill) {
        if (!hasLogBasket()) return false;
        
        // check if basket is already full
        BasketContents contents = checkBasketContents();
        if (contents.quantity >= LOG_BASKET_CAPACITY) {
            log.debug("basket is already full with {} logs", contents.quantity);
            return false;
        }
        
        int logCount = Rs2Inventory.count(logId);
        
        // fill basket when we have enough logs and few empty slots, or basket has space
        boolean hasEnoughLogs = logCount >= minLogsToFill;
        boolean inventoryNearFull = Rs2Inventory.emptySlotCount() <= 3;
        boolean basketHasSpace = contents.quantity < LOG_BASKET_CAPACITY;
        
        return (hasEnoughLogs || inventoryNearFull) && basketHasSpace;
    }
    
    /**
     * check if we should fill the log basket (generic version)
     * uses default threshold of 15 logs
     */
    public static boolean shouldFillBasket(int logId) {
        return shouldFillBasket(logId, 15);
    }
    
    /**
     * estimate total log capacity with basket
     */
    public static int getEffectiveLogCapacity() {
        if (hasLogBasket()) {
            // basket can hold 28 logs + inventory can hold ~25 (accounting for other items)
            return 53; // conservative estimate
        }
        return 25; // normal inventory minus space for tools/items
    }
    
    /**
     * check if inventory is getting full and we should consider using basket
     * @param logId the log item id to check
     */
    public static boolean isInventoryNearFull(int logId) {
        int logs = Rs2Inventory.count(logId);
        int emptySlots = Rs2Inventory.emptySlotCount();
        
        // consider "near full" when we have 15+ logs and few empty slots
        return logs >= 15 && emptySlots <= 3;
    }
    
    /**
     * get count of specific log type in inventory
     * @param logId the log item id
     */
    public static int getLogCount(int logId) {
        return Rs2Inventory.count(logId);
    }
    
    /**
     * check if we should use log basket for banking workflow
     * @param willBank whether this workflow involves banking
     */
    public static boolean shouldUseLogBasket(boolean willBank) {
        return hasLogBasket() && willBank;
    }
    
    /**
     * check if basket has logs without performing check action
     * uses inventory state and basket capacity estimation
     * @return true if basket likely has logs
     */
    public static boolean hasLogs() {
        if (!hasLogBasket()) return false;
        
        // if we have open basket, it likely has logs
        if (Rs2Inventory.hasItem(ItemID.OPEN_LOG_BASKET)) {
            return true;
        }
        
        // for closed basket, we need to check contents if needed
        BasketContents contents = checkBasketContents();
        return contents.quantity > 0;
    }
    
    /**
     * get status string for overlay/debugging
     * @param logId the log item id to check
     */
    public static String getLogBasketStatus(int logId) {
        if (!hasLogBasket()) return "";
        
        int logs = Rs2Inventory.count(logId);
        boolean isOpen = Rs2Inventory.hasItem(ItemID.OPEN_LOG_BASKET);
        
        return String.format(" | Basket: %s (%d logs)", isOpen ? "Open" : "Available", logs);
    }
    
    /**
     * comprehensive log basket management for banking workflows
     * @param logId the log item id to work with
     * @param logsToWithdraw how many logs to withdraw to inventory
     */
    public static boolean handleLogBasketBanking(int logId, int logsToWithdraw) {
        if (!hasLogBasket() || !Rs2Bank.isOpen()) {
            log.warn("log basket banking requires open bank and log basket in inventory");
            return false;
        }
        
        // empty log basket at bank to ensure we start fresh
        boolean emptied = emptyLogBasketAtBank();
        if (!emptied) {
            log.warn("failed to empty log basket at bank");
        }
        
        // fill inventory with logs, leaving space for log basket
        int actualLogsToWithdraw = Math.min(logsToWithdraw, 27); // keep 1 slot for log basket
        if (Rs2Bank.withdrawX(logId, actualLogsToWithdraw)) {
            sleepUntil(() -> Rs2Inventory.count(logId) >= Math.min(actualLogsToWithdraw, Rs2Bank.count(logId)), 3000);
        }
        
        // try to fill log basket with additional logs if bank has more
        return fillLogBasketFromBank(logId);
    }
    
    /**
     * data class to hold basket contents information
     */
    public static class BasketContents {
        public final int quantity;
        public final String logType;
        public final boolean isEmpty;
        
        public BasketContents(int quantity, String logType) {
            this.quantity = quantity;
            this.logType = logType != null ? logType : "";
            this.isEmpty = quantity == 0;
        }
        
        public static BasketContents empty() {
            return new BasketContents(0, "");
        }
    }
    
    /**
     * check log basket contents using the "check" action
     * parses widget text to determine quantity and log type
     * @return BasketContents with quantity and log type, or empty if unable to determine
     */
    public static BasketContents checkBasketContents() {
        if (!hasLogBasket()) {
            log.warn("no log basket found in inventory");
            return BasketContents.empty();
        }
        
        // interact with log basket to check contents
        if (!Rs2Inventory.interact(getLogBasketId(), "Check")) {
            log.warn("failed to interact with log basket for checking");
            return BasketContents.empty();
        }
        
        // wait for chatbox widget to appear with basket contents
        if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(193, 2), Constants.GAME_TICK_LENGTH*3)) {
            log.warn("chatbox widget did not appear within timeout");
            return BasketContents.empty();
        }
        
        String basketText = null;        
        // navigate the widget hierarchy: 162.54 -> static child 162.566 -> nested 193.0 -> 193.2
        Widget nestedWidgetLogBasket = Rs2Widget.getWidget(193, 2);       
        if (nestedWidgetLogBasket != null) {
            basketText = nestedWidgetLogBasket.getText();                     
        } else {
            log.warn("could not find nested widget 193.0");
        }
        // if direct path didn't work, try searching for the text widget
        if (basketText == null || basketText.isEmpty()) {
            log.warn("direct path failed, searching for basket text widget");
            Widget textWidget = Rs2Widget.getWidget(193, 2);
            if (textWidget != null) {
                basketText = textWidget.getText();
                log.debug("found basket text via search: {}", basketText);
            }
        }
        
        if (basketText == null || basketText.isEmpty()) {
            log.warn("could not find basket contents text in any widget");
            // handle continue dialogue if present
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                sleepUntil(() -> !Rs2Dialogue.hasContinue(), 2000);
            }
            return BasketContents.empty();
        }
        
        // handle continue dialogue
        if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            sleepUntil(() -> !Rs2Dialogue.hasContinue(), 2000);
        }
        
        // handle "remove logs?" dialogue by clicking "no"
        if (Rs2Widget.hasWidget("Would you like to remove")) {
            Rs2Widget.clickWidget("No");
            sleepUntil(() -> !Rs2Widget.hasWidget("Would you like to remove"), 2000);
        }
        
        // parse the basket contents text
        BasketContents contents = parseBasketText(basketText);
        log.info("basket contains {} x {}", contents.quantity, contents.logType);
        return contents;
    }
    
    /**
     * parse basket contents text to extract quantity and log type
     * expected format: "The basket contains:<br>28 x Oak logs" or similar
     * @param text the widget text containing basket contents
     * @return BasketContents with parsed quantity and log type
     */
    private static BasketContents parseBasketText(String text) {
        if (text == null || text.isEmpty()) {
            
            return BasketContents.empty();
        }
        
        // clean up the text - remove html tags and normalize
        String cleanText = Rs2UiHelper.stripColTags(text)
            .replace("<br>", " ")
            .replace("&nbsp;", " ")
            .trim();
        
        // pattern to match "X x LogType logs" or "X x LogType log"
        Pattern pattern = Pattern.compile("(\\d+)\\s*x\\s*([a-zA-Z\\s]+?)\\s*logs?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(cleanText);
        
        if (matcher.find()) {
            try {
                int quantity = Integer.parseInt(matcher.group(1));
                String logType = matcher.group(2).trim();
                return new BasketContents(quantity, logType);
            } catch (NumberFormatException e) {
                log.warn("failed to parse quantity from basket text: {}", e.getMessage());
            }
        }
        
        // check if basket is empty
        if (cleanText.toLowerCase().contains("empty") || cleanText.toLowerCase().contains("nothing")) {
            return BasketContents.empty();
        }
        
        log.warn("unable to parse basket contents from text: {}", cleanText);
        return BasketContents.empty();
    }
    
    /**
     * check if basket is full (contains maximum capacity)
     * @return true if basket is full
     */
    public static boolean isBasketFull() {
        BasketContents contents = checkBasketContents();
        return contents.quantity >= LOG_BASKET_CAPACITY;
    }
    
    /**
     * check if basket is empty
     * @return true if basket is empty
     */
    public static boolean isBasketEmpty() {
        BasketContents contents = checkBasketContents();
        return contents.isEmpty;
    }
    
    /**
     * get current basket contents without performing check action
     * uses cached information if available, otherwise performs check
     * @return BasketContents with current information
     */
    public static BasketContents getCurrentBasketContents() {
        return checkBasketContents();
    }
    
    /**
     * try to fill log basket with logs from bank
     */
    private static boolean fillLogBasketFromBank(int logId) {
        if (!hasLogBasket() || !Rs2Bank.isOpen()) return false;
        
        int bankLogs = Rs2Bank.count(logId);
        if (bankLogs <= 0) return true; // no more logs to get
        
        // withdraw logs to inventory first, then fill basket
        int logsToGet = Math.min(bankLogs, LOG_BASKET_CAPACITY);
        if (Rs2Inventory.emptySlotCount() > 0) {
            int slotsAvailable = Rs2Inventory.emptySlotCount();
            logsToGet = Math.min(logsToGet, slotsAvailable);
            
            if (Rs2Bank.withdrawX(logId, logsToGet)) {
                sleepUntil(() -> Rs2Inventory.count(logId) > 27, 3000);
                
                // now fill the log basket
                if (fillLogBasket()) {
                    log.info("filled log basket with additional logs from bank");
                    return true;
                }
            }
        }
        
        return false;
    }
}