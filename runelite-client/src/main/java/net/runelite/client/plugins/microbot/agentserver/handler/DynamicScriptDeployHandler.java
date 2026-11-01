package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.agentserver.scripting.DeployedScript;
import net.runelite.client.plugins.microbot.agentserver.scripting.DynamicScriptManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP handler for runtime script deployment.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code GET  /scripts/deploy}          — list all deployments</li>
 *   <li>{@code POST /scripts/deploy}          — deploy (compile + load + start)</li>
 *   <li>{@code POST /scripts/deploy/reload}   — reload an existing deployment</li>
 *   <li>{@code POST /scripts/deploy/undeploy} — stop and remove a deployment</li>
 * </ul>
 */
@Slf4j
public class DynamicScriptDeployHandler extends AgentHandler {

    private static final String BASE_PATH = "/scripts/deploy";
    private final DynamicScriptManager manager = DynamicScriptManager.getInstance();

    public DynamicScriptDeployHandler(Gson gson) {
        super(gson);
    }

    @Override
    public String getPath() {
        return BASE_PATH;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String sub = getSubPath(exchange, BASE_PATH);
        String method = exchange.getRequestMethod().toUpperCase();

        switch (sub) {
            case "":
            case "/":
                if ("GET".equals(method)) {
                    handleList(exchange);
                } else if ("POST".equals(method)) {
                    handleDeploy(exchange);
                } else {
                    sendJson(exchange, 405, errorResponse("Use GET to list or POST to deploy."));
                }
                break;
            case "/reload":
                handleReload(exchange);
                break;
            case "/undeploy":
                handleUndeploy(exchange);
                break;
            default:
                sendJson(exchange, 404, errorResponse("Unknown endpoint: " + BASE_PATH + sub));
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> deployments = manager.listDeployments();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", deployments.size());
        result.put("deployments", deployments);
        sendJson(exchange, 200, result);
    }

    /**
     * POST /scripts/deploy
     * <pre>{"name": "my-bot", "sourcePath": "/path/to/src/"}</pre>
     */
    private void handleDeploy(HttpExchange exchange) throws IOException {
        try {
            requirePost(exchange);
        } catch (HttpMethodException e) {
            sendJson(exchange, 405, errorResponse(e.getMessage()));
            return;
        }

        Map<String, Object> body = readJsonBody(exchange);
        String name = getString(body, "name");
        if (name == null || name.isEmpty()) {
            sendJson(exchange, 400, errorResponse("'name' is required."));
            return;
        }
        if (!isValidName(name)) {
            sendJson(exchange, 400, errorResponse("Invalid name. Use alphanumeric, hyphens, and underscores only."));
            return;
        }

        String sourcePath = getString(body, "sourcePath");
        if (sourcePath == null || sourcePath.isEmpty()) {
            sendJson(exchange, 400, errorResponse("'sourcePath' is required."));
            return;
        }
        Path sourceDir;
        try {
            sourceDir = Path.of(sourcePath);
        } catch (InvalidPathException e) {
            sendJson(exchange, 400, errorResponse("Invalid sourcePath: " + e.getMessage()));
            return;
        }
        if (!Files.isDirectory(sourceDir)) {
            sendJson(exchange, 400, errorResponse("sourcePath is not a directory: " + sourcePath));
            return;
        }

        try {
            DeployedScript deployment = manager.deploy(name, sourceDir);
            sendJson(exchange, 200, deploymentResponse(deployment, "Deployed successfully"));
        } catch (DynamicScriptManager.DeploymentException e) {
            log.warn("Deploy '{}' failed: {}", name, e.getMessage());
            sendJson(exchange, 400, errorResponse(e.getMessage()));
        }
    }

    /**
     * POST /scripts/deploy/reload
     * <pre>{"name": "my-bot"}</pre>
     */
    private void handleReload(HttpExchange exchange) throws IOException {
        try {
            requirePost(exchange);
        } catch (HttpMethodException e) {
            sendJson(exchange, 405, errorResponse(e.getMessage()));
            return;
        }

        Map<String, Object> body = readJsonBody(exchange);
        String name = getString(body, "name");
        if (name == null || name.isEmpty()) {
            sendJson(exchange, 400, errorResponse("'name' is required."));
            return;
        }

        try {
            DeployedScript deployment = manager.reload(name);
            sendJson(exchange, 200, deploymentResponse(deployment, "Reloaded successfully"));
        } catch (DynamicScriptManager.DeploymentException e) {
            log.warn("Reload '{}' failed: {}", name, e.getMessage());
            sendJson(exchange, 400, errorResponse(e.getMessage()));
        }
    }

    /**
     * POST /scripts/deploy/undeploy
     * <pre>{"name": "my-bot"}</pre>
     */
    private void handleUndeploy(HttpExchange exchange) throws IOException {
        try {
            requirePost(exchange);
        } catch (HttpMethodException e) {
            sendJson(exchange, 405, errorResponse(e.getMessage()));
            return;
        }

        Map<String, Object> body = readJsonBody(exchange);
        String name = getString(body, "name");
        if (name == null || name.isEmpty()) {
            sendJson(exchange, 400, errorResponse("'name' is required."));
            return;
        }

        try {
            manager.undeploy(name);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("name", name);
            result.put("message", "Undeployed successfully");
            sendJson(exchange, 200, result);
        } catch (DynamicScriptManager.DeploymentException e) {
            log.warn("Undeploy '{}' failed: {}", name, e.getMessage());
            sendJson(exchange, 400, errorResponse(e.getMessage()));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Map<String, Object> deploymentResponse(DeployedScript deployment, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("name", deployment.getName());
        result.put("message", message);
        result.put("deployedAt", deployment.getDeployedAt().toString());
        if (deployment.getPlugin() != null) {
            PluginDescriptor desc = deployment.getPlugin().getClass().getAnnotation(PluginDescriptor.class);
            result.put("plugin", desc != null ? desc.name() : deployment.getPlugin().getClass().getSimpleName());
        }
        return result;
    }

    private String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val instanceof String ? (String) val : null;
    }

    private boolean isValidName(String name) {
        return name.matches("[a-zA-Z0-9_-]+");
    }
}
