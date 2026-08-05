package net.runelite.client.plugins.microbot.testing.webwalker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class F2PWebWalkerRoute {
    static final List<F2PWebWalkerRoute> REQUIRED_ROUTES = List.of(
            route("F2P-01", "Lumbridge courtyard to Lumbridge bank",
                    point(3222, 3218, 0), point(3208, 3220, 2), 1, 1),
            route("F2P-02", "Lumbridge bank to cow pen",
                    point(3208, 3220, 2), point(3253, 3266, 0), 1, 2),
            route("F2P-03", "Cow pen to Draynor bank",
                    point(3253, 3266, 0), point(3092, 3245, 0), 2, 2),
            route("F2P-04", "Draynor bank to Wizards' Tower bridge",
                    point(3092, 3245, 0), point(3109, 3168, 0), 2, 2),
            route("F2P-05", "Wizards' Tower bridge to Port Sarim docks",
                    point(3109, 3168, 0), point(3029, 3217, 0), 2, 2),
            route("F2P-06", "Port Sarim docks to Rimmington",
                    point(3029, 3217, 0), point(2957, 3214, 0), 2, 2),
            route("F2P-07", "Rimmington to Falador west bank",
                    point(2957, 3214, 0), point(2946, 3368, 0), 2, 2),
            route("F2P-08", "Falador west bank to Barbarian Village",
                    point(2946, 3368, 0), point(3082, 3420, 0), 2, 2),
            route("F2P-09", "Barbarian Village to Edgeville bank",
                    point(3082, 3420, 0), point(3093, 3493, 0), 2, 2),
            route("F2P-10", "Edgeville bank to Grand Exchange",
                    point(3093, 3493, 0), point(3164, 3486, 0), 2, 2),
            route("F2P-11", "Grand Exchange to Varrock west bank",
                    point(3164, 3486, 0), point(3185, 3441, 0), 2, 2),
            route("F2P-12", "Varrock west bank to Varrock east bank",
                    point(3185, 3441, 0), point(3253, 3420, 0), 2, 2),
            route("F2P-13", "Varrock east bank to Lumbridge courtyard",
                    point(3253, 3420, 0), point(3222, 3218, 0), 2, 2),
            route("F2P-14", "Draynor bank to Draynor Manor outside",
                    point(3092, 3245, 0), point(3109, 3341, 0), 2, 1),
            route("F2P-15", "Draynor Manor outside to Draynor Manor interior",
                    point(3109, 3341, 0), point(3106, 3363, 0), 1, 1),
            route("F2P-16", "Draynor Manor interior to Draynor bank",
                    point(3106, 3363, 0), point(3092, 3245, 0), 1, 2),
            repeatedRoute("F2P-17", "Varrock manhole to Varrock Sewers",
                    point(3236, 3458, 0), point(3237, 9858, 0), 1, 1, 5, true, true)
    );
    static final List<F2PWebWalkerRoute> SELECTION_GATE_ROUTES = List.of(
            bankSelectionGateRoute("F2P-18", "Lumbridge to Champions' Guild by canoe",
                    point(3243, 3237, 0), point(3199, 3344, 0), 2, 2, 3,
                    true, false, Rs2TransportExecutor.CANOE, 5, 10),
            selectionGateRoute("F2P-19", "Port Sarim to Musa Point by ship",
                    point(3029, 3217, 0), point(2956, 3146, 0), 1, 1, 2,
                    false, true, Rs2TransportExecutor.TERMINAL_TRAVEL, 3),
            activeReplanSelectionGateRoute("F2P-20", "Port Sarim to Falador with active replans",
                    point(3029, 3217, 0), point(2946, 3368, 0), 2, 2, 12),
            recoveryReplanSelectionGateRoute("F2P-21", "Port Sarim to Rimmington with recovery replans",
                    point(3029, 3217, 0), point(2957, 3214, 0), 2, 2, 3, 2)
    );
    static final List<F2PWebWalkerRoute> MEMBERS_ROUTES = List.of(
            membersNetworkRoute("P2P-01", "Al Kharid to Tempoross Cove by ferry",
                    point(3272, 3143, 0), point(3148, 2843, 0), 1, 1, 3,
                    Rs2TransportExecutor.TERMINAL_TRAVEL, 5),
            membersNetworkRoute("P2P-02", "Al Kharid to Gnome Stronghold by glider",
                    point(3284, 3211, 0), point(2465, 3501, 3), 1, 1, 2,
                    Rs2TransportExecutor.GNOME_GLIDER, 3),
            membersNetworkRoute("P2P-03", "Grand Exchange to Gnome Stronghold by spirit tree",
                    point(3185, 3508, 0), point(2461, 3444, 0), 1, 1, 2,
                    Rs2TransportExecutor.SPIRIT_TREE, 3),
            membersNetworkRoute("P2P-04", "Ardougne to Brimhaven by members ship",
                    point(2673, 3275, 0), point(2775, 3233, 1), 1, 1, 3,
                    Rs2TransportExecutor.TERMINAL_TRAVEL, 5)
    );
    static final List<F2PWebWalkerRoute> ROUTES;

    static {
        List<F2PWebWalkerRoute> routes = new ArrayList<>(REQUIRED_ROUTES);
        routes.addAll(SELECTION_GATE_ROUTES);
        routes.addAll(MEMBERS_ROUTES);
        ROUTES = List.copyOf(routes);
    }

    final String id;
    final String name;
    final WorldPoint start;
    final WorldPoint destination;
    final int startTolerance;
    final int destinationTolerance;
    final int repetitions;
    final boolean currentLocationStart;
    final boolean requireF2PWorld;
    final boolean requireMembersWorld;
    final boolean forceNoAgilityShortcuts;
    final boolean forceNoTeleports;
    final boolean forceCanoes;
    final boolean forceShips;
    final Rs2TransportExecutor expectedShadowExecutor;
    final int minimumExpectedShadowExecutions;
    final int forcedActiveReplans;
    final int minimumExpectedActiveReplanComparisons;
    final int forcedRecoveryReplans;
    final int forcedSetupRecoveryReplans;
    final int minimumExpectedRecoveryReplanComparisons;
    final int minimumExpectedRecoveryArrivals;
    final int forcedBankRouteComparisons;
    final int minimumExpectedBankRouteFromBankComparisons;
    final int minimumExpectedBankRouteFromBankItemGatedComparisons;

    private F2PWebWalkerRoute(
            String id,
            String name,
            WorldPoint start,
            WorldPoint destination,
            int startTolerance,
            int destinationTolerance,
            int repetitions,
            boolean currentLocationStart,
            boolean requireF2PWorld,
            boolean requireMembersWorld,
            boolean forceNoAgilityShortcuts,
            boolean forceNoTeleports,
            boolean forceCanoes,
            boolean forceShips,
            Rs2TransportExecutor expectedShadowExecutor,
            int minimumExpectedShadowExecutions,
            int forcedActiveReplans,
            int minimumExpectedActiveReplanComparisons,
            int forcedRecoveryReplans,
            int forcedSetupRecoveryReplans,
            int minimumExpectedRecoveryReplanComparisons,
            int minimumExpectedRecoveryArrivals,
            int forcedBankRouteComparisons,
            int minimumExpectedBankRouteFromBankComparisons,
            int minimumExpectedBankRouteFromBankItemGatedComparisons
    ) {
        this.id = id;
        this.name = name;
        this.start = start;
        this.destination = destination;
        this.startTolerance = startTolerance;
        this.destinationTolerance = destinationTolerance;
        this.repetitions = repetitions;
        this.currentLocationStart = currentLocationStart;
        this.requireF2PWorld = requireF2PWorld;
        this.requireMembersWorld = requireMembersWorld;
        this.forceNoAgilityShortcuts = forceNoAgilityShortcuts;
        this.forceNoTeleports = forceNoTeleports;
        this.forceCanoes = forceCanoes;
        this.forceShips = forceShips;
        this.expectedShadowExecutor = expectedShadowExecutor;
        this.minimumExpectedShadowExecutions = minimumExpectedShadowExecutions;
        this.forcedActiveReplans = forcedActiveReplans;
        this.minimumExpectedActiveReplanComparisons = minimumExpectedActiveReplanComparisons;
        this.forcedRecoveryReplans = forcedRecoveryReplans;
        this.forcedSetupRecoveryReplans = forcedSetupRecoveryReplans;
        this.minimumExpectedRecoveryReplanComparisons = minimumExpectedRecoveryReplanComparisons;
        this.minimumExpectedRecoveryArrivals = minimumExpectedRecoveryArrivals;
        this.forcedBankRouteComparisons = forcedBankRouteComparisons;
        this.minimumExpectedBankRouteFromBankComparisons = minimumExpectedBankRouteFromBankComparisons;
        this.minimumExpectedBankRouteFromBankItemGatedComparisons =
                minimumExpectedBankRouteFromBankItemGatedComparisons;
    }

    static List<F2PWebWalkerRoute> selected(String routeFilter) {
        if (routeFilter == null || routeFilter.isBlank() || "all".equalsIgnoreCase(routeFilter)) {
            return REQUIRED_ROUTES;
        }
        if ("members".equalsIgnoreCase(routeFilter)) {
            return MEMBERS_ROUTES;
        }

        List<String> requested = Arrays.stream(routeFilter.split(","))
                .map(id -> id.trim().toUpperCase(Locale.ROOT))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toList());

        List<F2PWebWalkerRoute> routes = ROUTES.stream()
                .filter(route -> requested.contains(route.id.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toList());

        if (routes.size() != requested.size()) {
            List<String> known = ROUTES.stream().map(route -> route.id).collect(Collectors.toList());
            throw new IllegalArgumentException("Unknown webwalker route filter '" + routeFilter + "'. Known routes: " + known);
        }

        return routes;
    }

    private static F2PWebWalkerRoute route(
            String id,
            String name,
            WorldPoint start,
            WorldPoint destination,
            int startTolerance,
            int destinationTolerance
    ) {
        return new F2PWebWalkerRoute(id, name, start, destination, startTolerance, destinationTolerance,
                1, false, false, false, false, false, false, false, null, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0);
    }

    private static F2PWebWalkerRoute repeatedRoute(
            String id,
            String name,
            WorldPoint start,
            WorldPoint destination,
            int startTolerance,
            int destinationTolerance,
            int repetitions,
            boolean forceNoAgilityShortcuts,
            boolean forceNoTeleports
    ) {
        return new F2PWebWalkerRoute(id, name, start, destination, startTolerance, destinationTolerance,
                repetitions, false, true, false, forceNoAgilityShortcuts, forceNoTeleports, false, false, null, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static F2PWebWalkerRoute selectionGateRoute(
            String id,
            String name,
            WorldPoint start,
            WorldPoint destination,
            int startTolerance,
            int destinationTolerance,
            int repetitions,
            boolean forceCanoes,
            boolean forceShips,
            Rs2TransportExecutor expectedShadowExecutor,
            int minimumExpectedShadowExecutions
    ) {
        return new F2PWebWalkerRoute(id, name, start, destination, startTolerance, destinationTolerance,
                repetitions, false, false, false, true, true, forceCanoes, forceShips, expectedShadowExecutor,
                minimumExpectedShadowExecutions, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static F2PWebWalkerRoute bankSelectionGateRoute(
            String id,
            String name,
            WorldPoint start,
            WorldPoint destination,
            int startTolerance,
            int destinationTolerance,
            int repetitions,
            boolean forceCanoes,
            boolean forceShips,
            Rs2TransportExecutor expectedShadowExecutor,
            int minimumExpectedShadowExecutions,
            int bankRouteComparisons
    ) {
        return new F2PWebWalkerRoute(id, name, start, destination, startTolerance, destinationTolerance,
                repetitions, false, false, false, true, true, forceCanoes, forceShips, expectedShadowExecutor,
                minimumExpectedShadowExecutions, 0, 0, 0, 0, 0, 0,
                bankRouteComparisons, bankRouteComparisons, bankRouteComparisons);
    }

    private static F2PWebWalkerRoute activeReplanSelectionGateRoute(
            String id,
            String name,
            WorldPoint start,
            WorldPoint destination,
            int startTolerance,
            int destinationTolerance,
            int forcedActiveReplans
    ) {
        return new F2PWebWalkerRoute(id, name, start, destination, startTolerance, destinationTolerance,
                1, false, false, false, true, true, false, true, null, 0,
                forcedActiveReplans, forcedActiveReplans, 0, 0, 0, 0, 0, 0, 0);
    }

    private static F2PWebWalkerRoute recoveryReplanSelectionGateRoute(
            String id,
            String name,
            WorldPoint start,
            WorldPoint destination,
            int startTolerance,
            int destinationTolerance,
            int repetitions,
            int forcedRecoveryReplans
    ) {
        int evidenceLegs = repetitions + Math.max(0, repetitions - 1);
        return new F2PWebWalkerRoute(id, name, start, destination, startTolerance, destinationTolerance,
                repetitions, false, true, false, true, true, false, false, null, 0,
                0, 0, forcedRecoveryReplans, forcedRecoveryReplans,
                evidenceLegs * forcedRecoveryReplans, evidenceLegs, 0, 0, 0);
    }

    private static F2PWebWalkerRoute membersNetworkRoute(
            String id,
            String name,
            WorldPoint start,
            WorldPoint destination,
            int startTolerance,
            int destinationTolerance,
            int repetitions,
            Rs2TransportExecutor expectedShadowExecutor,
            int minimumExpectedShadowExecutions
    ) {
        return new F2PWebWalkerRoute(id, name, start, destination, startTolerance, destinationTolerance,
                repetitions, false, false, true, true, true, false, false, expectedShadowExecutor,
                minimumExpectedShadowExecutions, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static WorldPoint point(int x, int y, int plane) {
        return new WorldPoint(x, y, plane);
    }
}
