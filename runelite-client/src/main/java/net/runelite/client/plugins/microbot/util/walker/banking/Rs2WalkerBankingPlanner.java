package net.runelite.client.plugins.microbot.util.walker.banking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.TransportRouteAnalysis;
import net.runelite.client.plugins.microbot.util.walker.WebWalkLog;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public final class Rs2WalkerBankingPlanner {

    private Rs2WalkerBankingPlanner() {
    }

    public static List<Transport> getTransportsForDestination(WorldPoint destination, boolean useBankItems, TransportType prefTransportType) {
        if (destination == null) {
            return new ArrayList<>();
        }

        boolean originalUseBankItems = Rs2PathApi.getPathfinderConfig().isUseBankItems();
        try {
            Rs2PathApi.getPathfinderConfig().setUseBankItems(useBankItems);
            Rs2PathApi.getPathfinderConfig().refresh();
            Pathfinder pf = new Pathfinder(Rs2PathApi.getPathfinderConfig(), Rs2Player.getWorldLocation(), destination);
            pf.run();

            List<WorldPoint> path = pf.getPath();
            if (path.isEmpty()) {
                log.debug("Unable to find path to destination: " + destination);
                return new ArrayList<>();
            }

            List<Transport> transports = Rs2Walker.getTransportsForPath(path, 0, prefTransportType, true);
            transports.forEach(t -> log.debug("Transport found: " + t));
            return transports;
        } finally {
            Rs2PathApi.getPathfinderConfig().setUseBankItems(originalUseBankItems);
            Rs2PathApi.getPathfinderConfig().refresh();
        }
    }

    /**
     * Whether a plain {@link TransportType#TRANSPORT} takes part in bank planning.
     *
     * <p>Previously only currency-bearing ones did, so an item-gated obstacle — a machete for a
     * jungle bush, a pickaxe for a rockfall, a Shantay pass — fell through
     * {@link #hasRequiredTransportItems} to its catch-all {@code return true}, was never reported as
     * missing, and was never withdrawn. That contradicted the pathfinder:
     * {@code PathfinderConfig.hasRequiredItems} counts <em>bank</em> contents when
     * {@code useBankItems} is set, so a route was planned through the obstacle on the strength of a
     * banked item the planner then declined to fetch, stranding the walk at the obstacle.
     *
     * <p>This only widens which transports are <em>eligible</em>. Collection is path-scoped —
     * {@link #getTransportsForDestination} pathfinds first and inspects only transports on the
     * resulting route — so an item is fetched solely when the chosen route actually needs it.
     */
    static boolean planningCoversPlainTransport(Transport transport) {
        if (transport == null || transport.getType() != TransportType.TRANSPORT) {
            return false;
        }
        return transport.getCurrencyAmount() > 0
                || (transport.getItemIdRequirements() != null && !transport.getItemIdRequirements().isEmpty());
    }

    public static boolean hasRequiredTransportItems(Transport transport) {
        if (transport == null) {
            return false;
        }

        if (transport.getType() == TransportType.FAIRY_RING) {
            return Rs2Inventory.hasItem(ItemID.DRAMEN_STAFF)
                    || Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF)
                    || Rs2Inventory.hasItem(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)
                    || Rs2Equipment.isWearing(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)
                    || Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1;
        } else if (transport.getType() == TransportType.TELEPORTATION_ITEM
                || transport.getType() == TransportType.TELEPORTATION_SPELL
                || transport.getType() == TransportType.CANOE
                || transport.getType() == TransportType.BOAT
                || transport.getType() == TransportType.CHARTER_SHIP
                || transport.getType() == TransportType.SHIP
                || transport.getType() == TransportType.MINECART
                || transport.getType() == TransportType.MAGIC_CARPET
                || planningCoversPlainTransport(transport)) {
            if (transport.getType() == TransportType.TELEPORTATION_SPELL && transport.getDisplayInfo() != null) {
                String spellName = transport.getDisplayInfo().contains(":")
                        ? transport.getDisplayInfo().split(":")[0].trim()
                        : transport.getDisplayInfo().trim();
                boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
                String displayInfo = hasMultipleDestination
                        ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                        : transport.getDisplayInfo();
                log.debug("Looking for spell rune requirements for: '{}' - display info {}", spellName, displayInfo);
                Rs2Spells rs2Spell = Rs2Magic.getRs2Spell(displayInfo);
                return Rs2Magic.hasRequiredRunes(rs2Spell);
            }
            if (isCurrencyBasedTransport(transport.getType())
                    && (transport.getItemIdRequirements() == null || transport.getItemIdRequirements().isEmpty())
                    && transport.getCurrencyName() != null
                    && !transport.getCurrencyName().isEmpty()
                    && transport.getCurrencyAmount() > 0) {
                int currencyItemId = getCurrencyItemId(transport.getCurrencyName());
                return Rs2Inventory.count(currencyItemId) >= transport.getCurrencyAmount();
            }
            if (transport.getItemIdRequirements() == null || transport.getItemIdRequirements().isEmpty()) {
                return true;
            }

            return transport.getItemIdRequirements()
                    .stream()
                    .flatMap(Collection::stream)
                    .anyMatch(itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId));
        }

        return true;
    }

    public static List<Transport> getMissingTransports(List<Transport> transports) {
        if (transports == null) {
            return new ArrayList<>();
        }

        return transports.stream()
                .filter(t -> !hasRequiredTransportItems(t))
                .collect(Collectors.toList());
    }

    public static Map<Integer, Integer> getMissingTransportItemIdsWithQuantities(List<Transport> transports) {
        if (transports == null) {
            return new HashMap<>();
        }

        Map<Integer, Integer> itemQuantityMap = new HashMap<>();

        transports.forEach(transport -> {
            if (transport.getType() == TransportType.TELEPORTATION_SPELL) {
                Map<Integer, Integer> spellRuneRequirements = getSpellRuneRequirements(transport);
                if (!spellRuneRequirements.isEmpty()) {
                    spellRuneRequirements.forEach((runeItemId, requiredQuantity) -> {
                        try {
                            int bankQuantity = Rs2Bank.count(runeItemId);
                            int currentQuantity = itemQuantityMap.getOrDefault(runeItemId, 0);
                            itemQuantityMap.put(runeItemId, currentQuantity + requiredQuantity);
                            log.debug("Added teleportation spell rune requirement: {} (ID: {}) x{} (bank has: {} short={})",
                                    runeItemId, runeItemId, requiredQuantity, bankQuantity, bankQuantity < requiredQuantity);
                        } catch (Exception e) {
                            log.debug("Could not check bank for rune " + runeItemId + ": " + e.getMessage());
                        }
                    });
                }
                return;
            }

            if (transport.getItemIdRequirements() != null) {
                for (Set<Integer> alternativeItems : transport.getItemIdRequirements()) {
                    int requiredQuantity = (isCurrencyBasedTransport(transport.getType()) && transport.getCurrencyAmount() > 0)
                            ? transport.getCurrencyAmount()
                            : 1;

                    Integer preferredItemId = null;
                    int preferredBankQuantity = 0;
                    for (Integer itemId : alternativeItems) {
                        int bankQuantity = 0;
                        try {
                            bankQuantity = Rs2Bank.count(itemId);
                        } catch (Exception e) {
                            log.debug("Could not check bank for item " + itemId + ": " + e.getMessage());
                        }
                        if (preferredItemId == null || bankQuantity > preferredBankQuantity) {
                            preferredItemId = itemId;
                            preferredBankQuantity = bankQuantity;
                        }
                    }

                    if (preferredItemId != null) {
                        int currentQuantity = itemQuantityMap.getOrDefault(preferredItemId, 0);
                        itemQuantityMap.put(preferredItemId, currentQuantity + requiredQuantity);
                        log.debug("Added transport item requirement: itemId={} x{} (bank has: {} short={})",
                                preferredItemId, requiredQuantity, preferredBankQuantity, preferredBankQuantity < requiredQuantity);
                    }
                    break;
                }
            }
        });

        return itemQuantityMap;
    }

    public static List<Integer> getMissingTransportItemIds(List<Transport> transports) {
        return new ArrayList<>(getMissingTransportItemIdsWithQuantities(transports).keySet());
    }

    public static TransportRouteAnalysis compareRoutes(WorldPoint startPoint, WorldPoint target) {
        long totalStartTime = System.nanoTime();
        StringBuilder performanceLog = new StringBuilder();
        performanceLog.append("\n\t=== compareRoutes Performance Analysis ===\n");
        if (target == null) {
            return new TransportRouteAnalysis(new ArrayList<>(), null, null, new ArrayList<>(), new ArrayList<>(), "Target location is null");
        }

        if (startPoint == null) {
            startPoint = Rs2Player.getWorldLocation();
        }

        if (startPoint == null) {
            return new TransportRouteAnalysis(new ArrayList<>(), null, null, new ArrayList<>(), new ArrayList<>(), "Cannot determine starting location");
        }

        try {
            performanceLog.append("\tStart Point: ").append(startPoint).append(", Target: ").append(target).append("\n");
            long directPathStartTime = System.nanoTime();
            List<WorldPoint> directPath = Rs2Walker.getWalkPath(startPoint, target);
            long directPathEndTime = System.nanoTime();
            double directPathTimeMs = (directPathEndTime - directPathStartTime) / 1_000_000.0;

            int directDistance = Rs2Walker.getTotalTilesFromPath(directPath, target);
            performanceLog.append("\t-Direct path calculation: ").append(String.format("%.2f ms", directPathTimeMs))
                    .append(" (").append(directPath.size()).append(" waypoints, ").append(directDistance).append(" tiles)\n");

            BankLocation nearestBank = null;
            List<WorldPoint> pathToBank = new ArrayList<>();
            List<WorldPoint> pathFromBankToTarget = new ArrayList<>();
            int bankingRouteDistance = -1;

            try {
                boolean originalUseBankItems = Rs2PathApi.getPathfinderConfig().isUseBankItems();
                try {
                    Rs2PathApi.getPathfinderConfig().setUseBankItems(true);
                    Rs2PathApi.getPathfinderConfig().refresh(target);

                    performanceLog.append("\t-Bank items available: ").append(Rs2Bank.bankItems().size()).append("\n");

                    long bankSearchStartTime = System.nanoTime();
                    nearestBank = Rs2Bank.getNearestBank(startPoint);
                    long bankSearchEndTime = System.nanoTime();
                    double bankSearchTimeMs = (bankSearchEndTime - bankSearchStartTime) / 1_000_000.0;

                    if (nearestBank != null) {
                        WorldPoint bankLocation = nearestBank.getWorldPoint();
                        performanceLog.append("\t-Nearest bank search: ").append(String.format("%.2f ms", bankSearchTimeMs));
                        performanceLog.append("\t -> Found: ").append(nearestBank).append(" at ").append(bankLocation).append("\n");

                        long pathToBankStartTime = System.nanoTime();
                        pathToBank = Rs2Walker.getWalkPath(startPoint, bankLocation);
                        long pathToBankEndTime = System.nanoTime();
                        double pathToBankTimeMs = (pathToBankEndTime - pathToBankStartTime) / 1_000_000.0;
                        int distanceToBank = Rs2Walker.getTotalTilesFromPath(pathToBank, bankLocation);

                        long pathFromBankStartTime = System.nanoTime();
                        pathFromBankToTarget = Rs2Walker.getWalkPath(bankLocation, target);
                        long pathFromBankEndTime = System.nanoTime();
                        double pathFromBankTimeMs = (pathFromBankEndTime - pathFromBankStartTime) / 1_000_000.0;
                        List<Transport> bankLegTransports = Rs2Walker.getTransportsForPath(
                                pathFromBankToTarget, 0, TransportType.TELEPORTATION_SPELL, true);
                        long spellCount = bankLegTransports.stream()
                                .filter(t -> t.getType() == TransportType.TELEPORTATION_SPELL)
                                .count();
                        long itemCount = bankLegTransports.stream()
                                .filter(t -> t.getType() == TransportType.TELEPORTATION_ITEM)
                                .count();
                        int distanceFromBankRaw = Rs2Walker.getTotalTilesFromPath(pathFromBankToTarget, target);
                        int distanceFromBank = effectiveDistanceFromBank(pathFromBankToTarget, distanceFromBankRaw);

                        performanceLog.append("\t-Path to bank calculation: ").append(String.format("%.2f ms", pathToBankTimeMs))
                                .append(" (").append(pathToBank.size()).append(" waypoints, ").append(distanceToBank).append(" tiles)\n");
                        performanceLog.append("\t-Path from bank to target with banked items: ").append(String.format("%.2f ms", pathFromBankTimeMs))
                                .append(" (").append(pathFromBankToTarget.size()).append(" waypoints, ").append(distanceFromBank).append(" tiles)\n");
                        performanceLog.append("\t-Bank leg transports: total=").append(bankLegTransports.size())
                                .append(" spells=").append(spellCount)
                                .append(" items=").append(itemCount)
                                .append("\n");
                        Transport firstSpellTransport = bankLegTransports.stream()
                                .filter(t -> t.getType() == TransportType.TELEPORTATION_SPELL)
                                .findFirst()
                                .orElse(null);
                        if (firstSpellTransport != null) {
                            performanceLog.append("\t-First bank-leg spell transport: ")
                                    .append(firstSpellTransport.getDisplayInfo())
                                    .append(" -> ")
                                    .append(firstSpellTransport.getDestination())
                                    .append("\n");
                        }
                        WebWalkLog.spInfo("compare_bank_leg | total={} spells={} items={} firstSpell={}",
                                bankLegTransports.size(),
                                spellCount,
                                itemCount,
                                firstSpellTransport == null
                                        ? "none"
                                        : firstSpellTransport.getDisplayInfo() + " -> " + firstSpellTransport.getDestination());
                        if (distanceFromBankRaw != distanceFromBank) {
                            performanceLog.append("\t-Adjusted bank leg for immediate teleport: raw=")
                                    .append(distanceFromBankRaw)
                                    .append(" adjusted=")
                                    .append(distanceFromBank)
                                    .append(" tiles\n");
                        }

                        if (distanceToBank != -1
                                && distanceFromBank != -1
                                && distanceToBank != Integer.MAX_VALUE
                                && distanceFromBank != Integer.MAX_VALUE) {
                            bankingRouteDistance = distanceToBank + distanceFromBank;
                        }
                        performanceLog.append("\t-Total banking route distance: ").append(bankingRouteDistance).append(" tiles\n");
                    } else {
                        performanceLog.append("\t-Nearest bank search: ").append(String.format("%.2f ms", bankSearchTimeMs))
                                .append("\t -> No accessible bank found\n");
                    }
                } finally {
                    Rs2PathApi.getPathfinderConfig().setUseBankItems(originalUseBankItems);
                    Rs2PathApi.getPathfinderConfig().refresh();
                }
            } catch (Exception e) {
                performanceLog.append("Banking route calculation failed: ").append(e.getMessage()).append("\n");
                log.debug("Could not calculate banking route: " + e.getMessage());
            }

            long totalEndTime = System.nanoTime();
            double totalTimeMs = (totalEndTime - totalStartTime) / 1_000_000.0;
            performanceLog.append("\t=== Total compareRoutes time: ").append(String.format("%.2f ms", totalTimeMs)).append(" ===\n");

            if (bankingRouteDistance == -1) {
                performanceLog.append("\tResult: Direct route only (banking route unavailable)\n");
                WebWalkLog.compareDetail(performanceLog.toString());
                WebWalkLog.compareSummary(totalTimeMs, directDistance, -1, "direct_only_bank_unavailable");
                return new TransportRouteAnalysis(directPath, null, null, new ArrayList<>(), new ArrayList<>(),
                        "Direct route only (banking route unavailable)");
            }

            final boolean tie = directDistance == bankingRouteDistance;
            final boolean directStrictlyFaster = directDistance < bankingRouteDistance;
            final boolean preferTransportToTarget = Rs2PathApi.override("preferTransportToTarget", false);
            final String recommendation;
            final String verdictOneLine;
            if (tie) {
                if (preferTransportToTarget) {
                    recommendation = String.format("\tSame tile distance (%d); prefer banking route (prefer transport to target enabled)", directDistance);
                    verdictOneLine = String.format("tie %dt (prefer bank: transport-to-target)", directDistance);
                } else {
                    recommendation = String.format("\tSame tile distance (%d); prefer direct (no bank hop)", directDistance);
                    verdictOneLine = String.format("tie %dt (prefer direct)", directDistance);
                }
            } else if (directStrictlyFaster) {
                recommendation = String.format("\tDirect route is faster (%d vs %d tiles)", directDistance, bankingRouteDistance);
                verdictOneLine = String.format("direct faster %dt vs %dt", directDistance, bankingRouteDistance);
            } else {
                recommendation = String.format("\tBanking route is faster (%d vs %d tiles)", bankingRouteDistance, directDistance);
                verdictOneLine = String.format("bank faster %dt vs %dt", bankingRouteDistance, directDistance);
            }

            performanceLog.append("\tResult:\n\t\t ").append(recommendation).append("\n");
            WebWalkLog.compareDetail(performanceLog.toString());
            WebWalkLog.compareSummary(totalTimeMs, directDistance, bankingRouteDistance, verdictOneLine);

            return new TransportRouteAnalysis(directPath,
                    nearestBank, nearestBank != null ? nearestBank.getWorldPoint() : null, pathToBank, pathFromBankToTarget, recommendation,
                    directDistance, bankingRouteDistance);
        } catch (Exception e) {
            long totalEndTime = System.nanoTime();
            double totalTimeMs = (totalEndTime - totalStartTime) / 1_000_000.0;
            performanceLog.append("ERROR after ").append(String.format("%.2f ms", totalTimeMs)).append(": ").append(e.getMessage()).append("\n");
            WebWalkLog.compareDetail(performanceLog.toString());
            WebWalkLog.compareError(totalTimeMs, target, e.getMessage());
            return new TransportRouteAnalysis(new ArrayList<>(), null, null, new ArrayList<>(), new ArrayList<>(), "Error calculating routes: " + e.getMessage());
        }
    }

    private static Map<Integer, Integer> getSpellRuneRequirements(Transport transport) {
        Map<Integer, Integer> runeRequirements = new HashMap<>();
        if (transport.getType() != TransportType.TELEPORTATION_SPELL || transport.getDisplayInfo() == null) {
            return runeRequirements;
        }
        try {
            String spellName = transport.getDisplayInfo().contains(":")
                    ? transport.getDisplayInfo().split(":")[0].trim()
                    : transport.getDisplayInfo().trim();
            boolean hasMultipleDestination = transport.getDisplayInfo().contains(":");
            String displayInfo = hasMultipleDestination
                    ? transport.getDisplayInfo().split(":")[0].trim().toLowerCase()
                    : transport.getDisplayInfo();
            log.debug("Looking for spell rune requirements for: '{}' - display info {}", spellName, displayInfo);
            Rs2Spells rs2Spell = Rs2Magic.getRs2Spell(displayInfo);
            if (rs2Spell == null) {
                return runeRequirements;
            }
            Map<Runes, Integer> requiredRunes = Rs2Magic.getRequiredRunes(rs2Spell, 1, true);
            List<Runes> elementalRunes = rs2Spell.getElementalRunes();
            log.debug("Spell '{}' requires {} runes, including {} elemental runes",
                    spellName, requiredRunes.size(), elementalRunes.size());
            requiredRunes.forEach((rune, quantity) -> {
                int runeItemId = rune.getItemId();
                runeRequirements.put(runeItemId, quantity);
                log.debug("Spell '{}' requires {} x {} (ID: {})",
                        spellName, quantity, rune.name(), runeItemId);
            });
        } catch (Exception e) {
            log.warn("Error getting spell rune requirements for transport '{}': {}",
                    transport.getDisplayInfo(), e.getMessage());
        }

        return runeRequirements;
    }

    private static boolean isCurrencyBasedTransport(TransportType transportType) {
        return transportType == TransportType.BOAT
                || transportType == TransportType.CHARTER_SHIP
                || transportType == TransportType.SHIP
                || transportType == TransportType.MINECART
                || transportType == TransportType.MAGIC_CARPET
                || transportType == TransportType.TRANSPORT;
    }

    private static int getCurrencyItemId(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty()) {
            return -1;
        }

        String currency = currencyName.trim().toLowerCase();
        switch (currency) {
            case "coins":
                return ItemID.COINS;
            case "ecto-token":
                return ItemID.ECTOTOKEN;
            default:
                log.warn("Unknown currency type: {}", currencyName);
                return -1;
        }
    }

    /**
     * Score bank->target distance in a way that reflects "bank then immediate teleport" behavior.
     * For originless TELEPORTATION_ITEM / TELEPORTATION_SPELL edges, trim pre-teleport walking
     * from the bank leg metric and keep the post-teleport tail.
     */
    private static int effectiveDistanceFromBank(List<WorldPoint> pathFromBankToTarget, int rawDistance) {
        if (pathFromBankToTarget == null || pathFromBankToTarget.isEmpty() || rawDistance == Integer.MAX_VALUE) {
            return rawDistance;
        }

        List<Transport> transports = Rs2Walker.getTransportsForPath(pathFromBankToTarget, 0, TransportType.TELEPORTATION_SPELL, true);
        if (transports.isEmpty()) {
            return rawDistance;
        }

        // Use first transport that the bank->target path actually consumes and model:
        // walk_to_transport + transport_hop + post_transport_tail.
        Transport firstTransport = transports.get(0);
        int modeledDistance = transportModeledDistance(pathFromBankToTarget, firstTransport, rawDistance);
        if (modeledDistance == Integer.MAX_VALUE) {
            return rawDistance;
        }
        return Math.min(rawDistance, modeledDistance);
    }

    private static boolean isImmediateBankTeleport(Transport transport) {
        if (transport == null || transport.getOrigin() != null) {
            return false;
        }
        return transport.getType() == TransportType.TELEPORTATION_ITEM
                || transport.getType() == TransportType.TELEPORTATION_SPELL;
    }

    private static int transportModeledDistance(List<WorldPoint> pathFromBankToTarget, Transport transport, int fallbackRawDistance) {
        if (transport == null || pathFromBankToTarget == null || pathFromBankToTarget.isEmpty()) {
            return fallbackRawDistance;
        }

        WorldPoint destination = transport.getDestination();
        if (destination == null) {
            return fallbackRawDistance;
        }
        int destinationIndex = pathFromBankToTarget.indexOf(destination);
        if (destinationIndex < 0) {
            return fallbackRawDistance;
        }

        int originIndex;
        if (isImmediateBankTeleport(transport)) {
            originIndex = 0;
        } else {
            WorldPoint origin = transport.getOrigin();
            originIndex = origin == null ? 0 : pathFromBankToTarget.indexOf(origin);
            if (originIndex < 0) {
                originIndex = 0;
            }
        }

        if (destinationIndex < originIndex) {
            return fallbackRawDistance;
        }

        int walkToTransport = Math.max(0, originIndex);
        int transportHop = 1;
        int postTransportTail = Math.max(0, pathFromBankToTarget.size() - destinationIndex);
        return walkToTransport + transportHop + postTransportTail;
    }
}
