package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginClient;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginHealth;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import net.runelite.client.ui.components.IconTextField;
import org.junit.Before;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The Hub panel parts the Installed list relies on: opening the Hub on a search, and the shared pre-install check. */
public class MicrobotPluginHubPanelSearchTest
{
    private final Map<String, MicrobotPluginManifest> manifests = new HashMap<>();
    private MicrobotPluginManager manager;
    private ScheduledExecutorService executor;
    private MicrobotPluginHubPanel hub;

    @Before
    public void setUp()
    {
        manager = mock(MicrobotPluginManager.class);
        executor = mock(ScheduledExecutorService.class);
        when(manager.getPluginDirectory()).thenReturn(new File("microbot-plugins"));
        when(manager.getManifestMap()).thenReturn(manifests);
        when(manager.getPluginHealth(any(), any())).thenAnswer(call ->
            MicrobotPluginHealth.evaluate(call.getArgument(0), call.getArgument(1), true));
        // Loads run inline, so a test sees the finished list after one EDT flush.
        when(executor.submit(any(Runnable.class))).thenAnswer(call ->
        {
            call.<Runnable>getArgument(0).run();
            return null;
        });
    }

    private void open() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> hub = new MicrobotPluginHubPanel(mock(MicrobotTopLevelConfigPanel.class), manager,
            mock(PluginManager.class), mock(MicrobotPluginClient.class), executor));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    private Object field(Object owner, String name) throws Exception
    {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass())
        {
            try
            {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            }
            catch (NoSuchFieldException e)
            {
                // Keep walking up.
            }
        }
        throw new NoSuchFieldException(name);
    }

    private String searchText() throws Exception
    {
        return ((IconTextField) field(hub, "searchBar")).getText();
    }

    private static MicrobotPluginManifest manifest(String internalName, String version)
    {
        MicrobotPluginManifest manifest = new MicrobotPluginManifest();
        manifest.setInternalName(internalName);
        manifest.setDisplayName(internalName);
        manifest.setVersion(version);
        return manifest;
    }

    private static MicrobotPluginHealth health(MicrobotPluginManifest manifest, String status)
    {
        MicrobotPluginHealth.Metadata metadata = new MicrobotPluginHealth.Metadata();
        metadata.setStatus(status);
        metadata.setAffectedVersions(Collections.singletonList("*"));
        manifest.setHealth(metadata);
        return MicrobotPluginHealth.evaluate(manifest, manifest.getVersion(), true);
    }

    @Test
    public void searchSurvivesTheActivationThatFollowsAndOnlyThatOne() throws Exception
    {
        open();

        SwingUtilities.invokeAndWait(() ->
        {
            hub.search("Bird Hunter");
            hub.onActivate();
        });
        assertEquals("Bird Hunter", searchText());

        SwingUtilities.invokeAndWait(hub::onActivate);
        assertEquals("", searchText());
    }

    @Test
    public void preInstallCheckStopsBrokenOrDisabledVersionsAndIncompatibleClients()
    {
        MicrobotPluginManifest manifest = manifest("A", "1.0.0");
        assertEquals(MicrobotPluginHubPanel.InstallBlock.NONE,
            MicrobotPluginHubPanel.installBlock(health(manifest, MicrobotPluginHealth.STATUS_OK), true));
        assertEquals(MicrobotPluginHubPanel.InstallBlock.NONE,
            MicrobotPluginHubPanel.installBlock(health(manifest, MicrobotPluginHealth.STATUS_UNVERIFIED), true));
        assertEquals(MicrobotPluginHubPanel.InstallBlock.HEALTH,
            MicrobotPluginHubPanel.installBlock(health(manifest, MicrobotPluginHealth.STATUS_BROKEN), true));
        assertEquals(MicrobotPluginHubPanel.InstallBlock.CLIENT_VERSION,
            MicrobotPluginHubPanel.installBlock(health(manifest, MicrobotPluginHealth.STATUS_OK), false));
        manifest.setDisable(true);
        assertEquals(MicrobotPluginHubPanel.InstallBlock.HEALTH,
            MicrobotPluginHubPanel.installBlock(MicrobotPluginHealth.evaluate(manifest, "1.0.0", true), true));
    }
}
