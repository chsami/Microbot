package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginClient;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginHealth;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import net.runelite.client.ui.components.IconTextField;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MicrobotPluginHubPanelSearchTest
{
    @PluginDescriptor(name = "Hub Fixture", isExternal = true, version = "1.0.0")
    public static class HubFixture extends Plugin
    {
    }

    private final Map<String, String> config = new HashMap<>();
    private final Map<String, MicrobotPluginManifest> manifests = new HashMap<>();
    private MicrobotPluginManager manager;
    private PluginManager pluginManager;
    private ConfigManager configManager;
    private ScheduledExecutorService executor;
    private MicrobotPluginHubPanel hub;

    @Before
    public void setUp()
    {
        manager = mock(MicrobotPluginManager.class);
        pluginManager = mock(PluginManager.class);
        configManager = mock(ConfigManager.class);
        executor = mock(ScheduledExecutorService.class);
        when(manager.getPluginDirectory()).thenReturn(new File("microbot-plugins"));
        when(manager.getManifestMap()).thenReturn(manifests);
        when(manager.getPluginHealth(any(), any())).thenAnswer(call ->
            MicrobotPluginHealth.evaluate(call.getArgument(0), call.getArgument(1), true));
        when(configManager.getConfiguration(anyString(), anyString())).thenAnswer(call -> config.get(call.<String>getArgument(1)));
        doAnswer(call -> config.put(call.getArgument(1), call.getArgument(2)))
            .when(configManager).setConfiguration(anyString(), anyString(), anyString());
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
            pluginManager, mock(MicrobotPluginClient.class), executor, configManager));
        flush();
    }

    private static void flush() throws Exception
    {
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

    private List<?> items() throws Exception
    {
        return (List<?>) field(hub, "plugins");
    }

    private Object item(String internalName) throws Exception
    {
        for (Object item : items())
        {
            if (internalName.equals(((MicrobotPluginManifest) field(item, "manifest")).getInternalName()))
            {
                return item;
            }
        }
        throw new AssertionError(internalName);
    }

    private static MicrobotPluginManifest manifest(String internalName, String version)
    {
        MicrobotPluginManifest manifest = new MicrobotPluginManifest();
        manifest.setInternalName(internalName);
        manifest.setDisplayName(internalName);
        manifest.setVersion(version);
        return manifest;
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

    private String searchText() throws Exception
    {
        return ((IconTextField) field(hub, "searchBar")).getText();
    }

    @Test
    public void manifestWithNullAuthorsTagsAndDescriptionStillShows() throws Exception
    {
        manifests.put("Bare", manifest("Bare", "1.0.0"));

        open();

        assertEquals(1, items().size());
    }

    @Test
    public void sortMenuOffersInstalledFirstNewestAndAToZAndSavesTheChoice() throws Exception
    {
        manifests.put("Bravo", manifest("Bravo", "1.0.0"));
        manifests.put("Alpha", manifest("Alpha", "1.0.0"));
        manifests.get("Bravo").setAddedAt("2026-11-01");
        manifests.get("Alpha").setAddedAt("2026-01-01");
        open();
        JPopupMenu menu = (JPopupMenu) field(hub, "sortMenu");
        List<JMenuItem> items = Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast).collect(Collectors.toList());

        assertEquals(Arrays.asList("Installed first", "Newest", "A-Z", "Open plugin folder..."), items.stream().map(JMenuItem::getText).collect(Collectors.toList()));
        assertTrue(items.get(0) instanceof JRadioButtonMenuItem && items.get(0).isSelected());
        assertTrue(items.get(3).getToolTipText().startsWith("Open "));
        assertTrue("The folder button moved into the menu", findButton(hub, "Open Folder") == null);
        assertEquals(Arrays.asList("Alpha", "Bravo"), shownNames());

        SwingUtilities.invokeAndWait(() -> items.get(1).doClick(0));

        assertEquals("NEWEST", config.get("pluginHubSort"));
        assertEquals(Arrays.asList("Bravo", "Alpha"), shownNames());

        open();
        assertTrue(((JMenuItem) ((JPopupMenu) field(hub, "sortMenu")).getComponent(1)).isSelected());
        assertEquals(Arrays.asList("Bravo", "Alpha"), shownNames());
    }

    @Test
    public void sortButtonIsReachableByKeyboardButNotFocusedByAClick() throws Exception
    {
        open();
        javax.swing.AbstractButton sort = findButtonByName(hub, "Sort, or open your plugin folder");

        assertTrue(sort.isFocusable());
        assertTrue(!sort.isRequestFocusEnabled());
    }

    @Test
    public void profileChangeReReadsTheSort() throws Exception
    {
        manifests.put("Bravo", manifest("Bravo", "1.0.0"));
        manifests.put("Alpha", manifest("Alpha", "1.0.0"));
        manifests.get("Bravo").setAddedAt("2026-11-01");
        manifests.get("Alpha").setAddedAt("2026-01-01");
        open();
        assertEquals(Arrays.asList("Alpha", "Bravo"), shownNames());

        config.put("pluginHubSort", "NEWEST");
        SwingUtilities.invokeAndWait(() -> hub.onProfileChanged(new net.runelite.client.events.ProfileChanged()));
        flush();

        assertEquals(Arrays.asList("Bravo", "Alpha"), shownNames());
        assertTrue(((JMenuItem) ((JPopupMenu) field(hub, "sortMenu")).getComponent(1)).isSelected());
    }

    @Test
    public void reopeningTheTabReReadsASortChangedWhileItWasHidden() throws Exception
    {
        manifests.put("Bravo", manifest("Bravo", "1.0.0"));
        manifests.put("Alpha", manifest("Alpha", "1.0.0"));
        manifests.get("Bravo").setAddedAt("2026-11-01");
        manifests.get("Alpha").setAddedAt("2026-01-01");
        open();
        assertEquals(Arrays.asList("Alpha", "Bravo"), shownNames());

        // A profile switch on another tab: the hidden panel gets no event, only the new saved value.
        SwingUtilities.invokeAndWait(hub::onDeactivate);
        config.put("pluginHubSort", "NEWEST");
        // The same panel comes back: a new one would read the sort in its constructor and prove nothing.
        MicrobotPluginHubPanel same = hub;
        SwingUtilities.invokeAndWait(hub::onActivate);
        flush();
        assertSame(same, hub);

        assertEquals(Arrays.asList("Bravo", "Alpha"), shownNames());
        assertTrue(((JMenuItem) ((JPopupMenu) field(hub, "sortMenu")).getComponent(1)).isSelected());
    }

    private static javax.swing.AbstractButton findButtonByName(java.awt.Container root, String name)
    {
        for (java.awt.Component child : root.getComponents())
        {
            if (child instanceof javax.swing.AbstractButton
                && name.equals(((javax.swing.AbstractButton) child).getAccessibleContext().getAccessibleName()))
            {
                return (javax.swing.AbstractButton) child;
            }
            if (child instanceof java.awt.Container)
            {
                javax.swing.AbstractButton found = findButtonByName((java.awt.Container) child, name);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }

    private List<String> shownNames() throws Exception
    {
        javax.swing.JPanel mainPanel = (javax.swing.JPanel) field(hub, "mainPanel");
        List<String> names = new java.util.ArrayList<>();
        for (java.awt.Component row : mainPanel.getComponents())
        {
            names.add(((MicrobotPluginManifest) field(row, "manifest")).getInternalName());
        }
        return names;
    }

    private String stateWords(String internalName) throws Exception
    {
        JLabel label = (JLabel) field(item(internalName), "stateLabel");
        return label.isVisible() ? label.getText() : "";
    }

    @Test
    public void rowsSayTheirStateInWords() throws Exception
    {
        HubFixture plugin = new HubFixture();
        when(pluginManager.getPlugins()).thenReturn(Collections.singletonList(plugin));
        when(manager.getInstalledPlugins()).thenReturn(Collections.singletonList(plugin));
        manifests.put("HubFixture", manifest("HubFixture", "1.0.0"));
        manifests.put("Other", manifest("Other", "1.0.0"));
        open();

        assertEquals("Installed", stateWords("HubFixture"));
        assertEquals("", stateWords("Other"));

        manifests.get("HubFixture").setVersion("2.0.0");
        open();
        assertEquals("Update available", stateWords("HubFixture"));

        MicrobotPluginHealth.Metadata broken = new MicrobotPluginHealth.Metadata();
        broken.setStatus(MicrobotPluginHealth.STATUS_BROKEN);
        broken.setAffectedVersions(Collections.singletonList("*"));
        manifests.get("HubFixture").setVersion("1.0.0");
        manifests.get("HubFixture").setHealth(broken);
        open();
        assertEquals("Confirmed broken", stateWords("HubFixture"));
    }

    private static javax.swing.AbstractButton findButton(java.awt.Container root, String text)
    {
        for (java.awt.Component child : root.getComponents())
        {
            if (child instanceof javax.swing.AbstractButton && text.equals(((javax.swing.AbstractButton) child).getText()))
            {
                return (javax.swing.AbstractButton) child;
            }
            if (child instanceof java.awt.Container)
            {
                javax.swing.AbstractButton found = findButton((java.awt.Container) child, text);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
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