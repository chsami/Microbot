package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable result of a synchronous Microbot route calculation. */
public final class Rs2RouteResult
{
	private final WorldPoint start;
	private final Set<WorldPoint> targets;
	private final List<WorldPoint> path;
	private final List<Rs2RouteStep> steps;
	private final Map<WorldPoint, Map<WorldPoint, Rs2TransportEdge>> transportEdgesByEndpoints;
	private final Rs2RouteTermination terminationReason;
	private final Rs2RouteMetrics metrics;

	Rs2RouteResult(
		WorldPoint start,
		Set<WorldPoint> targets,
		List<WorldPoint> path,
		List<Rs2RouteStep> steps,
		Rs2RouteTermination terminationReason,
		Rs2RouteMetrics metrics)
	{
		this.start = start;
		this.targets = Collections.unmodifiableSet(new LinkedHashSet<>(targets));
		this.path = path == null ? Collections.emptyList() : List.copyOf(path);
		this.steps = steps == null ? Collections.emptyList() : List.copyOf(steps);
		validateSteps(this.path, this.steps);
		this.transportEdgesByEndpoints = indexTransportEdges(this.steps);
		this.terminationReason = Objects.requireNonNull(terminationReason, "terminationReason");
		this.metrics = Objects.requireNonNull(metrics, "metrics");
	}

	public WorldPoint getStart()
	{
		return start;
	}

	public Set<WorldPoint> getTargets()
	{
		return targets;
	}

	public List<WorldPoint> getPath()
	{
		return path;
	}

	public List<Rs2RouteStep> getSteps()
	{
		return steps;
	}

	public List<Rs2RouteStep> getTransportSteps()
	{
		List<Rs2RouteStep> transportSteps = new ArrayList<>();
		for (Rs2RouteStep step : steps)
		{
			if (step.isTransport())
			{
				transportSteps.add(step);
			}
		}
		return List.copyOf(transportSteps);
	}

	/** Exact selected transport for a directed route edge, if that edge is a transport. */
	public Optional<Rs2TransportEdge> getTransportEdge(WorldPoint from, WorldPoint to)
	{
		if (from == null || to == null)
		{
			return Optional.empty();
		}
		Map<WorldPoint, Rs2TransportEdge> byDestination = transportEdgesByEndpoints.get(from);
		return byDestination == null
			? Optional.empty()
			: Optional.ofNullable(byDestination.get(to));
	}

	/** Whether the search ended normally rather than through cancellation or an internal failure. */
	public boolean isSearchCompleted()
	{
		return terminationReason != Rs2RouteTermination.CANCELLED
			&& terminationReason != Rs2RouteTermination.FAILED;
	}

	public Rs2RouteTermination getTerminationReason()
	{
		return terminationReason;
	}

	public long getSearchNanos()
	{
		return metrics.getSearchNanos();
	}

	public boolean hasSearchNanos()
	{
		return metrics.hasSearchNanos();
	}

	public Rs2RouteMetrics getMetrics()
	{
		return metrics;
	}

	public Optional<WorldPoint> getEndpoint()
	{
		return path.isEmpty() ? Optional.empty() : Optional.of(path.get(path.size() - 1));
	}

	public Optional<WorldPoint> getReachedTarget(int tolerance)
	{
		if (tolerance < 0)
		{
			throw new IllegalArgumentException("tolerance must be non-negative");
		}
		Optional<WorldPoint> endpoint = getEndpoint();
		if (endpoint.isEmpty())
		{
			return Optional.empty();
		}
		WorldPoint end = endpoint.get();
		return targets.stream()
			.filter(target -> target.getPlane() == end.getPlane())
			.filter(target -> target.distanceTo2D(end) <= tolerance)
			.findFirst();
	}

	public boolean isTargetReached(int tolerance)
	{
		return getReachedTarget(tolerance).isPresent();
	}

	private static void validateSteps(List<WorldPoint> path, List<Rs2RouteStep> steps)
	{
		int expectedSteps = Math.max(0, path.size() - 1);
		if (steps.size() != expectedSteps)
		{
			throw new IllegalArgumentException(
				"route steps must describe every path edge: expected " + expectedSteps
					+ ", got " + steps.size());
		}
		for (int i = 0; i < steps.size(); i++)
		{
			Rs2RouteStep step = steps.get(i);
			if (!path.get(i).equals(step.getFrom()) || !path.get(i + 1).equals(step.getTo()))
			{
				throw new IllegalArgumentException("route step " + i + " is not contiguous with path");
			}
		}
	}

	private static Map<WorldPoint, Map<WorldPoint, Rs2TransportEdge>> indexTransportEdges(
		List<Rs2RouteStep> steps)
	{
		Map<WorldPoint, Map<WorldPoint, Rs2TransportEdge>> mutable = new LinkedHashMap<>();
		for (Rs2RouteStep step : steps)
		{
			if (!step.isTransport())
			{
				continue;
			}
			Rs2TransportEdge edge = step.getTransport().orElseThrow(IllegalStateException::new);
			mutable.computeIfAbsent(step.getFrom(), ignored -> new LinkedHashMap<>())
				.putIfAbsent(step.getTo(), edge);
		}
		Map<WorldPoint, Map<WorldPoint, Rs2TransportEdge>> immutable = new LinkedHashMap<>();
		for (Map.Entry<WorldPoint, Map<WorldPoint, Rs2TransportEdge>> entry : mutable.entrySet())
		{
			immutable.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
		}
		return Collections.unmodifiableMap(immutable);
	}
}
