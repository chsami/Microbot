package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The potion list of a quest setup: rows of (potion type, count). Pure logic, no client access: the type catalogue,
 * migration of the old single potion and boost fields, and the deposit protection decision for user-brought items.
 */
public final class QuestPotions
{
	/** What a potion row is for; decides when it is drunk. */
	public enum Role
	{
		PRAYER, BOOST, ANTIFIRE, ANTIPOISON, ENERGY
	}

	/** One potion type the setup can bring. {@code itemName} is the base name searched in the bank and bought. */
	public enum Kind
	{
		PRAYER_POTION("prayer_potion", "Prayer potion", Role.PRAYER, null, 2434, 139, 141, 143),
		SUPER_RESTORE("super_restore", "Super restore", Role.PRAYER, null, 3024, 3026, 3028, 3030),
		SUPER_ATTACK("super_attack", "Super attack", Role.BOOST, QuestBoosts.Potion.SUPER_ATTACK),
		SUPER_STRENGTH("super_strength", "Super strength", Role.BOOST, QuestBoosts.Potion.SUPER_STRENGTH),
		SUPER_DEFENCE("super_defence", "Super defence", Role.BOOST, QuestBoosts.Potion.SUPER_DEFENCE),
		SUPER_COMBAT("super_combat", "Super combat potion", Role.BOOST, QuestBoosts.Potion.SUPER_COMBAT),
		COMBAT("combat_potion", "Combat potion", Role.BOOST, QuestBoosts.Potion.COMBAT),
		ATTACK("attack_potion", "Attack potion", Role.BOOST, QuestBoosts.Potion.ATTACK),
		STRENGTH("strength_potion", "Strength potion", Role.BOOST, QuestBoosts.Potion.STRENGTH),
		DEFENCE("defence_potion", "Defence potion", Role.BOOST, QuestBoosts.Potion.DEFENCE),
		RANGING("ranging_potion", "Ranging potion", Role.BOOST, QuestBoosts.Potion.RANGING),
		MAGIC("magic_potion", "Magic potion", Role.BOOST, QuestBoosts.Potion.MAGIC),
		ANTIPOISON("antipoison", "Antipoison", Role.ANTIPOISON, null, 2446, 175, 177, 179),
		SUPERANTIPOISON("superantipoison", "Superantipoison", Role.ANTIPOISON, null, 2448, 181, 183, 185),
		ANTIDOTE_PLUS_PLUS("antidote_plus_plus", "Antidote++", Role.ANTIPOISON, null, 5952, 5954, 5956, 5958),
		ANTIFIRE("antifire", "Antifire potion", Role.ANTIFIRE, null, 2452, 2454, 2456, 2458),
		EXTENDED_ANTIFIRE("extended_antifire", "Extended antifire", Role.ANTIFIRE, null, 11951, 11953, 11955, 11957),
		SUPER_ANTIFIRE("super_antifire", "Super antifire potion", Role.ANTIFIRE, null, 21978, 21981, 21984, 21987),
		ENERGY("energy_potion", "Energy potion", Role.ENERGY, null, 3008, 3010, 3012, 3014),
		SUPER_ENERGY("super_energy", "Super energy", Role.ENERGY, null, 3016, 3018, 3020, 3022),
		STAMINA("stamina_potion", "Stamina potion", Role.ENERGY, null, 12625, 12627, 12629, 12631,
			31638, 31641, 31644, 31647);

		private final String key;
		private final String itemName;
		private final Role role;
		private final QuestBoosts.Potion boost;
		private final int[] ids;

		Kind(String key, String itemName, Role role, QuestBoosts.Potion boost, int... ids)
		{
			this.key = key;
			this.itemName = itemName;
			this.role = role;
			this.boost = boost;
			this.ids = boost != null ? boost.doseIds().stream().mapToInt(Integer::intValue).toArray() : ids;
		}

		public String key()
		{
			return key;
		}

		public String itemName()
		{
			return itemName;
		}

		public Role role()
		{
			return role;
		}

		/** The matching boost definition, or null for non-boost (and plain Defence) potions. */
		public QuestBoosts.Potion boost()
		{
			return boost;
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

		@Override
		public String toString()
		{
			return itemName;
		}
	}

	/** One row of the list; {@code type} is a {@link Kind#key()}. */
	public static class Row
	{
		private String type = "";
		private int qty = 1;

		public Row()
		{
		}

		public Row(String type, int qty)
		{
			this.type = type == null ? "" : type;
			this.qty = qty;
		}

		public String getType()
		{
			return type;
		}

		public int getQty()
		{
			return qty;
		}

		public Kind kind()
		{
			return kindOf(type);
		}
	}

	private QuestPotions()
	{
	}

	/** The kind named by a key or display name (case, spaces and dashes ignored), or null. */
	public static Kind kindOf(String text)
	{
		if (text == null)
		{
			return null;
		}
		String norm = normalise(text);
		for (Kind kind : Kind.values())
		{
			if (normalise(kind.key).equals(norm) || normalise(kind.itemName).equals(norm))
			{
				return kind;
			}
		}
		return null;
	}

	private static String normalise(String text)
	{
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+]", "");
	}

	/** Valid rows only: known type, quantity above zero, one row per type with quantities added, capped at 27. */
	public static List<Row> clean(List<Row> rows)
	{
		List<Row> out = new ArrayList<>();
		if (rows == null)
		{
			return out;
		}
		for (Row row : rows)
		{
			Kind kind = row == null ? null : row.kind();
			if (kind == null || row.qty <= 0)
			{
				continue;
			}
			Row existing = null;
			for (Row candidate : out)
			{
				if (candidate.kind() == kind)
				{
					existing = candidate;
				}
			}
			if (existing == null)
			{
				out.add(new Row(kind.key, Math.min(27, row.qty)));
			}
			else
			{
				existing.qty = Math.min(27, existing.qty + row.qty);
			}
		}
		return out;
	}

	/** The list the old single potion, its quantity and the boost choice (#50 fields) stand for. */
	public static List<Row> migrate(QuestCombatLoadout.Potion potion, int potionQty,
		QuestCombatLoadout.BoostType boostType, boolean boostDefence, int boostQty)
	{
		List<Row> out = new ArrayList<>();
		if (potion != null && potion != QuestCombatLoadout.Potion.NONE && potionQty > 0)
		{
			out.add(new Row(potion == QuestCombatLoadout.Potion.PRAYER_POTION ? Kind.PRAYER_POTION.key
				: Kind.SUPER_RESTORE.key, potionQty));
		}
		if (boostType != null && boostType != QuestCombatLoadout.BoostType.NONE && boostQty > 0)
		{
			for (QuestBoosts.Potion boost : QuestBoosts.potionsFor(boostType, boostDefence))
			{
				for (Kind kind : Kind.values())
				{
					if (kind.boost == boost)
					{
						out.add(new Row(kind.key, boostQty));
					}
				}
			}
		}
		return clean(out);
	}

	/**
	 * True when a base item name (dose stripped) is a run-energy potion: energy, super energy, stamina, and the
	 * stamina variants ("Extended stamina potion", "Brutal stamina potion").
	 */
	public static boolean isRunEnergyName(String baseName)
	{
		if (baseName == null)
		{
			return false;
		}
		String n = baseName.trim().toLowerCase(java.util.Locale.ROOT);
		return n.equals("energy potion") || n.equals("super energy") || n.endsWith("stamina potion");
	}

	/** True when {@code base} satisfies the wanted run-potion name, counting stamina variants as stamina. */
	public static boolean runPotionMatches(String base, String wanted)
	{
		if (base == null || wanted == null)
		{
			return false;
		}
		if (base.trim().equalsIgnoreCase(wanted.trim()))
		{
			return true;
		}
		return wanted.trim().equalsIgnoreCase("Stamina potion")
			&& base.trim().toLowerCase(java.util.Locale.ROOT).endsWith("stamina potion");
	}

	/** True when the item name (any dose) is the given kind. */
	public static boolean matchesKind(Kind kind, String itemName)
	{
		return kind != null && itemName != null && ChargedItemNames.sameFamily(itemName, kind.itemName);
	}

	/** The catalogue kind an item name (any dose) belongs to, or null. */
	public static Kind kindOfItem(String itemName)
	{
		for (Kind kind : Kind.values())
		{
			if (matchesKind(kind, itemName))
			{
				return kind;
			}
		}
		return null;
	}

	/** True for something the user plausibly brought on purpose: a potion, food, teleport or rune. */
	public static boolean isUserConsumable(String itemName, List<String> actions)
	{
		String name = itemName == null ? "" : itemName.toLowerCase(Locale.ROOT);
		if (name.contains("teleport") || name.endsWith(" rune") || name.contains(" runes") || kindOfItem(itemName) != null)
		{
			return true;
		}
		if (actions != null)
		{
			for (String action : actions)
			{
				if (action != null && Arrays.asList("drink", "eat").contains(action.toLowerCase(Locale.ROOT)))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Whether a bank visit may deposit an item. A consumable the user had in the inventory when the quest started
	 * stays, unless its slot is strictly needed for a quest item that must be withdrawn right now.
	 *
	 * @param snapshotIds item ids in the inventory when the quest was started
	 * @param slotNeededNow true only when a quest item must be withdrawn and no free slot is left
	 */
	public static boolean mayDepositUserItem(int itemId, String itemName, List<String> actions,
		Set<Integer> snapshotIds, boolean slotNeededNow)
	{
		boolean userBrought = snapshotIds != null && snapshotIds.contains(itemId);
		if (!userBrought || !isUserConsumable(itemName, actions))
		{
			return true;
		}
		return slotNeededNow;
	}

	/** Deposit order when a slot is strictly needed: the lower the value, the earlier the item goes. */
	public static int depositPriority(String itemName)
	{
		Kind kind = kindOfItem(itemName);
		if (kind == null)
		{
			return 0;
		}
		return kind.role == Role.PRAYER ? 3 : kind.role == Role.BOOST ? 2 : 1;
	}
}
