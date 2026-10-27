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
 * The Grand Exchange buy offer this script placed, identified by the slot it filled. Rs2GrandExchange can only abort
 * by item name, which could cancel one of the player's own offers for the same item.
 */
final class OwnOffer
{
	/** Pass to {@link #pick} to accept any new offer regardless of its item. */
	static final int NO_ITEM = -1;

	/** Child of the offers interface holding slot ONE; the other slots follow in order. */
	private static final int FIRST_SLOT_CHILD = 7;
	private static final int ABORT_IDENTIFIER = 2;
	private static final int ABORT_PARAM0 = 2;

	private OwnOffer()
	{
	}

	/** Which slots are empty right now; take this before placing an offer so the new one can be told apart. */
	static boolean[] emptySlots()
	{
		GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
		boolean[] empty = new boolean[offers.length];
		for (int i = 0; i < offers.length; i++)
		{
			empty[i] = offers[i].getState() == GrandExchangeOfferState.EMPTY;
		}
		return empty;
	}

	/** The slot that was empty before and now buys (or bought) {@code itemId}, or null. */
	static GrandExchangeSlots find(boolean[] emptyBefore, int itemId)
	{
		return slotAt(pickFromClient(emptyBefore, itemId));
	}

	/** Fallback: the slot that was empty before and now holds any offer, or null. */
	static GrandExchangeSlots findNewOffer(boolean[] emptyBefore)
	{
		return slotAt(pickFromClient(emptyBefore, NO_ITEM));
	}

	/**
	 * Pure pick of the new offer's slot index, or -1. Only slots that were empty before are considered, so an
	 * existing offer is never touched. With an item id the offer must be buying or bought that item; with
	 * {@link #NO_ITEM} any non-empty state qualifies.
	 */
	static int pick(boolean[] emptyBefore, GrandExchangeOfferState[] states, int[] itemIds, int itemId)
	{
		for (int i = 0; i < emptyBefore.length && i < states.length; i++)
		{
			if (!emptyBefore[i])
			{
				continue;
			}
			GrandExchangeOfferState state = states[i];
			boolean matches = itemId == NO_ITEM
				? state != GrandExchangeOfferState.EMPTY
				: (state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT) && itemIds[i] == itemId;
			if (matches)
			{
				return i;
			}
		}
		return -1;
	}

	private static int pickFromClient(boolean[] emptyBefore, int itemId)
	{
		GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
		GrandExchangeOfferState[] states = new GrandExchangeOfferState[offers.length];
		int[] itemIds = new int[offers.length];
		for (int i = 0; i < offers.length; i++)
		{
			states[i] = offers[i].getState();
			itemIds[i] = offers[i].getItemId();
		}
		return pick(emptyBefore, states, itemIds, itemId);
	}

	private static GrandExchangeSlots slotAt(int index)
	{
		return index < 0 ? null : GrandExchangeSlots.values()[index];
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
