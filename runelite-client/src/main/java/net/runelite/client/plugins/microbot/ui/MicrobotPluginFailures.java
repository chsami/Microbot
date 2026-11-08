package net.runelite.client.plugins.microbot.ui;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Why a plugin last failed to start this session. {@link net.runelite.client.plugins.PluginManager#startPlugin}
 * records here, so scripts, the agent server and live reloads count, not only this panel.
 */
@Slf4j
public final class MicrobotPluginFailures
{
    // Weak keys alone do not free a failed plugin: its Throwable holds the plugin's classes, which can reach the
    // plugin itself. So PluginManager.remove() drops the entry too (removed), and weak keys catch anything left.
    private static final Map<Plugin, Throwable> FAILURES = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Runnable listener = () -> { };

    private MicrobotPluginFailures()
    {
    }

    public static void failed(Plugin plugin, Throwable failure)
    {
        FAILURES.put(plugin, failure);
        notifyListener();
    }

    public static void started(Plugin plugin)
    {
        clear(plugin);
    }

    /** The plugin left the registry (a live reload, update or removal), so its old failure must not keep it loaded. */
    public static void removed(Plugin plugin)
    {
        clear(plugin);
    }

    static void clear(Plugin plugin)
    {
        if (FAILURES.remove(plugin) != null)
        {
            notifyListener();
        }
    }

    // Called inside PluginManager's start: a panel problem must never turn into a failed start.
    private static void notifyListener()
    {
        try
        {
            listener.run();
        }
        catch (RuntimeException e)
        {
            log.warn("Plugin list could not show a start failure change", e);
        }
    }

    @Nullable
    public static Throwable failure(Plugin plugin)
    {
        return FAILURES.get(plugin);
    }

    static String describe(Throwable failure)
    {
        String type = failure.getClass().getSimpleName();
        return failure.getMessage() == null ? type : type + ": " + failure.getMessage();
    }

    static void setListener(Runnable listener)
    {
        MicrobotPluginFailures.listener = listener;
    }
}
