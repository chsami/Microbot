package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import net.runelite.client.plugins.microbot.questhelper.EctofuntusPlan.Action;
import net.runelite.client.plugins.microbot.questhelper.EctofuntusPlan.State;
import net.runelite.client.plugins.microbot.questhelper.EctofuntusPlan.Where;
import org.junit.Test;

public class EctofuntusPlanTest
{
	private static State state(int remaining, int bones, int pots, int bonemeal, int buckets, int slime, Where where)
	{
		State s = new State();
		s.remaining = remaining;
		s.bones = bones;
		s.emptyPots = pots;
		s.bonemeal = bonemeal;
		s.emptyBuckets = buckets;
		s.slime = slime;
		s.where = where;
		return s;
	}

	@Test
	public void twentyTokensTakeFourSetsAndOwnedTokensShrinkThat()
	{
		assertEquals(4, EctofuntusPlan.setsNeeded(20, 0));
		assertEquals(3, EctofuntusPlan.setsNeeded(20, 5));
		assertEquals(4, EctofuntusPlan.setsNeeded(20, 4));
		assertEquals(0, EctofuntusPlan.setsNeeded(20, 20));
		assertEquals(0, EctofuntusPlan.setsNeeded(20, 99));
		assertEquals(1, EctofuntusPlan.setsNeeded(1, 0));
	}

	@Test
	public void recipeDataMatchesTheTokenRule()
	{
		assertEquals(QuestUntradableRecipes.ECTO_TOKEN, QuestUntradableRecipes.find(Arrays.asList(4278)));
		assertNull(QuestUntradableRecipes.find(Arrays.asList(995)));
		assertEquals(5, QuestUntradableRecipes.ECTO_TOKEN.yieldPerRound);
		assertEquals(3, QuestUntradableRecipes.ECTO_TOKEN.inputs.size());
	}

	@Test
	public void tripCapacityIsThreeSlotsPerSetAndNeverBelowOne()
	{
		assertEquals(8, EctofuntusPlan.tripCapacity(28, 0));
		assertEquals(3, EctofuntusPlan.tripCapacity(12, 0)); // one slot stays free for the tokens
		assertEquals(4, EctofuntusPlan.tripCapacity(13, 0));
		assertEquals(3, EctofuntusPlan.tripCapacity(0, 12));
		assertEquals(1, EctofuntusPlan.tripCapacity(0, 0));
	}

	@Test
	public void nothingLeftIsDoneOrCollects()
	{
		assertEquals(Action.DONE, EctofuntusPlan.next(state(0, 0, 0, 0, 0, 0, Where.ELSEWHERE)));
		State s = state(0, 0, 0, 0, 0, 0, Where.ELSEWHERE);
		s.pending = 2;
		assertEquals(Action.COLLECT, EctofuntusPlan.next(s));
	}

	@Test
	public void missingInputsAreFetchedBeforeAnyWalking()
	{
		assertEquals(Action.FETCH_INPUTS, EctofuntusPlan.next(state(4, 0, 0, 0, 0, 0, Where.ELSEWHERE)));
		assertEquals(Action.FETCH_INPUTS, EctofuntusPlan.next(state(4, 4, 4, 0, 3, 0, Where.ELSEWHERE)));
		assertEquals(Action.FETCH_INPUTS, EctofuntusPlan.next(state(4, 4, 3, 0, 4, 0, Where.ELSEWHERE)));
	}

	@Test
	public void theLoopGrindsThenFillsThenWorships()
	{
		assertEquals(Action.WALK_TO_GRINDER, EctofuntusPlan.next(state(4, 4, 4, 0, 4, 0, Where.ELSEWHERE)));
		assertEquals(Action.GRIND, EctofuntusPlan.next(state(4, 4, 4, 0, 4, 0, Where.GRINDER)));
		assertEquals(Action.GRIND, EctofuntusPlan.next(state(4, 1, 1, 3, 4, 0, Where.GRINDER)));
		assertEquals(Action.WALK_TO_POOL, EctofuntusPlan.next(state(4, 0, 0, 4, 4, 0, Where.GRINDER)));
		assertEquals(Action.FILL_BUCKETS, EctofuntusPlan.next(state(4, 0, 0, 4, 4, 0, Where.POOL)));
		assertEquals(Action.WALK_TO_ALTAR, EctofuntusPlan.next(state(4, 0, 0, 4, 0, 4, Where.POOL)));
		assertEquals(Action.WORSHIP, EctofuntusPlan.next(state(4, 0, 0, 4, 0, 4, Where.ALTAR)));
	}

	@Test
	public void collectsWhenTheLastWorshipIsDone()
	{
		State s = state(0, 0, 0, 0, 4, 0, Where.ALTAR);
		s.pending = 4;
		assertEquals(Action.COLLECT, EctofuntusPlan.next(s));
	}

	@Test
	public void aTripIsCappedByTheInventoryAndTheRestFollows()
	{
		State s = state(12, 0, 0, 0, 0, 0, Where.ELSEWHERE);
		s.maxSets = 4;
		assertEquals(Action.FETCH_INPUTS, EctofuntusPlan.next(s));
		assertArrayEquals(new int[] {4, 4, 4}, EctofuntusPlan.inputsWanted(s, 4));
		s = state(12, 4, 4, 0, 4, 0, Where.GRINDER);
		s.maxSets = 4;
		assertEquals(Action.GRIND, EctofuntusPlan.next(s));
	}

	@Test
	public void inputsWantedIgnoreWhatIsAlreadyGroundOrFilled()
	{
		State s = state(4, 2, 2, 2, 2, 2, Where.ELSEWHERE);
		assertArrayEquals(new int[] {2, 2, 2}, EctofuntusPlan.inputsWanted(s, 4));
		assertArrayEquals(new int[] {0, 0, 0}, EctofuntusPlan.inputsWanted(s, 2));
	}

	@Test
	public void afterAFailedFetchTheLoopRunsWithWhatIsCarried()
	{
		State s = state(4, 2, 2, 0, 2, 0, Where.GRINDER);
		s.fetchFailed = true;
		assertEquals(Action.GRIND, EctofuntusPlan.next(s));
		assertEquals(2, EctofuntusPlan.target(s));
	}

	@Test
	public void afterAFailedFetchWithNothingItProbesTheDisciplesOnceThenFails()
	{
		State s = state(4, 0, 0, 0, 0, 0, Where.ALTAR);
		s.fetchFailed = true;
		assertEquals(Action.COLLECT, EctofuntusPlan.next(s));
		s.collectProbed = true;
		assertEquals(Action.FAIL_NO_INPUTS, EctofuntusPlan.next(s));
		s.pending = 1;
		assertEquals(Action.COLLECT, EctofuntusPlan.next(s));
	}
}
