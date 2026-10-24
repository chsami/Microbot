package net.runelite.client.plugins.microbot.agentserver.handler;

import net.runelite.api.ChatMessageType;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ChatLogTest {

	@After
	public void cleanUp() {
		ChatLog.clear();
	}

	@Test
	public void keepsGameMessagesAndDropsPlayerChat() {
		ChatLog.record(ChatMessageType.GAMEMESSAGE, "Nothing interesting happens.");
		ChatLog.record(ChatMessageType.PUBLICCHAT, "hello");
		ChatLog.record(ChatMessageType.PRIVATECHAT, "secret");

		List<Map<String, Object>> lines = ChatLog.since(0, 10);
		assertEquals(1, lines.size());
		assertEquals("Nothing interesting happens.", lines.get(0).get("message"));
		assertEquals("GAMEMESSAGE", lines.get(0).get("type"));
	}

	@Test
	public void stripsColourTags() {
		ChatLog.record(ChatMessageType.GAMEMESSAGE, "<col=ef1020>You can't reach that.</col>");
		assertEquals("You can't reach that.", ChatLog.since(0, 10).get(0).get("message"));
	}

	@Test
	public void cursorReturnsOnlyNewerLines() {
		ChatLog.record(ChatMessageType.GAMEMESSAGE, "one");
		long cursor = ChatLog.cursor();
		ChatLog.record(ChatMessageType.SPAM, "two");
		ChatLog.record(ChatMessageType.GAMEMESSAGE, "three");

		List<Map<String, Object>> lines = ChatLog.since(cursor, 10);
		assertEquals(2, lines.size());
		assertEquals("two", lines.get(0).get("message"));
		assertEquals("three", lines.get(1).get("message"));
	}

	@Test
	public void limitKeepsTheNewestLines() {
		for (int i = 0; i < 5; i++) {
			ChatLog.record(ChatMessageType.GAMEMESSAGE, "line " + i);
		}
		List<Map<String, Object>> lines = ChatLog.since(0, 2);
		assertEquals("line 3", lines.get(0).get("message"));
		assertEquals("line 4", lines.get(1).get("message"));
	}

	@Test
	public void bufferIsBounded() {
		for (int i = 0; i < ChatLog.CAPACITY + 25; i++) {
			ChatLog.record(ChatMessageType.GAMEMESSAGE, "line " + i);
		}
		List<Map<String, Object>> lines = ChatLog.since(0, Integer.MAX_VALUE);
		assertEquals(ChatLog.CAPACITY, lines.size());
		assertEquals("line 25", lines.get(0).get("message"));
		assertEquals((long) ChatLog.CAPACITY + 25, ChatLog.cursor());
	}

	@Test
	public void observeChatDefaultsToRecentTailAndHonoursCursor() {
		for (int i = 0; i < 15; i++) {
			ChatLog.record(ChatMessageType.GAMEMESSAGE, "line " + i);
		}
		assertEquals(10, ((List<?>) ObserveHandler.chat(-1).get("messages")).size());
		assertEquals(3, ((List<?>) ObserveHandler.chat(12).get("messages")).size());
		assertEquals(15L, ObserveHandler.chat(-1).get("cursor"));
	}
}
