package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.*;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ShortestPathCoreTest {

	private static SplitFlagMap collisionMap;
	private static final WorldPoint AL_KHARID_GATE_WEST_SOUTH = new WorldPoint(3267, 3227, 0);
	private static final WorldPoint AL_KHARID_GATE_WEST_NORTH = new WorldPoint(3267, 3228, 0);
	private static final WorldPoint AL_KHARID_GATE_EAST_SOUTH = new WorldPoint(3268, 3227, 0);
	private static final WorldPoint AL_KHARID_GATE_EAST_NORTH = new WorldPoint(3268, 3228, 0);
	private static final int AL_KHARID_MINE_MIN_X = 3281;
	private static final int AL_KHARID_MINE_MAX_X = 3300;
	private static final int AL_KHARID_MINE_MIN_Y = 3151;
	private static final int AL_KHARID_MINE_MAX_Y = 3178;

	@BeforeClass
	public static void loadCollisionMap() {
		collisionMap = SplitFlagMap.fromResources();
		assertNotNull("Collision map should load from resources", collisionMap);
		assertNotNull("Region extents should be set", SplitFlagMap.getRegionExtents());
	}

	// ========================
	// PrimitiveIntHashMap Tests
	// ========================

	@Test
	public void testHashMapBasicPutAndGet() {
		PrimitiveIntHashMap<String> map = new PrimitiveIntHashMap<>(16);
		map.put(WorldPointUtil.packWorldPoint(3200, 3200, 0), "lumbridge");
		map.put(WorldPointUtil.packWorldPoint(3222, 3218, 0), "lumbridge_castle");

		assertEquals("lumbridge", map.get(WorldPointUtil.packWorldPoint(3200, 3200, 0)));
		assertEquals("lumbridge_castle", map.get(WorldPointUtil.packWorldPoint(3222, 3218, 0)));
		assertNull(map.get(WorldPointUtil.packWorldPoint(9999, 9999, 0)));
	}

	@Test
	public void testHashMapRehashPreservesAllEntries() {
		PrimitiveIntHashMap<Integer> map = new PrimitiveIntHashMap<>(8, 0.5f);
		int entryCount = 500;

		for (int i = 0; i < entryCount; i++) {
			int packed = WorldPointUtil.packWorldPoint(3000 + (i % 200), 3000 + (i / 200), 0);
			map.put(packed, i);
		}

		int found = 0;
		for (int i = 0; i < entryCount; i++) {
			int packed = WorldPointUtil.packWorldPoint(3000 + (i % 200), 3000 + (i / 200), 0);
			Integer val = map.get(packed);
			if (val != null && val == i) {
				found++;
			}
		}
		assertEquals("All entries must survive rehash", entryCount, found);
	}

	@Test
	public void testHashMapStressRehash() {
		PrimitiveIntHashMap<Set<String>> map = new PrimitiveIntHashMap<>(4, 0.25f);
		int entryCount = 2000;

		for (int i = 0; i < entryCount; i++) {
			int packed = WorldPointUtil.packWorldPoint(2944 + (i % 448), 3525 + (i / 448), 0);
			Set<String> set = new HashSet<>();
			set.add("transport_" + i);
			map.put(packed, set);
		}

		int found = 0;
		for (int i = 0; i < entryCount; i++) {
			int packed = WorldPointUtil.packWorldPoint(2944 + (i % 448), 3525 + (i / 448), 0);
			Set<String> val = map.get(packed);
			if (val != null && val.contains("transport_" + i)) {
				found++;
			}
		}
		assertEquals("All entries must survive multiple rehashes", entryCount, found);
	}

	@Test
	public void testHashMapCollectionValueMerge() {
		PrimitiveIntHashMap<Set<String>> map = new PrimitiveIntHashMap<>(16);
		int packed = WorldPointUtil.packWorldPoint(3200, 3200, 0);

		Set<String> first = new HashSet<>();
		first.add("fairy_ring");
		map.put(packed, first);

		Set<String> second = new HashSet<>();
		second.add("spirit_tree");
		map.put(packed, second);

		Set<String> result = map.get(packed);
		assertNotNull(result);
		assertTrue("Should contain fairy_ring after merge", result.contains("fairy_ring"));
		assertTrue("Should contain spirit_tree after merge", result.contains("spirit_tree"));
	}

	// ========================
	// Wilderness Boundary Tests
	// ========================

	@Test
	public void testWildernessAboveGroundBoundary() {
		int insideWild = WorldPointUtil.packWorldPoint(3100, 3530, 0);
		int outsideWild = WorldPointUtil.packWorldPoint(3100, 3520, 0);
		int deepWild = WorldPointUtil.packWorldPoint(3100, 3900, 0);

		assertTrue("Point at 3100,3530 should be in wilderness",
				PathfinderConfig.isInWilderness(insideWild));
		assertFalse("Point at 3100,3520 should NOT be in wilderness",
				PathfinderConfig.isInWilderness(outsideWild));
		assertTrue("Point at 3100,3900 should be in deep wilderness",
				PathfinderConfig.isInWilderness(deepWild));
	}

	@Test
	public void testWildernessUndergroundBoundary() {
		int insideUnderground = WorldPointUtil.packWorldPoint(3100, 10000, 0);
		int outsideUnderground = WorldPointUtil.packWorldPoint(3100, 9900, 0);
		int wideUnderground = WorldPointUtil.packWorldPoint(3400, 10100, 0);

		assertTrue("Point at 3100,10000 should be in underground wilderness",
				PathfinderConfig.isInWilderness(insideUnderground));
		assertFalse("Point at 3100,9900 should NOT be in underground wilderness",
				PathfinderConfig.isInWilderness(outsideUnderground));
		assertTrue("Point at 3400,10100 should be in underground wilderness (wide area)",
				PathfinderConfig.isInWilderness(wideUnderground));
	}

	@Test
	public void testWildernessUndergroundWidthCoversUpstream() {
		int farEastUnderground = WorldPointUtil.packWorldPoint(3450, 10100, 0);
		assertTrue("Point at 3450,10100 should be in underground wilderness (upstream width=518)",
				PathfinderConfig.isInWilderness(farEastUnderground));
	}

	@Test
	public void testFeroxEnclaveNotWilderness() {
		int feroxCenter = WorldPointUtil.packWorldPoint(3130, 3630, 0);
		assertFalse("Ferox Enclave center should NOT be wilderness",
				PathfinderConfig.isInWilderness(feroxCenter));
	}

	@Test
	public void testWildernessAboveGroundStartsAtCorrectY() {
		int atY3524 = WorldPointUtil.packWorldPoint(3100, 3524, 0);
		int atY3525 = WorldPointUtil.packWorldPoint(3100, 3525, 0);

		assertFalse("Y=3524 should NOT be wilderness (boundary is Y=3525)",
				PathfinderConfig.isInWilderness(atY3524));
		assertTrue("Y=3525 should be wilderness",
				PathfinderConfig.isInWilderness(atY3525));
	}

	// ========================
	// Collision Map Tests
	// ========================

	@Test
	public void testCollisionMapLoadsRegions() {
		SplitFlagMap.RegionExtent extents = SplitFlagMap.getRegionExtents();
		assertTrue("Region width should be > 0", extents.getWidth() > 0);
		assertTrue("Region height should be > 0", extents.getHeight() > 0);
	}

	@Test
	public void testCollisionMapWalkableTiles() {
		CollisionMap map = new CollisionMap(collisionMap);
		assertTrue("Lumbridge center should allow north movement", map.n(3222, 3218, 0));
		assertTrue("Lumbridge center should allow east movement", map.e(3222, 3218, 0));
	}

	@Test
	public void testCollisionMapBlockedTile() {
		CollisionMap map = new CollisionMap(collisionMap);
		assertTrue("Lumbridge castle wall tile should be blocked", map.isBlocked(3210, 3222, 0));
		assertFalse("Open Lumbridge courtyard tile should not be blocked", map.isBlocked(3222, 3218, 0));
	}

	// ========================
	// Transport Loading Tests
	// ========================

	@Test
	public void testTransportLoadingDoesNotThrow() {
		HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
		assertNotNull("Transports should load", transports);
		assertTrue("Should load at least 100 transport origins", transports.size() > 100);
	}

	@Test
	public void testAlKharidTollGateTransportsLoaded() {
		HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();

		assertTollGateTransport(transports, AL_KHARID_GATE_WEST_SOUTH, AL_KHARID_GATE_EAST_SOUTH,
				"Pay-toll(10gp)", 10, false);
		assertTollGateTransport(transports, AL_KHARID_GATE_WEST_NORTH, AL_KHARID_GATE_EAST_NORTH,
				"Pay-toll(10gp)", 10, false);
		assertTollGateTransport(transports, AL_KHARID_GATE_EAST_SOUTH, AL_KHARID_GATE_WEST_SOUTH,
				"Pay-toll(10gp)", 10, false);
		assertTollGateTransport(transports, AL_KHARID_GATE_EAST_NORTH, AL_KHARID_GATE_WEST_NORTH,
				"Pay-toll(10gp)", 10, false);

		assertTollGateTransport(transports, AL_KHARID_GATE_WEST_SOUTH, AL_KHARID_GATE_EAST_SOUTH,
				"Open", 0, true);
		assertTollGateTransport(transports, AL_KHARID_GATE_WEST_NORTH, AL_KHARID_GATE_EAST_NORTH,
				"Open", 0, true);
		assertTollGateTransport(transports, AL_KHARID_GATE_EAST_SOUTH, AL_KHARID_GATE_WEST_SOUTH,
				"Open", 0, true);
		assertTollGateTransport(transports, AL_KHARID_GATE_EAST_NORTH, AL_KHARID_GATE_WEST_NORTH,
				"Open", 0, true);
	}

	@Test
	public void testAlKharidTollGateIsEdgeBlockedNotTileRestricted() {
		Set<Integer> gateTiles = new HashSet<>(Arrays.asList(
				WorldPointUtil.packWorldPoint(AL_KHARID_GATE_WEST_SOUTH),
				WorldPointUtil.packWorldPoint(AL_KHARID_GATE_WEST_NORTH),
				WorldPointUtil.packWorldPoint(AL_KHARID_GATE_EAST_SOUTH),
				WorldPointUtil.packWorldPoint(AL_KHARID_GATE_EAST_NORTH)));

		List<Restriction> restrictions = Restriction.loadAllFromResources();
		assertFalse("Al Kharid gate tiles must not be quest-only restrictions",
				restrictions.stream().anyMatch(r -> gateTiles.contains(r.getPackedWorldPoint())));

		PathfinderConfig config = createMinimalConfig();
		assertTrue("South Al Kharid gate edge should be blocked without transport",
				config.isBlockedTransportStep(
						WorldPointUtil.packWorldPoint(AL_KHARID_GATE_WEST_SOUTH),
						WorldPointUtil.packWorldPoint(AL_KHARID_GATE_EAST_SOUTH)));
		assertTrue("North Al Kharid gate edge should be blocked without transport",
				config.isBlockedTransportStep(
						WorldPointUtil.packWorldPoint(AL_KHARID_GATE_WEST_NORTH),
						WorldPointUtil.packWorldPoint(AL_KHARID_GATE_EAST_NORTH)));
	}

	@Test
	public void testAlKharidMinePerimeterBlocksBothDirections() {
		PathfinderConfig config = createMinimalConfig();

		for (int x = AL_KHARID_MINE_MIN_X; x <= AL_KHARID_MINE_MAX_X; x++) {
			assertBlockedBothDirections(config,
					new WorldPoint(x, AL_KHARID_MINE_MIN_Y, 0),
					new WorldPoint(x, AL_KHARID_MINE_MIN_Y - 1, 0));
			assertBlockedBothDirections(config,
					new WorldPoint(x, AL_KHARID_MINE_MAX_Y, 0),
					new WorldPoint(x, AL_KHARID_MINE_MAX_Y + 1, 0));
		}

		for (int y = AL_KHARID_MINE_MIN_Y; y <= AL_KHARID_MINE_MAX_Y; y++) {
			assertBlockedBothDirections(config,
					new WorldPoint(AL_KHARID_MINE_MIN_X, y, 0),
					new WorldPoint(AL_KHARID_MINE_MIN_X - 1, y, 0));
			assertBlockedBothDirections(config,
					new WorldPoint(AL_KHARID_MINE_MAX_X, y, 0),
					new WorldPoint(AL_KHARID_MINE_MAX_X + 1, y, 0));
		}
	}

	@Test
	public void testPathfinderRoutesAroundAlKharidMine() {
		WorldPoint start = new WorldPoint(AL_KHARID_MINE_MIN_X - 1, 3164, 0);
		WorldPoint target = new WorldPoint(AL_KHARID_MINE_MAX_X + 5, 3164, 0);
		Pathfinder pathfinder = new Pathfinder(createMinimalConfig(), start, target);

		pathfinder.run();

		List<WorldPoint> path = pathfinder.getPath();
		assertTrue("Route around Al Kharid mine should complete", pathfinder.isDone());
		assertFalse("Route around Al Kharid mine should not be empty", path.isEmpty());
		assertEquals("Route should reach the target", target, path.get(path.size() - 1));
		assertFalse("Route must not enter the open pit",
				path.stream().anyMatch(ShortestPathCoreTest::isInsideAlKharidMine));
	}

	@Test
	public void shantaySouthboundOffersBothTicketAndCoinVariants() {
		// Southbound through the Shantay Pass must be plannable BOTH when already holding a ticket
		// (item 1854) and when merely holding 5 coins (Shantay sells passes at the gate; the walker's
		// ensureShantayPassBeforeGate buys one before interacting). Without the coin variant, a player
		// without a ticket gets a several-hundred-tile detour around the desert. Also guards against
		// the duplicate origin/destination rows being deduplicated away at load.
		HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
		WorldPoint origin = new WorldPoint(3304, 3117, 0);
		Set<Transport> atGate = transports.get(origin);
		assertNotNull("no transports loaded at the Shantay gate origin", atGate);
		boolean hasTicketVariant = false;
		boolean hasCoinVariant = false;
		for (Transport t : atGate) {
			if (t.getObjectId() != 4031) continue;
			if (t.getDestination() == null || t.getDestination().getY() >= origin.getY()) continue;
			if (t.getItemIdRequirements() != null && !t.getItemIdRequirements().isEmpty()) {
				hasTicketVariant = true;
			} else if (t.getCurrencyAmount() == 5 && "Coins".equalsIgnoreCase(t.getCurrencyName())) {
				hasCoinVariant = true;
			}
		}
		assertTrue("southbound Shantay must keep the ticket-gated variant", hasTicketVariant);
		assertTrue("southbound Shantay must offer the 5-coin buy-at-gate variant", hasCoinVariant);
	}

	@Test
	public void testNewTransportTypesLoaded() {
		HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();

		boolean hasHotAirBalloon = false;
		boolean hasMagicMushtree = false;
		boolean hasSeasonalTransport = false;

		for (Set<Transport> transportSet : transports.values()) {
			for (Transport t : transportSet) {
				if (t.getType() == TransportType.HOT_AIR_BALLOON) hasHotAirBalloon = true;
				if (t.getType() == TransportType.MAGIC_MUSHTREE) hasMagicMushtree = true;
				if (t.getType() == TransportType.SEASONAL_TRANSPORT) hasSeasonalTransport = true;
			}
		}

		assertTrue("Hot air balloon transports should be loaded", hasHotAirBalloon);
		assertTrue("Magic mushtree transports should be loaded", hasMagicMushtree);
		assertTrue("Seasonal transports should be loaded", hasSeasonalTransport);
	}

	@Test
	public void testMinigameTeleportsUseCurrentLandingsAndSpecialRequirements() {
		Set<Transport> teleports = Transport.loadAllFromResources()
			.getOrDefault(null, Collections.emptySet());

		Transport guardians = findTeleport(teleports, "Guardians of the Rift");
		assertEquals("Guardians teleport should land inside the Temple of the Eye",
			new WorldPoint(3614, 9477, 0), guardians.getDestination());

		Transport keldagrimRatPits = findTeleport(teleports, "Rat Pits: Keldagrim");
		assertEquals(new WorldPoint(2914, 10193, 0), keldagrimRatPits.getDestination());
		Transport varrockRatPits = findTeleport(teleports, "Rat Pits: Varrock");
		assertEquals(new WorldPoint(3262, 3405, 0), varrockRatPits.getDestination());

		Transport pestControl = findTeleport(teleports, "Pest Control");
		assertEquals("Pest Control teleport should retain its 40 combat gate",
			40, pestControl.getRequiredCombatLevel());
	}

	@Test
	public void testTransportParserSupportsUpstreamSpecialLevelRequirements() {
		Map<String, String> fields = new HashMap<>();
		fields.put("Destination", "1 2 0");
		fields.put("Skills", "2376 Total;40 Combat;327 Quest points");
		Transport transport = new Transport(fields, TransportType.TELEPORTATION_ITEM);

		assertEquals(2376, transport.getRequiredTotalLevel());
		assertEquals(40, transport.getRequiredCombatLevel());
		assertEquals(327, transport.getRequiredQuestPoints());
	}

	@Test
	public void testDirectMaxCapeAndQuestCapeImportPreservesRequirementsAndDestinations() {
		Set<Transport> teleports = Transport.loadAllFromResources()
			.getOrDefault(null, Collections.emptySet());

		List<Transport> directMaxCape = new ArrayList<>();
		for (Transport transport : teleports) {
			if (transport.getType() == TransportType.TELEPORTATION_ITEM
				&& transport.getDisplayInfo() != null
				&& transport.getDisplayInfo().startsWith("Max cape:")
				&& !transport.getDisplayInfo().equals("Max cape: Home")) {
				directMaxCape.add(transport);
			}
		}

		assertEquals("The reviewed direct Max-cape family should contain every upstream destination",
			17, directMaxCape.size());
		Set<String> routeIdentities = new HashSet<>();
		for (Transport transport : directMaxCape) {
			assertEquals(2376, transport.getRequiredTotalLevel());
			assertEquals(20, transport.getMaxWildernessLevel());
			assertEquals(1, transport.getItemRequirements().size());
			assertEquals(Set.of(13280, 13342), transport.getItemRequirements().get(0).getItemIds());
			assertTrue("Duplicate Max-cape route: " + transport.getDisplayInfo(),
				routeIdentities.add(transport.getDestination() + "|" + transport.getDisplayInfo()));
		}

		Transport hunterGuild = findItemTeleport(teleports,
			"Max cape: Other Teleports: Hunter Guild");
		assertEquals(new WorldPoint(1558, 3046, 0), hunterGuild.getDestination());
		Transport pandemonium = findItemTeleport(teleports,
			"Max cape: Other Teleports: The Pandemonium");
		assertEquals(new WorldPoint(3048, 2972, 0), pandemonium.getDestination());

		Transport questCape = findItemTeleport(teleports, "Quest point cape: Teleport");
		assertEquals(new WorldPoint(2729, 3348, 0), questCape.getDestination());
		assertEquals(327, questCape.getRequiredQuestPoints());
		assertEquals(20, questCape.getMaxWildernessLevel());
		assertEquals(Set.of(9813, 13068), questCape.getItemRequirements().get(0).getItemIds());
	}

	@Test
	public void testQuetzalNetworkAndWhistleFamilyMatchReviewedUpstream() {
		HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
		WorldPoint aldarin = new WorldPoint(1389, 2901, 0);
		WorldPoint quetzacalli = new WorldPoint(1510, 3222, 0);
		WorldPoint oldQuetzacalli = new WorldPoint(1510, 3221, 0);
		WorldPoint camTorum = new WorldPoint(1446, 3108, 0);

		assertFalse("the obsolete one-tile-off Quetzacalli origin must be gone",
			transports.getOrDefault(oldQuetzacalli, Collections.emptySet()).stream()
				.anyMatch(transport -> transport.getType() == TransportType.QUETZAL));
		Transport aldarinToCamTorum = transports.getOrDefault(aldarin, Collections.emptySet()).stream()
			.filter(transport -> transport.getType() == TransportType.QUETZAL)
			.filter(transport -> camTorum.equals(transport.getDestination()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing Aldarin -> Cam Torum quetzal route"));
		assertEquals("Travel", aldarinToCamTorum.getAction());
		assertEquals("Renu", aldarinToCamTorum.getName());
		assertEquals(13350, aldarinToCamTorum.getObjectId());
		assertEquals("Cam Torum", aldarinToCamTorum.getDisplayInfo());
		assertTrue(aldarinToCamTorum.getVarplayers().stream().anyMatch(requirement ->
			requirement.getVarplayerId() == 4182
				&& requirement.getOperator() == TransportVarPlayer.Operator.BIT_SET
				&& requirement.getValue() == 32));
		assertTrue("the corrected Quetzacalli origin must participate in the network",
			transports.getOrDefault(quetzacalli, Collections.emptySet()).stream()
				.anyMatch(transport -> transport.getType() == TransportType.QUETZAL));

		List<Transport> whistles = transports.getOrDefault(null, Collections.emptySet()).stream()
			.filter(transport -> transport.getType() == TransportType.TELEPORTATION_ITEM)
			.filter(transport -> transport.getDisplayInfo() != null
				&& transport.getDisplayInfo().startsWith("Quetzal whistle:"))
			.collect(java.util.stream.Collectors.toList());
		assertEquals("every whistle destination needs charged and permanent variants", 28, whistles.size());
		Set<String> whistleVariants = new HashSet<>();
		for (Transport whistle : whistles) {
			Set<Integer> itemIds = whistle.getItemRequirements().get(0).getItemIds();
			if (whistle.isConsumable()) {
				assertEquals(Set.of(29271, 29273, 29275), itemIds);
			} else {
				assertEquals(Set.of(33120), itemIds);
			}
			assertEquals(QuestState.FINISHED, whistle.getQuests().get(Quest.TWILIGHTS_PROMISE));
			assertEquals(20, whistle.getMaxWildernessLevel());
			assertTrue("duplicate whistle policy variant: " + whistle.getDisplayInfo(),
				whistleVariants.add(whistle.getDisplayInfo() + "|" + whistle.isConsumable()));
			assertFalse("obsolete executor label must not survive",
				whistle.getDisplayInfo().contains("Cam Torum Entrance"));
		}
		assertEquals("each destination must have one charged and one permanent variant",
			28, whistleVariants.size());
		Transport quetzacalliWhistle = whistles.stream()
			.filter(transport -> "Quetzal whistle: Quetzacalli Gorge".equals(transport.getDisplayInfo()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing Quetzacalli whistle destination"));
		assertEquals(quetzacalli, quetzacalliWhistle.getDestination());
	}

	@Test
	public void testBothCanoeChainsUsePinnedAxeCollectionAndUpstreamCosts() {
		HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
		Set<WorldPoint> riverLumOrigins = Set.of(
			new WorldPoint(3132, 3510, 0),
			new WorldPoint(3112, 3411, 0),
			new WorldPoint(3202, 3343, 0),
			new WorldPoint(3243, 3237, 0),
			new WorldPoint(3154, 3630, 0));
		Set<WorldPoint> riverDougneOrigins = Set.of(
			new WorldPoint(2439, 3135, 0),
			new WorldPoint(2485, 3192, 0),
			new WorldPoint(2579, 3260, 0),
			new WorldPoint(2573, 3358, 0),
			new WorldPoint(2525, 3408, 0));
		Set<WorldPoint> supportedOrigins = new HashSet<>(riverLumOrigins);
		supportedOrigins.addAll(riverDougneOrigins);
		List<Transport> canoes = supportedOrigins.stream()
			.flatMap(origin -> transports.getOrDefault(origin, Collections.emptySet()).stream())
			.filter(transport -> transport.getType() == TransportType.CANOE)
			.collect(java.util.stream.Collectors.toList());

		assertEquals("both supported canoe chains must retain all reviewed upstream routes", 45, canoes.size());
		for (Transport canoe : canoes) {
			assertEquals("Paddle Canoe", canoe.getAction());
			assertEquals("Canoe Station", canoe.getName());
			assertTrue(canoe.getDuration() == 20 || canoe.getDuration() == 30);
			assertEquals(1, canoe.getItemRequirements().size());
			Set<Integer> axes = canoe.getItemRequirements().get(0).getItemIds();
			assertEquals(12, axes.size());
			assertTrue(axes.contains(net.runelite.api.gameval.ItemID.BRONZE_AXE));
			assertTrue(axes.contains(net.runelite.api.gameval.ItemID.CRYSTAL_AXE));
		}
		List<Transport> dougneCanoes = canoes.stream()
			.filter(transport -> transport.getObjectId() >= 60845 && transport.getObjectId() <= 60849)
			.collect(java.util.stream.Collectors.toList());
		assertEquals("River Dougne has four destinations from each of five stations", 20, dougneCanoes.size());
		assertEquals(Set.of(60845, 60846, 60847, 60848, 60849), dougneCanoes.stream()
			.map(Transport::getObjectId)
			.collect(java.util.stream.Collectors.toSet()));
	}

	@Test
	public void testGrappleShortcutsRequireCrossbowAndMithGrapple() {
		Set<Integer> reviewedGrappleObjects = Set.of(17042, 17047, 17049, 17050, 17062, 17068, 17074);
		List<Transport> grappleShortcuts = Transport.loadAllFromResources().values().stream()
			.flatMap(Collection::stream)
			.filter(transport -> transport.getType() == TransportType.GRAPPLE_SHORTCUT)
			.filter(transport -> reviewedGrappleObjects.contains(transport.getObjectId()))
			.collect(java.util.stream.Collectors.toList());

		assertEquals("every reviewed grapple edge must retain the upstream equipment pair",
			12, grappleShortcuts.size());
		for (Transport grapple : grappleShortcuts) {
			assertEquals("crossbow and grapple are independent AND requirements: " + grapple,
				2, grapple.getItemRequirements().size());
			assertTrue("a usable crossbow family is required: " + grapple,
				grapple.getItemRequirements().stream().anyMatch(requirement ->
					requirement.getItemIds().contains(net.runelite.api.gameval.ItemID.CROSSBOW)
						&& requirement.getItemIds().contains(net.runelite.api.gameval.ItemID.ZARYTE_XBOW)));
			assertTrue("the mith grapple is required separately: " + grapple,
				grapple.getItemRequirements().stream().anyMatch(requirement ->
					requirement.getItemIds().equals(Set.of(
						net.runelite.api.gameval.ItemID.XBOWS_GRAPPLE_TIP_BOLT_MITHRIL_ROPE))));
		}
	}

	@Test
	public void testTrollheimRopeShortcutRetainsItemAndUnlockVarbit() {
		WorldPoint origin = new WorldPoint(2766, 3665, 0);
		Transport rope = Transport.loadAllFromResources().getOrDefault(origin, Collections.emptySet()).stream()
			.filter(transport -> new WorldPoint(2766, 3663, 0).equals(transport.getDestination()))
			.filter(transport -> transport.getObjectId() == 5842)
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing Trollheim rope shortcut"));

		assertEquals(1, rope.getItemRequirements().size());
		assertEquals(Set.of(net.runelite.api.gameval.ItemID.ROPE),
			rope.getItemRequirements().get(0).getItemIds());
		assertTrue("shortcut is available only after the rope has been attached",
			rope.getVarbits().stream().anyMatch(requirement -> requirement.getVarbitId() == 260
				&& requirement.getOperator() == TransportVarbit.Operator.GREATER_THAN
				&& requirement.getValue() == 0));
		assertEquals(10, rope.getDuration());
	}

	@Test
	public void testTrollheimClimbingRockAscentsRequireBootsButDescentsDoNot() {
		HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
		Map<WorldPoint, WorldPoint> ascents = Map.of(
			new WorldPoint(2820, 3635, 0), new WorldPoint(2822, 3635, 0),
			new WorldPoint(2856, 3611, 0), new WorldPoint(2856, 3613, 0),
			new WorldPoint(2857, 3611, 0), new WorldPoint(2857, 3613, 0));

		for (Map.Entry<WorldPoint, WorldPoint> edge : ascents.entrySet()) {
			Transport ascent = transports.getOrDefault(edge.getKey(), Collections.emptySet()).stream()
				.filter(transport -> edge.getValue().equals(transport.getDestination()))
				.filter(transport -> transport.getObjectId() == 3748)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing Trollheim ascent: " + edge));
			assertEquals(TransportType.AGILITY_SHORTCUT, ascent.getType());
			assertEquals(1, ascent.getItemRequirements().size());
			assertEquals(Set.of(
					net.runelite.api.gameval.ItemID.DEATH_CLIMBINGBOOTS,
					net.runelite.api.gameval.ItemID.CLIMBING_BOOTS_G),
				ascent.getItemRequirements().get(0).getItemIds());

			Transport descent = transports.getOrDefault(edge.getValue(), Collections.emptySet()).stream()
				.filter(transport -> edge.getKey().equals(transport.getDestination()))
				.filter(transport -> transport.getObjectId() == 3748)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing unrestricted Trollheim descent: " + edge));
			assertEquals(TransportType.TRANSPORT, descent.getType());
			assertTrue(descent.getItemRequirements().isEmpty());
		}
	}

	@Test
	public void testIsafdarForestObstaclesRetainAgilityAndDurationRequirements() {
		Set<Integer> forestObjectIds = Set.of(
			3921, 3922, 3925, 3931, 3932, 3933, 3937, 3938, 3939, 3998, 3999);
		Map<Integer, Integer> requiredAgility = Map.ofEntries(
			Map.entry(3921, 1),
			Map.entry(3922, 1),
			Map.entry(3925, 1),
			Map.entry(3931, 45),
			Map.entry(3932, 45),
			Map.entry(3933, 45),
			Map.entry(3937, 56),
			Map.entry(3938, 56),
			Map.entry(3939, 56),
			Map.entry(3998, 56),
			Map.entry(3999, 56));
		Map<Integer, Integer> expectedDuration = Map.ofEntries(
			Map.entry(3921, 8),
			Map.entry(3922, 6),
			Map.entry(3925, 4),
			Map.entry(3931, 8),
			Map.entry(3932, 8),
			Map.entry(3933, 9),
			Map.entry(3937, 4),
			Map.entry(3938, 4),
			Map.entry(3939, 4),
			Map.entry(3998, 4),
			Map.entry(3999, 4));

		HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
		List<Transport> forestShortcuts = transports.values().stream()
			.flatMap(Collection::stream)
			.filter(transport -> forestObjectIds.contains(transport.getObjectId()))
			.filter(transport -> transport.getOrigin() != null
				&& transport.getOrigin().getX() >= 2100 && transport.getOrigin().getX() <= 2310
				&& transport.getOrigin().getY() >= 3100 && transport.getOrigin().getY() <= 3300)
			.collect(java.util.stream.Collectors.toList());

		assertEquals("the complete reviewed Isafdar obstacle family must be loaded", 88,
			forestShortcuts.size());
		for (Transport shortcut : forestShortcuts) {
			assertEquals("forest obstacles must not bypass the agility toggle or level gate: " + shortcut,
				TransportType.AGILITY_SHORTCUT, shortcut.getType());
			assertEquals("wrong Agility requirement for object " + shortcut.getObjectId(),
				requiredAgility.get(shortcut.getObjectId()).intValue(),
				shortcut.getSkillLevels()[net.runelite.api.Skill.AGILITY.ordinal()]);
			assertEquals("wrong traversal duration for object " + shortcut.getObjectId(),
				expectedDuration.get(shortcut.getObjectId()).intValue(), shortcut.getDuration());
		}

		assertTrue("current stick landing must replace the stale 2295,3215 origin",
			transports.getOrDefault(new WorldPoint(2295, 3213, 0), Collections.emptySet()).stream()
				.anyMatch(transport -> transport.getObjectId() == 3922
					&& new WorldPoint(2295, 3217, 0).equals(transport.getDestination())));
		assertFalse("stale stick landing must not remain as a generic transport",
			transports.getOrDefault(new WorldPoint(2295, 3215, 0), Collections.emptySet()).stream()
				.anyMatch(transport -> transport.getObjectId() == 3922));
		assertTrue("current dense-forest landing must replace the stale 2279,3221 origin",
			transports.getOrDefault(new WorldPoint(2279, 3222, 0), Collections.emptySet()).stream()
				.anyMatch(transport -> transport.getObjectId() == 3938
					&& new WorldPoint(2279, 3225, 0).equals(transport.getDestination())));
		assertFalse("stale dense-forest landing must not remain as a generic transport",
			transports.getOrDefault(new WorldPoint(2279, 3221, 0), Collections.emptySet()).stream()
				.anyMatch(transport -> transport.getObjectId() == 3938));
	}

	@Test
	public void testConvertedGenericShortcutFamiliesRetainUpstreamRequirements() {
		Set<Integer> reviewedObjects = Set.of(
			21727, 21738, 21739, 20882, 20884, // Brimhaven Dungeon
			6905,                               // Lumbridge cellar
			2231,                               // Karamja rocks
			16537, 16538,                       // Slayer Tower ground floor
			39541, 39542);                      // Darkmeyer walls
		Map<Integer, Integer> expectedAgility = Map.ofEntries(
			Map.entry(21727, 1),
			Map.entry(21738, 1),
			Map.entry(21739, 1),
			Map.entry(20882, 1),
			Map.entry(20884, 1),
			Map.entry(6905, 13),
			Map.entry(2231, 15),
			Map.entry(16537, 61),
			Map.entry(16538, 61),
			Map.entry(39541, 63),
			Map.entry(39542, 63));
		Map<Integer, Integer> expectedDuration = Map.ofEntries(
			Map.entry(21727, 13),
			Map.entry(21738, 7),
			Map.entry(21739, 7),
			Map.entry(20882, 7),
			Map.entry(20884, 7),
			Map.entry(6905, 3),
			Map.entry(2231, 5),
			Map.entry(16537, 0),
			Map.entry(16538, 0),
			Map.entry(39541, 0),
			Map.entry(39542, 0));

		HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
		java.util.function.Predicate<Transport> reviewedFamily = transport -> {
			WorldPoint origin = transport.getOrigin();
			if (origin == null || !reviewedObjects.contains(transport.getObjectId())) {
				return false;
			}
			int objectId = transport.getObjectId();
			if (objectId == 16537 || objectId == 16538) {
				return origin.getX() >= 3421 && origin.getX() <= 3423
					&& origin.getY() >= 3549 && origin.getY() <= 3551;
			}
			return true;
		};
		List<Transport> shortcuts = transports.values().stream()
			.flatMap(Collection::stream)
			.filter(reviewedFamily)
			.filter(transport -> transport.getType() == TransportType.AGILITY_SHORTCUT)
			.collect(java.util.stream.Collectors.toList());

		assertEquals("all 28 reviewed generic edges must become agility shortcuts", 28, shortcuts.size());
		for (Transport shortcut : shortcuts) {
			assertEquals("wrong Agility level for " + shortcut,
				expectedAgility.get(shortcut.getObjectId()).intValue(),
				shortcut.getSkillLevels()[net.runelite.api.Skill.AGILITY.ordinal()]);
			assertEquals("wrong traversal duration for " + shortcut,
				expectedDuration.get(shortcut.getObjectId()).intValue(), shortcut.getDuration());
		}

		List<Transport> cellar = shortcuts.stream()
			.filter(transport -> transport.getObjectId() == 6905)
			.collect(java.util.stream.Collectors.toList());
		assertEquals(2, cellar.size());
		assertTrue("Lumbridge cellar hole must use the quest-progress varbit, not a completion-only wall",
			cellar.stream().allMatch(transport -> transport.getVarbits().stream().anyMatch(requirement ->
				requirement.getVarbitId() == 532
					&& requirement.getOperator() == TransportVarbit.Operator.GREATER_THAN
					&& requirement.getValue() == 3)));
		assertFalse("obsolete Lost Tribe wall objects must not survive the representation change",
			transports.values().stream().flatMap(Collection::stream)
				.anyMatch(transport -> transport.getObjectId() == 6898 || transport.getObjectId() == 6899));

		assertTrue("west Darkmeyer wall must retain its unlock varbit",
			shortcuts.stream().filter(transport -> transport.getObjectId() == 39542)
				.allMatch(transport -> transport.getVarbits().stream().anyMatch(requirement ->
					requirement.getVarbitId() == 10449
						&& requirement.getOperator() == TransportVarbit.Operator.EQUAL
						&& requirement.getValue() == 1)));
		assertTrue("east Darkmeyer wall must retain its unlock varbit",
			shortcuts.stream().filter(transport -> transport.getObjectId() == 39541)
				.allMatch(transport -> transport.getVarbits().stream().anyMatch(requirement ->
					requirement.getVarbitId() == 10450
						&& requirement.getOperator() == TransportVarbit.Operator.EQUAL
						&& requirement.getValue() == 1)));

		Set<String> genericReviewedEdges = transports.values().stream()
			.flatMap(Collection::stream)
			.filter(reviewedFamily)
			.filter(transport -> transport.getType() == TransportType.TRANSPORT)
			.map(transport -> transport.getOrigin() + " -> " + transport.getDestination())
			.collect(java.util.stream.Collectors.toSet());
		assertEquals("only upstream's two intentional Darkmeyer diagonal generic approaches may remain",
			Set.of(
				new WorldPoint(3672, 3376, 0) + " -> " + new WorldPoint(3670, 3375, 0),
				new WorldPoint(3672, 3374, 0) + " -> " + new WorldPoint(3670, 3375, 0)),
			genericReviewedEdges);
	}

	private static Transport findTeleport(Set<Transport> teleports, String displayInfo) {
		return teleports.stream()
			.filter(transport -> transport.getType() == TransportType.TELEPORTATION_MINIGAME)
			.filter(transport -> displayInfo.equals(transport.getDisplayInfo()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing minigame teleport: " + displayInfo));
	}

	private static Transport findItemTeleport(Set<Transport> teleports, String displayInfo) {
		return teleports.stream()
			.filter(transport -> transport.getType() == TransportType.TELEPORTATION_ITEM)
			.filter(transport -> displayInfo.equals(transport.getDisplayInfo()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Missing item teleport: " + displayInfo));
	}

	@Test
	public void testLovakengjMinecartsRespectForsakenTowerUnlock() {
		HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
		WorldPoint arceuusOrigin = new WorldPoint(1670, 3832, 0);
		WorldPoint farmingGuildDestination = new WorldPoint(1218, 3737, 0);
		Set<Transport> atArceuus = transports.getOrDefault(arceuusOrigin, Collections.emptySet());

		boolean paidBeforeUnlock = false;
		boolean freeAfterUnlock = false;
		boolean ungatedVariant = false;
		for (Transport transport : atArceuus) {
			if (transport.getType() != TransportType.MINECART
				|| !farmingGuildDestination.equals(transport.getDestination())) {
				continue;
			}
			boolean beforeUnlock = transport.getVarbits().stream().anyMatch(v -> v.getVarbitId() == 7796
				&& v.getOperator() == TransportVarbit.Operator.LESS_THAN && v.getValue() == 11);
			boolean afterUnlock = transport.getVarbits().stream().anyMatch(v -> v.getVarbitId() == 7796
				&& v.getOperator() == TransportVarbit.Operator.EQUAL && v.getValue() == 11);
			paidBeforeUnlock |= beforeUnlock && !afterUnlock
				&& transport.getCurrencyAmount() == 20 && "Coins".equals(transport.getCurrencyName());
			freeAfterUnlock |= afterUnlock && !beforeUnlock && transport.getCurrencyAmount() == 0;
			ungatedVariant |= !beforeUnlock && !afterUnlock;
		}

		assertTrue("Arceuus minecart should cost 20 coins before The Forsaken Tower unlock", paidBeforeUnlock);
		assertTrue("Arceuus minecart should be free after The Forsaken Tower unlock", freeAfterUnlock);
		assertFalse("Lovakengj minecart routes must not have an ungated fare variant", ungatedVariant);
	}

	@Test
	public void testAllSpellbookHomeTeleportTransportsLoaded() {
		assertHomeTeleport("Lumbridge Home Teleport", new WorldPoint(3221, 3218, 0), 0, null, false);
		assertHomeTeleport("Edgeville Home Teleport", new WorldPoint(3087, 3504, 0), 1,
			Quest.DESERT_TREASURE_I, true);
		assertHomeTeleport("Lunar Home Teleport", new WorldPoint(2113, 3915, 0), 2,
			Quest.LUNAR_DIPLOMACY, true);
		assertHomeTeleport("Arceuus Home Teleport", new WorldPoint(1700, 3882, 0), 3, null, true);
	}

	@Test
	public void testLumbridgeHomeTeleportCooldownRejectsRecentUse() {
		Transport transport = getLumbridgeHomeTeleportTransport();
		TransportVarPlayer cooldown = transport.getVarplayers().stream()
			.filter(v -> v.getVarplayerId() == VarPlayer.LAST_HOME_TELEPORT)
			.findFirst()
			.orElseThrow(() -> new AssertionError("Lumbridge Home Teleport should have a LAST_HOME_TELEPORT varplayer"));

		int nowMinutes = (int) (System.currentTimeMillis() / 60000);
		assertFalse("Home teleport should be unavailable shortly after use",
			cooldown.matches(nowMinutes - 5));
		assertFalse("Home teleport should stay unavailable until more than 30 minutes have elapsed",
			cooldown.matches(nowMinutes - 30));
		assertTrue("Home teleport should be available after the 30 minute cooldown has elapsed",
			cooldown.matches(nowMinutes - 31));
	}

	private static Transport getLumbridgeHomeTeleportTransport() {
		return getHomeTeleportTransport("Lumbridge Home Teleport", new WorldPoint(3221, 3218, 0));
	}

	private static void assertHomeTeleport(String displayInfo, WorldPoint destination, int spellbook,
			Quest requiredQuest, boolean members) {
		Transport transport = getHomeTeleportTransport(displayInfo, destination);

		assertEquals(displayInfo + " should have exactly one spellbook requirement",
			1, transport.getVarbits().size());
		assertTrue(displayInfo + " should require spellbook " + spellbook,
			transport.getVarbits().stream().anyMatch(v -> v.getVarbitId() == 4070 && v.getValue() == spellbook));
		assertTrue(displayInfo + " should be gated by LAST_HOME_TELEPORT cooldown",
			transport.getVarplayers().stream().anyMatch(v -> v.getVarplayerId() == VarPlayer.LAST_HOME_TELEPORT
				&& v.getOperator() == TransportVarPlayer.Operator.COOLDOWN_MINUTES
				&& v.getValue() == 30));
		assertEquals(displayInfo + " membership requirement", members, transport.isMembers());
		if (requiredQuest == null) {
			assertTrue(displayInfo + " should not have a quest requirement", transport.getQuests().isEmpty());
		} else {
			assertEquals(displayInfo + " quest requirement", QuestState.FINISHED,
				transport.getQuests().get(requiredQuest));
		}
	}

	private static Transport getHomeTeleportTransport(String displayInfo, WorldPoint destination) {
		HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();

		Optional<Transport> homeTeleport = transports.values().stream()
			.flatMap(Set::stream)
			.filter(t -> t.getType() == TransportType.TELEPORTATION_SPELL
				&& displayInfo.equals(t.getDisplayInfo())
				&& destination.equals(t.getDestination()))
			.findFirst();

		assertTrue(displayInfo + " should be loaded", homeTeleport.isPresent());
		return homeTeleport.get();
	}

	private static void assertTollGateTransport(HashMap<WorldPoint, Set<Transport>> transports,
			WorldPoint origin, WorldPoint destination, String action, int currencyAmount, boolean princeAliRequired) {
		Optional<Transport> match = transports.getOrDefault(origin, Collections.emptySet()).stream()
				.filter(t -> destination.equals(t.getDestination()))
				.filter(t -> action.equals(t.getAction()))
				.filter(t -> "Gate".equals(t.getName()))
				.findFirst();

		assertTrue("Missing Al Kharid toll gate transport " + action + " from " + origin + " to " + destination,
				match.isPresent());
		Transport transport = match.get();
		assertEquals("Gate transport should use normal TRANSPORT type",
				TransportType.TRANSPORT, transport.getType());
		assertEquals("Unexpected gate currency amount", currencyAmount, transport.getCurrencyAmount());
		if (currencyAmount > 0) {
			assertEquals("Unexpected gate currency name", "Coins", transport.getCurrencyName());
		}
		assertEquals("Unexpected Prince Ali Rescue requirement on gate transport",
				princeAliRequired, transport.getQuests().containsKey(Quest.PRINCE_ALI_RESCUE));
	}

	@Test
	public void testFairyRingTransportsExist() {
		HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
		boolean hasFairyRing = false;
		for (Set<Transport> transportSet : transports.values()) {
			for (Transport t : transportSet) {
				if (t.getType() == TransportType.FAIRY_RING) {
					hasFairyRing = true;
					break;
				}
			}
			if (hasFairyRing) break;
		}
		assertTrue("Fairy ring transports should be loaded", hasFairyRing);
	}

	// ========================
	// WorldPointUtil Tests
	// ========================

	@Test
	public void testPackUnpackRoundTrip() {
		int x = 3222, y = 3218, z = 0;
		int packed = WorldPointUtil.packWorldPoint(x, y, z);
		assertEquals(x, WorldPointUtil.unpackWorldX(packed));
		assertEquals(y, WorldPointUtil.unpackWorldY(packed));
		assertEquals(z, WorldPointUtil.unpackWorldPlane(packed));
	}

	@Test
	public void testPackUnpackHighCoords() {
		int x = 3462, y = 10376, z = 2;
		int packed = WorldPointUtil.packWorldPoint(x, y, z);
		assertEquals(x, WorldPointUtil.unpackWorldX(packed));
		assertEquals(y, WorldPointUtil.unpackWorldY(packed));
		assertEquals(z, WorldPointUtil.unpackWorldPlane(packed));
	}

	@Test
	public void testDistanceBetween() {
		int a = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		int b = WorldPointUtil.packWorldPoint(3210, 3200, 0);
		assertEquals(10, WorldPointUtil.distanceBetween(a, b));
	}

	@Test
	public void testDistanceToArea() {
		WorldArea area = new WorldArea(3200, 3200, 10, 10, 0);
		int inside = WorldPointUtil.packWorldPoint(3205, 3205, 0);
		int outside = WorldPointUtil.packWorldPoint(3220, 3205, 0);

		assertEquals("Inside point should have distance 0", 0, WorldPointUtil.distanceToArea2D(inside, area));
		assertTrue("Outside point should have distance > 0", WorldPointUtil.distanceToArea2D(outside, area) > 0);
	}

	// ========================
	// Pathfinder Partial Path Tests
	// ========================

	@Test
	public void testPathfinderGetPathReturnsEmptyWhenNoPath() {
		Pathfinder pf = new Pathfinder(
				createMinimalConfig(),
				new WorldPoint(3222, 3218, 0),
				new WorldPoint(3232, 3218, 0)
		);
		List<WorldPoint> path = pf.getPath();
		assertNotNull("getPath() should return empty list before run, not throw", path);
		assertTrue("Path should be empty before pathfinder runs", path.isEmpty());
	}

	@Test
	public void testPathfinderRunsAndProducesPath() throws Exception {
		PathfinderConfig config = createMinimalConfig();
		assertNotNull("Config map should be available", config.getMap());

		Pathfinder pf = new Pathfinder(
				config,
				new WorldPoint(3222, 3218, 0),
				new WorldPoint(3232, 3218, 0)
		);

		pf.run();

		assertTrue("Pathfinder should complete", pf.isDone());
		List<WorldPoint> path = pf.getPath();
		assertNotNull("Path should not be null", path);
		assertFalse("Path should not be empty for a short walkable route", path.isEmpty());
	}

	@Test
	public void testPathfinderLongRoute() {
		Pathfinder pf = new Pathfinder(
				createMinimalConfig(),
				new WorldPoint(3222, 3218, 0),
				new WorldPoint(3164, 3485, 0)
		);

		pf.run();

		assertTrue("Pathfinder should complete for Lumbridge to GE route", pf.isDone());
		List<WorldPoint> path = pf.getPath();
		assertNotNull(path);
		assertTrue("Path should have many tiles for a long route", path.size() > 50);
	}

	@Test
	public void testPathfinderCancelReturnsPath() throws Exception {
		Pathfinder pf = new Pathfinder(
				createMinimalConfig(),
				new WorldPoint(3222, 3218, 0),
				new WorldPoint(2500, 3500, 0)
		);

		Thread t = new Thread(pf);
		t.start();
		Thread.sleep(200);
		pf.cancel();
		t.join(5000);

		List<WorldPoint> path = pf.getPath();
		assertNotNull("Cancelled pathfinder should return a path (possibly partial)", path);
	}

	@Test
	public void testPathfinderWildernessRoute() {
		Pathfinder pf = new Pathfinder(
				createMinimalConfig(),
				new WorldPoint(3094, 3500, 0),
				new WorldPoint(3094, 3550, 0)
		);

		pf.run();

		assertTrue("Pathfinder should complete", pf.isDone());
		List<WorldPoint> path = pf.getPath();
		assertNotNull(path);
		assertFalse("Path into wilderness should not be empty", path.isEmpty());
	}

	// ========================
	// Isle of Souls Dungeon Route Tests
	// ========================

	@Test
	public void testKaramjaToIsleOfSoulsDungeonEntrance() {
		PathfinderConfig config = createConfigWithTransports();
		WorldPoint karamja = new WorldPoint(2852, 3078, 0);
		WorldPoint dungeonEntrance = new WorldPoint(2167, 9308, 0);

		Pathfinder pf = new Pathfinder(config, karamja, dungeonEntrance);
		pf.run();

		assertTrue("Pathfinder should complete", pf.isDone());
		List<WorldPoint> path = pf.getPath();
		assertNotNull(path);
		assertFalse("Path should not be empty", path.isEmpty());

		WorldPoint endpoint = path.get(path.size() - 1);
		int distToTarget = Math.max(
				Math.abs(endpoint.getX() - dungeonEntrance.getX()),
				Math.abs(endpoint.getY() - dungeonEntrance.getY()));
		assertTrue("Should reach within 5 tiles of dungeon entrance, got dist=" + distToTarget +
				" at " + endpoint, distToTarget <= 5);
	}

	@Test
	public void testKaramjaToIronDragons() {
		PathfinderConfig config = createConfigWithTransports();
		WorldPoint karamja = new WorldPoint(2852, 3078, 0);
		WorldPoint ironDragons = new WorldPoint(2154, 9294, 0);

		Pathfinder pf = new Pathfinder(config, karamja, ironDragons);
		pf.run();

		assertTrue("Pathfinder should complete", pf.isDone());
		List<WorldPoint> path = pf.getPath();
		assertFalse("Path should not be empty", path.isEmpty());

		WorldPoint endpoint = path.get(path.size() - 1);
		int distToTarget = Math.max(
				Math.abs(endpoint.getX() - ironDragons.getX()),
				Math.abs(endpoint.getY() - ironDragons.getY()));
		assertTrue("Should reach within 15 tiles of iron dragons, got dist=" + distToTarget +
				" at " + endpoint, distToTarget <= 15);
	}

	@Test
	public void testKaramjaToBlueDragons() {
		PathfinderConfig config = createConfigWithTransports();
		WorldPoint karamja = new WorldPoint(2852, 3078, 0);
		WorldPoint blueDragons = new WorldPoint(2126, 9303, 0);

		Pathfinder pf = new Pathfinder(config, karamja, blueDragons);
		pf.run();

		assertTrue("Pathfinder should complete", pf.isDone());
		List<WorldPoint> path = pf.getPath();
		assertFalse("Path should not be empty", path.isEmpty());

		WorldPoint endpoint = path.get(path.size() - 1);
		int distToTarget = Math.max(
				Math.abs(endpoint.getX() - blueDragons.getX()),
				Math.abs(endpoint.getY() - blueDragons.getY()));
		assertTrue("Should reach within 30 tiles of blue dragons, got dist=" + distToTarget +
				" at " + endpoint, distToTarget <= 30);
	}

	// ========================
	// Pathfinder Performance Tests
	// ========================

	@Test
	public void testShortPathDoesNotFloodEntireMap() {
		PathfinderConfig config = createMinimalConfig();
		WorldPoint src = new WorldPoint(3222, 3218, 0);
		WorldPoint dst = new WorldPoint(3232, 3228, 0);

		Pathfinder pf = new Pathfinder(config, src, dst);
		pf.run();

		assertTrue("Pathfinder should complete", pf.isDone());
		Pathfinder.PathfinderStats stats = pf.getStats();
		assertNotNull(stats);

		assertTrue("Short path (~15 tiles) should check fewer than 50,000 nodes, got " + stats.getTotalNodesChecked(),
				stats.getTotalNodesChecked() < 50_000);
	}

	@Test
	public void testUnreachableTargetCompletesViaCutoff() {
		PathfinderConfig config = createMinimalConfig();
		CollisionMap map = config.getMap();

		int startX = 3222, targetX = startX, targetY = 3218;
		while (!map.isBlocked(targetX, targetY, 0)) {
			targetX++;
			if (targetX > 3300) {
				fail("No blocked tile found scanning x=" + startX + ".." + targetX + " y=" + targetY + " plane=0");
			}
		}

		Pathfinder pf = new Pathfinder(config,
				new WorldPoint(3222, 3218, 0),
				new WorldPoint(targetX, targetY, 0));
		pf.run();

		assertTrue("Pathfinder should complete even for blocked target", pf.isDone());
		List<WorldPoint> path = pf.getPath();
		assertNotNull("Path should not be null", path);
		assertFalse("Should produce a partial path toward the blocked target", path.isEmpty());
	}

	@Test
	public void testShortPathCompletesUnder500ms() {
		PathfinderConfig config = createMinimalConfig();
		Pathfinder pf = new Pathfinder(config,
				new WorldPoint(3222, 3218, 0),
				new WorldPoint(3260, 3230, 0));

		pf.run();

		assertTrue("Pathfinder should complete", pf.isDone());
		Pathfinder.PathfinderStats stats = pf.getStats();
		assertNotNull(stats);

		long elapsedMs = stats.getElapsedTimeNanos() / 1_000_000;
		assertTrue("Short path should complete under 500ms, took " + elapsedMs + "ms", elapsedMs < 500);
	}

	@Test
	public void testLongPathCompletesUnder3Seconds() {
		PathfinderConfig config = createMinimalConfig();
		Pathfinder pf = new Pathfinder(config,
				new WorldPoint(3222, 3218, 0),
				new WorldPoint(3164, 3485, 0));

		pf.run();

		assertTrue("Pathfinder should complete", pf.isDone());
		Pathfinder.PathfinderStats stats = pf.getStats();
		assertNotNull(stats);

		long elapsedMs = stats.getElapsedTimeNanos() / 1_000_000;
		assertTrue("Long path (Lumbridge to GE) should complete under 3s, took " + elapsedMs + "ms",
				elapsedMs < 3000);
	}

	@Test
	public void testNearbyBlockedTargetResolvesFast() {
		PathfinderConfig config = createMinimalConfig();
		Pathfinder pf = new Pathfinder(config,
				new WorldPoint(1369, 3368, 0),
				new WorldPoint(1415, 3355, 0));

		pf.run();

		assertTrue("Pathfinder should complete", pf.isDone());
		Pathfinder.PathfinderStats stats = pf.getStats();
		assertNotNull(stats);

		long elapsedMs = stats.getElapsedTimeNanos() / 1_000_000;
		assertTrue("Nearby path (~46 tiles) should complete under 500ms, took " + elapsedMs + "ms",
				elapsedMs < 500);
		assertTrue("Nearby path should check under 150,000 nodes, got " + stats.getTotalNodesChecked(),
				stats.getTotalNodesChecked() < 150_000);
	}

	@Test
	public void testIsleOfSoulsDungeonEntranceIsWalkable() {
		CollisionMap map = new CollisionMap(collisionMap);
		assertFalse("IoS dungeon entrance (2167,9308) should be walkable",
				map.isBlocked(2167, 9308, 0));
	}

	@Test
	public void testDungeonPathToKnownReachableTile() {
		PathfinderConfig config = createConfigWithTransports();
		WorldPoint src = new WorldPoint(2167, 9308, 0);
		WorldPoint dst = new WorldPoint(2165, 9294, 0);

		Pathfinder pf = new Pathfinder(config, src, dst);
		pf.run();

		assertTrue("Pathfinder should complete", pf.isDone());
		List<WorldPoint> path = pf.getPath();
		assertFalse("Path to known reachable dungeon tile should not be empty", path.isEmpty());

		WorldPoint endpoint = path.get(path.size() - 1);
		int distToTarget = Math.max(
				Math.abs(endpoint.getX() - dst.getX()),
				Math.abs(endpoint.getY() - dst.getY()));
		assertTrue("Should reach within 2 tiles of reachable dungeon target, got dist=" + distToTarget,
				distToTarget <= 2);
	}

	@Test
	public void testVarrockSewerPathAvoidsDisabledPalaceTrellisShortcut() {
		PathfinderConfig config = createConfigWithUnavailableShortcutEdges(TransportType.AGILITY_SHORTCUT);
		WorldPoint src = new WorldPoint(3203, 3501, 0);
		WorldPoint dst = new WorldPoint(3237, 9858, 0);
		WorldPoint northTrellis = new WorldPoint(3228, 3471, 0);
		WorldPoint southTrellis = new WorldPoint(3228, 3470, 0);

		Pathfinder pf = new Pathfinder(config, src, dst);
		pf.run();

		assertTrue("Pathfinder should complete", pf.isDone());
		List<WorldPoint> rawPath = pf.getPath();
		assertFalse("Path should not be empty", rawPath.isEmpty());
		assertFalse("Raw path must not cross the disabled Varrock Palace trellis shortcut",
				hasConsecutiveStep(rawPath, northTrellis, southTrellis));

		List<WorldPoint> smoothedPath = pf.getWalkablePath();
		assertFalse("Smoothed path must not cross the disabled Varrock Palace trellis shortcut",
				hasLineSegmentStep(smoothedPath, northTrellis, southTrellis));

		WorldPoint endpoint = rawPath.get(rawPath.size() - 1);
		assertTrue("Path should still reach Varrock Sewers, ended at " + endpoint,
				endpoint.distanceTo(dst) <= 1);
	}

	@Test
	public void testVarrockSewerManholeCatalogContainsOnlyTheTraversingEdge() {
		WorldPoint origin = new WorldPoint(3236, 3458, 0);
		WorldPoint destination = new WorldPoint(3237, 9858, 0);
		List<Transport> manholeEdges = Transport.loadAllFromResources()
				.getOrDefault(origin, Collections.emptySet()).stream()
				.filter(transport -> destination.equals(transport.getDestination()))
				.collect(java.util.stream.Collectors.toList());

		assertEquals("Opening the cover is object state preparation, not a traversing graph edge",
				1, manholeEdges.size());
		Transport manhole = manholeEdges.get(0);
		assertEquals("Climb-down", manhole.getAction());
		assertEquals("Manhole", manhole.getName());
		assertEquals(882, manhole.getObjectId());
	}

	@Test
	public void testVarrockSewerPathAvoidsPalaceGardenSouthFenceCollisionGap() {
		PathfinderConfig config = createConfigWithUnavailableShortcutEdges(TransportType.AGILITY_SHORTCUT);
		WorldPoint src = new WorldPoint(3236, 3477, 0);
		WorldPoint dst = new WorldPoint(3237, 9858, 0);

		Pathfinder pf = new Pathfinder(config, src, dst);
		pf.run();

		assertTrue("Pathfinder should complete", pf.isDone());
		List<WorldPoint> rawPath = pf.getPath();
		assertFalse("Path should not be empty", rawPath.isEmpty());
		String rawCrossing = findFenceConsecutiveCrossing(rawPath, 3229, 3241, 3472, 3471, 0);
		assertNull("Raw path must not cross the Varrock Palace garden south fence: " + rawCrossing,
				rawCrossing);

		List<WorldPoint> smoothedPath = pf.getWalkablePath();
		String smoothedCrossing = findFenceCrossing(smoothedPath, 3229, 3241, 3472, 3471, 0);
		assertNull("Smoothed path must not cross the Varrock Palace garden south fence: " + smoothedCrossing,
				smoothedCrossing);

		WorldPoint endpoint = rawPath.get(rawPath.size() - 1);
		assertTrue("Path should still reach Varrock Sewers, ended at " + endpoint,
				endpoint.distanceTo(dst) <= 1);
	}

	@Test
	public void testIgnoreCollisionPackedIsHashSetLookup() {
		int packed = WorldPointUtil.packWorldPoint(3142, 3457, 0);
		assertTrue("Known ignore-collision tile should be in the packed set",
				CollisionMap.ignoreCollisionPacked.contains(packed));

		int notIgnored = WorldPointUtil.packWorldPoint(3200, 3200, 0);
		assertFalse("Random tile should not be in ignore set",
				CollisionMap.ignoreCollisionPacked.contains(notIgnored));
	}

	private PathfinderConfig createConfigWithTransports() {
		HashMap<WorldPoint, Set<Transport>> allTransports = Transport.loadAllFromResources();
		PathfinderConfig config = new PathfinderConfig(
				collisionMap,
				allTransports,
				Collections.emptyList(),
				null,
				null
		);
		try {
			java.lang.reflect.Field f = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
			f.setAccessible(true);
			f.setLong(config, 10000);

			for (Map.Entry<WorldPoint, Set<Transport>> entry : allTransports.entrySet()) {
				if (entry.getKey() == null) {
					continue;
				}
				config.getTransports().put(entry.getKey(), entry.getValue());
				config.getTransportsPacked().put(
						WorldPointUtil.packWorldPoint(entry.getKey()), entry.getValue());
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to configure transports", e);
		}
		return config;
	}

	private PathfinderConfig createConfigWithUnavailableShortcutEdges(TransportType... unavailableTypes) {
		Set<TransportType> unavailable = new HashSet<>(Arrays.asList(unavailableTypes));
		HashMap<WorldPoint, Set<Transport>> allTransports = Transport.loadAllFromResources();
		PathfinderConfig config = new PathfinderConfig(
				collisionMap,
				allTransports,
				Collections.emptyList(),
				null,
				null
		);
		try {
			java.lang.reflect.Field f = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
			f.setAccessible(true);
			f.setLong(config, 10000);

			for (Map.Entry<WorldPoint, Set<Transport>> entry : allTransports.entrySet()) {
				if (entry.getKey() == null) {
					continue;
				}
				Set<Transport> usable = entry.getValue().stream()
						.filter(t -> !unavailable.contains(t.getType()))
						.collect(java.util.stream.Collectors.toSet());
				entry.getValue().stream()
						.filter(t -> unavailable.contains(t.getType()))
						.forEach(config::addBlockedTransportEdgeIfNeeded);
				if (!usable.isEmpty()) {
					config.getTransports().put(entry.getKey(), usable);
					config.getTransportsPacked().put(
							WorldPointUtil.packWorldPoint(entry.getKey()), usable);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to configure unavailable shortcut edges", e);
		}
		return config;
	}

	private static boolean hasConsecutiveStep(List<WorldPoint> path, WorldPoint a, WorldPoint b) {
		for (int i = 0; i + 1 < path.size(); i++) {
			if (isEitherDirection(path.get(i), path.get(i + 1), a, b)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasLineSegmentStep(List<WorldPoint> path, WorldPoint a, WorldPoint b) {
		return findLineSegmentStep(path, a, b) != null;
	}

	private static String findLineSegmentStep(List<WorldPoint> path, WorldPoint a, WorldPoint b) {
		for (int i = 0; i + 1 < path.size(); i++) {
			if (path.get(i).distanceTo2D(path.get(i + 1)) > 10) {
				continue;
			}
			if (lineSegmentContainsStep(path.get(i), path.get(i + 1), a, b)) {
				return path.get(i) + " -> " + path.get(i + 1);
			}
		}
		return null;
	}

	private static boolean lineSegmentContainsStep(WorldPoint from, WorldPoint to, WorldPoint a, WorldPoint b) {
		if (from.getPlane() != to.getPlane()) return false;
		int x = from.getX();
		int y = from.getY();
		while (x != to.getX() || y != to.getY()) {
			WorldPoint stepFrom = new WorldPoint(x, y, from.getPlane());
			x += Integer.signum(to.getX() - x);
			y += Integer.signum(to.getY() - y);
			WorldPoint stepTo = new WorldPoint(x, y, from.getPlane());
			if (isEitherDirection(stepFrom, stepTo, a, b)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isEitherDirection(WorldPoint from, WorldPoint to, WorldPoint a, WorldPoint b) {
		return (from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a));
	}

	private static void assertBlockedBothDirections(PathfinderConfig config, WorldPoint a, WorldPoint b) {
		int packedA = WorldPointUtil.packWorldPoint(a);
		int packedB = WorldPointUtil.packWorldPoint(b);
		assertTrue("Expected blocked edge " + a + " -> " + b,
				config.isBlockedTransportStep(packedA, packedB));
		assertTrue("Expected blocked edge " + b + " -> " + a,
				config.isBlockedTransportStep(packedB, packedA));
	}

	private static boolean isInsideAlKharidMine(WorldPoint point) {
		return point.getPlane() == 0
				&& point.getX() >= AL_KHARID_MINE_MIN_X
				&& point.getX() <= AL_KHARID_MINE_MAX_X
				&& point.getY() >= AL_KHARID_MINE_MIN_Y
				&& point.getY() <= AL_KHARID_MINE_MAX_Y;
	}

	private static boolean hasFenceCrossing(List<WorldPoint> path, int minX, int maxX, int northY, int southY, int plane) {
		return findFenceCrossing(path, minX, maxX, northY, southY, plane) != null;
	}

	private static String findFenceConsecutiveCrossing(List<WorldPoint> path, int minX, int maxX, int northY, int southY, int plane) {
		for (int x = minX; x <= maxX; x++) {
			if (hasConsecutiveStep(path, new WorldPoint(x, northY, plane), new WorldPoint(x, southY, plane))) {
				return x + "," + northY + "<->" + x + "," + southY;
			}
		}
		return null;
	}

	private static String findFenceCrossing(List<WorldPoint> path, int minX, int maxX, int northY, int southY, int plane) {
		for (int x = minX; x <= maxX; x++) {
			String segment = findLineSegmentStep(path, new WorldPoint(x, northY, plane), new WorldPoint(x, southY, plane));
			if (segment != null) {
				return x + "," + northY + "<->" + x + "," + southY + " via " + segment;
			}
		}
		return null;
	}

	// ========================
	// Pathfinder Tiebreaker / Route Diversity Tests
	// ========================

	@Test
	public void testPathfinderTiebreakerProducesDiverseRoutes() {
		// With deterministic A*, the same (start, target) pair always produces the
		// same tile sequence, leaving a fingerprint on bots that shuttle between
		// fixed waypoints. Node.tiebreaker seeds a random secondary priority-queue
		// key so equal-fCost frontiers expand in a different order each run —
		// paths stay optimal by cost but diverge tile-by-tile.
		final WorldPoint start = new WorldPoint(3222, 3218, 0);   // Lumbridge
		final WorldPoint target = new WorldPoint(3164, 3485, 0);  // Grand Exchange
		final int runs = 10;

		List<List<WorldPoint>> paths = new ArrayList<>(runs);
		for (int i = 0; i < runs; i++) {
			Pathfinder pf = new Pathfinder(createMinimalConfig(), start, target);
			pf.run();
			assertTrue("Run " + i + " should complete", pf.isDone());
			List<WorldPoint> path = pf.getPath();
			assertNotNull("Run " + i + " path should not be null", path);
			assertFalse("Run " + i + " path should not be empty", path.isEmpty());
			paths.add(path);
		}

		// Optimality: with no transports configured, path cost == path length
		// (every step is cost 1). All runs should return the same length.
		int referenceLength = paths.get(0).size();
		for (int i = 1; i < runs; i++) {
			assertEquals(
					"Run " + i + " length should match run 0 (optimality preserved)",
					referenceLength, paths.get(i).size());
		}

		// Diversity: at least two of the N runs should produce different tile
		// sequences. Lumbridge → GE has abundant equal-cost alternatives through
		// Varrock squares, so the tiebreaker reliably picks different ones.
		long distinctPaths = paths.stream().distinct().count();
		assertTrue(
				"Expected at least 2 distinct paths over " + runs + " runs, got " + distinctPaths,
				distinctPaths >= 2);
	}

	@Test
	public void testPathfinderShortRouteStillOptimal() {
		// Tiebreaker must not break optimality on short routes where only one
		// shortest tile sequence exists. Two runs should still agree on length.
		final WorldPoint start = new WorldPoint(3222, 3218, 0);
		final WorldPoint target = new WorldPoint(3228, 3218, 0); // 6 tiles east

		Pathfinder a = new Pathfinder(createMinimalConfig(), start, target);
		a.run();
		Pathfinder b = new Pathfinder(createMinimalConfig(), start, target);
		b.run();

		assertEquals("Both runs should have equal path length",
				a.getPath().size(), b.getPath().size());
	}

	private PathfinderConfig createMinimalConfig() {
		PathfinderConfig config = new PathfinderConfig(
				collisionMap,
				new HashMap<>(),
				Collections.emptyList(),
				null,
				null
		);
		try {
			java.lang.reflect.Field f = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
			f.setAccessible(true);
			f.setLong(config, 10000);
		} catch (Exception e) {
			throw new RuntimeException("Failed to set calculationCutoffMillis", e);
		}
		return config;
	}
}
