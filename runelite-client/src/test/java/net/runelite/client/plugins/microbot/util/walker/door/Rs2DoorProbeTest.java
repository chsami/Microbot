package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless tests for {@link Rs2DoorProbe#isDoorLikeCatalogTransport} — whether a catalog transport is
 * really a walk-through door/gate (by name, display info, or a door-like menu action) versus a genuine
 * transport (ladder, stairs, cave). This gate is what keeps door handling from hijacking real transports;
 * pinning it under the harness is part of the door detection-layer coverage.
 */
public class Rs2DoorProbeTest {

    private static Transport transport(TransportType type, String name, String displayInfo, String action) {
        Transport t = mock(Transport.class);
        when(t.getType()).thenReturn(type);
        when(t.getName()).thenReturn(name);
        when(t.getDisplayInfo()).thenReturn(displayInfo);
        when(t.getAction()).thenReturn(action);
        return t;
    }

    @Test
    public void doorLikeByName() {
        assertTrue(Rs2DoorProbe.isDoorLikeCatalogTransport(
                transport(TransportType.TRANSPORT, "Gate", null, "Open")));
    }

    @Test
    public void doorLikeByDisplayInfo() {
        assertTrue(Rs2DoorProbe.isDoorLikeCatalogTransport(
                transport(TransportType.TRANSPORT, "Anonymous object", "Large door", null)));
    }

    @Test
    public void doorLikeByAction() {
        // Neutral name/display, but an "Open" action is a door-walk action -> classified door-like.
        assertTrue(Rs2DoorProbe.isDoorLikeCatalogTransport(
                transport(TransportType.TRANSPORT, "Anonymous object", "Anonymous object", "Open")));
    }

    @Test
    public void genuineTransportIsNotDoorLike() {
        // A ladder with a Climb action is a real transport, not a door.
        assertFalse(Rs2DoorProbe.isDoorLikeCatalogTransport(
                transport(TransportType.TRANSPORT, "Ladder", "Ladder", "Examine")));
    }

    @Test
    public void nonTransportTypeIsNeverDoorLike() {
        // Only TRANSPORT-type rows are considered; an agility shortcut named "Gate" must not qualify.
        assertFalse(Rs2DoorProbe.isDoorLikeCatalogTransport(
                transport(TransportType.AGILITY_SHORTCUT, "Gate", "Gate", "Open")));
    }

    @Test
    public void nullIsNotDoorLike() {
        assertFalse(Rs2DoorProbe.isDoorLikeCatalogTransport(null));
    }
}
