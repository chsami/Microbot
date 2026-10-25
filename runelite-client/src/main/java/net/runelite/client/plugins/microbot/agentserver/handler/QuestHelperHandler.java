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
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatHandler;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatStore;
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

		result.put("awaitingSetup", QuestCombatHandler.isAwaitingSetup());
		result.put("preflight", QuestPreflight.getStatus());

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
		// The quest script reads this when it sees the start, so it applies to this start only
		QuestPreflight.skipNextStart(Boolean.FALSE.equals(preflight));
		if (!plugin.startQuestHelper(name))
		{
			QuestPreflight.skipNextStart(false);
			sendJson(exchange, 404, errorResponse("Quest helper not found or not loaded: " + name));
			return;
		}

		Microbot.getConfigManager().setConfiguration(
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

		boolean stopped = Microbot.getClientThread()
			.runOnClientThreadOptional(() ->
			{
				plugin.getQuestManager().shutDownQuest(true);
				Microbot.getConfigManager().setConfiguration(
					QuestHelperConfig.QUEST_HELPER_GROUP,
					"TurnOn",
					false);
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
