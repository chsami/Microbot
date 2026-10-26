package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class WidgetClickHandler extends AgentHandler {

	public WidgetClickHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return "/widgets/click";
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		try {
			requirePost(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		Map<String, Object> request;
		try {
			request = readJsonBody(exchange);
		} catch (Exception e) {
			sendJson(exchange, 400, errorResponse("Invalid JSON body"));
			return;
		}

		Integer groupNum = getIntField(request, "groupId");
		Integer childNum = getIntField(request, "childId");
		String text = getStringField(request, "text");
		Integer indexNum = getIntField(request, "index");

		Map<String, Object> response = new LinkedHashMap<>();
		boolean clicked;

		if (groupNum != null && childNum != null) {
			int gid = groupNum.intValue();
			int cid = childNum.intValue();
			boolean visible = Rs2Widget.isWidgetVisible(gid, cid);
			if (!visible) {
				response.put("clicked", false);
				response.put("reason", "Widget not found or not visible");
				response.put("groupId", gid);
				response.put("childId", cid);
				sendJson(exchange, 200, response);
				return;
			}
			if (indexNum != null) {
				// dynamic child of the container, e.g. the options of the 219:1 chat menu
				net.runelite.api.widgets.Widget container = Rs2Widget.getWidget(gid, cid);
				net.runelite.api.widgets.Widget[] kids = container == null ? null : container.getDynamicChildren();
				int idx = indexNum.intValue();
				if (kids == null || idx < 0 || idx >= kids.length || kids[idx] == null) {
					response.put("clicked", false);
					response.put("reason", "No dynamic child at index " + idx);
					sendJson(exchange, 200, response);
					return;
				}
				clicked = Rs2Widget.clickWidget(kids[idx]);
				response.put("index", idx);
			} else {
				clicked = Rs2Widget.clickWidget(gid, cid);
			}
			response.put("groupId", gid);
			response.put("childId", cid);
		} else if (text != null && !text.isEmpty()) {
			clicked = Rs2Widget.clickWidget(text);
			response.put("text", text);
			if (!clicked) {
				response.put("reason", "No visible widget found matching text");
			}
		} else {
			sendJson(exchange, 400, errorResponse("Provide either groupId+childId or text"));
			return;
		}

		response.put("clicked", clicked);
		sendJson(exchange, 200, response);
	}
}
