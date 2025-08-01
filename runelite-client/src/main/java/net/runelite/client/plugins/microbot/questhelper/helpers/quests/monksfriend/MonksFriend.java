/*
 * Copyright (c) 2020, Zoinkwiz
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
package net.runelite.client.plugins.microbot.questhelper.helpers.quests.monksfriend;

import net.runelite.client.plugins.microbot.questhelper.panel.PanelDetails;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.BasicQuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.conditional.Conditions;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.zone.Zone;
import net.runelite.client.plugins.microbot.questhelper.requirements.zone.ZoneRequirement;
import net.runelite.client.plugins.microbot.questhelper.rewards.ExperienceReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.ItemReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.QuestPointReward;
import net.runelite.client.plugins.microbot.questhelper.steps.*;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;

import java.util.*;

public class MonksFriend extends BasicQuestHelper
{
	//Items Required
	ItemRequirement jugOfWater, log, blanket;

	//Items Recommended
	ItemRequirement ardougneCloak;

	Requirement inDungeon;

	QuestStep talkToOmad, goDownLadder, grabBlanket, goUpLadder, returnToOmadWithBlanket, talkToOmadAgain, talkToCedric, talkToCedricWithJug,
		continueTalkingToCedric, talkToCedricWithLog, finishQuest;

	//Zones
	Zone dungeon;

	@Override
	public Map<Integer, QuestStep> loadSteps()
	{
		initializeRequirements();
		setupConditions();
		setupSteps();
		Map<Integer, QuestStep> steps = new HashMap<>();

		steps.put(0, talkToOmad);

		ConditionalStep getBlanket = new ConditionalStep(this, goDownLadder);
		getBlanket.addStep(new Conditions(inDungeon, blanket.alsoCheckBank(questBank)), goUpLadder);
		getBlanket.addStep(blanket.alsoCheckBank(questBank), returnToOmadWithBlanket);
		getBlanket.addStep(inDungeon, grabBlanket);

		steps.put(10, getBlanket);

		steps.put(20, talkToOmadAgain);
		steps.put(30, talkToCedric);
		steps.put(40, talkToCedricWithJug);
		steps.put(50, continueTalkingToCedric);
		steps.put(60, talkToCedricWithLog);
		steps.put(70, finishQuest);

		return steps;
	}

	@Override
	protected void setupRequirements()
	{
		log = new ItemRequirement("Logs", ItemID.LOGS);
		jugOfWater = new ItemRequirement("Jug of Water", ItemID.JUG_WATER);
		blanket = new ItemRequirement("Child's blanket", ItemID.CHILDS_BLANKET);
		ardougneCloak = new ItemRequirement("Ardougne cloak 1 or higher for teleports to the monastery", ItemID.CERT_ARRAVCERTIFICATE).isNotConsumed();
	}

	@Override
	protected void setupZones()
	{
		dungeon = new Zone(new WorldPoint(2559, 9597, 0), new WorldPoint(2582, 9623, 0));
	}

	public void setupConditions()
	{
		inDungeon = new ZoneRequirement(dungeon);
	}

	public void setupSteps()
	{
		talkToOmad = new NpcStep(this, NpcID.BROTHER_OMAD, new WorldPoint(2607, 3211, 0), "Talk to Brother Omad in the monastery south of West Ardougne.");
		talkToOmad.addDialogStep("Why can't you sleep, what's wrong?");
		talkToOmad.addDialogStep("Can I help at all?");
		goDownLadder = new ObjectStep(this, ObjectID.LADDER_OUTSIDE_TO_UNDERGROUND, new WorldPoint(2561, 3222, 0), "Go down the ladder in a circle of stones west of the monastery.");
		grabBlanket = new DetailedQuestStep(this, new WorldPoint(2570, 9604, 0), "Pick up the Child's blanket in the room to the south.", blanket);
		goUpLadder = new ObjectStep(this, ObjectID.LADDER_FROM_CELLAR, new WorldPoint(2561, 9622, 0), "Go back up the ladder.");
		returnToOmadWithBlanket = new NpcStep(this, NpcID.BROTHER_OMAD, new WorldPoint(2607, 3211, 0), "Bring the blanket back to Brother Omad.", blanket);
		talkToOmadAgain = new NpcStep(this, NpcID.BROTHER_OMAD, new WorldPoint(2607, 3211, 0), "Talk to Brother Omad again.");
		talkToOmadAgain.addDialogStep("Is there anything else I can help with?");
		talkToOmadAgain.addDialogStep("Who's Brother Cedric?");
		talkToOmadAgain.addDialogStep("Where should I look?");
		talkToCedric = new NpcStep(this, NpcID.BROTHER_CEDRIC, new WorldPoint(2614, 3258, 0), "Talk to Brother Cedric north of the monastery.");
		talkToCedricWithJug = new NpcStep(this, NpcID.BROTHER_CEDRIC, new WorldPoint(2614, 3258, 0), "Talk to Brother Cedric again.", jugOfWater);
		talkToCedricWithJug.addDialogStep("Yes, I'd be happy to!");
		continueTalkingToCedric = new NpcStep(this, NpcID.BROTHER_CEDRIC, new WorldPoint(2614, 3258, 0), "Talk to Brother Cedric again.");
		continueTalkingToCedric.addDialogStep("Yes, I'd be happy to!");
		talkToCedricWithJug.addSubSteps(continueTalkingToCedric);
		talkToCedricWithLog = new NpcStep(this, NpcID.BROTHER_CEDRIC, new WorldPoint(2614, 3258, 0), "Talk to Brother Cedric once again with logs.", log);
		finishQuest = new NpcStep(this, NpcID.BROTHER_OMAD, new WorldPoint(2607, 3211, 0), "Return to Brother Omad to finish the quest.");
	}

	@Override
	public List<ItemRequirement> getItemRequirements()
	{
		ArrayList<ItemRequirement> reqs = new ArrayList<>();
		reqs.add(jugOfWater);
		reqs.add(log);
		return reqs;
	}

	@Override
	public List<ItemRequirement> getItemRecommended()
	{
		ArrayList<ItemRequirement> reqs = new ArrayList<>();
		reqs.add(ardougneCloak);
		return reqs;
	}

	@Override
	public QuestPointReward getQuestPointReward()
	{
		return new QuestPointReward(1);
	}

	@Override
	public List<ExperienceReward> getExperienceRewards()
	{
		return Collections.singletonList(new ExperienceReward(Skill.WOODCUTTING, 2000));
	}

	@Override
	public List<ItemReward> getItemRewards()
	{
		return Collections.singletonList(new ItemReward("Law Runes", ItemID.LAWRUNE, 8));
	}

	@Override
	public List<PanelDetails> getPanels()
	{
		List<PanelDetails> allSteps = new ArrayList<>();
		allSteps.add(new PanelDetails("Starting off", Collections.singletonList(talkToOmad), jugOfWater, log));
		allSteps.add(new PanelDetails("Finding the blanket", Arrays.asList(goDownLadder, grabBlanket, goUpLadder, returnToOmadWithBlanket)));
		allSteps.add(new PanelDetails("Help Cedric", Arrays.asList(talkToOmadAgain, talkToCedric, talkToCedricWithJug, talkToCedricWithLog, finishQuest)));

		return allSteps;
	}
}
