package net.runelite.client.plugins.microbot.bankjs.BanksBankStander;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.http.api.item.ItemPrice;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;
import static net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.calculateInteractOrder;

// heaps of new features added by Storm
public class BanksBankStanderScript extends Script {
    @Inject
    private BanksBankStanderConfig config;
    public static double version = 2.1;

    public static long previousItemChange;

    public static CurrentStatus currentStatus = CurrentStatus.FETCH_SUPPLIES;

    public static int itemsProcessed;

    static Integer thirdItemId;
    static Integer fourthItemId;


    static Integer firstItemId;
    public static Integer secondItemId;
    private int sleepMin;
    private int sleepMax;
    private int sleepTarget;

    public static boolean isWaitingForPrompt = false;
    private long timeValue;
    private int randomNum;
    List<Rs2ItemModel> inventorySlots;

    public boolean run(BanksBankStanderConfig config) {
        this.config = config; // Initialize the config object before accessing its parameters
        itemsProcessed = 0;
        inventorySlots = null;

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

        inventorySlots = calculateInteractOrder(new ArrayList<>(Rs2Inventory.items().collect(Collectors.toList())), config.interactOrder());

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

    public boolean hasItems() {
        // If none of the items is required, just return false.
        if (config.firstItemQuantity() <= 0
                && config.secondItemQuantity() <= 0
                && config.thirdItemQuantity() <= 0
                && config.fourthItemQuantity() <= 0) {
            Microbot.log("Something may have gone wrong.");
            return false;
        }

        boolean firstOK = true;
        boolean secondOK = true;
        boolean thirdOK = true;
        boolean fourthOK = true;

        // Check first item if needed
        if (config.firstItemQuantity() > 0) {
            System.out.println("Checking first item.");
            firstOK = Rs2Inventory.hasItem(firstItemId);
        }

        // Check second item if needed
        if (config.secondItemQuantity() > 0) {
            System.out.println("Checking second item.");
            secondOK = Rs2Inventory.hasItem(secondItemId);
        }

        // Check third item if needed
        if (config.thirdItemQuantity() > 0) {
            System.out.println("Checking third item.");
            thirdOK =  Rs2Inventory.hasItem(thirdItemId);
        }

        // Check fourth item if needed
        if (config.fourthItemQuantity() > 0) {
            System.out.println("Checking fourth item.");
            fourthOK = Rs2Inventory.hasItem(fourthItemId);
        }

        // Only return true if all required items were found
        return firstOK && secondOK && thirdOK && fourthOK;
    }


    private String fetchItems() {
        if (config.pause()) {
            while (this.isRunning() && config.pause()) {
                if (!config.pause() || !this.isRunning()) { break; }
                sleep(100, 1000);
            }
        }
        if (currentStatus != CurrentStatus.FETCH_SUPPLIES) { currentStatus = CurrentStatus.FETCH_SUPPLIES; }
        sleep(calculateSleepDuration(1));
        if (!hasItems()) {
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen);
            }

            if (config.amuletOfChemistry()){
                checkForAmulet();
            }

            depositUnwantedItems(firstItemId, config.firstItemQuantity());
            depositUnwantedItems(secondItemId, config.secondItemQuantity());
            depositUnwantedItems(thirdItemId, config.thirdItemQuantity());
            depositUnwantedItems(fourthItemId, config.fourthItemQuantity());



            // Checking that we have enough items in the bank
            String missingItem = checkItemSums();
            if (!missingItem.isEmpty()) {
                return missingItem;
            }
            getXItem(config.firstItemIdentifier(), config.firstItemQuantity());
            getXItem(config.secondItemIdentifier(), config.secondItemQuantity());
            getXItem(config.thirdItemIdentifier(), config.thirdItemQuantity());
            getXItem(config.fourthItemIdentifier(), config.fourthItemQuantity());

            // Checking that we have our items, and tallying a summary for the overlay.
            if (hasItems()) {
                Rs2Bank.closeBank();
                sleepUntil(() -> !Rs2Bank.isOpen());
                currentStatus = CurrentStatus.COMBINE_ITEMS;
                return "";
            }
        }
        return "";
    }
    private boolean combineItems() {
        if (!hasItems()) {
            String missingItem = fetchItems();
            if (!missingItem.isBlank()) {
                Microbot.log("Insufficient " + missingItem);
                sleep(2500, 5000);
                return false;
            }
        }
        // this is to prevent unintended behaviour when the script is started with the bank open.
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen());
            sleep(calculateSleepDuration(1));
            return false;
        }
        // We loop through executing this method "combineItems()", so we want to force return to do nothing while we wait for processing.
        if (config.waitForAnimation()) {
            if (Rs2Player.isAnimating() || (System.currentTimeMillis() - previousItemChange) < 3000) { return false; }// temp change from 2400 to 3000 as chiseling diamond takes longer time
        }

        if (currentStatus != CurrentStatus.COMBINE_ITEMS) { currentStatus = CurrentStatus.COMBINE_ITEMS; }

        // This just allows us to pause the script so that we don't lose our overlay.
        if (config.pause()) {
            while (this.isRunning() && config.pause()) {
                if (!config.pause()){ break; }
                sleep(100,1000);
            }
        }

        // using our items from the config string and the selected interaction order.
        timeValue = System.currentTimeMillis();
        interactOrder(firstItemId);
        randomNum = calculateSleepDuration(0.5);
        if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 28)); }
        if (config.secondItemQuantity() > 0) {
            timeValue = System.currentTimeMillis();
            interactOrder(secondItemId);
            randomNum = calculateSleepDuration(0.5);
            if (System.currentTimeMillis()-timeValue<randomNum) { sleep((int) (randomNum-(System.currentTimeMillis()-timeValue))); } else { sleep(Rs2Random.between(14, 28)); }
        }

        // When the config option is enabled, we interact with the popup when processing items.
        if (config.needPromptEntry()) {
            sleep(calculateSleepDuration(1));
            isWaitingForPrompt = true;
            sleepUntil(() -> !isWaitingForPrompt, Rs2Random.between(800, 1200));
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            previousItemChange = System.currentTimeMillis();
            sleep(100); // Short delay to ensure prompt processing
            isWaitingForPrompt = false; // Ensure prompt flag is reset
            if (secondItemId != null) {
                if(config.amuletOfChemistry()){
                    sleepUntil(() -> !Rs2Inventory.hasItem(secondItemId) || (!Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY) && !Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED)), 40000);
                    sleep(calculateSleepDuration(1));
                    checkForAmulet();
//                    if(Rs2Bank.isOpen()) {
//                        Rs2Bank.closeBank();
//                    }
                }else{
                    sleepUntil(() -> !Rs2Inventory.hasItem(secondItemId), 40000);
                }
            } else {
                sleepUntil(() -> !Rs2Inventory.hasItem(config.secondItemIdentifier()), 40000);
            }
            sleep(calculateSleepDuration(1));
        }
        return true;
    }

    public void interactOrder(Integer itemId) {
        if (itemId == null) return;
        if (secondItemId == null && thirdItemId == null && fourthItemId == null && Rs2Inventory.hasItemAmount(itemId, config.firstItemQuantity())) {
            //Process inventory quickly (cleaning herbs)
            for (Rs2ItemModel itemToInteract : inventorySlots) {
                if (itemToInteract != null) {
                    Rs2Inventory.interact(itemToInteract, config.menu());
                }
            }
            sleepUntilTick(1);
        } else {
            Rs2Inventory.interact(itemId, config.menu());
        }
    }

    private int calculateSleepDuration(double multiplier) {
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
        if ((int) Math.round(sleepDuration * multiplier) < 60) sleepDuration += ((60-sleepDuration)+Rs2Random.between(11,44));
        return sleepDuration;
    }
    public String checkItemSums(){
        if(!Rs2Bank.isOpen()){
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 18000);
            sleep(200, 600);
        }

        if (firstItemId != null && ((Rs2Bank.bankItems().stream().filter(item -> item.getId() == firstItemId).mapToInt(item -> item.getQuantity()).sum() + Rs2Inventory.count(firstItemId))) < config.firstItemQuantity()) {
            return firstItemId.toString();
        } else if (firstItemId == null && (Rs2Bank.count(config.firstItemIdentifier()) + Rs2Inventory.count(config.firstItemIdentifier())) < config.firstItemQuantity()) {
            return config.firstItemIdentifier();
        }

        if (config.secondItemQuantity() > 0 && !config.secondItemIdentifier().isEmpty()) {
            if (secondItemId != null && ((Rs2Bank.bankItems().stream().filter(item -> item.getId() == secondItemId).mapToInt(item -> item.getQuantity()).sum() + Rs2Inventory.count(secondItemId))) < config.secondItemQuantity()) {
                return secondItemId.toString();
            } else if (secondItemId == null && (Rs2Bank.count(config.secondItemIdentifier()) + Rs2Inventory.count(config.secondItemIdentifier())) < config.secondItemQuantity()) {
                return config.secondItemIdentifier();
            }
        }
        if (config.thirdItemQuantity() > 0 && !config.thirdItemIdentifier().isEmpty()) {

            if (thirdItemId != null && ((Rs2Bank.bankItems().stream().filter(item -> item.getId() == thirdItemId).mapToInt(item -> item.getQuantity()).sum() + Rs2Inventory.count(thirdItemId))) < config.thirdItemQuantity()) {
                return thirdItemId.toString();
            } else if (thirdItemId == null && (Rs2Bank.count(config.thirdItemIdentifier()) + Rs2Inventory.count(config.thirdItemIdentifier())) < config.thirdItemQuantity()) {
                return config.thirdItemIdentifier();
            }
        }
        if (config.fourthItemQuantity() > 0 && !config.fourthItemIdentifier().isEmpty()) {

            if (fourthItemId != null && ((Rs2Bank.bankItems().stream().filter(item -> item.getId() == fourthItemId).mapToInt(item -> item.getQuantity()).sum() + Rs2Inventory.count(fourthItemId))) < config.fourthItemQuantity()) {
                return fourthItemId.toString();
            } else if (fourthItemId == null && (Rs2Bank.count(config.fourthItemIdentifier()) + Rs2Inventory.count(config.fourthItemIdentifier())) < config.fourthItemQuantity()) {
                return config.fourthItemIdentifier();
            }
        }
        return "";
    }
    private void getXItem(String item, int amount) {
        if (amount > 0) {
            Integer id = TryParseInt(item);

            // calculates the quantity we need to withdraw in case of any bugs, so we don't get stuck in a loop from any bugs.
            int missingQuantity = (id != null) ? ((Rs2Inventory.count(id) < amount)
                    ? amount - Rs2Inventory.count(id)
                    : 0)
                    : ((Rs2Inventory.count(item) < amount)
                    ? amount - Rs2Inventory.count(item)
                    : 0);
            // just for efficiency, there's no point running everything else if we already have this item.
            if (missingQuantity > 0) {
                // watching our time immediately before attempting to withdraw an item, so we can keep our sleep timer within an expected range when not our last item.
                timeValue = System.currentTimeMillis();
                if (id != null) {
                    Rs2Bank.withdrawX(id, missingQuantity);
                } else {
                    Rs2Bank.withdrawX(item, missingQuantity);
                }
                // code here is checking that we've withdrawn our last item from the bank before we wait for it to be in our inventory before we attempt to close the bank.
                int lastItem = (config.fourthItemQuantity() > 0) ? 4 : (config.thirdItemQuantity() > 0) ? 3 : (config.secondItemQuantity() > 0) ? 2 : 1;
                if (lastItem == 4 && Objects.equals(item, config.fourthItemIdentifier()) ||
                        lastItem == 3 && Objects.equals(item, config.thirdItemIdentifier()) ||
                        lastItem == 2 && Objects.equals(item, config.secondItemIdentifier()) ||
                        lastItem == 1) {
                    if (id != null) {
                        sleepUntilTrue(() -> Rs2Inventory.hasItemAmount(id, amount), 40, 1800);
                    } else {
                        sleepUntilTrue(() -> Rs2Inventory.hasItemAmount(item, amount), 40, 1800);
                    }
                }

                // setting and executing our calculated action delay.
                randomNum = calculateSleepDuration(1);
                if (System.currentTimeMillis() - timeValue < randomNum) {
                    sleep((int) (randomNum - (System.currentTimeMillis() - timeValue)));
                } else {
                    sleep(Rs2Random.between(14, 48));
                }
            }
        }
    }

    private void depositUnwantedItems(Integer itemId, int quantityMax){
        if (itemId == null) return;
        if (config.depositAll() && Rs2Inventory.getEmptySlots() < 28) {
            timeValue = System.currentTimeMillis();
            Rs2Bank.depositAll();
            sleepUntilTrue(() -> Rs2Inventory.getEmptySlots() == 28,40, 1800);
            randomNum = calculateSleepDuration(1);
            if (System.currentTimeMillis() - timeValue < randomNum) {
                sleep((int) (randomNum - (System.currentTimeMillis() - timeValue)));
            } else {
                sleep(Rs2Random.between(14, 48));
            }
            // we check that the inventory changes in case the player's bank is full so that we don't cause an unintentional loop.
            // also since we're using the method "logout()", we'll also use "this.isRunning()" so people can avoid the logout by turning the plugin off.
            if (this.isRunning() && Rs2Inventory.getEmptySlots() < 28) {
                Microbot.showMessage("Bank is full, unable to deposit items.");
                long start = System.currentTimeMillis();
                while (this.isRunning() && ((System.currentTimeMillis()-start) < 120000) && Rs2Inventory.getEmptySlots() < 28) {
                    sleepUntilTrue(() -> Rs2Inventory.getEmptySlots() == 28,1800, 3600);
                    if (Rs2Inventory.getEmptySlots() == 28) sleep(10000);
                }
                if (this.isRunning() && Rs2Inventory.getEmptySlots() < 28) {
                    sleep(calculateSleepDuration(1));
                    Rs2Player.logout();
                    sleep(calculateSleepDuration(1));
                }
            }
        }

        if (!Rs2Inventory.hasItemAmount(itemId, quantityMax, true)) {
            Rs2Bank.depositAll(itemId);
        }

        // and here we just deposit anything that doesn't match our item entries
        List<Integer> bankExcept = new ArrayList<>();
        if (config.fourthItemQuantity() > 0) { if (fourthItemId != null) { if (Rs2Inventory.hasItem(fourthItemId)) { bankExcept.add(fourthItemId); } } else { if (Rs2Inventory.hasItem(config.fourthItemIdentifier())) { bankExcept.add(Rs2Inventory.get(config.fourthItemIdentifier()).getId()); } } }
        if (config.thirdItemQuantity() > 0) { if (thirdItemId != null) { if (Rs2Inventory.hasItem(thirdItemId)) { bankExcept.add(thirdItemId); } } else { if (Rs2Inventory.hasItem(config.thirdItemIdentifier())) { bankExcept.add(Rs2Inventory.get(config.thirdItemIdentifier()).getId()); } } }
        if (config.secondItemQuantity() > 0) { if (secondItemId != null) { if (Rs2Inventory.hasItem(secondItemId)) { bankExcept.add(secondItemId); } } else { if (Rs2Inventory.hasItem(config.secondItemIdentifier())) { bankExcept.add(Rs2Inventory.get(config.secondItemIdentifier()).getId()); } } }
        if (config.firstItemQuantity() > 0) { if (firstItemId != null) { if (Rs2Inventory.hasItem(firstItemId)) { bankExcept.add(firstItemId); } } else { if (Rs2Inventory.hasItem(config.firstItemIdentifier())) { bankExcept.add(Rs2Inventory.get(config.firstItemIdentifier()).getId()); } } }
        if (Rs2Inventory.getEmptySlots() < 28) {
            if (!bankExcept.isEmpty()) {
                Rs2Bank.depositAllExcept(bankExcept.toArray(new Integer[0]));
            } else {
                Rs2Bank.depositAll();
            }
            sleep(calculateSleepDuration(1));
        }
    }
    // method to parse string to integer, returns null if parsing fails
    public static Integer TryParseInt(String text) {
        if (text.isBlank()) return null;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            System.out.println("Could not Parse Int from Item, lookup item and return id");
            return Microbot.getItemManager().search(text).stream().map(ItemPrice::getId).findFirst().orElse(null);
        }
    }
    private void checkForAmulet(){
        if (!Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY) && !Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED)){
            Rs2ItemModel currentAmulet = Rs2Equipment.get(EquipmentInventorySlot.AMULET);
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen);
            }
            if (Rs2Bank.isOpen() && Rs2Bank.hasItem(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED)){
                Rs2Bank.withdrawAndEquip(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED);
            } else if (Rs2Bank.isOpen() && Rs2Bank.hasItem(ItemID.AMULET_OF_CHEMISTRY)) {
                Rs2Bank.withdrawAndEquip(ItemID.AMULET_OF_CHEMISTRY);
            } else {
                Microbot.log("Missing Alchemist's Amulet and Amulet of Chemistry. (disable button if not required to wear an amulet)");
                shutdown();
            }
            if (currentAmulet != null) {
                sleep(Rs2Random.between(1000, 1500));
                Rs2Bank.depositOne(currentAmulet.getId());
                sleep(Rs2Random.between(1000, 1500));
            }
        }
    }
}
