package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.HeadIcon;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.FightSetup;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.SecondaryStyle;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.Style;

/**
 * Pure helpers for fights where the target uses protection prayers and the player has to swap combat styles
 * (Twilight's Promise colosseum knight): detecting such a fight, choosing the second style and its default kit,
 * reading which styles the target protects against, and deciding when to swap.
 */
public final class QuestStyleSwap
{
	/** Default bought kit for a ranged second style. */
	public static final String DEFAULT_RANGED_WEAPON = "Shortbow";
	public static final String DEFAULT_RANGED_AMMO = "Bronze arrow";
	public static final int DEFAULT_RANGED_AMMO_QTY = 100;
	/** Default bought kit for a magic second style (Wind Strike: air + mind rune). */
	public static final String DEFAULT_MAGIC_WEAPON = "Staff of air";
	public static final String DEFAULT_MAGIC_AMMO = "Mind rune";
	public static final int DEFAULT_MAGIC_AMMO_QTY = 200;

	/** Two game ticks between swaps. */
	public static final long MIN_SWAP_INTERVAL_MS = 1_200L;
	/** With no overhead info: swap after this long without the target losing health (about five of our attacks). */
	public static final long FALLBACK_STALL_MS = 12_000L;

	private static final Pattern SWAP_TEXT = Pattern.compile(
		"swap(?:ping)?\\s+(?:your\\s+)?(?:combat\\s+)?(?:styles?|weapons?)|switch(?:ing)?\\s+(?:your\\s+)?combat\\s+styles?"
			+ "|(?:two|2|multiple|different|both)\\s+combat\\s+styles?"
			+ "|bypass\\s+(?:his|her|its|their|the)\\s+(?:protection\\s+)?prayers?",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PLACEHOLDER_NAME = Pattern.compile(
		"\\b(?:two|2|multiple|different|both|several)\\s+combat\\s+styles?\\b", Pattern.CASE_INSENSITIVE);

	private static final Map<String, Integer> KNOWN_IDS = new LinkedHashMap<>();

	static
	{
		KNOWN_IDS.put("shortbow", net.runelite.api.ItemID.SHORTBOW);
		KNOWN_IDS.put("bronze arrow", net.runelite.api.ItemID.BRONZE_ARROW);
		KNOWN_IDS.put("staff of air", net.runelite.api.ItemID.STAFF_OF_AIR);
		KNOWN_IDS.put("mind rune", net.runelite.api.ItemID.MIND_RUNE);
	}

	private QuestStyleSwap()
	{
	}

	/** The resolved second-style plan of one fight. */
	public static final class Plan
	{
		public final String fightKey;
		public final Style primary;
		public final Style secondary;
		public final String weapon;
		public final String ammo;
		public final int ammoQty;
		/** Inventory Setup naming the secondary weapon/ammo; empty when weapon and ammo are named directly. */
		public final String setup;

		Plan(String fightKey, Style primary, Style secondary, String weapon, String ammo, int ammoQty, String setup)
		{
			this.fightKey = fightKey;
			this.primary = primary;
			this.secondary = secondary;
			this.weapon = weapon;
			this.ammo = ammo;
			this.ammoQty = ammoQty;
			this.setup = setup;
		}

		/** The same plan with the weapon and ammo read from its Inventory Setup. */
		public Plan withKit(String setupWeapon, String setupAmmo, int setupAmmoQty)
		{
			return new Plan(fightKey, primary, secondary, setupWeapon == null ? "" : setupWeapon,
				setupAmmo == null ? "" : setupAmmo, Math.max(0, setupAmmoQty), setup);
		}

		public boolean hasSetup()
		{
			return !setup.isEmpty();
		}

		/** Equipment slots wielding the secondary weapon takes over (weapon, shield for two-handers, ammo). */
		public Set<String> slotsUsed()
		{
			Set<String> slots = weaponSlots(weapon);
			if (secondary == Style.RANGED && !ammo.isEmpty())
			{
				slots.add("ammo");
			}
			return slots;
		}
	}

	/** True when the text says the fight needs two combat styles (swapping to bypass prayers). */
	public static boolean textNeedsSwap(List<String> texts)
	{
		if (texts == null)
		{
			return false;
		}
		for (String line : texts)
		{
			if (line != null && SWAP_TEXT.matcher(line.replaceAll("<[^>]*>", " ")).find())
			{
				return true;
			}
		}
		return false;
	}

	/** True when a requirement name is the "Two combat styles" placeholder. */
	public static boolean isStylePlaceholder(String requirementName)
	{
		return requirementName != null && PLACEHOLDER_NAME.matcher(requirementName).find();
	}

	/** Slots a weapon occupies when wielded: always the weapon slot, and the shield slot for two-handers. */
	public static Set<String> weaponSlots(String weaponName)
	{
		Set<String> slots = new LinkedHashSet<>();
		slots.add("weapon");
		if (weaponName != null)
		{
			String name = weaponName.toLowerCase(Locale.ROOT);
			boolean bow = name.contains("bow") && !name.contains("crossbow");
			if (bow || name.contains("2h") || name.contains("godsword") || name.contains("halberd")
				|| name.contains("maul") || name.contains("battleaxe"))
			{
				slots.add("shield");
			}
		}
		return slots;
	}

	/**
	 * The best-trained style other than {@code primary}. Ties go ranged, then magic, then melee (the cheapest kits
	 * first). Styles with a level below 1 or in {@code forbidden} are skipped.
	 *
	 * @return null when no other style is usable
	 */
	public static Style defaultSecondary(Style primary, int melee, int ranged, int magic, Set<Style> forbidden)
	{
		Style best = null;
		int bestLevel = 0;
		Style[] order = {Style.RANGED, Style.MAGIC, Style.MELEE};
		int[] levels = {ranged, magic, melee};
		for (int i = 0; i < order.length; i++)
		{
			Style candidate = order[i];
			if (candidate == primary || levels[i] < 1 || forbidden != null && forbidden.contains(candidate))
			{
				continue;
			}
			if (levels[i] > bestLevel)
			{
				best = candidate;
				bestLevel = levels[i];
			}
		}
		return best;
	}

	/** Default weapon, ammo and ammo quantity for a style; blanks when the style has no default kit. */
	public static String[] defaultKit(Style style)
	{
		if (style == Style.RANGED)
		{
			return new String[]{DEFAULT_RANGED_WEAPON, DEFAULT_RANGED_AMMO, String.valueOf(DEFAULT_RANGED_AMMO_QTY)};
		}
		if (style == Style.MAGIC)
		{
			return new String[]{DEFAULT_MAGIC_WEAPON, DEFAULT_MAGIC_AMMO, String.valueOf(DEFAULT_MAGIC_AMMO_QTY)};
		}
		return new String[]{"", "", "0"};
	}

	/** Item id of a default-kit item by name, or -1 when the name is not one of them. */
	public static int knownItemId(String name)
	{
		Integer id = name == null ? null : KNOWN_IDS.get(name.trim().toLowerCase(Locale.ROOT));
		return id == null ? -1 : id;
	}

	/**
	 * Resolves a fight's swap plan.
	 *
	 * @param own the fight's saved setup, or null
	 * @param detected whether the fight text or requirements say it needs a style swap
	 * @param primary the style the fight starts with
	 * @return null when the fight is not swapped (not detected and no style chosen, "None" chosen, or no usable style)
	 */
	public static Plan plan(String fightKey, FightSetup own, boolean detected, Style primary, int melee, int ranged,
		int magic, List<String> restrictionTexts)
	{
		SecondaryStyle chosen = own == null || own.getSecondaryStyle() == null ? SecondaryStyle.AUTO
			: own.getSecondaryStyle();
		if (chosen == SecondaryStyle.NONE || chosen == SecondaryStyle.AUTO && !detected)
		{
			return null;
		}
		Style secondary = chosen == SecondaryStyle.AUTO
			? defaultSecondary(primary, melee, ranged, magic, QuestCombatLoadout.forbiddenStyles(restrictionTexts))
			: chosen.style();
		if (secondary == null || secondary == primary)
		{
			return null;
		}
		String[] kit = defaultKit(secondary);
		String setup = own == null || own.getSecondaryGearSetup() == null ? "" : own.getSecondaryGearSetup().trim();
		String weapon = own == null || own.getSecondaryWeapon().isEmpty() ? kit[0] : own.getSecondaryWeapon();
		String ammo = own == null || own.getSecondaryAmmo().isEmpty() ? kit[1] : own.getSecondaryAmmo();
		int qty = own == null || own.getSecondaryAmmoQty() <= 0 ? Integer.parseInt(kit[2]) : own.getSecondaryAmmoQty();
		if (!setup.isEmpty())
		{
			weapon = "";
			ammo = "";
			qty = 0;
		}
		ammo = ammo == null ? "" : ammo.trim();
		return new Plan(fightKey, primary, secondary, weapon == null ? "" : weapon.trim(), ammo,
			ammo.isEmpty() ? 0 : qty, setup);
	}

	/** Styles a target's overhead icon protects against. */
	public static Set<Style> stylesOf(HeadIcon icon)
	{
		Set<Style> out = EnumSet.noneOf(Style.class);
		if (icon == null)
		{
			return out;
		}
		switch (icon)
		{
			case MELEE:
			case DEFLECT_MELEE:
				out.add(Style.MELEE);
				break;
			case RANGED:
			case DEFLECT_RANGE:
				out.add(Style.RANGED);
				break;
			case MAGIC:
			case DEFLECT_MAGE:
				out.add(Style.MAGIC);
				break;
			case RANGE_MAGE:
				out.add(Style.RANGED);
				out.add(Style.MAGIC);
				break;
			case RANGE_MELEE:
				out.add(Style.RANGED);
				out.add(Style.MELEE);
				break;
			case MAGE_MELEE:
				out.add(Style.MAGIC);
				out.add(Style.MELEE);
				break;
			case RANGE_MAGE_MELEE:
				out.add(Style.RANGED);
				out.add(Style.MAGIC);
				out.add(Style.MELEE);
				break;
			default:
				break;
		}
		return out;
	}

	/**
	 * Styles the target protects against, from its overhead sprite ids (-1 marks an unused entry).
	 *
	 * @return null when there is no overhead information at all (array missing), else the protected styles (empty
	 * when the target shows no protection icon)
	 */
	public static Set<Style> protectedStyles(short[] overheadSpriteIds)
	{
		if (overheadSpriteIds == null)
		{
			return null;
		}
		Set<Style> out = EnumSet.noneOf(Style.class);
		HeadIcon[] icons = HeadIcon.values();
		for (short id : overheadSpriteIds)
		{
			if (id >= 0 && id < icons.length)
			{
				out.addAll(stylesOf(icons[id]));
			}
		}
		return out;
	}

	/**
	 * The style to swap to, or null to stay. Swaps only when {@code current} is protected against, to the other of
	 * the two planned styles when that one is open, else to the remaining open planned style.
	 */
	public static Style swapTarget(Style current, Style primary, Style secondary, Set<Style> protectedStyles)
	{
		if (current == null || protectedStyles == null || !protectedStyles.contains(current))
		{
			return null;
		}
		Style first = current == primary ? secondary : primary;
		if (first != null && first != current && !protectedStyles.contains(first))
		{
			return first;
		}
		Style second = first == primary ? secondary : primary;
		if (second != null && second != current && !protectedStyles.contains(second))
		{
			return second;
		}
		return null;
	}

	/** Without overhead info: the other planned style, once {@link #fallbackDue} says the fight has stalled. */
	public static Style fallbackTarget(Style current, Style primary, Style secondary)
	{
		Style other = current == primary ? secondary : primary;
		return other == current ? null : other;
	}

	/** Whether at least two ticks passed since the last swap. */
	public static boolean swapAllowed(long lastSwapMs, long nowMs)
	{
		return lastSwapMs <= 0 || nowMs - lastSwapMs >= MIN_SWAP_INTERVAL_MS;
	}

	/** Whether the target has not lost health for long enough to try the other style. */
	public static boolean fallbackDue(long lastProgressMs, long nowMs)
	{
		return lastProgressMs > 0 && nowMs - lastProgressMs >= FALLBACK_STALL_MS;
	}

	/** Tiles within which a kill step counts as being in the same area as the step before it. */
	public static final int SAME_AREA_TILES = 40;

	/** One step ahead of the active one, as the kit look-ahead sees it. */
	public static final class Ahead
	{
		public final boolean kill;
		public final boolean needsSwap;
		/** In the same area as the active step: within {@link #SAME_AREA_TILES} or the same NPC. */
		public final boolean near;

		public Ahead(boolean kill, boolean needsSwap, boolean near)
		{
			this.kill = kill;
			this.needsSwap = needsSwap;
			this.near = near;
		}
	}

	/**
	 * Index of the upcoming kill step whose swap kit has to be ready before the active (non-kill) step runs, or -1.
	 * Only the first kill step ahead counts, and only when it needs a swap and is in the same area as the active step
	 * (a talk step that starts the fight).
	 */
	public static int upcomingSwapKill(boolean currentIsKill, List<Ahead> ahead)
	{
		if (currentIsKill || ahead == null)
		{
			return -1;
		}
		for (int i = 0; i < ahead.size(); i++)
		{
			Ahead a = ahead.get(i);
			if (a != null && a.kill)
			{
				return a.needsSwap && a.near ? i : -1;
			}
		}
		return -1;
	}

	/** True when the swap weapon or its ammo/runes are not held in enough quantity. */
	public static boolean kitMissing(String weapon, boolean weaponHeld, String ammo, int ammoQty, int ammoHeld)
	{
		if (weapon != null && !weapon.isEmpty() && !weaponHeld)
		{
			return true;
		}
		return ammo != null && !ammo.isEmpty() && ammoQty > 0 && ammoHeld < ammoQty;
	}

	/**
	 * The style to wield now, or null to stay. A protected current style swaps first; otherwise (no overhead data, no
	 * protection icon read, or both planned styles protected) a target that has not lost health for
	 * {@link #FALLBACK_STALL_MS} makes the player try the other planned style.
	 *
	 * @param protectedStyles styles the target protects against, or null without overhead information
	 */
	public static Style wantedStyle(Style current, Style primary, Style secondary, Set<Style> protectedStyles,
		long lastProgressMs, long nowMs)
	{
		if (protectedStyles != null)
		{
			Style swap = swapTarget(current, primary, secondary, protectedStyles);
			if (swap != null)
			{
				return swap;
			}
		}
		return fallbackDue(lastProgressMs, nowMs) ? fallbackTarget(current, primary, secondary) : null;
	}
}
