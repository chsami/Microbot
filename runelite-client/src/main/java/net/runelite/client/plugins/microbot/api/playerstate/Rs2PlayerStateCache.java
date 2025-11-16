package net.runelite.client.plugins.microbot.api.playerstate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.annotations.Varbit;
import net.runelite.api.annotations.Varp;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches player state data such as quest states, varbits, and varps.
 * This cache is event-driven and automatically updates when game state changes.
 */
@Singleton
@Slf4j
public final class Rs2PlayerStateCache {
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private EventBus eventBus;

	@Getter
	private final ConcurrentHashMap<Integer, QuestState> quests = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Integer, Integer> varbits = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Integer, Integer> varps = new ConcurrentHashMap<>();

	volatile boolean questsPopulated = false;

	@Inject
	public Rs2PlayerStateCache(EventBus eventBus, Client client, ClientThread clientThread) {
		this.eventBus = eventBus;
		this.client = client;
		this.clientThread = clientThread;

		eventBus.register(this);
	}


	@Subscribe
	private void onGameStateChanged(GameStateChanged e) {
		if (e.getGameState() == GameState.LOGGED_IN) {
			populateQuests();
		}
		if (e.getGameState() == GameState.LOGIN_SCREEN) {
			questsPopulated = false;
			quests.clear();
			varbits.clear();
			varps.clear();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (questsPopulated) {
			updateQuest(event);
		}
		if (event.getVarbitId() != -1) {
			varbits.put(event.getVarbitId(), event.getValue());
		}
		if (event.getVarpId() != -1) {
			varps.put(event.getVarpId(), event.getValue());
		}
	}

	/**
	 * Update the quest state for a specific quest based on a varbit change event.
	 *
	 * @param event
	 */
	private void updateQuest(VarbitChanged event) {
		QuestHelperQuest quest = Arrays.stream(QuestHelperQuest.values())
				.filter(x -> x.getVarbit() != null)
				.filter(x -> x.getVarbit().getId() == event.getVarbitId())
				.findFirst()
				.orElse(null);

		if (quest != null) {
			QuestState questState = quest.getState(client);
			quests.put(quest.getId(), questState);
		}
	}

	/**
	 * Populate the quests map with the current quest states.
	 */
	private void populateQuests() {
		clientThread.invokeLater(() ->
		{
			for (Quest quest : Quest.values()) {
				QuestState questState = quest.getState(client);
				quests.put(quest.getId(), questState);
			}
			questsPopulated = true;
		});
	}

	/**
	 * Get the quest state for a specific quest.
	 *
	 * @param quest
	 * @return
	 */
	public QuestState getQuestState(Quest quest) {
		return quests.get(quest.getId());
	}

	/**
	 * Get the value of a specific varbit.
	 *
	 * @param varbitId
	 * @return
	 */
	public @Varbit int getVarbitValue(@Varbit int varbitId) {
		Integer cached = varbits.get(varbitId);

		if (cached != null) {
			return cached;
		}

		int value = updateVarbitValue(varbitId);

		return value;
	}

	private @Varbit int updateVarbitValue(@Varbit int varbitId) {
		int value;
		value = Microbot.getClientThread().runOnClientThreadOptional(() -> client.getVarbitValue(varbitId)).orElse(0);

		varbits.put(varbitId, value);
		return value;
	}

	/**
	 * Get the value of a specific varp.
	 *
	 * @param varbitId
	 * @return
	 */
	public @Varp int getVarpValue(@Varp int varbitId) {
		Integer cached = varps.get(varbitId);

		if (cached != null) {
			return cached;
		}

		int value = updateVarpValue(varbitId);

		return value;
	}

	private @Varp int updateVarpValue(@Varp int varpId) {
		int value;

		value = Microbot.getClientThread().runOnClientThreadOptional(() -> client.getVarpValue(varpId)).orElse(0);

		if (value > 0) {
			varps.put(varpId, value);
		}
		return value;
	}
}
