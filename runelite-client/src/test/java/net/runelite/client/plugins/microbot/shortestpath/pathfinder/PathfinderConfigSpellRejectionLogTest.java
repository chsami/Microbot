package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.TransportVarbit;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PathfinderConfigSpellRejectionLogTest {

    private static final WorldPoint VARROCK_HOUSE = new WorldPoint(3239, 3450, 0);
    private static final WorldPoint VARROCK_TELEPORT = new WorldPoint(3213, 3424, 0);
    private static final WorldPoint GRAND_EXCHANGE_TELEPORT = new WorldPoint(3164, 3478, 0);

    private static Transport spell(WorldPoint origin, WorldPoint destination, TransportType type) {
        Transport transport = mock(Transport.class);
        when(transport.getType()).thenReturn(type);
        when(transport.getOrigin()).thenReturn(origin);
        when(transport.getDestination()).thenReturn(destination);
        when(transport.getDisplayInfo()).thenReturn("Varrock Teleport");
        return transport;
    }

    @Test
    public void varrockTeleportIsNearTheReportedGoal() {
        assertTrue(PathfinderConfig.isSpellTeleportNearTarget(
                spell(null, VARROCK_TELEPORT, TransportType.TELEPORTATION_SPELL), VARROCK_HOUSE));
    }

    @Test
    public void grandExchangeVariantIsNotNearTheReportedGoal() {
        assertFalse(PathfinderConfig.isSpellTeleportNearTarget(
                spell(null, GRAND_EXCHANGE_TELEPORT, TransportType.TELEPORTATION_SPELL), VARROCK_HOUSE));
    }

    @Test
    public void onlyOriginlessSpellTeleportsWithATargetCount() {
        assertFalse(PathfinderConfig.isSpellTeleportNearTarget(
                spell(null, VARROCK_TELEPORT, TransportType.TELEPORTATION_ITEM), VARROCK_HOUSE));
        assertFalse(PathfinderConfig.isSpellTeleportNearTarget(
                spell(new WorldPoint(3200, 3400, 0), VARROCK_TELEPORT, TransportType.TELEPORTATION_SPELL),
                VARROCK_HOUSE));
        assertFalse(PathfinderConfig.isSpellTeleportNearTarget(
                spell(null, VARROCK_TELEPORT, TransportType.TELEPORTATION_SPELL), null));
    }

    @Test
    public void lineNamesTheReasonAndEveryVarbitValue() {
        Transport transport = spell(null, VARROCK_TELEPORT, TransportType.TELEPORTATION_SPELL);
        when(transport.getVarbits()).thenReturn(Set.of(
                new TransportVarbit(4585, 0, TransportVarbit.Operator.EQUAL),
                new TransportVarbit(4070, 0, TransportVarbit.Operator.EQUAL)));

        String line = PathfinderConfig.spellRejectionLogLine(transport, VARROCK_HOUSE,
                TransportRejection.VARBITS, id -> id == 4585 ? 1 : 0);

        assertEquals("spell=\"Varrock Teleport\" dest=3213,3424,0 goalDist=26 reason=VARBITS"
                + " varbits=[4070 EQUAL 0 is 0, 4585 EQUAL 0 is 1]", line);
    }

    @Test
    public void lineWithoutVarbitsStillNamesTheReason() {
        Transport transport = spell(null, VARROCK_TELEPORT, TransportType.TELEPORTATION_SPELL);
        when(transport.getVarbits()).thenReturn(Set.of());

        String line = PathfinderConfig.spellRejectionLogLine(transport, VARROCK_HOUSE,
                TransportRejection.TELEPORT_SPELL, id -> 0);

        assertEquals("spell=\"Varrock Teleport\" dest=3213,3424,0 goalDist=26 reason=TELEPORT_SPELL varbits=[]", line);
    }
}
