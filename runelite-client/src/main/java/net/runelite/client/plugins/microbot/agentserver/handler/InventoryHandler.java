package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class InventoryHandler extends AgentHandler {

	private static final Object SELECTION_LOCK = new Object();

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
			case "/use":
				handleUse(exchange);
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

		if ((name == null || name.isEmpty()) && idNum == null) {
			sendJson(exchange, 400, errorResponse("Provide either name or id"));
			return;
		}

		boolean success;
		synchronized (SELECTION_LOCK) {
			if (name != null && !name.isEmpty()) {
				success = Rs2Inventory.interact(name, action);
			} else {
				success = Rs2Inventory.interact(idNum.intValue(), action);
			}
		}

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("success", success);
		response.put("action", action);
		if (name != null) response.put("name", name);
		if (idNum != null) response.put("id", idNum.intValue());
		sendJson(exchange, 200, response);
	}

	/**
	 * Use an inventory item on another inventory item, the nearest object, or the nearest NPC (by name).
	 * Body: {"item": "...", "target": "...", "targetType": "item" | "object" | "npc"} (targetType defaults to item).
	 */
	private void handleUse(HttpExchange exchange) throws IOException {
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

		String item = getStringField(body, "item");
		String target = getStringField(body, "target");
		String targetType = getStringField(body, "targetType");
		if (item == null || item.isEmpty() || target == null || target.isEmpty()) {
			sendJson(exchange, 400, errorResponse("Missing required fields: item, target"));
			return;
		}
		if (targetType == null || targetType.isEmpty()) {
			targetType = "item";
		}

		boolean success;
		Map<String, Object> response = new LinkedHashMap<>();
		// Serialize selection-dependent requests: the server has several workers, and another request could
		// otherwise select or clear an item between this request's select and its target click.
		synchronized (SELECTION_LOCK) {
			// A selection left over from an earlier use, or a selected spell, would turn the first click into
			// "use/cast <old> on <item>". Cancel any widget targeting on the client thread before starting.
			if (!clearWidgetSelection()) {
				sendJson(exchange, 409, errorResponse("Could not clear the current item/spell selection"));
				return;
			}

			switch (targetType) {
				case "item":
					success = Rs2Inventory.combine(item, target);
					break;
				case "object": {
					Rs2TileObjectModel object = Microbot.getRs2TileObjectCache().query().withName(target).nearestOnClientThread();
					if (object == null) {
						sendJson(exchange, 404, errorResponse("Object not found: " + target));
						return;
					}
					success = Rs2Inventory.useUnNotedItemOnObject(item, object);
					response.put("objectId", object.getId());
					break;
				}
				case "npc": {
					Rs2NpcModel npc = Microbot.getRs2NpcCache().query().withName(target).nearestOnClientThread();
					Rs2ItemModel inventoryItem = Rs2Inventory.get(item);
					if (npc == null || inventoryItem == null) {
						sendJson(exchange, 404, errorResponse(npc == null ? "NPC not found: " + target : "Item not in inventory: " + item));
						return;
					}
					// Pass the resolved NPC instance so a target in a child world view is not re-resolved by id.
					success = Rs2Inventory.useItemOnNpc(inventoryItem.getId(), npc.getNpc());
					break;
				}
				default:
					sendJson(exchange, 400, errorResponse("targetType must be item, object or npc"));
					return;
			}
		}

		response.put("success", success);
		response.put("item", item);
		response.put("target", target);
		response.put("targetType", targetType);
		sendJson(exchange, 200, response);
	}

	/** Cancels any item or spell targeting on the client thread; true when nothing is selected afterwards. */
	private static boolean clearWidgetSelection() {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			Client client = Microbot.getClient();
			if (client.isWidgetSelected()) {
				client.setWidgetSelected(false);
			}
			return !client.isWidgetSelected();
		}).orElse(false);
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
