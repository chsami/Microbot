package net.runelite.client.plugins.microbot.agentserver.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AgentLogsTest {

	@After
	public void cleanUp() {
		AgentLogs.clearAll();
	}

	@Test
	public void keepsGameMessagesAndDropsPlayerChat() {
		AgentLogs.recordChat(ChatMessageType.GAMEMESSAGE, "Nothing interesting happens.");
		AgentLogs.recordChat(ChatMessageType.PUBLICCHAT, "hello");
		AgentLogs.recordChat(ChatMessageType.PRIVATECHAT, "secret");

		List<Map<String, Object>> lines = AgentLogs.CHAT.since(0, 10);
		assertEquals(1, lines.size());
		assertEquals("Nothing interesting happens.", lines.get(0).get("message"));
		assertEquals("GAMEMESSAGE", lines.get(0).get("type"));
	}

	@Test
	public void stripsColourTags() {
		AgentLogs.recordChat(ChatMessageType.GAMEMESSAGE, "<col=ef1020>You can't reach that.</col>");
		assertEquals("You can't reach that.", AgentLogs.CHAT.since(0, 10).get(0).get("message"));
	}

	@Test
	public void cursorReturnsOnlyNewerEntries() {
		AgentLogs.recordChat(ChatMessageType.GAMEMESSAGE, "one");
		long cursor = AgentLogs.CHAT.cursor();
		AgentLogs.recordChat(ChatMessageType.SPAM, "two");
		AgentLogs.recordChat(ChatMessageType.GAMEMESSAGE, "three");

		List<Map<String, Object>> lines = AgentLogs.CHAT.since(cursor, 10);
		assertEquals(2, lines.size());
		assertEquals("two", lines.get(0).get("message"));
		assertEquals("three", lines.get(1).get("message"));
	}

	@Test
	public void limitKeepsTheNewestEntriesAndBufferIsBounded() {
		RecentLog log = new RecentLog(3);
		for (int i = 0; i < 5; i++) {
			log.add(new java.util.LinkedHashMap<>(Map.of("n", i)));
		}
		List<Map<String, Object>> all = log.since(0, 10);
		assertEquals(3, all.size());
		assertEquals(2, all.get(0).get("n"));
		assertEquals(5L, log.cursor());
		assertEquals(4, log.since(0, 1).get(0).get("n"));
	}

	@Test
	public void observeStreamDefaultsToTailAndHonoursCursor() {
		for (int i = 0; i < 15; i++) {
			AgentLogs.recordChat(ChatMessageType.GAMEMESSAGE, "line " + i);
		}
		assertEquals(10, ((List<?>) ObserveHandler.stream(AgentLogs.CHAT, "messages", -1, 10).get("messages")).size());
		assertEquals(3, ((List<?>) ObserveHandler.stream(AgentLogs.CHAT, "messages", 12, 10).get("messages")).size());
		assertEquals(15L, ObserveHandler.stream(AgentLogs.CHAT, "messages", -1, 10).get("cursor"));
	}

	@Test
	public void recordsMenuClicksWithoutTags() {
		MenuEntry entry = mock(MenuEntry.class);
		when(entry.getOption()).thenReturn("Chop down");
		when(entry.getTarget()).thenReturn("<col=ffff>Oak tree");
		when(entry.getType()).thenReturn(MenuAction.GAME_OBJECT_FIRST_OPTION);
		when(entry.getIdentifier()).thenReturn(10820);
		when(entry.getParam0()).thenReturn(51);
		when(entry.getParam1()).thenReturn(52);
		when(entry.getItemId()).thenReturn(-1);

		AgentLogs.recordMenu(new MenuOptionClicked(entry));

		Map<String, Object> click = AgentLogs.MENU.since(0, 1).get(0);
		assertEquals("Chop down", click.get("option"));
		assertEquals("Oak tree", click.get("target"));
		assertEquals("GAME_OBJECT_FIRST_OPTION", click.get("action"));
		assertEquals(10820, click.get("id"));
		assertEquals(51, click.get("param0"));
	}

	@Test
	public void recordsVarbitAndVarpChanges() {
		VarbitChanged varbit = new VarbitChanged();
		varbit.setVarbitId(4070);
		varbit.setVarpId(439);
		varbit.setValue(3);
		VarbitChanged varp = new VarbitChanged();
		varp.setVarpId(281);
		varp.setValue(1000);

		AgentLogs.recordVar(varbit);
		AgentLogs.recordVar(varp);

		List<Map<String, Object>> changes = AgentLogs.VARS.since(0, 10);
		assertEquals(4070, changes.get(0).get("varbit"));
		assertEquals(3, ((Number) changes.get(0).get("value")).intValue());
		assertFalse(changes.get(1).containsKey("varbit"));
		assertEquals(281, changes.get(1).get("varp"));
	}

	@Test
	public void lastErrorIsMatchedByScriptPackage() {
		AgentLogs.recordError(error("com.example.woodcutting.WoodcuttingScript", new IllegalStateException("no axe")));
		AgentLogs.recordError(error("com.example.other.OtherScript", new RuntimeException("other")));
		AgentLogs.recordError(error("com.example.woodcutting.WoodcuttingScript", new NullPointerException("tree")));

		Map<String, Object> last = AgentLogs.lastErrorFor("com.example.woodcutting.WoodcuttingScript");
		assertNotNull(last);
		assertEquals("java.lang.NullPointerException", last.get("exception"));
		assertEquals(2, last.get("recentCount"));
		assertNull(AgentLogs.lastErrorFor("com.example.mining.MiningScript"));
	}

	@Test
	public void errorFramesAttributeErrorsLoggedElsewhere() {
		Exception thrown = new IllegalArgumentException("bad");
		thrown.setStackTrace(new StackTraceElement[]{
				new StackTraceElement("java.util.Objects", "requireNonNull", "Objects.java", 1),
				new StackTraceElement("com.example.fishing.FishingScript", "loop", "FishingScript.java", 42)});
		AgentLogs.recordError(error("net.runelite.client.plugins.microbot.Script", thrown));

		Map<String, Object> last = AgentLogs.lastErrorFor("com.example.fishing.FishingPlugin");
		assertNotNull(last);
		assertEquals(List.of("com.example.fishing.FishingScript.loop:42"), last.get("frames"));
	}

	@Test
	public void ignoresWarningsAndOwnLogger() {
		AgentLogs.recordError(event("com.example.a.AScript", Level.WARN, null));
		AgentLogs.recordError(error(AgentLogs.class.getName(), null));
		assertEquals(0, AgentLogs.ERRORS.since(0, 10).size());
	}

	private static LoggingEvent error(String logger, Throwable thrown) {
		return event(logger, Level.ERROR, thrown);
	}

	private static LoggingEvent event(String logger, Level level, Throwable thrown) {
		LoggerContext context = new LoggerContext();
		return new LoggingEvent(logger, context.getLogger(logger), level, "failed", thrown, null);
	}
}
