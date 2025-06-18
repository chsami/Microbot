/*
 * Copyright (c) 2019, dillydill123 <https://github.com/dillydill123>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.inventorysetups.ui;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsSlotID;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsVariationMapping;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

// The panel that contains the inventory slots
public class InventorySetupsInventoryPanel extends InventorySetupsContainerPanel
{

	private static final int ITEMS_PER_ROW = 4;
	private static final int NUM_INVENTORY_ITEMS = 28;

	private List<InventorySetupsSlot> inventorySlots;
	private final InventorySetupsRunePouchPanel runePouchPanel;
	private final InventorySetupsBoltPouchPanel boltPouchPanel;

	InventorySetupsInventoryPanel(final ItemManager itemManager, final MInventorySetupsPlugin plugin,
									final InventorySetupsRunePouchPanel runePouchPanel,
									final InventorySetupsBoltPouchPanel boltPouchPanel)
	{
		super(itemManager, plugin, "Inventory");
		this.runePouchPanel = runePouchPanel;
		this.boltPouchPanel = boltPouchPanel;
	}

	@Override
	public void setupContainerPanel(final JPanel containerSlotsPanel)
	{
		this.inventorySlots = new ArrayList<>();
		for (int i = 0; i < NUM_INVENTORY_ITEMS; i++)
		{
			inventorySlots.add(new InventorySetupsSlot(ColorScheme.DARKER_GRAY_COLOR, InventorySetupsSlotID.INVENTORY, i));
		}

		int numRows = (NUM_INVENTORY_ITEMS + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
		containerSlotsPanel.setLayout(new GridLayout(numRows, ITEMS_PER_ROW, 1, 1));
		for (int i = 0; i < NUM_INVENTORY_ITEMS; i++)
		{
			containerSlotsPanel.add(inventorySlots.get(i));
			InventorySetupsSlot.addFuzzyMouseListenerToSlot(plugin, inventorySlots.get(i));
			InventorySetupsSlot.addStackMouseListenerToSlot(plugin, inventorySlots.get(i));
			InventorySetupsSlot.addUpdateFromContainerMouseListenerToSlot(plugin, inventorySlots.get(i));
			InventorySetupsSlot.addUpdateFromSearchMouseListenerToSlot(plugin, inventorySlots.get(i), true);
			InventorySetupsSlot.addRemoveMouseListenerToSlot(plugin, inventorySlots.get(i));

			// Shift menu
			InventorySetupsSlot.addUpdateFromContainerToAllInstancesMouseListenerToSlot(this, plugin, inventorySlots.get(i));
			InventorySetupsSlot.addUpdateFromSearchToAllInstancesMouseListenerToSlot(this, plugin, inventorySlots.get(i), true);
		}
	}

	@Override
	public void updatePanelWithSetupInformation(final InventorySetup setup)
	{
		for (int i = 0; i < NUM_INVENTORY_ITEMS; i++)
		{
			InventorySetupsSlot.setSlotImageAndText(itemManager, inventorySlots.get(i), setup, setup.getInventory().get(i));
		}

		validate();
		repaint();
	}

	@Override
	public void highlightSlots(final List<InventorySetupsItem> currInventory, final InventorySetup inventorySetup)
	{
		final List<InventorySetupsItem> inventoryToCheck = inventorySetup.getInventory();

		assert currInventory.size() == inventoryToCheck.size() : "size mismatch";

		isHighlighted = true;

		if (inventorySetup.isUnorderedHighlight())
		{
			doUnorderedHighlighting(currInventory, inventorySetup);
			return;
		}

		for (int i = 0; i < NUM_INVENTORY_ITEMS; i++)
		{
			InventorySetupsSlot.highlightSlot(inventorySetup, inventoryToCheck.get(i), currInventory.get(i), inventorySlots.get(i));
		}
	}

	@Override
	public void resetSlotColors()
	{
		// Don't waste time resetting if we were never highlighted to begin with
		if (!isHighlighted)
		{
			return;
		}

		for (InventorySetupsSlot inventorySlot : inventorySlots)
		{
			inventorySlot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		}

		runePouchPanel.resetSlotColors();
		boltPouchPanel.resetSlotColors();

		isHighlighted = false;
	}

	public boolean isStackCompareForSlotAllowed(final int id)
	{
		return true;
	}

	private void doUnorderedHighlighting(final List<InventorySetupsItem> currInventory, final InventorySetup inventorySetup)
	{
		Map<Integer, List<Integer>> currentInventoryMapping = new HashMap<>();

		// collect items in current inventory in the form of a Map -> List of stack sizes
		for (final InventorySetupsItem item : currInventory)
		{
			List<Integer> currentItemList = currentInventoryMapping.get(item.getId());
			if (currentItemList == null)
			{
				currentItemList = new ArrayList<>();
				currentInventoryMapping.put(item.getId(), currentItemList);
			}
			currentItemList.add(item.getQuantity());
		}

		final List<InventorySetupsItem> setupInventory = inventorySetup.getInventory();

		List<Boolean> processedInventoryItems = new ArrayList<>(Arrays.asList(new Boolean[setupInventory.size()]));
		Collections.fill(processedInventoryItems, Boolean.FALSE);

		// First process non fuzzy exact items, then fuzzy exact items
		processExactItems(inventorySetup, currentInventoryMapping, processedInventoryItems, false);
		processExactItems(inventorySetup, currentInventoryMapping, processedInventoryItems, true);

		// now process any items left which may use fuzzy mappings
		for (int i = 0; i < setupInventory.size(); i++)
		{
			// the item was already processed above using exact match
			if (processedInventoryItems.get(i))
			{
				continue;
			}

			final InventorySetupsItem savedItemFromInventory = setupInventory.get(i);

			// Exact items have been handled, try to find fuzzy items
			int savedItemId = savedItemFromInventory.getId();
			List<Integer> currentItemListForSpecificId = currentInventoryMapping.get(savedItemId);
			if (currentItemListForSpecificId == null && savedItemFromInventory.isFuzzy()) // item list should always return null, exact matches handled above
			{
				// if the item is fuzzy, attempt to find a suitable item
				for (final int currentItemIdFromMapping : currentInventoryMapping.keySet())
				{
					if (InventorySetupsVariationMapping.map(currentItemIdFromMapping) == InventorySetupsVariationMapping.map(savedItemId))
					{
						currentItemListForSpecificId = currentInventoryMapping.get(currentItemIdFromMapping);
						savedItemId = currentItemIdFromMapping; // Needed to delete the correct list later
						break;
					}
				}
			}

			// if we could not find a item Id that is sufficient for that item, highlight it
			if (currentItemListForSpecificId == null)
			{
				inventorySlots.get(i).setBackground(inventorySetup.getHighlightColor());
				continue;
			}

			updateCurrentUnorderedSlot(savedItemId, inventorySetup, inventorySlots.get(i), savedItemFromInventory, currentItemListForSpecificId, currentInventoryMapping);
		}
	}

	private void processExactItems(final InventorySetup inventorySetup,
									final Map<Integer, List<Integer>> currentInventoryMapping,
									final List<Boolean> processedInvItems,
									final boolean allowFuzzy)
	{
		List<InventorySetupsItem> setupInventory = inventorySetup.getInventory();

		// Handle exact non fuzzy items first
		// Exact items will be preferred first
		for (int i = 0; i < setupInventory.size(); i++)
		{
			if (processedInvItems.get(i))
			{
				continue;
			}

			final InventorySetupsItem savedItemFromInventory = setupInventory.get(i);

			// don't count empty spaces. We only want to show items that are missing, not "extra items"
			// that would be indicated by highlighting empty slots.
			if (savedItemFromInventory.getId() == -1)
			{
				inventorySlots.get(i).setBackground(ColorScheme.DARKER_GRAY_COLOR);
				processedInvItems.set(i, Boolean.TRUE);
				continue;
			}

			// Only mark it as processed if the item exists. If it doesn't there may be a fuzzy variant available
			List<Integer> currentItemListForSpecificId = currentInventoryMapping.get(savedItemFromInventory.getId());
			if (currentItemListForSpecificId != null && (allowFuzzy || !savedItemFromInventory.isFuzzy()))
			{
				updateCurrentUnorderedSlot(savedItemFromInventory.getId(), inventorySetup, inventorySlots.get(i), savedItemFromInventory, currentItemListForSpecificId, currentInventoryMapping);
				processedInvItems.set(i, Boolean.TRUE);
			}

		}
	}


	private void updateCurrentUnorderedSlot(int itemId, final InventorySetup inventorySetup,
											final InventorySetupsSlot currentSlot,
											final InventorySetupsItem savedItemFromInventory,
											final List<Integer> currentItemListForSpecificId,
											final Map<Integer, List<Integer>> currentInventoryMapping)
	{
		// This assumes the last item contains the correct quantity. This is done because
		// in the actual game, you can't have two stacks of stackable items. This assumption
		// is fine for stackable items as well.
		Integer currentInventoryItemQty = currentItemListForSpecificId.get(currentItemListForSpecificId.size() - 1);

		// delete the list if it's empty to simplify things
		currentItemListForSpecificId.remove(currentItemListForSpecificId.size() - 1);
		if (currentItemListForSpecificId.isEmpty())
		{
			currentInventoryMapping.remove(itemId);
		}

		if (InventorySetupsSlot.shouldHighlightSlotBasedOnStack(savedItemFromInventory.getStackCompare(), savedItemFromInventory.getQuantity(), currentInventoryItemQty))
		{
			currentSlot.setBackground(inventorySetup.getHighlightColor());
		}
		else
		{
			currentSlot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		}
	}
}
