package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2ActiveRouteStatus;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteMetrics;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteResult;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteStep;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportEdge;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PathHandler extends AgentHandler {

	private static final String BASE_PATH = "/path";

	public PathHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return BASE_PATH;
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		try {
			requireGet(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		Map<String, String> params = parseQuery(exchange.getRequestURI());
		boolean full = "true".equalsIgnoreCase(params.get("full")) || "1".equals(params.get("full"));
		String sub = getSubPath(exchange, BASE_PATH);
		switch (sub) {
			case "":
			case "/":
				handlePlan(exchange, params, full);
				break;
			case "/active":
				sendJson(exchange, 200, serializeActive(Rs2PathApi.getActiveRouteStatus(), full));
				break;
			default:
				sendJson(exchange, 404, errorResponse("Unknown endpoint: " + BASE_PATH + sub));
		}
	}

	private void handlePlan(HttpExchange exchange, Map<String, String> params, boolean full) throws IOException {
		WorldPoint target = new WorldPoint(requiredInt(params, "x"), requiredInt(params, "y"), optionalInt(params, "plane", 0));

		WorldPoint start;
		if (params.containsKey("fromX") || params.containsKey("fromY")) {
			start = new WorldPoint(requiredInt(params, "fromX"), requiredInt(params, "fromY"), optionalInt(params, "fromPlane", 0));
		} else {
			start = Microbot.getRs2PlayerStateCache().getLocalPlayerPosition();
			if (start == null) {
				sendJson(exchange, 409, errorResponse("Player position unknown; pass fromX and fromY"));
				return;
			}
		}

		Rs2RouteRequest request = Rs2RouteRequest.to(start, target);
		String bankItems = params.get("bankItems");
		if (bankItems != null) {
			request = request.withBankItems(Boolean.parseBoolean(bankItems));
		}

		Rs2RouteResult route;
		try {
			route = Rs2PathApi.plan(request);
		} catch (IllegalStateException e) {
			sendJson(exchange, 503, errorResponse("Pathfinder unavailable: " + e.getMessage()));
			return;
		}
		sendJson(exchange, 200, serializeRoute(route, target, full));
	}

	static Map<String, Object> serializeRoute(Rs2RouteResult route, WorldPoint target, boolean full) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("start", position(route.getStart()));
		result.put("target", position(target));
		result.put("searchCompleted", route.isSearchCompleted());
		result.put("termination", route.getTerminationReason() == null ? null : route.getTerminationReason().name());
		result.put("reachesTarget", route.isTargetReached(0));
		route.getEndpoint().ifPresent(endpoint -> {
			result.put("endpoint", position(endpoint));
			result.put("endpointDistanceToTarget", endpoint.distanceTo(target));
		});
		result.put("tiles", route.getPath().size());
		result.put("metrics", serializeMetrics(route.getMetrics()));
		result.put("transports", route.getTransportSteps().stream()
				.map(PathHandler::serializeTransport)
				.collect(Collectors.toList()));
		if (full) {
			result.put("path", positions(route.getPath()));
		}
		return result;
	}

	static Map<String, Object> serializeActive(Rs2ActiveRouteStatus status, boolean full) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("phase", status.getPhase().name());
		result.put("generation", status.getGeneration());
		if (!status.isPresent()) {
			return result;
		}
		status.getStart().ifPresent(start -> result.put("start", position(start)));
		result.put("targets", positions(status.getTargets()));
		status.getEndpoint().ifPresent(endpoint -> result.put("endpoint", position(endpoint)));
		status.getTerminationReason().ifPresent(reason -> result.put("termination", reason.name()));
		result.put("rawTiles", status.getRawPath().size());
		result.put("walkableTiles", status.getWalkablePath().size());
		status.getMetrics().ifPresent(metrics -> result.put("metrics", serializeMetrics(metrics)));
		if (full) {
			result.put("walkablePath", positions(status.getWalkablePath()));
		}
		return result;
	}

	private static Map<String, Object> serializeMetrics(Rs2RouteMetrics metrics) {
		Map<String, Object> map = new LinkedHashMap<>();
		if (metrics == null) {
			return map;
		}
		if (metrics.hasSearchNanos()) map.put("searchMs", metrics.getSearchNanos() / 1_000_000);
		if (metrics.hasPathCost()) map.put("pathCost", metrics.getPathCost());
		if (metrics.hasNodesChecked()) map.put("nodesChecked", metrics.getNodesChecked());
		if (metrics.hasTransportsChecked()) map.put("transportsChecked", metrics.getTransportsChecked());
		return map;
	}

	private static Map<String, Object> serializeTransport(Rs2RouteStep step) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("from", position(step.getFrom()));
		map.put("to", position(step.getTo()));
		step.getTransport().ifPresent(edge -> putEdge(map, edge));
		return map;
	}

	private static void putEdge(Map<String, Object> map, Rs2TransportEdge edge) {
		map.put("type", edge.getType() == null ? null : edge.getType().name());
		map.put("info", edge.getDisplayInfo());
		map.put("action", edge.getAction());
		map.put("target", edge.getTarget());
		map.put("objectId", edge.getObjectId());
		map.put("teleport", edge.isTeleport());
		map.put("consumable", edge.isConsumable());
	}

	private static List<Map<String, Integer>> positions(Collection<WorldPoint> points) {
		return points.stream().map(PathHandler::position).collect(Collectors.toList());
	}

	static Map<String, Integer> position(WorldPoint point) {
		if (point == null) {
			return null;
		}
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("x", point.getX());
		map.put("y", point.getY());
		map.put("plane", point.getPlane());
		return map;
	}

	static int requiredInt(Map<String, String> params, String key) {
		String val = params.get(key);
		if (val == null || val.isEmpty()) {
			throw new BadRequestException("Missing required parameter: " + key);
		}
		return parseInt(key, val);
	}

	static int optionalInt(Map<String, String> params, String key, int defaultValue) {
		String val = params.get(key);
		return val == null || val.isEmpty() ? defaultValue : parseInt(key, val);
	}

	private static int parseInt(String key, String val) {
		try {
			return Integer.parseInt(val.trim());
		} catch (NumberFormatException e) {
			throw new BadRequestException("Parameter '" + key + "' must be a number");
		}
	}
}
