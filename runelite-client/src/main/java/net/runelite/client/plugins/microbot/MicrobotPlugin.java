package net.runelite.client.plugins.microbot;

import ch.qos.logback.classic.LoggerContext;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import javax.inject.Provider;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.pouch.PouchOverlay;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginConfigurationDescriptor;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginListPanel;
import net.runelite.client.plugins.microbot.ui.MicrobotTopLevelConfigPanel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.overlay.GembagOverlay;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.cache.Rs2CacheManager;
import net.runelite.client.plugins.microbot.util.cache.Rs2VarbitCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2SkillCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2QuestCache;

import net.runelite.client.plugins.microbot.util.cache.Rs2NpcCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2GroundItemCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2ObjectCache;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

		MicrobotPluginListPanel pluginListPanel = pluginListPanelProvider.get();
		pluginListPanel.addFakePlugin(new MicrobotPluginConfigurationDescriptor(
			"Microbot", "Microbot client settings",
			new String[]{"client"},
			microbotConfig, configManager.getConfigDescriptor(microbotConfig)
		));
		pluginListPanel.rebuildPluginList();

		topLevelConfigPanel = topLevelConfigPanelProvider.get();

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "microbot_config_icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Community Plugins")
			.icon(icon)
			.priority(0)
			.panel(topLevelConfigPanel)
			.build();

		clientToolbar.addNavigation(navButton);

		new InputSelector(clientToolbar);

		Microbot.getPouchScript().startUp();

		// Initialize the cache system
		initializeCacheSystem();

		if (overlayManager != null)
		{
			overlayManager.add(microbotOverlay);
			overlayManager.add(gembagOverlay);
			overlayManager.add(pouchOverlay);
			microbotOverlay.cacheButton.hookMouseListener();
		}
	}

	protected void shutDown()
	{
		overlayManager.remove(microbotOverlay);
		overlayManager.remove(gembagOverlay);
		overlayManager.remove(pouchOverlay);
		microbotOverlay.cacheButton.unhookMouseListener();
		clientToolbar.removeNavigation(navButton);
		if (gameChatAppender.isStarted()) gameChatAppender.stop();
		
		// Shutdown the cache system
		shutdownCacheSystem();
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
			log.warn("Received RuneScape profile change event from '{}' to '{}'", oldProfile, newProfile);
			// Handle profile changes for all cache systems through .tmpFiles/Rs2Cache/MicrobotOverlay.java
			Rs2CacheManager.setUnknownInitialCacheState();
			Rs2CacheManager.loadCacheStateFromConfig(newProfile);
			return;
		}
		
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		Microbot.getPouchScript().onItemContainerChanged(event);
		if (event.getContainerId() == InventoryID.BANK)
		{
			Rs2Bank.updateLocalBank(event);
		}
		else if (event.getContainerId() == InventoryID.INV)
		{
			Rs2Inventory.storeInventoryItemsInMemory(event);
		}
		else if (event.getContainerId() == InventoryID.WORN)
		{
			Rs2Equipment.storeEquipmentItemsInMemory(event);
		}
		else if (Arrays.stream(getShopContainerIds()).anyMatch(sid -> Objects.equals(event.getContainerId(), sid))) {
			Rs2Shop.storeShopItemsInMemory(event, event.getContainerId());
		}
	}

	/**
	 * Retrieves all container IDs from {@link net.runelite.api.gameval.InventoryID}
	 * whose field names suggest they are shop-related.
	 *
	 * <p>This includes fields containing any of the following keywords
	 * (case-insensitive): {@code "shop"}, {@code "store"}, {@code "merchant"},
	 * {@code "bazaar"}, {@code "stall"}, {@code "trader"}, {@code "supplies"}, or {@code "seller"}.
	 *
	 * <p>The method reflects over the public static integer fields in the
	 * {@code InventoryID} class and collects those whose names match
	 * one or more of the defined keywords.
	 *
	 * @return an array of container IDs potentially associated with shop-like inventories
	 */
	private int[] getShopContainerIds()
	{
		Field[] fields = net.runelite.api.gameval.InventoryID.class.getFields();
		List<Integer> shopContainerIds = new ArrayList<>();
		String[] keywords = { "shop", "store", "merchant", "bazaar", "stall", "trader", "supplies", "seller" };

		for (Field field : fields)
		{
			if (field.getType() != int.class)
				continue;

			String fieldName = field.getName().toLowerCase();

			if (Arrays.stream(keywords).anyMatch(fieldName::contains))
			{
				try
				{
					int id = field.getInt(null);
					shopContainerIds.add(id);
				}
				catch (IllegalAccessException e)
				{
					log.error("Failed to access field: {}", field.getName(), e);
				}
			}
		}
		return shopContainerIds.stream().mapToInt(Integer::intValue).toArray();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		
	   if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
	   {
		   // Region-based login detection logic
		   final Client client = Microbot.getClient();
		   if (client != null) {
				@SuppressWarnings("deprecation")
				int[] currentRegions = client.getMapRegions();
				int[] lastRegions = Microbot.getLastKnownRegions();
				boolean regionsChanged = (currentRegions != null && (lastRegions == null || !Arrays.equals(currentRegions, lastRegions)));
				boolean wasLoggedIn = Microbot.loggedIn;
				if (!wasLoggedIn) {
					Microbot.setLoginTime(Instant.now());
					Rs2RunePouch.fullUpdate();
					log.info("Logged in to RuneScape, initializing caches...");
					// Load all cache states from config when logging in through Rs2CacheManager
					Rs2CacheManager.setUnknownInitialCacheState();
					Rs2CacheManager.loadInitialCacheStateFromConfig();
				}
				if (currentRegions != null) {
					Microbot.setLastKnownRegions(currentRegions.clone());
				}
				Microbot.loggedIn = true;
		   }
	   }
	   if (gameStateChanged.getGameState() == GameState.HOPPING || gameStateChanged.getGameState() == GameState.LOGIN_SCREEN || gameStateChanged.getGameState() == GameState.CONNECTION_LOST)
	   {
		   // Clear all cache states when logging out through Rs2CacheManager		   
		   Rs2CacheManager.emptyCacheState();
		   Microbot.loggedIn = false;
		   Microbot.setLastKnownRegions(null);
	   }
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
					.setOption(Microbot.targetMenu.getOption())
					.setTarget(Microbot.targetMenu.getTarget())
					.setIdentifier(Microbot.targetMenu.getIdentifier())
					.setType(Microbot.targetMenu.getType())
					.setParam0(Microbot.targetMenu.getParam0())
					.setParam1(Microbot.targetMenu.getParam1())
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
		if (event.getType() == ChatMessageType.ENGINE && event.getMessage().equalsIgnoreCase("I can't reach that!"))
		{
			Microbot.cantReachTarget = true;
		}
		if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().toLowerCase().contains("you can't log into a non-members"))
		{
			Microbot.cantHopWorld = true;
		}
		Microbot.getPouchScript().onChatMessage(event);
		Rs2Gembag.onChatMessage(event);
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
		if(!Rs2CacheManager.isCacheDataVaild() && Microbot.getClient().getGameState() == GameState.LOGGED_IN)
		{
			// Cache loading is now handled properly during login/profile changes
			Rs2CacheManager.loadInitialCacheStateFromConfig();			
		}
		// Cache loading is now handled properly during login/profile changes
		// No need to call loadInitialCacheFromCurrentConfig on every tick
	}

	@Subscribe(priority = 100)
	private void onClientShutdown(ClientShutdown e)
	{
		// Save all caches through Rs2CacheManager
		Rs2CacheManager.savePersistentCaches();
		Rs2CacheManager.getInstance().close();
	}
	
	/**
	 * Initializes the cache system and registers all caches with the EventBus.
	 * Cache loading from configuration will happen later during game events when the RS profile is available.
	 */
	private void initializeCacheSystem() {
		try {
			// Get the cache manager instance
			Rs2CacheManager cacheManager = Rs2CacheManager.getInstance();
			
			// Set the EventBus for cache event handling (without loading caches yet)
			Rs2CacheManager.setEventBus(eventBus);
			
			// Initialize all cache instances (they register themselves automatically)
			Rs2VarbitCache.getInstance();
			Rs2SkillCache.getInstance();
			Rs2QuestCache.getInstance();
			
			// Initialize specialized entity caches
			Rs2NpcCache.getInstance();
			Rs2GroundItemCache.getInstance();
			Rs2ObjectCache.getInstance();
			
			// Keep deprecated EntityCache for backward compatibility (for now)
			//Rs2EntityCache.getInstance();
			
			log.info("Cache system initialized successfully with specialized caches");
			log.info("Cache persistence will be loaded when RS profile becomes available");
			log.debug("Cache statistics: {}", cacheManager.getCacheStatistics());
			
		} catch (Exception e) {
			log.error("Failed to initialize cache system: {}", e.getMessage(), e);
		}
	}
	
	/**
	 * Shuts down the cache system and cleans up resources.
	 */
	private void shutdownCacheSystem() {
		try {
			Rs2CacheManager cacheManager = Rs2CacheManager.getInstance();
			
			log.debug("Final cache statistics before shutdown: {}", cacheManager.getCacheStatistics());
			
			// Close the cache manager and all caches
			cacheManager.close();
			
			// Reset singleton instances for clean shutdown
			Rs2CacheManager.resetInstance();
			Rs2VarbitCache.resetInstance();
			Rs2SkillCache.resetInstance();
			Rs2QuestCache.resetInstance();
			
			// Reset specialized entity cache instances
			Rs2NpcCache.resetInstance();
			Rs2GroundItemCache.resetInstance();
			Rs2ObjectCache.resetInstance();
			
			// Reset deprecated EntityCache
			//Rs2EntityCache.resetInstance();
			
			log.info("Cache system shutdown completed");
			
		} catch (Exception e) {
			log.error("Error during cache system shutdown: {}", e.getMessage(), e);
		}
	}
	/**
	 * Dynamically checks if any visible widget overlaps with the specified bounds
	 * @param overlayBounds The bounds to check for widget overlap
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
}
