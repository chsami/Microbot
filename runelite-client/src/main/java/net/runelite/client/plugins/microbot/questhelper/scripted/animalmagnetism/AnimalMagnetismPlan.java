package net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.questhelper.QuestPreflight;
import net.runelite.client.plugins.microbot.questhelper.collections.ItemCollections;

/**
 * What to do next in Animal Magnetism, decided from the quest varbit and what the player holds, and nothing else. Pure:
 * no game access, so every stage and every restart point is unit-tested. The executor performs the chosen action.
 */
public final class AnimalMagnetismPlan
{
	public static final int VARBIT = 3185;
	public static final int DONE_AT = 240;

	// ---- the numbers of the run record ----
	public static final int CHICKENS = 2;
	public static final int TOKENS_PER_CHICKEN = 10;
	/** Tokens in hand for the chickens; the Port Phasmatys entry toll comes on top while Ghosts Ahoy is not done. */
	public static final int CHICKEN_TOKENS = CHICKENS * TOKENS_PER_CHICKEN;
	public static final int ENTRY_TOLL = 2;
	/** Eat when the hitpoints fall below this while chopping the undead trees. */
	public static final int EAT_BELOW_HP = 25;
	public static final int LOBSTERS = 6;
	public static final int IRON_BARS = 5;

	// ---- places ----
	public static final WorldPoint TREES = new WorldPoint(3107, 3344, 0);
	public static final WorldPoint AWAY_FROM_TREES = new WorldPoint(3109, 3356, 0);
	public static final WorldPoint OUTSIDE_MANOR = new WorldPoint(3109, 3351, 0);
	public static final WorldPoint LIBRARY = new WorldPoint(3099, 3359, 0);
	public static final WorldPoint AVA_ROOM = new WorldPoint(3094, 3357, 0);
	public static final WorldPoint MALCOLM = new WorldPoint(3618, 3526, 0);
	public static final WorldPoint ALICE = new WorldPoint(3627, 3526, 0);
	public static final WorldPoint CRONE = new WorldPoint(3461, 3558, 0);
	public static final WorldPoint WITCH = new WorldPoint(3099, 3370, 0);
	public static final WorldPoint IRON_MINE_STAND = new WorldPoint(2978, 3241, 0);
	public static final WorldPoint IRON_MINE_FACE_NORTH = new WorldPoint(2978, 3242, 0);
	public static final WorldPoint TUREAL = new WorldPoint(2931, 3536, 0);
	/** The tree area counts as "near" within this many tiles of {@link #TREES}. */
	public static final int NEAR_TREES_TILES = 9;
	public static final int BURTHORPE_TILES = 45;

	// ---- ids ----
	public static final int AVA_NPC = 4407;
	public static final int[] MALCOLM_NPCS = {4411, 4412};
	public static final int ALICE_NPC = 504;
	public static final int CRONE_NPC = 2996;
	public static final int WITCH_NPC = 4409;
	public static final int[] TUREAL_NPCS = {13433, 13434, 13560, 13618, 13619};
	public static final int UNDEAD_TREE = 4417;
	public static final int[] BOOKCASES = {155, 156};
	public static final int GHOSTSPEAK = ItemID.AMULET_OF_GHOSTSPEAK;
	public static final int ECTO_TOKEN = ItemID.ECTOTOKEN;
	public static final int CHICKEN = ItemID.ANMA_CHICKEN_SACK_FULL;
	public static final int IRON_BAR = ItemID.IRON_BAR;
	public static final int SELECTED_IRON = ItemID.ANMA_IRON_BAR;
	public static final int BAR_MAGNET = ItemID.ANMA_MAGNET;
	public static final int HAMMER = ItemID.HAMMER;
	public static final int MITHRIL_AXE = ItemID.MITHRIL_AXE;
	public static final int HOLY_SYMBOL = ItemID.BLESSEDSTAR;
	public static final int BLESSED_AXE = ItemID.ANMA_AXE;
	public static final int TWIGS = ItemID.ANMA_WOOD;
	public static final int RESEARCH_NOTES = ItemID.RESEARCH_NOTES;
	public static final int PATTERN = ItemID.ANMA_PATTERN;
	public static final int CONTAINER = ItemID.ANMA_CONTAINER;
	public static final int HARD_LEATHER = ItemID.HARD_LEATHER;
	public static final int POLISHED_BUTTONS = ItemID.ANMA_P_BUTTONS;
	public static final int LOBSTER = ItemID.LOBSTER;
	/** The "Draynor manor teleport" tablet. */
	public static final int DRAYNOR_MANOR_TAB = ItemID.TELETAB_DRAYNOR;
	/** A Games necklace of any charge; the preflight buys the fullest one, (8), never (1). */
	public static final List<Integer> GAMES_NECKLACES = ItemCollections.GAMES_NECKLACES.getItems();

	// ---- the translation puzzle ----
	public static final int TRANSLATION_GROUP = 480;
	public static final int[] TRANSLATION_CLICKS = {26, 31, 34, 40, 43, 46};

	/** The quest stages the plan is keyed by, ascending. */
	private static final int[] STAGES = {0, 10, 20, 30, 40, 50, 60, 70, 73, 76, 80, 90, 100, 110, 120, 130, 140, 150,
		160, 170, 180, 190, 200, 210, 220, 230, DONE_AT};

	private AnimalMagnetismPlan()
	{
	}

	/** One thing to do. */
	public enum Action
	{
		FINISH,
		WEAR_GHOSTSPEAK,
		EAT,
		RESUPPLY,
		GET_TOKENS,
		BUY_CHICKENS,
		TALK_AVA,
		TALK_MALCOLM,
		TALK_ALICE,
		TALK_CRONE,
		TALK_WITCH,
		MAKE_MAGNET,
		LEAVE_MANOR,
		GO_TO_TREES,
		CHOP_ONCE,
		LEAVE_TREES,
		TELEPORT_BURTHORPE,
		UNEQUIP_FOR_TURAEL,
		TALK_TUREAL,
		CHOP_FOR_TWIGS,
		TRANSLATE_NOTES,
		CLICK_TRANSLATION,
		COMBINE_LEATHER,
		COMBINE_BUTTONS
	}

	/** How to get out of the manor without its front doors, which never open from inside. */
	public enum Exit
	{
		TAB,
		NECKLACE,
		NONE
	}

	/** Where the player stands, as far as the plan cares. INSIDE_MANOR is the building and the tile the trees are left at. */
	public enum Place
	{
		INSIDE_MANOR,
		NEAR_TREES,
		BURTHORPE,
		ELSEWHERE
	}

	/** What the player holds and where they are. */
	public static final class Snapshot
	{
		private final Map<Integer, Integer> inventory = new HashMap<>();
		private final Set<Integer> worn = new HashSet<>();
		private boolean ghostsAhoyDone;
		private boolean translationOpen;
		private int hitpoints = 99;
		private Place place = Place.ELSEWHERE;

		public static Snapshot empty()
		{
			return new Snapshot();
		}

		public Snapshot holding(int itemId, int quantity)
		{
			inventory.merge(itemId, quantity, Integer::sum);
			return this;
		}

		public Snapshot wearing(int itemId)
		{
			worn.add(itemId);
			return this;
		}

		public Snapshot ghostsAhoyDone(boolean done)
		{
			ghostsAhoyDone = done;
			return this;
		}

		public Snapshot translationOpen(boolean open)
		{
			translationOpen = open;
			return this;
		}

		public Snapshot hitpoints(int hp)
		{
			hitpoints = hp;
			return this;
		}

		public Snapshot at(Place where)
		{
			place = where;
			return this;
		}

		/** Items of this id in the pack. */
		public int count(int itemId)
		{
			return inventory.getOrDefault(itemId, 0);
		}

		/** Items of any of the entry's ids in the pack, plus worn ones. */
		public int count(Carry entry)
		{
			int total = 0;
			for (int id : entry.ids)
			{
				total += count(id) + (worn.contains(id) ? 1 : 0);
			}
			return total;
		}

		/** In the pack (a worn item does not count). */
		public boolean has(int itemId)
		{
			return count(itemId) > 0;
		}

		public boolean isWorn(int itemId)
		{
			return worn.contains(itemId);
		}

		public boolean ghostsAhoyDone()
		{
			return ghostsAhoyDone;
		}

		public boolean translationOpen()
		{
			return translationOpen;
		}

		public int hitpoints()
		{
			return hitpoints;
		}

		public Place place()
		{
			return place;
		}

		/** What a stall check watches: the stage plus every item held. A change means the quest moved. */
		public long progress(int varbit)
		{
			long hash = varbit;
			for (Map.Entry<Integer, Integer> entry : new TreeMap<>(inventory).entrySet())
			{
				hash = hash * 31 + entry.getKey() * 7L + entry.getValue();
			}
			return hash;
		}
	}

	/** An item to hold: any of {@code ids}, at least {@code min} of them, topped up to {@code target}. */
	public static final class Carry
	{
		public final String name;
		public final List<Integer> ids;
		public final int min;
		public final int target;
		public final boolean consumed;

		Carry(String name, List<Integer> ids, int min, int target, boolean consumed)
		{
			this.name = name;
			this.ids = ids;
			this.min = min;
			this.target = target;
			this.consumed = consumed;
		}

		public QuestPreflight.Need toNeed()
		{
			return new QuestPreflight.Need(name, ids, target, consumed);
		}
	}

	private static Carry item(String name, int id, int min, int target, boolean consumed)
	{
		return new Carry(name, Collections.singletonList(id), min, target, consumed);
	}

	/** The stage the quest is in: the largest stage not above {@code varbit}. */
	static int stageOf(int varbit)
	{
		int stage = STAGES[0];
		for (int candidate : STAGES)
		{
			if (candidate <= varbit)
			{
				stage = candidate;
			}
		}
		return stage;
	}

	/** Ecto-tokens to hold before leaving for the chickens: 22 while Ghosts Ahoy is not done (the entry toll), else 20. */
	public static int tokensToHold(boolean ghostsAhoyDone)
	{
		return CHICKEN_TOKENS + (ghostsAhoyDone ? 0 : ENTRY_TOLL);
	}

	/** Whether more tokens must be earned before the remaining chickens can be bought. */
	public static boolean needsTokens(int tokens, int chickens)
	{
		return tokens < tokenGoal(chickens);
	}

	/** The tokens the Ectofuntus loop is asked to hold (the loop adds the entry toll itself). */
	public static int tokenGoal(int chickens)
	{
		return TOKENS_PER_CHICKEN * Math.max(0, CHICKENS - chickens);
	}

	/** The Morytania part: the farm, the crone and the chickens, before anything is carried to the manor. */
	static boolean inMorytaniaPhase(int varbit, int chickens)
	{
		return varbit < 100 || (varbit == 100 && chickens < CHICKENS);
	}

	/**
	 * Everything the rest of the quest needs from the bank, whatever the phase: what the preflight stocks and what the
	 * first bank visit after the chickens collects. Items the quest produces itself are not listed.
	 */
	public static List<Carry> shoppingList(int varbit, Snapshot held)
	{
		List<Carry> list = new ArrayList<>();
		if (varbit >= DONE_AT)
		{
			return list;
		}
		if (varbit <= 100)
		{
			list.add(item("Ghostspeak amulet", GHOSTSPEAK, 1, 1, false));
		}
		if (varbit <= 130)
		{
			list.add(item("Iron bar", IRON_BAR, IRON_BARS, IRON_BARS, true));
		}
		if (varbit <= 140)
		{
			list.add(item("Hammer", HAMMER, 1, 1, false));
		}
		if (varbit <= 170)
		{
			list.add(item("Mithril axe", MITHRIL_AXE, 1, 1, true));
			list.add(item("Holy symbol", HOLY_SYMBOL, 1, 1, false));
			list.add(new Carry("Games necklace", GAMES_NECKLACES, 1, 1, true));
		}
		int tabs = draynorTabs(varbit, held);
		if (tabs > 0)
		{
			list.add(item("Draynor manor teleport", DRAYNOR_MANOR_TAB, 1, tabs, true));
		}
		if (varbit <= 180 && !held.has(TWIGS))
		{
			list.add(item("Lobster", LOBSTER, 1, LOBSTERS, true));
		}
		if (varbit <= 210)
		{
			list.add(item("Hard leather", HARD_LEATHER, 1, 1, true));
			list.add(item("Polished buttons", POLISHED_BUTTONS, 1, 1, true));
		}
		return list;
	}

	/** Draynor manor teleports to hold: one to leave the manor at 150, one to come back at 180, one spare to leave again. */
	static int draynorTabs(int varbit, Snapshot held)
	{
		return varbit <= 150 || (varbit <= 180 && !held.has(TWIGS)) ? 2 : 0;
	}

	/** What to carry now: only the amulet in Morytania (the inventory is for the Ectofuntus), the whole list after. */
	public static List<Carry> carryList(int varbit, Snapshot held)
	{
		List<Carry> all = shoppingList(varbit, held);
		if (!inMorytaniaPhase(varbit, held.count(CHICKEN)))
		{
			return all;
		}
		List<Carry> morytania = new ArrayList<>();
		for (Carry entry : all)
		{
			if (entry.ids.contains(GHOSTSPEAK))
			{
				morytania.add(entry);
			}
		}
		return morytania;
	}

	/** The way out of the manor: a Draynor manor teleport, else the Games necklace, never the doors. */
	public static Exit exitRoute(Snapshot s)
	{
		if (s.has(DRAYNOR_MANOR_TAB))
		{
			return Exit.TAB;
		}
		for (int id : GAMES_NECKLACES)
		{
			if (s.has(id))
			{
				return Exit.NECKLACE;
			}
		}
		return Exit.NONE;
	}

	/**
	 * The least of an item that still lets the quest go on. A tablet is only a must where the player cannot walk on
	 * (inside the manor, or in Burthorpe); at the trees or outside it is spare, so breaking one never sends the bot
	 * back to the bank for another.
	 */
	private static int minimum(Carry entry, Snapshot held)
	{
		boolean tab = entry.ids.contains(DRAYNOR_MANOR_TAB);
		boolean mustHave = held.place() == Place.INSIDE_MANOR || held.place() == Place.BURTHORPE;
		return tab && !mustHave ? 0 : entry.min;
	}

	/** The next switch of the translation puzzle after {@code done} clicks, or -1 when the sequence is finished. */
	public static int nextTranslationClick(int done)
	{
		return done >= 0 && done < TRANSLATION_CLICKS.length ? TRANSLATION_CLICKS[done] : -1;
	}

	/** The carried items below their minimum. */
	public static List<Carry> missing(int varbit, Snapshot held)
	{
		List<Carry> out = new ArrayList<>();
		for (Carry entry : carryList(varbit, held))
		{
			if (held.count(entry) < minimum(entry, held))
			{
				out.add(entry);
			}
		}
		return out;
	}

	/** The dialogue options to pick, by text, in the conversation of this stage. Empty when only "continue" is pressed. */
	public static List<String> dialogueOptions(int varbit, int chickens)
	{
		switch (stageOf(varbit))
		{
			case 0:
				return Collections.singletonList("I would be happy to make your home a better place.");
			case 20:
			case 40:
			case 60:
				return Collections.singletonList("I'm here about a quest.");
			case 70:
			case 73:
				return Collections.singletonList("I'm here about the farmers east of here.");
			case 76:
				return Collections.singletonList("Okay, you need it more than I do, I suppose.");
			case 100:
				return chickens < CHICKENS
					? Arrays.asList("Could I buy those chickens now, then?", "Could I buy 1 chicken now?")
					: Collections.<String>emptyList();
			case 160:
			case 170:
				return turaelOptions(varbit);
			default:
				return Collections.emptyList();
		}
	}

	/** What to say to Turael: the first visit asks for the quest, the second for the axe. */
	public static List<String> turaelOptions(int varbit)
	{
		return stageOf(varbit) >= 170
			? Arrays.asList("Hello, I'm here about those trees again.", "I'd love one, thanks.")
			: Collections.singletonList("I'm here about a quest.");
	}

	/** The next action. */
	public static Action next(int varbit, Snapshot s)
	{
		if (varbit >= DONE_AT)
		{
			return Action.FINISH;
		}
		if (varbit <= 100 && s.has(GHOSTSPEAK) && !s.isWorn(GHOSTSPEAK))
		{
			return Action.WEAR_GHOSTSPEAK;
		}
		int stage = stageOf(varbit);
		boolean underAttack = stage == 150 || (stage == 180 && !s.has(TWIGS));
		if (underAttack && s.hitpoints() < EAT_BELOW_HP)
		{
			if (s.has(LOBSTER))
			{
				return Action.EAT;
			}
			if (s.place() == Place.NEAR_TREES)
			{
				return Action.LEAVE_TREES;
			}
		}
		if (!missing(varbit, s).isEmpty())
		{
			return Action.RESUPPLY;
		}
		return stageAction(stage, s);
	}

	private static Action stageAction(int stage, Snapshot s)
	{
		switch (stage)
		{
			case 10:
			case 30:
			case 50:
			case 76:
			case 80:
			case 90:
				return Action.TALK_MALCOLM;
			case 20:
			case 40:
			case 60:
				return Action.TALK_ALICE;
			case 70:
			case 73:
				return Action.TALK_CRONE;
			case 100:
				return atChickens(s);
			case 110:
				return s.count(CHICKEN) >= CHICKENS ? Action.TALK_AVA : Action.TALK_WITCH;
			case 120:
			case 130:
				return Action.TALK_WITCH;
			case 140:
				return atMagnet(s);
			case 150:
				return atFirstTree(s);
			case 160:
			case 170:
				return atTureal(s);
			case 180:
				return atTwigs(s);
			case 200:
				return atNotes(s);
			case 220:
				return atPattern(s);
			default:
				return Action.TALK_AVA;
		}
	}

	private static Action atChickens(Snapshot s)
	{
		if (s.count(CHICKEN) >= CHICKENS)
		{
			return Action.TALK_AVA;
		}
		return needsTokens(s.count(ECTO_TOKEN), s.count(CHICKEN)) ? Action.GET_TOKENS : Action.BUY_CHICKENS;
	}

	private static Action atMagnet(Snapshot s)
	{
		if (s.has(BAR_MAGNET))
		{
			return Action.TALK_AVA;
		}
		return s.has(SELECTED_IRON) ? Action.MAKE_MAGNET : Action.TALK_WITCH;
	}

	private static Action atFirstTree(Snapshot s)
	{
		if (s.place() == Place.INSIDE_MANOR)
		{
			return Action.LEAVE_MANOR;
		}
		return s.place() == Place.NEAR_TREES ? Action.CHOP_ONCE : Action.GO_TO_TREES;
	}

	private static Action atTureal(Snapshot s)
	{
		if (s.place() == Place.NEAR_TREES)
		{
			return Action.LEAVE_TREES;
		}
		if (s.place() != Place.BURTHORPE)
		{
			return Action.TELEPORT_BURTHORPE;
		}
		boolean worn = s.isWorn(MITHRIL_AXE) || s.isWorn(HOLY_SYMBOL);
		return worn ? Action.UNEQUIP_FOR_TURAEL : Action.TALK_TUREAL;
	}

	private static Action atTwigs(Snapshot s)
	{
		if (s.has(TWIGS))
		{
			return Action.TALK_AVA;
		}
		if (!s.has(BLESSED_AXE))
		{
			return s.place() == Place.BURTHORPE ? Action.TALK_TUREAL : Action.TELEPORT_BURTHORPE;
		}
		switch (s.place())
		{
			case INSIDE_MANOR:
				return Action.LEAVE_MANOR;
			case NEAR_TREES:
				return Action.CHOP_FOR_TWIGS;
			default:
				return Action.GO_TO_TREES;
		}
	}

	private static Action atNotes(Snapshot s)
	{
		if (s.translationOpen())
		{
			return Action.CLICK_TRANSLATION;
		}
		return s.has(RESEARCH_NOTES) ? Action.TRANSLATE_NOTES : Action.TALK_AVA;
	}

	private static Action atPattern(Snapshot s)
	{
		if (s.has(CONTAINER) || !s.has(PATTERN))
		{
			return Action.TALK_AVA;
		}
		if (s.has(HARD_LEATHER))
		{
			return Action.COMBINE_LEATHER;
		}
		return s.has(POLISHED_BUTTONS) ? Action.COMBINE_BUTTONS : Action.TALK_AVA;
	}
}
