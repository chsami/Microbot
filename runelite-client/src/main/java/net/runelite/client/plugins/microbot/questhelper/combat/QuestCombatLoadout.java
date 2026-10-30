package net.runelite.client.plugins.microbot.questhelper.combat;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;

/**
 * What the player wants to wear, bring and how to fight for one quest: combat gear, food, potions, prayer, style,
 * per-fight overrides, plus quest-wide supplies (stamina potions, custom inventory items, reserved slots).
 */
@Data
public class QuestCombatLoadout
{
	/** Combat potion the setup can bring. */
	public enum Potion
	{
		NONE("None", ""),
		PRAYER_POTION("Prayer potion", "Prayer potion"),
		SUPER_RESTORE("Super restore", "Super restore");

		private final String display;
		private final String itemName;

		Potion(String display, String itemName)
		{
			this.display = display;
			this.itemName = itemName;
		}

		public String getItemName()
		{
			return itemName;
		}

		@Override
		public String toString()
		{
			return display;
		}
	}

	/** Protection prayer the setup can use. */
	public enum Prayer
	{
		OFF("Off"),
		QUICK("Quick prayers"),
		BEST_OFFENSIVE("Best offensive"),
		PROTECT_MELEE("Protect from Melee"),
		PROTECT_RANGE("Protect from Missiles"),
		PROTECT_MAGIC("Protect from Magic");

		private final String display;

		Prayer(String display)
		{
			this.display = display;
		}

		@Override
		public String toString()
		{
			return display;
		}
	}

	/** Combat style (attack type) of a fight. */
	public enum Style
	{
		AUTO("Auto (from worn weapon)"),
		MELEE("Melee"),
		RANGED("Ranged"),
		MAGIC("Magic");

		private final String display;

		Style(String display)
		{
			this.display = display;
		}

		@Override
		public String toString()
		{
			return display;
		}
	}

	/** Second combat style of a fight where the target's prayers force a swap. */
	public enum SecondaryStyle
	{
		AUTO("Auto (best trained)", null),
		MELEE("Melee", Style.MELEE),
		RANGED("Ranged", Style.RANGED),
		MAGIC("Magic", Style.MAGIC),
		NONE("None (no swapping)", null);

		private final String display;
		private final Style style;

		SecondaryStyle(String display, Style style)
		{
			this.display = display;
			this.style = style;
		}

		/** The combat style this choice names; null for Auto and None. */
		public Style style()
		{
			return style;
		}

		@Override
		public String toString()
		{
			return display;
		}
	}

	/** Run-energy potion the setup can bring. */
	public enum StaminaType
	{
		NONE("None", ""),
		STAMINA("Stamina potion", "Stamina potion"),
		ENERGY("Energy potion", "Energy potion");

		private final String display;
		private final String itemName;

		StaminaType(String display, String itemName)
		{
			this.display = display;
			this.itemName = itemName;
		}

		public String getItemName()
		{
			return itemName;
		}

		@Override
		public String toString()
		{
			return display;
		}
	}

	/** Combat boost potions the setup can bring and drink when a fight starts. */
	public enum BoostType
	{
		NONE("None"),
		SUPER_ATTACK_STRENGTH("Super attack + Super strength"),
		COMBAT("Combat potion"),
		ATTACK_STRENGTH("Attack + Strength potion");

		private final String display;

		BoostType(String display)
		{
			this.display = display;
		}

		@Override
		public String toString()
		{
			return display;
		}
	}

	/** One recorded or hand-picked worn item. Matched by id when positive, else by exact name. */
	@Data
	public static class GearItem
	{
		private int id = -1;
		private String name = "";
		/** Lower-case equipment slot name ("weapon", "head", ...) or empty when unknown. */
		private String slot = "";
		private int qty = 1;

		public GearItem()
		{
		}

		/** Creates a worn item; null name or slot become empty. */
		public GearItem(int id, String name, String slot, int qty)
		{
			this.id = id;
			this.name = name == null ? "" : name;
			this.slot = slot == null ? "" : slot;
			this.qty = qty;
		}
	}

	/** One thing to bring in the inventory (ammo, runes, teleports, extra potions). */
	@Data
	public static class InventoryItem
	{
		private String name = "";
		private int id = -1;
		private int qty = 1;
		/** Stackable items count as one slot when budgeting space. */
		private boolean stackable;

		public InventoryItem()
		{
		}

		/** Creates an inventory item; a null name becomes empty. */
		public InventoryItem(String name, int id, int qty, boolean stackable)
		{
			this.name = name == null ? "" : name;
			this.id = id;
			this.qty = qty;
			this.stackable = stackable;
		}
	}

	/** Gear to wear: an Inventory Setup name, or a list of items; both empty means "leave the gear alone". */
	@Data
	public static class GearSpec
	{
		private String setup = "";
		private List<GearItem> items = new ArrayList<>();

		public GearSpec()
		{
		}

		/** Creates a gear spec; null values become empty. */
		public GearSpec(String setup, List<GearItem> items)
		{
			this.setup = setup == null ? "" : setup.trim();
			this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
		}

		/** True when neither a setup name nor items are given. */
		public boolean isEmpty()
		{
			return (setup == null || setup.isEmpty()) && (items == null || items.isEmpty());
		}
	}

	/** Per-fight override. A null style or empty gear inherits the main setup. */
	@Data
	public static class FightSetup
	{
		private Style style;
		private String gearSetup = "";
		private List<GearItem> gearItems = new ArrayList<>();
		/** Second style for a fight that needs a style swap; null means Auto. */
		private SecondaryStyle secondaryStyle;
		/** Secondary weapon and ammo/runes item names; empty uses the style's default kit. */
		private String secondaryWeapon = "";
		private String secondaryAmmo = "";
		private int secondaryAmmoQty;
		/** Inventory Setup naming the secondary weapon and ammo; wins over the item names. */
		private String secondaryGearSetup = "";

		public String getSecondaryWeapon()
		{
			return secondaryWeapon == null ? "" : secondaryWeapon;
		}

		public String getSecondaryAmmo()
		{
			return secondaryAmmo == null ? "" : secondaryAmmo;
		}

		public String getSecondaryGearSetup()
		{
			return secondaryGearSetup == null ? "" : secondaryGearSetup;
		}

		/** The gear this fight overrides with. */
		public GearSpec gear()
		{
			return new GearSpec(gearSetup, gearItems);
		}
	}

	public static final List<String> SLOT_NAMES = Arrays.asList(
		"head", "cape", "amulet", "weapon", "body", "shield", "legs", "gloves", "boots", "ring", "ammo");

	private static final Gson GSON = new Gson();

	/** Fight with whatever is worn, bring no combat supplies. Stamina, custom items and reserved slots still apply. */
	private boolean skip;
	/** Inventory Setup name whose equipment gets worn; empty uses {@link #gearItems} or keeps what is worn. */
	private String gearSetup = "";
	/** Recorded or custom worn items; used when no Inventory Setup is named. */
	private List<GearItem> gearItems = new ArrayList<>();
	private String food = "Lobster";
	private int foodQty = 10;
	private int eatAtPercent = 50;
	private Potion potion = Potion.NONE;
	private int potionQty = 2;
	private Prayer prayer = Prayer.OFF;
	private int drinkAtPrayer = 20;
	private Style style = Style.AUTO;

	private boolean useStamina;
	private StaminaType staminaType = StaminaType.STAMINA;
	private int staminaQty = 2;
	private int drinkStaminaAtEnergy = 30;

	private BoostType boostType = BoostType.NONE;
	/** Adds Super defence to the chosen boost. */
	private boolean boostDefence;
	/** Potions of each kind to bring (any dose). */
	private int boostQty = 1;

	/** The potion list (type + count). Null in loadouts saved before the list existed: then the old fields stand in. */
	private List<QuestPotions.Row> potions;

	private List<InventoryItem> inventoryItems = new ArrayList<>();
	/** Free slots to keep for quest items; negative means "work it out from the quest". */
	private int reserveSlots = -1;
	/** Per-fight overrides keyed by {@link #fightKey(String)}. */
	private Map<String, FightSetup> fights = new LinkedHashMap<>();

	/** A loadout that brings nothing and leaves gear alone (the user chose to skip the setup). */
	public static QuestCombatLoadout skipped()
	{
		QuestCombatLoadout loadout = new QuestCombatLoadout();
		loadout.skip = true;
		loadout.foodQty = 0;
		loadout.potionQty = 0;
		return loadout.normalised();
	}

	/** Brings no food, potions or prayer; only what an Agent Server request adds on top. */
	public static QuestCombatLoadout bare()
	{
		QuestCombatLoadout loadout = new QuestCombatLoadout();
		loadout.food = "";
		loadout.foodQty = 0;
		loadout.potionQty = 0;
		return loadout.normalised();
	}

	/** True when food should be brought. */
	public boolean wantsFood()
	{
		return !skip && foodQty > 0 && !food.isEmpty();
	}

	/** True when combat potions should be brought. */
	public boolean wantsPotions()
	{
		return !skip && !potionRows().isEmpty();
	}

	/** The effective potion rows: the list when set, else what the old potion and boost fields stand for. */
	public List<QuestPotions.Row> potionRows()
	{
		if (skip)
		{
			return new ArrayList<>();
		}
		return QuestPotions.clean(potions != null ? potions
			: QuestPotions.migrate(potion, potionQty, boostType, boostDefence, boostQty));
	}

	/** Rows of one role. */
	public List<QuestPotions.Row> potionRows(QuestPotions.Role role)
	{
		List<QuestPotions.Row> out = new ArrayList<>();
		for (QuestPotions.Row row : potionRows())
		{
			if (row.kind().role() == role)
			{
				out.add(row);
			}
		}
		return out;
	}

	/** True when a prayer potion or super restore is in the list. */
	public boolean wantsPrayerPotions()
	{
		return !potionRows(QuestPotions.Role.PRAYER).isEmpty();
	}

	/** Count of one boost potion in the list; 0 when absent. */
	public int boostQtyOf(QuestBoosts.Potion boost)
	{
		for (QuestPotions.Row row : potionRows(QuestPotions.Role.BOOST))
		{
			if (row.kind().boost() == boost)
			{
				return row.getQty();
			}
		}
		return 0;
	}

	/** Inventory slots the potion list takes. */
	public int potionSlots()
	{
		int sum = 0;
		for (QuestPotions.Row row : potionRows())
		{
			sum += row.getQty();
		}
		return sum;
	}

	/** True when a protection prayer should be used. */
	public boolean usesPrayer()
	{
		return !skip && prayer != Prayer.OFF;
	}

	/** True when run-energy potions should be brought. */
	public boolean wantsStamina()
	{
		return wantsLegacyStamina() || !potionRows(QuestPotions.Role.ENERGY).isEmpty();
	}

	/** True when the old single run-energy choice asks for potions. */
	public boolean wantsLegacyStamina()
	{
		return useStamina && staminaType != StaminaType.NONE && staminaQty > 0;
	}

	/** True when combat boost potions should be brought and drunk in fights. */
	public boolean wantsBoosts()
	{
		return !boostPotions().isEmpty();
	}

	/** The boost potions this loadout brings; empty when none. */
	public List<QuestBoosts.Potion> boostPotions()
	{
		List<QuestBoosts.Potion> out = new ArrayList<>();
		for (QuestPotions.Row row : potionRows(QuestPotions.Role.BOOST))
		{
			if (row.kind().boost() != null)
			{
				out.add(row.kind().boost());
			}
		}
		return out;
	}

	/** True when custom inventory items are configured. */
	public boolean wantsInventoryItems()
	{
		return !inventoryItems.isEmpty();
	}

	/** True when any quest-wide supply (not tied to a fight) should be fetched at the start of the quest. */
	public boolean wantsQuestWideSupplies()
	{
		return wantsStamina() || wantsInventoryItems();
	}

	/** The main gear spec, empty when the setup is skipped. */
	public GearSpec mainGear()
	{
		return skip ? new GearSpec() : new GearSpec(gearSetup, gearItems);
	}

	/** Whether the main gear or any fight's gear lists the item, by id or (without an id) by name. */
	public boolean listsGearItem(int itemId, String itemName)
	{
		List<GearItem> all = new ArrayList<>(gearItems == null ? new ArrayList<>() : gearItems);
		for (FightSetup fight : fights.values())
		{
			if (fight != null && fight.getGearItems() != null)
			{
				all.addAll(fight.getGearItems());
			}
		}
		return gearItemListed(all, itemId, itemName);
	}

	static boolean gearItemListed(List<GearItem> items, int itemId, String itemName)
	{
		for (GearItem wanted : items)
		{
			if (wanted == null)
			{
				continue;
			}
			if (wanted.getId() > 0 ? wanted.getId() == itemId
				: itemName != null && wanted.getName() != null && wanted.getName().equalsIgnoreCase(itemName))
			{
				return true;
			}
		}
		return false;
	}

	/** Gear for one fight: its own override when it sets one, else the main gear. */
	public GearSpec gearFor(String fightKey)
	{
		if (skip)
		{
			return new GearSpec();
		}
		FightSetup own = fightKey == null ? null : fights.get(fightKey);
		if (own != null && !own.gear().isEmpty())
		{
			return own.gear();
		}
		return mainGear();
	}

	/** Style for one fight; may be {@link Style#AUTO}. */
	public Style styleFor(String fightKey)
	{
		FightSetup own = fightKey == null ? null : fights.get(fightKey);
		return own != null && own.getStyle() != null ? own.getStyle() : style;
	}

	/**
	 * The style swap plan of one fight, or null when it is not swapped (the setup is skipped, the fight needs no
	 * swap and none was chosen, or "None" was chosen).
	 */
	public QuestStyleSwap.Plan swapPlanFor(String fightKey, boolean detected, Style primary, int melee, int ranged,
		int magic, List<String> restrictionTexts)
	{
		if (skip || fightKey == null)
		{
			return null;
		}
		return QuestStyleSwap.plan(fightKey, fights.get(fightKey), detected, primary, melee, ranged, magic,
			restrictionTexts);
	}

	/** Clamps values and fills nulls, so a hand-edited or partial JSON cannot break the fight loop. */
	public QuestCombatLoadout normalised()
	{
		gearSetup = gearSetup == null ? "" : gearSetup.trim();
		gearItems = cleanGear(gearItems);
		food = food == null ? "" : food.trim();
		foodQty = clamp(foodQty, 0, 27);
		eatAtPercent = clamp(eatAtPercent, 1, 99);
		potion = potion == null ? Potion.NONE : potion;
		potionQty = clamp(potionQty, 0, 27);
		prayer = prayer == null ? Prayer.OFF : prayer;
		drinkAtPrayer = clamp(drinkAtPrayer, 1, 98);
		style = style == null ? Style.AUTO : style;
		staminaType = staminaType == null ? StaminaType.STAMINA : staminaType;
		staminaQty = clamp(staminaQty, 0, 27);
		drinkStaminaAtEnergy = clamp(drinkStaminaAtEnergy, 1, 99);
		boostType = boostType == null ? BoostType.NONE : boostType;
		boostQty = clamp(boostQty, 0, 27);
		potions = potions == null ? null : QuestPotions.clean(potions);
		reserveSlots = reserveSlots < 0 ? -1 : Math.min(reserveSlots, 27);

		List<InventoryItem> cleanItems = new ArrayList<>();
		if (inventoryItems != null)
		{
			for (InventoryItem item : inventoryItems)
			{
				if (item == null)
				{
					continue;
				}
				item.name = item.name == null ? "" : item.name.trim();
				item.qty = clamp(item.qty, 1, 1_000_000);
				if (!item.name.isEmpty() || item.id > 0)
				{
					cleanItems.add(item);
				}
			}
		}
		inventoryItems = cleanItems;

		Map<String, FightSetup> cleanFights = new LinkedHashMap<>();
		if (fights != null)
		{
			for (Map.Entry<String, FightSetup> entry : fights.entrySet())
			{
				if (entry.getKey() == null || entry.getValue() == null)
				{
					continue;
				}
				FightSetup setup = entry.getValue();
				setup.gearSetup = setup.gearSetup == null ? "" : setup.gearSetup.trim();
				setup.gearItems = cleanGear(setup.gearItems);
				setup.secondaryWeapon = setup.getSecondaryWeapon().trim();
				setup.secondaryAmmo = setup.getSecondaryAmmo().trim();
				setup.secondaryGearSetup = setup.getSecondaryGearSetup().trim();
				setup.secondaryAmmoQty = clamp(setup.secondaryAmmoQty, 0, 1_000_000);
				cleanFights.put(fightKey(entry.getKey()), setup);
			}
		}
		fights = cleanFights;
		return this;
	}

	private static List<GearItem> cleanGear(List<GearItem> items)
	{
		List<GearItem> out = new ArrayList<>();
		if (items != null)
		{
			for (GearItem item : items)
			{
				if (item == null)
				{
					continue;
				}
				item.name = item.name == null ? "" : item.name.trim();
				item.slot = item.slot == null ? "" : item.slot.trim().toLowerCase(Locale.ROOT);
				item.qty = clamp(item.qty, 1, 1_000_000);
				if (item.id > 0 || !item.name.isEmpty())
				{
					out.add(item);
				}
			}
		}
		return out;
	}

	/** Serialises the loadout for the config store. */
	public String toJson()
	{
		return GSON.toJson(this);
	}

	/** @return the loadout, or null when the text is empty or not valid JSON */
	public static QuestCombatLoadout fromJson(String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			QuestCombatLoadout loadout = GSON.fromJson(json, QuestCombatLoadout.class);
			return loadout == null ? null : loadout.normalised();
		}
		catch (JsonSyntaxException e)
		{
			return null;
		}
	}

	/** Normalises a fight label or key ("Temple Guardian (level 30)") to its map key ("temple guardian"). */
	public static String fightKey(String label)
	{
		if (label == null)
		{
			return "";
		}
		String text = label.replaceAll("<[^>]*>", " ").replaceAll("\\([^)]*\\)", " ");
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	/**
	 * Builds a loadout from the Agent Server "combat" object; absent keys keep their defaults.
	 *
	 * @throws IllegalArgumentException for an unknown enum value or a non-numeric number
	 */
	public static QuestCombatLoadout fromMap(Map<?, ?> map)
	{
		QuestCombatLoadout loadout = new QuestCombatLoadout();
		if (map.get("gearSetup") != null)
		{
			loadout.gearSetup = String.valueOf(map.get("gearSetup"));
		}
		if (map.get("food") != null)
		{
			loadout.food = String.valueOf(map.get("food"));
		}
		loadout.foodQty = intValue(map, "foodQty", loadout.foodQty, "combat.");
		loadout.eatAtPercent = intValue(map, "eatAtPercent", loadout.eatAtPercent, "combat.");
		loadout.potion = enumValue(map, "potion", Potion.class, loadout.potion, "combat.");
		loadout.potionQty = intValue(map, "potionQty", loadout.potionQty, "combat.");
		loadout.prayer = enumValue(map, "prayer", Prayer.class, loadout.prayer, "combat.");
		loadout.drinkAtPrayer = intValue(map, "drinkAtPrayer", loadout.drinkAtPrayer, "combat.");
		loadout.style = enumValue(map, "style", Style.class, loadout.style, "combat.");
		return loadout.normalised();
	}

	/**
	 * Applies the other Agent Server body fields on top of {@code base}: "gear", "inventory", "reserveSlots",
	 * "stamina" and "fights". Absent fields leave {@code base} alone.
	 *
	 * @param worn supplies the currently worn items for {@code "gear": "worn"}
	 * @throws IllegalArgumentException for any bad value; the message names the field
	 */
	public static QuestCombatLoadout applyOverrides(QuestCombatLoadout base, Map<?, ?> body,
		Supplier<List<GearItem>> worn)
	{
		if (body.get("gear") != null)
		{
			GearSpec gear = parseGear(body.get("gear"), worn, "gear");
			base.gearSetup = gear.setup;
			base.gearItems = gear.items;
		}
		if (body.get("inventory") != null)
		{
			Object raw = body.get("inventory");
			if (!(raw instanceof List))
			{
				throw new IllegalArgumentException("inventory must be an array of {name, qty}");
			}
			List<InventoryItem> items = new ArrayList<>();
			for (Object entry : (List<?>) raw)
			{
				if (!(entry instanceof Map))
				{
					throw new IllegalArgumentException("inventory entries must be objects like {\"name\":\"Law rune\",\"qty\":10}");
				}
				Map<?, ?> row = (Map<?, ?>) entry;
				InventoryItem item = new InventoryItem();
				item.name = row.get("name") == null ? "" : String.valueOf(row.get("name")).trim();
				item.id = intValue(row, "id", -1, "inventory.");
				item.qty = intValue(row, "qty", 1, "inventory.");
				item.stackable = Boolean.parseBoolean(String.valueOf(row.get("stackable")));
				if (item.name.isEmpty() && item.id <= 0)
				{
					throw new IllegalArgumentException("inventory entries need a name or an id");
				}
				if (item.qty < 1)
				{
					throw new IllegalArgumentException("inventory.qty must be at least 1");
				}
				items.add(item);
			}
			base.inventoryItems = items;
		}
		if (body.get("reserveSlots") != null)
		{
			int slots = intValue(body, "reserveSlots", -1, "");
			if (slots < 0 || slots > 27)
			{
				throw new IllegalArgumentException("reserveSlots must be between 0 and 27");
			}
			base.reserveSlots = slots;
		}
		if (body.get("stamina") != null)
		{
			applyStamina(base, body.get("stamina"));
		}
		if (body.get("boosts") != null)
		{
			applyBoosts(base, body.get("boosts"));
		}
		if (body.get("potions") != null)
		{
			applyPotions(base, body.get("potions"));
		}
		if (body.get("fights") != null)
		{
			Object raw = body.get("fights");
			if (!(raw instanceof Map))
			{
				throw new IllegalArgumentException("fights must be an object keyed by fight name");
			}
			Map<String, FightSetup> parsed = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet())
			{
				String key = fightKey(String.valueOf(entry.getKey()));
				if (key.isEmpty() || !(entry.getValue() instanceof Map))
				{
					throw new IllegalArgumentException("fights." + entry.getKey()
						+ " must be an object like {\"style\":\"melee\",\"gearSetup\":\"...\"}");
				}
				Map<?, ?> row = (Map<?, ?>) entry.getValue();
				String prefix = "fights." + entry.getKey() + ".";
				FightSetup setup = new FightSetup();
				if (row.get("style") != null)
				{
					setup.style = enumValue(row, "style", Style.class, null, prefix);
				}
				if (row.get("gearSetup") != null)
				{
					setup.gearSetup = String.valueOf(row.get("gearSetup"));
				}
				if (row.get("secondaryStyle") != null)
				{
					setup.secondaryStyle = enumValue(row, "secondaryStyle", SecondaryStyle.class, null, prefix);
				}
				if (row.get("secondaryWeapon") != null)
				{
					setup.secondaryWeapon = String.valueOf(row.get("secondaryWeapon"));
				}
				if (row.get("secondaryAmmo") != null)
				{
					setup.secondaryAmmo = String.valueOf(row.get("secondaryAmmo"));
				}
				setup.secondaryAmmoQty = intValue(row, "secondaryAmmoQty", 0, prefix);
				if (row.get("secondaryGearSetup") != null)
				{
					setup.secondaryGearSetup = String.valueOf(row.get("secondaryGearSetup"));
				}
				if (row.get("gear") != null)
				{
					GearSpec gear = parseGear(row.get("gear"), worn, prefix + "gear");
					setup.gearSetup = gear.setup;
					setup.gearItems = gear.items;
				}
				parsed.put(key, setup);
			}
			base.fights = parsed;
		}
		return base.normalised();
	}

	private static void applyPotions(QuestCombatLoadout base, Object raw)
	{
		if (!(raw instanceof List))
		{
			throw new IllegalArgumentException("potions must be an array like [{\"type\":\"prayer_potion\",\"qty\":4}]");
		}
		List<QuestPotions.Row> rows = new ArrayList<>();
		for (Object entry : (List<?>) raw)
		{
			if (!(entry instanceof Map))
			{
				throw new IllegalArgumentException("potions entries must be objects like {\"type\":\"prayer_potion\",\"qty\":4}");
			}
			Map<?, ?> row = (Map<?, ?>) entry;
			QuestPotions.Kind kind = QuestPotions.kindOf(row.get("type") == null ? null : String.valueOf(row.get("type")));
			if (kind == null)
			{
				throw new IllegalArgumentException("potions.type " + row.get("type") + " is unknown; use one of "
					+ java.util.Arrays.stream(QuestPotions.Kind.values()).map(QuestPotions.Kind::key)
						.collect(java.util.stream.Collectors.joining(", ")));
			}
			int qty = intValue(row, "qty", 1, "potions.");
			if (qty < 0 || qty > 27)
			{
				throw new IllegalArgumentException("potions.qty must be between 0 and 27");
			}
			rows.add(new QuestPotions.Row(kind.key(), qty));
		}
		base.potions = rows;
	}

	private static void applyBoosts(QuestCombatLoadout base, Object raw)
	{
		if (raw instanceof String)
		{
			base.boostType = enumValue(Collections1.single("type", raw), "type", BoostType.class, base.boostType,
				"boosts.");
			return;
		}
		if (!(raw instanceof Map))
		{
			throw new IllegalArgumentException("boosts must be an object like "
				+ "{\"type\":\"super_attack_strength\",\"defence\":false,\"qty\":1}");
		}
		Map<?, ?> map = (Map<?, ?>) raw;
		base.boostType = enumValue(map, "type", BoostType.class, base.boostType, "boosts.");
		base.boostQty = intValue(map, "qty", base.boostQty, "boosts.");
		if (base.boostQty < 0 || base.boostQty > 27)
		{
			throw new IllegalArgumentException("boosts.qty must be between 0 and 27");
		}
		if (map.get("defence") != null)
		{
			base.boostDefence = Boolean.parseBoolean(String.valueOf(map.get("defence")));
		}
	}

	private static void applyStamina(QuestCombatLoadout base, Object raw)
	{
		if (raw instanceof Boolean)
		{
			base.useStamina = (Boolean) raw;
			return;
		}
		if (raw instanceof String)
		{
			base.staminaType = enumValue(Collections1.single("type", raw), "type", StaminaType.class,
				base.staminaType, "stamina.");
			base.useStamina = base.staminaType != StaminaType.NONE;
			return;
		}
		if (!(raw instanceof Map))
		{
			throw new IllegalArgumentException("stamina must be an object like {\"type\":\"stamina\",\"qty\":2,\"drinkAtEnergy\":30}");
		}
		Map<?, ?> map = (Map<?, ?>) raw;
		base.staminaType = enumValue(map, "type", StaminaType.class, base.staminaType, "stamina.");
		base.staminaQty = intValue(map, "qty", base.staminaQty, "stamina.");
		base.drinkStaminaAtEnergy = intValue(map, "drinkAtEnergy", base.drinkStaminaAtEnergy, "stamina.");
		base.useStamina = base.staminaType != StaminaType.NONE && base.staminaQty > 0;
	}

	private static GearSpec parseGear(Object raw, Supplier<List<GearItem>> worn, String field)
	{
		if (raw instanceof String)
		{
			String text = ((String) raw).trim();
			if ("worn".equalsIgnoreCase(text))
			{
				return new GearSpec("", worn == null ? null : worn.get());
			}
			if (text.isEmpty() || "keep".equalsIgnoreCase(text))
			{
				return new GearSpec();
			}
			throw new IllegalArgumentException(field + " must be \"worn\", {\"setup\":\"name\"} or {\"items\":[...]}");
		}
		if (!(raw instanceof Map))
		{
			throw new IllegalArgumentException(field + " must be \"worn\", {\"setup\":\"name\"} or {\"items\":[...]}");
		}
		Map<?, ?> map = (Map<?, ?>) raw;
		if (map.get("setup") != null)
		{
			return new GearSpec(String.valueOf(map.get("setup")), null);
		}
		if (map.get("items") instanceof List)
		{
			List<GearItem> items = new ArrayList<>();
			for (Object entry : (List<?>) map.get("items"))
			{
				if (!(entry instanceof Map))
				{
					throw new IllegalArgumentException(field + ".items entries must be objects like {\"id\":1163,\"slot\":\"head\"}");
				}
				Map<?, ?> row = (Map<?, ?>) entry;
				GearItem item = new GearItem();
				item.id = intValue(row, "id", -1, field + ".");
				item.name = row.get("name") == null ? "" : String.valueOf(row.get("name")).trim();
				item.slot = row.get("slot") == null ? "" : String.valueOf(row.get("slot")).trim().toLowerCase(Locale.ROOT);
				item.qty = intValue(row, "qty", 1, field + ".");
				if (item.id <= 0 && item.name.isEmpty())
				{
					throw new IllegalArgumentException(field + ".items entries need an id or a name");
				}
				if (!item.slot.isEmpty() && !SLOT_NAMES.contains(item.slot))
				{
					throw new IllegalArgumentException(field + ".items.slot must be one of " + SLOT_NAMES);
				}
				items.add(item);
			}
			return new GearSpec("", items);
		}
		throw new IllegalArgumentException(field + " needs \"setup\" or \"items\"");
	}

	/** The style of a weapon, judged from its name; melee when nothing says otherwise. */
	public static Style styleOfWeapon(String weaponName)
	{
		if (weaponName == null)
		{
			return Style.MELEE;
		}
		String name = weaponName.toLowerCase(Locale.ROOT);
		if (name.contains("bladed staff") || name.contains("polestaff") || name.contains("staff of balance"))
		{
			return Style.MELEE;
		}
		String[] rangedWords = {"bow", "dart", "knife", "knives", "throwing axe", "thrownaxe", "javelin", "chinchompa",
			"blowpipe", "ballista", "sling", "boomerang"};
		for (String word : rangedWords)
		{
			if (name.contains(word))
			{
				return Style.RANGED;
			}
		}
		String[] magicWords = {"staff", "wand", "sceptre", "trident", "sanguinesti", "shadow", "kodai"};
		for (String word : magicWords)
		{
			if (name.contains(word))
			{
				return Style.MAGIC;
			}
		}
		return Style.MELEE;
	}

	private static final Pattern FORBID_VERB = Pattern.compile(
		"(?:immune to|cannot use|can't use|can not use|unaffected by|not affected by|"
			+ "cannot be (?:hit|damaged|harmed) (?:by|with)|(?:do|does)(?:n't| not) work (?:on|against)?)"
			+ "\\s+(?:any\\s+)?(magic|spells?|ranged?|missiles?|melee)");
	private static final Pattern FORBID_NOUN = Pattern.compile(
		"(magic|ranged?|melee)\\s+(?:attacks?\\s+)?(?:(?:do|does)(?:n't| not) work|(?:is|are) (?:ineffective|useless))");

	/** Styles the given quest or step text says do not work. */
	public static Set<Style> forbiddenStyles(List<String> texts)
	{
		Set<Style> out = EnumSet.noneOf(Style.class);
		if (texts == null)
		{
			return out;
		}
		for (String line : texts)
		{
			if (line == null)
			{
				continue;
			}
			String text = line.toLowerCase(Locale.ROOT);
			for (Pattern pattern : new Pattern[]{FORBID_VERB, FORBID_NOUN})
			{
				Matcher matcher = pattern.matcher(text);
				while (matcher.find())
				{
					out.add(styleWord(matcher.group(1)));
				}
			}
		}
		return out;
	}

	private static Style styleWord(String word)
	{
		if (word.startsWith("magic") || word.startsWith("spell"))
		{
			return Style.MAGIC;
		}
		if (word.startsWith("range") || word.startsWith("missile"))
		{
			return Style.RANGED;
		}
		return Style.MELEE;
	}

	/**
	 * The style a fight starts with: the global choice unless the restriction forbids it, then the first allowed of
	 * melee, ranged, magic. Auto stays Auto (it is judged against the worn weapon later).
	 */
	public static Style defaultStyle(Style global, List<String> restrictionTexts)
	{
		Style preferred = global == null ? Style.AUTO : global;
		if (preferred == Style.AUTO)
		{
			return Style.AUTO;
		}
		Set<Style> forbidden = forbiddenStyles(restrictionTexts);
		if (!forbidden.contains(preferred))
		{
			return preferred;
		}
		for (Style candidate : new Style[]{Style.MELEE, Style.RANGED, Style.MAGIC})
		{
			if (!forbidden.contains(candidate))
			{
				return candidate;
			}
		}
		return preferred;
	}

	/** True when the quest's combat notes say magic does not work against its fights. */
	public static boolean mentionsMagicImmunity(List<String> combatRequirements)
	{
		if (combatRequirements == null)
		{
			return false;
		}
		for (String line : combatRequirements)
		{
			if (line == null)
			{
				continue;
			}
			String text = line.toLowerCase(Locale.ROOT);
			if (text.contains("magic is ineffective"))
			{
				return true;
			}
		}
		return forbiddenStyles(combatRequirements).contains(Style.MAGIC);
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private static int intValue(Map<?, ?> map, String key, int fallback, String prefix)
	{
		Object value = map.get(key);
		if (value == null)
		{
			return fallback;
		}
		if (value instanceof Number)
		{
			return ((Number) value).intValue();
		}
		try
		{
			return Integer.parseInt(String.valueOf(value).trim());
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException(prefix + key + " must be a number");
		}
	}

	private static <E extends Enum<E>> E enumValue(Map<?, ?> map, String key, Class<E> type, E fallback, String prefix)
	{
		Object value = map.get(key);
		if (value == null)
		{
			return fallback;
		}
		String wanted = String.valueOf(value).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		for (E constant : type.getEnumConstants())
		{
			if (constant.name().equals(wanted))
			{
				return constant;
			}
		}
		throw new IllegalArgumentException(prefix + key + " must be one of "
			+ Arrays.toString(type.getEnumConstants()).toLowerCase(Locale.ROOT));
	}

	private static final class Collections1
	{
		static Map<String, Object> single(String key, Object value)
		{
			Map<String, Object> map = new LinkedHashMap<>();
			map.put(key, value);
			return map;
		}
	}
}
