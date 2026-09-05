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

    /**
     * The regression this class exists for after the Ardougne stile. A Stile is named door-like and
     * would classify as a door on its name alone — but it is crossed by climbing over it, and the
     * door cascade can only wait for an edge to open. That wait timed out
     * ({@code door_edge_post_unresolved}) and cost twenty seconds of refused clicks, a recovery
     * wander and a replan before the transport handler crossed it in a single action.
     */
    @Test
    public void aMovesYouObstacleIsNotDoorLikeEvenWhenItsNameIs() {
        assertFalse("a Climb-over stile belongs to the transport handler, not the door cascade",
                Rs2DoorProbe.isDoorLikeCatalogTransport(
                        transport(Rs2TransportType.TRANSPORT, "Stile", "Stile", "Climb-over")));
        assertFalse(Rs2DoorProbe.isDoorLikeCatalogTransport(
                transport(Rs2TransportType.TRANSPORT, "Gate", "Gate", "Squeeze-through")));
        assertFalse(Rs2DoorProbe.isDoorLikeCatalogTransport(
                transport(Rs2TransportType.TRANSPORT, "Gangplank", "Gangplank", "Cross")));
    }

    /** Opening actions are untouched: a named gate you Open is still the door cascade's job. */
    @Test
    public void anOpeningActionIsStillDoorLike() {
        assertTrue(Rs2DoorProbe.isDoorLikeCatalogTransport(
                transport(Rs2TransportType.TRANSPORT, "Gate", "Gate", "Open")));
        assertTrue(Rs2DoorProbe.isDoorLikeCatalogTransport(
                transport(Rs2TransportType.TRANSPORT, "Door", "Door", "Walk-through")));
    }

	@Test
	public void draynorPuzzleDoorIsOwnedByItsObjectExecutor() {
		Rs2TransportEdge puzzleDoor = transport(
				Rs2TransportType.TRANSPORT, "Door", "Draynor basement puzzle door", "Open");

		assertTrue("door semantics remain available independently of handler ownership",
				Rs2DoorProbe.isDoorLikeCatalogTransport(puzzleDoor));
		assertTrue("the puzzle transport owns this door through its object executor",
				Rs2DoorProbe.isObjectExecutorTransport(puzzleDoor));
	}

	@Test
	public void ordinaryOpeningDoorIsAlsoOwnedByItsObjectExecutor() {
		assertTrue(Rs2DoorProbe.isObjectExecutorTransport(
				transport(Rs2TransportType.TRANSPORT, "Gate", "Gate", "Open")));
	}

    @Test
    public void nullIsNotDoorLike() {
        assertFalse(Rs2DoorProbe.isDoorLikeCatalogTransport(null));
    }
}
