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
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsAmmoHandler;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsSlotID;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsVariationMapping;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.*;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class InventorySetupsSlot extends JPanel
{
	@Getter
	private final JLabel imageLabel;

	@Getter
	private final InventorySetupsSlotID slotID;

	@Getter
	@Setter
	private InventorySetup parentSetup;

	@Getter
	private int indexInSlot;

	@Getter
	private JLabel fuzzyIndicator;

	@Getter
	private JLabel stackIndicator;

	@Getter
	private JPopupMenu rightClickMenu;

	@Getter
	private JPopupMenu shiftRightClickMenu;

	public InventorySetupsSlot(Color color, InventorySetupsSlotID id, int indexInSlot)
	{
		this(color, id, indexInSlot, 46, 42);
	}

	public InventorySetupsSlot(Color color, InventorySetupsSlotID id, int indexInSlot, int width, int height)
	{
		this.slotID = id;
		this.imageLabel = new JLabel();
		this.parentSetup = null;
		this.fuzzyIndicator = new JLabel();
		this.stackIndicator = new JLabel();
		fuzzyIndicator.setFont(FontManager.getRunescapeSmallFont());
		stackIndicator.setFont(FontManager.getRunescapeSmallFont());
		this.indexInSlot = indexInSlot;
		this.rightClickMenu = new JPopupMenu();
		this.shiftRightClickMenu = new JPopupMenu();

		MouseAdapter menuAdapter = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				showPopupMenu(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				showPopupMenu(e);
			}

			private void showPopupMenu(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					if ((e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0)
					{
						shiftRightClickMenu.show(e.getComponent(), e.getX(), e.getY());
					}
					else
					{
						rightClickMenu.show(e.getComponent(), e.getX(), e.getY());
					}
				}
			}
		};
		this.addMouseListener(menuAdapter);
		this.imageLabel.addMouseListener(menuAdapter);

		setPreferredSize(new Dimension(width, height));
		setBackground(color);
		setLayout(new GridBagLayout());
		// Set constraints to put it in the north east (top right)
		GridBagConstraints fuzzyConstraints = new GridBagConstraints(0, 0, 1, 1, 1, 1,
																		GridBagConstraints.NORTHEAST, GridBagConstraints.NONE,
																		new Insets(0, 0, 0, 0), 0, 0);
		// Set constraints for the bottom right
		GridBagConstraints stackConstraints = new GridBagConstraints(0, 0, 1, 1, 1, 1,
																		GridBagConstraints.SOUTHEAST, GridBagConstraints.NONE,
																		new Insets(0, 0, 0, 0), 0, 0);
		add(imageLabel);
		add(fuzzyIndicator, fuzzyConstraints);
		add(stackIndicator, stackConstraints);
	}

	public void setImageLabel(String toolTip, BufferedImage itemImage, boolean isFuzzy, InventorySetupsStackCompareID stackCompare)
	{
		if (itemImage == null || toolTip == null)
		{
			imageLabel.setToolTipText("");
			imageLabel.setIcon(null);
			imageLabel.revalidate();
		}
		else
		{
			imageLabel.setToolTipText(toolTip);
			if (itemImage instanceof AsyncBufferedImage) // if the slot is a spellbook, use these
			{
				AsyncBufferedImage itemImageAsync = (AsyncBufferedImage)itemImage;
				itemImageAsync.addTo(imageLabel);
			}
			else
			{
				imageLabel.setIcon(new ImageIcon(itemImage));
			}

		}

		fuzzyIndicator.setText(isFuzzy ? "*" : "");
		stackIndicator.setText(InventorySetupsStackCompareID.getStringFromValue(stackCompare));

		validate();
		repaint();
	}

	public void setImageLabel(String toolTip, BufferedImage itemImage)
	{
		setImageLabel(toolTip, itemImage, false, InventorySetupsStackCompareID.None);
	}

	// adds the menu option to update a slot from the container it presides in
	public static void addUpdateFromContainerMouseListenerToSlot(final MInventorySetupsPlugin plugin, final InventorySetupsSlot slot)
	{
		String updateContainerFrom = getContainerString(slot);
		JMenuItem updateFromContainer = new JMenuItem("Update Slot from " + updateContainerFrom);
		slot.getRightClickMenu().add(updateFromContainer);
		updateFromContainer.addActionListener(e ->
		{
			plugin.updateSlotFromContainer(slot, false);
		});
	}

	// adds the option replace all slots containing the old item with the new item in all setups
	public static void addUpdateFromContainerToAllInstancesMouseListenerToSlot(final JPanel parentPanel, final MInventorySetupsPlugin plugin, final InventorySetupsSlot slot)
	{
		String updateContainerFrom = getContainerString(slot);
		JMenuItem updateFromContainer = new JMenuItem("Update ALL Slots from " + updateContainerFrom);
		slot.getShiftRightClickMenu().add(updateFromContainer);
		updateFromContainer.addActionListener(e ->
		{
			int confirm = JOptionPane.showConfirmDialog(parentPanel,
					"Do you want to update ALL setups which have this item to the new item?",
					"Update ALL Setups", JOptionPane.OK_CANCEL_OPTION);

			if (confirm == JOptionPane.YES_OPTION)
			{
				plugin.updateSlotFromContainer(slot, true);
			}
		});
	}

	// adds the menu option to update a slot from item search
	public static void addUpdateFromSearchMouseListenerToSlot(final MInventorySetupsPlugin plugin, final InventorySetupsSlot slot, boolean allowStackable)
	{
		JMenuItem updateFromSearch = new JMenuItem("Update Slot from Search");
		slot.getRightClickMenu().add(updateFromSearch);
		updateFromSearch.addActionListener(e ->
		{
			plugin.updateSlotFromSearch(slot, allowStackable, false);
		});
	}

	// adds the option replace all slots containing the old item with the newly searched item in all setups
	public static void addUpdateFromSearchToAllInstancesMouseListenerToSlot(final JPanel parentPanel, final MInventorySetupsPlugin plugin, final InventorySetupsSlot slot, boolean allowStackable)
	{
		JMenuItem updateFromContainer = new JMenuItem("Update ALL Slots from Search");
		slot.getShiftRightClickMenu().add(updateFromContainer);
		updateFromContainer.addActionListener(e ->
		{
			int confirm = JOptionPane.showConfirmDialog(parentPanel,
					"Do you want to update ALL setups which have this item to the new item?",
					"Update ALL Setups", JOptionPane.OK_CANCEL_OPTION);

			if (confirm == JOptionPane.YES_OPTION)
			{
				plugin.updateSlotFromSearch(slot, allowStackable, true);
			}
		});
	}

	// adds the menu option to clear a slot
	public static void addRemoveMouseListenerToSlot(final MInventorySetupsPlugin plugin, final InventorySetupsSlot slot)
	{
		JMenuItem removeSlot = new JMenuItem("Remove Item from Slot");
		slot.getRightClickMenu().add(removeSlot);
		removeSlot.addActionListener(e ->
		{
			plugin.removeItemFromSlot(slot);
		});
	}

	// adds the menu option to update set a slot to fuzzy
	public static void addFuzzyMouseListenerToSlot(final MInventorySetupsPlugin plugin, final InventorySetupsSlot slot)
	{
		JMenuItem makeSlotFuzzy = new JMenuItem("Toggle Fuzzy");
		slot.getRightClickMenu().add(makeSlotFuzzy);
		makeSlotFuzzy.addActionListener(e ->
		{
			plugin.toggleFuzzyOnSlot(slot);
		});
	}

	// adds the menu option to update set a slot to fuzzy
	public static void addStackMouseListenerToSlot(final MInventorySetupsPlugin plugin, final InventorySetupsSlot slot)
	{
		JMenuItem stackIndicatorNone = new JMenuItem("Stack Difference None");
		stackIndicatorNone.addActionListener(e ->
		{
			plugin.setStackCompareOnSlot(slot, InventorySetupsStackCompareID.None);
		});

		JMenuItem stackIndicatorStandard = new JMenuItem("Stack Difference Standard");
		stackIndicatorStandard.addActionListener(e ->
		{
			plugin.setStackCompareOnSlot(slot, InventorySetupsStackCompareID.Standard);
		});

		JMenuItem stackIndicatorGreaterThan = new JMenuItem("Stack Difference Greater Than");
		stackIndicatorGreaterThan.addActionListener(e ->
		{
			plugin.setStackCompareOnSlot(slot, InventorySetupsStackCompareID.Greater_Than);
		});

		JMenuItem stackIndicatorLessThan = new JMenuItem("Stack Difference Less Than");
		stackIndicatorLessThan.addActionListener(e ->
		{
			plugin.setStackCompareOnSlot(slot, InventorySetupsStackCompareID.Less_Than);
		});

		JMenu stackIndicatorMainMenu = new JMenu("Stack Indicator");
		stackIndicatorMainMenu.add(stackIndicatorNone);
		stackIndicatorMainMenu.add(stackIndicatorStandard);
		stackIndicatorMainMenu.add(stackIndicatorLessThan);
		stackIndicatorMainMenu.add(stackIndicatorGreaterThan);
		slot.getRightClickMenu().add(stackIndicatorMainMenu);
	}

	public static String getContainerString(final InventorySetupsSlot slot)
	{
		String updateContainerFrom = "";
		switch (slot.getSlotID())
		{
			case INVENTORY:
				updateContainerFrom = "Inventory";
				break;
			case EQUIPMENT:
				updateContainerFrom = "Equipment";
				break;
			default:
				return InventorySetupsAmmoHandler.getSpecialContainerString(slot);
		}
		return updateContainerFrom;
	}

	// Sets the image and tooltip text for a slot
	public static void setSlotImageAndText(final ItemManager itemManager, final InventorySetupsSlot containerSlot, final InventorySetup setup, final InventorySetupsItem item)
	{
		containerSlot.setParentSetup(setup);

		if (item.getId() == -1)
		{
			containerSlot.setImageLabel(null, null, item.isFuzzy(), item.getStackCompare());
			return;
		}

		int itemId = item.getId();
		int quantity = item.getQuantity();
		final String itemName = item.getName();
		AsyncBufferedImage itemImg = itemManager.getImage(itemId, quantity, quantity > 1);
		String toolTip = itemName;
		if (quantity > 1)
		{
			toolTip += " (" + quantity + ")";
		}
		containerSlot.setImageLabel(toolTip, itemImg, item.isFuzzy(), item.getStackCompare());
	}

	// highlights the slot based on the configuration and the saved item vs item in the slot
	public static void highlightSlot(final InventorySetup setup, InventorySetupsItem savedItemFromSetup, InventorySetupsItem currentItemFromContainer, final InventorySetupsSlot containerSlot)
	{
		// important note: do not use item names for comparisons
		// they are all empty to avoid clientThread usage when highlighting

		// first check if stack differences are enabled and compare quantities
		if (shouldHighlightSlotBasedOnStack(savedItemFromSetup.getStackCompare(), savedItemFromSetup.getQuantity(), currentItemFromContainer.getQuantity()))
		{
			containerSlot.setBackground(setup.getHighlightColor());
			return;
		}

		// obtain the correct item ids using fuzzy mapping if applicable
		int currentItemId = currentItemFromContainer.getId();
		int savedItemId = savedItemFromSetup.getId();

		if (savedItemFromSetup.isFuzzy())
		{
			currentItemId = InventorySetupsVariationMapping.map(currentItemId);
			savedItemId = InventorySetupsVariationMapping.map(savedItemId);
		}

		// if the ids don't match, highlight the container slot
		if (currentItemId != savedItemId)
		{
			containerSlot.setBackground(setup.getHighlightColor());
			return;
		}

		// set the color back to the original, because they match
		containerSlot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
	}

	public static boolean shouldHighlightSlotBasedOnStack(final InventorySetupsStackCompareID stackCompareType, final Integer savedItemQty, final Integer currItemQty)
	{
		final int stackCompareResult = Integer.compare(currItemQty, savedItemQty);
		return stackCompareType == InventorySetupsStackCompareID.Less_Than && stackCompareResult < 0 ||
				stackCompareType == InventorySetupsStackCompareID.Greater_Than && stackCompareResult > 0 ||
				stackCompareType == InventorySetupsStackCompareID.Standard && stackCompareResult != 0;
	}

}
