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
import lombok.Setter;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
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
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ColorUtil;
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
import java.util.regex.Pattern;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Web Walker",
        description = "Draws the shortest path to a chosen destination on the map (right click a spot on the world map to use)",
        tags = {"pathfinder", "map", "waypoint", "navigation", "microbot"},
        enabledByDefault = true,
        alwaysOn = true
)
public class ShortestPathPlugin extends Plugin implements KeyListener {
    protected static final String CONFIG_GROUP = "shortestpath";
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
    @Getter
    @Setter
    public static WorldMapPoint marker;
    @Setter
    public static WorldPoint lastLocation = new WorldPoint(0, 0, 0);
    private NavigationButton navButton;
    private Shape minimapClipFixed;
    private Shape minimapClipResizeable;
    private BufferedImage minimapSpriteFixed;
    private BufferedImage minimapSpriteResizeable;
    private Rectangle minimapRectangle = new Rectangle();

    @Getter
    @Setter
    public static ExecutorService pathfindingExecutor = Executors.newSingleThreadExecutor();
    @Getter
    @Setter
    public static Future<?> pathfinderFuture;
    @Getter
    public static final Object pathfinderMutex = new Object();
	private static final Map<String, Object> configOverride = new HashMap<>(50);
    @Getter
    @Setter
    public static Pathfinder pathfinder;
    @Getter
    public static PathfinderConfig pathfinderConfig;
    @Getter
    @Setter
    public static boolean startPointSet = false;
    @Setter
    private static int reachedDistance;
    @Getter(AccessLevel.PACKAGE)
    private ShortestPathScript shortestPathScript;
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
        final BufferedImage icon = ImageUtil.loadImageResource(ShortestPathPlugin.class, "panel_icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Web Walker")
                .icon(icon)
                .priority(8)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);

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
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(pathOverlay);
        overlayManager.remove(pathMinimapOverlay);
        overlayManager.remove(pathMapOverlay);
        overlayManager.remove(pathMapTooltipOverlay);
        overlayManager.remove(debugOverlayPanel);
        clientToolbar.removeNavigation(navButton);
        navButton = null;
        panel = null;

        shortestPathScript.shutdown();

        exit();
        keyManager.unregisterKeyListener(this);
    }

    //Method from microbot
    public static void exit() {
        if (pathfindingExecutor != null) {
            Rs2Walker.setTarget(null);
            pathfindingExecutor.shutdownNow();
            pathfindingExecutor = null;
        }
    }

    public void restartPathfinding(WorldPoint start, Set<WorldPoint> ends, boolean canReviveFiltered) {
        synchronized (pathfinderMutex) {
            if (pathfinder != null) {
                pathfinder.cancel();
                pathfinderFuture.cancel(true);
            }

            if (pathfindingExecutor == null) {
                ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("shortest-path-%d").build();
                pathfindingExecutor = Executors.newSingleThreadExecutor(shortestPathNaming);
            }
        }

        getClientThread().invokeLater(() -> {
            pathfinderConfig.refresh();
            pathfinderConfig.filterLocations(ends, canReviveFiltered);
            synchronized (pathfinderMutex) {
                if (ends.isEmpty()) {
                    setTarget(null);
                } else {
                    pathfinder = new Pathfinder(pathfinderConfig, start, ends);
                    pathfinderFuture = pathfindingExecutor.submit(pathfinder);
                }
            }
        });
    }

    public void restartPathfinding(WorldPoint start, Set<WorldPoint> ends) {
        restartPathfinding(start, ends, true);
    }

    public void restartPathfinding(WorldPoint start, WorldPoint end) {
        restartPathfinding(start, Set.of(end), true);
    }

    public boolean isNearPath(WorldPoint location) {
        if (pathfinder == null || pathfinder.getPath() == null || pathfinder.getPath().isEmpty() ||
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
				this.configOverride.clear();
				for (String key : configOverride.keySet()) {
					this.configOverride.put(key, configOverride.get(key));
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
			this.configOverride.clear();
			cacheConfigValues();
			setTarget(null);
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
    public void onGameTick(GameTick tick) {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || pathfinder == null) {
            return;
        }

        var path = pathfinder.getPath();

        for (WorldPoint target : pathfinder.getTargets()) {
            if (Rs2Player.getWorldLocation().distanceTo(target) < reachedDistance
                    && Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), reachedDistance).containsKey(path.get(path.size() - 1))) {
                setTarget(null);
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
                if (pathfinder.getPath() != null) {
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
            if (pathfinder != null) {
                if (pathfinder.getTargets().size() >= 1) {
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
            setTarget(null);
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
            return calculateMapPoint(client.isMenuOpen() ? lastMenuOpenedPoint : client.getMouseCanvasPosition());
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
        setTargets(targets, append);
    }

    private void setTargets(Set<WorldPoint> targets, boolean append) {
        if (targets == null || targets.isEmpty()) {
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

            WorldPoint start = WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation());
            lastLocation = start;
            if (startPointSet && pathfinder != null) {
                start = pathfinder.getStart();
            }
            Set<WorldPoint> destinations = new HashSet<>(targets);
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
            Rs2Walker.setTarget(null);
            shortestPathScript.setTriggerWalker(null);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {

    }
}
