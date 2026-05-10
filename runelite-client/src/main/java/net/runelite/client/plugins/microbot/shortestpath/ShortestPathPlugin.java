/**
 *
 * Credit:
 *
 * This project includes code or inspiration from the following open-source project:
 *
 * Project: Shortest Path Algorithm
 * Repository: https://github.com/Skretzo/shortest-path
 * Author: Skretzo
 * License: BSD-2-Clause license
 *
 * Description:
 * The shortest-path implementation in this project was adapted or inspired by
 * the algorithm and code shared in the repository linked above. We thank the original
 * author for making this available as open-source software.
 *
 * Any modifications to the original code have been made by Microbot.
 */

package net.runelite.client.plugins.microbot.shortestpath;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.Setter;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.api.worldmap.WorldMapData;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WebWalkLog;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Web Walker",
        description = "Draws the shortest path to a chosen destination on the map (right click a spot on the world map to use)",
        tags = {"pathfinder", "map", "waypoint", "navigation", "microbot"},
        enabledByDefault = true,
        alwaysOn = true
)
public class ShortestPathPlugin extends Plugin implements KeyListener {
    public static final String CONFIG_GROUP = "shortestpath";
    private static final String PLUGIN_MESSAGE_PATH = "path";
    private static final String PLUGIN_MESSAGE_CLEAR = "clear";
    private static final String PLUGIN_MESSAGE_START = "start";
    private static final String PLUGIN_MESSAGE_TARGET = "target";
	private static final String PLUGIN_MESSAGE_CONFIG_OVERRIDE = "config";
    private static final String CLEAR = "Clear";
    private static final String PATH = ColorUtil.wrapWithColorTag("Path", JagexColors.MENU_TARGET);
    private static final String SET = "Set";
    private static final String START = ColorUtil.wrapWithColorTag("Start", JagexColors.MENU_TARGET);
    private static final String TARGET = ColorUtil.wrapWithColorTag("Target", JagexColors.MENU_TARGET);
    private static final String TEST = ColorUtil.wrapWithColorTag("Test Target", JagexColors.MENU_TARGET);

    public static final BufferedImage MARKER_IMAGE = ImageUtil.loadImageResource(ShortestPathPlugin.class, "marker.png");

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Getter
    @Inject
    private ClientThread clientThread;

    @Inject
    @Getter(AccessLevel.PACKAGE)
    private ShortestPathConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private PathTileOverlay pathOverlay;

    @Inject
    private PathMinimapOverlay pathMinimapOverlay;

    @Inject
    private PathMapOverlay pathMapOverlay;

    @Inject
    private PathMapTooltipOverlay pathMapTooltipOverlay;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private DebugOverlayPanel debugOverlayPanel;

    @Inject
    private ETAOverlayPanel etaOverlayPanel;

    @Inject
    private SpriteManager spriteManager;

    @Inject
    private WorldMapPointManager worldMapPointManager;
    @Inject
    private KeyManager keyManager;

	boolean drawCollisionMap;
	boolean drawMap;
	boolean drawMinimap;
	boolean drawTiles;
	boolean drawTransports;
	boolean showTransportInfo;
	Color colourCollisionMap;
	Color colourPath;
	Color colourPathCalculating;
	Color colourText;
	Color colourTransports;
	int tileCounterStep;
	TileCounter showTileCounter;
	TileStyle pathStyle;

    private Point lastMenuOpenedPoint;
    private ShortestPathPanel panel;
    private PohPanel pohPanel;
    @Getter
    @Setter
    public static WorldMapPoint marker;
    @Setter
    public static volatile WorldPoint lastLocation = new WorldPoint(0, 0, 0);
    private NavigationButton navButton, pohNavButton;
    private Shape minimapClipFixed;
    private Shape minimapClipResizeable;
    private BufferedImage minimapSpriteFixed;
    private BufferedImage minimapSpriteResizeable;
    private Rectangle minimapRectangle = new Rectangle();

    @Getter
    @Setter
    public static volatile ExecutorService pathfindingExecutor = Executors.newSingleThreadExecutor();
    @Getter
    @Setter
    public static volatile Future<?> pathfinderFuture;
    @Getter
    public static final Object pathfinderMutex = new Object();
	private static final Map<String, Object> configOverride = new HashMap<>(50);
    @Getter
    @Setter
    public static volatile Pathfinder pathfinder;
    @Getter
    public static PathfinderConfig pathfinderConfig;
    @Getter
    @Setter
    public static boolean startPointSet = false;
    @Setter
    private static int reachedDistance;
    @Getter(AccessLevel.PACKAGE)
    private ShortestPathScript shortestPathScript;

    // Set on LOGGED_IN or when League region-unlock varbits change. Consumed on the next game tick
    // so varbits, quest states, inventory, and bank containers are hydrated before
    // PathfinderConfig#refresh rebuilds the transport availability cache.
    volatile boolean pendingLoginRefresh = false;

    /**
     * First successful session-init route clear after {@link #startUp()} (or plugin re-enable).
     * Never cleared on world hop / subsequent LOGGED_IN — avoids wiping Rs2Walker mid-walk.
     */
    private volatile boolean sessionInitRouteClearPending = true;
    @Provides
    public ShortestPathConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ShortestPathConfig.class);
    }

    @Override
    protected void startUp() {
		cacheConfigValues();
        SplitFlagMap map = SplitFlagMap.fromResources();
        Map<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();

        List<Restriction> restrictions = Restriction.loadAllFromResources();
        pathfinderConfig = new PathfinderConfig(map, transports, restrictions, client, config);

        panel = injector.getInstance(ShortestPathPanel.class);
        pohPanel = new PohPanel(config);
        final BufferedImage icon = ImageUtil.loadImageResource(ShortestPathPlugin.class, "panel_icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Web Walker")
                .icon(icon)
                .priority(8)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);

        final BufferedImage pohIcon = ImageUtil.loadImageResource(ShortestPathPlugin.class, "poh_icon.png");
        pohNavButton = NavigationButton.builder()
                .tooltip("Poh Web Config")
                .icon(pohIcon)
                .priority(9)
                .panel(pohPanel)
                .build();
        clientToolbar.addNavigation(pohNavButton);

        Rs2Walker.setConfig(config);
        shortestPathScript = new ShortestPathScript();
        shortestPathScript.run(config);

        overlayManager.add(pathOverlay);
        overlayManager.add(pathMinimapOverlay);
        overlayManager.add(pathMapOverlay);
        overlayManager.add(pathMapTooltipOverlay);
        if (config.showETA()) {
            overlayManager.add(etaOverlayPanel);
        }

        if (config.drawDebugPanel()) {
            overlayManager.add(debugOverlayPanel);
        }
        keyManager.registerKeyListener(this);
        keyManager.registerKeyListener(customLocationHotkeyListener);
        keyManager.registerKeyListener(bankHotkeyListener);
        keyManager.registerKeyListener(nearestBankHotkeyListener);
        keyManager.registerKeyListener(depositBoxHotkeyListener);
        keyManager.registerKeyListener(nearestDepositBoxHotkeyListener);
        keyManager.registerKeyListener(slayerMasterHotkeyListener);
        keyManager.registerKeyListener(questHotkeyListener);
        keyManager.registerKeyListener(clueHotkeyListener);
        keyManager.registerKeyListener(farmingHotkeyListener);
        keyManager.registerKeyListener(hunterHotkeyListener);

        clientThread.invokeLater(this::consumeSessionInitRouteClearIfNeeded);
    }

    @Override
    protected void shutDown() {
        // Unregister hotkey listeners first so any in-flight keystroke can't
        // dereference panel/shortestPathScript after we null/tear them down.
        keyManager.unregisterKeyListener(hunterHotkeyListener);
        keyManager.unregisterKeyListener(farmingHotkeyListener);
        keyManager.unregisterKeyListener(clueHotkeyListener);
        keyManager.unregisterKeyListener(questHotkeyListener);
        keyManager.unregisterKeyListener(slayerMasterHotkeyListener);
        keyManager.unregisterKeyListener(nearestDepositBoxHotkeyListener);
        keyManager.unregisterKeyListener(depositBoxHotkeyListener);
        keyManager.unregisterKeyListener(nearestBankHotkeyListener);
        keyManager.unregisterKeyListener(bankHotkeyListener);
        keyManager.unregisterKeyListener(customLocationHotkeyListener);
        keyManager.unregisterKeyListener(this);

        overlayManager.remove(pathOverlay);
        overlayManager.remove(pathMinimapOverlay);
        overlayManager.remove(pathMapOverlay);
        overlayManager.remove(pathMapTooltipOverlay);
        overlayManager.remove(debugOverlayPanel);
        clientToolbar.removeNavigation(navButton);
        clientToolbar.removeNavigation(pohNavButton);
        navButton = null;
        pohNavButton = null;
        if (panel != null) {
            panel.disposeTimers();
        }
        panel = null;
        PohPanel.instance = null;
        pohPanel = null;

        shortestPathScript.shutdown();

        sessionInitRouteClearPending = true;
        exit();
    }

    //Method from microbot
    public static void exit() {
        Rs2Walker.clearWalkingRoute("shortest-path-plugin:exit");
        if (pathfindingExecutor != null) {
            pathfindingExecutor.shutdownNow();
            pathfindingExecutor = null;
        }
    }

    public void restartPathfinding(WorldPoint start, Set<WorldPoint> ends, boolean canReviveFiltered) {
        ExecutorService executor;
        synchronized (pathfinderMutex) {
            if (pathfinder != null) {
                pathfinder.cancel();
                pathfinderFuture.cancel(true);
            }

            if ((executor = pathfindingExecutor) == null) {
                ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("shortest-path-%d").build();
                executor = Executors.newSingleThreadExecutor(shortestPathNaming);
                pathfindingExecutor = executor;
            }
        }

        final ExecutorService finalExecutor = executor;
        final long scheduleTime = System.currentTimeMillis();
        getClientThread().invokeLater(() -> {
            long invokeLaterDelay = System.currentTimeMillis() - scheduleTime;
            long refreshStart = System.currentTimeMillis();
            pathfinderConfig.refresh();
            long refreshTime = System.currentTimeMillis() - refreshStart;
            // filterLocations mutates the set (removeIf / addAll). Callers often pass Set.of(...) — immutable.
            Set<WorldPoint> endsWorking = new HashSet<>(ends);
            pathfinderConfig.filterLocations(endsWorking, canReviveFiltered);
            if (endsWorking.isEmpty()) {
                WorldPoint walkerGoal = Rs2Walker.getCurrentTarget();
                if (walkerGoal != null) {
                    endsWorking.add(walkerGoal);
                    WebWalkLog.spWarn("restart filter emptied ends during walk — restore goal {}",
                            walkerGoal);
                }
            }
            synchronized (pathfinderMutex) {
                if (endsWorking.isEmpty()) {
                    // Re-read: walk thread may set currentTarget after pre-sync filter pass (race with invokeLater).
                    WorldPoint walkerRetry = Rs2Walker.getCurrentTarget();
                    if (walkerRetry != null) {
                        endsWorking.add(walkerRetry);
                        WebWalkLog.spWarn("restart ends empty before PF — restore goal {} (retry)",
                                walkerRetry);
                    }
                }
                if (endsWorking.isEmpty()) {
                    // Third read: walk thread may publish currentTarget after synchronized walkerRetry pass.
                    WorldPoint invokeLaterFinal = Rs2Walker.getCurrentTarget();
                    if (invokeLaterFinal != null) {
                        endsWorking.add(invokeLaterFinal);
                        WebWalkLog.spWarn("restart invokeLater final restore goal {} (avoid empty clear)", invokeLaterFinal);
                    }
                }
                if (endsWorking.isEmpty()) {
                    WebWalkLog.spWarn("restart ends empty after filter — clear route start={}", start);
                    clearPluginTargets("shortest-path-plugin:restart-empty-ends");
                } else {
                    pathfinder = new Pathfinder(pathfinderConfig, start, endsWorking);
                    pathfinderFuture = finalExecutor.submit(pathfinder);
                }
            }
            WebWalkLog.spDebug("restart invokeLater delay={}ms cfg.refresh={}ms",
                    invokeLaterDelay, refreshTime);
        });
    }

    public void restartPathfinding(WorldPoint start, Set<WorldPoint> ends) {
        restartPathfinding(start, ends, true);
    }

    public void restartPathfinding(WorldPoint start, WorldPoint end) {
        restartPathfinding(start, Set.of(end), true);
    }

    public boolean isNearPath(WorldPoint location) {
        if (pathfinder == null || !pathfinder.isDone() || pathfinder.getPath() == null || pathfinder.getPath().isEmpty() ||
                config.recalculateDistance() < 0 || lastLocation.equals(lastLocation = location)) {
            return true;
        }

        var reachableTiles = Rs2Tile.getReachableTilesFromTile(location, config.recalculateDistance() - 1);
        for (WorldPoint point : pathfinder.getPath()) {
            if (reachableTiles.containsKey(point)) {
                return true;
            }
        }

        return false;
    }

    private final Pattern TRANSPORT_OPTIONS_REGEX = Pattern.compile("^(avoidWilderness|use\\w+|useTeleportationItems)$");

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!CONFIG_GROUP.equals(event.getGroup())) {
            return;
        }

		// Reset config in Rs2Walker when changed
		Rs2Walker.setConfig(config);

        if ("reloadTransportDefinitions".equals(event.getKey())) {
            if (!Boolean.parseBoolean(event.getNewValue())) {
                return;
            }
            try {
                HashMap<WorldPoint, Set<Transport>> next = Transport.reloadFromResources();
                if (pathfinderConfig != null) {
                    pathfinderConfig.getAllTransports().clear();
                    pathfinderConfig.getAllTransports().putAll(next);
                    pathfinderConfig.invalidateTransportRefreshCache();
                    WorldPoint wp = Rs2Player.getWorldLocation();
                    if (Microbot.isLoggedIn() && wp != null) {
                        pathfinderConfig.refresh(wp);
                    }
                    WebWalkLog.spInfo("transport TSV reload ok keys={}", next.size());
                }
            } catch (Exception e) {
                WebWalkLog.spWarn("transport TSV reload failed", e);
            } finally {
                configManager.setConfiguration(CONFIG_GROUP, "reloadTransportDefinitions", "false");
            }
            return;
        }

        if ("drawDebugPanel".equals(event.getKey())) {
            if (config.drawDebugPanel()) {
                overlayManager.add(debugOverlayPanel);
            } else {
                overlayManager.remove(debugOverlayPanel);
            }
            return;
        }

        if ("showETA".equals(event.getKey())) {
            if (config.showETA()) {
                overlayManager.add(etaOverlayPanel);
            } else {
                overlayManager.remove(etaOverlayPanel);
            }
            return;
        }

        // Transport option changed; rerun pathfinding
        if (TRANSPORT_OPTIONS_REGEX.matcher(event.getKey()).find()) {
            if (pathfinder != null) {
                restartPathfinding(pathfinder.getStart(), pathfinder.getTargets());
            }
        }
    }

	@Subscribe
	public void onPluginMessage(PluginMessage event) {
		if (!CONFIG_GROUP.equals(event.getNamespace())) {
			return;
		}

		String action = event.getName();
		if (PLUGIN_MESSAGE_PATH.equals(action)) {
			Map<String, Object> data = event.getData();
			Object objStart = data.getOrDefault(PLUGIN_MESSAGE_START, null);
			Object objTarget = data.getOrDefault(PLUGIN_MESSAGE_TARGET, null);
			Object objConfigOverride = data.getOrDefault(PLUGIN_MESSAGE_CONFIG_OVERRIDE, null);

			@SuppressWarnings("unchecked")
			Map<String, Object> configOverride = (objConfigOverride instanceof Map<?,?>) ? ((Map<String, Object>) objConfigOverride) : null;
			if (configOverride != null && !configOverride.isEmpty()) {
				ShortestPathPlugin.configOverride.clear();
				for (String key : configOverride.keySet()) {
                    ShortestPathPlugin.configOverride.put(key, configOverride.get(key));
				}
				cacheConfigValues();
			}

			if (objStart == null && objTarget == null) {
				return;
			}

			WorldPoint start = (objStart instanceof WorldPoint) ? (WorldPoint) objStart
				: ((objStart instanceof Integer) ? WorldPointUtil.unpackWorldPoint((int) objStart) : new WorldPoint(WorldPointUtil.UNDEFINED, WorldPointUtil.UNDEFINED, WorldPointUtil.UNDEFINED));

			if (start.equals(new WorldPoint(WorldPointUtil.UNDEFINED, WorldPointUtil.UNDEFINED, WorldPointUtil.UNDEFINED))) {
				if (client.getLocalPlayer() == null) {
					return;
				}
				start = client.getLocalPlayer().getWorldLocation();
			}

			Set<WorldPoint> targets = new HashSet<>();
			if (objTarget instanceof Integer) {
				int packedPoint = (Integer) objTarget;
				if (packedPoint == WorldPointUtil.UNDEFINED) {
					return;
				}
				targets.add(WorldPointUtil.unpackWorldPoint(packedPoint));
			} else if (objTarget instanceof WorldPoint) {
				WorldPoint point = (WorldPoint) objTarget;
				if (point.equals(new WorldPoint(WorldPointUtil.UNDEFINED, WorldPointUtil.UNDEFINED, WorldPointUtil.UNDEFINED))) {
					return;
				}
				targets.add(point);
			} else if (objTarget instanceof Set<?>) {
				@SuppressWarnings("unchecked")
				Set<Object> objTargets = (Set<Object>) objTarget;
				for (Object obj : objTargets) {
					WorldPoint point = new WorldPoint(WorldPointUtil.UNDEFINED, WorldPointUtil.UNDEFINED, WorldPointUtil.UNDEFINED);
					if (obj instanceof Integer) {
						point = WorldPointUtil.unpackWorldPoint((Integer) obj);
					} else if (obj instanceof WorldPoint) {
						point = (WorldPoint) obj;
					}
					if (point.equals(new WorldPoint(WorldPointUtil.UNDEFINED, WorldPointUtil.UNDEFINED, WorldPointUtil.UNDEFINED))) {
						return;
					}
					targets.add(point);
				}
			}

			boolean useOld = targets.isEmpty() && pathfinder != null;
			restartPathfinding(start, useOld ? pathfinder.getTargets() : targets, useOld);
		} else if (PLUGIN_MESSAGE_CLEAR.equals(action)) {
			ShortestPathPlugin.configOverride.clear();
			cacheConfigValues();
			clearPluginTargets("shortest-path-plugin:plugin-message-clear");
		}
	}

	private void cacheConfigValues() {
		drawCollisionMap = override("drawCollisionMap", config.drawCollisionMap());
		drawMap = override("drawMap", config.drawMap());
		drawMinimap = override("drawMinimap", config.drawMinimap());
		drawTiles = override("drawTiles", config.drawTiles());
		drawTransports = override("drawTransports", config.drawTransports());
		showTransportInfo = override("showTransportInfo", config.showTransportInfo());

		colourCollisionMap = override("colourCollisionMap", config.colourCollisionMap());
		colourPath = override("colourPath", config.colourPath());
		colourPathCalculating = override("colourPathCalculating", config.colourPathCalculating());
		colourText = override("colourText", config.colourText());
		colourTransports = override("colourTransports", config.colourTransports());

		tileCounterStep = override("tileCounterStep", config.tileCounterStep());

		showTileCounter = override("showTileCounter", config.showTileCounter());
		pathStyle = override("pathStyle", config.pathStyle());
	}

    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        lastMenuOpenedPoint = client.getMouseCanvasPosition();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            pendingLoginRefresh = true;
            consumeSessionInitRouteClearIfNeeded();
        }
    }

    /**
     * One-off map/route reset when the session starts ({@link ShortestPathPlugin} loaded while already
     * in game, or first {@link GameState#LOGGED_IN} after startup). Skipped on later LOGGED_IN events
     * (world hops), matching leagues calibration style init rather than clear-on-every-login.
     */
    private void consumeSessionInitRouteClearIfNeeded() {
        if (!sessionInitRouteClearPending || client == null || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        sessionInitRouteClearPending = false;
        if (worldMapPointManager != null) {
            clearPluginTargets("shortest-path-plugin:session-init");
        } else {
            clearRouteState();
        }
    }

    private static boolean isLeagueAreaSelectionVarbit(int varbitId) {
        switch (varbitId) {
            case VarbitID.LEAGUE_AREA_SELECTION_0:
            case VarbitID.LEAGUE_AREA_SELECTION_1:
            case VarbitID.LEAGUE_AREA_SELECTION_2:
            case VarbitID.LEAGUE_AREA_SELECTION_3:
            case VarbitID.LEAGUE_AREA_SELECTION_4:
            case VarbitID.LEAGUE_AREA_SELECTION_5:
                return true;
            default:
                return false;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        int id = event.getVarbitId();
        if (id < 0 || !isLeagueAreaSelectionVarbit(id)) {
            return;
        }
        if (!Rs2LeaguesTransport.isLeaguesActive() || pathfinderConfig == null || client == null
                || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        pathfinderConfig.invalidateTransportRefreshCache();
        pendingLoginRefresh = true;
        WebWalkLog.leaguesInfo(
                "leagues area-selection varbit {} -> {} | transport cache invalidated, pathfinder refresh queued",
                id, event.getValue());
        Rs2LeaguesTransport.calibrateMissingLandingsAsync(Rs2LeaguesTransport.unlockedRegions(), true);
    }

	private static void clearRouteState()
	{
		// Unit-test fallback: `setTarget(null)` requires UI wiring; this must stay semantically equivalent to its cleanup.
		ShortestPathPlugin.pathfinder = null;
		ShortestPathPlugin.marker = null;
		ShortestPathPlugin.startPointSet = false;
	}

    void handlePendingLoginRefresh() {
        if (pendingLoginRefresh && pathfinderConfig != null) {
            pendingLoginRefresh = false;
            try {
                pathfinderConfig.refresh();
            } catch (Exception e) {
                log.warn("[ShortestPath] post-login refresh failed", e);
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        handlePendingLoginRefresh();

        final WorldPoint myLoc = Rs2Player.getWorldLocation();
        final Pathfinder pathfinder = ShortestPathPlugin.pathfinder;
        if (myLoc == null || pathfinder == null || !pathfinder.isDone()) {
            return;
        }

        // Scripted ShortestPath walk (panel/hotkey): never overlay-clear map route while trigger is armed —
        // avoids setTarget(null) when currentTarget is briefly inconsistent with pathfinder arrival heuristic.
        if (shortestPathScript != null && shortestPathScript.getTriggerWalker() != null) {
            return;
        }

        final List<WorldPoint> path = pathfinder.getPath();
        if (path == null) return;

        // Rs2Walker completion clears marker/pathfinder; overlay "arrival" here only applies to
        // map-only routing. With large finishDistance the heuristic can fire early and wipe
        // pathfinder while a script walk is still in progress.
        if (Rs2Walker.getCurrentTarget() != null) {
            return;
        }

        for (WorldPoint target : pathfinder.getTargets()) {
            if (myLoc.distanceTo(target) < reachedDistance
                    && Rs2Tile.getReachableTilesFromTile(myLoc, reachedDistance).containsKey(path.get(path.size() - 1))) {
                // TOCTOU: walk thread may set currentTarget after the outer guard on this same tick.
                if (Rs2Walker.getCurrentTarget() != null) {
                    return;
                }
                if (shortestPathScript != null && shortestPathScript.getTriggerWalker() != null) {
                    return;
                }
                clearPluginTargets("shortest-path-plugin:overlay-arrival-heuristic");
                if (Microbot.getClientThread().scheduledFuture != null) {
                    Microbot.getClientThread().scheduledFuture.cancel(true);
                }
            }
        }
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (client.isKeyPressed(KeyCode.KC_SHIFT)
                && event.getType() == MenuAction.WALK.getId()) {
            addMenuEntry(event, SET, TARGET, 1);
            if (pathfinder != null) {
                if (!pathfinder.getTargets().isEmpty()) {
                    addMenuEntry(event, SET, TARGET + ColorUtil.wrapWithColorTag(" " +
                            (pathfinder.getTargets().size() + 1), JagexColors.MENU_TARGET), 1);
                }
                for (WorldPoint target : pathfinder.getTargets()) {
                    if (target != null) {
                        addMenuEntry(event, SET, START, 1);
                        break;
                    }
                }
                WorldPoint selectedTile = getSelectedWorldPoint();
                if (pathfinder.isDone() && pathfinder.getPath() != null) {
                    for (WorldPoint tile : pathfinder.getPath()) {
                        if (tile.equals(selectedTile)) {
                            addMenuEntry(event, CLEAR, PATH, 1);
                            break;
                        }
                    }
                }
            }
        }

        final Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);

        if (map != null
                && map.getBounds().contains(
                client.getMouseCanvasPosition().getX(),
                client.getMouseCanvasPosition().getY())) {
            addMenuEntry(event, SET, TARGET, 0);
            if (Microbot.isDebug()) {
                addMenuEntry(event, SET, TEST, 0);
            }
            final Pathfinder pathfinder = ShortestPathPlugin.pathfinder;
            if (pathfinder != null) {
                if (!pathfinder.getTargets().isEmpty()) {
                    addMenuEntry(event, SET, TARGET + ColorUtil.wrapWithColorTag(" " +
                            (pathfinder.getTargets().size() + 1), JagexColors.MENU_TARGET), 0);
                }
                for (WorldPoint target : pathfinder.getTargets()) {
                    if (target != null) {
                        addMenuEntry(event, SET, START, 0);
                        addMenuEntry(event, CLEAR, PATH, 0);
                    }
                }
            }
        }

        final Shape minimap = getMinimapClipArea();

        if (minimap != null && pathfinder != null
                && minimap.contains(
                client.getMouseCanvasPosition().getX(),
                client.getMouseCanvasPosition().getY())) {
            addMenuEntry(event, CLEAR, PATH, 0);
        }

        if (minimap != null && pathfinder != null
                && ("Floating World Map".equals(Text.removeTags(event.getOption()))
                || "Close Floating panel".equals(Text.removeTags(event.getOption())))) {
            addMenuEntry(event, CLEAR, PATH, 1);
        }
    }

    public static Map<WorldPoint, Set<Transport>> getTransports() {
        return pathfinderConfig.getTransports();
    }

    public CollisionMap getMap() {
        return pathfinderConfig.getMap();
    }

	public static boolean override(String configOverrideKey, boolean defaultValue) {
		if (!configOverride.isEmpty()) {
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof Boolean) {
				return (boolean) value;
			}
		}
		return defaultValue;
	}

	private Color override(String configOverrideKey, Color defaultValue) {
		if (!configOverride.isEmpty()) {
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof Color) {
				return (Color) value;
			}
		}
		return defaultValue;
	}

	public static int override(String configOverrideKey, int defaultValue) {
		if (!configOverride.isEmpty()) {
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof Integer) {
				return (int) value;
			}
		}
		return defaultValue;
	}

	public static TeleportationItem override(String configOverrideKey, TeleportationItem defaultValue) {
		if (!configOverride.isEmpty()) {
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof String) {
				TeleportationItem teleportationItem = TeleportationItem.fromType((String) value);
				if (teleportationItem != null) {
					return teleportationItem;
				}
			}
		}
		return defaultValue;
	}

	private TileCounter override(String configOverrideKey, TileCounter defaultValue) {
		if (!configOverride.isEmpty()) {
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof String) {
				TileCounter tileCounter = TileCounter.fromType((String) value);
				if (tileCounter != null) {
					return tileCounter;
				}
			}
		}
		return defaultValue;
	}

	private TileStyle override(String configOverrideKey, TileStyle defaultValue) {
		if (!configOverride.isEmpty()) {
			Object value = configOverride.get(configOverrideKey);
			if (value instanceof String) {
				TileStyle tileStyle = TileStyle.fromType((String) value);
				if (tileStyle != null) {
					return tileStyle;
				}
			}
		}
		return defaultValue;
	}

    private void onMenuOptionClicked(MenuEntry entry) {
        if (entry.getOption().equals(SET) && entry.getTarget().equals(TARGET)) {
            WorldPoint worldPoint = getSelectedWorldPoint();
            shortestPathScript.setTriggerWalker(worldPoint);
        }
        if (entry.getOption().equals(SET) && entry.getTarget().equals(TEST)) {
            //For debugging you can use setTarget, it will calculate path without walking
            WorldPoint worldPoint = getSelectedWorldPoint();
            setTarget(worldPoint);
        }

        if (entry.getOption().equals(SET) && entry.getTarget().equals(START)) {
            setStart(getSelectedWorldPoint());
        }

        if (entry.getOption().equals(CLEAR) && entry.getTarget().equals(PATH)) {
			shortestPathScript.setTriggerWalker(null, "menu:world-map-clear-path");
        }
    }

    private WorldPoint getSelectedWorldPoint() {
        if (client.getWidget(ComponentID.WORLD_MAP_MAPVIEW) == null) {
            if (client.getSelectedSceneTile() != null) {
                return client.isInInstancedRegion()
                        ? WorldPoint.fromLocalInstance(client, client.getSelectedSceneTile().getLocalLocation())
                        : client.getSelectedSceneTile().getWorldLocation();
            }
        } else {
            WorldPoint mapPoint = calculateMapPoint(client.isMenuOpen() ? lastMenuOpenedPoint : client.getMouseCanvasPosition());
            if (mapPoint != null) {
                WorldMapData worldMapData = client.getWorldMap().getWorldMapData();
                if (worldMapData != null && !worldMapData.surfaceContainsPosition(mapPoint.getX(), mapPoint.getY())) {
                    log.warn("[ShortestPath] World map target {} is a dungeon display coordinate (not on surface map). " +
                            "The actual game tiles may be at different coordinates. " +
                            "For accurate dungeon navigation, close the world map and right-click a tile in the game view instead.",
                            mapPoint);
                    return null;
                }
            }
            return mapPoint;
        }
        return null;
    }

    public void setTarget(WorldPoint target) {
        setTarget(target, false);
    }

    private void setTarget(WorldPoint target, boolean append) {
        Set<WorldPoint> targets = new HashSet<>();
        if (target != null) {
            targets.add(target);
        }
        setTargets(targets, append, null);
    }

    /**
     * Drops ShortestPath targets from UI/state; if Microbot walk is active, clears walker with {@code reason}.
     */
    private void clearPluginTargets(String clearActiveWalkerReason) {
        setTargets(Collections.emptySet(), false, clearActiveWalkerReason);
    }

    private void setTargets(Set<WorldPoint> targets, boolean append) {
        setTargets(targets, append, null);
    }

    private void setTargets(Set<WorldPoint> targets, boolean append, String clearActiveWalkerReason) {
        if (targets == null || targets.isEmpty()) {
            // Map-only routing clears here; Microbot walks must clear Rs2Walker route so
            // pathfinder, marker, and Rs2Walker.currentTarget stay in sync. Otherwise plugin-only
            // teardown leaves currentTarget set → processWalk sees pathfinder-still-null EXIT.
            if (Rs2Walker.getCurrentTarget() != null) {
                Rs2Walker.clearWalkingRoute(
                        clearActiveWalkerReason != null && !clearActiveWalkerReason.isBlank()
                                ? clearActiveWalkerReason
                                : "shortest-path-plugin:setTargets-empty-active-walker");
                return;
            }
            synchronized (pathfinderMutex) {
                if (pathfinder != null) {
                    pathfinder.cancel();
                }
                pathfinder = null;
            }

            worldMapPointManager.removeIf(x -> x == marker);
            marker = null;
            startPointSet = false;
        } else {
            Player localPlayer = client.getLocalPlayer();
            if (!startPointSet && localPlayer == null) {
                return;
            }
            worldMapPointManager.removeIf(x -> x == marker);
            if (targets.size() == 1) {
                marker = new WorldMapPoint(targets.iterator().next(), MARKER_IMAGE);
                marker.setName("Target");
                marker.setTarget(marker.getWorldPoint());
                marker.setJumpOnClick(true);
                worldMapPointManager.add(marker);
            }

            final Pathfinder pathfinder = ShortestPathPlugin.pathfinder;
            final WorldPoint start;
            if (startPointSet && pathfinder != null) {
                start = pathfinder.getStart();
                lastLocation = WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation());
            } else {
                WorldPoint rawStart = WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation());
                // When the player is inside a POH instance, the raw instance-template tile
                // (e.g. (1941,7052,3)) doesn't match any registered POH transport origin — the
                // POH transports are keyed to PohPanel.instance.tilePanel.getTile() (the exit
                // portal). Without this remap the pathfinder never considers any POH teleport
                // and the walker tight-loops on null LocalPoint canvas-walks.
                //
                // We gate on "in an instance AND the POH panel has an exit-portal tile
                // configured". PohTeleports.isInHouse() is too strict — it additionally
                // requires POH_EXIT_PORTAL to be currently loaded in the scene, which fails
                // on larger houses where the portal is out of render range.
                WorldPoint exitPortal = PohPanel.getExitPortalTile();
                boolean inInstance = client.getTopLevelWorldView().getScene().isInstance();
                if (exitPortal != null && inInstance) {
                    Microbot.log("[ShortestPath] In POH instance — remapping pathfinder start "
                            + rawStart + " -> exit portal " + exitPortal);
                    start = exitPortal;
                } else {
                    start = rawStart;
                }
                lastLocation = start;
            }
            final Set<WorldPoint> destinations = new HashSet<>(targets);
            if (pathfinder != null && append) {
                destinations.addAll(pathfinder.getTargets());
            }
            restartPathfinding(start, destinations, append);
        }
    }

    private void setStart(WorldPoint start) {
        if (pathfinder == null) {
            return;
        }
        startPointSet = true;
        restartPathfinding(start, pathfinder.getTargets());
    }

    public WorldPoint calculateMapPoint(Point point) {
        WorldMap worldMap = client.getWorldMap();
        float zoom = worldMap.getWorldMapZoom();
        final WorldPoint mapPoint = new WorldPoint(worldMap.getWorldMapPosition().getX(), worldMap.getWorldMapPosition().getY(), 0);
        final Point middle = mapWorldPointToGraphicsPoint(mapPoint);

        if (point == null || middle == null) {
            return null;
        }

        final int dx = (int) ((point.getX() - middle.getX()) / zoom);
        final int dy = (int) ((-(point.getY() - middle.getY())) / zoom);

        return mapPoint.dx(dx).dy(dy);
    }

    public Point mapWorldPointToGraphicsPoint(WorldPoint worldPoint) {
        WorldMap worldMap = client.getWorldMap();

        float pixelsPerTile = worldMap.getWorldMapZoom();

        Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);
        if (map != null) {
            Rectangle worldMapRect = map.getBounds();

            int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);
            int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);

            Point worldMapPosition = worldMap.getWorldMapPosition();

            int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
            int yTileOffset = (yTileMax - worldPoint.getY() - 1) * -1;
            int xTileOffset = worldPoint.getX() + widthInTiles / 2 - worldMapPosition.getX();

            int xGraphDiff = ((int) (xTileOffset * pixelsPerTile));
            int yGraphDiff = (int) (yTileOffset * pixelsPerTile);

            yGraphDiff -= pixelsPerTile - Math.ceil(pixelsPerTile / 2);
            xGraphDiff += pixelsPerTile - Math.ceil(pixelsPerTile / 2);

            yGraphDiff = worldMapRect.height - yGraphDiff;
            yGraphDiff += (int) worldMapRect.getY();
            xGraphDiff += (int) worldMapRect.getX();

            return new Point(xGraphDiff, yGraphDiff);
        }
        return null;
    }

    private void addMenuEntry(MenuEntryAdded event, String option, String target, int position) {
        List<MenuEntry> entries = new LinkedList<>(Arrays.asList(client.getMenuEntries()));

        if (entries.stream().anyMatch(e -> e.getOption().equals(option) && e.getTarget().equals(target))) {
            return;
        }

        client.createMenuEntry(position)
                .setOption(option)
                .setTarget(target)
                .setParam0(event.getActionParam0())
                .setParam1(event.getActionParam1())
                .setIdentifier(event.getIdentifier())
                .setType(MenuAction.RUNELITE)
                .onClick(this::onMenuOptionClicked);
    }

    private Widget getMinimapDrawWidget() {
        if (client.isResized()) {
            if (client.getVarbitValue(Varbits.SIDE_PANELS) == 1) {
                return client.getWidget(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA);
            }
            return client.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA);
        }
        return client.getWidget(ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);
    }

    private Shape getMinimapClipAreaSimple() {
        Widget minimapDrawArea = getMinimapDrawWidget();

        if (minimapDrawArea == null || minimapDrawArea.isHidden()) {
            return null;
        }

        Rectangle bounds = minimapDrawArea.getBounds();

        return new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
    }

    public Shape getMinimapClipArea() {
        Widget minimapWidget = getMinimapDrawWidget();

        if (minimapWidget == null || minimapWidget.isHidden() || !minimapRectangle.equals(minimapRectangle = minimapWidget.getBounds())) {
            minimapClipFixed = null;
            minimapClipResizeable = null;
            minimapSpriteFixed = null;
            minimapSpriteResizeable = null;
        }

        if (minimapWidget == null || minimapWidget.isHidden()) {
            return null;
        }

        if (client.isResized()) {
            if (minimapClipResizeable != null) {
                return minimapClipResizeable;
            }
            if (minimapSpriteResizeable == null) {
                minimapSpriteResizeable = spriteManager.getSprite(SpriteID.RESIZEABLE_MODE_MINIMAP_ALPHA_MASK, 0);
            }
            if (minimapSpriteResizeable != null) {
                minimapClipResizeable = bufferedImageToPolygon(minimapSpriteResizeable);
                return minimapClipResizeable;
            }
            return getMinimapClipAreaSimple();
        }
        if (minimapClipFixed != null) {
            return minimapClipFixed;
        }
        if (minimapSpriteFixed == null) {
            minimapSpriteFixed = spriteManager.getSprite(SpriteID.FIXED_MODE_MINIMAP_ALPHA_MASK, 0);
        }
        if (minimapSpriteFixed != null) {
            minimapClipFixed = bufferedImageToPolygon(minimapSpriteFixed);
            return minimapClipFixed;
        }
        return getMinimapClipAreaSimple();
    }

    private Polygon bufferedImageToPolygon(BufferedImage image) {
        Color outsideColour = null;
        Color previousColour;
        final int width = image.getWidth();
        final int height = image.getHeight();
        List<java.awt.Point> points = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            previousColour = outsideColour;
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int a = (rgb & 0xff000000) >>> 24;
                int r = (rgb & 0x00ff0000) >> 16;
                int g = (rgb & 0x0000ff00) >> 8;
                int b = (rgb & 0x000000ff) >> 0;
                Color colour = new Color(r, g, b, a);
                if (x == 0 && y == 0) {
                    outsideColour = colour;
                    previousColour = colour;
                }
                if (!colour.equals(outsideColour) && previousColour.equals(outsideColour)) {
                    points.add(new java.awt.Point(x, y));
                }
                if ((colour.equals(outsideColour) || x == (width - 1)) && !previousColour.equals(outsideColour)) {
                    points.add(0, new java.awt.Point(x, y));
                }
                previousColour = colour;
            }
        }
        int offsetX = minimapRectangle.x;
        int offsetY = minimapRectangle.y;
        Polygon polygon = new Polygon();
        for (java.awt.Point point : points) {
            polygon.addPoint(point.x + offsetX, point.y + offsetY);
        }
        return polygon;
    }

    private void toggleCategory(String categoryName, Function<ShortestPathPanel, WorldPoint> targetFn) {
        // Capture panel locally so the null check is effective. Call sites
        // pass unbound method references (ShortestPathPanel::get...) so panel
        // is not dereferenced until after we've confirmed it's non-null.
        ShortestPathPanel p = panel;
        if (p == null || !Microbot.isLoggedIn()) {
            return;
        }
        WorldPoint target = targetFn.apply(p);
        if (target == null) {
            Microbot.log("WebWalker: no " + categoryName + " selected in the panel.");
            return;
        }
        WorldPoint current = shortestPathScript.getTriggerWalker();
        if (target.equals(current)) {
            p.stopWalking();
        } else {
            p.startWalking(target);
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (client == null || !Microbot.isLoggedIn())
        {
            return;
        }
        /**
         * We took decided to avoid "ESC" as this conflicts with the
         * osrs keybindings and closing the world map
         * Therefor CTRL + X seemed a bit more robust and userfriendly
         */
        if (e.getKeyCode() == KeyEvent.VK_X && e.isControlDown()) {
			shortestPathScript.setTriggerWalker(null, "hotkey:ctrl+x-stop-route");
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }

    private final HotkeyListener customLocationHotkeyListener = new HotkeyListener(() -> config.customLocationToggleHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleCategory("custom location", ShortestPathPanel::getCustomLocation);
        }
    };

    private final HotkeyListener bankHotkeyListener = new HotkeyListener(() -> config.bankToggleHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleCategory("bank", ShortestPathPanel::getBankTarget);
        }
    };

    private final HotkeyListener nearestBankHotkeyListener = new HotkeyListener(() -> config.nearestBankHotkey()) {
        @Override
        public void hotkeyPressed() {
            if (panel == null || !Microbot.isLoggedIn()) return;
            if (shortestPathScript.getTriggerWalker() != null) {
                panel.stopWalking();
            } else {
                panel.startWalkingNearestBank();
            }
        }
    };

    private final HotkeyListener depositBoxHotkeyListener = new HotkeyListener(() -> config.depositBoxToggleHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleCategory("deposit box", ShortestPathPanel::getDepositBoxTarget);
        }
    };

    private final HotkeyListener nearestDepositBoxHotkeyListener = new HotkeyListener(() -> config.nearestDepositBoxHotkey()) {
        @Override
        public void hotkeyPressed() {
            if (panel == null || !Microbot.isLoggedIn()) return;
            if (shortestPathScript.getTriggerWalker() != null) {
                panel.stopWalking();
            } else {
                panel.startWalkingNearestDepositBox();
            }
        }
    };

    private final HotkeyListener slayerMasterHotkeyListener = new HotkeyListener(() -> config.slayerMasterToggleHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleCategory("slayer master", ShortestPathPanel::getSlayerMasterTarget);
        }
    };

    private final HotkeyListener questHotkeyListener = new HotkeyListener(() -> config.questToggleHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleCategory("quest location", ShortestPathPanel::getCurrentQuestLocation);
        }
    };

    private final HotkeyListener clueHotkeyListener = new HotkeyListener(() -> config.clueToggleHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleCategory("clue location", ShortestPathPanel::getCurrentClueLocation);
        }
    };

    private final HotkeyListener farmingHotkeyListener = new HotkeyListener(() -> config.farmingToggleHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleCategory("farming location", ShortestPathPanel::getSelectedFarmingLocation);
        }
    };

    private final HotkeyListener hunterHotkeyListener = new HotkeyListener(() -> config.hunterToggleHotkey()) {
        @Override
        public void hotkeyPressed() {
            toggleCategory("hunter area", ShortestPathPanel::getSelectedHuntingArea);
        }
    };
}
