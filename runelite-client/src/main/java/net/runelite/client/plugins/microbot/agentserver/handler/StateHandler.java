package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.plugins.microbot.Microbot;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class StateHandler extends AgentHandler {

	private final Client client;

	public StateHandler(Gson gson, Client client) {
		super(gson);
		this.client = client;
	}

	@Override
	public String getPath() {
		return "/state";
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		try {
			requireGet(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		sendJson(exchange, 200, snapshot(client));
	}

	static Map<String, Object> snapshot(Client client) {
		Map<String, Object> state = new LinkedHashMap<>();
		GameState gameState = client.getGameState();
		state.put("loggedIn", Microbot.isLoggedIn());
		state.put("gameState", gameState.name());

		if (Microbot.isLoggedIn()) {
			try {
				Microbot.getClientThread().runOnClientThreadOptional(() -> {
					Player localPlayer = client.getLocalPlayer();
					if (localPlayer == null) return null;

					Map<String, Object> playerInfo = new LinkedHashMap<>();
					playerInfo.put("name", localPlayer.getName());
					playerInfo.put("combatLevel", localPlayer.getCombatLevel());
					playerInfo.put("healthRatio", localPlayer.getHealthRatio());
					playerInfo.put("healthScale", localPlayer.getHealthScale());
					playerInfo.put("animating", localPlayer.getAnimation() != -1);
					playerInfo.put("animationId", localPlayer.getAnimation());
					playerInfo.put("moving", localPlayer.getPoseAnimation() != localPlayer.getIdlePoseAnimation());
					playerInfo.put("interacting", localPlayer.isInteracting());

					var loc = Microbot.getRs2PlayerStateCache().getLocalPlayerPosition();
					if (loc != null) {
						Map<String, Integer> position = new LinkedHashMap<>();
						position.put("x", loc.getX());
						position.put("y", loc.getY());
						position.put("plane", loc.getPlane());
						playerInfo.put("position", position);
					}

					state.put("player", playerInfo);
					return null;
				});
			} catch (Exception e) {
				log.debug("Failed to retrieve player state", e);
			}
		}

		state.put("scriptsPaused", Microbot.pauseAllScripts.get());
		state.put("instance", instance());
		return state;
	}

	static Map<String, Object> instance() {
		Map<String, Object> instance = new LinkedHashMap<>();
		instance.put("pid", ProcessHandle.current().pid());
		instance.put("microbotVersion", RuneLiteProperties.getMicrobotVersion());
		instance.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
		return instance;
	}
}
