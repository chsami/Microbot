package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathEdge;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Route-regression corpus: real end-to-end routes computed with the real pathfinder, collision map and
 * transport catalog, asserting the PROPERTIES that past live incidents depended on (uses the ferry, crosses
 * the gate, goes through the door, honest partial when unreachable) rather than exact tile sequences.
 * <p>
 * Every journey here was once a live failure or a live fix: pin it, and a data regression (a filtered
 * ferry, a lost transport row, a collision-map hole) fails the build instead of stranding a walk mid-quest.
 * Scenario "player state" (holding a Shantay ticket, holding coins) is expressed by filtering which
 * transports are usable, mirroring what PathfinderConfig's live refresh does with real inventory/quest
 * state — the pathfinder itself is exercised unmodified.
 */
public class WalkerRouteCorpusTest {

    private static SplitFlagMap collisionMap;
    private static HashMap<WorldPoint, Set<Transport>> allTransports;

    @BeforeClass
    public static void load() {
        collisionMap = SplitFlagMap.fromResources();
        allTransports = Transport.loadAllFromResources();
    }

    // ---- harness -----------------------------------------------------------------------------------

    /** A transport with no requirements at all — usable by any account in any state. */
    private static boolean unrestricted(Transport t) {
        return (t.getQuests() == null || t.getQuests().isEmpty())
                && (t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty())
                && (t.getVarbits() == null || t.getVarbits().isEmpty())
                && (t.getVarplayers() == null || t.getVarplayers().isEmpty())
                && t.getCurrencyAmount() <= 0
                && Arrays.stream(t.getSkillLevels()).allMatch(l -> l <= 1);
    }

    /**
     * Config whose usable-transport set is {@code allTransports} filtered by {@code allow}, injected via
     * the same reflection seam the rest of this suite already uses for {@code calculationCutoffMillis} —
     * the live population path (refreshTransports) needs a client.
     */
    @SuppressWarnings("unchecked")
    private static PathfinderConfig configWith(Predicate<Transport> allow) {
        PathfinderConfig config = new PathfinderConfig(
                collisionMap, new HashMap<>(), Collections.emptyList(), null, null);
        try {
            Field cutoff = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
            cutoff.setAccessible(true);
            cutoff.setLong(config, 10000);

            Field transportsField = PathfinderConfig.class.getDeclaredField("transports");
            transportsField.setAccessible(true);
            Map<WorldPoint, Set<Transport>> transports =
                    (Map<WorldPoint, Set<Transport>>) transportsField.get(config);

            Field packedField = PathfinderConfig.class.getDeclaredField("transportsPacked");
            packedField.setAccessible(true);
            PrimitiveIntHashMap<Set<Transport>> packed =
                    (PrimitiveIntHashMap<Set<Transport>>) packedField.get(config);

            for (Map.Entry<WorldPoint, Set<Transport>> e : allTransports.entrySet()) {
                if (e.getKey() == null) {
                    continue; // teleports (null origin) live in usableTeleports; not corpus scope
                }
                Set<Transport> allowed = e.getValue().stream().filter(allow).collect(Collectors.toSet());
                if (allowed.isEmpty()) {
                    continue;
                }
                transports.put(e.getKey(), allowed);
                packed.put(WorldPointUtil.packWorldPoint(e.getKey()), allowed);
            }
        } catch (Exception ex) {
            throw new RuntimeException("corpus transport injection failed", ex);
        }
        return config;
    }

    private static Pathfinder runPathfinder(PathfinderConfig config, WorldPoint from, WorldPoint to) {
        Pathfinder pf = new Pathfinder(config, from, to);
        pf.run();
        assertTrue("pathfinder did not complete for " + from + " -> " + to, pf.isDone());
        return pf;
    }

    private static List<WorldPoint> route(PathfinderConfig config, WorldPoint from, WorldPoint to) {
        return runPathfinder(config, from, to).getPath();
    }

    private static boolean selectsTransport(Pathfinder pathfinder, Predicate<Transport> predicate) {
        List<PathEdge> edges = pathfinder.getPathEdges();
        return edges != null && edges.stream().anyMatch(edge ->
                edge.getTransport() != null && predicate.test(edge.getTransport()));
    }

    private static boolean selectsTransportObject(Pathfinder pathfinder, int objectId) {
        return selectsTransport(pathfinder, transport -> transport.getObjectId() == objectId);
    }

    private static boolean arrives(List<WorldPoint> path, WorldPoint goal, int tolerance) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        WorldPoint end = path.get(path.size() - 1);
        return end.getPlane() == goal.getPlane() && end.distanceTo2D(goal) <= tolerance;
    }

    private static boolean visits(List<WorldPoint> path, WorldPoint tile, int radius) {
        return path != null && path.stream().anyMatch(p ->
                p.getPlane() == tile.getPlane() && p.distanceTo2D(tile) <= radius);
    }

    private static boolean usesTransportType(List<WorldPoint> path, TransportType type) {
        if (path == null || path.size() < 2) {
            return false;
        }
        for (int index = 0; index < path.size() - 1; index++) {
            WorldPoint origin = path.get(index);
            WorldPoint destination = path.get(index + 1);
            if (allTransports.getOrDefault(origin, Collections.emptySet()).stream().anyMatch(transport ->
                    transport.getType() == type && destination.equals(transport.getDestination()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesTransportObject(List<WorldPoint> path, int objectId) {
        if (path == null || path.size() < 2) {
            return false;
        }
        for (int index = 0; index < path.size() - 1; index++) {
            WorldPoint origin = path.get(index);
            WorldPoint destination = path.get(index + 1);
            if (allTransports.getOrDefault(origin, Collections.emptySet()).stream().anyMatch(transport ->
                    transport.getObjectId() == objectId && destination.equals(transport.getDestination()))) {
                return true;
            }
        }
        return false;
    }

    private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);

    // ---- baseline ----------------------------------------------------------------------------------

    @Test
    public void standardApeAtollSpellRetainsSourceAwareRequirementsWithoutMovingItsLanding() {
        WorldPoint reviewedLanding = new WorldPoint(2797, 2798, 1);
        Transport teleport = allTransports.getOrDefault(null, Collections.emptySet()).stream()
                .filter(transport -> transport.getType() == TransportType.TELEPORTATION_SPELL)
                .filter(transport -> reviewedLanding.equals(transport.getDestination()))
                .filter(transport -> "Ape Atoll Teleport".equals(transport.getDisplayInfo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("standard Ape Atoll spell row is missing"));

        assertEquals("the requirement import must not change the reviewed landing",
                reviewedLanding, teleport.getDestination());
        assertEquals("fire, water, law and banana are separate AND-clauses",
                4, teleport.getItemRequirements().size());
        TransportItemRequirement fire = teleport.getItemRequirements().stream()
                .filter(requirement -> requirement.getAlternatives().containsKey(ItemID.FIRERUNE))
                .findFirst()
                .orElseThrow(() -> new AssertionError("fire-rune clause is missing"));
        TransportItemRequirement water = teleport.getItemRequirements().stream()
                .filter(requirement -> requirement.getAlternatives().containsKey(ItemID.WATERRUNE))
                .findFirst()
                .orElseThrow(() -> new AssertionError("water-rune clause is missing"));
        TransportItemRequirement banana = teleport.getItemRequirements().stream()
                .filter(requirement -> requirement.getAlternatives().containsKey(ItemID.BANANA))
                .findFirst()
                .orElseThrow(() -> new AssertionError("banana clause is missing"));

        assertTrue("one Twinflame staff must satisfy both elemental clauses",
                fire.getStaffAlternatives().contains(ItemID.TWINFLAME_STAFF)
                        && water.getStaffAlternatives().contains(ItemID.TWINFLAME_STAFF));
        assertTrue("ordinary inventory items must not become equipment providers",
                banana.getStaffAlternatives().isEmpty() && banana.getOffhandAlternatives().isEmpty());
    }

    @Test
    public void lumbridgeToGrandExchange_plainWalkArrives() {
        List<WorldPoint> path = route(configWith(WalkerRouteCorpusTest::unrestricted),
                LUMBRIDGE, new WorldPoint(3164, 3485, 0));
        assertTrue("baseline overland route must arrive", arrives(path, new WorldPoint(3164, 3485, 0), 5));
    }

    @Test
    public void quetzalNetworkUsesCurrentQuetzacalliLanding() {
        WorldPoint aldarin = new WorldPoint(1389, 2901, 0);
        WorldPoint quetzacalli = new WorldPoint(1510, 3222, 0);
        List<WorldPoint> path = route(configWith(transport -> unrestricted(transport)
                        || transport.getType() == TransportType.QUETZAL),
                aldarin, quetzacalli);

        assertTrue("quetzal route must arrive at the current Gorge landing",
                arrives(path, quetzacalli, 1));
        assertTrue("the long Varlamore crossing must use the QUETZAL network",
                usesTransportType(path, TransportType.QUETZAL));
    }

    @Test
    public void riverDougneCanoeConnectsCastleWarsToTreeGnomeStronghold() {
        WorldPoint castleWarsStation = new WorldPoint(2439, 3135, 0);
        WorldPoint strongholdLanding = new WorldPoint(2523, 3408, 0);
        List<WorldPoint> path = route(configWith(transport -> unrestricted(transport)
                        || transport.getType() == TransportType.CANOE),
                castleWarsStation, strongholdLanding);

        assertTrue("River Dougne route must arrive at Tree Gnome Stronghold",
                arrives(path, strongholdLanding, 1));
        assertTrue("the long western crossing must use the CANOE network",
                usesTransportType(path, TransportType.CANOE));
    }

    @Test
    public void lagunaAuroraeSpiritTreeHasTheCompleteReviewedOutboundPerimeter() {
        Set<WorldPoint> reviewedOrigins = Set.of(
                new WorldPoint(1201, 2788, 0),
                new WorldPoint(1202, 2788, 0),
                new WorldPoint(1201, 2787, 0),
                new WorldPoint(1201, 2786, 0),
                new WorldPoint(1204, 2786, 0),
                new WorldPoint(1201, 2785, 0),
                new WorldPoint(1202, 2785, 0),
                new WorldPoint(1203, 2785, 0),
                new WorldPoint(1204, 2785, 0));
        WorldPoint grandExchange = new WorldPoint(3185, 3508, 0);

        for (WorldPoint origin : reviewedOrigins) {
            assertTrue("Laguna perimeter origin must offer the reviewed spirit-tree network: " + origin,
                    allTransports.getOrDefault(origin, Collections.emptySet()).stream()
                            .anyMatch(transport -> transport.getType() == TransportType.SPIRIT_TREE
                                    && transport.getObjectId() == 26262
                                    && grandExchange.equals(transport.getDestination())));
        }

        WorldPoint pohSpiritTree = new WorldPoint(2007, 5700, 0);
        assertFalse("POH spirit-tree execution is programmatic and must not be duplicated in static data",
                allTransports.getOrDefault(pohSpiritTree, Collections.emptySet()).stream()
                        .anyMatch(transport -> transport.getType() == TransportType.SPIRIT_TREE));
        assertFalse("the static destination list must not duplicate the programmatic POH spirit tree",
                allTransports.getOrDefault(null, Collections.emptySet()).stream()
                        .anyMatch(transport -> transport.getType() == TransportType.SPIRIT_TREE
                                && pohSpiritTree.equals(transport.getDestination())));

        WorldPoint northWestApproach = new WorldPoint(1201, 2788, 0);
        List<WorldPoint> path = route(configWith(transport -> unrestricted(transport)
                        || transport.getType() == TransportType.SPIRIT_TREE),
                northWestApproach, grandExchange);
        assertTrue("Laguna Aurorae must route outbound through its spirit tree",
                arrives(path, grandExchange, 1));
        assertTrue("the selected Laguna edge must retain the current object id",
                usesTransportObject(path, 26262));
    }

    @Test
    public void elementalWorkshopWallUsesConcreteObjectAndFailsClosedForUnverifiedKeyring() {
        WorldPoint south = new WorldPoint(2709, 3495, 0);
        WorldPoint north = new WorldPoint(2709, 3496, 0);
        Set<WorldPoint> endpoints = Set.of(south, north);

        for (WorldPoint origin : endpoints) {
            WorldPoint destination = origin.equals(south) ? north : south;
            Transport wall = allTransports.getOrDefault(origin, Collections.emptySet()).stream()
                    .filter(transport -> destination.equals(transport.getDestination()))
                    .filter(transport -> transport.getObjectId() == 26115)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Elemental Workshop wall edge is missing: " + origin + " -> " + destination));

            assertEquals("the reviewed wall action must remain explicit", "Open", wall.getAction());
            assertEquals("the wall has one OR-clause", 1, wall.getItemRequirements().size());
            assertEquals("only the concrete battered key is currently verifiable",
                    Collections.singleton(ItemID.ELEMENTAL_WORKSHOP_KEY),
                    wall.getItemRequirements().get(0).getItemIds());
            assertFalse("a steel key ring does not prove that it contains the battered key",
                    wall.getItemRequirements().get(0).getItemIds().contains(ItemID.FAVOUR_KEY_RING));
        }

        Predicate<Transport> batteredKeyState = transport -> unrestricted(transport)
                || transport.getItemRequirements().stream().allMatch(requirement ->
                        requirement.isSatisfiedBy(itemId ->
                                itemId == ItemID.ELEMENTAL_WORKSHOP_KEY ? 1 : 0));
        Pathfinder withKeyPathfinder = runPathfinder(configWith(batteredKeyState), south, north);
        List<WorldPoint> withKey = withKeyPathfinder.getPath();
        assertTrue("the concrete battered key must unlock the direct wall crossing",
                arrives(withKey, north, 0));
        assertTrue("the route must select the current Elemental Workshop wall object",
                selectsTransportObject(withKeyPathfinder, 26115));

        Predicate<Transport> keyRingOnlyState = transport -> unrestricted(transport)
                || transport.getItemRequirements().stream().allMatch(requirement ->
                        requirement.isSatisfiedBy(itemId -> itemId == ItemID.FAVOUR_KEY_RING ? 1 : 0));
        Pathfinder keyRingOnlyPathfinder = runPathfinder(configWith(keyRingOnlyState), south, north);
        List<WorldPoint> keyRingOnly = keyRingOnlyPathfinder.getPath();
        assertFalse("an unverified key-ring state must not select the wall transport",
                selectsTransportObject(keyRingOnlyPathfinder, 26115));
    }

    @Test
    public void lumbridgeFarmFenceUsesCurrentOneTileLanding() {
        WorldPoint south = new WorldPoint(3240, 3334, 0);
        WorldPoint north = new WorldPoint(3240, 3335, 0);
        List<WorldPoint> path = route(configWith(transport -> unrestricted(transport)
                        || transport.getObjectId() == 16518),
                south, north);

        assertTrue("the current fence landing must be reachable", arrives(path, north, 0));
        assertTrue("crossing the closed fence must select the agility shortcut edge",
                usesTransportType(path, TransportType.AGILITY_SHORTCUT));
    }

    @Test
    public void northernVarlamoreRocksUseCurrentEightTileLanding() {
        WorldPoint south = new WorldPoint(1324, 3777, 0);
        WorldPoint north = new WorldPoint(1324, 3785, 0);
        List<WorldPoint> path = route(configWith(transport -> unrestricted(transport)
                        || transport.getObjectId() == 34397),
                south, north);

        assertTrue("the reviewed northern landing must be reachable", arrives(path, north, 0));
        assertTrue("the rock face must select the agility shortcut edge",
                usesTransportType(path, TransportType.AGILITY_SHORTCUT));
    }

    @Test
    public void trollheimClimbingRocksUseBootsGatedAscent() {
        WorldPoint west = new WorldPoint(2820, 3635, 0);
        WorldPoint east = new WorldPoint(2822, 3635, 0);
        List<WorldPoint> path = route(configWith(transport -> unrestricted(transport)
                        || (transport.getType() == TransportType.AGILITY_SHORTCUT
                        && transport.getObjectId() == 3748)),
                west, east);

        assertTrue("the climbing-rock landing must be reachable", arrives(path, east, 0));
        assertTrue("the ascent must use the boots-gated agility edge",
                usesTransportType(path, TransportType.AGILITY_SHORTCUT));
    }

    @Test
    public void isafdarDenseForestChainUsesReviewedShortcutLandings() {
        WorldPoint south = new WorldPoint(2188, 3162, 0);
        WorldPoint north = new WorldPoint(2188, 3171, 0);
        Set<Integer> chainObjectIds = Set.of(3939, 3998, 3999);
        List<WorldPoint> path = route(configWith(transport -> unrestricted(transport)
                        || (transport.getType() == TransportType.AGILITY_SHORTCUT
                        && chainObjectIds.contains(transport.getObjectId()))),
                south, north);

        assertTrue("the three-obstacle forest chain must reach its reviewed northern landing",
                arrives(path, north, 0));
        assertTrue("the route must traverse the first dense-forest landing",
                visits(path, new WorldPoint(2188, 3165, 0), 0));
        assertTrue("the route must traverse the second dense-forest landing",
                visits(path, new WorldPoint(2188, 3168, 0), 0));
        assertTrue("the forest chain must use Agility-gated shortcut edges",
                usesTransportType(path, TransportType.AGILITY_SHORTCUT));
    }

    @Test
    public void brimhavenDungeonPipeUsesAgilityShortcut() {
        WorldPoint south = new WorldPoint(2698, 9492, 0);
        WorldPoint north = new WorldPoint(2698, 9500, 0);
        List<WorldPoint> path = route(configWith(transport -> unrestricted(transport)
                        || transport.getObjectId() == 21727), south, north);

        assertTrue("Brimhaven pipe route must reach the reviewed landing", arrives(path, north, 0));
        assertTrue("Brimhaven pipe route must use object 21727", usesTransportObject(path, 21727));
        assertTrue("Brimhaven pipe must be represented as an agility shortcut",
                usesTransportType(path, TransportType.AGILITY_SHORTCUT));
    }

    @Test
    public void karamjaRocksUseAgilityShortcut() {
        WorldPoint west = new WorldPoint(2791, 2978, 0);
        WorldPoint east = new WorldPoint(2795, 2978, 0);
        List<WorldPoint> path = route(configWith(transport -> unrestricted(transport)
                        || transport.getObjectId() == 2231), west, east);

        assertTrue("Karamja rocks route must reach the reviewed landing", arrives(path, east, 0));
        assertTrue("Karamja rocks route must use object 2231", usesTransportObject(path, 2231));
        assertTrue("Karamja rocks must be represented as an agility shortcut",
                usesTransportType(path, TransportType.AGILITY_SHORTCUT));
    }

    @Test
    public void lumbridgeCellarHoleUsesQuestProgressShortcut() {
        WorldPoint west = new WorldPoint(3219, 9618, 0);
        WorldPoint east = new WorldPoint(3221, 9618, 0);
        List<WorldPoint> path = route(configWith(transport -> unrestricted(transport)
                        || transport.getObjectId() == 6905), west, east);

        assertTrue("Lumbridge cellar route must reach the reviewed hole landing", arrives(path, east, 0));
        assertTrue("Lumbridge cellar route must use hole object 6905", usesTransportObject(path, 6905));
        assertTrue("Lumbridge cellar hole must be represented as an agility shortcut",
                usesTransportType(path, TransportType.AGILITY_SHORTCUT));
    }

    @Test
    public void slayerTowerGroundFloorChainUsesAgilityShortcut() {
        WorldPoint ground = new WorldPoint(3421, 3550, 0);
        WorldPoint firstFloor = new WorldPoint(3421, 3550, 1);
        List<WorldPoint> path = route(configWith(transport -> unrestricted(transport)
                        || transport.getObjectId() == 16537), ground, firstFloor);

        assertTrue("Slayer Tower chain must reach the first floor", arrives(path, firstFloor, 0));
        assertTrue("Slayer Tower route must use chain object 16537", usesTransportObject(path, 16537));
        assertTrue("Slayer Tower chain must be represented as an agility shortcut",
                usesTransportType(path, TransportType.AGILITY_SHORTCUT));
    }

    @Test
    public void darkmeyerWallChainUsesBothAgilityShortcuts() {
        WorldPoint west = new WorldPoint(3667, 3375, 0);
        WorldPoint middle = new WorldPoint(3670, 3375, 0);
        WorldPoint east = new WorldPoint(3673, 3375, 0);
        List<WorldPoint> path = route(configWith(transport -> unrestricted(transport)
                        || transport.getObjectId() == 39541
                        || transport.getObjectId() == 39542), west, east);

        assertTrue("Darkmeyer wall chain must reach the eastern landing", arrives(path, east, 0));
        assertTrue("Darkmeyer wall chain must cross the middle landing", visits(path, middle, 0));
        assertTrue("Darkmeyer wall chain must use west wall object 39542", usesTransportObject(path, 39542));
        assertTrue("Darkmeyer wall chain must use east wall object 39541", usesTransportObject(path, 39541));
        assertTrue("Darkmeyer walls must be represented as agility shortcuts",
                usesTransportType(path, TransportType.AGILITY_SHORTCUT));
    }

    // ---- Falador-area farm (the walk-1 "hairpin" report — resolved: no bug) ------------------------

    @Test
    public void farmFieldGoal_arrivesViaTheHonestTour() {
        // The reported 67-tile "out-and-back" was two parallel rows on OPPOSITE sides of the field
        // fence: the goal tile sits inside the enclosure, whose only mapped opening is the far west
        // corner, so the tour is the honest shortest path. Cold reproduction settled static-vs-live
        // in one run; live, the walk correctly ended within-distance on the road without walking the
        // loop. Pinned so a collision regression around the fence turns this into a loud failure.
        WorldPoint faladorCentre = new WorldPoint(2964, 3378, 0);
        WorldPoint fieldGoal = new WorldPoint(2930, 3449, 0);
        List<WorldPoint> path = route(configWith(WalkerRouteCorpusTest::unrestricted), faladorCentre, fieldGoal);
        assertTrue("goal inside the fenced field must remain reachable", arrives(path, fieldGoal, 5));
    }

    // ---- Tempoross / Ruins of Unkah (the route-flapping incident) ----------------------------------

    @Test
    public void ruinsOfUnkah_reachedViaTheFerry() {
        // The Unkah peninsula is ferry-only (probe-verified: no walkable approach exists). If the ferry
        // rows in boats.tsv are ever lost or gain a bogus requirement, this fails instead of the walker
        // bouncing between partial routes at the coast.
        WorldPoint unkahBank = new WorldPoint(3156, 2835, 0);
        List<WorldPoint> path = route(configWith(WalkerRouteCorpusTest::unrestricted), LUMBRIDGE, unkahBank);
        assertTrue("route to Ruins of Unkah must arrive", arrives(path, unkahBank, 5));
        assertTrue("route to Ruins of Unkah must use the ferry landing",
                visits(path, new WorldPoint(3148, 2843, 0), 3));
        assertTrue("route to Ruins of Unkah must contain a BOAT transport edge",
                usesTransportType(path, TransportType.BOAT));
    }

    @Test
    public void portSarimToMusaPoint_usesShipAndGangplank() {
        WorldPoint portSarim = new WorldPoint(3029, 3217, 0);
        WorldPoint musaPoint = new WorldPoint(2956, 3146, 0);
        List<WorldPoint> path = route(configWith(t -> unrestricted(t)
                        || (t.getType() == TransportType.SHIP
                        && t.getCurrencyAmount() == 30
                        && (t.getQuests() == null || t.getQuests().isEmpty()))),
                portSarim, musaPoint);

        assertTrue("30-coin ship route must arrive at Musa Point", arrives(path, musaPoint, 1));
        assertTrue("Port Sarim to Musa Point must contain a SHIP transport edge",
                usesTransportType(path, TransportType.SHIP));
        assertTrue("Microbot's ship route must retain the Musa Point deck/gangplank transition",
                visits(path, new WorldPoint(2956, 3143, 1), 0));
    }

    @Test
    public void pandemoniumShipsAreQuestAndFareGatedDirectTerminalEdges() {
        WorldPoint portSarim = new WorldPoint(3029, 3217, 0);
        WorldPoint musaPoint = new WorldPoint(2956, 3146, 0);
        WorldPoint pandemonium = new WorldPoint(3064, 3003, 0);
        Object[][] reviewed = {
                {portSarim, pandemonium, 14979, "The Pandemonium", "Pandemonium"},
                {pandemonium, portSarim, 8631, "Port Sarim", "Port Sarim"},
                {musaPoint, pandemonium, 14985, "The Pandemonium", "Pandemonium"},
                {pandemonium, musaPoint, 8631, "Musa Point", "Musa Point"}
        };

        for (Object[] expectation : reviewed) {
            WorldPoint origin = (WorldPoint) expectation[0];
            WorldPoint destination = (WorldPoint) expectation[1];
            int npcId = (int) expectation[2];
            String action = (String) expectation[3];
            String display = (String) expectation[4];
            Transport ship = allTransports.getOrDefault(origin, Collections.emptySet()).stream()
                    .filter(transport -> destination.equals(transport.getDestination()))
                    .filter(transport -> transport.getType() == TransportType.SHIP)
                    .filter(transport -> transport.getObjectId() == npcId)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Pandemonium ship edge is missing: " + origin + " -> " + destination));

            assertEquals("the current NPC menu action must remain exact", action, ship.getAction());
            assertEquals("the network label must remain stable", display, ship.getDisplayInfo());
            assertEquals("all four routes charge the reviewed fare", 30, ship.getCurrencyAmount());
            assertFalse("the Pandemonium quest gate must not be dropped", ship.getQuests().isEmpty());
            assertEquals(TransportExecutionRegistry.Executor.TERMINAL_TRAVEL,
                    TransportExecutionRegistry.executorFor(ship).orElse(null));
            assertEquals(TransportExecutionRegistry.TerminalTravelMode.DIRECT,
                    TransportExecutionRegistry.terminalTravelModeFor(ship).orElse(null));
        }

        Predicate<Transport> pandemoniumShip = transport -> transport.getType() == TransportType.SHIP
                && (transport.getObjectId() == 14979
                || transport.getObjectId() == 14985
                || transport.getObjectId() == 8631)
                && (pandemonium.equals(transport.getOrigin())
                || pandemonium.equals(transport.getDestination()));
        Pathfinder unlocked = runPathfinder(configWith(transport -> unrestricted(transport)
                        || pandemoniumShip.test(transport)),
                portSarim, pandemonium);
        assertTrue("the reviewed ship must reach the Pandemonium dock",
                arrives(unlocked.getPath(), pandemonium, 0));
        assertTrue("the selected path must own an explicit Pandemonium SHIP edge",
                selectsTransport(unlocked, pandemoniumShip));

        Pathfinder locked = runPathfinder(configWith(WalkerRouteCorpusTest::unrestricted),
                portSarim, pandemonium);
        assertFalse("without the quest and fare the planner must not select a Pandemonium ship",
                selectsTransport(locked, pandemoniumShip));
    }

    @Test
    public void treeGnomeVillageShortcut_usesElkoyNpcTravel() {
        WorldPoint mazeEntrance = new WorldPoint(2503, 3193, 0);
        WorldPoint villageSide = new WorldPoint(2515, 3159, 0);
        List<WorldPoint> path = route(configWith(t -> unrestricted(t)
                        || (t.getType() == TransportType.NPC && "Elkoy".equals(t.getName()))),
                mazeEntrance, villageSide);

        assertTrue("Elkoy shortcut must reach the village side", arrives(path, villageSide, 1));
        assertTrue("Tree Gnome Village shortcut must contain an NPC transport edge",
                usesTransportType(path, TransportType.NPC));
    }

    @Test
    public void temporossCove_staysAnHonestPartial() {
        // The arena template west of Unkah is unreachable from everywhere. The planner must say so via a
        // partial (endpoint far from the goal), never fabricate an arrival.
        WorldPoint cove = new WorldPoint(3044, 2870, 0);
        List<WorldPoint> path = route(configWith(WalkerRouteCorpusTest::unrestricted), LUMBRIDGE, cove);
        assertTrue("the cove route must return an honest partial path", path != null && !path.isEmpty());
        assertFalse("the cove must not be reachable", arrives(path, cove, 40));
    }

    // ---- Shantay Pass (the buy-at-gate fix) --------------------------------------------------------

    private static final WorldPoint NORTH_OF_GATE = new WorldPoint(3304, 3125, 0);
    private static final WorldPoint SOUTH_OF_GATE = new WorldPoint(3304, 3105, 0);
    private static final WorldPoint GATE = new WorldPoint(3304, 3116, 0);

    @Test
    public void shantaySouthbound_withTicket_crossesTheGate() {
        List<WorldPoint> path = route(configWith(t -> unrestricted(t)
                        || (t.getObjectId() == 4031 && t.getItemIdRequirements() != null
                        && !t.getItemIdRequirements().isEmpty())),
                NORTH_OF_GATE, SOUTH_OF_GATE);
        assertTrue("ticket holder must arrive south of the gate", arrives(path, SOUTH_OF_GATE, 3));
        assertTrue("ticket holder must cross AT the gate", visits(path, GATE, 2));
    }

    @Test
    public void shantaySouthbound_withCoinsOnly_crossesTheGate() {
        // Pins the buy-at-gate fix: 5 coins (no ticket) must keep the gate plannable, or the planner
        // detours several hundred tiles ("it starts then retargets").
        List<WorldPoint> path = route(configWith(t -> unrestricted(t)
                        || (t.getObjectId() == 4031 && t.getCurrencyAmount() == 5)),
                NORTH_OF_GATE, SOUTH_OF_GATE);
        assertTrue("coin holder must arrive south of the gate", arrives(path, SOUTH_OF_GATE, 3));
        assertTrue("coin holder must cross AT the gate", visits(path, GATE, 2));
    }

    @Test
    public void shantaySouthbound_withNothing_neverCrossesTheGate() {
        List<WorldPoint> path = route(configWith(WalkerRouteCorpusTest::unrestricted),
                NORTH_OF_GATE, SOUTH_OF_GATE);
        // Same predicate as the positive tests. The old form required BOTH tiles either side of the
        // gate at radius 0, so a diagonal step across the gate satisfied neither and the assertion
        // passed while the route did cross.
        assertFalse("without a ticket or coins the route must not cross the gate",
                visits(path, GATE, 2));
    }

    // ---- Port Sarim, Wydin's shop (the door-poisoning incident) ------------------------------------

    @Test
    public void wydinsShop_isEnterableWithoutCircling() {
        // The door-poisoning incident: a mis-learned blocked edge turned entering Wydin's shop into an
        // endless circling loop. Pin that the shop interior stays reachable and short from the west side.
        // (The planner may pick either entrance; WHICH door is a cost choice, not a correctness property.)
        WorldPoint outside = new WorldPoint(3008, 3207, 0);
        WorldPoint inside = new WorldPoint(3014, 3206, 0);
        List<WorldPoint> path = route(configWith(WalkerRouteCorpusTest::unrestricted), outside, inside);
        assertTrue("must arrive inside the shop", arrives(path, inside, 2));
        assertTrue("route must be the short way in, not a loop (" + path.size() + " tiles)",
                path.size() < 40);
    }

    @Test
    public void wydinsShopDoorTransportRow_staysInTheCatalog() {
        // The west door edge (3012,3204 <-> 3011,3204) exists as a transport row because the collision
        // map holds that wall closed; the row is what makes the west-side approach plannable at all.
        Set<Transport> atDoor = allTransports.get(new WorldPoint(3012, 3204, 0));
        assertTrue("the Wydin shop door transport row must exist",
                atDoor != null && atDoor.stream().anyMatch(t ->
                        t.getDestination() != null
                                && t.getDestination().equals(new WorldPoint(3011, 3204, 0))));
    }

    @Test
    public void sturdyDoorTransportRows_stayInTheCatalog() {
        // A door that MOVES you through rather than swinging open (the Al Kharid toll-gate pattern):
        // measured live via the agent server on plane 1 — standing at (3026,3511) and opening put the
        // player on (3025,3511), and the reverse held. Collision cannot reveal this one: static and
        // resolved both report every edge passable there, so without these rows the walker plans
        // straight through and then has nothing to open. Both directions are pinned because the
        // measurement covered both.
        assertTransportEdge(new WorldPoint(3026, 3511, 1), new WorldPoint(3025, 3511, 1));
        assertTransportEdge(new WorldPoint(3025, 3511, 1), new WorldPoint(3026, 3511, 1));
    }

    /**
     * The Black Knights' Fortress ladder (object 17148 at 3025,3513,p1) sits behind the Sturdy door.
     * From inside the west room the ladder's own tile is NOT walkable and cannot be reached — the
     * quester stood one tile south of it, got "can't reach", then asked for another equally walled
     * tile. The usable approach is east of the ladder, and reaching it requires the door transport.
     * <p>
     * This pins the walker half of that incident: the route through the door exists. The remaining
     * half is the quester choosing an approach tile by proximity rather than by reachability.
     */
    @Test
    public void bkfLadderApproachIsReachableOnlyThroughTheSturdyDoor() {
        WorldPoint insideWestRoom = new WorldPoint(3024, 3512, 1);
        PathfinderConfig cfg = configWith(WalkerRouteCorpusTest::unrestricted);

        WorldPoint ladderTile = new WorldPoint(3025, 3513, 1);
        assertFalse("the ladder's own tile is an object tile and must stay unreachable",
                arrives(route(cfg, insideWestRoom, ladderTile), ladderTile, 0));

        WorldPoint eastOfLadder = new WorldPoint(3026, 3513, 1);
        assertTrue("the usable approach east of the ladder must be reachable through the door",
                arrives(route(cfg, insideWestRoom, eastOfLadder), eastOfLadder, 0));

        WorldPoint doorFarSide = new WorldPoint(3026, 3511, 1);
        assertTrue("the door's far side must be reachable, i.e. the transport row is doing its job",
                arrives(route(cfg, insideWestRoom, doorFarSide), doorFarSide, 0));
    }

    private static void assertTransportEdge(WorldPoint origin, WorldPoint destination) {
        Set<Transport> at = allTransports.get(origin);
        assertTrue("transport row " + origin + " -> " + destination + " must exist",
                at != null && at.stream().anyMatch(t ->
                        destination.equals(t.getDestination())));
    }

    // ---- White Wolf Mountain tunnel (Fishing Contest reward) ---------------------------------------

    /** Burthorpe/Taverley-side entrance and the Catherby-side exit of the under-mountain tunnel. */
    private static final WorldPoint TUNNEL_EAST_SURFACE = new WorldPoint(2877, 3482, 0);
    private static final WorldPoint TUNNEL_EAST_UNDER = new WorldPoint(2876, 9878, 0);
    private static final WorldPoint TUNNEL_WEST_UNDER = new WorldPoint(2820, 9882, 0);
    private static final WorldPoint TUNNEL_WEST_SURFACE = new WorldPoint(2820, 3486, 0);

    /** Tunnel stair object ids from transports.tsv lines 1255-1260. */
    private static boolean isWhiteWolfTunnel(Transport t) {
        int id = t.getObjectId();
        return id == 54 || id == 55 || id == 56 || id == 57;
    }

    /**
     * Fishing Contest unlocks the tunnel under White Wolf Mountain. The rows, the collision map and the
     * pathfinder were all correct — measured here: the cave is walkable end to end and a surface
     * crossing does route under the mountain once the transport is allowed. What failed live was the
     * runtime quest gate, because Fishing Contest is tracked by a VARPLAYER and the player-state cache
     * only refreshed varbit-tracked quests, so completing it mid-session never opened the gate.
     * <p>
     * Pinned so a lost row or a collision regression in the cave fails here instead of quietly sending
     * every Catherby-bound walk back over the mountain.
     */
    @Test
    public void whiteWolfTunnel_isRoutableWhenTheQuestGateOpens() {
        List<WorldPoint> underground = route(configWith(WalkerRouteCorpusTest::unrestricted),
                TUNNEL_EAST_UNDER, TUNNEL_WEST_UNDER);
        assertTrue("the tunnel cave must be walkable end to end",
                arrives(underground, TUNNEL_WEST_UNDER, 3));

        List<WorldPoint> surface = route(configWith(t -> unrestricted(t) || isWhiteWolfTunnel(t)),
                TUNNEL_EAST_SURFACE, TUNNEL_WEST_SURFACE);
        assertTrue("crossing must arrive on the Catherby side", arrives(surface, TUNNEL_WEST_SURFACE, 3));
        assertTrue("with the tunnel usable the route must go UNDER the mountain",
                visits(surface, TUNNEL_EAST_UNDER, 5));
    }

    /** Without the quest-gated tunnel the crossing must NOT dive into the cave. */
    @Test
    public void whiteWolfTunnel_notUsedWithoutTheQuest() {
        List<WorldPoint> surface = route(configWith(WalkerRouteCorpusTest::unrestricted),
                TUNNEL_EAST_SURFACE, TUNNEL_WEST_SURFACE);
        assertFalse("a player without Fishing Contest must not be routed through the tunnel",
                visits(surface, TUNNEL_EAST_UNDER, 5));
    }

    // ---- Draynor sewers (surface/underground transition coverage) ---------------------------------

    private static final WorldPoint DRAYNOR_SEWER_EAST_SURFACE = new WorldPoint(3118, 3243, 0);
    private static final WorldPoint DRAYNOR_SEWER_EAST_UNDER = new WorldPoint(3118, 9644, 0);
    private static final WorldPoint DRAYNOR_SEWER_WEST_UNDER = new WorldPoint(3084, 9673, 0);

    private static boolean isDraynorWestTransition(Transport transport) {
        WorldPoint origin = transport.getOrigin();
        WorldPoint destination = transport.getDestination();
        if (origin == null || destination == null) {
            return false;
        }
        boolean originWest = origin.getX() >= 3083 && origin.getX() <= 3085
                && (origin.getY() >= 3271 && origin.getY() <= 3273
                || origin.getY() >= 9671 && origin.getY() <= 9673);
        boolean destinationWest = destination.getX() >= 3083 && destination.getX() <= 3085
                && (destination.getY() >= 3271 && destination.getY() <= 3273
                || destination.getY() >= 9671 && destination.getY() <= 9673);
        return originWest && destinationWest;
    }

    @Test
    public void draynorSewer_eastEntranceConnectsSurfaceAndWestUnderground() {
        // Disable the west ladders so both directions must use the east transition and traverse the
        // underground corridor. This prevents a regression from being hidden by walking above ground
        // to a different trapdoor before entering the sewer.
        PathfinderConfig config = configWith(t -> unrestricted(t) && !isDraynorWestTransition(t));

        List<WorldPoint> descending = route(config, DRAYNOR_SEWER_EAST_SURFACE, DRAYNOR_SEWER_WEST_UNDER);
        assertTrue("east trapdoor must reach the west side of Draynor sewers",
                arrives(descending, DRAYNOR_SEWER_WEST_UNDER, 1));
        assertTrue("descent route must enter at the mapped east underground landing",
                visits(descending, DRAYNOR_SEWER_EAST_UNDER, 2));

        List<WorldPoint> ascending = route(config, DRAYNOR_SEWER_WEST_UNDER, DRAYNOR_SEWER_EAST_SURFACE);
        assertTrue("west sewer must return to the surface through the east ladder",
                arrives(ascending, DRAYNOR_SEWER_EAST_SURFACE, 1));
        assertTrue("ascent route must approach the mapped east underground ladder",
                visits(ascending, DRAYNOR_SEWER_EAST_UNDER, 2));
    }

    // ---- Barrows mounds, individual crypts and randomized tunnel boundary -------------------------

    /**
     * Surface dig/route-anchor tile, deterministic individual-crypt stair/landing, sarcophagus approach
     * and exit-stair object id. These values are shared with Quest Helper's reviewed Barrows zones and
     * object steps; the fifth value is the sarcophagus object id, which must never become a static
     * tunnel edge because the empty crypt is randomized per run. A crypt exit can spawn on another tile
     * within its surface mound, so the surface point is a planner anchor rather than an exact live landing.
     */
    private static final Object[][] BARROWS_CRYPTS = {
            {new WorldPoint(3564, 3291, 0), new WorldPoint(3559, 9703, 3),
                    new WorldPoint(3554, 9699, 3), 20667, 20770},
            {new WorldPoint(3575, 3299, 0), new WorldPoint(3558, 9718, 3),
                    new WorldPoint(3555, 9713, 3), 20668, 20720},
            {new WorldPoint(3578, 3281, 0), new WorldPoint(3534, 9706, 3),
                    new WorldPoint(3539, 9702, 3), 20669, 20722},
            {new WorldPoint(3567, 3274, 0), new WorldPoint(3546, 9686, 3),
                    new WorldPoint(3549, 9683, 3), 20670, 20771},
            {new WorldPoint(3553, 3281, 0), new WorldPoint(3566, 9683, 3),
                    new WorldPoint(3568, 9686, 3), 20671, 20721},
            {new WorldPoint(3556, 3297, 0), new WorldPoint(3578, 9704, 3),
                    new WorldPoint(3572, 9706, 3), 20672, 20772}
    };

    private static boolean isBarrowsDig(Transport transport) {
        return TransportExecutionRegistry.executorFor(transport).orElse(null)
                == TransportExecutionRegistry.Executor.BARROWS_DIG;
    }

    private static boolean usesExactTransport(List<WorldPoint> path, WorldPoint origin,
                                              WorldPoint destination,
                                              TransportExecutionRegistry.Executor executor) {
        if (path == null || path.size() < 2) {
            return false;
        }
        for (int index = 0; index < path.size() - 1; index++) {
            if (!origin.equals(path.get(index)) || !destination.equals(path.get(index + 1))) {
                continue;
            }
            if (allTransports.getOrDefault(origin, Collections.emptySet()).stream().anyMatch(transport ->
                    destination.equals(transport.getDestination())
                            && TransportExecutionRegistry.executorFor(transport).orElse(null) == executor)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void barrowsMoundsAndIndividualCryptExitsAreStaticallyRoutable() {
        PathfinderConfig withSpade = configWith(transport -> unrestricted(transport) || isBarrowsDig(transport));
        PathfinderConfig withoutSpecialRequirements = configWith(WalkerRouteCorpusTest::unrestricted);

        for (Object[] crypt : BARROWS_CRYPTS) {
            WorldPoint surface = (WorldPoint) crypt[0];
            WorldPoint stair = (WorldPoint) crypt[1];
            WorldPoint sarcophagusApproach = (WorldPoint) crypt[2];
            int stairObjectId = (int) crypt[3];

            List<WorldPoint> entering = route(withSpade, surface, sarcophagusApproach);
            assertTrue("mound dig must enter the matching individual crypt: " + surface,
                    arrives(entering, sarcophagusApproach, 0));
            assertTrue("mound route must retain the exact spade executor edge: " + surface,
                    usesExactTransport(entering, surface, stair,
                            TransportExecutionRegistry.Executor.BARROWS_DIG));

            List<WorldPoint> leaving = route(withoutSpecialRequirements, sarcophagusApproach, surface);
            assertTrue("individual crypt must route to its own surface-mound anchor: " + stair,
                    arrives(leaving, surface, 2));
            assertTrue("crypt exit must use its reviewed staircase object: " + stairObjectId,
                    usesTransportObject(leaving, stairObjectId));
        }
    }

    @Test
    public void barrowsRandomSarcophagusTunnelIsNotInventedAsAStaticTransport() {
        Set<Integer> sarcophagusIds = Arrays.stream(BARROWS_CRYPTS)
                .map(crypt -> (Integer) crypt[4])
                .collect(Collectors.toSet());
        List<Transport> staticSarcophagusEdges = allTransports.values().stream()
                .flatMap(Set::stream)
                .filter(transport -> sarcophagusIds.contains(transport.getObjectId()))
                .collect(Collectors.toList());

        assertTrue("the empty sarcophagus is randomized and must be observed live, not statically routed: "
                        + staticSarcophagusEdges,
                staticSarcophagusEdges.isEmpty());

        WorldPoint surface = (WorldPoint) BARROWS_CRYPTS[0][0];
        WorldPoint tunnelChest = new WorldPoint(3551, 9695, 0);
        List<WorldPoint> attempted = route(
                configWith(transport -> unrestricted(transport) || isBarrowsDig(transport)),
                surface, tunnelChest);
        assertFalse("a mound dig alone must not claim deterministic access to the randomized tunnel",
                arrives(attempted, tunnelChest, 2));
    }
}
