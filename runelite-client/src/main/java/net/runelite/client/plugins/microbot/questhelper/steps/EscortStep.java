package net.runelite.client.plugins.microbot.questhelper.steps;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;

import java.util.Arrays;

/**
 * A step where an NPC follows the player to {@code worldPoint}. The automation walks it in short hops at
 * walking pace and talks to the follower again whenever it stops following.
 */
public class EscortStep extends DetailedQuestStep
{
	@Getter
	private final int[] followerIds;

	public EscortStep(QuestHelper questHelper, WorldPoint worldPoint, String text, int[] followerIds, Requirement... requirements)
	{
		super(questHelper, worldPoint, text, requirements);
		this.followerIds = followerIds == null ? new int[0] : Arrays.copyOf(followerIds, followerIds.length);
	}
}
