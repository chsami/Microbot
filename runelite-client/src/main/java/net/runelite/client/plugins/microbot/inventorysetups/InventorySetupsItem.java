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

import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.microbot.Microbot;

@AllArgsConstructor
public class InventorySetupsItem
{
	@Getter
	private final int id;
	@Setter
	private String name;
	@Getter
	@Setter
	private int quantity;
	@Getter
	@Setter
	private boolean fuzzy;
	@Getter
	@Setter
	private InventorySetupsStackCompareID stackCompare;
	@Getter
	@Setter
	private boolean locked;
	@Getter
	@Setter
	private int slot = -1;

	public void toggleIsFuzzy()
	{
		fuzzy = !fuzzy;
	}
	public void toggleIsLocked()
	{
		locked = !locked;
	}

	public InventorySetupsItem copyToSlot(final int newSlot)
	{
		return new InventorySetupsItem(id, name, quantity, fuzzy, stackCompare, locked, newSlot);
	}

	public static InventorySetupsItem getDummyItem()
	{
		return new InventorySetupsItem(-1, "", 0, false, InventorySetupsStackCompareID.None, false, -1);
	}

	public static boolean itemIsDummy(final InventorySetupsItem item)
	{
		// Don't use the name to compare
		return item.getId() == -1 &&
				item.getQuantity() == 0 &&
				!item.isFuzzy() &&
				(item.getStackCompare() == InventorySetupsStackCompareID.None || item.getStackCompare() == null) &&
				!item.isLocked() &&
				item.getSlot() == -1;
	}

	public static boolean isEmptySlot(final InventorySetupsItem item)
	{
		return item == null || item.getId() == -1;
	}

	public String getName() {
		String itemName = name;

		if (isFuzzy()) {
			String[] splitItemName = itemName.split("\\(\\d+\\)$");
			itemName = (splitItemName.length == 0) ? itemName : splitItemName[0];
		}

		String lowerCaseName = itemName.toLowerCase();

		if (isBarrowsItem(lowerCaseName)) {
			itemName = itemName.replaceAll("\\s+[1-9]\\d*$", "");
		}

		return itemName;
	}

	public boolean matches(InventorySetupsItem item) {
		return isFuzzy() ? this.getName().toLowerCase().contains(item.getName().toLowerCase()) : Objects.equals(this.getId(), item.getId());
	}

	public static boolean isBarrowsItem(String lowerCaseName) {
		return !lowerCaseName.endsWith(" 0") && (
				lowerCaseName.contains("dharok's") ||
				lowerCaseName.contains("ahrim's") ||
				lowerCaseName.contains("guthan's") ||
				lowerCaseName.contains("torag's") ||
				lowerCaseName.contains("verac's") ||
				lowerCaseName.contains("karil's")
		);
	}
}