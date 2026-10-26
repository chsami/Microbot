package net.runelite.client.plugins.microbot.questhelper.planner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a quest forbids carrying or wearing: read from Quest Helper's requirement and note texts, plus a small curated
 * table for known cases. Pure. A restriction is either enforced (the items are banked before the step that needs it)
 * or a warning only.
 */
public final class QuestRestrictions
{
	/** What is restricted. */
	public enum Kind
	{
		EMPTY_INVENTORY, NO_WEAPONS_OR_ARMOUR, NO_FOOD, FREE_SLOTS, ITEM
	}

	/** One restriction. {@code slots} is the slot count (FREE_SLOTS), {@code item} the item name (ITEM). */
	public static final class Restriction
	{
		public final Kind kind;
		public final String text;
		public final String item;
		public final int slots;
		/** True when the planner banks the items itself; false for a warning only. */
		public final boolean enforce;

		public Restriction(Kind kind, String text, String item, int slots, boolean enforce)
		{
			this.kind = kind;
			this.text = text;
			this.item = item == null ? "" : item;
			this.slots = slots;
			this.enforce = enforce;
		}
	}

	private static final String NEGATION = "(?:cannot|can not|can\\W?t|must not|do not|don\\W?t|never)";

	private static final Pattern EMPTY = Pattern.compile(
		"empty (?:inventory|backpack)|inventory (?:must be|to be|should be) empty|no items in (?:your )?inventory|"
			+ NEGATION + " (?:carry|bring|take) (?:any )?(?:items|anything)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern NO_GEAR = Pattern.compile(
		"no (?:weapons?|armou?r)|(?:weapons?|armou?r) (?:are|is) (?:not allowed|banned|forbidden)|"
			+ NEGATION + " (?:wield|wear|equip|bring)[^.;]*(?:weapons?|armou?r)|"
			+ "remove (?:all )?(?:your )?(?:weapons?|armou?r|equipment)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern NO_FOOD = Pattern.compile(
		"no food|food (?:is|are) (?:not allowed|banned|forbidden)|" + NEGATION + " (?:bring|carry|eat|take)[^.;]*food",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern SLOTS = Pattern.compile(
		"(\\d+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+(?:or more\\s+)?(?:free|empty|spare)\\s+"
			+ "(?:inventory\\s+)?(?:slots?|spaces?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern ITEM = Pattern.compile(
		NEGATION + " (?:bring|carry|take|have|use) (?:a |an |any |the )?([A-Za-z ]{3,40}?)"
			+ "(?:[.,;)]|$| in | into | when | while | to )", Pattern.CASE_INSENSITIVE);

	/** Quests with a known carrying problem that the texts do not state. Warnings only until verified in game. */
	private static final Map<String, List<Restriction>> CURATED = new LinkedHashMap<>();

	static
	{
		CURATED.put("MONKEY_MADNESS_I", list(new Restriction(Kind.NO_WEAPONS_OR_ARMOUR,
			"On Ape Atoll the monkey greegree replaces your weapon and shield; remove them first.", "", 0, false)));
		CURATED.put("FIGHT_ARENA", list(new Restriction(Kind.NO_WEAPONS_OR_ARMOUR,
			"The guards may take your weapons and armour before the cell.", "", 0, false)));
	}

	private QuestRestrictions()
	{
	}

	private static List<Restriction> list(Restriction... items)
	{
		List<Restriction> out = new ArrayList<>();
		java.util.Collections.addAll(out, items);
		return out;
	}

	/** Restrictions stated by the given Quest Helper texts, one per text and kind. */
	public static List<Restriction> fromTexts(List<String> texts)
	{
		List<Restriction> out = new ArrayList<>();
		if (texts == null)
		{
			return out;
		}
		for (String raw : texts)
		{
			if (raw == null || raw.trim().isEmpty())
			{
				continue;
			}
			String text = raw.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
			if (EMPTY.matcher(text).find())
			{
				add(out, new Restriction(Kind.EMPTY_INVENTORY, text, "", 0, true));
			}
			if (NO_GEAR.matcher(text).find())
			{
				add(out, new Restriction(Kind.NO_WEAPONS_OR_ARMOUR, text, "", 0, true));
			}
			if (NO_FOOD.matcher(text).find())
			{
				add(out, new Restriction(Kind.NO_FOOD, text, "", 0, true));
			}
			Matcher slots = SLOTS.matcher(text);
			if (slots.find())
			{
				add(out, new Restriction(Kind.FREE_SLOTS, text, "", number(slots.group(1)), true));
			}
			Matcher item = ITEM.matcher(text);
			while (item.find())
			{
				String name = item.group(1).trim();
				String lower = name.toLowerCase(Locale.ROOT);
				if (!name.isEmpty() && !lower.equals("items") && !lower.equals("anything") && !lower.contains("food")
					&& !lower.contains("weapon") && !lower.contains("armour") && !lower.contains("armor"))
				{
					add(out, new Restriction(Kind.ITEM, text, name, 0, true));
				}
			}
		}
		return out;
	}

	/** Text restrictions plus the curated ones for {@code questEnumName}. */
	public static List<Restriction> forQuest(String questEnumName, List<String> texts)
	{
		List<Restriction> out = fromTexts(texts);
		List<Restriction> curated = CURATED.get(questEnumName);
		if (curated != null)
		{
			for (Restriction restriction : curated)
			{
				boolean have = false;
				for (Restriction existing : out)
				{
					have |= existing.kind == restriction.kind;
				}
				if (!have)
				{
					out.add(restriction);
				}
			}
		}
		return out;
	}

	/** True when any enforced restriction asks for an empty inventory (or no carried items at all). */
	public static boolean forbidsCarrying(List<Restriction> restrictions)
	{
		for (Restriction restriction : restrictions)
		{
			if (restriction.enforce && restriction.kind == Kind.EMPTY_INVENTORY)
			{
				return true;
			}
		}
		return false;
	}

	/** The largest number of free slots any restriction asks for, 0 when none does. */
	public static int requiredFreeSlots(List<Restriction> restrictions)
	{
		int slots = 0;
		for (Restriction restriction : restrictions)
		{
			if (restriction.kind == Kind.FREE_SLOTS)
			{
				slots = Math.max(slots, restriction.slots);
			}
		}
		return slots;
	}

	/** True when stamina potions must stay banked: nothing may be carried, or the free slots needed leave no room. */
	public static boolean restrictsStamina(List<Restriction> restrictions, int freeSlotsWithoutStamina)
	{
		return forbidsCarrying(restrictions) || (requiredFreeSlots(restrictions) > 0
			&& freeSlotsWithoutStamina <= requiredFreeSlots(restrictions));
	}

	private static void add(List<Restriction> out, Restriction restriction)
	{
		for (Restriction existing : out)
		{
			if (existing.kind == restriction.kind && existing.item.equalsIgnoreCase(restriction.item)
				&& existing.slots == restriction.slots)
			{
				return;
			}
		}
		out.add(restriction);
	}

	private static int number(String word)
	{
		String[] names = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
		for (int i = 0; i < names.length; i++)
		{
			if (names[i].equalsIgnoreCase(word))
			{
				return i;
			}
		}
		try
		{
			return Integer.parseInt(word);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
