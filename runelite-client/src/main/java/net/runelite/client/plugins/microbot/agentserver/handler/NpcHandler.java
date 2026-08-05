package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class NpcHandler extends AgentHandler {

	private final int defaultLimit;

	public NpcHandler(Gson gson, int defaultLimit) {
		super(gson);
		this.defaultLimit = defaultLimit;
	}

	@Override
	public String getPath() {
		return "/npcs";
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		String sub = getSubPath(exchange, "/npcs");

		switch (sub) {
			case "":
			case "/":
				handleQuery(exchange);
				break;
			case "/interact":
				handleInteract(exchange);
				break;
			default:
				sendJson(exchange, 404, errorResponse("Unknown path: /npcs" + sub));
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

		var query = Microbot.getRs2NpcCache().query();
		if (id >= 0) {
			query = query.withId(id);
		}
		if (name != null && !name.isEmpty()) {
			query = query.withName(name);
		} else if (nameContains != null && !nameContains.isEmpty()) {
			query = query.withNameContains(nameContains);
		}
		query = query.within(maxDistance);

		List<Rs2NpcModel> npcs = query.toListOnClientThread();

		List<Map<String, Object>> serialized = npcs.stream()
				.limit(limit)
				.map(this::serializeNpc)
				.collect(Collectors.toList());

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("count", serialized.size());
		response.put("total", npcs.size());
		response.put("npcs", serialized);
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

		Rs2NpcModel npc = null;
		if (name != null && !name.isEmpty()) {
			npc = Microbot.getRs2NpcCache().query()
					.withName(name)
					.nearestOnClientThread();
		} else if (idNum != null) {
			npc = Microbot.getRs2NpcCache().query()
					.withId(idNum.intValue())
					.nearestOnClientThread();
		} else {
			sendJson(exchange, 400, errorResponse("Provide either name or id"));
			return;
		}

		Map<String, Object> response = new LinkedHashMap<>();
		if (npc == null) {
			response.put("success", false);
			response.put("reason", "NPC not found");
		} else {
			boolean clicked = npc.click(action);
			response.put("success", clicked);
			response.put("npc", serializeNpc(npc));
			response.put("action", action);
		}
		sendJson(exchange, 200, response);
	}

	private Map<String, Object> serializeNpc(Rs2NpcModel npc) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", npc.getId());
		map.put("index", npc.getIndex());
		map.put("name", npc.getName());
		map.put("combatLevel", npc.getCombatLevel());
		map.put("healthRatio", npc.getHealthRatio());
		map.put("healthScale", npc.getHealthScale());
		map.put("interacting", npc.isInteracting());
		map.put("distance", npc.getDistanceFromPlayer());
		// Which way it faces and whether it is walking. Needed to diagnose follow/stealth steps, where
		// the rule is literally "he turns round and looks" — without these the only signals available
		// are position diffing and timers, which are proxies for both and tune badly.
		map.put("orientation", npc.getOrientation());
		map.put("currentOrientation", npc.getCurrentOrientation());
		map.put("poseAnimation", npc.getPoseAnimation());
		map.put("animation", npc.getAnimation());

		WorldPoint loc = npc.getWorldLocation();
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
