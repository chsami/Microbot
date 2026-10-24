package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class InventoryHandler extends AgentHandler {

	public InventoryHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return "/inventory";
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		String sub = getSubPath(exchange, "/inventory");

		switch (sub) {
			case "":
			case "/":
				handleList(exchange);
				break;
			case "/interact":
				handleInteract(exchange);
				break;
			case "/drop":
				handleDrop(exchange);
				break;
			default:
				sendJson(exchange, 404, errorResponse("Unknown path: /inventory" + sub));
		}
	}

	private void handleList(HttpExchange exchange) throws IOException {
		try {
			requireGet(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		sendJson(exchange, 200, snapshot());
	}

	static Map<String, Object> snapshot() {
		List<Map<String, Object>> items = Rs2Inventory.all().stream()
				.map(InventoryHandler::serializeItem)
				.collect(Collectors.toList());

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("count", items.size());
		response.put("capacity", 28);
		response.put("freeSlots", Rs2Inventory.emptySlotCount());
		response.put("full", Rs2Inventory.isFull());
		response.put("items", items);
		return response;
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

		boolean success;
		if (name != null && !name.isEmpty()) {
			success = Rs2Inventory.interact(name, action);
		} else if (idNum != null) {
			success = Rs2Inventory.interact(idNum.intValue(), action);
		} else {
			sendJson(exchange, 400, errorResponse("Provide either name or id"));
			return;
		}

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", success);
		response.put("action", action);
		if (name != null) response.put("name", name);
		if (idNum != null) response.put("id", idNum.intValue());
		sendJson(exchange, 200, response);
	}

	private void handleDrop(HttpExchange exchange) throws IOException {
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

		String name = getStringField(body, "name");
		if (name == null || name.isEmpty()) {
			sendJson(exchange, 400, errorResponse("Missing required field: name"));
			return;
		}

		Boolean all = getBooleanField(body, "all");
		boolean success;
		if (Boolean.TRUE.equals(all)) {
			success = Rs2Inventory.dropAll(name);
		} else {
			success = Rs2Inventory.drop(name);
		}

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", success);
		response.put("name", name);
		response.put("droppedAll", Boolean.TRUE.equals(all));
		sendJson(exchange, 200, response);
	}

	private static Map<String, Object> serializeItem(Rs2ItemModel item) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", item.getId());
		map.put("name", item.getName());
		map.put("quantity", item.getQuantity());
		map.put("slot", item.getSlot());
		map.put("stackable", item.isStackable());
		map.put("noted", item.isNoted());
		String[] actions = item.getInventoryActions();
		if (actions != null) {
			map.put("actions", Arrays.stream(actions)
					.filter(a -> a != null && !a.isEmpty())
					.collect(Collectors.toList()));
		}
		return map;
	}
}
