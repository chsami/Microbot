package net.runelite.client.plugins.microbot.pluginscheduler.config;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.annotations.Component;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.plugins.microbot.Microbot;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


/**
 * Utility class for managing plugin configuration interactions from PluginScheduleEntry
 */
@Slf4j
public class ScheduleEntryConfigManager {    
    private final ConfigDescriptor configDescriptor;
    private final String configGroup;
    //private final JPanel configPanel;
    
    /**
     * Constructs a new configuration manager for a specific plugin
     * 
     * @param configDescriptor The config descriptor from the plugin
     */
    public ScheduleEntryConfigManager(ConfigDescriptor configDescriptor) {
        this.configDescriptor = configDescriptor;
        this.configGroup = configDescriptor != null ? configDescriptor.getGroup().value() : null;
        
    }
    
    /**
     * Finds a config item descriptor by its name
     * 
     * @param name The display name of the config item
     * @return Optional containing the config item descriptor if found
     */
    public Optional<ConfigItemDescriptor> findConfigItemByName(String name) {
        if (configDescriptor == null) {
            return Optional.empty();
        }
        
        Collection<ConfigItemDescriptor> items = configDescriptor.getItems();
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
        if (configDescriptor == null) {
            return Optional.empty();
        }
        
        Collection<ConfigItemDescriptor> items = configDescriptor.getItems();
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
        if (configGroup == null) {
            return null;
        }
        
        return Microbot.getConfigManager().getConfiguration(configGroup, keyName, type);
    }
    
    /**
     * Sets the configuration value for a specific key
     * 
     * @param keyName The key name of the config item
     * @param value The value to set
     */
    public <T> void setConfiguration(String keyName, T value) {
        if (configGroup == null) {
            return;
        }
        
        Microbot.getConfigManager().setConfiguration(configGroup, keyName, value);
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
        return configGroup;
    }
    
    /**
     * Gets the configuration descriptor
     * 
     * @return The configuration descriptor
     */
    public ConfigDescriptor getConfigDescriptor() {
        return configDescriptor;
    }
    
    /**
     * Gets all configuration items
     * 
     * @return Collection of config item descriptors
     */
    public Collection<ConfigItemDescriptor> getAllConfigItems() {
        if (configDescriptor == null) {
            return java.util.Collections.emptyList();
        }
        
        return configDescriptor.getItems();
    }



}