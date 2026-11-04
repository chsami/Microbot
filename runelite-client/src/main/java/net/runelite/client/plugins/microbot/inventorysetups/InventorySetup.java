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
package net.runelite.client.plugins.microbot.inventorysetups;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
public class InventorySetup implements InventorySetupsDisplayAttributes
{
	@Getter
	private List<InventorySetupsItem> inventory;

	@Getter
	private List<InventorySetupsItem> equipment;

	@Getter
	private List<InventorySetupsItem> rune_pouch;

	@Getter
	private List<InventorySetupsItem> boltPouch;

	@Getter
	private List<InventorySetupsItem> quiver;

	@Getter
	private Map<Integer, InventorySetupsItem> additionalFilteredItems;

	@Getter
	@Setter
	private String name;

	@Getter
	@Setter
	private String notes;

	@Getter
	@Setter
	private Color highlightColor;

	@Getter
	@Setter
	private boolean highlightDifference;

	@Getter
	@Setter
	private Color displayColor;

	@Getter
	@Setter
	private boolean filterBank;

	@Getter
	@Setter
	private boolean unorderedHighlight;

	/*
		0 = Standard
		1 = Ancient
		2 = Lunar
		3 = Arceuus
		4 = NONE

		Avoiding Enum because won't work well with GSON (defaults to null)
	*/
	@Getter
	@Setter
	private int spellBook;

	@Getter
	@Setter
	private boolean favorite;

	@Getter
	@Setter
	private int iconID;

	public void updateInventory(final List<InventorySetupsItem> inv)
	{
		inventory = padToSize(inv, MInventorySetupsPlugin.NUM_INVENTORY_ITEMS);
	}

	public void updateEquipment(final List<InventorySetupsItem> eqp)
	{
		equipment = padToSize(eqp, MInventorySetupsPlugin.NUM_EQUIPMENT_ITEMS);
	}

	public void padContainers()
	{
		updateInventory(inventory);
		updateEquipment(equipment);
	}

	private static List<InventorySetupsItem> padToSize(final List<InventorySetupsItem> items, final int size)
	{
		if (items == null || items.size() >= size)
		{
			return items;
		}
		final List<InventorySetupsItem> padded = new ArrayList<>(items);
		while (padded.size() < size)
		{
			padded.add(InventorySetupsItem.getDummyItem());
		}
		return padded;
	}

	public void updateRunePouch(final List<InventorySetupsItem> rp)
	{
		rune_pouch = rp;
	}

	public void updateBoltPouch(final List<InventorySetupsItem> bp)
	{
		boltPouch = bp;
	}

	public void updateQuiver(final List<InventorySetupsItem> q)
	{
		quiver = q;
	}

	public void updateAdditionalItems(final Map<Integer, InventorySetupsItem> ai)
	{
		additionalFilteredItems = ai;
	}

	public void updateSpellbook(final int sb)
	{
		spellBook = sb;
	}

	public void updateNotes(final String text)
	{
		notes = text;
	}

	public static List<InventorySetupsItem> getSetupItems(final InventorySetup setup)
	{
		List<InventorySetupsItem> itemsInSetup = new ArrayList<>();
		itemsInSetup.addAll(setup.getInventory());
		itemsInSetup.addAll(setup.getEquipment());
		itemsInSetup.addAll(setup.getAdditionalFilteredItems().values());
		if (setup.getRune_pouch() != null)
		{
			itemsInSetup.addAll(setup.getRune_pouch());
		}
		if (setup.getBoltPouch() != null)
		{
			itemsInSetup.addAll(setup.getBoltPouch());
		}
		if (setup.getQuiver() != null)
		{
			itemsInSetup.addAll(setup.getQuiver());
		}
		return itemsInSetup;
	}

}
