package net.runelite.client.plugins.microbot.agentserver.handler;

import net.runelite.api.ChatMessageType;
import net.runelite.client.util.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChatLog {

	static final int CAPACITY = 100;
	private static final Set<ChatMessageType> GAME_TYPES = EnumSet.of(
			ChatMessageType.GAMEMESSAGE,
			ChatMessageType.SPAM,
			ChatMessageType.ENGINE,
			ChatMessageType.MESBOX);

	private static final Deque<Map<String, Object>> lines = new ArrayDeque<>();
	private static long lastSeq;

	private ChatLog() {}

	public static void record(ChatMessageType type, String message) {
		if (type == null || message == null || !GAME_TYPES.contains(type)) {
			return;
		}
		Map<String, Object> line = new LinkedHashMap<>();
		line.put("type", type.name());
		line.put("message", Text.removeTags(message));
		line.put("at", System.currentTimeMillis());
		synchronized (ChatLog.class) {
			line.put("seq", ++lastSeq);
			lines.addLast(line);
			if (lines.size() > CAPACITY) {
				lines.removeFirst();
			}
		}
	}

	static synchronized long cursor() {
		return lastSeq;
	}

	static synchronized List<Map<String, Object>> since(long afterSeq, int limit) {
		List<Map<String, Object>> newer = new ArrayList<>();
		for (Map<String, Object> line : lines) {
			if ((long) line.get("seq") > afterSeq) {
				newer.add(line);
			}
		}
		int from = Math.max(0, newer.size() - limit);
		return new ArrayList<>(newer.subList(from, newer.size()));
	}

	static synchronized void clear() {
		lines.clear();
		lastSeq = 0;
	}
}
