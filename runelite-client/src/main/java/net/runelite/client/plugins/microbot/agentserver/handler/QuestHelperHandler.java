package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.QuestPreflight;
import net.runelite.client.plugins.microbot.questhelper.QuestPurchasePlan;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatStore;
import net.runelite.client.plugins.microbot.questhelper.QuestPause;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestApproval;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestWindow;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestPlannerLauncher;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestQueue;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestQueueRequest;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestRequirementPlanner;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestRequirementResolver;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;

public class QuestHelperHandler extends AgentHandler
{
	private static final String BASE_PATH = "/quest-helper";

	public QuestHelperHandler(Gson gson)
	{
		super(gson);
	}

	@Override
	public String getPath()
	{
		return BASE_PATH;
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException
	{
		String sub = getSubPath(exchange, BASE_PATH);

		switch (sub)
		{
			case "/status":
				handleStatus(exchange);
				break;
			case "/start":
				handleStart(exchange);
				break;
			case "/stop":
				handleStop(exchange);
				break;
			case "/queue":
				handleQueue(exchange);
				break;
			case "/requirements":
				handleRequirements(exchange);
				break;
			case "/pause":
				handlePause(exchange, true);
				break;
			case "/resume":
				handlePause(exchange, false);
				break;
			case "/list":
			case "":
			case "/":
				handleList(exchange);
				break;
			case "/states":
				handleStates(exchange);
				break;
			default:
				sendJson(exchange, 404, errorResponse("Unknown endpoint: " + BASE_PATH + sub));
		}
	}

	private void handleStatus(HttpExchange exchange) throws IOException
	{
		try
		{
			requireGet(exchange);
		}
		catch (HttpMethodException e)
		{
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		QuestHelperPlugin plugin = Microbot.getPlugin(QuestHelperPlugin.class);
		if (plugin == null)
		{
			sendJson(exchange, 404, errorResponse("Quest Helper plugin not found"));
			return;
		}

		Map<String, Object> result = new LinkedHashMap<>();
		PluginManager pluginManager = Microbot.getPluginManager();
		result.put("active", pluginManager != null && pluginManager.isActive(plugin));
		result.put("enabled", pluginManager != null && pluginManager.isPluginEnabled(plugin));
		result.put("turnOn", Microbot.getConfigManager()
			.getConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "TurnOn"));

		result.put("paused", QuestPause.isPaused());
		result.put("fullyAutomated", Microbot.getConfigManager()
			.getConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "fullyAutomated"));
		result.put("maxSpendPerQuest", Microbot.getConfigManager()
			.getConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "maxSpendPerQuest"));
		result.put("queueActive", QuestQueue.shared().isActive());
		result.put("awaitingSetup", QuestCombatHandler.isAwaitingSetup());
		result.put("stuckStage", net.runelite.client.plugins.microbot.questhelper.QuestScript.getStuckStage());
		result.put("stuckForMs", net.runelite.client.plugins.microbot.questhelper.QuestScript.getStuckForMs());
		result.put("preflight", QuestPreflight.getStatus());
		result.put("awaitingPurchaseConfirm", QuestPurchasePlan.isAwaitingConfirmation());
		QuestPurchasePlan plannedPurchases = QuestPurchasePlan.current();
		if (plannedPurchases != null)
		{
			result.put("plannedPurchases", plannedPurchases.toStatus());
		}

		QuestHelper selectedQuest = plugin.getSelectedQuest();
		result.put("selected", selectedQuest != null);
		if (selectedQuest != null)
		{
			result.put("questName", selectedQuest.getQuest().getName());
			result.put("completed", Microbot.getClientThread()
				.runOnClientThreadOptional(selectedQuest::isCompleted)
				.orElse(false));

			QuestStep currentStep = selectedQuest.getCurrentStep();
			if (currentStep != null)
			{
				QuestStep activeStep = currentStep.getActiveStep();
				result.put("stepText", activeStep != null ? activeStep.getText() : currentStep.getText());
				result.put("stepClass", activeStep != null
					? activeStep.getClass().getSimpleName()
					: currentStep.getClass().getSimpleName());
			}
		}

		sendJson(exchange, 200, result);
	}

	/** GET /quest-helper/requirements?name=...: the plan to meet a quest's requirements (nothing is started). */
	private void handleRequirements(HttpExchange exchange) throws IOException
	{
		try
		{
			requireGet(exchange);
		}
		catch (HttpMethodException e)
		{
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}
		String name = parseQuery(exchange.getRequestURI()).get("name");
		if (name == null || name.isEmpty())
		{
			sendJson(exchange, 400, errorResponse("name is required"));
			return;
		}
		QuestRequirementResolver.Plan plan = QuestRequirementPlanner.plan(name);
		if (plan == null)
		{
			sendJson(exchange, 404, errorResponse("Unknown quest: " + name));
			return;
		}
		Map<String, Object> result = QuestRequirementPlanner.toMap(plan);
		result.put("skillPlugins", QuestRequirementPlanner.skillPlugins().serialise());
		result.put("builtInTrainers", net.runelite.client.plugins.microbot.questhelper.training.BuiltInTrainers.names());
		sendJson(exchange, 200, result);
	}

	private void handleStart(HttpExchange exchange) throws IOException
	{
		try
		{
			requirePost(exchange);
		}
		catch (HttpMethodException e)
		{
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		Map<String, Object> body = readJsonBody(exchange);
		String name = getStringField(body, "name");
		if (name == null || name.isEmpty())
		{
			sendJson(exchange, 400, errorResponse("name is required"));
			return;
		}

		QuestHelperPlugin plugin = Microbot.getPlugin(QuestHelperPlugin.class);
		if (plugin == null)
		{
			sendJson(exchange, 404, errorResponse("Quest Helper plugin not found"));
			return;
		}

		PluginManager pluginManager = Microbot.getPluginManager();
		if (pluginManager == null || !pluginManager.isActive(plugin))
		{
			sendJson(exchange, 409, errorResponse("Quest Helper plugin is not active"));
			return;
		}

		Object resolveRaw = body.get("resolveRequirements");
		boolean resolve = !(resolveRaw instanceof Boolean) || (Boolean) resolveRaw;
		if (resolve && helperKnown(name))
		{
			QuestRequirementResolver.Plan plan = QuestRequirementPlanner.plan(name);
			if (plan != null && plan.hasBlockers())
			{
				Map<String, Object> blocked = QuestRequirementPlanner.toMap(plan);
				blocked.put("success", false);
				blocked.put("error", "The requirements cannot be met automatically");
				sendJson(exchange, 409, blocked);
				return;
			}
			if (plan != null && plan.hasPrerequisites())
			{
				List<QuestProfile> entries = QuestRequirementPlanner.entriesOf(plan,
					QuestPlannerLauncher.profileFor(name), new java.util.HashSet<>());
				QuestQueue.shared().stop();
				if (!QuestPlannerLauncher.startRun(plugin, entries, QuestApproval.everything(), false))
				{
					sendJson(exchange, 400, errorResponse("Nothing to run"));
					return;
				}
				QuestQueue.shared().setResolveRequirements(true);
				Map<String, Object> started = QuestRequirementPlanner.toMap(plan);
				started.put("success", true);
				started.put("questName", name);
				started.put("queued", true);
				sendJson(exchange, 200, started);
				return;
			}
		}

		QuestHelperConfig.ObtainMissingItemsOption obtainOption = null;
		Object obtainRaw = body.get("obtainMissingItems");
		if (obtainRaw != null)
		{
			if (obtainRaw instanceof String)
			{
				try
				{
					obtainOption = QuestHelperConfig.ObtainMissingItemsOption
						.valueOf(((String) obtainRaw).trim().toUpperCase(java.util.Locale.ROOT));
				}
				catch (IllegalArgumentException e)
				{
					obtainOption = null;
				}
			}
			if (obtainOption == null)
			{
				sendJson(exchange, 400, errorResponse("obtainMissingItems must be YES, NO or ASK"));
				return;
			}
		}

		QuestHelper helper = QuestHelperQuest.getByName(name);
		Object combat = body.get("combat");
		boolean hasExtras = false;
		for (String field : new String[]{"gear", "inventory", "reserveSlots", "stamina", "fights"})
		{
			hasExtras |= body.get(field) != null;
		}
		if (helper != null && helper.getQuest() != null && (combat != null || hasExtras))
		{
			String questKey = helper.getQuest().name();
			QuestCombatLoadout loadout;
			if (combat == null)
			{
				QuestCombatLoadout saved = QuestCombatStore.load(questKey);
				loadout = saved != null ? saved : QuestCombatLoadout.bare();
			}
			else if ("skip".equals(combat))
			{
				loadout = QuestCombatLoadout.skipped();
			}
			else if (combat instanceof Map)
			{
				try
				{
					loadout = QuestCombatLoadout.fromMap((Map<?, ?>) combat);
				}
				catch (IllegalArgumentException e)
				{
					sendJson(exchange, 400, errorResponse(e.getMessage()));
					return;
				}
			}
			else
			{
				sendJson(exchange, 400, errorResponse("combat must be \"skip\" or an object"));
				return;
			}
			if (hasExtras)
			{
				try
				{
					loadout = QuestCombatLoadout.applyOverrides(loadout, body, QuestCombatHandler::wornGear);
				}
				catch (IllegalArgumentException e)
				{
					sendJson(exchange, 400, errorResponse(e.getMessage()));
					return;
				}
			}
			QuestCombatStore.save(questKey, loadout);
		}

		Object preflight = body.get("preflight");
		if (preflight != null && !(preflight instanceof Boolean))
		{
			sendJson(exchange, 400, errorResponse("preflight must be true or false"));
			return;
		}
		Object confirmPurchases = body.get("confirmPurchases");
		if (confirmPurchases != null && !(confirmPurchases instanceof Boolean))
		{
			sendJson(exchange, 400, errorResponse("confirmPurchases must be true or false"));
			return;
		}
		if (obtainOption != null)
		{
			Microbot.getConfigManager().setConfiguration(
				QuestHelperConfig.QUEST_HELPER_GROUP,
				"obtainMissingItems",
				obtainOption);
		}
		// a single start replaces any queue that was running
		QuestQueue.shared().stop();
		QuestPlannerLauncher.noteExternalStart();
		// The quest script reads this when it sees the start, so it applies to this start only
		QuestPreflight.skipNextStart(Boolean.FALSE.equals(preflight));
		QuestPurchasePlan.overrideNextStart((Boolean) confirmPurchases);
		if (!plugin.startQuestHelper(name))
		{
			QuestPreflight.skipNextStart(false);
			QuestPurchasePlan.overrideNextStart(null);
			sendJson(exchange, 404, errorResponse("Quest helper not found or not loaded: " + name));
			return;
		}

		// Global key drives the toolbar toggle; the profile key is what Quest Helper itself reads
		Microbot.getConfigManager().setConfiguration(
			QuestHelperConfig.QUEST_HELPER_GROUP,
			"TurnOn",
			true);
		Microbot.getConfigManager().setRSProfileConfiguration(
			QuestHelperConfig.QUEST_HELPER_GROUP,
			"TurnOn",
			true);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("questName", name);
		sendJson(exchange, 200, result);
	}

	private void handleStop(HttpExchange exchange) throws IOException
	{
		try
		{
			requirePost(exchange);
		}
		catch (HttpMethodException e)
		{
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		QuestHelperPlugin plugin = Microbot.getPlugin(QuestHelperPlugin.class);
		if (plugin == null)
		{
			sendJson(exchange, 404, errorResponse("Quest Helper plugin not found"));
			return;
		}

		QuestQueue.shared().stop();
		QuestPause.resume();
		QuestWindow.dismiss();
		boolean stopped = Microbot.getClientThread()
			.runOnClientThreadOptional(() ->
			{
				plugin.getQuestManager().shutDownQuest(true);
				if (Microbot.getConfigManager() != null)
				{
					Microbot.getConfigManager().setConfiguration(
						QuestHelperConfig.QUEST_HELPER_GROUP,
						"TurnOn",
						false);
				}
				return true;
			})
			.orElse(false);
		if (!stopped)
		{
			sendJson(exchange, 500, errorResponse("Failed to stop quest helper"));
			return;
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		sendJson(exchange, 200, result);
	}

	/** GET: the queue and its state. POST: replace the queue and settings, optionally start it. */
	private void handleQueue(HttpExchange exchange) throws IOException
	{
		QuestQueue queue = QuestQueue.shared();
		if ("GET".equalsIgnoreCase(exchange.getRequestMethod()))
		{
			sendJson(exchange, 200, queueStatus(queue));
			return;
		}
		try
		{
			requirePost(exchange);
		}
		catch (HttpMethodException e)
		{
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}
		Map<String, Object> body = readJsonBody(exchange);
		QuestQueueRequest request = QuestQueueRequest.parse(body, name -> QuestHelperQuest.getByName(name) != null,
			QuestPlannerLauncher::profileFor);
		if (request.error != null)
		{
			sendJson(exchange, 400, errorResponse(request.error));
			return;
		}
		if (request.fullyAutomated != null && Microbot.getConfigManager() != null)
		{
			Microbot.getConfigManager().setConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "fullyAutomated",
				request.fullyAutomated);
		}
		if (request.maxSpendPerQuest != null && Microbot.getConfigManager() != null)
		{
			Microbot.getConfigManager().setConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "maxSpendPerQuest",
				request.maxSpendPerQuest);
		}
		if (request.skillPlugins != null && Microbot.getConfigManager() != null)
		{
			Microbot.getConfigManager().setConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP,
				net.runelite.client.plugins.microbot.questhelper.training.SkillPluginMap.CONFIG_KEY,
				net.runelite.client.plugins.microbot.questhelper.training.SkillPluginMap.of(request.skillPlugins)
					.serialise());
		}
		Map<String, Object> planOut = new LinkedHashMap<>();
		if (request.clear)
		{
			queue.stop();
			queue.setEntries(new ArrayList<>());
		}
		if (request.mode != null)
		{
			queue.setMode(request.mode);
		}
		if (request.pool != null)
		{
			queue.setPool(request.pool);
		}
		if (request.pauseMin != null || request.pauseMax != null)
		{
			queue.setPause(request.pauseMin != null ? request.pauseMin : queue.pauseMinMinutes(),
				request.pauseMax != null ? request.pauseMax : queue.pauseMaxMinutes());
		}
		if (request.defaultMinStamina != null)
		{
			queue.setDefaultMinStamina(request.defaultMinStamina);
		}
		if (request.hasQuests)
		{
			queue.stop();
			List<QuestProfile> profiles = new ArrayList<>();
			for (QuestQueueRequest.Entry entry : request.entries)
			{
				profiles.add(entry.profile);
				QuestHelper helper = QuestHelperQuest.getByName(entry.profile.getQuestName());
				if (entry.combat != null && helper != null)
				{
					try
					{
						QuestCombatLoadout loadout = "skip".equals(entry.combat) ? QuestCombatLoadout.skipped()
							: QuestCombatLoadout.fromMap((Map<?, ?>) entry.combat);
						QuestCombatStore.save(helper.getQuest().name(), loadout);
					}
					catch (IllegalArgumentException e)
					{
						sendJson(exchange, 400, errorResponse(entry.profile.getQuestName() + ": " + e.getMessage()));
						return;
					}
				}
			}
			if (request.resolveRequirements && !profiles.isEmpty())
			{
				QuestRequirementPlanner.Expansion expansion = QuestRequirementPlanner.expand(profiles);
				for (Map.Entry<String, QuestRequirementResolver.Plan> plan : expansion.plans.entrySet())
				{
					planOut.put(plan.getKey(), QuestRequirementPlanner.toMap(plan.getValue()));
				}
				if (!expansion.blockers.isEmpty())
				{
					Map<String, Object> blocked = errorResponse("The requirements cannot be met automatically");
					blocked.put("blockers", expansion.blockers);
					blocked.put("plans", planOut);
					sendJson(exchange, 409, blocked);
					return;
				}
				profiles = expansion.entries;
			}
			queue.setEntries(profiles);
		}
		if (request.start)
		{
			QuestHelperPlugin plugin = Microbot.getPlugin(QuestHelperPlugin.class);
			PluginManager pluginManager = Microbot.getPluginManager();
			if (plugin == null || pluginManager == null || !pluginManager.isActive(plugin))
			{
				sendJson(exchange, 409, errorResponse("Quest Helper plugin is not active"));
				return;
			}
			List<QuestProfile> run = new ArrayList<>(queue.entries());
			if (!queue.isRandom() && run.isEmpty())
			{
				sendJson(exchange, 400, errorResponse("The queue is empty"));
				return;
			}
			if (!QuestPlannerLauncher.startRun(plugin, run,
				request.approve ? QuestApproval.everything() : null, false))
			{
				sendJson(exchange, 400, errorResponse("Nothing to run"));
				return;
			}
			queue.setResolveRequirements(request.resolveRequirements);
		}
		Map<String, Object> status = queueStatus(queue);
		if (!planOut.isEmpty())
		{
			status.put("plans", planOut);
		}
		sendJson(exchange, 200, status);
	}

	private static boolean helperKnown(String name)
	{
		return QuestHelperQuest.getByName(name) != null;
	}

	private void handlePause(HttpExchange exchange, boolean pause) throws IOException
	{
		try
		{
			requirePost(exchange);
		}
		catch (HttpMethodException e)
		{
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}
		if (pause)
		{
			QuestPause.pause();
		}
		else
		{
			QuestPause.resume();
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("paused", QuestPause.isPaused());
		sendJson(exchange, 200, result);
	}

	private static Map<String, Object> queueStatus(QuestQueue queue)
	{
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("paused", QuestPause.isPaused());
		out.put("active", queue.isActive());
		out.put("mode", queue.mode().name());
		out.put("index", queue.index());
		QuestProfile current = queue.current();
		out.put("current", current == null ? null : current.getQuestName());
		out.put("pool", queue.pool());
		out.put("skipped", new ArrayList<>(queue.skipped()));
		out.put("pauseMinMinutes", queue.pauseMinMinutes());
		out.put("pauseMaxMinutes", queue.pauseMaxMinutes());
		out.put("defaultMinStamina", queue.defaultMinStamina());
		List<Map<String, Object>> quests = new ArrayList<>();
		for (QuestProfile profile : queue.entries())
		{
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("name", profile.getQuestName());
			if (profile.isStep())
			{
				row.put("step", profile.getStepKind());
			}
			row.put("obtain", profile.getObtain().name());
			row.put("type", profile.getType().name());
			row.put("minStamina", profile.getMinStamina());
			row.put("staminaDoses", profile.getSupplies().isStaminaOn() ? profile.getSupplies().getStaminaQty() : 0);
			row.put("food", profile.getSupplies().getFood());
			row.put("foodQty", profile.getSupplies().getFoodQty());
			quests.add(row);
		}
		out.put("quests", quests);
		return out;
	}

	private void handleList(HttpExchange exchange) throws IOException
	{
		try
		{
			requireGet(exchange);
		}
		catch (HttpMethodException e)
		{
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		List<Map<String, Object>> quests = new ArrayList<>();
		for (QuestHelperQuest qhq : QuestHelperQuest.values())
		{
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("enum", qhq.name());
			entry.put("displayName", qhq.getName());
			quests.add(entry);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("count", quests.size());
		result.put("quests", quests);
		sendJson(exchange, 200, result);
	}

	private void handleStates(HttpExchange exchange) throws IOException
	{
		try
		{
			requireGet(exchange);
		}
		catch (HttpMethodException e)
		{
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		Client client = Microbot.getClient();
		if (client == null)
		{
			sendJson(exchange, 503, errorResponse("Client not available"));
			return;
		}

		List<Map<String, Object>> states = Microbot.getClientThread()
			.runOnClientThreadOptional(() ->
			{
				List<Map<String, Object>> out = new ArrayList<>();
				for (QuestHelperQuest qhq : QuestHelperQuest.values())
				{
					Map<String, Object> entry = new LinkedHashMap<>();
					entry.put("enum", qhq.name());
					entry.put("displayName", qhq.getName());
					try
					{
						QuestState state = qhq.getState(client);
						entry.put("state", state != null ? state.name() : "UNKNOWN");
					}
					catch (Exception ex)
					{
						entry.put("state", "ERROR");
						entry.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
					}
					out.add(entry);
				}
				return out;
			})
			.orElse(new ArrayList<>());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("count", states.size());
		result.put("quests", states);
		sendJson(exchange, 200, result);
	}
}
