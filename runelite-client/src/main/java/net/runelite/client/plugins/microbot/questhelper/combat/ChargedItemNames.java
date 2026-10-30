package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Charged items (glories, rings of wealth, games necklaces) and dosed potions are separate item ids per charge or
 * dose, named "Ring of wealth (4)" or "Prayer potion(3)". A saved setup that recorded one charge must still match
 * any other, so names are compared with the trailing charge/dose stripped.
 */
public final class ChargedItemNames
{
	private static final Pattern SUFFIX = Pattern.compile("\\s*\\((?:t)?(\\d+)\\)\\s*$", Pattern.CASE_INSENSITIVE);

	private ChargedItemNames()
	{
	}

	/** Lower-case name without a trailing " (n)" / "(n)" charge or dose; empty for null. */
	public static String baseName(String name)
	{
		if (name == null)
		{
			return "";
		}
		return SUFFIX.matcher(name.trim()).replaceFirst("").trim().toLowerCase(Locale.ROOT);
	}

	/** The charge or dose in the name; 0 when it has no suffix (the uncharged base item). */
	public static int charge(String name)
	{
		if (name == null)
		{
			return 0;
		}
		Matcher m = SUFFIX.matcher(name.trim());
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}

	/** True when both names are the same item apart from the dose or charge suffix. */
	public static boolean sameFamily(String a, String b)
	{
		String base = baseName(a);
		return !base.isEmpty() && base.equals(baseName(b));
	}

	/**
	 * Every candidate of the wanted item's family (any dose or charge), highest charge first; the exact id comes
	 * first when present. Empty when nothing matches.
	 */
	public static <T> List<T> ordered(Collection<T> candidates, ToIntFunction<T> id, Function<T, String> name,
		int wantedId, String wantedName)
	{
		List<T> out = new ArrayList<>();
		for (T candidate : candidates)
		{
			if (candidate == null)
			{
				continue;
			}
			boolean exact = wantedId > 0 && id.applyAsInt(candidate) == wantedId;
			if (exact || (wantedName != null && !wantedName.isEmpty() && sameFamily(name.apply(candidate), wantedName)))
			{
				out.add(candidate);
			}
		}
		out.sort(Comparator.<T>comparingInt(c -> wantedId > 0 && id.applyAsInt(c) == wantedId ? 0 : 1)
			.thenComparing(Comparator.<T>comparingInt(c -> charge(name.apply(c))).reversed()));
		return out;
	}

	/**
	 * Picks the best candidate for a wanted item: the exact id, else the highest charge of the same family, else
	 * the uncharged base. Returns null when nothing matches.
	 */
	public static <T> T pick(Collection<T> candidates, ToIntFunction<T> id, Function<T, String> name,
		int wantedId, String wantedName)
	{
		T best = null;
		int bestCharge = -1;
		for (T candidate : candidates)
		{
			if (candidate == null)
			{
				continue;
			}
			if (wantedId > 0 && id.applyAsInt(candidate) == wantedId)
			{
				return candidate;
			}
			String candidateName = name.apply(candidate);
			if (wantedName != null && !wantedName.isEmpty() && sameFamily(candidateName, wantedName))
			{
				int charge = charge(candidateName);
				if (charge > bestCharge)
				{
					best = candidate;
					bestCharge = charge;
				}
			}
		}
		return best;
	}
}
