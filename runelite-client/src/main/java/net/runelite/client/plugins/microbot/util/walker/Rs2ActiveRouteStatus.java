package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, planner-independent snapshot of the route currently owned by the walker.
 *
 * <p>The generation identifies one published calculation without exposing the concrete planner.
 * Callers that wait asynchronously can therefore reject a result when a newer route replaced the
 * one they observed.</p>
 */
public final class Rs2ActiveRouteStatus
{
	public enum Phase
	{
		ABSENT,
		CALCULATING,
		READY
	}

	private final long generation;
	private final Phase phase;
	private final WorldPoint start;
	private final Set<WorldPoint> targets;
	private final List<WorldPoint> rawPath;
	private final List<WorldPoint> walkablePath;
	private final Rs2RouteTermination terminationReason;
	private final Rs2RouteMetrics metrics;

	Rs2ActiveRouteStatus(
		long generation,
		Phase phase,
		WorldPoint start,
		Set<WorldPoint> targets,
		List<WorldPoint> rawPath,
		List<WorldPoint> walkablePath,
		Rs2RouteTermination terminationReason,
		Rs2RouteMetrics metrics)
	{
		if (generation < 0)
		{
			throw new IllegalArgumentException("generation must be non-negative");
		}
		this.generation = generation;
		this.phase = Objects.requireNonNull(phase, "phase");
		this.start = start;
		this.targets = targets == null
			? Collections.emptySet()
			: Collections.unmodifiableSet(new LinkedHashSet<>(targets));
		this.rawPath = rawPath == null ? Collections.emptyList() : List.copyOf(rawPath);
		this.walkablePath = walkablePath == null
			? Collections.emptyList()
			: List.copyOf(walkablePath);
		this.terminationReason = terminationReason;
		this.metrics = metrics;
		validatePhase();
	}

	private void validatePhase()
	{
		if (phase == Phase.ABSENT
			&& (start != null || !targets.isEmpty() || !rawPath.isEmpty() || !walkablePath.isEmpty()
				|| terminationReason != null || metrics != null))
		{
			throw new IllegalArgumentException("an absent route cannot carry planner state");
		}
		if (phase == Phase.READY && (terminationReason == null || metrics == null))
		{
			throw new IllegalArgumentException("a ready route must include termination and metrics");
		}
		if (phase == Phase.CALCULATING && (terminationReason != null || metrics != null))
		{
			throw new IllegalArgumentException("a calculating route cannot include completed search state");
		}
	}

	static Rs2ActiveRouteStatus absent(long generation)
	{
		return new Rs2ActiveRouteStatus(
			generation, Phase.ABSENT, null, Collections.emptySet(),
			Collections.emptyList(), Collections.emptyList(), null, null);
	}

	public long getGeneration()
	{
		return generation;
	}

	public Phase getPhase()
	{
		return phase;
	}

	public boolean isPresent()
	{
		return phase != Phase.ABSENT;
	}

	public boolean isCalculating()
	{
		return phase == Phase.CALCULATING;
	}

	public boolean isReady()
	{
		return phase == Phase.READY;
	}

	public Optional<WorldPoint> getStart()
	{
		return Optional.ofNullable(start);
	}

	public Set<WorldPoint> getTargets()
	{
		return targets;
	}

	public List<WorldPoint> getRawPath()
	{
		return rawPath;
	}

	public List<WorldPoint> getWalkablePath()
	{
		return walkablePath;
	}

	public Optional<WorldPoint> getEndpoint()
	{
		return rawPath.isEmpty()
			? Optional.empty()
			: Optional.of(rawPath.get(rawPath.size() - 1));
	}

	public Optional<Rs2RouteTermination> getTerminationReason()
	{
		return Optional.ofNullable(terminationReason);
	}

	public Optional<Rs2RouteMetrics> getMetrics()
	{
		return Optional.ofNullable(metrics);
	}
}
