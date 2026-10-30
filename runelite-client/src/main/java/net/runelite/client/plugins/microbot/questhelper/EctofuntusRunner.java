package net.runelite.client.plugins.microbot.questhelper;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.EctofuntusPlan.Action;
import net.runelite.client.plugins.microbot.questhelper.EctofuntusPlan.State;
import net.runelite.client.plugins.microbot.questhelper.EctofuntusPlan.Where;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.event.Level;

/**
 * Earns Ecto-tokens at the Ectofuntus: grind bones to bonemeal upstairs, fill buckets at the pool of slime below,
 * worship, then collect the tokens from a ghost disciple. One game action per {@link #step}; the quest script's tick
 * calls it again and every decision is re-derived from the inventory ({@link EctofuntusPlan}). Each stage is bounded
 * by a count of ticks that brought no change, so it can never loop forever.
 *
 * Object ids and options: Ectofuntus 16648 "Worship"; loader 16654 (bones are used on it), bone grinder 16655
 * "Wind", bin 16656 "Empty"; pool of slime 17116-17119 (a bucket is used on it); ghost disciple npc 2988 "Talk-to".
 */
public final class EctofuntusRunner
{
	public enum Progress
	{
		WORKING,
		DONE,
		FAILED
	}

	public enum Fetch
	{
		/** This tick was spent fetching. */
		WORKED,
		/** Everything wanted is already carried. */
		NOTHING,
		/** Cannot be fetched (buying is off, or the purchase keeps failing); {@link Host#blockedReason()} says why. */
		BLOCKED
	}

	/** The quest script's side: gets inputs from the bank or the Grand Exchange, and wears the ghostspeak amulet. */
	public interface Host
	{
		/**
		 * @param trip item id to the total wanted on the player for this trip
		 * @param all  item id to the total wanted for every set still to do (what a purchase should cover)
		 */
		Fetch fetchInputs(Map<Integer, Integer> trip, Map<Integer, Integer> all);

		String blockedReason();

		/** True when the ghostspeak amulet is worn (or nothing can be done about it); otherwise acts and says false. */
		boolean ghostspeakReady();
	}

	private static final WorldPoint GRINDER_SPOT = new WorldPoint(3659, 3523, 1);
	private static final WorldPoint POOL_SPOT = new WorldPoint(3683, 9888, 0);
	private static final WorldPoint ALTAR_SPOT = new WorldPoint(3661, 3519, 0);
	private static final int[] POOL_IDS = {ObjectID.AHOY_GLOOPBUBBLES, ObjectID.AHOY_GLOOPBUBBLES_LARGE,
		ObjectID.AHOY_GLOOPBUBBLES_LARGER, ObjectID.AHOY_NEW_GREEN_FLOOR};
	private static final int[] ALTAR_IDS = {ObjectID.AHOY_ECTOFUNTUS, ObjectID.AHOY_ECTOFUNTUS_SMALL};

	/** Ticks in a row with no change before a stage is given up (walks get a few more). */
	static final int MAX_STALLS = 6;
	static final int MAX_WALK_STALLS = 8;
	/** Whole-run ceiling on ticks, in case progress keeps flickering. */
	static final int MAX_TICKS = 600;

	private final Host host;
	private final State state = new State();
	private int pending;
	private int worshipBase = -1;
	private boolean hopperLoaded;
	private int stalls;
	private int ticks;
	private int fetchTicks;
	private String lastSignature = "";
	private Action lastAction;
	private String failure;

	public EctofuntusRunner(Host host)
	{
		this.host = host;
	}

	public void reset()
	{
		state.fetchFailed = false;
		state.collectProbed = false;
		pending = 0;
		worshipBase = -1;
		hopperLoaded = false;
		stalls = 0;
		ticks = 0;
		fetchTicks = 0;
		lastSignature = "";
		lastAction = null;
		failure = null;
	}

	public String failure()
	{
		return failure;
	}

	/** Runs one action toward having {@code tokensNeeded} Ecto-tokens in the inventory. */
	public Progress step(int tokensNeeded)
	{
		if (++ticks > MAX_TICKS)
		{
			return fail("ran " + MAX_TICKS + " ticks without finishing");
		}
		int held = Rs2Inventory.itemQuantity(ItemID.ECTOTOKEN);
		readInventory(state);
		reconcileWorship();
		state.pending = pending;
		state.remaining = EctofuntusPlan.setsNeeded(tokensNeeded, held + pending * 5);
		state.maxSets = EctofuntusPlan.tripCapacity(Rs2Inventory.emptySlotCount(),
			state.bones + state.emptyPots + state.bonemeal + state.emptyBuckets + state.slime);
		state.where = locate();

		Action action = EctofuntusPlan.next(state);
		String signature = held + "/" + state.bones + "/" + state.emptyPots + "/" + state.bonemeal + "/"
			+ state.emptyBuckets + "/" + state.slime + "/" + pending + "/" + state.where + "/" + action;
		if (signature.equals(lastSignature))
		{
			stalls++;
		}
		else
		{
			stalls = 0;
			lastSignature = signature;
		}
		if (action != lastAction && lastAction == Action.WORSHIP)
		{
			worshipBase = -1;
		}
		if (action != Action.FETCH_INPUTS)
		{
			fetchTicks = 0;
		}
		if (action != Action.GRIND)
		{
			hopperLoaded = false;
		}
		lastAction = action;
		int limit = action == Action.GRIND || action == Action.FILL_BUCKETS || action == Action.WORSHIP
			? MAX_STALLS : MAX_WALK_STALLS;
		if (stalls > limit && action != Action.DONE)
		{
			return fail(action + " made no progress for " + stalls + " ticks");
		}

		switch (action)
		{
			case DONE:
				Microbot.log("Ectofuntus: " + held + " Ecto-tokens in hand", Level.INFO);
				return Progress.DONE;
			case FAIL_NO_INPUTS:
				return fail("no bones, pots or buckets to make the missing tokens with");
			case FETCH_INPUTS:
				return fetch();
			case WALK_TO_GRINDER:
				status("walking to the bone grinder");
				Rs2Walker.walkTo(GRINDER_SPOT, 3);
				break;
			case GRIND:
				grind();
				break;
			case WALK_TO_POOL:
				status("walking to the pool of slime");
				Rs2Walker.walkTo(POOL_SPOT, 2);
				break;
			case FILL_BUCKETS:
				fillBuckets();
				break;
			case WALK_TO_ALTAR:
				status("walking to the Ectofuntus");
				Rs2Walker.walkTo(ALTAR_SPOT, 3);
				break;
			case WORSHIP:
				worship();
				break;
			case COLLECT:
				if (state.where != Where.ALTAR)
				{
					status("walking to the ghost disciples");
					Rs2Walker.walkTo(ALTAR_SPOT, 3);
				}
				else
				{
					collect(held);
				}
				break;
			default:
				break;
		}
		return Progress.WORKING;
	}

	private Progress fetch()
	{
		if (++fetchTicks > 12)
		{
			fetchTicks = 0;
			state.fetchFailed = true;
			Microbot.log("Ectofuntus: could not get all the bones, pots and buckets; using what is carried", Level.WARN);
			return Progress.WORKING;
		}
		int[] trip = EctofuntusPlan.inputsWanted(state, Math.min(state.remaining, state.maxSets));
		int[] all = EctofuntusPlan.inputsWanted(state, state.remaining);
		status("getting bones, pots and buckets");
		switch (host.fetchInputs(totals(trip, state), totals(all, state)))
		{
			case BLOCKED:
				return fail(host.blockedReason());
			case NOTHING:
				state.fetchFailed = true;
				return Progress.WORKING;
			default:
				return Progress.WORKING;
		}
	}

	private static Map<Integer, Integer> totals(int[] wanted, State s)
	{
		Map<Integer, Integer> totals = new LinkedHashMap<>();
		totals.put(ItemID.BONES, Math.max(wanted[0], s.bones));
		totals.put(ItemID.POT_EMPTY, Math.max(wanted[1], s.emptyPots));
		totals.put(ItemID.BUCKET_EMPTY, Math.max(wanted[2], s.emptyBuckets));
		return totals;
	}

	private void grind()
	{
		status("grinding bones");
		int bonemealBefore = state.bonemeal;
		int bonesBefore = state.bones;
		if (!hopperLoaded)
		{
			if (Rs2Inventory.useItemOnObject(ItemID.BONES, ObjectID.AHOY_GRINDER_LOADER)
				&& sleepUntil(() -> Rs2Inventory.count(ItemID.BONES) < bonesBefore, 5_000))
			{
				hopperLoaded = true;
			}
			else
			{
				Rs2Inventory.deselect();
			}
			return;
		}
		// the loader runs the whole grind by itself; wait while bones or bonemeal keep changing
		if (sleepUntil(() -> Rs2Inventory.count(ItemID.BONES) != bonesBefore || countBonemeal() != bonemealBefore, 10_000))
		{
			return;
		}
		// nothing moved: turn the wheel and empty the bin by hand
		Microbot.log("Ectofuntus: grinder idle, winding and emptying by hand", Level.WARN);
		if (Rs2GameObject.interact(ObjectID.AHOY_GRINDER_HANDLE, "Wind"))
		{
			sleepUntil(() -> countBonemeal() != bonemealBefore, 4_000);
		}
		if (Rs2GameObject.interact(ObjectID.AHOY_GRINDER_CHUTE, "Empty"))
		{
			sleepUntil(() -> countBonemeal() != bonemealBefore, 4_000);
		}
		if (Rs2Inventory.count(ItemID.BONES) == bonesBefore)
		{
			hopperLoaded = false; // load it again next tick
		}
	}

	private void fillBuckets()
	{
		status("filling buckets with slime");
		int slimeBefore = state.slime;
		boolean used = false;
		for (int id : POOL_IDS)
		{
			if (Rs2Inventory.useItemOnObject(ItemID.BUCKET_EMPTY, id))
			{
				used = true;
				break;
			}
		}
		if (!used)
		{
			Rs2Inventory.deselect();
			return;
		}
		// the pool fills every bucket carried, one after another
		if (sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET_ECTOPLASM) > slimeBefore, 8_000))
		{
			int last = Rs2Inventory.count(ItemID.BUCKET_ECTOPLASM);
			while (Rs2Inventory.contains(ItemID.BUCKET_EMPTY))
			{
				final int seen = last;
				if (!sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET_ECTOPLASM) > seen, 4_000))
				{
					break;
				}
				last = Rs2Inventory.count(ItemID.BUCKET_ECTOPLASM);
			}
		}
	}

	private void worship()
	{
		status("worshipping the Ectofuntus");
		int sets = Math.min(countBonemeal(), Rs2Inventory.count(ItemID.BUCKET_ECTOPLASM));
		if (worshipBase < 0)
		{
			worshipBase = sets;
		}
		boolean clicked = false;
		for (int id : ALTAR_IDS)
		{
			if (Rs2GameObject.interact(id, "Worship"))
			{
				clicked = true;
				break;
			}
		}
		if (!clicked)
		{
			return;
		}
		// each worship uses one bonemeal and one slime; keep waiting while they keep being used up
		int last = sets;
		for (int i = 0; i < sets + 2; i++)
		{
			final int seen = last;
			if (!sleepUntil(() -> Math.min(countBonemeal(), Rs2Inventory.count(ItemID.BUCKET_ECTOPLASM)) < seen, 6_000))
			{
				break;
			}
			last = Math.min(countBonemeal(), Rs2Inventory.count(ItemID.BUCKET_ECTOPLASM));
			if (last <= 0)
			{
				break;
			}
		}
	}

	/** Counts the sets used since the last worship snapshot as tokens waiting with the disciples. */
	private void reconcileWorship()
	{
		if (worshipBase < 0)
		{
			return;
		}
		int now = Math.min(countBonemeal(), Rs2Inventory.count(ItemID.BUCKET_ECTOPLASM));
		if (now < worshipBase)
		{
			pending += worshipBase - now;
		}
		worshipBase = now;
	}

	private void collect(int heldBefore)
	{
		if (!host.ghostspeakReady())
		{
			return;
		}
		status("collecting Ecto-tokens from a ghost disciple");
		state.collectProbed = true;
		if (!Rs2Npc.interact(NpcID.AHOY_DISCIPLE, "Talk-to"))
		{
			return;
		}
		if (!sleepUntil(Rs2Dialogue::isInDialogue, 8_000))
		{
			return;
		}
		boolean optionsShown = false;
		for (int i = 0; i < 12 && Rs2Dialogue.isInDialogue(); i++)
		{
			if (Rs2Dialogue.hasContinue())
			{
				Rs2Dialogue.clickContinue();
				sleepUntil(() -> !Rs2Dialogue.hasContinue() || !Rs2Dialogue.isInDialogue(), 2_000);
			}
			else if (Rs2Dialogue.hasSelectAnOption())
			{
				// with nothing waiting the disciple only chats: leave
				optionsShown = true;
				if (!Rs2Dialogue.clickOption("Thanks for your time"))
				{
					break;
				}
				sleepUntil(() -> !Rs2Dialogue.hasSelectAnOption(), 2_000);
			}
			else
			{
				break;
			}
		}
		sleepUntil(() -> Rs2Inventory.itemQuantity(ItemID.ECTOTOKEN) > heldBefore, 1_500);
		if (Rs2Inventory.itemQuantity(ItemID.ECTOTOKEN) > heldBefore || optionsShown)
		{
			pending = 0;
		}
		else
		{
			Microbot.log("Ectofuntus: the disciple gave no tokens (is the ghostspeak amulet worn?)", Level.WARN);
		}
	}

	private Where locate()
	{
		WorldPoint p = Rs2Player.getWorldLocation();
		if (p == null)
		{
			return Where.ELSEWHERE;
		}
		if (p.distanceTo(GRINDER_SPOT) <= 7)
		{
			return Where.GRINDER;
		}
		if (p.distanceTo(POOL_SPOT) <= 6)
		{
			return Where.POOL;
		}
		if (p.distanceTo(ALTAR_SPOT) <= 6)
		{
			return Where.ALTAR;
		}
		return Where.ELSEWHERE;
	}

	private static void readInventory(State s)
	{
		s.bones = Rs2Inventory.count(ItemID.BONES);
		s.emptyPots = Rs2Inventory.count(ItemID.POT_EMPTY);
		s.bonemeal = countBonemeal();
		s.emptyBuckets = Rs2Inventory.count(ItemID.BUCKET_EMPTY);
		s.slime = Rs2Inventory.count(ItemID.BUCKET_ECTOPLASM);
	}

	/** Pots of ground bones of any kind (bonemeal, bat bonemeal, ...). */
	private static int countBonemeal()
	{
		return Rs2Inventory.count(item -> {
			String name = item.getName();
			return name != null && name.toLowerCase(Locale.ROOT).endsWith("bonemeal");
		});
	}

	private Progress fail(String reason)
	{
		failure = "Ectofuntus: " + reason;
		Microbot.log(failure, Level.WARN);
		return Progress.FAILED;
	}

	private static void status(String text)
	{
		Microbot.status = "Quest helper: " + text;
	}
}
