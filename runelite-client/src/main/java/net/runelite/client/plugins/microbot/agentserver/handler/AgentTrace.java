package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public final class AgentTrace {

	static final long MAX_BYTES = 5L * 1024 * 1024;
	private static final int MAX_BODY = 2048;
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static volatile Path file;

	private AgentTrace() {}

	public static void setFile(Path traceFile) {
		file = traceFile;
	}

	static boolean isEnabled() {
		return file != null;
	}

	static void record(String method, String uri, int status, long durationMs, String body) {
		Path target = file;
		if (target == null) {
			return;
		}
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("at", Instant.now().toString());
		line.put("method", method);
		line.put("uri", uri);
		line.put("status", status);
		line.put("ms", durationMs);
		if (body != null && !body.isBlank()) {
			line.put("body", body.length() > MAX_BODY ? body.substring(0, MAX_BODY) + "…" : body);
		}
		byte[] bytes = (GSON.toJson(line) + "\n").getBytes(StandardCharsets.UTF_8);
		synchronized (AgentTrace.class) {
			try {
				Files.createDirectories(target.getParent());
				if (Files.exists(target) && Files.size(target) + bytes.length > MAX_BYTES) {
					Files.move(target, target.resolveSibling(target.getFileName() + ".1"), StandardCopyOption.REPLACE_EXISTING);
				}
				Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (IOException e) {
				log.debug("Could not write agent trace: {}", e.getMessage());
			}
		}
	}
}
