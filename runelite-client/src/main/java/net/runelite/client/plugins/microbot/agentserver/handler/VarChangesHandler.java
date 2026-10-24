package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class VarChangesHandler extends AgentHandler {

	private static final int DEFAULT_LIMIT = 50;

	public VarChangesHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return "/var-changes";
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
		long since = getIntParam(params, "since", 0);
		int limit = getIntParam(params, "limit", DEFAULT_LIMIT);
		int varbit = getIntParam(params, "varbit", -1);
		int varp = getIntParam(params, "varp", -1);

		Predicate<Map<String, Object>> filter = entry ->
				(varbit < 0 || Integer.valueOf(varbit).equals(entry.get("varbit")))
				&& (varp < 0 || Integer.valueOf(varp).equals(entry.get("varp")));
		List<Map<String, Object>> changes = AgentLogs.VARS.since(since, limit, filter);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("cursor", AgentLogs.VARS.cursor());
		result.put("count", changes.size());
		result.put("changes", changes);
		sendJson(exchange, 200, result);
	}
}
