package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diagnostics for the hybrid live-collision overlay: {@code GET /live-collision[?x=&y=&plane=]}.
 * <p>
 * Reports whether the overlay is enabled and captured, the snapshot's scene base, and — for a tile
 * (the player's by default, or one given by query params) — the overlay's own edge readings
 * ({@code overlayRaw}: {@code true}/{@code false}/{@code null} = defer to static), the resolved edges
 * the pathfinder uses, and the static-only edges. Where {@code resolved} differs from {@code static},
 * live collision is changing routing. Exists to verify Stage 1/2 against a running client, since the
 * overlay and pathfinder are otherwise not observable over HTTP.
 */
@Slf4j
public class LiveCollisionHandler extends AgentHandler {

    public LiveCollisionHandler(Gson gson) {
        super(gson);
    }

    @Override
    public String getPath() {
        return "/live-collision";
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        try {
            requireGet(exchange);
        } catch (HttpMethodException e) {
            sendJson(exchange, 405, errorResponse(e.getMessage()));
            return;
        }

        final PathfinderConfig config = ShortestPathPlugin.getPathfinderConfig();
        if (config == null) {
            sendJson(exchange, 503, errorResponse("Pathfinder not initialised yet"));
            return;
        }

        final Map<String, String> params = parseQuery(exchange.getRequestURI());
        final WorldPoint tile = resolveTile(params);
        if (tile == null) {
            sendJson(exchange, 400, errorResponse("No tile: pass x, y (and optional plane) or log in"));
            return;
        }

        final Map<String, Object> response = new LinkedHashMap<>(
                config.liveCollisionDiagnostics(tile.getX(), tile.getY(), tile.getPlane()));
        sendJson(exchange, 200, response);
    }

    private WorldPoint resolveTile(Map<String, String> params) {
        if (params.get("x") != null && params.get("y") != null) {
            try {
                final int x = Integer.parseInt(params.get("x"));
                final int y = Integer.parseInt(params.get("y"));
                final int plane = getIntParam(params, "plane", 0);
                return new WorldPoint(x, y, plane);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (!Microbot.isLoggedIn()) {
            return null;
        }
        return Rs2Player.getWorldLocation();
    }
}
