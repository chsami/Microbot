package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout.GearSpec;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.DetailedQuestStep;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.steps.ObjectStep;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.slf4j.event.Level;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Per-quest setup: asks once for a loadout (gear, food, potions, prayer, stamina, extra items, per-fight style and
 * gear), wears and stocks it, then eats, drinks and prays while fighting.
 * {@link #beginStep} and {@link #prepareForKill} run on the quest script thread, {@link #fightTick}
 * on its own short-period task so it keeps working while the script thread waits out a fight.
 */
public class QuestCombatHandler
{
	private static final int MAX_BANK_ATTEMPTS = 4;
	private static final int MAX_RESTOCKS = 3;
	private static final int MAX_GEAR_ATTEMPTS = 3;
	private static final long CONSUME_COOLDOWN_MS = 1800L;
	private static final long PRAYER_OFF_DELAY_MS = 4000L;
	private static final int MIN_RUN_ENERGY_TO_TOGGLE = 10;

	/** Bumped whenever a quest is started from the panel or the Agent Server, so the handler never reuses old state. */
	private static final AtomicInteger START_NONCE = new AtomicInteger();
	/** True while the setup dialog is open; read by the Agent Server status endpoint. */
	private static final AtomicBoolean AWAITING_SETUP = new AtomicBoolean(false);

	private enum Phase
	{
		UNRESOLVED, PROMPTING, ACTIVE, NONE
	}

	private final Supplier<QuestHelperConfig> config;
	private final Supplier<ScheduledFuture<?>> scheduler;
	private final Consumer<String> stopper;

	private volatile Phase phase = Phase.UNRESOLVED;
	private volatile String questKey;
	private volatile QuestCombatLoadout loadout;
	private volatile int generation;
	private int seenNonce = START_NONCE.get();
	private volatile int promptedGeneration = -1;
	private int promptsThisGeneration;
	/** True once reset() ran and nothing has started since, so repeat resets are no-ops. */
	private boolean pristine;
	private final AtomicReference<QuestCombatSetupDialog.Result> pendingAnswer = new AtomicReference<>();

	private boolean questHasFights;
	private boolean gearDone;
	private String wornSpecKey = "";
	private String attemptKey = "";
	private int specAttempts;
	private String currentFight;
	private boolean fightGearDone = true;
	private String styleAppliedFor;
	private boolean supplied;
	private int bankAttempts;
	private int restocks;
	private int defaultReserve = -1;
	private final Set<String> warned = new HashSet<>();

	private volatile boolean prayerOnByUs;
	private volatile long lastInCombatMs;
	private volatile long lastConsumeMs;
	private volatile long lastStaminaMs;
	private volatile WorldPoint lastSafeTile;
	private boolean recovering;
	/** Epoch ms when an escape from combat without food began; 0 when not escaping. */
	private volatile long escapeStartMs;
	private volatile long lastAntifireMs;
	private volatile boolean antifireSeenActive;
	private volatile long antifireDurationMs = 6 * 60_000L;
	private boolean defaultFoodTried;
	private boolean antifireTried;
	private int safeSpotFailures;
	private static final long ESCAPE_STOP_AFTER_MS = 15_000L;
	private static final int ESCAPE_STOP_HP_PERCENT = 20;
	private static final int ESCAPE_OBJECT_RANGE = 6;
	private static final int DEFAULT_FOOD_QTY = 5;
	private int recoveryBankAttempts;

	private volatile QuestHelper activeQuest;
	private volatile QuestStep activeStep;
	private volatile Set<Integer> protectedIds = Collections.emptySet();
	private volatile QuestStep protectedIdsStep;
	private volatile QuestHelper protectedIdsQuest;

	public QuestCombatHandler(Supplier<QuestHelperConfig> config, Supplier<ScheduledFuture<?>> scheduler,
		Consumer<String> stopper)
	{
		this.config = config;
		this.scheduler = scheduler;
		this.stopper = stopper;
	}

	/** Call when the user starts a quest (panel or Agent Server): the next tick forgets any earlier state. */
	public static void noteQuestStarted()
	{
		START_NONCE.incrementAndGet();
	}

	/** Counter bumped on every quest start; the preflight runs once per value. */
	public static int startNonce()
	{
		return START_NONCE.get();
	}

	/** True while the quest setup dialog is waiting for an answer. */
	public static boolean isAwaitingSetup()
	{
		return AWAITING_SETUP.get();
	}

	/** Forget the current quest's state; the next quest tick starts from scratch. */
	public synchronized void reset()
	{
		if (pristine)
		{
			return;
		}
		pristine = true;
		promptsThisGeneration = 0;
		generation++;
		questKey = null;
		loadout = null;
		phase = Phase.UNRESOLVED;
		pendingAnswer.set(null);
		if (AWAITING_SETUP.getAndSet(false))
		{
			QuestCombatSetupDialog.dismissOpen();
		}
		questHasFights = false;
		gearDone = false;
		wornSpecKey = "";
		attemptKey = "";
		specAttempts = 0;
		currentFight = null;
		fightGearDone = true;
		styleAppliedFor = null;
		supplied = false;
		bankAttempts = 0;
		restocks = 0;
		defaultReserve = -1;
		warned.clear();
		lastConsumeMs = 0;
		lastStaminaMs = 0;
		recovering = false;
		recoveryBankAttempts = 0;
		escapeStartMs = 0;
		lastAntifireMs = 0;
		antifireSeenActive = false;
		defaultFoodTried = false;
		antifireTried = false;
		safeSpotFailures = 0;
	}

	/**
	 * Resolves the quest's loadout (saved, or asked once) and wears the chosen gear.
	 *
	 * @return true when this tick was used and the script should not act further
	 */
	public boolean beginStep(QuestHelper quest)
	{
		int nonce = START_NONCE.get();
		if (nonce != seenNonce)
		{
			seenNonce = nonce;
			reset();
		}
		String key = quest.getQuest().name();
		if (!key.equals(questKey))
		{
			reset();
			synchronized (this)
			{
				questKey = key;
				pristine = false;
			}
		}
		activeQuest = quest;

		switch (phase)
		{
			case NONE:
				return false;
			case PROMPTING:
				return awaitAnswer(quest);
			case UNRESOLVED:
				return resolve(quest);
			default:
				break;
		}

		QuestCombatLoadout current = loadout;
		if (current == null || gearDone || Rs2Combat.inCombat())
		{
			return false;
		}
		GearSpec spec = current.mainGear();
		boolean acted = !spec.isEmpty() && wearSpec(spec);
		if (!acted)
		{
			gearDone = true;
		}
		return acted;
	}

	private boolean resolve(QuestHelper quest)
	{
		if (quest.getCurrentStep() == null)
		{
			return false;
		}
		questHasFights = QuestCombatDetector.hasFights(quest);

		QuestCombatLoadout saved = QuestCombatStore.load(questKey);
		if (saved != null)
		{
			loadout = saved;
			phase = Phase.ACTIVE;
			return false;
		}
		if (!config.get().askCombatSetup())
		{
			phase = Phase.NONE;
			return false;
		}

		phase = Phase.PROMPTING;
		return awaitAnswer(quest);
	}

	private boolean awaitAnswer(QuestHelper quest)
	{
		Microbot.status = "Quest helper: choose your quest setup in the dialog";
		QuestCombatSetupDialog.Result answer = pendingAnswer.getAndSet(null);
		if (answer == null)
		{
			final int askedIn;
			synchronized (this)
			{
				if (promptedGeneration == generation)
				{
					return true; // a dialog for this generation is already open or pending
				}
				promptedGeneration = generation;
				promptsThisGeneration++;
				askedIn = generation;
				AWAITING_SETUP.set(true);
			}
			QuestCombatSetupDialog.Context context = buildContext(quest);
			Microbot.log("Quest helper: waiting for quest setup for " + quest.getQuest().getName(), Level.INFO);
			QuestCombatSetupDialog.showAsync(context, result -> onDialogResult(askedIn, result));
			return true;
		}

		switch (answer.outcome)
		{
			case SAVE:
			case SKIP:
				QuestCombatStore.save(questKey, answer.loadout);
				loadout = answer.loadout;
				phase = Phase.ACTIVE;
				return false;
			default:
				stopper.accept("Quest setup cancelled for " + quest.getQuest().getName());
				phase = Phase.NONE;
				return true;
		}
	}

	/** What to do with a dialog result; pure so it can be tested. */
	enum AnswerAction
	{
		IGNORE, ACCEPT, STOP, REPROMPT
	}

	static AnswerAction decide(QuestCombatSetupDialog.Outcome outcome, int askedIn, int currentGeneration,
		int promptsThisGeneration)
	{
		if (askedIn != currentGeneration)
		{
			return AnswerAction.IGNORE;
		}
		switch (outcome)
		{
			case SAVE:
			case SKIP:
				return AnswerAction.ACCEPT;
			case CANCEL:
				return AnswerAction.STOP;
			default:
				return promptsThisGeneration <= 1 ? AnswerAction.REPROMPT : AnswerAction.STOP;
		}
	}

	private synchronized void onDialogResult(int askedIn, QuestCombatSetupDialog.Result result)
	{
		switch (decide(result.outcome, askedIn, generation, promptsThisGeneration))
		{
			case IGNORE:
				return;
			case REPROMPT:
				Microbot.log("Quest helper: setup dialog closed without an answer; asking again", Level.INFO);
				promptedGeneration = -1; // next tick prompts once more; AWAITING_SETUP stays true
				return;
			default:
				AWAITING_SETUP.set(false);
				pendingAnswer.set(result);
		}
	}

	private QuestCombatSetupDialog.Context buildContext(QuestHelper quest)
	{
		QuestCombatSetupDialog.Context context = new QuestCombatSetupDialog.Context();
		context.obtainMissingItems = config.get().obtainMissingItems().name();
		context.questName = quest.getQuest().getName();
		context.hasFights = questHasFights;
		List<String> notes = quest.getCombatRequirements();
		if (notes != null)
		{
			context.notes = new ArrayList<>(notes);
		}
		try
		{
			context.fights = QuestCombatDetector.enumerateFights(quest);
			context.defaultReserve = QuestCombatDetector.defaultReservedSlots(quest);
		}
		catch (RuntimeException e)
		{
			Microbot.log("Quest helper: could not list fights for the setup dialog: " + e.getMessage(), Level.WARN);
		}
		context.wornGear = wornGear();
		Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		context.wornWeapon = weapon == null || weapon.getName() == null ? "" : weapon.getName();

		Set<Integer> questItems = new HashSet<>();
		addIds(questItems, quest.getItemRequirements());
		List<QuestCombatLoadout.InventoryItem> snapshot = new ArrayList<>();
		for (Rs2ItemModel item : Rs2Inventory.items().collect(Collectors.toList()))
		{
			if (item.isNoted() || item.getName() == null || questItems.contains(item.getId()))
			{
				continue;
			}
			QuestCombatLoadout.InventoryItem existing = null;
			for (QuestCombatLoadout.InventoryItem row : snapshot)
			{
				if (row.getName().equalsIgnoreCase(item.getName()))
				{
					existing = row;
				}
			}
			if (existing == null)
			{
				snapshot.add(new QuestCombatLoadout.InventoryItem(item.getName(), item.getId(), item.getQuantity(),
					item.isStackable()));
			}
			else
			{
				existing.setQty(existing.getQty() + item.getQuantity());
			}
		}
		context.inventory = snapshot;
		return context;
	}

	/** What is worn right now, one entry per slot; used for "use what I'm wearing" and the Agent Server "worn" gear. */
	public static List<QuestCombatLoadout.GearItem> wornGear()
	{
		List<QuestCombatLoadout.GearItem> out = new ArrayList<>();
		List<Rs2ItemModel> worn = Rs2Equipment.items();
		if (worn == null)
		{
			return out;
		}
		for (Rs2ItemModel item : new ArrayList<>(worn))
		{
			if (item == null || item.getName() == null)
			{
				continue;
			}
			out.add(new QuestCombatLoadout.GearItem(item.getId(), item.getName(), slotName(item.getSlot()),
				Math.max(1, item.getQuantity())));
		}
		return out;
	}

	private static String slotName(int slot)
	{
		for (EquipmentInventorySlot candidate : EquipmentInventorySlot.values())
		{
			if (candidate.getSlotIdx() == slot)
			{
				return candidate.name().toLowerCase(java.util.Locale.ROOT);
			}
		}
		return "";
	}

	// ---- gear ----

	private volatile Set<String> questGearSlots = Collections.emptySet();
	private volatile QuestStep questGearSlotsStep;
	private volatile QuestHelper questGearSlotsQuest;

	/** Equipment slot name of a stored gear item: the recorded slot, else looked up from the item's stats. */
	private static String gearSlotOf(QuestCombatLoadout.GearItem item)
	{
		if (item.getSlot() != null && !item.getSlot().isEmpty())
		{
			return item.getSlot();
		}
		return slotNameOfItem(item.getId());
	}

	private static String slotNameOfItem(int itemId)
	{
		if (itemId <= 0)
		{
			return "";
		}
		try
		{
			net.runelite.client.game.ItemStats stats = Microbot.getItemManager().getItemStats(itemId);
			if (stats != null && stats.getEquipment() != null)
			{
				return slotName(stats.getEquipment().getSlot());
			}
		}
		catch (RuntimeException ignored)
		{
			// unknown slot: treated as no conflict
		}
		return "";
	}

	/**
	 * Equipment slots the quest needs worn: every must-be-equipped item requirement of the quest, the current step
	 * and the steps still ahead. Quest requirements always win over the saved combat gear.
	 */
	private Set<String> questGearSlots()
	{
		QuestHelper quest = activeQuest;
		QuestStep step = activeStep;
		if (quest == questGearSlotsQuest && step == questGearSlotsStep)
		{
			return questGearSlots;
		}
		Set<String> slots = new HashSet<>();
		try
		{
			List<ItemRequirement> requirements = new ArrayList<>();
			if (quest != null && quest.getItemRequirements() != null)
			{
				requirements.addAll(quest.getItemRequirements());
			}
			if (step instanceof DetailedQuestStep)
			{
				for (Requirement requirement : ((DetailedQuestStep) step).getRequirements())
				{
					if (requirement instanceof ItemRequirement)
					{
						requirements.add((ItemRequirement) requirement);
					}
				}
			}
			List<DetailedQuestStep> ahead = quest == null ? null : QuestCombatDetector.remainingDetailedSteps(quest);
			if (ahead != null)
			{
				for (DetailedQuestStep detailed : ahead)
				{
					for (Requirement requirement : detailed.getRequirements())
					{
						if (requirement instanceof ItemRequirement)
						{
							requirements.add((ItemRequirement) requirement);
						}
					}
				}
			}
			for (ItemRequirement requirement : requirements)
			{
				if (requirement == null || !requirement.mustBeEquipped())
				{
					continue;
				}
				for (Integer id : requirement.getAllIds())
				{
					String slot = id == null ? "" : slotNameOfItem(id);
					if (!slot.isEmpty())
					{
						slots.add(slot);
						break;
					}
				}
			}
		}
		catch (RuntimeException e)
		{
			Microbot.log("Quest helper: could not list the slots the quest needs worn: " + e.getMessage(), Level.WARN);
		}
		questGearSlots = slots;
		questGearSlotsQuest = quest;
		questGearSlotsStep = step;
		return slots;
	}


	private static String specKey(GearSpec spec)
	{
		if (!spec.getSetup().isEmpty())
		{
			return "setup:" + spec.getSetup().toLowerCase(java.util.Locale.ROOT);
		}
		StringBuilder key = new StringBuilder("items:");
		for (QuestCombatLoadout.GearItem item : spec.getItems())
		{
			key.append(item.getId() > 0 ? String.valueOf(item.getId()) : item.getName().toLowerCase(java.util.Locale.ROOT))
				.append(',');
		}
		return key.toString();
	}

	/**
	 * Gets the gear in {@code spec} onto the player, one step per call.
	 *
	 * @return true when this tick was used and the caller should come back next tick; false when it is worn or given up
	 */
	private boolean wearSpec(GearSpec spec)
	{
		String key = specKey(spec);
		if (key.equals(wornSpecKey))
		{
			return false;
		}
		if (!key.equals(attemptKey))
		{
			attemptKey = key;
			specAttempts = 0;
		}
		if (specAttempts >= MAX_GEAR_ATTEMPTS)
		{
			warnOnce("gear:" + key, "Quest helper: could not put on the chosen gear, continuing with what is worn");
			wornSpecKey = key;
			return false;
		}

		Set<String> reservedSlots = questGearSlots();
		if (!spec.getSetup().isEmpty() && !reservedSlots.isEmpty())
		{
			warnOnce("setup-slots:" + key, "Quest helper: the quest needs " + reservedSlots
				+ " worn, so the inventory setup '" + spec.getSetup() + "' is not applied");
			wornSpecKey = key;
			return false;
		}
		if (!spec.getSetup().isEmpty())
		{
			if (QuestCombatSetupDialog.setupNames().stream().noneMatch(name -> name.equalsIgnoreCase(spec.getSetup())))
			{
				warnOnce("setup:" + key, "Quest helper: inventory setup '" + spec.getSetup()
					+ "' not found, keeping current gear");
				wornSpecKey = key;
				return false;
			}
			Microbot.status = "Quest helper: wearing " + spec.getSetup();
			specAttempts++;
			if (new Rs2InventorySetup(spec.getSetup(), scheduler.get()).loadEquipment())
			{
				wornSpecKey = key;
			}
			return true;
		}

		List<QuestCombatLoadout.GearItem> missing = new ArrayList<>();
		for (QuestCombatLoadout.GearItem item : spec.getItems())
		{
			if (QuestFightSupport.slotConflicts(gearSlotOf(item), reservedSlots))
			{
				warnOnce("slot:" + item.getId() + item.getName(), "Quest helper: leaving the " + gearSlotOf(item)
					+ " slot alone, the quest needs it worn");
				continue;
			}
			if (!isWorn(item))
			{
				missing.add(item);
			}
		}
		if (missing.isEmpty())
		{
			wornSpecKey = key;
			return false;
		}

		Microbot.status = "Quest helper: putting on chosen gear";
		specAttempts++;
		List<QuestCombatLoadout.GearItem> inBank = new ArrayList<>();
		for (QuestCombatLoadout.GearItem item : missing)
		{
			if (inInventory(item))
			{
				Rs2ItemModel held = inventoryVariant(item);
				if (held != null)
				{
					Rs2Inventory.wear(held.getId());
				}
				else if (item.getId() > 0)
				{
					Rs2Inventory.wear(item.getId());
				}
				else
				{
					Rs2Inventory.wear(item.getName());
				}
				sleepUntil(() -> isWorn(item), 2_000);
			}
			else
			{
				inBank.add(item);
			}
		}
		if (inBank.isEmpty())
		{
			return true;
		}

		if (!Rs2Bank.isOpen())
		{
			Rs2Bank.walkToBankAndUseBank();
			if (!sleepUntil(Rs2Bank::isOpen, 15_000))
			{
				return true;
			}
		}
		boolean anyFetched = false;
		for (QuestCombatLoadout.GearItem item : inBank)
		{
			Rs2ItemModel variant = item.getName().isEmpty() ? null : bankVariant(item.getId(), item.getName());
			int available = variant != null ? variant.getQuantity()
				: (item.getId() > 0 ? Rs2Bank.count(item.getId()) : Rs2Bank.count(item.getName(), true));
			if (available <= 0)
			{
				warnOnce("bank:" + item.getId() + item.getName(), "Quest helper: "
					+ (item.getName().isEmpty() ? "item " + item.getId() : item.getName())
					+ " is not in the bank, continuing without it");
				continue;
			}
			int qty = Math.min(available, Math.max(1, item.getQty()));
			anyFetched = true;
			if (variant != null)
			{
				Rs2Bank.withdrawXAndEquip(variant.getId(), qty);
			}
			else if (item.getId() > 0)
			{
				Rs2Bank.withdrawXAndEquip(item.getId(), qty);
			}
			else
			{
				Rs2Bank.withdrawXAndEquip(item.getName(), qty);
			}
			sleepUntil(() -> isWorn(item), 3_000);
		}
		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
		if (!anyFetched)
		{
			wornSpecKey = key;
			return false;
		}
		return true;
	}

	private static boolean isWorn(QuestCombatLoadout.GearItem item)
	{
		if (item.getId() > 0 && Rs2Equipment.isWearing(item.getId()))
		{
			return true;
		}
		if (item.getName().isEmpty())
		{
			return item.getId() <= 0 && Rs2Equipment.isWearing(item.getName(), true);
		}
		// the same item at another charge counts as worn
		return Rs2Equipment.all().anyMatch(worn -> ChargedItemNames.sameFamily(worn.getName(), item.getName()));
	}

	private static boolean inInventory(QuestCombatLoadout.GearItem item)
	{
		if (item.getId() > 0 && Rs2Inventory.hasItem(item.getId()))
		{
			return true;
		}
		if (item.getName().isEmpty())
		{
			return item.getId() <= 0 && Rs2Inventory.hasItem(item.getName(), true);
		}
		return inventoryVariant(item) != null;
	}

	/** Exact id, else any charge/dose of the same item held in the inventory (highest first). */
	private static Rs2ItemModel inventoryVariant(QuestCombatLoadout.GearItem item)
	{
		return ChargedItemNames.pick(Rs2Inventory.items(i -> !i.isNoted()).collect(java.util.stream.Collectors.toList()),
			Rs2ItemModel::getId, Rs2ItemModel::getName, item.getId(), item.getName());
	}

	/** Exact id, else any charge/dose of the same item in the bank (highest first), else the uncharged base. */
	private static Rs2ItemModel bankVariant(int wantedId, String wantedName)
	{
		List<Rs2ItemModel> bank = Rs2Bank.bankItems();
		if (bank == null)
		{
			return null;
		}
		return ChargedItemNames.pick(bank.stream().filter(i -> i != null && !i.isNoted() && i.getQuantity() > 0)
				.collect(java.util.stream.Collectors.toList()),
			Rs2ItemModel::getId, Rs2ItemModel::getName, wantedId, wantedName);
	}

	private void warnOnce(String key, String message)
	{
		if (warned.add(key))
		{
			Microbot.log(message, Level.WARN);
		}
	}

	// ---- quest-wide supplies and per-fight preparation ----

	/**
	 * Quest-wide supply trip (stamina potions, custom items, and combat food in the same visit): runs once near the
	 * start of the quest, whether or not it has fights.
	 *
	 * @param reservedSlots free slots to leave for quest items the step still needs
	 * @return true when this tick was used and the script should not act further
	 */
	public boolean prepareQuestSupplies(int reservedSlots)
	{
		QuestCombatLoadout current = loadout;
		if (phase != Phase.ACTIVE || current == null || supplied || !current.wantsQuestWideSupplies()
			|| bankAttempts >= MAX_BANK_ATTEMPTS || Rs2Combat.inCombat())
		{
			return false;
		}
		return restock(current, reservedSlots);
	}

	/**
	 * Before a kill step: put on this fight's gear, set its style, and withdraw food and potions from the bank.
	 *
	 * @param step the active step; its target picks the per-fight gear and style
	 * @param reservedSlots free slots to leave for quest items the step still needs
	 * @return true when this tick was used and the script should not act further
	 */
	public boolean prepareForKill(QuestStep step, int reservedSlots)
	{
		QuestCombatLoadout current = loadout;
		if (phase == Phase.ACTIVE && current != null && !Rs2Combat.inCombat() && needsDefaultFood(current)
			&& fetchDefaultFood(step, reservedSlots, current))
		{
			return true;
		}
		if (phase != Phase.ACTIVE || current == null || current.isSkip() || Rs2Combat.inCombat()
			|| !QuestCombatDetector.hasFightsRemaining(activeQuest))
		{
			return false;
		}

		String fight = QuestCombatDetector.fightKeyOf(step);
		if (fight != null && !fight.equals(currentFight))
		{
			currentFight = fight;
			fightGearDone = false;
			styleAppliedFor = null;
		}

		if (!fightGearDone)
		{
			GearSpec spec = current.gearFor(currentFight);
			boolean acted = !spec.isEmpty() && wearSpec(spec);
			if (acted)
			{
				return true;
			}
			fightGearDone = true;
			styleAppliedFor = null;
		}

		String styleMarker = currentFight == null ? "" : currentFight;
		if (!styleMarker.equals(styleAppliedFor))
		{
			styleAppliedFor = styleMarker;
			applyStyle(current, step);
		}

		if (isDragonFight(step) && !antifireTried && antifireCount() == 0)
		{
			antifireTried = true;
			return restock(current, reservedSlots);
		}
		if (!needsSupplies(current))
		{
			return false;
		}
		return restock(current, reservedSlots);
	}

	/** True while the player is trying to get away from a fight with no food; QuestScript may read it. */
	public boolean shouldEscape()
	{
		return escapeStartMs > 0;
	}

	private void noteNoFoodInCombat(long now)
	{
		if (escapeStartMs == 0)
		{
			escapeStartMs = now;
		}
	}

	/** The step is an object step (ladder, stairs, door) whose object is close: the way out of a fight. */
	private static boolean stepTransportNearby(QuestStep step)
	{
		if (!(step instanceof ObjectStep) || ((ObjectStep) step).getDefinedPoint() == null)
		{
			return false;
		}
		WorldPoint target = ((ObjectStep) step).getDefinedPoint().getWorldPoint();
		WorldPoint here = Rs2Player.getWorldLocation();
		return target != null && here != null && target.getPlane() == here.getPlane()
			&& target.distanceTo(here) <= ESCAPE_OBJECT_RANGE;
	}

	private boolean needsDefaultFood(QuestCombatLoadout current)
	{
		if (defaultFoodTried || current.wantsFood() || hasEdibleFood() || bankAttempts >= MAX_BANK_ATTEMPTS)
		{
			return false;
		}
		QuestHelper quest = activeQuest;
		if (quest == null)
		{
			return false;
		}
		List<String> combatRequirements = quest.getCombatRequirements();
		return QuestCombatDetector.hasFightsRemaining(quest)
			|| (combatRequirements != null && !combatRequirements.isEmpty());
	}

	/** Setup is Skip or brings no food, yet fights lie ahead: still carry a small stack of the bank's best food. */
	private boolean fetchDefaultFood(QuestStep step, int reservedSlots, QuestCombatLoadout current)
	{
		defaultFoodTried = true;
		Microbot.status = "Quest helper: fetching emergency food";
		if (!Rs2Bank.isOpen())
		{
			Rs2Bank.walkToBankAndUseBank();
			if (!sleepUntil(Rs2Bank::isOpen, 15_000))
			{
				bankAttempts++;
				defaultFoodTried = false;
				return true;
			}
		}
		if (!Rs2Bank.setWithdrawAsItem())
		{
			Rs2Bank.closeBank();
			stopper.accept("Failed to set bank withdraw mode to Item. Toggle it manually and restart.");
			return true;
		}
		prepareBankInventory(activeQuest, step);
		String best = bestBankFood();
		if (best != null)
		{
			int reserved = Math.min(27, reservedSlots + reserveFor(current));
			withdraw(best, true, carry(DEFAULT_FOOD_QTY, 0, reserved));
		}
		else
		{
			warnOnce("defaultfood", "Quest helper: no food in the bank, continuing without any");
		}
		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
		return true;
	}

	/** Whether this kill step is against a dragon or the step text talks about dragonfire. */
	private static boolean isDragonFight(QuestStep step)
	{
		return step != null && QuestCombatDetector.isKillStep(step)
			&& QuestFightSupport.isDragonFight(QuestCombatDetector.fightKeyOf(step), step.getText());
	}

	/** Inventory antifire potions, best protection first then smallest dose; quest items excluded. */
	private List<Rs2ItemModel> antifireItems()
	{
		Set<Integer> keep = protectedItemIds();
		List<Rs2ItemModel> out = Rs2Inventory.items(i -> !i.isNoted() && !keep.contains(i.getId())
			&& QuestFightSupport.isAntifire(i.getName())).collect(Collectors.toList());
		out.sort(Comparator.<Rs2ItemModel>comparingInt(i -> antifireRank(i.getName()))
			.thenComparingInt(QuestCombatHandler::dose));
		return out;
	}

	private static int antifireRank(String itemName)
	{
		for (int i = 0; i < QuestFightSupport.ANTIFIRE_NAMES.size(); i++)
		{
			if (ChargedItemNames.sameFamily(QuestFightSupport.ANTIFIRE_NAMES.get(i), itemName))
			{
				return i;
			}
		}
		return QuestFightSupport.ANTIFIRE_NAMES.size();
	}

	private int antifireCount()
	{
		return antifireItems().size();
	}

	private void withdrawAntifire(int reserved)
	{
		if (antifireCount() > 0 || Rs2Inventory.emptySlotCount() - reserved < 1)
		{
			return;
		}
		List<Rs2ItemModel> bank = Rs2Bank.bankItems();
		if (bank != null)
		{
			List<Rs2ItemModel> usable = bank.stream().filter(i -> i != null && !i.isNoted() && i.getQuantity() > 0)
				.collect(Collectors.toList());
			for (String name : QuestFightSupport.ANTIFIRE_NAMES)
			{
				List<Rs2ItemModel> options = ChargedItemNames.ordered(usable, Rs2ItemModel::getId,
					Rs2ItemModel::getName, 0, name);
				if (!options.isEmpty())
				{
					Rs2Bank.withdrawX(options.get(0).getId(), 1);
					if (!sleepUntil(() -> antifireCount() > 0, 3_000))
					{
						Microbot.log("Quest helper: withdrawing antifire did not land in time", Level.WARN);
					}
					return;
				}
			}
		}
		warnOnce("antifire", "Quest helper: no antifire potion in the bank for the dragon fight");
	}

	/** Drinks antifire when a dragon fight starts and again when it wears off. Never touches quest items. */
	private void antifireTick(QuestStep step, long now)
	{
		if (!isDragonFight(step) || !Rs2Combat.inCombat() || Rs2Bank.isOpen()
			|| now - lastConsumeMs < CONSUME_COOLDOWN_MS)
		{
			return;
		}
		boolean active = Rs2Player.hasAntiFireActive();
		if (active)
		{
			antifireSeenActive = true;
		}
		if (!QuestFightSupport.antifireDue(lastAntifireMs, now, antifireDurationMs, antifireSeenActive, active))
		{
			return;
		}
		List<Rs2ItemModel> items = antifireItems();
		if (items.isEmpty())
		{
			return;
		}
		Rs2ItemModel potion = items.get(0);
		if (Rs2Inventory.interact(potion, "Drink"))
		{
			lastConsumeMs = now;
			lastAntifireMs = now;
			antifireSeenActive = false;
			antifireDurationMs = QuestFightSupport.antifireDurationMs(potion.getName());
		}
	}

	private QuestCombatLoadout.Style effectiveStyle(QuestCombatLoadout current, QuestStep step)
	{
		QuestCombatLoadout.Style style = current.styleFor(QuestCombatDetector.fightKeyOf(step));
		if (style == QuestCombatLoadout.Style.AUTO)
		{
			Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
			style = QuestCombatLoadout.styleOfWeapon(weapon == null ? null : weapon.getName());
		}
		return style;
	}

	/** Safespot tiles the kill step carries (addSafeSpots). */
	private static List<WorldPoint> safeSpotsOf(QuestStep step)
	{
		if (!(step instanceof DetailedQuestStep))
		{
			return Collections.emptyList();
		}
		List<WorldPoint> out = new ArrayList<>();
		for (net.runelite.client.plugins.microbot.questhelper.tools.QuestTile tile : new ArrayList<>(((DetailedQuestStep) step).getMarkedTiles()))
		{
			if (tile != null && tile.getWorldPoint() != null
				&& tile.getIconID() == net.runelite.api.gameval.SpriteID.SideIcons.COMBAT)
			{
				out.add(tile.getWorldPoint());
			}
		}
		return out;
	}

	/**
	 * Before attacking a kill step with a safespot while fighting ranged or magic: walk to the nearest safespot and
	 * stay on it. Melee ignores safespots.
	 *
	 * @return true when this tick walked toward the safespot and the script must skip the attack click
	 */
	public boolean positionForKill(QuestStep step)
	{
		QuestCombatLoadout current = loadout;
		if (phase != Phase.ACTIVE || current == null || current.isSkip() || !QuestCombatDetector.isKillStep(step)
			|| !QuestFightSupport.usesSafeSpot(effectiveStyle(current, step)) || safeSpotFailures >= 6)
		{
			return false;
		}
		WorldPoint here = Rs2Player.getWorldLocation();
		WorldPoint spot = QuestFightSupport.nearestSafeSpot(here, safeSpotsOf(step));
		if (spot == null || spot.equals(here))
		{
			safeSpotFailures = 0;
			return false;
		}
		Microbot.status = "Quest helper: moving to the safespot";
		if (!Rs2Walker.walkTo(spot, 0))
		{
			safeSpotFailures++;
		}
		return true;
	}

	private void applyStyle(QuestCombatLoadout current, QuestStep step)
	{
		QuestCombatLoadout.Style wanted = current.styleFor(currentFight);
		QuestCombatLoadout.Style style = wanted;
		if (wanted == QuestCombatLoadout.Style.AUTO)
		{
			Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
			style = QuestCombatLoadout.styleOfWeapon(weapon == null ? null : weapon.getName());
		}
		if (currentFight != null)
		{
			List<String> restriction = QuestCombatDetector.restrictionTextsFor(activeQuest, step, currentFight);
			if (QuestCombatLoadout.forbiddenStyles(restriction).contains(style))
			{
				warnOnce("style:" + currentFight, "Quest helper: " + style + " is said not to work against "
					+ currentFight + "; fighting with it anyway");
			}
		}
		if (style == QuestCombatLoadout.Style.MAGIC && !setAutocast())
		{
			Microbot.log("Quest helper: no castable combat spell found for autocast, "
				+ "set one manually", Level.WARN);
		}
	}

	private boolean needsSupplies(QuestCombatLoadout current)
	{
		if (!current.wantsFood() && !current.wantsPotions() && !current.wantsQuestWideSupplies())
		{
			return false;
		}
		if (!supplied)
		{
			return bankAttempts < MAX_BANK_ATTEMPTS;
		}
		boolean outOfFood = current.wantsFood() && foodCount(current) == 0;
		boolean outOfPotions = current.wantsPotions() && current.usesPrayer() && potionCount(current) == 0;
		return (outOfFood || outOfPotions) && restocks < MAX_RESTOCKS && bankAttempts < MAX_BANK_ATTEMPTS;
	}

	private int reserveFor(QuestCombatLoadout current)
	{
		int reserve = current.getReserveSlots();
		if (reserve < 0)
		{
			QuestHelper quest = activeQuest;
			if (defaultReserve < 0 && quest != null)
			{
				defaultReserve = QuestCombatDetector.defaultReservedSlots(quest);
			}
			reserve = Math.max(0, defaultReserve);
		}
		return reserve;
	}

	private boolean restock(QuestCombatLoadout current, int stepReserved)
	{
		Microbot.status = "Quest helper: fetching supplies";
		if (!Rs2Bank.isOpen())
		{
			Rs2Bank.walkToBankAndUseBank();
			if (!sleepUntil(Rs2Bank::isOpen, 15_000))
			{
				bankAttempts++;
				return true;
			}
		}

		if (!Rs2Bank.setWithdrawAsItem())
		{
			Rs2Bank.closeBank();
			stopper.accept("Failed to set bank withdraw mode to Item. Toggle it manually and restart.");
			return true;
		}

		boolean fights = QuestCombatDetector.hasFightsRemaining(activeQuest);
		prepareBankInventory(activeQuest, activeStep);

		int reserved = Math.min(27, stepReserved + reserveFor(current));

		if (current.wantsStamina())
		{
			int held = staminaCount(current);
			// with no fights left the free slots go to the quest items, so a single run potion is all that is kept
			int target = carry(fights ? current.getStaminaQty() : 1, held, reserved);
			withdrawPotion(current.getStaminaType().getItemName(), target - held);
		}
		if (!fights)
		{
			// food and combat potions are skipped, but the user's custom items are still carried (reserved slots respected)
			for (QuestCombatLoadout.InventoryItem item : current.getInventoryItems())
			{
				withdrawCustom(item, reserved);
			}
			Rs2Bank.closeBank();
			sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
			supplied = true;
			return true;
		}
		if (isDragonFight(activeStep))
		{
			withdrawAntifire(reserved);
		}
		if (current.wantsPotions())
		{
			int held = potionCount(current);
			int target = carry(current.getPotionQty(), held, reserved);
			withdrawPotion(current.getPotion().getItemName(), target - held);
		}
		for (QuestCombatLoadout.InventoryItem item : current.getInventoryItems())
		{
			withdrawCustom(item, reserved);
		}
		boolean wantFood = current.wantsFood();
		if (wantFood)
		{
			int held = foodCount(current);
			int target = carry(current.getFoodQty(), held, reserved);
			withdraw(current.getFood(), true, target - held);
		}

		boolean gotFood = !wantFood || foodCount(current) > 0;
		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);

		if (supplied)
		{
			restocks++;
		}
		supplied = true;
		if (!gotFood)
		{
			warnOnce("food:" + current.getFood(), "Quest helper: no " + current.getFood()
				+ " in the bank for the combat setup, continuing without food");
		}
		return true;
	}

	/** Withdraws {@code missing} of a potion, any dose, highest dose first. */
	private void withdrawPotion(String itemName, int missing)
	{
		if (missing <= 0)
		{
			return;
		}
		List<Rs2ItemModel> bank = Rs2Bank.bankItems();
		if (bank == null)
		{
			return;
		}
		List<Rs2ItemModel> options = ChargedItemNames.ordered(
			bank.stream().filter(i -> i != null && !i.isNoted() && i.getQuantity() > 0).collect(Collectors.toList()),
			Rs2ItemModel::getId, Rs2ItemModel::getName, 0, itemName);
		int remaining = missing;
		for (Rs2ItemModel option : options)
		{
			if (remaining <= 0)
			{
				break;
			}
			int amount = Math.min(remaining, option.getQuantity());
			int before = potionFamilyCount(itemName);
			Rs2Bank.withdrawX(option.getId(), amount);
			if (!sleepUntil(() -> potionFamilyCount(itemName) > before, 3_000))
			{
				Microbot.log("Quest helper: withdrawing " + itemName + " did not land in time", Level.WARN);
				return;
			}
			remaining -= Math.max(1, potionFamilyCount(itemName) - before);
		}
	}

	private static int potionFamilyCount(String itemName)
	{
		return Rs2Inventory.items(i -> !i.isNoted() && ChargedItemNames.sameFamily(i.getName(), itemName))
			.mapToInt(Rs2ItemModel::getQuantity).sum();
	}

	private static int invQuantity(QuestCombatLoadout.InventoryItem wanted)
	{
		return Rs2Inventory.items(i -> !i.isNoted() && (wanted.getId() > 0 && i.getId() == wanted.getId()
			|| !wanted.getName().isEmpty() && ChargedItemNames.sameFamily(i.getName(), wanted.getName())))
			.mapToInt(Rs2ItemModel::getQuantity).sum();
	}

	private void withdrawCustom(QuestCombatLoadout.InventoryItem wanted, int reserved)
	{
		String label = wanted.getName().isEmpty() ? "item " + wanted.getId() : wanted.getName();
		Rs2ItemModel inBank = null;
		inBank = bankVariant(wanted.getId(), wanted.getName());
		if (inBank == null)
		{
			warnOnce("custom:" + label, "Quest helper: " + label + " is not in the bank, continuing without it");
			return;
		}
		int held = invQuantity(wanted);
		int missing = wanted.getQty() - held;
		if (missing <= 0)
		{
			return;
		}
		boolean stackable = inBank.isStackable() || wanted.isStackable();
		int free = Math.max(0, Rs2Inventory.emptySlotCount() - reserved);
		int amount;
		if (stackable)
		{
			if (held == 0 && free < 1)
			{
				warnOnce("custom-space:" + label, "Quest helper: no free slot for " + label + " above the reserved slots");
				return;
			}
			amount = missing;
		}
		else
		{
			amount = Math.min(missing, free);
		}
		amount = Math.min(amount, inBank.getQuantity());
		if (amount <= 0)
		{
			warnOnce("custom-space:" + label, "Quest helper: no free slot for " + label + " above the reserved slots");
			return;
		}
		final int before = held;
		if (inBank.getId() > 0)
		{
			Rs2Bank.withdrawX(inBank.getId(), amount);
		}
		else
		{
			Rs2Bank.withdrawX(wanted.getName(), amount, true);
		}
		if (!sleepUntil(() -> invQuantity(wanted) > before, 3_000))
		{
			Microbot.log("Quest helper: withdrawing " + label + " did not land in time", Level.WARN);
		}
	}

	private static int carry(int wanted, int held, int reserved)
	{
		return Math.min(wanted, held + Math.max(0, Rs2Inventory.emptySlotCount() - reserved));
	}

	private void withdraw(String name, boolean exact, int amount)
	{
		int available = Rs2Bank.count(name, exact);
		int qty = Math.min(amount, available);
		if (qty <= 0)
		{
			return;
		}
		int before = Rs2Inventory.count(name, exact);
		Rs2Bank.withdrawX(name, qty, exact);
		if (!sleepUntil(() -> Rs2Inventory.count(name, exact) > before, 3_000))
		{
			Microbot.log("Quest helper: withdrawing " + name + " did not land in time", Level.WARN);
		}
	}

	private int foodCount(QuestCombatLoadout current)
	{
		return namedFood(current).stream().mapToInt(Rs2ItemModel::getQuantity).sum();
	}

	/** Inventory stacks of the loadout food that are not quest items. */
	private List<Rs2ItemModel> namedFood(QuestCombatLoadout current)
	{
		Set<Integer> keep = protectedItemIds();
		List<Rs2ItemModel> out = new ArrayList<>();
		for (Rs2ItemModel item : Rs2Inventory.items(i -> i.getName() != null && i.getName().equalsIgnoreCase(current.getFood())).collect(java.util.stream.Collectors.toList()))
		{
			if (!item.isNoted() && !keep.contains(item.getId()))
			{
				out.add(item);
			}
		}
		return out;
	}

	private int potionCount(QuestCombatLoadout current)
	{
		return Rs2Inventory.count(current.getPotion().getItemName(), false);
	}

	/** Names (without dose) of the potions that count as the loadout's run energy potion. */
	private static List<String> staminaNames(QuestCombatLoadout current)
	{
		if (current.getStaminaType() == QuestCombatLoadout.StaminaType.ENERGY)
		{
			return java.util.Arrays.asList("Energy potion", "Super energy");
		}
		return java.util.Arrays.asList("Stamina potion", "Energy potion", "Super energy");
	}

	/** Inventory stamina or energy potions of any dose that are not quest items. */
	private List<Rs2ItemModel> staminaItems(QuestCombatLoadout current)
	{
		Set<Integer> keep = protectedItemIds();
		List<String> names = staminaNames(current);
		List<Rs2ItemModel> out = new ArrayList<>();
		for (Rs2ItemModel item : Rs2Inventory.items(i -> !i.isNoted() && i.getName() != null).collect(Collectors.toList()))
		{
			String base = item.getName().replaceAll("\\s*\\(\\d+\\)$", "").trim();
			if (!keep.contains(item.getId()) && names.stream().anyMatch(base::equalsIgnoreCase))
			{
				out.add(item);
			}
		}
		return out;
	}

	private int staminaCount(QuestCombatLoadout current)
	{
		return staminaItems(current).size();
	}

	/** Drinks a stamina or energy potion when run energy is low; keeps run on. Never touches quest items. */
	private void staminaTick(QuestCombatLoadout current, long now)
	{
		if (!current.wantsStamina() || !Microbot.isLoggedIn() || Rs2Bank.isOpen())
		{
			return;
		}
		int energy = Rs2Player.getRunEnergy();
		if (energy > MIN_RUN_ENERGY_TO_TOGGLE && !Rs2Player.isRunEnabled())
		{
			Rs2Player.toggleRunEnergy(true);
		}
		if (energy > current.getDrinkStaminaAtEnergy() || now - lastStaminaMs < CONSUME_COOLDOWN_MS
			|| now - lastConsumeMs < 600L)
		{
			return;
		}
		if (current.getStaminaType() == QuestCombatLoadout.StaminaType.STAMINA && Rs2Player.hasStaminaActive())
		{
			return;
		}
		List<Rs2ItemModel> items = staminaItems(current);
		if (items.isEmpty())
		{
			return;
		}
		// finish the smallest dose first so part-used potions do not pile up
		items.sort(Comparator.comparingInt(QuestCombatHandler::dose));
		lastStaminaMs = now;
		Rs2Inventory.interact(items.get(0), "Drink");
	}

	private static int dose(Rs2ItemModel item)
	{
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\((\\d+)\\)$").matcher(item.getName());
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : 4;
	}

	private boolean setAutocast()
	{
		Rs2CombatSpells[] spells = Rs2CombatSpells.values();
		for (int i = spells.length - 1; i >= 0; i--)
		{
			if (Rs2Magic.canCast(spells[i].getMagicAction()))
			{
				return Rs2Combat.setAutoCastSpell(spells[i], false);
			}
		}
		return false;
	}


	private int eatThreshold()
	{
		QuestCombatLoadout current = loadout;
		return current != null && !current.isSkip() ? current.getEatAtPercent() : 50;
	}

	/** Edible inventory food that is not a quest item. */
	private List<Rs2ItemModel> edibleFood()
	{
		Set<Integer> keep = protectedItemIds();
		List<Rs2ItemModel> out = new ArrayList<>();
		for (Rs2ItemModel item : Rs2Inventory.getInventoryFood())
		{
			if (!keep.contains(item.getId()))
			{
				out.add(item);
			}
		}
		return out;
	}

	private boolean hasEdibleFood()
	{
		return !edibleFood().isEmpty();
	}

	/**
	 * Picks the item to eat: the highest-healing candidate (per {@link Rs2Food}) that is not protected, else the first
	 * unprotected one, else null.
	 */
	static <T> T pickEdible(List<T> candidates, ToIntFunction<T> idOf, ToIntFunction<T> healOf, Set<Integer> protectedIds)
	{
		T best = null;
		int bestHeal = -1;
		for (T candidate : candidates)
		{
			if (candidate == null || protectedIds.contains(idOf.applyAsInt(candidate)))
			{
				continue;
			}
			int heal = healOf.applyAsInt(candidate);
			if (best == null || heal > bestHeal)
			{
				best = candidate;
				bestHeal = heal;
			}
		}
		return best;
	}

	private static int healOf(Rs2ItemModel item)
	{
		for (Rs2Food food : Rs2Food.values())
		{
			if (food.getId() == item.getId())
			{
				return food.getHeal();
			}
		}
		return 0;
	}

	/** Eats the best non-quest inventory food (Drink for jugs of wine); never touches protected items. */
	private boolean eatBest()
	{
		Rs2ItemModel food = pickEdible(Rs2Inventory.getInventoryFood(), Rs2ItemModel::getId,
			QuestCombatHandler::healOf, protectedItemIds());
		if (food == null)
		{
			return false;
		}
		String action = food.getName() != null && food.getName().toLowerCase().contains("jug of wine") ? "drink" : "eat";
		return Rs2Inventory.interact(food, action);
	}

	/** Eats the loadout food if held, else the best non-quest food. */
	private boolean eatAnyFood(long now)
	{
		if (!hasEdibleFood())
		{
			return false;
		}
		if (now - lastConsumeMs < CONSUME_COOLDOWN_MS)
		{
			return true;
		}
		lastConsumeMs = now;
		QuestCombatLoadout current = loadout;
		if (current != null && !current.isSkip() && current.wantsFood())
		{
			List<Rs2ItemModel> named = namedFood(current);
			if (!named.isEmpty() && Rs2Inventory.interact(named.get(0), "Eat"))
			{
				return true;
			}
		}
		return eatBest();
	}

	/**
	 * Whether a bank visit should deposit an inventory item. Protected items (quest items, coins, kept run potions) are
	 * never deposited; supplies (setup food, potions, custom items) are kept only while a fight still lies ahead.
	 */
	/**
	 * Quest-level item requirements are protected only before the quest starts (the gathering phase) or when the
	 * remaining steps cannot be read; otherwise the current and remaining steps decide.
	 */
	static boolean protectsQuestLevelItems(boolean notStartedOrUnknown, boolean remainingKnown)
	{
		return notStartedOrUnknown || !remainingKnown;
	}

	/** Ids of the step's item requirements (alternates included) and its icon item. */
	private static Set<Integer> stepItemIds(QuestStep step)
	{
		Set<Integer> ids = new HashSet<>();
		if (step instanceof DetailedQuestStep)
		{
			for (Requirement requirement : ((DetailedQuestStep) step).getRequirements())
			{
				if (requirement instanceof ItemRequirement)
				{
					addIds(ids, Collections.singletonList((ItemRequirement) requirement));
				}
			}
		}
		if (step != null && step.getIconItemID() > 0)
		{
			ids.add(step.getIconItemID());
		}
		return ids;
	}

	/** Whether the bank prep deposits an item: never a protected one, and not a fight supply while fights remain. */
	static boolean shouldDeposit(boolean protectedItem, boolean supplyItem, boolean fightsRemaining)
	{
		return !protectedItem && !(fightsRemaining && supplyItem);
	}

	/**
	 * With the bank open for a quest reason, deposits everything the rest of the quest does not need so bulk
	 * withdrawals get the freed slots. With no fights left, setup food and potions go too, and when the setup asked for
	 * stamina one run potion is kept (or withdrawn) for the 600ms tick to drink. Equipment is never touched.
	 *
	 * @return true when anything was deposited
	 */
	public boolean prepareBankInventory(QuestHelper quest, QuestStep step)
	{
		if (quest == null || !Rs2Bank.isOpen())
		{
			return false;
		}
		activeQuest = quest;
		if (step != null)
		{
			activeStep = step;
		}
		boolean fights = QuestCombatDetector.hasFightsRemaining(quest);
		QuestCombatLoadout current = phase == Phase.ACTIVE ? loadout : null;
		boolean wantsRunPotion = current != null && current.wantsStamina();
		boolean lowHp = recovering || Rs2Player.getHealthPercentage() <= eatThreshold();

		Set<Integer> remaining = QuestCombatDetector.remainingStepItemIds(quest);
		QuestState state = Microbot.getClientThread().runOnClientThreadOptional(() -> quest.getState(Microbot.getClient()))
			.orElse(null);
		Set<Integer> keep = new HashSet<>();
		if (protectsQuestLevelItems(state == QuestState.NOT_STARTED || state == null, remaining != null))
		{
			keep.addAll(protectedItemIds());
		}
		else
		{
			keep.addAll(stepItemIds(activeStep));
		}
		keep.add(ItemID.COINS_995);
		if (remaining != null)
		{
			keep.addAll(remaining);
		}

		Predicate<Rs2ItemModel> deposit = item ->
		{
			boolean protectedItem = keep.contains(item.getId()) || (wantsRunPotion && isRunPotion(item))
				|| (lowHp && item.isFood()) || (current != null && isCustomItem(current, item));
			return shouldDeposit(protectedItem, current != null && isSupply(current, item), fights);
		};
		Microbot.status = "Quest helper: banking items the quest no longer needs";
		boolean deposited = Rs2Bank.depositAll(deposit);
		if (!fights && wantsRunPotion)
		{
			ensureRunPotion();
		}
		return deposited;
	}

	private static String baseName(Rs2ItemModel item)
	{
		return item.getName() == null ? "" : item.getName().replaceAll("\\s*\\(\\d+\\)$", "").trim();
	}

	private static boolean isRunPotion(Rs2ItemModel item)
	{
		if (item.isNoted())
		{
			return false;
		}
		String base = baseName(item);
		return base.equalsIgnoreCase("Stamina potion") || base.equalsIgnoreCase("Energy potion")
			|| base.equalsIgnoreCase("Super energy");
	}

	/** True for an item configured in the dialog's inventory tab; the bank prep never deposits these. */
	private static boolean isCustomItem(QuestCombatLoadout current, Rs2ItemModel item)
	{
		if (item.isNoted())
		{
			return false;
		}
		String name = item.getName();
		for (QuestCombatLoadout.InventoryItem wanted : current.getInventoryItems())
		{
			if (wanted.getId() > 0 ? item.getId() == wanted.getId() : name != null && name.equalsIgnoreCase(wanted.getName()))
			{
				return true;
			}
		}
		return false;
	}

	/** Setup food, potions and custom items: what a fight needs and the rest of the quest does not. */
	private static boolean isSupply(QuestCombatLoadout current, Rs2ItemModel item)
	{
		String name = item.getName();
		if (name == null || item.isNoted())
		{
			return false;
		}
		if (current.wantsFood() && name.equalsIgnoreCase(current.getFood()))
		{
			return true;
		}
		if (QuestFightSupport.isAntifire(name))
		{
			return true;
		}
		if (current.wantsPotions() && baseName(item).equalsIgnoreCase(current.getPotion().getItemName()))
		{
			return true;
		}
		for (QuestCombatLoadout.InventoryItem wanted : current.getInventoryItems())
		{
			if (wanted.getId() > 0 ? item.getId() == wanted.getId() : name.equalsIgnoreCase(wanted.getName()))
			{
				return true;
			}
		}
		return current.wantsStamina() && isRunPotion(item);
	}

	/** Keeps one run potion for a bulk trip: the one already held, else a single dose potion from the bank. */
	private void ensureRunPotion()
	{
		if (Rs2Inventory.items(QuestCombatHandler::isRunPotion).findAny().isPresent() || Rs2Inventory.isFull())
		{
			return;
		}
		if (!Rs2Bank.setWithdrawAsItem())
		{
			return;
		}
		Rs2ItemModel pick = null;
		for (String name : new String[]{"Stamina potion", "Energy potion", "Super energy"})
		{
			for (Rs2ItemModel item : Rs2Bank.bankItems())
			{
				if (item != null && !item.isNoted() && item.getName() != null && baseName(item).equalsIgnoreCase(name))
				{
					pick = item;
					break;
				}
			}
			if (pick != null)
			{
				break;
			}
		}
		if (pick == null)
		{
			warnOnce("runpotion", "Quest helper: no stamina or energy potion in the bank, continuing without one");
			return;
		}
		Rs2Bank.withdrawX(pick.getId(), 1);
		if (!sleepUntil(() -> Rs2Inventory.items(QuestCombatHandler::isRunPotion).findAny().isPresent(), 3_000))
		{
			Microbot.log("Quest helper: withdrawing a run potion did not land in time", Level.WARN);
		}
	}

	/**
	 * Ids of every item the quest needs (quest-level and active-step item requirements with their alternates, plus the
	 * step icon item). Recommended items are excluded on purpose: those are the normal food the player wants eaten.
	 * Cached per quest and step.
	 */
	private Set<Integer> protectedItemIds()
	{
		QuestHelper quest = activeQuest;
		QuestStep step = activeStep;
		if (quest == protectedIdsQuest && step == protectedIdsStep)
		{
			return protectedIds;
		}
		Set<Integer> ids = new HashSet<>();
		try
		{
			if (quest != null)
			{
				addIds(ids, quest.getItemRequirements());
			}
			QuestStep active = step;
			if (active instanceof DetailedQuestStep)
			{
				for (Requirement requirement : ((DetailedQuestStep) active).getRequirements())
				{
					if (requirement instanceof ItemRequirement)
					{
						addIds(ids, Collections.singletonList((ItemRequirement) requirement));
					}
				}
			}
			if (active != null && active.getIconItemID() > 0)
			{
				ids.add(active.getIconItemID());
			}
		}
		catch (RuntimeException e)
		{
			Microbot.log("Quest helper: could not list quest items to protect from eating: " + e.getMessage(), Level.WARN);
		}
		protectedIds = ids;
		protectedIdsQuest = quest;
		protectedIdsStep = step;
		return ids;
	}

	private static void addIds(Set<Integer> ids, List<ItemRequirement> requirements)
	{
		if (requirements == null)
		{
			return;
		}
		for (ItemRequirement requirement : requirements)
		{
			if (requirement == null)
			{
				continue;
			}
			for (Integer id : requirement.getAllIds())
			{
				if (id != null && id > 0)
				{
					ids.add(id);
				}
			}
		}
	}

	/**
	 * Low-HP safety net that works with or without a loadout. With food it eats; with none and out of combat it
	 * banks for food and eats back up before the quest resumes.
	 *
	 * @return true when this tick was used and quest steps should pause
	 */
	public boolean recoverHealth(QuestStep step, int reservedSlots)
	{
		activeStep = step;
		if (!Microbot.isLoggedIn())
		{
			return false;
		}
		int threshold = eatThreshold();
		double hp = Rs2Player.getHealthPercentage();
		boolean hasFood = hasEdibleFood();
		boolean fighting = Rs2Combat.inCombat();

		if (recovering && hasFood && hp < Math.min(100, threshold + 25))
		{
			Microbot.status = "Quest helper: eating back up";
			eatAnyFood(System.currentTimeMillis());
			return true;
		}
		if (hp > threshold || (recovering && (!hasFood || hp >= Math.min(100, threshold + 25))))
		{
			if (hp > threshold)
			{
				recovering = false;
				recoveryBankAttempts = 0;
			}
			if (hp > threshold || hasFood)
			{
				return false;
			}
		}

		if (hasFood)
		{
			// fightTick eats on its own task; just give it the tick when not fighting
			if (!fighting)
			{
				eatAnyFood(System.currentTimeMillis());
				return true;
			}
			return false;
		}

		if (fighting)
		{
			Microbot.status = "Quest helper: low HP and no food, escaping";
			long nowMs = System.currentTimeMillis();
			if (escapeStartMs == 0)
			{
				escapeStartMs = nowMs;
			}
			if (hp <= ESCAPE_STOP_HP_PERCENT && nowMs - escapeStartMs >= ESCAPE_STOP_AFTER_MS)
			{
				stopper.accept("Could not escape the fight at " + Math.round(hp) + "% HP with no food. Stopping so you do not die.");
				return true;
			}
			if (stepTransportNearby(step))
			{
				return false; // the step's own ladder, stairs or door is the way out: let the script click it
			}
			WorldPoint retreat = lastSafeTile;
			if (retreat != null)
			{
				Rs2Walker.walkTo(retreat, 2);
			}
			return true;
		}

		Microbot.status = "Quest helper: low HP, fetching food";
		if (!Rs2Bank.isOpen())
		{
			Rs2Bank.walkToBankAndUseBank();
			if (!sleepUntil(Rs2Bank::isOpen, 15_000))
			{
				if (++recoveryBankAttempts >= MAX_BANK_ATTEMPTS)
				{
					stopper.accept("Low HP and could not reach a bank for food");
				}
				return true;
			}
		}
		if (!Rs2Bank.setWithdrawAsItem())
		{
			Rs2Bank.closeBank();
			stopper.accept("Failed to set bank withdraw mode to Item. Toggle it manually and restart.");
			return true;
		}

		prepareBankInventory(activeQuest, step);
		QuestCombatLoadout current = loadout;
		boolean named = current != null && !current.isSkip() && current.wantsFood()
			&& Rs2Bank.count(current.getFood(), true) > 0 && !bankItemProtected(current.getFood());
		if (named)
		{
			int target = carry(current.getFoodQty(), 0, reservedSlots);
			withdraw(current.getFood(), true, target);
		}
		else
		{
			String best = bestBankFood();
			if (best != null)
			{
				withdraw(best, true, carry(10, 0, reservedSlots));
			}
		}
		boolean got = hasEdibleFood();
		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
		if (!got)
		{
			stopper.accept("Low HP and no food in the bank");
			return true;
		}
		recovering = true;
		return true;
	}

	private boolean bankItemProtected(String name)
	{
		Set<Integer> keep = protectedItemIds();
		for (Rs2ItemModel item : Rs2Bank.bankItems())
		{
			if (item != null && name.equalsIgnoreCase(item.getName()) && keep.contains(item.getId()))
			{
				return true;
			}
		}
		return false;
	}

	/** Name of the best-healing food in the bank, by Rs2Food heal values; any edible item as a last resort. */
	private String bestBankFood()
	{
		Set<Integer> keep = protectedItemIds();
		String bestName = null;
		int bestHeal = 0;
		String anyEdible = null;
		for (Rs2ItemModel item : Rs2Bank.bankItems())
		{
			if (item == null || item.isNoted() || item.getName() == null || keep.contains(item.getId()))
			{
				continue;
			}
			for (Rs2Food food : Rs2Food.values())
			{
				if (food.getId() == item.getId() && food.getHeal() > bestHeal)
				{
					bestHeal = food.getHeal();
					bestName = item.getName();
				}
			}
			if (anyEdible == null && item.isFood())
			{
				anyEdible = item.getName();
			}
		}
		return bestName != null ? bestName : anyEdible;
	}

	/** Eat, drink and pray for the current fight. Runs on its own task, not the quest script loop. */
	public void fightTick(QuestStep step)
	{
		activeStep = step;
		QuestCombatLoadout current = loadout;
		long tickNow = System.currentTimeMillis();
		if (!Rs2Combat.inCombat() && Microbot.isLoggedIn())
		{
			WorldPoint here = Rs2Player.getWorldLocation();
			if (here != null)
			{
				lastSafeTile = here;
			}
		}
		if (phase == Phase.ACTIVE && current != null)
		{
			staminaTick(current, tickNow);
		}
		// Safety net regardless of loadout: any food in the inventory is eaten at the threshold
		if (Rs2Player.getHealthPercentage() <= eatThreshold())
		{
			if (eatAnyFood(tickNow))
			{
				return;
			}
			if (Rs2Combat.inCombat())
			{
				noteNoFoodInCombat(tickNow);
			}
		}
		else if (!Rs2Combat.inCombat())
		{
			escapeStartMs = 0;
		}
		antifireTick(step, tickNow);
		if (phase != Phase.ACTIVE || current == null || current.isSkip())
		{
			return;
		}

		boolean fighting = Rs2Combat.inCombat();
		long now = System.currentTimeMillis();
		if (fighting)
		{
			lastInCombatMs = now;
		}

		if (!fighting)
		{
			if (prayerOnByUs && now - lastInCombatMs > PRAYER_OFF_DELAY_MS)
			{
				prayerOff(current);
			}
			return;
		}

		if (Rs2Player.getHealthPercentage() <= current.getEatAtPercent() && eat(current, now))
		{
			return;
		}
		if (current.usesPrayer() && QuestCombatDetector.isKillStep(step))
		{
			managePrayer(current, now);
		}
	}

	private boolean eat(QuestCombatLoadout current, long now)
	{
		List<Rs2ItemModel> named = current.wantsFood() ? namedFood(current) : Collections.emptyList();
		boolean hasNamed = !named.isEmpty();
		boolean hasAny = hasEdibleFood();
		if (!hasNamed && !hasAny)
		{
			noteNoFoodInCombat(now);
			return false;
		}
		if (now - lastConsumeMs < CONSUME_COOLDOWN_MS)
		{
			return true;
		}
		lastConsumeMs = now;
		return hasNamed ? Rs2Inventory.interact(named.get(0), "Eat") : eatBest();
	}

	private void managePrayer(QuestCombatLoadout current, long now)
	{
		if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) <= current.getDrinkAtPrayer()
			&& current.wantsPotions() && now - lastConsumeMs >= CONSUME_COOLDOWN_MS)
		{
			if (Rs2Player.drinkPrayerPotionAt(current.getDrinkAtPrayer()))
			{
				lastConsumeMs = now;
				return;
			}
		}
		if (Rs2Prayer.isOutOfPrayer())
		{
			prayerOnByUs = false;
			return;
		}

		if (current.getPrayer() == QuestCombatLoadout.Prayer.QUICK)
		{
			if (!Rs2Prayer.isQuickPrayerEnabled() && Rs2Prayer.toggleQuickPrayer(true))
			{
				prayerOnByUs = true;
			}
			return;
		}

		Rs2PrayerEnum overhead = overhead(current.getPrayer());
		if (overhead == null || Rs2Player.getRealSkillLevel(Skill.PRAYER) < overhead.getLevel())
		{
			return;
		}
		if (!Rs2Prayer.isPrayerActive(overhead) && Rs2Prayer.toggle(overhead, true))
		{
			prayerOnByUs = true;
		}
	}

	private void prayerOff(QuestCombatLoadout current)
	{
		boolean off;
		if (current.getPrayer() == QuestCombatLoadout.Prayer.QUICK)
		{
			off = Rs2Prayer.toggleQuickPrayer(false);
		}
		else
		{
			Rs2PrayerEnum overhead = overhead(current.getPrayer());
			off = overhead == null || Rs2Prayer.toggle(overhead, false);
		}
		if (off)
		{
			prayerOnByUs = false;
		}
	}

	private static Rs2PrayerEnum overhead(QuestCombatLoadout.Prayer prayer)
	{
		switch (prayer)
		{
			case PROTECT_MELEE:
				return Rs2PrayerEnum.PROTECT_MELEE;
			case PROTECT_RANGE:
				return Rs2PrayerEnum.PROTECT_RANGE;
			case PROTECT_MAGIC:
				return Rs2PrayerEnum.PROTECT_MAGIC;
			default:
				return null;
		}
	}
}
