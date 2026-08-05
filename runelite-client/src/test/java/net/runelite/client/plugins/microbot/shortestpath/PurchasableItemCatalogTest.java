package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The purchasable-item catalog is what keeps "buy the ticket at the gate" data-driven: the walker's
 * pre-transport step and the banked planner both consult it. These tests pin the parse (lenient),
 * the shipped Shantay row, and the transport-matching contract that scopes buying to the paying
 * direction of a gate.
 */
public class PurchasableItemCatalogTest {

    private static List<Transport> shantayGateRows;

    @BeforeClass
    public static void load() {
        HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
        shantayGateRows = transports.values().stream()
                .flatMap(Set::stream)
                .filter(t -> t.getObjectId() == 4031)
                .collect(Collectors.toList());
    }

    @Test
    public void parseIsLenientAndReadsAllColumns() {
        List<PurchasableItemCatalog.PurchasableItem> items = PurchasableItemCatalog.parse(
                "# Item ID\tCost\tVendor NPC ID\tVendor action\tVendor location\tRadius\n"
                        + "# a comment line\n"
                        + "\n"
                        + "not-a-number\t5 Coins\t1\tBuy\t1 2 0\t3\n"
                        + "17\t5 Coins\t1\tBuy\t1 2\t3\n"
                        + "954\t18 Coins\t7601\tTrade\t2620 3145 0\t10\n");
        assertEquals("only the well-formed row survives", 1, items.size());
        PurchasableItemCatalog.PurchasableItem rope = items.get(0);
        assertEquals(954, rope.itemId);
        assertEquals(18, rope.costAmount);
        assertEquals("Coins", rope.costCurrencyName);
        assertEquals(7601, rope.vendorNpcId);
        assertEquals("Trade", rope.vendorAction);
        assertEquals(new WorldPoint(2620, 3145, 0), rope.vendorLocation);
        assertEquals(10, rope.radius);
        assertTrue(rope.vendorNear(new WorldPoint(2625, 3150, 0)));
        assertFalse("wrong plane never matches", rope.vendorNear(new WorldPoint(2620, 3145, 1)));
        assertFalse(rope.vendorNear(new WorldPoint(2700, 3145, 0)));
    }

    @Test
    public void shippedCatalogCarriesTheShantayPass() {
        PurchasableItemCatalog.PurchasableItem pass = PurchasableItemCatalog.byItemId(1854);
        assertNotNull("the Shantay pass row must ship in purchasable_items.tsv", pass);
        assertEquals(4642, pass.vendorNpcId);
        assertEquals("Buy-pass", pass.vendorAction);
        assertEquals(5, pass.costAmount);
        assertNull(PurchasableItemCatalog.byItemId(-1));
    }

    /**
     * Matching contract: the southbound gate rows (ticket-gated and its 5-coin currency twin) match;
     * the free northbound rows — same object, vendor equally near — never match, because they carry
     * neither an item nor a currency requirement. This replaces the old dest.getY() special-case.
     */
    @Test
    public void forTransportMatchesOnlyThePayingDirection() {
        assertFalse("precondition: catalog data should contain the Shantay gate rows",
                shantayGateRows.isEmpty());
        int matchedGated = 0;
        int matchedFree = 0;
        for (Transport row : shantayGateRows) {
            boolean gated = row.getCurrencyAmount() > 0
                    || (row.getItemIdRequirements() != null && !row.getItemIdRequirements().isEmpty());
            PurchasableItemCatalog.PurchasableItem match = PurchasableItemCatalog.forTransport(row);
            if (gated) {
                assertNotNull("a gated Shantay row must match the catalog: " + row, match);
                assertEquals(1854, match.itemId);
                matchedGated++;
            } else {
                assertNull("a free (northbound) Shantay row must never trigger a buy: " + row, match);
                matchedFree++;
            }
        }
        assertTrue("expected both gated and free rows in the catalog data",
                matchedGated > 0 && matchedFree > 0);
        assertNull(PurchasableItemCatalog.forTransport(null));
    }

    @Test
    public void currencyTwinMustMatchBothFareFields() {
        assertNotNull("the exact Shantay fare must match",
                PurchasableItemCatalog.forTransport(currencyTransport("5 Coins")));
        assertNull("a different amount must not match merely because the vendor is nearby",
                PurchasableItemCatalog.forTransport(currencyTransport("6 Coins")));
        assertNull("a different currency must not match merely because the amount and vendor are nearby",
                PurchasableItemCatalog.forTransport(currencyTransport("5 Ecto-token")));
    }

    private static Transport currencyTransport(String fare) {
        HashMap<String, String> fields = new HashMap<>();
        fields.put("Origin", "3304 3117 0");
        fields.put("Destination", "3304 3115 0");
        fields.put("Currency", fare);
        return new Transport(fields, TransportType.TRANSPORT);
    }
}
