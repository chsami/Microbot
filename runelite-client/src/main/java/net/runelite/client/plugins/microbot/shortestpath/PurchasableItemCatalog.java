package net.runelite.client.plugins.microbot.shortestpath;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Catalog of transport-gating items that can be bought from a vendor standing at the transport
 * itself (the Shantay pass pattern: the gate wants a ticket, Shantay sells tickets two tiles away).
 * Backed by {@code purchasable_items.tsv}. Data-driven so the next purchasable gate is a TSV row,
 * not another hardcoded {@code ensureShantayPassBeforeGate}.
 *
 * <p>Consumers: the walker's pre-transport step (buy the item when missing and affordable) and the
 * banked planner (withdraw the fare instead of an item the bank does not hold). Routing is not
 * consulted here — transports.tsv carries a duplicate-row OR (item row + currency-twin row) so the
 * pathfinder already plans through the transport for either holding.
 *
 * <p>Parsing is lenient like {@code LearnedBlockedEdges}: a malformed row is logged and skipped,
 * never fatal.
 */
@Slf4j
public final class PurchasableItemCatalog {
    private static final String DELIM_COLUMN = "\t";
    private static final String PREFIX_COMMENT = "#";
    private static final String RESOURCE = "purchasable_items.tsv";

    /** One catalog row: which item, what it costs, and who sells it where. */
    public static final class PurchasableItem {
        public final int itemId;
        public final int costAmount;
        public final String costCurrencyName;
        public final int vendorNpcId;
        public final String vendorAction;
        public final WorldPoint vendorLocation;
        public final int radius;

        PurchasableItem(int itemId, int costAmount, String costCurrencyName,
                        int vendorNpcId, String vendorAction, WorldPoint vendorLocation, int radius) {
            this.itemId = itemId;
            this.costAmount = costAmount;
            this.costCurrencyName = costCurrencyName;
            this.vendorNpcId = vendorNpcId;
            this.vendorAction = vendorAction;
            this.vendorLocation = vendorLocation;
            this.radius = radius;
        }

        /** Whether this entry's vendor stands close enough to {@code point} to service it. */
        public boolean vendorNear(WorldPoint point) {
            return point != null
                    && vendorLocation.getPlane() == point.getPlane()
                    && vendorLocation.distanceTo2D(point) <= radius;
        }
    }

    private static volatile List<PurchasableItem> catalog;

    private PurchasableItemCatalog() {
    }

    public static List<PurchasableItem> all() {
        List<PurchasableItem> loaded = catalog;
        if (loaded == null) {
            synchronized (PurchasableItemCatalog.class) {
                loaded = catalog;
                if (loaded == null) {
                    loaded = loadFromResource();
                    catalog = loaded;
                }
            }
        }
        return loaded;
    }

    /** The entry for {@code itemId}, or null when the item is not purchasable at any transport. */
    public static PurchasableItem byItemId(int itemId) {
        for (PurchasableItem item : all()) {
            if (item.itemId == itemId) {
                return item;
            }
        }
        return null;
    }

    /**
     * The entry that services {@code transport}, or null. An entry matches when its vendor stands
     * within {@code radius} of the transport origin AND the transport is actually gated on the entry:
     * either it requires the item outright, or it is the item's currency twin (a fare row with no
     * item requirements — the duplicate-row OR pattern). Rows carrying neither (e.g. the free
     * northbound direction of a gate) never match, which is what scopes the buy to the paying
     * direction without any coordinate special-casing.
     */
    public static PurchasableItem forTransport(Transport transport) {
        if (transport == null || transport.getOrigin() == null) {
            return null;
        }
        boolean currencyTwin = transport.getCurrencyAmount() > 0
                && (transport.getItemIdRequirements() == null || transport.getItemIdRequirements().isEmpty());
        for (PurchasableItem item : all()) {
            if (!item.vendorNear(transport.getOrigin())) {
                continue;
            }
            boolean matchingCurrencyTwin = currencyTwin
                    && transport.getCurrencyAmount() == item.costAmount
                    && item.costCurrencyName.equalsIgnoreCase(transport.getCurrencyName());
            if (requiresItem(transport, item.itemId) || matchingCurrencyTwin) {
                return item;
            }
        }
        return null;
    }

    private static boolean requiresItem(Transport transport, int itemId) {
        if (transport.getItemIdRequirements() == null) {
            return false;
        }
        for (Set<Integer> alternatives : transport.getItemIdRequirements()) {
            if (alternatives != null && alternatives.contains(itemId)) {
                return true;
            }
        }
        return false;
    }

    private static List<PurchasableItem> loadFromResource() {
        try (InputStream stream = ShortestPathPlugin.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                log.warn("Purchasable item resource missing: {}", RESOURCE);
                return Collections.emptyList();
            }
            return parse(new String(Util.readAllBytes(stream), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Unable to load purchasable item catalog: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Package-private parse seam so tests can feed synthetic TSV content. */
    static List<PurchasableItem> parse(String content) {
        List<PurchasableItem> items = new ArrayList<>();
        try (Scanner scanner = new Scanner(content)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith(PREFIX_COMMENT) || line.isBlank()) {
                    continue;
                }
                PurchasableItem item = parseRow(line);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return Collections.unmodifiableList(items);
    }

    private static PurchasableItem parseRow(String line) {
        String[] fields = line.split(DELIM_COLUMN);
        if (fields.length < 6) {
            log.warn("Skipping malformed purchasable-item row (need 6 columns): {}", line);
            return null;
        }
        try {
            int itemId = Integer.parseInt(fields[0].trim());
            String[] cost = fields[1].trim().split(" ", 2);
            int costAmount = Integer.parseInt(cost[0].trim());
            String costCurrency = cost.length > 1 ? cost[1].trim() : "";
            int vendorNpcId = Integer.parseInt(fields[2].trim());
            String vendorAction = fields[3].trim();
            String[] loc = fields[4].trim().split(" ");
            if (loc.length != 3 || vendorAction.isEmpty() || costCurrency.isEmpty()) {
                log.warn("Skipping malformed purchasable-item row: {}", line);
                return null;
            }
            WorldPoint vendorLocation = new WorldPoint(
                    Integer.parseInt(loc[0]), Integer.parseInt(loc[1]), Integer.parseInt(loc[2]));
            int radius = Integer.parseInt(fields[5].trim());
            return new PurchasableItem(itemId, costAmount, costCurrency,
                    vendorNpcId, vendorAction, vendorLocation, radius);
        } catch (NumberFormatException e) {
            log.warn("Skipping unparseable purchasable-item row: {}", line);
            return null;
        }
    }
}
