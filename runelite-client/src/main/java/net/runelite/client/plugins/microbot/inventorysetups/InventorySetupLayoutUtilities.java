package net.runelite.client.plugins.microbot.inventorysetups;

import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.util.Text;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin.LAYOUT_PREFIX_MARKER;

public class InventorySetupLayoutUtilities
{
	private final ItemManager itemManager;

	private final TagManager tagManager;

	private final LayoutManager layoutManager;

	private final MInventorySetupsConfig config;

	private final Client client;

	public InventorySetupLayoutUtilities(final ItemManager itemManager, final TagManager tagManager, final LayoutManager layoutManager, final MInventorySetupsConfig config, final Client client)
	{
		this.itemManager = itemManager;
		this.tagManager = tagManager;
		this.layoutManager = layoutManager;
		this.config = config;
		this.client = client;
	}

	public static String getTagNameForLayout(final String inventorySetupName)
	{
		String hashOfName = InventorySetupsPersistentDataManager.hashFunction.hashUnencodedChars(inventorySetupName).toString();
		return LAYOUT_PREFIX_MARKER + hashOfName;
	}

	public Layout getSetupLayout(final InventorySetup setup)
	{
		final String tagName = getTagNameForLayout(setup.getName());
		final Layout layout = layoutManager.loadLayout(tagName);
		assert layout != null : "Layout for " + setup.getName() + " is null.";
		return layout;
	}

	public Layout createSetupLayout(final InventorySetup setup)
	{
		InventorySetupLayoutType type = config.defaultLayout();
		return createSetupLayout(setup, type, true);
	}

	public Layout createSetupLayout(final InventorySetup setup, InventorySetupLayoutType type, final boolean addToTag)
	{
		if (type.equals(InventorySetupLayoutType.PRESET))
		{
			return getPresetLayout(setup, addToTag);
		}
		else
		{
			return getZigZagLayout(setup, addToTag);
		}
	}

	public Layout getZigZagLayout(final InventorySetup setup, final boolean addToTag)
	{
		final String tag = getTagNameForLayout(setup.getName());
		final Layout layout = new Layout(tag);

		int startOfEquipment = 0;
		int startOfInventory = 16;
		int startOfRunePouch = 48;
		int startOfBoltPouch = 52;
		int startOfAdditionalItems = 56;

		int newSizeGuess = setup.getAdditionalFilteredItems().size() + startOfAdditionalItems;
		layout.resize(newSizeGuess);
		final HashMap<Integer, Integer> counter = new HashMap<>();


		int nextPos = layoutZigZagContainer(setup.getEquipment(), layout, tag, addToTag, startOfEquipment, counter);
		if (setup.getQuiver() != null && !setup.getQuiver().isEmpty())
		{
			addItemToLayout(layout, tag, setup.getQuiver().get(0), nextPos, addToTag, counter);
		}
		layoutZigZagContainer(setup.getInventory(), layout, tag, addToTag, startOfInventory, counter);

		// Layout the rune pouch
		if (setup.getRune_pouch() != null)
		{
			for (int i = 0; i < setup.getRune_pouch().size(); i++)
			{
				addItemToLayout(layout, tag, setup.getRune_pouch().get(i), i + startOfRunePouch, addToTag, counter);
			}
		}

		// Bolt pouch below the rune pouch
		if (setup.getBoltPouch() != null)
		{
			for (int i = 0; i < setup.getBoltPouch().size(); i++)
			{
				addItemToLayout(layout, tag, setup.getBoltPouch().get(i), i + startOfBoltPouch, addToTag, counter);
			}
		}

		// Additional items
		int additionalItemsCounter = 0;
		Collection<InventorySetupsItem> additionalItems = setup.getAdditionalFilteredItems().values();
		for (final InventorySetupsItem item : additionalItems)
		{
			boolean added = addItemToLayout(layout, tag, item, additionalItemsCounter + startOfAdditionalItems, addToTag, counter);
			if (added)
			{
				additionalItemsCounter++;
			}
		}

		// If duplicate items are disabled, this will ensure fuzzy items get added to the bottom row after
		trimLayout(layout);
		if (layout.size() < startOfAdditionalItems)
		{
			layout.resize(startOfAdditionalItems);
		}
		addFuzzyItemsToEndOfLayout(layout, setup);

		return layout;
	}

	private int layoutZigZagContainer(final List<InventorySetupsItem> container, final Layout layout, final String tag, boolean addToTag, final int start, final Map<Integer, Integer> counter)
	{
		// Note, this might not work if the start is not a multiple of the row size (8)...
		// But this is not needed, so I won't spend time over engineering this function.

		int doubleRowStart = 0;
		int nextPos = 0;
		final int rowSize = 8;

		for (final InventorySetupsItem item : container)
		{
			boolean added = addItemToLayout(layout, tag, item, nextPos + start, addToTag, counter);
			if (!added)
			{
				continue;
			}

			if (nextPos == (rowSize * 2) - 1)
			{
				// We hit the end of a double row, we need to start a new one.
				doubleRowStart += 2;
				nextPos = doubleRowStart * rowSize;
			}
			else if (nextPos < ((doubleRowStart * rowSize) + rowSize))
			{
				// We are in the top half of a double row. Go down directly one.
				nextPos += rowSize;
			}
			else
			{
				// We are in the bottom half of a double. Go back up and add one to move to the right.
				nextPos = (nextPos - rowSize) + 1;
			}
		}

		return nextPos;
	}

	public Layout getPresetLayout(final InventorySetup setup, final boolean addToTag)
	{
		// This generates a "Preset" layout based on an InventorySetup
		// It will have the equipment on the left and inventory on the right
		// This must be called on the clientThread

		// Note: Core Layout API does not support custom variation mappings, so even if fuzzy is marked,
		// custom mappings will not work in the layout.

		final String tag = getTagNameForLayout(setup.getName());
		final Layout layout = new Layout(tag);

		// Determine size to help reduce amount of copy overs.
		// Since the inventory will be a 4x7 on the right side of the bank, it will end at position 55.
		int startOfAdditionalItems = 56;
		int startOfBoltPouch = 48;
		int startOfRunePouch = 40;

		int newSizeGuess = setup.getAdditionalFilteredItems().size() + startOfAdditionalItems;
		layout.resize(newSizeGuess);

		final HashMap<Integer, Integer> counter = new HashMap<>();

		// Layout the equipment on left side
		final List<InventorySetupsItem> eqp = setup.getEquipment();
		addItemToLayout(layout, tag, eqp.get(EquipmentInventorySlot.HEAD.getSlotIdx()), 1, addToTag, counter);
		if (setup.getQuiver() != null && !setup.getQuiver().isEmpty())
		{
			addItemToLayout(layout, tag, setup.getQuiver().get(0), 2, addToTag, counter);
		}
		addItemToLayout(layout, tag, eqp.get(EquipmentInventorySlot.CAPE.getSlotIdx()), 8, addToTag, counter);
		addItemToLayout(layout, tag, eqp.get(EquipmentInventorySlot.AMULET.getSlotIdx()), 9, addToTag, counter);
		addItemToLayout(layout, tag, eqp.get(EquipmentInventorySlot.AMMO.getSlotIdx()), 10, addToTag, counter);
		addItemToLayout(layout, tag, eqp.get(EquipmentInventorySlot.WEAPON.getSlotIdx()), 16, addToTag, counter);
		addItemToLayout(layout, tag, eqp.get(EquipmentInventorySlot.BODY.getSlotIdx()), 17, addToTag, counter);
		addItemToLayout(layout, tag, eqp.get(EquipmentInventorySlot.SHIELD.getSlotIdx()), 18, addToTag, counter);
		addItemToLayout(layout, tag, eqp.get(EquipmentInventorySlot.LEGS.getSlotIdx()), 25, addToTag, counter);
		addItemToLayout(layout, tag, eqp.get(EquipmentInventorySlot.GLOVES.getSlotIdx()), 32, addToTag, counter);
		addItemToLayout(layout, tag, eqp.get(EquipmentInventorySlot.BOOTS.getSlotIdx()), 33, addToTag, counter);
		addItemToLayout(layout, tag, eqp.get(EquipmentInventorySlot.RING.getSlotIdx()), 34, addToTag, counter);

		// Layout the inventory on the right side
		int invRow = 0;
		int invCol = 4;
		int width = 8;
		for (final InventorySetupsItem item: setup.getInventory())
		{
			addItemToLayout(layout, tag, item, invCol + (invRow * width), addToTag, counter);
			if (invCol == 7)
			{
				invCol = 4;
				invRow++;
			}
			else
			{
				invCol++;
			}
		}

		// Rune pouch below equipment
		if (setup.getRune_pouch() != null)
		{
			for (int i = 0; i < setup.getRune_pouch().size(); i++)
			{
				addItemToLayout(layout, tag, setup.getRune_pouch().get(i), i + startOfRunePouch, addToTag, counter);
			}
		}

		// Bolt pouch below the rune pouch
		if (setup.getBoltPouch() != null)
		{
			for (int i = 0; i < setup.getBoltPouch().size(); i++)
			{
				addItemToLayout(layout, tag, setup.getBoltPouch().get(i), i + startOfBoltPouch, addToTag, counter);
			}
		}

		// Additional items
		int additionalItemsCounter = 0;
		Collection<InventorySetupsItem> additionalItems = setup.getAdditionalFilteredItems().values();
		for (final InventorySetupsItem item : additionalItems)
		{
			boolean added = addItemToLayout(layout, tag, item, additionalItemsCounter + startOfAdditionalItems, addToTag, counter);
			if (added)
			{
				additionalItemsCounter++;
			}
		}

		// If duplicate items are disabled, this will ensure fuzzy items get added to the bottom row after
		trimLayout(layout);
		if (layout.size() < startOfAdditionalItems)
		{
			layout.resize(startOfAdditionalItems);
		}
		addFuzzyItemsToEndOfLayout(layout, setup);

		return layout;
	}

	private boolean addItemToLayout(final Layout layout, final String tagName, final InventorySetupsItem item, final int pos, final boolean addToTag, final Map<Integer, Integer> counter)
	{
		if (item.getId() == -1)
		{
			return false;
		}
		int id = itemManager.canonicalize(item.getId());
		if (!config.layoutDuplicates() && counter.containsKey(id))
		{
			return false;
		}
		layout.setItemAtPos(id, pos);
		// We may not want to add to the tag if we just want to create a layout but not update tags.
		// Useful for displaying a temporary layout.
		if (addToTag)
		{
			tagManager.addTag(id, tagName, item.isFuzzy());
		}
		counter.put(id, counter.getOrDefault(id, 0) + 1);

		return true;
	}

	public void recalculateLayout(final InventorySetup setup)
	{
		// Recalculate all the items that should be in the layout

		final List<InventorySetupsItem> itemsInSetup = InventorySetup.getSetupItems(setup);
		final String tagName = getTagNameForLayout(setup.getName());
		final Layout layout = layoutManager.loadLayout(tagName);
		assert layout != null : "Setup " + setup.getName() + " has no layout.";

		// Remove the tag entirely.
		tagManager.removeTag(tagName);

		// Re-add every item in the tag.
		Set<Integer> idsInSetup = new LinkedHashSet<>();
		Set<Integer> idsInSetupNoFuzzy = new LinkedHashSet<>();
		for (InventorySetupsItem item : itemsInSetup)
		{
			int processedId = itemManager.canonicalize(item.getId());
			if (processedId == -1)
			{
				continue;
			}

			// Fuzzy tags and non-fuzzy tags are actually stored separate, so a non-fuzzy item can't override
			// The same fuzzy item that came before it (even though this would be a rare case).
			// I don't know the effect of the fuzzy and non-fuzzy items being part of both tags.
			tagManager.addTag(processedId, tagName, item.isFuzzy());

			// Track all the IDs in the setup
			idsInSetup.add(processedId);
			idsInSetupNoFuzzy.add(processedId);
			if (item.isFuzzy())
			{
				final int baseProcessedId = InventorySetupsVariationMapping.map(processedId);
				idsInSetup.addAll(InventorySetupsVariationMapping.getVariations(baseProcessedId));
			}
		}

		// Remove any items that do not belong in the layout.
		// This won't potentially remove an item if there are multiple copies of it in the setup.
		// It's possible that some users would want one of the instances of the item to be replaced
		// But for now, we avoid that. If this is needed, it's probably better to do it in the caller of this function
		// Since it knows if an item was removed or not, while this is catch all recalculation of the layout.
		Set<Integer> idsInLayout = new HashSet<>();
		for (int i = 0; i < layout.size(); i++)
		{
			int layoutId = layout.getItemAtPos(i);
			if (layoutId == -1)
			{
				continue;
			}

			// Make sure to convert placeholders to the actual value otherwise we might delete
			// The placeholder in the layout.
			ItemComposition itemComp = itemManager.getItemComposition(layoutId);
			boolean itemIsPlaceholder = itemComp.getPlaceholderTemplateId() > -1;
			int processedId = layoutId;
			if (itemIsPlaceholder)
			{
				processedId = itemComp.getPlaceholderId();
			}

			if (!idsInSetup.contains(processedId))
			{
				layout.removeItemAtPos(i);
				continue;
			}

			idsInLayout.add(layoutId);
		}

		// Add any items that should belong in the layout based on the setup to the first available position
		// So it's easy to find.
		// Do not include fuzzy items in the setup because we can add those at the bottom.
		for (final Integer idInSetup : idsInSetupNoFuzzy)
		{
			if (!idsInLayout.contains(idInSetup))
			{
				layout.addItem(idInSetup);
			}
		}

		addFuzzyItemsToEndOfLayout(layout, idsInSetup, idsInSetupNoFuzzy, idsInLayout);

		trimLayout(layout);

		layoutManager.saveLayout(layout);
	}

	private void addFuzzyItemsToEndOfLayout(final Layout layout, final InventorySetup setup)
	{
		List<InventorySetupsItem> itemsInSetup = InventorySetup.getSetupItems(setup);
		Set<Integer> idsInSetup = new LinkedHashSet<>();
		Set<Integer> idsInSetupNoFuzzy = new LinkedHashSet<>();
		for (InventorySetupsItem item : itemsInSetup)
		{
			int processedId = itemManager.canonicalize(item.getId());
			if (processedId == -1)
			{
				continue;
			}

			idsInSetup.add(processedId);
			idsInSetupNoFuzzy.add(processedId);
			if (item.isFuzzy())
			{
				final int baseProcessedId = InventorySetupsVariationMapping.map(processedId);
				idsInSetup.addAll(InventorySetupsVariationMapping.getVariations(baseProcessedId));
			}
		}

		Set<Integer> idsInLayout = new LinkedHashSet<>();
		for (int i = 0; i < layout.size(); i++)
		{
			idsInLayout.add(layout.getItemAtPos(i));
		}

		addFuzzyItemsToEndOfLayout(layout, idsInSetup, idsInSetupNoFuzzy, idsInLayout);
	}

	private void addFuzzyItemsToEndOfLayout(final Layout layout, final Set<Integer> idsInSetup, final Set<Integer> idsInSetupNoFuzzy, final Set<Integer> idsInLayout)
	{
		// Try our best at adding fuzzy items to the bottom of the layout
		// Only add those that exist in the bank, otherwise we would add every possible variation which is not ideal.
		// Any missed items will be added by the layout manager.
		// This also adds the benefit that we can use our own custom mappings along with base item variation mappings.
		// This will probably only work if the bank is open, but still worth doing for things like auto layouts.

		// NOTE: This does have some drawbacks. Fuzzy items will occupy a new slot rather than fit an existing placeholder
		// This means that a fuzzy barrows item will have two bank slots.
		// Additionally, this will not find item placeholders like "Amulet of Glory" so they will be put at the top
		// of the bank instead of at the bottom.
		ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
		if (bankContainer == null)
		{
			return;
		}

		Set<Integer> idsInSetupOnlyFuzzy = new LinkedHashSet<>(idsInSetup);
		idsInSetupOnlyFuzzy.removeAll(idsInSetupNoFuzzy);
		if (idsInSetupOnlyFuzzy.isEmpty())
		{
			return;
		}

		Set<Integer> bankItems = new LinkedHashSet<>();
		for (int i = 0; i < bankContainer.size(); i++)
		{
			Item item = bankContainer.getItem(i);
			if (item != null && item.getId() > -1 && item.getId() != ItemID.BLANKOBJECT)
			{
				bankItems.add(item.getId());
			}
		}

		for (final Integer id : idsInSetupOnlyFuzzy)
		{
			if (bankItems.contains(id) && !idsInLayout.contains(id))
			{
				layout.addItemAfter(id, layout.size());
				continue;
			}

			// Try to add the placeholder IDs at the bottom of the layout if the actual item doesn't exist.
			// Bank Tags will add the placeholder ID rather than the item ID, so we should add the placeholder as well.
			int placeholderID = itemManager.getItemComposition(id).getPlaceholderId();
			if (bankItems.contains(placeholderID) && !idsInLayout.contains(placeholderID))
			{
				layout.addItemAfter(placeholderID, layout.size());
			}
		}

	}

	public void exportSetupToBankTagTab(final InventorySetup setup, final Component component)
	{
		final List<String> data = new ArrayList<>();
		final Layout layout = getSetupLayout(setup);
		data.add("banktags");
		data.add("1");

		// Instead of the super long marked name, just use the standardized name for the setup.
		data.add(Text.standardize(setup.getName()));
		int icon = setup.getIconID();
		if (icon <= 0)
		{
			// try to use the current weapon
			icon = setup.getEquipment().get(EquipmentInventorySlot.WEAPON.getSlotIdx()).getId();
		}
		if (icon <= 0)
		{
			icon = ItemID.SPADE;
		}

		data.add(String.valueOf(icon));

		for (Integer item : tagManager.getItemsForTag(layout.getTag()))
		{
			if (layout.count(item) == 0)
			{
				data.add(String.valueOf(item));
			}
		}

		data.add("layout");
		int[] l = layout.getLayout();
		for (int idx = 0; idx < l.length; ++idx)
		{
			if (l[idx] != -1)
			{
				data.add(String.valueOf(idx));
				data.add(String.valueOf(l[idx]));
			}
		}

		final StringSelection stringSelection = new StringSelection(Text.toCSV(data));
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);

		SwingUtilities.invokeLater(() ->
		{
			JOptionPane.showMessageDialog(component,
					"Bank tag tab data was copied to clipboard.",
					"Export Setup To Bank Tag Tab Succeeded",
					JOptionPane.PLAIN_MESSAGE);
		});
	}

	public Layout convertHubBankTagLayoutToCoreBankTagLayout(final String hubBankTagLayoutData, final String tag)
	{
		try
		{
			final Layout layout = new Layout(tag);
			final String[] pairs = hubBankTagLayoutData.split(",");
			for (final String pair : pairs)
			{
				String[] numbers = pair.split(":");
				int id = Integer.parseInt(numbers[0]);
				int pos = Integer.parseInt(numbers[1]);

				// Bank Tag Layout setups might have item placeholder id which won't play nice with bank tags
				int processedID = itemManager.canonicalize(id);
				layout.setItemAtPos(processedID, pos);
			}
			return layout;
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private void trimLayout(final Layout layout)
	{
		// Remove all trailing `-1`s in a layout.
		if (layout.size() == 0)
		{
			return;
		}
		final int[] layoutArr = layout.getLayout();
		if (layoutArr[layoutArr.length - 1] != -1)
		{
			return;
		}

		int indexToTrimTo = layoutArr.length - 2;
		while (indexToTrimTo >= 0 && layoutArr[indexToTrimTo] == -1)
		{
			indexToTrimTo--;
		}
		layout.resize(indexToTrimTo + 1);
	}

}