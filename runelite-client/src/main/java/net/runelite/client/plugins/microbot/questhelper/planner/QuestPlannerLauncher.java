package net.runelite.client.plugins.microbot.questhelper.planner;

import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.api.QuestState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.QuestPreflight;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatDetector;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatStore;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import org.slf4j.event.Level;

/**
 * Glue between the Quest Helper plugin and the quest window: lists the quests and why some are unavailable, opens the
 * window (the planner, or one quest about to start), stores the per-quest profiles as loadouts and starts the run.
 */
public final class QuestPlannerLauncher
{
	/** An Agent Server start flips TurnOn right after selecting its quest; the planner must not pop up for it. */
	private static final long EXTERNAL_START_GRACE_MS = 10_000L;

	private static final AtomicBoolean PLANNER_OPEN = new AtomicBoolean(false);
	private static volatile long externalStartMs;

	private QuestPlannerLauncher()
	{
	}

	/** Called by the Agent Server before it starts a quest or a queue itself. */
	public static void noteExternalStart()
	{
		externalStartMs = System.currentTimeMillis();
	}

	/** Called when TurnOn becomes true. Opens the planner when no quest is selected and no queue is running. */
	public static void onTurnOn(QuestHelperPlugin plugin)
	{
		if (plugin == null || QuestQueue.shared().isActive() || plugin.getSelectedQuest() != null
			|| System.currentTimeMillis() - externalStartMs < EXTERNAL_START_GRACE_MS)
		{
			return;
		}
		open(plugin);
	}

	/** Opens the planner (also from the config panel). One planner at a time. */
	public static void open(QuestHelperPlugin plugin)
	{
		if (!PLANNER_OPEN.compareAndSet(false, true))
		{
			return;
		}
		Thread thread = new Thread(() ->
		{
			try
			{
				List<QuestPlannerFilter.Row> rows = buildRows();
				SwingUtilities.invokeLater(() -> QuestWindow.openPlanner(plugin, rows, result ->
				{
					PLANNER_OPEN.set(false);
					if (result != null)
					{
						startRun(plugin, result);
					}
				}));
			}
			catch (RuntimeException e)
			{
				PLANNER_OPEN.set(false);
				Microbot.log("Quest planner: could not open: " + e, Level.ERROR);
			}
		}, "quest-planner-open");
		thread.setDaemon(true);
		thread.start();
	}

	/** Config-panel entry point: the quest window for the selected quest; Start saves its profile and setup. */
	public static void openForSelected(QuestHelperPlugin plugin)
	{
		QuestHelper selected = plugin == null ? null : plugin.getSelectedQuest();
		if (selected == null)
		{
			Microbot.log("Quest helper: select a quest first to open its setup window.", Level.INFO);
			return;
		}
		openForQuest(selected.getQuest().getName(), result -> { });
	}

	/**
	 * Opens the window for one quest about to start, prefilled from its saved profile. {@code done} runs on the EDT
	 * with the result, or null when the window was closed without Start. Safe from any thread.
	 */
	public static void openForQuest(String questName, Consumer<QuestWindow.Result> done)
	{
		QuestHelperPlugin plugin = Microbot.getPlugin(QuestHelperPlugin.class);
		Thread thread = new Thread(() ->
		{
			try
			{
				QuestProfile profile = profileFor(questName);
				SwingUtilities.invokeLater(() -> QuestWindow.openForQuest(plugin, profile, done));
			}
			catch (RuntimeException e)
			{
				Microbot.log("Quest window: could not open for " + questName + ": " + e, Level.ERROR);
				SwingUtilities.invokeLater(() -> done.accept(null));
			}
		}, "quest-window-open");
		thread.setDaemon(true);
		thread.start();
	}

	/** Every quest with whether the player can do it now and, if not, why. Call off the client thread. */
	public static List<QuestPlannerFilter.Row> buildRows()
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			List<QuestPlannerFilter.Row> rows = new ArrayList<>();
			for (QuestHelper helper : QuestHelperQuest.getQuestHelpers(false))
			{
				if (helper == null || helper.getQuest() == null)
				{
					continue;
				}
				rows.add(rowOf(helper));
			}
			rows.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
			return rows;
		}).orElse(new ArrayList<>());
	}

	/** Client thread only. */
	private static QuestPlannerFilter.Row rowOf(QuestHelper helper)
	{
		String name = helper.getQuest().getName();
		try
		{
			QuestState state = helper.getState(Microbot.getClient());
			if (state == QuestState.FINISHED)
			{
				return new QuestPlannerFilter.Row(name, false, "Already completed");
			}
			helper.initializeRequirements();
			List<String> unmet = new ArrayList<>();
			List<Requirement> general = helper.getGeneralRequirements();
			if (general != null)
			{
				for (Requirement requirement : general)
				{
					if (requirement != null && requirement.shouldConsiderForFilter()
						&& !requirement.check(Microbot.getClient()))
					{
						unmet.add(requirement.getDisplayText());
					}
				}
			}
			String reason = QuestPlannerFilter.reasonOf(unmet, 3);
			String other = QuestPreflight.anotherPlayerFailure(helper.getQuest().name(), name);
			if (other != null && reason.isEmpty())
			{
				reason = other.substring(QuestPreflight.FAILED_PREFIX.length());
			}
			return new QuestPlannerFilter.Row(name, reason.isEmpty(), reason);
		}
		catch (RuntimeException e)
		{
			return new QuestPlannerFilter.Row(name, false, "Could not check requirements");
		}
	}

	/** The suggested profile for a quest: Combat when Quest Helper finds fights, else Travel. Off the client thread. */
	public static QuestProfile suggestedProfile(String questName)
	{
		QuestHelper helper = QuestHelperQuest.getByName(questName);
		boolean combat = false;
		int food = QuestProfile.DEFAULT_COMBAT_FOOD_QTY;
		if (helper != null)
		{
			try
			{
				combat = QuestCombatDetector.hasFights(helper);
				if (combat)
				{
					food = QuestCombatDetector.defaultFoodQty(helper);
				}
			}
			catch (RuntimeException e)
			{
				combat = false;
			}
		}
		return QuestQueue.shared().applyTemplate(QuestProfile.defaultFor(questName, combat, food));
	}

	/** The profile to run a quest with: the saved one, else the suggested default. */
	public static QuestProfile profileFor(String questName)
	{
		QuestProfile saved = QuestQueue.shared().savedProfile(questName);
		return saved != null ? saved : suggestedProfile(questName);
	}

	/** The default for "keep at least N stamina potions": the config value, else the queue's. */
	public static int globalMinStamina()
	{
		ConfigManager manager = Microbot.getConfigManager();
		String value = manager == null ? null : manager.getConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "minStaminaPotions");
		if (value != null)
		{
			try
			{
				return Math.max(0, Math.min(QuestProfile.MAX_MIN_STAMINA, Integer.parseInt(value.trim())));
			}
			catch (NumberFormatException e)
			{
				// fall through to the queue's value
			}
		}
		return QuestQueue.shared().defaultMinStamina();
	}

	public static void setGlobalMinStamina(int value)
	{
		int clamped = Math.max(0, Math.min(QuestProfile.MAX_MIN_STAMINA, value));
		QuestQueue.shared().setDefaultMinStamina(clamped);
		ConfigManager manager = Microbot.getConfigManager();
		if (manager != null)
		{
			manager.setConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "minStaminaPotions", clamped);
		}
	}

	/** Writes the profile's loadout into the per-quest store, so the quest runs with it and never asks. */
	public static void applyProfile(QuestProfile profile)
	{
		QuestHelper helper = QuestHelperQuest.getByName(profile.getQuestName());
		if (helper == null || helper.getQuest() == null)
		{
			return;
		}
		String key = helper.getQuest().name();
		QuestCombatLoadout loadout = profile.loadoutFor(QuestCombatStore.load(key), QuestCombatStore.loadGlobal(),
			globalMinStamina());
		QuestCombatStore.save(key, loadout);
	}

	/** Starts the queue the window built: the window already saved the profiles and loadouts. */
	public static boolean startRun(QuestHelperPlugin plugin, QuestWindow.Result result)
	{
		return startRun(plugin, result.profiles, result.approval);
	}

	/** Starts a queue (fixed or random) with the given approval; null keeps the normal per-quest confirmation. */
	public static boolean startRun(QuestHelperPlugin plugin, List<QuestProfile> entries, QuestApproval approval)
	{
		return startRun(plugin, entries, approval, true);
	}

	/**
	 * As above. With {@code resolve} the unmet requirements of each quest (prerequisite quests, training, boosts) are
	 * queued in front of it now and re-checked before each quest starts; quests that cannot be automated are dropped
	 * and logged.
	 */
	public static boolean startRun(QuestHelperPlugin plugin, List<QuestProfile> wanted, QuestApproval approval,
		boolean resolve)
	{
		QuestQueue queue = QuestQueue.shared();
		List<QuestProfile> entries = wanted;
		if (resolve && !queue.isRandom())
		{
			QuestRequirementPlanner.Expansion expansion = QuestRequirementPlanner.expand(wanted);
			for (String blocker : expansion.blockers)
			{
				Microbot.log("Quest planner: left out, cannot be automated: " + blocker, Level.WARN);
			}
			entries = expansion.entries;
			if (approval != null)
			{
				QuestRequirementPlanner.extendApproval(approval, entries);
			}
		}
		queue.setResolveRequirements(resolve);
		queue.setEntries(entries);
		for (QuestProfile profile : entries)
		{
			applyProfile(profile);
		}
		queue.setApproval(approval);
		if (!queue.start())
		{
			return false;
		}
		noteExternalStart();
		Microbot.getConfigManager().setConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "TurnOn", true);
		Microbot.getConfigManager().setRSProfileConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "TurnOn", true);
		return true;
	}

	/** Swing owner for dialogs: the RuneLite window. */
	static Frame clientFrame()
	{
		Frame best = null;
		for (Frame frame : Frame.getFrames())
		{
			if (!frame.isShowing())
			{
				continue;
			}
			if (frame.getTitle() != null && frame.getTitle().toLowerCase(Locale.ROOT).contains("runelite"))
			{
				return frame;
			}
			if (best == null)
			{
				best = frame;
			}
		}
		return best;
	}
}
