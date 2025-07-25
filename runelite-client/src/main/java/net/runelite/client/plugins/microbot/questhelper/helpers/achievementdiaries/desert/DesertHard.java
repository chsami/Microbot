/*
 * Copyright (c) 2021, Obasill <https://github.com/Obasill>
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
package net.runelite.client.plugins.microbot.questhelper.helpers.achievementdiaries.desert;

import net.runelite.client.plugins.microbot.questhelper.bank.banktab.BankSlotIcons;
import net.runelite.client.plugins.microbot.questhelper.collections.ItemCollections;
import net.runelite.client.plugins.microbot.questhelper.panel.PanelDetails;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.ComplexStateQuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.TeleportItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.player.SkillRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.player.SpellbookRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.quest.QuestRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.util.Spellbook;
import net.runelite.client.plugins.microbot.questhelper.requirements.var.VarplayerRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.zone.Zone;
import net.runelite.client.plugins.microbot.questhelper.requirements.zone.ZoneRequirement;
import net.runelite.client.plugins.microbot.questhelper.rewards.ItemReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.UnlockReward;
import net.runelite.client.plugins.microbot.questhelper.steps.*;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DesertHard extends ComplexStateQuestHelper
{
	// Items required
	ItemRequirement combatGear, blackjack, pickaxe, fireRune, waterRune, astralRune, emptyWaterskin, slayerHelm, keris,
		lightsource, yewLog, tinderbox, mithBar, hammer;

	// Items recommended
	ItemRequirement food, waterskin, desertBoots, desertRobe, desertShirt, rope, nardahTP;

	// Quests required
	Requirement theFeud, dreamMentor, desertTreasure, contact, lunarBook;

	Requirement notMenaThug, notGranite, notRefillWaterskin, notKalphQueen, notPollRooftop, notKillDust,
		notAncientMagicks, notKillLocustRider, notBurnYew, notMithPlatebody;

	QuestStep claimReward, menaThug, granite, refillWaterskin, pollRooftop, killDust, ancientMagicks, burnYew,
		mithPlatebody, moveToSmoke, moveToPyramid, moveToMayor, moveToSoph, moveToSoph2;

	ObjectStep moveToKalph, kalphQueen;

	NpcStep killLocustRider;

	Zone kalph, smoke, soph1, soph2, pyramid, mayor;

	ZoneRequirement inKalph, inSmoke, inPyramid, inSoph1, inSoph2, inMayor;

	ConditionalStep menaThugTask, graniteTask, refillWaterskinTask, kalphQueenTask, pollRooftopTask, killDustTask,
		ancientMagicksTask, killLocustRiderTask, burnYewTask, mithPlatebodyTask;

	@Override
	public QuestStep loadStep()
	{
		initializeRequirements();
		setupSteps();

		ConditionalStep doHard = new ConditionalStep(this, claimReward);

		kalphQueenTask = new ConditionalStep(this, moveToKalph);
		kalphQueenTask.addStep(inKalph, kalphQueen);
		doHard.addStep(notKalphQueen, kalphQueenTask);

		pollRooftopTask = new ConditionalStep(this, pollRooftop);
		doHard.addStep(notPollRooftop, pollRooftopTask);

		menaThugTask = new ConditionalStep(this, menaThug);
		doHard.addStep(notMenaThug, menaThugTask);

		refillWaterskinTask = new ConditionalStep(this, refillWaterskin);
		doHard.addStep(notRefillWaterskin, refillWaterskinTask);

		killDustTask = new ConditionalStep(this, moveToSmoke);
		killDustTask.addStep(inSmoke, killDust);
		doHard.addStep(notKillDust, killDustTask);

		ancientMagicksTask = new ConditionalStep(this, moveToPyramid);
		ancientMagicksTask.addStep(inPyramid, ancientMagicks);
		doHard.addStep(notAncientMagicks, ancientMagicksTask);

		graniteTask = new ConditionalStep(this, granite);
		doHard.addStep(notGranite, graniteTask);

		burnYewTask = new ConditionalStep(this, moveToMayor);
		burnYewTask.addStep(inMayor, burnYew);
		doHard.addStep(notBurnYew, burnYewTask);

		mithPlatebodyTask = new ConditionalStep(this, mithPlatebody);
		doHard.addStep(notMithPlatebody, mithPlatebodyTask);

		killLocustRiderTask = new ConditionalStep(this, moveToSoph);
		killLocustRiderTask.addStep(inSoph1, moveToSoph2);
		killLocustRiderTask.addStep(inSoph2, killLocustRider);
		doHard.addStep(notKillLocustRider, killLocustRiderTask);

		return doHard;
	}

	@Override
	protected void setupRequirements()
	{
		notMenaThug = new VarplayerRequirement(VarPlayerID.DESERT_ACHIEVEMENT_DIARY, false, 24);
		notGranite = new VarplayerRequirement(VarPlayerID.DESERT_ACHIEVEMENT_DIARY, false, 25);
		notRefillWaterskin = new VarplayerRequirement(VarPlayerID.DESERT_ACHIEVEMENT_DIARY, false, 26);
		notKalphQueen = new VarplayerRequirement(VarPlayerID.DESERT_ACHIEVEMENT_DIARY, false, 27);
		notPollRooftop = new VarplayerRequirement(VarPlayerID.DESERT_ACHIEVEMENT_DIARY, false, 28);
		notKillDust = new VarplayerRequirement(VarPlayerID.DESERT_ACHIEVEMENT_DIARY, false, 29);
		notAncientMagicks = new VarplayerRequirement(VarPlayerID.DESERT_ACHIEVEMENT_DIARY, false, 30);
		notKillLocustRider = new VarplayerRequirement(VarPlayerID.DESERT_ACHIEVEMENT_DIARY, false, 31);
		notBurnYew = new VarplayerRequirement(VarPlayerID.DESERT_ACHIEVEMENT_DIARY2, false, 0);
		notMithPlatebody = new VarplayerRequirement(VarPlayerID.DESERT_ACHIEVEMENT_DIARY2, false, 1);

		lunarBook = new SpellbookRequirement(Spellbook.LUNAR);

		blackjack = new ItemRequirement("Blackjack", ItemCollections.BLACKJACKS).showConditioned(notMenaThug).isNotConsumed();
		pickaxe = new ItemRequirement("Any pickaxe", ItemCollections.PICKAXES).showConditioned(notGranite).isNotConsumed();
		fireRune = new ItemRequirement("Fire rune", ItemID.FIRERUNE).showConditioned(notRefillWaterskin);
		waterRune = new ItemRequirement("Water runes", ItemID.WATERRUNE).showConditioned(notRefillWaterskin);
		astralRune = new ItemRequirement("Astral rune", ItemID.ASTRALRUNE).showConditioned(notRefillWaterskin);
		emptyWaterskin = new ItemRequirement("Empty waterskin", ItemID.WATER_SKIN0).showConditioned(notRefillWaterskin).isNotConsumed();
		slayerHelm = new ItemRequirement("Slayer Helmet", ItemCollections.SLAYER_HELMETS)
			.showConditioned(notKillDust).isNotConsumed();
		keris = new ItemRequirement("Keris", ItemCollections.KERIS).showConditioned(notKillLocustRider).isNotConsumed();
		lightsource = new ItemRequirement("Light source", ItemCollections.LIGHT_SOURCES)
			.showConditioned(notKillLocustRider).isNotConsumed();
		yewLog = new ItemRequirement("Yew log", ItemID.YEW_LOGS).showConditioned(notBurnYew);
		tinderbox = new ItemRequirement("Tinderbox", ItemID.TINDERBOX).showConditioned(notBurnYew).isNotConsumed();
		mithBar = new ItemRequirement("Mithril bars", ItemID.MITHRIL_BAR).showConditioned(notMithPlatebody);
		hammer = new ItemRequirement("Hammer", ItemID.HAMMER).showConditioned(notMithPlatebody).isNotConsumed();
		rope = new ItemRequirement("Ropes", ItemID.ROPE).showConditioned(notKalphQueen);

		nardahTP = new TeleportItemRequirement("Nardah teleport", ItemID.TELEPORTSCROLL_NARDAH);
		desertBoots = new ItemRequirement("Desert boots", ItemID.DESERT_BOOTS).isNotConsumed();
		desertRobe = new ItemRequirement("Desert robe", ItemID.DESERT_ROBE).isNotConsumed();
		desertShirt = new ItemRequirement("Desert shirt", ItemID.DESERT_SHIRT).isNotConsumed();
		waterskin = new ItemRequirement("Waterskin", ItemCollections.WATERSKIN).isNotConsumed();

		combatGear = new ItemRequirement("Combat gear", -1, -1).isNotConsumed();
		combatGear.setDisplayItemId(BankSlotIcons.getCombatGear());

		food = new ItemRequirement("Food", ItemCollections.GOOD_EATING_FOOD, -1);

		inKalph = new ZoneRequirement(kalph);
		inSmoke = new ZoneRequirement(smoke);
		inPyramid = new ZoneRequirement(pyramid);
		inSoph1 = new ZoneRequirement(soph1);
		inSoph2 = new ZoneRequirement(soph2);
		inMayor = new ZoneRequirement(mayor);

		theFeud = new QuestRequirement(QuestHelperQuest.THE_FEUD, QuestState.FINISHED);
		dreamMentor = new QuestRequirement(QuestHelperQuest.DREAM_MENTOR, QuestState.FINISHED);
		desertTreasure = new QuestRequirement(QuestHelperQuest.DESERT_TREASURE, QuestState.FINISHED);
		contact = new QuestRequirement(QuestHelperQuest.CONTACT, QuestState.FINISHED);
	}

	@Override
	protected void setupZones()
	{
		kalph = new Zone(new WorldPoint(3454, 9531, 0), new WorldPoint(3520, 9473, 2));
		smoke = new Zone(new WorldPoint(3166, 9408, 0), new WorldPoint(3332, 9344, 0));
		soph1 = new Zone(new WorldPoint(2792, 5174, 0), new WorldPoint(2807, 5158, 0));
		soph2 = new Zone(new WorldPoint(2110, 4340, 2), new WorldPoint(2180, 4415, 2));
		pyramid = new Zone(new WorldPoint(3199, 9340, 0), new WorldPoint(3265, 9282, 0));
		mayor = new Zone(new WorldPoint(3436, 2924, 1), new WorldPoint(3452, 2907, 1));
	}

	public void setupSteps()
	{
		moveToKalph = new ObjectStep(this, 3827, new WorldPoint(3228, 3109, 0),
			"Use the rope on the entrance and enter the Kalphite Hive.", rope.highlighted().quantity(2));
		moveToKalph.addAlternateObjects(ObjectID.KALPHITE_BURROW_ENTRANCE_WITHROPE);
		moveToKalph.addIcon(ItemID.ROPE);
		kalphQueen = new ObjectStep(this, 23609, new WorldPoint(3510, 9498, 2),
			"Climb down the tunnel entrance that leads to the Kalphite Queen and kill her.", rope);
		kalphQueen.addAlternateObjects(10230);
		kalphQueen.addIcon(ItemID.ROPE);

		pollRooftop = new ObjectStep(this, ObjectID.ROOFTOPS_POLLNIVNEACH_BASKET, new WorldPoint(3351, 2962, 0),
			"Climb on the basket and complete a lap of the Pollnivneach Rooftop course.");

		menaThug = new NpcStep(this, new int[]{NpcID.FEUD_EGYPTIAN_DOORMAN_1, NpcID.FEUD_EGYPTIAN_DOORMAN_2}, new WorldPoint(3347, 2959, 0),
			"Knockout and pickpocket a Menaphite thug.", blackjack);

		refillWaterskin = new ItemStep(this, "Refill an empty waterskin using the Lunar spell Humidify in the Desert " +
			"(You must be losing health or water from the heat).",
			lunarBook, fireRune.quantity(1), waterRune.quantity(3), astralRune.quantity(1));

		moveToSmoke = new ObjectStep(this, ObjectID.SWORD_HAUNTED_WELL, new WorldPoint(3310, 2962, 0),
			"Go down the Smokey well.");
		killDust = new NpcStep(this, NpcID.SLAYER_DUSTDEVIL, new WorldPoint(3219, 9370, 0),
			"Kill a Dust devil with a slayer helm equipped.", slayerHelm.equipped());

		moveToPyramid = new ObjectStep(this, ObjectID.DESERT_TRASURE_BACK_DOOR_SKEW, new WorldPoint(3233, 2889, 0),
			"Enter the Jaldraocht Pyramid.");
		ancientMagicks = new ObjectStep(this, ObjectID.DT_ZAROS_ALTAR, new WorldPoint(3233, 9311, 0),
			"Pray at the altar.");

		granite = new ObjectStep(this, ObjectID.ENAKH_GRANITE_ROCKS, new WorldPoint(3167, 2911, 0),
			"Mine granite in the mine south of the Bandit Camp.", pickaxe);

		moveToMayor = new ObjectStep(this, ObjectID.ELID_MAYOR_STAIRS, new WorldPoint(3447, 2912, 0),
			"Climb the staircase in the Nardah Mayor's house.");
		burnYew = new TileStep(this, new WorldPoint(3440, 2913, 1),
			"Burn yew logs on the balcony. ", yewLog.highlighted(), tinderbox.highlighted());

		mithPlatebody = new ObjectStep(this, ObjectID.ANVIL, new WorldPoint(3409, 2921, 0),
			"Make a Mithril platebody in Nardah.", mithBar.quantity(5), hammer);

		moveToSoph = new ObjectStep(this, ObjectID.CONTACT_TEMPLE_TRAPDOOR_OPEN, new WorldPoint(3315, 2797, 0),
			"Climb down the ladder to enter the Sophanem Dungeon.", combatGear, lightsource);
		moveToSoph2 = new ObjectStep(this, ObjectID.CONTACT_BANK_FIXED_LADDER_TOP, new WorldPoint(2800, 5159, 0),
			"Climb down the ladder again.", combatGear, lightsource);
		killLocustRider = new NpcStep(this, NpcID.CONTACT_LOCUST_BOW, new WorldPoint(3296, 9267, 2),
			"Kill a Locust rider with keris.", true, combatGear, keris.equipped());
		killLocustRider.addAlternateNpcs(NpcID.CONTACT_LOCUST_LANCE, NpcID.CONTACT_LOCUST_LANCE_B, NpcID.CONTACT_LOCUST_BOW_B);

		claimReward = new NpcStep(this, NpcID.JARR_DESERT_DIARY, new WorldPoint(3303, 3124, 0),
			"Talk to Jarr at the Shantay pass to claim your reward!");
		claimReward.addDialogStep("I have a question about my Achievement Diary.");
	}

	@Override
	public List<ItemRequirement> getItemRequirements()
	{
		return Arrays.asList(combatGear, blackjack, pickaxe, fireRune.quantity(1), waterRune.quantity(3),
			astralRune.quantity(1), emptyWaterskin, slayerHelm, keris, lightsource, yewLog, tinderbox,
			mithBar.quantity(5), hammer, rope.quantity(2));
	}

	@Override
	public List<ItemRequirement> getItemRecommended()
	{
		return Arrays.asList(food, waterskin, desertBoots, desertRobe, desertShirt, nardahTP);
	}

	@Override
	public List<Requirement> getGeneralRequirements()
	{
		List<Requirement> reqs = new ArrayList<>();
		reqs.add(new SkillRequirement(Skill.AGILITY, 70, true));
		reqs.add(new SkillRequirement(Skill.ATTACK, 50, false));
		reqs.add(new SkillRequirement(Skill.DEFENCE, 10, false));
		reqs.add(new SkillRequirement(Skill.FIREMAKING, 60, true));
		reqs.add(new SkillRequirement(Skill.MAGIC, 68, true));
		reqs.add(new SkillRequirement(Skill.MINING, 45, true));
		reqs.add(new SkillRequirement(Skill.SMITHING, 68, true));
		reqs.add(new SkillRequirement(Skill.SLAYER, 65, true));
		reqs.add(new SkillRequirement(Skill.THIEVING, 65, true));

		reqs.add(contact);
		reqs.add(dreamMentor);
		reqs.add(desertTreasure);
		reqs.add(theFeud);

		return reqs;
	}

	@Override
	public List<String> getCombatRequirements()
	{
		return Collections.singletonList("Kill a Dust devil (lvl 93), Locust rider (lvl 98), Kalphite Queen (lvl 333)");
	}

	@Override
	public List<ItemReward> getItemRewards()
	{
		return Arrays.asList(
			new ItemReward("Desert amulet 3", ItemID.DESERT_AMULET_HARD, 1),
			new ItemReward("15,000 Exp. Lamp (Any skill over 50)", ItemID.THOSF_REWARD_LAMP, 1)
		);
	}

	@Override
	public List<UnlockReward> getUnlockRewards()
	{
		return Arrays.asList(
			new UnlockReward("Pharaoh's sceptre can hold up to 50 charges"),
			new UnlockReward("Unlocked the ability to toggle the Camulet teleport location between the inside and outside of Enakhra's Temple"),
			new UnlockReward("All carpet rides are free"),
			new UnlockReward("Zahur will create unfinished potions for 200 coins per potion from a vial of water and a clean herb. Items can be noted or unnoted"),
			new UnlockReward("Zahur will now clean noted grimy herbs for 200 coins each"),
			new UnlockReward("Ropes placed at both the Kalphite Lair entrance and the Kalphite Queen tunnel entrance become permanent"),
			new UnlockReward("Increased experience on the Pollnivneach Rooftop Course")
		);
	}

	@Override
	public List<PanelDetails> getPanels()
	{
		List<PanelDetails> allSteps = new ArrayList<>();

		PanelDetails kalphiteQueenSteps = new PanelDetails("Kalphite Queen", Arrays.asList(moveToKalph, kalphQueen),
			combatGear, food, rope.quantity(2));
		kalphiteQueenSteps.setDisplayCondition(notKalphQueen);
		kalphiteQueenSteps.setLockingStep(kalphQueenTask);
		allSteps.add(kalphiteQueenSteps);

		PanelDetails pollRooftopSteps = new PanelDetails("Pollnivneach Rooftops",
			Collections.singletonList(pollRooftop), new SkillRequirement(Skill.AGILITY, 70, true));
		pollRooftopSteps.setDisplayCondition(notPollRooftop);
		pollRooftopSteps.setLockingStep(pollRooftopTask);
		allSteps.add(pollRooftopSteps);

		PanelDetails menaphiteThugSteps = new PanelDetails("Menaphite Thug", Collections.singletonList(menaThug),
			new SkillRequirement(Skill.THIEVING, 65, true), theFeud, blackjack);
		menaphiteThugSteps.setDisplayCondition(notMenaThug);
		menaphiteThugSteps.setLockingStep(menaThugTask);
		allSteps.add(menaphiteThugSteps);

		PanelDetails refillWaterskinsSteps = new PanelDetails("Refill Waterskins",
			Collections.singletonList(refillWaterskin), new SkillRequirement(Skill.MAGIC, 68, true), dreamMentor,
			lunarBook, fireRune.quantity(1), waterRune.quantity(3), astralRune.quantity(1), emptyWaterskin);
		refillWaterskinsSteps.setDisplayCondition(notRefillWaterskin);
		refillWaterskinsSteps.setLockingStep(refillWaterskinTask);
		allSteps.add(refillWaterskinsSteps);

		PanelDetails dustDevilSteps = new PanelDetails("Dust Devil", Arrays.asList(moveToSmoke, killDust),
			new SkillRequirement(Skill.CRAFTING, 55, true), new SkillRequirement(Skill.DEFENCE, 10, false),
			new SkillRequirement(Skill.SLAYER, 65, true), desertTreasure, combatGear, food, slayerHelm);
		dustDevilSteps.setDisplayCondition(notKillDust);
		dustDevilSteps.setLockingStep(killDustTask);
		allSteps.add(dustDevilSteps);

		PanelDetails ancientMagicksSteps = new PanelDetails("Activate Ancient Magicks", Arrays.asList(moveToPyramid,
			ancientMagicks), desertTreasure);
		ancientMagicksSteps.setDisplayCondition(notAncientMagicks);
		ancientMagicksSteps.setLockingStep(ancientMagicksTask);
		allSteps.add(ancientMagicksSteps);

		PanelDetails mineGraniteSteps = new PanelDetails("Mine Granite", Collections.singletonList(granite),
			new SkillRequirement(Skill.MINING, 45, true), pickaxe);
		mineGraniteSteps.setDisplayCondition(notGranite);
		mineGraniteSteps.setLockingStep(graniteTask);
		allSteps.add(mineGraniteSteps);

		PanelDetails burnYewSteps = new PanelDetails("Burn Yew on Nardah Mayor's Balcony", Arrays.asList(moveToMayor,
			burnYew), new SkillRequirement(Skill.FIREMAKING, 60, true), yewLog, tinderbox);
		burnYewSteps.setDisplayCondition(notBurnYew);
		burnYewSteps.setLockingStep(burnYewTask);
		allSteps.add(burnYewSteps);

		PanelDetails mithrilPlatebodySteps = new PanelDetails("Mithril Platebody",
			Collections.singletonList(mithPlatebody), new SkillRequirement(Skill.SMITHING, 68, true), mithBar.quantity(5),
			hammer);
		mithrilPlatebodySteps.setDisplayCondition(notMithPlatebody);
		mithrilPlatebodySteps.setLockingStep(mithPlatebodyTask);
		allSteps.add(mithrilPlatebodySteps);

		PanelDetails locusRiderSteps = new PanelDetails("Kill Locust Rider with Keris", Arrays.asList(moveToSoph,
			killLocustRider), new SkillRequirement(Skill.ATTACK, 50, false), contact, keris, combatGear, lightsource, food);
		locusRiderSteps.setDisplayCondition(notKillLocustRider);
		locusRiderSteps.setLockingStep(killLocustRiderTask);
		allSteps.add(locusRiderSteps);

		allSteps.add(new PanelDetails("Finishing off", Collections.singletonList(claimReward)));

		return allSteps;
	}
}
