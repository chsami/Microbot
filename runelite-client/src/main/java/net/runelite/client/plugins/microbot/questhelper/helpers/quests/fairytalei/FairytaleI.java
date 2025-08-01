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
package net.runelite.client.plugins.microbot.questhelper.helpers.quests.fairytalei;

import net.runelite.client.plugins.microbot.questhelper.collections.ItemCollections;
import net.runelite.client.plugins.microbot.questhelper.panel.PanelDetails;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.BasicQuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.conditional.Conditions;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemOnTileRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.npc.DialogRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.player.InInstanceRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.quest.QuestRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.util.LogicType;
import net.runelite.client.plugins.microbot.questhelper.requirements.widget.WidgetTextRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.zone.Zone;
import net.runelite.client.plugins.microbot.questhelper.requirements.zone.ZoneRequirement;
import net.runelite.client.plugins.microbot.questhelper.rewards.ExperienceReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.ItemReward;
import net.runelite.client.plugins.microbot.questhelper.rewards.QuestPointReward;
import net.runelite.client.plugins.microbot.questhelper.steps.*;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;

import java.util.*;

public class FairytaleI extends BasicQuestHelper
{
	ItemRequirement secateurs, draynorSkull, spade, ghostspeak, dramenOrLunarStaff, randomItems;

	ItemRequirement varrockTeleport, faladorTeleport, lumbridgeTeleport, moryTele, food;

	ItemRequirement symptomsList, magicSecateurs, magicSecateursEquipped, queensSecateurs, items3, skullOrSpade;

	Zone zanaris, towerF1, towerF2, grotto, tanglefootRoom;

	Requirement inZanaris, inTowerF1, inTowerF2, inGrotto, inTanglefootRoom;

	Requirement talkedToFarmers, secateursNearby;

	QuestStep talkToMartin, talkToFarmers, talkToMartinAgain;

	QuestStep enterZanaris, talkToGodfather, talkToNuff, climbTowerF0ToF1, climbTowerF1ToF2, talkToZandar;

	QuestStep talkToMortifer, getSkull, giveMortiferItems, enterGrotto, talkToSpirit;

	QuestStep enterZanarisForFight, enterTanglefootRoom, killTanglefoot, pickUpSecateurs, enterZanarisForEnd,
		talkToGodfatherToFinish;

	@Override
	public Map<Integer, QuestStep> loadSteps()
	{
		initializeRequirements();
		setupConditions();
		setupSteps();

		Map<Integer, QuestStep> steps = new HashMap<>();

		steps.put(0, talkToMartin);

		ConditionalStep goTalkToFarmers = new ConditionalStep(this, talkToFarmers);
		goTalkToFarmers.addStep(talkedToFarmers, talkToMartinAgain);
		steps.put(10, goTalkToFarmers);

		ConditionalStep goTalkToGodfather = new ConditionalStep(this, enterZanaris);
		goTalkToGodfather.addStep(inZanaris, talkToGodfather);
		steps.put(20, goTalkToGodfather);

		ConditionalStep goTalkToNuff = new ConditionalStep(this, enterZanaris);
		goTalkToNuff.addStep(inZanaris, talkToNuff);
		steps.put(30, goTalkToNuff);

		ConditionalStep goTalkToZandar = new ConditionalStep(this, climbTowerF0ToF1);
		goTalkToZandar.addStep(inTowerF2, talkToZandar);
		goTalkToZandar.addStep(inTowerF1, climbTowerF1ToF2);
		steps.put(40, goTalkToZandar);

		ConditionalStep goTalkToMortifer = new ConditionalStep(this, getSkull);
		goTalkToMortifer.addStep(draynorSkull, talkToMortifer);
		steps.put(50, goTalkToMortifer);

		ConditionalStep goEnchantSecateurs = new ConditionalStep(this, enterGrotto);
		goEnchantSecateurs.addStep(inGrotto, talkToSpirit);
		steps.put(60, goEnchantSecateurs);

		ConditionalStep goKillTanglefoot = new ConditionalStep(this, enterZanarisForFight);
		goKillTanglefoot.addStep(secateursNearby, pickUpSecateurs);
		goKillTanglefoot.addStep(inTanglefootRoom, killTanglefoot);
		goKillTanglefoot.addStep(inZanaris, enterTanglefootRoom);
		steps.put(70, goKillTanglefoot);

		ConditionalStep finishQuest = new ConditionalStep(this, enterZanarisForEnd);
		finishQuest.addStep(inZanaris, talkToGodfatherToFinish);
		steps.put(80, finishQuest);

		return steps;
	}

	@Override
	protected void setupRequirements()
	{
		secateurs = new ItemRequirement("Secateurs", ItemID.SECATEURS);
		draynorSkull = new ItemRequirement("Draynor skull", ItemID.FAIRY_SKULL);
		draynorSkull.canBeObtainedDuringQuest();
		skullOrSpade = new ItemRequirement("Draynor skull or a spade to get it", ItemID.FAIRY_SKULL);
		skullOrSpade.addAlternates(ItemID.SPADE);
		spade = new ItemRequirement("Spade", ItemID.SPADE).isNotConsumed();
		ghostspeak = new ItemRequirement("Ghostspeak amulet", ItemCollections.GHOSTSPEAK, 1, true).isNotConsumed();
		ghostspeak.setTooltip("You can get another from Father Urhney in the Lumbridge Swamp");
		dramenOrLunarStaff = new ItemRequirement("Dramen or lunar staff", ItemID.DRAMEN_STAFF, 1, true).isNotConsumed();
		dramenOrLunarStaff.addAlternates(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF);
		dramenOrLunarStaff.setDisplayMatchedItemName(true);
		randomItems = new ItemRequirement("3 random items requested by Malignius", -1);

		varrockTeleport = new ItemRequirement("Varrock teleport", ItemID.POH_TABLET_VARROCKTELEPORT);
		faladorTeleport = new ItemRequirement("Falador teleport", ItemID.POH_TABLET_FALADORTELEPORT);
		lumbridgeTeleport = new ItemRequirement("Lumbridge teleport", ItemID.POH_TABLET_LUMBRIDGETELEPORT);
		moryTele = new ItemRequirement("Teleport to Morytania", ItemID.TELEPORTSCROLL_MORTTON);
		moryTele.addAlternates(ItemID.TELETAB_BARROWS, ItemID.ECTOPHIAL);
		food = new ItemRequirement("Food", ItemCollections.GOOD_EATING_FOOD, -1);

		symptomsList = new ItemRequirement("Symptoms list", ItemID.FAIRY_SYMPTOMS_LIST);
		symptomsList.setTooltip("You can get another from Fairy Nuff");
		magicSecateurs = new ItemRequirement("Magic secateurs", ItemID.FAIRY_ENCHANTED_SECATEURS);
		magicSecateurs.setTooltip("If you lost these you'll need to have the Nature Spirit enchant another secateur");
		magicSecateursEquipped = new ItemRequirement("Magic secateurs", ItemID.FAIRY_ENCHANTED_SECATEURS, 1, true);
		magicSecateursEquipped.setTooltip("If you lost these you'll need to have the Nature Spirit enchant another secateur");
		queensSecateurs = new ItemRequirement("Queen's secateurs", ItemID.FAIRY_QUEEN_SECATEURS);
		queensSecateurs.setTooltip("You can get another by killing another Tanglefoot");
		items3 = new ItemRequirement("3 items Mortifer told you to get", -1, -1);
	}

	@Override
	protected void setupZones()
	{
		zanaris = new Zone(new WorldPoint(2368, 4353, 0), new WorldPoint(2495, 4479, 0));
		towerF1 = new Zone(new WorldPoint(2900, 3324, 1), new WorldPoint(2914, 3341, 1));
		towerF2 = new Zone(new WorldPoint(2900, 3324, 2), new WorldPoint(2914, 3341, 2));
		grotto = new Zone(new WorldPoint(3435, 9733, 1), new WorldPoint(3448, 9746, 1));
		tanglefootRoom = new Zone(new WorldPoint(2368, 4353, 0), new WorldPoint(2402, 4399, 0));
	}

	public void setupConditions()
	{
		inZanaris = new ZoneRequirement(zanaris);
		inTowerF1 = new ZoneRequirement(towerF1);
		inTowerF2 = new ZoneRequirement(towerF2);
		inGrotto = new ZoneRequirement(grotto);
		inTanglefootRoom = new Conditions(new InInstanceRequirement(), new ZoneRequirement(tanglefootRoom));

		// Enter Zanaris,
		// 1808 0->1
		// 1807 0->1

		// Given skull
		// Try 1:
		// 1804 0->8
		// 1805 0->9
		// 1806 0->10
		// 2543 0->8->296->10536
		// Mosquito, jangerberries, potato cactus

		// Try 2
		// 1804: 15
		// 1805: 16
		// 1807: 17
		// 2543 stays 0
		// Baby dragon bones, uncut diamond, raw cave eel

		// Try 3:
		// 1804: 14, Volencia moss


		talkedToFarmers = new Conditions(true, LogicType.OR,
			new WidgetTextRequirement(InterfaceID.Questjournal.TEXTLAYER, true, "back and talk to <col=800000>Martin"),
			new DialogRequirement("Right, well thanks for your input."),
			new DialogRequirement("I don't think the crops ARE failing"));

		secateursNearby = new ItemOnTileRequirement(queensSecateurs);
	}

	public void setupSteps()
	{
		talkToMartin = new NpcStep(this, NpcID.MARTIN_THE_MASTER_FARMER, new WorldPoint(3078, 3256, 0),
			"Talk to Martin in the Draynor Market.");
		talkToMartin.addDialogSteps("Ask about the quest.", "Anything I can help with?", "Now that I think about it, " +
			"you're right!");
		talkToFarmers = new NpcStep(this, NpcID.ELSTAN,
			"Talk to 5 farmers, then return to Martin in Draynor Village. The recommended 5 are:");
		talkToFarmers.addText("Frizzy in Port Sarim.");
		talkToFarmers.addText("Elstan north west of Draynor.");
		talkToFarmers.addText("Heskel in Falador Park.");
		talkToFarmers.addText("Treznor south of Varrock Castle.");
		talkToFarmers.addText("Dreven south of Varrock, next to the Champions' Guild.");
		talkToFarmers.addDialogStep("Are you a member of the Group of Advanced Gardeners?");
		((NpcStep) (talkToFarmers)).addAlternateNpcs(NpcID.FARMING_GARDENER_SPIRIT_TREE_1, NpcID.FARMING_GARDENER_TREE_2, NpcID.FARMING_GARDENER_BUSH_1, NpcID.FARMING_GARDENER_TREE_4,
			NpcID.FARMING_GARDENER_TREE_3_02);

		talkToMartinAgain = new NpcStep(this, NpcID.MARTIN_THE_MASTER_FARMER, new WorldPoint(3078, 3256, 0),
			"Return to Martin in the Draynor Market.");
		talkToMartinAgain.addDialogStep("Ask about the quest.");
		talkToFarmers.addSubSteps(talkToMartinAgain);

		enterZanaris = new ObjectStep(this, ObjectID.ZANARISDOOR, new WorldPoint(3202, 3169, 0), "Travel to Zanaris.",
			dramenOrLunarStaff);

		talkToGodfather = new NpcStep(this, NpcID.FAIRY_GODFATHER2, new WorldPoint(2447, 4430, 0),
			"Talk to the Fairy Godfather in the Throne Room.");
		talkToGodfather.addDialogStep("Where's the Fairy Queen?");

		talkToNuff = new NpcStep(this, NpcID.FAIRY_NUFF2, new WorldPoint(2386, 4472, 0),
			"Talk to Fairy Nuff in north west Zanaris.");
		climbTowerF0ToF1 = new ObjectStep(this, ObjectID.FAI_DARKWIZTOWER_SPIRALSTAIRS, new WorldPoint(2908, 3335, 0),
			"Talk to Zandar in the Dark Wizards' Tower west of Falador.", symptomsList);
		climbTowerF1ToF2 = new ObjectStep(this, ObjectID.FAI_DARKWIZTOWER_SPIRALSTAIRS_MIDDLE, new WorldPoint(2908, 3335, 1),
			"Talk to Zandar in the Dark Wizards' Tower west of Falador.", symptomsList);
		climbTowerF1ToF2.addDialogStep("Climb up");
		talkToZandar = new NpcStep(this, NpcID.ZANDAR_HORFYRE, new WorldPoint(2907, 3335, 2),
			"Talk to Zandar in the Dark Wizards' Tower west of Falador.", symptomsList);
		talkToZandar.addSubSteps(climbTowerF1ToF2, climbTowerF0ToF1);

		talkToMortifer = new NpcStep(this, NpcID.ELEMENTAL_WIZARD_BOSS, new WorldPoint(2991, 3270, 0),
			"Talk to Malignius Mortifer west of Port Sarim.", draynorSkull);
		talkToMortifer.addDialogSteps("I need help with fighting a Tanglefoot.",
			"I was asking you about fighting a Tanglefoot...");
		getSkull = new DigStep(this, new WorldPoint(3106, 3383, 0), "Dig for a skull in the north of Draynor Manor.");
		giveMortiferItems = new NpcStep(this, NpcID.ELEMENTAL_WIZARD_BOSS, new WorldPoint(2991, 3270, 0),
			"Give Malignius Mortifer the items he wanted.");
		enterGrotto = new ObjectStep(this, ObjectID.GROTTO_DOOR_DRUIDICSPIRIT, new WorldPoint(3440, 3337, 0),
			"Get the items Mortifer tells you to get, and enter the Grotto in the south of Mort Myre.",
			ghostspeak, secateurs, items3);
		talkToSpirit = new NpcStep(this, NpcID.FILLIMAN_TARLOCK_NS, new WorldPoint(3441, 9738, 1),
			"Talk to the Nature Spirit.", ghostspeak, secateurs, items3);

		enterZanarisForFight = new ObjectStep(this, ObjectID.ZANARISDOOR, new WorldPoint(3202, 3169, 0),
			"Travel to Zanaris, ready to fight the Tanglefoot.",
					dramenOrLunarStaff, magicSecateurs);
		enterTanglefootRoom = new ObjectStep(this, ObjectID.FAIRY_WALLS_MULTI, new WorldPoint(2399, 4379, 0),
			"Enter the tanglefoot lair in the south of Zanaris, near the cosmic altar.", magicSecateursEquipped, food);
		killTanglefoot = new NpcStep(this, NpcID.FAIRY_TANGLEFOOT, new WorldPoint(2375, 4385, 0), "Kill the large " +
			"Tanglefoot with the Magic Secateurs. You can flinch it on a corner.", magicSecateursEquipped);
		pickUpSecateurs = new ItemStep(this, "Pick up the queen's secateurs.", queensSecateurs);

		enterZanarisForEnd = new ObjectStep(this, ObjectID.ZANARISDOOR, new WorldPoint(3202, 3169, 0),
			"Talk to the Fairy Godfather in Zanaris to finish the quest.", dramenOrLunarStaff, queensSecateurs);
		talkToGodfatherToFinish = new NpcStep(this, NpcID.FAIRY_GODFATHER2, new WorldPoint(2447, 4430, 0),
			"Talk to the Fairy Godfather to finish the quest.", queensSecateurs);
		talkToGodfatherToFinish.addSubSteps(enterZanarisForEnd);
	}

	@Override
	public List<ItemRequirement> getItemRequirements()
	{
		return Arrays.asList(secateurs, spade, ghostspeak, dramenOrLunarStaff, draynorSkull);
	}

	@Override
	public List<ItemRequirement> getItemRecommended()
	{
		return Arrays.asList(varrockTeleport, faladorTeleport, lumbridgeTeleport, moryTele, food);
	}

	@Override
	public List<Requirement> getGeneralRequirements()
	{
		List<Requirement> reqs = new ArrayList<>();
		reqs.add(new QuestRequirement(QuestHelperQuest.LOST_CITY, QuestState.FINISHED));
		reqs.add(new QuestRequirement(QuestHelperQuest.NATURE_SPIRIT, QuestState.FINISHED));
		return reqs;
	}

	@Override
	public List<String> getCombatRequirements()
	{
		return Collections.singletonList("Tanglefoot (level 111)");
	}

	@Override
	public QuestPointReward getQuestPointReward()
	{
		return new QuestPointReward(2);
	}

	@Override
	public List<ExperienceReward> getExperienceRewards()
	{
		return Arrays.asList(
				new ExperienceReward(Skill.FARMING, 3500),
				new ExperienceReward(Skill.ATTACK, 2000),
				new ExperienceReward(Skill.MAGIC, 1000));
	}

	@Override
	public List<ItemReward> getItemRewards()
	{
		return Collections.singletonList(new ItemReward("Magic Secateurs", ItemID.FAIRY_ENCHANTED_SECATEURS, 1));
	}

	@Override
	public ArrayList<PanelDetails> getPanels()
	{
		ArrayList<PanelDetails> allSteps = new ArrayList<>();

		allSteps.add(new PanelDetails("Investigating", Arrays.asList(talkToMartin, talkToFarmers, talkToMartinAgain)));
		allSteps.add(new PanelDetails("Unnatural events", Arrays.asList(enterZanaris, talkToGodfather, talkToNuff,
			talkToZandar), dramenOrLunarStaff));
		allSteps.add(new PanelDetails("Finding a cure", Arrays.asList(getSkull, talkToMortifer),
			skullOrSpade));
		allSteps.add(new PanelDetails("Enchanting secateurs", Arrays.asList(enterGrotto, talkToSpirit), ghostspeak, secateurs,
			items3));
		allSteps.add(new PanelDetails("Defeat the Tanglefoot", Arrays.asList(enterZanarisForFight,
			enterTanglefootRoom, killTanglefoot, talkToGodfatherToFinish), dramenOrLunarStaff, magicSecateurs, food));

		return allSteps;
	}
}
