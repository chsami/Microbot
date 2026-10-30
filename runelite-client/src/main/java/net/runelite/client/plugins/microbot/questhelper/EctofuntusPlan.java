package net.runelite.client.plugins.microbot.questhelper;

/**
 * The decisions of the Ectofuntus token loop, derived only from what the inventory and the player's position show.
 * Pure: no game calls. {@link EctofuntusRunner} reads the state each tick, asks {@link #next}, and performs the one
 * action it names.
 */
public final class EctofuntusPlan
{
	/** Most sets (bone + pot + bucket) carried in one trip: 3 slots each, leaving room for the tokens and quest items. */
	public static final int MAX_SETS_PER_TRIP = 8;

	private EctofuntusPlan()
	{
	}

	public enum Where
	{
		/** On the temple's top floor beside the bone grinder. */
		GRINDER,
		/** In the slime cavern beside the pool. */
		POOL,
		/** On the ground floor by the Ectofuntus and the ghost disciples. */
		ALTAR,
		ELSEWHERE
	}

	public enum Action
	{
		/** The tokens are in hand, nothing more to do. */
		DONE,
		/** Bring bones, pots or buckets from the bank or buy them. */
		FETCH_INPUTS,
		WALK_TO_GRINDER,
		GRIND,
		WALK_TO_POOL,
		FILL_BUCKETS,
		WALK_TO_ALTAR,
		WORSHIP,
		COLLECT,
		/** Inputs cannot be had and nothing is left to collect. */
		FAIL_NO_INPUTS
	}

	/** What the inventory and position show. */
	public static final class State
	{
		/** Worships still to do: sets needed for the tokens wanted, after the tokens held and the uncollected ones. */
		public int remaining;
		/** Sets the inventory can carry this trip. */
		public int maxSets = MAX_SETS_PER_TRIP;
		public int bones;
		public int emptyPots;
		public int bonemeal;
		public int emptyBuckets;
		public int slime;
		/** Worships done whose tokens the ghost disciples still hold. */
		public int pending;
		public Where where = Where.ELSEWHERE;
		/** Fetching inputs has been tried and gave up. */
		public boolean fetchFailed;
		/** A disciple has been asked for tokens at least once this run (covers worships from an earlier run). */
		public boolean collectProbed;
	}

	/** Sets (rounds) needed for {@code needed} tokens with {@code held} in hand, 5 tokens each. */
	public static int setsNeeded(int needed, int held)
	{
		return QuestUntradableRecipes.ECTO_TOKEN.roundsFor(needed, held);
	}

	/** Sets the inventory can carry: 3 slots per set, counting the slots the loop's own items already hold. */
	public static int tripCapacity(int freeSlots, int slotsHeldByInputs)
	{
		// one slot stays free for the Ecto-tokens
		return Math.max(1, Math.min(MAX_SETS_PER_TRIP, (freeSlots + slotsHeldByInputs - 1) / 3));
	}

	/** Sets that bones + pots (and buckets) on hand could still make. */
	public static int potentialBonemeal(State s)
	{
		return s.bonemeal + Math.min(s.bones, s.emptyPots);
	}

	public static int potentialSlime(State s)
	{
		return s.slime + s.emptyBuckets;
	}

	/** The sets this trip aims for. */
	public static int target(State s)
	{
		int target = Math.min(s.remaining, s.maxSets);
		if (s.fetchFailed)
		{
			target = Math.min(target, Math.min(potentialBonemeal(s), potentialSlime(s)));
		}
		return Math.max(0, target);
	}

	/**
	 * Totals of each input wanted on the player to finish the trip: {bones, pots, buckets}. Items already turned into
	 * bonemeal or slime no longer count.
	 */
	public static int[] inputsWanted(State s, int target)
	{
		int boneSets = Math.max(0, target - s.bonemeal);
		int slimeSets = Math.max(0, target - s.slime);
		return new int[] {boneSets, boneSets, slimeSets};
	}

	public static Action next(State s)
	{
		if (s.remaining <= 0)
		{
			return s.pending > 0 ? Action.COLLECT : Action.DONE;
		}
		int target = target(s);
		boolean lacking = potentialBonemeal(s) < Math.min(s.remaining, s.maxSets)
			|| potentialSlime(s) < Math.min(s.remaining, s.maxSets);
		if (lacking && !s.fetchFailed)
		{
			return Action.FETCH_INPUTS;
		}
		if (target <= 0)
		{
			// worships from an earlier run may be waiting with the disciples
			return s.pending > 0 || !s.collectProbed ? Action.COLLECT : Action.FAIL_NO_INPUTS;
		}
		if (s.bonemeal < target)
		{
			return s.where == Where.GRINDER ? Action.GRIND : Action.WALK_TO_GRINDER;
		}
		if (s.slime < target)
		{
			return s.where == Where.POOL ? Action.FILL_BUCKETS : Action.WALK_TO_POOL;
		}
		return s.where == Where.ALTAR ? Action.WORSHIP : Action.WALK_TO_ALTAR;
	}
}
