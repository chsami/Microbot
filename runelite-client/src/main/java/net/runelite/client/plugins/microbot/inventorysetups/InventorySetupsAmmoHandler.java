package net.runelite.client.plugins.microbot.inventorysetups;

import net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsPluginPanel;
import net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsRunePouchPanel;
import net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsSlot;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.ItemID;
import net.runelite.api.VarPlayer;
import net.runelite.client.game.ItemManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsBoltPouchPanel.BOLT_POUCH_AMOUNT_VARBIT_IDS;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsBoltPouchPanel.BOLT_POUCH_BOLT_VARBIT_IDS;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsQuiverPanel.DIZANA_QUIVER_IDS;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsQuiverPanel.DIZANA_QUIVER_IDS_SET;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsRunePouchPanel.RUNE_POUCH_AMOUNT_VARBITS;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsRunePouchPanel.RUNE_POUCH_DIVINE_IDS_SET;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsRunePouchPanel.RUNE_POUCH_IDS_SET;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsRunePouchPanel.RUNE_POUCH_RUNE_VARBITS;

// Handles special containers like rune pouches, bolt pouches, and quivers
public class InventorySetupsAmmoHandler
{

	private Map<Integer, Consumer<InventorySetup>> updateDataHandler;

	private Map<Integer, Consumer<InventorySetup>> removeDataHandler;

	private final MInventorySetupsPlugin plugin;
	private final Client client;

	private final ItemManager itemManager;

	private final InventorySetupsPluginPanel panel;

	private final MInventorySetupsConfig config;

	public InventorySetupsAmmoHandler(MInventorySetupsPlugin plugin, Client client, ItemManager itemManager,
										InventorySetupsPluginPanel panel, MInventorySetupsConfig config)
	{
		this.plugin = plugin;
		this.client = client;
		this.itemManager = itemManager;
		this.panel = panel;
		this.config = config;

		this.updateDataHandler = new HashMap<>();
		this.removeDataHandler = new HashMap<>();

		// Handler for when an item being replaced is a rune pouch
		for (final int itemID : InventorySetupsRunePouchPanel.RUNE_POUCH_IDS)
		{
			updateDataHandler.put(itemID, (setup) -> setup.updateRunePouch(getRunePouchData(InventorySetupsRunePouchType.NORMAL)));
			removeDataHandler.put(itemID, (setup) -> setup.updateRunePouch(null));
		}
		for (final int itemID : InventorySetupsRunePouchPanel.RUNE_POUCH_DIVINE_IDS)
		{
			updateDataHandler.put(itemID, (setup) -> setup.updateRunePouch(getRunePouchData(InventorySetupsRunePouchType.DIVINE)));
			removeDataHandler.put(itemID, (setup) -> setup.updateRunePouch(null));
		}

		// Handler for when an item being replaced is a bolt pouch
		updateDataHandler.put(ItemID.BOLT_POUCH, (setup) -> setup.updateBoltPouch(getBoltPouchData()));
		removeDataHandler.put(ItemID.BOLT_POUCH, (setup) -> setup.updateBoltPouch(null));

		// Handler for when an item being replaced is a quiver
		for (final int itemID : DIZANA_QUIVER_IDS)
		{
			updateDataHandler.put(itemID, (setup) -> setup.updateQuiver(getQuiverData()));
			removeDataHandler.put(itemID, (setup) -> setup.updateQuiver(null));
		}
	}

	// Checks when updating a slot in a setup that it is part of a special ammo. If so, handle it.
	public void handleSpecialAmmo(final InventorySetup inventorySetup,
									final InventorySetupsItem oldItem, final InventorySetupsItem newItem)
	{
		int newID = newItem.getId();
		int oldID = oldItem.getId();
		if (updateDataHandler.containsKey(newID))
		{
			updateDataHandler.get(newID).accept(inventorySetup);
		}
		else if (updateDataHandler.containsKey(oldID))
		{
			removeDataHandler.get(oldID).accept(inventorySetup);
		}
	}

	public void handleSpecialHighlighting(final InventorySetup setup, final List<InventorySetupsItem> inventory, final List<InventorySetupsItem> equipment)
	{
		Set<Integer> combinedIds = inventory.stream().map(InventorySetupsItem::getId).collect(Collectors.toSet());
		combinedIds.addAll(equipment.stream().map(InventorySetupsItem::getId).collect(Collectors.toSet()));

		Set<Integer> divine_rune_pouch_intersection = new HashSet<>(RUNE_POUCH_DIVINE_IDS_SET);
		divine_rune_pouch_intersection.retainAll(combinedIds);
		Set<Integer> rune_pouch_intersection = new HashSet<>(RUNE_POUCH_IDS_SET);
		rune_pouch_intersection.retainAll(combinedIds);
		if (!divine_rune_pouch_intersection.isEmpty())
		{
			plugin.getClientThread().invoke(() ->
					panel.getRunePouchPanel().handleRunePouchHighlighting(setup, InventorySetupsRunePouchType.DIVINE));
		}
		else if (!rune_pouch_intersection.isEmpty())
		{
			plugin.getClientThread().invoke(() ->
					panel.getRunePouchPanel().handleRunePouchHighlighting(setup, InventorySetupsRunePouchType.NORMAL));
		}
		else
		{
			plugin.getClientThread().invoke(() ->
					panel.getRunePouchPanel().handleRunePouchHighlighting(setup, InventorySetupsRunePouchType.NONE));
		}

		Set<Integer> quiver_intersection = new HashSet<>(DIZANA_QUIVER_IDS_SET);
		quiver_intersection.retainAll(combinedIds);
		plugin.getClientThread().invoke(() ->
				panel.getQuiverPanel().handleQuiverHighlighting(setup, !quiver_intersection.isEmpty()));

		boolean currentInventoryHasBoltPouch = combinedIds.contains(ItemID.BOLT_POUCH);
		plugin.getClientThread().invoke(() ->
				panel.getBoltPouchPanel().handleBoltPouchHighlighting(setup, currentInventoryHasBoltPouch));
	}

	public InventorySetupsRunePouchType getRunePouchTypeFromContainer(final List<InventorySetupsItem> container)
	{
		// Don't allow fuzzy when checking because it will incorrectly assume the type
		if (plugin.containerContainsItemFromSet(RUNE_POUCH_IDS_SET, container, false, true))
		{
			return InventorySetupsRunePouchType.NORMAL;
		}
		if (plugin.containerContainsItemFromSet(RUNE_POUCH_DIVINE_IDS_SET, container, false, true))
		{
			return InventorySetupsRunePouchType.DIVINE;
		}
		return InventorySetupsRunePouchType.NONE;
	}

	public List<InventorySetupsItem> getRunePouchDataIfInContainer(final List<InventorySetupsItem> container)
	{
		InventorySetupsRunePouchType runePouchType = getRunePouchTypeFromContainer(container);
		return runePouchType != InventorySetupsRunePouchType.NONE ? getRunePouchData(runePouchType) : null;
	}

	// Must be run on client thread!
	public List<InventorySetupsItem> getRunePouchData(final InventorySetupsRunePouchType runePouchType)
	{
		List<InventorySetupsItem> runePouchData = new ArrayList<>();
		EnumComposition runepouchEnum = client.getEnum(982);
		for (int i = 0; i < runePouchType.getSize(); i++)
		{
			final int varbitVal = client.getVarbitValue(RUNE_POUCH_RUNE_VARBITS.get(i));
			if (varbitVal == 0)
			{
				runePouchData.add(InventorySetupsItem.getDummyItem());
			}
			else
			{
				final int runeId = runepouchEnum.getIntValue(varbitVal);
				int runeAmount = client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS.get(i));
				String runeName = itemManager.getItemComposition(runeId).getName();
				InventorySetupsStackCompareID stackCompareType = panel.isStackCompareForSlotAllowed(InventorySetupsSlotID.RUNE_POUCH, i) ? config.stackCompareType() : InventorySetupsStackCompareID.None;
				runePouchData.add(new InventorySetupsItem(runeId, runeName, runeAmount, false, stackCompareType));
			}
		}

		return runePouchData;
	}

	public boolean containerContainsBoltPouch(final List<InventorySetupsItem> container)
	{
		return plugin.containerContainsItem(ItemID.BOLT_POUCH, container, false, true);
	}

	public List<InventorySetupsItem> getBoltPouchDataIfInContainer(final List<InventorySetupsItem> container)
	{
		return containerContainsBoltPouch(container) ? getBoltPouchData() : null;
	}

	// Must be run on client thread!
	public List<InventorySetupsItem> getBoltPouchData()
	{
		List<InventorySetupsItem> boltPouchData = new ArrayList<>();

		for (int i = 0; i < BOLT_POUCH_BOLT_VARBIT_IDS.size(); i++)
		{
			int boltVarbitId = client.getVarbitValue(BOLT_POUCH_BOLT_VARBIT_IDS.get(i));
			Bolts bolt = Bolts.getBolt(boltVarbitId);
			boolean boltNotFound = bolt == null;
			int boltAmount = boltNotFound ? 0 : client.getVarbitValue(BOLT_POUCH_AMOUNT_VARBIT_IDS.get(i));
			String boltName = boltNotFound ? "" : itemManager.getItemComposition(bolt.getItemId()).getName();
			int boltItemId = boltNotFound ? -1 : bolt.getItemId();

			if (boltItemId == -1)
			{
				boltPouchData.add(InventorySetupsItem.getDummyItem());
			}
			else
			{
				InventorySetupsStackCompareID stackCompareType =
						panel.isStackCompareForSlotAllowed(InventorySetupsSlotID.BOLT_POUCH, i)
								? config.stackCompareType() : InventorySetupsStackCompareID.None;
				boltPouchData.add(new InventorySetupsItem(boltItemId, boltName, boltAmount, false, stackCompareType));
			}
		}

		return boltPouchData;
	}

	public boolean setupContainsQuiver(final List<InventorySetupsItem> inv, final List<InventorySetupsItem> eq)
	{
		boolean inventoryHasQuiver = plugin.containerContainsItemFromSet(DIZANA_QUIVER_IDS_SET, inv, false, true);
		return inventoryHasQuiver || plugin.containerContainsItemFromSet(DIZANA_QUIVER_IDS_SET, eq, false, true);
	}

	public List<InventorySetupsItem> getQuiverDataIfInSetup(final List<InventorySetupsItem> inv, final List<InventorySetupsItem> eq)
	{
		return setupContainsQuiver(inv, eq) ? getQuiverData() : null;
	}

	// Must be run on client thread!
	public List<InventorySetupsItem> getQuiverData()
	{
		List<InventorySetupsItem> quiverData = new ArrayList<>();
		final int quiverAmmoId = client.getVarpValue(VarPlayer.DIZANAS_QUIVER_ITEM_ID);
		final int quiverAmmoCount = Math.max(0, client.getVarpValue(VarPlayer.DIZANAS_QUIVER_ITEM_COUNT));

		if (quiverAmmoId == -1 || quiverAmmoCount == 0)
		{
			quiverData.add(InventorySetupsItem.getDummyItem());
		}
		else
		{
			final String ammoName = itemManager.getItemComposition(quiverAmmoId).getName();

			InventorySetupsStackCompareID stackCompareType =
					panel.isStackCompareForSlotAllowed(InventorySetupsSlotID.QUIVER, 0)
							? config.stackCompareType() : InventorySetupsStackCompareID.None;

			final InventorySetupsItem quiverItem = new InventorySetupsItem(quiverAmmoId, ammoName,
					quiverAmmoCount, false, stackCompareType);
			quiverData.add(quiverItem);
		}

		return quiverData;
	}

	public static String getSpecialContainerString(final InventorySetupsSlot slot)
	{
		String specialContainerString = "";
		switch (slot.getSlotID())
		{
			case RUNE_POUCH:
				specialContainerString = "Rune Pouch";
				break;
			case BOLT_POUCH:
				specialContainerString = "Bolt Pouch";
				break;
			case QUIVER:
				specialContainerString = "Quiver";
				break;
			default:
				assert false : "Wrong slot ID!";
				break;
		}
		return specialContainerString;
	}

	public boolean isStackCompareForSpecialSlotAllowed(final InventorySetupsSlotID inventoryID, final int slotId)
	{
		switch (inventoryID)
		{
			case RUNE_POUCH:
				return panel.getRunePouchPanel().isStackCompareForSlotAllowed(slotId);
			case BOLT_POUCH:
				return panel.getBoltPouchPanel().isStackCompareForSlotAllowed(slotId);
			case QUIVER:
				return true;
			default:
				assert false : "Wrong Slot ID!";
				return false;
		}
	}

	public List<InventorySetupsItem> getNormalizedSpecialContainer(final InventorySetupsSlotID id)
	{
		switch (id)
		{
			case RUNE_POUCH:
				return getRunePouchData(InventorySetupsRunePouchType.DIVINE);
			case BOLT_POUCH:
				return getBoltPouchData();
			case QUIVER:
				return getQuiverData();
			default:
				assert false : "Wrong slot ID!";
				return null;
		}
	}

	public List<InventorySetupsItem> getSpecialContainerFromID(final InventorySetup inventorySetup, InventorySetupsSlotID ID)
	{
		switch (ID)
		{
			case RUNE_POUCH:
				return inventorySetup.getRune_pouch();
			case BOLT_POUCH:
				return inventorySetup.getBoltPouch();
			case QUIVER:
				return inventorySetup.getQuiver();
			default:
				assert false : "Invalid ID given";
				return null;
		}
	}

	public boolean specialContainersContainItem(final InventorySetup setup, int itemID, boolean allowFuzzy, boolean canonicalize)
	{
		if (plugin.containerContainsItem(itemID, setup.getRune_pouch(), allowFuzzy, canonicalize))
		{
			return true;
		}
		if (plugin.containerContainsItem(itemID, setup.getBoltPouch(), allowFuzzy, canonicalize))
		{
			return true;
		}
		return plugin.containerContainsItem(itemID, setup.getQuiver(), allowFuzzy, canonicalize);
	}

	public void updateSpecialContainersInSetup(final InventorySetup setup, final List<InventorySetupsItem> inv, final List<InventorySetupsItem> eqp)
	{
		List<InventorySetupsItem> runePouchData = getRunePouchDataIfInContainer(inv);
		List<InventorySetupsItem> boltPouchData = getBoltPouchDataIfInContainer(inv);
		List<InventorySetupsItem> quiverData = getQuiverDataIfInSetup(inv, eqp);

		setup.updateRunePouch(runePouchData);
		setup.updateBoltPouch(boltPouchData);
		setup.updateQuiver(quiverData);
	}
}
