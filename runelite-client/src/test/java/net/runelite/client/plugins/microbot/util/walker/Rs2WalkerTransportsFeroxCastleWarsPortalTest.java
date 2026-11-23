package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerTransportsFeroxCastleWarsPortalTest
{
	private static final WorldPoint FEROX_PORTAL_ORIGIN = new WorldPoint(3147, 3638, 0);
	private static final WorldPoint CASTLE_WARS_LANDING = new WorldPoint(2440, 3089, 0);

	private static Transport portal(TransportType type, int objectId)
	{
		return new Transport(FEROX_PORTAL_ORIGIN, CASTLE_WARS_LANDING, "", type, false,
			"Enter", "Castle Wars portal", objectId);
	}

	@Test
	public void feroxCastleWarsPortalRowIsRecognised()
	{
		assertTrue(Rs2WalkerTransports.isFeroxCastleWarsPortalTransport(
			portal(TransportType.TELEPORTATION_PORTAL, ObjectID.CASTLEWARS_F2P_PORTAL)));
	}

	@Test
	public void otherPortalsAndTypesAreNotRecognised()
	{
		assertFalse(Rs2WalkerTransports.isFeroxCastleWarsPortalTransport(
			portal(TransportType.TELEPORTATION_PORTAL, 4387)));
		assertFalse(Rs2WalkerTransports.isFeroxCastleWarsPortalTransport(
			portal(TransportType.TRANSPORT, ObjectID.CASTLEWARS_F2P_PORTAL)));
		assertFalse(Rs2WalkerTransports.isFeroxCastleWarsPortalTransport(null));
	}

	@Test
	public void landingIsSeenAtCastleWars()
	{
		Transport transport = portal(TransportType.TELEPORTATION_PORTAL, ObjectID.CASTLEWARS_F2P_PORTAL);
		assertTrue(Rs2WalkerTransports.hasLandedFromFeroxCastleWarsPortal(transport, new WorldPoint(2439, 3090, 0)));
		assertTrue(Rs2WalkerTransports.hasLandedFromFeroxCastleWarsPortal(transport, CASTLE_WARS_LANDING));
	}

	@Test
	public void stillInFeroxWithDialogueOpenIsNotALanding()
	{
		Transport transport = portal(TransportType.TELEPORTATION_PORTAL, ObjectID.CASTLEWARS_F2P_PORTAL);
		assertFalse(Rs2WalkerTransports.hasLandedFromFeroxCastleWarsPortal(transport, FEROX_PORTAL_ORIGIN));
		assertFalse(Rs2WalkerTransports.hasLandedFromFeroxCastleWarsPortal(transport, new WorldPoint(2440, 3089, 1)));
		assertFalse(Rs2WalkerTransports.hasLandedFromFeroxCastleWarsPortal(transport, null));
	}
}
