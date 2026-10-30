package net.runelite.client.plugins.microbot.util.dialogues;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public class DialogueChoiceProviderTest {
	private static final List<String> DOOR = List.of("Okay, I won't.", "<col=ffffff>[2] I don't care. I'm going in anyway.</col>");

	@Test
	public void picksTaggedIndexedOption() {
		assertEquals(1, DialogueChoiceProvider.pickIndex(DOOR, List.of("I don't care. I'm going in anyway.")));
	}

	@Test
	public void earlierWantedChoiceWins() {
		assertEquals(0, DialogueChoiceProvider.pickIndex(DOOR, List.of("okay, i won't.", "going in anyway")));
	}

	@Test
	public void fallsBackToContains() {
		assertEquals(1, DialogueChoiceProvider.pickIndex(DOOR, List.of("going in anyway")));
	}

	@Test
	public void noMatchAndNulls() {
		assertEquals(-1, DialogueChoiceProvider.pickIndex(DOOR, List.of("Yes")));
		assertEquals(-1, DialogueChoiceProvider.pickIndex(DOOR, List.of()));
		assertEquals(-1, DialogueChoiceProvider.pickIndex(null, List.of("x")));
	}
}
