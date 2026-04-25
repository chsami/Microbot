package net.runelite.client.plugins.microbot.util.huntkit;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Huntsman's kit UI + withdraw helpers (bank-like {@code CC_OP} flow), with Rs2Bank/Rs2Inventory-style
 * event cache for container {@link InventoryID#HUNTSMANS_KIT} (855).
 * <p>
 * With the Microbot parent plugin enabled, {@link net.runelite.client.plugins.microbot.MicrobotPlugin#onItemContainerChanged}
 * forwards {@link ItemContainerChanged} for {@link InventoryID#HUNTSMANS_KIT} here (same as bank/inv). Do not duplicate
 * that subscription in Hub plugins. Call {@link #updateLocalKit(ItemContainerChanged)} yourself only if outside that path.
 * <p>
 * With the kit UI open, inventory shows {@code Deposit-1} / {@code Deposit-5} / {@code Deposit-X} / {@code Deposit-All}
 * on items; {@code deposit*} uses {@link #KIT_SIDE_INVENTORY_PACKED_ID} and resolves op indices from slot widgets.
 * <p>
 * Name matching: {@link #contains(String...)} is shorthand for {@link #contains(boolean, String...)}{@code (true, names)}
 * (each name is an exact, case-insensitive match to the whole item name). Use
 * {@code contains(false, "needle", ...)} for substring (fuzzy) matching on kit item display names.
 * <p>
 * {@link #stream()} reads the in-memory cache; when it is empty and the client is logged in, a client-thread refresh
 * is scheduled (same eventual consistency pattern as {@code Rs2Inventory.items()}).
 */
@Slf4j
public final class Rs2HuntKit
{
	private Rs2HuntKit()
	{
	}

	/** Packed interface root ({@link InterfaceID.HuntsmansKit#UNIVERSE}). */
	public static final int INTERFACE_ROOT_PACKED_ID = InterfaceID.HuntsmansKit.UNIVERSE;
	/** Scrollable item grid ({@link InterfaceID.HuntsmansKit#ITEMS}) — {@code param1} for withdraw {@code CC_OP}. */
	public static final int ITEM_CONTAINER_PACKED_ID = InterfaceID.HuntsmansKit.ITEMS;
	/**
	 * Player inventory panel while kit UI is open — {@code param1} for {@code Deposit-*} {@code CC_OP}
	 * (same role as {@code ComponentID.BANK_INVENTORY_ITEM_CONTAINER} for bank).
	 */
	public static final int KIT_SIDE_INVENTORY_PACKED_ID = InterfaceID.HuntsmansKitSide.ITEMS;

	public static final int KIT_ITEM_CONTAINER_ID = InventoryID.HUNTSMANS_KIT;
	public static final int KIT_ITEM_ID = ItemID.HUNTSMANS_KIT;
	public static final String KIT_NAME = "Huntsman's kit";
	public static final int CAPACITY = 32;

	/** Kit grid columns (same as player inventory). */
	private static final int KIT_ITEMS_PER_ROW = 4;
	/**
	 * Vertical pitch for {@link #scrollKitToSlot} (slot row height + gap in px). Must match
	 * {@link InterfaceID.HuntsmansKit#ITEMS} row layout in-game; if scroll misses rows, re-measure here.
	 */
	private static final int KIT_ITEM_HEIGHT = 32;
	private static final int KIT_ITEM_Y_PADDING = 4;

	private static final int ENTER_AMOUNT_GROUP = 162;
	private static final int ENTER_AMOUNT_CHILD = 43;
	/** Upper bound on inner-loop iterations per intended withdraw-all in {@link #withdrawAllStackables(int)}. */
	private static final int WITHDRAW_ALL_STACKABLES_PASSES_PER_OP = 8;

	private static volatile List<Rs2ItemModel> kitItems = Collections.emptyList();

	private static int clampKitSlot(int slot)
	{
		return Math.max(0, Math.min(slot, CAPACITY - 1));
	}

	private static int clampInventorySlot(int slot)
	{
		int cap = Rs2Inventory.capacity();
		if (cap <= 1)
		{
			return 0;
		}
		return Math.max(0, Math.min(slot, cap - 1));
	}

	/**
	 * Rebuilds {@link #kitItems} from {@link Client#getItemContainer(int)} on the client thread.
	 * Used for {@link ItemContainerChanged} and for {@code invokeLater} paths so we never read a possibly
	 * reused event payload off the next tick.
	 */
	private static void rebuildKitFromCurrentClient()
	{
		Client client = Microbot.getClient();
		if (client == null || !client.isClientThread())
		{
			return;
		}
		ItemContainer container = client.getItemContainer(KIT_ITEM_CONTAINER_ID);
		if (container == null)
		{
			kitItems = Collections.emptyList();
			return;
		}
		Item[] items = container.getItems();
		if (items == null)
		{
			kitItems = Collections.emptyList();
			return;
		}
		List<Rs2ItemModel> built = new ArrayList<>();
		final int limit = Math.min(items.length, CAPACITY);
		for (int slot = 0; slot < limit; slot++)
		{
			Item item = items[slot];
			if (item == null || item.getId() <= 0)
			{
				continue;
			}
			ItemComposition composition = client.getItemDefinition(item.getId());
			if (composition == null || composition.getPlaceholderTemplateId() > 0)
			{
				continue;
			}
			built.add(new Rs2ItemModel(item, composition, slot));
		}
		kitItems = Collections.unmodifiableList(built);
	}

	public static void updateLocalKit(ItemContainerChanged event)
	{
		if (event == null || event.getContainerId() != KIT_ITEM_CONTAINER_ID)
		{
			return;
		}
		Client client = Microbot.getClient();
		if (client == null)
		{
			return;
		}
		if (!client.isClientThread())
		{
			Microbot.getClientThread().invokeLater(Rs2HuntKit::rebuildKitFromCurrentClient);
			return;
		}
		rebuildKitFromCurrentClient();
	}

	public static boolean isOpen()
	{
		return Rs2Widget.isWidgetVisible(INTERFACE_ROOT_PACKED_ID);
	}

	/**
	 * Non-blocking open request for the kit UI.
	 * <p>
	 * Safe to call from the client thread (e.g. dev-shell {@code GameTick} subscriptions): this method never waits.
	 * Use {@link #isOpen()} on subsequent ticks to observe when the interface becomes visible.
	 * <p>
	 * Return value semantics: {@code true} means the kit is already open <em>or</em> the "View" click was issued;
	 * {@code false} means the kit item is not present / not interactable.
	 */
	public static boolean requestOpenView()
	{
		if (isOpen())
		{
			return true;
		}
		if (!Rs2Inventory.hasItem(KIT_ITEM_ID) && !Rs2Inventory.hasItem(KIT_NAME))
		{
			return false;
		}
		return Rs2Inventory.interact(KIT_ITEM_ID, "View") || Rs2Inventory.interact(KIT_NAME, "View");
	}

	/**
	 * Opens the kit UI by clicking inventory "View" and waiting until it becomes visible.
	 * <p>
	 * Intended for script/executor threads. On the client thread, {@code sleepUntil} is a no-op to avoid blocking RuneLite,
	 * so this method may return {@code false} even if the click succeeded.
	 * For client-thread usage, call {@link #requestOpenView()} and poll {@link #isOpen()} across ticks.
	 */
	public static boolean openView()
	{
		if (isOpen())
		{
			return true;
		}
		if (!Rs2Inventory.hasItem(KIT_ITEM_ID) && !Rs2Inventory.hasItem(KIT_NAME))
		{
			return false;
		}
		boolean clicked = Rs2Inventory.interact(KIT_ITEM_ID, "View") || Rs2Inventory.interact(KIT_NAME, "View");
		if (!clicked)
		{
			return false;
		}
		return sleepUntil(Rs2HuntKit::isOpen, 3500);
	}

	/**
	 * Fills traps/supplies from inventory via kit item **Fill** (only valid when kit UI is closed — same as in-game).
	 */
	public static boolean fill()
	{
		if (isOpen())
		{
			return false;
		}
		return Rs2Inventory.interact(KIT_ITEM_ID, "Fill") || Rs2Inventory.interact(KIT_NAME, "Fill");
	}

	/**
	 * Empties the kit via inventory "Empty" (only valid when kit UI is closed — same as in-game).
	 */
	public static boolean emptyKit()
	{
		if (isOpen())
		{
			return false;
		}
		return Rs2Inventory.interact(KIT_ITEM_ID, "Empty") || Rs2Inventory.interact(KIT_NAME, "Empty");
	}

	/**
	 * Clicks kit UI **Fill** ({@link InterfaceID.HuntsmansKit#FILL}): right-click menu op if present, else left-click.
	 * Kit window must be open ({@link #isOpen()}).
	 */
	public static boolean fillWidget()
	{
		if (!isOpen())
		{
			return false;
		}
		if (invokeKitChromeAction(InterfaceID.HuntsmansKit.FILL, "Fill"))
		{
			return true;
		}
		Widget fill = Rs2Widget.getWidget(InterfaceID.HuntsmansKit.FILL);
		return fill != null && Rs2Widget.clickWidget(fill);
	}

	/**
	 * Runs kit UI **Empty**: normally from right-click on the Fill icon → Empty ({@link InterfaceID.HuntsmansKit#FILL});
	 * falls back to {@link InterfaceID.HuntsmansKit#EMPTY} if needed. Kit window must be open.
	 */
	public static boolean emptyWidget()
	{
		if (!isOpen())
		{
			return false;
		}
		if (invokeKitChromeAction(InterfaceID.HuntsmansKit.FILL, "Empty"))
		{
			return true;
		}
		if (invokeKitChromeAction(InterfaceID.HuntsmansKit.EMPTY, "Empty"))
		{
			return true;
		}
		Widget empty = Rs2Widget.getWidget(InterfaceID.HuntsmansKit.EMPTY);
		return empty != null && Rs2Widget.clickWidget(empty);
	}

	private static boolean invokeKitChromeAction(int packedWidgetId, String actionLabel)
	{
		if (actionLabel == null)
		{
			return false;
		}
		Widget w = Rs2Widget.getWidget(packedWidgetId);
		if (w == null || w.isHidden())
		{
			return false;
		}
		String[] actions = w.getActions();
		if (actions == null)
		{
			return false;
		}
		for (int i = 0; i < actions.length; i++)
		{
			if (actionLabel.equals(actions[i]))
			{
				// CC_OP identifiers are 1-based (match bank/menu entry behavior), even though Widget actions[] is 0-based.
				Rs2Widget.clickWidgetFast(w, -1, i + 1);
				return true;
			}
		}
		for (int i = 0; i < actions.length; i++)
		{
			if (actions[i] != null && actionLabel.equalsIgnoreCase(actions[i]))
			{
				Rs2Widget.clickWidgetFast(w, -1, i + 1);
				return true;
			}
		}
		return false;
	}

	public static boolean close()
	{
		if (!isOpen())
		{
			return true;
		}
		// Close action lives on the kit frame widget (no text, action = "Close").
		if (invokeKitChromeAction(InterfaceID.HuntsmansKit.FRAME, "Close"))
		{
			return sleepUntil(() -> !isOpen(), 3500);
		}
		Widget root = Rs2Widget.getWidget(INTERFACE_ROOT_PACKED_ID);
		if (root == null)
		{
			return false;
		}
		Widget close = Rs2Widget.findWidget("Close", Collections.singletonList(root), false);
		if (close == null)
		{
			close = Rs2Widget.findWidget("Close window", Collections.singletonList(root), false);
		}
		if (close == null)
		{
			close = Rs2Widget.findWidget("Exit", Collections.singletonList(root), false);
		}
		if (close == null)
		{
			return false;
		}
		Rs2Widget.clickWidget(close);
		return sleepUntil(() -> !isOpen(), 3500);
	}

	/**
	 * Alias for {@link #close()} to match the {@code openView()} naming used by scripts.
	 */
	public static boolean closeView()
	{
		return close();
	}

	public static ItemContainer kitItemContainer()
	{
		Client client = Microbot.getClient();
		if (client == null)
		{
			return null;
		}
		return client.getItemContainer(KIT_ITEM_CONTAINER_ID);
	}

	/**
	 * Current kit cache snapshot (unmodifiable). Do not hold across ticks for strict consistency — list reference
	 * is replaced when {@link #updateLocalKit} runs.
	 */
	public static List<Rs2ItemModel> kitItems()
	{
		return kitItems;
	}

	public static List<Widget> getItemWidgets()
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			Client c = Microbot.getClient();
			if (c == null)
			{
				return Collections.<Widget>emptyList();
			}
			Widget w = c.getWidget(ITEM_CONTAINER_PACKED_ID);
			if (w == null)
			{
				return Collections.<Widget>emptyList();
			}
			Widget[] dyn = w.getDynamicChildren();
			if (dyn == null || dyn.length == 0)
			{
				return Collections.<Widget>emptyList();
			}
			return Arrays.asList(dyn);
		}).orElse(Collections.<Widget>emptyList());
	}

	private static Widget findKitSlotWidget(List<Widget> children, int slot)
	{
		if (children == null || slot < 0 || slot >= CAPACITY)
		{
			return null;
		}
		for (Widget w : children)
		{
			if (w != null && w.getIndex() == slot)
			{
				return w;
			}
		}
		return null;
	}

	/**
	 * @param slot kit container index, {@code 0} to {@link #CAPACITY}{@code -1}; not clamped — invalid indices yield {@code null}.
	 */
	public static Widget getItemWidget(int slot)
	{
		return findKitSlotWidget(getItemWidgets(), slot);
	}

	public static Rectangle itemBounds(Rs2ItemModel rs2Item)
	{
		if (rs2Item == null)
		{
			return null;
		}
		final int slot = clampKitSlot(rs2Item.getSlot());
		Widget w = getItemWidget(slot);
		if (w == null || w.getBounds() == null)
		{
			return null;
		}
		return w.getBounds();
	}

	/**
	 * Stream over cached kit rows. When cache empty and logged in, schedules {@link #rebuildKitFromCurrentClient}
	 * on client thread (same race as {@code Rs2Inventory.items()}): same-tick reads may still see empty until next event.
	 */
	public static Stream<Rs2ItemModel> stream()
	{
		if (kitItems.isEmpty() && Microbot.isLoggedIn())
		{
			Microbot.getClientThread().runOnClientThreadOptional(() -> {
				rebuildKitFromCurrentClient();
				return null;
			});
		}
		return kitItems.stream();
	}

	public static Stream<Rs2ItemModel> items(Predicate<Rs2ItemModel> predicate)
	{
		if (predicate == null)
		{
			return Stream.empty();
		}
		return stream().filter(predicate);
	}

	public static List<Rs2ItemModel> all()
	{
		return stream().collect(Collectors.toList());
	}

	public static List<Rs2ItemModel> all(Predicate<Rs2ItemModel> filter)
	{
		return items(filter).collect(Collectors.toList());
	}

	public static int capacity()
	{
		return CAPACITY;
	}

	/**
	 * Free slots in kit (32 max). Cache holds at most one {@link Rs2ItemModel} per occupied container index.
	 */
	public static int emptySlotCount()
	{
		return CAPACITY - (int) stream().count();
	}

	public static Rs2ItemModel get(Predicate<Rs2ItemModel> predicate)
	{
		if (predicate == null)
		{
			return null;
		}
		return stream().filter(predicate).findFirst().orElse(null);
	}

	public static Rs2ItemModel findKitItem(int id)
	{
		return stream().filter(x -> x.getId() == id).findFirst().orElse(null);
	}

	public static Rs2ItemModel findKitItem(String name, boolean exact)
	{
		if (name == null || name.isEmpty())
		{
			return null;
		}
		String needle = name.toLowerCase();
		Stream<Rs2ItemModel> s = stream();
		if (exact)
		{
			return s.filter(x -> x.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
		}
		return s.filter(x -> {
					String n = x.getName();
					return n != null && n.toLowerCase().contains(needle);
				})
				.min(Comparator.comparingInt(x -> {
					String n = x.getName();
					return n == null ? Integer.MAX_VALUE : n.length();
				}))
				.orElse(null);
	}

	public static Rs2ItemModel findKitItem(String name)
	{
		return findKitItem(name, false);
	}

	public static Rs2ItemModel findKitItem(String name, boolean exact, int minQuantity)
	{
		Rs2ItemModel item = findKitItem(name, exact);
		if (item == null || item.getQuantity() < minQuantity)
		{
			return null;
		}
		return item;
	}

	public static boolean hasKitItem(int id)
	{
		return findKitItem(id) != null;
	}

	public static boolean hasKitItem(int id, int amount)
	{
		return count(id) >= amount;
	}

	public static boolean hasKitItem(String name)
	{
		return findKitItem(name, false) != null;
	}

	public static boolean hasKitItem(String name, boolean exact)
	{
		return findKitItem(name, exact) != null;
	}

	public static boolean hasKitItem(String name, int amount)
	{
		return hasKitItem(name, amount, false);
	}

	public static boolean hasKitItem(String name, int amount, boolean exact)
	{
		return findKitItem(name, exact, amount) != null;
	}

	public static boolean hasKitItem(int... ids)
	{
		if (ids == null)
		{
			return false;
		}
		return Arrays.stream(ids).allMatch(Rs2HuntKit::hasKitItem);
	}

	public static boolean hasKitItem(Collection<String> names)
	{
		if (names == null)
		{
			return false;
		}
		return names.stream().allMatch(Rs2HuntKit::hasKitItem);
	}

	public static int count(Predicate<Rs2ItemModel> predicate)
	{
		if (predicate == null)
		{
			return 0;
		}
		return (int) stream().filter(predicate).count();
	}

	public static int count(int id)
	{
		Rs2ItemModel item = findKitItem(id);
		return item == null ? 0 : item.getQuantity();
	}

	public static int count(String name, boolean exact)
	{
		Rs2ItemModel item = findKitItem(name, exact);
		return item == null ? 0 : item.getQuantity();
	}

	public static boolean contains(Predicate<Rs2ItemModel> predicate)
	{
		if (predicate == null)
		{
			return false;
		}
		return stream().anyMatch(predicate);
	}

	public static boolean contains(int... ids)
	{
		if (ids == null || ids.length == 0)
		{
			return false;
		}
		return contains(item -> Arrays.stream(ids).anyMatch(id -> id == item.getId()));
	}

	/**
	 * True if the kit holds an item whose <em>whole</em> display name equals one of {@code names} (case-insensitive).
	 * For substring matching, use {@link #contains(boolean, String...)}{@code (false, names)}.
	 */
	public static boolean contains(String... names)
	{
		if (names == null || names.length == 0)
		{
			return false;
		}
		return contains(true, names);
	}

	/**
	 * @param exact {@code true}: whole-name equality (case-insensitive) to any of {@code names};
	 *              {@code false}: each non-null needle must appear as a substring of the item name (case-insensitive).
	 */
	public static boolean contains(boolean exact, String... names)
	{
		if (names == null || names.length == 0)
		{
			return false;
		}
		if (exact)
		{
			return contains(item -> Arrays.stream(names)
					.filter(Objects::nonNull)
					.anyMatch(name -> name.equalsIgnoreCase(item.getName())));
		}
		return contains(item -> {
			String itemName = item.getName();
			if (itemName == null)
			{
				return false;
			}
			String lowerItem = itemName.toLowerCase();
			return Arrays.stream(names)
					.filter(Objects::nonNull)
					.map(String::toLowerCase)
					.anyMatch(lowerItem::contains);
		});
	}

	public static boolean contains(String name, boolean exact)
	{
		return contains(exact, name);
	}

	public static boolean containsAll(int... ids)
	{
		if (ids == null)
		{
			return false;
		}
		if (ids.length == 0)
		{
			return true;
		}
		Set<Integer> present = stream().map(Rs2ItemModel::getId).collect(Collectors.toSet());
		return Arrays.stream(ids).allMatch(present::contains);
	}

	public static boolean containsAll(String... names)
	{
		if (names == null)
		{
			return false;
		}
		if (names.length == 0)
		{
			return true;
		}
		Set<String> present = stream()
				.map(Rs2ItemModel::getName)
				.filter(Objects::nonNull)
				.map(String::toLowerCase)
				.collect(Collectors.toSet());
		return Arrays.stream(names)
				.allMatch(name -> name != null && present.contains(name.toLowerCase()));
	}

	public static boolean scrollKitToSlot(int slotId)
	{
		Client client = Microbot.getClient();
		if (client == null)
		{
			return false;
		}
		final int slot = clampKitSlot(slotId);
		int row = slot / KIT_ITEMS_PER_ROW;
		int scrollY = row * (KIT_ITEM_HEIGHT + KIT_ITEM_Y_PADDING);
		Widget w = Rs2Widget.getWidget(InterfaceID.HuntsmansKit.ITEMS);
		if (w == null)
		{
			return false;
		}
		int rowHeight = KIT_ITEM_HEIGHT + KIT_ITEM_Y_PADDING;
		int viewRows = Math.max(1, w.getHeight() / rowHeight);
		int minScrollY = scrollY - viewRows * rowHeight;
		if (minScrollY < 0)
		{
			minScrollY = 0;
		}
		int currentScrollY = w.getScrollY();
		if (currentScrollY >= minScrollY && currentScrollY <= scrollY)
		{
			return true;
		}
		final int target = minScrollY >= scrollY ? scrollY : Rs2Random.nextInt(minScrollY, scrollY, 0.5, true);
		final int scrollTarget = target;
		Microbot.getClientThread().invokeLater(() ->
				client.runScript(ScriptID.UPDATE_SCROLLBAR, InterfaceID.HuntsmansKit.SCROLLBAR, InterfaceID.HuntsmansKit.ITEMS, scrollTarget));
		w.setScrollY(scrollTarget);
		return true;
	}

	/**
	 * Invokes a kit-grid {@link MenuAction#CC_OP} for {@link #ITEM_CONTAINER_PACKED_ID} at {@code rs2Item}'s slot
	 * (slot is clamped; grid is scrolled into view first). {@code identifier} is the menu op index from the slot widget.
	 */
	public static void invokeMenu(int identifier, Rs2ItemModel rs2Item)
	{
		Objects.requireNonNull(rs2Item, "rs2Item");
		final int slotParam = clampKitSlot(rs2Item.getSlot());
		scrollKitToSlot(slotParam);
		Widget slotWidget = findKitSlotWidget(getItemWidgets(), slotParam);
		Rectangle bounds = slotWidget == null || slotWidget.getBounds() == null ? null : slotWidget.getBounds();
		Microbot.doInvoke(new NewMenuEntry()
						.param0(slotParam)
						.param1(ITEM_CONTAINER_PACKED_ID)
						.opcode(MenuAction.CC_OP.getId())
						.identifier(identifier)
						.itemId(rs2Item.getId())
						.target(rs2Item.getName()),
				bounds != null ? bounds : new Rectangle(1, 1));
	}

	private static int withdrawActionIndex(Rs2ItemModel item, String actionLabel)
	{
		if (item == null || actionLabel == null)
		{
			return -1;
		}
		final int slot = clampKitSlot(item.getSlot());
		Widget w = findKitSlotWidget(getItemWidgets(), slot);
		if (w == null)
		{
			return -1;
		}
		String[] actions = w.getActions();
		if (actions == null)
		{
			return -1;
		}
		for (int i = 0; i < actions.length; i++)
		{
			if (actionLabel.equals(actions[i]))
			{
				return i + 1;
			}
		}
		for (int i = 0; i < actions.length; i++)
		{
			if (actions[i] != null && actionLabel.equalsIgnoreCase(actions[i]))
			{
				return i + 1;
			}
		}
		return -1;
	}

	private static boolean inventoryHasAtLeast(String name, int amount, boolean exact)
	{
		Rs2ItemModel inv = Rs2Inventory.get(name, false, exact);
		return inv != null && Rs2Inventory.hasItemAmount(name, amount, inv.isStackable(), exact);
	}

	private static Widget getKitSideInventoryParent()
	{
		if (!isOpen())
		{
			return null;
		}
		return Rs2Widget.getWidget(KIT_SIDE_INVENTORY_PACKED_ID);
	}

	private static Widget getKitSideInventorySlotWidget(int slot)
	{
		Widget parent = getKitSideInventoryParent();
		if (parent == null)
		{
			return null;
		}
		Widget[] dyn = parent.getDynamicChildren();
		if (dyn == null)
		{
			return null;
		}
		for (Widget w : dyn)
		{
			if (w != null && w.getIndex() == slot)
			{
				return w;
			}
		}
		return null;
	}

	/** {@code inventorySlot} must already lie in the kit side-inventory index range (use {@link #clampInventorySlot}). */
	private static Rectangle depositSlotBounds(int inventorySlot)
	{
		Widget w = getKitSideInventorySlotWidget(inventorySlot);
		if (w == null || w.getBounds() == null)
		{
			return null;
		}
		return w.getBounds();
	}

	private static int depositActionIndex(Rs2ItemModel invItem, String actionLabel)
	{
		if (invItem == null || actionLabel == null)
		{
			return -1;
		}
		final int slot = clampInventorySlot(invItem.getSlot());
		Widget w = getKitSideInventorySlotWidget(slot);
		if (w == null)
		{
			return -1;
		}
		String[] actions = w.getActions();
		if (actions == null)
		{
			return -1;
		}
		for (int i = 0; i < actions.length; i++)
		{
			if (actionLabel.equals(actions[i]))
			{
				return i + 1;
			}
		}
		for (int i = 0; i < actions.length; i++)
		{
			if (actions[i] != null && actionLabel.equalsIgnoreCase(actions[i]))
			{
				return i + 1;
			}
		}
		return -1;
	}

	private static void invokeDepositMenu(int identifier, Rs2ItemModel invItem)
	{
		Objects.requireNonNull(invItem, "invItem");
		final int slotParam = clampInventorySlot(invItem.getSlot());
		Rectangle bounds = depositSlotBounds(slotParam);
		Microbot.doInvoke(new NewMenuEntry()
						.param0(slotParam)
						.param1(KIT_SIDE_INVENTORY_PACKED_ID)
						.opcode(MenuAction.CC_OP.getId())
						.identifier(identifier)
						.itemId(invItem.getId())
						.target(invItem.getName()),
				bounds != null ? bounds : new Rectangle(1, 1));
	}

	private static boolean depositOne(Rs2ItemModel rs2Item)
	{
		if (rs2Item == null || !isOpen())
		{
			return false;
		}
		if (!Rs2Inventory.hasItem(rs2Item.getId()))
		{
			return false;
		}
		int op = depositActionIndex(rs2Item, "Deposit-1");
		if (op < 0)
		{
			log.debug("Rs2HuntKit: no Deposit-1 for slot {} id {}", rs2Item.getSlot(), rs2Item.getId());
			return false;
		}
		invokeDepositMenu(op, rs2Item);
		return true;
	}

	public static boolean depositOne(int id)
	{
		return depositOne(Rs2Inventory.get(id));
	}

	public static boolean depositOne(String name, boolean exact)
	{
		return depositOne(Rs2Inventory.get(name, exact));
	}

	public static boolean depositOne(String name)
	{
		return depositOne(name, false);
	}

	private static boolean depositFive(Rs2ItemModel rs2Item)
	{
		if (rs2Item == null || !isOpen())
		{
			return false;
		}
		if (!Rs2Inventory.hasItem(rs2Item.getId()))
		{
			return false;
		}
		int op = depositActionIndex(rs2Item, "Deposit-5");
		if (op < 0)
		{
			log.debug("Rs2HuntKit: no Deposit-5 for slot {} id {}", rs2Item.getSlot(), rs2Item.getId());
			return false;
		}
		invokeDepositMenu(op, rs2Item);
		return true;
	}

	public static boolean depositFive(int id)
	{
		return depositFive(Rs2Inventory.get(id));
	}

	public static boolean depositFive(String name, boolean exact)
	{
		return depositFive(Rs2Inventory.get(name, exact));
	}

	public static boolean depositFive(String name)
	{
		return depositFive(name, false);
	}

	private static boolean depositX(Rs2ItemModel rs2Item, int amount)
	{
		if (rs2Item == null || !isOpen())
		{
			return false;
		}
		if (!Rs2Inventory.hasItem(rs2Item.getId()))
		{
			return false;
		}
		if (amount <= 0)
		{
			return true;
		}
		int op = depositActionIndex(rs2Item, "Deposit-X");
		if (op < 0)
		{
			log.debug("Rs2HuntKit: no Deposit-X for slot {} id {}", rs2Item.getSlot(), rs2Item.getId());
			return false;
		}
		invokeDepositMenu(op, rs2Item);
		// Note: client-thread amount entry is inherently flaky in some contexts due to focus/timing.
		// Prefer calling deposit/withdraw X from script threads when possible.
		boolean found;
		if (Microbot.getClient() != null && Microbot.getClient().isClientThread())
		{
			final boolean[] ok = {false};
			net.runelite.client.plugins.microbot.util.Global.sleepUntilOnClientThread(() -> {
				Widget widget = Rs2Widget.getWidget(ENTER_AMOUNT_GROUP, ENTER_AMOUNT_CHILD);
				ok[0] = widget != null && "Enter amount:".equalsIgnoreCase(widget.getText());
				return ok[0];
			}, 5000);
			found = ok[0];
		}
		else
		{
			found = sleepUntil(() -> {
				Widget widget = Rs2Widget.getWidget(ENTER_AMOUNT_GROUP, ENTER_AMOUNT_CHILD);
				return widget != null && "Enter amount:".equalsIgnoreCase(widget.getText());
			}, 5000);
		}
		if (!found)
		{
			return false;
		}
		Rs2Random.waitEx(1200, 100);
		Rs2Keyboard.typeString(String.valueOf(amount));
		Rs2Keyboard.enter();
		return true;
	}

	public static boolean depositX(int id, int amount)
	{
		return depositX(Rs2Inventory.get(id), amount);
	}

	public static boolean depositX(String name, int amount, boolean exact)
	{
		return depositX(Rs2Inventory.get(name, exact), amount);
	}

	public static boolean depositX(String name, int amount)
	{
		return depositX(name, amount, false);
	}

	private static boolean depositAll(Rs2ItemModel rs2Item)
	{
		if (rs2Item == null || !isOpen())
		{
			return false;
		}
		if (!Rs2Inventory.hasItem(rs2Item.getId()))
		{
			return false;
		}
		int op = depositActionIndex(rs2Item, "Deposit-All");
		if (op < 0)
		{
			log.debug("Rs2HuntKit: no Deposit-All for slot {} id {}", rs2Item.getSlot(), rs2Item.getId());
			return false;
		}
		invokeDepositMenu(op, rs2Item);
		return true;
	}

	public static boolean depositAll(int id)
	{
		Rs2ItemModel item = Rs2Inventory.get(id);
		if (item == null)
		{
			return false;
		}
		return depositAll(item);
	}

	public static boolean depositAll(String name, boolean exact)
	{
		return depositAll(Rs2Inventory.get(name, exact));
	}

	public static boolean depositAll(String name)
	{
		return depositAll(name, false);
	}

	/**
	 * Deposits every inventory stack matching {@code predicate}, re-querying each pass. Bounded by
	 * {@code max(Rs2Inventory.capacity() * 2, 40)} passes so pathological predicates cannot spin forever.
	 */
	public static boolean depositAll(Predicate<Rs2ItemModel> predicate)
	{
		if (predicate == null)
		{
			return false;
		}
		boolean result = false;
		final int maxPasses = Math.max(40, Rs2Inventory.capacity() * 2);
		for (int pass = 0; pass < maxPasses; pass++)
		{
			Rs2ItemModel item = Rs2Inventory.get(predicate);
			if (item == null)
			{
				break;
			}
			if (!depositAll(item))
			{
				return false;
			}
			sleep(Rs2Random.randomGaussian(400, 200));
			result = true;
		}
		return result;
	}

	public static boolean depositAllExcept(Predicate<Rs2ItemModel> predicate)
	{
		if (predicate == null)
		{
			return false;
		}
		return depositAll(predicate.negate());
	}

	public static boolean depositAllExcept(Integer... ids)
	{
		if (ids == null)
		{
			return false;
		}
		return depositAll(x -> Arrays.stream(ids).noneMatch(id -> id == x.getId()));
	}

	public static boolean depositAllExcept(String... names)
	{
		if (names == null)
		{
			return false;
		}
		return depositAllExcept(Rs2ItemModel.matches(false, names));
	}

	public static boolean depositAllExcept(Collection<String> names)
	{
		if (names == null)
		{
			return false;
		}
		return depositAllExcept(names.toArray(new String[0]));
	}

	public static boolean depositAllExcept(Map<String, Boolean> itemsToExclude)
	{
		if (itemsToExclude == null)
		{
			return false;
		}
		return depositAll(item -> itemsToExclude.entrySet().stream().noneMatch(entry -> {
			String excludedItemName = entry.getKey();
			if (excludedItemName == null)
			{
				return false;
			}
			boolean isFuzzy = Boolean.TRUE.equals(entry.getValue());
			return isFuzzy
					? item.getName().toLowerCase().contains(excludedItemName.toLowerCase())
					: item.getName().equalsIgnoreCase(excludedItemName);
		}));
	}

	public static boolean depositAllExcept(boolean exact, String... names)
	{
		if (names == null)
		{
			return false;
		}
		return depositAllExcept(Rs2ItemModel.matches(exact, names));
	}

	public static boolean withdrawOne(Rs2ItemModel rs2Item)
	{
		if (rs2Item == null || !isOpen())
		{
			return false;
		}
		if (Rs2Inventory.isFull())
		{
			return false;
		}
		int op = withdrawActionIndex(rs2Item, "Withdraw-1");
		if (op < 0)
		{
			log.debug("Rs2HuntKit: no Withdraw-1 op for slot {} id {}", rs2Item.getSlot(), rs2Item.getId());
			return false;
		}
		invokeMenu(op, rs2Item);
		return true;
	}

	public static boolean withdrawOne(int id)
	{
		return withdrawOne(findKitItem(id));
	}

	public static boolean withdrawOne(String name, boolean exact)
	{
		return withdrawOne(findKitItem(name, exact));
	}

	public static boolean withdrawOne(String name)
	{
		return withdrawOne(name, false);
	}

	public static boolean withdrawOne(boolean checkInv, int id)
	{
		if (checkInv && Rs2Inventory.hasItem(id))
		{
			return true;
		}
		return withdrawOne(id);
	}

	public static boolean withdrawItem(int id)
	{
		return withdrawOne(id);
	}

	public static boolean withdrawItem(String name)
	{
		return withdrawOne(name);
	}

	public static boolean withdrawItem(boolean checkInv, int id)
	{
		return withdrawOne(checkInv, id);
	}

	public static boolean withdrawItem(boolean checkInv, String name)
	{
		if (checkInv && Rs2Inventory.hasItem(name))
		{
			return true;
		}
		return withdrawOne(name);
	}

	public static boolean withdrawItem(boolean checkInv, String name, boolean exact)
	{
		if (checkInv && Rs2Inventory.hasItem(name, exact))
		{
			return true;
		}
		return withdrawOne(name, exact);
	}

	public static boolean withdrawAll(Rs2ItemModel rs2Item)
	{
		if (rs2Item == null || !isOpen())
		{
			return false;
		}
		if (Rs2Inventory.isFull())
		{
			return false;
		}
		int op = withdrawActionIndex(rs2Item, "Withdraw-All");
		if (op < 0)
		{
			log.debug("Rs2HuntKit: no Withdraw-All op for slot {} id {}", rs2Item.getSlot(), rs2Item.getId());
			return false;
		}
		invokeMenu(op, rs2Item);
		return true;
	}

	public static boolean withdrawAll(int id)
	{
		return withdrawAll(findKitItem(id));
	}

	public static boolean withdrawAll(String name, boolean exact)
	{
		return withdrawAll(findKitItem(name, exact));
	}

	public static boolean withdrawAll(String name)
	{
		return withdrawAll(name, false);
	}

	public static boolean withdrawAllButOne(int id)
	{
		return withdrawAllButOne(findKitItem(id));
	}

	public static boolean withdrawAllButOne(String name)
	{
		return withdrawAllButOne(name, false);
	}

	public static boolean withdrawAllButOne(String name, boolean exact)
	{
		return withdrawAllButOne(findKitItem(name, exact));
	}

	private static boolean withdrawAllButOne(Rs2ItemModel rs2Item)
	{
		if (rs2Item == null || !isOpen())
		{
			return false;
		}
		if (Rs2Inventory.isFull())
		{
			return true;
		}
		int op = withdrawActionIndex(rs2Item, "Withdraw-All-but-one");
		if (op < 0)
		{
			log.debug("Rs2HuntKit: no Withdraw-All-but-one op for slot {} id {}", rs2Item.getSlot(), rs2Item.getId());
			return false;
		}
		invokeMenu(op, rs2Item);
		return true;
	}

	public static boolean withdrawFive(Rs2ItemModel rs2Item)
	{
		if (rs2Item == null || !isOpen())
		{
			return false;
		}
		if (Rs2Inventory.isFull())
		{
			return false;
		}
		int op = withdrawActionIndex(rs2Item, "Withdraw-5");
		if (op < 0)
		{
			log.debug("Rs2HuntKit: no Withdraw-5 op for slot {} id {}", rs2Item.getSlot(), rs2Item.getId());
			return false;
		}
		invokeMenu(op, rs2Item);
		return true;
	}

	public static boolean withdrawFive(int id)
	{
		return withdrawFive(findKitItem(id));
	}

	public static boolean withdrawFive(String name)
	{
		return withdrawFive(name, false);
	}

	public static boolean withdrawFive(String name, boolean exact)
	{
		return withdrawFive(findKitItem(name, exact));
	}

	public static boolean withdrawFive(boolean checkInv, String name, boolean exact)
	{
		if (checkInv && inventoryHasAtLeast(name, 5, exact))
		{
			return true;
		}
		return withdrawFive(name, exact);
	}

	public static boolean withdrawX(Rs2ItemModel rs2Item, int amount)
	{
		if (rs2Item == null || !isOpen())
		{
			return false;
		}
		if (amount <= 0)
		{
			return true;
		}
		if (Rs2Inventory.isFull() && !Rs2Inventory.hasItem(rs2Item.getId()) && !rs2Item.isStackable())
		{
			return false;
		}
		int op = withdrawActionIndex(rs2Item, "Withdraw-X");
		if (op < 0)
		{
			log.debug("Rs2HuntKit: no Withdraw-X op for slot {} id {}", rs2Item.getSlot(), rs2Item.getId());
			return false;
		}
		invokeMenu(op, rs2Item);
		// Note: client-thread amount entry is inherently flaky in some contexts due to focus/timing.
		// Prefer calling deposit/withdraw X from script threads when possible.
		boolean found;
		if (Microbot.getClient() != null && Microbot.getClient().isClientThread())
		{
			final boolean[] ok = {false};
			net.runelite.client.plugins.microbot.util.Global.sleepUntilOnClientThread(() -> {
				Widget widget = Rs2Widget.getWidget(ENTER_AMOUNT_GROUP, ENTER_AMOUNT_CHILD);
				ok[0] = widget != null && "Enter amount:".equalsIgnoreCase(widget.getText());
				return ok[0];
			}, 5000);
			found = ok[0];
		}
		else
		{
			found = sleepUntil(() -> {
				Widget widget = Rs2Widget.getWidget(ENTER_AMOUNT_GROUP, ENTER_AMOUNT_CHILD);
				return widget != null && "Enter amount:".equalsIgnoreCase(widget.getText());
			}, 5000);
		}
		if (!found)
		{
			return false;
		}
		Rs2Random.waitEx(1200, 100);
		Rs2Keyboard.typeString(String.valueOf(amount));
		Rs2Keyboard.enter();
		return true;
	}

	public static boolean withdrawX(int id, int amount)
	{
		return withdrawX(findKitItem(id), amount);
	}

	public static boolean withdrawX(String name, int amount, boolean exact)
	{
		return withdrawX(findKitItem(name, exact), amount);
	}

	public static boolean withdrawX(String name, int amount)
	{
		return withdrawX(name, amount, false);
	}

	public static boolean withdrawX(boolean checkInv, int id, int amount)
	{
		if (checkInv && Rs2Inventory.hasItemAmount(id, amount))
		{
			return true;
		}
		return withdrawX(id, amount);
	}

	public static boolean withdrawX(boolean checkInv, String name, int amount)
	{
		return withdrawX(checkInv, name, amount, false);
	}

	public static boolean withdrawX(boolean checkInv, String name, int amount, boolean exact)
	{
		if (checkInv && inventoryHasAtLeast(name, amount, exact))
		{
			return true;
		}
		return withdrawX(name, amount, exact);
	}

	public static boolean withdrawDeficit(int id, int requiredAmount)
	{
		int have = Rs2Inventory.itemQuantity(id);
		int deficit = requiredAmount - have;
		if (deficit <= 0)
		{
			return true;
		}
		if (!hasKitItem(id, deficit))
		{
			return false;
		}
		Rs2ItemModel inKit = findKitItem(id);
		if (inKit == null)
		{
			return false;
		}
		if (inKit.isStackable() || deficit == 1)
		{
			return withdrawX(inKit, deficit);
		}
		boolean ok = true;
		int safety = 0;
		final int cap = 40;
		while (Rs2Inventory.itemQuantity(id) < requiredAmount && safety++ < cap)
		{
			if (Rs2Inventory.isFull())
			{
				return false;
			}
			if (!withdrawOne(inKit))
			{
				ok = false;
				break;
			}
			Rs2Inventory.waitForInventoryChanges(1200);
			inKit = findKitItem(id);
			if (inKit == null)
			{
				break;
			}
		}
		return ok && Rs2Inventory.itemQuantity(id) >= requiredAmount;
	}

	public static boolean withdrawDeficit(String name, int requiredAmount, boolean exact)
	{
		int deficit = requiredAmount - Rs2Inventory.itemQuantity(name, exact);
		if (deficit <= 0)
		{
			return true;
		}
		if (!hasKitItem(name, deficit, exact))
		{
			return false;
		}
		return withdrawX(findKitItem(name, exact), deficit);
	}

	public static boolean withdrawDeficit(String name, int requiredAmount)
	{
		return withdrawDeficit(name, requiredAmount, false);
	}

	/**
	 * Withdraw-all on each stackable in the kit cache, re-querying after each op. {@code maxOps} caps successful
	 * withdraw-alls; {@code maxOps *}{@link #WITHDRAW_ALL_STACKABLES_PASSES_PER_OP} bounds idle passes when the kit
	 * state does not change (e.g. full inventory).
	 */
	public static boolean withdrawAllStackables(int maxOps)
	{
		if (!isOpen())
		{
			return false;
		}
		if (maxOps <= 0)
		{
			return true;
		}
		int ops = 0;
		final int maxPasses = maxOps * WITHDRAW_ALL_STACKABLES_PASSES_PER_OP;
		for (int pass = 0; pass < maxPasses && ops < maxOps; pass++)
		{
			Rs2ItemModel it = stream().filter(Rs2ItemModel::isStackable).findFirst().orElse(null);
			if (it == null)
			{
				break;
			}
			if (Rs2Inventory.isFull())
			{
				return false;
			}
			if (!withdrawAll(it))
			{
				return false;
			}
			ops++;
		}
		return true;
	}
}
