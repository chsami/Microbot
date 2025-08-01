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
package net.runelite.client.plugins.microbot.questhelper.helpers.achievementdiaries.kandarin;

import net.runelite.client.plugins.microbot.questhelper.bank.banktab.BankSlotIcons;
import net.runelite.client.plugins.microbot.questhelper.collections.ItemCollections;
import net.runelite.client.plugins.microbot.questhelper.panel.PanelDetails;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.ComplexStateQuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.requirements.ChatMessageRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.ComplexRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.conditional.Conditions;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.player.SkillRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.player.SpellbookRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.quest.QuestRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.util.LogicType;
import net.runelite.client.plugins.microbot.questhelper.requirements.util.Operation;
import net.runelite.client.plugins.microbot.questhelper.requirements.util.Spellbook;
import net.runelite.client.plugins.microbot.questhelper.requirements.var.VarbitRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.var.VarplayerRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.zone.Zone;
import net.runelite.client.plugins.microbot.questhelper.requirements.zone.ZoneRequirement;
import net.runelite.client.plugins.microbot.questhelper.rewards.ItemReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.UnlockReward;
import net.runelite.client.plugins.microbot.questhelper.steps.*;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class KandarinElite extends ComplexStateQuestHelper
{
	// Items required
	ItemRequirement dwarfSeed, seedDib, spade, rake, compost, harpoon, cookingGaunt, stamPot, caviar, runiteBar,
		magicLogs1, magicLogs2, magicLogs, hammer, chewedBone, tinderbox, axe, lawRune, astralRune, waterRune, combatGear;

	// unlisted item reqs
	ItemRequirement rawShark;

	Requirement notbarb5, notPickDwarf, not5Shark, notStamMix, notRuneHasta, notPyre, notTPCath, notHeal, notAtk, notDef,
		notCol, bothLogs;

	Requirement barbSmith, barbFire, barbHerb, familyCrest, lunarDip, fishedSharks;

	//Quest steps
	QuestStep claimReward, tpCath, plantAndPickDwarf, moveToSeersRooftop, stamMix, runeHasta, pyre, barb5, barb52,
		barb5Heal, barb5Atk, barb5Def, barb5Col, cook5Sharks;

	NpcStep catch5Sharks;

	Zone bankRoof, barbUnder, catherby;

	ZoneRequirement inBankRoof, inBarbUnder, inCatherby;

	Requirement lunarBook;

	ConditionalStep barb5Task, pickDwarfTask, sharkTask, stamMixTask, runeHastaTask, pyreTask, tpCathTask;

	@Override
	public QuestStep loadStep()
	{
		initializeRequirements();
		setupSteps();

		ConditionalStep doElite = new ConditionalStep(this, claimReward);

		pickDwarfTask = new ConditionalStep(this, plantAndPickDwarf);
		doElite.addStep(notPickDwarf, pickDwarfTask);

		tpCathTask = new ConditionalStep(this, tpCath);
		doElite.addStep(notTPCath, tpCathTask);

		sharkTask = new ConditionalStep(this, catch5Sharks);
		sharkTask.addStep(new Conditions(fishedSharks, rawShark.quantity(5)), cook5Sharks);
		doElite.addStep(not5Shark, sharkTask);

		stamMixTask = new ConditionalStep(this, moveToSeersRooftop);
		stamMixTask.addStep(inBankRoof, stamMix);
		doElite.addStep(notStamMix, stamMixTask);

		runeHastaTask = new ConditionalStep(this, runeHasta);
		doElite.addStep(notRuneHasta, runeHastaTask);

		pyreTask = new ConditionalStep(this, pyre);
		doElite.addStep(notPyre, pyreTask);

		barb5Task = new ConditionalStep(this, barb5);
		barb5Task.addStep(inBarbUnder, barb52);
		barb5Task.addStep(notCol, barb5Col);
		barb5Task.addStep(notDef, barb5Def);
		barb5Task.addStep(notAtk, barb5Atk);
		barb5Task.addStep(notHeal, barb5Heal);
		doElite.addStep(notbarb5, barb5Task);

		return doElite;
	}

	@Override
	protected void setupRequirements()
	{
		notbarb5 = new VarplayerRequirement(VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY2, false, 5);
		notPickDwarf = new VarplayerRequirement(VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY2, false, 6);
		not5Shark = new VarplayerRequirement(VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY2, false, 7);
		notStamMix = new VarplayerRequirement(VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY2, false, 8);
		notRuneHasta = new VarplayerRequirement(VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY2, false, 9);
		notPyre = new VarplayerRequirement(VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY2, false, 10);
		notTPCath = new VarplayerRequirement(VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY2, false, 11);
		bothLogs = new ComplexRequirement(LogicType.AND, "Magic logs", notRuneHasta, notPyre);

		// BA levels
		notHeal = new VarbitRequirement(VarbitID.BARBASSULT_ROLELEVEL_HEAL, 4, Operation.LESS_EQUAL);
		notAtk = new VarbitRequirement(VarbitID.BARBASSULT_ROLELEVEL_ATT, 4, Operation.LESS_EQUAL);
		notDef = new VarbitRequirement(VarbitID.BARBASSAULT_USINGHORN, 4, Operation.LESS_EQUAL);
		notCol = new VarbitRequirement(VarbitID.BARBASSULT_ROLELEVEL_COL, 4, Operation.LESS_EQUAL);

		dwarfSeed = new ItemRequirement("Dwarf weed seed", ItemID.DWARF_WEED_SEED).showConditioned(notPickDwarf);
		seedDib = new ItemRequirement("Seed dibber", ItemID.DIBBER).showConditioned(notPickDwarf).isNotConsumed();
		spade = new ItemRequirement("Spade", ItemID.SPADE).showConditioned(notPickDwarf).isNotConsumed();
		rake = new ItemRequirement("Rake", ItemID.RAKE).showConditioned(notPickDwarf).isNotConsumed();
		compost = new ItemRequirement("Any compost", ItemCollections.COMPOST).showConditioned(notPickDwarf);
		harpoon = new ItemRequirement("Harpoon", ItemID.HARPOON).showConditioned(not5Shark).isNotConsumed();
		cookingGaunt = new ItemRequirement("Cooking gauntlets", ItemID.GAUNTLETS_OF_COOKING).showConditioned(not5Shark).isNotConsumed();
		stamPot = new ItemRequirement("Stamina potion (2)", ItemID._2DOSESTAMINA).showConditioned(notStamMix);
		caviar = new ItemRequirement("Caviar", ItemID.BRUT_CAVIAR).showConditioned(notStamMix);
		runiteBar = new ItemRequirement("Runite bar", ItemID.RUNITE_BAR).showConditioned(notRuneHasta);
		magicLogs1 = new ItemRequirement("Magic logs", ItemID.MAGIC_LOGS, 1)
			.showConditioned(new Conditions(new Conditions(LogicType.OR, notRuneHasta, notPyre), new Conditions(LogicType.NOR, bothLogs)));
		magicLogs2 = new ItemRequirement("Magic logs", ItemID.MAGIC_LOGS, 2)
			.showConditioned(new Conditions(LogicType.OR, notRuneHasta, notPyre));
		magicLogs = new ItemRequirement("Magic logs", ItemID.MAGIC_LOGS, 1);
		hammer = new ItemRequirement("Hammer", ItemID.HAMMER).showConditioned(notRuneHasta).isNotConsumed();
		chewedBone = new ItemRequirement("Chewed bones", ItemID.BRUT_BARBARIAN_BONES).showConditioned(notPyre);
		chewedBone.setTooltip("These are a rare drop from mithril dragons");
		tinderbox = new ItemRequirement("Tinderbox", ItemID.TINDERBOX).showConditioned(notPyre).isNotConsumed();
		axe = new ItemRequirement("Any axe", ItemCollections.AXES).showConditioned(notPyre).isNotConsumed();
		lawRune = new ItemRequirement("Law runes", ItemID.LAWRUNE).showConditioned(notTPCath);
		astralRune = new ItemRequirement("Astral runes", ItemID.ASTRALRUNE).showConditioned(notTPCath);
		waterRune = new ItemRequirement("Water runes", ItemID.WATERRUNE).showConditioned(notTPCath);
		rawShark = new ItemRequirement("Raw shark", ItemID.RAW_SHARK).showConditioned(not5Shark);

		combatGear = new ItemRequirement("Combat gear", -1, -1).showConditioned(notbarb5).isNotConsumed();
		combatGear.setDisplayItemId(BankSlotIcons.getCombatGear());

		lunarBook = new SpellbookRequirement(Spellbook.LUNAR);

		setupGeneralRequirements();

		inBankRoof = new ZoneRequirement(bankRoof);
		inBarbUnder = new ZoneRequirement(barbUnder);
		inCatherby = new ZoneRequirement(catherby);

		fishedSharks = new ChatMessageRequirement(
			inCatherby,
			"<col=0040ff>Achievement Diary Stage Task - Current stage: 5 caught, 0 cooked.</col>"
		);
		((ChatMessageRequirement) fishedSharks).setInvalidateRequirement(
			new ChatMessageRequirement(
				new Conditions(LogicType.NOR, inCatherby),
				"<col=0040ff>Achievement Diary Stage Task - Current stage: 5 caught, 0 cooked.</col>"
			)
		);
	}

	private void setupGeneralRequirements()
	{
		// TODO find a way to track barb training
		barbHerb = new ItemRequirement("Completed Barbarian herblore", 1, -1).showConditioned(notStamMix);
		barbFire = new ItemRequirement("Unlocked the Ancient Caverns through Barbarian Firemaking", 1, -1).showConditioned(notPyre);
		barbSmith = new ItemRequirement("Completed Barbarian Smithing", 1, -1).showConditioned(notRuneHasta);
		familyCrest = new QuestRequirement(QuestHelperQuest.FAMILY_CREST, QuestState.FINISHED);
		lunarDip = new QuestRequirement(QuestHelperQuest.LUNAR_DIPLOMACY, QuestState.FINISHED);
	}

	@Override
	protected void setupZones()
	{
		bankRoof = new Zone(new WorldPoint(2721, 3495, 3), new WorldPoint(2730, 3490, 3));
		barbUnder = new Zone(new WorldPoint(2572, 53202, 0), new WorldPoint(2614, 5258, 0));
		catherby = new Zone(new WorldPoint(2801, 3457, 0), new WorldPoint(2864, 3416, 0));
	}

	public void setupSteps()
	{
		tpCath = new DetailedQuestStep(this, "Teleport to Catherby.",
			lunarBook, waterRune.quantity(10), astralRune.quantity(3), lawRune.quantity(3));
		plantAndPickDwarf = new ObjectStep(this, ObjectID.FARMING_HERB_PATCH_2, new WorldPoint(2814, 3464, 0),
			"Plant and harvest the dwarf weed from the Catherby patch. " +
				"If you're waiting for it to grow and want to complete further tasks, use the tick box on panel.",
			rake, dwarfSeed, seedDib);
		catch5Sharks = new NpcStep(this, NpcID._0_44_53_MEMBERFISH, new WorldPoint(2837, 3431, 0),
			"Catch 5 sharks in Catherby.", harpoon, cookingGaunt.equipped());
		cook5Sharks = new ObjectStep(this, ObjectID.RANGE, new WorldPoint(2817, 3444, 0),
			"Successfully cook 5 on the range in Catherby.", cookingGaunt.equipped());
		moveToSeersRooftop = new ObjectStep(this, ObjectID.ROOFTOPS_SEERS_WALLCLIMB, new WorldPoint(2729, 3489, 0),
			"Climb on top of Seers' Bank.", stamPot, caviar);
		stamMix = new ItemStep(this, "Create a stamina mix.", stamPot.highlighted(), caviar.highlighted());
		runeHasta = new ObjectStep(this, ObjectID.BRUT_ANVIL, new WorldPoint(2502, 3485, 0),
			"Smith a rune hasta on the barbarian anvil near Otto.", runiteBar, magicLogs, hammer);
		runeHasta.addIcon(ItemID.RUNITE_BAR);
		pyre = new ObjectStep(this, ObjectID.BRUT_BURNED_GROUND, new WorldPoint(2519, 3519, 0),
			"Construct a pyre ship from magic logs.", magicLogs, chewedBone, tinderbox, axe);
		barb5Heal = new DetailedQuestStep(this, "Get to level 5 in the healer role at Barbarian Assault.");
		barb5Atk = new DetailedQuestStep(this, "Get to level 5 in the attacker role at Barbarian Assault.");
		barb5Def = new DetailedQuestStep(this, "Get to level 5 in the defender role at Barbarian Assault.");
		barb5Col = new DetailedQuestStep(this, "Get to level 5 in the collector role at Barbarian Assault.");
		barb5 = new ObjectStep(this, ObjectID.BARBASSAULT_BLACKBOARD, new WorldPoint(2535, 3569, 0),
			"Click one of the blackboards around Barbarian Assault!");
		barb52 = new ObjectStep(this, ObjectID.BARBASSAULT_BLACKBOARD, new WorldPoint(2587, 5264, 0),
			"Click one of the blackboards around Barbarian Assault!");
		barb5.addSubSteps(barb52);

		claimReward = new NpcStep(this, NpcID.SEERS_DIARY_WEDGE, new WorldPoint(2760, 3476, 0),
			"Talk to the 'Wedge' in front of Camelot Castle to claim your reward!");
		claimReward.addDialogStep("I have a question about my Achievement Diary.");
	}

	@Override
	public List<ItemRequirement> getItemRequirements()
	{
		return Arrays.asList(dwarfSeed, seedDib, spade, rake, compost, harpoon,
			cookingGaunt, stamPot, caviar, runiteBar, magicLogs1, magicLogs2, hammer, chewedBone, tinderbox, axe,
			lawRune.quantity(3), astralRune.quantity(3), waterRune.quantity(10), combatGear);
	}

	@Override
	public List<Requirement> getGeneralRequirements()
	{
		initializeRequirements();

		ArrayList<Requirement> req = new ArrayList<>();
		req.add(new SkillRequirement(Skill.AGILITY, 60, true));
		req.add(new SkillRequirement(Skill.COOKING, 80, true));
		req.add(new SkillRequirement(Skill.CRAFTING, 85, true));
		req.add(new SkillRequirement(Skill.FARMING, 79, true));
		req.add(new SkillRequirement(Skill.FIREMAKING, 85, true));
		req.add(new SkillRequirement(Skill.FISHING, 76, true));
		req.add(new SkillRequirement(Skill.HERBLORE, 86, true));
		req.add(new SkillRequirement(Skill.MAGIC, 87, true));
		req.add(new SkillRequirement(Skill.SMITHING, 90, true));

		req.add(barbHerb);
		req.add(barbFire);
		req.add(barbSmith);
		req.add(familyCrest);
		req.add(lunarDip);

		return req;
	}

	@Override
	public List<String> getCombatRequirements()
	{
		return Collections.singletonList("Mithril Dragons (level 304) for chewed bones");
	}

	@Override
	public List<ItemReward> getItemRewards()
	{
		return Arrays.asList(
			new ItemReward("Kandarin headgear (4)", ItemID.SEERS_HEADBAND_ELITE, 1),
			new ItemReward("50,000 Exp. Lamp (Any skill over 70)", ItemID.THOSF_REWARD_LAMP, 1));
	}

	@Override
	public List<UnlockReward> getUnlockRewards()
	{
		return Arrays.asList(
			new UnlockReward("Thormac will enchant battlestaves for 20,000 coins each"),
			new UnlockReward("The Flax keeper will exchange 250 noted flax for 250 noted bow strings daily"),
			new UnlockReward("15% increased chance to save a harvest life from the Catherby herb patch"),
			new UnlockReward("The first 200 Coal placed into coal trucks every day will be automatically transported to your bank"),
			new UnlockReward("Otto Godblessed will turn a Zamorakian spear into Zamorakian hasta for 150,000 Coins"));
	}

	@Override
	public List<PanelDetails> getPanels()
	{
		List<PanelDetails> allSteps = new ArrayList<>();

		PanelDetails dwarfWeedSteps = new PanelDetails("Dwarf Weed in Catherby", Collections.singletonList(plantAndPickDwarf),
			new SkillRequirement(Skill.FARMING, 79, true), dwarfSeed, seedDib, rake, spade, compost);
		dwarfWeedSteps.setDisplayCondition(notPickDwarf);
		dwarfWeedSteps.setLockingStep(pickDwarfTask);
		allSteps.add(dwarfWeedSteps);

		PanelDetails teleCathSteps = new PanelDetails("Teleport to Catherby", Collections.singletonList(tpCath),
			new SkillRequirement(Skill.MAGIC, 87, true), lunarDip, waterRune.quantity(10),
			lawRune.quantity(3), astralRune.quantity(3));
		teleCathSteps.setDisplayCondition(notTPCath);
		teleCathSteps.setLockingStep(tpCathTask);
		allSteps.add(teleCathSteps);

		PanelDetails catchSharkSteps = new PanelDetails("5 Sharks Caught and Cooked in Catherby",
			Arrays.asList(catch5Sharks, cook5Sharks), new SkillRequirement(Skill.FISHING, 76, true),
			new SkillRequirement(Skill.COOKING, 80, true), familyCrest, harpoon, cookingGaunt);
		catchSharkSteps.setDisplayCondition(not5Shark);
		catchSharkSteps.setLockingStep(sharkTask);
		allSteps.add(catchSharkSteps);

		PanelDetails staminaSteps = new PanelDetails("Stamina Mix on the Bank", Arrays.asList(moveToSeersRooftop, stamMix),
			new SkillRequirement(Skill.HERBLORE, 86, true),
			new SkillRequirement(Skill.AGILITY, 60, true), barbHerb, stamPot, caviar);
		staminaSteps.setDisplayCondition(notStamMix);
		staminaSteps.setLockingStep(stamMixTask);
		allSteps.add(staminaSteps);

		PanelDetails smithRuneHastaSteps = new PanelDetails("Smith Rune Hasta", Collections.singletonList(runeHasta),
			new SkillRequirement(Skill.SMITHING, 90, true), barbSmith, magicLogs, runiteBar, hammer);
		smithRuneHastaSteps.setDisplayCondition(notRuneHasta);
		smithRuneHastaSteps.setLockingStep(runeHastaTask);
		allSteps.add(smithRuneHastaSteps);

		PanelDetails magicPyreSteps = new PanelDetails("Magic Pyre Ship", Collections.singletonList(pyre),
			new SkillRequirement(Skill.FIREMAKING, 85, true),
			new SkillRequirement(Skill.CRAFTING, 85, true),
			barbFire, axe, tinderbox, magicLogs, chewedBone);
		magicPyreSteps.setDisplayCondition(notPyre);
		magicPyreSteps.setLockingStep(pyreTask);
		allSteps.add(magicPyreSteps);

		PanelDetails level5RolesSteps = new PanelDetails("Level 5 each Role", Arrays.asList(barb5Heal, barb5Atk, barb5Def, barb5Col, barb5));
		level5RolesSteps.setDisplayCondition(notbarb5);
		level5RolesSteps.setLockingStep(barb5Task);
		allSteps.add(level5RolesSteps);

		PanelDetails finishingOffSteps = new PanelDetails("Finishing off", Collections.singletonList(claimReward));
		allSteps.add(finishingOffSteps);

		return allSteps;
	}
}
