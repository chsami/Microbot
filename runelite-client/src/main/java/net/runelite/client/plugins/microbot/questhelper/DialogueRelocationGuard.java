package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.api.coords.WorldPoint;

final class DialogueRelocationGuard
{
	static final int RELOCATION_DISTANCE = 32;
	static final long ANCHOR_TTL_MS = 15_000L;
	static final long MAX_HOLD_MS = 20_000L;

	private WorldPoint dialogueAnchor;
	private long dialogueSeenAt;
	private WorldPoint heldAt;
	private long heldSince;

	synchronized void noteDialogue(WorldPoint position, long now)
	{
		if (position == null)
		{
			return;
		}
		boolean anchorLive = dialogueAnchor != null && now - dialogueSeenAt <= ANCHOR_TTL_MS;
		if (anchorLive && isFar(dialogueAnchor, position))
		{
			heldAt = position;
			heldSince = now;
		}
		else if (heldAt != null)
		{
			heldSince = now;
		}
		dialogueAnchor = position;
		dialogueSeenAt = now;
	}

	synchronized boolean shouldHold(WorldPoint position, WorldPoint target, long now)
	{
		WorldPoint anchor = now - dialogueSeenAt <= ANCHOR_TTL_MS ? dialogueAnchor : null;
		dialogueAnchor = null;

		if (position == null || target == null || !isFar(position, target))
		{
			heldAt = null;
			return false;
		}

		if (heldAt != null)
		{
			if (isFar(position, heldAt) || now - heldSince >= MAX_HOLD_MS)
			{
				heldAt = null;
				return false;
			}
			return true;
		}

		if (anchor == null || !isFar(anchor, position))
		{
			return false;
		}

		heldAt = position;
		heldSince = now;
		return true;
	}

	synchronized void reset()
	{
		dialogueAnchor = null;
		dialogueSeenAt = 0;
		heldAt = null;
		heldSince = 0;
	}

	private static boolean isFar(WorldPoint from, WorldPoint to)
	{
		return from.distanceTo2D(to) > RELOCATION_DISTANCE;
	}
}
