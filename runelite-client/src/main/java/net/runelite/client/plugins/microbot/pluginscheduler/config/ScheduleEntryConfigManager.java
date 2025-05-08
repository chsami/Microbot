package net.runelite.client.plugins.microbot.pluginscheduler.config;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigSectionDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.config.ui.ScheduleEntryConfigManagerPanel;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.JPanel;


/**
 * Utility class for managing plugin configuration interactions from PluginScheduleEntry
 */
@Slf4j
public class ScheduleEntryConfigManager {    
    private ConfigDescriptor initialConfigPluginDescriptor; // Initial configuration from plugin (now final)
    private ConfigDescriptor configScheduleEntryDescriptor;
    private Supplier<ConfigDescriptor> configPluginDescriptorProvider;
    private JPanel configPanel;


    public void setConfigPluginDescriptor(ConfigDescriptor configPluginDescriptor) {
        if (configScheduleEntryDescriptor == null) {
            log.info("Setting configScheduleEntryDescriptor to configPluginDescriptor");
            this.configScheduleEntryDescriptor = configPluginDescriptor;
        }
        // Cannot modify initialConfigPluginDescriptor as it's final now
    }
    
    /**
     * Sets a provider function that returns the current ConfigDescriptor for a plugin
     * This is more flexible than directly storing the descriptor as it can get updated values
     * 
     * @param provider A supplier function that returns the current ConfigDescriptor
     */
    public void setConfigPluginDescriptorProvider(Supplier<ConfigDescriptor> provider) {
        this.configPluginDescriptorProvider = provider;
    }
    
    /**
     * Sets a provider function that returns the current ConfigDescriptor for a SchedulablePlugin
     * 
     * @param plugin The SchedulablePlugin that provides the ConfigDescriptor
     */
    public void setConfigPluginDescriptorProvider(SchedulablePlugin plugin) {
        if (plugin != null) {
            this.configPluginDescriptorProvider = plugin::getConfigDescriptor;
            this.initialConfigPluginDescriptor = plugin.getConfigDescriptor();
            // Initialize with the current value
            ConfigDescriptor descriptor = plugin.getConfigDescriptor();
            if (descriptor != null) {
                // Only update schedule entry descriptor, not the initial descriptor
                this.configScheduleEntryDescriptor = descriptor;
            }
        }
    }
    
    /**
     * Gets the current ConfigDescriptor from the provider if available,
     * otherwise returns the stored descriptor
     * 
     * @return The current ConfigDescriptor from the provider or stored value
     */
    public ConfigDescriptor getCurrentPluginConfigDescriptor() {
        if (configPluginDescriptorProvider != null) {
            ConfigDescriptor current = configPluginDescriptorProvider.get();
            if (current != null) {
                return current;
            }
        }
        return initialConfigPluginDescriptor;
    }
    
    //private final JPanel configPanel;
    /**
     * Constructs a new configuration manager for a specific plugin
     * 
     * @param configPluginDescriptor The config descriptor from the plugin
    */
    public ScheduleEntryConfigManager() {
        this.initialConfigPluginDescriptor = null;
    }
    /**
     * Constructs a new configuration manager for a specific plugin
     * 
     * @param configPluginDescriptor The config descriptor from the plugin
     */
    public ScheduleEntryConfigManager(ConfigDescriptor configPluginDescriptor) {
        this.initialConfigPluginDescriptor = configPluginDescriptor;
        //this.configGroup = configPluginDescriptor != null ? configPluginDescriptor.getGroup().value() : null;
        // Initialize with the plugin's config descriptor
        this.configScheduleEntryDescriptor = configPluginDescriptor;
    }
    
    /**
     * Constructs a new configuration manager with a provider function
     * 
     * @param provider A supplier function that returns the current ConfigDescriptor
     */
    public ScheduleEntryConfigManager(Supplier<ConfigDescriptor> provider) {
        this.configPluginDescriptorProvider = provider;
        ConfigDescriptor initialDescriptor = provider.get();
        this.initialConfigPluginDescriptor = initialDescriptor;
        if (initialDescriptor != null) {
            this.configScheduleEntryDescriptor = initialDescriptor;
        }
    }
    
    /**
     * Constructs a new configuration manager for a specific SchedulablePlugin
     * 
     * @param plugin The SchedulablePlugin that provides the ConfigDescriptor
     */
    public ScheduleEntryConfigManager(SchedulablePlugin plugin) {
        if (plugin != null) {
            this.configPluginDescriptorProvider = plugin::getConfigDescriptor;
            ConfigDescriptor initialDescriptor = plugin.getConfigDescriptor();
            this.initialConfigPluginDescriptor = initialDescriptor;
            if (initialDescriptor != null) {
                this.configScheduleEntryDescriptor = initialDescriptor;
            }
        } else {
            this.initialConfigPluginDescriptor = null;
        }
    }
    
    /**
     * Finds a config item descriptor by its name
     * 
     * @param name The display name of the config item
     * @return Optional containing the config item descriptor if found
     */
    public Optional<ConfigItemDescriptor> findConfigItemByName(String name) {
        if (configScheduleEntryDescriptor == null) {
            return Optional.empty();
        }
        
        Collection<ConfigItemDescriptor> items = configScheduleEntryDescriptor.getItems();
        return items.stream()
                .filter(item -> item.name().equals(name))
                .findFirst();
    }
    
    /**
     * Finds a config item descriptor by its key name
     * 
     * @param keyName The key name of the config item
     * @return Optional containing the config item descriptor if found
     */
    public Optional<ConfigItemDescriptor> findConfigItemByKeyName(String keyName) {
        if (configScheduleEntryDescriptor == null) {
            return Optional.empty();
        }
        
        Collection<ConfigItemDescriptor> items = configScheduleEntryDescriptor.getItems();
        return items.stream()
                .filter(item -> item.key().equals(keyName))
                .findFirst();
    }
    
    /**
     * Gets the configuration value for a specific key
     * 
     * @param keyName The key name of the config item
     * @param type The type of the config value
     * @return The configuration value, or null if not found
     */
    public <T> T getConfiguration(String keyName, Class<T> type) {
        if (getConfigGroup() == null) {
            return null;
        }
        return Microbot.getConfigManager().getConfiguration(getConfigGroup(), keyName, type);
    }
    
    /**
     * Sets the configuration value for a specific key
     * 
     * @param keyName The key name of the config item
     * @param value The value to set
     */
    public <T> void setConfiguration(String keyName, T value) {
        if (getConfigGroup() == null) {
            return;
        }
        
        Microbot.getConfigManager().setConfiguration(getConfigGroup(), keyName, value);
    }
    
    /**
     * Gets the boolean value of a config item
     * 
     * @param keyName The key name of the config item
     * @return The boolean value, or false if the item doesn't exist or is not a boolean
     */
    public boolean getBooleanValue(String keyName) {
        Boolean value = getConfiguration(keyName, Boolean.class);
        return value != null && value;
    }
    
    /**
     * Gets the string value of a config item
     * 
     * @param keyName The key name of the config item
     * @return The string value, or null if not found
     */
    public String getStringValue(String keyName) {
        return getConfiguration(keyName, String.class);
    }
    
    /**
     * Gets the integer value of a config item
     * 
     * @param keyName The key name of the config item
     * @return The integer value, or null if not found
     */
    public Integer getIntegerValue(String keyName) {
        return getConfiguration(keyName, Integer.class);
    }
    
    /**
     * Sets the schedule mode of the plugin.
     * This is a utility function to indicate whether a plugin is currently being managed
     * by the scheduler.
     * 
     * @param isActive Whether the plugin is actively being scheduled
     */
    public void setScheduleMode(boolean isActive) {
        setConfiguration("scheduleMode", isActive);
    }
    
    /**
     * Gets the current schedule mode of the plugin
     * 
     * @return Whether the plugin is in schedule mode
     */
    public boolean isInScheduleMode() {
        return getBooleanValue("scheduleMode");
    }
    
    /**
     * Gets the configuration group name
     * 
     * @return The configuration group name
     */
    public String getConfigGroup() {
    
        
        if (initialConfigPluginDescriptor == null) {
            return configScheduleEntryDescriptor != null ? configScheduleEntryDescriptor.getGroup().value() : null;
        }
        return initialConfigPluginDescriptor != null ? initialConfigPluginDescriptor.getGroup().value() : null;
    }
    
    
    /**
     * Gets the current schedule entry's configuration descriptor
     * 
     * @return The current schedule entry's configuration descriptor
     */
    public ConfigDescriptor getConfigScheduleEntryDescriptor() {
        return configScheduleEntryDescriptor;
    }
    
    /**
     * Sets the current schedule entry's configuration descriptor
     * 
     * @param configScheduleEntryDescriptor The configuration descriptor to set
     */
    public void setConfigScheduleEntryDescriptor(ConfigDescriptor configScheduleEntryDescriptor) {
        this.configScheduleEntryDescriptor = configScheduleEntryDescriptor;
    }
    
    /**
     * Gets all configuration items from the current schedule entry's config descriptor
     * 
     * @return Collection of config item descriptors
     */
    public Collection<ConfigItemDescriptor> getAllConfigItems() {
        if (configScheduleEntryDescriptor == null) {
            return java.util.Collections.emptyList();
        }
        
        return configScheduleEntryDescriptor.getItems();
    }

    /**
     * Applies the saved schedule entry configuration to the current plugin
     * This should be called when starting a scheduled plugin to use its custom config
     */
    public void applyScheduleEntryConfig() {
        if (configScheduleEntryDescriptor == null || initialConfigPluginDescriptor == null) {
            return;
        }
        
        // Apply configuration values from the schedule entry config
        for (ConfigItemDescriptor item : configScheduleEntryDescriptor.getItems()) {
            // Find corresponding item in plugin config
            initialConfigPluginDescriptor.getItems().stream()
                .filter(pluginItem -> pluginItem.key().equals(item.key()))
                .findFirst()
                .ifPresent(pluginItem -> {
                    // Copy configuration value from schedule entry to plugin
                    try {
                        Object value = Microbot.getConfigManager().getConfiguration(
                            configScheduleEntryDescriptor.getGroup().value(), 
                            item.key(), 
                            item.getType()
                        );
                        
                        if (value != null) {
                            Microbot.getConfigManager().setConfiguration(
                                initialConfigPluginDescriptor.getGroup().value(), 
                                pluginItem.key(), 
                                value
                            );
                        }
                    } catch (Exception e) {
                        log.error("Failed to apply config item " + item.key(), e);
                    }
                });
        }
    }

   

    /**
     * Applies the original plugin configuration to reset the plugin to default settings
     * This should be called when stopping a scheduled plugin to reset its configuration
     * Renamed from applyPluginConfig to applyInitialPluginConfig
     */
    public void applyInitialPluginConfig() {
        // Use the initial config descriptor directly instead of getting from provider
        ConfigDescriptor descriptorToApply = initialConfigPluginDescriptor;
        
        if (descriptorToApply == null) {
            log.warn("No initial config descriptor available to apply plugin config");
            return;
        }
        
        // Apply configuration values from the original plugin config descriptor
        for (ConfigItemDescriptor item : descriptorToApply.getItems()) {
            try {
                Object value = Microbot.getConfigManager().getConfiguration(
                    descriptorToApply.getGroup().value(), 
                    item.key(), 
                    item.getType()
                );
                
                if (value != null) {
                    Microbot.getConfigManager().setConfiguration(
                        descriptorToApply.getGroup().value(), 
                        item.key(), 
                        value
                    );
                }
            } catch (Exception e) {
                log.error("Failed to apply original config item " + item.key(), e);
            }
        }
        
        log.debug("Applied initial plugin configuration");
    }
    
    /**
     * Gets or creates a configuration panel for the current schedule entry config
     * 
     * @return JPanel containing configuration controls, or null if no configuration is available
     */
    public JPanel getConfigPanel() {
        if (configScheduleEntryDescriptor == null) {
            return null;
        }
        
        // Return cached panel if it exists
        if (configPanel != null) {
            return configPanel;
        }
        
        try {
            // Create a new panel using the ScheduleEntryConfigManagerPanel class
            configPanel = new ScheduleEntryConfigManagerPanel(Microbot.getConfigManager(), configScheduleEntryDescriptor);
            return configPanel;
        } catch (Exception e) {
            log.error("Error creating config panel", e);
            return null;
        }
    }
    
    /**
     * Refreshes the configuration panel if it exists.
     * Call this when the configScheduleEntryDescriptor has been updated.
     */
    public void refreshConfigPanel() {
        if (configPanel != null) {
            configPanel = new ScheduleEntryConfigManagerPanel(Microbot.getConfigManager(), configScheduleEntryDescriptor);
        }
    }
    
    /**
     * Returns whether there is configuration available for this plugin
     * 
     * @return true if configuration is available, false otherwise
     */
    public boolean hasConfiguration() {
        return configScheduleEntryDescriptor != null && 
               !configScheduleEntryDescriptor.getItems().isEmpty();
    }
    
    /**
     * Logs the current ConfigDescriptor for debugging purposes
     */
    public void logConfigDescriptor() {
        log.debug("Plugin ConfigDescriptor: \n{}", getPluginConfigDescriptorString());
        log.debug("Schedule Entry ConfigDescriptor: \n{}", getScheduleEntryConfigDescriptorString());
    }
    
    /**
     * Returns a string representation of the plugin ConfigDescriptor
     * 
     * @return A readable string representation of the plugin ConfigDescriptor
     */
    public String getPluginConfigDescriptorString() {
        return configDescriptorToString(initialConfigPluginDescriptor);
    }
    
    /**
     * Returns a string representation of the schedule entry ConfigDescriptor
     * 
     * @return A readable string representation of the schedule entry ConfigDescriptor
     */
    public String getScheduleEntryConfigDescriptorString() {
        return configDescriptorToString(configScheduleEntryDescriptor);
    }
    
    /**
     * Converts a ConfigDescriptor to a readable string representation
     * 
     * @param descriptor The ConfigDescriptor to convert
     * @return A string representation of the ConfigDescriptor
     */
    private String configDescriptorToString(ConfigDescriptor descriptor) {
        if (descriptor == null) {
            return "null";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("ConfigDescriptor{");
        
        // Add group info
        sb.append("group=").append(configGroupToString(descriptor.getGroup()));
        
        // Add sections
        sb.append(", sections=[");
        if (descriptor.getSections() != null) {
            sb.append(descriptor.getSections().stream()
                .map(this::configSectionDescriptorToString)
                .collect(Collectors.joining(", ")));
        }
        sb.append("]");
        
        // Add items
        sb.append(", items=[");
        if (descriptor.getItems() != null) {
            sb.append(descriptor.getItems().stream()
                .map(this::configItemDescriptorToString)
                .collect(Collectors.joining(", ")));
        }
        sb.append("]");
        
        // Add information if present
        if (descriptor.getInformation() != null) {
            sb.append(", information='").append(descriptor.getInformation().value()).append("'");
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Converts a ConfigGroup to a readable string representation
     * 
     * @param group The ConfigGroup to convert
     * @return A string representation of the ConfigGroup
     */
    private String configGroupToString(ConfigGroup group) {
        if (group == null) {
            return "null";
        }
        
        return "'" + group.value() + "'";
    }
    
    /**
     * Converts a ConfigSectionDescriptor to a readable string representation
     * 
     * @param section The ConfigSectionDescriptor to convert
     * @return A string representation of the ConfigSectionDescriptor
     */
    private String configSectionDescriptorToString(ConfigSectionDescriptor section) {
        if (section == null) {
            return "null";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Section{");
        sb.append("key='").append(section.key()).append("'");
        sb.append(", name='").append(section.name()).append("'");
        sb.append(", position=").append(section.position());
        
        if (section.getSection() != null) {
            sb.append(", description='").append(section.getSection().description()).append("'");
            sb.append(", closedByDefault=").append(section.getSection().closedByDefault());
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Converts a ConfigItemDescriptor to a readable string representation
     * 
     * @param item The ConfigItemDescriptor to convert
     * @return A string representation of the ConfigItemDescriptor
     */
    private String configItemDescriptorToString(ConfigItemDescriptor item) {
        if (item == null) {
            return "null";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Item{");
        sb.append("key='").append(item.key()).append("'");
        
        if (item.getItem() != null) {
            sb.append(", name='").append(item.getItem().name()).append("'");
            sb.append(", description='").append(item.getItem().description()).append("'");
            sb.append(", position=").append(item.getItem().position());
            
            if (!item.getItem().section().isEmpty()) {
                sb.append(", section='").append(item.getItem().section()).append("'");
            }
            
            if (item.getItem().hidden()) {
                sb.append(", hidden=true");
            }
            
            if (item.getItem().secret()) {
                sb.append(", secret=true");
            }
            
            if (!item.getItem().warning().isEmpty()) {
                sb.append(", warning='").append(item.getItem().warning()).append("'");
            }
        }
        
        if (item.getType() != null) {
            sb.append(", type=").append(item.getType().getTypeName());
        }
        
        if (item.getRange() != null) {
            sb.append(", range=[min=").append(item.getRange().min());
            sb.append(", max=").append(item.getRange().max());
            sb.append("]");
        }
        
        if (item.getAlpha() != null) {
            sb.append(", hasAlpha=true");
        }
        
        if (item.getUnits() != null) {
            sb.append(", units='").append(item.getUnits().value()).append("'");
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ScheduleEntryConfigManager{");
        
        sb.append("configGroup='").append(getConfigGroup()).append("'");
        
        if (configPluginDescriptorProvider != null) {
            sb.append(", hasConfigProvider=true");
        } else {
            sb.append(", hasConfigProvider=false");
        }
        
        if (initialConfigPluginDescriptor != null) {
            sb.append(", pluginConfig=").append(getPluginConfigDescriptorString());
        } else {
            sb.append(", pluginConfig=null");
        }
        
        if (configScheduleEntryDescriptor != null) {
            sb.append(", scheduleEntryConfig=").append(getScheduleEntryConfigDescriptorString());
        } else {
            sb.append(", scheduleEntryConfig=null");
        }
        
        sb.append("}");
        return sb.toString();
    }
}