package net.runelite.client.plugins.microbot.util.inventory;

import java.awt.Rectangle;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.MenuAction;
import net.runelite.api.Varbits;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility class to manage and interact with the player's Rune Pouch.
 * Includes methods for reading, loading, and depositing rune pouch contents.
 */
public class Rs2RunePouch
{
	private static final int NUM_SLOTS = 4;
	private static final int[] AMOUNT_VARBITS = {
		Varbits.RUNE_POUCH_AMOUNT1,
		Varbits.RUNE_POUCH_AMOUNT2,
		Varbits.RUNE_POUCH_AMOUNT3,
		Varbits.RUNE_POUCH_AMOUNT4
	};
	private static final int[] RUNE_VARBITS = {
		Varbits.RUNE_POUCH_RUNE1,
		Varbits.RUNE_POUCH_RUNE2,
		Varbits.RUNE_POUCH_RUNE3,
		Varbits.RUNE_POUCH_RUNE4
	};

	private static final int BANK_PARENT_ID = InterfaceID.BANKSIDE;
	private static final int RUNEPOUCH_ROOT_CHILD_ID = 19; // Validated
	private static final int RUNEPOUCH_CLOSE_CHILD_ID = 22; // Validated
	private static final int RUNEPOUCH_DEPOSIT_ALL_CHILD_ID = 20; // Validated
	private static final List<Integer> RUNEPOUCH_LOADOUT_WIDGETS = Arrays.asList(34, 38, 41, 44, 46, 48, 50, 52, 54, 56); // New Loadout Child IDs

	@Getter
	private static final List<PouchSlot> slots = new ArrayList<>();

	@Getter
	private static final Map<Integer, List<PouchSlot>> loadoutSlots = IntStream.range(0, NUM_SLOTS)
		.boxed()
		.collect(Collectors.toMap(
			i -> i,
			i -> new ArrayList<>()
		));

	/**
	 * Updates the internal state of the pouch from the current varbits.
	 */
	public static void fullUpdate()
	{
		if (!isEmpty())
		{
			slots.clear();
		}
		for (int i = 0; i < NUM_SLOTS; i++)
		{
			int rawRune = Microbot.getVarbitValue(RUNE_VARBITS[i]);
			int rawQuantity = Microbot.getVarbitValue(AMOUNT_VARBITS[i]);
			PouchSlot slot = new PouchSlot(Runes.byVarbitId(rawRune), rawQuantity);
			slots.add(slot);
		}
	}

	/**
	 * Updates the specific pouch slot when a varbit is changed.
	 *
	 * @param ev The varbit changed event.
	 */
    public static void onVarbitChanged(VarbitChanged ev) {
        assert Microbot.getClient().isClientThread();

        for (int i = 0; i < NUM_SLOTS; i++) {
            if (i >= slots.size()) {
                break; // avoid index out of bounds
            }

            PouchSlot slot = slots.get(i);
            if (slot == null) {
                continue; // skip null entries
            }

            int varbitId = ev.getVarbitId();
            int value = ev.getValue();

            if (varbitId == RUNE_VARBITS[i]) {
                slot.setRune(Runes.byVarbitId(value));
                break;
            }
            if (varbitId == AMOUNT_VARBITS[i]) {
                slot.setQuantity(value);
                break;
            }
        }
    }

	/**
	 * Handles reading rune pouch loadouts from the bank interface widgets.
	 *
	 * @param ev The widget loaded event.
	 */
	public static void onWidgetLoaded(WidgetLoaded ev)
	{

		assert Microbot.getClient().isClientThread();

		if (ev.getGroupId() != BANK_PARENT_ID)
		{
			return;
		}

		for (int loadOutIndex : RUNEPOUCH_LOADOUT_WIDGETS)
		{
			int index = RUNEPOUCH_LOADOUT_WIDGETS.indexOf(loadOutIndex);
			Widget rootLoadoutWidget = Microbot.getClient().getWidget(BANK_PARENT_ID, loadOutIndex);
			if (rootLoadoutWidget == null || rootLoadoutWidget.getDynamicChildren() == null)
			{
				continue;
			}

			List<PouchSlot> pouchSlots = new ArrayList<>();

			for (Widget childWidget : rootLoadoutWidget.getDynamicChildren())
			{
				if (childWidget.getIndex() == 0 || childWidget.getIndex() > 4)
				{
					continue;
				}

				int itemId = childWidget.getItemId();
				int quantity = childWidget.getItemQuantity();

				// Ignore X's or empty item slots
				Predicate<Integer> invalidItemId = i -> (i == 11526 || i == 6512 || i == 0);

				if (invalidItemId.test(itemId) || quantity <= 0)
				{
					continue;
				}

				PouchSlot slot = new PouchSlot(Runes.byItemId(itemId), quantity);
				pouchSlots.add(slot);
			}

			loadoutSlots.put(index, pouchSlots);
		}
	}

	/**
	 * Checks if the rune pouch is currently empty.
	 *
	 * @return True if all slots are empty, false otherwise.
	 */
	public static boolean isEmpty()
	{
		return slots.stream().noneMatch(s -> s.getRune() != null);
	}

	/**
	 * Checks if the rune pouch contains at least one of the specified rune item by item ID.
	 *
	 * @param itemId The item ID of the rune to check.
	 * @return {@code true} if the rune is present in the pouch, {@code false} otherwise.
	 */
	public static boolean contains(int itemId)
	{
		return contains(itemId, 1);
	}

	/**
	 * Checks if the rune pouch contains at least one of the specified rune by item ID,
	 * allowing optional combination rune matching.
	 *
	 * @param itemId                The item ID of the rune to check.
	 * @param allowCombinationRunes If {@code true}, allows combination runes (e.g. dust, steam) to count toward this rune.
	 * @return {@code true} if the rune is present (or satisfied via combination runes), {@code false} otherwise.
	 */
	public static boolean contains(int itemId, boolean allowCombinationRunes)
	{
		return contains(itemId, 1, allowCombinationRunes);
	}

	/**
	 * Checks if the rune pouch contains at least one of the specified rune.
	 *
	 * @param rune The rune to check.
	 * @return {@code true} if the rune is present, {@code false} otherwise.
	 */
	public static boolean contains(Runes rune)
	{
		return contains(rune, 1);
	}

	/**
	 * Checks if the rune pouch contains at least one of the specified rune,
	 * allowing optional combination rune matching.
	 *
	 * @param rune                  The rune to check.
	 * @param allowCombinationRunes If {@code true}, allows combination runes to satisfy the requirement.
	 * @return {@code true} if the rune is present or satisfied via a combination rune, {@code false} otherwise.
	 */
	public static boolean contains(Runes rune, boolean allowCombinationRunes)
	{
		return contains(rune, 1, allowCombinationRunes);
	}

	/**
	 * Checks if the rune pouch contains at least the specified quantity of the given rune item ID.
	 *
	 * @param itemId The item ID of the rune to check.
	 * @param amt    The minimum required quantity.
	 * @return {@code true} if the required quantity is present, {@code false} otherwise.
	 */
	public static boolean contains(int itemId, int amt)
	{
		return contains(Runes.byItemId(itemId), amt, false);
	}

	/**
	 * Checks if the rune pouch contains at least the specified quantity of the given rune.
	 *
	 * @param rune The rune to check.
	 * @param amt  The minimum required quantity.
	 * @return {@code true} if the required quantity is present, {@code false} otherwise.
	 */
	public static boolean contains(Runes rune, int amt)
	{
		return contains(rune, amt, false);
	}

	/**
	 * Checks if the rune pouch contains at least the specified quantity of the given rune item ID,
	 * allowing optional combination rune matching.
	 *
	 * @param itemId                The item ID of the rune to check.
	 * @param amt                   The required quantity.
	 * @param allowCombinationRunes If {@code true}, allows combination runes to fulfill the requirement.
	 * @return {@code true} if the requirement is fulfilled, {@code false} otherwise.
	 */
	public static boolean contains(int itemId, int amt, boolean allowCombinationRunes)
	{
		return contains(Runes.byItemId(itemId), amt, allowCombinationRunes);
	}

	/**
	 * Checks if the rune pouch contains at least the specified quantity of the given rune,
	 * allowing optional combination rune matching.
	 *
	 * @param rune                  The rune to check.
	 * @param amt                   The required quantity.
	 * @param allowCombinationRunes If {@code true}, allows combination runes to fulfill the requirement.
	 * @return {@code true} if the requirement is fulfilled, {@code false} otherwise.
	 */
	public static boolean contains(Runes rune, int amt, boolean allowCombinationRunes)
	{
		if (rune == null) return false;
		Map<Runes, Integer> required = Map.of(rune, amt);
		return contains(required, allowCombinationRunes);
	}

	/**
	 * Retrieves the current quantity of the specified rune item ID in the rune pouch.
	 *
	 * @param itemId The item ID of the rune.
	 * @return The quantity found, or {@code 0} if not present.
	 */
	public static int getQuantity(int itemId)
	{
		return getQuantity(Runes.byItemId(itemId));
	}

	/**
	 * Retrieves the current quantity of the specified rune in the rune pouch.
	 *
	 * @param rune The rune to query.
	 * @return The quantity found, or {@code 0} if not present.
	 */
	public static int getQuantity(Runes rune)
	{
		return slots.stream()
			.filter(s -> s.getRune() != null && Objects.equals(s.getRune(), rune))
			.mapToInt(PouchSlot::getQuantity)
			.findFirst()
			.orElse(0);
	}

	/**
	 * Checks if the rune pouch contains the required runes and their quantities.
	 *
	 * @param requiredRunes         A map of required runes and their corresponding quantities.
	 * @param allowCombinationRunes If {@code true}, allows combination runes to satisfy matching requirements.
	 * @return {@code true} if all rune requirements are fulfilled, {@code false} otherwise.
	 */
	public static boolean contains(Map<Runes, Integer> requiredRunes, boolean allowCombinationRunes)
	{
		Map<Runes, Integer> availableRunes = getRunes();

		return contains(requiredRunes, availableRunes, allowCombinationRunes ? COMBO_SUPPORT : STRICT_ONLY);
	}

	/**
	 * Internal method for matching required runes against available runes using a custom predicate.
	 * This allows support for both strict and combination-based matching.
	 *
	 * @param requiredRunes  A map of required runes and their quantities.
	 * @param availableRunes A mutable map of currently available runes in the pouch.
	 * @param satisfier      A predicate defining how a rune can be matched (strict or fuzzy).
	 * @return {@code true} if all requirements are met using the given satisfier, {@code false} otherwise.
	 */
	private static boolean contains(Map<Runes, Integer> requiredRunes, Map<Runes, Integer> availableRunes, BiPredicate<Runes, Map<Runes, Integer>> satisfier)
	{
		for (Map.Entry<Runes, Integer> req : requiredRunes.entrySet())
		{
			Runes required = req.getKey();
			int quantity = req.getValue();

			int remaining = quantity;
			while (remaining > 0 && satisfier.test(required, availableRunes))
			{
				remaining--;
			}
			if (remaining > 0)
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if the rune pouch contains all of the specified runes in the required quantities,
	 * using strict matching (no combination runes allowed).
	 *
	 * @param requiredRunes A map of {@link Runes} to their required quantities.
	 * @return {@code true} if all runes are present in the specified amounts, {@code false} otherwise.
	 */
	public static boolean contains(Map<Runes, Integer> requiredRunes)
	{
		return contains(requiredRunes, false);
	}

	/**
	 * Attempts to load the specified runes into the rune pouch using strict matching only.
	 * <p>
	 * First checks if the current rune pouch already contains all the required runes (no combination runes allowed).
	 * If not, it attempts to load the runes via a saved pouch loadout or by withdrawing them directly from the bank.
	 *
	 * @param requiredRunes A map of required {@link Runes} to their quantities.
	 * @return {@code true} if the pouch already contains or successfully loads all required runes, {@code false} otherwise.
	 */
	public static boolean load(Map<Runes, Integer> requiredRunes)
	{
		if (contains(requiredRunes, false))
		{
			return true;
		}

		return coreLoad(requiredRunes);
	}

	/**
	 * Attempts to load the specified runes into the rune pouch using fuzzy logic where applicable.
	 * <p>
	 * Each rune is evaluated individually: if the corresponding {@link InventorySetupsItem#isFuzzy()} flag is set,
	 * combination runes will be accepted. If all required runes are already satisfied, this method returns early.
	 * Otherwise, it falls back to loading from saved pouch loadouts or withdrawing runes from the bank.
	 *
	 * @param inventorySetupRunes A map of {@link Runes} to their corresponding {@link InventorySetupsItem} containing quantity and fuzzy status.
	 * @return {@code true} if the pouch already satisfies the requirements or is successfully loaded, {@code false} otherwise.
	 */
	public static boolean loadFromInventorySetup(Map<Runes, InventorySetupsItem> inventorySetupRunes)
	{
		boolean allPresent = inventorySetupRunes.entrySet().stream()
			.allMatch(entry -> {
				Runes rune = entry.getKey();
				InventorySetupsItem item = entry.getValue();
				int qty = item.getQuantity();
				boolean fuzzy = item.isFuzzy();

				return contains(rune, qty, fuzzy);
			});

		if (allPresent)
		{
			return true;
		}

		return coreLoad(inventorySetupRunes.entrySet().stream()
			.collect(Collectors.toMap(
				Map.Entry::getKey,
				e -> e.getValue().getQuantity()
			)));
	}

	/**
	 * Core logic for loading runes into the rune pouch from saved loadouts or directly from the bank.
	 * <p>
	 * First attempts to load a saved pouch configuration that matches the required runes exactly.
	 * If no saved loadout matches, all existing pouch contents are deposited and the required runes
	 * are withdrawn from the bank one-by-one. This method does not perform any fuzzy matching logic.
	 *
	 * @param requiredRunes A map of required {@link Runes} to their quantities.
	 * @return {@code true} if the rune pouch is successfully configured to contain the required runes, {@code false} otherwise.
	 */
	private static boolean coreLoad(Map<Runes, Integer> requiredRunes)
	{
		if (!Rs2Bank.isOpen() || !Rs2Inventory.hasRunePouch())
		{
			return false;
		}

		Rs2Inventory.interact(RunePouchType.getPouchIds(), "Configure");
		Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(BANK_PARENT_ID, RUNEPOUCH_ROOT_CHILD_ID));

		for (Map.Entry<Integer, List<PouchSlot>> entry : loadoutSlots.entrySet())
		{
			List<PouchSlot> loadout = entry.getValue();

			Map<Runes, Integer> loadoutMap = loadout.stream()
				.filter(slot -> slot.getRune() != null && slot.getQuantity() > 0)
				.collect(Collectors.toMap(
					PouchSlot::getRune,
					PouchSlot::getQuantity
				));

			if (loadoutMap.equals(requiredRunes))
			{
				final int widgetIndex = RUNEPOUCH_LOADOUT_WIDGETS.get(entry.getKey());
				Widget parentLoadoutWidget = Rs2Widget.getWidget(BANK_PARENT_ID, widgetIndex);
				if (parentLoadoutWidget == null || parentLoadoutWidget.getStaticChildren() == null)
				{
					Microbot.log("Failed to find loadout widget for index: " + widgetIndex, Level.WARNING);
					break;
				}
				Widget loadWidget = Rs2Widget.findWidget("Load", List.of(parentLoadoutWidget.getStaticChildren()));
				if (loadWidget == null)
				{
					Microbot.log("Failed to find 'Load' child widget in loadout index: " + widgetIndex, Level.WARNING);
					break;
				}
				Rectangle loadBounds = loadWidget.getBounds();
				NewMenuEntry menuEntry = new NewMenuEntry()
						.option("Load")
						.target("")
						.identifier(1)
						.type(MenuAction.CC_OP)
						.itemId(-1)
						.param1(loadWidget.getId())
						.forceLeftClick(false);
				Microbot.doInvoke(menuEntry, loadBounds != null && Rs2UiHelper.isRectangleWithinCanvas(loadBounds) ? loadBounds : Rs2UiHelper.getDefaultRectangle());
				Global.sleepUntil(() -> getRunes().entrySet().stream().allMatch(e -> requiredRunes.getOrDefault(e.getKey(), 0) <= e.getValue()));
				return closeRunePouch();
			}
		}

		if (!depositAll())
		{
			return false;
		}

		// Withdraw runes from the bank
		for (Map.Entry<Runes, Integer> entry : requiredRunes.entrySet()) {
			Runes rune = entry.getKey();
			int qty = entry.getValue();

			if (!Rs2Bank.withdrawX(rune.getItemId(), qty))
			{
				Microbot.log("Failed to withdraw rune: " + rune + " x" + qty, Level.WARNING);
				return false;
			}
			Global.sleepUntil(() -> contains(rune, qty));
		}

		Global.sleepUntil(() -> {
			Map<Runes, Integer> currentRunes = getRunes();
			return requiredRunes.entrySet().stream().allMatch(e -> currentRunes.getOrDefault(e.getKey(), 0) >= e.getValue());
		});

		return closeRunePouch();
	}

	/**
	 * Retrieves the current rune pouch contents.
	 *
	 * @return A map of rune item IDs to their quantities.
	 */
	public static Map<Runes, Integer> getRunes() {
		return slots.stream()
			.filter(s -> s.getRune() != null && s.getQuantity() > 0)
			.collect(Collectors.toMap(PouchSlot::getRune, PouchSlot::getQuantity, Integer::sum));
	}

	/**
	 * Deposits all runes from the pouch into the bank.
	 *
	 * @return True if all runes were successfully deposited.
	 */
	public static boolean depositAll()
	{
		if (isEmpty())
		{
			return true;
		}
		if (!Rs2Bank.isOpen() || !Rs2Inventory.hasRunePouch())
		{
			return false;
		}

		if (!isRunePouchOpen())
		{
			openRunePouch();
		}

		Rs2Widget.clickWidget(BANK_PARENT_ID, RUNEPOUCH_DEPOSIT_ALL_CHILD_ID);
		return Global.sleepUntil(Rs2RunePouch::isEmpty);
	}

	/**
	 * Checks if the rune pouch UI is currently open.
	 *
	 * @return True if open.
	 */
	public static boolean isRunePouchOpen()
	{
		return Rs2Widget.isWidgetVisible(BANK_PARENT_ID, RUNEPOUCH_ROOT_CHILD_ID);
	}

	/**
	 * Opens the rune pouch UI.
	 *
	 * @return True if the pouch was successfully opened.
	 */
	public static boolean openRunePouch()
	{
		if (isRunePouchOpen())
		{
			return true;
		}
		Rs2Inventory.interact(RunePouchType.getPouchIds(), "Configure");
		return Global.sleepUntil(Rs2RunePouch::isRunePouchOpen);
	}

	/**
	 * Closes the rune pouch UI.
	 *
	 * @return True if the pouch was successfully closed.
	 */
	public static boolean closeRunePouch()
	{
		if (!isRunePouchOpen())
		{
			return true;
		}
		Rs2Widget.clickWidget(BANK_PARENT_ID, RUNEPOUCH_CLOSE_CHILD_ID);
		return Global.sleepUntil(() -> !isRunePouchOpen());
	}

	/**
	 * BiPredicate that allows fuzzy matching using combination runes.
	 * <p>
	 * For a required rune, this predicate checks the available rune map to see if
	 * any runes can satisfy the requirement via {@code getBaseRunes()}, such as
	 * dust runes for both air and earth. If found, it decrements the matching
	 * combination rune count and returns {@code true}.
	 */
	private static final BiPredicate<Runes, Map<Runes, Integer>> COMBO_SUPPORT = (required, availableRunes) -> {
		for (Map.Entry<Runes, Integer> entry : availableRunes.entrySet())
		{
			final int availableQuantity = entry.getValue();
			if (availableQuantity <= 0) continue;

			final Runes available = entry.getKey();
			if (available != required && available.providesRune(required)) {
				availableRunes.put(available, availableQuantity - 1);
				return true;
			}
		}
		return false;
	};


	/**
	 * BiPredicate that enforces strict rune matching.
	 * <p>
	 * Only considers exact rune type matches from the available rune map.
	 * If the required rune is present in sufficient quantity, decrements it
	 * and returns {@code true}. Otherwise, returns {@code false}.
	 */
	private static final BiPredicate<Runes, Map<Runes, Integer>> STRICT_ONLY = (required, availableRunes) -> {
		int count = availableRunes.getOrDefault(required, 0);
		if (count > 0)
		{
			availableRunes.put(required, count - 1);
			return true;
		}
		return false;
	};

	/**
	 * Represents a slot inside the rune pouch, containing a rune and its quantity.
	 */
	@Getter
	@Setter
	private static class PouchSlot
	{
		PouchSlot(Runes rune, int quantity)
		{
			this.rune = rune;
			this.quantity = quantity;
		}

		private Runes rune;
		private int quantity;
	}
}