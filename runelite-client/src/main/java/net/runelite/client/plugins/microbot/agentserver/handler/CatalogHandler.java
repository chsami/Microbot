package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CatalogHandler extends AgentHandler {

	private final List<String> endpoints;

	public CatalogHandler(Gson gson, List<String> endpoints) {
		super(gson);
		this.endpoints = List.copyOf(endpoints);
	}

	@Override
	public String getPath() {
		return "/";
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		Map<String, Object> result = new LinkedHashMap<>();
		if (!"/".equals(path)) {
			result.put("error", "Unknown endpoint: " + path);
			result.put("endpoints", endpoints);
			sendJson(exchange, 404, result);
			return;
		}
		result.put("count", endpoints.size());
		result.put("endpoints", endpoints);
		result.put("docs", "docs/AGENT_SERVER.md");
		sendJson(exchange, 200, result);
	}
}
