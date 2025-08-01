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
package net.runelite.client.plugins.microbot.questhelper.helpers.quests.monkeymadnessii;

import net.runelite.client.plugins.microbot.questhelper.bank.banktab.BankSlotIcons;
import net.runelite.client.plugins.microbot.questhelper.collections.ItemCollections;
import net.runelite.client.plugins.microbot.questhelper.panel.PanelDetails;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.BasicQuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestUtil;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestVarbits;
import net.runelite.client.plugins.microbot.questhelper.requirements.ChatMessageRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.conditional.Conditions;
import net.runelite.client.plugins.microbot.questhelper.requirements.conditional.NpcCondition;
import net.runelite.client.plugins.microbot.questhelper.requirements.conditional.ObjectCondition;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemOnTileRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirements;
import net.runelite.client.plugins.microbot.questhelper.requirements.npc.FollowerRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.npc.NpcInteractingRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.player.SkillRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.quest.QuestRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.util.LogicType;
import net.runelite.client.plugins.microbot.questhelper.requirements.util.Operation;
import net.runelite.client.plugins.microbot.questhelper.requirements.var.VarbitRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.widget.WidgetTextRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.zone.Zone;
import net.runelite.client.plugins.microbot.questhelper.requirements.zone.ZoneRequirement;
import net.runelite.client.plugins.microbot.questhelper.rewards.ExperienceReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.ItemReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.QuestPointReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.UnlockReward;
import net.runelite.client.plugins.microbot.questhelper.steps.*;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;

import java.util.*;

public class MonkeyMadnessII extends BasicQuestHelper
{
	//Items Required
	ItemRequirement lemon, grape, pestle, pickaxe, logs, lightSource, hammerSidebar, hammer, chisel, chiselSidebar, mspeakAmulet, talisman, ninjaGreegree, translationBook,
		pestleHighlighted, lemonHighlighted, grapesHighlighted, handkerchief, mysteriousNote, mysteriousNoteLemon, mysteriousNoteLemonCandle, brush, scrawledNote, grapeBrush,
		translatedNote, noCombatItems, talismanOr1000Coins, ninjaGreegreeEquipped, mspeakAmuletEquipped, greegree, kruksPaw, greegreeEquipped, krukGreegree, coins20,
		chiselHighlighted, deconstructedOnyx, chargedOnyx, climbingBoots;

	//Items Recommended
	ItemRequirement combatGear, combatGear2, magicLog, food, staminaPotions, prayerPotions, antidote;

	//Other Requirements
	Requirement nieveFollower, strongholdBalloon;

	QuestStep talkToNarnode;

	QuestStep goUpToGloughHouse, goUpGloughTree, investigateTree, goDownGloughTree, goDownFromGloughHouse, goUpToAnita, talkToAnita, goDownFromAnita,
		goUpGloughTreeToThirdFloor, investigateStatue, searchCupboard, searchCrate, searchRemains, usePestleOnLemon, useNotesOnCandles,
		usePestleOnGrapes, useBrushOnNote, readScrawledNote, goDown3rdto2nd, useTranslationOnNote, talkToNarnodeBlank, readTranslatedNote,
		talkToAuguste, talkToNarnodeAfterEntrana;

	QuestStep talkToGarkor, talkToAwowogei, talkToGarkorAfterAwow, talkToArcher, enterTrapdoor, pickUpGreegree, doAgilitySection, pickUpKrukCorpse, leaveKrukDungeon, goDownToZooknock, talkToZooknock, talkToAwowAsKruk,
		talkToGarkorAfterKruk;

	DetailedQuestStep enterTrollStronghold, talkToKob, fightKob, talkToKeef, fightKeef, talkToGarkorAfterKeef, findSmith, talkToSmith, talkToGarkorAfterSmith,
		talkToMonkeyGuard;

	MM2Sabotage sabotageShips;

	QuestStep talkToGarkorAfterSabotage, enterKrukDungeonAgain, climbMonkeyBarsAsKruk, enterLabratory, climbOnGorilla, fightGorillas, dismountGorilla, getChiselAndHammer,
		tamperWithDevice, useChiselOnOnyx, useOnyxOnDevice, investigateIncubationChamber, talkToGarkorAfterLab, talkToAwowAfterLab, talkToGarkorAfterLabAgain;

	QuestStep talkToNarnodeAfterLab, talkToNieve, killGorillasInStronghold, enterNorthOfTree, enterStrongholdCave, killTorturedAndDemonic, enterNorthOfTreeNoNieve, fightGlough, talkToZooknockToFinish,
		talkToNarnodeToFinish;

	Requirement inGloughHouse, inGloughHouseF1, inGloughHouseF2, inGloughHouseF3, inAnitaHouse, inCaves, inZooknockDungeon, inStrongholdFloor2, inLab,
		isPastMonkeyBars, isNorthOfTree, inCrashSiteCavern, gorillaNotOnHoldingArea, hasChiselAndHammer;

	Requirement foundHandkerchief, talkedToAnita, openedCupboard, foundNote, hasBrush, triedTranslating, greegreeNearby, krukCorpseNearby, kob2Nearby,
		keef2Nearby, defeatedKob, defeatedKeef, smithNearby, smithInLocation1, smithInLocation2, smithInLocation3, smithInLocation4, killedGorillas,
		nieveFollowing;

	ConditionalStep goToGlough2ndFloor, goInvestigateGloughHouse, leaveGloughHouse, goTalkToAnita, goToGlough3rdFloor, goInvestigateUpstairs, goShowNoteToNarnode, goTalkToAnitaWithNote,
		bringTranslationToNarnode;

	//Zones
	Zone gloughHouseF1, gloughHouseF2, gloughHouseF3, anitaHouse, caves, subCaves, zooknockDungeon, strongholdFloor2, lab, pastMonkeyBars, northOfTree, crashSiteCavern;

	@Override
	public Map<Integer, QuestStep> loadSteps()
	{
		initializeRequirements();
		setupConditions();
		setupSteps();
		setupConditionalSteps();
		Map<Integer, QuestStep> steps = new HashMap<>();

		steps.put(0, talkToNarnode);

		ConditionalStep investigatingGlough = new ConditionalStep(this, goInvestigateGloughHouse);
		investigatingGlough.addStep(talkedToAnita, goInvestigateUpstairs);
		investigatingGlough.addStep(foundHandkerchief, goTalkToAnita);
		steps.put(5, investigatingGlough);

		ConditionalStep goTranslate = new ConditionalStep(this, useTranslationOnNote);
		goTranslate.addStep(triedTranslating, goShowNoteToNarnode);
		steps.put(6, goTranslate);

		steps.put(7, goTalkToAnitaWithNote);
		steps.put(8, bringTranslationToNarnode);
		steps.put(9, talkToAuguste);
		steps.put(10, talkToNarnodeAfterEntrana);

		steps.put(15, talkToGarkor);
		steps.put(20, talkToAwowogei);
		steps.put(25, talkToGarkorAfterAwow);
		steps.put(30, talkToArcher);
		steps.put(35, enterTrapdoor);

		ConditionalStep traverseAgilityArea = new ConditionalStep(this, enterTrapdoor);
		traverseAgilityArea.addStep(greegreeNearby, pickUpGreegree);
		traverseAgilityArea.addStep(inCaves, doAgilitySection);
		steps.put(40, traverseAgilityArea);

		ConditionalStep goMakeGreegree = new ConditionalStep(this, goDownToZooknock);
		goMakeGreegree.addStep(new Conditions(inZooknockDungeon), talkToZooknock);
		goMakeGreegree.addStep(new Conditions(inCaves, kruksPaw), leaveKrukDungeon);
		goMakeGreegree.addStep(krukCorpseNearby, pickUpKrukCorpse);
		steps.put(45, goMakeGreegree);

		steps.put(50, talkToAwowAsKruk);
		steps.put(55, talkToGarkorAfterKruk);

		ConditionalStep goDefeatKob = new ConditionalStep(this, enterTrollStronghold);
		goDefeatKob.addStep(kob2Nearby, fightKob);
		goDefeatKob.addStep(inStrongholdFloor2, talkToKob);

		ConditionalStep goDefeatKeef = new ConditionalStep(this, talkToKeef);
		goDefeatKeef.addStep(keef2Nearby, fightKeef);

		ConditionalStep defeatKobAndKeef = new ConditionalStep(this, goDefeatKob);
		defeatKobAndKeef.addStep(defeatedKob, goDefeatKeef);
		steps.put(60, defeatKobAndKeef);
		steps.put(61, defeatKobAndKeef);
		steps.put(62, defeatKobAndKeef);
		steps.put(63, defeatKobAndKeef);
		steps.put(64, defeatKobAndKeef);

		steps.put(65, talkToGarkorAfterKeef);

		ConditionalStep goTalkToSmith = new ConditionalStep(this, findSmith);
		goTalkToSmith.addStep(smithNearby, talkToSmith);
		//	goTalkToSmith.addStep(smithInLocation4, goUpTo4);
		steps.put(66, goTalkToSmith);

		steps.put(70, talkToGarkorAfterSmith);

		steps.put(71, sabotageShips);

		steps.put(75, talkToGarkorAfterSabotage);

		ConditionalStep enterLab = new ConditionalStep(this, enterKrukDungeonAgain);
		enterLab.addStep(isPastMonkeyBars, enterLabratory);
		enterLab.addStep(inCaves, climbMonkeyBarsAsKruk);

		ConditionalStep fightingGorillasInLab = new ConditionalStep(this, enterLab);
		fightingGorillasInLab.addStep(new Conditions(inLab, gorillaNotOnHoldingArea), fightGorillas);
		fightingGorillasInLab.addStep(inLab, climbOnGorilla);
		steps.put(80, fightingGorillasInLab);

		ConditionalStep sabotageOnyx = new ConditionalStep(this, enterLab);
		sabotageOnyx.addStep(new Conditions(inLab, deconstructedOnyx), useOnyxOnDevice);
		sabotageOnyx.addStep(new Conditions(inLab, chargedOnyx, hasChiselAndHammer), useChiselOnOnyx);
		sabotageOnyx.addStep(new Conditions(inLab, hasChiselAndHammer), tamperWithDevice);
		sabotageOnyx.addStep(inLab, getChiselAndHammer);
		steps.put(95, sabotageOnyx);

		ConditionalStep goInspectMutagen = new ConditionalStep(this, enterLab);
		goInspectMutagen.addStep(inLab, investigateIncubationChamber);
		steps.put(100, goInspectMutagen);

		steps.put(105, talkToGarkorAfterLab);
		steps.put(110, talkToAwowAfterLab);
		steps.put(115, talkToGarkorAfterLabAgain);
		steps.put(120, talkToGarkorAfterLabAgain);

		steps.put(125, talkToNarnodeAfterLab);

		steps.put(130, talkToNieve);

		steps.put(140, killGorillasInStronghold);

		ConditionalStep goDefeatDemonicAndTorturedGorillas = new ConditionalStep(this, enterNorthOfTree);
		goDefeatDemonicAndTorturedGorillas.addStep(inCrashSiteCavern, killTorturedAndDemonic);
		goDefeatDemonicAndTorturedGorillas.addStep(isNorthOfTree, enterStrongholdCave);

		steps.put(145, goDefeatDemonicAndTorturedGorillas);
		steps.put(150, goDefeatDemonicAndTorturedGorillas);
		steps.put(155, goDefeatDemonicAndTorturedGorillas);

		ConditionalStep goDefeatGlough = new ConditionalStep(this, enterNorthOfTreeNoNieve);
		goDefeatGlough.addStep(inCrashSiteCavern, fightGlough);
		goDefeatGlough.addStep(isNorthOfTree, enterStrongholdCave);
		steps.put(165, goDefeatGlough);

		ConditionalStep finishQuest = new ConditionalStep(this, talkToNarnodeToFinish);
		finishQuest.addStep(isNorthOfTree, talkToZooknockToFinish);
		steps.put(180, finishQuest);
		steps.put(185, talkToNarnodeToFinish);
		steps.put(190, talkToNarnodeToFinish);

		return steps;
	}

	@Override
	protected void setupRequirements()
	{
		strongholdBalloon = new VarbitRequirement(VarbitID.ZEP_MULTI_GNO, Operation.EQUAL, 1, "Unlocked the Tree Gnome Stronghold balloon route");

		lemon = new ItemRequirement("Lemon", ItemID.LEMON);
		grape = new ItemRequirement("Grapes", ItemID.GRAPES);
		pestle = new ItemRequirement("Pestle and mortar", ItemID.PESTLE_AND_MORTAR).isNotConsumed();
		lemonHighlighted = new ItemRequirement("Lemon", ItemID.LEMON);
		lemonHighlighted.setHighlightInInventory(true);
		grapesHighlighted = new ItemRequirement("Grapes", ItemID.GRAPES);
		grapesHighlighted.setHighlightInInventory(true);
		pestleHighlighted = new ItemRequirement("Pestle and mortar", ItemID.PESTLE_AND_MORTAR);
		pestleHighlighted.setHighlightInInventory(true);

		pickaxe = new ItemRequirement("Any pickaxe", ItemCollections.PICKAXES).isNotConsumed();
		logs = new ItemRequirement("Logs", ItemID.LOGS);
		lightSource = new ItemRequirement("A lightsource", ItemCollections.LIGHT_SOURCES).isNotConsumed();
		hammerSidebar = new ItemRequirement("Hammer (obtainable in quest)", ItemCollections.HAMMER).isNotConsumed();
		hammer = new ItemRequirement("Hammer", ItemCollections.HAMMER).isNotConsumed();
		chisel = new ItemRequirement("Chisel", ItemID.CHISEL).isNotConsumed();
		chiselSidebar = new ItemRequirement("Chisel (obtainable in quest)", ItemID.CHISEL).isNotConsumed();
		mspeakAmulet = new ItemRequirement("M'speak amulet", ItemID.MM_AMULET_OF_MONKEY_SPEAK).isNotConsumed();
		mspeakAmuletEquipped = new ItemRequirement("M'speak amulet", ItemID.MM_AMULET_OF_MONKEY_SPEAK, 1, true).isNotConsumed();

		talisman = new ItemRequirement("Monkey talisman", ItemID.MM_MONKEY_TALISMAN);
		talisman.setTooltip("You can buy one from the Ape Atoll magic shop for 1000 coins");
		talismanOr1000Coins = new ItemRequirements(LogicType.OR, "Monkey talisman or 1000 coins", talisman, new ItemRequirement("1000 coins", ItemCollections.COINS, 1000));
		ninjaGreegree = new ItemRequirement("Ninja greegree", ItemID.MM_MONKEY_GREEGREE_FOR_SMALL_NINJA_MONKEY).isNotConsumed();
		ninjaGreegree.addAlternates(ItemID.MM_MONKEY_GREEGREE_FOR_MEDIUM_NINJA_MONKEY);

		ninjaGreegreeEquipped = ninjaGreegree.equipped();
		translationBook = new ItemRequirement("Translation book", ItemID.GRANDTREE_TRANSLATIONBOOK);
		translationBook.setHighlightInInventory(true);
		translationBook.setTooltip("If it's not in your bank you can get another from Narnode during the quest");

		magicLog = new ItemRequirement("Magic logs", ItemID.MAGIC_LOGS);
		food = new ItemRequirement("Food", ItemCollections.GOOD_EATING_FOOD, -1);
		staminaPotions = new ItemRequirement("Stamina potions", ItemCollections.STAMINA_POTIONS, -1);
		prayerPotions = new ItemRequirement("Prayer potions", ItemCollections.PRAYER_POTIONS, -1);
		antidote = new ItemRequirement("Antidote", ItemCollections.ANTIPOISONS, -1);
		combatGear = new ItemRequirement("Combat gear", -1, -1).isNotConsumed();
		combatGear.setDisplayItemId(BankSlotIcons.getCombatGear());
		climbingBoots = new ItemRequirement("Climbing boots", ItemCollections.CLIMBING_BOOTS);

		handkerchief = new ItemRequirement("Handkerchief", ItemID.MM2_ANITAS_HANDKERCHIEF);
		mysteriousNote = new ItemRequirement("Mysterious note", ItemID.MM2_MYSTERIOUS_NOTE);
		mysteriousNote.setHighlightInInventory(true);
		mysteriousNoteLemon = new ItemRequirement("Mysterious note", ItemID.MM2_MYSTERIOUS_NOTE_LEMON);
		mysteriousNoteLemon.setHighlightInInventory(true);

		mysteriousNoteLemonCandle = new ItemRequirement("Mysterious note", ItemID.MM2_MYSTERIOUS_NOTE_HEATED);
		mysteriousNoteLemonCandle.setHighlightInInventory(true);

		brush = new ItemRequirement("Brush", ItemID.MM2_BRUSH);
		brush.setHighlightInInventory(true);

		grapeBrush = new ItemRequirement("Juice-coated brush", ItemID.MM2_BRUSH_GRAPEJUICE);
		grapeBrush.setHighlightInInventory(true);

		scrawledNote = new ItemRequirement("Scrawled note", ItemID.MM2_GLOUGHS_NOTE);
		scrawledNote.setTooltip("If you've lost it you'll need to go through the process of revealing the text again");
		scrawledNote.setHighlightInInventory(true);

		translatedNote = new ItemRequirement("Translated note", ItemID.MM2_TRANSLATED_NOTE);
		translatedNote.setTooltip("You can get another from Anita");
		translatedNote.setHighlightInInventory(true);

		noCombatItems = new ItemRequirement("No combat items to travel to Entrana", -1, -1);

		greegree = new ItemRequirement("Greegree", ItemCollections.GREEGREES).isNotConsumed();
		greegreeEquipped = greegree.equipped();

		kruksPaw = new ItemRequirement("Kruk's paw", ItemID.MM2_KRUK_REMAINS);
		kruksPaw.setTooltip("You can get another from where you fought Kruk");

		krukGreegree = new ItemRequirement("Kruk monkey greegree", ItemID.MM2_KRUK_GREEGREE, 1, true);
		krukGreegree.setTooltip("If you've lost this you can get another from Zooknock");

		coins20 = new ItemRequirement("Coins", ItemCollections.COINS, 20);

		chiselHighlighted = new ItemRequirement("Chisel", ItemID.CHISEL).isNotConsumed();
		chiselHighlighted.setHighlightInInventory(true);
		deconstructedOnyx = new ItemRequirement("Deconstructed onyx", ItemID.MM2_DECONSTRUCTED_ONYX);
		deconstructedOnyx.setHighlightInInventory(true);
		chargedOnyx = new ItemRequirement("Charged onyx", ItemID.MM2_CHARGED_ONYX);
		chargedOnyx.setHighlightInInventory(true);

		combatGear2 = new ItemRequirement("2 styles of combat gear", -1, -1).isNotConsumed();
		combatGear2.setDisplayItemId(ItemID.DEVIOUS_BOWSWORD);

		nieveFollower = new FollowerRequirement("Nieve", NpcID.MM2_NIEVE_FOLLOWER);
	}

	public void setupConditions()
	{
		// Started quest:
		// 5039 0->1
		// 5032 1->0
		inGloughHouse = new ZoneRequirement(gloughHouseF1, gloughHouseF2, gloughHouseF3);
		inGloughHouseF1 = new ZoneRequirement(gloughHouseF1);
		inGloughHouseF2 = new ZoneRequirement(gloughHouseF2);
		inGloughHouseF3 = new ZoneRequirement(gloughHouseF3);
		inAnitaHouse = new ZoneRequirement(anitaHouse);
		inCaves = new ZoneRequirement(caves, subCaves);
		inZooknockDungeon = new ZoneRequirement(zooknockDungeon);
		inStrongholdFloor2 = new ZoneRequirement(strongholdFloor2);
		inLab = new ZoneRequirement(lab);
		isPastMonkeyBars = new ZoneRequirement(pastMonkeyBars);
		isNorthOfTree = new ZoneRequirement(northOfTree);
		inCrashSiteCavern = new ZoneRequirement(crashSiteCavern);

		// 5039 1->2 when removing handkerchief search option
		foundHandkerchief = new Conditions(LogicType.OR, new VarbitRequirement(VarbitID.MM2_FOUND_HANDKERCHIEF, 2, Operation.GREATER_EQUAL), handkerchief);
		talkedToAnita = new VarbitRequirement(VarbitID.MM2_ANTIAS_CLUE, 1, Operation.GREATER_EQUAL);
		openedCupboard = new Conditions(true, LogicType.OR, new WidgetTextRequirement(229, 1, "You turn the statue and hear a clicking sound in the room."), new ChatMessageRequirement("You have already activated the statue."));
		foundNote = new VarbitRequirement(5028, 1);
		hasBrush = new Conditions(LogicType.OR, grapeBrush, brush);

		// Read note:
		// 5027 5->6
		// 5031 0->1

		triedTranslating = new Conditions(true,
			new WidgetTextRequirement(229, 1, "Some of the ancient Gnome phrases found on the note are missing<br>from the translation book. I should tell the King."));

		greegreeNearby = new ItemOnTileRequirement(greegree);
		krukCorpseNearby = new ObjectCondition(ObjectID.MM2_KRUKS_REMAINS_MULTI);

		kob2Nearby = new NpcCondition(NpcID.MM2_GENERAL_KOB_COMBAT);
		keef2Nearby = new NpcCondition(NpcID.MM2_CHIEFTAN_KEEF_COMBAT);
		defeatedKob = new VarbitRequirement(5035, 1);
		defeatedKeef = new VarbitRequirement(5034, 1);

		// Killed Kruk, 5036 0->2

		smithInLocation1 = new VarbitRequirement(5040, 1); // TODO: Get location
		smithInLocation2 = new VarbitRequirement(5040, 2); // TODO: Get location
		smithInLocation3 = new VarbitRequirement(5040, 3); // TODO: Get location
		smithInLocation4 = new VarbitRequirement(5040, 4); // Smith near rune store

		smithNearby = new NpcCondition(NpcID.MM2_LE_SMITH);

		gorillaNotOnHoldingArea = new Conditions(LogicType.NOR, new NpcCondition(NpcID.MM2_TORTURED_GORILLA_SMALL));

		hasChiselAndHammer = new Conditions(hammer, chisel);

		// Nieve leaves:
		// 5037 0->1 (Steve appears)
		// 5069 0->1 (Gorillas attackable)
		// Nieve stops following:
		// 5038 0->1 (Nieve now at Grand Tree)

		// Killed gorilla 1, south west of Grand Tree:
		// 5068 0->1
		// 5069 1->2

		killedGorillas = new VarbitRequirement(5068, 3);

		nieveFollowing = new NpcInteractingRequirement(NpcID.MM2_NIEVE_FOLLOWER);
	}

	@Override
	protected void setupZones()
	{
		gloughHouseF1 = new Zone(new WorldPoint(2475, 3461, 1), new WorldPoint(2484, 3465, 1));
		gloughHouseF2 = new Zone(new WorldPoint(2484, 3462, 2), new WorldPoint(2489, 3467, 2));
		gloughHouseF3 = new Zone(new WorldPoint(2484, 3462, 3), new WorldPoint(2489, 3467, 3));

		anitaHouse = new Zone(new WorldPoint(2387, 3513, 1), new WorldPoint(2391, 3516, 1));

		caves = new Zone(new WorldPoint(2481, 9000, 1), new WorldPoint(2680, 9300, 1));
		subCaves = new Zone(new WorldPoint(2304, 9132, 1), new WorldPoint(2500, 9400, 1));
		zooknockDungeon = new Zone(new WorldPoint(2690, 9088, 0), new WorldPoint(2813, 9149, 0));

		strongholdFloor2 = new Zone(new WorldPoint(2820, 10048, 2), new WorldPoint(2862, 10110, 2));

		lab = new Zone(new WorldPoint(2199, 5446, 0), new WorldPoint(2220, 5493, 0));

		pastMonkeyBars = new Zone(new WorldPoint(2500, 9203, 1), new WorldPoint(2515, 9213, 1));

		northOfTree = new Zone(new WorldPoint(1979, 5560, 0), new WorldPoint(2047, 5622, 0));

		crashSiteCavern = new Zone(new WorldPoint(2050, 5643, 0), new WorldPoint(2167, 5690, 0));
	}

	public void setupSteps()
	{
		talkToNarnode = new NpcStep(this, NpcID.GRANDTREE_NARNODE_1OP, new WorldPoint(2465, 3496, 0), "Talk to King Narnode Shareen in the Tree Gnome Stronghold.");
		talkToNarnode.addDialogStep("I'll help you look for Glough.");

		goUpToGloughHouse = new ObjectStep(this, ObjectID.LADDER, new WorldPoint(2476, 3463, 0), "Climb up into Glough's house south of the Grand Tree.");
		goUpGloughTree = new ObjectStep(this, ObjectID.GRANDTREE_CLIMBTREE, new WorldPoint(2484, 3464, 1), "Climb up to the next floor in Glough's house.");
		investigateTree = new ObjectStep(this, ObjectID.MM2_GLOUGH_BRANCH_DOWN_MULTI, new WorldPoint(2485, 3464, 2), "Right-click investigate the tree.");

		goDownGloughTree = new ObjectStep(this, ObjectID.MM2_GLOUGH_BRANCH_DOWN_MULTI, new WorldPoint(2485, 3464, 2), "");
		goDownFromGloughHouse = new ObjectStep(this, ObjectID.LADDERTOP, new WorldPoint(2476, 3463, 1), "");
		goUpToAnita = new ObjectStep(this, ObjectID.SPIRALSTAIRS_WOODEN, new WorldPoint(2390, 3513, 0), "");
		talkToAnita = new NpcStep(this, NpcID.GT_ANITA, new WorldPoint(2390, 3515, 1), "");

		goDownFromAnita = new ObjectStep(this, ObjectID.SPIRALSTAIRSTOP_WOODEN, new WorldPoint(2389, 3513, 1), "");
		goUpGloughTreeToThirdFloor = new ObjectStep(this, ObjectID.MM2_GLOUGH_BRANCH_UP_MULTI, new WorldPoint(2484, 3465, 2), "Climb up the tree.");

		investigateStatue = new ObjectStep(this, ObjectID.MM2_GLOUGHS_BUST, new WorldPoint(2487, 3463, 3), "Investigate the nearby statue and turn it.");
		investigateStatue.addDialogStep("Yes");
		searchCupboard = new ObjectStep(this, ObjectID.MM2_GLOUGHS_CUPBOARD_CLOSED, new WorldPoint(2488, 3465, 3), "Search the cupboard.");
		((ObjectStep) (searchCupboard)).addAlternateObjects(ObjectID.MM2_GLOUGHS_CUPBOARD_OPEN);

		searchCrate = new ObjectStep(this, ObjectID.MM2_GLOUGHS_CRATES, new WorldPoint(2485, 3467, 3), "Search the crates.");
		searchRemains = new ObjectStep(this, ObjectID.MM2_GLOUGHS_FIREPLACE_MULTI, new WorldPoint(2487, 3467, 3), "Search the fire remains.");
		usePestleOnLemon = new DetailedQuestStep(this, "Use a pestle and mortar on a lemon", pestleHighlighted, lemonHighlighted);
		useNotesOnCandles = new ObjectStep(this, ObjectID.MM2_GLOUGHS_CANDLES, new WorldPoint(2486, 3463, 3), "Use the notes on the candles.", mysteriousNoteLemon);
		useNotesOnCandles.addIcon(ItemID.MM2_MYSTERIOUS_NOTE_LEMON);
		usePestleOnGrapes = new DetailedQuestStep(this, "Use a pestle and mortar on some grapes.", pestleHighlighted, grapesHighlighted);
		useBrushOnNote = new DetailedQuestStep(this, "Use the brush on the note.", grapeBrush, mysteriousNoteLemonCandle);
		readScrawledNote = new DetailedQuestStep(this, "Read the note.", scrawledNote);
		goDown3rdto2nd = new ObjectStep(this, ObjectID.MM2_GLOUGH_BRANCH_DOWN, new WorldPoint(2484, 3464, 3), "Climb down the tree.");

		useTranslationOnNote = new DetailedQuestStep(this, "Use the translation book on the scrawled note.", translationBook, scrawledNote);
		talkToNarnodeBlank = new NpcStep(this, NpcID.GRANDTREE_NARNODE_1OP, new WorldPoint(2465, 3496, 0), "");
		readTranslatedNote = new DetailedQuestStep(this, "Read the translated note.", translatedNote);
		talkToAuguste = new NpcStep(this, NpcID.ZEP_PICCARD, new WorldPoint(2809, 3354, 0), "Talk to Auguste on Entrana. You can take the hot air balloon there from south of the Grand Tree with a log, or travel from Port Sarim.", noCombatItems);
		talkToAuguste.addDialogStep("Talk about Assistant Le Smith.");
		talkToNarnodeAfterEntrana = new NpcStep(this, NpcID.GRANDTREE_NARNODE_1OP, new WorldPoint(2465, 3496, 0), "Return to King Narnode Shareen.");
		talkToNarnodeAfterEntrana.addDialogStep("Thanks for the information.");

		// Infiltrating
		talkToGarkor = new NpcStep(this, NpcID.MM_GARKOR_AA, new WorldPoint(2807, 2762, 0), "Talk to Garkor on Ape Atoll.", ninjaGreegreeEquipped, mspeakAmulet, talismanOr1000Coins, lightSource, food);
		talkToGarkor.addDialogSteps("King Narnode has sent me.", "It's worth a shot.");
		talkToAwowogei = new ObjectStep(this, ObjectID.MM_THRONE, new WorldPoint(2803, 2765, 0), "Talk to Awowogei.", ninjaGreegreeEquipped, mspeakAmuletEquipped);
		talkToAwowogei.addDialogSteps("I'm here to discuss military strategies.", "Of course, my king.");
		talkToGarkorAfterAwow = new NpcStep(this, NpcID.MM_GARKOR_AA, new WorldPoint(2807, 2762, 0), "Talk to Garkor again.", ninjaGreegreeEquipped);
		talkToArcher = new NpcStep(this, NpcID.MM2_MONKEY_ARCHER, new WorldPoint(2713, 2758, 0), "Talk to a monkey archer on the west of Ape Atoll. Buy a monkey talisman for 1000 coins now if you need one.", ninjaGreegreeEquipped, mspeakAmuletEquipped);
		enterTrapdoor = new ObjectStep(this, ObjectID.MM2_SECRET_ENTRANCE_MULTI, new WorldPoint(2714, 2788, 0), "Enter the trapdoor under some grass in the north west of Ape Atoll.", lightSource, combatGear, food, prayerPotions, staminaPotions);
		enterTrapdoor.addDialogStep("Yes");

		pickUpGreegree = new ItemStep(this, "Pick up your greegree!", greegree);
		doAgilitySection = new AgilityDungeonSteps(this);
		pickUpKrukCorpse = new ObjectStep(this, ObjectID.MM2_KRUKS_REMAINS_MULTI, "Search Kruk's Corpse.");
		leaveKrukDungeon = new ObjectStep(this, ObjectID.MM2_MAZE_EXIT_ROPE, new WorldPoint(2513, 9207, 1), "Leave the dungeon up the rope to the west.");
		goDownToZooknock = new ObjectStep(this, ObjectID.MM_BAMBOO_LADDER_DUNGEON_ENTRANCE, new WorldPoint(2763, 2703, 0), "Enter the dungeon in south Ape Atoll.", greegreeEquipped, kruksPaw, talisman);

		List<WorldPoint> zooknockDungeonPath = Arrays.asList(
			new WorldPoint(2768, 9101, 0),
			new WorldPoint(2788, 9102, 0),
			new WorldPoint(2788, 9109, 0),
			new WorldPoint(2766, 9111, 0),
			new WorldPoint(2764, 9121, 0),
			new WorldPoint(2800, 9116, 0),
			new WorldPoint(2802, 9094, 0),
			new WorldPoint(2809, 9096, 0),
			new WorldPoint(2807, 9130, 0),
			new WorldPoint(2787, 9128, 0),
			new WorldPoint(2770, 9133, 0),
			new WorldPoint(2747, 9134, 0),
			new WorldPoint(2734, 9138, 0),
			new WorldPoint(2716, 9139, 0),
			new WorldPoint(2717, 9131, 0),
			new WorldPoint(2738, 9129, 0),
			new WorldPoint(2738, 9121, 0),
			new WorldPoint(2709, 9118, 0),
			new WorldPoint(2711, 9108, 0),
			new WorldPoint(2741, 9106, 0),
			new WorldPoint(2741, 9095, 0),
			new WorldPoint(2721, 9099, 0),
			new WorldPoint(2713, 9094, 0),
			new WorldPoint(2695, 9096, 0),
			new WorldPoint(2693, 9113, 0),
			new WorldPoint(2697, 9128, 0),
			new WorldPoint(2696, 9144, 0),
			new WorldPoint(2722, 9147, 0),
			new WorldPoint(2750, 9142, 0),
			new WorldPoint(2773, 9144, 0),
			new WorldPoint(2799, 9138, 0)
		);
		talkToZooknock = new NpcStep(this, NpcID.MM_ZOOKNOCK_AA, new WorldPoint(2805, 9143, 0), "Talk to Zooknock in the north east of the dungeon.", greegreeEquipped, kruksPaw, talisman);
		talkToZooknock.addDialogStep("Talk about your mission.");
		((NpcStep) (talkToZooknock)).setLinePoints(zooknockDungeonPath);

		talkToAwowAsKruk = new ObjectStep(this, ObjectID.MM_THRONE, new WorldPoint(2803, 2765, 0), "Talk to Awowogei as Kruk.", krukGreegree, mspeakAmuletEquipped);
		talkToAwowAsKruk.addDialogSteps();

		talkToGarkorAfterKruk = new NpcStep(this, NpcID.MM_GARKOR_AA, new WorldPoint(2807, 2762, 0), "Talk to Garkor on Ape Atoll.");

		enterTrollStronghold = new ObjectStep(this, ObjectID.TROLL_STRONGHOLD_DOOR, new WorldPoint(2839, 3690, 0),
			"Enter the Troll Stronghold, ready to fight Kob.", climbingBoots, combatGear);

		talkToKob = new NpcStep(this, NpcID.MM2_GENERAL_KOB_NONCOMBAT, new WorldPoint(2831, 10060, 2), "Talk to Kob with Protect from Melee on, ready to fight.");
		talkToKob.addDialogSteps("I know about your deal with the monkeys.", "You won't be around to crush anyone when I'm done with you.", "I accept your challenge.");

		fightKob = new NpcStep(this, NpcID.MM2_GENERAL_KOB_COMBAT, new WorldPoint(2831, 10060, 2), "Fight Kob. He can be safespotted from the doorway.");

		if (client.getBoostedSkillLevel(Skill.AGILITY) >= 71)
		{
			talkToKeef = new NpcStep(this, NpcID.MM2_CHIEFTAN_KEEF_NONCOMBAT, new WorldPoint(2542, 3031, 0), "Talk to Keef in Gu'Tanoth. Get to him via the agility shortcut next to him. Be prepared to fight him and pray Protect from Melee.");
		}
		else
		{
			talkToKeef = new NpcStep(this, NpcID.MM2_CHIEFTAN_KEEF_NONCOMBAT, new WorldPoint(2542, 3031, 0), "Talk to Keef in Gu'Tanoth. Be prepared to fight him and pray Protect from Melee.", coins20);
		}
		talkToKeef.addDialogSteps("I know about your deal with the monkeys.", "I offer to spare your life.", "I accept your challenge.");

		fightKeef = new NpcStep(this, NpcID.MM2_CHIEFTAN_KEEF_COMBAT, new WorldPoint(2542, 3031, 0), "Fight Keef. He can be safespotted.");
		talkToGarkorAfterKeef = new NpcStep(this, NpcID.MM_GARKOR_AA, new WorldPoint(2807, 2762, 0), "Talk to Garkor on Ape Atoll.", krukGreegree);
		findSmith = new DetailedQuestStep(this, "Search around the various rooftops in Ape Atoll for Assistant Le Smith.", krukGreegree);
		talkToSmith = new NpcStep(this, NpcID.MM2_LE_SMITH, "Talk to Assistant Le Smith.", krukGreegree);
		talkToSmith.addDialogSteps("I was going to ask you the same question.", "Why is that?", "Awowogei has already informed me about the battleships.", "Where is the fleet, currently?");
		talkToGarkorAfterSmith = new NpcStep(this, NpcID.MM_GARKOR_AA, new WorldPoint(2807, 2762, 0), "Talk to Garkor.", krukGreegree);
		talkToMonkeyGuard = new NpcStep(this, NpcID.MM2_MONKEY_BOAT_GUARD, new WorldPoint(2694, 2784, 0), "Talk to the monkey guard on the north west of Ape Atoll.", krukGreegree, mspeakAmuletEquipped);
		talkToMonkeyGuard.addDialogSteps("What's going on here?", "Construction platform?", "Can I visit the platform?", "I would like to go back to the construction platform.");

		sabotageShips = new MM2Sabotage(this, talkToMonkeyGuard);

		talkToGarkorAfterSabotage = new NpcStep(this, NpcID.MM_GARKOR_AA, new WorldPoint(2807, 2762, 0), "Return to Garkor on Ape Atoll.", krukGreegree, mspeakAmulet);

		enterKrukDungeonAgain = new ObjectStep(this, ObjectID.MM2_SECRET_ENTRANCE_MULTI, new WorldPoint(2714, 2788, 0), "Enter the trapdoor under some grass in the north west of Ape Atoll.", krukGreegree);
		enterKrukDungeonAgain.addDialogStep("Yes");
		climbMonkeyBarsAsKruk = new ObjectStep(this, ObjectID.MM2_MONKEYBARS_START, new WorldPoint(2504, 9190, 1), "Climb across the monkey bars to the north as Kruk.", krukGreegree);
		enterLabratory = new ObjectStep(this, ObjectID.MM2_SECRET_LAB_ENTRANCE_MULTI, new WorldPoint(2510, 9213, 1), "Enter the passage to the north.", krukGreegree);
		climbOnGorilla = new NpcStep(this, NpcID.MM2_TORTURED_GORILLA_SMALL, new WorldPoint(2213, 5464, 0), "Climb onto the stunted gorilla.", krukGreegree);
		fightGorillas = new NpcStep(this, NpcID.MM2_TORTURED_GORILLA_LAB, new WorldPoint(2207, 5474, 0), "Fight the tortured gorillas. When you're done, be careful NOT to click out of the cutscene or you'll need to do this again.", true);
		dismountGorilla = new ObjectStep(this, ObjectID.MM2_LAB_DISMOUNT, new WorldPoint(2213, 5464, 0), "Dismount the gorilla in the holding area.");
		getChiselAndHammer = new ObjectStep(this, ObjectID.MM2_LAB_CRATE_01, new WorldPoint(2209, 5468, 0), "Search the crates nearby for a chisel and hammer.");
		tamperWithDevice = new ObjectStep(this, ObjectID.MM2_LAB_MUTAGEN_ONYX_ORB, new WorldPoint(2208, 5489, 0), "Dismount the gorilla on a holding area and tamper with the device to the north.");
		useChiselOnOnyx = new DetailedQuestStep(this, "Use a chisel on the charged onyx.", chiselHighlighted, chargedOnyx);
		useOnyxOnDevice = new ObjectStep(this, ObjectID.MM2_LAB_MUTAGEN_ONYX_ORB, new WorldPoint(2208, 5489, 0), "Use the onyx back on the device.", deconstructedOnyx);
		useOnyxOnDevice.addIcon(ItemID.MM2_DECONSTRUCTED_ONYX);
		investigateIncubationChamber = new ObjectStep(this, ObjectID.MM2_LAB_INCUBATOR_MULTI, new WorldPoint(2216, 5480, 0), "Investigate one of the incubation chambers in the room.");
		talkToGarkorAfterLab = new NpcStep(this, NpcID.MM_GARKOR_AA, new WorldPoint(2807, 2762, 0), "Return to Garkor.", krukGreegree, mspeakAmulet);
		talkToAwowAfterLab = new ObjectStep(this, ObjectID.MM_THRONE, new WorldPoint(2803, 2765, 0), "Talk to Awowogei.", krukGreegree, mspeakAmuletEquipped);
		talkToGarkorAfterLabAgain = new NpcStep(this, NpcID.MM_GARKOR_AA, new WorldPoint(2807, 2762, 0), "Talk to Garkor again.", krukGreegree);

		talkToNarnodeAfterLab = new NpcStep(this, NpcID.GRANDTREE_NARNODE_1OP, new WorldPoint(2465, 3496, 0), "Talk to King Narnode Shareen in the Tree Gnome Stronghold. Be prepared for fighting, and bring 2 combat styles.");

		talkToNieve = new NpcStep(this, NpcID.SLAYER_MASTER_NIEVE, new WorldPoint(2432, 3424, 0), "Talk to Nieve south of the Grand Tree. Be prepared to fight with 2 combat styles.", combatGear2, food);
		talkToNieve.addDialogSteps("Talk about Monkey Madness II.", "Yes.");

		killGorillasInStronghold = new DetailedQuestStep(this, "Roam around with Nieve killing the gorillas within the Gnome Stronghold. If you lose Nieve, you can get her again from the Grand Tree.");
		enterNorthOfTree = new ObjectStep(this, ObjectID.MM2_STRONGHOLD_FENCE_MULTI, new WorldPoint(2435, 3520, 0), "Enter the breach north west of the Grand Tree with Nieve.", combatGear2, food, prayerPotions);
		enterNorthOfTree.addDialogStep("Yes, let's go.");
		enterStrongholdCave = new ObjectStep(this, ObjectID.MM2_CAVERN_ENTRANCE, new WorldPoint(2027, 5613, 0), "Enter the cavern to the north east.");
		killTorturedAndDemonic = new NpcStep(this, NpcID.MM2_TORTURED_GORILLA_1, new WorldPoint(2129, 5682, 0), "Talk to Glough in the north of the cavern. After, defeat the tortured and demonic gorillas.", true, combatGear2);
		// TODO: 7152 and 7153 are the non-attackable version. Once NPC changes are properly checked, remove these
		((NpcStep) (killTorturedAndDemonic)).addAlternateNpcs(NpcID.MM2_TORTURED_GORILLA_2, NpcID.MM2_TORTURED_GORILLA_NONCOMBAT, NpcID.MM2_DEMON_GORILLA_NONCOMBAT, NpcID.MM2_DEMON_GORILLA_1_MELEE, NpcID.MM2_DEMON_GORILLA_1_RANGED,
			NpcID.MM2_DEMON_GORILLA_1_MAGIC);

		enterNorthOfTreeNoNieve = new ObjectStep(this, ObjectID.MM2_STRONGHOLD_FENCE_MULTI, new WorldPoint(2435, 3520, 0), "Enter the breach north west of the Grand Tree.", combatGear, food, prayerPotions);

		fightGlough = new NpcStep(this, NpcID.MM2_DEMON_GLOUGH, new WorldPoint(2075, 5677, 0), "Defeat Glough. He has 3 phases, changing rooms each time. You can safe spot the first 2 phases. Protect from Melee if you're next to him, or Protect from Magic if not in the third phase.", true);
		fightGlough.addText("The easiest way to do phase 3 is to attack him from 1 tile away, have Protect from Magic on, and step away a tile whenever he pulls you closer.");
		((NpcStep) (fightGlough)).addAlternateNpcs(NpcID.MM2_DEMON_GLOUGH_NOMOVE, NpcID.MM2_DEMON_GLOUGH_CUTSCENE, NpcID.MM2_CAVERN_GLOUGH);
		((NpcStep) (fightGlough)).setMaxRoamRange(200);
		fightGlough.addSubSteps(enterNorthOfTreeNoNieve);
		talkToZooknockToFinish = new NpcStep(this, NpcID.MM2_ZOOKNOCK_POSTBOSS, new WorldPoint(2027, 5610, 0), "Talk to Zooknock to teleport to the Grand Tree.");
		talkToZooknockToFinish.addDialogStep("Yes.");
		talkToNarnodeToFinish = new NpcStep(this, NpcID.GRANDTREE_NARNODE_1OP, new WorldPoint(2465, 3496, 0), "Talk to Narnode to finish the madness!");

	}

	public void setupConditionalSteps()
	{
		goToGlough2ndFloor = new ConditionalStep(this, goUpToGloughHouse);
		goToGlough2ndFloor.addStep(inGloughHouseF1, goUpGloughTree);

		goInvestigateGloughHouse = new ConditionalStep(this, goToGlough2ndFloor, "Investigate the tree in the 2nd floor of Glough's house.");
		goInvestigateGloughHouse.addStep(inGloughHouseF2, investigateTree);

		leaveGloughHouse = new ConditionalStep(this, goDownFromGloughHouse);
		leaveGloughHouse.addStep(inGloughHouseF3, goDown3rdto2nd);
		leaveGloughHouse.addStep(inGloughHouseF2, goDownGloughTree);

		goTalkToAnita = new ConditionalStep(this, goUpToAnita, "Go talk to Anita in the west of the Tree Gnome Stronghold.", handkerchief);
		goTalkToAnita.addStep(inAnitaHouse, talkToAnita);
		goTalkToAnita.addStep(inGloughHouse, leaveGloughHouse);
		goTalkToAnita.addDialogSteps("What's wrong?", "He might be in trouble, I could help.");

		goToGlough3rdFloor = new ConditionalStep(this, goToGlough2ndFloor, "Return to Glough's house and climb to the 3rd floor.", lemon, grape, pestle);
		goToGlough3rdFloor.addStep(inGloughHouseF2, goUpGloughTreeToThirdFloor);

		goInvestigateUpstairs = new ConditionalStep(this, goToGlough3rdFloor);
		goInvestigateUpstairs.addStep(new Conditions(scrawledNote), readScrawledNote);
		goInvestigateUpstairs.addStep(new Conditions(inGloughHouseF3, grapeBrush, mysteriousNoteLemonCandle), useBrushOnNote);
		goInvestigateUpstairs.addStep(new Conditions(inGloughHouseF3, hasBrush, mysteriousNoteLemonCandle), usePestleOnGrapes);
		goInvestigateUpstairs.addStep(new Conditions(inGloughHouseF3, hasBrush, mysteriousNoteLemon), useNotesOnCandles);
		goInvestigateUpstairs.addStep(new Conditions(openedCupboard, hasBrush, mysteriousNote), usePestleOnLemon);
		goInvestigateUpstairs.addStep(new Conditions(inGloughHouseF3, openedCupboard, hasBrush), searchRemains);
		goInvestigateUpstairs.addStep(new Conditions(inGloughHouseF3, openedCupboard), searchCrate);
		goInvestigateUpstairs.addStep(inGloughHouseF3, investigateStatue);
		goInvestigateUpstairs.addStep(inAnitaHouse, goDownFromAnita);

		goShowNoteToNarnode = new ConditionalStep(this, talkToNarnodeBlank, "Go talk to Narnode.", scrawledNote);
		goShowNoteToNarnode.addStep(inGloughHouse, leaveGloughHouse);

		goTalkToAnitaWithNote = new ConditionalStep(this, goUpToAnita, "Go talk to Anita in the west of the Tree Gnome Stronghold.", scrawledNote);
		goTalkToAnitaWithNote.addStep(inAnitaHouse, talkToAnita);
		goTalkToAnitaWithNote.addStep(inGloughHouse, leaveGloughHouse);
		goTalkToAnitaWithNote.addDialogStep("Could you translate these notes?");

		bringTranslationToNarnode = new ConditionalStep(this, talkToNarnodeBlank, "Return to Narnode with the translation.", translatedNote);
		bringTranslationToNarnode.addStep(inAnitaHouse, goDownFromAnita);
	}

	@Override
	public List<ItemRequirement> getItemRequirements()
	{
		return Arrays.asList(lemon, grape, pestle, pickaxe, logs, lightSource, translationBook, hammerSidebar, chiselSidebar, mspeakAmulet, talismanOr1000Coins, ninjaGreegree);
	}

	@Override
	public List<ItemRequirement> getItemRecommended()
	{
		return Arrays.asList(magicLog, food, staminaPotions, prayerPotions, antidote, combatGear);
	}

	@Override
	public List<String> getCombatRequirements()
	{
		return Arrays.asList("Kruk (level 149, flinchable)", "Keef (level 178, safespottable)", "Kob (level 185, safespottable)", "9 Tortured gorillas (level 141)", "2 Demonic Gorillas (level 275)", "Glough (level 431)");
	}

	@Override
	public QuestPointReward getQuestPointReward()
	{
		return new QuestPointReward(4);
	}

	@Override
	public List<ExperienceReward> getExperienceRewards()
	{
		return Arrays.asList(
			new ExperienceReward(Skill.SLAYER, 80000),
			new ExperienceReward(Skill.AGILITY, 60000),
			new ExperienceReward(Skill.THIEVING, 50000),
			new ExperienceReward(Skill.HUNTER, 50000));
	}

	@Override
	public List<ItemReward> getItemRewards()
	{
		return Arrays.asList(
			new ItemReward("50,000 Experience Lamps (Any Combat Skill)", ItemID.THOSF_REWARD_LAMP, 2), //4447 is placeholder for filter
			new ItemReward("A Royal Seed Pod", ItemID.MM2_ROYAL_SEED_POD, 1),
			new ItemReward("A pet monkey", ItemID.MM2_MONKEY, 1));
	}

	@Override
	public List<UnlockReward> getUnlockRewards()
	{
		return Arrays.asList(
			new UnlockReward("Access to Demonic Gorillas"),
			new UnlockReward("A new Gnome Glider location"),
			new UnlockReward("Access to a bank on Ape Atoll"),
			new UnlockReward("Ability to wield the Heavy Ballista"),
			new UnlockReward("Access to Maniacal Monkey hunting area"));
	}

	@Override
	public List<PanelDetails> getPanels()
	{
		List<PanelDetails> allSteps = new ArrayList<>();
		allSteps.add(new PanelDetails("Starting off",
			Arrays.asList(talkToNarnode, goInvestigateGloughHouse, goTalkToAnita, goToGlough3rdFloor, investigateStatue, searchRemains, searchCrate,
				usePestleOnLemon, useNotesOnCandles, usePestleOnGrapes, useBrushOnNote, readScrawledNote, useTranslationOnNote, goShowNoteToNarnode, goTalkToAnitaWithNote,
				bringTranslationToNarnode, talkToAuguste, talkToNarnodeAfterEntrana),
			Arrays.asList(lemon, grapesHighlighted, translationBook, pestleHighlighted, noCombatItems), Arrays.asList(logs, magicLog)));

		List<QuestStep> chapter2Steps = QuestUtil.toArrayList(talkToGarkor, talkToAwowogei, talkToGarkorAfterAwow, talkToArcher, enterTrapdoor);
		chapter2Steps.addAll(((AgilityDungeonSteps) (doAgilitySection)).getDisplaySteps());
		chapter2Steps.addAll(Arrays.asList(pickUpKrukCorpse, leaveKrukDungeon, goDownToZooknock, talkToZooknock, talkToAwowAsKruk, talkToGarkorAfterKruk));
		allSteps.add(new PanelDetails("Going undercover", chapter2Steps, ninjaGreegree, mspeakAmulet, talismanOr1000Coins, lightSource, combatGear, food, prayerPotions, staminaPotions));

		allSteps.add(new PanelDetails("Defeating trolls and ogres",
			Arrays.asList(enterTrollStronghold, talkToKob, fightKob, talkToKeef, fightKeef), combatGear, climbingBoots,
			coins20.hideConditioned(new SkillRequirement(Skill.AGILITY, 71, true))));

		List<QuestStep> sabotageSteps = QuestUtil.toArrayList(talkToGarkorAfterKeef, findSmith, talkToSmith, talkToGarkorAfterSmith,
			talkToMonkeyGuard);
		sabotageSteps.addAll(sabotageShips.getDisplaySteps());
		allSteps.add(new PanelDetails("Sabotage",
			sabotageSteps, krukGreegree, mspeakAmulet));

		allSteps.add(new PanelDetails("Glough's experiments",
			Arrays.asList(talkToGarkorAfterSabotage, enterKrukDungeonAgain, climbMonkeyBarsAsKruk, enterLabratory, climbOnGorilla,
				fightGorillas, tamperWithDevice, useChiselOnOnyx, useOnyxOnDevice, investigateIncubationChamber, talkToGarkorAfterLab, talkToAwowAfterLab,
				talkToGarkorAfterLabAgain), krukGreegree, mspeakAmulet));

		allSteps.add(new PanelDetails("Defending the tree",
			Arrays.asList(talkToNarnodeAfterLab, talkToNieve, killGorillasInStronghold, enterNorthOfTree, enterStrongholdCave, killTorturedAndDemonic,
				fightGlough, talkToZooknockToFinish, talkToNarnodeToFinish), combatGear2));
		return allSteps;
	}

	@Override
	public List<Requirement> getGeneralRecommended()
	{
		ArrayList<Requirement> req = new ArrayList<>();
		req.add(new ItemRequirement("It is beneficial to have a high Combat and Agility level", -1, -1));
		return req;
	}

	@Override
	public List<Requirement> getGeneralRequirements()
	{
		ArrayList<Requirement> req = new ArrayList<>();
		req.add(new QuestRequirement(QuestHelperQuest.ENLIGHTENED_JOURNEY, QuestState.FINISHED));
		req.add(strongholdBalloon);
		req.add(new QuestRequirement(QuestHelperQuest.THE_EYES_OF_GLOUPHRIE, QuestState.FINISHED));
		req.add(new VarbitRequirement(QuestVarbits.QUEST_RECIPE_FOR_DISASTER_MONKEY_AMBASSADOR.getId(),
			Operation.GREATER_EQUAL, 50, "Finished the 'Freeing King Awowogei' subquest of RFD"));
		req.add(new QuestRequirement(QuestHelperQuest.TROLL_STRONGHOLD, QuestState.FINISHED));
		req.add(new QuestRequirement(QuestHelperQuest.WATCHTOWER, QuestState.FINISHED));
		req.add(new SkillRequirement(Skill.SLAYER, 69));
		req.add(new SkillRequirement(Skill.CRAFTING, 70));
		req.add(new SkillRequirement(Skill.HUNTER, 60));
		req.add(new SkillRequirement(Skill.AGILITY, 55));
		req.add(new SkillRequirement(Skill.THIEVING, 55));
		req.add(new SkillRequirement(Skill.FIREMAKING, 60));
		return req;
	}
}
