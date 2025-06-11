/*
 * Copyright (c) 2021, rbbi <https://github.com/rbbi>
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
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

/**
 * @author robbie, created on 23/09/2021 20:34
 */
public abstract class InventorySetupsAmmunitionPanel extends InventorySetupsContainerPanel
{

	List<InventorySetupsSlot> ammoSlots;

	GridLayout gridLayout;

	List<Boolean> ammoSlotsAddedToPanel;

	InventorySetupsAmmunitionPanel(ItemManager itemManager, MInventorySetupsPlugin plugin, String captionText)
	{
		super(itemManager, plugin, captionText);
	}

	@Override
	public boolean isStackCompareForSlotAllowed(int id)
	{
		return true;
	}

	@Override
	public void setupContainerPanel(JPanel containerSlotsPanel)
	{
		ammoSlots = new ArrayList<>();
		ammoSlotsAddedToPanel = new ArrayList<>();
		for (int i = 0; i < getSlotsCount(); i++)
		{
			ammoSlots.add(new InventorySetupsSlot(ColorScheme.DARKER_GRAY_COLOR, getSlotId(), i));
			ammoSlotsAddedToPanel.add(Boolean.TRUE);
		}

		this.gridLayout = new GridLayout(1, 4, 1, 1);
		containerSlotsPanel.setLayout(gridLayout);

		for (final InventorySetupsSlot slot : ammoSlots)
		{
			containerSlotsPanel.add(slot);
			InventorySetupsSlot.addStackMouseListenerToSlot(plugin, slot);
			InventorySetupsSlot.addUpdateFromContainerMouseListenerToSlot(plugin, slot);
			InventorySetupsSlot.addUpdateFromSearchMouseListenerToSlot(plugin, slot, true);
			InventorySetupsSlot.addRemoveMouseListenerToSlot(plugin, slot);
		}
	}

	protected abstract InventorySetupsSlotID getSlotId();

	protected abstract int getSlotsCount();

	@Override
	public void highlightSlots(List<InventorySetupsItem> currentContainer, InventorySetup inventorySetup)
	{
		assert getContainer(inventorySetup) != null : "Container is null.";

		int slotsCount = getSlotsCount();
		assert slotsCount == getSlotsCount() : "Incorrect size";

		isHighlighted = true;

		final List<InventorySetupsItem> ammoContainer = getContainer(inventorySetup);

		List<InventorySetupsItem> currentContainerReference = currentContainer;

		if (currentContainer.size() < ammoContainer.size())
		{
			// Fill the current container to the size of the saved container for easier comparison
			final List<InventorySetupsItem> currentContainerCopy = new ArrayList<>(currentContainer);
			for (int i = currentContainerCopy.size(); i < ammoContainer.size(); i++)
			{
				currentContainerCopy.add(InventorySetupsItem.getDummyItem());
			}
			currentContainerReference = currentContainerCopy;
		}


		for (int i = 0; i < ammoContainer.size(); i++)
		{
			boolean shouldHighlightSlot = false;
			boolean foundAmmo = false;
			int currentContainerIndex = -1;
			for (int j = 0; j < currentContainerReference.size(); j++)
			{
				if (ammoContainer.get(i).getId() == currentContainerReference.get(j).getId())
				{
					foundAmmo = true;
					currentContainerIndex = j;
					break;
				}
			}

			if (foundAmmo)
			{
				int savedQuantity = ammoContainer.get(i).getQuantity();
				int currentQuantity = currentContainerReference.get(currentContainerIndex).getQuantity();
				if (InventorySetupsSlot.shouldHighlightSlotBasedOnStack(ammoContainer.get(i).getStackCompare(), savedQuantity, currentQuantity))
				{
					shouldHighlightSlot = true;
				}
			}
			else
			{
				shouldHighlightSlot = true;
			}

			if (shouldHighlightSlot)
			{
				ammoSlots.get(i).setBackground(inventorySetup.getHighlightColor());
			}
			else
			{
				ammoSlots.get(i).setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}

		}

	}

	protected abstract List<InventorySetupsItem> getContainer(InventorySetup inventorySetup);

	@Override
	public void updatePanelWithSetupInformation(InventorySetup setup)
	{
		List<InventorySetupsItem> container = getContainer(setup);
		if (container != null)
		{
			// grid layout is dumb, it won't center the panel if a slot is invisible, so we have to remove the slot instead...
			// Make sure to set the columns as well.
			gridLayout.setColumns(container.size());
			for (int i = 0; i < ammoSlots.size(); i++)
			{
				if (i >= container.size())
				{
					if (ammoSlotsAddedToPanel.get(i))
					{
						ammoSlotsAddedToPanel.set(i, Boolean.FALSE);
						this.getContainerSlotsPanel().remove(ammoSlots.get(i));
					}
				}
				else
				{
					if (!ammoSlotsAddedToPanel.get(i))
					{
						ammoSlotsAddedToPanel.set(i, Boolean.TRUE);
						this.getContainerSlotsPanel().add(ammoSlots.get(i));
					}
					InventorySetupsSlot.setSlotImageAndText(itemManager, ammoSlots.get(i), setup, container.get(i));
				}
			}
		}
		else
		{
			for (int i = 0; i < ammoSlots.size(); i++)
			{
				InventorySetupsSlot.setSlotImageAndText(itemManager, ammoSlots.get(i), setup, InventorySetupsItem.getDummyItem());
			}
		}

		validate();
		repaint();
	}

	@Override
	public void resetSlotColors()
	{
		if (!isHighlighted)
		{
			return;
		}
		for (final InventorySetupsSlot slot : ammoSlots)
		{
			slot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		}
		isHighlighted = false;
	}

	public void highlightAllSlots(final InventorySetup setup)
	{
		for (final InventorySetupsSlot slot : ammoSlots)
		{
			slot.setBackground(setup.getHighlightColor());
		}
		isHighlighted = true;
	}
}
