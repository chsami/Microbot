/*
 * Copyright (c) 2021, Zoinkwiz <https://github.com/Zoinkwiz>
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
package net.runelite.client.plugins.microbot.questhelper.bank.banktab;

import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;

public class BankWidget
{
	public Widget itemWidget;

	public BankWidget(Widget itemWidget)
	{
		this.itemWidget = itemWidget;
	}

	public boolean isPointOverWidget(Point point)
	{
		return itemWidget.contains(point);
	}

	public int getItemID()
	{
		return itemWidget.getItemId();
	}

	public int getItemQuantity()
	{
		return itemWidget.getItemQuantity();
	}

	public void swap(BankWidget otherWidget)
	{
		int otherXItem = otherWidget.itemWidget.getOriginalX();
		int otherYItem = otherWidget.itemWidget.getOriginalY();

		otherWidget.swapPosition(otherWidget.itemWidget, itemWidget);
		swapPosition(itemWidget, otherXItem, otherYItem);
	}

	private void swapPosition(Widget thisWidget, Widget otherWidget)
	{
		thisWidget.setOriginalX(otherWidget.getOriginalX());
		thisWidget.setOriginalY(otherWidget.getOriginalY());
		thisWidget.revalidate();
	}

	private void swapPosition(Widget thisWidget, int x, int y)
	{
		thisWidget.setOriginalX(x);
		thisWidget.setOriginalY(y);
		thisWidget.revalidate();
	}
}
