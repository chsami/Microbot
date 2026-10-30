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
import java.util.function.Function;
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
	/** Gap between boost potion sips, so the level update lands before the next check. */
	private static final long BOOST_COOLDOWN_MS = 3000L;
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
	/** Food withdrawn instead of the chosen one because the chosen one was not in the bank. */
	private String foodFallback;
	private final Set<String> warned = new HashSet<>();

	private volatile boolean prayerOnByUs;
	/** Offensive prayers this helper switched on ("Best offensive"), so they are switched off again. */
	private final Set<Rs2PrayerEnum> offensiveOnByUs = new HashSet<>();
	private int prayerRechargeFailures;
	private static final int MAX_RECHARGE_FAILURES = 3;
	private static final int MAX_ALTAR_TRIP = 300;
	private volatile long lastInCombatMs;
	private volatile long lastConsumeMs;
	private volatile long lastStaminaMs;
	private volatile long lastBoostMs;
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

	/** Item ids in the inventory when the quest was started: the user's own supplies, kept out of the bank deposits. */
	private volatile Set<Integer> userItemIds;

	private void snapshotUserItems()
	{
		if (userItemIds != null)
		{
			return;
		}
		try
		{
			Set<Integer> ids = new HashSet<>();
			Rs2Inventory.items(i -> !i.isNoted()).forEach(i -> ids.add(i.getId()));
			// worn gear is the player's too: it can come off into the inventory later
			Rs2Equipment.items().forEach(i -> ids.add(i.getId()));
			userItemIds = ids;
		}
		catch (RuntimeException e)
		{
			Microbot.log("Quest helper: could not snapshot the starting inventory: " + e.getMessage(), Level.WARN);
		}
	}

	private static List<String> actionsOf(Rs2ItemModel item)
	{
		String[] actions = item.getInventoryActions();
		return actions == null ? new ArrayList<>() : java.util.Arrays.asList(actions);
	}

	/** True for an item the user brought into the quest that a bank visit must not deposit (a consumable). */
	public boolean isUserBroughtProtected(Rs2ItemModel item)
	{
		return item != null && !item.isNoted()
			&& !QuestPotions.mayDepositUserItem(item.getId(), item.getName(), actionsOf(item), userItemIds, false);
	}

	/** Whether the starting inventory was recorded: without it nothing can be told apart as gained during the quest. */
	public boolean hasUserSnapshot()
	{
		return userItemIds != null;
	}

	/**
	 * True for an item the player owns rather than one the quest handed out: anything in the starting inventory (gear
	 * too, not only consumables), the setup's custom items and listed potions, and the swap kit.
	 */
	public boolean isUserOrSetupItem(Rs2ItemModel item)
	{
		Set<Integer> snapshot = userItemIds;
		if (item == null || snapshot == null || snapshot.contains(item.getId()) || isSwapKitItem(item))
		{
			return true;
		}
		QuestCombatLoadout current = phase == Phase.ACTIVE ? loadout : null;
		return current != null && (isCustomItem(current, item) || isListedPotion(current, item)
			|| current.listsGearItem(item.getId(), item.getName()));
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
		userItemIds = null;
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
		prayerRechargeFailures = 0;
		fightGearDone = true;
		styleAppliedFor = null;
		supplied = false;
		staminaPending = false;
		bankAttempts = 0;
		restocks = 0;
		defaultReserve = -1;
		warned.clear();
		foodFallback = null;
		lastConsumeMs = 0;
		lastStaminaMs = 0;
		lastBoostMs = 0;
		recovering = false;
		recoveryBankAttempts = 0;
		escapeStartMs = 0;
		lastAntifireMs = 0;
		antifireSeenActive = false;
		defaultFoodTried = false;
		antifireTried = false;
		safeSpotFailures = 0;
		swapPlan = null;
		swapPlanFight = null;
		swapBlocked = false;
		swapKitTrips = 0;
		swapKitBuys = 0;
		lastSwapMs = 0;
		lastTargetProgressMs = 0;
		lastTargetHealth = -1;
		primaryWeaponId = -1;
		primaryWeaponName = "";
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
		// whatever the phase: without a starting inventory nothing can be told apart as gained during the quest
		snapshotUserItems();

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
			snapshotUserItems();
			return false;
		}
		if (!config.get().askCombatSetup())
		{
			phase = Phase.NONE;
			return false;
		}

		QuestCombatLoadout global = QuestCombatStore.loadGlobal();
		boolean swapUnanswered = false;
		try
		{
			for (QuestCombatDetector.Fight fight : QuestCombatDetector.enumerateFights(quest))
			{
				// the global default carries no per-fight rows, so it can never answer a style swap
				swapUnanswered |= fight.needsSwap;
			}
		}
		catch (RuntimeException e)
		{
			swapUnanswered = true;
		}
		if (decidePrompt(config.get().fullyAutomated() ? QuestHelperConfig.SetupPromptOption.NEVER : config.get().setupPrompt(),
			false, global != null, questHasFights, swapUnanswered)
			== PromptDecision.AUTO_APPLY)
		{
			loadout = autoLoadout(global);
			Microbot.log("Quest helper: using the last used setup for " + quest.getQuest().getName()
				+ (global == null ? " (no saved setup yet, bringing nothing extra)" : "")
				+ " without asking", Level.INFO);
			phase = Phase.ACTIVE;
			snapshotUserItems();
			return false;
		}

		phase = Phase.PROMPTING;
		return awaitAnswer(quest);
	}

	/** Whether to apply a setup silently or show the dialog; pure so it can be tested. */
	enum PromptDecision
	{
		AUTO_APPLY, SHOW
	}

	static PromptDecision decidePrompt(QuestHelperConfig.SetupPromptOption policy, boolean hasPerQuest,
		boolean hasGlobal, boolean hasFights, boolean needsSwapUnanswered)
	{
		if (hasPerQuest || policy == QuestHelperConfig.SetupPromptOption.NEVER)
		{
			return PromptDecision.AUTO_APPLY;
		}
		if (policy == QuestHelperConfig.SetupPromptOption.ALWAYS)
		{
			return PromptDecision.SHOW;
		}
		if (!hasFights)
		{
			return PromptDecision.AUTO_APPLY;
		}
		return hasGlobal && !needsSwapUnanswered ? PromptDecision.AUTO_APPLY : PromptDecision.SHOW;
	}

	/** The global default without per-fight rows (Auto style, default swap kits), or a bare loadout. */
	static QuestCombatLoadout autoLoadout(QuestCombatLoadout global)
	{
		if (global == null)
		{
			return QuestCombatLoadout.bare();
		}
		QuestCombatLoadout copy = QuestCombatLoadout.fromJson(global.toJson());
		if (copy == null)
		{
			return QuestCombatLoadout.bare();
		}
		copy.setFights(new java.util.LinkedHashMap<>());
		copy.setStyle(QuestCombatLoadout.Style.AUTO);
		return copy.normalised();
	}

	/** What the combat tabs of the quest window show for a quest (running or not). Call off the EDT. */
	public QuestCombatSetupDialog.Context contextFor(QuestHelper quest)
	{
		QuestCombatSetupDialog.Context context = buildContext(quest);
		try
		{
			context.hasFights = QuestCombatDetector.hasFights(quest);
		}
		catch (RuntimeException e)
		{
			context.hasFights = true;
		}
		return context;
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
			Microbot.log("Quest helper: waiting for quest setup for " + quest.getQuest().getName(), Level.INFO);
			final String questName = quest.getQuest().getName();
			net.runelite.client.plugins.microbot.questhelper.planner.QuestPlannerLauncher.openForQuest(questName, window ->
			{
				QuestCombatLoadout chosen = window == null ? null : window.loadouts.get(questName);
				onDialogResult(askedIn, chosen == null
					? QuestCombatSetupDialog.Result.of(QuestCombatSetupDialog.Outcome.CANCEL, null, false)
					: QuestCombatSetupDialog.Result.of(QuestCombatSetupDialog.Outcome.SAVE, chosen, false));
			});
			return true;
		}

		switch (answer.outcome)
		{
			case SAVE:
			case SKIP:
				QuestCombatStore.save(questKey, answer.loadout);
				QuestCombatStore.saveGlobal(answer.loadout);
				if (answer.dontAskAgain)
				{
					Microbot.getConfigManager().setConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "setupPrompt",
						QuestHelperConfig.SetupPromptOption.NEVER);
				}
				loadout = answer.loadout;
				phase = Phase.ACTIVE;
				snapshotUserItems();
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
		context.defaults = QuestCombatStore.loadGlobal();
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
		context.meleeLevel = Math.max(Rs2Player.getRealSkillLevel(Skill.ATTACK), Rs2Player.getRealSkillLevel(Skill.STRENGTH));
		context.rangedLevel = Rs2Player.getRealSkillLevel(Skill.RANGED);
		context.magicLevel = Rs2Player.getRealSkillLevel(Skill.MAGIC);
		context.prayerLevel = Rs2Player.getRealSkillLevel(Skill.PRAYER);
		try
		{
			Set<Integer> questIds = new HashSet<>();
			addIds(questIds, quest.getItemRequirements());
			List<Rs2ItemModel> bank = Rs2Bank.bankItems();
			context.bankSeen = bank != null && !bank.isEmpty();
			context.bestOwnedFood = ownedBestFood(bank, Rs2Inventory.items().collect(Collectors.toList()), questIds);
		}
		catch (RuntimeException e)
		{
			Microbot.log("Quest helper: could not read owned food for the setup dialog: " + e.getMessage(), Level.WARN);
		}
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
	/** Slots a boss-prep step wore a protection item in: the combat loadout must not swap them back. */
	private final Set<String> extraWornSlots = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public void reserveWornSlot(int itemId)
	{
		String slot = slotNameOfItem(itemId);
		if (!slot.isEmpty())
		{
			extraWornSlots.add(slot);
			questGearSlotsQuest = null;
		}
	}
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
		Set<String> slots = new HashSet<>(extraWornSlots);
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
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
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
		QuestStep prepStep = step;
		if (fight == null)
		{
			AheadKill ahead = aheadSwapKill(step);
			if (ahead != null)
			{
				fight = ahead.key;
				prepStep = ahead.step;
			}
		}
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

		if (QuestCombatDetector.isKillStep(prepStep) && rechargePrayer(current))
		{
			return true;
		}

		String styleMarker = currentFight == null ? "" : currentFight;
		if (!styleMarker.equals(styleAppliedFor))
		{
			styleAppliedFor = styleMarker;
			applyStyle(current, prepStep);
		}

		prepareSwap(current, prepStep);
		QuestStyleSwap.Plan kitPlan = swapPlan;
		if (kitPlan != null && !swapBlocked && swapKitTrips < MAX_SWAP_KIT_TRIPS && swapKitMissing(kitPlan))
		{
			swapKitTrips++;
			return restock(current, reservedSlots);
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

	/**
	 * Before a boss ("Prepare to fight X (level N)"): top the inventory up to {@code wanted} food from the bank, once.
	 *
	 * @return true when this tick was spent on the bank trip
	 */
	public boolean prepareBossFood(QuestStep step, int reservedSlots, int wanted)
	{
		if (bossFoodStep == step || Rs2Combat.inCombat() || bankAttempts >= MAX_BANK_ATTEMPTS)
		{
			return false;
		}
		int held = edibleFood().stream().mapToInt(Rs2ItemModel::getQuantity).sum();
		if (held >= wanted)
		{
			bossFoodStep = step;
			return false;
		}
		bossFoodStep = step;
		return fetchFood(step, reservedSlots, loadout == null ? 0 : reserveFor(loadout), wanted - held, "food for the boss");
	}

	private QuestStep bossFoodStep;

	/** Setup is Skip or brings no food, yet fights lie ahead: still carry a small stack of the bank's best food. */
	private boolean fetchDefaultFood(QuestStep step, int reservedSlots, QuestCombatLoadout current)
	{
		defaultFoodTried = true;
		return fetchFood(step, reservedSlots, reserveFor(current), DEFAULT_FOOD_QTY, "emergency food");
	}

	private boolean fetchFood(QuestStep step, int reservedSlots, int loadoutReserve, int quantity, String what)
	{
		Microbot.status = "Quest helper: fetching " + what;
		if (!Rs2Bank.isOpen())
		{
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
			if (!sleepUntil(Rs2Bank::isOpen, 15_000))
			{
				bankAttempts++;
				defaultFoodTried = false;
				bossFoodStep = null;
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
			int reserved = Math.min(27, reservedSlots + loadoutReserve);
			withdraw(best, true, carry(quantity, 0, reserved));
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

	/** Drinks an antipoison from the potion list when poisoned or venomed. */
	private void antipoisonTick(QuestCombatLoadout current, long now)
	{
		if (current.potionRows(QuestPotions.Role.ANTIPOISON).isEmpty() || Rs2Bank.isOpen()
			|| Rs2Player.antiPoisonTime <= 0 || now - lastConsumeMs < CONSUME_COOLDOWN_MS)
		{
			return;
		}
		Set<Integer> keep = protectedItemIds();
		List<Rs2ItemModel> items = Rs2Inventory.items(i -> !i.isNoted() && !keep.contains(i.getId())
			&& isListedPotion(current, i) && QuestPotions.kindOfItem(i.getName()) != null
			&& QuestPotions.kindOfItem(i.getName()).role() == QuestPotions.Role.ANTIPOISON).collect(Collectors.toList());
		if (items.isEmpty())
		{
			return;
		}
		items.sort(Comparator.comparingInt(QuestCombatHandler::dose));
		if (Rs2Inventory.interact(items.get(0), "Drink"))
		{
			lastConsumeMs = now;
		}
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
		boolean outOfPotions = current.wantsPrayerPotions() && current.usesPrayer() && prayerPotionCount(current) == 0;
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

	/** Where banking must come back to: the active step's defined point, else the player's tile. */
	private WorldPoint bankReturnPoint()
	{
		QuestStep step = activeStep;
		if (step instanceof DetailedQuestStep && ((DetailedQuestStep) step).getDefinedPoint() != null)
		{
			WorldPoint p = ((DetailedQuestStep) step).getDefinedPoint().getWorldPoint();
			if (p != null)
			{
				return p;
			}
		}
		return Rs2Player.getWorldLocation();
	}

	private volatile boolean carryRestricted;
	private boolean staminaPending;

	/** The current or upcoming step forbids carrying items; set by the quest script every tick. */
	public void setCarryRestricted(boolean restricted)
	{
		carryRestricted = restricted;
	}

	/** True while stamina potions were left in the bank because of a restricted step and still need fetching. */
	public boolean staminaPending()
	{
		return staminaPending;
	}

	/**
	 * With the bank open: puts stamina potions back when the step forbids carrying them, or when quest items or
	 * reserved slots need the room (the potions go first). Returns true when it deposited something.
	 */
	public boolean depositStamina(int stepReserved)
	{
		QuestCombatLoadout current = loadout;
		if (current == null || phase != Phase.ACTIVE || !Rs2Bank.isOpen() || !current.wantsLegacyStamina())
		{
			return false;
		}
		List<Rs2ItemModel> held = staminaItems(current);
		int reserved = Math.min(27, stepReserved + reserveFor(current));
		int free = Rs2Inventory.emptySlotCount();
		if (held.isEmpty() || !net.runelite.client.plugins.microbot.questhelper.planner.QuestStaminaRules.mustDeposit(
			current.getStaminaQty(), held.size(), free, reserved, carryRestricted, 27))
		{
			return false;
		}
		int keep = net.runelite.client.plugins.microbot.questhelper.planner.QuestStaminaRules.allowed(
			current.getStaminaQty(), held.size(), free, reserved, carryRestricted, 27);
		boolean acted = false;
		for (int i = 0; i < held.size() - keep; i++)
		{
			acted |= Rs2Bank.depositOne(held.get(i).getId());
			sleepUntil(() -> true, 200);
		}
		if (acted)
		{
			staminaPending = true;
			Microbot.log("Quest helper: stamina potions banked for this step, back at the next bank visit", Level.INFO);
		}
		return acted;
	}

	/** With the bank open and the step allowing items: fetches the stamina potions left behind. No walking. */
	public boolean topUpStamina(int stepReserved)
	{
		QuestCombatLoadout current = loadout;
		if (!staminaPending || carryRestricted || current == null || phase != Phase.ACTIVE || !Rs2Bank.isOpen()
			|| !current.wantsLegacyStamina() || !Rs2Bank.setWithdrawAsItem())
		{
			return false;
		}
		int reserved = Math.min(27, stepReserved + reserveFor(current));
		int held = staminaCount(current);
		int target = net.runelite.client.plugins.microbot.questhelper.planner.QuestStaminaRules.allowed(
			current.getStaminaQty(), held, Rs2Inventory.emptySlotCount(), reserved, false, 27);
		staminaPending = false;
		if (target <= held)
		{
			return false;
		}
		withdrawPotion(current.getStaminaType().getItemName(), target - held);
		return true;
	}

	private boolean restock(QuestCombatLoadout current, int stepReserved)
	{
		Microbot.status = "Quest helper: fetching supplies";
		if (!Rs2Bank.isOpen())
		{
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
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

		if (current.wantsLegacyStamina() && carryRestricted)
		{
			staminaPending = true; // a restricted step: left in the bank, fetched at the next bank visit
		}
		else if (current.wantsLegacyStamina())
		{
			int held = staminaCount(current);
			// with no fights left the free slots go to the quest items, so a single run potion is all that is kept
			int target = carry(fights ? current.getStaminaQty() : 1, held, reserved);
			withdrawPotion(current.getStaminaType().getItemName(), target - held);
		}
		if (!fights)
		{
			// no fights: no antifire or prayer, but custom items and the optional food/potions are still carried
			// (reserved slots and quest items come first, so these only use what is left)
			for (QuestCombatLoadout.InventoryItem item : current.getInventoryItems())
			{
				withdrawCustom(item, reserved);
			}
			withdrawPotionRows(current, reserved);
			if (current.wantsFood())
			{
				int held = foodCount(current);
				withdrawLoadoutFood(current, carry(current.getFoodQty(), held, reserved) - held);
			}
			Rs2Bank.closeBank();
			sleepUntil(() -> !Rs2Bank.isOpen(), 3_000);
			supplied = true;
			return true;
		}
		if (isDragonFight(activeStep) && current.potionRows(QuestPotions.Role.ANTIFIRE).isEmpty())
		{
			withdrawAntifire(reserved);
		}
		withdrawPotionRows(current, reserved);
		for (QuestCombatLoadout.InventoryItem item : current.getInventoryItems())
		{
			withdrawCustom(item, reserved);
		}
		withdrawSwapKit(swapPlan, reserved);
		boolean wantFood = current.wantsFood();
		if (wantFood)
		{
			int held = foodCount(current);
			int target = carry(current.getFoodQty(), held, reserved);
			withdrawLoadoutFood(current, target - held);
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

	/** Inventory potions (unnoted, any dose) of one list kind. */
	private static int kindCount(QuestPotions.Kind kind)
	{
		return Rs2Inventory.items(i -> !i.isNoted() && QuestPotions.matchesKind(kind, i.getName())).mapToInt(i -> 1).sum();
	}

	/** Withdraws each potion row's count (reserved slots and quest items first via the carry cap). */
	private void withdrawPotionRows(QuestCombatLoadout current, int reserved)
	{
		for (QuestPotions.Row row : current.potionRows())
		{
			QuestPotions.Kind kind = row.kind();
			int held = kindCount(kind);
			withdrawPotion(kind.itemName(), carry(row.getQty(), held, reserved) - held);
		}
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
		for (Rs2ItemModel item : Rs2Inventory.items(i -> i.getName() != null && (i.getName().equalsIgnoreCase(current.getFood()) || i.getName().equalsIgnoreCase(foodFallback))).collect(java.util.stream.Collectors.toList()))
		{
			if (!item.isNoted() && !keep.contains(item.getId()))
			{
				out.add(item);
			}
		}
		return out;
	}

	private int prayerPotionCount(QuestCombatLoadout current)
	{
		int sum = 0;
		for (QuestPotions.Row row : current.potionRows(QuestPotions.Role.PRAYER))
		{
			sum += kindCount(row.kind());
		}
		return sum;
	}

	/** True for an unnoted potion of any kind in the loadout's potion list (any dose). */
	private static boolean isListedPotion(QuestCombatLoadout current, Rs2ItemModel item)
	{
		if (item.isNoted() || item.getName() == null)
		{
			return false;
		}
		for (QuestPotions.Row row : current.potionRows())
		{
			if (QuestPotions.matchesKind(row.kind(), item.getName()))
			{
				return true;
			}
		}
		return false;
	}

	/** Names (without dose) of the potions that count as the loadout's run energy potion. */
	private static List<String> staminaNames(QuestCombatLoadout current)
	{
		if (!current.potionRows(QuestPotions.Role.ENERGY).isEmpty() && !current.wantsLegacyStamina())
		{
			List<String> names = new ArrayList<>();
			for (QuestPotions.Row row : current.potionRows(QuestPotions.Role.ENERGY))
			{
				names.add(row.kind().itemName());
			}
			return names;
		}
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
			if (!keep.contains(item.getId()) && names.stream().anyMatch(n -> QuestPotions.runPotionMatches(base, n)))
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
		if (staminaNames(current).contains("Stamina potion") && Rs2Player.hasStaminaActive())
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


	private int eatOffset = EatPolicy.rollOffset(new java.util.Random());

	private int eatThreshold()
	{
		QuestCombatLoadout current = loadout;
		QuestHelperConfig cfg = config.get();
		return EatPolicy.threshold(cfg == null ? 50 : cfg.eatAtHealthPercent(), current, eatOffset);
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
		eatOffset = EatPolicy.rollOffset(new java.util.Random());
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
				|| (lowHp && item.isFood()) || (current != null && isCustomItem(current, item))
				|| (current != null && isListedPotion(current, item)) || isUserBroughtProtected(item)
				|| (current != null && !fights && isFoodOrPotion(current, item));
			return shouldDeposit(protectedItem, current != null && (isSupply(current, item) || isSwapKitItem(item)), fights);
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
		return QuestPotions.isRunEnergyName(base);
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

	/** The setup's optional food or combat potion; kept (not deposited) even when no fights remain. */
	private static boolean isFoodOrPotion(QuestCombatLoadout current, Rs2ItemModel item)
	{
		String name = item.getName();
		if (name == null || item.isNoted())
		{
			return false;
		}
		return (current.wantsFood() && name.equalsIgnoreCase(current.getFood()))
			|| isListedPotion(current, item);
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
		if (isListedPotion(current, item))
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
		if (current.wantsBoosts() && isBoostPotion(current, item))
		{
			return true;
		}
		return current.wantsStamina() && isRunPotion(item);
	}

	/** True for an unnoted potion of the loadout's boost choice (any dose). */
	private static boolean isBoostPotion(QuestCombatLoadout current, Rs2ItemModel item)
	{
		if (item.isNoted() || item.getName() == null)
		{
			return false;
		}
		QuestBoosts.Potion potion = QuestBoosts.byItemName(item.getName());
		return potion != null && current.boostPotions().contains(potion);
	}

	/** Inventory potions of one boost kind (any dose) that are not quest items. */
	private List<Rs2ItemModel> boostItems(QuestBoosts.Potion boost)
	{
		Set<Integer> keep = protectedItemIds();
		return Rs2Inventory.items(i -> !i.isNoted() && i.getName() != null && !keep.contains(i.getId())
			&& QuestBoosts.byItemName(i.getName()) == boost).collect(Collectors.toList());
	}

	private int boostCount(QuestBoosts.Potion boost)
	{
		return boostItems(boost).size();
	}

	/**
	 * Tradable needs for the loadout's boost potions (any dose, bought as the 4-dose) when the quest has a kill step
	 * among {@code steps}, so the preflight buys them. Empty when there is no active loadout or no boosts chosen.
	 */
	public List<net.runelite.client.plugins.microbot.questhelper.QuestPreflight.Need> supplyPreflightNeeds(
		java.util.function.IntPredicate tradable)
	{
		QuestCombatLoadout current = loadout;
		if (phase != Phase.ACTIVE || current == null)
		{
			return new ArrayList<>();
		}
		return QuestSupplyNeeds.fromLoadout(current, QuestCombatHandler::resolveItemId, tradable);
	}

	public List<net.runelite.client.plugins.microbot.questhelper.QuestPreflight.Need> boostPreflightNeeds(
		List<DetailedQuestStep> steps)
	{
		List<net.runelite.client.plugins.microbot.questhelper.QuestPreflight.Need> out = new ArrayList<>();
		QuestCombatLoadout current = loadout;
		if (steps == null || phase != Phase.ACTIVE || current == null || !current.wantsPotions())
		{
			return out;
		}
		for (QuestPotions.Row row : current.potionRows())
		{
			out.add(new net.runelite.client.plugins.microbot.questhelper.QuestPreflight.Need(row.kind().itemName(),
				row.kind().doseIds(), row.getQty(), true));
		}
		return out;
	}

	/**
	 * In a kill step's fight: drinks each chosen boost potion whose skill is back to its real level (boost absent or
	 * expired), one per call, rate limited. Quest items are never drunk.
	 */
	private void boostTick(QuestCombatLoadout current, long now)
	{
		if (!current.wantsBoosts() || Rs2Bank.isOpen() || now - lastConsumeMs < 600L)
		{
			return;
		}
		Skill[] skills = Skill.values();
		int[] boosted = new int[skills.length];
		int[] real = new int[skills.length];
		for (QuestBoosts.Potion boost : current.boostPotions())
		{
			for (Skill skill : boost.skills())
			{
				boosted[skill.ordinal()] = Rs2Player.getBoostedSkillLevel(skill);
				real[skill.ordinal()] = Rs2Player.getRealSkillLevel(skill);
			}
			List<Rs2ItemModel> held = boostItems(boost);
			if (!QuestBoosts.shouldDrink(boost, boosted, real, !held.isEmpty(), false, now - lastBoostMs,
				BOOST_COOLDOWN_MS))
			{
				continue;
			}
			held.sort(Comparator.comparingInt(QuestCombatHandler::dose));
			lastBoostMs = now;
			lastConsumeMs = now;
			Rs2Inventory.interact(held.get(0), "Drink");
			return;
		}
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
				if (item != null && !item.isNoted() && item.getName() != null && QuestPotions.runPotionMatches(baseName(item), name))
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
			Rs2Bank.walkToBankAndUseBankReturningTo(bankReturnPoint());
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
		return bestFoodName(Rs2Bank.bankItems(), protectedItemIds());
	}

	static String bestFoodName(List<Rs2ItemModel> items, Set<Integer> keep)
	{
		return bestFoodName(items, Rs2ItemModel::getName, Rs2ItemModel::getId, QuestCombatHandler::healOf,
			Rs2ItemModel::isFood, Rs2ItemModel::isNoted, keep);
	}

	/** Pure core of {@link #bestBankFood()}: best-healing unprotected, un-noted food name, else any edible one. */
	static <T> String bestFoodName(List<T> items, Function<T, String> nameOf, ToIntFunction<T> idOf,
		ToIntFunction<T> healOf, Predicate<T> edible, Predicate<T> noted, Set<Integer> keep)
	{
		if (items == null)
		{
			return null;
		}
		String bestName = null;
		int bestHeal = 0;
		String anyEdible = null;
		for (T item : items)
		{
			if (item == null || noted.test(item) || nameOf.apply(item) == null || keep.contains(idOf.applyAsInt(item)))
			{
				continue;
			}
			int heal = healOf.applyAsInt(item);
			if (heal > bestHeal)
			{
				bestHeal = heal;
				bestName = nameOf.apply(item);
			}
			if (anyEdible == null && edible.test(item))
			{
				anyEdible = nameOf.apply(item);
			}
		}
		return bestName != null ? bestName : anyEdible;
	}

	/** Best-healing food the player owns across the bank and inventory; null when none is known. */
	static String ownedBestFood(List<Rs2ItemModel> bank, List<Rs2ItemModel> inventory, Set<Integer> keep)
	{
		List<Rs2ItemModel> all = new ArrayList<>();
		if (bank != null)
		{
			all.addAll(bank);
		}
		if (inventory != null)
		{
			all.addAll(inventory);
		}
		return bestFoodName(all, keep);
	}

	/** Food to withdraw: the chosen one if the bank (or inventory) has it, else the best bank food, else null. */
	static String chooseFood(String chosen, boolean chosenInBank, boolean chosenHeld, String bestBank)
	{
		if (chosen != null && !chosen.trim().isEmpty() && (chosenInBank || chosenHeld))
		{
			return chosen;
		}
		return bestBank;
	}

	/** Withdraws {@code missing} of the loadout food, falling back to the best bank food when it is not banked. */
	private void withdrawLoadoutFood(QuestCombatLoadout current, int missing)
	{
		if (missing <= 0)
		{
			return;
		}
		String chosen = current.getFood();
		boolean inBank = Rs2Bank.count(chosen, true) > 0 && !bankItemProtected(chosen);
		boolean held = !namedFood(current).isEmpty();
		String pick = chooseFood(chosen, inBank, held, bestBankFood());
		if (pick == null)
		{
			return;
		}
		if (!pick.equalsIgnoreCase(chosen))
		{
			foodFallback = pick;
			if (warned.add("foodfallback:" + chosen))
			{
				Microbot.log("Quest helper: no " + chosen + " in the bank, bringing " + pick + " instead", Level.INFO);
			}
		}
		withdraw(pick, true, missing);
	}

	// ---- style swapping (targets that protect against the style we use) ----

	private volatile QuestStyleSwap.Plan swapPlan;
	private volatile String swapPlanFight;
	private volatile boolean swapBlocked;
	private volatile long lastSwapMs;
	private volatile long lastTargetProgressMs;
	private volatile int lastTargetHealth = -1;
	private volatile int primaryWeaponId = -1;
	private volatile String primaryWeaponName = "";
	private int swapKitTrips;
	private static final int MAX_SWAP_KIT_TRIPS = 2;

	/** One client-thread read of the player's target. */
	private static final class TargetRead
	{
		/** Styles the target's overhead icon protects against; null when it reports no overhead data. */
		final Set<QuestCombatLoadout.Style> protectedStyles;
		final int health;

		TargetRead(Set<QuestCombatLoadout.Style> protectedStyles, int health)
		{
			this.protectedStyles = protectedStyles;
			this.health = health;
		}
	}

	private QuestCombatLoadout.Style primaryStyleOf(QuestCombatLoadout current, String fightKey)
	{
		QuestCombatLoadout.Style style = current.styleFor(fightKey);
		if (style != QuestCombatLoadout.Style.AUTO)
		{
			return style;
		}
		for (QuestCombatLoadout.GearItem item : current.gearFor(fightKey).getItems())
		{
			if ("weapon".equals(item.getSlot()))
			{
				return QuestCombatLoadout.styleOfWeapon(item.getName());
			}
		}
		Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		return QuestCombatLoadout.styleOfWeapon(weapon == null ? null : weapon.getName());
	}

	/** The swap plan for a kill step's fight, or null when the fight is not swapped. */
	private QuestStyleSwap.Plan buildSwapPlan(QuestCombatLoadout current, QuestStep step, String fightKey)
	{
		if (fightKey == null || current == null)
		{
			return null;
		}
		List<String> restriction = QuestCombatDetector.restrictionTextsFor(activeQuest, step, fightKey);
		boolean detected = QuestCombatDetector.stepNeedsSwap(step) || QuestStyleSwap.textNeedsSwap(restriction);
		int melee = Math.max(Rs2Player.getRealSkillLevel(Skill.ATTACK), Rs2Player.getRealSkillLevel(Skill.STRENGTH));
		QuestStyleSwap.Plan plan = current.swapPlanFor(fightKey, detected, primaryStyleOf(current, fightKey), melee,
			Rs2Player.getRealSkillLevel(Skill.RANGED), Rs2Player.getRealSkillLevel(Skill.MAGIC), restriction);
		if (plan != null && plan.hasSetup())
		{
			plan = resolveSetupKit(plan);
		}
		return plan;
	}

	/** Reads the weapon and ammo out of the plan's Inventory Setup. */
	private QuestStyleSwap.Plan resolveSetupKit(QuestStyleSwap.Plan plan)
	{
		try
		{
			List<net.runelite.client.plugins.microbot.inventorysetups.InventorySetup> setups =
				net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin.getInventorySetups();
			if (setups != null)
			{
				int weaponIdx = EquipmentInventorySlot.WEAPON.getSlotIdx();
				int ammoIdx = EquipmentInventorySlot.AMMO.getSlotIdx();
				for (net.runelite.client.plugins.microbot.inventorysetups.InventorySetup setup : new ArrayList<>(setups))
				{
					if (setup == null || !plan.setup.equalsIgnoreCase(setup.getName()) || setup.getEquipment() == null)
					{
						continue;
					}
					List<net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem> worn = setup.getEquipment();
					String weapon = "";
					String ammo = "";
					int qty = 0;
					if (worn.size() > weaponIdx && worn.get(weaponIdx) != null && worn.get(weaponIdx).getId() != -1)
					{
						weapon = worn.get(weaponIdx).getName();
					}
					if (worn.size() > ammoIdx && worn.get(ammoIdx) != null && worn.get(ammoIdx).getId() != -1)
					{
						ammo = worn.get(ammoIdx).getName();
						qty = worn.get(ammoIdx).getQuantity();
					}
					return plan.withKit(weapon, ammo, qty);
				}
			}
		}
		catch (RuntimeException e)
		{
			Microbot.log("Quest helper: could not read the swap setup '" + plan.setup + "': " + e.getMessage(), Level.WARN);
		}
		warnOnce("swapsetup:" + plan.setup, "Quest helper: inventory setup '" + plan.setup
			+ "' not found for the style swap, using the default kit");
		String[] kit = QuestStyleSwap.defaultKit(plan.secondary);
		return plan.withKit(kit[0], kit[1], Integer.parseInt(kit[2]));
	}

	/** Item id for a swap kit name: a default-kit id, else an exact tradable-name match, else -1. */
	public static int resolveItemId(String name)
	{
		int known = QuestStyleSwap.knownItemId(name);
		if (known > 0 || name == null || name.isEmpty())
		{
			return known;
		}
		try
		{
			for (net.runelite.http.api.item.ItemPrice price : Microbot.getItemManager().search(name))
			{
				if (price.getName().equalsIgnoreCase(name.trim()))
				{
					return price.getId();
				}
			}
		}
		catch (RuntimeException e)
		{
			Microbot.log("Quest helper: could not look up '" + name + "': " + e.getMessage(), Level.WARN);
		}
		return -1;
	}

	/**
	 * Tradable needs for the secondary weapon and ammo/runes of every swapped fight among {@code steps}, so the
	 * preflight buys them. Empty when there is no active loadout.
	 */
	public List<net.runelite.client.plugins.microbot.questhelper.QuestPreflight.Need> swapPreflightNeeds(
		List<DetailedQuestStep> steps)
	{
		List<net.runelite.client.plugins.microbot.questhelper.QuestPreflight.Need> out = new ArrayList<>();
		QuestCombatLoadout current = loadout;
		if (steps == null || phase != Phase.ACTIVE || current == null || current.isSkip())
		{
			return out;
		}
		Set<String> seen = new HashSet<>();
		for (DetailedQuestStep step : steps)
		{
			String key = QuestCombatDetector.fightKeyOf(step);
			if (key == null || !seen.add(key))
			{
				continue;
			}
			QuestStyleSwap.Plan plan = buildSwapPlan(current, step, key);
			if (plan == null)
			{
				continue;
			}
			addSwapNeed(out, plan.weapon, 1);
			addSwapNeed(out, plan.ammo, plan.ammoQty);
		}
		return out;
	}

	private void addSwapNeed(List<net.runelite.client.plugins.microbot.questhelper.QuestPreflight.Need> out,
		String name, int qty)
	{
		if (name == null || name.isEmpty() || qty <= 0)
		{
			return;
		}
		int id = resolveItemId(name);
		if (id <= 0)
		{
			warnOnce("swapitem:" + name, "Quest helper: could not find an item called '" + name
				+ "' for the style swap, not buying it");
			return;
		}
		out.add(new net.runelite.client.plugins.microbot.questhelper.QuestPreflight.Need(name,
			Collections.singletonList(id), qty, true));
	}

	/** True when the plan's weapon and ammo are not both held (inventory or worn). */
	private boolean swapKitMissing(QuestStyleSwap.Plan plan)
	{
		boolean weaponHeld = plan.weapon.isEmpty() || Rs2Inventory.hasItem(plan.weapon, true)
			|| Rs2Equipment.isWearing(plan.weapon, true);
		return QuestStyleSwap.kitMissing(plan.weapon, weaponHeld, plan.ammo, plan.ammoQty,
			plan.ammo.isEmpty() ? 0 : swapAmmoHeld(plan));
	}

	private static final class AheadKill
	{
		final QuestStep step;
		final String key;

		AheadKill(QuestStep step, String key)
		{
			this.step = step;
			this.key = key;
		}
	}

	private QuestStep aheadFor;
	private AheadKill aheadResult;
	private int swapKitBuys;
	private static final int MAX_SWAP_KIT_BUYS = 2;

	/**
	 * For a non-kill step (the talk step that starts a fight): the next kill step when it needs a style swap and is
	 * in the same area, so its kit is ready before the fight starts. Cached per active step.
	 */
	private AheadKill aheadSwapKill(QuestStep step)
	{
		if (step == aheadFor)
		{
			return aheadResult;
		}
		aheadFor = step;
		aheadResult = null;
		QuestHelper quest = activeQuest;
		if (step == null || quest == null)
		{
			return null;
		}
		try
		{
			List<DetailedQuestStep> steps = QuestCombatDetector.remainingDetailedSteps(quest);
			if (steps == null)
			{
				return null;
			}
			WorldPoint here = step instanceof DetailedQuestStep && ((DetailedQuestStep) step).getDefinedPoint() != null
				? ((DetailedQuestStep) step).getDefinedPoint().getWorldPoint() : null;
			int npcId = step instanceof net.runelite.client.plugins.microbot.questhelper.steps.NpcStep
				? ((net.runelite.client.plugins.microbot.questhelper.steps.NpcStep) step).getNpcID() : -1;
			List<QuestStep> candidates = new ArrayList<>();
			List<QuestStyleSwap.Ahead> facts = new ArrayList<>();
			for (DetailedQuestStep s : steps)
			{
				if (s == step)
				{
					continue;
				}
				boolean kill = QuestCombatDetector.isKillStep(s);
				boolean swap = false;
				boolean near = false;
				if (kill)
				{
					String key = QuestCombatDetector.fightKeyOf(s);
					swap = QuestCombatDetector.stepNeedsSwap(s)
						|| QuestStyleSwap.textNeedsSwap(QuestCombatDetector.restrictionTextsFor(quest, s, key));
					WorldPoint there = s.getDefinedPoint() == null ? null : s.getDefinedPoint().getWorldPoint();
					near = npcId > 0 && ((net.runelite.client.plugins.microbot.questhelper.steps.NpcStep) s).getNpcID() == npcId
						|| here != null && there != null && here.getPlane() == there.getPlane()
						&& here.distanceTo2D(there) <= QuestStyleSwap.SAME_AREA_TILES;
				}
				candidates.add(s);
				facts.add(new QuestStyleSwap.Ahead(kill, swap, near));
			}
			int idx = QuestStyleSwap.upcomingSwapKill(false, facts);
			if (idx >= 0)
			{
				QuestStep kill = candidates.get(idx);
				aheadResult = new AheadKill(kill, QuestCombatDetector.fightKeyOf(kill));
			}
		}
		catch (RuntimeException e)
		{
			Microbot.log("Quest helper: could not look ahead for a style swap kit: " + e.getMessage(), Level.WARN);
		}
		return aheadResult;
	}

	/** True when a swap fight is planned, the bank trips are used up and the kit is still not held. */
	public boolean swapKitWanted()
	{
		QuestStyleSwap.Plan plan = swapPlan;
		return plan != null && !swapBlocked && swapKitTrips >= MAX_SWAP_KIT_TRIPS && !Rs2Combat.inCombat()
			&& swapKitMissing(plan);
	}

	/**
	 * Quest script hook, called after prepareForKill: when the bank trips could not supply the swap kit, the missing
	 * items to buy; an empty list means the quest was stopped (kit not obtainable); null means nothing to do.
	 */
	public List<net.runelite.client.plugins.microbot.questhelper.QuestPreflight.Need> swapKitToObtain(boolean mayBuy)
	{
		QuestStyleSwap.Plan plan = swapPlan;
		if (!swapKitWanted())
		{
			return null;
		}
		List<net.runelite.client.plugins.microbot.questhelper.QuestPreflight.Need> out = new ArrayList<>();
		if (mayBuy && swapKitBuys < MAX_SWAP_KIT_BUYS)
		{
			swapKitBuys++;
			if (!plan.weapon.isEmpty() && !Rs2Inventory.hasItem(plan.weapon, true)
				&& !Rs2Equipment.isWearing(plan.weapon, true))
			{
				addSwapNeed(out, plan.weapon, 1);
			}
			if (!plan.ammo.isEmpty() && plan.ammoQty - swapAmmoHeld(plan) > 0)
			{
				addSwapNeed(out, plan.ammo, plan.ammoQty - swapAmmoHeld(plan));
			}
			if (!out.isEmpty())
			{
				Microbot.log("Quest helper: swap kit for " + plan.fightKey + " is not in the bank, buying it", Level.INFO);
				return out;
			}
		}
		swapBlocked = true;
		stopper.accept("The " + plan.secondary + " kit for " + plan.fightKey + " (" + plan.weapon
			+ (plan.ammo.isEmpty() ? "" : " + " + plan.ammo) + ") is missing and could not be bought. "
			+ "Fighting without it would stall; get it and restart.");
		return out;
	}

	private static int swapAmmoHeld(QuestStyleSwap.Plan plan)
	{
		int held = Rs2Inventory.count(plan.ammo, true);
		Rs2ItemModel worn = Rs2Equipment.get(EquipmentInventorySlot.AMMO);
		if (worn != null && worn.getName() != null && worn.getName().equalsIgnoreCase(plan.ammo))
		{
			held += worn.getQuantity();
		}
		return held;
	}

	/** True for the swap weapon and ammo/runes, and the primary weapon while it sits in the inventory. */
	private boolean isSwapKitItem(Rs2ItemModel item)
	{
		QuestStyleSwap.Plan plan = swapPlan;
		String name = item.getName();
		if (plan == null || name == null || item.isNoted())
		{
			return false;
		}
		return name.equalsIgnoreCase(plan.weapon) || name.equalsIgnoreCase(plan.ammo)
			|| item.getId() == primaryWeaponId && primaryWeaponId > 0;
	}

	/** Withdraws the swap kit during a restock; quest slots are reserved first and the carry is capped. */
	private void withdrawSwapKit(QuestStyleSwap.Plan plan, int reserved)
	{
		if (plan == null || swapBlocked)
		{
			return;
		}
		if (!plan.weapon.isEmpty() && !Rs2Inventory.hasItem(plan.weapon, true)
			&& !Rs2Equipment.isWearing(plan.weapon, true))
		{
			withdrawCustom(new QuestCombatLoadout.InventoryItem(plan.weapon, QuestStyleSwap.knownItemId(plan.weapon), 1,
				false), reserved);
		}
		if (!plan.ammo.isEmpty() && plan.ammoQty > 0 && plan.ammoQty - swapAmmoHeld(plan) > 0)
		{
			withdrawCustom(new QuestCombatLoadout.InventoryItem(plan.ammo, QuestStyleSwap.knownItemId(plan.ammo),
				plan.ammoQty - swapAmmoHeld(plan), true), reserved);
		}
	}

	/** Called from prepareForKill once the fight's gear is on: builds the plan and notes the primary weapon. */
	private void prepareSwap(QuestCombatLoadout current, QuestStep step)
	{
		if (currentFight == null || currentFight.equals(swapPlanFight))
		{
			return;
		}
		swapPlanFight = currentFight;
		swapPlan = null;
		swapBlocked = false;
		swapKitTrips = 0;
		lastSwapMs = 0;
		lastTargetProgressMs = 0;
		QuestStyleSwap.Plan plan = buildSwapPlan(current, step, currentFight);
		if (plan == null)
		{
			return;
		}
		Set<String> questSlots = questGearSlots();
		for (String slot : plan.slotsUsed())
		{
			if (QuestFightSupport.slotConflicts(slot, questSlots))
			{
				warnOnce("swapslot:" + currentFight, "Quest helper: the quest needs the " + slot
					+ " slot worn, so " + currentFight + " will not swap combat styles");
				swapBlocked = true;
			}
		}
		if (plan.weapon.isEmpty())
		{
			warnOnce("swapweapon:" + currentFight, "Quest helper: no weapon for the " + plan.secondary
				+ " style of " + currentFight + "; set one in the Fights tab. Not swapping.");
			swapBlocked = true;
		}
		Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		if (weapon != null && QuestCombatLoadout.styleOfWeapon(weapon.getName()) == plan.primary)
		{
			primaryWeaponId = weapon.getId();
			primaryWeaponName = weapon.getName() == null ? "" : weapon.getName();
		}
		Microbot.log("Quest helper: " + currentFight + " needs a style swap: " + plan.primary + " / " + plan.secondary
			+ (plan.hasSetup() ? " (setup " + plan.setup + ")" : " (" + plan.weapon
			+ (plan.ammo.isEmpty() ? "" : " + " + plan.ammoQty + " " + plan.ammo) + ")"), Level.INFO);
		swapPlan = plan;
	}

	private static TargetRead readTarget()
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			net.runelite.api.Actor target = Microbot.getClient().getLocalPlayer() == null ? null
				: Microbot.getClient().getLocalPlayer().getInteracting();
			if (!(target instanceof net.runelite.api.NPC))
			{
				return null;
			}
			net.runelite.api.NPC npc = (net.runelite.api.NPC) target;
			return new TargetRead(QuestStyleSwap.protectedStyles(new net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel(npc).getOverheadSpriteIds()), npc.getHealthRatio());
		}).orElse(null);
	}

	/**
	 * Fight tick: swap to the style the target is not protecting against, at most once per two ticks. Without
	 * overhead data, alternates after the target stops losing health.
	 */
	private void swapTick(long now)
	{
		QuestStyleSwap.Plan plan = swapPlan;
		if (plan == null || swapBlocked || !plan.fightKey.equals(currentFight))
		{
			return;
		}
		TargetRead target = readTarget();
		if (target == null)
		{
			return;
		}
		if (target.health != lastTargetHealth || lastTargetProgressMs == 0)
		{
			lastTargetHealth = target.health;
			lastTargetProgressMs = now;
		}
		Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		QuestCombatLoadout.Style wielded = QuestCombatLoadout.styleOfWeapon(weapon == null ? null : weapon.getName());
		QuestCombatLoadout.Style wanted = QuestStyleSwap.wantedStyle(wielded, plan.primary, plan.secondary,
			target.protectedStyles, lastTargetProgressMs, now);
		if (wanted == null || wanted == wielded || !QuestStyleSwap.swapAllowed(lastSwapMs, now))
		{
			return;
		}
		Microbot.log("Quest helper: swapping " + wielded + " -> " + wanted + " (target protects "
			+ target.protectedStyles + ", no health loss for " + (now - lastTargetProgressMs) / 1000 + "s)", Level.INFO);
		swapTo(plan, wanted, now);
	}

	private void swapTo(QuestStyleSwap.Plan plan, QuestCombatLoadout.Style wanted, long now)
	{
		Microbot.status = "Quest helper: swapping to " + wanted;
		lastSwapMs = now;
		lastTargetProgressMs = now;
		if (wanted == plan.secondary)
		{
			if (plan.secondary == QuestCombatLoadout.Style.RANGED && !plan.ammo.isEmpty()
				&& Rs2Inventory.hasItem(plan.ammo, true))
			{
				Rs2Inventory.wield(plan.ammo);
				sleepUntil(() -> !Rs2Inventory.hasItem(plan.ammo, true), 1_200);
			}
			if (!Rs2Inventory.wield(plan.weapon))
			{
				warnOnce("swapkit:" + plan.fightKey, "Quest helper: the " + plan.weapon
					+ " is not in the inventory, cannot swap styles");
				return;
			}
			if (!sleepUntil(() -> Rs2Equipment.isWearing(plan.weapon, true), 1_800))
			{
				return;
			}
			if (plan.secondary == QuestCombatLoadout.Style.MAGIC && !setAutocast())
			{
				warnOnce("swapcast:" + plan.fightKey, "Quest helper: no castable combat spell for the style swap");
			}
		}
		else
		{
			final int id = primaryWeaponId;
			final String name = primaryWeaponName;
			boolean wielded = id > 0 ? Rs2Inventory.wield(id) : !name.isEmpty() && Rs2Inventory.wield(name);
			if (!wielded || !sleepUntil(() -> id > 0 ? Rs2Equipment.isWearing(id) : Rs2Equipment.isWearing(name, true),
				1_800))
			{
				return;
			}
		}
		reattack();
	}

	/** After a weapon change: re-attacks the kill step's target when the game dropped it. */
	private void reattack()
	{
		if (Rs2Player.getInteracting() != null)
		{
			return;
		}
		QuestStep step = activeStep;
		if (step instanceof net.runelite.client.plugins.microbot.questhelper.steps.NpcStep)
		{
			net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel npc =
				net.runelite.client.plugins.microbot.util.npc.Rs2Npc.getNpc(
					((net.runelite.client.plugins.microbot.questhelper.steps.NpcStep) step).getNpcID());
			if (npc != null)
			{
				net.runelite.client.plugins.microbot.util.npc.Rs2Npc.interact(npc, "Attack");
			}
		}
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
		if (phase == Phase.ACTIVE && current != null)
		{
			antipoisonTick(current, tickNow);
		}
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
			if (now - lastInCombatMs > PRAYER_OFF_DELAY_MS)
			{
				heldBoostsDone = false;
			}
			if (prayerOnByUs && now - lastInCombatMs > PRAYER_OFF_DELAY_MS)
			{
				prayerOff(current);
			}
			return;
		}

		if (Rs2Player.getHealthPercentage() <= eatThreshold() && eat(current, now))
		{
			return;
		}
		if (QuestCombatDetector.isKillStep(step) && heldPrayerTick(current, now))
		{
			return;
		}
		if (current.usesPrayer() && QuestCombatDetector.isKillStep(step))
		{
			managePrayer(current, now);
		}
		if (QuestCombatDetector.isKillStep(step))
		{
			if (heldBoostTick(now))
			{
				return;
			}
			boostTick(current, now);
			swapTick(now);
		}
	}

	private volatile boolean heldBoostsDone;
	private volatile boolean heldPrayerStopped;

	private static boolean anyPrayerActive()
	{
		if (Rs2Prayer.isQuickPrayerEnabled())
		{
			return true;
		}
		for (Rs2PrayerEnum p : Rs2PrayerEnum.values())
		{
			if (Rs2Prayer.isPrayerActive(p))
			{
				return true;
			}
		}
		return false;
	}

	/** Held prayer potions and super restores (not quest items), lowest dose first. */
	private List<Rs2ItemModel> heldPrayerPotions()
	{
		Set<Integer> keep = protectedItemIds();
		List<Rs2ItemModel> out = Rs2Inventory.items(i -> !i.isNoted() && i.getName() != null
			&& !keep.contains(i.getId()) && (QuestPotions.matchesKind(QuestPotions.Kind.PRAYER_POTION, i.getName())
			|| QuestPotions.matchesKind(QuestPotions.Kind.SUPER_RESTORE, i.getName()))).collect(Collectors.toList());
		out.sort(Comparator.comparingInt(QuestCombatHandler::dose));
		return out;
	}

	/**
	 * In a fight with any prayer on (ours or the player's own): drinks a held prayer potion at the low-points
	 * threshold whatever the loadout lists, and stops our prayers once points are gone with nothing to restore.
	 *
	 * @return true when this tick was used
	 */
	private boolean heldPrayerTick(QuestCombatLoadout current, long now)
	{
		if (Rs2Bank.isOpen())
		{
			return false;
		}
		int level = Rs2Player.getRealSkillLevel(Skill.PRAYER);
		if (level < 1)
		{
			return false;
		}
		int points = Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
		boolean active = anyPrayerActive();
		if (!active && points > 0)
		{
			heldPrayerStopped = false;
			return false;
		}
		List<Rs2ItemModel> held = heldPrayerPotions();
		if (QuestPrayer.shouldDrinkHeldPotion(active, points, QuestPrayer.drinkThreshold(current.getDrinkAtPrayer(),
			level), !held.isEmpty(), now - lastConsumeMs, CONSUME_COOLDOWN_MS))
		{
			lastConsumeMs = now;
			Rs2Inventory.interact(held.get(0), "Drink");
			return true;
		}
		if (QuestPrayer.shouldStopPraying(active, points, !held.isEmpty()) && !heldPrayerStopped)
		{
			heldPrayerStopped = true;
			prayerOnByUs = false;
			offensiveOnByUs.clear();
			if (Rs2Prayer.isQuickPrayerEnabled())
			{
				Rs2Prayer.toggleQuickPrayer(false);
			}
			warnOnce("prayerdry", "Quest helper: out of prayer points and no potion held, prayers off");
		}
		return false;
	}

	/** At fight start: drinks any held boost potion (listed or not) whose skill is not boosted. Once per fight. */
	private boolean heldBoostTick(long now)
	{
		if (heldBoostsDone || Rs2Bank.isOpen() || now - lastConsumeMs < 600L)
		{
			return false;
		}
		Skill[] skills = Skill.values();
		int[] boosted = new int[skills.length];
		int[] real = new int[skills.length];
		for (Skill skill : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC})
		{
			boosted[skill.ordinal()] = Rs2Player.getBoostedSkillLevel(skill);
			real[skill.ordinal()] = Rs2Player.getRealSkillLevel(skill);
		}
		Set<Integer> keep = protectedItemIds();
		List<Rs2ItemModel> held = Rs2Inventory.items(i -> !i.isNoted() && i.getName() != null
			&& !QuestBoosts.skillsOfHeld(i.getName()).isEmpty()).collect(Collectors.toList());
		held.sort(Comparator.comparingInt(QuestCombatHandler::dose));
		for (Rs2ItemModel item : held)
		{
			if (QuestBoosts.shouldDrinkHeld(QuestBoosts.skillsOfHeld(item.getName()), boosted, real,
				keep.contains(item.getId()), now - lastBoostMs, BOOST_COOLDOWN_MS))
			{
				lastBoostMs = now;
				lastConsumeMs = now;
				Rs2Inventory.interact(item, "Drink");
				return true;
			}
		}
		heldBoostsDone = true;
		return false;
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
		eatOffset = EatPolicy.rollOffset(new java.util.Random());
		return hasNamed ? Rs2Inventory.interact(named.get(0), "Eat") : eatBest();
	}

	/**
	 * Before a kill step with a prayer chosen: when prayer points are low, walks to an altar and prays there. One
	 * action per tick; gives up after a few failures so the fight is never blocked.
	 *
	 * @return true when this tick was used
	 */
	private boolean rechargePrayer(QuestCombatLoadout current)
	{
		if (!current.usesPrayer() || prayerRechargeFailures >= MAX_RECHARGE_FAILURES)
		{
			return false;
		}
		final int level = Rs2Player.getRealSkillLevel(Skill.PRAYER);
		if (level < 1 || !QuestPrayer.needsRecharge(Rs2Player.getBoostedSkillLevel(Skill.PRAYER), level))
		{
			prayerRechargeFailures = 0;
			return false;
		}
		Microbot.status = "Quest helper: recharging prayer at an altar";
		WorldPoint here = Rs2Player.getWorldLocation();
		net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel altar = Microbot
			.getRs2TileObjectCache().query().withNameContains("Altar").withAction("Pray-at").nearestOnClientThread(20);
		if (altar != null && !QuestPrayer.inWilderness(altar.getWorldLocation()))
		{
			if (altar.click("Pray-at")
				&& sleepUntil(() -> Rs2Player.getBoostedSkillLevel(Skill.PRAYER) >= level, 8_000))
			{
				prayerRechargeFailures = 0;
			}
			else
			{
				prayerRechargeFailures++;
			}
			return true;
		}
		WorldPoint known = QuestPrayer.nearestKnownAltar(here);
		if (known == null || known.getPlane() != here.getPlane() || known.distanceTo2D(here) > MAX_ALTAR_TRIP)
		{
			warnOnce("prayeraltar", "Quest helper: no altar nearby to recharge prayer, fighting with what is left");
			prayerRechargeFailures = MAX_RECHARGE_FAILURES;
			return false;
		}
		net.runelite.client.plugins.microbot.util.walker.WalkerState state = Rs2Walker.walkStep(known, 6);
		if (state == net.runelite.client.plugins.microbot.util.walker.WalkerState.UNREACHABLE
			|| state == net.runelite.client.plugins.microbot.util.walker.WalkerState.EXIT
			|| state == net.runelite.client.plugins.microbot.util.walker.WalkerState.ARRIVED)
		{
			prayerRechargeFailures++;
		}
		return true;
	}

	/** "Best offensive": keep the best offensive prayers for the wielded style on, and switch off stale ones. */
	private void manageOffensive()
	{
		int level = Rs2Player.getRealSkillLevel(Skill.PRAYER);
		Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		QuestPrayer.Unlocks unlocks = new QuestPrayer.Unlocks();
		unlocks.chivalry = Rs2Prayer.isChivalryUnlocked();
		unlocks.piety = Rs2Prayer.isPietyUnlocked();
		unlocks.rigour = Rs2Prayer.isRigourUnlocked();
		unlocks.deadeye = Rs2Prayer.isDeadeyeUnlocked();
		unlocks.augury = Rs2Prayer.isAuguryUnlocked();
		unlocks.mysticVigour = Rs2Prayer.isMysticVigourUnlocked();
		List<Rs2PrayerEnum> wanted = QuestPrayer.bestOffensive(
			QuestCombatLoadout.styleOfWeapon(weapon == null ? null : weapon.getName()), level, unlocks);
		for (Rs2PrayerEnum stale : new ArrayList<>(offensiveOnByUs))
		{
			if (!wanted.contains(stale))
			{
				if (!Rs2Prayer.isPrayerActive(stale) || Rs2Prayer.toggle(stale, false))
				{
					offensiveOnByUs.remove(stale);
				}
				return;
			}
		}
		for (Rs2PrayerEnum prayer : wanted)
		{
			if (!Rs2Prayer.isPrayerActive(prayer))
			{
				if (Rs2Prayer.toggle(prayer, true))
				{
					offensiveOnByUs.add(prayer);
					prayerOnByUs = true;
				}
				return;
			}
			offensiveOnByUs.add(prayer);
			prayerOnByUs = true;
		}
	}

	private void managePrayer(QuestCombatLoadout current, long now)
	{
		if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) <= current.getDrinkAtPrayer()
			&& current.wantsPrayerPotions() && now - lastConsumeMs >= CONSUME_COOLDOWN_MS)
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
			offensiveOnByUs.clear();
			return;
		}

		if (current.getPrayer() == QuestCombatLoadout.Prayer.BEST_OFFENSIVE)
		{
			manageOffensive();
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
		if (current.getPrayer() == QuestCombatLoadout.Prayer.BEST_OFFENSIVE)
		{
			off = true;
			for (Rs2PrayerEnum p : new ArrayList<>(offensiveOnByUs))
			{
				if (!Rs2Prayer.isPrayerActive(p) || Rs2Prayer.toggle(p, false))
				{
					offensiveOnByUs.remove(p);
				}
				else
				{
					off = false;
				}
			}
		}
		else if (current.getPrayer() == QuestCombatLoadout.Prayer.QUICK)
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
