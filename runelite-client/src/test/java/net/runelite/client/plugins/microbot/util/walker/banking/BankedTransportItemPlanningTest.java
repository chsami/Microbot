package net.runelite.client.plugins.microbot.util.walker.banking;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.walker.Rs2TerminalTravelMode;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteStep;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteTermination;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportItemRequirement;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportLoadout;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;
import net.runelite.client.plugins.microbot.util.walker.TransportRouteAnalysis;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Item-gated plain transports must take part in bank planning.
 *
 * <p>Only currency-bearing {@link TransportType#TRANSPORT} entries used to qualify, so a machete for
 * a jungle bush, a pickaxe for a Motherlode rockfall or a Shantay pass fell through
 * {@code hasRequiredTransportItems} to its catch-all {@code return true}, was never counted as
 * missing, and was never withdrawn.
 *
 * <p>That contradicted the pathfinder, which <em>does</em> count bank contents when
 * {@code useBankItems} is set — so a route was planned through the obstacle on the strength of a
 * banked item the planner then refused to fetch, stranding the walk at the obstacle.
 */
public class BankedTransportItemPlanningTest {

    private static List<Transport> all;

    @BeforeClass
    public static void load() {
        HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
        all = transports.values().stream().flatMap(Set::stream).collect(Collectors.toList());
    }

    /** {@code menuOption;menuTarget} for readable assertion messages. */
    private static String describe(Transport t) {
        String action = t.getAction() == null ? "" : t.getAction();
        String name = t.getName() == null ? "" : t.getName();
        return action + ";" + name;
    }

    private static List<Transport> matching(String menuFragment) {
        return all.stream()
                .filter(t -> t.getType() == TransportType.TRANSPORT)
                .filter(t -> {
                    String target = describe(t);
                    return target.toLowerCase().contains(menuFragment.toLowerCase());
                })
                .collect(Collectors.toList());
    }

	private static Rs2TransportEdge owned(Transport transport) {
		List<Rs2TransportItemRequirement> requirements = transport.getItemRequirements().stream()
			.map(requirement -> new Rs2TransportItemRequirement(
				requirement.getAlternatives(),
				requirement.getStaffAlternatives(),
				requirement.getOffhandAlternatives(),
				requirement.isRuneOnly()))
			.collect(Collectors.toList());
		return new Rs2TransportEdge(
			transport.getOrigin(),
			transport.getDestination(),
			Rs2TransportType.valueOf(transport.getType().name()),
			Rs2TransportExecutor.OBJECT,
			Rs2TerminalTravelMode.UNSUPPORTED,
			transport.getDisplayInfo(),
			transport.getAction(),
			transport.getName(),
			transport.getObjectId(),
			transport.getDuration(),
			TransportType.isTeleport(transport.getType(), transport.getOrigin()),
			transport.isConsumable(),
			transport.isMembers(),
			transport.getMaxWildernessLevel(),
			transport.getCurrencyName(),
			transport.getCurrencyAmount(),
			requirements);
	}

	private static Rs2TransportEdge sourceAwareSpellEdge() {
		Rs2TransportItemRequirement fire = new Rs2TransportItemRequirement(
			Map.of(ItemID.FIRERUNE, 2),
			Set.of(ItemID.TWINFLAME_STAFF),
			Set.of(),
			true);
		Rs2TransportItemRequirement water = new Rs2TransportItemRequirement(
			Map.of(ItemID.WATERRUNE, 2),
			Set.of(ItemID.TWINFLAME_STAFF),
			Set.of(),
			true);
		Rs2TransportItemRequirement law = new Rs2TransportItemRequirement(
			Map.of(ItemID.LAWRUNE, 2), Set.of(), Set.of(), true);
		Rs2TransportItemRequirement banana = new Rs2TransportItemRequirement(
			Map.of(ItemID.BANANA, 1));
		return new Rs2TransportEdge(
			null,
			new WorldPoint(2771, 9102, 0),
			Rs2TransportType.TELEPORTATION_SPELL,
			Rs2TransportExecutor.SPELL_TELEPORT,
			Rs2TerminalTravelMode.UNSUPPORTED,
			"Ape Atoll Teleport",
			"Cast",
			"",
			-1,
			5,
			true,
			true,
			true,
			20,
			"",
			0,
			List.of(fire, water, law, banana));
	}

	private static Rs2TransportEdge teleportEdge(WorldPoint destination) {
		return new Rs2TransportEdge(
			null,
			destination,
			Rs2TransportType.TELEPORTATION_ITEM,
			Rs2TransportExecutor.ITEM_TELEPORT,
			Rs2TerminalTravelMode.UNSUPPORTED,
			"test teleport",
			"Teleport",
			"test item",
			-1,
			1,
			true,
			false,
			false,
			0,
			"",
			0,
			List.of());
	}

	@Test
	public void bankDistanceUsesExactSelectedRouteStepForImmediateTeleport() {
		WorldPoint bank = new WorldPoint(3200, 3200, 0);
		WorldPoint landing = new WorldPoint(3000, 3000, 0);
		WorldPoint tail = new WorldPoint(3001, 3000, 0);
		WorldPoint target = new WorldPoint(3002, 3000, 0);
		Rs2TransportEdge selected = teleportEdge(landing);
		List<WorldPoint> path = List.of(bank, landing, tail, target);
		List<Rs2RouteStep> steps = List.of(
			Rs2RouteStep.transport(bank, landing, selected),
			Rs2RouteStep.walk(landing, tail),
			Rs2RouteStep.walk(tail, target));

		assertEquals("the bank-leg metric must use the exact route edge rather than rematching the catalog",
			4, Rs2WalkerBankingPlanner.effectiveDistanceFromBank(path, steps, 205));
	}

	@Test
    public void bankDistanceWithoutSelectedTransportKeepsRawDistance() {
		WorldPoint bank = new WorldPoint(3200, 3200, 0);
		WorldPoint target = new WorldPoint(3201, 3200, 0);
		List<WorldPoint> path = List.of(bank, target);

		assertEquals(1, Rs2WalkerBankingPlanner.effectiveDistanceFromBank(
			path, List.of(Rs2RouteStep.walk(bank, target)), 1));
	}

	@Test
	public void exhaustedPartialRouteIsUnavailableRatherThanMaxValueDistance() {
		WorldPoint start = new WorldPoint(2963, 3378, 0);
		WorldPoint partial = new WorldPoint(2919, 3499, 0);
		WorldPoint target = new WorldPoint(2907, 3539, 0);
		assertEquals(-1, Rs2WalkerBankingPlanner.comparableRouteDistance(
			List.of(start, partial), target, Rs2RouteTermination.SEARCH_EXHAUSTED, false));
	}

	@Test
	public void reachedRouteHasFiniteComparableDistance() {
		WorldPoint start = new WorldPoint(3200, 3200, 0);
		WorldPoint target = new WorldPoint(3201, 3200, 0);
		assertEquals(2, Rs2WalkerBankingPlanner.comparableRouteDistance(
			List.of(start, target), target, Rs2RouteTermination.TARGET_REACHED, true));
	}

	@Test
	public void withdrawalPlanningUsesTheExactComparedBankRoute() {
		WorldPoint start = new WorldPoint(3200, 3200, 0);
		WorldPoint bank = new WorldPoint(3201, 3200, 0);
		WorldPoint landing = new WorldPoint(3000, 3000, 0);
		Rs2TransportEdge selected = teleportEdge(landing);
		TransportRouteAnalysis analysis = new TransportRouteAnalysis(
			List.of(start, bank),
			null,
			bank,
			List.of(start, bank),
			List.of(bank, landing),
			"bank route selected",
			1,
			2,
			List.of(Rs2RouteStep.walk(start, bank)),
			List.of(Rs2RouteStep.walk(start, bank)),
			List.of(Rs2RouteStep.transport(bank, landing, selected)));

		List<Rs2TransportEdge> required =
			Rs2WalkerBankingPlanner.getRequiredTransportEdgesFromBank(analysis);

		assertEquals(1, required.size());
		assertSame("withdrawal planning must consume the transport selected by the compared bank leg",
			selected, required.get(0));
	}

	@Test
	public void sourceAwareSpellLoadoutWithdrawsAndEquipsOneCombinationStaff() {
		Rs2TransportLoadout loadout = Rs2WalkerBankingPlanner.getMissingTransportEdgeLoadout(
			List.of(sourceAwareSpellEdge()),
			itemId -> itemId == ItemID.TWINFLAME_STAFF ? 1
				: itemId == ItemID.LAWRUNE ? 2
				: itemId == ItemID.BANANA ? 1 : 0,
			ignored -> 0,
			ignored -> 0,
			itemId -> itemId == ItemID.LAWRUNE ? 2
				: itemId == ItemID.BANANA ? 1 : 0,
			ignored -> false);

		assertTrue(loadout.isSatisfiable());
		assertEquals(Map.of(
			ItemID.TWINFLAME_STAFF, 1,
			ItemID.LAWRUNE, 2,
			ItemID.BANANA, 1), loadout.getWithdrawals());
		assertEquals(List.of(ItemID.TWINFLAME_STAFF), loadout.getEquipmentItemIds());
		assertFalse(loadout.getWithdrawals().containsKey(ItemID.FIRERUNE));
		assertFalse(loadout.getWithdrawals().containsKey(ItemID.WATERRUNE));
	}

	@Test
	public void carriedUnequippedStaffCreatesEquipActionWithoutStaffWithdrawal() {
		Rs2TransportLoadout loadout = Rs2WalkerBankingPlanner.getMissingTransportEdgeLoadout(
			List.of(sourceAwareSpellEdge()),
			itemId -> itemId == ItemID.LAWRUNE ? 2
				: itemId == ItemID.BANANA ? 1 : 0,
			itemId -> itemId == ItemID.TWINFLAME_STAFF ? 1 : 0,
			ignored -> 0,
			itemId -> itemId == ItemID.LAWRUNE ? 2
				: itemId == ItemID.BANANA ? 1 : 0,
			ignored -> false);

		assertTrue(loadout.isSatisfiable());
		assertEquals(Map.of(ItemID.LAWRUNE, 2, ItemID.BANANA, 1), loadout.getWithdrawals());
		assertEquals(List.of(ItemID.TWINFLAME_STAFF), loadout.getEquipmentItemIds());
	}

	@Test
	public void missingRuneProviderMakesTheLoadoutExplicitlyUnavailable() {
		Rs2TransportLoadout loadout = Rs2WalkerBankingPlanner.getMissingTransportEdgeLoadout(
			List.of(sourceAwareSpellEdge()),
			itemId -> itemId == ItemID.LAWRUNE ? 2
				: itemId == ItemID.BANANA ? 1 : 0,
			ignored -> 0,
			ignored -> 0,
			ignored -> 0,
			ignored -> false);

		assertFalse(loadout.isSatisfiable());
		assertTrue(loadout.isEmpty());
	}

    @Test
    public void itemGatedPlainTransportsNowQualifyForPlanning() {
        List<Transport> itemGated = all.stream()
                .filter(t -> t.getType() == TransportType.TRANSPORT)
                .filter(t -> t.getItemIdRequirements() != null && !t.getItemIdRequirements().isEmpty())
                .filter(t -> t.getCurrencyAmount() <= 0)
                .collect(Collectors.toList());

        assertFalse("the data should contain item-gated plain transports (rockfalls, jungle bushes, "
                + "Shantay passes)", itemGated.isEmpty());

        for (Transport t : itemGated) {
            assertTrue("an item-gated plain transport must be eligible for bank planning, otherwise "
                            + "the pathfinder routes through it on a banked item nobody withdraws: " + describe(t),
                    Rs2WalkerBankingPlanner.planningCoversPlainTransport(t));
        }
    }

	@Test
	public void itemGatedPlainTransportsSurviveTheActualPlanningFilter() {
		Transport itemGated = all.stream()
				.filter(t -> t.getType() == TransportType.TRANSPORT)
				.filter(t -> t.getItemIdRequirements() != null && !t.getItemIdRequirements().isEmpty())
				.filter(t -> t.getCurrencyAmount() <= 0)
				.findFirst()
				.orElseThrow(() -> new AssertionError("catalog should contain an item-gated plain transport"));

		List<Transport> filtered = Rs2WalkerBankingPlanner.applyTransportFiltering(List.of(itemGated));

		assertEquals("the real banking filter must not discard the selected item-gated edge",
				List.of(itemGated), filtered);
		Rs2TransportEdge edge = owned(itemGated);
		assertEquals("the immutable banking filter must retain the same selected edge",
				List.of(edge), Rs2WalkerBankingPlanner.applyTransportEdgeFiltering(List.of(edge)));
		assertTrue(Rs2WalkerBankingPlanner.planningCoversPlainTransportEdge(edge));
	}

    /** A transport with no item and no currency requirement must stay out of planning. */
    @Test
    public void unrestrictedTransportsAreStillIgnored() {
        List<Transport> unrestricted = all.stream()
                .filter(t -> t.getType() == TransportType.TRANSPORT)
                .filter(t -> t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty())
                .filter(t -> t.getCurrencyAmount() <= 0)
                .collect(Collectors.toList());

        assertFalse("precondition: most doors and stairs require nothing", unrestricted.isEmpty());
        for (Transport t : unrestricted) {
            assertFalse("a transport requiring nothing must never trigger a bank trip: " + describe(t),
                    Rs2WalkerBankingPlanner.planningCoversPlainTransport(t));
        }
    }

    /**
     * Pure currency transports (charter fares, magic carpets, the Shantay coin row) have EMPTY
     * itemIdRequirements — the withdrawal-quantity collector's item loop never ran for them, so their
     * coins were never withdrawn at the bank and the post-bank replan dropped the transport ("banked
     * walking does not withdraw gold"). Every real currency transport in the catalog must contribute its
     * fare to the withdrawal map, and fares must SUM across multiple currency hops.
     */
    @Test
    public void pureCurrencyFaresEnterTheWithdrawalMap() {
        List<Transport> pureCurrency = all.stream()
                .filter(t -> t.getCurrencyAmount() > 0)
                .filter(t -> "Coins".equalsIgnoreCase(t.getCurrencyName()))
                .filter(t -> t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty())
                // Match the collector's own type gate, or this can pick a catalog row the collector
                // never considers and fail for a reason the test is not about.
                .filter(t -> Rs2WalkerBankingPlanner.isCurrencyBasedTransport(t.getType()))
                .collect(Collectors.toList());
        assertFalse("precondition: the catalog has pure coin-fare transports (charters etc.)",
                pureCurrency.isEmpty());

        Transport one = pureCurrency.get(0);
        java.util.Map<Integer, Integer> map =
                Rs2WalkerBankingPlanner.getMissingTransportItemIdsWithQuantities(java.util.List.of(one));
        assertTrue("a pure coin fare must appear in the withdrawal map: " + describe(one),
                map.getOrDefault(net.runelite.api.gameval.ItemID.COINS, 0) >= one.getCurrencyAmount());

        java.util.Map<Integer, Integer> summed =
                Rs2WalkerBankingPlanner.getMissingTransportItemIdsWithQuantities(java.util.List.of(one, one));
        assertTrue("fares must sum across currency hops",
                summed.getOrDefault(net.runelite.api.gameval.ItemID.COINS, 0) >= one.getCurrencyAmount() * 2);

		Rs2TransportEdge edge = owned(one);
		java.util.Map<Integer, Integer> edgeSummed =
				Rs2WalkerBankingPlanner.getMissingTransportEdgeItemIdsWithQuantities(
						List.of(edge, edge),
						itemId -> itemId == net.runelite.api.gameval.ItemID.COINS
								? one.getCurrencyAmount() * 2 : 0,
						ignored -> 0);
		assertEquals("immutable selected edges must sum the same fares",
				one.getCurrencyAmount() * 2,
				edgeSummed.getOrDefault(net.runelite.api.gameval.ItemID.COINS, 0).intValue());
    }

    /**
     * An item-gated row whose item the bank cannot supply, but which is vendor-purchasable at the
     * transport (the Shantay ticket row): the planner must withdraw the FARE, not request an item
     * the withdrawal step can never satisfy. Headless bank counts read as zero, which is exactly
     * the "not banked" case.
     */
    @Test
    public void unbankedPurchasableItemFallsBackToItsFare() {
        Transport ticketRow = all.stream()
                .filter(t -> t.getObjectId() == 4031)
                .filter(t -> t.getItemIdRequirements() != null && !t.getItemIdRequirements().isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("catalog should contain the Shantay ticket row"));

        java.util.Map<Integer, Integer> map =
                Rs2WalkerBankingPlanner.getMissingTransportItemIdsWithQuantities(
                        java.util.List.of(ticketRow), ignored -> 0);

        assertEquals("the planner must withdraw exactly one 5-coin fare",
                5, map.getOrDefault(net.runelite.api.gameval.ItemID.COINS, 0).intValue());
        assertFalse("the unbankable ticket itself must not be requested",
                map.containsKey(1854));

		Rs2TransportEdge edge = owned(ticketRow);
		java.util.Map<Integer, Integer> edgeMap =
				Rs2WalkerBankingPlanner.getMissingTransportEdgeItemIdsWithQuantities(
						List.of(edge),
						itemId -> itemId == net.runelite.api.gameval.ItemID.COINS ? 5 : 0,
						ignored -> 0);
		assertEquals("the immutable selected edge must preserve the purchasable fallback",
				5, edgeMap.getOrDefault(net.runelite.api.gameval.ItemID.COINS, 0).intValue());
		assertFalse(edgeMap.containsKey(1854));
    }

	@Test
	public void accumulatedFareMustExistInBankBeforeBankRouteIsSelected() {
		Transport fare = all.stream()
				.filter(t -> t.getCurrencyAmount() > 1)
				.filter(t -> "Coins".equalsIgnoreCase(t.getCurrencyName()))
				.filter(t -> t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty())
				.filter(t -> Rs2WalkerBankingPlanner.isCurrencyBasedTransport(t.getType()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("catalog should contain a coin-fare transport"));
		int totalFare = fare.getCurrencyAmount() * 2;
		Rs2TransportLoadout loadout = Rs2WalkerBankingPlanner.getMissingTransportEdgeLoadout(
				List.of(owned(fare), owned(fare)),
				itemId -> itemId == net.runelite.api.gameval.ItemID.COINS ? totalFare - 1 : 0,
				ignored -> 0,
				ignored -> 0,
				itemId -> itemId == net.runelite.api.gameval.ItemID.COINS ? totalFare - 1 : 0,
				ignored -> false);

		assertFalse("a route must not claim an unaffordable accumulated fare is satisfiable",
				loadout.isSatisfiable());
		assertTrue(loadout.isEmpty());
	}

    @Test
    public void legacyChargedItemVariantsRequestOnlyOneAlternative() {
        Transport gamesNecklace = all.stream()
                .filter(t -> t.getType() == TransportType.TELEPORTATION_ITEM)
                .filter(t -> t.getDisplayInfo() != null
                        && t.getDisplayInfo().startsWith("Games necklace:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("catalog should contain Games necklace teleports"));

        assertEquals("legacy semicolon variants must be represented as one OR requirement",
                1, gamesNecklace.getItemRequirements().size());
        java.util.Map<Integer, Integer> requested =
                Rs2WalkerBankingPlanner.getMissingTransportItemIdsWithQuantities(
                        java.util.List.of(gamesNecklace), ignored -> 0);

        assertEquals("bank planning must request one charged variant, not every charge state",
                1, requested.size());
        assertEquals(1, requested.values().iterator().next().intValue());
    }

    @Test
    public void symbolicCanoeAxeCollectionChoosesOneBankedAlternative() {
        Transport canoe = all.stream()
                .filter(t -> t.getType() == TransportType.CANOE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("catalog should contain River Lum canoes"));
        int crystalAxe = net.runelite.api.gameval.ItemID.CRYSTAL_AXE;

        assertEquals(12, canoe.getItemRequirements().get(0).getItemIds().size());
        java.util.Map<Integer, Integer> requested =
                Rs2WalkerBankingPlanner.getMissingTransportItemIdsWithQuantities(
                        java.util.List.of(canoe), itemId -> itemId == crystalAxe ? 1 : 0);

        assertEquals("bank planning should request the available axe, not every symbolic variant",
                java.util.Map.of(crystalAxe, 1), requested);

		Rs2TransportEdge edge = owned(canoe);
		java.util.Map<Integer, Integer> edgeRequested =
				Rs2WalkerBankingPlanner.getMissingTransportEdgeItemIdsWithQuantities(
						List.of(edge), itemId -> itemId == crystalAxe ? 1 : 0, ignored -> 0);
		assertEquals("immutable selected edges must preserve OR-alternative selection",
				java.util.Map.of(crystalAxe, 1), edgeRequested);
    }

    /** Currency-bearing transports kept their existing eligibility. */
    @Test
    public void currencyTransportsRemainEligible() {
        List<Transport> currency = all.stream()
                .filter(t -> t.getType() == TransportType.TRANSPORT)
                .filter(t -> t.getCurrencyAmount() > 0)
                .collect(Collectors.toList());

        assertFalse("precondition: the data should contain currency-bearing plain transports",
                currency.isEmpty());
        for (Transport t : currency) {
            assertTrue("currency transports must keep qualifying: " + describe(t),
                    Rs2WalkerBankingPlanner.planningCoversPlainTransport(t));
        }
    }

    /**
     * A concrete upstream example: the machete-gated jungle obstacles on Karamja. Uses data that
     * predates this branch so the assertion does not depend on anything we added.
     */
    @Test
    public void theMacheteGatedJungleObstaclesQualify() {
        List<Transport> jungle = matching("Jungle");
        assertFalse("upstream data should contain machete-gated jungle obstacles", jungle.isEmpty());
        int gated = 0;
        for (Transport t : jungle) {
            if (t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty()) continue;
            gated++;
            assertTrue("a machete-gated obstacle must be plannable: " + describe(t),
                    Rs2WalkerBankingPlanner.planningCoversPlainTransport(t));
        }
        assertTrue("at least one jungle obstacle should carry an item requirement", gated > 0);
    }

    @Test
    public void nullAndNonPlainTransportsAreRejectedSafely() {
        assertFalse("null must not qualify", Rs2WalkerBankingPlanner.planningCoversPlainTransport(null));

        List<Transport> teleports = all.stream()
                .filter(t -> t.getType() == TransportType.TELEPORTATION_ITEM)
                .collect(Collectors.toList());
        for (Transport t : teleports) {
            assertFalse("non-plain types are handled by their own branch and must not match here",
                    Rs2WalkerBankingPlanner.planningCoversPlainTransport(t));
        }
    }
}
