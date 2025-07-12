package net.runelite.client.plugins.microbot.hal.halsutility;

import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.hal.halsutility.config.HalUtilityConfig;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.AbstractHalModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalModuleCategory;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalSubPlugin;
import net.runelite.client.plugins.microbot.hal.halsutility.overlays.HalOverlay;
import net.runelite.client.plugins.microbot.hal.halsutility.panels.HalPanel;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main plugin class for Hal's Utility & Plugins Framework.
 * 
 * This plugin provides a modular framework for creating and managing multiple sub-plugins
 * from a single main plugin. Each module can be started/stopped independently and has
 * its own configuration and overlay.
 * 
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>Modular design with separate config files and overlays</li>
 *   <li>Programmatic module control (enable/disable with config options)</li>
 *   <li>Status checking and module listing</li>
 *   <li>Category-based organization (Skilling, Money, Activity, Bossing, Utility)</li>
 *   <li>Real-time UI updates and visual feedback</li>
 * </ul>
 * 
 * <h3>Usage Examples:</h3>
 * <pre>{@code
 * // Get plugin instance
 * HalsUtilityandPluginsPlugin plugin = Microbot.getPluginManager().getPlugin(HalsUtilityandPluginsPlugin.class);
 * 
 * // Enable modules
 * plugin.enableModule("Example Skilling");
 * plugin.enableModule(HalModule.BLESSED_WINE);
 * 
 * // Enable with config
 * Map<String, String> config = new HashMap<>();
 * config.put("enabled", "true");
 * config.put("skillToTrain", "Mining");
 * plugin.enableModule("Example Skilling", config);
 * 
 * // Check status
 * boolean isRunning = plugin.isModuleEnabled("Example Skilling");
 * List<HalSubPlugin> runningModules = plugin.getRunningModules();
 * }</pre>
 * 
 * @author Hal
 * @since 1.0
 */
@PluginDescriptor(
        name = PluginDescriptor.Hal + "<b><font color=#000000>Utility & Plugins</font></b>",
        description = "Modular plugin launcher with overlay and panel",
        tags = {"hal", "utility", "modular", "overlay", "control"}
)
@Slf4j
public class HalsUtilityandPluginsPlugin extends Plugin {

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject public OverlayManager overlayManager;
    @Inject public HalOverlay overlay;
    @Inject private Injector injector;

    @Inject
    @Named("developerMode")
    private boolean developerMode;

    @Getter private int lastTickInventoryUpdated = -1;
    @Getter private int lastTickBankUpdated = -1;
    private boolean profileChanged;

    private NavigationButton navButton;

    private final Map<HalModule, HalSubPlugin> loadedModules = new EnumMap<>(HalModule.class);

    /**
     * Provides the main plugin configuration.
     * 
     * @param configManager The config manager instance
     * @return The HalUtilityConfig instance
     */
    @Provides
    HalUtilityConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(HalUtilityConfig.class);
    }

    @Override
    protected void startUp() {
        // Load the custom hal_config_icon.png for the navigation button
        final BufferedImage icon = ImageUtil.loadImageResource(HalsUtilityandPluginsPlugin.class, "hal_config_icon.png");
        
        // Load modules first
        scanAndInstantiate();
        
        // Create panel after modules are loaded
        HalPanel panel = new HalPanel(this);

        navButton = NavigationButton.builder()
                .tooltip("Hal's Utilities")
                .icon(icon)
                .priority(7)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
        overlayManager.add(overlay);

        clientThread.invokeAtTickEnd(() -> {
            if (client.getGameState() == GameState.LOGGED_IN) {
                // Optional login hooks
            }
        });

        log.info("Hal's Utility & Plugins started");
    }

    @Override
    protected void shutDown() {
        for (HalSubPlugin helper : loadedModules.values()) {
            helper.stop();
        }
        loadedModules.clear();

        overlayManager.remove(overlay);
        clientToolbar.removeNavigation(navButton);

        log.info("Hal's Utility & Plugins stopped");
    }

    private void scanAndInstantiate() {
        for (HalModule module : HalModule.values()) {
            instantiate(module);
        }
    }

    private void instantiate(HalModule module) {
        HalSubPlugin helper = module.getPlugin();

        Module halModule = (Binder binder) -> {
            binder.bind(HalSubPlugin.class).toInstance(helper);
            binder.install(helper);
        };
        Injector halInjector = Microbot.getInjector().createChildInjector(halModule);
        injector.injectMembers(helper);
        helper.setInjector(halInjector);
        helper.setPlugin(this);

        // Set the config on the module if it's an AbstractHalModule
        Class<? extends Config> configClass = module.getConfigClass();
        if (configClass != null) {
            Config config = configManager.getConfig(configClass);
            module.setRuntimeConfig(config);
            
            if (helper instanceof AbstractHalModule) {
                ((AbstractHalModule<?>) helper).setConfigDirectly(config);
            }
        }

        loadedModules.put(module, helper);
        log.debug("Loaded subplugin module {}", module.name());
    }

    @Subscribe
    public void onGameTick(GameTick event) {}

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        ItemContainer container = event.getItemContainer();
        if (container == client.getItemContainer(InventoryID.BANK)) {
            lastTickBankUpdated = client.getTickCount();
        } else if (container == client.getItemContainer(InventoryID.INV)) {
            lastTickInventoryUpdated = client.getTickCount();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) profileChanged = true;
        if (event.getGameState() == GameState.LOGGED_IN && profileChanged) profileChanged = false;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {}

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        // Handle main plugin config changes
        if (Objects.equals(event.getGroup(), "halsutility")) {
            // Handle main plugin config changes here
            log.debug("Main plugin config changed: {} = {}", event.getKey(), event.getNewValue());
            return;
        }
        
        // Handle module config changes
        for (HalSubPlugin module : loadedModules.values()) {
            if (module instanceof AbstractHalModule) {
                AbstractHalModule<?> halModule = (AbstractHalModule<?>) module;
                if (halModule.getConfigClass() != null) {
                    String moduleConfigGroup = halModule.getConfigClass().getAnnotation(net.runelite.client.config.ConfigGroup.class).value();
                    if (Objects.equals(event.getGroup(), moduleConfigGroup)) {
                        // Notify module of config change
                        halModule.onConfigChanged(event);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Refreshes the main panel to update module status displays.
     * This method is called automatically when modules are enabled/disabled.
     */
    public void refreshPanel() {
        if (navButton != null && navButton.getPanel() instanceof HalPanel) {
            ((HalPanel) navButton.getPanel()).rebuildModuleList();
        }
    }

    @Subscribe
    public void onCommandExecuted(CommandExecuted commandExecuted) {}

    @Subscribe(priority = 100)
    public void onClientShutdown(ClientShutdown event) {}

    /**
     * Displays the main plugin panel.
     * This opens the panel in the RuneLite sidebar.
     */
    public void displayPanel() {
        SwingUtilities.invokeLater(() -> clientToolbar.openPanel(navButton));
    }

    /**
     * Gets all loaded modules.
     * 
     * @return A map of module enums to their corresponding HalSubPlugin instances
     */
    public Map<HalModule, HalSubPlugin> getLoadedModules() {
        return loadedModules;
    }

    /**
     * Gets the config manager instance.
     * 
     * @return The ConfigManager instance
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Gets a module by its display name.
     * 
     * @param displayName The display name of the module to find
     * @return Optional containing the module if found, empty otherwise
     * 
     * @example
     * <pre>{@code
     * Optional<HalSubPlugin> module = plugin.getModuleByName("Example Skilling");
     * if (module.isPresent()) {
     *     HalSubPlugin skillingModule = module.get();
     *     // Use the module
     * }
     * }</pre>
     */
    public Optional<HalSubPlugin> getModuleByName(String displayName) {
        return loadedModules.values().stream()
                .filter(module -> module.getDisplayName().equalsIgnoreCase(displayName))
                .findFirst();
    }

    /**
     * Gets a module by its enum value.
     * 
     * @param moduleEnum The HalModule enum value
     * @return Optional containing the module if found, empty otherwise
     * 
     * @example
     * <pre>{@code
     * Optional<HalSubPlugin> module = plugin.getModuleByEnum(HalModule.EXAMPLE_SKILLING);
     * if (module.isPresent()) {
     *     HalSubPlugin skillingModule = module.get();
     *     // Use the module
     * }
     * }</pre>
     */
    public Optional<HalSubPlugin> getModuleByEnum(HalModule moduleEnum) {
        return Optional.ofNullable(loadedModules.get(moduleEnum));
    }

    /**
     * Enables a module by its display name with optional configuration options.
     * 
     * <p>This method will:
     * <ul>
     *   <li>Find the module by display name</li>
     *   <li>Set any provided configuration options</li>
     *   <li>Start the module if it's not already running</li>
     *   <li>Refresh the UI panel to show updated status</li>
     * </ul>
     * 
     * @param displayName The display name of the module to enable
     * @param configOptions Map of config key-value pairs to set before starting, or null for no config changes
     * 
     * @example
     * <pre>{@code
     * // Enable without config changes
     * plugin.enableModule("Example Skilling");
     * 
     * // Enable with specific config
     * Map<String, String> config = new HashMap<>();
     * config.put("enabled", "true");
     * config.put("skillToTrain", "Mining");
     * config.put("autoDrop", "true");
     * plugin.enableModule("Example Skilling", config);
     * }</pre>
     */
    public void enableModule(String displayName, Map<String, String> configOptions) {
        Optional<HalSubPlugin> moduleOpt = getModuleByName(displayName);
        if (!moduleOpt.isPresent()) {
            log.warn("Module not found: {}", displayName);
            return;
        }

        HalSubPlugin module = moduleOpt.get();
        enableModule(module, configOptions);
    }

    /**
     * Enables a module by its enum value with optional configuration options.
     * 
     * <p>This method will:
     * <ul>
     *   <li>Find the module by enum value</li>
     *   <li>Set any provided configuration options</li>
     *   <li>Start the module if it's not already running</li>
     *   <li>Refresh the UI panel to show updated status</li>
     * </ul>
     * 
     * @param moduleEnum The HalModule enum value
     * @param configOptions Map of config key-value pairs to set before starting, or null for no config changes
     * 
     * @example
     * <pre>{@code
     * // Enable without config changes
     * plugin.enableModule(HalModule.EXAMPLE_SKILLING);
     * 
     * // Enable with specific config
     * Map<String, String> config = new HashMap<>();
     * config.put("enabled", "true");
     * config.put("skillToTrain", "Mining");
     * plugin.enableModule(HalModule.EXAMPLE_SKILLING, config);
     * }</pre>
     */
    public void enableModule(HalModule moduleEnum, Map<String, String> configOptions) {
        Optional<HalSubPlugin> moduleOpt = getModuleByEnum(moduleEnum);
        if (!moduleOpt.isPresent()) {
            log.warn("Module not found: {}", moduleEnum);
            return;
        }

        HalSubPlugin module = moduleOpt.get();
        enableModule(module, configOptions);
    }

    /**
     * Enables a module with optional configuration options.
     * 
     * <p>This method will:
     * <ul>
     *   <li>Set any provided configuration options via ConfigManager</li>
     *   <li>Start the module if it's not already running</li>
     *   <li>Refresh the UI panel to show updated status</li>
     *   <li>Log the operation for debugging</li>
     * </ul>
     * 
     * @param module The module to enable
     * @param configOptions Map of config key-value pairs to set before starting, or null for no config changes
     * 
     * @example
     * <pre>{@code
     * // Get module first
     * Optional<HalSubPlugin> moduleOpt = plugin.getModuleByName("Example Skilling");
     * if (moduleOpt.isPresent()) {
     *     HalSubPlugin module = moduleOpt.get();
     *     
     *     // Enable with config
     *     Map<String, String> config = new HashMap<>();
     *     config.put("enabled", "true");
     *     plugin.enableModule(module, config);
     * }
     * }</pre>
     */
    public void enableModule(HalSubPlugin module, Map<String, String> configOptions) {
        try {
            // Set config options if provided
            if (configOptions != null && !configOptions.isEmpty()) {
                if (module instanceof AbstractHalModule) {
                    AbstractHalModule<?> halModule = (AbstractHalModule<?>) module;
                    if (halModule.getConfigClass() != null) {
                        String configGroup = halModule.getConfigClass().getAnnotation(net.runelite.client.config.ConfigGroup.class).value();
                        
                        for (Map.Entry<String, String> entry : configOptions.entrySet()) {
                            configManager.setConfiguration(configGroup, entry.getKey(), entry.getValue());
                            log.debug("Set config {} = {} for module {}", entry.getKey(), entry.getValue(), module.getDisplayName());
                        }
                    }
                }
            }

            // Start the module
            if (!module.isRunning()) {
                module.start();
                log.info("Enabled module: {}", module.getDisplayName());
                
                // Refresh the panel to show updated status
                refreshPanel();
            } else {
                log.info("Module {} is already running", module.getDisplayName());
            }
        } catch (Exception e) {
            log.error("Failed to enable module: {}", module.getDisplayName(), e);
        }
    }

    /**
     * Enables a module by its display name without any configuration changes.
     * 
     * @param displayName The display name of the module to enable
     * 
     * @example
     * <pre>{@code
     * plugin.enableModule("Example Skilling");
     * plugin.enableModule("Blessed Wine");
     * }</pre>
     */
    public void enableModule(String displayName) {
        enableModule(displayName, null);
    }

    /**
     * Enables a module by its enum value without any configuration changes.
     * 
     * @param moduleEnum The HalModule enum value
     * 
     * @example
     * <pre>{@code
     * plugin.enableModule(HalModule.EXAMPLE_SKILLING);
     * plugin.enableModule(HalModule.BLESSED_WINE);
     * }</pre>
     */
    public void enableModule(HalModule moduleEnum) {
        enableModule(moduleEnum, null);
    }

    /**
     * Disables a module by its display name.
     * 
     * <p>This method will:
     * <ul>
     *   <li>Find the module by display name</li>
     *   <li>Stop the module if it's running</li>
     *   <li>Refresh the UI panel to show updated status</li>
     * </ul>
     * 
     * @param displayName The display name of the module to disable
     * 
     * @example
     * <pre>{@code
     * plugin.disableModule("Example Skilling");
     * plugin.disableModule("Blessed Wine");
     * }</pre>
     */
    public void disableModule(String displayName) {
        Optional<HalSubPlugin> moduleOpt = getModuleByName(displayName);
        if (!moduleOpt.isPresent()) {
            log.warn("Module not found: {}", displayName);
            return;
        }

        HalSubPlugin module = moduleOpt.get();
        disableModule(module);
    }

    /**
     * Disables a module by its enum value.
     * 
     * <p>This method will:
     * <ul>
     *   <li>Find the module by enum value</li>
     *   <li>Stop the module if it's running</li>
     *   <li>Refresh the UI panel to show updated status</li>
     * </ul>
     * 
     * @param moduleEnum The HalModule enum value
     * 
     * @example
     * <pre>{@code
     * plugin.disableModule(HalModule.EXAMPLE_SKILLING);
     * plugin.disableModule(HalModule.BLESSED_WINE);
     * }</pre>
     */
    public void disableModule(HalModule moduleEnum) {
        Optional<HalSubPlugin> moduleOpt = getModuleByEnum(moduleEnum);
        if (!moduleOpt.isPresent()) {
            log.warn("Module not found: {}", moduleEnum);
            return;
        }

        HalSubPlugin module = moduleOpt.get();
        disableModule(module);
    }

    /**
     * Disables a module.
     * 
     * <p>This method will:
     * <ul>
     *   <li>Stop the module if it's running</li>
     *   <li>Refresh the UI panel to show updated status</li>
     *   <li>Log the operation for debugging</li>
     * </ul>
     * 
     * @param module The module to disable
     * 
     * @example
     * <pre>{@code
     * // Get module first
     * Optional<HalSubPlugin> moduleOpt = plugin.getModuleByName("Example Skilling");
     * if (moduleOpt.isPresent()) {
     *     HalSubPlugin module = moduleOpt.get();
     *     plugin.disableModule(module);
     * }
     * }</pre>
     */
    public void disableModule(HalSubPlugin module) {
        try {
            if (module.isRunning()) {
                module.stop();
                log.info("Disabled module: {}", module.getDisplayName());
                
                // Refresh the panel to show updated status
                refreshPanel();
            } else {
                log.info("Module {} is already stopped", module.getDisplayName());
            }
        } catch (Exception e) {
            log.error("Failed to disable module: {}", module.getDisplayName(), e);
        }
    }

    /**
     * Checks if a module is enabled/running by its display name.
     * 
     * @param displayName The display name of the module to check
     * @return true if the module is running, false otherwise
     * 
     * @example
     * <pre>{@code
     * boolean isRunning = plugin.isModuleEnabled("Example Skilling");
     * if (isRunning) {
     *     log.info("Example Skilling is currently running");
     * }
     * }</pre>
     */
    public boolean isModuleEnabled(String displayName) {
        Optional<HalSubPlugin> moduleOpt = getModuleByName(displayName);
        return moduleOpt.map(HalSubPlugin::isRunning).orElse(false);
    }

    /**
     * Checks if a module is enabled/running by its enum value.
     * 
     * @param moduleEnum The HalModule enum value
     * @return true if the module is running, false otherwise
     * 
     * @example
     * <pre>{@code
     * boolean isRunning = plugin.isModuleEnabled(HalModule.EXAMPLE_SKILLING);
     * if (isRunning) {
     *     log.info("Example Skilling is currently running");
     * }
     * }</pre>
     */
    public boolean isModuleEnabled(HalModule moduleEnum) {
        Optional<HalSubPlugin> moduleOpt = getModuleByEnum(moduleEnum);
        return moduleOpt.map(HalSubPlugin::isRunning).orElse(false);
    }

    /**
     * Checks if a module is enabled/running.
     * 
     * @param module The module to check
     * @return true if the module is running, false otherwise
     * 
     * @example
     * <pre>{@code
     * Optional<HalSubPlugin> moduleOpt = plugin.getModuleByName("Example Skilling");
     * if (moduleOpt.isPresent()) {
     *     boolean isRunning = plugin.isModuleEnabled(moduleOpt.get());
     *     log.info("Module is running: {}", isRunning);
     * }
     * }</pre>
     */
    public boolean isModuleEnabled(HalSubPlugin module) {
        return module != null && module.isRunning();
    }

    /**
     * Gets all currently running modules.
     * 
     * @return List of running modules
     * 
     * @example
     * <pre>{@code
     * List<HalSubPlugin> runningModules = plugin.getRunningModules();
     * log.info("Currently running {} modules", runningModules.size());
     * 
     * for (HalSubPlugin module : runningModules) {
     *     log.info("Running: {}", module.getDisplayName());
     * }
     * }</pre>
     */
    public List<HalSubPlugin> getRunningModules() {
        return loadedModules.values().stream()
                .filter(HalSubPlugin::isRunning)
                .collect(Collectors.toList());
    }

    /**
     * Gets all currently stopped modules.
     * 
     * @return List of stopped modules
     * 
     * @example
     * <pre>{@code
     * List<HalSubPlugin> stoppedModules = plugin.getStoppedModules();
     * log.info("Currently stopped {} modules", stoppedModules.size());
     * 
     * for (HalSubPlugin module : stoppedModules) {
     *     log.info("Stopped: {}", module.getDisplayName());
     * }
     * }</pre>
     */
    public List<HalSubPlugin> getStoppedModules() {
        return loadedModules.values().stream()
                .filter(module -> !module.isRunning())
                .collect(Collectors.toList());
    }

    /**
     * Gets running modules by category.
     * 
     * @param category The category to filter by
     * @return List of running modules in the specified category
     * 
     * @example
     * <pre>{@code
     * List<HalSubPlugin> runningSkilling = plugin.getRunningModulesByCategory(HalModuleCategory.SKILLING);
     * List<HalSubPlugin> runningBossing = plugin.getRunningModulesByCategory(HalModuleCategory.BOSSING);
     * 
     * log.info("Running skilling modules: {}", runningSkilling.size());
     * log.info("Running bossing modules: {}", runningBossing.size());
     * }</pre>
     */
    public List<HalSubPlugin> getRunningModulesByCategory(HalModuleCategory category) {
        return loadedModules.values().stream()
                .filter(module -> module.isRunning() && module.getCategory() == category)
                .collect(Collectors.toList());
    }
}
