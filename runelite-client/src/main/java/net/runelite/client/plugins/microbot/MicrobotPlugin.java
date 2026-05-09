package net.runelite.client.plugins.microbot;

import ch.qos.logback.classic.LoggerContext;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.pouch.PouchOverlay;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginConfigurationDescriptor;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginListPanel;
import net.runelite.client.plugins.microbot.ui.MicrobotTopLevelConfigPanel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.huntkit.Rs2HuntKit;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.overlay.GembagOverlay;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.api.boat.Rs2BoatCache;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ImageUtil;
import com.google.common.annotations.VisibleForTesting;
import net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer;
import net.runelite.client.plugins.microbot.util.logging.Rs2LogRateLimit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@PluginDescriptor(
	name = PluginDescriptor.Default + "Microbot",
	description = "Microbot",
	tags = {"main", "microbot", "parent"},
	alwaysOn = true,
	hidden = true,
	priority = true
)
@Slf4j
public class MicrobotPlugin extends Plugin
{
	private static final AtomicInteger LEAGUES_LOCK_CHAT_TRUNC_WARN = new AtomicInteger(0);
	/**
	 * Max age of {@code lastTransportAttempt} for attributing locked-region chat to a click.
	 * Public so {@link net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport} shares one value with scripts.
	 *
	 * @apiNote Treat as stable external API: renames or semantic changes break scripts — note in changelog when modifying.
	 */
	public static final long LEAGUES_LOCK_CHAT_MAX_ATTEMPT_AGE_MS = 15_000L;
	private static final int LEAGUES_STALE_LOCK_CHAT_INFO_INTERVAL = 50;
	private static final AtomicInteger LEAGUES_STALE_LOCK_CHAT_IGNORED = new AtomicInteger(0);

	private static final String LEAGUES_AREA_TOKEN = " area";
	private static final int LEAGUES_LOCK_ATTRIBUTED_INFO_INTERVAL = 25;
	private static final AtomicInteger LEAGUES_LOCK_ATTRIBUTED_INFO = new AtomicInteger(0);
	private static final int LEAGUES_LOCK_ATTRIBUTED_DEBUG_INTERVAL = 25;
	private static final AtomicInteger LEAGUES_LOCK_ATTRIBUTED_DEBUG = new AtomicInteger(0);
	private static final int LEAGUES_LOCK_REROUTE_INFO_INTERVAL = 25;
	private static final AtomicInteger LEAGUES_LOCK_REROUTE_INFO = new AtomicInteger(0);

	@Inject
	private Provider<MicrobotPluginListPanel> pluginListPanelProvider;

	@Inject
	private Provider<MicrobotTopLevelConfigPanel> topLevelConfigPanelProvider;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private MicrobotConfig microbotConfig;

	private MicrobotTopLevelConfigPanel topLevelConfigPanel;

	private NavigationButton navButton;

	@Provides
	@Singleton
	MicrobotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MicrobotConfig.class);
	}

	@Inject
	private OverlayManager overlayManager;
	@Inject
	private MicrobotOverlay microbotOverlay;
	@Inject
	private GembagOverlay gembagOverlay;
	@Inject
	private PouchOverlay pouchOverlay;
	@Inject
	private EventBus eventBus;
	private GameChatAppender gameChatAppender;

	@Inject
	private MicrobotVersionChecker microbotVersionChecker;
	
	// Widget change tracking for overlay cache invalidation
	private volatile boolean widgetLayoutChanged = false;
	private Rectangle lastCheckedBounds = null;
	private boolean lastOverlapResult = false;
	/**
	 * Initializes the cache system and registers all caches with the EventBus.
	 * Cache loading from configuration will happen later during game events.
	 */
	@Override
	protected void startUp() throws AWTException
	{
		log.info("Microbot: {} - {}", RuneLiteProperties.getMicrobotVersion(), RuneLiteProperties.getMicrobotCommit());
		log.info("JVM: {} {}", System.getProperty("java.vendor"), System.getProperty("java.runtime.version"));

		microbotVersionChecker.checkForUpdate();

		gameChatAppender = new GameChatAppender();
		gameChatAppender.setName("GAME_CHAT");
		
		// Set pattern based on new configuration
		String pattern = microbotConfig.getGameChatLogPattern().getPattern();
		gameChatAppender.setPattern(pattern);

		final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		gameChatAppender.setContext(context);
		context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(gameChatAppender);

		// Start appender if logging is enabled
		if (microbotConfig.enableGameChatLogging()) {
			gameChatAppender.start();
		}
		
		// Initialize the cached configuration in GameChatAppender
		GameChatAppender.updateConfiguration(
			microbotConfig.enableGameChatLogging(),
			microbotConfig.getGameChatLogLevel().getLevel(),
			microbotConfig.onlyMicrobotLogging()
		);

		Microbot.pauseAllScripts.set(false);
		Microbot.getBlockingEventManager().start();

		MicrobotPluginListPanel pluginListPanel = pluginListPanelProvider.get();
		pluginListPanel.addFakePlugin(new MicrobotPluginConfigurationDescriptor(
			"Microbot", "Microbot client settings",
			new String[]{"client"},
			microbotConfig, configManager.getConfigDescriptor(microbotConfig)
		));
		pluginListPanel.rebuildPluginList();

		topLevelConfigPanel = topLevelConfigPanelProvider.get();

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "microbot_config_icon_lg.png");

		navButton = NavigationButton.builder()
			.tooltip("Community Plugins")
			.icon(icon)
			.priority(0)
			.panel(topLevelConfigPanel)
			.build();

		clientToolbar.addNavigation(navButton);

		new InputSelector(clientToolbar);

		Microbot.getPouchScript().startUp();

		if (overlayManager != null)
		{
			overlayManager.add(microbotOverlay);
			overlayManager.add(gembagOverlay);
			overlayManager.add(pouchOverlay);
		}

	}

	protected void shutDown()
	{
		overlayManager.remove(microbotOverlay);
		overlayManager.remove(gembagOverlay);
		overlayManager.remove(pouchOverlay);
		clientToolbar.removeNavigation(navButton);
		if (gameChatAppender.isStarted()) gameChatAppender.stop();
		microbotVersionChecker.shutdown();
	}


	@Subscribe
	public void onStatChanged(StatChanged statChanged)
	{
		Microbot.setIsGainingExp(true);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		String newProfile = event.getNewProfile();
		String oldProfile = event.getPreviousProfile();
		if ((newProfile != null && !newProfile.isEmpty()) &&
			(oldProfile == null || oldProfile.isEmpty() || !newProfile.equals(oldProfile))
		)
		{
			log.info("\nReceived RuneScape profile change event from '{}' to '{}'", oldProfile, newProfile);
		}
		
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		Microbot.getPouchScript().onItemContainerChanged(event);
		if (event.getContainerId() == InventoryID.INV)
		{
			Rs2Inventory.storeInventoryItemsInMemory(event);
		}
		else if (event.getContainerId() == InventoryID.WORN)
		{
			Rs2Equipment.storeEquipmentItemsInMemory(event);
		}
		else if (event.getContainerId() == InventoryID.BANK)
		{
			Rs2Bank.updateLocalBank(event);
		}
		else if (event.getContainerId() == InventoryID.HUNTSMANS_KIT)
		{
			Rs2HuntKit.updateLocalKit(event);
		}
		else if (Arrays.stream(getShopContainerIds()).anyMatch(sid -> Objects.equals(event.getContainerId(), sid))) {
			Rs2Shop.storeShopItemsInMemory(event, event.getContainerId());
		}
	}

	/**
	 * Retrieves all currently open container IDs from {@link InventoryID}
	 * and excludes specific container IDs.
	 *
	 * @return an array of open container IDs excluding the specified excluded IDs
	 */
	private int[] getShopContainerIds()
	{
		Field[] fields = InventoryID.class.getFields();
		List<Integer> openContainerIds = new ArrayList<>();
		int[] excludedIds = { 90, 93, 94, 95 };

		for (Field field : fields)
		{
			if (field.getType() != int.class)
				continue;

			try
			{
				int containerId = field.getInt(null);
				ItemContainer container = Microbot.getClient().getItemContainer(containerId);
				
				if (container != null && container.getItems() != null && container.getItems().length > 0) {
					boolean hasItems = Arrays.stream(container.getItems())
						.anyMatch(item -> item != null && item.getId() != -1);
						
					if (hasItems && Arrays.stream(excludedIds).noneMatch(excludedId -> excludedId == containerId)) {
						openContainerIds.add(containerId);
					}
				}
			}
			catch (IllegalAccessException e)
            {
                log.error("Failed to access field: {}", field.getName(), e);
            }
		}
		return openContainerIds.stream().mapToInt(Integer::intValue).toArray();
	}



	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		
	   if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
	   {
		   // Region-based login detection logic
		   final Client client = Microbot.getClient();
		   if (client != null) {
				int[] currentRegions = client.getTopLevelWorldView().getMapRegions();
				boolean wasLoggedIn = LoginManager.getLastKnownGameState() == GameState.LOGGED_IN;
				if (!wasLoggedIn) {
					LoginManager.markLoggedIn();
					Rs2RunePouch.fullUpdate();
				}
				if (currentRegions != null) {
					Microbot.setLastKnownRegions(currentRegions.clone());
				}
		   }
	   }
	   if (gameStateChanged.getGameState() == GameState.HOPPING || gameStateChanged.getGameState() == GameState.LOGIN_SCREEN || gameStateChanged.getGameState() == GameState.CONNECTION_LOST)
	   {
		   // Clear all cache states when logging out through Rs2CacheManager
		   //Rs2CacheManager.emptyCacheState(); // should not be nessary here, handled in ClientShutdown event,
		   // and we also handle correct cache loading in onRuneScapeProfileChanged event
		   LoginManager.markLoggedOut();
		   Microbot.setLastKnownRegions(null);
		   Rs2LeaguesTransport.onLogout();
	   }
	   // update last known game state to track login/logout transitions
	   LoginManager.setLastKnownGameState(gameStateChanged.getGameState());
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		Rs2Tab.onVarClientIntChanged(event);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		Rs2Player.handlePotionTimers(event);
		Rs2Player.handleTeleblockTimer(event);
		Rs2RunePouch.onVarbitChanged(event);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Rs2Player.handleAnimationChanged(event);
	}

	@Subscribe(priority = 999)
	private void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (Microbot.targetMenu != null && event.getType() != Microbot.targetMenu.getType().getId())
		{
			Microbot.getClient().getMenu().setMenuEntries(new MenuEntry[]{});
		}

		if (Microbot.targetMenu != null)
		{
			MenuEntry entry =
				Microbot.getClient().getMenu().createMenuEntry(-1)
                    .setItemId(0)
					.setOption(Microbot.targetMenu.getOption())
					.setTarget(Microbot.targetMenu.getTarget())
					.setIdentifier(Microbot.targetMenu.getIdentifier())
					.setType(Microbot.targetMenu.getType())
					.setParam0(Microbot.targetMenu.getParam0())
					.setParam1(Microbot.targetMenu.getParam1())
                    .setWorldViewId(Microbot.targetMenu.getWorldViewId())
					.setForceLeftClick(false);

			if (Microbot.targetMenu.getItemId() > 0)
			{
				try
				{
					Rs2Reflection.setItemId(entry, Microbot.targetMenu.getItemId());
				}
				catch (IllegalAccessException | InvocationTargetException e)
				{
					log.error(e.getMessage(), e);
				}
			}
			Microbot.getClient().getMenu().setMenuEntries(new MenuEntry[]{entry});
		}
	}

	@Subscribe
	private void onMenuOptionClicked(MenuOptionClicked event)
	{
		Microbot.getPouchScript().onMenuOptionClicked(event);
		Rs2Gembag.onMenuOptionClicked(event);
		Microbot.targetMenu = null;
		if (microbotConfig.enableMenuEntryLogging()) log.info(event.getMenuEntry().toString());
	}

	@Subscribe
	private void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.ENGINE)
		{
			String msg = event.getMessage();
			if (msg != null && msg.equalsIgnoreCase("I can't reach that!"))
			{
				Microbot.cantReachTarget = true;
			}
		}
		if (event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			String msg = event.getMessage();
			if (msg != null && containsIgnoreCase(msg, "you can't log into a non-members"))
			{
				Microbot.cantHopWorld = true;
			}

			// Leagues: "haven't unlocked access to X area" -> blacklist last transport dest.
			// Fast reject: avoid sanitize/regex work for unrelated gamemessages.
			handleLeaguesLockedRegionChat(msg);
		}
		Microbot.getPouchScript().onChatMessage(event);
		Rs2Gembag.onChatMessage(event);
	}

	private static boolean containsIgnoreCase(String haystack, String needle)
	{
		if (haystack == null || needle == null || needle.isEmpty())
		{
			return false;
		}
		int hLen = haystack.length();
		int nLen = needle.length();
		if (nLen > hLen)
		{
			return false;
		}
		for (int i = 0; i <= hLen - nLen; i++)
		{
			if (haystack.regionMatches(true, i, needle, 0, nLen))
			{
				return true;
			}
		}
		return false;
	}

	private void handleLeaguesLockedRegionChat(String msg)
	{
		if (msg == null)
		{
			return;
		}
		// Cheap prefilter for the regional lock copy ("... access to the X area") before lowercasing/sanitize+regex work.
		// Case-sensitive on purpose: cheap reject only; full parse does sanitize+casefold + phrase matching.
		boolean hasAccess = msg.contains("access") || msg.contains("Access");
		boolean hasArea = msg.contains(" area") || msg.contains(" Area");
		if (!hasAccess || !hasArea)
		{
			return;
		}
		String lower = msg.toLowerCase(Locale.ROOT);
		if (!isLeaguesLockedAccessMessageLower(lower))
		{
			return;
		}

		String rawForMatch = clipLeaguesLockChatRawForMatch(msg);
		if (msg.length() > Rs2LeaguesTransport.LEAGUES_LOCK_CHAT_MAX_NORMALIZE_CHARS
				&& Rs2LogRateLimit.everyN(LEAGUES_LOCK_CHAT_TRUNC_WARN, Rs2LeaguesTransport.LEAGUES_LOCK_CHAT_TRUNC_WARN_INTERVAL))
		{
			log.warn("[Leagues] locked-region gamemessage length {} exceeds cap {}; matching on first {} chars only",
					msg.length(), Rs2LeaguesTransport.LEAGUES_LOCK_CHAT_MAX_NORMALIZE_CHARS, rawForMatch.length());
		}

		String region = Rs2LeaguesTransport.captureLockedRegionFromChatRaw(rawForMatch).orElse(null);
		if (region != null)
		{
			handleLeaguesLockedRegionMatch(region, rawForMatch);
		}
	}

	private void handleLeaguesLockedRegionMatch(String region, String rawForMatch)
	{
		long nowMs = System.currentTimeMillis();
		Rs2LeaguesTransport.TransportAttemptSnapshot snap = Rs2LeaguesTransport.getLastTransportAttemptSnapshot();
		if (snap == null)
		{
			handleLeaguesLockedRegionStale(region, -1);
			return;
		}
		Integer packedDest = snap.getPackedDest();
		String methodSafe = snap.getMethod() != null ? snap.getMethod() : "";
		long ageMs = nowMs - snap.getTsMs();

		// Guard against stale/racey attribution (e.g. other teleports attempted after click).
		if (packedDest == null || ageMs > LEAGUES_LOCK_CHAT_MAX_ATTEMPT_AGE_MS)
		{
			handleLeaguesLockedRegionStale(region, ageMs);
			// Only clear when we had an attempt snapshot but chose not to attribute it (stale / missing dest).
			Rs2LeaguesTransport.clearLastTransportAttempt();
			return;
		}

		boolean willDebug = log.isDebugEnabled()
				&& Rs2LogRateLimit.everyN(LEAGUES_LOCK_ATTRIBUTED_DEBUG, LEAGUES_LOCK_ATTRIBUTED_DEBUG_INTERVAL);
		boolean willInfo = !willDebug
				&& Rs2LogRateLimit.everyN(LEAGUES_LOCK_ATTRIBUTED_INFO, LEAGUES_LOCK_ATTRIBUTED_INFO_INTERVAL);
		if (willDebug || willInfo)
		{
			var dest = WorldPointUtil.unpackWorldPoint(packedDest);
			if (willDebug)
			{
				log.debug("[Leagues] locked-region rawMsg='{}' region='{}' method='{}' destPacked={} dest={}",
						rawForMatch,
						region,
						methodSafe,
						packedDest,
						dest);
			}
			else
			{
				log.info("[Leagues] locked-region region='{}' method='{}' destPacked={} dest={} (summary every {} msgs)",
						region,
						methodSafe,
						packedDest,
						dest,
						LEAGUES_LOCK_ATTRIBUTED_INFO_INTERVAL);
			}
		}

		boolean recorded = Rs2LeaguesTransport.recordBlockedDestinationFromChat(
				region,
				packedDest,
				methodSafe);
		if (!recorded)
		{
			return;
		}

		// Reroute: INFO cadence only — see Rs2LeaguesTransport#recordBlockedDestinationFromChat Javadoc.
		if (Rs2LogRateLimit.everyN(LEAGUES_LOCK_REROUTE_INFO, LEAGUES_LOCK_REROUTE_INFO_INTERVAL))
		{
			log.info("[Leagues] reroute: locked region='{}' method='{}' destPacked={} (summary every {} msgs)",
					region, methodSafe, packedDest, LEAGUES_LOCK_REROUTE_INFO_INTERVAL);
		}
		// When Rs2LeaguesTransport#shouldRecalculatePathAfterLock returns false, reroute was deduped in-window (skip recalc).
		// When it returns true, dedupe keys update inside that method even if client == null
		// (recalc skipped whenever the client is unavailable, e.g. login transition).
		// Blacklist already persisted above.
		if (!Rs2LeaguesTransport.shouldRecalculatePathAfterLock(region, packedDest))
		{
			return;
		}
		Client client = Microbot.getClient();
		if (client == null)
		{
			return;
		}
		if (client.isClientThread())
		{
			Rs2Walker.recalculatePath();
		}
		else
		{
			var clientThread = Microbot.getClientThread();
			if (clientThread == null)
			{
				return;
			}
			clientThread.invokeLater(Rs2Walker::recalculatePath);
		}
	}

	private static boolean isLeaguesLockedAccessMessageLower(String lower)
	{
		if (lower == null)
		{
			return false;
		}
		// Match multiple Jagex phrasings:
		// - "haven't unlocked access to the X area"
		// - "don't have access to the X area"
		// We require "access to the" then an " area" token after it, plus at least one "locked" hint.
		int accessIdx = lower.indexOf("access to the ");
		if (accessIdx < 0)
		{
			return false;
		}
		if (lower.indexOf(LEAGUES_AREA_TOKEN, accessIdx) < 0)
		{
			return false;
		}
		return lower.indexOf("haven't unlocked access") >= 0
				|| lower.indexOf("havent unlocked access") >= 0
				|| lower.indexOf("don't have access") >= 0
				|| lower.indexOf("do not have access") >= 0
				|| lower.indexOf("cannot access to the ") >= 0
				|| lower.indexOf("cannot access the ") >= 0;
	}

	private void handleLeaguesLockedRegionStale(String region, long ageMs)
	{
		if (!Rs2LogRateLimit.everyN(LEAGUES_STALE_LOCK_CHAT_IGNORED, LEAGUES_STALE_LOCK_CHAT_INFO_INTERVAL))
		{
			return;
		}
		int n = LEAGUES_STALE_LOCK_CHAT_IGNORED.get();
		String age = ageMs >= 0 ? Long.toString(ageMs) : "unknown";
		log.info("[Leagues] locked-region stale/no-attempt summary: count={} lastRegion='{}' lastAgeMs={}",
				n, region, age);
		if (log.isDebugEnabled())
		{
			log.debug("[Leagues] locked-region msg ignored (stale/no attempt): region='{}' ageMs={}", region, age);
		}
	}

	@VisibleForTesting
	static boolean isLeaguesLockedAccessMessage(String msg)
	{
		if (msg == null)
		{
			return false;
		}
		return isLeaguesLockedAccessMessageLower(msg.toLowerCase(Locale.ROOT));
	}

	/** Strip chat markup / entities for region text passed to {@link Rs2LeaguesTransport#parseRegionName(String)}. */
	@VisibleForTesting
	static String sanitizeLeaguesLockedRegionName(String raw)
	{
		return Rs2TextSanitizer.sanitizeLeaguesLockedRegionName(raw);
	}

	/**
	 * First capture of the Leagues locked-region chat pattern after {@link Rs2TextSanitizer#sanitizeForParsing(String)} on {@code rawForMatch},
	 * or {@code null} if no match. For unit tests only — production uses {@link #onChatMessage(ChatMessage)}.
	 */
	@VisibleForTesting
	static String leaguesLockedRegionCapturedRegionAfterNormalizeForTests(String rawForMatch)
	{
		if (rawForMatch == null)
		{
			return null;
		}
		String clipped = clipLeaguesLockChatRawForMatch(rawForMatch);
		return Rs2LeaguesTransport.captureLockedRegionFromChatRaw(clipped).orElse(null);
	}

	/** Same prefix cap as {@link #onChatMessage(ChatMessage)} before normalize + regex. */
	private static String clipLeaguesLockChatRawForMatch(String msg)
	{
		return msg.length() > Rs2LeaguesTransport.LEAGUES_LOCK_CHAT_MAX_NORMALIZE_CHARS
				? msg.substring(0, Rs2LeaguesTransport.LEAGUES_LOCK_CHAT_MAX_NORMALIZE_CHARS)
				: msg;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged ev)
	{
		if (ev.getGroup().equals(MicrobotConfig.configGroup)) {
			switch (ev.getKey()) {
				case MicrobotConfig.keyEnableGameChatLogging:
				case MicrobotConfig.keyGameChatLogPattern:
				case MicrobotConfig.keyGameChatLogLevel:
				case MicrobotConfig.keyOnlyMicrobotLogging:
					// Handle any logging-related configuration changes
					final boolean shouldBeStarted = microbotConfig.enableGameChatLogging();

					// Update the cached configuration in GameChatAppender
					GameChatAppender.updateConfiguration(
							microbotConfig.enableGameChatLogging(),
							microbotConfig.getGameChatLogLevel().getLevel(),
							microbotConfig.onlyMicrobotLogging()
					);

					if (shouldBeStarted) {
						// Update pattern if needed
						String pattern = microbotConfig.getGameChatLogPattern().getPattern();
						gameChatAppender.setPattern(pattern);

						if (!gameChatAppender.isStarted()) {
							gameChatAppender.start();
						}
					} else if (gameChatAppender.isStarted()) {
						gameChatAppender.stop();
					}
					break;
				default:
					break;
			}
		}
		if (ev.getKey().equals("displayPouchCounter"))
		{
			if (Objects.equals(ev.getNewValue(), "true"))
			{
				Microbot.getPouchScript().startUp();
			}
			else
			{
				Microbot.getPouchScript().shutdown();
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		Rs2RunePouch.onWidgetLoaded(event);
		
		// Mark that widget layout has changed for cache invalidation
		widgetLayoutChanged = true;
		log.debug("Widget {} loaded, layout changed", event.getGroupId());
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		// Mark that widget layout has changed for cache invalidation
		widgetLayoutChanged = true;
		log.debug("Widget {} closed, layout changed", event.getGroupId());
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		// Case 1: Hitsplat applied to the local player (indicates someone or something is attacking you)
		if (event.getActor().equals(Microbot.getClient().getLocalPlayer()))
		{
			if (!event.getHitsplat().isOthers())
			{
				Rs2Player.updateCombatTime();
			}
		}

		// Case 2: Hitsplat is applied to another player (indicates you are attacking another player)
		else if (event.getActor() instanceof Player)
		{
			if (event.getHitsplat().isMine())
			{
				Rs2Player.updateCombatTime();
			}
		}

		// Case 3: Hitsplat is applied to an NPC (indicates you are attacking an NPC)
		else if (event.getActor() instanceof NPC)
		{
			if (event.getHitsplat().isMine())
			{
				Rs2Player.updateCombatTime();
			}
		}
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked overlayMenuClicked)
	{
		OverlayMenuEntry overlayMenuEntry = overlayMenuClicked.getEntry();
		if (overlayMenuEntry.getMenuAction() == MenuAction.RUNELITE_OVERLAY_CONFIG)
		{
			Overlay overlay = overlayMenuClicked.getOverlay();
			Plugin plugin = overlay.getPlugin();
			if (plugin == null)
			{
				return;
			}

			// Expand config panel for plugin
			SwingUtilities.invokeLater(() ->
			{
				clientToolbar.openPanel(navButton);
				topLevelConfigPanel.openConfigurationPanel(plugin.getName());
			});
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Start Leagues teleport calibration ASAP after login (non-blocking; prompts for consent once).
		Rs2LeaguesTransport.tickLeaguesCalibration();
	}

	@Subscribe(priority = 100)
	private void onClientShutdown(ClientShutdown e)
	{

	}

	/**
	 * Dynamically checks if any visible widget overlaps with the specified bounds
	 * @param overlayBoundsCanvas The bounds to check for widget overlap
	 * @return true if any visible widget overlaps with the specified bounds
	 */
	public boolean hasWidgetOverlapWithBounds(Rectangle overlayBoundsCanvas) {
		if (overlayBoundsCanvas == null || Microbot.getClient() == null) {
			return false;
		}

	   int viewportXOffset = Microbot.getClient().getViewportXOffset();
	   int viewportYOffset = Microbot.getClient().getViewportYOffset();

		// Use cached result if widget layout hasn't changed and bounds are the same
		if (!this.widgetLayoutChanged && overlayBoundsCanvas.equals(this.lastCheckedBounds)) {
			return this.lastOverlapResult;
		}

	   boolean result = Microbot.getClientThread().runOnClientThreadOptional(() -> {
		   try {
			   return Rs2Widget.checkBoundsOverlapWidgetInMainModal(overlayBoundsCanvas, viewportXOffset, viewportYOffset);
		   } catch (Exception e) {
			   log.debug("Error checking widget overlap: {}", e.getMessage());
			   return false;
		   }
	   }).orElse(false);

		// Cache the result
		widgetLayoutChanged = false;
		lastCheckedBounds = new Rectangle(overlayBoundsCanvas);
		lastOverlapResult = result;

		return result;
	}

    @Subscribe
    public void onWorldViewLoaded(WorldViewLoaded event)
    {
        Microbot.getWorldViewIds().add(event.getWorldView().getId());
    }

    @Subscribe
    public void onWorldViewUnloaded(WorldViewUnloaded event)
    {
        Microbot.getWorldViewIds().remove(event.getWorldView().getId());
    }
}
