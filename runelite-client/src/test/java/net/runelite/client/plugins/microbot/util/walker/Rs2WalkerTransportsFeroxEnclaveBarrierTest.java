package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerTransportsFeroxEnclaveBarrierTest
{
	private static Transport barrier(int objectId)
	{
		return new Transport(new WorldPoint(3135, 3617, 0), new WorldPoint(3135, 3616, 0), "",
			TransportType.TRANSPORT, false, "Pass-Through", "Barrier", objectId);
	}

	@Test
	public void bothBarrierVariantsAreRecognised()
	{
		assertTrue(Rs2WalkerTransports.isFeroxEnclaveBarrierTransport(barrier(ObjectID.WILDY_HUB_ENTRY_BARRIER)));
		assertTrue(Rs2WalkerTransports.isFeroxEnclaveBarrierTransport(barrier(ObjectID.WILDY_HUB_ENTRY_BARRIER_M)));
	}

	@Test
	public void otherObjectsAreNotRecognised()
	{
		assertFalse(Rs2WalkerTransports.isFeroxEnclaveBarrierTransport(barrier(32153)));
		assertFalse(Rs2WalkerTransports.isFeroxEnclaveBarrierTransport(null));
	}

	@Test
	public void firstTimeWarningIsRecognised()
	{
		assertTrue(Rs2WalkerTransports.isFeroxEnclaveBarrierWarning(
			"When returning to the Enclave, if you are teleblocked, you will not be able to get back in."));
		assertFalse(Rs2WalkerTransports.isFeroxEnclaveBarrierWarning("Click here to continue"));
		assertFalse(Rs2WalkerTransports.isFeroxEnclaveBarrierWarning(""));
		assertFalse(Rs2WalkerTransports.isFeroxEnclaveBarrierWarning(null));
	}
}
