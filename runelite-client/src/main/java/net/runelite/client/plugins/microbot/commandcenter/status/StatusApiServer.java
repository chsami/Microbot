package net.runelite.client.plugins.microbot.commandcenter.status;

import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lightweight HTTP server bound to localhost only.
 * Exposes /status and /health endpoints for Command Center polling.
 */
@Slf4j
public class StatusApiServer {
    private HttpServer server;
    private int port;
    private final Path portFilePath;
    private final StatusApiHandler handler;

    public StatusApiServer(Path portFilePath, StatusApiHandler handler) {
        this.portFilePath = portFilePath;
        this.handler = handler;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/status", handler::handleStatus);
        server.createContext("/health", handler::handleHealth);
        server.setExecutor(null); // default single-thread executor
        server.start();

        // Write port to file for Command Center discovery
        Files.writeString(portFilePath, String.valueOf(port));
        log.info("Status API started on 127.0.0.1:{}", port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("Status API stopped");
        }
        // Clean up port file
        try {
            Files.deleteIfExists(portFilePath);
        } catch (IOException e) {
            log.warn("Failed to delete port file: {}", e.getMessage());
        }
    }

    public int getPort() {
        return port;
    }
}
