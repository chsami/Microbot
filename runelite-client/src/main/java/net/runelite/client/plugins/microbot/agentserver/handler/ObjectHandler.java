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
				.map(ObjectHandler::serializeObject)
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

		String action = getStringField(body, "action");
		if (action == null || action.isEmpty()) {
			sendJson(exchange, 400, errorResponse("Missing required field: action"));
			return;
		}

		String name = getStringField(body, "name");
		Integer idNum = getIntField(body, "id");
		// x, y (and plane) pick one exact object among several with the same id or name
		Integer wantX = getIntField(body, "x");
		Integer wantY = getIntField(body, "y");
		Integer wantPlane = getIntField(body, "plane");
		if (TargetFilter.halfATile(wantX, wantY)) {
			sendJson(exchange, 400, errorResponse("Give x and y together"));
			return;
		}

		Rs2TileObjectModel obj = null;
		if (name != null && !name.isEmpty()) {
			obj = Microbot.getRs2TileObjectCache().query()
					.withName(name)
					.where(o -> TargetFilter.matchesTile(coveredTiles(o), wantX, wantY, wantPlane))
					.nearestOnClientThread();
		} else if (idNum != null) {
			obj = Microbot.getRs2TileObjectCache().query()
					.withId(idNum.intValue())
					.where(o -> TargetFilter.matchesTile(coveredTiles(o), wantX, wantY, wantPlane))
					.nearestOnClientThread();
		} else {
			sendJson(exchange, 400, errorResponse("Provide either name or id"));
			return;
		}

		Map<String, Object> response = new LinkedHashMap<>();
		if (obj == null) {
			response.put("success", false);
			response.put("reason", TargetFilter.namesTile(wantX, wantY)
					? "filtered: no matching object at " + wantX + "," + wantY : "Object not found");
		} else if (obj.getAvailableActions().stream().noneMatch(action::equalsIgnoreCase)) {
			// the requested action must be one the object really offers: the click maps the name to its menu slot
			Map<String, Object> bad = errorResponse("Object has no action \"" + action + "\"");
			bad.put("availableActions", obj.getAvailableActions());
			bad.put("object", serializeObject(obj));
			sendJson(exchange, 400, bad);
			return;
		} else {
			boolean clicked = obj.click(action);
			response.put("success", clicked);
			response.put("object", serializeObject(obj));
			response.put("action", action);
		}
		sendJson(exchange, 200, response);
	}

	/** Every tile the object stands on, so a multi-tile object matches on any of them. */
	private static java.util.List<WorldPoint> coveredTiles(Rs2TileObjectModel object) {
		net.runelite.api.coords.WorldArea area = object.getWorldArea();
		if (area != null) {
			return area.toWorldPointList();
		}
		WorldPoint at = object.getWorldLocation();
		return at == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(at);
	}

	static Map<String, Object> serializeObject(Rs2TileObjectModel obj) {
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
			// inside an instance "position" is the raw scene tile RuneLite reports; "templatePosition" is the tile
			// in the player's coordinate space
			WorldPoint template = obj.getTemplateWorldLocation();
			if (template != null && !template.equals(loc)) {
				Map<String, Integer> templatePosition = new LinkedHashMap<>();
				templatePosition.put("x", template.getX());
				templatePosition.put("y", template.getY());
				templatePosition.put("plane", template.getPlane());
				map.put("templatePosition", templatePosition);
			}
		}
		return map;
	}
}
