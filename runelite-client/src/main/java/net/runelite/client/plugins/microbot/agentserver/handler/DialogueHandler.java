package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DialogueHandler extends AgentHandler {

	public DialogueHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return "/dialogue";
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		String sub = getSubPath(exchange, "/dialogue");

		switch (sub) {
			case "":
			case "/":
				handleStatus(exchange);
				break;
			case "/continue":
				handleContinue(exchange);
				break;
			case "/select":
				handleSelect(exchange);
				break;
			default:
				sendJson(exchange, 404, errorResponse("Unknown path: /dialogue" + sub));
		}
	}

	private void handleStatus(HttpExchange exchange) throws IOException {
		try {
			requireGet(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		sendJson(exchange, 200, snapshot());
	}

	static Map<String, Object> snapshot() {
		boolean inDialogue = Rs2Dialogue.isInDialogue();
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("inDialogue", inDialogue);

		if (inDialogue) {
			response.put("hasContinue", Rs2Dialogue.hasContinue());
			response.put("hasOptions", Rs2Dialogue.hasSelectAnOption());

			String dialogueText = Rs2Dialogue.getDialogueText();
			if (dialogueText != null && !dialogueText.isEmpty()) {
				response.put("dialogueText", dialogueText);
			}

			if (Rs2Dialogue.hasSelectAnOption()) {
				String question = Rs2Dialogue.getQuestion();
				if (question != null && !question.isEmpty()) {
					response.put("question", question);
				}

				List<Widget> optionWidgets = Rs2Dialogue.getDialogueOptions();
				if (optionWidgets != null) {
					List<String> options = optionWidgets.stream()
							.filter(w -> w != null && w.getText() != null && !w.getText().isEmpty())
							.map(Widget::getText)
							.collect(Collectors.toList());
					response.put("options", options);
				}
			}
		}

		return response;
	}

	private void handleContinue(HttpExchange exchange) throws IOException {
		try {
			requirePost(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		boolean hasContinue = Rs2Dialogue.hasContinue();
		Map<String, Object> response = new LinkedHashMap<>();

		if (hasContinue) {
			Rs2Dialogue.clickContinue();
			response.put("success", true);
		} else {
			response.put("success", false);
			response.put("reason", "No continue button available");
		}

		sendJson(exchange, 200, response);
	}

	private void handleSelect(HttpExchange exchange) throws IOException {
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

		String option = getStringField(body, "option");
		Integer indexNum = getIntField(body, "index");

		Map<String, Object> response = new LinkedHashMap<>();
		boolean success;

		if (option != null && !option.isEmpty()) {
			success = Rs2Dialogue.clickOption(option);
			response.put("option", option);
		} else if (indexNum != null) {
			success = Rs2Dialogue.keyPressForDialogueOption(indexNum.intValue());
			response.put("index", indexNum.intValue());
		} else {
			sendJson(exchange, 400, errorResponse("Provide either option (text) or index"));
			return;
		}

		response.put("success", success);
		sendJson(exchange, 200, response);
	}
}
