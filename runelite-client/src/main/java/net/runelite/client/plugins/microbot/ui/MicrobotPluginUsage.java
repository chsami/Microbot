package net.runelite.client.plugins.microbot.ui;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.Plugin;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Last start time per plugin class. Kept in a local file,
 * not the profile, so a start does not churn synced config.
 */
@Slf4j
@Singleton
public class MicrobotPluginUsage
{
    private static final Gson GSON = new Gson();
    private static final long WRITE_DELAY_MS = 2_000;
    private static volatile Consumer<Plugin> startRecorder = plugin -> { };

    private static final class Data
    {
        Map<String, Long> lastStarted = new HashMap<>();
    }

    private final Path file;
    private final ScheduledExecutorService writer;
    private final Data data;
    // Guards the file, so a shutdown flush and a queued write never interleave.
    private final Object fileLock = new Object();
    private boolean writeQueued;

    @Inject
    MicrobotPluginUsage(ScheduledExecutorService writer)
    {
        this(new File(RuneLite.RUNELITE_DIR, "microbot/plugin-usage.json").toPath(), writer);
    }

    MicrobotPluginUsage(Path file, ScheduledExecutorService writer)
    {
        this.file = file;
        this.writer = writer;
        this.data = read(file);
    }

    /** Where PluginManager reports a start that the switch, a script or the agent server asked for. */
    public static void setStartRecorder(Consumer<Plugin> recorder)
    {
        startRecorder = recorder;
    }

    public static void recordStart(Plugin plugin)
    {
        try
        {
            startRecorder.accept(plugin);
        }
        catch (RuntimeException e)
        {
            // The plugin is already running: losing its sort time must not fail the start.
            log.warn("Could not record the start of {}", plugin.getClass().getSimpleName(), e);
        }
    }

    private static Data read(Path file)
    {
        Data loaded = null;
        try
        {
            if (Files.isRegularFile(file))
            {
                loaded = GSON.fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), Data.class);
            }
        }
        catch (Exception e)
        {
            log.debug("Ignoring unreadable plugin usage file {}", file, e);
        }
        Data data = loaded == null ? new Data() : loaded;
        if (data.lastStarted == null)
        {
            data.lastStarted = new HashMap<>();
        }
        return data;
    }

    public void started(String className)
    {
        started(className, System.currentTimeMillis());
    }

    synchronized void started(String className, long time)
    {
        data.lastStarted.put(className, time);
        queueWrite();
    }

    /** 0 when the plugin has not started since this file began. */
    synchronized long lastStarted(String className)
    {
        return data.lastStarted.getOrDefault(className, 0L);
    }

    /** Writes a start still waiting for its delayed write, so one made just before exit is kept. */
    void flush()
    {
        boolean queued;
        synchronized (this)
        {
            queued = writeQueued;
        }
        if (queued)
        {
            write();
        }
    }

    private void queueWrite()
    {
        if (!writeQueued)
        {
            try
            {
                writer.schedule(this::write, WRITE_DELAY_MS, TimeUnit.MILLISECONDS);
                writeQueued = true;
            }
            catch (RejectedExecutionException e)
            {
                // Shutting down: a plugin start must not fail because its time can't be saved.
                log.debug("Plugin usage not saved: writer stopped");
            }
        }
    }

    private void write()
    {
        synchronized (fileLock)
        {
            // Another client may have written since this one loaded: keep its starts.
            Data saved = read(file);
            String json;
            synchronized (this)
            {
                writeQueued = false;
                saved.lastStarted.forEach((name, time) -> data.lastStarted.merge(name, time, Math::max));
                json = GSON.toJson(data);
            }
            Path temp = null;
            try
            {
                Files.createDirectories(file.getParent());
                // Unique per write, so two clients never share one; the move leaves a whole file, never half of one.
                temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
                Files.write(temp, json.getBytes(StandardCharsets.UTF_8));
                try
                {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
                catch (AtomicMoveNotSupportedException e)
                {
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            catch (Exception e)
            {
                log.debug("Could not save plugin usage to {}", file, e);
                try
                {
                    if (temp != null)
                    {
                        Files.deleteIfExists(temp);
                    }
                }
                catch (Exception ignored)
                {
                    // Best effort: a leftover temp file is harmless.
                }
            }
        }
    }
}
