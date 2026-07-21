package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ObjectHandler extends AgentHandler {

	private final int defaultLimit;

	public ObjectHandler(Gson gson, int defaultLimit) {
		super(gson);
		this.defaultLimit = defaultLimit;
	}

	@Override
	public String getPath() {
		return "/objects";
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		String sub = getSubPath(exchange, "/objects");

		switch (sub) {
			case "":
			case "/":
				handleQuery(exchange);
				break;
			case "/interact":
				handleInteract(exchange);
				break;
			case "/neighbours":
			case "/neighbors":
				handleNeighbours(exchange);
				break;
			default:
				sendJson(exchange, 404, errorResponse("Unknown path: /objects" + sub));
		}
	}

	private void handleQuery(HttpExchange exchange) throws IOException {
		try {
			requireGet(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		Map<String, String> params = parseQuery(exchange.getRequestURI());
		String name = params.get("name");
		String nameContains = params.get("nameContains");
		int id = getIntParam(params, "id", -1);
		int maxDistance = getIntParam(params, "maxDistance", 20);
		int limit = getIntParam(params, "limit", defaultLimit);

		var query = Microbot.getRs2TileObjectCache().query();
		if (id >= 0) {
			query = query.withId(id);
		}
		if (name != null && !name.isEmpty()) {
			query = query.withName(name);
		} else if (nameContains != null && !nameContains.isEmpty()) {
			query = query.withNameContains(nameContains);
		}
		query = query.within(maxDistance);

		List<Rs2TileObjectModel> objects = query.toListOnClientThread();

		List<Map<String, Object>> serialized = objects.stream()
				.limit(limit)
				.map(this::serializeObject)
				.collect(Collectors.toList());

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("count", serialized.size());
		response.put("total", objects.size());
		response.put("objects", serialized);
		sendJson(exchange, 200, response);
	}

	private void handleInteract(HttpExchange exchange) throws IOException {
		try {
			requirePost(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		Map<String, Object> body;
		try {
			body = readJsonBody(exchange);
		} catch (Exception e) {
			sendJson(exchange, 400, errorResponse("Invalid JSON body"));
			return;
		}

		String action = (String) body.get("action");
		if (action == null || action.isEmpty()) {
			sendJson(exchange, 400, errorResponse("Missing required field: action"));
			return;
		}

		String name = (String) body.get("name");
		Number idNum = (Number) body.get("id");

		Rs2TileObjectModel obj = null;
		if (name != null && !name.isEmpty()) {
			obj = Microbot.getRs2TileObjectCache().query()
					.withName(name)
					.nearestOnClientThread();
		} else if (idNum != null) {
			obj = Microbot.getRs2TileObjectCache().query()
					.withId(idNum.intValue())
					.nearestOnClientThread();
		} else {
			sendJson(exchange, 400, errorResponse("Provide either name or id"));
			return;
		}

		Map<String, Object> response = new LinkedHashMap<>();
		if (obj == null) {
			response.put("success", false);
			response.put("reason", "Object not found");
		} else {
			boolean clicked = obj.click(action);
			response.put("success", clicked);
			response.put("object", serializeObject(obj));
			response.put("action", action);
		}
		sendJson(exchange, 200, response);
	}

	/**
	 * Like the plain query, but also reports the walkability of the tiles surrounding each match.
	 *
	 * <p>Exists to catalogue obstacles that sit <em>between</em> two walkable tiles — Motherlode
	 * rockfalls, rubble, rock slides. Modelling one as a transport needs both sides, which the
	 * object's own position does not give you. {@code sides} pairs opposing neighbours on each axis
	 * so a blocking obstacle's two open ends can be read straight off the response.
	 *
	 * <p>Walkability comes from the <em>live scene</em> collision flags, not the bundled collision
	 * map, so it reflects what the player can actually do right now. The flag array is read once per
	 * request rather than per tile, since each {@code Rs2Tile.isWalkable} call hops the client thread.
	 */
	private void handleNeighbours(HttpExchange exchange) throws IOException {
		try {
			requireGet(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		Map<String, String> params = parseQuery(exchange.getRequestURI());
		String name = params.get("name");
		String nameContains = params.get("nameContains");
		int id = getIntParam(params, "id", -1);
		int maxDistance = getIntParam(params, "maxDistance", 20);
		int limit = getIntParam(params, "limit", defaultLimit);
		int radius = Math.max(1, Math.min(5, getIntParam(params, "radius", 1)));

		var query = Microbot.getRs2TileObjectCache().query();
		if (id >= 0) {
			query = query.withId(id);
		}
		if (name != null && !name.isEmpty()) {
			query = query.withName(name);
		} else if (nameContains != null && !nameContains.isEmpty()) {
			query = query.withNameContains(nameContains);
		}
		query = query.within(maxDistance);

		List<Rs2TileObjectModel> objects = query.toListOnClientThread();
		List<Rs2TileObjectModel> limited = objects.stream().limit(limit).collect(Collectors.toList());

		List<Map<String, Object>> serialized = Microbot.getClientThread().runOnClientThreadOptional(() -> {
			var wv = Microbot.getClient().getTopLevelWorldView();
			if (wv == null) {
				return null;
			}
			var collisionData = wv.getCollisionMaps();
			int[][] flags = collisionData != null ? collisionData[wv.getPlane()].getFlags() : null;

			List<Map<String, Object>> out = new ArrayList<>();
			for (Rs2TileObjectModel obj : limited) {
				Map<String, Object> map = serializeObject(obj);
				WorldPoint loc = obj.getWorldLocation();
				if (loc == null) {
					out.add(map);
					continue;
				}

				Map<String, Map<String, Object>> byOffset = new LinkedHashMap<>();
				List<Map<String, Object>> neighbours = new ArrayList<>();
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dy = -radius; dy <= radius; dy++) {
						if (dx == 0 && dy == 0) {
							continue;
						}
						WorldPoint p = new WorldPoint(loc.getX() + dx, loc.getY() + dy, loc.getPlane());
						Map<String, Object> entry = new LinkedHashMap<>();
						entry.put("position", positionOf(p));
						entry.put("dx", dx);
						entry.put("dy", dy);
						entry.put("walkable", isWalkableInScene(wv, flags, p));
						neighbours.add(entry);
						byOffset.put(dx + "," + dy, entry);
					}
				}
				map.put("neighbours", neighbours);

				// Opposing pairs on each axis: for an obstacle standing between two open tiles these
				// are the transport's two endpoints.
				Map<String, Object> sides = new LinkedHashMap<>();
				sides.put("eastWest", axisPair(byOffset, -1, 0, 1, 0));
				sides.put("northSouth", axisPair(byOffset, 0, -1, 0, 1));
				map.put("sides", sides);

				map.put("selfWalkable", isWalkableInScene(wv, flags, loc));
				out.add(map);
			}
			return out;
		}).orElse(null);

		if (serialized == null) {
			sendJson(exchange, 503, errorResponse("Scene unavailable (not logged in?)"));
			return;
		}

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("count", serialized.size());
		response.put("total", objects.size());
		response.put("radius", radius);
		response.put("objects", serialized);
		sendJson(exchange, 200, response);
	}

	private static Map<String, Object> axisPair(Map<String, Map<String, Object>> byOffset,
												int aDx, int aDy, int bDx, int bDy) {
		Map<String, Object> a = byOffset.get(aDx + "," + aDy);
		Map<String, Object> b = byOffset.get(bDx + "," + bDy);
		Map<String, Object> pair = new LinkedHashMap<>();
		pair.put("a", a);
		pair.put("b", b);
		pair.put("bothWalkable", a != null && b != null
				&& Boolean.TRUE.equals(a.get("walkable")) && Boolean.TRUE.equals(b.get("walkable")));
		return pair;
	}

	private static Map<String, Integer> positionOf(WorldPoint p) {
		Map<String, Integer> position = new LinkedHashMap<>();
		position.put("x", p.getX());
		position.put("y", p.getY());
		position.put("plane", p.getPlane());
		return position;
	}

	/** Live-scene walkability; false when the tile is outside the loaded scene. */
	private static boolean isWalkableInScene(net.runelite.api.WorldView wv, int[][] flags, WorldPoint p) {
		if (flags == null) {
			return false;
		}
		net.runelite.api.coords.LocalPoint lp = net.runelite.api.coords.LocalPoint.fromWorld(wv, p);
		if (lp == null) {
			return false;
		}
		int sx = lp.getSceneX();
		int sy = lp.getSceneY();
		if (sx < 0 || sy < 0 || sx >= flags.length || sy >= flags[sx].length) {
			return false;
		}
		return (flags[sx][sy] & net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
	}

	private Map<String, Object> serializeObject(Rs2TileObjectModel obj) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", obj.getId());
		map.put("name", obj.getName());
		map.put("type", obj.getTileObjectType().name());
		map.put("reachable", obj.isReachable());

		WorldPoint loc = obj.getWorldLocation();
		if (loc != null) {
			Map<String, Integer> position = new LinkedHashMap<>();
			position.put("x", loc.getX());
			position.put("y", loc.getY());
			position.put("plane", loc.getPlane());
			map.put("position", position);
		}
		return map;
	}
}
