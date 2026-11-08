package net.runelite.client.plugins.microbot.ui;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MicrobotPluginUsageTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private final ScheduledExecutorService writer = mock(ScheduledExecutorService.class);

    private Path file() throws Exception
    {
        return folder.getRoot().toPath().resolve("microbot").resolve("plugin-usage.json");
    }

    private MicrobotPluginUsage load() throws Exception
    {
        return new MicrobotPluginUsage(file(), writer);
    }

    // Runs the one write the debounce queued.
    private void flush(int expectedWrites) throws Exception
    {
        ArgumentCaptor<Runnable> write = ArgumentCaptor.forClass(Runnable.class);
        verify(writer, times(expectedWrites)).schedule(write.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        write.getValue().run();
    }

    private static final java.util.function.Predicate<String> WORTH = MicrobotPluginListPanel::worthReporting;

    private static Map<String, String> health(String... pairs)
    {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
        {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    public void missingFileMeansNothingUsedYet() throws Exception
    {
        assertEquals(0, load().lastStarted("a.Plugin"));
    }

    @Test
    public void corruptOrWrongShapedFileFallsBackToEmpty() throws Exception
    {
        Files.createDirectories(file().getParent());
        for (String content : Arrays.asList("{not json", "[]", "{\"lastStarted\":null,\"health\":7}", ""))
        {
            Files.write(file(), content.getBytes(StandardCharsets.UTF_8));
            MicrobotPluginUsage usage = load();
            assertEquals(content, 0, usage.lastStarted("a.Plugin"));
            assertTrue(content, usage.healthChanges(health("A", "BROKEN"), WORTH).isEmpty());
        }
    }

    @Test
    public void startTimesAreWrittenOffThreadAndReadBack() throws Exception
    {
        MicrobotPluginUsage usage = load();
        usage.started("a.Plugin", 100);
        usage.started("b.Plugin", 200);
        usage.started("a.Plugin", 300);
        assertFalse("Nothing written on the calling thread", Files.exists(file()));

        flush(1);

        MicrobotPluginUsage reloaded = load();
        assertEquals(300, reloaded.lastStarted("a.Plugin"));
        assertEquals(200, reloaded.lastStarted("b.Plugin"));
        try (java.util.stream.Stream<Path> files = Files.list(file().getParent()))
        {
            assertEquals("No temp file left behind", Collections.singletonList(file()), files.collect(java.util.stream.Collectors.toList()));
        }
    }

    @Test
    public void startAfterAWriteQueuesAnother() throws Exception
    {
        MicrobotPluginUsage usage = load();
        usage.started("a.Plugin", 100);
        flush(1);

        usage.started("a.Plugin", 200);

        flush(2);
        assertEquals(200, load().lastStarted("a.Plugin"));
    }

    @Test
    public void unwritableFileDoesNotThrow() throws Exception
    {
        Files.write(folder.getRoot().toPath().resolve("microbot"), new byte[0]);
        MicrobotPluginUsage usage = load();

        usage.started("a.Plugin", 100);
        flush(1);

        assertEquals(100, usage.lastStarted("a.Plugin"));
    }

    @Test
    public void firstHealthCheckRecordsWithoutReportingChanges() throws Exception
    {
        MicrobotPluginUsage usage = load();

        assertTrue(usage.healthChanges(health("A", "VERIFIED", "B", "UNKNOWN"), WORTH).isEmpty());
        assertEquals(Collections.singletonList("A"), usage.healthChanges(health("A", "BROKEN", "B", "UNKNOWN"), WORTH));
        assertEquals("Still unseen, so still reported", Collections.singletonList("A"),
            usage.healthChanges(health("A", "BROKEN", "B", "UNKNOWN"), WORTH));
        usage.markHealthSeen(health("A", "BROKEN"));
        assertTrue("Seen once opened or dismissed", usage.healthChanges(health("A", "BROKEN", "B", "UNKNOWN"), WORTH).isEmpty());
    }

    @Test
    public void newlyInstalledOrMissingPluginsAreNotChanges() throws Exception
    {
        MicrobotPluginUsage usage = load();
        usage.healthChanges(health("A", "VERIFIED"), WORTH);

        assertTrue(usage.healthChanges(health("B", "BROKEN"), WORTH).isEmpty());
        assertEquals("A kept while its manifest was missing", Collections.singletonList("A"),
            usage.healthChanges(health("A", "BROKEN", "B", "BROKEN"), WORTH));
    }

    @Test
    public void routineChangesAreRecordedQuietly() throws Exception
    {
        MicrobotPluginUsage usage = load();
        usage.healthChanges(health("A", "VERIFIED"), WORTH);

        assertTrue("Verified to not reported after an update says nothing", usage.healthChanges(health("A", "UNKNOWN"), WORTH).isEmpty());
        assertTrue("and is recorded, not reported later", usage.healthChanges(health("A", "UNKNOWN"), WORTH).isEmpty());
        assertEquals("Into a warning is reported", Collections.singletonList("A"), usage.healthChanges(health("A", "UNVERIFIED"), WORTH));
    }

    @Test
    public void theNewestSightingWinsWhenTwoClientsMerge() throws Exception
    {
        MicrobotPluginUsage stale = load();
        stale.healthChanges(health("A", "VERIFIED"), WORTH);
        flush(1);

        ScheduledExecutorService otherWriter = mock(ScheduledExecutorService.class);
        MicrobotPluginUsage other = new MicrobotPluginUsage(file(), otherWriter);
        Thread.sleep(5);
        other.markHealthSeen(health("A", "BROKEN"));
        ArgumentCaptor<Runnable> otherWrite = ArgumentCaptor.forClass(Runnable.class);
        verify(otherWriter).schedule(otherWrite.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        otherWrite.getValue().run();

        stale.started("x.Plugin", 1);
        flush(2);
        assertTrue("The other client already saw BROKEN; stale memory must not undo it",
            load().healthChanges(health("A", "BROKEN"), WORTH).isEmpty());
    }

    @Test
    public void unchangedHealthDoesNotWriteAgain() throws Exception
    {
        MicrobotPluginUsage usage = load();
        usage.healthChanges(health("A", "VERIFIED"), WORTH);
        flush(1);

        usage.healthChanges(health("A", "VERIFIED"), WORTH);

        verify(writer, times(1)).schedule(org.mockito.ArgumentMatchers.any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void writeKeepsWhatAnotherClientSavedAndTheNewestStart() throws Exception
    {
        MicrobotPluginUsage mine = load();
        ScheduledExecutorService otherWriter = mock(ScheduledExecutorService.class);
        MicrobotPluginUsage other = new MicrobotPluginUsage(file(), otherWriter);
        other.started("b.Plugin", 200);
        other.started("c.Plugin", 50);
        other.healthChanges(health("B", "VERIFIED"), WORTH);
        ArgumentCaptor<Runnable> otherWrite = ArgumentCaptor.forClass(Runnable.class);
        verify(otherWriter).schedule(otherWrite.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        otherWrite.getValue().run();

        mine.started("a.Plugin", 100);
        mine.started("c.Plugin", 300);
        mine.healthChanges(health("A", "VERIFIED"), WORTH);
        flush(1);

        MicrobotPluginUsage reloaded = load();
        assertEquals(100, reloaded.lastStarted("a.Plugin"));
        assertEquals("Another client's start is kept", 200, reloaded.lastStarted("b.Plugin"));
        assertEquals("The newest start wins", 300, reloaded.lastStarted("c.Plugin"));
        assertEquals("Both clients' health is seen", Collections.singletonList("B"),
            reloaded.healthChanges(health("A", "VERIFIED", "B", "BROKEN"), WORTH));
    }

    @Test
    public void aLeftoverTempPathDoesNotBlockTheWrite() throws Exception
    {
        Files.createDirectories(file().resolveSibling(file().getFileName() + ".tmp"));
        MicrobotPluginUsage usage = load();

        usage.started("a.Plugin", 100);
        flush(1);

        assertEquals(100, load().lastStarted("a.Plugin"));
    }

    @Test
    public void installTimeIsTheFirstJarTimeSoAnUpdateDoesNotBumpIt() throws Exception
    {
        MicrobotPluginUsage usage = load();

        assertEquals(100, usage.installedSince("a.Plugin", 100));
        assertEquals("A new jar from an update keeps the first time", 100, usage.installedSince("a.Plugin", 900));
        assertEquals("Built in, no jar: not recorded", 0, usage.installedSince("b.Plugin", 0));
        assertEquals(500, usage.installedSince("b.Plugin", 500));

        flush(1);
        assertEquals("Survives a restart", 100, load().installedSince("a.Plugin", 900));
    }

    @Test
    public void flushWritesAPendingStartAtOnce() throws Exception
    {
        MicrobotPluginUsage usage = load();
        usage.started("a.Plugin", 100);

        usage.flush();

        assertEquals(100, load().lastStarted("a.Plugin"));
    }

    @Test
    public void healthSeenSurvivesARestart() throws Exception
    {
        load().healthChanges(health("A", "VERIFIED"), WORTH);
        flush(1);

        assertEquals(Collections.singletonList("A"), load().healthChanges(health("A", "BROKEN"), WORTH));
    }
}
