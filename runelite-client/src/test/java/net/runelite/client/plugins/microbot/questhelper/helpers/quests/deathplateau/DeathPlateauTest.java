package net.runelite.client.plugins.microbot.questhelper.helpers.quests.deathplateau;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.RequirementValidator;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeathPlateauTest
{
	private final Map<String, Object> profile = new HashMap<>();
	private ConfigManager profileConfig;
	private Client client;

	@Before
	public void setUp()
	{
		profileConfig = mock(ConfigManager.class);
		when(profileConfig.getRSProfileKey()).thenReturn("profile");
		when(profileConfig.getRSProfileConfiguration(anyString(), anyString())).thenAnswer(invocation ->
		{
			Object value = profile.get(invocation.<String>getArgument(1));
			return value == null ? null : value.toString();
		});
		doAnswer(invocation ->
		{
			profile.put(invocation.getArgument(1), invocation.getArgument(2));
			return null;
		}).when(profileConfig).setRSProfileConfiguration(anyString(), anyString(), any());
		client = mock(Client.class);
	}

	@Test
	public void freshHelperHasNotPassedEitherStep()
	{
		DeathPlateau deathPlateau = load();

		assertFalse(deathPlateau.givenHaroldBlurberry.check(client));
		assertFalse(deathPlateau.isFarEnough.check(client));
	}

	@Test
	public void pathCheckSurvivesAHelperReload()
	{
		DeathPlateau deathPlateau = load();
		receive(deathPlateau.isFarEnough, new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", "You should go and speak to Denulth.", "", 0));

		assertTrue(deathPlateau.isFarEnough.check(client));
		assertTrue(load().isFarEnough.check(client));
		assertFalse(load().givenHaroldBlurberry.check(client));
	}

	@Test
	public void blurberryHandInSurvivesAHelperReload()
	{
		DeathPlateau deathPlateau = load();
		receive(deathPlateau.givenHaroldBlurberry, new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", "You give Harold a Blurberry Special.", "", 0));

		assertTrue(deathPlateau.givenHaroldBlurberry.check(client));
		assertTrue(load().givenHaroldBlurberry.check(client));
		assertFalse(load().isFarEnough.check(client));
	}

	@Test
	public void haroldRefusingAnotherDrinkCountsAsBlurberryGiven()
	{
		DeathPlateau deathPlateau = load();
		receive(deathPlateau.givenHaroldBlurberry, new ChatMessage(null, ChatMessageType.DIALOG, "", "Harold|I fink I've had enough!", "", 0));

		assertTrue(load().givenHaroldBlurberry.check(client));
	}

	@Test
	public void anotherSpeakerSayingTheLineDoesNotCount()
	{
		DeathPlateau deathPlateau = load();
		receive(deathPlateau.givenHaroldBlurberry, new ChatMessage(null, ChatMessageType.DIALOG, "", "Eohric|I fink I've had enough!", "", 0));

		assertFalse(load().givenHaroldBlurberry.check(client));
	}

	private void receive(Requirement requirement, ChatMessage message)
	{
		RequirementValidator validator = new RequirementValidator(client, null, requirement);
		validator.startUp();
		validator.handleChatMessage(message);
		validator.onGameTick(new GameTick());
	}

	private DeathPlateau load()
	{
		DeathPlateau deathPlateau = new DeathPlateau()
		{
			{
				configManager = profileConfig;
			}
		};
		deathPlateau.setConfig(mock(QuestHelperConfig.class));
		deathPlateau.loadSteps();
		return deathPlateau;
	}
}
