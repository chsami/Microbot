package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionSnapshot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveEdgeSource;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TileHandler extends AgentHandler {

	public TileHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return "/tile";
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
		WorldPoint tile = new WorldPoint(
				PathHandler.requiredInt(params, "x"),
				PathHandler.requiredInt(params, "y"),
				PathHandler.optionalInt(params, "plane", 0));

		PathfinderConfig config = Rs2PathApi.getPathfinderConfig();
		if (config == null || config.getMap() == null) {
			sendJson(exchange, 503, errorResponse("Pathfinder unavailable: shortest-path configuration is not initialized"));
			return;
		}

		Map<String, Object> result = describe(tile, config.getMap(), config.getLiveCollisionOverlay().current());
		result.put("liveCollisionEnabled", config.getLiveCollisionOverlay().isEnabled());
		if (Microbot.isLoggedIn()) {
			result.put("objects", Microbot.getRs2TileObjectCache().query()
					.where(o -> tile.equals(o.getWorldLocation()))
					.toListOnClientThread().stream()
					.map(ObjectHandler::serializeObject)
					.collect(Collectors.toList()));
		}
		sendJson(exchange, 200, result);
	}

	static Map<String, Object> describe(WorldPoint tile, CollisionMap map, LiveEdgeSource live) {
		int x = tile.getX();
		int y = tile.getY();
		int z = tile.getPlane();

		Map<String, Object> edges = new LinkedHashMap<>();
		edges.put("north", edge(map, live, x, y, z, LiveCollisionSnapshot.FLAG_NORTH));
		edges.put("east", edge(map, live, x, y, z, LiveCollisionSnapshot.FLAG_EAST));
		edges.put("south", edge(map, live, x, y - 1, z, LiveCollisionSnapshot.FLAG_NORTH));
		edges.put("west", edge(map, live, x - 1, y, z, LiveCollisionSnapshot.FLAG_EAST));

		boolean blocked = true;
		List<String> conflicts = new ArrayList<>();
		for (Map.Entry<String, Object> entry : edges.entrySet()) {
			@SuppressWarnings("unchecked")
			Map<String, Object> edge = (Map<String, Object>) entry.getValue();
			if (Boolean.TRUE.equals(edge.get("effective"))) {
				blocked = false;
			}
			if (Boolean.TRUE.equals(edge.get("conflict"))) {
				conflicts.add(entry.getKey());
			}
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("tile", PathHandler.position(tile));
		result.put("regionMapped", map.hasRegion(x, y));
		result.put("blocked", blocked);
		result.put("conflicts", conflicts);
		result.put("canMove", edges);
		return result;
	}

	private static Map<String, Object> edge(CollisionMap map, LiveEdgeSource live, int x, int y, int z, int flag) {
		boolean stat = map.staticEdge(x, y, z, flag);
		Boolean liveEdge = live == null ? null : live.edge(x, y, z, flag);
		Map<String, Object> edge = new LinkedHashMap<>();
		edge.put("static", stat);
		edge.put("live", liveEdge);
		edge.put("effective", liveEdge != null ? liveEdge : stat);
		edge.put("conflict", liveEdge != null && liveEdge != stat);
		return edge;
	}
}
