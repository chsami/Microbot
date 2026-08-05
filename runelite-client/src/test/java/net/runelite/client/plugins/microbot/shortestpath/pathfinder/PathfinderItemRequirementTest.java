package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.client.plugins.microbot.shortestpath.TeleportationItem;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportItemRequirement;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PathfinderItemRequirementTest {
    private static final List<TransportItemRequirement> REQUIREMENTS = List.of(
            new TransportItemRequirement(Map.of(100, 2, 101, 3)),
            new TransportItemRequirement(Map.of(200, 1)));

    @Test
    public void everyAndGroupMustBeSatisfied() {
        assertTrue(PathfinderConfig.meetsItemRequirements(REQUIREMENTS, itemId -> {
            if (itemId == 100) return 2;
            if (itemId == 200) return 1;
            return 0;
        }));

        assertFalse(PathfinderConfig.meetsItemRequirements(REQUIREMENTS, itemId ->
                itemId == 100 ? 2 : 0));
    }

    @Test
    public void alternativeQuantitiesAreEvaluatedIndependently() {
        assertTrue(PathfinderConfig.meetsItemRequirements(REQUIREMENTS, itemId -> {
            if (itemId == 101) return 3;
            if (itemId == 200) return 1;
            return 0;
        }));
        assertFalse(PathfinderConfig.meetsItemRequirements(REQUIREMENTS, itemId -> {
            if (itemId == 101) return 2;
            if (itemId == 200) return 1;
            return 0;
        }));
    }

    @Test
    public void noRequirementsAreSatisfied() {
        assertTrue(PathfinderConfig.meetsItemRequirements(List.of(), itemId -> 0));
        assertTrue(PathfinderConfig.meetsItemRequirements(null, itemId -> 0));
    }

    @Test
    public void permanentItemPolicyKeepsOnlyInfiniteQuetzalWhistles() {
        Set<Transport> originlessTransports = Transport.loadAllFromResources().get(null);
        assertNotNull(originlessTransports);
        List<Transport> whistles = originlessTransports.stream()
                .filter(transport -> transport.getType() == TransportType.TELEPORTATION_ITEM)
                .filter(transport -> transport.getDisplayInfo() != null
                        && transport.getDisplayInfo().startsWith("Quetzal whistle:"))
                .collect(Collectors.toList());

        assertEquals(28, whistles.size());
        List<Transport> permanent = whistles.stream()
                .filter(transport -> PathfinderConfig.isTeleportationItemAllowedByPolicy(
                        TeleportationItem.INVENTORY_NON_CONSUMABLE,
                        transport.isConsumable()))
                .collect(Collectors.toList());

        assertEquals(14, permanent.size());
        assertTrue(permanent.stream().noneMatch(Transport::isConsumable));
        assertTrue(permanent.stream().allMatch(transport ->
                transport.getItemRequirements().size() == 1
                        && transport.getItemRequirements().get(0).getItemIds().equals(Set.of(33120))));
        assertTrue(whistles.stream().allMatch(transport ->
                PathfinderConfig.isTeleportationItemAllowedByPolicy(
                        TeleportationItem.INVENTORY,
                        transport.isConsumable())));
        assertTrue(whistles.stream().noneMatch(transport ->
                PathfinderConfig.isTeleportationItemAllowedByPolicy(
                        TeleportationItem.NONE,
                        transport.isConsumable())));
    }
}
