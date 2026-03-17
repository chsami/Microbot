package net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2;

import lombok.Getter;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2Plugin;
import net.runelite.client.plugins.microbot.example.ExamplePlugin;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;

@Getter
public enum MicrobotPluginChoice {
    NONE("None", null),
    ANTIBAN("Antiban", AntibanPlugin.class),
    SHORTEST_PATH("Shortest Path", ShortestPathPlugin.class),
    BREAK_HANDLER("BreakHandler (v1)", BreakHandlerPlugin.class),
    BREAK_HANDLER_V2("BreakHandler V2", BreakHandlerV2Plugin.class),
    EXAMPLE("Example Plugin", ExamplePlugin.class);

    private final String displayName;
    private final Class<? extends Plugin> pluginClass;

    MicrobotPluginChoice(String displayName, Class<? extends Plugin> pluginClass) {
        this.displayName = displayName;
        this.pluginClass = pluginClass;
    }

    /**
     * Returns the fully qualified class name for this choice, or {@code null} for NONE.
     */
    public String getClassName() {
        return pluginClass != null ? pluginClass.getName() : null;
    }

    /**
     * Attempts to resolve a stored configuration value (enum name, display name, or class name)
     * back to a {@link MicrobotPluginChoice}.
     *
     * @param value persisted config value
     * @return optional matching choice
     */
    public static java.util.Optional<MicrobotPluginChoice> fromConfigValue(String value) {
        if (value == null) {
            return java.util.Optional.empty();
        }

        for (MicrobotPluginChoice choice : values()) {
            if (choice.name().equalsIgnoreCase(value)
                || choice.displayName.equalsIgnoreCase(value)
                || (choice.pluginClass != null && choice.pluginClass.getName().equalsIgnoreCase(value))
                || (choice.pluginClass != null && choice.pluginClass.getSimpleName().equalsIgnoreCase(value))) {
                return java.util.Optional.of(choice);
            }
        }

        return java.util.Optional.empty();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
