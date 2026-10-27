package net.runelite.client.plugins.microbot.ddchopper;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.ddchopper.forestry.ForestryHandler;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Singleton;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * DD Chopper main loop. One 600 ms tick: goal check, axe check (on start and every level-up), tree/spot plan, crowd
 * check, then drop/bank when full or chop. Blocking events (random events, login, break handler pauses) are handled
 * by the {@link Script#run()} gate. The steps themselves live in {@link AxeSupplier}, {@link CrowdHopper},
 * {@link LogHandler}, {@link TreeChopper}, {@link BreakSync} and {@link ForestryHandler}.
 */
@Slf4j
@Singleton
public class DDChopperScript extends Script
{
	private static final int TICK_MS = 600;
	/** Cap on the time one tick adds to the active-time clock, so a stalled client does not count as playtime. */
	private static final long MAX_ACTIVE_STEP_MS = 5_000;
	/** XP/hour is extrapolated over at least this long so the first minutes do not show absurd rates. */
	private static final long MIN_XP_RATE_WINDOW_MS = 60_000;
	private static final double MS_PER_HOUR = 3_600_000.0;
	private static final long MS_PER_MINUTE = 60_000L;
	private static final int FORESTRY_PAUSE_MIN_MS = 400;
	private static final int FORESTRY_PAUSE_MAX_MS = 700;

	private DDChopperConfig config;

	// What the overlay and panel show.
	private volatile int currentLevel;
	private volatile long xpGained;
	private volatile long currentXp;
	private volatile long xpPerHour;
	private volatile String status = "Idle";
	private volatile TreeType currentTree;
	private volatile Spot currentSpot;
	private volatile boolean paused;
	private volatile boolean running;
	private volatile String blockReason = "";
	/** A problem the script works around while it keeps running, shown next to RUNNING in the panel. */
	private volatile String notice = "";

	// Run state.
	private volatile long activeMillis;
	private long lastTickMillis;
	private int lastLevel = -1;
	private int startXp = -1;
	private boolean axeCheck = true;
	private boolean replan = true;
	private ChopPlanner.Plan plan;
	private String lastNote = "";
	private boolean finished;

	private final WalkerOverrides walkerOverrides = new WalkerOverrides();
	private final ForestryHandler forestry = new ForestryHandler();
	private HumanPacing pacing;
	private AxeSupplier axes;
	private CrowdHopper crowd;
	private LogHandler logs;
	private TreeChopper chopper;
	private BreakSync breaks;
	private SpotRotation rotation;

	public boolean run(DDChopperConfig config)
	{
		this.config = config;
		resetState();
		Rs2Antiban.resetAntibanSettings();
		Rs2Antiban.antibanSetupTemplates.applyWoodcuttingSetup();
		Rs2AntibanSettings.naturalMouse = true;
		// No surprise boats, spirit trees or teleports: spots are reached on foot or not at all.
		Rs2Walker.disableTeleports = true;
		walkerOverrides.apply();
		pacing = new HumanPacing(new Random(), config.humanizedTiming(), config.humanizedFatigue(), config.idlePauseChance());
		BankTrip bank = new BankTrip(() -> notice = "Bank unavailable - dropping logs");
		axes = new AxeSupplier(config, this::setStatus, bank);
		crowd = new CrowdHopper(config, this::setStatus);
		logs = new LogHandler(config, pacing, this::setStatus, this::isRunning, bank);
		chopper = new TreeChopper(pacing, this::setStatus);
		breaks = new BreakSync(config);
		rotation = new SpotRotation(new Random(), config.rotateMinMinutes(), config.rotateMaxMinutes());
		forestry.reset();
		running = true;
		paused = false;
		blockReason = "";
		notice = "";
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::loop, 0, TICK_MS, TimeUnit.MILLISECONDS);
		return true;
	}

	private void resetState()
	{
		activeMillis = 0;
		lastTickMillis = 0;
		lastLevel = -1;
		startXp = -1;
		axeCheck = true;
		replan = true;
		plan = null;
		lastNote = "";
		finished = false;
		currentTree = null;
		currentSpot = null;
		xpGained = 0;
		currentXp = 0;
		xpPerHour = 0;
		status = "Starting";
	}

	@Override
	public void shutdown()
	{
		super.shutdown();
		running = false;
		Rs2Walker.disableTeleports = false;
		walkerOverrides.clear();
		Rs2Antiban.resetAntibanSettings();
		status = "Stopped";
	}

	private void setStatus(String text)
	{
		status = text;
	}

	private void loop()
	{
		try
		{
			if (finished || !super.run() || !Microbot.isLoggedIn())
			{
				return;
			}
			if (paused)
			{
				status = "Paused";
				return;
			}
			trackActiveTime();
			if (Microbot.isHopping())
			{
				status = "Hopping worlds";
				return;
			}

			int level = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
			currentLevel = level;
			updateXp();
			noteLevelChange(level);

			String reason = GoalTracker.stopReason(config.goalType(), level, config.targetLevel(), activeMillis,
				GoalTracker.runLimitMillis(config.runHours(), config.runMinutes()));
			if (reason != null)
			{
				finish(reason);
				return;
			}

			breaks.sync();

			if (axeCheck && !secureAxe(level))
			{
				return;
			}
			if (replan || plan == null)
			{
				replan(level);
			}
			if (plan.spot == null)
			{
				finish("no usable woodcutting spot for level " + level);
				return;
			}
			crowd.refresh();
			if (crowd.isActive() && crowd.checkAndHop(activeSpot()))
			{
				return;
			}
			if (Rs2Inventory.isFull())
			{
				logs.handleFull();
				return;
			}
			if (config.pickupNests() && chopper.pickUpNest())
			{
				return;
			}
			if (forestry.tick(config))
			{
				status = "Forestry: " + forestry.lastEvent();
				sleep(FORESTRY_PAUSE_MIN_MS, FORESTRY_PAUSE_MAX_MS);
				return;
			}
			chopper.chop(plan.tree, this::activeSpot);
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

	/** Advances the active-time clock and the human pacing model. */
	private void trackActiveTime()
	{
		long now = System.currentTimeMillis();
		if (lastTickMillis > 0)
		{
			activeMillis += Math.min(now - lastTickMillis, MAX_ACTIVE_STEP_MS);
		}
		lastTickMillis = now;
		if (pacing != null && pacing.isEnabled())
		{
			pacing.update(now, BreakHandlerScript.isBreakActive());
			Rs2Antiban.mouseFatigue.noiseAmplitude = pacing.mouseNoiseAmplitude();
		}
	}

	private void noteLevelChange(int level)
	{
		if (level == lastLevel)
		{
			return;
		}
		if (lastLevel > 0)
		{
			Microbot.log("DD Chopper: level up to " + level + ", re-checking axe and tree");
		}
		lastLevel = level;
		axeCheck = true;
		replan = true;
	}

	private void updateXp()
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
		currentXp = xp;
		xpGained = xp - startXp;
		double hours = Math.max(activeMillis, MIN_XP_RATE_WINDOW_MS) / MS_PER_HOUR;
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

	/** Runs one axe step; true once an axe is ready and the tick can carry on. */
	private boolean secureAxe(int level)
	{
		switch (axes.check(level))
		{
			case READY:
				axeCheck = false;
				blockReason = "";
				replan = true;
				return true;
			case MISSING:
				blockReason = "No axe equipped/in inventory";
				finish("no usable axe for level " + level + (config.buyAxe() ? " and none could be bought" : ""));
				return false;
			default:
				return false;
		}
	}

	private void replan(int level)
	{
		ChopPlanner.Input in = new ChopPlanner.Input();
		in.level = level;
		Axe current = axes.currentAxe();
		in.axe = current != null ? current : Axe.BRONZE;
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

	// ---- Controls used by the side panel ----

	public void pause()
	{
		paused = true;
	}

	public void resume()
	{
		paused = false;
	}

	/** Stops the loop but leaves the plugin (and its panel) loaded. */
	public void stopRun()
	{
		shutdown();
		blockReason = "Stopped from the panel";
	}

	/** Starts the loop again after {@link #stopRun()} or a finished goal. */
	public void startRun(DDChopperConfig cfg)
	{
		if (!running)
		{
			run(cfg);
		}
		paused = false;
	}

	// ---- Getters for the overlay and panel ----

	public String getBlockReason()
	{
		return finished ? status : blockReason;
	}

	public String getNotice()
	{
		return notice;
	}

	public RuntimeStatus.State getRunState()
	{
		return RuntimeStatus.state(running && !finished, paused, config != null && config.enableBreaks() && BreakHandlerScript.isBreakActive());
	}

	public String getRunStatusLine()
	{
		RuntimeStatus.State state = getRunState();
		String reason = state == RuntimeStatus.State.STOPPED && finished ? status : (state == RuntimeStatus.State.RUNNING ? notice : blockReason);
		return RuntimeStatus.describe(state, reason);
	}

	/** Overlay text for the break countdown. */
	public String getBreakText()
	{
		return breaks == null ? "Off" : breaks.countdownText();
	}

	/** Time left at the current spot, or "-" when not rotating. */
	public String getSpotTimeLeft()
	{
		if (config == null || !config.rotateSpots() || plan == null || plan.pool.size() <= 1)
		{
			return "-";
		}
		long ms = rotation.remainingMillis(activeMillis);
		return String.format("%d:%02d left", ms / MS_PER_MINUTE, ms / 1000 % 60);
	}

	/** Time to the next Woodcutting level at the current XP rate, formatted; a dash when unknown. */
	public String getNextLevelEta()
	{
		return XpEta.format(XpEta.millisToNextLevel(currentLevel, currentXp, xpPerHour));
	}

	/** Label for {@link #getGoalEta()} (for example "Lvl 35 in"), or null when the goal has no estimate. */
	public String getGoalEtaLabel()
	{
		if (config == null)
		{
			return null;
		}
		switch (config.goalType())
		{
			case TARGET_LEVEL:
				return "Lvl " + config.targetLevel() + " in";
			case RUN_TIME:
				return "Run time left";
			default:
				return null;
		}
	}

	/** Time to the goal, formatted; a dash when unknown. Only meaningful when {@link #getGoalEtaLabel()} is set. */
	public String getGoalEta()
	{
		if (config == null)
		{
			return XpEta.UNKNOWN;
		}
		if (config.goalType() == GoalType.RUN_TIME)
		{
			long limit = GoalTracker.runLimitMillis(config.runHours(), config.runMinutes());
			return XpEta.format(Math.max(0, limit - activeMillis));
		}
		return XpEta.format(XpEta.millisToLevel(config.targetLevel(), currentXp, xpPerHour));
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
		Axe axe = axes == null ? null : axes.currentAxe();
		return axe == null ? "-" : axe.getItemName();
	}
}
