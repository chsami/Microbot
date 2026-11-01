package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
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
    public void slayerRingPoliciesRespectChargesAcrossAllDestinations() {
        Set<Transport> originlessTransports = Transport.loadAllFromResources().get(null);
        assertNotNull(originlessTransports);
        List<Transport> rings = originlessTransports.stream()
                .filter(transport -> transport.getType() == TransportType.TELEPORTATION_ITEM)
                .filter(transport -> transport.getDisplayInfo() != null
                        && transport.getDisplayInfo().startsWith("Slayer ring:"))
                .collect(Collectors.toList());
        Map<String, WorldPoint> destinations = Map.of(
                "Slayer ring: Stronghold Slayer Cave", new WorldPoint(2432, 3423, 0),
                "Slayer ring: Slayer Tower", new WorldPoint(3421, 3537, 0),
                "Slayer ring: Fremennik", new WorldPoint(2802, 10000, 0),
                "Slayer ring: Tarn's Lair", new WorldPoint(3185, 4601, 0),
                "Slayer ring: Dark Beasts", new WorldPoint(2028, 4636, 0));

        for (int heldItemId : List.of(ItemID.SLAYER_RING_ETERNAL,
                ItemID.SLAYER_RING_8, ItemID.SLAYER_RING_7, ItemID.SLAYER_RING_6,
                ItemID.SLAYER_RING_5, ItemID.SLAYER_RING_4, ItemID.SLAYER_RING_3,
                ItemID.SLAYER_RING_2, ItemID.SLAYER_RING_1, -1)) {
            for (TeleportationItem policy : TeleportationItem.values()) {
                Map<String, WorldPoint> availableDestinations = rings.stream()
                        .filter(transport -> PathfinderConfig.isTeleportationItemAllowedByPolicy(
                                policy, transport.isConsumable()))
                        .filter(transport -> TransportItemRequirement.selectProviders(
                                transport.getItemRequirements(),
                                itemId -> itemId == heldItemId ? 1 : 0,
                                itemId -> itemId == heldItemId,
                                itemId -> itemId == heldItemId).isPresent())
                        .collect(Collectors.toMap(Transport::getDisplayInfo, Transport::getDestination));
                boolean allowed = heldItemId != -1 && (policy == TeleportationItem.INVENTORY
                        || (policy == TeleportationItem.INVENTORY_NON_CONSUMABLE
                        && heldItemId == ItemID.SLAYER_RING_ETERNAL));
                assertEquals("Item " + heldItemId + " with policy " + policy,
                        allowed ? destinations : Map.of(), availableDestinations);
            }
        }
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
