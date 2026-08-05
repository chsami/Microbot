package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, engine-neutral policy resolved for one route calculation.
 *
 * <p>Graph data and the engine's static collision representation remain engine inputs; every mutable
 * Microbot routing choice that can change admission or search behavior is copied here before dispatch.
 * An upstream adapter must consume this value rather than reading {@code ShortestPathPlugin} globals.</p>
 */
public final class Rs2RoutePolicy
{
	public enum TeleportationItemMode
	{
		NONE,
		INVENTORY,
		INVENTORY_NON_CONSUMABLE
	}

	private final boolean useBankItems;
	private final boolean avoidWilderness;
	private final boolean avoidDangerousNpcs;
	private final boolean ignoreTeleportAndItems;
	private final boolean teleportsDisabled;
	private final boolean membersWorld;
	private final boolean liveCollisionEnabled;
	private final long calculationCutoffMillis;
	private final int distanceBeforeUsingTeleport;
	private final TeleportationItemMode teleportationItemMode;
	private final Set<Rs2TransportType> enabledTransportTypes;
	private final Set<WorldPoint> restrictedPoints;

	public Rs2RoutePolicy(
		boolean useBankItems,
		boolean avoidWilderness,
		boolean avoidDangerousNpcs,
		boolean ignoreTeleportAndItems,
		boolean teleportsDisabled,
		boolean membersWorld,
		boolean liveCollisionEnabled,
		long calculationCutoffMillis,
		int distanceBeforeUsingTeleport,
		TeleportationItemMode teleportationItemMode,
		Set<Rs2TransportType> enabledTransportTypes,
		Set<WorldPoint> restrictedPoints)
	{
		if (calculationCutoffMillis <= 0)
		{
			throw new IllegalArgumentException("calculationCutoffMillis must be positive");
		}
		if (distanceBeforeUsingTeleport < 0)
		{
			throw new IllegalArgumentException("distanceBeforeUsingTeleport must be non-negative");
		}
		this.useBankItems = useBankItems;
		this.avoidWilderness = avoidWilderness;
		this.avoidDangerousNpcs = avoidDangerousNpcs;
		this.ignoreTeleportAndItems = ignoreTeleportAndItems;
		this.teleportsDisabled = teleportsDisabled;
		this.membersWorld = membersWorld;
		this.liveCollisionEnabled = liveCollisionEnabled;
		this.calculationCutoffMillis = calculationCutoffMillis;
		this.distanceBeforeUsingTeleport = distanceBeforeUsingTeleport;
		this.teleportationItemMode = Objects.requireNonNull(
			teleportationItemMode, "teleportationItemMode");
		Objects.requireNonNull(enabledTransportTypes, "enabledTransportTypes");
		EnumSet<Rs2TransportType> enabledCopy = enabledTransportTypes.isEmpty()
			? EnumSet.noneOf(Rs2TransportType.class)
			: EnumSet.copyOf(enabledTransportTypes);
		this.enabledTransportTypes = Collections.unmodifiableSet(enabledCopy);
		Objects.requireNonNull(restrictedPoints, "restrictedPoints");
		LinkedHashSet<WorldPoint> restrictionCopy = new LinkedHashSet<>();
		for (WorldPoint point : restrictedPoints)
		{
			restrictionCopy.add(Objects.requireNonNull(point, "restricted point"));
		}
		this.restrictedPoints = Collections.unmodifiableSet(restrictionCopy);
	}

	public Rs2RoutePolicy withUseBankItems(boolean enabled)
	{
		return new Rs2RoutePolicy(
			enabled, avoidWilderness, avoidDangerousNpcs, ignoreTeleportAndItems,
			teleportsDisabled, membersWorld, liveCollisionEnabled, calculationCutoffMillis,
			distanceBeforeUsingTeleport, teleportationItemMode, enabledTransportTypes,
			restrictedPoints);
	}

	public boolean isUseBankItems() { return useBankItems; }
	public boolean isAvoidWilderness() { return avoidWilderness; }
	public boolean isAvoidDangerousNpcs() { return avoidDangerousNpcs; }
	public boolean isIgnoreTeleportAndItems() { return ignoreTeleportAndItems; }
	public boolean isTeleportsDisabled() { return teleportsDisabled; }
	public boolean isMembersWorld() { return membersWorld; }
	public boolean isLiveCollisionEnabled() { return liveCollisionEnabled; }
	public long getCalculationCutoffMillis() { return calculationCutoffMillis; }
	public int getDistanceBeforeUsingTeleport() { return distanceBeforeUsingTeleport; }
	public TeleportationItemMode getTeleportationItemMode() { return teleportationItemMode; }
	public Set<Rs2TransportType> getEnabledTransportTypes() { return enabledTransportTypes; }
	public Set<WorldPoint> getRestrictedPoints() { return restrictedPoints; }
}
