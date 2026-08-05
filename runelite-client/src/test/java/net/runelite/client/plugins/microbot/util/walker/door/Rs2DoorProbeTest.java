package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.Rs2TerminalTravelMode;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for {@link Rs2DoorProbe#isDoorLikeCatalogTransport} — whether a catalog transport is
 * really a walk-through door/gate (by name, display info, or a door-like menu action) versus a genuine
 * transport (ladder, stairs, cave). This gate is what keeps door handling from hijacking real transports;
 * pinning it under the harness is part of the door detection-layer coverage.
 */
public class Rs2DoorProbeTest {

	private static Rs2TransportEdge transport(
		Rs2TransportType type, String name, String displayInfo, String action) {
		return new Rs2TransportEdge(
			new WorldPoint(3200, 3200, 0),
			new WorldPoint(3200, 3201, 0),
			type,
			Rs2TransportExecutor.OBJECT,
			Rs2TerminalTravelMode.UNSUPPORTED,
			displayInfo,
			action,
			name,
			1,
			1,
			false,
			false,
			false,
			0,
			"",
			0,
			Collections.emptyList());
    }

    @Test
    public void doorLikeByName() {
        assertTrue(Rs2DoorProbe.isDoorLikeCatalogTransport(
				transport(Rs2TransportType.TRANSPORT, "Gate", null, "Open")));
    }

    @Test
    public void doorLikeByDisplayInfo() {
        assertTrue(Rs2DoorProbe.isDoorLikeCatalogTransport(
				transport(Rs2TransportType.TRANSPORT, "Anonymous object", "Large door", null)));
    }

    @Test
    public void doorLikeByAction() {
        // Neutral name/display, but an "Open" action is a door-walk action -> classified door-like.
        assertTrue(Rs2DoorProbe.isDoorLikeCatalogTransport(
				transport(Rs2TransportType.TRANSPORT, "Anonymous object", "Anonymous object", "Open")));
    }

    @Test
    public void genuineTransportIsNotDoorLike() {
        // A ladder with a Climb action is a real transport, not a door.
        assertFalse(Rs2DoorProbe.isDoorLikeCatalogTransport(
				transport(Rs2TransportType.TRANSPORT, "Ladder", "Ladder", "Climb")));
    }

    @Test
    public void nonTransportTypeIsNeverDoorLike() {
        // Only TRANSPORT-type rows are considered; an agility shortcut named "Gate" must not qualify.
        assertFalse(Rs2DoorProbe.isDoorLikeCatalogTransport(
				transport(Rs2TransportType.AGILITY_SHORTCUT, "Gate", "Gate", "Open")));
    }

    @Test
    public void nullIsNotDoorLike() {
        assertFalse(Rs2DoorProbe.isDoorLikeCatalogTransport(null));
    }
}
