package net.runelite.client.plugins.microbot.commandcenter.status;

import com.sun.net.httpserver.HttpExchange;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
public class StatusApiHandler {
    private final BotStatusModel statusModel;

    public StatusApiHandler(BotStatusModel statusModel) {
        this.statusModel = statusModel;
    }

    public void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        try {
            String json = statusModel.toJson();
            sendResponse(exchange, 200, json);
        } catch (Exception e) {
            log.error("Error building status response", e);
            sendResponse(exchange, 500, "{\"error\":\"Internal error\"}");
        }
    }

    public void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        sendResponse(exchange, 200, "{\"alive\":true}");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
