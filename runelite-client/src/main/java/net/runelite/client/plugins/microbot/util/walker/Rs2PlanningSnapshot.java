package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Immutable engine-neutral graph inputs captured after Microbot has resolved runtime admission.
 *
 * <p>The static collision archive is pinned separately and shared by both engines. This value owns the
 * per-search overlays and already-filtered exact transport catalog, so an engine never reads mutable
 * Microbot plugin state while searching.</p>
 */
public final class Rs2PlanningSnapshot
{
	@FunctionalInterface
	public interface CollisionOverride
	{
		/** Known north/east edge value, or {@code null} to fall back to pinned static collision. */
		Boolean edge(int x, int y, int plane, int flag);
	}

	private static final int DANGEROUS_TILE_PENALTY = 100;
	private static final CollisionOverride NO_COLLISION_OVERRIDE = (x, y, plane, flag) -> null;

	private final Rs2RoutePolicy policy;
	private final List<Rs2TransportEdge> admittedTransports;
	private final CollisionOverride collisionOverride;
	private final Set<Long> blockedWalkingEdges;
	private final IntPredicate dangerousTilePredicate;

	Rs2PlanningSnapshot(
		Rs2RoutePolicy policy,
		List<Rs2TransportEdge> admittedTransports,
		CollisionOverride collisionOverride,
		Set<Long> blockedWalkingEdges,
		IntPredicate dangerousTilePredicate)
	{
		this.policy = Objects.requireNonNull(policy, "policy");
		this.admittedTransports = List.copyOf(admittedTransports);
		this.collisionOverride = collisionOverride == null
			? NO_COLLISION_OVERRIDE : collisionOverride;
		this.blockedWalkingEdges = Collections.unmodifiableSet(
			new LinkedHashSet<>(blockedWalkingEdges));
		this.dangerousTilePredicate = Objects.requireNonNull(
			dangerousTilePredicate, "dangerousTilePredicate");
	}

	public Rs2RoutePolicy getPolicy()
	{
		return policy;
	}

	public List<Rs2TransportEdge> getAdmittedTransports()
	{
		return admittedTransports;
	}

	public Boolean collisionOverride(int x, int y, int plane, int flag)
	{
		return collisionOverride.edge(x, y, plane, flag);
	}

	public boolean isWalkingEdgeBlocked(int originPacked, int destinationPacked)
	{
		if (blockedWalkingEdges.isEmpty())
		{
			return false;
		}
		if (blockedWalkingEdges.contains(edgeKey(originPacked, destinationPacked)))
		{
			return true;
		}
		int ox = unpackX(originPacked);
		int oy = unpackY(originPacked);
		int oz = unpackPlane(originPacked);
		int dx = Integer.signum(unpackX(destinationPacked) - ox);
		int dy = Integer.signum(unpackY(destinationPacked) - oy);
		if (unpackPlane(destinationPacked) != oz || dx == 0 || dy == 0)
		{
			return false;
		}
		int xThenY = pack(ox + dx, oy, oz);
		int yThenX = pack(ox, oy + dy, oz);
		return blockedWalkingEdges.contains(edgeKey(originPacked, xThenY))
			|| blockedWalkingEdges.contains(edgeKey(xThenY, destinationPacked))
			|| blockedWalkingEdges.contains(edgeKey(originPacked, yThenX))
			|| blockedWalkingEdges.contains(edgeKey(yThenX, destinationPacked));
	}

	public int getAdditionalWalkingCost(int packedDestination, Set<WorldPoint> targets)
	{
		if (!policy.isAvoidDangerousNpcs() || containsPacked(targets, packedDestination))
		{
			return 0;
		}
		return dangerousTilePredicate.test(packedDestination) ? DANGEROUS_TILE_PENALTY : 0;
	}

	private static boolean containsPacked(Set<WorldPoint> points, int packed)
	{
		for (WorldPoint point : points)
		{
			if (pack(point.getX(), point.getY(), point.getPlane()) == packed)
			{
				return true;
			}
		}
		return false;
	}

	private static long edgeKey(int originPacked, int destinationPacked)
	{
		return ((long) originPacked << 32) ^ (destinationPacked & 0xffffffffL);
	}

	private static int pack(int x, int y, int plane)
	{
		return (x & 0x7FFF) | ((y & 0x7FFF) << 15) | ((plane & 0x3) << 30);
	}

	private static int unpackX(int packed)
	{
		return packed & 0x7FFF;
	}

	private static int unpackY(int packed)
	{
		return (packed >> 15) & 0x7FFF;
	}

	private static int unpackPlane(int packed)
	{
		return (packed >> 30) & 0x3;
	}
}
