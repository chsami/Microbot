package net.runelite.client.plugins.microbot.util;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.event.Level;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Utility class for managing inventory setups in the Microbot plugin.
 * Handles loading inventory and equipment setups, verifying matches, and ensuring
 * the correct items are equipped and in the inventory.
 */
public class Rs2InventorySetup {

    /**
     * When {@code -Dmicrobot.bank.validateInventorySetup=true}, {@link #loadInventory()} logs warnings for stale preset
     * ids, id/name drift, and impossible non-stackable quantities on a single row.
     */
    private static final String PROP_VALIDATE_INVENTORY_SETUP = "microbot.bank.validateInventorySetup";

	/**
	 * {@link Client#getItemDefinition(int)} is client-thread-only; inventory setup runs from script executor threads.
	 */
	private static ItemComposition getItemDefinitionThreadSafe(int id) {
		Client c = Microbot.getClient();
		if (c == null) {
			return null;
		}
		if (c.isClientThread()) {
			return c.getItemDefinition(id);
		}
		return Microbot.getClientThread().runOnClientThreadOptional(() -> c.getItemDefinition(id)).orElse(null);
	}

	/**
	 * Non-fuzzy rows: use cache definition so stackables (runes, bolts, etc.) always use total-quantity math,
	 * not "missing slot count" ({@code matchingRows - invStacks}), which blows up for duplicate rows or bad heuristics.
	 */
	private static boolean setupRowIsStackableByDefinition(InventorySetupsItem setupItem) {
		if (setupItem == null || setupItem.isFuzzy()) {
			return false;
		}
		int id = setupItem.getId();
		if (id <= 0) {
			return false;
		}
		ItemComposition comp = getItemDefinitionThreadSafe(id);
		return comp != null && comp.isStackable();
	}

	/**
	 * Matches {@link #calculateWithdrawQuantity}: stack total vs stack count for unslotted checks.
	 */
	private static boolean inventoryMatchUseStackQuantity(InventorySetupsItem groupRep, int groupSize, int desiredSum) {
		assert groupRep != null;
		if (groupSize != 1) {
			return false;
		}
		if (setupRowIsStackableByDefinition(groupRep)) {
			return true;
		}
		return desiredSum > 1;
	}

	/**
	 * Inventory Setups "&lt;" stack indicator: UI only highlights when current qty is below the saved qty.
	 * For automation, treat saved qty as a soft target — withdraw up to bank max and accept inv under target after load.
	 */
	private static boolean setupSoftMinStackTarget(InventorySetupsItem setupItem) {
		if (setupItem == null) {
			return false;
		}
		InventorySetupsStackCompareID sc = setupItem.getStackCompare();
		return sc == InventorySetupsStackCompareID.Less_Than;
	}

	/**
	 * Withdraw up to bank holdings when short; do not pause for less-than-preset totals. Applies to
	 * {@link InventorySetupsStackCompareID#Less_Than} and {@link InventorySetupsStackCompareID#Standard} ({@code !=}).
	 */
	private static boolean setupWithdrawIgnoresExactBankTotal(InventorySetupsItem setupItem) {
		if (setupItem == null) {
			return false;
		}
		InventorySetupsStackCompareID sc = stackCompareOf(setupItem);
		return sc == InventorySetupsStackCompareID.Less_Than || sc == InventorySetupsStackCompareID.Standard;
	}

	private static InventorySetupsStackCompareID stackCompareOf(InventorySetupsItem setupItem) {
		if (setupItem == null || setupItem.getStackCompare() == null) {
			return InventorySetupsStackCompareID.None;
		}
		return setupItem.getStackCompare();
	}

	/**
	 * Slotted row: stack indicator from Inventory Setups (None / != / &lt; / &gt;).
	 * <p>{@link InventorySetupsStackCompareID#Standard} ({@code !=}): automation only requires the correct item in the
	 * slot, not a matching stack size (withdraw also uses partial-bank semantics).
	 * {@link InventorySetupsStackCompareID#None}: exact quantity. {@link InventorySetupsStackCompareID#Less_Than}:
	 * same presence-only qty check as Standard here.
	 */
	private static boolean inventorySlotQuantityMatchesPreset(int invQty, InventorySetupsItem setupItem) {
		assert setupItem != null;
		int need = setupItem.getQuantity();
		switch (stackCompareOf(setupItem)) {
			case None:
				return invQty == need;
			case Standard:
			case Less_Than:
				return true;
			case Greater_Than:
				if (need <= 0) {
					return true;
				}
				return invQty > 0 && invQty <= need;
			default:
				return invQty >= need;
		}
	}

	/**
	 * Unslotted pooled check aligned with {@link InventorySetupsSlot#shouldHighlightSlotBasedOnStack}.
	 */
	private boolean unslottedInventorySatisfiesPreset(InventorySetupsItem setupItem, int withdrawQuantity, boolean useStackQuantity) {
		assert setupItem != null;
		if (withdrawQuantity <= 0) {
			return true;
		}
		int invStack = Rs2Inventory.itemQuantity(setupItem.getName());
		int invCount = Rs2Inventory.count(setupItem.getName(), false);
		switch (stackCompareOf(setupItem)) {
			case Less_Than:
				return useStackQuantity ? invStack > 0 : invCount > 0;
			case Standard:
				if (useStackQuantity) {
					return invStack > 0;
				}
				return invCount == withdrawQuantity;
			case Greater_Than:
				if (useStackQuantity) {
					return invStack > 0 && invStack <= withdrawQuantity;
				}
				return invCount > 0 && invCount <= withdrawQuantity;
			case None:
				if (useStackQuantity) {
					return invStack == withdrawQuantity;
				}
				return invCount == withdrawQuantity;
			default:
				return Rs2Inventory.hasItemAmount(setupItem.getName(), withdrawQuantity, useStackQuantity);
		}
	}

	private static void addRuneLikePouchRows(List<InventorySetupsItem> out, List<InventorySetupsItem> pouch) {
		if (pouch == null) {
			return;
		}
		for (InventorySetupsItem x : pouch) {
			if (x != null && x.getId() != -1 && x.getQuantity() > 0 && !InventorySetupsItem.itemIsDummy(x)) {
				out.add(x);
			}
		}
	}

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
        if (inventorySetup == null) {
			inventorySetup = MInventorySetupsPlugin.getInventorySetups().stream().filter(Objects::nonNull).filter(x -> x.getName().equals("default")).findFirst().orElse(null);
		}
		_mainScheduler = mainScheduler;
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
			Microbot.pauseAllScripts.compareAndSet(false, true);
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
	 * Level-aware structured lines: {@code [InventorySetup:<preset>] message} via {@link Microbot#log(Level, String, Object...)}.
	 */
	private void logSetup(Level level, String format, Object... args) {
		String prefix = "[InventorySetup:" + (inventorySetup != null ? inventorySetup.getName() : "?") + "] ";
		if (args == null || args.length == 0) {
			Microbot.log(prefix + format, level);
		} else {
			Microbot.log(level, prefix + format, args);
		}
	}

	/**
	 * Total quantity in bank mirror for a preset row (id total, with lowercase name fallback when id total is 0).
	 */
	private static int bankQtyForPresetRow(InventorySetupsItem item, String lowerCaseName, boolean isFuzzy) {
		assert item != null;
		assert lowerCaseName != null;
		if (isFuzzy) {
			return Rs2Bank.count(lowerCaseName);
		}
		int byId = Rs2Bank.count(item.getId());
		if (byId > 0) {
			return byId;
		}
		return Rs2Bank.count(lowerCaseName, false);
	}

    /**
     * Loads the inventory setup from the bank.
     *
     * @return true if the inventory matches the setup after loading, false otherwise.
     */
	public boolean loadInventory() {
		return loadInventory(true);
	}

	/**
	 * @param skipIfAlreadyMatching when {@code true}, skip bank open when inventory already matches the setup,
	 *                              there are no foreign items, and quantities are not over setup limits.
	 */
	public boolean loadInventory(boolean skipIfAlreadyMatching) {
		if (inventorySetup == null) {
			return false;
		}

		validateInventorySetupAgainstDefsIfEnabled();

		Set<Integer> retainIds = computeSetupRetainItemIds();
		Map<String, Boolean> fuzzy = computeSetupFuzzyKeepNames();
		if (skipIfAlreadyMatching && doesInventoryMatch() && !needsDepositCleanupBeforeBanking(retainIds, fuzzy)) {
			return true;
		}

		boolean bankWasOpen = Rs2Bank.isOpen();
		int bankEpochBeforeOpen = Rs2Bank.getBankLiveEpoch();
		Rs2Bank.openBank();
		if (!Rs2Bank.isOpen()) {
			return false;
		}
		if (!Rs2Bank.verifyBankMirrorAfterOpen(bankWasOpen, bankEpochBeforeOpen)) {
			logSetup(Level.WARN, "bank mirror not ready after open (epoch before=%d after=%d) — abort load",
					bankEpochBeforeOpen, Rs2Bank.getBankLiveEpoch());
			return false;
		}

        if (!Rs2Bank.findLockedSlots().isEmpty()) {
            Rs2Bank.toggleAllLocks();
        }

		if (Rs2Inventory.isFull()) {
			int epochBeforeDeposit = Rs2Bank.getBankLiveEpoch();
			if (Rs2Bank.depositAll()) {
				Rs2Bank.syncBankInventoryAfterChange(epochBeforeDeposit);
			}
		} else if (needsDepositCleanupBeforeBanking(retainIds, fuzzy)) {
			int epochBeforeDeposit = Rs2Bank.getBankLiveEpoch();
			if (Rs2Bank.depositAllExcept(retainIds, fuzzy)) {
				Rs2Bank.syncBankInventoryAfterChange(epochBeforeDeposit);
			}
		}

		List<InventorySetupsItem> setupItems = inventorySetup.getInventory();

		Set<String> withdrewInventoryGroups = new HashSet<>();
		for (InventorySetupsItem item : setupItems) {
			if (isMainSchedulerCancelled()) break;
			if (InventorySetupsItem.itemIsDummy(item)) continue;

			String withdrawGroupKey = item.isFuzzy()
				? "f:" + item.getName().toLowerCase(Locale.ROOT)
				: "i:" + item.getId();
			if (!withdrewInventoryGroups.add(withdrawGroupKey)) {
				continue;
			}

			List<InventorySetupsItem> matchingItems = setupItems.stream()
				.filter(i -> i.matches(item))
				.collect(Collectors.toList());

			int desiredWithdraw = calculateWithdrawQuantity(matchingItems, item);
			if (desiredWithdraw == 0) continue;

			Rs2ItemModel existingItem = new Rs2ItemModel(item.getId(), desiredWithdraw, desiredWithdraw);
			boolean isNoted = existingItem.isNoted(); //for noted items, we also need to use the name of the hasBankItem method, or the unnoted id
			String lowerCaseName = item.getName().toLowerCase();
			boolean isFuzzy = item.isFuzzy();
			Object identifier = isFuzzy ? lowerCaseName : item.getId();

			boolean partialBankWithdraw = matchingItems.stream().anyMatch(Rs2InventorySetup::setupWithdrawIgnoresExactBankTotal);
			int bankAvail = bankQtyForPresetRow(item, lowerCaseName, isFuzzy);
			int withdrawQuantity = partialBankWithdraw ? Math.min(desiredWithdraw, bankAvail) : desiredWithdraw;

			if (withdrawQuantity == 0 && desiredWithdraw > 0) {
				Microbot.pauseAllScripts.compareAndSet(false, true);
				int setupTotal = matchingItems.stream().mapToInt(InventorySetupsItem::getQuantity).sum();
				int invQty = isFuzzy ? Rs2Inventory.itemQuantity(lowerCaseName) : Rs2Inventory.itemQuantity(item.getId());
				logSetup(Level.WARN,
						"bank short: %s | bank=%d | inv=%d | setup_total=%d | withdraw=%d (id=%d fuzzy=%b noted=%b)",
						item.getName(), bankAvail, invQty, setupTotal, desiredWithdraw,
						item.getId(), isFuzzy, isNoted);
				return false;
			}
			if (withdrawQuantity == 0) {
				continue;
			}

			if (partialBankWithdraw && withdrawQuantity < desiredWithdraw) {
				logSetup(Level.INFO, "partial withdraw %s: take=%d wanted=%d (!= or < stack mode)",
						item.getName(), withdrawQuantity, desiredWithdraw);
			}

			boolean hasBankItem = isFuzzy || isNoted
				? Rs2Bank.hasBankItem((String) identifier, withdrawQuantity, false)
				: Rs2Bank.hasBankItem((int) identifier, withdrawQuantity);
			if (!hasBankItem && !isFuzzy && !isNoted) {
				hasBankItem = Rs2Bank.hasBankItem(lowerCaseName, withdrawQuantity, false);
			}

			if (!hasBankItem) {
				Microbot.pauseAllScripts.compareAndSet(false, true);
				int setupTotal = matchingItems.stream().mapToInt(InventorySetupsItem::getQuantity).sum();
				int invQty = isFuzzy ? Rs2Inventory.itemQuantity(lowerCaseName) : Rs2Inventory.itemQuantity(item.getId());
				logSetup(Level.WARN,
						"bank short: %s | bank=%d | inv=%d | setup_total=%d | withdraw=%d (id=%d fuzzy=%b noted=%b)",
						item.getName(), bankAvail, invQty, setupTotal, withdrawQuantity,
						item.getId(), isFuzzy, isNoted);
				return false;
			}

			withdrawItem(item, withdrawQuantity,isNoted);
		}

		List<InventorySetupsItem> itemsWithSlots = setupItems.stream()
			.filter(item -> !InventorySetupsItem.itemIsDummy(item) && item.getSlot() >= 0)
			.collect(Collectors.toList());

		sortInventoryItems(itemsWithSlots);

        if (inventorySetup.getRune_pouch() != null) {
			Map<Runes, InventorySetupsItem> inventorySetupRunes = inventorySetup.getRune_pouch().stream()
				.filter(item -> item.getId() != -1 && item.getQuantity() > 0)
				.collect(Collectors.toMap(
					item -> Runes.byItemId(item.getId()),
					item -> item
				));

			if (!Rs2RunePouch.loadFromInventorySetup(inventorySetupRunes)) {
				logSetup(Level.WARN, "rune pouch load failed");
				return false;
			}
		}

		sleep(800, 1200);

        lockLockedItemsFromSetup(inventorySetup);

		return doesInventoryMatch();
	}

	private static String firstNonEmptyCompositionName(ItemComposition comp)
	{
		if (comp == null)
		{
			return null;
		}
		String a = comp.getMembersName();
		String b = comp.getName();
		if (a != null && !a.isEmpty())
		{
			return a;
		}
		if (b != null && !b.isEmpty())
		{
			return b;
		}
		return null;
	}

	private void validateInventorySetupAgainstDefsIfEnabled()
	{
		if (!Boolean.parseBoolean(System.getProperty(PROP_VALIDATE_INVENTORY_SETUP, "false")))
		{
			return;
		}
		if (inventorySetup == null)
		{
			return;
		}
		List<InventorySetupsItem> inv = inventorySetup.getInventory();
		List<InventorySetupsItem> equip = inventorySetup.getEquipment();
		List<InventorySetupsItem> all = new ArrayList<>();
		if (inv != null)
		{
			all.addAll(inv);
		}
		if (equip != null)
		{
			all.addAll(equip);
		}
		if (inventorySetup.getAdditionalFilteredItems() != null)
		{
			all.addAll(inventorySetup.getAdditionalFilteredItems().values());
		}
		addRuneLikePouchRows(all, inventorySetup.getRune_pouch());
		addRuneLikePouchRows(all, inventorySetup.getBoltPouch());
		addRuneLikePouchRows(all, inventorySetup.getQuiver());

		Set<Integer> loggedIdNameDrift = new HashSet<>();

		for (InventorySetupsItem item : all)
		{
			if (item == null || InventorySetupsItem.itemIsDummy(item))
			{
				continue;
			}
			if (item.isFuzzy())
			{
				continue;
			}
			int id = item.getId();
			if (id <= 0)
			{
				logSetup(Level.WARN, "validate: non-fuzzy row invalid id for \"%s\"", item.getName());
				continue;
			}
			ItemComposition comp = getItemDefinitionThreadSafe(id);
			if (comp == null)
			{
				logSetup(Level.WARN, "validate: no ItemComposition id=%d name=\"%s\"", id, item.getName());
				continue;
			}
			String defName = firstNonEmptyCompositionName(comp);
			String setupName = item.getName();
			if (defName != null && setupName != null && loggedIdNameDrift.add(id)
					&& !defName.equalsIgnoreCase(setupName)
					&& !defName.toLowerCase(Locale.ROOT).contains(setupName.toLowerCase(Locale.ROOT))
					&& !setupName.toLowerCase(Locale.ROOT).contains(defName.toLowerCase(Locale.ROOT)))
			{
				logSetup(Level.WARN, "validate: id/name drift id=%d setup=\"%s\" def=\"%s\"", id, setupName, defName);
			}
		}

		if (inv == null)
		{
			return;
		}

		Set<Integer> warnedNonStackableQty = new HashSet<>();
		for (InventorySetupsItem item : inv)
		{
			if (InventorySetupsItem.itemIsDummy(item) || item.isFuzzy())
			{
				continue;
			}
			List<InventorySetupsItem> matchingItems = inv.stream().filter(i -> i.matches(item)).collect(Collectors.toList());
			int desiredQuantity = matchingItems.stream().mapToInt(InventorySetupsItem::getQuantity).sum();
			if (matchingItems.size() != 1 || desiredQuantity <= 1)
			{
				continue;
			}
			int itemId = item.getId();
			if (itemId <= 0)
			{
				continue;
			}
			ItemComposition comp = getItemDefinitionThreadSafe(itemId);
			if (comp == null || comp.isStackable())
			{
				continue;
			}
			if (warnedNonStackableQty.add(itemId))
			{
				logSetup(Level.WARN,
						"validate: non-stackable \"%s\" (id=%d) qty=%d on one row — split rows or use fuzzy",
						item.getName(), itemId, desiredQuantity);
			}
		}
	}

	private List<InventorySetupsItem> allNonDummySetupItemsForDepositRules() {
		List<InventorySetupsItem> out = new ArrayList<>();
		if (inventorySetup == null) {
			return out;
		}
		if (inventorySetup.getInventory() != null) {
			for (InventorySetupsItem x : inventorySetup.getInventory()) {
				if (x != null && !InventorySetupsItem.itemIsDummy(x)) {
					out.add(x);
				}
			}
		}
		if (inventorySetup.getEquipment() != null) {
			for (InventorySetupsItem x : inventorySetup.getEquipment()) {
				if (x != null && !InventorySetupsItem.itemIsDummy(x)) {
					out.add(x);
				}
			}
		}
		if (inventorySetup.getAdditionalFilteredItems() != null) {
			for (InventorySetupsItem x : inventorySetup.getAdditionalFilteredItems().values()) {
				if (x != null && !InventorySetupsItem.itemIsDummy(x)) {
					out.add(x);
				}
			}
		}
		addRuneLikePouchRows(out, inventorySetup.getRune_pouch());
		addRuneLikePouchRows(out, inventorySetup.getBoltPouch());
		addRuneLikePouchRows(out, inventorySetup.getQuiver());
		return out;
	}

	private Set<Integer> computeSetupRetainItemIds() {
		Set<Integer> ids = new HashSet<>();
		for (InventorySetupsItem item : allNonDummySetupItemsForDepositRules()) {
			if (item.isFuzzy()) {
				continue;
			}
			int id = item.getId();
			if (id <= 0) {
				continue;
			}
			ids.add(id);
			ItemComposition comp = getItemDefinitionThreadSafe(id);
			if (comp != null) {
				int linked = comp.getLinkedNoteId();
				if (linked > 0 && linked != id) {
					ids.add(linked);
				}
			}
		}
		return ids;
	}

	private Map<String, Boolean> computeSetupFuzzyKeepNames() {
		Map<String, Boolean> map = new LinkedHashMap<>();
		for (InventorySetupsItem item : allNonDummySetupItemsForDepositRules()) {
			String name = item.getName();
			if (name == null || name.isEmpty()) {
				continue;
			}
			map.merge(name, item.isFuzzy(), (a, b) -> a || b);
		}
		return map;
	}

	private boolean needsDepositCleanupBeforeBanking(Set<Integer> retainIds, Map<String, Boolean> fuzzy) {
		if (retainIds == null || fuzzy == null) {
			return true;
		}

		boolean foreign = Rs2Inventory.items()
				.anyMatch(inv -> !Rs2Bank.isInventoryItemRetainedForSetupDeposit(inv, retainIds, fuzzy));
		return foreign || inventoryExceedsSetupQuantities();
	}

	private boolean inventoryExceedsSetupQuantities() {
		if (inventorySetup == null || inventorySetup.getInventory() == null) {
			return false;
		}
		List<InventorySetupsItem> setupItems = inventorySetup.getInventory();
		Set<String> seenGroup = new HashSet<>();
		for (InventorySetupsItem item : setupItems) {
			if (InventorySetupsItem.itemIsDummy(item)) {
				continue;
			}
			String key = item.isFuzzy()
					? "f:" + item.getName().toLowerCase(Locale.ROOT)
					: "i:" + item.getId();
			if (!seenGroup.add(key)) {
				continue;
			}
			List<InventorySetupsItem> matching = setupItems.stream().filter(i -> i.matches(item)).collect(Collectors.toList());
			int desiredQuantity = matching.stream().mapToInt(InventorySetupsItem::getQuantity).sum();
			boolean isFuzzy = item.isFuzzy();
			int itemId = item.getId();
			String itemName = item.getName().toLowerCase(Locale.ROOT);
			boolean singleStackRow = matching.size() == 1 && desiredQuantity > 1;
			if (singleStackRow) {
				int currentQuantity = isFuzzy ? Rs2Inventory.itemQuantity(itemName) : Rs2Inventory.itemQuantity(itemId);
				boolean allowExcess = matching.stream().anyMatch(Rs2InventorySetup::setupWithdrawIgnoresExactBankTotal);
				if (currentQuantity > desiredQuantity && !allowExcess) {
					return true;
				}
			} else {
				long alreadyPresent = isFuzzy
						? Rs2Inventory.items(i -> i.getName().toLowerCase(Locale.ROOT).contains(itemName)).count()
						: Rs2Inventory.items(i -> i.getId() == itemId).count();
				if (alreadyPresent > matching.size()) {
					return true;
				}
			}
		}
		return false;
	}

    /**
     * Calculates the quantity of an item to withdraw based on the current inventory state.
     *
     * @param setupItems              List of items to consider.
     * @param setupItem The inventory setup item.
     * @return The quantity to withdraw.
     */
	private int calculateWithdrawQuantity(List<InventorySetupsItem> setupItems, InventorySetupsItem setupItem) {
		int itemId = setupItem.getId();
		String itemName = setupItem.getName().toLowerCase(Locale.ROOT);
		boolean isFuzzy = setupItem.isFuzzy();

		int desiredQuantity = setupItems.stream()
			.mapToInt(InventorySetupsItem::getQuantity)
			.sum();

		int currentQuantity = isFuzzy
			? Rs2Inventory.itemQuantity(itemName)
			: Rs2Inventory.itemQuantity(itemId);

		if (currentQuantity >= desiredQuantity) {
			return 0;
		}

		if (setupRowIsStackableByDefinition(setupItem)) {
			return desiredQuantity - currentQuantity;
		}

		boolean legacyStackableHeuristic = (setupItems.size() == 1) && (desiredQuantity > 1);

		if (!legacyStackableHeuristic) {
			long alreadyPresent = isFuzzy
				? Rs2Inventory.items(i -> i.getName().toLowerCase(Locale.ROOT).contains(itemName)).count()
				: Rs2Inventory.items(i -> i.getId() == itemId).count();

			int missing = setupItems.size() - (int) alreadyPresent;
			return Math.max(missing, 0);
		}

		return desiredQuantity - currentQuantity;
	}

    /**
     * Withdraws an item from the bank.
     *
     * @param item     The item to withdraw.
     * @param quantity The quantity to withdraw.
     */
    private void withdrawItem(InventorySetupsItem item, int quantity,  boolean isNoted) {
		boolean useName = item.isFuzzy() || isNoted; // when notded we must use the name to withdraw or we must use the unnoted id
		Object identifier = useName ? item.getName().toLowerCase() : item.getId();
		boolean isWithdrawAs= Rs2Bank.isWithdrawAs(isNoted);
		if(!isWithdrawAs){
			Rs2Bank.setWithdrawAs(isNoted);
		}
		if (quantity > 1) {
			if (useName) {
				Rs2Bank.withdrawX((String) identifier, quantity);
			} else {
				Rs2Bank.withdrawX((int) identifier, quantity);
			}
		} else {
			if (useName) {
				Rs2Bank.withdrawItem((String) identifier);
			} else {
				Rs2Bank.withdrawItem((int) identifier);
			}
		}
		// Using wait for inventory changes here makes sure the inventory is updated more reliably to avoid withdrawing excess items.
		Rs2Inventory.waitForInventoryChanges(5000);
    }

    /**
     * Loads the equipment setup from the bank.
     *
     * @return true if the equipment matches the setup after loading, false otherwise.
     */
    public boolean loadEquipment() {
        return loadEquipment(true);
    }

	/**
	 * @param skipIfAlreadyMatching when {@code true}, skip bank if equipment matches and inventory needs no deposit cleanup.
	 */
	public boolean loadEquipment(boolean skipIfAlreadyMatching) {
		if (inventorySetup == null) {
			return false;
		}

		Set<Integer> retainIds = computeSetupRetainItemIds();
		Map<String, Boolean> fuzzy = computeSetupFuzzyKeepNames();
		if (skipIfAlreadyMatching && doesEquipmentMatch() && !needsDepositCleanupBeforeBanking(retainIds, fuzzy)) {
			return true;
		}

		boolean bankWasOpen = Rs2Bank.isOpen();
		int bankEpochBeforeOpen = Rs2Bank.getBankLiveEpoch();
        Rs2Bank.openBank();
        if (!Rs2Bank.isOpen()) {
            return false;
        }
		if (!Rs2Bank.verifyBankMirrorAfterOpen(bankWasOpen, bankEpochBeforeOpen)) {
			logSetup(Level.WARN, "bank mirror not ready after open (epoch before=%d after=%d) — abort equipment load",
					bankEpochBeforeOpen, Rs2Bank.getBankLiveEpoch());
			return false;
		}

        //Clear inventory if full
        if (Rs2Inventory.isFull()) {
            int epochBeforeDeposit = Rs2Bank.getBankLiveEpoch();
            if (Rs2Bank.depositAll()) {
                Rs2Bank.syncBankInventoryAfterChange(epochBeforeDeposit);
            }
        } else if (needsDepositCleanupBeforeBanking(retainIds, fuzzy)) {
            int epochBeforeDeposit = Rs2Bank.getBankLiveEpoch();
            if (Rs2Bank.depositAllExcept(retainIds, fuzzy)) {
                Rs2Bank.syncBankInventoryAfterChange(epochBeforeDeposit);
            }
        }


        /*
            Check if we have extra equipment already equipped before attempting to gear
            For example, player is wearing full graceful set but your desired inventory setup does not contain boots, keeping the graceful boots equipped
         */
        boolean hasExtraGearEquipped = Rs2Equipment.isWearing(equip ->
                inventorySetup.getEquipment().stream().noneMatch(setup -> setup.isFuzzy() ?
					equip.getName().toLowerCase().contains(setup.getName().toLowerCase()) :
					equip.getName().equalsIgnoreCase(setup.getName()))
        );

        if (hasExtraGearEquipped) {
            logSetup(Level.DEBUG, "extra gear not in setup — deposit equipment");
            Rs2Bank.depositEquipment();
            sleepUntil(() -> Rs2Equipment.items().stream().noneMatch(Objects::nonNull));
        }

		for (InventorySetupsItem item : inventorySetup.getEquipment()) {
			if (isMainSchedulerCancelled()) break;
			if (InventorySetupsItem.itemIsDummy(item)) continue;

			String lowerCaseName = item.getName().toLowerCase();

			boolean isFuzzy = item.isFuzzy();
			Object identifier = isFuzzy ? item.getName().toLowerCase() : item.getId();

			// Check if already equipped
			if (Rs2Equipment.isWearing(item.getName())) continue;

			// Check in inventory
			boolean inInventory = isFuzzy
				? Rs2Inventory.hasItem((String) identifier) || Rs2Inventory.hasItemAmount((String) identifier, item.getQuantity())
				: Rs2Inventory.hasItem((int) identifier) || Rs2Inventory.hasItemAmount((int) identifier, item.getQuantity());

			// Check in bank (name fallback covers stale preset ids vs live bank row ids)
			boolean inBank = isFuzzy
				? Rs2Bank.hasItem((String) identifier) || Rs2Bank.hasBankItem((String) identifier, item.getQuantity(), false)
				: Rs2Bank.hasItem((int) identifier) || Rs2Bank.hasBankItem((int) identifier, item.getQuantity());
			if (!inBank && !isFuzzy) {
				inBank = Rs2Bank.hasBankItem(lowerCaseName, item.getQuantity(), false);
			}

			if (!inInventory && !inBank) {
				int bankGear = bankQtyForPresetRow(item, lowerCaseName, isFuzzy);
				int invGear = isFuzzy ? Rs2Inventory.itemQuantity((String) identifier) : Rs2Inventory.itemQuantity((int) identifier);
				logSetup(Level.WARN,
						"missing gear %s | bank=%d | inv=%d | need=%d — pausing",
						item.getName(), bankGear, invGear, item.getQuantity());
				Microbot.pauseAllScripts.compareAndSet(false, true);
				continue;
			}

			if (inInventory) {
				if (isFuzzy) {
					Rs2Bank.wearItem((String) identifier);
				} else {
					Rs2Bank.wearItem((int) identifier);
				}
				sleepUntil(() -> Rs2Equipment.isWearing(item.getName()));
				continue;
			}

			if (item.getQuantity() > 1) {
				if (isFuzzy) {
					Rs2Bank.withdrawXAndEquip((String) identifier, item.getQuantity());
				} else {
					Rs2Bank.withdrawXAndEquip((int) identifier, item.getQuantity());
				}
			} else {
				if (isFuzzy) {
					Rs2Bank.withdrawAndEquip((String) identifier);
				} else {
					Rs2Bank.withdrawAndEquip((int) identifier);
				}
			}

			sleepUntil(() -> Rs2Equipment.isWearing(item.getName()));
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
	 * Normalizes names like {@code Prayer potion(4)} so dose-only differences still match strict slots.
	 */
	private static String inventorySetupBaseItemName(String name) {
		if (name == null) {
			return "";
		}
		return name.replaceAll("(?i)\\s*\\(\\d\\)\\s*$", "").trim().toLowerCase();
	}

	/**
	 * True when the inventory item satisfies the setup row (fuzzy, exact id, or same base name with different dose id).
	 */
	private static boolean slotItemMatchesPreset(InventorySetupsItem setupItem, Rs2ItemModel invItem) {
		assert setupItem != null;
		assert invItem != null;
		if (setupItem.isFuzzy()) {
			return invItem.getName().toLowerCase().contains(setupItem.getName().toLowerCase());
		}
		if (invItem.getId() == setupItem.getId()) {
			return true;
		}
		String baseSetup = inventorySetupBaseItemName(setupItem.getName());
		String baseInv = inventorySetupBaseItemName(invItem.getName());
		return !baseSetup.isEmpty() && baseSetup.equals(baseInv);
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

			int groupSize = entry.getValue().size();
			int desiredSum = entry.getValue().stream().mapToInt(InventorySetupsItem::getQuantity).sum();
			int withdrawQuantity;
			boolean useStackQuantity = inventoryMatchUseStackQuantity(item, groupSize, desiredSum);

			if (groupSize == 1) {
				withdrawQuantity = item.getQuantity();
			} else {
				withdrawQuantity = groupSize;
			}

			for (InventorySetupsItem setupItem : entry.getValue()) {
				int expectedSlot = setupItem.getSlot();

				if (expectedSlot >= 0) {
					Rs2ItemModel invItem = Rs2Inventory.getItemInSlot(expectedSlot);

					boolean itemDoesntExist = invItem == null;
					boolean itemDoesntMatch = invItem != null && !slotItemMatchesPreset(setupItem, invItem);

					if (itemDoesntExist || itemDoesntMatch) {
						int inSlotQty = invItem != null ? invItem.getQuantity() : 0;
						logSetup(Level.WARN,
								"slot mismatch: want %s x%d in slot %d%s | in_slot_qty=%d (wrong or empty slot)",
								setupItem.getName(), setupItem.getQuantity(), expectedSlot,
								invItem != null ? " found " + invItem.getName() : " empty",
								inSlotQty);
						found = false;
						continue;
					}

					if (!inventorySlotQuantityMatchesPreset(invItem.getQuantity(), setupItem)) {
						logSetup(Level.WARN, "wrong qty slot %d %s | inv=%d | need=%d | stack=%s",
								expectedSlot, setupItem.getName(), invItem.getQuantity(), setupItem.getQuantity(),
								stackCompareOf(setupItem));
						found = false;
					}
				} else {
					if (!unslottedInventorySatisfiesPreset(setupItem, withdrawQuantity, useStackQuantity)) {
						int invHave = useStackQuantity
								? Rs2Inventory.itemQuantity(setupItem.getName())
								: Rs2Inventory.count(setupItem.getName(), false);
						logSetup(Level.WARN, "missing item: %s | inv=%d | need=%d (use_stack_qty=%b stack=%s)",
								setupItem.getName(), invHave, withdrawQuantity, useStackQuantity,
								stackCompareOf(setupItem));
						found = false;
					}
				}
			}
		}

		if (inventorySetup.getRune_pouch() != null) {
			// TODO: allow each item's is fuzzy to contains(runes, isFuzzy), to allow combination rune matching
			// This creates a hash-map of the 20% of the required runes in the setup
			Map<Runes, Integer> requiredRunes = inventorySetup.getRune_pouch().stream()
				.filter(item -> item.getId() != -1 && item.getQuantity() > 0)
				.map(item -> {
					Runes rune = Runes.byItemId(item.getId());
					if (rune == null) return null;

					int originalQty = item.getQuantity();
					int minQty = Math.max(1, (int) Math.ceil(originalQty * 0.2));
					return Map.entry(rune, minQty);
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toMap(
					Map.Entry::getKey,
					Map.Entry::getValue,
					Integer::sum
				));

			if (!Rs2RunePouch.contains(requiredRunes, false)) {
				logSetup(Level.WARN, "rune pouch contents do not match expected setup");
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
        if (inventorySetup ==null || inventorySetup.getEquipment() == null) {
            return false;
        }
        for (InventorySetupsItem inventorySetupsItem : inventorySetup.getEquipment()) {
            if (InventorySetupsItem.itemIsDummy(inventorySetupsItem)) continue;

			boolean exact = !inventorySetupsItem.isFuzzy() && !InventorySetupsItem.isBarrowsItem(inventorySetupsItem.getName().toLowerCase());

			if (!Rs2Equipment.isWearing(inventorySetupsItem.getName(), exact)) {
				int needEq = Math.max(1, inventorySetupsItem.getQuantity());
				boolean fuzzyEq = inventorySetupsItem.isFuzzy();
				String lowEq = inventorySetupsItem.getName().toLowerCase(Locale.ROOT);
				int invEq = fuzzyEq ? Rs2Inventory.itemQuantity(lowEq) : Rs2Inventory.itemQuantity(inventorySetupsItem.getId());
				if (Rs2Bank.isOpen()) {
					int bankEq = bankQtyForPresetRow(inventorySetupsItem, lowEq, fuzzyEq);
					logSetup(Level.WARN, "missing equipment: %s | bank=%d | inv=%d | need=%d",
							inventorySetupsItem.getName(), bankEq, invEq, needEq);
				} else {
					logSetup(Level.WARN, "missing equipment: %s | inv=%d | need=%d (bank closed)",
							inventorySetupsItem.getName(), invEq, needEq);
				}
				return false;
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
        return inventorySetup.getInventory().stream().filter(x ->  x != null && x.getId() != -1).collect(Collectors.toList());
    }

    /**
     * Retrieves the list of equipment items from the setup, excluding any dummy items (ID == -1).
     *
     * @return A list of valid equipment items.
     */
    public List<InventorySetupsItem> getEquipmentItems() {
        return inventorySetup.getEquipment().stream().filter(x -> x != null && x.getId() != -1).collect(Collectors.toList());
    }

    /**
     * Retrieves the list of additional items from the setup, excluding any dummy items (ID == -1).
     *
     * @return A list of valid additional filtered items.
     */
    public List<InventorySetupsItem> getAdditionalItems() {
		if (inventorySetup.getAdditionalFilteredItems() == null) {
			return Collections.emptyList();
		}
        return inventorySetup.getAdditionalFilteredItems().values().stream()
				.filter(Objects::nonNull)
				.filter(x -> x.getId() != -1)
				.collect(Collectors.toList());
    }

    /**
     * Names that should not be deposited (exact vs fuzzy), derived from inventory, equipment, additional items, and rune pouch.
     * For automation, prefer {@link Rs2Bank#depositAllExcept(Set, Map)} with ids from non-fuzzy rows plus linked noted/unnoted ids.
     *
     * @return map suitable for {@link Rs2Bank#depositAllExcept(Map)}
     */
    public Map<String, Boolean> itemsToNotDeposit() {
        return new LinkedHashMap<>(computeSetupFuzzyKeepNames());
    }

    /**
     * Checks if the current spellbook matches the one defined in the inventory setup.
     *
     * @return true if the current spellbook matches the setup, false otherwise.
     */
    public boolean hasSpellBook() {
		int setupBook = inventorySetup.getSpellBook();
		if (setupBook == 4) {
			return true;
		}
        return setupBook == Microbot.getVarbitValue(VarbitID.SPELLBOOK);
    }

	/**
	 * Sorts inventory items to match a predefined list of {@link InventorySetupsItem} objects.
	 * <p>
	 * For each item in the setup list, this method attempts to ensure that the item is located
	 * in its designated slot within the player's inventory. If the item is already in the correct
	 * slot (based on ID or fuzzy name match), it is skipped. Otherwise, the item is searched for
	 * elsewhere in the inventory and moved to its intended slot if found.
	 * <p>
	 * Fuzzy matching allows partial name matching instead of strict ID matching.
	 *
	 * <p><b>Behavior Notes:</b></p>
	 * <ul>
	 *   <li>Items already in the correct slots are not moved.</li>
	 *   <li>If {@code isMainSchedulerCancelled()} returns true during execution, sorting is aborted early.</li>
	 *   <li>After a successful move, the method waits for inventory changes to take effect.</li>
	 * </ul>
	 *
	 * @param setupItems the desired inventory setup to match; ignored if null or empty
	 */
	private void sortInventoryItems(List<InventorySetupsItem> setupItems) {
		if (setupItems == null || setupItems.isEmpty()) return;

		for (InventorySetupsItem setupItem : setupItems) {
			if (isMainSchedulerCancelled()) break;

			Set<Integer> matchingSlots = setupItems.stream()
				.filter(item -> {
					Rs2ItemModel invItem = Rs2Inventory.getItemInSlot(item.getSlot());
					return invItem != null && (
						item.isFuzzy()
							? invItem.getName().toLowerCase().contains(item.getName().toLowerCase())
							: invItem.getId() == item.getId()
					);
				})
				.map(InventorySetupsItem::getSlot)
				.collect(Collectors.toSet());

			int targetSlot = setupItem.getSlot();

			if (matchingSlots.contains(targetSlot)) {
				continue;
			}

			Predicate<Rs2ItemModel> matchPredicate = invItem -> {
				if (matchingSlots.contains(invItem.getSlot())) {
					return false;
				}

				return setupItem.isFuzzy()
					? invItem.getName().toLowerCase().contains(setupItem.getName().toLowerCase())
					: invItem.getId() == setupItem.getId();
			};

			Rs2ItemModel itemToMove = Rs2Inventory.get(matchPredicate);

			if (itemToMove != null) {
				int sourceSlot = itemToMove.getSlot();
				logSetup(Level.DEBUG, "moving %s from slot %d to slot %d", itemToMove.getName(), sourceSlot, targetSlot);

				if (Rs2Inventory.moveItemToSlot(itemToMove, targetSlot)) {
					Rs2Inventory.waitForInventoryChanges(2000);
				}
			} else {
				logSetup(Level.DEBUG, "no inv item for %s to place in slot %d", setupItem.getName(), targetSlot);
			}
		}

		logSetup(Level.DEBUG, "inventory sorting complete");
	}

    /**
     * Locks all inventory slots marked as “locked” in the given setup.
     *
     * Iterates through the setup’s list of items, collects the indices (slots) of those flagged locked,
     * and invokes lockAllBySlot on that array of slot indices. Returns true if any slots were processed
     * (i.e., there were locked slots to act on), false if none were found.
     *
     * Preconditions:
     * - The InventorySetup must be populated, and InventorySetup.getSetupItems(setup) returns a non-null list.
     * - Each InventorySetupsItem.getSlot() is assumed to correspond to an inventory slot index if used elsewhere.
     *
     * Postconditions:
     * - lockAllBySlot(...) is called with the locked slot indices; its result is returned.
     * - If no locked items exist, the method returns false without side effects.
     *
     * @param setup the InventorySetup whose locked items’ slots should be toggled/locked
     */
    private void lockLockedItemsFromSetup(InventorySetup setup) {
        List<InventorySetupsItem> setupItems = InventorySetup.getSetupItems(setup);
        List<Integer> lockedSlots = IntStream.range(0, setupItems.size())
                .filter(i -> {
                    InventorySetupsItem item = setupItems.get(i);
                    return item != null && item.isLocked();
                })
                .boxed()
                .collect(Collectors.toList());
        if (lockedSlots.isEmpty()) {
            return;
        }
		Rs2Bank.lockAllBySlot(lockedSlots.stream().mapToInt(Integer::intValue).toArray());
    }

	/**
	 * Prepares the player for combat by drinking boosting potions and optionally healing.
	 *
	 * This method handles the following:
	 * - Temporarily storing inventory items if the inventory is full
	 * - Drinking a "chug barrel" device if configured
	 * - Drinking boosting potions from the inventory or bank
	 * - Depositing empty potion vials
	 * - Healing with food if HP is not full
	 * - Optionally boosting HP using anglerfish
	 * - Restoring any temporarily deposited items after pre-potting
	 *
	 * @param potionsToPrePot a list of potion name substrings (e.g. "ranging", "magic") to match and consume
	 * @return true if pre-potting succeeded or completed, false if setup was invalid or cancelled
	 */
	public boolean prePot(List<String> potionsToPrePot)
	{
		List<InventorySetupsItem> additionalItems = getAdditionalItems();

		if (additionalItems.isEmpty())
		{
			logSetup(Level.WARN, "no additional items to pre-pot");
			return false;
		}

		List<Rs2ItemModel> storedItems = new ArrayList<>();

		if (Rs2Inventory.isFull())
		{
			logSetup(Level.INFO, "inventory full — temporarily storing items for space");
			Rs2Inventory.items()
				.sorted(Comparator.comparing(Rs2ItemModel::isStackable))
				.limit(3)
				.forEach(item -> {
					storedItems.add(item);
					Rs2Bank.depositOne(item.getId());
					Rs2Inventory.waitForInventoryChanges(1800);
				});
		}

		boolean useChugBarrel = additionalItems.stream().anyMatch(item -> item.getId() == ItemID.MM_PREPOT_DEVICE);
		if (useChugBarrel && !handleChugBarrel())
		{
			return false;
		}
		else
		{
			List<String> setupPotionNames = additionalItems.stream()
				.map(InventorySetupsItem::getName)
				.filter(Objects::nonNull)
				.map(String::toLowerCase)
				.collect(Collectors.toList());

			List<String> validPotionsToPrePot = potionsToPrePot.stream()
				.filter(pot -> setupPotionNames.stream().anyMatch(setupName -> setupName.equalsIgnoreCase(pot.toLowerCase())))
				.collect(Collectors.toList());

			findBoostingPotions(validPotionsToPrePot).stream().forEachOrdered(potion -> {
				if (isMainSchedulerCancelled() || isPotionEffectActive(potion.getName().toLowerCase()))
				{
					return;
				}

				boolean fromInventory = Rs2Inventory.hasItem(potion.getId());
				if (!fromInventory)
				{
					Rs2Bank.withdrawOne(potion.getName());
					Rs2Inventory.waitForInventoryChanges(1800);
				}

				Rs2Inventory.interact(potion.getId(), "drink");
				Rs2Random.wait(1200, 1800); // added pot delay

				if (!fromInventory)
				{
					Matcher matcher = Pattern.compile("\\((\\d)\\)").matcher(potion.getName());
					int resultingDose = matcher.find() ? Integer.parseInt(matcher.group(1)) - 1 : -1;

					if (resultingDose > 0)
					{
						String resultingName = potion.getName().replaceAll("\\(\\d\\)", "(" + resultingDose + ")");
						Rs2ItemModel resultingItem = Rs2Inventory.items()
							.filter(item -> item.getName().equalsIgnoreCase(resultingName))
							.findFirst()
							.orElse(null);

						if (resultingItem != null)
						{
							Rs2Bank.depositOne(resultingItem.getId());
							Rs2Inventory.waitForInventoryChanges(1800);
						}
					}
					else if (Rs2Inventory.hasItem(ItemID.VIAL_EMPTY))
					{
						Rs2Bank.depositAll(ItemID.VIAL_EMPTY);
						Rs2Inventory.waitForInventoryChanges(1800);
					}
				}
			});
		}

		if (Rs2Player.getHealthPercentage() < 100)
		{
			handleHealing(additionalItems);
		}

		boolean isPlayerHealthBoosted = Rs2Player.getHealthPercentage() > 100;
		boolean shouldUseAnglerfish = additionalItems.stream()
			.anyMatch(item -> item.getId() == ItemID.ANGLERFISH);

		if (!isPlayerHealthBoosted && shouldUseAnglerfish)
		{
			Rs2ItemModel anglerFishItem = Rs2Bank.getBankItem(ItemID.ANGLERFISH);
			if (anglerFishItem != null)
			{
				Rs2Bank.withdrawOne(anglerFishItem.getId());
				Rs2Inventory.waitForInventoryChanges(1800);
				Rs2Inventory.interact(anglerFishItem.getId(), "eat");
				Rs2Inventory.waitForInventoryChanges(1800);
			}
		}

		if (!storedItems.isEmpty())
		{
			logSetup(Level.INFO, "restoring temporarily stored items");

			for (Rs2ItemModel storedItem : storedItems)
			{
				if (isMainSchedulerCancelled())
				{
					break;
				}

				if (Rs2Inventory.isFull())
				{
					logSetup(Level.WARN, "inventory full — cannot restore all stored items");
					return false;
				}

				if (storedItem.isStackable() && storedItem.getQuantity() > 1)
				{
					Rs2Bank.withdrawX(storedItem.getId(), storedItem.getQuantity());
				}
				else
				{
					Rs2Bank.withdrawItem(storedItem.getId());
				}
				Rs2Inventory.waitForInventoryChanges(1800);
			}
		}

		return true;
	}

	/**
	 * Handles withdrawing and using the "chugging barrel" pre-pot device.
	 *
	 * The device is withdrawn from the bank, consumed via interaction, and then deposited back.
	 *
	 * @return true if the chug barrel was used successfully, false if the item was not available in the bank
	 */
	private boolean handleChugBarrel() {
		if (!Rs2Bank.hasItem(ItemID.MM_PREPOT_DEVICE)) {
			logSetup(Level.WARN, "chug barrel in setup but not in bank");
			return false;
		}
		Rs2Bank.withdrawItem(ItemID.MM_PREPOT_DEVICE);
		Rs2Inventory.waitForInventoryChanges(1800);
		Rs2Inventory.interact(ItemID.MM_PREPOT_DEVICE, "drink");
		Rs2Random.wait(1200, 1800); // added pot delay
		Rs2Bank.depositOne(ItemID.MM_PREPOT_DEVICE);
		Rs2Inventory.waitForInventoryChanges(1800);
		return true;
	}

	/**
	 * Finds boosting potions from the inventory or bank that match the given list of substrings.
	 *
	 * The result includes only one variant per potion ID (e.g., avoids duplicates like "(1)", "(2)", etc.),
	 * and is sorted to prioritize potions with higher doses if pulled from the bank.
	 *
	 * @param potionsToPrePot list of lowercase substrings to match potion names
	 * @return a list of unique matching potions from inventory or bank
	 */
	private List<Rs2ItemModel> findBoostingPotions(List<String> potionsToPrePot) {
		List<Rs2ItemModel> potions = Rs2Bank.bankItems().stream()
			.filter(item -> item.getName() != null &&
				potionsToPrePot.stream().anyMatch(name -> item.getName().toLowerCase().contains(name)))
			.collect(Collectors.toList());

		if (!potions.isEmpty()) {
			potions.sort(Comparator.comparingInt(item -> {
				String name = item.getName().toLowerCase();
				Matcher matcher = Pattern.compile("\\((\\d)\\)").matcher(name);
				return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
			}));
		}

		return potions;
	}

	/**
	 * Checks whether the effect of a potion is already active.
	 *
	 * This prevents redundant consumption of potions with effects that are still active,
	 * such as ranging, magic, divine potions, stamina, and prayer regeneration.
	 *
	 * @param potionName the lowercase name of the potion to check
	 * @return true if the effect of the potion is currently active, false otherwise
	 */
	private boolean isPotionEffectActive(String potionName) {
		boolean isRangedPotionType = Rs2Potion.getRangePotionsVariants().stream()
			.map(String::toLowerCase)
			.anyMatch(potionName::contains);
		if (isRangedPotionType && (Rs2Player.hasRangingPotionActive(3) || Rs2Player.hasDivineRangedActive())) {
			return true;
		}

		boolean isMagicPotionType = Rs2Potion.getMagicPotionsVariants().stream()
			.map(String::toLowerCase)
			.anyMatch(potionName::contains);
		if (isMagicPotionType && (Rs2Player.hasMagicActive(3) || Rs2Player.hasDivineMagicActive())) {
			return true;
		}

		boolean isCombatPotionType = Rs2Potion.getCombatPotionsVariants().stream()
			.map(String::toLowerCase)
			.anyMatch(potionName::contains);

		// TODO: Missing implementation for normal combat potions or super combats
		if (isCombatPotionType && Rs2Player.hasDivineCombatActive()) {
			return true;
		}

		boolean isPrayerRegenPotionType = potionName.contains(Rs2Potion.getPrayerRegenerationPotion().toLowerCase());
		if (isPrayerRegenPotionType && Rs2Player.hasPrayerRegenerationActive()) {
			return true;
		}

		boolean isStaminaPotionType = potionName.contains(Rs2Potion.getStaminaPotion().toLowerCase());
		if (isStaminaPotionType && Rs2Player.hasStaminaActive()) {
			return true;
		}

		return false;
	}

	/**
	 * Heals the player using food found in the bank, based on the items defined in the inventory setup.
	 *
	 * If no matching food is found in the setup, the method will select the highest-healing food available in the bank.
	 * Food types like karambwan or anglerfish are excluded here since they have special uses.
	 *
	 * @param additionalItems the list of items defined in the current inventory setup
	 */
	private void handleHealing(List<InventorySetupsItem> additionalItems) {
		Set<String> excluded = Set.of("karambwan", "anglerfish");

		Optional<Rs2Food> healingFoodFromSetup = additionalItems.stream()
			.map(InventorySetupsItem::getName)
			.filter(Objects::nonNull)
			.map(String::toLowerCase)
			.flatMap(name -> Arrays.stream(Rs2Food.values())
				.filter(food -> {
					String foodName = food.getName().toLowerCase();
					return !excluded.contains(foodName) && name.contains(foodName);
				}))
			.findFirst();

		Rs2ItemModel healingFood = healingFoodFromSetup
			.flatMap(food -> Rs2Bank.bankItems().stream()
				.filter(item -> item.getId() == food.getId())
				.findFirst())
			.orElseGet(() ->
				Rs2Bank.bankItems().stream()
					.filter(item -> Arrays.stream(Rs2Food.values())
						.anyMatch(food -> food.getId() == item.getId()))
					.max(Comparator.comparingInt(item ->
						Arrays.stream(Rs2Food.values())
							.filter(food -> food.getId() == item.getId())
							.findFirst()
							.map(Rs2Food::getHeal)
							.orElse(0)))
					.orElse(null)
			);

		if (healingFood == null) {
			logSetup(Level.WARN, "no healing food found in bank");
			return;
		}

		Rs2Bank.withdrawOne(healingFood.getId());
		Rs2Inventory.waitForInventoryChanges(1800);
		Rs2Inventory.interact(healingFood.getId(), "eat");
		Rs2Random.wait(1200, 1800); // added pot delay
	}

	/**
	 * Calls {@link #prePot(List)} using a standard set of boosting potions.
	 *
	 * The potion types included are:
	 * - Ranging potions
	 * - Magic potions
	 * - Combat potions
	 * - Prayer regeneration potion
	 * - Stamina potion
	 *
	 * @return true if pre-potting succeeded, false otherwise
	 */
	public boolean prePot()
	{
		List<String> boostingPotionNames =
			Stream.of(
				new ArrayList<String>() {{
					addAll(Rs2Potion.getRangePotionsVariants());
					Collections.reverse(this);
				}},
				new ArrayList<String>() {{
					addAll(Rs2Potion.getMagicPotionsVariants());
					Collections.reverse(this);
				}},
				new ArrayList<String>() {{
					addAll(Rs2Potion.getCombatPotionsVariants());
					Collections.reverse(this);
				}},
				List.of(Rs2Potion.getPrayerRegenerationPotion()),
				List.of(Rs2Potion.getStaminaPotion())
			)
			.flatMap(Collection::stream)
			.map(String::toLowerCase)
			.collect(Collectors.toList());
		return prePot(boostingPotionNames);
	}

	/**
	 * Detects items in the player's inventory that are not part of the inventory setup.
	 * Optionally excludes teleportation items from detection.
	 * 
	 * @param excludeTeleportItems Whether to exclude teleportation items from detection
	 * @return List of items in inventory that are not in the setup
	 */
	public List<Rs2ItemModel> getItemsNotInInventorySetup(boolean excludeTeleportItems) {
		if (inventorySetup == null || inventorySetup.getInventory() == null) {
			return Rs2Inventory.all(); // Return all items if no setup defined
		}

		// Get all setup item IDs and names for comparison
		Set<Integer> setupItemIds = inventorySetup.getInventory().stream()
			.filter(item -> !InventorySetupsItem.itemIsDummy(item))
			.map(InventorySetupsItem::getId)
			.collect(Collectors.toSet());

		Set<String> setupItemNames = inventorySetup.getInventory().stream()
			.filter(item -> !InventorySetupsItem.itemIsDummy(item) && item.isFuzzy())
			.map(item -> item.getName().toLowerCase())
			.collect(Collectors.toSet());

		return Rs2Inventory.all().stream()
			.filter(item -> {
				// Check if item is excluded due to teleport filter
				if (excludeTeleportItems && Rs2Walker.isTeleportItem(item.getId())) {
					return false;
				}

				// Check if item matches any setup item by ID
				if (setupItemIds.contains(item.getId())) {
					return false;
				}

				// Check if item matches any fuzzy setup item by name
				String itemName = item.getName().toLowerCase();
				for (String setupName : setupItemNames) {
					if (itemName.contains(setupName)) {
						return false;
					}
				}

				return true; // Item is not in setup
			})
			.collect(Collectors.toList());
	}

	/**
	 * Detects items in the player's equipment that are not part of the equipment setup.
	 * Optionally excludes teleportation items from detection.
	 * 
	 * @param excludeTeleportItems Whether to exclude teleportation items from detection
	 * @return List of equipment items that are not in the setup
	 */
	public List<Rs2ItemModel> getEquipmentNotInSetup(boolean excludeTeleportItems) {
		if (inventorySetup == null || inventorySetup.getEquipment() == null) {
			return Rs2Equipment.all().collect(Collectors.toList()); // Return all equipment if no setup defined
		}

		// Get all setup equipment IDs and names for comparison
		Set<Integer> setupEquipmentIds = inventorySetup.getEquipment().stream()
			.filter(item -> !InventorySetupsItem.itemIsDummy(item))
			.map(InventorySetupsItem::getId)
			.collect(Collectors.toSet());

		Set<String> setupEquipmentNames = inventorySetup.getEquipment().stream()
			.filter(item -> !InventorySetupsItem.itemIsDummy(item) && item.isFuzzy())
			.map(item -> item.getName().toLowerCase())
			.collect(Collectors.toSet());

		return Rs2Equipment.all()
			.filter(Objects::nonNull)
			.filter(item -> {
				// Check if item is excluded due to teleport filter
				if (excludeTeleportItems && Rs2Walker.isTeleportItem(item.getId())) {
					return false;
				}

				// Check if item matches any setup item by ID
				if (setupEquipmentIds.contains(item.getId())) {
					return false;
				}

				// Check if item matches any fuzzy setup item by name
				String itemName = item.getName().toLowerCase();
				for (String setupName : setupEquipmentNames) {
					if (itemName.contains(setupName)) {
						return false;
					}
				}

				return true; // Equipment is not in setup
			})
			.collect(Collectors.toList());
	}

	/**
	 * Banks all items in the inventory that are not part of the inventory setup.
	 * Optionally excludes teleportation items from banking.
	 * 
	 * @param excludeTeleportItems Whether to exclude teleportation items from banking
	 * @return true if banking was successful, false otherwise
	 */
	public boolean bankItemsNotInInventorySetup(boolean excludeTeleportItems) {
		List<Rs2ItemModel> itemsToBank = getItemsNotInInventorySetup(excludeTeleportItems);
		
		if (itemsToBank.isEmpty()) {
			logSetup(Level.DEBUG, "no inv items to bank — matches setup");
			return true;
		}

		// Ensure bank is open
		if (!Rs2Bank.isOpen()) {
			logSetup(Level.WARN, "bank must be open to deposit inv items not in setup");
			return false;
		}

		logSetup(Level.INFO, "banking %d inv items not in setup (teleport exclude=%s)",
				itemsToBank.size(), excludeTeleportItems);

		for (Rs2ItemModel item : itemsToBank) {
			if (isMainSchedulerCancelled()) {
				return false;
			}

			try {
				logSetup(Level.DEBUG, "deposit inv not in setup: %s", item.getName());
				Rs2Bank.depositAll(item.getId());
				sleep(Rs2Random.between(200, 400));
			} catch (Exception e) {
				logSetup(Level.WARN, "deposit inv failed %s: %s", item.getName(), e.getMessage());
			}
		}

		return true;
	}

	/**
	 * Banks all equipped items that are not part of the equipment setup.
	 * Optionally excludes teleportation items from banking.
	 * 
	 * @param excludeTeleportItems Whether to exclude teleportation items from banking
	 * @return true if banking was successful, false otherwise
	 */
	public boolean bankEquipmentNotInSetup(boolean excludeTeleportItems) {
		List<Rs2ItemModel> equipmentToBank = getEquipmentNotInSetup(excludeTeleportItems);
		
		if (equipmentToBank.isEmpty()) {
			logSetup(Level.DEBUG, "no equipment to bank — matches setup");
			return true;
		}

		// Ensure bank is open
		if (!Rs2Bank.isOpen()) {
			logSetup(Level.WARN, "bank must be open to deposit equipment not in setup");
			return false;
		}

		logSetup(Level.INFO, "banking %d equipment slots not in setup (teleport exclude=%s)",
				equipmentToBank.size(), excludeTeleportItems);

		for (Rs2ItemModel item : equipmentToBank) {
			if (isMainSchedulerCancelled()) {
				return false;
			}

			try {
				logSetup(Level.DEBUG, "deposit equipment not in setup: %s", item.getName());
				// Remove equipment first, then deposit
				Rs2Equipment.unEquip(item.getId());
				sleep(Rs2Random.between(300, 500));
				if (Rs2Inventory.hasItem(item.getId())) {
					Rs2Bank.depositAll(item.getId());
					sleep(Rs2Random.between(200, 400));
				}
			} catch (Exception e) {
				logSetup(Level.WARN, "deposit equipment failed %s: %s", item.getName(), e.getMessage());
			}
		}

		return true;
	}

	/**
	 * Banks all items (both inventory and equipment) that are not part of the setup.
	 * Convenience method that calls both banking functions.
	 * 
	 * @param excludeTeleportItems Whether to exclude teleportation items from banking
	 * @return true if both inventory and equipment banking was successful, false otherwise
	 */
	public boolean bankAllItemsNotInSetup(boolean excludeTeleportItems) {
		boolean inventorySuccess = bankItemsNotInInventorySetup(excludeTeleportItems);
		boolean equipmentSuccess = bankEquipmentNotInSetup(excludeTeleportItems);
		return inventorySuccess && equipmentSuccess;
	}

	/**
	 * Checks if there are any items in inventory or equipment that are not part of the setup.
	 * This is useful for validating if the current state matches the desired setup.
	 * 
	 * @param excludeTeleportItems Whether to exclude teleportation items from the check
	 * @return true if there are items not in the setup, false if everything matches the setup
	 */
	public boolean hasNotInventorySetup(boolean excludeTeleportItems) {
		List<Rs2ItemModel> inventoryNotInSetup = getItemsNotInInventorySetup(excludeTeleportItems);
		List<Rs2ItemModel> equipmentNotInSetup = getEquipmentNotInSetup(excludeTeleportItems);
		
		return !inventoryNotInSetup.isEmpty() || !equipmentNotInSetup.isEmpty();
	}

}
