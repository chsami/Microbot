package net.runelite.client.plugins.microbot.util.walker.banking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.RuneFilter;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteResult;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteStep;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportItemRequirement;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportLoadout;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;
import net.runelite.client.plugins.microbot.shortestpath.PurchasableItemCatalog;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportItemRequirement;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.TransportRouteAnalysis;
import net.runelite.client.plugins.microbot.util.walker.WebWalkLog;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

@Slf4j
public final class Rs2WalkerBankingPlanner {

    private Rs2WalkerBankingPlanner() {
    }

	/**
	 * Plan the destination and retain only the immutable transport edges selected by that search.
	 *
	 * <p>This is the planner-independent banking contract. In particular, it does not rescan the
	 * mutable transport catalog by origin/destination after pathfinding, so two transports sharing an
	 * edge cannot be confused.</p>
	 */
	public static List<Rs2TransportEdge> getTransportEdgesForDestination(
		WorldPoint destination, boolean useBankItems)
	{
		if (destination == null)
		{
			return List.of();
		}
		WorldPoint start = Rs2Player.getWorldLocation();
		if (start == null)
		{
			log.debug("Unable to plan transport edges without a player location");
			return List.of();
		}

		Rs2RouteResult route = Rs2PathApi.plan(
			Rs2RouteRequest.to(start, destination)
				.withBankItems(useBankItems));
		if (route.getPath().isEmpty())
		{
			log.debug("Unable to find path to destination: {}", destination);
			return List.of();
		}

		List<Rs2TransportEdge> selected = route.getTransportSteps().stream()
			.map(Rs2RouteStep::getTransport)
			.map(transport -> transport.orElseThrow(
				() -> new IllegalStateException("typed route step has no transport metadata")))
			.collect(Collectors.toList());
		List<Rs2TransportEdge> transports = applyTransportEdgeFiltering(selected);
		transports.forEach(transport -> log.debug("Transport edge found: {} -> {} ({})",
			transport.getOrigin(), transport.getDestination(), transport.getType()));
		return transports;
	}

	/**
	 * Return the bank-to-target transport requirements from the exact route already compared.
	 *
	 * <p>This must not perform another search from the player's current pre-bank location. Doing so
	 * can select a different transport network from the route whose distance caused the banking
	 * decision, and then withdraw items for a route that will never be executed.</p>
	 */
	public static List<Rs2TransportEdge> getRequiredTransportEdgesFromBank(
		TransportRouteAnalysis analysis)
	{
		if (analysis == null || !analysis.isRouteFromBankStepsExact())
		{
			return List.of();
		}
		return applyTransportEdgeFiltering(analysis.getTransportEdgesFromBank());
	}

    /**
     * Compatibility API for Hub plugins compiled against concrete shortest-path transports.
     * New code must use {@link #getTransportEdgesForDestination(WorldPoint, boolean)}.
     */
    @Deprecated
    public static List<Transport> getTransportsForDestination(WorldPoint destination, boolean useBankItems, TransportType prefTransportType) {
        if (destination == null) {
            return new ArrayList<>();
        }
		WorldPoint start = Rs2Player.getWorldLocation();
		if (start == null) {
			return new ArrayList<>();
		}

        Rs2RouteResult route = Rs2PathApi.plan(
                Rs2RouteRequest.to(start, destination)
                        .withBankItems(useBankItems));
        List<WorldPoint> path = route.getPath();
        if (path.isEmpty()) {
            log.debug("Unable to find path to destination: " + destination);
            return new ArrayList<>();
        }

        // This deprecated return type cannot carry the planner-owned immutable edge, so retain the
        // historical catalog view for binary compatibility. The active banking path above uses exact
        // Rs2TransportEdge instances and never enters this endpoint-based adapter.
        List<Transport> transports = Rs2Walker.getTransportsForPath(
                path, 0, prefTransportType, true);
        transports.forEach(t -> log.debug("Transport found: " + t));
        return transports;
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

	/** Legacy concrete-transport filter while banking consumers migrate to immutable edge views. */
	public static List<Transport> applyTransportFiltering(List<Transport> transports) {
		return transports.stream()
				.filter(t -> t.getType() == TransportType.TELEPORTATION_ITEM
						|| t.getType() == TransportType.FAIRY_RING
						|| t.getType() == TransportType.TELEPORTATION_SPELL
						|| t.getType() == TransportType.CANOE
						|| t.getType() == TransportType.BOAT
						|| t.getType() == TransportType.CHARTER_SHIP
						|| t.getType() == TransportType.SHIP
						|| t.getType() == TransportType.MINECART
						|| t.getType() == TransportType.MAGIC_CARPET
						|| t.getType() == TransportType.SPIRIT_TREE
						|| planningCoversPlainTransport(t)
						|| t.getType() == TransportType.SEASONAL_TRANSPORT
							&& Rs2LeaguesTransport.isLeaguesActive()
							&& t.getDisplayInfo() != null
							&& t.getDisplayInfo().toLowerCase().startsWith("leagues area:"))
				.peek(t -> {
					if (t.getType() == TransportType.FAIRY_RING
							&& (t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty())
							&& Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) != 1) {
						t.setItemIdRequirements(Set.of(Set.of(
								ItemID.DRAMEN_STAFF,
								ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)));
					}
					if (isCurrencyBasedTransport(t.getType())
							&& (t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty())
							&& t.getCurrencyName() != null && !t.getCurrencyName().isEmpty()
							&& t.getCurrencyAmount() > 0) {
						int currencyItemId = getCurrencyItemId(t.getCurrencyName());
						if (currencyItemId != -1) {
							t.setItemIdRequirements(Set.of(Set.of(currencyItemId)));
							log.debug("Set currency requirement for {}: {} x{} (ID: {})",
									t.getType(), t.getCurrencyName(), t.getCurrencyAmount(), currencyItemId);
						}
					}
				})
				.collect(Collectors.toList());
	}

	static boolean planningCoversPlainTransportEdge(Rs2TransportEdge transport)
	{
		return transport != null
			&& transport.getType() == Rs2TransportType.TRANSPORT
			&& (transport.getCurrencyAmount() > 0 || !transport.getItemRequirements().isEmpty());
	}

	/** Filter selected immutable route edges down to transports relevant to bank preparation. */
	public static List<Rs2TransportEdge> applyTransportEdgeFiltering(
		List<Rs2TransportEdge> transports)
	{
		if (transports == null)
		{
			return List.of();
		}
		return transports.stream()
			.filter(transport -> transport.getType() == Rs2TransportType.TELEPORTATION_ITEM
				|| transport.getType() == Rs2TransportType.FAIRY_RING
				|| transport.getType() == Rs2TransportType.TELEPORTATION_SPELL
				|| transport.getType() == Rs2TransportType.CANOE
				|| transport.getType() == Rs2TransportType.BOAT
				|| transport.getType() == Rs2TransportType.CHARTER_SHIP
				|| transport.getType() == Rs2TransportType.SHIP
				|| transport.getType() == Rs2TransportType.MINECART
				|| transport.getType() == Rs2TransportType.MAGIC_CARPET
				|| transport.getType() == Rs2TransportType.SPIRIT_TREE
				|| planningCoversPlainTransportEdge(transport)
				|| transport.getType() == Rs2TransportType.SEASONAL_TRANSPORT
					&& Rs2LeaguesTransport.isLeaguesActive()
					&& transport.getDisplayInfo() != null
					&& transport.getDisplayInfo().toLowerCase(Locale.ROOT)
						.startsWith("leagues area:"))
			.collect(Collectors.toUnmodifiableList());
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
                if (!transport.getItemRequirements().isEmpty()) {
                    return TransportItemRequirement.selectProviders(
                            transport.getItemRequirements(),
                            Rs2WalkerBankingPlanner::carriedRequirementItemQuantity,
                            itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId),
                            itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId))
                            .isPresent();
                }
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
                return Rs2Inventory.itemQuantity(currencyItemId) >= transport.getCurrencyAmount();
            }
            if (transport.getItemIdRequirements() == null || transport.getItemIdRequirements().isEmpty()) {
                return true;
            }

            return transport.getItemRequirements().stream()
                    .allMatch(requirement -> requirement.isSatisfiedBy(
                            Rs2WalkerBankingPlanner::carriedItemQuantity));
        }

        return true;
    }

	public static boolean hasRequiredTransportEdgeItems(Rs2TransportEdge transport)
	{
		if (transport == null)
		{
			return false;
		}
		if (transport.getType() == Rs2TransportType.FAIRY_RING)
		{
			return hasFairyRingAccess();
		}
		if (!isBankPlanningTransport(transport))
		{
			return true;
		}
		if (isSpellTransport(transport) && transport.getDisplayInfo() != null)
		{
			if (!transport.getItemRequirements().isEmpty())
			{
				return Rs2TransportItemRequirement.selectProviders(
					transport.getItemRequirements(),
					Rs2WalkerBankingPlanner::carriedRequirementItemQuantity,
					itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId),
					itemId -> Rs2Equipment.isWearing(itemId) || Rs2Inventory.hasItem(itemId))
					.isPresent();
			}
			Rs2Spells rs2Spell = Rs2Magic.getRs2Spell(spellLookupName(transport.getDisplayInfo()));
			return rs2Spell != null && Rs2Magic.hasRequiredRunes(rs2Spell);
		}
		if (isCurrencyBasedTransport(transport.getType())
			&& transport.getItemRequirements().isEmpty()
			&& !transport.getCurrencyName().isEmpty()
			&& transport.getCurrencyAmount() > 0)
		{
			int currencyItemId = getCurrencyItemId(transport.getCurrencyName());
			return currencyItemId > 0
				&& Rs2Inventory.itemQuantity(currencyItemId) >= transport.getCurrencyAmount();
		}
		return transport.getItemRequirements().stream()
			.allMatch(requirement -> requirement.isSatisfiedBy(
				Rs2WalkerBankingPlanner::carriedItemQuantity));
	}

	private static boolean hasFairyRingAccess()
	{
		return Rs2Inventory.hasItem(ItemID.DRAMEN_STAFF)
			|| Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF)
			|| Rs2Inventory.hasItem(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)
			|| Rs2Equipment.isWearing(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)
			|| Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1;
	}

	private static boolean isBankPlanningTransport(Rs2TransportEdge transport)
	{
		Rs2TransportType type = transport.getType();
		return type == Rs2TransportType.TELEPORTATION_ITEM
			|| isSpellTransport(transport)
			|| type == Rs2TransportType.CANOE
			|| type == Rs2TransportType.BOAT
			|| type == Rs2TransportType.CHARTER_SHIP
			|| type == Rs2TransportType.SHIP
			|| type == Rs2TransportType.MINECART
			|| type == Rs2TransportType.MAGIC_CARPET
			|| planningCoversPlainTransportEdge(transport);
	}

	private static boolean isSpellTransport(Rs2TransportEdge transport)
	{
		return transport.getType() == Rs2TransportType.TELEPORTATION_SPELL;
	}

	private static String spellLookupName(String displayInfo)
	{
		return displayInfo.contains(":")
			? displayInfo.split(":", 2)[0].trim().toLowerCase(Locale.ROOT)
			: displayInfo.trim().toLowerCase(Locale.ROOT);
	}

    public static List<Transport> getMissingTransports(List<Transport> transports) {
        if (transports == null) {
            return new ArrayList<>();
        }

        return transports.stream()
                .filter(t -> !hasRequiredTransportItems(t))
                .collect(Collectors.toList());
    }

	public static List<Rs2TransportEdge> getMissingTransportEdges(
		List<Rs2TransportEdge> transports)
	{
		if (transports == null)
		{
			return List.of();
		}
		return transports.stream()
			.filter(transport -> !hasRequiredTransportEdgeItems(transport))
			.collect(Collectors.toUnmodifiableList());
	}

    public static Map<Integer, Integer> getMissingTransportItemIdsWithQuantities(List<Transport> transports) {
        return getMissingTransportItemIdsWithQuantities(transports, Rs2Bank::count);
    }

    /**
     * Pure selection seam for tests and callers that already hold a bank snapshot.
     *
     * <p>The public entry point supplies {@link Rs2Bank#count(int)}. Keeping the provider outside the
     * selection rules prevents headless tests from waiting on the client thread and lets the AND/OR
     * choice policy be verified independently of the bank widget.
     */
    static Map<Integer, Integer> getMissingTransportItemIdsWithQuantities(
            List<Transport> transports,
            IntUnaryOperator bankQuantityProvider) {
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
                            int bankQuantity = bankQuantityProvider.applyAsInt(runeItemId);
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

            // Pure currency transports (charter fares, magic carpets, the Shantay 5-coin gate row) have
            // EMPTY itemIdRequirements, so the item loop below never runs for them — their coins were
            // never added to the withdrawal map. The transport was correctly detected as missing, but the
            // fare was never fetched: the post-bank replan (inventory-only) then dropped the transport and
            // produced the long overland route ("banked walking does not withdraw gold"). Sum fares across
            // every currency transport on the route.
            if (isCurrencyBasedTransport(transport.getType())
                    && transport.getCurrencyAmount() > 0
                    && (transport.getItemIdRequirements() == null || transport.getItemIdRequirements().isEmpty())) {
                int currencyItemId = getCurrencyItemId(transport.getCurrencyName());
                if (currencyItemId > 0) {
                    int currentQuantity = itemQuantityMap.getOrDefault(currencyItemId, 0);
                    itemQuantityMap.put(currencyItemId, currentQuantity + transport.getCurrencyAmount());
                    log.debug("Added currency fare requirement: itemId={} x{} for {}",
                            currencyItemId, transport.getCurrencyAmount(), transport.getType());
                }
                return;
            }

            if (transport.getItemRequirements() != null) {
                for (TransportItemRequirement requirement : transport.getItemRequirements()) {
                    if (requirement.isSatisfiedBy(Rs2WalkerBankingPlanner::carriedItemQuantity)) {
                        continue;
                    }
                    Set<Integer> alternativeItems = requirement.getItemIds();

                    Integer preferredItemId = null;
                    int preferredBankQuantity = 0;
                    for (Integer itemId : alternativeItems) {
                        int requiredQuantity = requirement.getRequiredQuantity(itemId);
                        if (requiredQuantity == 0) {
                            continue;
                        }
                        int bankQuantity = 0;
                        try {
                            bankQuantity = bankQuantityProvider.applyAsInt(itemId);
                        } catch (Exception e) {
                            log.debug("Could not check bank for item " + itemId + ": " + e.getMessage());
                        }
                        int preferredRequired = preferredItemId == null
                                ? Integer.MAX_VALUE
                                : requirement.getRequiredQuantity(preferredItemId);
                        boolean satisfies = bankQuantity >= requiredQuantity;
                        boolean preferredSatisfies = preferredItemId != null
                                && preferredBankQuantity >= preferredRequired;
                        if (preferredItemId == null
                                || (satisfies && !preferredSatisfies)
                                || (satisfies == preferredSatisfies && bankQuantity > preferredBankQuantity)) {
                            preferredItemId = itemId;
                            preferredBankQuantity = bankQuantity;
                        }
                    }

                    // The bank holds none of the alternatives — withdrawing the item is impossible.
                    // If one of them is vendor-purchasable at its transport (the Shantay pass
                    // pattern), withdraw the fare instead so the buy-at-transport step can run.
                    if (preferredItemId != null && preferredBankQuantity == 0) {
                        PurchasableItemCatalog.PurchasableItem purchasable = alternativeItems.stream()
                                .map(PurchasableItemCatalog::byItemId)
                                .filter(java.util.Objects::nonNull)
                                .findFirst()
                                .orElse(null);
                        int currencyItemId = purchasable == null ? -1 : getCurrencyItemId(purchasable.costCurrencyName);
                        if (currencyItemId > 0) {
                            int requiredQuantity = requirement.getRequiredQuantity(purchasable.itemId);
                            int itemsNeeded = isCurrencyBasedTransport(transport.getType()) ? 1 : requiredQuantity;
                            int fare = purchasable.costAmount * itemsNeeded;
                            itemQuantityMap.merge(currencyItemId, fare, Integer::sum);
                            log.debug("Transport item {} not banked but purchasable — withdrawing fare {} x{} instead",
                                    purchasable.itemId, purchasable.costCurrencyName, fare);
                            continue;
                        }
                    }
                    if (preferredItemId != null) {
                        int requiredQuantity = requirement.getRequiredQuantity(preferredItemId);
                        int currentQuantity = itemQuantityMap.getOrDefault(preferredItemId, 0);
                        itemQuantityMap.put(preferredItemId, currentQuantity + requiredQuantity);
                        log.debug("Added transport item requirement: itemId={} x{} (bank has: {} short={})",
                                preferredItemId, requiredQuantity, preferredBankQuantity, preferredBankQuantity < requiredQuantity);
                    }
                }
            }
        });

        return itemQuantityMap;
    }

	public static Map<Integer, Integer> getMissingTransportEdgeItemIdsWithQuantities(
		List<Rs2TransportEdge> transports)
	{
		return getMissingTransportEdgeLoadout(transports).getWithdrawals();
	}

	/**
	 * Build one atomic preparation contract for the exact selected edges.
	 *
	 * <p>Rune quantities include the inventory, rune pouch, equipped providers and combination runes.
	 * Bank rune quantities are kept separate from raw bank stacks because a semantic contribution
	 * still has to resolve to a concrete item that can actually be withdrawn.</p>
	 */
	public static Rs2TransportLoadout getMissingTransportEdgeLoadout(
		List<Rs2TransportEdge> transports)
	{
		Map<Integer, Integer> carriedRunes = runeQuantities(
			RuneFilter.builder().includeBank(false).build());
		Map<Integer, Integer> bankRunes = runeQuantities(RuneFilter.builder()
			.includeInventory(false)
			.includeEquipment(false)
			.includeRunePouch(false)
			.includeBank(true)
			.build());
		return getMissingTransportEdgeLoadout(
			transports,
			Rs2Bank::count,
			Rs2WalkerBankingPlanner::carriedItemQuantity,
			itemId -> carriedRunes.getOrDefault(itemId, carriedItemQuantity(itemId)),
			itemId -> bankRunes.getOrDefault(itemId, safeQuantity(Rs2Bank::count, itemId)),
			Rs2Equipment::isWearing);
	}

	/** Compatibility view for callers that only consume withdrawals. */
	static Map<Integer, Integer> getMissingTransportEdgeItemIdsWithQuantities(
		List<Rs2TransportEdge> transports,
		IntUnaryOperator bankQuantityProvider,
		IntUnaryOperator carriedQuantityProvider)
	{
		return getMissingTransportEdgeLoadout(
			transports,
			bankQuantityProvider,
			carriedQuantityProvider,
			carriedQuantityProvider,
			bankQuantityProvider,
			ignored -> false).getWithdrawals();
	}

	/** Pure source-aware selection seam used by banking regressions. */
	static Rs2TransportLoadout getMissingTransportEdgeLoadout(
		List<Rs2TransportEdge> transports,
		IntUnaryOperator bankQuantityProvider,
		IntUnaryOperator carriedQuantityProvider,
		IntUnaryOperator carriedRequirementQuantityProvider,
		IntUnaryOperator bankRequirementQuantityProvider,
		IntPredicate equippedItemProvider)
	{
		if (transports == null)
		{
			return Rs2TransportLoadout.empty();
		}
		Map<Integer, Integer> withdrawals = new LinkedHashMap<>();
		LinkedHashSet<Integer> equipmentItemIds = new LinkedHashSet<>();
		for (Rs2TransportEdge transport : transports)
		{
			if (isSpellTransport(transport) && transport.getItemRequirements().isEmpty())
			{
				for (Map.Entry<Integer, Integer> rune : getSpellRuneRequirements(transport).entrySet())
				{
					int bankQuantity = safeQuantity(bankQuantityProvider, rune.getKey());
					if (bankQuantity < rune.getValue())
					{
						return Rs2TransportLoadout.unavailable();
					}
					withdrawals.merge(rune.getKey(), rune.getValue(), Integer::sum);
				}
				continue;
			}

			if (isCurrencyBasedTransport(transport.getType())
				&& transport.getCurrencyAmount() > 0
				&& transport.getItemRequirements().isEmpty())
			{
				int currencyItemId = getCurrencyItemId(transport.getCurrencyName());
				if (currencyItemId > 0)
				{
					withdrawals.merge(
						currencyItemId, transport.getCurrencyAmount(), Integer::sum);
				}
				continue;
			}

			List<Rs2TransportItemRequirement> requirements = transport.getItemRequirements();
			if (transport.getType() == Rs2TransportType.FAIRY_RING
				&& requirements.isEmpty()
				&& Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) != 1)
			{
				requirements = List.of(new Rs2TransportItemRequirement(Map.of(
					ItemID.DRAMEN_STAFF, 1,
					ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF, 1)));
			}

			Rs2TransportItemRequirement.ProviderSelection providers =
				Rs2TransportItemRequirement.selectEquipmentProviders(
					requirements,
					itemId -> safeSum(
						safeQuantity(carriedRequirementQuantityProvider, itemId),
						safeQuantity(bankRequirementQuantityProvider, itemId)),
					itemId -> safeSum(
						safeQuantity(carriedQuantityProvider, itemId),
						safeQuantity(bankQuantityProvider, itemId)) > 0,
					itemId -> safeSum(
						safeQuantity(carriedQuantityProvider, itemId),
						safeQuantity(bankQuantityProvider, itemId)) > 0)
					.orElse(null);
			if (providers == null)
			{
				return Rs2TransportLoadout.unavailable();
			}
			if (!addProviderPreparation(
				providers.getStaffItemId(), withdrawals, equipmentItemIds,
				bankQuantityProvider, carriedQuantityProvider, equippedItemProvider)
				|| !addProviderPreparation(
				providers.getOffhandItemId(), withdrawals, equipmentItemIds,
				bankQuantityProvider, carriedQuantityProvider, equippedItemProvider))
			{
				return Rs2TransportLoadout.unavailable();
			}

			for (Rs2TransportItemRequirement requirement : requirements)
			{
				if (requirement.isSatisfiedBy(carriedRequirementQuantityProvider)
					|| requirement.getStaffAlternatives().contains(providers.getStaffItemId())
					|| requirement.getOffhandAlternatives().contains(providers.getOffhandItemId()))
				{
					continue;
				}
				if (!addPreferredRequirement(
					withdrawals,
					requirement.getAlternatives(),
					transport.getType(),
					bankQuantityProvider,
					carriedRequirementQuantityProvider))
				{
					return Rs2TransportLoadout.unavailable();
				}
			}
		}
		if (withdrawals.isEmpty() && equipmentItemIds.isEmpty())
		{
			return Rs2TransportLoadout.empty();
		}
		return new Rs2TransportLoadout(
			withdrawals, new ArrayList<>(equipmentItemIds), true);
	}

	private static boolean addProviderPreparation(
		int itemId,
		Map<Integer, Integer> withdrawals,
		Set<Integer> equipmentItemIds,
		IntUnaryOperator bankQuantityProvider,
		IntUnaryOperator carriedQuantityProvider,
		IntPredicate equippedItemProvider)
	{
		if (itemId <= 0 || equippedItemProvider.test(itemId))
		{
			return true;
		}
		equipmentItemIds.add(itemId);
		if (safeQuantity(carriedQuantityProvider, itemId) > 0)
		{
			return true;
		}
		if (safeQuantity(bankQuantityProvider, itemId) <= 0)
		{
			return false;
		}
		withdrawals.merge(itemId, 1, Math::max);
		return true;
	}

	private static boolean addPreferredRequirement(
		Map<Integer, Integer> requested,
		Map<Integer, Integer> alternatives,
		Rs2TransportType transportType,
		IntUnaryOperator bankQuantityProvider,
		IntUnaryOperator carriedQuantityProvider)
	{
		Integer preferredItemId = null;
		int preferredBankQuantity = 0;
		int preferredDeficit = Integer.MAX_VALUE;
		for (Map.Entry<Integer, Integer> alternative : alternatives.entrySet())
		{
			int itemId = alternative.getKey();
			int requiredQuantity = alternative.getValue();
			if (requiredQuantity == 0)
			{
				continue;
			}
			int bankQuantity = safeQuantity(bankQuantityProvider, itemId);
			int deficit = Math.max(0,
				requiredQuantity - safeQuantity(carriedQuantityProvider, itemId));
			boolean satisfies = bankQuantity >= deficit;
			boolean preferredSatisfies = preferredItemId != null
				&& preferredBankQuantity >= preferredDeficit;
			if (preferredItemId == null
				|| satisfies && !preferredSatisfies
				|| satisfies == preferredSatisfies && deficit < preferredDeficit
				|| satisfies == preferredSatisfies && deficit == preferredDeficit
					&& bankQuantity > preferredBankQuantity)
			{
				preferredItemId = itemId;
				preferredBankQuantity = bankQuantity;
				preferredDeficit = deficit;
			}
		}

		if (preferredItemId == null)
		{
			return false;
		}
		if (preferredBankQuantity < preferredDeficit)
		{
			PurchasableItemCatalog.PurchasableItem purchasable = alternatives.keySet().stream()
				.map(PurchasableItemCatalog::byItemId)
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.orElse(null);
			int currencyItemId = purchasable == null
				? -1 : getCurrencyItemId(purchasable.costCurrencyName);
			if (currencyItemId > 0)
			{
				int requiredQuantity = alternatives.get(purchasable.itemId);
				int itemsNeeded = isCurrencyBasedTransport(transportType)
					? 1 : requiredQuantity;
				requested.merge(
					currencyItemId, purchasable.costAmount * itemsNeeded, Integer::sum);
				return true;
			}
			return false;
		}
		if (preferredDeficit > 0)
		{
			requested.merge(preferredItemId, preferredDeficit, Integer::sum);
		}
		return true;
	}

	private static Map<Integer, Integer> runeQuantities(RuneFilter filter)
	{
		Map<Integer, Integer> quantities = new HashMap<>();
		Rs2Magic.getRunes(filter).forEach((rune, quantity) ->
			quantities.put(rune.getItemId(), quantity));
		return quantities;
	}

	private static int safeSum(int first, int second)
	{
		long sum = (long) Math.max(0, first) + Math.max(0, second);
		return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
	}

	private static int safeQuantity(IntUnaryOperator provider, int itemId)
	{
		try
		{
			return Math.max(0, provider.applyAsInt(itemId));
		}
		catch (Exception exception)
		{
			log.debug("Could not check bank for item {}: {}", itemId, exception.getMessage());
			return 0;
		}
	}

    private static int carriedItemQuantity(int itemId) {
        int quantity = Rs2Inventory.itemQuantity(itemId);
        net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel equipped = Rs2Equipment.get(itemId);
        if (equipped != null) {
            quantity += Math.max(1, equipped.getQuantity());
        }
        return quantity;
    }

	private static int carriedRequirementItemQuantity(int itemId)
	{
		Runes rune = Runes.byItemId(itemId);
		if (rune == null)
		{
			return carriedItemQuantity(itemId);
		}
		return Rs2Magic.getRunes().getOrDefault(rune, 0);
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
            Rs2RouteResult directRoute = planRoute(
                    startPoint, target, false, Rs2RouteRequest.Purpose.BANK_ROUTE_DIRECT);
            List<WorldPoint> directPath = directRoute.getPath();
            List<Rs2RouteStep> directRouteSteps = directRoute.getSteps();
            long directPathEndTime = System.nanoTime();
            double directPathTimeMs = (directPathEndTime - directPathStartTime) / 1_000_000.0;

            int directDistance = Rs2Walker.getTotalTilesFromPath(directPath, target);
            performanceLog.append("\t-Direct path calculation: ").append(String.format("%.2f ms", directPathTimeMs))
                    .append(" (").append(directPath.size()).append(" waypoints, ").append(directDistance).append(" tiles)\n");

            BankLocation nearestBank = null;
            List<WorldPoint> pathToBank = new ArrayList<>();
            List<Rs2RouteStep> routeToBankSteps = List.of();
            List<WorldPoint> pathFromBankToTarget = new ArrayList<>();
            List<Rs2RouteStep> routeFromBankSteps = List.of();
            int bankingRouteDistance = -1;

            try {
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
                        Rs2RouteResult bankRoute = planRoute(
                                startPoint, bankLocation, false,
                                Rs2RouteRequest.Purpose.BANK_ROUTE_TO_BANK);
                        pathToBank = bankRoute.getPath();
                        routeToBankSteps = bankRoute.getSteps();
                        long pathToBankEndTime = System.nanoTime();
                        double pathToBankTimeMs = (pathToBankEndTime - pathToBankStartTime) / 1_000_000.0;
                        int distanceToBank = Rs2Walker.getTotalTilesFromPath(pathToBank, bankLocation);

                        long pathFromBankStartTime = System.nanoTime();
                        Rs2RouteResult bankTargetRoute = planRoute(
                                bankLocation, target, true,
                                Rs2RouteRequest.Purpose.BANK_ROUTE_FROM_BANK);
                        pathFromBankToTarget = bankTargetRoute.getPath();
                        routeFromBankSteps = bankTargetRoute.getSteps();
                        long pathFromBankEndTime = System.nanoTime();
                        double pathFromBankTimeMs = (pathFromBankEndTime - pathFromBankStartTime) / 1_000_000.0;
                        List<Rs2TransportEdge> bankLegTransports = bankTargetRoute.getTransportSteps().stream()
                                .map(Rs2RouteStep::getTransport)
                                .map(transport -> transport.orElseThrow(
                                        () -> new IllegalStateException("transport step has no edge")))
                                .collect(Collectors.toList());
                        long spellCount = bankLegTransports.stream()
                                .filter(t -> t.getType() == Rs2TransportType.TELEPORTATION_SPELL)
                                .count();
                        long itemCount = bankLegTransports.stream()
                                .filter(t -> t.getType() == Rs2TransportType.TELEPORTATION_ITEM)
                                .count();
                        int distanceFromBankRaw = Rs2Walker.getTotalTilesFromPath(pathFromBankToTarget, target);
						int distanceFromBank = effectiveDistanceFromBank(
								pathFromBankToTarget, bankTargetRoute.getSteps(), distanceFromBankRaw);

                        performanceLog.append("\t-Path to bank calculation: ").append(String.format("%.2f ms", pathToBankTimeMs))
                                .append(" (").append(pathToBank.size()).append(" waypoints, ").append(distanceToBank).append(" tiles)\n");
                        performanceLog.append("\t-Path from bank to target with banked items: ").append(String.format("%.2f ms", pathFromBankTimeMs))
                                .append(" (").append(pathFromBankToTarget.size()).append(" waypoints, ").append(distanceFromBank).append(" tiles)\n");
                        performanceLog.append("\t-Bank leg transports: total=").append(bankLegTransports.size())
                                .append(" spells=").append(spellCount)
                                .append(" items=").append(itemCount)
                                .append("\n");
                        Rs2TransportEdge firstSpellTransport = bankLegTransports.stream()
                                .filter(t -> t.getType() == Rs2TransportType.TELEPORTATION_SPELL)
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
                        "Direct route only (banking route unavailable)", directDistance, -1,
                        directRouteSteps, List.of(), List.of());
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
                    directDistance, bankingRouteDistance,
                    directRouteSteps, routeToBankSteps, routeFromBankSteps);
        } catch (Exception e) {
            long totalEndTime = System.nanoTime();
            double totalTimeMs = (totalEndTime - totalStartTime) / 1_000_000.0;
            performanceLog.append("ERROR after ").append(String.format("%.2f ms", totalTimeMs)).append(": ").append(e.getMessage()).append("\n");
            WebWalkLog.compareDetail(performanceLog.toString());
            WebWalkLog.compareError(totalTimeMs, target, e.getMessage());
            return new TransportRouteAnalysis(new ArrayList<>(), null, null, new ArrayList<>(), new ArrayList<>(), "Error calculating routes: " + e.getMessage());
        }
    }

	private static Rs2RouteResult planRoute(
		WorldPoint start,
		WorldPoint target,
		boolean useBankItems,
		Rs2RouteRequest.Purpose purpose)
	{
		return Rs2PathApi.plan(
			Rs2RouteRequest.to(start, target)
				.withRefreshTarget(target)
				.withBankItems(useBankItems)
				.withPurpose(purpose));
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

	private static Map<Integer, Integer> getSpellRuneRequirements(Rs2TransportEdge transport)
	{
		Map<Integer, Integer> runeRequirements = new HashMap<>();
		if (!isSpellTransport(transport) || transport.getDisplayInfo() == null)
		{
			return runeRequirements;
		}
		try
		{
			Rs2Spells rs2Spell = Rs2Magic.getRs2Spell(spellLookupName(transport.getDisplayInfo()));
			if (rs2Spell == null)
			{
				return runeRequirements;
			}
			Rs2Magic.getRequiredRunes(rs2Spell, 1, true).forEach((rune, quantity) ->
				runeRequirements.put(rune.getItemId(), quantity));
		}
		catch (Exception exception)
		{
			log.warn("Error getting spell rune requirements for transport '{}': {}",
				transport.getDisplayInfo(), exception.getMessage());
		}
		return runeRequirements;
	}

    /** Package-private so the planning tests can select the same rows this collector accepts. */
    static boolean isCurrencyBasedTransport(TransportType transportType) {
        return transportType == TransportType.BOAT
                || transportType == TransportType.CHARTER_SHIP
                || transportType == TransportType.SHIP
                || transportType == TransportType.MINECART
                || transportType == TransportType.MAGIC_CARPET
                || transportType == TransportType.TRANSPORT;
    }

	static boolean isCurrencyBasedTransport(Rs2TransportType transportType)
	{
		return transportType == Rs2TransportType.BOAT
			|| transportType == Rs2TransportType.CHARTER_SHIP
			|| transportType == Rs2TransportType.SHIP
			|| transportType == Rs2TransportType.MINECART
			|| transportType == Rs2TransportType.MAGIC_CARPET
			|| transportType == Rs2TransportType.TRANSPORT;
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
	static int effectiveDistanceFromBank(
			List<WorldPoint> pathFromBankToTarget,
			List<Rs2RouteStep> routeSteps,
			int rawDistance) {
		if (pathFromBankToTarget == null || pathFromBankToTarget.isEmpty() || rawDistance == Integer.MAX_VALUE) {
			return rawDistance;
		}
		if (routeSteps == null || routeSteps.isEmpty()) {
			return rawDistance;
		}

		int firstTransportStep = -1;
		Rs2TransportEdge firstTransport = null;
		for (int i = 0; i < routeSteps.size(); i++) {
			Rs2RouteStep step = routeSteps.get(i);
			if (step != null && step.isTransport()) {
				firstTransportStep = i;
				firstTransport = step.getTransport().orElse(null);
				break;
			}
		}
		if (firstTransport == null) {
			return rawDistance;
		}

		// Use the exact first transport selected by this route. Endpoint rematching is ambiguous when
		// multiple catalog entries share an origin/destination pair and could score the wrong command.
		int modeledDistance = transportModeledDistance(
				pathFromBankToTarget, firstTransportStep, firstTransport, rawDistance);
        if (modeledDistance == Integer.MAX_VALUE) {
            return rawDistance;
        }
        return Math.min(rawDistance, modeledDistance);
    }

	private static boolean isImmediateBankTeleport(Rs2TransportEdge transport) {
		if (transport == null || transport.getOrigin() != null) {
			return false;
		}
		return transport.getType() == Rs2TransportType.TELEPORTATION_ITEM
				|| transport.getType() == Rs2TransportType.TELEPORTATION_SPELL;
	}

	private static int transportModeledDistance(
			List<WorldPoint> pathFromBankToTarget,
			int transportStepIndex,
			Rs2TransportEdge transport,
			int fallbackRawDistance) {
		if (transport == null || pathFromBankToTarget == null || pathFromBankToTarget.isEmpty()
				|| transportStepIndex < 0 || transportStepIndex >= pathFromBankToTarget.size() - 1) {
			return fallbackRawDistance;
		}

		WorldPoint stepOrigin = pathFromBankToTarget.get(transportStepIndex);
		WorldPoint stepDestination = pathFromBankToTarget.get(transportStepIndex + 1);
		if (!stepDestination.equals(transport.getDestination())) {
			return fallbackRawDistance;
		}
		if (!isImmediateBankTeleport(transport)
				&& transport.getOrigin() != null
				&& !stepOrigin.equals(transport.getOrigin())) {
			return fallbackRawDistance;
		}

		int destinationIndex = transportStepIndex + 1;
		int walkToTransport = isImmediateBankTeleport(transport) ? 0 : transportStepIndex;
        int transportHop = 1;
        int postTransportTail = Math.max(0, pathFromBankToTarget.size() - destinationIndex);
        return walkToTransport + transportHop + postTransportTail;
    }
}
