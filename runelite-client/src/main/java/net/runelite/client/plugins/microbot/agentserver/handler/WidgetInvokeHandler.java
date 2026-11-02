package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class WidgetInvokeHandler extends AgentHandler {

	public WidgetInvokeHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return "/widgets/invoke";
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
		Integer param0Num = getIntField(request, "param0");
		String action = getStringField(request, "action");
		Integer identifierNum = getIntField(request, "identifier");

		if (groupNum == null || childNum == null) {
			sendJson(exchange, 400, errorResponse("Required: groupId, childId"));
			return;
		}

		int groupId = groupNum.intValue();
		int childId = childNum.intValue();
		int param0 = param0Num != null ? param0Num.intValue() : -1;
		int identifier = identifierNum != null ? identifierNum.intValue() : 1;
		String option = action != null ? action : "Select";

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("groupId", groupId);
		response.put("childId", childId);
		response.put("param0", param0);
		response.put("action", option);
		response.put("identifier", identifier);
		long pauseGeneration = ScriptPauseController.getGeneration();
		if (ScriptPauseController.isPaused()) {
			response.put("invoked", false);
			response.put("reason", "Scripts are paused");
			sendJson(exchange, 200, response);
			return;
		}

		int packedId = (groupId << 16) | childId;
		Widget widget = Rs2Widget.getWidget(packedId);

		if (widget == null) {
			response.put("invoked", false);
			response.put("reason", "Widget not found");
			sendJson(exchange, 200, response);
			return;
		}

		response.put("param1", widget.getId());

		java.awt.Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
			Widget w = Microbot.getClient().getWidget(packedId);
			if (w != null && w.getDynamicChildren() != null && param0 >= 0 && param0 < w.getDynamicChildren().length) {
				Widget child = w.getDynamicChildren()[param0];
				if (child != null) return child.getBounds();
			}
			return w != null ? w.getBounds() : null;
		}).orElse(null);

		java.awt.Rectangle clickRect = (bounds != null && Rs2UiHelper.isRectangleWithinCanvas(bounds))
				? bounds : Rs2UiHelper.getDefaultRectangle();

		NewMenuEntry menuEntry = new NewMenuEntry()
				.option(option)
				.target("")
				.identifier(identifier)
				.type(MenuAction.CC_OP)
				.param0(param0)
				.param1(widget.getId())
				.forceLeftClick(false);

		if (pauseGeneration != ScriptPauseController.getGeneration() || ScriptPauseController.isPaused()) {
			response.put("invoked", false);
			response.put("reason", "Scripts were paused while the action was being prepared");
			sendJson(exchange, 200, response);
			return;
		}
		Microbot.doInvoke(menuEntry, clickRect);
		response.put("bounds", bounds != null ? bounds.toString() : "null");
		boolean invoked = pauseGeneration == ScriptPauseController.getGeneration() && !ScriptPauseController.isPaused();
		response.put("invoked", invoked);
		if (!invoked) response.put("reason", "Scripts were paused while the action was being submitted");
		sendJson(exchange, 200, response);
	}
}
