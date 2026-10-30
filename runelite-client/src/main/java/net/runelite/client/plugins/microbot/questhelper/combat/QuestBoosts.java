package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Skill;

/** Combat boost potions of the quest setup: which potions a choice names, the skills they raise, when to drink. */
public final class QuestBoosts
{
	/** A boost still counts as active while the boosted level exceeds the real level by more than this. */
	public static final int ACTIVE_BOOST_MARGIN = 2;

	/** One brewable boost potion: base name, the skills it raises and its item ids, dose 4 first. */
	public enum Potion
	{
		SUPER_ATTACK("Super attack", new int[]{2436, 145, 147, 149}, Skill.ATTACK),
		SUPER_STRENGTH("Super strength", new int[]{2440, 157, 159, 161}, Skill.STRENGTH),
		SUPER_DEFENCE("Super defence", new int[]{2442, 163, 165, 167}, Skill.DEFENCE),
		COMBAT("Combat potion", new int[]{9739, 9741, 9743, 9745}, Skill.ATTACK, Skill.STRENGTH),
		ATTACK("Attack potion", new int[]{2428, 121, 123, 125}, Skill.ATTACK),
		STRENGTH("Strength potion", new int[]{113, 115, 117, 119}, Skill.STRENGTH),
		DEFENCE("Defence potion", new int[]{2432, 133, 135, 137}, Skill.DEFENCE),
		SUPER_COMBAT("Super combat potion", new int[]{12695, 12697, 12699, 12701}, Skill.ATTACK, Skill.STRENGTH,
			Skill.DEFENCE),
		RANGING("Ranging potion", new int[]{2444, 169, 171, 173}, Skill.RANGED),
		MAGIC("Magic potion", new int[]{3040, 3042, 3044, 3046}, Skill.MAGIC);

		private final String itemName;
		private final int[] ids;
		private final Set<Skill> skills;

		Potion(String itemName, int[] ids, Skill first, Skill... more)
		{
			this.itemName = itemName;
			this.ids = ids;
			this.skills = EnumSet.of(first, more);
		}

		/** Base item name without the dose. */
		public String itemName()
		{
			return itemName;
		}

		/** Item ids of the four doses, the 4-dose first (the one a Grand Exchange buy uses). */
		public List<Integer> doseIds()
		{
			List<Integer> out = new ArrayList<>();
			for (int id : ids)
			{
				out.add(id);
			}
			return out;
		}

		/** Skills this potion raises. */
		public Set<Skill> skills()
		{
			return Collections.unmodifiableSet(skills);
		}
	}

	private QuestBoosts()
	{
	}

	/** The potions a boost choice brings; {@code defence} adds Super defence to the super and combat choices. */
	public static List<Potion> potionsFor(QuestCombatLoadout.BoostType type, boolean defence)
	{
		List<Potion> out = new ArrayList<>();
		if (type == null)
		{
			return out;
		}
		switch (type)
		{
			case SUPER_ATTACK_STRENGTH:
				out.add(Potion.SUPER_ATTACK);
				out.add(Potion.SUPER_STRENGTH);
				break;
			case COMBAT:
				out.add(Potion.COMBAT);
				break;
			case ATTACK_STRENGTH:
				out.add(Potion.ATTACK);
				out.add(Potion.STRENGTH);
				break;
			default:
				return out;
		}
		if (defence)
		{
			out.add(Potion.SUPER_DEFENCE);
		}
		return out;
	}

	/** The boost potion whose base name (dose stripped) is {@code itemName}, or null. */
	public static Potion byItemName(String itemName)
	{
		if (itemName == null)
		{
			return null;
		}
		String base = itemName.replaceAll("\\s*\\(\\d+\\)$", "").trim().toLowerCase(Locale.ROOT);
		for (Potion potion : Potion.values())
		{
			if (potion.itemName.toLowerCase(Locale.ROOT).equals(base))
			{
				return potion;
			}
		}
		return null;
	}

	/** True when a boost is absent or has run down: the boosted level is at most {@code ACTIVE_BOOST_MARGIN} above real. */
	public static boolean needsDrink(int boostedLevel, int realLevel)
	{
		return boostedLevel <= realLevel + ACTIVE_BOOST_MARGIN;
	}

	/** Skills a held boost potion raises by item name (any dose), divine variants included; empty if not a boost. */
	public static Set<Skill> skillsOfHeld(String itemName)
	{
		Potion potion = byItemName(itemName);
		if (potion != null)
		{
			return potion.skills();
		}
		if (itemName == null)
		{
			return Collections.emptySet();
		}
		String base = itemName.replaceAll("\\s*\\(\\d+\\)$", "").trim().toLowerCase(Locale.ROOT);
		if (!base.startsWith("divine "))
		{
			return Collections.emptySet();
		}
		switch (base.substring("divine ".length()))
		{
			case "super attack potion":
				return EnumSet.of(Skill.ATTACK);
			case "super strength potion":
				return EnumSet.of(Skill.STRENGTH);
			case "super defence potion":
				return EnumSet.of(Skill.DEFENCE);
			case "super combat potion":
				return EnumSet.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE);
			case "ranging potion":
				return EnumSet.of(Skill.RANGED);
			case "magic potion":
				return EnumSet.of(Skill.MAGIC);
			default:
				return Collections.emptySet();
		}
	}

	/** Drink a held boost potion regardless of any list: a skill it raises is unboosted, not a quest item, rate ok. */
	public static boolean shouldDrinkHeld(Set<Skill> skills, int[] boosted, int[] real, boolean protectedItem,
		long sinceLastDrinkMs, long minGapMs)
	{
		if (skills == null || skills.isEmpty() || protectedItem || sinceLastDrinkMs < minGapMs)
		{
			return false;
		}
		for (Skill skill : skills)
		{
			if (needsDrink(boosted[skill.ordinal()], real[skill.ordinal()]))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether to drink {@code potion} now: any skill it raises is unboosted, the potion is held, it is not a protected
	 * quest item and the rate limit has passed.
	 *
	 * @param boosted boosted level per {@link Skill#ordinal()}
	 * @param real real level per {@link Skill#ordinal()}
	 */
	public static boolean shouldDrink(Potion potion, int[] boosted, int[] real, boolean held, boolean protectedItem,
		long sinceLastDrinkMs, long minGapMs)
	{
		if (potion == null || !held || protectedItem || sinceLastDrinkMs < minGapMs)
		{
			return false;
		}
		for (Skill skill : potion.skills)
		{
			if (needsDrink(boosted[skill.ordinal()], real[skill.ordinal()]))
			{
				return true;
			}
		}
		return false;
	}
}
