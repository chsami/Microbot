package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class KeyboardHandler extends AgentHandler {

	public KeyboardHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return "/keyboard";
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

		String text = getStringField(request, "text");
		String key = getStringField(request, "key");

		if (text == null && key == null) {
			sendJson(exchange, 400, errorResponse("Required: text (string to type) or key (enter/escape/backspace)"));
			return;
		}

		Map<String, Object> response = new LinkedHashMap<>();
		long pauseGeneration = ScriptPauseController.getGeneration();
		if (ScriptPauseController.isPaused()) {
			response.put("success", false);
			response.put("reason", "Scripts are paused");
			sendJson(exchange, 200, response);
			return;
		}

		if (key != null) {
			switch (key.toLowerCase()) {
				case "enter":
					Rs2Keyboard.enter();
					response.put("key", "enter");
					break;
				case "escape":
				case "esc":
					Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
					response.put("key", "escape");
					break;
				case "backspace":
					Rs2Keyboard.keyPress(KeyEvent.VK_BACK_SPACE);
					response.put("key", "backspace");
					break;
				default:
					sendJson(exchange, 400, errorResponse("Unknown key: " + key + ". Valid: enter, escape, backspace"));
					return;
			}
			boolean success = pauseGeneration == ScriptPauseController.getGeneration() && !ScriptPauseController.isPaused();
			response.put("success", success);
			if (!success) response.put("reason", "Scripts were paused while keyboard input was being submitted");
			sendJson(exchange, 200, response);
			return;
		}

		Rs2Keyboard.typeString(text);

		response.put("text", text);
		response.put("length", text.length());
		boolean success = pauseGeneration == ScriptPauseController.getGeneration() && !ScriptPauseController.isPaused();
		response.put("success", success);
		if (!success) response.put("reason", "Scripts were paused while keyboard input was being submitted");
		sendJson(exchange, 200, response);
	}
}
