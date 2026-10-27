package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static net.runelite.api.GrandExchangeOfferState.BOUGHT;
import static net.runelite.api.GrandExchangeOfferState.BUYING;
import static net.runelite.api.GrandExchangeOfferState.EMPTY;
import static net.runelite.api.GrandExchangeOfferState.SELLING;
import static org.junit.Assert.assertEquals;

public class OwnOfferTest
{
	private static final int ADAMANT_AXE = 1357;

	@Test
	public void picksTheNewlyFilledSlotForTheItem()
	{
		boolean[] before = {false, true, true};
		GrandExchangeOfferState[] states = {BUYING, EMPTY, BUYING};
		int[] ids = {ADAMANT_AXE, 0, ADAMANT_AXE};
		// Slot 0 already held the player's own adamant axe offer; ours is slot 2.
		assertEquals(2, OwnOffer.pick(before, states, ids, ADAMANT_AXE));
	}

	@Test
	public void neverPicksASlotThatWasOccupiedBefore()
	{
		boolean[] before = {false, false};
		GrandExchangeOfferState[] states = {BUYING, SELLING};
		int[] ids = {ADAMANT_AXE, ADAMANT_AXE};
		assertEquals(-1, OwnOffer.pick(before, states, ids, ADAMANT_AXE));
		assertEquals(-1, OwnOffer.pick(before, states, ids, OwnOffer.NO_ITEM));
	}

	@Test
	public void acceptsAnInstantlyBoughtOfferButNotASellOrOtherItem()
	{
		boolean[] before = {true, true, true};
		GrandExchangeOfferState[] states = {SELLING, BUYING, BOUGHT};
		int[] ids = {ADAMANT_AXE, 1355, ADAMANT_AXE};
		assertEquals(2, OwnOffer.pick(before, states, ids, ADAMANT_AXE));
	}

	@Test
	public void fallbackTakesAnyNewOfferByStateAlone()
	{
		boolean[] before = {false, true, true};
		GrandExchangeOfferState[] states = {BUYING, EMPTY, BUYING};
		int[] ids = {1, 0, 0};
		assertEquals(2, OwnOffer.pick(before, states, ids, OwnOffer.NO_ITEM));
	}

	@Test
	public void nothingNewMeansNoPick()
	{
		boolean[] before = {true, true};
		GrandExchangeOfferState[] states = {EMPTY, EMPTY};
		assertEquals(-1, OwnOffer.pick(before, states, new int[2], ADAMANT_AXE));
		assertEquals(-1, OwnOffer.pick(before, states, new int[2], OwnOffer.NO_ITEM));
	}
}
