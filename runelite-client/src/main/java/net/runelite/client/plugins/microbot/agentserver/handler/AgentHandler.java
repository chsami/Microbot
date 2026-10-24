package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
public abstract class AgentHandler implements HttpHandler {

	private static final int MAX_BODY_SIZE = 16 * 1024;
	private static final String AUTH_HEADER = "X-Agent-Token";

	private static volatile Supplier<String> tokenSupplier = () -> null;
	private static final ThreadLocal<String> requestBody = new ThreadLocal<>();

	protected final Gson gson;

	protected AgentHandler(Gson gson) {
		this.gson = gson;
	}

	public static void setTokenSupplier(Supplier<String> supplier) {
		tokenSupplier = supplier != null ? supplier : () -> null;
	}

	public abstract String getPath();

	protected abstract void handleRequest(HttpExchange exchange) throws IOException;

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		// Indistinguishable-from-generic 404 for every pre-auth failure: cross-origin,
		// non-loopback Host, missing/wrong token. Scanners probing /varp or /scripts/deploy
		// get the same response as /anything-not-here, so endpoint shape cannot be
		// fingerprinted without the token.
		if (exchange.getRequestHeaders().getFirst("Origin") != null) {
			sendOpaqueNotFound(exchange);
			return;
		}

		String host = exchange.getRequestHeaders().getFirst("Host");
		if (host == null || !isLoopbackHost(host)) {
			sendOpaqueNotFound(exchange);
			return;
		}

		String expected = tokenSupplier.get();
		if (expected != null && !expected.isEmpty()) {
			String provided = exchange.getRequestHeaders().getFirst(AUTH_HEADER);
			if (provided == null || !constantTimeEquals(expected, provided)) {
				sendOpaqueNotFound(exchange);
				return;
			}
		}

		long started = System.nanoTime();
		try {
			handleRequest(exchange);
		} catch (BadRequestException e) {
			log.debug("Bad request for {}: {}", exchange.getRequestURI(), e.getMessage());
			sendErrorResponse(exchange, 400, e.getMessage());
		} catch (Exception e) {
			if (isClientDisconnect(e)) {
				log.debug("Client disconnected before {} response could be sent", exchange.getRequestURI());
				return;
			}
			log.error("Error handling {}", exchange.getRequestURI(), e);
			sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
		} finally {
			if (AgentTrace.isEnabled()) {
				AgentTrace.record(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
						exchange.getResponseCode(), (System.nanoTime() - started) / 1_000_000, requestBody.get());
			}
			requestBody.remove();
		}
	}

	private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
		try {
			sendJson(exchange, statusCode, errorResponse(message));
		} catch (IOException ioe) {
			if (!isClientDisconnect(ioe)) {
				throw ioe;
			}
			log.debug("Client disconnected before error response could be sent for {}", exchange.getRequestURI());
		}
	}

	private static boolean isLoopbackHost(String hostHeader) {
		String h = hostHeader.trim();
		if (h.startsWith("[")) {
			int end = h.indexOf(']');
			if (end < 0) return false;
			String ip = h.substring(1, end);
			return "::1".equals(ip) || "0:0:0:0:0:0:0:1".equalsIgnoreCase(ip);
		}
		int colon = h.indexOf(':');
		String name = colon >= 0 ? h.substring(0, colon) : h;
		return "127.0.0.1".equals(name) || "localhost".equalsIgnoreCase(name);
	}

	private static boolean constantTimeEquals(String a, String b) {
		byte[] ab = a.getBytes(StandardCharsets.UTF_8);
		byte[] bb = b.getBytes(StandardCharsets.UTF_8);
		if (ab.length != bb.length) return false;
		int diff = 0;
		for (int i = 0; i < ab.length; i++) {
			diff |= ab[i] ^ bb[i];
		}
		return diff == 0;
	}

	public static boolean isClientDisconnect(Throwable t) {
		while (t != null) {
			if (t instanceof ClientDisconnectException) {
				return true;
			}
			if (t instanceof java.io.IOException) {
				String msg = t.getMessage();
				if (msg != null) {
					String lower = msg.toLowerCase();
					if (lower.contains("broken pipe")
							|| lower.contains("connection reset")
							|| lower.contains("connection closed")
							|| lower.contains("connection was aborted")
							|| lower.contains("forcibly closed")
							|| lower.contains("insufficient bytes written")) {
						return true;
					}
				}
			}
			t = t.getCause();
		}
		return false;
	}

	protected void requireGet(HttpExchange exchange) throws HttpMethodException {
		if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
			throw new HttpMethodException("Method not allowed. Use GET.");
		}
	}

	protected void requirePost(HttpExchange exchange) throws HttpMethodException {
		if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
			throw new HttpMethodException("Method not allowed. Use POST.");
		}
	}

	protected void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
		String json = gson.toJson(body);
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(statusCode, bytes.length);
		writeBody(exchange, bytes);
	}

	protected static void writeBody(HttpExchange exchange, byte[] bytes) throws IOException {
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		} catch (IOException e) {
			throw new ClientDisconnectException(e);
		}
	}

	// Generic response used for any pre-auth failure: the wire shape must not differ
	// from a genuinely absent endpoint, so keep the body and headers minimal and
	// constant. Any variation here re-opens the endpoint-discovery leak this replaced.
	protected void sendOpaqueNotFound(HttpExchange exchange) throws IOException {
		byte[] bytes = "Not Found".getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
		exchange.sendResponseHeaders(404, bytes.length);
		writeBody(exchange, bytes);
	}

	protected Map<String, Object> errorResponse(String message) {
		Map<String, Object> err = new LinkedHashMap<>();
		err.put("error", message);
		return err;
	}

	protected Map<String, String> parseQuery(URI uri) {
		Map<String, String> params = new LinkedHashMap<>();
		String query = uri.getRawQuery();
		if (query == null) return params;
		for (String pair : query.split("&")) {
			String[] kv = pair.split("=", 2);
			if (kv.length == 2) {
				params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
			} else if (kv.length == 1) {
				params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), "");
			}
		}
		return params;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> readJsonBody(HttpExchange exchange) throws IOException {
		String cl = exchange.getRequestHeaders().getFirst("Content-Length");
		if (cl != null) {
			try {
				long advertised = Long.parseLong(cl.trim());
				if (advertised > MAX_BODY_SIZE) {
					throw new BadRequestException("Request body too large");
				}
			} catch (NumberFormatException ignored) {
			}
		}
		byte[] bodyBytes = exchange.getRequestBody().readNBytes(MAX_BODY_SIZE + 1);
		if (bodyBytes.length > MAX_BODY_SIZE) {
			throw new BadRequestException("Request body too large");
		}
		String body = new String(bodyBytes, StandardCharsets.UTF_8);
		requestBody.set(body);
		Map<String, Object> parsed;
		try {
			parsed = gson.fromJson(body, LinkedHashMap.class);
		} catch (JsonParseException e) {
			throw new BadRequestException("Invalid JSON body");
		}
		return parsed != null ? parsed : new LinkedHashMap<>();
	}

	protected static Integer getIntField(Map<String, Object> body, String key) {
		Object val = body.get(key);
		if (val == null) return null;
		if (val instanceof Number) return ((Number) val).intValue();
		if (val instanceof String) {
			try {
				return Integer.parseInt(((String) val).trim());
			} catch (NumberFormatException ignored) {
			}
		}
		throw new BadRequestException("Field '" + key + "' must be a number");
	}

	protected static String getStringField(Map<String, Object> body, String key) {
		Object val = body.get(key);
		if (val == null) return null;
		if (val instanceof String) return (String) val;
		throw new BadRequestException("Field '" + key + "' must be a string");
	}

	protected static Boolean getBooleanField(Map<String, Object> body, String key) {
		Object val = body.get(key);
		if (val == null) return null;
		if (val instanceof Boolean) return (Boolean) val;
		if (val instanceof String) {
			String s = ((String) val).trim();
			if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
			if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
		}
		throw new BadRequestException("Field '" + key + "' must be a boolean");
	}

	protected int getIntParam(Map<String, String> params, String key, int defaultValue) {
		String val = params.get(key);
		if (val == null) return defaultValue;
		try {
			return Integer.parseInt(val);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	protected <T> Map<String, Object> paginate(List<T> items, int offset, int limit, String key) {
		int total = items.size();
		int start = Math.min(offset, total);
		int end = Math.min(offset + limit, total);
		List<T> page = items.subList(start, end);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("count", page.size());
		result.put("total", total);
		result.put("offset", offset);
		result.put("limit", limit);
		result.put(key, page);
		return result;
	}

	protected String getSubPath(HttpExchange exchange, String basePath) {
		String full = exchange.getRequestURI().getPath();
		if (full.length() <= basePath.length()) return "";
		return full.substring(basePath.length());
	}

	public static final class ClientDisconnectException extends IOException {
		public ClientDisconnectException(IOException cause) {
			super(cause.getMessage(), cause);
		}
	}

	public static class BadRequestException extends IllegalArgumentException {
		public BadRequestException(String message) {
			super(message);
		}
	}

	public static class HttpMethodException extends Exception {
		public HttpMethodException(String message) {
			super(message);
		}
	}
}
