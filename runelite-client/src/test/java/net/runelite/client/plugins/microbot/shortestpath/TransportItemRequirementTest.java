package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

public class TransportItemRequirementTest {
    @Test
    public void numericUpstreamGrammarPreservesAndOrAndUsesUpstreamMaximumQuantity() {
        List<TransportItemRequirement> requirements =
                TransportItemRequirement.parseNumericRequirements("100=2||101=3&&200=1");

        assertEquals(2, requirements.size());
        assertEquals(3, requirements.get(0).getRequiredQuantity(100));
        assertEquals(3, requirements.get(0).getRequiredQuantity(101));
        assertEquals(Set.of(100, 101), requirements.get(0).getItemIds());
        assertEquals(Set.of(200), requirements.get(1).getItemIds());

        Map<Integer, Integer> available = new HashMap<>();
        available.put(100, 3);
        available.put(200, 1);
        assertTrue(requirements.stream().allMatch(
                requirement -> requirement.isSatisfiedBy(id -> available.getOrDefault(id, 0))));

        available.put(200, 0);
        assertFalse(requirements.stream().allMatch(
                requirement -> requirement.isSatisfiedBy(id -> available.getOrDefault(id, 0))));
    }

    @Test
    public void legacySemicolonIdsRemainOneAlternativeRequirement() {
        Map<String, String> fields = new HashMap<>();
        fields.put("Destination", "1 2 0");
        fields.put("Item IDs", "3853;3855;3857");

        Transport transport = new Transport(fields, TransportType.TELEPORTATION_ITEM);

        assertEquals(1, transport.getItemRequirements().size());
        assertEquals(Set.of(3853, 3855, 3857), transport.getItemRequirements().get(0).getItemIds());
        assertTrue(transport.getItemRequirements().get(0).isSatisfiedBy(id -> id == 3855 ? 1 : 0));
    }

    @Test
    public void transportAcceptsNumericUpstreamGrammarInCompatibilityColumn() {
        Map<String, String> fields = new HashMap<>();
        fields.put("Destination", "1 2 0");
        fields.put("Item IDs", "13280=1||13342=1&&995=20");

        Transport transport = new Transport(fields, TransportType.TELEPORTATION_ITEM);

        assertEquals(2, transport.getItemRequirements().size());
        assertEquals(Set.of(13280, 13342), transport.getItemRequirements().get(0).getItemIds());
        assertEquals(20, transport.getItemRequirements().get(1).getRequiredQuantity(995));
    }

    @Test
    public void transportAcceptsUpstreamInteractionAndVarPlayersGrammar() {
        Map<String, String> fields = new HashMap<>();
        fields.put("Origin", "1 2 0");
        fields.put("Destination", "3 4 0");
        fields.put("menuOption menuTarget objectID", "Travel Renu 13350");
        fields.put("VarPlayers", "4182&32");

        Transport transport = new Transport(fields, TransportType.QUETZAL);

        assertEquals("Travel", transport.getAction());
        assertEquals("Renu", transport.getName());
        assertEquals(13350, transport.getObjectId());
        assertEquals(1, transport.getVarplayers().size());
        TransportVarPlayer varplayer = transport.getVarplayers().iterator().next();
        assertEquals(4182, varplayer.getVarplayerId());
        assertEquals(32, varplayer.getValue());
        assertEquals(TransportVarPlayer.Operator.BIT_SET,
                varplayer.getOperator());
    }

    @Test
    public void supportedSymbolicCollectionsExpandWithoutFlatteningAndGroups() {
        List<TransportItemRequirement> requirements =
                TransportItemRequirement.parseRequirements("CROSSBOW=1&MITH_GRAPPLE=1");

        assertEquals(2, requirements.size());
        assertTrue(requirements.get(0).getItemIds().contains(ItemID.CROSSBOW));
        assertTrue(requirements.get(0).getItemIds().contains(ItemID.ZARYTE_XBOW));
        assertEquals(20, requirements.get(0).getItemIds().size());
        assertEquals(Set.of(ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE),
                requirements.get(1).getItemIds());
    }

    @Test
    public void transportAcceptsPinnedAxeCollectionFromUpstreamItemsColumn() {
        Map<String, String> fields = new HashMap<>();
        fields.put("Origin", "1 2 0");
        fields.put("Destination", "3 4 0");
        fields.put("Items", "AXE=1");

        Transport transport = new Transport(fields, TransportType.CANOE);

        assertEquals(1, transport.getItemRequirements().size());
        Set<Integer> axes = transport.getItemRequirements().get(0).getItemIds();
        assertEquals(12, axes.size());
        assertTrue(axes.contains(ItemID.BRONZE_AXE));
        assertTrue(axes.contains(ItemID.CRYSTAL_AXE));
        assertTrue(axes.contains(ItemID._3A_AXE));
    }

    @Test
    public void runeCollectionRetainsComboRuneAndEquipmentProviders() {
        TransportItemRequirement requirement =
                TransportItemRequirement.parseRequirements("AIR_RUNE=3").get(0);

        assertEquals(3, requirement.getRequiredQuantity(ItemID.AIRRUNE));
        assertEquals(3, requirement.getRequiredQuantity(ItemID.MISTRUNE));
        assertEquals(3, requirement.getRequiredQuantity(ItemID.DUSTRUNE));
        assertEquals(3, requirement.getRequiredQuantity(ItemID.SMOKERUNE));
        assertTrue(requirement.getStaffAlternatives().contains(ItemID.STAFF_OF_AIR));
        assertTrue(requirement.getStaffAlternatives().contains(ItemID.SHADOWFLAME_QUADRANT));
        assertTrue(requirement.getOffhandAlternatives().isEmpty());
        assertTrue(requirement.isRuneOnly());
    }

    @Test
    public void oneCombinationStaffCanSatisfyMultipleRuneClauses() {
        List<TransportItemRequirement> requirements =
                TransportItemRequirement.parseRequirements("FIRE_RUNE=2&WATER_RUNE=2");

        TransportItemRequirement.ProviderSelection selection =
                TransportItemRequirement.selectProviders(
                        requirements,
                        ignored -> 0,
                        itemId -> itemId == ItemID.TWINFLAME_STAFF,
                        ignored -> false)
                        .orElseThrow(() -> new AssertionError("Twinflame staff should satisfy both clauses"));

        assertEquals(ItemID.TWINFLAME_STAFF, selection.getStaffItemId());
        assertFalse(selection.hasOffhand());
    }

    @Test
    public void unequippedStaffIsNotMistakenForOrdinaryRuneQuantity() {
        List<TransportItemRequirement> requirements =
                TransportItemRequirement.parseRequirements("FIRE_RUNE=2");

        assertFalse(TransportItemRequirement.selectProviders(
                requirements,
                itemId -> itemId == ItemID.STAFF_OF_FIRE ? 1 : 0,
                ignored -> false,
                ignored -> false).isPresent());
        assertTrue(TransportItemRequirement.selectProviders(
                requirements,
                itemId -> itemId == ItemID.STAFF_OF_FIRE ? 1 : 0,
                itemId -> itemId == ItemID.STAFF_OF_FIRE,
                ignored -> false).isPresent());
    }

    @Test
    public void unsupportedSlotCollectionStillFailsClosed() {
        try {
            TransportItemRequirement.parseRequirements("CAPESLOT=1");
            fail("slot requirements need explicit equipment-slot semantics");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unresolved symbolic"));
        }
    }

    @Test
    public void mergedTransportRequiresBothEndpointRequirementGroups() {
        Transport origin = new Transport(
                new WorldPoint(1, 2, 0), "origin", TransportType.TRANSPORT, true, 19,
                Set.of(Set.of(10, 11)));
        Transport destination = new Transport(
                new WorldPoint(3, 4, 0), "destination", TransportType.TRANSPORT, true, 19,
                Set.of(Set.of(20)));

        Transport merged = new Transport(origin, destination);

        assertEquals(2, merged.getItemRequirements().size());
        assertEquals(Set.of(10, 11), merged.getItemRequirements().get(0).getItemIds());
        assertEquals(Set.of(20), merged.getItemRequirements().get(1).getItemIds());
    }

    @Test
    public void unresolvedSymbolicItemsFailClosed() {
        try {
            TransportItemRequirement.parseNumericRequirements("COINS=20");
            fail("symbolic item names must be resolved by the schema adapter");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unresolved symbolic"));
        }
    }
}
