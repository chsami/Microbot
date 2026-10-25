/*
 * Copyright (c) 2020, Zoinkwiz <https://github.com/Zoinkwiz>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.questhelper.requirements.conditional;

import net.runelite.client.plugins.microbot.questhelper.requirements.zone.Zone;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.NpcChanged;

import java.util.ArrayList;

public class NpcCondition extends ConditionForStep
{
	private final int npcID;
	private int[] alternateNpcIDs = new int[0];
	private final ArrayList<NPC> npcs = new ArrayList<>();
	private boolean npcInScene = false;
	private final Zone zone;

	@Setter
	private Integer animationIDRequired;

	public NpcCondition(int npcID)
	{
		this.npcID = npcID;
		this.zone = null;
	}

	public NpcCondition(int npcID, WorldPoint worldPoint)
	{
		assert(worldPoint != null);

		this.npcID = npcID;
		this.zone = new Zone(worldPoint, worldPoint);
	}

	public NpcCondition(int npcID, Zone zone)
	{
		assert(zone != null);

		this.npcID = npcID;
		this.zone = zone;
	}

	/**
	 * Also accept these NPC ids (for example the {@code _VIS} sibling of the base id).
	 *
	 * @return this condition
	 */
	public NpcCondition addAlternateNpcs(int... ids)
	{
		this.alternateNpcIDs = ids == null ? new int[0] : ids.clone();
		return this;
	}

	private boolean matchesId(int id)
	{
		if (id == npcID)
		{
			return true;
		}
		for (int alternate : alternateNpcIDs)
		{
			if (id == alternate)
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public void initialize(Client client)
	{
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (matchesId(npc.getId()))
			{
				this.npcs.add(npc);
				npcInScene = true;
			}
		}
	}

	public boolean check(Client client)
	{
		if (zone != null)
		{
			for (NPC npc : npcs)
			{
				if (npc != null)
				{
					if (isInZone(client, npc) && hasCorrectAnimation(npc)) return true;
				}
			}
			return false;
		}
		else
		{
			return npcInScene;
		}
	}

	private boolean isInZone(Client client, NPC npc)
	{
		if (zone == null) return true;

		WorldPoint wp = WorldPoint.fromLocalInstance(client, npc.getLocalLocation());
		if (wp != null)
		{
			return zone.contains(wp);
		}
		return false;
	}

	private boolean hasCorrectAnimation(NPC npc)
	{
		return animationIDRequired == null || npc.getAnimation() == animationIDRequired;
	}

	public void checkNpcSpawned(NPC npc)
	{
		if (matchesId(npc.getId()))
		{
			npcs.add(npc);
			npcInScene = true;
		}
	}

	public void checkNpcDespawned(NPC npc)
	{
		if (npcs.contains(npc))
		{
			npcs.remove(npc);
			npcInScene = false;
		}
	}

	public void checkNpcChanged(NpcChanged npcChanged)
	{
		if (npcs.contains(npcChanged.getNpc()) && !matchesId(npcChanged.getNpc().getId()))
		{
			this.npcs.remove(npcChanged.getNpc());
			npcInScene = false;
		}

		if (matchesId(npcChanged.getNpc().getId()))
		{
			this.npcs.add(npcChanged.getNpc());
			npcInScene = true;
		}
	}

	@Override
	public void updateHandler()
	{
		npcInScene = false;
	}
}
