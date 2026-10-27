package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;

/**
 * The Grand Exchange buy offer this script placed, addressed by slot. Rs2GrandExchange can only abort by item name,
 * which could cancel one of the player's own offers for the same item.
 */
final class OwnOffer
{
	/** Child of the offers interface holding slot ONE; the other slots follow in order. */
	private static final int FIRST_SLOT_CHILD = 7;
	private static final int ABORT_IDENTIFIER = 2;
	private static final int ABORT_PARAM0 = 2;

	private OwnOffer()
	{
	}

	/** The slot with a buy offer for {@code itemId} (still buying, or already bought), or null. */
	static GrandExchangeSlots find(int itemId)
	{
		GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
		for (GrandExchangeSlots slot : GrandExchangeSlots.values())
		{
			if (slot.ordinal() >= offers.length)
			{
				break;
			}
			GrandExchangeOffer offer = offers[slot.ordinal()];
			boolean buy = offer.getState() == GrandExchangeOfferState.BUYING || offer.getState() == GrandExchangeOfferState.BOUGHT;
			if (buy && offer.getItemId() == itemId)
			{
				return slot;
			}
		}
		return null;
	}

	static boolean isBuying(GrandExchangeSlots slot)
	{
		return Microbot.getClient().getGrandExchangeOffers()[slot.ordinal()].getState() == GrandExchangeOfferState.BUYING;
	}

	/** Clicks "Abort offer" on exactly this slot; the server applies it a moment later. */
	static void abort(GrandExchangeSlots slot)
	{
		Object[] target = Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget widget = Rs2Widget.getWidget(InterfaceID.GE_OFFERS, FIRST_SLOT_CHILD + slot.ordinal());
			return widget == null ? null : new Object[]{widget.getId(), widget.getBounds()};
		}).orElse(null);
		if (target == null)
		{
			return;
		}
		Rectangle bounds = (Rectangle) target[1];
		NewMenuEntry entry = new NewMenuEntry()
			.option("Abort offer")
			.target("")
			.identifier(ABORT_IDENTIFIER)
			.type(MenuAction.CC_OP)
			.param0(ABORT_PARAM0)
			.param1((int) target[0])
			.itemId(-1)
			.forceLeftClick(false);
		Microbot.doInvoke(entry, bounds != null && Rs2UiHelper.isRectangleWithinCanvas(bounds)
			? bounds : Rs2UiHelper.getDefaultRectangle());
	}
}
