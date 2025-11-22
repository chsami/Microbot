package net.runelite.client.plugins.microbot;

import com.google.inject.Injector;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.annotations.Component;
import net.runelite.api.annotations.Varbit;
import net.runelite.api.annotations.Varp;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteDebug;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ProfileManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.NPCManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.loottracker.LootTrackerRecord;
import net.runelite.client.plugins.microbot.configs.SpecialAttackConfigs;
import net.runelite.client.plugins.microbot.pouch.PouchScript;
import net.runelite.client.plugins.microbot.util.cache.Rs2VarPlayerCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2VarbitCache;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import org.slf4j.event.Level;

import javax.inject.Inject;
import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.*;

@Slf4j
@NoArgsConstructor
public class Microbot {
    //Version path used to load the client faster when developing by checking version number
    //If the version is the same as the current version we do not download the latest .jar
    //Resulting in a faster startup
    private static final String VERSION_FILE_PATH = "debug_temp_version.txt";
    private static final ScheduledExecutorService xpSchedulor = Executors.newSingleThreadScheduledExecutor();
    @Getter
    private static final SpecialAttackConfigs specialAttackConfigs = new SpecialAttackConfigs();
    public static MenuEntry targetMenu;
    public static boolean isGainingExp = false;
    public static AtomicBoolean pauseAllScripts = new AtomicBoolean(false);
    public static String status = "IDLE";

    // Feature Flags
    public static boolean enableAutoRunOn = true;
    public static boolean useStaminaPotsIfNeeded = true;
    public static int runEnergyThreshold = 1000;
    public static boolean isCantReachTargetDetectionEnabled = false;

    @Getter
    @Inject
    public static MouseManager mouseManager;
    @Getter
    @Inject
    public static NaturalMouse naturalMouse;
    @Getter
    private static Mouse mouse = new VirtualMouse();
    @Getter
    @Inject
    private static Client client;
    @Getter
    @Inject
    private static ClientThread clientThread;
    @Getter
    @Inject
    private static EventBus eventBus;
    @Getter
    @Inject
    private static WorldMapPointManager worldMapPointManager;
    @Getter
    @Inject
    private static SpriteManager spriteManager;
    @Getter
    @Inject
    private static ItemManager itemManager;
    @Getter
    @Inject
    private static Notifier notifier;
    @Getter
    @Inject
    private static NPCManager npcManager;
    @Getter
    @Inject
    private static ProfileManager profileManager;
    @Getter
    @Inject
    private static ConfigManager configManager;
    @Getter
    @Inject
    private static WorldService worldService;
    @Getter
    @Inject
    private static PluginManager pluginManager;
    @Getter
    @Inject
    private static WorldMapOverlay worldMapOverlay;
    @Getter
    @Inject
    private static InfoBoxManager infoBoxManager;
    @Getter
    @Inject
    private static ChatMessageManager chatMessageManager;
    @Getter
    @Inject
    private static TooltipManager tooltipManager;
    private static ScheduledFuture<?> xpSchedulorFuture;
    /**
     * PouchScript is injected in the main MicrobotPlugin as it's being used in multiple scripts
     */
    @Getter
    @Inject
    private static PouchScript pouchScript;
    /**
     * Tracks the last known map regions for login/region change detection.
     */
    @Getter
    @Setter
    private static int[] lastKnownRegions = null;

    /**
     * Tracks the last GameState for login/region change detection.
     */
    @Getter
    @Setter
    private static GameState lastGameState = null;

    public static boolean cantReachTarget = false;
    public static volatile long cantHopWorld = System.currentTimeMillis();

    public static int cantReachTargetRetries = 0;

    @Getter
    private static final BlockingEventManager blockingEventManager = new BlockingEventManager();

    @Getter
    private static HashMap<String, Integer> scriptRuntimes = new HashMap<>();

    @Getter
    private static Rs2ItemManager rs2ItemManager = new Rs2ItemManager();

    @Setter
    @Getter
    public static boolean isRs2CacheEnabled = false;

    @Inject
    @Getter
    private static Rs2PlayerCache rs2PlayerCache;

    /**
     * Get the total runtime of the script
     *
     * @return the {@link Duration} the account has been logged in
     */
    public static Duration getLoginTime() {
        return LoginManager.getLoginDuration();
    }

    /**
     * Checking the Report button will ensure that we are logged in, as there seems to be a small moment in time
     * when at the welcome screen that Rs2Settings.isLevelUpNotificationsEnabled() will return true then turn back to false
     * even if {@code Varbits.DISABLE_LEVEL_UP_INTERFACE} is 1 after clicking play button
     */
    @Component
    private static final int REPORT_BUTTON_COMPONENT_ID = 10616833;

    public static boolean isDebug() {
        return java.lang.management.ManagementFactory.getRuntimeMXBean().
                getInputArguments().toString().contains("-agentlib:jdwp");
    }

    public static int getVarbitValue(@Varbit int varbit) {
        if (isRs2CacheEnabled()) {
            return Rs2VarbitCache.getVarbitValue(varbit);
        }
        return rs2PlayerCache.getVarbitValue(varbit);
    }

    public static int getVarbitPlayerValue(@Varp int varpId) {
        if (isRs2CacheEnabled()) {
            return Rs2VarPlayerCache.getVarPlayerValue(varpId);
        }
        return rs2PlayerCache.getVarpValue(varpId);
    }

    public static EnumComposition getEnum(int id) {
        return getClientThread().runOnClientThreadOptional(() -> getClient().getEnum(id)).orElse(null);
    }

    public static StructComposition getStructComposition(int structId) {
        return getClientThread().runOnClientThreadOptional(() -> getClient().getStructComposition(structId)).orElse(null);
    }

    public static List<Integer> getDBTableRows(int table) {
        return getClientThread().runOnClientThreadOptional(() -> getClient().getDBTableRows(table)).orElse(new ArrayList<>());
    }

    public static Object[] getDBTableField(int rowID, int column, int tupleIndex) {
        return getClientThread().runOnClientThreadOptional(() -> getClient().getDBTableField(rowID, column, tupleIndex)).orElse(new Object[]{});
    }

    public static List<Integer> getDBRowsByValue(int table, int column, int tupleIndex, Object value) {
        return getClientThread().runOnClientThreadOptional(() -> getClient().getDBRowsByValue(table, column, tupleIndex, value)).orElse(new ArrayList<>());
    }

    public static void setIsGainingExp(boolean value) {
        isGainingExp = value;
        scheduleIsGainingExp();
    }

    public static void scheduleIsGainingExp() {
        if (xpSchedulorFuture != null && !xpSchedulorFuture.isDone()) {
            xpSchedulorFuture.cancel(true);
        }
        xpSchedulorFuture = xpSchedulor.schedule(() -> {
            isGainingExp = false;
        }, 4000, TimeUnit.MILLISECONDS);
    }

    public static boolean isLoggedIn() {
        return LoginManager.isLoggedIn();
    }

    public static boolean isHopping() {
        if (client == null) {
            return false;
        }
        GameState idx = client.getGameState();
        return idx == GameState.HOPPING;
    }

    private static net.runelite.api.World createWorld(int worldNumber) {
        final WorldResult worldResult = Microbot.getWorldService().getWorlds();
        if (worldResult == null) {
            log.error("World Result is null");
            return null;
        }

        final World newWorld = Microbot.getWorldService().getWorlds().findWorld(worldNumber);
        if (newWorld == null) {
            log.error("Invalid world {}", worldNumber);
            return null;
        }

        final net.runelite.api.World world = Microbot.getClient().createWorld();
        if (world == null) {
            log.error("Failed to create world {}", worldNumber);
            return null;
        }

        world.setActivity(newWorld.getActivity());
        world.setAddress(newWorld.getAddress());
        world.setId(newWorld.getId());
        world.setPlayerCount(newWorld.getPlayers());
        world.setLocation(newWorld.getLocation());
        world.setTypes(WorldUtil.toWorldTypes(newWorld.getTypes()));
        return world;
    }

    public static boolean hopToWorld(int worldNumber) {
        if (!Microbot.isLoggedIn()) {
            log.warn("Cannot hop user is not logged in");
            return false;
        }

        if (Microbot.isHopping()) {
            log.warn("Already hopping world");
            return true;
        }

        final long hopStart = System.currentTimeMillis();
        if ((Microbot.cantHopWorld - hopStart) >= 0) Microbot.cantHopWorld = hopStart - 1;
        boolean isHopping = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            final Player myPlayer = Microbot.getClient().getLocalPlayer();
            if (myPlayer != null && myPlayer.isInteracting()) {
                log.error("Local player is interacting, cannot hop worlds");
                return false;
            }

            if (Microbot.getClient().getGameState() != GameState.LOGGED_IN) {
                log.error("Game state is not logged in, cannot hop worlds");
                return false;
            }

            if (Microbot.getClient().getWorld() == worldNumber) return false;

            final net.runelite.api.World world = createWorld(worldNumber);
            if (world == null) return false;

            Microbot.getClient().openWorldHopper();
            Microbot.getClient().hopToWorld(world);
            return true;
        }).orElse(false);

        if (!isHopping) {
            log.error("Failed to invoke World Hop");
            return false;
        }

        if (sleepUntil(() -> Microbot.isHopping() || Rs2Widget.getWidget(193, 0) != null, 2_000) || (Microbot.cantHopWorld - hopStart) >= 0) {
            if (Microbot.isHopping()) return true;
            if ((Microbot.cantHopWorld - hopStart) >= 0) {
                log.error("Can't hop world, can't log into a non-members world");
                return false;
            }

            final Widget confirmRoot = Rs2Widget.getWidget(193, 0);
            if (confirmRoot != null) {
                List<Widget> children = Arrays.stream(confirmRoot.getDynamicChildren()).collect(Collectors.toList());
                Widget switchWorldWidget =
                        sleepUntilNotNull(() -> Rs2Widget.findWidget("Switch world", children, true), 2_000);
                if (switchWorldWidget != null) {
                    if (Rs2Widget.clickWidget(switchWorldWidget)) {
                        return sleepUntil(Microbot::isHopping, 4_000);
                    }
                }
            }
            log.error("Failed to hop to world {}", worldNumber);
        }

        return false;
    }

    public static void showMessage(String message) {
        try {
            Runnable messageRunnable = () ->
            {
                JOptionPane.showConfirmDialog(null, message, "Message",
                        JOptionPane.DEFAULT_OPTION);
            };
            if (SwingUtilities.isEventDispatchThread()) {
                messageRunnable.run();
            } else {
                SwingUtilities.invokeAndWait(messageRunnable);
            }
        } catch (Exception ex) {
            log.error("Error displaying message {}:", message, ex);
        }
    }

    public static void showMessage(String message, int disposeTime) {
        try {
            Runnable messageRunnable = () ->
            {
                JOptionPane pane = new JOptionPane(message, JOptionPane.INFORMATION_MESSAGE);
                JDialog dialog = pane.createDialog("Message");
                dialog.setModal(false);
                dialog.setVisible(true);
                Timer timer = new Timer(disposeTime, e -> dialog.dispose());
                timer.setRepeats(false);
                timer.start();

            };
            if (SwingUtilities.isEventDispatchThread()) {
                messageRunnable.run();
            } else {
                SwingUtilities.invokeAndWait(messageRunnable);
            }
        } catch (Exception ex) {
            log.error("Error displaying message {}:", message, ex);
        }
    }

    public static List<Rs2ItemModel> updateItemContainer(int id, ItemContainerChanged e) {
        if (e.getContainerId() == id) {
            List<Rs2ItemModel> list = new ArrayList<>();
            int i = -1;
            for (Item item : e.getItemContainer().getItems()) {
                if (item == null) {
                    i++;
                    continue;
                }
                i++; //increment before checking if it is a placeholder. This way the index will match the slots in the bank
                ItemComposition composition = Microbot.getItemManager().getItemComposition(item.getId());
                boolean isPlaceholder = composition.getPlaceholderTemplateId() > 0;
                if (isPlaceholder) {
                    continue;
                }

                list.add(new Rs2ItemModel(item, composition, i));
            }
            return list;
        }
        return null;
    }

    @SneakyThrows
    @SuppressWarnings("SpellCheckingInspection")
    private static boolean togglePlugin(Plugin plugin, boolean enable) {
        if (plugin == null) return false;
        final AtomicBoolean success = new AtomicBoolean(false);
        Runnable runnable = () -> {
            try {
                getPluginManager().setPluginEnabled(plugin, enable);
                if (enable) {
                    success.set(getPluginManager().startPlugin(plugin));
                    getPluginManager().startPlugins();
                } else {
                    success.set(getPluginManager().stopPlugin(plugin));
                }
            } catch (PluginInstantiationException ex) {
                log.error("Error {}abling plugin ({}):", enable ? "en" : "dis", plugin.getClass().getSimpleName(), ex);
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            // Ensure the runnable is executed on the Event Dispatch Thread
            // This is necessary for Swing components and plugin management
            SwingUtilities.invokeLater(runnable);
        }

        return success.get();
    }

    /**
     * Starts the specified plugin by enabling it and starting all plugins in the plugin manager.
     * This method checks if the provided plugin is non-null before proceeding.
     *
     * @param plugin the plugin to be started.
     */
    public static boolean startPlugin(Plugin plugin) {
        return togglePlugin(plugin, true);
    }

    public static boolean startPlugin(Class<? extends Plugin> clazz) {
        return startPlugin(getPlugin(clazz));
    }

    public static boolean startPlugin(String className) {
        return startPlugin(getPlugin(className));
    }

    /**
     * Retrieves a plugin by its class name from the plugin manager.
     * This method searches through the available plugins and returns the one matching the specified class name.
     *
     * @param className the fully qualified class name of the plugin to retrieve.
     *                  For example: {@code BreakHandlerPlugin.getClass().getName()}.
     * @return the plugin instance matching the specified class name, or {@code null} if no such plugin is found.
     */
    public static Plugin getPlugin(String className) {
        return getPluginManager().getPlugins().stream()
                .filter(plugin -> plugin.getClass().getName().equals(className))
                .findFirst().orElse(null);
    }

    public static <T extends Plugin> T getPlugin(Class<T> clazz) {
        return getPluginManager().getPlugins().stream()
                .filter(plugin -> plugin.getClass() == clazz)
                .map(clazz::cast).findFirst().orElse(null);
    }

    /**
     * Stops the specified plugin using the plugin manager.
     * If the plugin is non-null, this method attempts to stop it and handles any instantiation exceptions.
     *
     * @param plugin the plugin to be stopped.
     */
    public static boolean stopPlugin(Plugin plugin) {
        return togglePlugin(plugin, false);
    }

    public static boolean stopPlugin(Class<? extends Plugin> clazz) {
        return stopPlugin(getPlugin(clazz));
    }

    public static boolean stopPlugin(String className) {
        return stopPlugin(getPlugin(className));
    }

    public static void doInvoke(NewMenuEntry entry, Rectangle rectangle) {
        try {
            if (Rs2UiHelper.isRectangleWithinCanvas(rectangle)) {
                click(rectangle, entry);
            } else {
                click(new Rectangle(1, 1), entry);
            }
        } catch (ArrayIndexOutOfBoundsException ex) {
            log.error("Error during doInvoke", ex);
            // Handle the error as needed
        }
    }

    public static void drag(Rectangle start, Rectangle end) {
        if (start == null || end == null) {
            return;
        }
        if (!Rs2UiHelper.isRectangleWithinCanvas(start) || !Rs2UiHelper.isRectangleWithinCanvas(end)) {
            return;
        }
        Point startPoint = Rs2UiHelper.getClickingPoint(start, true);
        Point endPoint = Rs2UiHelper.getClickingPoint(end, true);
        mouse.drag(startPoint, endPoint);
        if (!Microbot.getClient().isClientThread()) {
            sleep(50, 80);
        }
    }

    public static void click(Rectangle rectangle, NewMenuEntry entry) {
        if (entry.getType() == MenuAction.WALK) {
            mouse.click(new Point(entry.getParam0(), entry.getParam1()), entry);
        } else {
            Point point = Rs2UiHelper.getClickingPoint(rectangle, true);
            mouse.click(point, entry);
        }

        if (!Microbot.getClient().isClientThread()) {
            sleep(50, 100);
        }
    }

    public static void click(Rectangle rectangle) {

        Point point = Rs2UiHelper.getClickingPoint(rectangle, true);
        mouse.click(point);


        if (!Microbot.getClient().isClientThread()) {
            sleep(50, 80);
        }
    }

    @Deprecated(since = "Use LootTrackerPlugin.getAggregateLootRecords()", forRemoval = true)
    public static List<LootTrackerRecord> getAggregateLootRecords() {
        return new ArrayList<>();
    }

    @Deprecated(since = "Use LootTrackerPlugin.getAggregateLootRecords()", forRemoval = true)
    public static LootTrackerRecord getAggregateLootRecords(String npcName) {
        return null;
    }

    /**
     * Calculates the total GE value of loot records for a specific NPC.
     * This method uses reflection to access private methods and fields of the LootTrackerItem class.
     *
     * @param npcName name of the npc to get the loot records for
     * @return total GE value of the loot records
     */
    @Deprecated(since = "Use LootTrackerPlugin.getAggregateLootRecords()", forRemoval = true)
    public static long getAggregateLootRecordsTotalGevalue(String npcName) {
        return 0;
    }

    /**
     * Logs the stack trace of an exception to the console and chat.
     *
     * @param scriptName the name of the script where the exception occurred
     * @param ex         the exception
     */
    public static void logStackTrace(String scriptName, Exception ex) {
        log(scriptName, Level.ERROR, ex);
    }

    public static void log(String message) {
        log(message, Level.INFO);
    }

    public static void log(String format, Object... args) {
        log(String.format(format, args), Level.INFO);
    }

    public static void log(Level level, String format, Object... args) {
        log(String.format(format, args), level);
    }

    public static void log(String message, Level level) {
        log(message, level, null);
    }

    public static void log(String message, Level level, Exception ex) {
        if (message == null || message.isEmpty()) {
            return;
        }
        if (level == null) {
            return;
        }

        switch (level) {
            case WARN:
                log.warn(message);
                break;
            case ERROR:
                if (ex != null) {
                    log.error(message, ex);
                } else {
                    log.error(message);
                }
                break;
            case DEBUG:
                log.debug(message);
                break;
            default:
                log.info(message);
                break;
        }
    }

    /**
     * Opens a pop‑up interface with the specified title and description.
     * <p>
     * This method uses the client thread to invoke the creation of a modal interface
     * and displays the provided title and description. It ensures that the interface
     * is properly closed after it is displayed.
     * </p>
     *
     * <h3>Example:</h3>
     * <pre><code>
     * // show a popup titled "Microbot" with a formatted enabled message
     * Microbot.openPopUp(
     *     "Microbot",
     *     String.format(
     *         "S-1D:&lt;br&gt;&lt;br&gt;&lt;col=ffffff&gt;%s Enabled&lt;/col&gt;",
     *         "Antiban"
     *     )
     * );
     * </code></pre>
     *
     * @param title       The title to be displayed in the pop‑up.
     * @param description The description or message to be displayed in the pop‑up.
     */
    public static void openPopUp(String title, String description) {
        getClientThread().invoke(() -> {
            // Open a modal interface with the specified widget ID and modal mode
            WidgetNode widgetNode = getClient().openInterface((161 << 16) | 13, 660, WidgetModalMode.MODAL_CLICKTHROUGH);

            // Run a client script to populate the interface with the title and description
            getClient().runScript(3343, title, description, -1);

            // Schedule a task to check the widget's state and close the interface if necessary
            getClientThread().invokeLater(() -> {
                Widget w = getClient().getWidget(InterfaceID.NOTIFICATION_DISPLAY, 1);
                if (w == null || w.getWidth() > 0) {
                    return false; // Exit if the widget is null or already displayed
                }

                // Close the interface if the widget is valid
                getClient().closeInterface(widgetNode, true);
                return true;
            });
        });
    }

    public static boolean isPluginEnabled(Plugin plugin) {
        return plugin != null && Microbot.getPluginManager().isPluginEnabled(plugin);
    }

    public static boolean isPluginEnabled(Class<? extends Plugin> clazz) {
        return isPluginEnabled(getPlugin(clazz));
    }

    private static boolean isPluginEnabled(String name) {
        return isPluginEnabled(getPlugin(name));
    }

    public static void writeVersionToFile(String version) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(VERSION_FILE_PATH))) {
            writer.write(version);
        }
    }

    public static boolean isFirstRun() {
        File file = new File(VERSION_FILE_PATH);
        // Check if the version file exists
        return !file.exists();
    }

    public static String readVersionFromFile() throws IOException {
        try (Scanner scanner = new Scanner(new File(VERSION_FILE_PATH))) {
            return scanner.hasNextLine() ? scanner.nextLine() : "";
        }
    }

    public static boolean shouldSkipVanillaClientDownload() {
        if (isDebug()) {
            try {
                String currentVersion = RuneLiteProperties.getVersion();
                if (Microbot.isFirstRun()) {
                    Microbot.writeVersionToFile(currentVersion);
                    System.out.println("First run in debug mode. Version written to file.");
                } else {
                    String storedVersion = Microbot.readVersionFromFile();
                    if (currentVersion.equals(storedVersion)) {
                        System.out.println("Running in debug mode. Version matches stored version.");
                        return true;
                    } else {
                        System.out.println("Version mismatch detected...updating client.");
                        Microbot.writeVersionToFile(currentVersion);
                    }
                }
            } catch (Exception ex) {
                log.error("Error while checking should download vanilla client:", ex);
            }
        }
        return false;
    }

    public static Injector getInjector() {
        if (RuneLiteDebug.getInjector() != null) {
            return RuneLiteDebug.getInjector();
        }
        return RuneLite.getInjector();
    }

    /**
     * Retrieves a list of active plugins that are part of the "microbot" package, excluding specific plugins.
     * <p>
     * This method filters the active plugins managed by the `pluginManager` to include only those whose
     * package name contains "microbot" (case-insensitive). It further excludes certain plugins based on
     * their class names, such as "QuestHelperPlugin", "MInventorySetupsPlugin", "MicrobotPlugin",
     * "ShortestPathPlugin", "AntibanPlugin", and "ExamplePlugin".
     *
     * @return a list of active plugins belonging to the "microbot" package, excluding the specified plugins.
     */
    public static List<Plugin> getActiveMicrobotPlugins() {
        return pluginManager.getActivePlugins().stream()
                .filter(x -> x.getClass().getPackage().getName().toLowerCase().contains("microbot"))
                .filter(x -> !x.getClass().getSimpleName().equalsIgnoreCase("QuestHelperPlugin")
                        && !x.getClass().getSimpleName().equalsIgnoreCase("MInventorySetupsPlugin")
                        && !x.getClass().getSimpleName().equalsIgnoreCase("MicrobotPlugin")
                        && !x.getClass().getSimpleName().equalsIgnoreCase("ShortestPathPlugin")
                        && !x.getClass().getSimpleName().equalsIgnoreCase("AntibanPlugin")
                        && !x.getClass().getSimpleName().equalsIgnoreCase("ExamplePlugin"))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a list of active `Script` instances from the currently active microbot plugins.
     * <p>
     * This method iterates through all active microbot plugins and inspects their fields using reflection
     * to find fields of type `Script` or its subclasses. The identified `Script` fields are extracted
     * and returned as a list.
     * <p>
     * Key Details:
     * - The method uses reflection to access the fields of each plugin class.
     * - Only fields that are assignable to the `Script` type are included.
     * - Private fields are made accessible via `field.setAccessible(true)` to retrieve their values.
     * - Any exceptions encountered during field access (e.g., `IllegalAccessException`) are logged, and
     * the corresponding field is skipped.
     * - Null values resulting from inaccessible or uninitialized fields are filtered out.
     *
     * @return a list of active `Script` instances extracted from the microbot plugins.
     */
    public static List<Script> getActiveScripts() {
        return getActiveMicrobotPlugins().stream()
                .flatMap(x -> {
                    // Get all fields of the class
                    Field[] fields = x.getClass().getDeclaredFields();

                    // Filter fields that are assignable to Script
                    return java.util.Arrays.stream(fields)
                            .filter(field -> Script.class.isAssignableFrom(field.getType()))
                            .map(field -> {
                                field.setAccessible(true); // Allow access to private fields
                                try {
                                    return (Script) field.get(x); // Map the field to a Script instance
                                } catch (IllegalAccessException ex) {
                                    log.error("Error getting active scripts", ex);
                                    return null; // Handle exception if field cannot be accessed
                                }
                            });
                })
                .filter(Objects::nonNull) // Exclude nulls
                .collect(Collectors.toList());
    }
}
