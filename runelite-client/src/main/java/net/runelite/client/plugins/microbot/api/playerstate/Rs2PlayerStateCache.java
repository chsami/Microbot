package net.runelite.client.plugins.microbot.api.playerstate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.WorldView;
import net.runelite.api.annotations.Varbit;
import net.runelite.api.annotations.Varp;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

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

	private volatile int lastLocalPlayerTick = -1;
	private volatile WorldPoint localPlayerPosition;
	private volatile WorldView localPlayerWorldView;

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
			lastLocalPlayerTick = -1;
			localPlayerPosition = null;
			localPlayerWorldView = null;
		}
		// The server only re-sends non-zero varps after a hop/reconnect, so a value that
		// dropped to 0 while out of sync would never be corrected by onVarbitChanged.
		if (e.getGameState() == GameState.HOPPING || e.getGameState() == GameState.CONNECTION_LOST) {
			varbits.clear();
			varps.clear();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (questsPopulated) {
			updateQuest(event);
		}
		// Only cache while LOGGED_IN. onGameStateChanged clears both maps on HOPPING /
		// CONNECTION_LOST because the server re-sends only non-zero varps afterwards, but an event
		// arriving after that clear and before the next LOGGED_IN would repopulate the very value the
		// clear existed to discard. Gating the writes closes that window instead of racing it.
		if (client == null || client.getGameState() != GameState.LOGGED_IN) {
			return;
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
	/**
	 * Whether a quest tracked by {@code questVarbitId} / {@code questVarPlayerId} (either may be null)
	 * is the one this VarbitChanged refers to.
	 * <p>
	 * Matching only varbits left every VARPLAYER-tracked quest frozen at whatever
	 * {@link #populateQuests()} saw at login. Completing one mid-session never updated the cache, and
	 * {@code TransportRequirementPolicy.completedQuests} fails closed on a stale or null state, so
	 * quest-gated transports stayed invisible until relog — the White Wolf Mountain tunnel unlocked by
	 * Fishing Contest (a varplayer quest) being the case that surfaced it. VarbitChanged carries both
	 * ids; this cache already reads {@code getVarpId()} for its varp map a few lines below.
	 */
	static boolean questTrackedByChangedVar(Integer questVarbitId, Integer questVarPlayerId,
			int eventVarbitId, int eventVarpId) {
		if (questVarbitId != null && eventVarbitId != -1 && questVarbitId == eventVarbitId) {
			return true;
		}
		return questVarPlayerId != null && eventVarpId != -1 && questVarPlayerId.equals(eventVarpId);
	}

	private void updateQuest(VarbitChanged event) {
		// Read the event once: inside the filter these ran for every QuestHelperQuest value on every
		// VarbitChanged, which is a few hundred redundant accessor calls per game tick.
		final int changedVarbitId = event.getVarbitId();
		final int changedVarpId = event.getVarpId();
		QuestHelperQuest quest = Arrays.stream(QuestHelperQuest.values())
				.filter(x -> questTrackedByChangedVar(
						x.getVarbit() == null ? null : x.getVarbit().getId(),
						x.getVarPlayer() == null ? null : x.getVarPlayer().getId(),
						changedVarbitId,
						changedVarpId))
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
		return clientThread.runOnClientThreadOptional(() -> {
			int value = client.getVarbitValue(varbitId);
			// Only cache while logged in: before the initial varp sync completes the client
			// returns default/stale values, and a wrong entry for a varp the server never
			// re-sends (zero-valued ones) would stick until the next flush.
			if (client.getGameState() == GameState.LOGGED_IN) {
				varbits.put(varbitId, value);
			}
			return value;
		}).orElse(0);
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
		return clientThread.runOnClientThreadOptional(() -> {
			int value = client.getVarpValue(varpId);
			// Zero is a legitimate value and must be cached too — see updateVarbitValue.
			if (client.getGameState() == GameState.LOGGED_IN) {
				varps.put(varpId, value);
			}
			return value;
		}).orElse(0);
	}

	private void refreshLocalPlayer() {
		int currentTick = client.getTickCount();
		if (lastLocalPlayerTick >= currentTick) {
			return;
		}
		localPlayerPosition = Rs2Player.getWorldLocation_Internal();
		localPlayerWorldView = Rs2Player.getWorldView_Internal();
		lastLocalPlayerTick = currentTick;
	}

	public WorldPoint getLocalPlayerPosition() {
		refreshLocalPlayer();
		return localPlayerPosition;
	}

	public WorldView getLocalPlayerWorldView() {
		refreshLocalPlayer();
		return localPlayerWorldView;
	}

	public void invalidateLocalPlayer() {
		lastLocalPlayerTick = -1;
		localPlayerPosition = null;
		localPlayerWorldView = null;
	}
}
