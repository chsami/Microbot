package net.runelite.client.plugins.microbot.questhelper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.microbot.questhelper.helpers.quests.enlightenedjourney.EnlightenedJourney;
import net.runelite.client.plugins.microbot.questhelper.helpers.quests.lostcity.LostCity;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.BasicQuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.steps.ConditionalStep;
import net.runelite.client.plugins.microbot.questhelper.steps.NpcStep;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class QuestScriptEntranaGearTest
{
	private static final String BOAT_OPTION = "Yes, okay, I'm ready to go.";

	@Test
	public void lostCityBoatStepBanksGearAndPicksTheBoatOption()
	{
		QuestStep step = defaultStep(new LostCity(), 2);
		assertTrue(QuestScript.isEntranaBoatStep(step));
		assertTrue(hasChoice(step, BOAT_OPTION));
	}

	@Test
	public void enlightenedJourneyBoatStepBanksGearAndPicksTheBoatOption()
	{
		QuestStep step = defaultStep(new EnlightenedJourney(), 0);
		assertTrue(QuestScript.isEntranaBoatStep(step));
		assertTrue(hasChoice(step, BOAT_OPTION));
	}

	@Test
	public void otherNpcStepsAreNotBoatSteps()
	{
		LostCity helper = loaded(new LostCity());
		assertFalse(QuestScript.isEntranaBoatStep(new NpcStep(helper, NpcID.WARRIORADVENTURERPG, new WorldPoint(3151, 3207, 0), "Talk")));
		assertFalse(QuestScript.isEntranaBoatStep(new NpcStep(helper, NpcID.SHIPMONK2, new WorldPoint(2834, 3335, 0), "Leave")));
		assertFalse(QuestScript.isEntranaBoatStep(null));
	}

	@Test
	public void boatOptionIsPickedOnEveryPortSarimMonkStep()
	{
		LostCity helper = loaded(new LostCity());
		NpcStep heroesStyle = new NpcStep(helper, NpcID.SHIPMONK1_C, new WorldPoint(3047, 3236, 0), "Travel");
		NpcStep deviousMindsStyle = new NpcStep(helper, NpcID.SHIPMONK, new WorldPoint(3045, 3236, 0), "Travel");
		NpcStep diaryStyle = new NpcStep(helper, NpcID.SHIPMONK1_C, new WorldPoint(3048, 3236, 0), "Travel");
		diaryStyle.addAlternateNpcs(NpcID.SHIPMONK1_B, NpcID.SHIPMONK);
		for (NpcStep step : new NpcStep[]{heroesStyle, deviousMindsStyle, diaryStyle})
		{
			assertTrue(QuestScript.isEntranaBoatOption(step, BOAT_OPTION));
			assertTrue(QuestScript.isEntranaBoatOption(step, "2. " + BOAT_OPTION));
			assertFalse(QuestScript.isEntranaBoatOption(step, "No, not right now."));
			assertFalse(QuestScript.isEntranaBoatOption(step, null));
		}
	}

	@Test
	public void boatOptionIsNotPickedForOtherNpcs()
	{
		LostCity helper = loaded(new LostCity());
		NpcStep other = new NpcStep(helper, NpcID.WARRIORADVENTURERPG, new WorldPoint(3151, 3207, 0), "Talk");
		assertFalse(QuestScript.isEntranaBoatOption(other, BOAT_OPTION));
		assertFalse(QuestScript.isEntranaBoatOption(null, BOAT_OPTION));
	}

	@Test
	public void stopReasonNamesTheItemsThatCouldNotBeBanked()
	{
		String reason = QuestScript.entranaGearStuckReason(List.of("Bronze axe", "Iron chainbody"));
		assertTrue(reason.startsWith("Could not bank Bronze axe, Iron chainbody for the Entrana boat (your bank may be full)."));
		assertTrue(reason.contains("rings and amulets can stay on"));
		assertTrue(QuestScript.entranaGearStuckReason(List.of()).startsWith("Could not bank your weapons and armour"));
	}

	@Test
	public void bankedMessageNamesWhatWasMoved()
	{
		String message = QuestScript.entranaGearBankedMessage(List.of("Bronze axe", "Iron full helm", "Bronze axe"));
		assertTrue(message.startsWith("Quest helper: banked Bronze axe, Iron full helm before the Entrana boat."));
		assertTrue(message.contains("Rings and amulets were kept."));
	}

	@Test
	public void jewelleryStaysOnAndOtherWornSlotsAreBanked()
	{
		assertFalse(QuestScript.isEntranaGearSlot(EquipmentInventorySlot.AMULET.getSlotIdx()));
		assertFalse(QuestScript.isEntranaGearSlot(EquipmentInventorySlot.RING.getSlotIdx()));
		assertFalse(QuestScript.isEntranaGearSlot(-1));
		for (EquipmentInventorySlot slot : new EquipmentInventorySlot[]{
				EquipmentInventorySlot.HEAD, EquipmentInventorySlot.CAPE, EquipmentInventorySlot.WEAPON,
				EquipmentInventorySlot.BODY, EquipmentInventorySlot.SHIELD, EquipmentInventorySlot.LEGS,
				EquipmentInventorySlot.GLOVES, EquipmentInventorySlot.BOOTS, EquipmentInventorySlot.AMMO})
		{
			assertTrue(slot.name(), QuestScript.isEntranaGearSlot(slot.getSlotIdx()));
		}
	}

	@Test
	public void onlyEquipableItemsHaveASlot()
	{
		assertEquals(-1, QuestScript.equipmentSlot(null));
		assertEquals(-1, QuestScript.equipmentSlot(new ItemStats(false, 0.5, 0, null)));
		ItemEquipmentStats weapon = ItemEquipmentStats.builder().slot(EquipmentInventorySlot.WEAPON.getSlotIdx()).build();
		assertEquals(EquipmentInventorySlot.WEAPON.getSlotIdx(), QuestScript.equipmentSlot(new ItemStats(true, 1.0, 0, weapon)));
	}

	@Test
	public void itemsTheBoatStepNeedsAreKept()
	{
		LostCity helper = loaded(new LostCity());
		NpcStep step = new NpcStep(helper, NpcID.SHIPMONK1_C, new WorldPoint(3047, 3236, 0), "Travel",
				new ItemRequirement("Ice gloves", ItemID.ICE_GLOVES, 1, true));
		Set<Integer> keep = QuestScript.stepItemIds(step);
		assertTrue(keep.contains(ItemID.ICE_GLOVES));
	}

	private static <T extends BasicQuestHelper> T loaded(T helper)
	{
		helper.setConfig(mock(QuestHelperConfig.class));
		helper.loadSteps();
		return helper;
	}

	private static QuestStep defaultStep(BasicQuestHelper helper, int state)
	{
		helper.setConfig(mock(QuestHelperConfig.class));
		Map<Integer, QuestStep> steps = helper.loadSteps();
		QuestStep step = steps.get(state);
		assertTrue(step instanceof ConditionalStep);
		return ((ConditionalStep) step).getStepsMap().get(null);
	}

	private static boolean hasChoice(QuestStep step, String text)
	{
		return step.getChoices().getChoices().stream().anyMatch(choice -> text.equals(choice.getChoice()));
	}
}
