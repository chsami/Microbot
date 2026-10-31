package net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism;

import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.questhelper.scripted.ScriptedQuestHost;
import net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism.AnimalMagnetismPlan.Carry;
import net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism.AnimalMagnetismPlan.Place;
import net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism.AnimalMagnetismPlan.Snapshot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.event.Level;

/**
 * The game side of the Animal Magnetism script: reads the player into a {@link Snapshot} and performs one action of
 * the plan per call. Every wait is bounded; a method returns false when it could not do what it set out to do.
 */
final class AnimalMagnetismActions
{
	private static final int WAIT_MS = 6_000;
	private static final int[] TRACKED_ITEMS = {
		AnimalMagnetismPlan.GHOSTSPEAK, AnimalMagnetismPlan.ECTO_TOKEN, AnimalMagnetismPlan.CHICKEN,
		AnimalMagnetismPlan.IRON_BAR, AnimalMagnetismPlan.SELECTED_IRON, AnimalMagnetismPlan.BAR_MAGNET,
		AnimalMagnetismPlan.HAMMER, AnimalMagnetismPlan.MITHRIL_AXE, AnimalMagnetismPlan.HOLY_SYMBOL,
		AnimalMagnetismPlan.BLESSED_AXE, AnimalMagnetismPlan.TWIGS, AnimalMagnetismPlan.RESEARCH_NOTES,
		AnimalMagnetismPlan.PATTERN, AnimalMagnetismPlan.CONTAINER, AnimalMagnetismPlan.HARD_LEATHER,
		AnimalMagnetismPlan.POLISHED_BUTTONS, AnimalMagnetismPlan.LOBSTER, AnimalMagnetismPlan.DRAYNOR_MANOR_TAB};

	private int translationClicks;
	private int patternResultId = -1;
	private boolean preflightRearmed;

	// ---- reading the game ----

	Snapshot snapshot()
	{
		Snapshot s = Snapshot.empty();
		for (int id : TRACKED_ITEMS)
		{
			s.holding(id, Rs2Inventory.itemQuantity(id));
		}
		for (int id : AnimalMagnetismPlan.GAMES_NECKLACES)
		{
			s.holding(id, Rs2Inventory.itemQuantity(id));
		}
		for (int id : new int[] {AnimalMagnetismPlan.GHOSTSPEAK, AnimalMagnetismPlan.MITHRIL_AXE,
			AnimalMagnetismPlan.HOLY_SYMBOL})
		{
			if (Rs2Equipment.isWearing(id))
			{
				s.wearing(id);
			}
		}
		boolean open = Rs2Widget.isWidgetVisible(AnimalMagnetismPlan.TRANSLATION_GROUP,
			AnimalMagnetismPlan.TRANSLATION_CLICKS[0]);
		if (!open)
		{
			translationClicks = 0;
		}
		return s.translationOpen(open)
			.ghostsAhoyDone(Rs2Player.getQuestState(Quest.GHOSTS_AHOY) == QuestState.FINISHED)
			.hitpoints(Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS))
			.at(placeOf(Rs2Player.getWorldLocation()));
	}

	static Place placeOf(WorldPoint me)
	{
		if (me == null)
		{
			return Place.ELSEWHERE;
		}
		if (insideManor(me))
		{
			return Place.INSIDE_MANOR;
		}
		if (me.getPlane() == 0 && me.distanceTo2D(AnimalMagnetismPlan.TREES) <= AnimalMagnetismPlan.NEAR_TREES_TILES)
		{
			return Place.NEAR_TREES;
		}
		boolean burthorpe = me.distanceTo2D(AnimalMagnetismPlan.TUREAL) <= AnimalMagnetismPlan.BURTHORPE_TILES;
		return burthorpe ? Place.BURTHORPE : Place.ELSEWHERE;
	}

	/** The manor's ground floor, the hidden room behind the library included. */
	static boolean insideManor(WorldPoint me)
	{
		return me.getPlane() == 0 && me.getX() >= 3084 && me.getX() <= 3128 && me.getY() >= 3354 && me.getY() <= 3382;
	}

	/** True when the dialogue is handled this tick; false hands a menu this script has no answer for to the generic code. */
	boolean answerDialogue(List<String> options)
	{
		if (Rs2Dialogue.hasQuestStartMenu())
		{
			Rs2Dialogue.acceptQuestStartMenu();
			return true;
		}
		if (Rs2Dialogue.hasSelectAnOption())
		{
			return !options.isEmpty() && Rs2Dialogue.clickOption(false, options.toArray(new String[0]));
		}
		if (Rs2Dialogue.hasContinue())
		{
			Rs2Dialogue.clickContinue();
			sleepGaussian(350, 90);
		}
		return true;
	}

	// ---- walking and talking ----

	private static boolean walk(WorldPoint where, int distance)
	{
		return Rs2Walker.walkWithState(where, distance) == WalkerState.ARRIVED;
	}

	private static int distanceTo(WorldPoint where)
	{
		WorldPoint me = Rs2Player.getWorldLocation();
		return me == null ? Integer.MAX_VALUE : me.distanceTo2D(where);
	}

	boolean talkTo(WorldPoint where, int... npcIds)
	{
		Rs2NpcModel npc = Microbot.getRs2NpcCache().query().withIds(npcIds).nearestOnClientThread();
		if (npc == null || distanceTo(npc.getWorldLocation()) > 8)
		{
			return walk(where, 3);
		}
		return npc.click("Talk-to") && sleepUntil(Rs2Dialogue::isInDialogue, WAIT_MS);
	}

	boolean talkToAva()
	{
		if (distanceTo(AnimalMagnetismPlan.AVA_ROOM) > 2)
		{
			return enterAvaRoom();
		}
		Rs2NpcModel ava = Microbot.getRs2NpcCache().query().withId(AnimalMagnetismPlan.AVA_NPC).nearestOnClientThread();
		return ava != null && ava.click("Talk-to") && sleepUntil(Rs2Dialogue::isInDialogue, WAIT_MS);
	}

	/** Through the library bookcase, searched directly: the walker livelocks in the library. */
	private boolean enterAvaRoom()
	{
		Rs2TileObjectModel bookcase = Microbot.getRs2TileObjectCache().query()
			.withIds(AnimalMagnetismPlan.BOOKCASES).nearestOnClientThread();
		if (bookcase == null || distanceTo(bookcase.getWorldLocation()) > 4)
		{
			return walk(AnimalMagnetismPlan.LIBRARY, 2);
		}
		return bookcase.click("Search") && sleepUntil(() -> distanceTo(AnimalMagnetismPlan.AVA_ROOM) <= 2, WAIT_MS);
	}

	// ---- small actions ----

	boolean wearGhostspeak()
	{
		return Rs2Inventory.interact(AnimalMagnetismPlan.GHOSTSPEAK, "Wear")
			&& sleepUntil(() -> Rs2Equipment.isWearing(AnimalMagnetismPlan.GHOSTSPEAK), WAIT_MS / 2);
	}

	boolean eat()
	{
		boolean ate = Rs2Inventory.interact(AnimalMagnetismPlan.LOBSTER, "Eat");
		sleepGaussian(700, 150);
		return ate;
	}

	boolean earnTokens(ScriptedQuestHost host, int chickens)
	{
		ScriptedQuestHost.Progress progress = host.earnEctoTokens(AnimalMagnetismPlan.tokenGoal(chickens));
		return progress != ScriptedQuestHost.Progress.FAILED;
	}

	boolean makeMagnet()
	{
		if (distanceTo(AnimalMagnetismPlan.IRON_MINE_STAND) > 0 && distanceTo(AnimalMagnetismPlan.IRON_MINE_FACE_NORTH) > 0)
		{
			return walk(AnimalMagnetismPlan.IRON_MINE_STAND, 0);
		}
		if (distanceTo(AnimalMagnetismPlan.IRON_MINE_FACE_NORTH) > 0)
		{
			// one tile north so the player faces north, which the hammer on the iron needs
			Rs2Walker.walkFastCanvas(AnimalMagnetismPlan.IRON_MINE_FACE_NORTH);
			return sleepUntil(() -> distanceTo(AnimalMagnetismPlan.IRON_MINE_FACE_NORTH) == 0, WAIT_MS / 2);
		}
		return Rs2Inventory.combine(AnimalMagnetismPlan.HAMMER, AnimalMagnetismPlan.SELECTED_IRON)
			&& sleepUntil(() -> Rs2Inventory.hasItem(AnimalMagnetismPlan.BAR_MAGNET), WAIT_MS);
	}

	boolean breakDraynorTab(boolean leavingManor)
	{
		if (!Rs2Inventory.interact(AnimalMagnetismPlan.DRAYNOR_MANOR_TAB, "Break"))
		{
			return false;
		}
		return sleepUntil(() -> leavingManor
			? !insideManor(Rs2Player.getWorldLocation())
			: distanceTo(AnimalMagnetismPlan.OUTSIDE_MANOR) <= 6, 2 * WAIT_MS);
	}

	boolean goToTrees()
	{
		boolean far = distanceTo(AnimalMagnetismPlan.OUTSIDE_MANOR) > 25;
		if (far && Rs2Inventory.hasItem(AnimalMagnetismPlan.DRAYNOR_MANOR_TAB))
		{
			return breakDraynorTab(false);
		}
		return walk(AnimalMagnetismPlan.OUTSIDE_MANOR, 4);
	}

	boolean chopOnce(int varbit)
	{
		return clickTree() && sleepUntil(() -> Microbot.getVarbitValue(AnimalMagnetismPlan.VARBIT) != varbit, WAIT_MS);
	}

	boolean chopForTwigs()
	{
		if (Rs2Player.isAnimating())
		{
			sleepGaussian(500, 120);
			return true;
		}
		return clickTree() && sleepUntil(() -> Rs2Player.isAnimating()
			|| Rs2Inventory.hasItem(AnimalMagnetismPlan.TWIGS), WAIT_MS);
	}

	private static boolean clickTree()
	{
		Rs2NpcModel tree = Microbot.getRs2NpcCache().query().withId(AnimalMagnetismPlan.UNDEAD_TREE)
			.nearestOnClientThread();
		if (tree == null)
		{
			return false;
		}
		String action = "Chop down";
		for (String candidate : tree.getAvailableActions())
		{
			if (candidate.toLowerCase().contains("chop"))
			{
				action = candidate;
				break;
			}
		}
		return tree.click(action);
	}

	boolean leaveTrees()
	{
		return walk(AnimalMagnetismPlan.AWAY_FROM_TREES, 2);
	}

	boolean rubNecklace()
	{
		for (int id : AnimalMagnetismPlan.GAMES_NECKLACES)
		{
			if (Rs2Inventory.hasItem(id))
			{
				return Rs2Inventory.interact(id, "Rub") && sleepUntil(Rs2Dialogue::hasSelectAnOption, WAIT_MS);
			}
		}
		return false;
	}

	boolean unequipForTureal()
	{
		int id = Rs2Equipment.isWearing(AnimalMagnetismPlan.MITHRIL_AXE)
			? AnimalMagnetismPlan.MITHRIL_AXE : AnimalMagnetismPlan.HOLY_SYMBOL;
		return Rs2Equipment.unEquip(id) && sleepUntil(() -> !Rs2Equipment.isWearing(id), WAIT_MS / 2);
	}

	boolean translateNotes()
	{
		translationClicks = 0;
		return Rs2Inventory.interact(AnimalMagnetismPlan.RESEARCH_NOTES, "Translate")
			&& sleepUntil(() -> Rs2Widget.isWidgetVisible(AnimalMagnetismPlan.TRANSLATION_GROUP,
				AnimalMagnetismPlan.TRANSLATION_CLICKS[0]), WAIT_MS);
	}

	/** One switch of the fixed answer per call. */
	boolean clickTranslation()
	{
		int[] clicks = AnimalMagnetismPlan.TRANSLATION_CLICKS;
		if (translationClicks >= clicks.length)
		{
			sleepGaussian(600, 150);
			return true;
		}
		boolean clicked = Rs2Widget.clickWidget(AnimalMagnetismPlan.TRANSLATION_GROUP, clicks[translationClicks]);
		if (clicked)
		{
			translationClicks++;
		}
		sleepGaussian(550, 130);
		return clicked;
	}

	boolean combineLeather()
	{
		Set<Integer> before = new HashSet<>();
		Rs2Inventory.items().forEach(item -> before.add(item.getId()));
		if (!Rs2Inventory.combine(AnimalMagnetismPlan.HARD_LEATHER, AnimalMagnetismPlan.PATTERN)
			|| !sleepUntil(() -> !Rs2Inventory.hasItem(AnimalMagnetismPlan.HARD_LEATHER), WAIT_MS))
		{
			return false;
		}
		for (Rs2ItemModel item : Rs2Inventory.all())
		{
			if (!before.contains(item.getId()))
			{
				patternResultId = item.getId();
			}
		}
		return true;
	}

	boolean combineButtons()
	{
		int target = patternResultId > 0 && Rs2Inventory.hasItem(patternResultId)
			? patternResultId : AnimalMagnetismPlan.PATTERN;
		return Rs2Inventory.combine(AnimalMagnetismPlan.POLISHED_BUTTONS, target)
			&& sleepUntil(() -> Rs2Inventory.hasItem(AnimalMagnetismPlan.CONTAINER), WAIT_MS);
	}

	// ---- banking ----

	/** One bank visit: tops up every carried item from the bank. Never the Port Phasmatys bank, which costs a toll. */
	boolean resupply(ScriptedQuestHost host, int varbit, Snapshot s)
	{
		if (Rs2Dialogue.isInDialogue())
		{
			return true;
		}
		if (!Rs2Bank.isOpen())
		{
			Me me = new Me(Rs2Player.getWorldLocation());
			BankLocation bank = me.inMorytania() ? BankLocation.CANIFIS : BankLocation.DRAYNOR_VILLAGE;
			return Rs2Bank.walkToBankAndUseBank(bank) && sleepUntil(Rs2Bank::isOpen, WAIT_MS);
		}
		boolean allFound = Rs2Bank.setWithdrawAsItem();
		for (Carry entry : AnimalMagnetismPlan.carryList(varbit, s))
		{
			allFound &= topUp(entry, s);
		}
		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), WAIT_MS / 2);
		if (!allFound && !preflightRearmed)
		{
			preflightRearmed = true;
			Microbot.log("AnimalMagnetism: the bank is short of items, asking the preflight to buy them", Level.INFO);
			host.rearmPreflight();
		}
		return true;
	}

	private boolean topUp(Carry entry, Snapshot s)
	{
		int want = entry.target - s.count(entry);
		for (int i = entry.ids.size() - 1; i >= 0 && want > 0; i--)
		{
			int id = entry.ids.get(i);
			int take = Math.min(want, Rs2Bank.count(id));
			if (take <= 0)
			{
				continue;
			}
			int before = Rs2Inventory.itemQuantity(id);
			Rs2Bank.withdrawX(id, take);
			sleepUntil(() -> Rs2Inventory.itemQuantity(id) > before, WAIT_MS / 2);
			want -= Rs2Inventory.itemQuantity(id) - before;
		}
		return want <= 0 || s.count(entry) >= entry.min;
	}

	void resupplyDone()
	{
		preflightRearmed = false;
	}

	/** Which side of the Holy barrier the player is on. */
	private static final class Me
	{
		private final WorldPoint at;

		Me(WorldPoint at)
		{
			this.at = at;
		}

		boolean inMorytania()
		{
			return at != null && at.getX() >= 3400 && at.getY() >= 3400;
		}
	}
}
