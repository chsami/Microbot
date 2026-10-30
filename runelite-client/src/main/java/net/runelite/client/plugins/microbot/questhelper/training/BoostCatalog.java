package net.runelite.client.plugins.microbot.questhelper.training;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Cheap boosts the plan may use instead of training: the item to drink and how many levels it gives. Pure. */
public final class BoostCatalog
{
	/** One boost. */
	public static final class Boost
	{
		public final String item;
		public final int id;
		public final int levels;

		Boost(String item, int id, int levels)
		{
			this.item = item;
			this.id = id;
			this.levels = levels;
		}
	}

	private static final Map<String, Boost> BOOSTS = new HashMap<>();

	static
	{
		BOOSTS.put("ATTACK", new Boost("Attack potion(3)", 121, 3));
		BOOSTS.put("STRENGTH", new Boost("Strength potion(3)", 115, 3));
		BOOSTS.put("DEFENCE", new Boost("Defence potion(3)", 133, 3));
		BOOSTS.put("RANGED", new Boost("Ranging potion(3)", 169, 4));
		BOOSTS.put("MAGIC", new Boost("Magic potion(3)", 3042, 4));
		BOOSTS.put("AGILITY", new Boost("Agility potion(3)", 3034, 3));
		BOOSTS.put("FISHING", new Boost("Fishing potion(3)", 151, 3));
		BOOSTS.put("HUNTER", new Boost("Hunter potion(3)", 10000, 3));
	}

	private BoostCatalog()
	{
	}

	public static Boost of(String skill)
	{
		return skill == null ? null : BOOSTS.get(skill.toUpperCase(Locale.ROOT));
	}

	/** Levels the best known boost gives, 0 when there is none. */
	public static int maxBoost(String skill)
	{
		Boost boost = of(skill);
		return boost == null ? 0 : boost.levels;
	}
}
