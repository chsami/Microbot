package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.client.plugins.microbot.Microbot;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class VarbitHandler extends AgentHandler {

	public VarbitHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return "/varbit";
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
		String idStr = params.get("id");
		if (idStr == null || idStr.isEmpty()) {
			sendJson(exchange, 400, errorResponse("Missing required parameter: id"));
			return;
		}

		int varbitId;
		try {
			varbitId = Integer.parseInt(idStr);
		} catch (NumberFormatException e) {
			sendJson(exchange, 400, errorResponse("Invalid varbit id: " + idStr));
			return;
		}

		Optional<Boolean> validId = Microbot.getRs2PlayerStateCache().isValidVarbitId(varbitId);
		if (!validId.isPresent()) {
			sendJson(exchange, 503, errorResponse("Client thread unavailable"));
			return;
		}
		if (!validId.get()) {
			sendJson(exchange, 400, errorResponse("Invalid varbit id: " + idStr));
			return;
		}

		int value = Microbot.getVarbitValue(varbitId);

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("varbitId", varbitId);
		response.put("value", value);
		sendJson(exchange, 200, response);
	}
}
