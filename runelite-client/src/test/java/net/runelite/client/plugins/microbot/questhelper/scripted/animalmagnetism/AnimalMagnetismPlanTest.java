package net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism;

import static net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism.AnimalMagnetismPlan.*;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism.AnimalMagnetismPlan.Action;
import net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism.AnimalMagnetismPlan.Exit;
import net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism.AnimalMagnetismPlan.Place;
import net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism.AnimalMagnetismPlan.Snapshot;
import org.junit.Test;

public class AnimalMagnetismPlanTest
{
	/** A fully equipped player: amulet worn, every quest item in the pack. */
	private static Snapshot kit()
	{
		return Snapshot.empty().wearing(GHOSTSPEAK).holding(IRON_BAR, 5).holding(HAMMER, 1)
			.holding(MITHRIL_AXE, 1).holding(HOLY_SYMBOL, 1).holding(GAMES_NECKLACES.get(7), 1)
			.holding(DRAYNOR_MANOR_TAB, 2).holding(LOBSTER, 6).holding(HARD_LEATHER, 1).holding(POLISHED_BUTTONS, 1);
	}

	private static Action next(int varbit, Snapshot s)
	{
		return AnimalMagnetismPlan.next(varbit, s);
	}

	@Test
	public void earlyStagesTalkToTheRightPeople()
	{
		assertEquals(Action.TALK_AVA, next(0, kit()));
		assertEquals(Action.TALK_MALCOLM, next(10, kit()));
		for (int v : new int[] {20, 40, 60})
		{
			assertEquals(Action.TALK_ALICE, next(v, kit()));
		}
		for (int v : new int[] {30, 50, 76, 80, 90})
		{
			assertEquals(Action.TALK_MALCOLM, next(v, kit()));
		}
		assertEquals(Action.TALK_CRONE, next(70, kit()));
		assertEquals(Action.TALK_CRONE, next(73, kit()));
	}

	@Test
	public void ghostspeakIsWornBeforeTalkingToGhosts()
	{
		Snapshot carried = Snapshot.empty().holding(GHOSTSPEAK, 1);
		assertEquals(Action.WEAR_GHOSTSPEAK, next(10, carried));
		assertEquals(Action.RESUPPLY, next(10, Snapshot.empty()));
	}

	@Test
	public void chickensNeedTokensThenPurchaseThenAva()
	{
		assertEquals(Action.GET_TOKENS, next(100, kit()));
		assertEquals(Action.GET_TOKENS, next(100, kit().holding(ECTO_TOKEN, 19)));
		assertEquals(Action.BUY_CHICKENS, next(100, kit().holding(ECTO_TOKEN, 20)));
		assertEquals(Action.BUY_CHICKENS, next(100, kit().holding(ECTO_TOKEN, 10).holding(CHICKEN, 1)));
		assertEquals(Action.GET_TOKENS, next(100, kit().holding(ECTO_TOKEN, 9).holding(CHICKEN, 1)));
		assertEquals(Action.TALK_AVA, next(100, kit().holding(CHICKEN, 2)));
		assertEquals(Action.TALK_AVA, next(110, kit().holding(CHICKEN, 2)));
		assertEquals(Action.TALK_WITCH, next(110, kit()));
	}

	@Test
	public void onlyTheAmuletIsFetchedWhileMakingTokens()
	{
		assertEquals(Action.GET_TOKENS, next(100, Snapshot.empty().wearing(GHOSTSPEAK)));
		// the first bank visit after the chickens collects the rest
		assertEquals(Action.RESUPPLY, next(100, Snapshot.empty().wearing(GHOSTSPEAK).holding(CHICKEN, 2)));
	}

	@Test
	public void tokenCountDependsOnGhostsAhoy()
	{
		assertEquals(22, tokensToHold(false));
		assertEquals(20, tokensToHold(true));
		assertEquals(20, tokenGoal(0));
		assertEquals(10, tokenGoal(1));
		assertEquals(0, tokenGoal(2));
		assertTrue(needsTokens(0, 0));
		assertFalse(needsTokens(20, 0));
	}

	@Test
	public void witchAndMagnet()
	{
		assertEquals(Action.TALK_WITCH, next(120, kit()));
		assertEquals(Action.TALK_WITCH, next(130, kit()));
		Snapshot iron = Snapshot.empty().wearing(GHOSTSPEAK).holding(HAMMER, 1).holding(SELECTED_IRON, 1)
			.holding(MITHRIL_AXE, 1).holding(HOLY_SYMBOL, 1).holding(GAMES_NECKLACES.get(7), 1)
			.holding(DRAYNOR_MANOR_TAB, 2).holding(LOBSTER, 6).holding(HARD_LEATHER, 1).holding(POLISHED_BUTTONS, 1);
		assertEquals(Action.MAKE_MAGNET, next(140, iron));
		assertEquals(Action.TALK_AVA, next(140, iron.holding(BAR_MAGNET, 1)));
	}

	@Test
	public void firstTreeIsChoppedOnceThenLeft()
	{
		assertEquals(Action.LEAVE_MANOR, next(150, kit().at(Place.INSIDE_MANOR)));
		assertEquals(Action.GO_TO_TREES, next(150, kit()));
		assertEquals(Action.CHOP_ONCE, next(150, kit().at(Place.NEAR_TREES)));
		assertEquals(Action.LEAVE_TREES, next(160, kit().at(Place.NEAR_TREES)));
	}

	@Test
	public void turaelNeedsBurthorpeThenUnequippedItems()
	{
		assertEquals(Action.TELEPORT_BURTHORPE, next(160, kit()));
		assertEquals(Action.TALK_TUREAL, next(160, kit().at(Place.BURTHORPE)));
		assertEquals(Action.UNEQUIP_FOR_TURAEL, next(170, kit().at(Place.BURTHORPE).wearing(MITHRIL_AXE)));
		assertEquals(Action.UNEQUIP_FOR_TURAEL, next(170, kit().at(Place.BURTHORPE).wearing(HOLY_SYMBOL)));
	}

	@Test
	public void turaelOptionTextPerStage()
	{
		assertEquals(Collections.singletonList("I'm here about a quest."), turaelOptions(160));
		assertEquals(Arrays.asList("Hello, I'm here about those trees again.", "I'd love one, thanks."),
			turaelOptions(170));
		assertEquals(turaelOptions(170), dialogueOptions(170, 0));
		assertEquals(turaelOptions(160), dialogueOptions(160, 0));
	}

	@Test
	public void dialogueOptionsAreTextNeverIndex()
	{
		assertEquals(Collections.singletonList("I would be happy to make your home a better place."),
			dialogueOptions(0, 0));
		assertEquals(Collections.singletonList("I'm here about a quest."), dialogueOptions(40, 0));
		assertEquals(Collections.singletonList("I'm here about the farmers east of here."), dialogueOptions(73, 0));
		assertEquals(Collections.singletonList("Okay, you need it more than I do, I suppose."), dialogueOptions(76, 0));
		assertEquals(Arrays.asList("Could I buy those chickens now, then?", "Could I buy 1 chicken now?"),
			dialogueOptions(100, 1));
		assertTrue(dialogueOptions(100, 2).isEmpty());
		assertTrue(dialogueOptions(10, 0).isEmpty());
	}

	@Test
	public void twigStageResumesFromWherever()
	{
		assertEquals(Action.CHOP_FOR_TWIGS, next(180, kit().holding(BLESSED_AXE, 1).at(Place.NEAR_TREES)));
		assertEquals(Action.GO_TO_TREES, next(180, kit().holding(BLESSED_AXE, 1).at(Place.BURTHORPE)));
		assertEquals(Action.LEAVE_MANOR, next(180, kit().holding(BLESSED_AXE, 1).at(Place.INSIDE_MANOR)));
		assertEquals(Action.TELEPORT_BURTHORPE, next(180, kit()));
		// twigs already in the pack: straight to Ava, no food or tabs needed
		assertEquals(Action.TALK_AVA, next(180, Snapshot.empty().holding(TWIGS, 1).holding(HARD_LEATHER, 1)
			.holding(POLISHED_BUTTONS, 1)));
	}

	@Test
	public void lowHitpointsEatThenLeaveWhenFoodRunsOut()
	{
		assertEquals(Action.EAT, next(180, kit().holding(BLESSED_AXE, 1).at(Place.NEAR_TREES).hitpoints(24)));
		Snapshot noFood = Snapshot.empty().wearing(GHOSTSPEAK).holding(BLESSED_AXE, 1).holding(DRAYNOR_MANOR_TAB, 1)
			.holding(HARD_LEATHER, 1).holding(POLISHED_BUTTONS, 1).at(Place.NEAR_TREES).hitpoints(24);
		assertEquals(Action.LEAVE_TREES, next(180, noFood));
		assertEquals(Action.CHOP_FOR_TWIGS,
			next(180, kit().holding(BLESSED_AXE, 1).at(Place.NEAR_TREES).hitpoints(25)));
	}

	@Test
	public void notesPuzzleAndPattern()
	{
		assertEquals(Action.TALK_AVA, next(190, kit()));
		assertEquals(Action.TRANSLATE_NOTES, next(200, kit().holding(RESEARCH_NOTES, 1)));
		assertEquals(Action.CLICK_TRANSLATION, next(200, kit().holding(RESEARCH_NOTES, 1).translationOpen(true)));
		assertEquals(Action.TALK_AVA, next(210, kit()));
		Snapshot pattern = Snapshot.empty().holding(PATTERN, 1).holding(HARD_LEATHER, 1).holding(POLISHED_BUTTONS, 1);
		assertEquals(Action.COMBINE_LEATHER, next(220, pattern));
		assertEquals(Action.COMBINE_BUTTONS,
			next(220, Snapshot.empty().holding(PATTERN, 1).holding(POLISHED_BUTTONS, 1)));
		assertEquals(Action.TALK_AVA, next(220, Snapshot.empty().holding(CONTAINER, 1)));
		assertEquals(Action.TALK_AVA, next(230, Snapshot.empty().holding(CONTAINER, 1)));
		assertEquals(Action.FINISH, next(240, Snapshot.empty()));
	}

	@Test
	public void translationClicksComeInTheRecordedOrderThenStop()
	{
		int[] seen = new int[8];
		int n = 0;
		for (int done = 0; nextTranslationClick(done) >= 0; done++)
		{
			seen[n++] = nextTranslationClick(done);
		}
		assertArrayEquals(new int[] {26, 31, 34, 40, 43, 46}, Arrays.copyOf(seen, n));
		assertEquals(-1, nextTranslationClick(6));
		assertEquals(-1, nextTranslationClick(-1));
	}

	@Test
	public void eachDocumentedStageHasItsAction()
	{
		Snapshot ava = kit().holding(CHICKEN, 2);
		int[] stages = {0, 10, 20, 30, 40, 50, 60, 70, 73, 76, 80, 90, 100, 110, 120, 130, 190, 210, 230};
		Action[] expected = {Action.TALK_AVA, Action.TALK_MALCOLM, Action.TALK_ALICE, Action.TALK_MALCOLM,
			Action.TALK_ALICE, Action.TALK_MALCOLM, Action.TALK_ALICE, Action.TALK_CRONE, Action.TALK_CRONE,
			Action.TALK_MALCOLM, Action.TALK_MALCOLM, Action.TALK_MALCOLM, Action.TALK_AVA, Action.TALK_AVA,
			Action.TALK_WITCH, Action.TALK_WITCH, Action.TALK_AVA, Action.TALK_AVA, Action.TALK_AVA};
		for (int i = 0; i < stages.length; i++)
		{
			assertEquals("stage " + stages[i], expected[i], next(stages[i], ava));
		}
	}

	@Test
	public void unlistedStagesFollowTheStageBelow()
	{
		assertEquals(next(70, kit()), next(72, kit()));
		assertEquals(next(150, kit().at(Place.NEAR_TREES)), next(155, kit().at(Place.NEAR_TREES)));
		assertEquals(Action.FINISH, next(300, Snapshot.empty()));
	}

	@Test
	public void theFullestNecklaceIsTheOneBought()
	{
		java.util.List<net.runelite.client.plugins.microbot.questhelper.QuestBuyRules.Candidate> candidates =
			new java.util.ArrayList<>();
		for (int i = 0; i < GAMES_NECKLACES.size(); i++)
		{
			candidates.add(new net.runelite.client.plugins.microbot.questhelper.QuestBuyRules.Candidate(
				GAMES_NECKLACES.get(i), "Games necklace(" + (i + 1) + ")"));
		}
		int bought = net.runelite.client.plugins.microbot.questhelper.QuestBuyRules.pickBuyId(candidates, id -> 100);
		assertEquals(GAMES_NECKLACES.get(7).intValue(), bought);
		AnimalMagnetismPlan.Carry necklace = shoppingList(0, Snapshot.empty()).stream()
			.filter(c -> c.name.equals("Games necklace")).findFirst().get();
		assertEquals(new java.util.HashSet<>(GAMES_NECKLACES), new java.util.HashSet<>(necklace.toNeed().ids));
	}

	@Test
	public void aBrokenTabNeverSendsTheBotBackToTheBankAtStage180()
	{
		Snapshot burthorpe = kit().holding(BLESSED_AXE, 1).at(Place.BURTHORPE);
		assertEquals(Action.GO_TO_TREES, next(180, burthorpe));
		// one of the two tablets broken, now at the trees
		Snapshot atTrees = Snapshot.empty().wearing(GHOSTSPEAK).holding(BLESSED_AXE, 1).holding(DRAYNOR_MANOR_TAB, 1)
			.holding(LOBSTER, 6).holding(HARD_LEATHER, 1).holding(POLISHED_BUTTONS, 1).at(Place.NEAR_TREES);
		assertEquals(Action.CHOP_FOR_TWIGS, next(180, atTrees));
		// the last tablet gone as well: still chops, no resupply loop
		Snapshot noTabs = Snapshot.empty().wearing(GHOSTSPEAK).holding(BLESSED_AXE, 1).holding(LOBSTER, 6)
			.holding(HARD_LEATHER, 1).holding(POLISHED_BUTTONS, 1).at(Place.NEAR_TREES);
		assertEquals(Action.CHOP_FOR_TWIGS, next(180, noTabs));
		assertTrue(missing(180, noTabs).isEmpty());
	}

	@Test
	public void tabsAreOnlyAMustWhereTheBotCannotWalkOn()
	{
		Snapshot none = Snapshot.empty().wearing(GHOSTSPEAK).holding(BLESSED_AXE, 1).holding(LOBSTER, 6)
			.holding(HARD_LEATHER, 1).holding(POLISHED_BUTTONS, 1);
		assertTrue(missing(180, none.at(Place.ELSEWHERE)).isEmpty());
		assertFalse(missing(180, none.at(Place.INSIDE_MANOR)).isEmpty());
		assertFalse(missing(180, none.at(Place.BURTHORPE)).isEmpty());
	}

	@Test
	public void runningOutOfFoodAtTheTreesLeadsToOneCleanExit()
	{
		Snapshot hurt = Snapshot.empty().wearing(GHOSTSPEAK).holding(BLESSED_AXE, 1).holding(DRAYNOR_MANOR_TAB, 1)
			.holding(LOBSTER, 1).holding(HARD_LEATHER, 1).holding(POLISHED_BUTTONS, 1)
			.at(Place.NEAR_TREES).hitpoints(20);
		assertEquals(Action.EAT, next(180, hurt));
		// the last lobster eaten: leave the trees, then restock from the tile it was left at (a tablet exits)
		Snapshot out = Snapshot.empty().wearing(GHOSTSPEAK).holding(BLESSED_AXE, 1).holding(DRAYNOR_MANOR_TAB, 1)
			.holding(HARD_LEATHER, 1).holding(POLISHED_BUTTONS, 1).hitpoints(20);
		assertEquals(Action.LEAVE_TREES, next(180, out.at(Place.NEAR_TREES)));
		assertEquals(Action.RESUPPLY, next(180, out.at(Place.INSIDE_MANOR)));
		assertEquals(Exit.TAB, exitRoute(out));
	}

	@Test
	public void foodIsEatenAtTheFirstTreeToo()
	{
		assertEquals(Action.EAT, next(150, kit().at(Place.NEAR_TREES).hitpoints(10)));
		Snapshot noFood = Snapshot.empty().wearing(GHOSTSPEAK).holding(MITHRIL_AXE, 1).holding(DRAYNOR_MANOR_TAB, 1)
			.holding(HAMMER, 1).holding(HOLY_SYMBOL, 1).holding(GAMES_NECKLACES.get(7), 1)
			.holding(HARD_LEATHER, 1).holding(POLISHED_BUTTONS, 1).holding(LOBSTER, 0);
		assertEquals(Action.LEAVE_TREES, next(150, noFood.at(Place.NEAR_TREES).hitpoints(10)));
	}

	@Test
	public void theWayOutOfTheManorIsATabThenTheNecklaceNeverTheDoors()
	{
		assertEquals(Exit.TAB, exitRoute(Snapshot.empty().holding(DRAYNOR_MANOR_TAB, 1)
			.holding(GAMES_NECKLACES.get(7), 1)));
		assertEquals(Exit.NECKLACE, exitRoute(Snapshot.empty().holding(GAMES_NECKLACES.get(2), 1)));
		assertEquals(Exit.NONE, exitRoute(Snapshot.empty()));
	}


	@Test
	public void shoppingListShrinksAsTheQuestAdvances()
	{
		assertTrue(shoppingList(0, Snapshot.empty()).stream().anyMatch(c -> c.ids.contains(IRON_BAR)));
		assertFalse(shoppingList(140, Snapshot.empty()).stream().anyMatch(c -> c.ids.contains(IRON_BAR)));
		assertFalse(shoppingList(220, Snapshot.empty()).stream().anyMatch(c -> c.ids.contains(HARD_LEATHER)));
		assertTrue(shoppingList(240, Snapshot.empty()).isEmpty());
	}
}
