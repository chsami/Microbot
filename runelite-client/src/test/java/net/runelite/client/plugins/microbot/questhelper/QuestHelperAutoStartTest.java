package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.microbot.questhelper.managers.QuestManager;
import net.runelite.client.plugins.microbot.questhelper.managers.QuestMenuHandler;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class QuestHelperAutoStartTest
{
	@Mock private Client client;
	@Mock private QuestHelperConfig config;
	@Mock private QuestManager questManager;
	@Mock private QuestMenuHandler questMenuHandler;
	@InjectMocks private QuestHelperPlugin plugin;

	@Before
	public void setUp()
	{
		MockitoAnnotations.initMocks(this);
		when(config.autoStartQuests()).thenReturn(true);
	}

	private void sendMessage(String expanded, ChatMessageType type)
	{
		ChatMessage message = mock(ChatMessage.class);
		when(message.getType()).thenReturn(type);
		when(message.getMessage()).thenReturn("<macro=quest_start>");
		when(client.macroExpand("<macro=quest_start>")).thenReturn(expanded);
		plugin.onChatMessage(message);
	}

	@Test
	public void startsQuestFromExpandedColoredMessage()
	{
		sendMessage("You've started a new quest: <col=ff0000>The Red Reef</col>", ChatMessageType.GAMEMESSAGE);
		verify(questMenuHandler).startUpQuest("The Red Reef");
	}

	@Test
	public void startsSpeedrunWithoutRequiringColorTags()
	{
		sendMessage("You've started a new quest speedrun: Cook's Assistant", ChatMessageType.GAMEMESSAGE);
		verify(questMenuHandler).startUpQuest("Cook's Assistant");
	}

	@Test
	public void ignoresUnrelatedGameMessage()
	{
		sendMessage("You have not started a new quest.", ChatMessageType.GAMEMESSAGE);
		verifyNoInteractions(questMenuHandler);
	}

	@Test
	public void doesNotReplaceActiveHelper()
	{
		when(questManager.getSelectedQuest()).thenReturn(mock(QuestHelper.class));
		sendMessage("You've started a new quest: The Red Reef", ChatMessageType.GAMEMESSAGE);
		verifyNoInteractions(questMenuHandler);
	}

	@Test
	public void respectsDisabledAutoStart()
	{
		when(config.autoStartQuests()).thenReturn(false);
		sendMessage("You've started a new quest: The Red Reef", ChatMessageType.GAMEMESSAGE);
		verifyNoInteractions(questMenuHandler);
	}

	@Test
	public void ignoresPlayerChat()
	{
		sendMessage("You've started a new quest: The Red Reef", ChatMessageType.PUBLICCHAT);
		verifyNoInteractions(questMenuHandler);
	}
}
