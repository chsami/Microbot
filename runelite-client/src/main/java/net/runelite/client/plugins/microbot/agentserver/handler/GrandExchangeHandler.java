package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class GrandExchangeHandler extends AgentHandler {

	private static final String BASE_PATH = "/ge";

	public GrandExchangeHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return BASE_PATH;
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		String sub = getSubPath(exchange, BASE_PATH);
		try {
			switch (sub) {
				case "":
				case "/":
					requireGet(exchange);
					sendJson(exchange, 200, offers());
					break;
				case "/buy":
					requirePost(exchange);
					handleOffer(exchange, true);
					break;
				case "/sell":
					requirePost(exchange);
					handleOffer(exchange, false);
					break;
				case "/collect":
					requirePost(exchange);
					Boolean toBank = getBooleanField(readJsonBody(exchange), "toBank");
					sendResult(exchange, Rs2GrandExchange.collectAll(toBank == null || toBank));
					break;
				case "/abort":
					requirePost(exchange);
					String name = requireName(readJsonBody(exchange));
					sendResult(exchange, Rs2GrandExchange.abortOffer(name, true)
							&& sleepUntil(() -> !hasOffer(name), 5000));
					break;
				case "/close":
					requirePost(exchange);
					Rs2GrandExchange.closeExchange();
					sendResult(exchange, !Rs2GrandExchange.isOpen());
					break;
				default:
					sendJson(exchange, 404, errorResponse("Unknown endpoint: " + BASE_PATH + sub));
			}
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
		}
	}

	private void handleOffer(HttpExchange exchange, boolean buying) throws IOException {
		Map<String, Object> body = readJsonBody(exchange);
		String name = requireName(body);
		int quantity = requirePositiveWholeNumber(body, "quantity");
		int price = requirePositiveWholeNumber(body, "price");

		boolean placed = Rs2GrandExchange.processOffer(GrandExchangeRequest.builder()
				.action(buying ? GrandExchangeAction.BUY : GrandExchangeAction.SELL)
				.itemName(name)
				.exact(true)
				.quantity(quantity)
				.price(price)
				.build());
		sendResult(exchange, placed);
	}

	/**
	 * JSON numbers arrive as doubles, so 1.9 would otherwise become a price of 1.
	 */
	static int requirePositiveWholeNumber(Map<String, Object> body, String key) {
		Object value = body.get(key);
		double number;
		try {
			number = value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value).trim());
		} catch (NumberFormatException e) {
			number = Double.NaN;
		}
		if (number != Math.rint(number) || number < 1 || number > Integer.MAX_VALUE) {
			throw new BadRequestException("Field '" + key + "' must be a whole number of at least 1");
		}
		return (int) number;
	}

	private void sendResult(HttpExchange exchange, boolean success) throws IOException {
		Map<String, Object> result = offers();
		result.put("success", success);
		sendJson(exchange, success ? 200 : 409, result);
	}

	private boolean hasOffer(String name) {
		return slots().stream().anyMatch(slot -> name.equalsIgnoreCase(String.valueOf(slot.get("item"))));
	}

	private static String requireName(Map<String, Object> body) {
		String name = getStringField(body, "name");
		if (Strings.isNullOrEmpty(name)) {
			throw new BadRequestException("Field 'name' is required");
		}
		return name;
	}

	private Map<String, Object> offers() {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("open", Rs2GrandExchange.isOpen());
		result.put("offers", slots());
		return result;
	}

	private List<Map<String, Object>> slots() {
		List<Map<String, Object>> slots = new ArrayList<>();
		GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
		for (int i = 0; i < offers.length; i++) {
			GrandExchangeOffer offer = offers[i];
			if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY) {
				continue;
			}
			int itemId = offer.getItemId();
			Map<String, Object> slot = new LinkedHashMap<>();
			slot.put("slot", i + 1);
			slot.put("item", Microbot.getClientThread()
					.runOnClientThreadOptional(() -> Microbot.getItemManager().getItemComposition(itemId).getName())
					.orElse(null));
			slot.put("itemId", itemId);
			slot.put("state", offer.getState().name());
			slot.put("price", offer.getPrice());
			slot.put("done", offer.getQuantitySold());
			slot.put("total", offer.getTotalQuantity());
			slot.put("spent", offer.getSpent());
			slots.add(slot);
		}
		return slots;
	}
}
