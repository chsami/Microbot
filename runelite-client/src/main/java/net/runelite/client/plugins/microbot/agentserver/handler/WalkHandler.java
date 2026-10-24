package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class WalkHandler extends AgentHandler {

	private static final int DEFAULT_TIMEOUT_SECONDS = 30;
	private static final int MAX_TIMEOUT_SECONDS = 600;
	private static final int DEFAULT_REACHED_DISTANCE = 0;
	private static final int MAX_REACHED_DISTANCE = 20;

	private static final AtomicInteger WALK_THREAD_COUNT = new AtomicInteger(1);
	private static final ExecutorService WALK_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "AgentServer-Walk-" + WALK_THREAD_COUNT.getAndIncrement());
		t.setDaemon(true);
		return t;
	});

	private static volatile Future<WalkerState> activeWalk;
	private static volatile WorldPoint activeWalkTarget;
	private static volatile int activeWalkReachedDistance;

	public WalkHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return "/walk";
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
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

		Integer xNum = getIntField(body, "x");
		Integer yNum = getIntField(body, "y");
		if (xNum == null || yNum == null) {
			sendJson(exchange, 400, errorResponse("Missing required fields: x, y"));
			return;
		}

		Integer planeNum = getIntField(body, "plane");
		int plane = planeNum != null ? planeNum.intValue() : 0;

		boolean wait = Boolean.TRUE.equals(getBooleanField(body, "wait"));
		int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
		Integer timeoutNum = getIntField(body, "timeout");
		if (timeoutNum != null) {
			timeoutSeconds = timeoutNum;
			if (timeoutSeconds <= 0) timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
			if (timeoutSeconds > MAX_TIMEOUT_SECONDS) timeoutSeconds = MAX_TIMEOUT_SECONDS;
		}

		int reachedDistance = DEFAULT_REACHED_DISTANCE;
		Integer distanceNum = getIntField(body, "distance");
		if (distanceNum != null) {
			reachedDistance = distanceNum;
			if (reachedDistance < 0) reachedDistance = DEFAULT_REACHED_DISTANCE;
			if (reachedDistance > MAX_REACHED_DISTANCE) reachedDistance = MAX_REACHED_DISTANCE;
		}

		WorldPoint destination = new WorldPoint(xNum.intValue(), yNum.intValue(), plane);

		Future<WalkerState> walkFuture = submitWalk(destination, reachedDistance);

		Map<String, Object> response = new LinkedHashMap<>();
		Map<String, Integer> dest = new LinkedHashMap<>();
		dest.put("x", destination.getX());
		dest.put("y", destination.getY());
		dest.put("plane", destination.getPlane());
		response.put("destination", dest);
		response.put("reachedDistance", reachedDistance);

		if (!wait) {
			response.put("success", true);
			response.put("walking", true);
			response.put("message", "Walk initiated");
			addPlayerPosition(response);
			sendJson(exchange, 200, response);
			return;
		}

		WalkerState state = null;
		boolean timedOut;
		boolean arrivedByPosition = false;
		try {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
			while (true) {
				if (walkFuture.isDone()) {
					state = walkFuture.get();
					timedOut = false;
					break;
				}

				int distanceToDestination = distanceTo(destination);
				if (distanceToDestination >= 0 && distanceToDestination <= reachedDistance) {
					state = WalkerState.ARRIVED;
					arrivedByPosition = true;
					timedOut = false;
					walkFuture.cancel(true);
					clearActiveWalk(walkFuture);
					break;
				}

				long remainingNanos = deadline - System.nanoTime();
				if (remainingNanos <= 0) {
					timedOut = true;
					break;
				}

				Thread.sleep(Math.min(100, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			response.put("success", false);
			response.put("error", "Walk interrupted");
			addPlayerPosition(response);
			sendJson(exchange, 500, response);
			return;
		} catch (Exception e) {
			log.warn("Walk failed: {}", e.toString());
			response.put("success", false);
			response.put("error", "Walk failed: " + e.getMessage());
			addPlayerPosition(response);
			sendJson(exchange, 500, response);
			return;
		}

		if (timedOut) {
			int distanceToDestination = distanceTo(destination);
			response.put("success", false);
			response.put("walking", true);
			response.put("timedOut", true);
			response.put("message", "Walk did not complete within " + timeoutSeconds + "s; still in progress");
			if (distanceToDestination >= 0) {
				response.put("distanceToDestination", distanceToDestination);
			}
		} else {
			int distanceToDestination = distanceTo(destination);
			response.put("success", state == WalkerState.ARRIVED && distanceToDestination <= reachedDistance);
			response.put("walking", false);
			response.put("state", state == null ? "UNKNOWN" : state.name());
			if (arrivedByPosition) {
				response.put("arrivedByPosition", true);
			}
			if (distanceToDestination >= 0) {
				response.put("distanceToDestination", distanceToDestination);
			}
		}
		if (!Boolean.TRUE.equals(response.get("success"))) {
			response.put("route", PathHandler.serializeActive(Rs2PathApi.getActiveRouteStatus(), false));
		}
		addPlayerPosition(response);
		sendJson(exchange, 200, response);
	}

	private static synchronized Future<WalkerState> submitWalk(WorldPoint destination, int reachedDistance) {
		if (activeWalk != null && !activeWalk.isDone()
			&& destination.equals(activeWalkTarget)
			&& reachedDistance == activeWalkReachedDistance) {
			return activeWalk;
		}
		if (activeWalk != null && !activeWalk.isDone()) {
			activeWalk.cancel(true);
			Rs2Walker.setTarget(null);
		}
		activeWalkTarget = destination;
		activeWalkReachedDistance = reachedDistance;
		activeWalk = WALK_EXECUTOR.submit(() -> Rs2Walker.walkWithState(destination, reachedDistance));
		return activeWalk;
	}

	private static synchronized void clearActiveWalk(Future<WalkerState> walkFuture) {
		if (activeWalk == walkFuture) {
			activeWalk = null;
			activeWalkTarget = null;
			activeWalkReachedDistance = DEFAULT_REACHED_DISTANCE;
		}
	}

	private static int distanceTo(WorldPoint destination) {
		WorldPoint playerPos = Microbot.getRs2PlayerStateCache().getLocalPlayerPosition();
		return playerPos == null ? -1 : playerPos.distanceTo(destination);
	}

	private static void addPlayerPosition(Map<String, Object> response) {
		WorldPoint playerPos = Microbot.getRs2PlayerStateCache().getLocalPlayerPosition();
		if (playerPos != null) {
			Map<String, Integer> pos = new LinkedHashMap<>();
			pos.put("x", playerPos.getX());
			pos.put("y", playerPos.getY());
			pos.put("plane", playerPos.getPlane());
			response.put("playerPosition", pos);
		}
	}
}
