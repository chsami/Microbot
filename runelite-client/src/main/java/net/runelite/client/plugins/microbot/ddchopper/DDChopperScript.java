package net.runelite.client.plugins.microbot.ddchopper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerConfig;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * DD Chopper main loop. One 600 ms tick: goal check, axe check (on start and every level-up), tree/spot plan, crowd
 * check, then drop/bank when full or chop. Blocking events (random events, login, break handler pauses) are handled
 * by the {@link Script#run()} gate.
 */
@Slf4j
@Singleton
public class DDChopperScript extends Script
{
	private static final int COINS = 995;
	private static final Integer[] AXE_IDS = new Integer[Axe.values().length];

	static
	{
		for (Axe a : Axe.values())
		{
			AXE_IDS[a.ordinal()] = a.getItemId();
		}
	}

	private DDChopperConfig config;

	// What the overlay shows.
	private volatile int currentLevel;
	private volatile long xpGained;
	private volatile long xpPerHour;
	private volatile String status = "Idle";
	private volatile TreeType currentTree;
	private volatile Spot currentSpot;
	private volatile Axe currentAxe;

	// Run state.
	private long activeMillis;
	private long lastTickMillis;
	private int lastLevel = -1;
	private int startXp = -1;
	private boolean axeCheck = true;
	private boolean replan = true;
	private boolean bankChecked;
	private int buyAttemptLevel = -1;
	private final Map<Axe, Integer> axePrices = new EnumMap<>(Axe.class);
	private ChopPlanner.Plan plan;
	private String lastNote = "";
	private long tickCount;
	private boolean finished;
	private boolean walkerOverridden;

	// Crowd hopping and breaks.
	private HopGate hopGate;
	private BreakPlan breakPlan;
	private SpotRotation rotation;
	private boolean breaksSynced;
	private int lastTotalBreaks = -1;

	// Trees that refused our click recently.
	private final Map<WorldPoint, Long> blacklist = new HashMap<>();
	private WorldPoint lastTarget;
	private int failedClicks;

	public boolean run(DDChopperConfig config)
	{
		this.config = config;
		resetState();
		Rs2Antiban.resetAntibanSettings();
		Rs2Antiban.antibanSetupTemplates.applyWoodcuttingSetup();
		Rs2AntibanSettings.naturalMouse = true;
		// No surprise boats, spirit trees or teleports: spots are reached on foot or not at all.
		Rs2Walker.disableTeleports = true;
		applyWalkerOverrides();
		hopGate = new HopGate(config.maxOthers(), config.maxHopsPerHour());
		breakPlan = new BreakPlan(config.minPlay(), config.maxPlay(), config.minBreak(), config.maxBreak(), new Random());
		rotation = new SpotRotation(new Random(), config.rotateMinMinutes(), config.rotateMaxMinutes());
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::loop, 0, 600, TimeUnit.MILLISECONDS);
		return true;
	}

	/** Turns off ships, spirit trees and teleports in the shortest path planner (null-safe if the event bus is absent). */
	private void applyWalkerOverrides()
	{
		if (Microbot.getEventBus() == null)
		{
			return;
		}
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("useShips", false);
		overrides.put("useBoats", false);
		overrides.put("useSpiritTrees", false);
		overrides.put("useTeleportationItems", "None");
		overrides.put("useTeleportationLevers", false);
		overrides.put("useTeleportationMinigames", false);
		overrides.put("useTeleportationPortals", false);
		overrides.put("useTeleportationSpells", false);
		overrides.put("useWildernessObelisks", false);
		Map<String, Object> data = new HashMap<>();
		data.put("config", overrides);
		Microbot.getEventBus().post(new PluginMessage("shortestpath", "path", data));
		walkerOverridden = true;
	}

	private void clearWalkerOverrides()
	{
		if (walkerOverridden && Microbot.getEventBus() != null)
		{
			Microbot.getEventBus().post(new PluginMessage("shortestpath", "clear"));
		}
		walkerOverridden = false;
	}

	private void resetState()
	{
		activeMillis = 0;
		lastTickMillis = 0;
		lastLevel = -1;
		startXp = -1;
		axeCheck = true;
		replan = true;
		bankChecked = false;
		buyAttemptLevel = -1;
		axePrices.clear();
		plan = null;
		lastNote = "";
		tickCount = 0;
		finished = false;
		breaksSynced = false;
		lastTotalBreaks = -1;
		blacklist.clear();
		failedClicks = 0;
		currentAxe = null;
		currentTree = null;
		currentSpot = null;
		xpGained = 0;
		xpPerHour = 0;
		status = "Starting";
	}

	@Override
	public void shutdown()
	{
		super.shutdown();
		Rs2Walker.disableTeleports = false;
		clearWalkerOverrides();
		Rs2Antiban.resetAntibanSettings();
		status = "Stopped";
	}

	private void loop()
	{
		try
		{
			if (finished || !super.run() || !Microbot.isLoggedIn())
			{
				return;
			}
			long now = System.currentTimeMillis();
			if (lastTickMillis > 0)
			{
				activeMillis += Math.min(now - lastTickMillis, 5_000);
			}
			lastTickMillis = now;
			if (Microbot.isHopping())
			{
				status = "Hopping worlds";
				return;
			}
			tickCount++;

			int level = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
			currentLevel = level;
			updateXp(level);
			if (level != lastLevel)
			{
				if (lastLevel > 0)
				{
					Microbot.log("DD Chopper: level up to " + level + ", re-checking axe and tree");
				}
				lastLevel = level;
				axeCheck = true;
				replan = true;
			}

			String reason = GoalTracker.stopReason(config.goalType(), level, config.targetLevel(), activeMillis,
				GoalTracker.runLimitMillis(config.runHours(), config.runMinutes()));
			if (reason != null)
			{
				finish(reason);
				return;
			}

			syncBreaks();

			if (axeCheck)
			{
				handleAxe(level);
				if (axeCheck || finished)
				{
					return;
				}
			}
			if (replan || plan == null || false)
			{
				replan(level);
			}
			if (plan == null || plan.spot == null)
			{
				finish("no usable woodcutting spot for level " + level);
				return;
			}
			if (config.hopWhenCrowded() && checkCrowd())
			{
				return;
			}
			if (Rs2Inventory.isFull())
			{
				handleFull();
				return;
			}
			if (config.pickupNests() && pickNest())
			{
				return;
			}
			chop();
		}
		catch (Exception e)
		{
			if (e instanceof InterruptedException || Thread.currentThread().isInterrupted())
			{
				Thread.currentThread().interrupt();
				return;
			}
			log.error("DD Chopper tick failed", e);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Goal, XP, breaks
	// ---------------------------------------------------------------------------------------------

	private void updateXp(int level)
	{
		int xp = Microbot.getClientThread().runOnClientThreadOptional(
			() -> Microbot.getClient().getSkillExperience(Skill.WOODCUTTING)).orElse(-1);
		if (xp < 0)
		{
			return;
		}
		if (startXp < 0)
		{
			startXp = xp;
		}
		xpGained = xp - startXp;
		double hours = Math.max(activeMillis, 60_000) / 3_600_000.0;
		xpPerHour = (long) (xpGained / hours);
	}

	private void finish(String reason)
	{
		finished = true;
		status = "Done: " + reason;
		Microbot.log("DD Chopper stopping: " + reason + " (level " + currentLevel + ", " + xpGained + " xp gained)");
		Thread stopper = new Thread(() -> Microbot.stopPlugin(DDChopperPlugin.class), "ddchopper-stop");
		stopper.setDaemon(true);
		stopper.start();
	}

	/** Pushes freshly picked break lengths to Microbot's Break Handler and makes sure it is running. */
	private void syncBreaks()
	{
		if (!config.enableBreaks())
		{
			return;
		}
		int total = BreakHandlerScript.totalBreaks;
		if (breaksSynced && total == lastTotalBreaks)
		{
			return;
		}
		breaksSynced = true;
		lastTotalBreaks = total;
		int play = breakPlan.nextPlayMinutes();
		int rest = breakPlan.nextBreakMinutes();
		String group = BreakHandlerConfig.configGroup;
		Microbot.getConfigManager().setConfiguration(group, "Min Playtime", play);
		Microbot.getConfigManager().setConfiguration(group, "Max Playtime", play);
		Microbot.getConfigManager().setConfiguration(group, "Min BreakTime", rest);
		Microbot.getConfigManager().setConfiguration(group, "Max BreakTime", rest);
		Microbot.getConfigManager().setConfiguration(group, "Logout", config.logoutOnBreak());
		Microbot.getConfigManager().setConfiguration(group, "OnlyMicroBreaks", false);
		Plugin handler = Microbot.getPlugin(BreakHandlerPlugin.class);
		if (handler != null && !Microbot.getPluginManager().isActive(handler))
		{
			Microbot.startPlugin(handler);
		}
		Microbot.log("DD Chopper: next break after " + play + " min, lasting " + rest + " min");
	}

	/** Overlay text for the break countdown. */
	public String getBreakText()
	{
		if (!config.enableBreaks())
		{
			return "Off";
		}
		if (BreakHandlerScript.isBreakActive())
		{
			return BreakHandlerScript.breakDuration > 0
				? "On break, " + BreakHandlerScript.formatDuration(Duration.ofSeconds(BreakHandlerScript.breakDuration))
				: "On break";
		}
		return BreakHandlerScript.breakIn > 0
			? "in " + BreakHandlerScript.formatDuration(Duration.ofSeconds(BreakHandlerScript.breakIn)) : "Pending";
	}

	// ---------------------------------------------------------------------------------------------
	// Axe
	// ---------------------------------------------------------------------------------------------

	private Set<Axe> ownedAxes()
	{
		Set<Axe> owned = EnumSet.noneOf(Axe.class);
		for (Axe axe : Axe.values())
		{
			int id = axe.getItemId();
			if (Rs2Equipment.isWearing(id) || Rs2Inventory.hasItem(id) || (bankChecked && Rs2Bank.hasItem(id)))
			{
				owned.add(axe);
			}
		}
		return owned;
	}

	private void handleAxe(int level)
	{
		Set<Axe> owned = ownedAxes();
		Axe target = AxePlanner.chooseOwned(config.axe(), owned, level);
		Axe desired = config.axe().axe() != null ? config.axe().axe() : AxePlanner.bestUnlocked(level, false);
		boolean wantBuy = config.buyAxe() && desired != null && desired.isBuyable() && desired.canChop(level)
			&& (target == null || target.ordinal() < desired.ordinal()) && buyAttemptLevel != level;

		if (!bankChecked && (target == null || wantBuy))
		{
			status = "Checking bank for axes";
			if (!Rs2Bank.isOpen())
			{
				Rs2Bank.walkToBankAndUseBank();
			}
			if (Rs2Bank.isOpen())
			{
				bankChecked = true;
			}
			return;
		}

		if (wantBuy)
		{
			long coins = Rs2Inventory.count(COINS) + (long) Rs2Bank.count(COINS);
			fillPrices(level, target);
			Axe buy = AxePlanner.chooseToBuy(config.axe(), level, coins, axePrices);
			if (buy == null || (target != null && buy.ordinal() <= target.ordinal()))
			{
				buyAttemptLevel = level;
				Microbot.log("DD Chopper: cannot afford a better axe at level " + level);
				return;
			}
			buyStep(buy, axePrices.get(buy), level);
			return;
		}

		if (target == null)
		{
			finish("no usable axe for level " + level + (config.buyAxe() ? " and none could be bought" : ""));
			return;
		}

		int id = target.getItemId();
		int attack = Rs2Player.getRealSkillLevel(Skill.ATTACK);
		if (Rs2Equipment.isWearing(id))
		{
			axeReady(target);
		}
		else if (Rs2Inventory.hasItem(id))
		{
			if (target.canWield(attack))
			{
				status = "Wielding " + target.getItemName();
				Rs2Inventory.wield(id);
				sleepUntil(() -> Rs2Equipment.isWearing(id), 3_000);
			}
			axeReady(target);
		}
		else if (bankChecked && Rs2Bank.hasItem(id))
		{
			status = "Fetching " + target.getItemName();
			if (!Rs2Bank.isOpen())
			{
				Rs2Bank.walkToBankAndUseBank();
			}
			if (Rs2Bank.isOpen())
			{
				if (target.canWield(attack))
				{
					Rs2Bank.withdrawAndEquip(id);
				}
				else
				{
					Rs2Bank.withdrawOne(id);
				}
				sleepUntil(() -> Rs2Equipment.isWearing(id) || Rs2Inventory.hasItem(id), 3_000);
				Rs2Bank.closeBank();
			}
		}
		else
		{
			bankChecked = false;
		}
	}

	private void axeReady(Axe axe)
	{
		if (currentAxe != axe)
		{
			Microbot.log("DD Chopper: using " + axe.getItemName());
		}
		currentAxe = axe;
		axeCheck = false;
		replan = true;
	}

	private void fillPrices(int level, Axe owned)
	{
		for (Axe axe : Axe.values())
		{
			if (axe.isBuyable() && axe.canChop(level) && (owned == null || axe.ordinal() > owned.ordinal())
				&& !axePrices.containsKey(axe))
			{
				axePrices.put(axe, Rs2GrandExchange.getAdaptiveBuyPrice(axe.getItemId(), 1.15, 0));
			}
		}
	}

	/** One step of getting coins out of the bank and buying the axe at the GE. */
	private void buyStep(Axe axe, int price, int level)
	{
		status = "Buying " + axe.getItemName();
		if (Rs2Inventory.count(COINS) < price)
		{
			if (!Rs2Bank.isOpen())
			{
				Rs2Bank.walkToBankAndUseBank();
			}
			if (Rs2Bank.isOpen())
			{
				Rs2Bank.withdrawAll(COINS);
				sleepUntil(() -> Rs2Inventory.count(COINS) >= price, 3_000);
				Rs2Bank.closeBank();
			}
			return;
		}
		if (!Rs2GrandExchange.isOpen())
		{
			Rs2GrandExchange.walkToGrandExchange();
			Rs2GrandExchange.openExchange();
			return;
		}
		buyAttemptLevel = level;
		if (!Rs2GrandExchange.buyItem(axe.getItemName(), price, 1))
		{
			Microbot.log("DD Chopper: could not place the offer for " + axe.getItemName());
			Rs2GrandExchange.closeExchange();
			return;
		}
		sleepUntilTrue(Rs2GrandExchange::hasFinishedBuyingOffers, 600, 60_000);
		Rs2GrandExchange.collectAllToInventory();
		Rs2GrandExchange.closeExchange();
		bankChecked = false;
	}

	// ---------------------------------------------------------------------------------------------
	// Plan
	// ---------------------------------------------------------------------------------------------

	private void replan(int level)
	{
		ChopPlanner.Input in = new ChopPlanner.Input();
		in.level = level;
		in.axe = currentAxe != null ? currentAxe : Axe.BRONZE;
		in.treeChoice = config.tree();
		in.spotChoice = config.spot();
		in.mode = config.mode();
		in.members = config.members();
		in.allowRestricted = config.allowRestricted();
		in.rotate = config.rotateSpots();
		in.rotateSpots = ChopPlanner.parseSpots(config.rotateSpotNames());
		in.player = Rs2Player.getWorldLocation();
		ChopPlanner.Plan next = ChopPlanner.plan(in);
		String note = String.join("; ", next.notes);
		if (!note.equals(lastNote))
		{
			lastNote = note;
			if (!note.isEmpty())
			{
				Microbot.log("DD Chopper: " + note);
			}
		}
		if (plan == null || plan.tree != next.tree || plan.spot != next.spot)
		{
			Microbot.log("DD Chopper: chopping " + next.tree.getObjectName() + " at "
				+ (next.spot != null ? next.spot.getLabel() : "no spot"));
		}
		plan = next;
		currentTree = next.tree;
		currentSpot = next.spot;
		replan = false;
	}

	// ---------------------------------------------------------------------------------------------
	// Crowd hopping
	// ---------------------------------------------------------------------------------------------

	/** Returns true when this tick was spent on a hop. */
	private boolean checkCrowd()
	{
		WorldPoint me = Rs2Player.getWorldLocation();
		Spot spot = activeSpot();
		if (me == null || spot.center().distanceTo2D(me) > 25)
		{
			return false;
		}
		String myName = Microbot.getClientThread().runOnClientThreadOptional(
			() -> Microbot.getClient().getLocalPlayer() != null ? Microbot.getClient().getLocalPlayer().getName() : null)
			.orElse(null);
		int others = (int) Microbot.getRs2PlayerCache().query().within(config.crowdRadius()).toListOnClientThread()
			.stream().filter(p -> p != null && (myName == null || !myName.equals(p.getName()))).count();
		long now = System.currentTimeMillis();
		HopGate.Decision decision = hopGate.decide(others, now);
		if (decision == HopGate.Decision.CAPPED)
		{
			status = "Crowded (" + others + "), hop limit reached";
			return false;
		}
		if (decision != HopGate.Decision.HOP)
		{
			return false;
		}
		int world = pickWorld(spot);
		if (world <= 0)
		{
			Microbot.log("DD Chopper: crowded (" + others + " others) but no suitable world found");
			hopGate.recordHop(now);
			return false;
		}
		Microbot.log("DD Chopper: " + others + " other players at the spot, hopping to world " + world);
		status = "Hopping to " + world;
		hopGate.recordHop(now);
		if (Microbot.hopToWorld(world))
		{
			sleepUntil(() -> !Microbot.isHopping() && Microbot.isLoggedIn(), 20_000);
		}
		return true;
	}

	private int pickWorld(Spot spot)
	{
		Set<Integer> avoid = HopGate.parseWorlds(config.avoidWorlds());
		int current = Microbot.getClient().getWorld();
		for (int i = 0; i < 8; i++)
		{
			// Rs2WorldUtil skips PvP, high risk, total level, deadman, seasonal and other restricted worlds, and
			// F2P-only accounts only get F2P worlds.
			int world = Rs2WorldUtil.getRandomAccessibleWorld(true, true, spot.isMembers());
			if (world > 0 && world != current && !avoid.contains(world))
			{
				return world;
			}
		}
		return -1;
	}

	// ---------------------------------------------------------------------------------------------
	// Chopping
	// ---------------------------------------------------------------------------------------------

	private boolean pickNest()
	{
		Rs2TileItemModel nest = Microbot.getRs2TileItemCache().query().withName("Bird nest").nearestOnClientThread(8);
		if (nest == null)
		{
			return false;
		}
		status = "Picking up bird nest";
		if (nest.pickup())
		{
			sleepUntil(() -> !Rs2Player.isMoving(), 4_000);
			return true;
		}
		return false;
	}

	private void handleFull()
	{
		if (config.mode() == ChopMode.POWER_CHOP)
		{
			status = "Dropping logs";
			Rs2Inventory.dropAll(item -> item.getName() != null && item.getName().toLowerCase().endsWith("logs"));
			return;
		}
		status = "Banking logs";
		if (!Rs2Bank.isOpen())
		{
			Rs2Bank.walkToBankAndUseBank();
		}
		if (Rs2Bank.isOpen())
		{
			Rs2Bank.depositAllExcept(AXE_IDS);
			sleepUntil(() -> !Rs2Inventory.isFull(), 3_000);
			Rs2Bank.closeBank();
		}
	}

	private void chop()
	{
		if (Rs2Player.isAnimating(1800) || Rs2Player.isMoving())
		{
			status = "Chopping " + plan.tree.getObjectName();
			return;
		}
		Spot spot = activeSpot();
		WorldPoint me = Rs2Player.getWorldLocation();
		long now = System.currentTimeMillis();
		blacklist.values().removeIf(until -> until < now);

		List<Rs2TileObjectModel> found = Microbot.getRs2TileObjectCache().query()
			.withName(plan.tree.getObjectName()).toListOnClientThread();
		Rs2TileObjectModel best = nearest(found, me, spot, false);
		if (best == null)
		{
			if (me == null || !spot.contains(me) && spot.center().distanceTo2D(me) > 6)
			{
				status = "Walking to " + spot.getLabel();
				Rs2Walker.walkTo(spot.center(), 4);
				return;
			}
			// The rectangle may be slightly off: take any matching tree close to us.
			best = nearest(found, me, spot, true);
			if (best == null)
			{
				status = "Waiting for " + plan.tree.getObjectName() + " to respawn";
				return;
			}
		}
		status = "Chopping " + plan.tree.getObjectName();
		WorldPoint at = best.getWorldLocation();
		if (at.equals(lastTarget))
		{
			failedClicks++;
		}
		else
		{
			lastTarget = at;
			failedClicks = 0;
		}
		if (failedClicks >= 4)
		{
			blacklist.put(at, now + 60_000);
			failedClicks = 0;
			return;
		}
		if (best.click("Chop down"))
		{
			sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isMoving(), 3_000);
			Rs2Antiban.actionCooldown();
			Rs2Antiban.takeMicroBreakByChance();
		}
	}

	private Rs2TileObjectModel nearest(List<Rs2TileObjectModel> trees, WorldPoint me, Spot spot, boolean anywhereNearby)
	{
		Rs2TileObjectModel best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Rs2TileObjectModel tree : trees)
		{
			WorldPoint at = tree.getWorldLocation();
			if (blacklist.containsKey(at))
			{
				continue;
			}
			if (anywhereNearby ? (me == null || at.distanceTo2D(me) > 20) : !spot.contains(at))
			{
				continue;
			}
			int distance = me == null ? 0 : at.distanceTo2D(me);
			if (distance < bestDistance)
			{
				bestDistance = distance;
				best = tree;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------------------------------------
	// Overlay getters
	// ---------------------------------------------------------------------------------------------

	/** The spot to be at now: the rotation's pick when rotating, otherwise the planned spot. */
	private Spot activeSpot()
	{
		Spot spot = config.rotateSpots() && plan.pool.size() > 1 ? rotation.current(plan.pool, activeMillis) : plan.spot;
		if (spot != currentSpot)
		{
			if (currentSpot != null && spot != null)
			{
				Microbot.log("DD Chopper: moving on foot to " + spot.getLabel());
			}
			currentSpot = spot;
		}
		return spot;
	}

	/** Time left at the current spot, or empty when not rotating. */
	public String getSpotTimeLeft()
	{
		if (!config.rotateSpots() || plan == null || plan.pool.size() <= 1)
		{
			return "-";
		}
		long ms = rotation.remainingMillis(activeMillis);
		return String.format("%d:%02d left", ms / 3_600_000L * 60 + ms / 60_000L % 60, ms / 1000 % 60);
	}

	public int getCurrentLevel()
	{
		return currentLevel;
	}

	public long getXpGained()
	{
		return xpGained;
	}

	public long getXpPerHour()
	{
		return xpPerHour;
	}

	public String getStatus()
	{
		return status;
	}

	public String getTreeLabel()
	{
		return currentTree == null ? "-" : currentTree.getObjectName()
			+ (currentSpot == null ? "" : " @ " + currentSpot.getLabel().replaceFirst("^[^:]*: ", ""));
	}

	public String getAxeLabel()
	{
		return currentAxe == null ? "-" : currentAxe.getItemName();
	}
}
