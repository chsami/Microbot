package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ObserveHandler extends AgentHandler {

	private static final int DEFAULT_CHAT_LINES = 10;
	private static final int MAX_NEARBY_DISTANCE = 30;
	private static final int DEFAULT_NEARBY_LIMIT = 10;

	private final Client client;

	public ObserveHandler(Gson gson, Client client) {
		super(gson);
		this.client = client;
	}

	@Override
	public String getPath() {
		return "/observe";
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
		long chatSince = getLongParam(params, "chatSince");
		int nearby = Math.min(getIntParam(params, "nearby", 0), MAX_NEARBY_DISTANCE);
		int nearbyLimit = getIntParam(params, "nearbyLimit", DEFAULT_NEARBY_LIMIT);

		Map<String, Object> result = new LinkedHashMap<>(StateHandler.snapshot(client));
		if (Microbot.isLoggedIn()) {
			result.put("inventory", InventoryHandler.snapshot());
			result.put("equipment", equipment());
			result.put("dialogue", DialogueHandler.snapshot());
			if (nearby > 0) {
				result.put("npcs", nearbyNpcs(nearby, nearbyLimit));
				result.put("objects", nearbyObjects(nearby, nearbyLimit));
			}
		}
		result.put("chat", chat(chatSince));
		result.put("scripts", new ArrayList<>(ScriptHeartbeatRegistry.getAllHealth().values()));
		sendJson(exchange, 200, result);
	}

	static Map<String, Object> chat(long chatSince) {
		Map<String, Object> chat = new LinkedHashMap<>();
		chat.put("cursor", ChatLog.cursor());
		chat.put("messages", chatSince >= 0
				? ChatLog.since(chatSince, ChatLog.CAPACITY)
				: ChatLog.since(0, DEFAULT_CHAT_LINES));
		return chat;
	}

	private static long getLongParam(Map<String, String> params, String key) {
		String val = params.get(key);
		if (val == null || val.isEmpty()) return -1;
		try {
			return Long.parseLong(val.trim());
		} catch (NumberFormatException e) {
			throw new BadRequestException("Parameter '" + key + "' must be a number");
		}
	}

	private static List<Map<String, Object>> equipment() {
		return Rs2Equipment.all().map(ObserveHandler::serializeEquipped).collect(Collectors.toList());
	}

	private static Map<String, Object> serializeEquipped(Rs2ItemModel item) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("slot", slotName(item.getSlot()));
		map.put("id", item.getId());
		map.put("name", item.getName());
		map.put("quantity", item.getQuantity());
		return map;
	}

	private static String slotName(int slotIdx) {
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
			if (slot.getSlotIdx() == slotIdx) {
				return slot.name();
			}
		}
		return String.valueOf(slotIdx);
	}

	private static List<Map<String, Object>> nearbyNpcs(int distance, int limit) {
		return Microbot.getRs2NpcCache().query().within(distance).toListOnClientThread().stream()
				.sorted(Comparator.comparingInt(Rs2NpcModel::getDistanceFromPlayer))
				.limit(limit)
				.map(NpcHandler::serializeNpc)
				.collect(Collectors.toList());
	}

	private static List<Map<String, Object>> nearbyObjects(int distance, int limit) {
		WorldPoint player = Microbot.getRs2PlayerStateCache().getLocalPlayerPosition();
		return Microbot.getRs2TileObjectCache().query().within(distance).toListOnClientThread().stream()
				.sorted(Comparator.comparingInt((Rs2TileObjectModel o) -> distanceFrom(player, o.getWorldLocation())))
				.limit(limit)
				.map(ObjectHandler::serializeObject)
				.collect(Collectors.toList());
	}

	private static int distanceFrom(WorldPoint player, WorldPoint target) {
		return player == null || target == null ? Integer.MAX_VALUE : player.distanceTo(target);
	}
}
