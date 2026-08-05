package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.shortestpath.TransportExecutionRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2HotAirBalloonTest
{
	@Test
	public void everyRegisteredDestinationHasTheCanonicalMapButton()
	{
		assertEquals(InterfaceID.ZepBalloonMap.BTN_CAST,
			Rs2HotAirBalloon.destinationButton(
				TransportExecutionRegistry.BalloonDestination.CASTLE_WARS));
		assertEquals(InterfaceID.ZepBalloonMap.BTN_GNO,
			Rs2HotAirBalloon.destinationButton(
				TransportExecutionRegistry.BalloonDestination.GRAND_TREE));
		assertEquals(InterfaceID.ZepBalloonMap.BTN_CRAFT,
			Rs2HotAirBalloon.destinationButton(
				TransportExecutionRegistry.BalloonDestination.CRAFTING_GUILD));
		assertEquals(InterfaceID.ZepBalloonMap.BTN_ENT,
			Rs2HotAirBalloon.destinationButton(
				TransportExecutionRegistry.BalloonDestination.ENTRANA));
		assertEquals(InterfaceID.ZepBalloonMap.BTN_TAV,
			Rs2HotAirBalloon.destinationButton(
				TransportExecutionRegistry.BalloonDestination.TAVERLEY));
		assertEquals(InterfaceID.ZepBalloonMap.BTN_VARR,
			Rs2HotAirBalloon.destinationButton(
				TransportExecutionRegistry.BalloonDestination.VARROCK));
		assertEquals(-1, Rs2HotAirBalloon.destinationButton(null));
	}

	@Test
	public void basketLookupAcceptsBaseAndUnlockedStationTransforms()
	{
		assertTrue(Rs2HotAirBalloon.isBasketObjectId(ObjectID.ZEP_BASKET_ENTRANA));
		assertTrue(Rs2HotAirBalloon.isBasketObjectId(ObjectID.ZEP_BASKET));
		assertTrue(Rs2HotAirBalloon.isBasketObjectId(ObjectID.ZEP_MULTI_BASKET_ENTRANA));
		assertTrue(Rs2HotAirBalloon.isBasketObjectId(ObjectID.ZEP_MULTI_BASKET_TAV));
		assertTrue(Rs2HotAirBalloon.isBasketObjectId(ObjectID.ZEP_MULTI_BASKET_CAST));
		assertTrue(Rs2HotAirBalloon.isBasketObjectId(ObjectID.ZEP_MULTI_BASKET_GNO));
		assertTrue(Rs2HotAirBalloon.isBasketObjectId(ObjectID.ZEP_MULTI_BASKET_CRAFT));
		assertTrue(Rs2HotAirBalloon.isBasketObjectId(ObjectID.ZEP_MULTI_BASKET_VARR));
		assertFalse(Rs2HotAirBalloon.isBasketObjectId(ObjectID.ZEP_BALLOON));
	}
}
