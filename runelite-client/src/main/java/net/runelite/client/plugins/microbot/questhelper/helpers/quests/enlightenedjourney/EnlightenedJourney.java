/*
 * Copyright (c) 2021, Zoinkwiz <https://github.com/Zoinkwiz>
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
package net.runelite.client.plugins.microbot.questhelper.helpers.quests.enlightenedjourney;

import net.runelite.client.plugins.microbot.questhelper.collections.ItemCollections;
import net.runelite.client.plugins.microbot.questhelper.panel.PanelDetails;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.BasicQuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.conditional.Conditions;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.player.SkillRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.quest.QuestPointRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.util.LogicType;
import net.runelite.client.plugins.microbot.questhelper.requirements.var.VarbitRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.widget.WidgetTextRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.zone.Zone;
import net.runelite.client.plugins.microbot.questhelper.requirements.zone.ZoneRequirement;
import net.runelite.client.plugins.microbot.questhelper.rewards.ExperienceReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.ItemReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.QuestPointReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.UnlockReward;
import net.runelite.client.plugins.microbot.questhelper.steps.*;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;

import java.util.*;

public class EnlightenedJourney extends BasicQuestHelper
{
	ItemRequirement papyrus3, ballOfWool, sackOfPotatoes, emptySack8, unlitCandle, yellowDye, redDye, silk10, bowl,
		logs10, tinderbox, willowBranches12, papyrus, papyrus2;

	ItemRequirement draynorTeleport;

	ItemRequirement balloonStructure, origamiBalloon, sandbag8;

	Requirement onEntrana, hasSandbags, flying;

	Zone entrana;

	QuestStep travelToEntrana, talkToAuguste, usePapyrusOnWool, useCandleOnBalloon, talkToAugusteAgain,
		talkToAugusteWithPapyrus, talkToAugusteAfterMob;

	QuestStep fillSacks, talkToAugusteWithDye, talkToAugusteWithBranches,
		talkToAugusteWithLogsAndTinderbox, talkToAugusteToFinish;

	DetailedOwnerStep doPuzzle;


	@Override
	public Map<Integer, QuestStep> loadSteps()
	{
		initializeRequirements();
		setupConditions();
		setupSteps();

		Map<Integer, QuestStep> steps = new HashMap<>();

		ConditionalStep startingOff = new ConditionalStep(this, travelToEntrana);
		startingOff.addStep(onEntrana, talkToAuguste);
		steps.put(0, startingOff);
		steps.put(5, startingOff);
		steps.put(6, startingOff);
		steps.put(10, startingOff);

		ConditionalStep makingPrototype = new ConditionalStep(this, usePapyrusOnWool);
		makingPrototype.addStep(origamiBalloon, talkToAugusteAgain);
		makingPrototype.addStep(balloonStructure, useCandleOnBalloon);
		steps.put(20, makingPrototype);

		steps.put(40, talkToAugusteWithPapyrus);
		steps.put(60, talkToAugusteAfterMob);

		ConditionalStep gettingFinalMaterials = new ConditionalStep(this, fillSacks);
		gettingFinalMaterials.addStep(hasSandbags, talkToAugusteWithDye);
		steps.put(70, gettingFinalMaterials);

		steps.put(80, talkToAugusteWithBranches);

		ConditionalStep flight = new ConditionalStep(this, talkToAugusteWithLogsAndTinderbox);
		flight.addStep(flying, doPuzzle);
		steps.put(90, flight);

		steps.put(100, talkToAugusteToFinish);

		return steps;
	}

	@Override
	protected void setupRequirements()
	{
		papyrus3 = new ItemRequirement("Papyrus", ItemID.PAPYRUS, 3);
		papyrus2 = new ItemRequirement("Papyrus", ItemID.PAPYRUS, 2);
		papyrus = new ItemRequirement("Papyrus", ItemID.PAPYRUS);
		ballOfWool = new ItemRequirement("Ball of wool", ItemID.BALL_OF_WOOL);
		sackOfPotatoes = new ItemRequirement("Sack of potatoes (10)", ItemID.SACK_POTATO_10);
		emptySack8 = new ItemRequirement("Empty sack", ItemID.SACK_EMPTY, 8);
		emptySack8.addAlternates(ItemID.ZEP_SANDBAG);
		unlitCandle = new ItemRequirement("Unlit candle", ItemID.UNLIT_CANDLE);
		unlitCandle.addAlternates(ItemID.UNLIT_BLACK_CANDLE);
		yellowDye = new ItemRequirement("Yellow dye", ItemID.YELLOWDYE);
		redDye = new ItemRequirement("Red dye", ItemID.REDDYE);
		silk10 = new ItemRequirement("Silk", ItemID.SILK, 10);
		bowl = new ItemRequirement("Bowl", ItemID.BOWL_EMPTY);
		logs10 = new ItemRequirement("Logs", ItemID.LOGS, 10);
		tinderbox = new ItemRequirement("Tinderbox", ItemID.TINDERBOX).isNotConsumed();
		willowBranches12 = new ItemRequirement("Willow branches", ItemID.WILLOW_BRANCH, 12);
		willowBranches12.setTooltip("You can get these by using secateurs on a willow tree you've grown. Auguste will" +
			" give you a sapling to grow during the quest if you need one");

		draynorTeleport = new ItemRequirement("Draynor/Port Sarim teleport", ItemID.LUMBRIDGE_RING_HARD);
		draynorTeleport.addAlternates(ItemID.LUMBRIDGE_RING_ELITE, ItemID.TELETAB_DRAYNOR);
		draynorTeleport.addAlternates(ItemCollections.AMULET_OF_GLORIES);

		balloonStructure = new ItemRequirement("Balloon structure", ItemID.ZEP_TEST_BALLOON_STRUC);
		origamiBalloon = new ItemRequirement("Origami balloon", ItemID.ZEP_TEST_BALLOON);
		sandbag8 = new ItemRequirement("Sandbag", ItemID.ZEP_SANDBAG, 8);
	}

	@Override
	protected void setupZones()
	{
		entrana = new Zone(new WorldPoint(2798, 3327,0), new WorldPoint(2878, 3394,1));
	}

	public void setupConditions()
	{
		onEntrana = new ZoneRequirement(entrana);

		hasSandbags = new Conditions(LogicType.OR,
			new VarbitRequirement(2875, 1),
			sandbag8);

		flying = new WidgetTextRequirement(471, 1, "Balloon Controls");
		// Finished flight, 2868 = 1
	}

	public void setupSteps()
	{
		travelToEntrana = new NpcStep(this, NpcID.SHIPMONK1_C, new WorldPoint(3047, 3236, 0),
			"Bank all weapons and armour you have, and go to Port Sarim to get a boat to Entrana.");

		talkToAuguste = new NpcStep(this, NpcID.ZEP_PICCARD, new WorldPoint(2809, 3354, 0), "Talk to Auguste on Entrana 3" +
			" times.", papyrus3, ballOfWool);
		talkToAuguste.addDialogSteps("Yes! Sign me up.", "Umm, yes. What's your point?", "Yes.");

		usePapyrusOnWool = new DetailedQuestStep(this, "Use papyrus on a ball of wool.", papyrus.highlighted(),
			ballOfWool.highlighted());

		useCandleOnBalloon = new DetailedQuestStep(this, "Use a candle on the balloon.", unlitCandle.highlighted(),
			balloonStructure.highlighted());

		talkToAugusteAgain = new NpcStep(this, NpcID.ZEP_PICCARD, new WorldPoint(2809, 3354, 0), "Talk to Auguste again.",
			origamiBalloon);
		talkToAugusteAgain.addDialogSteps("Yes, I have them here.");

		talkToAugusteWithPapyrus = new NpcStep(this, NpcID.ZEP_PICCARD, new WorldPoint(2809, 3354, 0),
			"Talk to Auguste with 2 papyrus and a sack of potatoes.", papyrus2, sackOfPotatoes);
		talkToAugusteWithPapyrus.addDialogStep("Yes, I have them here.");

		talkToAugusteAfterMob = new NpcStep(this, NpcID.ZEP_PICCARD, new WorldPoint(2809, 3354, 0),
			"Talk to Auguste after the flash mob.");

		fillSacks = new ObjectStep(this, ObjectID.SANDPIT, new WorldPoint(2817, 3342, 0),
			"Fill your empty sacks on the sand pit south of Auguste.", emptySack8.highlighted());
		fillSacks.addIcon(ItemID.SACK_EMPTY);

		talkToAugusteWithDye = new GiveAugusteItems(this);
		talkToAugusteWithDye.addDialogSteps("Yes, I want to give you some items.", "Dye.", "Sandbags.", "Silk.",
			"Bowl.");

		talkToAugusteWithBranches = new ObjectStep(this, ObjectID.ZEP_MULTI_BASKET_ENTRANA, new WorldPoint(2807, 3356, 0),
			"Get 12 willow branches and use them to make the basket.", willowBranches12.highlighted());
		talkToAugusteWithBranches.addIcon(ItemID.WILLOW_BRANCH);

		talkToAugusteWithLogsAndTinderbox = new NpcStep(this, NpcID.ZEP_PICCARD, new WorldPoint(2809, 3354, 0),
			"Talk to Auguste to fly.", logs10, tinderbox);
		talkToAugusteWithLogsAndTinderbox.addDialogSteps("Okay.");

		doPuzzle = new TaverleyBalloonFlight(this);

		talkToAugusteToFinish = new NpcStep(this, NpcID.ZEP_PICCARD, new WorldPoint(2937, 3421, 0),
			"Talk to Auguste in Taverley to finish the quest.");
	}

	@Override
	public List<ItemRequirement> getItemRequirements()
	{
		return Arrays.asList(papyrus3, ballOfWool, sackOfPotatoes, emptySack8, unlitCandle, yellowDye, redDye, silk10, bowl,
			logs10, tinderbox, willowBranches12);
	}

	@Override
	public List<ItemRequirement> getItemRecommended()
	{
		return Collections.singletonList(draynorTeleport);
	}

	@Override
	public List<Requirement> getGeneralRequirements()
	{
		List<Requirement> reqs = new ArrayList<>();
		reqs.add(new QuestPointRequirement(20));
		reqs.add(new SkillRequirement(Skill.FIREMAKING, 20));
		reqs.add(new SkillRequirement(Skill.FARMING, 30, true));
		reqs.add(new SkillRequirement(Skill.CRAFTING, 36, true));
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
		return Arrays.asList(
				new ExperienceReward(Skill.CRAFTING, 2000),
				new ExperienceReward(Skill.FARMING, 3000),
				new ExperienceReward(Skill.WOODCUTTING, 1500),
				new ExperienceReward(Skill.FIREMAKING, 4000));
	}

	@Override
	public List<ItemReward> getItemRewards()
	{
		return Arrays.asList(
				new ItemReward("Bomber Jacket", ItemID.ZEP_BOMBER_JACKET, 1),
				new ItemReward("Bomber Cap", ItemID.ZEP_BOMBER_CAP, 1));
	}

	@Override
	public List<UnlockReward> getUnlockRewards()
	{
		return Arrays.asList(
				new UnlockReward("Access to the Hot Air Balloon transport system."),
				new UnlockReward("Ability to make origami balloons."));
	}


	@Override
	public ArrayList<PanelDetails> getPanels()
	{
		ArrayList<PanelDetails> allSteps = new ArrayList<>();

		allSteps.add(new PanelDetails("Making a balloon", Arrays.asList(travelToEntrana, talkToAuguste,
			usePapyrusOnWool,
			useCandleOnBalloon, talkToAugusteAgain, talkToAugusteWithPapyrus, talkToAugusteAfterMob, fillSacks,
			talkToAugusteWithDye),	papyrus3, ballOfWool, unlitCandle, sackOfPotatoes, emptySack8, yellowDye,redDye,
			silk10, bowl));

		allSteps.add(new PanelDetails("Flying", Arrays.asList(talkToAugusteWithBranches,
			talkToAugusteWithLogsAndTinderbox, doPuzzle, talkToAugusteToFinish),
			willowBranches12, logs10, tinderbox));

		return allSteps;
	}
}
