package net.runelite.client.plugins.microbot.util;

import net.runelite.api.Varbits;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.inventory.RunePouchType;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Utility class for managing inventory setups in the Microbot plugin.
 * Handles loading inventory and equipment setups, verifying matches, and ensuring
 * the correct items are equipped and in the inventory.
 */
public class Rs2InventorySetup {

    InventorySetup inventorySetup;

    ScheduledFuture<?> _mainScheduler;

    /**
     * Constructor to initialize the Rs2InventorySetup with a specific setup name and scheduler.
     *
     * @param name          The name of the inventory setup to load.
     * @param mainScheduler The scheduler to monitor for cancellation.
     */
    public Rs2InventorySetup(String name, ScheduledFuture<?> mainScheduler) {
        inventorySetup = MInventorySetupsPlugin.getInventorySetups().stream().filter(Objects::nonNull).filter(x -> x.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
        _mainScheduler = mainScheduler;
        if (inventorySetup == null) {
            Microbot.showMessage("Inventory load with name " + name + " not found!", 10);
            Microbot.pauseAllScripts = true;
        }
    }

    /**
     * Constructor to initialize the Rs2InventorySetup with a specific setup and scheduler.
     * The setup can now directly be fetched from the new config selector.
     *
     * @param setup          The inventory setup to load.
     * @param mainScheduler The scheduler to monitor for cancellation.
     */
    public Rs2InventorySetup(InventorySetup setup, ScheduledFuture<?> mainScheduler) {
        inventorySetup = setup;
        _mainScheduler = mainScheduler;
        if (inventorySetup == null) {
            Microbot.showMessage("Inventory load error!", 10);
            Microbot.pauseAllScripts = true;
        }
    }
    /**
     * Checks if an inventory setup with the specified name exists.
     *
     * @param name The name of the inventory setup to check for (case-insensitive)
     * @return {@code true} if an inventory setup with the specified name exists, {@code false} otherwise
     */
    public static boolean isInventorySetup(String name) {
        return MInventorySetupsPlugin.getInventorySetups().stream().filter(Objects::nonNull).filter(x -> x.getName().equalsIgnoreCase(name)).findFirst().orElse(null) != null;
    }

    /**
     * Checks if the main scheduler has been cancelled.
     *
     * @return true if the scheduler is cancelled, false otherwise.
     */
    public boolean isMainSchedulerCancelled() {
        return _mainScheduler != null && _mainScheduler.isCancelled();
    }

    /**
     * Loads the inventory setup from the bank.
     *
     * @return true if the inventory matches the setup after loading, false otherwise.
     */
	public boolean loadInventory() {
		Rs2Bank.openBank();
		if (!Rs2Bank.isOpen()) {
			return false;
		}

		Rs2Bank.depositAllExcept(itemsToNotDeposit());

		Map<Integer, List<InventorySetupsItem>> groupedByItems = inventorySetup.getInventory().stream()
			.collect(Collectors.groupingBy(InventorySetupsItem::getId));

		for (Map.Entry<Integer, List<InventorySetupsItem>> entry : groupedByItems.entrySet()) {
			if (isMainSchedulerCancelled()) break;

			InventorySetupsItem item = entry.getValue().get(0);
			int key = entry.getKey();

			if (InventorySetupsItem.itemIsDummy(item)) continue;

			int withdrawQuantity = calculateWithdrawQuantity(entry.getValue(), item, key);
			if (withdrawQuantity == 0) continue;

			String lowerCaseName = item.getName().toLowerCase();

			boolean isBarrowsItem = isBarrowsItem(lowerCaseName);
			if (isBarrowsItem) {
				item.setName(lowerCaseName.replaceAll("\\s+[1-9]\\d*$", ""));
			}

			boolean exact = !item.isFuzzy();

			if (!Rs2Bank.hasBankItem(lowerCaseName, withdrawQuantity, exact)) {
				Microbot.pauseAllScripts = true;
				Microbot.log("Bank is missing the following item: " + item.getName());
				return false;
			}

			withdrawItem(item, withdrawQuantity);
		}

		if (inventorySetup.getRune_pouch() != null) {
			Map<Runes, InventorySetupsItem> inventorySetupRunes = inventorySetup.getRune_pouch().stream()
				.filter(item -> item.getId() != -1 && item.getQuantity() > 0)
				.collect(Collectors.toMap(
					item -> Runes.byItemId(item.getId()),
					item -> item
				));

			if (!Rs2RunePouch.loadFromInventorySetup(inventorySetupRunes)) {
				Microbot.log("Failed to load rune pouch.");
				return false;
			}
		}

		sleep(800, 1200);

		return doesInventoryMatch();
	}

    private static boolean isBarrowsItem(String lowerCaseName) {
        boolean isBarrowsItem = !lowerCaseName.endsWith(" 0") &&  (lowerCaseName.contains("dharok's")
                || lowerCaseName.contains("ahrim's")
                || lowerCaseName.contains("guthan's")
                || lowerCaseName.contains("torag's")
                || lowerCaseName.contains("verac's"));
        return isBarrowsItem;
    }

    /**
     * Calculates the quantity of an item to withdraw based on the current inventory state.
     *
     * @param items              List of items to consider.
     * @param inventorySetupsItem The inventory setup item.
     * @param key                The item ID.
     * @return The quantity to withdraw.
     */
    private int calculateWithdrawQuantity(List<InventorySetupsItem> items, InventorySetupsItem inventorySetupsItem, int key) {
        int withdrawQuantity;
        if (items.size() == 1) {
            Rs2ItemModel rs2Item = Rs2Inventory.get(key);
            if (rs2Item != null && rs2Item.isStackable()) {
                withdrawQuantity = inventorySetupsItem.getQuantity() - rs2Item.getQuantity();
                if (Rs2Inventory.hasItemAmount(inventorySetupsItem.getName(), inventorySetupsItem.getQuantity())) {
                    return 0;
                }
            } else {
                withdrawQuantity = items.get(0).getQuantity();
                if (Rs2Inventory.hasItemAmount(inventorySetupsItem.getName(), withdrawQuantity)) {
                    return 0;
                }
            }
        } else {
            withdrawQuantity = items.size() - (int) Rs2Inventory.items().stream().filter(x -> x.getId() == key).count();
            if (Rs2Inventory.hasItemAmount(inventorySetupsItem.getName(), items.size())) {
                return 0;
            }
        }
        return withdrawQuantity;
    }

    /**
     * Withdraws an item from the bank.
     *
     * @param item     The item to withdraw.
     * @param quantity The quantity to withdraw.
     */
    private void withdrawItem(InventorySetupsItem item, int quantity) {
        if (item.isFuzzy()) {
            Rs2Bank.withdrawX(item.getName(), quantity);
        } else {
            if (quantity > 1) {
                Rs2Bank.withdrawX(item.getId(), quantity);
            } else {
                Rs2Bank.withdrawItem(item.getId());
            }
        }
		sleepUntil(() -> Rs2Inventory.hasItemAmount(item.getName(), item.getQuantity()));
    }

    /**
     * Loads the equipment setup from the bank.
     *
     * @return true if the equipment matches the setup after loading, false otherwise.
     */
    public boolean loadEquipment() {
        Rs2Bank.openBank();
        if (!Rs2Bank.isOpen()) {
            return false;
        }

        //Clear inventory if full
        if (Rs2Inventory.isFull()) {
            Rs2Bank.depositAll();
        } else {
            //only deposit the items we don't need
            Rs2Bank.depositAllExcept(itemsToNotDeposit());
        }


        /*
            Check if we have extra equipment already equipped before attempting to gear
            For example, player is wearing full graceful set but your desired inventory setup does not contain boots, keeping the graceful boots equipped
         */
        boolean hasExtraGearEquipped = Rs2Equipment.contains(equip ->
                inventorySetup.getEquipment().stream().noneMatch(setup -> setup.isFuzzy() ?
					equip.getName().toLowerCase().contains(setup.getName().toLowerCase()) :
					equip.getName().equalsIgnoreCase(setup.getName()))
        );

        if (hasExtraGearEquipped) {
            Microbot.log("Found Extra Gear that is not contained within the setup", Level.DEBUG);
            Rs2Bank.depositEquipment();
            sleepUntil(() -> Rs2Equipment.items().stream().noneMatch(Objects::nonNull));
        }

        for (InventorySetupsItem inventorySetupsItem : inventorySetup.getEquipment()) {
            if (isMainSchedulerCancelled()) break;
            if (InventorySetupsItem.itemIsDummy(inventorySetupsItem)) continue;

            String lowerCaseName = inventorySetupsItem.getName().toLowerCase();

            boolean isBarrowsItem = isBarrowsItem(lowerCaseName);

            if (isBarrowsItem) {
                inventorySetupsItem.setName(lowerCaseName.replaceAll("\\s+[1-9]\\d*$", ""));
            }

            if (inventorySetupsItem.isFuzzy()) {
				if (Rs2Equipment.isWearing(inventorySetupsItem.getName()))
					continue;

                if (Rs2Inventory.hasItem(inventorySetupsItem.getName()) || Rs2Inventory.hasItemAmount(inventorySetupsItem.getName(), (int) inventorySetup.getInventory().stream().filter(x -> x.getId() == inventorySetupsItem.getId()).count())) {
                    Rs2Bank.wearItem(inventorySetupsItem.getName());
                    continue;
                }

                if (inventorySetupsItem.getQuantity() > 1) {
                    Rs2Bank.withdrawAllAndEquip(inventorySetupsItem.getName());
                } else {
                    Rs2Bank.withdrawAndEquip(inventorySetupsItem.getName());
                }

				sleepUntil(() -> Rs2Equipment.isWearing(inventorySetupsItem.getName()));
            } else {
                if (!Rs2Bank.hasItem(inventorySetupsItem.getName()) && !Rs2Inventory.hasItem(inventorySetupsItem.getName()))
                    continue;

                if (Rs2Inventory.hasItem(inventorySetupsItem.getName())) {
                    Rs2Bank.wearItem(inventorySetupsItem.getName());
					sleepUntil(() -> Rs2Equipment.isWearing(inventorySetupsItem.getName()));
                    continue;
                }

                if (inventorySetupsItem.getQuantity() > 1) {
                    Rs2Bank.withdrawAllAndEquip(inventorySetupsItem.getName());
                } else {
                    Rs2Bank.withdrawAndEquip(inventorySetupsItem.getName());
                }

				sleepUntil(() -> Rs2Equipment.isWearing(inventorySetupsItem.getName()));
            }
        }

        sleep(800, 1200);

        return doesEquipmentMatch();
    }

    /**
     * Wears the equipment items defined in the inventory setup.
     * Iterates through the equipment setup and equips the items to the player.
     *
     * @return true if the equipment setup matches the current worn equipment, false otherwise.
     */
    public boolean wearEquipment() {
        for (InventorySetupsItem inventorySetupsItem : inventorySetup.getEquipment()) {
            Rs2Inventory.wield(inventorySetupsItem.getId());
        }
        return doesEquipmentMatch();
    }

    /**
     * Checks if the current inventory matches the setup defined in the inventory setup.
     * It compares the quantity and stackability of items in the current inventory
     * against the quantities required by the inventory setup.
     *
     * @return true if the inventory matches the setup, false otherwise.
     */
	public boolean doesInventoryMatch() {
		if (inventorySetup == null || inventorySetup.getInventory() == null) {
			return false;
		}

		Map<Integer, List<InventorySetupsItem>> groupedByItems = inventorySetup.getInventory().stream()
			.collect(Collectors.groupingBy(InventorySetupsItem::getId));

		boolean found = true;

		for (Map.Entry<Integer, List<InventorySetupsItem>> entry : groupedByItems.entrySet()) {
			InventorySetupsItem item = entry.getValue().get(0);
			if (item.getId() == -1) continue;

			int withdrawQuantity;
			boolean isStackable = false;

			if (entry.getValue().size() == 1) {
				withdrawQuantity = item.getQuantity();
				isStackable = withdrawQuantity > 1;
			} else {
				withdrawQuantity = entry.getValue().size();
			}

			if (!Rs2Inventory.hasItemAmount(item.getName(), withdrawQuantity, isStackable)) {
				Microbot.log("Looking for " + item.getName() + " with amount " + withdrawQuantity);
				found = false;
			}
		}

		if (inventorySetup.getRune_pouch() != null) {
			Map<Runes, Integer> requiredRunes = inventorySetup.getRune_pouch().stream()
				.filter(item -> item.getId() != -1 && item.getQuantity() > 0)
				.map(item -> Map.entry(Runes.byItemId(item.getId()), item.getQuantity()))
				.filter(e -> e.getKey() != null)
				.collect(Collectors.toMap(
					Map.Entry::getKey,
					Map.Entry::getValue,
					Integer::sum
				));

			if (!Rs2RunePouch.contains(requiredRunes, false)) {
				Microbot.log("Rune pouch contents do not match expected setup.");
				found = false;
			}
		}

		return found;
	}

    /**
     * Checks if the current equipment setup matches the desired setup.
     * Iterates through the equipment setup items and verifies if they are equipped properly.
     *
     * @return true if all equipment items match the setup, false otherwise.
     */
    public boolean doesEquipmentMatch() {
        if(inventorySetup ==null || inventorySetup.getEquipment() == null) {
            return false;
        }
        for (InventorySetupsItem inventorySetupsItem : inventorySetup.getEquipment()) {
            if (inventorySetupsItem.getId() == -1) continue;
            if (inventorySetupsItem.isFuzzy()) {
                if (!Rs2Equipment.isWearing(inventorySetupsItem.getName(), false)) {
                    Microbot.log("Missing item " + inventorySetupsItem.getName());
                    return false;
                }
            } else {
                if (!Rs2Equipment.isWearing(inventorySetupsItem.getName(), true)) {
                    Microbot.log("Missing item " + inventorySetupsItem.getName());
                    return false;
                }
            }
        }
        return true;
    }
    /**
     * Retrieves the list of inventory items from the setup, excluding any dummy items (ID == -1).
     *
     * @return A list of valid inventory items.
     */
    public List<InventorySetupsItem> getInventoryItems() {
        return inventorySetup.getInventory().stream().filter(x -> x.getId() != -1).collect(Collectors.toList());
    }

    /**
     * Retrieves the list of equipment items from the setup, excluding any dummy items (ID == -1).
     *
     * @return A list of valid equipment items.
     */
    public List<InventorySetupsItem> getEquipmentItems() {
        return inventorySetup.getEquipment().stream().filter(x -> x.getId() != -1).collect(Collectors.toList());
    }

    /**
     * Retrieves the list of additional items from the setup, excluding any dummy items (ID == -1).
     *
     * @return A list of valid additional filtered items.
     */
    public List<InventorySetupsItem> getAdditionalItems() {
        return inventorySetup.getAdditionalFilteredItems().values().stream().filter(x -> x.getId() != -1).collect(Collectors.toList());
    }

    /**
     * Creates a list of item names that should not be deposited into the bank.
     * Combines items from both the inventory setup and the equipment setup.
     *
     * @return A list of item names that should not be deposited.
     */
    public Map<String, Boolean> itemsToNotDeposit() {
        List<InventorySetupsItem> inventorySetupItems = getInventoryItems();
        List<InventorySetupsItem> equipmentSetupItems = getEquipmentItems();

        List<InventorySetupsItem> combined = new ArrayList<>();

        combined.addAll(inventorySetupItems);
        combined.addAll(equipmentSetupItems);

        return combined.stream()
                .collect(Collectors.toMap(
                        InventorySetupsItem::getName,
                        InventorySetupsItem::isFuzzy,
                        (existing, replacement) -> existing)
                );
    }

    /**
     * Checks if the current spellbook matches the one defined in the inventory setup.
     *
     * @return true if the current spellbook matches the setup, false otherwise.
     */
    public boolean hasSpellBook() {
        return inventorySetup.getSpellBook() == Microbot.getVarbitValue(Varbits.SPELLBOOK);
    }
}
