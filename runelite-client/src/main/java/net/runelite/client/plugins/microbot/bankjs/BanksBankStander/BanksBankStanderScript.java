package net.runelite.client.plugins.microbot.bankjs.BanksBankStander;

import lombok.Setter;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.WallObject;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Encryption;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.storm.common.Rs2Storm.getRandomItemWithLimit;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;
import static net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.items;

// heaps of new features added by Storm
public class BanksBankStanderScript extends Script {
    @Inject
    private BanksBankStanderConfig config;
    public static double version = 1.8;

    int MAX_TRIES = 4;
    public static long previousItemChange;

    public static CurrentStatus currentStatus = CurrentStatus.FETCH_SUPPLIES;

    public static int itemsProcessed;


    static Integer thirdItemId;
    static Integer fourthItemId;

    static String firstItemIdentifier;

    static String thirdItemIdentifier;
    static String fourthItemIdentifier;

    private int firstItemQuantity;
    static Integer firstItemId;
    static String secondItemIdentifier;
    public static Integer secondItemId;
    private int secondItemQuantity;
    private int thirdItemQuantity;
    private int fourthItemQuantity;
    static int firstItemSum;
    static int secondItemSum;
    static int thirdItemSum;
    static int fourthItemSum;

    private int sleepMin;
    private int sleepMax;
    private int sleepTarget;

    public static boolean isWaitingForPrompt = false;
    private static boolean sleep;
    private static String menu;
    public static String firstIdentity;
    public static String secondIdentity;
    public static String thirdIdentity;
    public static String fourthIdentity;
    private long timeValue;
    private int randomNum;
    @Setter
    private static boolean confirmedPIN = false;

    public boolean run(BanksBankStanderConfig config) {
        this.config = config; // Initialize the config object before accessing its parameters
        itemsProcessed = 0;
        firstItemSum = 0;
        secondItemSum = 0;
        thirdItemSum = 0;
        fourthItemSum = 0;
        // Initialize other variables
        firstItemIdentifier = config.firstItemIdentifier();
        firstItemQuantity = config.firstItemQuantity();
        secondItemIdentifier = config.secondItemIdentifier();
        thirdItemIdentifier = config.thirdItemIdentifier();
        fourthItemIdentifier = config.fourthItemIdentifier();


        secondItemQuantity = config.secondItemQuantity();
        thirdItemQuantity = config.thirdItemQuantity();
        fourthItemQuantity = config.fourthItemQuantity();

        sleepMin = config.sleepMin();
        sleepMax = config.sleepMax();
        if (config.sleepMax() > config.sleepMin() + 120) {
            sleepMax = config.sleepMax();
            sleepTarget = config.sleepTarget();
        } else {
            sleepMax = config.sleepMax() + Rs2Random.between(120 - (config.sleepMax() - config.sleepMin()), 151);
            sleepTarget = sleepMin + ((sleepMax - sleepMin) / 2);
        }
        // Determine whether the first & second item is the ID or Name.
        firstItemId = TryParseInt(config.firstItemIdentifier());
        secondItemId = TryParseInt(config.secondItemIdentifier());
        thirdItemId = TryParseInt(config.thirdItemIdentifier());
        fourthItemId = TryParseInt(config.fourthItemIdentifier());
        firstIdentity = firstItemId != null ? "identified by ID" : "identified by name";
        secondIdentity = secondItemId != null ? "identified by ID" : "identified by name";
        thirdIdentity = thirdItemId != null ? "identified by ID" : "identified by name";
        fourthIdentity = fourthItemId != null ? "identified by ID" : "identified by name";
        // Print the types of firstItemIdentifier and firstItemId
        System.out.println("Type of firstItemIdentifier: " + firstItemIdentifier.getClass().getSimpleName());
        System.out.println("Type of firstItemId: " + (firstItemId != null ? firstItemId.getClass().getSimpleName() : "null"));
        if (config.secondItemQuantity() > 0) {
            // Print the types of secondItemIdentifier and secondItemId
            System.out.println("Type of secondItemIdentifier: " + secondItemIdentifier.getClass().getSimpleName());
            System.out.println("Type of secondItemId: " + (secondItemId != null ? secondItemId.getClass().getSimpleName() : "null"));
        }
        if (config.thirdItemQuantity() > 0) {
            // Print the types of thirdItemIdentifier and thirdItemId
            System.out.println("Type of thirdItemIdentifier: " + thirdItemIdentifier.getClass().getSimpleName());
            System.out.println("Type of thirdItemId: " + (thirdItemId != null ? thirdItemId.getClass().getSimpleName() : "null"));
        }
        if (config.fourthItemQuantity() > 0) {
            // Print the types of fourthItemIdentifier and fourthItemId
            System.out.println("Type of fourthItemIdentifier: " + fourthItemIdentifier.getClass().getSimpleName());
            System.out.println("Type of fourthItemId: " + (fourthItemId != null ? fourthItemId.getClass().getSimpleName() : "null"));

        }
        //menu = (firstItemId != null ? Microbot.getClientThread().runOnClientThread(() -> Microbot.getClient().getItemDefinition(firstItemId).getName().toLowerCase().contains("grimy")) : firstItemIdentifier.toLowerCase().contains("grimy")) ? "clean" : "use";
        menu = config.menu();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;
            try {
                //start
                combineItems();

            } catch (Exception ex) {
                ex.printStackTrace();
                Microbot.log(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean hasItems() {
        // Check if the player has the required quantity of both items using the configuration
        if (firstItemId != null && secondItemId != null) {
            // User has inputted the item id for both items.
            System.out.println("Checking for items by ID...");
            return Rs2Inventory.hasItem(firstItemId) &&
                    Rs2Inventory.hasItem(secondItemId);
        } else if (firstItemId != null) {
            // User has inputted the item id for the first item and item identifier for the second item.
            System.out.println("Checking for first item by ID and second item by identifier...");
            return Rs2Inventory.hasItem(firstItemId) &&
                    Rs2Inventory.hasItem(secondItemIdentifier);
        } else if (secondItemId != null) {
            // User has inputted the item id for the second item and item identifier for the first item.
            System.out.println("Checking for second item by ID and first item by identifier...");
            return Rs2Inventory.hasItem(firstItemIdentifier) &&
                    Rs2Inventory.hasItem(secondItemId);
        } else {
            // User has inputted the item identifier for both items.
            System.out.println("Checking for items by identifier...");
            return !firstItemIdentifier.isEmpty() &&
                    Rs2Inventory.hasItem(firstItemIdentifier) &&
                    (secondItemIdentifier.isEmpty() || Rs2Inventory.hasItem(secondItemIdentifier));
        }
    }


    private String fetchItems() {
        if (config.pause()) {
            while (this.isRunning() && config.pause()) {
                if (!config.pause() || !this.isRunning()) { break; }
                sleep(100, 1000);
            }
        }
        if (currentStatus != CurrentStatus.FETCH_SUPPLIES) { currentStatus = CurrentStatus.FETCH_SUPPLIES; }
        sleep(calculateSleepDuration());
        if (!hasItems()) {
            if (!isBankOpen()) {
                openBank();
            }
            sleep = sleepUntilTrue(() -> isBankOpen(), Rs2Random.between(67, 97), 18000);
            sleep(calculateSleepDuration());
            if (config.depositAll() && Rs2Inventory.getEmptySlots() < 28) {
                Rs2Bank.depositAll();
                sleep(100,300);
                return "";
            }
            if(firstItemId != null && firstItemQuantity < Rs2Inventory.count(firstItemId)) {
                Rs2Bank.depositAll(firstItemId);
                sleep(100,300);
            } else if (firstItemId == null && firstItemQuantity < Rs2Inventory.count(firstItemIdentifier)) {
                Rs2Bank.depositAll(firstItemIdentifier);
                sleep(100,300);
            }
            if (secondItemId != null && secondItemQuantity < Rs2Inventory.count(secondItemId)) {
                Rs2Bank.depositAll(secondItemId);
                sleep(100,300);
            } else if (secondItemId == null && secondItemQuantity < Rs2Inventory.count(secondItemIdentifier)) {
                Rs2Bank.depositAll(secondItemIdentifier);
                sleep(100,300);
            }
            if(thirdItemId != null && thirdItemQuantity < Rs2Inventory.count(thirdItemId)) {
                Rs2Bank.depositAll(thirdItemId);
                sleep(100,300);
            } else if (thirdItemId == null && thirdItemQuantity < Rs2Inventory.count(thirdItemIdentifier)) {
                Rs2Bank.depositAll(thirdItemIdentifier);
                sleep(100,300);
            }
            if(fourthItemId != null && fourthItemQuantity < Rs2Inventory.count(fourthItemId)) {
                Rs2Bank.depositAll(fourthItemId);
                sleep(100,300);
            } else if (fourthItemId == null && fourthItemQuantity < Rs2Inventory.count(fourthItemIdentifier)) {
                Rs2Bank.depositAll(fourthItemIdentifier);
                sleep(100,300);
            }
            if (firstItemId != null && secondItemId != null && (thirdItemId != null || thirdItemQuantity == 0) && (fourthItemId !=null || fourthItemQuantity == 0)) {
                Rs2Bank.depositAllExcept(firstItemId, secondItemId, thirdItemId, fourthItemId);
            } else if (firstItemId == null && secondItemId == null && (thirdItemId == null || thirdItemQuantity == 0) && (fourthItemId == null || fourthItemQuantity == 0)){
                Rs2Bank.depositAllExcept(firstItemIdentifier, secondItemIdentifier, thirdItemIdentifier, fourthItemIdentifier);
            } else if (!config.depositAll()) {
                Rs2Bank.depositAll();
            }
            sleep = sleepUntilTrue(() -> !Rs2Inventory.isFull(), 100, 6000);
            sleep(100, 300);
            String missingItem = checkItemSums();
            if (!missingItem.isEmpty()) {
                return missingItem;
            }
            if (firstItemId != null) {
                if (Rs2Bank.hasItem(firstItemId) && Rs2Inventory.count(firstItemId) < firstItemQuantity) {
                    int missingQuantity = Rs2Inventory.count(firstItemId) < firstItemQuantity
                            ? firstItemQuantity - Rs2Inventory.count(firstItemId)
                            : 0;
                    timeValue = System.currentTimeMillis();
                    Rs2Bank.withdrawX(true, firstItemId, missingQuantity);
                    if (secondItemQuantity == 0 && thirdItemQuantity == 0 && fourthItemQuantity == 0) sleepUntilTrue(() -> Rs2Inventory.waitForInventoryChanges(60) || Rs2Inventory.hasItemAmount(firstItemId, firstItemQuantity),60, 600);//this one only matters if it's only the one item, like herb cleaning.
                    randomNum = calculateSleepDuration();
                    if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 48)); }
                }
            } else {
                if (Rs2Bank.hasItem(firstItemIdentifier) && Rs2Inventory.count(firstItemIdentifier) < firstItemQuantity) {
                    int missingQuantity = Rs2Inventory.count(firstItemIdentifier) < firstItemQuantity
                            ? firstItemQuantity - Rs2Inventory.count(firstItemIdentifier)
                            : 0;
                    timeValue = System.currentTimeMillis();
                    Rs2Bank.withdrawX(true, firstItemIdentifier, missingQuantity);
                    if (secondItemQuantity == 0 && thirdItemQuantity == 0 && fourthItemQuantity == 0) sleepUntilTrue(() -> Rs2Inventory.waitForInventoryChanges(60) || Rs2Inventory.hasItemAmount(firstItemIdentifier, firstItemQuantity),60, 600);//this one only matters if it's only the one item, like herb cleaning.
                    randomNum = calculateSleepDuration();
                    if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 48)); }
                }
            }
            if (config.secondItemQuantity() > 0) {
                if (secondItemId != null) {
                    if (Rs2Bank.hasItem(secondItemId) && Rs2Inventory.count(secondItemId) < secondItemQuantity) {
                        if (!config.withdrawAll()) {
                            int missingQuantity = Rs2Inventory.count(secondItemId) < secondItemQuantity
                                    ? secondItemQuantity - Rs2Inventory.count(secondItemId)
                                    : 0;
                            timeValue = System.currentTimeMillis();
                            Rs2Bank.withdrawX(true, secondItemId, missingQuantity);
                            if (thirdItemQuantity == 0 && fourthItemQuantity == 0) sleepUntilTrue(() -> Rs2Inventory.waitForInventoryChanges(60) || Rs2Inventory.hasItemAmount(secondItemId, secondItemQuantity),60, 600);
                            randomNum = calculateSleepDuration();
                            if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 48)); }
                        } else {
                            timeValue = System.currentTimeMillis();
                            Rs2Bank.withdrawAll(secondItemId);
                            if (thirdItemQuantity == 0 && fourthItemQuantity == 0) sleepUntilTrue(() -> Rs2Inventory.waitForInventoryChanges(60) || Rs2Inventory.hasItemAmount(secondItemId, secondItemQuantity),60, 600);
                            randomNum = calculateSleepDuration();
                            if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 48)); }
                        }
                    }
                } else {
                    if (Rs2Bank.hasItem(secondItemIdentifier) && config.secondItemQuantity() > 0) {
                        if (!config.withdrawAll()) {
                            int missingQuantity = Rs2Inventory.count(secondItemIdentifier) < secondItemQuantity
                                    ? secondItemQuantity - Rs2Inventory.count(secondItemIdentifier)
                                    : 0;
                            timeValue = System.currentTimeMillis();
                            Rs2Bank.withdrawX(true, secondItemIdentifier, missingQuantity);
                            if (thirdItemQuantity == 0 && fourthItemQuantity == 0) sleepUntilTrue(() -> Rs2Inventory.waitForInventoryChanges(60) || Rs2Inventory.hasItemAmount(secondItemIdentifier, secondItemQuantity),60, 600);
                            randomNum = calculateSleepDuration();
                            if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 48)); }
                        } else {
                            timeValue = System.currentTimeMillis();
                            Rs2Bank.withdrawAll(true, secondItemIdentifier);
                            if (thirdItemQuantity == 0 && fourthItemQuantity == 0) sleepUntilTrue(() -> Rs2Inventory.waitForInventoryChanges(60) || Rs2Inventory.hasItemAmount(secondItemIdentifier, secondItemQuantity),60, 600);
                            randomNum = calculateSleepDuration();
                            if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 48)); }
                        }
                    }
                }
            }
            if (config.thirdItemQuantity() > 0) {
                if (thirdItemId != null) {
                    if (Rs2Bank.hasItem(thirdItemId) && Rs2Inventory.count(thirdItemId) < config.thirdItemQuantity()) {
                        int missingQuantity = Rs2Inventory.count(thirdItemId) < thirdItemQuantity
                                ? thirdItemQuantity - Rs2Inventory.count(thirdItemId)
                                : 0;
                        timeValue = System.currentTimeMillis();
                        Rs2Bank.withdrawX(true, thirdItemId, missingQuantity);
                        if (fourthItemQuantity == 0) sleepUntilTrue(() -> Rs2Inventory.waitForInventoryChanges(60) || Rs2Inventory.hasItemAmount(thirdItemId, thirdItemQuantity),60, 600);
                        randomNum = calculateSleepDuration();
                        if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 48)); }
                    }
                } else {
                    if (Rs2Bank.hasItem(thirdItemIdentifier) && Rs2Inventory.count(thirdItemIdentifier) < config.thirdItemQuantity()) {
                        int missingQuantity = Rs2Inventory.count(thirdItemIdentifier) < thirdItemQuantity
                                ? thirdItemQuantity - Rs2Inventory.count(thirdItemIdentifier)
                                : 0;
                        timeValue = System.currentTimeMillis();
                        Rs2Bank.withdrawX(true, thirdItemIdentifier, missingQuantity);
                        if (fourthItemQuantity == 0) sleepUntilTrue(() -> Rs2Inventory.waitForInventoryChanges(60) || Rs2Inventory.hasItemAmount(thirdItemIdentifier, thirdItemQuantity),60, 600);
                        randomNum = calculateSleepDuration();
                        if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 48)); }
                    }
                }
            }
            if (config.fourthItemQuantity() > 0) {
                if (fourthItemId != null) {
                    if (Rs2Bank.hasItem(fourthItemId)) {
                        if (!config.withdrawAll()) {
                            int missingQuantity = Rs2Inventory.count(fourthItemId) < fourthItemQuantity
                                    ? fourthItemQuantity - Rs2Inventory.count(fourthItemId)
                                    : 0;
                            timeValue = System.currentTimeMillis();
                            Rs2Bank.withdrawX(true, fourthItemId, missingQuantity);
                            sleepUntilTrue(() -> Rs2Inventory.waitForInventoryChanges(60) || Rs2Inventory.hasItemAmount(fourthItemId, fourthItemQuantity),60, 600);
                            randomNum = calculateSleepDuration();
                            if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 48)); }
                        } else {
                            timeValue = System.currentTimeMillis();
                            Rs2Bank.withdrawAll(fourthItemId);
                            sleepUntilTrue(() -> Rs2Inventory.waitForInventoryChanges(60) || Rs2Inventory.hasItemAmount(fourthItemId, fourthItemQuantity),60, 600);
                            randomNum = calculateSleepDuration();
                            if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 48)); }
                        }
                    }
                } else {
                    // User has inputted the item identifier for the fourth item.
                    if (Rs2Bank.hasItem(fourthItemIdentifier)) {
                        if (!config.withdrawAll()) {
                            int missingQuantity = Rs2Inventory.count(fourthItemIdentifier) < fourthItemQuantity
                                    ? fourthItemQuantity - Rs2Inventory.count(fourthItemIdentifier)
                                    : 0;
                            timeValue = System.currentTimeMillis();
                            Rs2Bank.withdrawX(true, fourthItemIdentifier, missingQuantity);
                            sleepUntilTrue(() -> Rs2Inventory.waitForInventoryChanges(60) || Rs2Inventory.hasItemAmount(fourthItemIdentifier, fourthItemQuantity),60, 600);
                            randomNum = calculateSleepDuration();
                            if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 48)); }
                        } else {
                            timeValue = System.currentTimeMillis();
                            Rs2Bank.withdrawAll(true, fourthItemIdentifier);
                            sleepUntilTrue(() -> Rs2Inventory.waitForInventoryChanges(60) || Rs2Inventory.hasItemAmount(fourthItemIdentifier, fourthItemQuantity),60, 600);
                            randomNum = calculateSleepDuration();
                            if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 48)); }
                        }
                    }
                }
            }
            //if (!hasItems()) { Rs2Inventory.waitForInventoryChanges(600); }
            if (hasItems()) {
                previousItemChange = (System.currentTimeMillis() - 2500);
                if (firstItemSum == 0) {
                    firstItemSum = firstItemId != null ? (Rs2Bank.bankItems.stream().filter(item -> item.id == firstItemId).mapToInt(item -> item.quantity).sum() + Rs2Inventory.count(firstItemId)) : (Rs2Bank.count(firstItemIdentifier) + Rs2Inventory.count(firstItemIdentifier));
                }
                if (config.secondItemQuantity() > 0 && secondItemSum == 0) {
                    secondItemSum = secondItemId != null ? (Rs2Bank.bankItems.stream().filter(item -> item.id == secondItemId).mapToInt(item -> item.quantity).sum() + Rs2Inventory.count(secondItemId)) : (Rs2Bank.count(secondItemIdentifier) + Rs2Inventory.count(secondItemIdentifier));
                }
                if (config.thirdItemQuantity() > 0 && thirdItemSum == 0) {
                    thirdItemSum = thirdItemId != null ? (Rs2Bank.bankItems.stream().filter(item -> item.id == thirdItemId).mapToInt(item -> item.quantity).sum() + Rs2Inventory.count(thirdItemId)) : (Rs2Bank.count(thirdItemIdentifier) + Rs2Inventory.count(thirdItemIdentifier));
                }
                if (config.fourthItemQuantity() > 0 && fourthItemSum == 0) {
                    fourthItemSum = fourthItemId != null ? (Rs2Bank.bankItems.stream().filter(item -> item.id == fourthItemId).mapToInt(item -> item.quantity).sum() + Rs2Inventory.count(fourthItemId)) : (Rs2Bank.count(fourthItemIdentifier) + Rs2Inventory.count(fourthItemIdentifier));
                }
                long bankCloseTime = System.currentTimeMillis();
                sleep = false;
                while (this.isRunning() && !sleep
                        && (System.currentTimeMillis() - bankCloseTime < 1800)) {
                    //switched our close bank method because searching for a widget by the contained text appears to be significantly slower.
                    closeBank();
                    timeValue = System.currentTimeMillis();
                    sleep = sleepUntilTrue(() -> !isBankOpen(), Rs2Random.between(60, 97), 600);
                }
                randomNum = calculateSleepDuration()-10;
                if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 28)); }
                //to make our code easier to read, and remove a redundant check.
                if (!sleep) {
                    sleep(calculateSleepDuration());
                    if (this.isRunning()) { Rs2Player.logout(); }
                    sleep(calculateSleepDuration());
                }
                currentStatus = CurrentStatus.COMBINE_ITEMS;
                return "";
            }
        }
        return "";
    }
    private boolean closeBank(){
        return Rs2Widget.clickChildWidget(786434, 11);
    }
    public boolean openBank() {
        Microbot.status = "Opening bank";
        try {
            if (Microbot.getClient().isWidgetSelected())
                Microbot.getMouse().click();
            if (isBankOpen()) return true;
            boolean action;
            WallObject grandExchangeBooth = Rs2GameObject.getWallObjects()
                    .stream()
                    .filter(x -> x.getId() == 10060 || x.getId() == 30389)
                    .findFirst()
                    .orElse(null);
            GameObject bank = Rs2GameObject.findBank();
            GameObject chest = Rs2GameObject.findChest();

            // Determine if bank should be skipped in favor of chest
            boolean useChest = bank != null && chest != null && bank.getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation()) > chest.getWorldLocation().distanceTo2D(Rs2Player.getWorldLocation());

            if (!useChest && bank != null && (grandExchangeBooth == null ||
                    bank.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= grandExchangeBooth.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()))) {
                action = Rs2GameObject.interact(bank, "bank");
            } else if (grandExchangeBooth != null) {
                action = Rs2GameObject.interact(grandExchangeBooth, "bank");
            } else if (chest != null) {
                action = Rs2GameObject.interact(chest, "use");
            } else {
                NPC npc = Rs2Npc.getNpc("banker");
                if (npc == null) return false;
                action = Rs2Npc.interact(npc, "bank");
            }

            if (action) {
                Global.sleepUntil(() -> isBankOpen() || Rs2Widget.hasWidget("Please enter your PIN"), 2500);
                Global.sleep(calculateSleepDuration()+55);
            }
            return action;
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return false;
    }
    private static boolean isClosed() {
        return Microbot.getClientThread().runOnClientThread(() -> Microbot.getClient().getWidget(786434)) == null;
    }
    private static boolean isBankOpen(){
        if (!confirmedPIN && Rs2Widget.hasWidget("Please enter your PIN")) {
            try {
                if (Login.activeProfile.getBankPin().isEmpty()) {
                    Microbot.showMessage("Your bankpin is empty. Please fill this field in your runelite profile.");
                    return false;
                }
                Rs2Bank.handleBankPin(Encryption.decrypt(Login.activeProfile.getBankPin()));
            } catch (Exception e) {
                System.out.println("Something went wrong handling bankpin");
            }
            return false;
        }
        confirmedPIN = true;
        return !isClosed();
    }
    private boolean combineItems() {
        if (!hasItems()) {
            String missingItem = fetchItems();
            if (!missingItem.isEmpty()) {
                Microbot.showMessage("Insufficient " + missingItem);
                while (this.isRunning()) {
                    if (hasItems()) {
                        break;
                    }
                    sleep(300, 3000);
                }
            }
            return false;
        }
        if (isBankOpen()) {
            closeBank();
            sleep = sleepUntilTrue(() -> !isBankOpen(), Rs2Random.between(60, 97), 5000);
            sleep(calculateSleepDuration());
            return false;
        }
        if (config.waitForAnimation()) {
            if (Rs2Player.isAnimating() || (System.currentTimeMillis() - previousItemChange) < 2400) { return false; }
        }
        if (currentStatus != CurrentStatus.COMBINE_ITEMS) { currentStatus = CurrentStatus.COMBINE_ITEMS; }
        if (config.pause()) {
            while (this.isRunning() && config.pause()) {
                if (!config.pause()){ break; }
                sleep(100,1000);
            }
        }
        if (firstItemId != null && secondItemId != null) {
            Rs2Inventory.interact(config.randomSelection() ? getRandomItemWithLimit(firstItemId, MAX_TRIES) : items().stream().filter(x -> x.id == firstItemId).findFirst().orElse(null), menu); // Use first Rs2Item (random or not)

            if (config.secondItemQuantity() > 0) {
                Rs2Inventory.interact(config.randomSelection() ? getRandomItemWithLimit(secondItemId, MAX_TRIES) : items().stream().filter(x -> x.id == secondItemId).findFirst().orElse(null), menu);
            }

        } else if (firstItemId != null) {
            Rs2Inventory.interact(getRandomItemWithLimit(firstItemId, MAX_TRIES), menu); // Use first Rs2Item (random or not)

            if (config.secondItemQuantity() > 0) {
                Rs2Inventory.interact(config.randomSelection() ? getRandomItemWithLimit(secondItemIdentifier, MAX_TRIES) : items().stream().filter(x -> x.name.equalsIgnoreCase(secondItemIdentifier.toLowerCase())).findFirst().orElse(null), menu);
            }
        } else if (secondItemId != null) {
            Rs2Inventory.interact(getRandomItemWithLimit(firstItemIdentifier, MAX_TRIES), menu); // Use first Rs2Item (random or not)

            if (config.secondItemQuantity() > 0) {
                Rs2Inventory.interact(config.randomSelection() ? getRandomItemWithLimit(secondItemId, MAX_TRIES) : items().stream().filter(x -> x.id == secondItemId).findFirst().orElse(null), menu);
            }
        } else {
            if (menu.equalsIgnoreCase("clean")) {
                Rs2Inventory.cleanHerbs(config.interactOrder());
                return true;
            }
            Rs2Inventory.interact(getRandomItemWithLimit(firstItemIdentifier, MAX_TRIES), menu); // Use first Rs2Item (random or not)

            if (config.secondItemQuantity() > 0) {
                Rs2Inventory.interact(config.randomSelection() ? getRandomItemWithLimit(secondItemIdentifier, MAX_TRIES) : items().stream().filter(x -> x.name.equalsIgnoreCase(secondItemIdentifier.toLowerCase())).findFirst().orElse(null), menu);
            }
        }

        if (config.needPromptEntry()) {
            sleep(calculateSleepDuration());
            isWaitingForPrompt = true;
            sleep = sleepUntilTrue(() -> !isWaitingForPrompt, Rs2Random.between(7, 31), Rs2Random.between(800, 1200));
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            previousItemChange = System.currentTimeMillis();
            if (secondItemId != null) {
                sleep = sleepUntilTrue(() -> !Rs2Inventory.hasItem(secondItemId), 30, 40000);
            } else {
                sleep = sleepUntilTrue(() -> !Rs2Inventory.hasItem(secondItemIdentifier), 30, 40000);
            }
        }
        sleep(calculateSleepDuration());
        return true;
    }


    private int calculateSleepDuration() {
        // Create a Random object
        Random random = new Random();

        // Calculate the mean (average) of sleepMin and sleepMax, adjusted by sleepTarget
        double mean = (sleepMin + sleepMax + sleepTarget) / 3.0;

        // Calculate the standard deviation with added noise
        double noiseFactor = 0.2; // Adjust the noise factor as needed (0.0 to 1.0)
        double stdDeviation = Math.abs(sleepTarget - mean) / 3.0 * (1 + noiseFactor * (random.nextDouble() - 0.5) * 2);

        // Generate a random number following a normal distribution
        int sleepDuration;
        do {
            // Generate a random number using nextGaussian method, scaled by standard deviation
            sleepDuration = (int) Math.round(mean + random.nextGaussian() * stdDeviation);
        } while (sleepDuration < sleepMin || sleepDuration > sleepMax); // Ensure the duration is within the specified range

        return sleepDuration;
    }
    public String checkItemSums(){
        if(!isBankOpen()){
            openBank();
            sleep = sleepUntilTrue(BanksBankStanderScript::isBankOpen, Rs2Random.between(67, 97), 18000);
            sleep(200, 600);
        }
        //System.out.println("Attempting to check first item");
        if (firstItemId != null && ((Rs2Bank.bankItems.stream().filter(item -> item.id == firstItemId).mapToInt(item -> item.quantity).sum() + Rs2Inventory.count(firstItemId))) < config.firstItemQuantity()) {
            return firstItemId.toString();
        } else if (firstItemId == null && (Rs2Bank.count(firstItemIdentifier) + Rs2Inventory.count(firstItemIdentifier)) < config.firstItemQuantity()) {
            return firstItemIdentifier;
        }
        //System.out.println("Attempting to check second item");
        if (config.secondItemQuantity() > 0 && !config.secondItemIdentifier().isEmpty()) {
            if (secondItemId != null && ((Rs2Bank.bankItems.stream().filter(item -> item.id == secondItemId).mapToInt(item -> item.quantity).sum() + Rs2Inventory.count(secondItemId))) < config.secondItemQuantity()) {
                return secondItemId.toString();
            } else if (secondItemId == null && (Rs2Bank.count(secondItemIdentifier) + Rs2Inventory.count(secondItemIdentifier)) < config.secondItemQuantity()) {
                return secondItemIdentifier;
            }
        }
        if (config.thirdItemQuantity() > 0 && !config.thirdItemIdentifier().isEmpty()) {
            //System.out.println("Attempting to check third item");
            if (thirdItemId != null && ((Rs2Bank.bankItems.stream().filter(item -> item.id == thirdItemId).mapToInt(item -> item.quantity).sum() + Rs2Inventory.count(thirdItemId))) < config.thirdItemQuantity()) {
                return thirdItemId.toString();
            } else if (thirdItemId == null && (Rs2Bank.count(thirdItemIdentifier) + Rs2Inventory.count(thirdItemIdentifier)) < config.thirdItemQuantity()) {
                return thirdItemIdentifier;
            }
        }
        if (config.fourthItemQuantity() > 0 && !config.fourthItemIdentifier().isEmpty()) {
            //System.out.println("Attempting to check fourth item");
            if (fourthItemId != null && ((Rs2Bank.bankItems.stream().filter(item -> item.id == fourthItemId).mapToInt(item -> item.quantity).sum() + Rs2Inventory.count(fourthItemId))) < config.fourthItemQuantity()) {
                return fourthItemId.toString();
            } else if (fourthItemId == null && (Rs2Bank.count(fourthItemIdentifier) + Rs2Inventory.count(fourthItemIdentifier)) < config.fourthItemQuantity()) {
                return fourthItemIdentifier;
            }
        }
        return "";
    }

    // method to parse string to integer, returns null if parsing fails
    public static Integer TryParseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            System.out.println("Could not Parse Int from Item, using Name Instead");
            return null;
        }
    }
}
