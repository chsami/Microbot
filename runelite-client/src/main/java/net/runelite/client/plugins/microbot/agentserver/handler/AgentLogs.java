package net.runelite.client.plugins.microbot.agentserver.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentLogs {

	public static final RecentLog CHAT = new RecentLog(100);
	public static final RecentLog MENU = new RecentLog(50);
	public static final RecentLog VARS = new RecentLog(500);
	public static final RecentLog ERRORS = new RecentLog(50);

	private static final int MAX_FRAMES = 8;
	private static final int MAX_MESSAGE = 300;
	private static final Set<ChatMessageType> GAME_CHAT = EnumSet.of(
			ChatMessageType.GAMEMESSAGE,
			ChatMessageType.SPAM,
			ChatMessageType.ENGINE,
			ChatMessageType.MESBOX);

	private AgentLogs() {}

	public static void recordChat(ChatMessageType type, String message) {
		if (type == null || message == null || !GAME_CHAT.contains(type)) {
			return;
		}
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("type", type.name());
		entry.put("message", Text.removeTags(message));
		CHAT.add(entry);
	}

	public static void recordMenu(MenuOptionClicked event) {
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("option", plain(event.getMenuOption()));
		entry.put("target", plain(event.getMenuTarget()));
		entry.put("action", event.getMenuAction() == null ? null : event.getMenuAction().name());
		entry.put("id", event.getId());
		entry.put("itemId", event.getItemId());
		entry.put("param0", event.getParam0());
		entry.put("param1", event.getParam1());
		MENU.add(entry);
	}

	public static void recordVar(VarbitChanged event) {
		Map<String, Object> entry = new LinkedHashMap<>();
		if (event.getVarbitId() != -1) {
			entry.put("varbit", event.getVarbitId());
		}
		entry.put("varp", event.getVarpId());
		entry.put("value", event.getValue());
		VARS.add(entry);
	}

	public static void recordError(ILoggingEvent event) {
		if (!event.getLevel().isGreaterOrEqual(Level.ERROR)
				|| event.getLoggerName().startsWith(AgentLogs.class.getPackageName())) {
			return;
		}
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("logger", event.getLoggerName());
		entry.put("thread", event.getThreadName());
		entry.put("message", truncate(event.getFormattedMessage()));
		IThrowableProxy root = event.getThrowableProxy();
		while (root != null && root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		if (root != null) {
			entry.put("exception", root.getClassName());
			entry.put("exceptionMessage", truncate(root.getMessage()));
			entry.put("frames", frames(root));
		}
		ERRORS.add(entry);
	}

	public static Map<String, Object> lastErrorFor(String className) {
		int dot = className.lastIndexOf('.');
		if (dot < 0) {
			return null;
		}
		String pkg = className.substring(0, dot + 1);
		List<Map<String, Object>> matches = ERRORS.since(0, ERRORS.capacity(), entry -> mentions(entry, pkg));
		if (matches.isEmpty()) {
			return null;
		}
		Map<String, Object> last = new LinkedHashMap<>(matches.get(matches.size() - 1));
		last.put("recentCount", matches.size());
		return last;
	}

	@SuppressWarnings("unchecked")
	private static boolean mentions(Map<String, Object> entry, String pkg) {
		if (((String) entry.get("logger")).startsWith(pkg)) {
			return true;
		}
		Object frames = entry.get("frames");
		if (frames instanceof List) {
			for (String frame : (List<String>) frames) {
				if (frame.startsWith(pkg)) {
					return true;
				}
			}
		}
		return false;
	}

	private static List<String> frames(IThrowableProxy proxy) {
		List<String> frames = new ArrayList<>();
		StackTraceElementProxy[] stack = proxy.getStackTraceElementProxyArray();
		if (stack == null) {
			return frames;
		}
		for (StackTraceElementProxy element : stack) {
			StackTraceElement frame = element.getStackTraceElement();
			String cls = frame.getClassName();
			if (cls.startsWith("java.") || cls.startsWith("javax.") || cls.startsWith("jdk.") || cls.startsWith("sun.")) {
				continue;
			}
			frames.add(cls + "." + frame.getMethodName() + ":" + frame.getLineNumber());
			if (frames.size() == MAX_FRAMES) {
				break;
			}
		}
		return frames;
	}

	private static String plain(String text) {
		return text == null ? null : Text.removeTags(text);
	}

	private static String truncate(String text) {
		if (text == null || text.length() <= MAX_MESSAGE) {
			return text;
		}
		return text.substring(0, MAX_MESSAGE) + "…";
	}

	static void clearAll() {
		CHAT.clear();
		MENU.clear();
		VARS.clear();
		ERRORS.clear();
	}
}
