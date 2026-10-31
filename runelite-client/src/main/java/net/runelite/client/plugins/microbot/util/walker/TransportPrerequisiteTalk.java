package net.runelite.client.plugins.microbot.util.walker;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.List;
import java.util.Optional;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;

import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Talks to the NPC a gated transport object wants a conversation with (see
 * {@link net.runelite.client.plugins.microbot.util.walker.transport.TransportLandingRules#talkFirstNpc}) and finishes the dialogue: continue only, or the single option offered.
 * Kept out of Rs2WalkerTransports so its widget reads stay on the client thread and add no lambdas there.
 */
final class TransportPrerequisiteTalk
{
	private static final int OPEN_WAIT_MS = 10_000;
	private static final long DIALOGUE_LIMIT_MS = 30_000;

	private TransportPrerequisiteTalk()
	{
	}

	static boolean talkTo(String npcName)
	{
		WebWalkLog.spInfo("transport prerequisite | talking to {} before retrying", npcName);
		if (!Rs2Npc.interact(npcName, "Talk-to"))
		{
			return false;
		}
		sleepUntil(Rs2Dialogue::isInDialogue, OPEN_WAIT_MS);
		long deadline = System.currentTimeMillis() + DIALOGUE_LIMIT_MS;
		while (Rs2Dialogue.isInDialogue() && System.currentTimeMillis() < deadline)
		{
			if (Rs2Dialogue.hasContinue())
			{
				Rs2Dialogue.clickContinue();
			}
			else if (Rs2Dialogue.hasSelectAnOption())
			{
				Optional<String> only = soleOptionText();
				if (only.isPresent())
				{
					Rs2Dialogue.clickOption(only.get());
				}
				else
				{
					sleep(300);
				}
			}
			else
			{
				sleep(300);
			}
			sleep(600);
		}
		return !Rs2Dialogue.isInDialogue();
	}

	/** Waits until the player stops animating (default wait). Lives here so Rs2WalkerTransports gains no lambda. */
	static void awaitIdle()
	{
		sleepUntil(TransportPrerequisiteTalk::notAnimating);
	}

	private static boolean notAnimating()
	{
		return !Rs2Player.isAnimating();
	}

	/** The text of the one option on offer, read on the client thread; empty when there are none or several. */
	private static Optional<String> soleOptionText()
	{
		String text = Microbot.getClientThread().runOnClientThreadOptional(TransportPrerequisiteTalk::readSoleOption).orElse("");
		return text.isEmpty() ? Optional.empty() : Optional.of(text);
	}

	private static String readSoleOption()
	{
		List<Widget> options = Rs2Dialogue.getDialogueOptions();
		String found = null;
		int count = 0;
		for (Widget option : options)
		{
			String optionText = option.getText();
			if (optionText != null)
			{
				count++;
				found = optionText;
			}
		}
		return count == 1 ? found : "";
	}
}
