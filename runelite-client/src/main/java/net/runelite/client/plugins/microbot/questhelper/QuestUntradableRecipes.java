package net.runelite.client.plugins.microbot.questhelper;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;

/**
 * Untradeable items the bot can earn for itself, described as data: what is produced, how many per round and which
 * tradeable inputs a round uses up. The quest script finds the recipe for a missing untradeable requirement here; the
 * preflight uses the same data to stock the inputs (bank first, then the Grand Exchange) before the step arrives.
 * Pure: the live work of a recipe lives in its runner (see {@link EctofuntusRunner}).
 */
public final class QuestUntradableRecipes
{
	private QuestUntradableRecipes()
	{
	}

	/** One tradeable input of a recipe: {@code perRound} of {@code id} are used up each round. */
	public static final class Input
	{
		public final String name;
		public final List<Integer> ids;
		public final int perRound;

		Input(String name, int id, int perRound)
		{
			this.name = name;
			this.ids = Collections.singletonList(id);
			this.perRound = perRound;
		}
	}

	/** How one untradeable item is earned. */
	public static final class Recipe
	{
		public final String name;
		public final int itemId;
		public final int yieldPerRound;
		public final List<Input> inputs;

		Recipe(String name, int itemId, int yieldPerRound, List<Input> inputs)
		{
			this.name = name;
			this.itemId = itemId;
			this.yieldPerRound = yieldPerRound;
			this.inputs = inputs;
		}

		/** Rounds still to run when {@code needed} of the item are wanted and {@code owned} are already held. */
		public int roundsFor(int needed, int owned)
		{
			int missing = needed - Math.max(0, owned);
			return missing <= 0 ? 0 : (missing + yieldPerRound - 1) / yieldPerRound;
		}
	}

	/** Ecto-tokens: one worship of the Ectofuntus (bonemeal + bucket of slime) pays 5 of them. */
	public static final Recipe ECTO_TOKEN = new Recipe("Ecto-token", ItemID.ECTOTOKEN, 5, Arrays.asList(
		new Input("Bones", ItemID.BONES, 1),
		new Input("Pot", ItemID.POT_EMPTY, 1),
		new Input("Bucket", ItemID.BUCKET_EMPTY, 1)));

	private static final List<Recipe> ALL = Collections.singletonList(ECTO_TOKEN);

	/**
	 * Whether the generic missing-item handler must leave these ids alone: they are an input of a recipe that is running
	 * for the current step and the step itself does not ask for them.
	 */
	public static boolean ownedByRecipe(Collection<Integer> itemIds, boolean recipeActive, boolean stepOwn)
	{
		if (!recipeActive || stepOwn || itemIds == null)
		{
			return false;
		}
		for (Recipe recipe : ALL)
		{
			for (Input input : recipe.inputs)
			{
				if (itemIds.contains(input.ids.get(0)))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** The recipe that produces any of these item ids, or null when nothing can be earned for them. */
	public static Recipe find(Collection<Integer> itemIds)
	{
		if (itemIds == null)
		{
			return null;
		}
		for (Recipe recipe : ALL)
		{
			if (itemIds.contains(recipe.itemId))
			{
				return recipe;
			}
		}
		return null;
	}
}
