package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.externalplugins.LivePluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import net.runelite.client.plugins.microbot.ui.search.MicrobotPluginSearch;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginHealth;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.inject.Provider;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MicrobotPluginListPanelTest
{
    private static final String NEW_KEY = "pinnedPluginClasses";
    private static final String OLD_KEY = "pinnedPlugins";
    private static final String HTML_NAME = "<html>[<font color=green>D</font>] Html Fixture";

    @PluginDescriptor(name = HTML_NAME, description = "Runs laps")
    public static class HtmlPlugin extends Plugin
    {
    }

    @PluginDescriptor(name = "Comma, Fixture")
    public static class CommaPlugin extends Plugin
    {
    }

    @PluginDescriptor(name = "Late Fixture")
    public static class LatePlugin extends Plugin
    {
    }

    @PluginDescriptor(name = "Always Fixture", alwaysOn = true)
    public static class AlwaysPlugin extends Plugin
    {
    }

    @PluginDescriptor(name = "Startup Off Fixture", disableOnStartUp = true)
    public static class StartupOffPlugin extends Plugin
    {
    }

    @PluginDescriptor(name = "Disabled Fixture", disable = true)
    public static class DisabledPlugin extends Plugin
    {
    }

    @PluginDescriptor(name = "Bracket Fixture", description = "[BETA] Fights giants")
    public static class BracketPlugin extends Plugin
    {
    }

    @PluginDescriptor(name = "External Always Fixture", alwaysOn = true, isExternal = true)
    public static class ExternalAlwaysPlugin extends Plugin
    {
    }

    @PluginDescriptor(name = "Versioned Fixture", version = "1.4", isExternal = true)
    public static class VersionedPlugin extends Plugin
    {
    }

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private MicrobotPluginUsage usage;
    private boolean confirmInstall = true;
    private final List<MicrobotPluginManifest> installChecks = new ArrayList<>();
    private final Map<String, String> config = new HashMap<>();
    private final MicrobotBugReporter bugReporter = mock(MicrobotBugReporter.class);
    private final List<String> failureDialogs = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private boolean chooseReport;
    private ConfigManager configManager;
    private PluginManager pluginManager;
    private MicrobotPluginManager microbotPluginManager;
    private ExternalPluginManager externalPluginManager;
    private LocalPluginsPanel localPluginsPanel;
    private MicrobotPluginListPanel panel;
    private Component focusOwner;
    private boolean safeMode;
    private final List<MicrobotPluginConfigurationDescriptor> openedSettings = new ArrayList<>();
    private final List<Integer> focusMoves = new ArrayList<>();

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception
    {
        configManager = mock(ConfigManager.class);
        pluginManager = mock(PluginManager.class);
        microbotPluginManager = mock(MicrobotPluginManager.class);
        externalPluginManager = mock(ExternalPluginManager.class);
        when(configManager.getConfiguration(anyString(), anyString()))
            .thenAnswer(call -> config.get(call.<String>getArgument(1)));
        doAnswer(call -> config.put(call.getArgument(1), call.getArgument(2)))
            .when(configManager).setConfiguration(anyString(), anyString(), anyString());
        localPluginsPanel = mock(LocalPluginsPanel.class);
        Provider<LocalPluginsPanel> localPanels = mock(Provider.class);
        when(localPanels.get()).thenReturn(localPluginsPanel);
        when(microbotPluginManager.getPluginHealth(any(), any())).thenAnswer(call ->
            MicrobotPluginHealth.evaluate(call.getArgument(0), call.getArgument(1), true));
        usage = new MicrobotPluginUsage(folder.getRoot().toPath().resolve("plugin-usage.json"), mock(ScheduledExecutorService.class));
        onEdt(() -> panel = new MicrobotPluginListPanel(configManager, pluginManager, microbotPluginManager,
            externalPluginManager, mock(EventBus.class), mock(Provider.class), localPanels,
            bugReporter, usage)
        {
            @Override
            boolean askToReport(String name, String reason, boolean alwaysOn)
            {
                failureDialogs.add(reason);
                return chooseReport;
            }

            @Override
            void showError(String title, String message)
            {
                errors.add(message);
            }

            @Override
            boolean confirmInstall(MicrobotPluginManifest manifest, String version)
            {
                installChecks.add(manifest);
                return confirmInstall;
            }

            @Override
            Component focusOwner()
            {
                return focusOwner;
            }

            @Override
            boolean isSafeMode()
            {
                return safeMode;
            }

            @Override
            void moveFocus(int delta)
            {
                focusMoves.add(delta);
                super.moveFocus(delta);
            }

            @Override
            void openConfigurationPanel(MicrobotPluginConfigurationDescriptor plugin)
            {
                openedSettings.add(plugin);
            }
        });
    }

    private static void onEdt(Runnable task) throws Exception
    {
        SwingUtilities.invokeAndWait(task);
    }

    private void rebuild(Plugin... plugins) throws Exception
    {
        when(pluginManager.getPlugins()).thenReturn(Arrays.asList(plugins));
        onEdt(panel::rebuildPluginList);
        onEdt(() -> { });
    }

    private static Object field(Object owner, String name) throws Exception
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
                // A test subclass or a Swing parent: keep walking up.
            }
        }
        throw new NoSuchFieldException(name);
    }

    @SuppressWarnings("unchecked")
    private List<MicrobotPluginListItem> rows() throws Exception
    {
        return (List<MicrobotPluginListItem>) field(panel, "pluginList");
    }

    private MicrobotPluginListItem row(String name) throws Exception
    {
        return rows().stream().filter(item -> item.getPluginConfig().getName().equals(name)).findFirst().get();
    }

    private void addMicrobotRow()
    {
        panel.addFakePlugin(new MicrobotPluginConfigurationDescriptor("Microbot", "Settings", new String[0], null, null));
    }

    private List<String> savedPins(String key)
    {
        return Arrays.asList(config.getOrDefault(key, "").split(","));
    }

    @Test
    public void oldPinsMigrateOnFirstLaunchAndFakeRowStaysPinned() throws Exception
    {
        config.put(OLD_KEY, HTML_NAME + ",Microbot");
        addMicrobotRow();

        rebuild(new HtmlPlugin());

        assertTrue(row(HTML_NAME).isPinned());
        assertTrue(row("Microbot").isPinned());
        assertEquals(Collections.singletonList(HtmlPlugin.class.getName()), savedPins(NEW_KEY));
        assertEquals(Collections.singletonList("Microbot"), savedPins(OLD_KEY));
    }

    @Test
    public void oldPinOfAPluginThatLoadsLaterIsStillMigrated() throws Exception
    {
        config.put(OLD_KEY, "Late Fixture");

        rebuild(new HtmlPlugin());
        assertEquals(Collections.singletonList("Late Fixture"), savedPins(OLD_KEY));

        rebuild(new HtmlPlugin(), new LatePlugin());
        assertTrue(row("Late Fixture").isPinned());
        assertEquals(Collections.singletonList(LatePlugin.class.getName()), savedPins(NEW_KEY));
    }

    @Test
    public void unpinningAMigratedPluginSticksAfterRebuild() throws Exception
    {
        config.put(OLD_KEY, HTML_NAME);
        rebuild(new HtmlPlugin());

        row(HTML_NAME).setPinned(false);
        panel.savePinnedPlugins();
        rebuild(new HtmlPlugin());

        assertFalse(row(HTML_NAME).isPinned());
    }

    @Test
    public void savingKeepsPinsOfPluginsNotLoaded() throws Exception
    {
        config.put(NEW_KEY, "gone.GonePlugin");
        config.put(OLD_KEY, "Gone Old Name");
        rebuild(new HtmlPlugin());

        row(HTML_NAME).setPinned(true);
        panel.savePinnedPlugins();

        assertTrue(savedPins(NEW_KEY).containsAll(Arrays.asList("gone.GonePlugin", HtmlPlugin.class.getName())));
        assertEquals(Collections.singletonList("Gone Old Name"), savedPins(OLD_KEY));
    }

    @Test
    public void commasInNamesDoNotCorruptPins() throws Exception
    {
        config.put(OLD_KEY, "Comma, Fixture");
        rebuild(new CommaPlugin());
        assertTrue(row("Comma, Fixture").isPinned());
        assertEquals(Collections.singletonList(CommaPlugin.class.getName()), savedPins(NEW_KEY));

        panel.addFakePlugin(new MicrobotPluginConfigurationDescriptor("Fake, Row", "Settings", new String[0], null, null));
        rebuild(new CommaPlugin());
        row("Fake, Row").setPinned(true);
        panel.savePinnedPlugins();
        rebuild(new CommaPlugin());

        assertTrue(row("Fake, Row").isPinned());
        assertTrue(row("Comma, Fixture").isPinned());
    }

    @Test
    public void rowConstructorHasNoStartOrStopSideEffects() throws Exception
    {
        for (Plugin plugin : Arrays.asList(new AlwaysPlugin(), new StartupOffPlugin(), new DisabledPlugin()))
        {
            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            onEdt(() -> new MicrobotPluginListItem(panel, new MicrobotPluginConfigurationDescriptor(descriptor.name(),
                "", new String[0], plugin, null, null, null, null)));
        }

        verify(pluginManager, never()).startPlugin(any());
        verify(pluginManager, never()).stopPlugin(any());
        verify(pluginManager, never()).setPluginEnabled(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    public void alwaysOnPluginIsStartedByEveryRebuild() throws Exception
    {
        AlwaysPlugin plugin = new AlwaysPlugin();

        rebuild(plugin);
        verify(pluginManager, times(1)).startPlugin(plugin);

        // A profile change rebuilds the list and must start the plugin again.
        rebuild(plugin);
        verify(pluginManager, times(2)).startPlugin(plugin);
    }

    @Test
    public void disableOnStartUpAppliesOncePerClassPerSession() throws Exception
    {
        StartupOffPlugin plugin = new StartupOffPlugin();

        rebuild(plugin);
        rebuild(plugin);
        rebuild(new StartupOffPlugin());

        verify(pluginManager, times(1)).stopPlugin(any());
    }

    @Test
    public void disabledPluginIsStoppedByEveryRebuild() throws Exception
    {
        DisabledPlugin plugin = new DisabledPlugin();

        rebuild(plugin);
        rebuild(plugin);

        verify(pluginManager, times(2)).stopPlugin(plugin);
    }

    @Test
    public void switchShowsWhetherThePluginIsActive() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        when(pluginManager.isPluginEnabled(plugin)).thenReturn(true);
        when(pluginManager.isPluginActive(plugin)).thenReturn(false);
        rebuild(plugin);
        assertFalse(((JToggleButton) field(row(HTML_NAME), "onOffToggle")).isSelected());

        when(pluginManager.isPluginEnabled(plugin)).thenReturn(false);
        when(pluginManager.isPluginActive(plugin)).thenReturn(true);
        onEdt(panel::refresh);
        assertTrue(((JToggleButton) field(row(HTML_NAME), "onOffToggle")).isSelected());
    }

    @Test
    public void burstOfPluginChangedEventsRefreshesOnce() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        rebuild(plugin);
        clearInvocations(pluginManager);

        onEdt(() ->
        {
            panel.onPluginChanged(new PluginChanged(plugin, true));
            panel.onPluginChanged(new PluginChanged(plugin, false));
            panel.onPluginChanged(new PluginChanged(plugin, true));
        });
        onEdt(() -> { });
        verify(pluginManager, times(1)).isPluginActive(plugin);

        onEdt(() -> panel.onPluginChanged(new PluginChanged(plugin, true)));
        onEdt(() -> { });
        verify(pluginManager, times(2)).isPluginActive(plugin);
    }

    @Test
    public void refreshLeavesFocusAloneAndActivationTakesIt() throws Exception
    {
        AtomicInteger focusRequests = new AtomicInteger();
        IconTextField countingSearchBar = new IconTextField()
        {
            @Override
            public boolean requestFocusInWindow()
            {
                focusRequests.incrementAndGet();
                return false;
            }
        };
        new JPanel().add(countingSearchBar);
        Field searchBar = MicrobotPluginListPanel.class.getDeclaredField("searchBar");
        searchBar.setAccessible(true);
        searchBar.set(panel, countingSearchBar);
        rebuild();

        onEdt(panel::refresh);
        assertEquals(0, focusRequests.get());

        onEdt(panel::onActivate);
        assertEquals(1, focusRequests.get());
    }

    @Test
    public void hubRowUninstallsThroughMicrobotPluginManager() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        when(microbotPluginManager.getHubInternalName(plugin)).thenReturn("HubPlugin");
        when(microbotPluginManager.removePlugin(any())).thenReturn(
            CompletableFuture.completedFuture(LivePluginManager.OperationResult.success("Removed")));
        rebuild(plugin);

        onEdt(() -> panel.uninstall(rowConfig()));
        onEdt(() -> { });

        verify(microbotPluginManager).removePlugin(argThat(manifest -> "HubPlugin".equals(manifest.getInternalName())));
        verify(externalPluginManager, never()).remove(anyString());
    }

    private MicrobotPluginConfigurationDescriptor rowConfig()
    {
        try
        {
            return row(HTML_NAME).getPluginConfig();
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    public void hubRowCarriesItsInternalNameAndOpenWithFilterFindsIt() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        when(microbotPluginManager.getHubInternalName(plugin)).thenReturn("HubPlugin");
        rebuild(plugin, new LatePlugin());

        assertEquals("HubPlugin", row(HTML_NAME).getPluginConfig().getInternalPluginHubName());
        assertTrue(row(HTML_NAME).getKeywords().contains("HubPlugin"));
        assertTrue(row(HTML_NAME).getKeywords().contains("pluginhub"));

        onEdt(() -> panel.openWithFilter("HubPlugin"));

        Collection<Component> shown = Arrays.asList(((JPanel) field(panel, "mainPanel")).getComponents());
        assertEquals(Collections.singletonList(row(HTML_NAME)), shown.stream().collect(Collectors.toList()));
    }

    @Test
    public void rowDescriptionKeepsBracketWords() throws Exception
    {
        rebuild(new BracketPlugin());

        assertEquals(1, MicrobotPluginSearch.search(rows(), "beta").size());
    }

    @Test
    public void rowDoesNotMatchThroughItsOwnMarkup() throws Exception
    {
        rebuild(new HtmlPlugin());

        assertTrue(MicrobotPluginSearch.search(rows(), "font").isEmpty());
        assertTrue(MicrobotPluginSearch.search(rows(), "color").isEmpty());
        assertEquals(1, MicrobotPluginSearch.search(rows(), "laps").size());
    }

    private JPanel mainPanel() throws Exception
    {
        return (JPanel) field(panel, "mainPanel");
    }

    private List<String> shownNames() throws Exception
    {
        return Arrays.stream(mainPanel().getComponents())
            .filter(MicrobotPluginListItem.class::isInstance)
            .map(c -> ((MicrobotPluginListItem) c).getPluginConfig().getName())
            .collect(Collectors.toList());
    }

    private void click(String buttonField) throws Exception
    {
        AbstractButton button = (AbstractButton) field(panel, buttonField);
        onEdt(() -> button.doClick(0));
    }

    private void search(String text) throws Exception
    {
        IconTextField searchBar = (IconTextField) field(panel, "searchBar");
        onEdt(() -> searchBar.setText(text));
    }

    private String searchText() throws Exception
    {
        return ((IconTextField) field(panel, "searchBar")).getText();
    }

    private String text(String field) throws Exception
    {
        Object component = field(panel, field);
        return component instanceof JLabel ? ((JLabel) component).getText() : ((AbstractButton) component).getText();
    }

    private boolean visible(String field) throws Exception
    {
        return ((Component) field(panel, field)).isVisible();
    }

    private boolean inList(String field) throws Exception
    {
        return Arrays.asList(mainPanel().getComponents()).contains(field(panel, field));
    }

    private boolean press(int key, int modifiers) throws Exception
    {
        // Space acts on release, like a button, so holding it cannot flip a plugin repeatedly.
        KeyStroke stroke = KeyStroke.getKeyStroke(key, modifiers, key == KeyEvent.VK_SPACE);
        Object binding = panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(stroke);
        Action action = binding == null ? null : panel.getActionMap().get(binding);
        if (action == null || !action.isEnabled())
        {
            return false;
        }
        onEdt(() -> action.actionPerformed(null));
        return true;
    }

    private void setActive(Plugin plugin, boolean active)
    {
        when(pluginManager.isPluginActive(plugin)).thenReturn(active);
    }

    private AtomicInteger countSearchFocus() throws Exception
    {
        AtomicInteger focusRequests = new AtomicInteger();
        IconTextField countingSearchBar = new IconTextField()
        {
            @Override
            public boolean requestFocusInWindow()
            {
                focusRequests.incrementAndGet();
                return false;
            }
        };
        Field searchBar = MicrobotPluginListPanel.class.getDeclaredField("searchBar");
        searchBar.setAccessible(true);
        searchBar.set(panel, countingSearchBar);
        return focusRequests;
    }

    @Test
    public void sortMenuListsSortsThenLocalPlugins() throws Exception
    {
        JPopupMenu menu = (JPopupMenu) field(panel, "sortMenu");

        List<JMenuItem> items = Arrays.stream(menu.getComponents())
            .filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast).collect(Collectors.toList());

        assertEquals(Arrays.asList("A-Z", "Recently installed", "Recently used", "By author", "Only on", "Local Plugins..."),
            items.stream().map(JMenuItem::getText).collect(Collectors.toList()));
        for (int i = 0; i < 4; i++)
        {
            assertTrue(items.get(i) instanceof JRadioButtonMenuItem);
        }
        assertTrue(items.get(4) instanceof JCheckBoxMenuItem);
        assertEquals("Add, update, reload or remove plugin JARs while Microbot is running", items.get(5).getToolTipText());

        onEdt(() -> items.get(5).doClick(0));
        verify(localPluginsPanel).open(panel);
    }

    @Test
    public void byAuthorPutsAHeaderAboveEachAuthorAndReusesIt() throws Exception
    {
        rebuild(new HtmlPlugin(), new LatePlugin(), new CommaPlugin());
        JPopupMenu menu = (JPopupMenu) field(panel, "sortMenu");
        onEdt(() -> ((JMenuItem) menu.getComponent(3)).doClick(0));

        assertEquals("AUTHOR", config.get("pluginListSort"));
        List<String> shown = Arrays.stream(mainPanel().getComponents())
            .map(c -> c instanceof JLabel ? "#" + ((JLabel) c).getText()
                : c instanceof MicrobotPluginListItem ? ((MicrobotPluginListItem) c).getPluginConfig().getName() : null)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
        assertEquals(Arrays.asList("#D", HTML_NAME, "#No author tag", "Comma, Fixture", "Late Fixture"), shown);

        Component header = mainPanel().getComponent(0);
        onEdt(panel::refresh);
        assertSame(header, mainPanel().getComponent(0));

        search("late");
        assertEquals(Collections.singletonList("Late Fixture"), shownNames());
        assertFalse(Arrays.stream(mainPanel().getComponents()).anyMatch(c -> c == header));
    }

    @Test
    public void sortChoiceIsSavedAndReadBackOnRebuild() throws Exception
    {
        HtmlPlugin older = new HtmlPlugin();
        LatePlugin newer = new LatePlugin();
        Map<String, Long> times = new HashMap<>();
        times.put(HtmlPlugin.class.getName(), 100L);
        times.put(LatePlugin.class.getName(), 200L);
        when(microbotPluginManager.getInstallTimes()).thenReturn(times);
        rebuild(older, newer);
        assertEquals(Arrays.asList(HTML_NAME, "Late Fixture"), shownNames());

        JPopupMenu menu = (JPopupMenu) field(panel, "sortMenu");
        JMenuItem recent = (JMenuItem) menu.getComponent(1);
        onEdt(() -> recent.doClick(0));

        assertEquals("RECENTLY_INSTALLED", config.get("pluginListSort"));
        assertEquals(Arrays.asList("Late Fixture", HTML_NAME), shownNames());

        config.put("pluginListSort", "AZ");
        rebuild(older, newer);
        assertEquals(Arrays.asList(HTML_NAME, "Late Fixture"), shownNames());
        assertTrue(((JMenuItem) menu.getComponent(0)).isSelected());
    }

    @Test
    public void chipsShowLiveCountsAndOnLeavesOutAlwaysOnPlugins() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        AlwaysPlugin always = new AlwaysPlugin();
        setActive(plugin, true);
        setActive(always, true);
        rebuild(plugin, always, new LatePlugin());

        assertEquals("Only on (1)", text("onChip"));
        assertEquals("0 to fix", text("fixChip"));

        setActive(plugin, false);
        onEdt(panel::refresh);
        assertEquals("Only on (0)", text("onChip"));
    }

    @Test
    public void onChipNarrowsTheListAndCountOffersClear() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        setActive(plugin, true);
        rebuild(plugin, new LatePlugin());
        assertFalse(visible("countPanel"));

        click("onChip");

        assertEquals(Collections.singletonList(HTML_NAME), shownNames());
        assertTrue(visible("countPanel"));
        assertEquals("1 of 2 \u00b7 Clear", countLine());

        click("clearButton");
        assertEquals(2, shownNames().size());
        assertFalse(visible("countPanel"));
        assertFalse(((AbstractButton) field(panel, "onChip")).isSelected());
    }

    @Test
    public void clickingTheActiveChipTurnsItOff() throws Exception
    {
        rebuild(new HtmlPlugin());
        AbstractButton on = (AbstractButton) field(panel, "onChip");

        click("onChip");
        assertTrue(on.isSelected());

        click("onChip");
        assertFalse(on.isSelected());
        assertFalse(visible("countPanel"));
    }

    @Test
    public void toFixChipIsHiddenWhileNothingNeedsFixing() throws Exception
    {
        rebuild(new HtmlPlugin());

        assertFalse(visible("fixChip"));
        assertFalse(visible("chipRow"));
        assertEquals(Collections.singletonList(HTML_NAME), shownNames());
        assertFalse(visible("countPanel"));
    }

    @Test
    public void installTimesAreReadOncePerRebuild() throws Exception
    {
        rebuild(new HtmlPlugin(), new LatePlugin(), new AlwaysPlugin());

        verify(microbotPluginManager, times(1)).getInstallTimes();
    }

    @Test
    public void countShowsWhileSearching() throws Exception
    {
        rebuild(new HtmlPlugin(), new LatePlugin());

        search("late");

        assertEquals("1 of 2 \u00b7 Clear", countLine());
        assertTrue(visible("countPanel"));
    }

    @Test
    public void leavingTheTabResetsChipsButOpeningSettingsKeepsThemAndSearch() throws Exception
    {
        rebuild(new HtmlPlugin());
        click("onChip");
        search("html");

        onEdt(panel::onDeactivate);
        assertTrue(((AbstractButton) field(panel, "onChip")).isSelected());
        assertEquals("html", searchText());

        onEdt(panel.getMuxer()::onDeactivate);
        assertFalse(((AbstractButton) field(panel, "onChip")).isSelected());
        assertEquals("html", searchText());
    }

    @Test
    public void openWithFilterDropsAChipThatWouldHideTheTarget() throws Exception
    {
        rebuild(new HtmlPlugin());
        click("onChip");

        onEdt(() -> panel.openWithFilter("html"));

        assertEquals(Collections.singletonList(HTML_NAME), shownNames());
    }

    @Test
    public void rowSwitchedOffUnderOnStaysDimmedUntilTheFilterChanges() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        LatePlugin other = new LatePlugin();
        setActive(plugin, true);
        setActive(other, true);
        rebuild(plugin, other);
        click("onChip");

        setActive(plugin, false);
        onEdt(panel::refresh);

        assertEquals(Arrays.asList(HTML_NAME, "Late Fixture"), shownNames());
        assertTrue((boolean) field(row(HTML_NAME), "dimmed"));
        assertFalse((boolean) field(row("Late Fixture"), "dimmed"));
        assertEquals("Only on (1)", text("onChip"));

        setActive(plugin, true);
        onEdt(panel::refresh);
        assertFalse((boolean) field(row(HTML_NAME), "dimmed"));

        setActive(plugin, false);
        onEdt(panel::refresh);
        click("onChip");
        click("onChip");
        assertEquals(Collections.singletonList("Late Fixture"), shownNames());
        assertFalse((boolean) field(row(HTML_NAME), "dimmed"));
    }

    @Test
    public void refreshReusesRowsWithoutRemovingAny() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        rebuild(plugin, new LatePlugin());
        MicrobotPluginListItem before = row(HTML_NAME);
        AtomicInteger changes = new AtomicInteger();
        mainPanel().addContainerListener(new ContainerAdapter()
        {
            @Override
            public void componentAdded(ContainerEvent e)
            {
                changes.incrementAndGet();
            }

            @Override
            public void componentRemoved(ContainerEvent e)
            {
                changes.incrementAndGet();
            }
        });

        setActive(plugin, true);
        onEdt(panel::refresh);

        assertEquals(0, changes.get());
        assertSame(before, row(HTML_NAME));
    }

    @Test
    public void alwaysOnRowShowsALabelInPlaceOfTheSwitch() throws Exception
    {
        rebuild(new AlwaysPlugin());

        MicrobotPluginListItem row = row("Always Fixture");
        JLabel label = (JLabel) field(row, "sideLabel");
        assertEquals("Always on", label.getText());
        assertEquals("Always on: required by Microbot", label.getToolTipText());
        assertNotNull(label.getParent());
        assertNull(((Component) field(row, "onOffToggle")).getParent());
        assertEquals("Always on", row.getAccessibleContext().getAccessibleDescription());
    }

    @Test
    public void externalAlwaysOnPluginKeepsItsSwitchOutsideBuiltIn() throws Exception
    {
        ExternalAlwaysPlugin plugin = new ExternalAlwaysPlugin();
        setActive(plugin, true);
        rebuild(plugin);

        assertEquals(Collections.singletonList("External Always Fixture"), shownNames());
        assertNotNull(((Component) field(row("External Always Fixture"), "onOffToggle")).getParent());
        assertEquals("Only on (1)", text("onChip"));
        assertFalse(inList("builtInHeader"));
    }

    @Test
    public void builtInSectionIsClosedByDefaultAndItsStateIsSaved() throws Exception
    {
        rebuild(new AlwaysPlugin(), new HtmlPlugin());

        assertTrue(inList("builtInHeader"));
        assertTrue(text("builtInHeader").endsWith("Always on & settings (1)"));
        assertEquals(Collections.singletonList(HTML_NAME), shownNames());

        click("builtInHeader");
        assertEquals("true", config.get("pluginListBuiltInOpen"));
        assertEquals(Arrays.asList(HTML_NAME, "Always Fixture"), shownNames());

        rebuild(new AlwaysPlugin(), new HtmlPlugin());
        assertEquals(Arrays.asList(HTML_NAME, "Always Fixture"), shownNames());
    }

    @Test
    public void searchOpensBuiltInWithoutChangingTheSavedState() throws Exception
    {
        rebuild(new AlwaysPlugin(), new HtmlPlugin());

        search("always");

        assertEquals(Collections.singletonList("Always Fixture"), shownNames());
        assertNull(config.get("pluginListBuiltInOpen"));
    }

    @Test
    public void starredBuiltInMovesToThePinnedGroup() throws Exception
    {
        config.put(NEW_KEY, AlwaysPlugin.class.getName());

        rebuild(new AlwaysPlugin(), new HtmlPlugin());

        assertEquals(Arrays.asList("Always Fixture", HTML_NAME), shownNames());
        assertFalse(inList("builtInHeader"));
    }

    @Test
    public void microbotRowSitsUnderAlwaysOnAndSettingsWithOnlyItsCog() throws Exception
    {
        addMicrobotRow();
        rebuild(new HtmlPlugin(), new LatePlugin());

        assertEquals(Arrays.asList(HTML_NAME, "Late Fixture"), shownNames());
        assertTrue(text("builtInHeader").endsWith("Always on & settings (1)"));
        click("builtInHeader");
        assertEquals(Arrays.asList(HTML_NAME, "Late Fixture", "Microbot"), shownNames());
        assertNull(field(row("Microbot"), "sideLabel"));
        assertFalse(((Component) field(row("Microbot"), "onOffToggle")).isVisible());
        assertEquals("Only on (0)", text("onChip"));
    }

    @Test
    public void noMatchesShowsAnEmptyStateThatClears() throws Exception
    {
        rebuild(new HtmlPlugin());

        search("zzz");

        assertTrue(inList("emptyPanel"));
        assertEquals("No matches", ((JLabel) field(field(panel, "emptyMessage"), "noResultsTitle")).getText());
        // The count line already offers Clear, so the empty state does not repeat it.
        assertFalse(visible("emptyButton"));
        assertTrue(visible("countPanel"));
        assertEquals("0 of 1 \u00b7 Clear", countLine());
        click("clearButton");
        assertEquals("", searchText());
        assertEquals(Collections.singletonList(HTML_NAME), shownNames());
    }

    @Test
    public void lastRowLeavingAFilterShowsTheEmptyStateNotABlankPanel() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        setActive(plugin, true);
        when(microbotPluginManager.getInstalledPlugins()).thenReturn(Collections.singletonList(plugin));
        rebuild(plugin);
        click("onChip");
        assertFalse(inList("emptyPanel"));

        rebuild();

        assertTrue(inList("emptyPanel"));
        assertTrue(visible("countPanel"));
        assertTrue(text("clearButton").contains("Clear"));
    }

    @Test
    public void noPluginsInstalledOffersTheHub() throws Exception
    {
        AtomicBoolean opened = new AtomicBoolean();
        panel.setOpenHub(query -> opened.set(query.isEmpty()));
        rebuild(new AlwaysPlugin());

        assertTrue(inList("emptyPanel"));
        assertTrue(inList("builtInHeader"));
        assertEquals("Open Microbot Hub", text("emptyButton"));
        assertEquals("No plugins installed yet", ((JLabel) field(field(panel, "emptyMessage"), "noResultsTitle")).getText());
        assertTrue(((JLabel) field(field(panel, "emptyMessage"), "noResultsDescription")).getText().contains("Plugins you install from the Microbot Hub"));
        onEdt(() -> ((JButton) unchecked("emptyButton")).doClick(0));
        assertTrue(opened.get());
    }

    @Test
    public void safeModeSaysExternalPluginsAreOffAndOffersNoInstall() throws Exception
    {
        safeMode = true;
        rebuild(new AlwaysPlugin());

        assertTrue(inList("emptyPanel"));
        assertFalse(visible("emptyButton"));
    }

    @Test
    public void installedScriptsHideTheFirstRunNotice() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        when(microbotPluginManager.getInstalledPlugins()).thenReturn(Collections.singletonList(plugin));

        rebuild(plugin);

        assertFalse(inList("emptyPanel"));
    }

    private Object unchecked(String name)
    {
        try
        {
            return field(panel, name);
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    @Test
    public void keysAreBoundOnlyWhileFocusIsInsideThePanel() throws Exception
    {
        assertEquals(0, panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).size());
        assertNotNull(panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .get(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK)));
    }

    @Test
    public void ctrlFFocusesSearch() throws Exception
    {
        AtomicInteger focusRequests = countSearchFocus();
        rebuild(new HtmlPlugin());

        assertTrue(press(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));

        assertEquals(1, focusRequests.get());
    }

    @Test
    public void spaceTogglesTheFocusedRow() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        rebuild(plugin);
        focusOwner = row(HTML_NAME);

        assertTrue(press(KeyEvent.VK_SPACE, 0));

        verify(pluginManager).startPlugin(plugin);
    }

    @Test
    public void spaceAndEnterLeaveTheSearchFieldAlone() throws Exception
    {
        rebuild(new HtmlPlugin());
        focusOwner = (Component) field(panel, "searchBar");

        assertFalse(press(KeyEvent.VK_SPACE, 0));
        assertFalse(press(KeyEvent.VK_ENTER, 0));
    }

    @Test
    public void spaceOnAnAlwaysOnRowDoesNothing() throws Exception
    {
        AlwaysPlugin plugin = new AlwaysPlugin();
        rebuild(plugin);
        clearInvocations(pluginManager);
        focusOwner = row("Always Fixture");

        press(KeyEvent.VK_SPACE, 0);

        verify(pluginManager, never()).startPlugin(any());
        verify(pluginManager, never()).stopPlugin(any());
    }

    @Test
    public void enterOpensSettingsAndNeverStartsThePlugin() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        rebuild(plugin);
        clearInvocations(pluginManager);
        focusOwner = row(HTML_NAME);

        assertTrue(press(KeyEvent.VK_ENTER, 0));

        assertEquals(Collections.singletonList(row(HTML_NAME).getPluginConfig()), openedSettings);
        verify(pluginManager, never()).startPlugin(any());
    }

    @Test
    public void escapeClearsFirstThenReturnsToSearch() throws Exception
    {
        rebuild(new HtmlPlugin());
        click("onChip");
        search("html");
        focusOwner = row(HTML_NAME);

        assertTrue(press(KeyEvent.VK_ESCAPE, 0));
        assertEquals("", searchText());
        assertFalse(visible("countPanel"));

        AtomicInteger focusRequests = countSearchFocus();
        assertTrue(press(KeyEvent.VK_ESCAPE, 0));
        assertEquals(1, focusRequests.get());
    }

    @Test
    public void escapeInAnEmptySearchWithNoClientDoesNotThrow() throws Exception
    {
        rebuild(new HtmlPlugin());
        focusOwner = (Component) field(panel, "searchBar");

        assertTrue(press(KeyEvent.VK_ESCAPE, 0));
    }

    @Test
    public void arrowsDispatchedFromARowReachThePanelPastTheScrollPane() throws Exception
    {
        rebuild(new HtmlPlugin(), new LatePlugin());
        focusOwner = row(HTML_NAME);

        dispatchKey(row(HTML_NAME), KeyEvent.VK_DOWN);
        dispatchKey(row(HTML_NAME), KeyEvent.VK_UP);

        assertEquals(Arrays.asList(1, -1), focusMoves);
    }

    @Test
    public void downDispatchedFromSearchEntersTheList() throws Exception
    {
        rebuild(new HtmlPlugin());
        IconTextField searchBar = (IconTextField) field(panel, "searchBar");
        focusOwner = searchBar;

        dispatchKey(searchBar, KeyEvent.VK_DOWN);
        dispatchKey(searchBar, KeyEvent.VK_UP);

        assertEquals(Collections.singletonList(1), focusMoves);
    }

    // Sends a real key press up the component tree, as Swing does for the focused component.
    private static void dispatchKey(JComponent focused, int key) throws Exception
    {
        class Probe extends JComponent
        {
            void press()
            {
                processKeyEvent(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, key, KeyEvent.CHAR_UNDEFINED));
            }
        }
        Probe probe = new Probe();
        onEdt(() ->
        {
            focused.add(probe);
            probe.press();
            focused.remove(probe);
        });
    }

    @Test
    public void keysDoNothingWhenFocusIsElsewhere() throws Exception
    {
        rebuild(new HtmlPlugin());
        focusOwner = null;

        assertFalse(press(KeyEvent.VK_SPACE, 0));
        assertFalse(press(KeyEvent.VK_DOWN, 0));
    }

    private void fail(Plugin plugin, String reason) throws Exception
    {
        onEdt(() -> MicrobotPluginFailures.failed(plugin, new IllegalStateException(reason)));
        onEdt(() -> { });
    }

    private AbstractButton statusButton(MicrobotPluginListItem row) throws Exception
    {
        for (Class<?> type = row.getClass(); type != null; type = type.getSuperclass())
        {
            try
            {
                Field field = type.getDeclaredField("statusButton");
                field.setAccessible(true);
                return (AbstractButton) field.get(row);
            }
            catch (NoSuchFieldException e)
            {
                // Keep walking up.
            }
        }
        throw new NoSuchFieldException("statusButton");
    }

    private void clickStatus(String name) throws Exception
    {
        AbstractButton button = statusButton(row(name));
        onEdt(() -> button.doClick(0));
        onEdt(() -> { });
    }

    @Test
    public void failedStartSaysSoCountsToFixAndShowsUnderToFix() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        rebuild(plugin, new LatePlugin());
        assertFalse(statusButton(row(HTML_NAME)).isVisible());

        fail(plugin, "Missing config");

        assertEquals("Failed to start", row(HTML_NAME).getStatus().getText());
        assertTrue(statusButton(row(HTML_NAME)).getText().contains("Failed to start"));
        assertTrue(statusButton(row(HTML_NAME)).isVisible());
        assertEquals("1 to fix", text("fixChip"));
        click("fixChip");
        assertEquals(Collections.singletonList(HTML_NAME), shownNames());
    }

    @Test
    public void successfulStartClearsTheFailedLine() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        rebuild(plugin);
        fail(plugin, "Missing config");

        onEdt(() -> MicrobotPluginFailures.started(plugin));
        onEdt(() -> { });

        assertNull(row(HTML_NAME).getStatus());
        assertFalse(statusButton(row(HTML_NAME)).isVisible());
        assertEquals("0 to fix", text("fixChip"));
    }

    @Test
    public void failedBuiltInForcesBuiltInOpenWithoutSavingIt() throws Exception
    {
        AlwaysPlugin always = new AlwaysPlugin();
        rebuild(new HtmlPlugin(), always);
        assertFalse(shownNames().contains("Always Fixture"));

        fail(always, "Broke");

        assertTrue(shownNames().contains("Always Fixture"));
        assertEquals("1 to fix", text("fixChip"));
        assertNull(config.get("pluginListBuiltInOpen"));
    }

    @Test
    public void clickingAFailedLineShowsTheReasonAndCanReportIt() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        rebuild(plugin);
        fail(plugin, "Missing config");

        clickStatus(HTML_NAME);
        assertEquals(Collections.singletonList("IllegalStateException: Missing config"), failureDialogs);
        verify(bugReporter, never()).open(any(), any());
        assertNull("Close clears the line on a normal row too", row(HTML_NAME).getStatus());

        fail(plugin, "Missing config");
        chooseReport = true;
        clickStatus(HTML_NAME);
        verify(bugReporter).open(panel, plugin);
    }

    @Test
    public void typingSortingOrFilteringBeforeTheFirstBuildDoesNothing() throws Exception
    {
        search("zul");
        click("onChip");
        JMenuItem byAuthor = (JMenuItem) ((JPopupMenu) field(panel, "sortMenu")).getComponent(3);
        onEdt(() -> byAuthor.doClick(0));

        rebuild(new HtmlPlugin());
        assertEquals("The search still applies once the list exists", Collections.emptyList(), shownNames());
    }

    @Test
    public void failureDialogSaysWhatHappenedAndWhatToDoBeforeTheError()
    {
        assertEquals("Html Fixture hit an error while starting and is switched off. Switch it on to try again, or press Report"
            + " to send a bug report.\n\nError: IllegalStateException: Missing config",
            MicrobotPluginListPanel.failureMessage("Html Fixture", "IllegalStateException: Missing config", false));
        assertEquals("Always Fixture hit an error while starting. Restart the client to try again, or press Report to send a"
            + " bug report. Close hides this message.\n\nError: IllegalStateException: Missing config",
            MicrobotPluginListPanel.failureMessage("Always Fixture", "IllegalStateException: Missing config", true));
    }

    @Test
    public void hubHeaderSaysTheNumberIsTheBestMatchesOnceCut()
    {
        assertEquals("Microbot Hub: 1 match", MicrobotPluginListPanel.hubHeaderText(1, 5));
        assertEquals("Microbot Hub: 3 matches", MicrobotPluginListPanel.hubHeaderText(3, 5));
        assertEquals("Microbot Hub: top 5 matches", MicrobotPluginListPanel.hubHeaderText(5, 5));
    }

    private MicrobotPluginListItem ghostWithManifest(List<String> hubSearches) throws Exception
    {
        when(microbotPluginManager.getBlockedPlugins()).thenReturn(Collections.singletonMap("BirdHunter", "disabled"));
        MicrobotPluginManifest manifest = new MicrobotPluginManifest();
        manifest.setInternalName("BirdHunter");
        manifest.setDisplayName("<html>[M] Bird Hunter");
        when(microbotPluginManager.getManifestMap()).thenReturn(Collections.singletonMap("BirdHunter", manifest));
        panel.setOpenHub(hubSearches::add);
        rebuild(new HtmlPlugin());
        return row("<html>[M] Bird Hunter");
    }

    @Test
    public void blockedPluginIsAGhostRowThatOpensTheHubFilteredToIt() throws Exception
    {
        List<String> hubSearches = new ArrayList<>();
        MicrobotPluginListItem ghost = ghostWithManifest(hubSearches);

        assertEquals("Not loaded: disabled", ghost.getStatus().getText());
        assertEquals("As serious as a failed start", ColorScheme.PROGRESS_ERROR_COLOR, statusButton(ghost).getForeground());
        assertNull(ghost.getPluginConfig().getPlugin());
        assertNull("No cog", ghost.getPluginConfig().getConfigDescriptor());
        assertFalse("No switch", ((Component) field(ghost, "onOffToggle")).isVisible());
        assertEquals("1 to fix", text("fixChip"));
        assertFalse("A blocked plugin is the user's, not a settings row", ghost.isNoSwitch());
        assertFalse(inList("builtInHeader"));

        clickStatus("<html>[M] Bird Hunter");
        assertEquals(Collections.singletonList("Bird Hunter"), hubSearches);
        verify(localPluginsPanel, never()).open(any());
    }

    @Test
    public void aBlockedPluginAloneIsNotTheFirstRunScreen() throws Exception
    {
        when(microbotPluginManager.getBlockedPlugins()).thenReturn(Collections.singletonMap("MyScript-1.0", "zip END header not found"));
        addMicrobotRow();

        rebuild(new AlwaysPlugin());

        assertFalse(inList("emptyPanel"));
        assertTrue(visible("fixChip"));
        assertEquals("1 to fix", text("fixChip"));
        assertEquals(Collections.singletonList("MyScript-1.0"), shownNames());
    }

    @Test
    public void blockedJarWithoutAManifestOpensLocalPlugins() throws Exception
    {
        when(microbotPluginManager.getBlockedPlugins()).thenReturn(Collections.singletonMap("MyScript-1.0", "zip END header not found"));
        List<String> hubSearches = new ArrayList<>();
        panel.setOpenHub(hubSearches::add);

        rebuild(new HtmlPlugin());
        assertEquals("Not loaded: zip END header not found", row("MyScript-1.0").getStatus().getText());

        clickStatus("MyScript-1.0");
        verify(localPluginsPanel).open(panel);
        assertTrue(hubSearches.isEmpty());
    }

    @Test
    public void enterOnAGhostRowOpensTheHub() throws Exception
    {
        List<String> hubSearches = new ArrayList<>();
        MicrobotPluginListItem ghost = ghostWithManifest(hubSearches);
        focusOwner = ghost;

        dispatchKey(ghost, KeyEvent.VK_ENTER);

        assertEquals(Collections.singletonList("Bird Hunter"), hubSearches);
        assertTrue(openedSettings.isEmpty());
    }

    @Test
    public void enterOnAFailedRowShowsWhyAndNeverStartsIt() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        rebuild(plugin);
        fail(plugin, "Missing config");
        clearInvocations(pluginManager);
        focusOwner = row(HTML_NAME);

        dispatchKey(row(HTML_NAME), KeyEvent.VK_ENTER);

        assertEquals(Collections.singletonList("IllegalStateException: Missing config"), failureDialogs);
        assertTrue(openedSettings.isEmpty());
        verify(pluginManager, never()).startPlugin(any());
    }

    @Test
    public void enterOnARowWithoutStatusStillOpensSettings() throws Exception
    {
        rebuild(new HtmlPlugin());
        focusOwner = row(HTML_NAME);

        dispatchKey(row(HTML_NAME), KeyEvent.VK_ENTER);

        assertEquals(Collections.singletonList(row(HTML_NAME).getPluginConfig()), openedSettings);
    }

    private MicrobotPluginManifest outdated(VersionedPlugin plugin)
    {
        MicrobotPluginManifest manifest = new MicrobotPluginManifest();
        manifest.setInternalName("Versioned");
        MicrobotPluginManager.OutdatedPluginUpdate update = new MicrobotPluginManager.OutdatedPluginUpdate("Versioned", "Versioned", "1.4", "1.5");
        when(microbotPluginManager.getManifestMap()).thenReturn(Collections.singletonMap("Versioned", manifest));
        when(microbotPluginManager.findUpdates(any())).thenReturn(Collections.singletonMap(plugin, update));
        when(microbotPluginManager.findUpdate(plugin)).thenReturn(Optional.of(update));
        return manifest;
    }

    @Test
    public void updateLineNamesVersionsAndUpdatesThroughTheManager() throws Exception
    {
        VersionedPlugin plugin = new VersionedPlugin();
        MicrobotPluginManifest manifest = outdated(plugin);
        when(microbotPluginManager.updatePlugin(any(), any())).thenReturn(
            CompletableFuture.completedFuture(LivePluginManager.OperationResult.success("Updated")));
        rebuild(plugin);

        assertEquals("Update 1.4 to 1.5", row("Versioned Fixture").getStatus().getText());
        assertEquals("The Hub's update orange", new java.awt.Color(0xD4A574), statusButton(row("Versioned Fixture")).getForeground());
        assertEquals("1 to fix", text("fixChip"));

        clickStatus("Versioned Fixture");
        verify(microbotPluginManager).updatePlugin(manifest, "1.5");
    }

    @Test
    public void updateSaysUpdatingWhileItRunsAndRestoresTheLineIfItFails() throws Exception
    {
        VersionedPlugin plugin = new VersionedPlugin();
        outdated(plugin);
        CompletableFuture<LivePluginManager.OperationResult> running = new CompletableFuture<>();
        when(microbotPluginManager.updatePlugin(any(), any())).thenReturn(running);
        rebuild(plugin);

        clickStatus("Versioned Fixture");
        assertEquals("Updating...", row("Versioned Fixture").getStatus().getText());
        onEdt(panel::refresh);
        assertEquals("A refresh mid-update keeps it", "Updating...", row("Versioned Fixture").getStatus().getText());
        clickStatus("Versioned Fixture");
        verify(microbotPluginManager, times(1)).updatePlugin(any(), any());

        running.complete(LivePluginManager.OperationResult.failure("Couldn't reach the Plugin Hub"));
        onEdt(() -> { });
        onEdt(() -> { });

        assertEquals("Update 1.4 to 1.5", row("Versioned Fixture").getStatus().getText());
        assertEquals(Collections.singletonList("Couldn't reach the Plugin Hub"), errors);
    }

    @Test
    public void manifestsNotLoadedYetShowNoUpdateLine() throws Exception
    {
        rebuild(new VersionedPlugin());

        assertNull(row("Versioned Fixture").getStatus());
        assertEquals("0 to fix", text("fixChip"));
    }
    @Test
    public void tooltipShowsTheVersion() throws Exception
    {
        rebuild(new VersionedPlugin());

        String tooltip = ((JLabel) field(row("Versioned Fixture"), "nameLabel")).getToolTipText();
        assertTrue(tooltip, tooltip.contains("Version 1.4"));
    }

    @Test
    public void recentlyUsedFollowsStartsButOnlyReSortsOnActivation() throws Exception
    {
        HtmlPlugin html = new HtmlPlugin();
        LatePlugin late = new LatePlugin();
        usage.started(HtmlPlugin.class.getName(), 100);
        usage.started(LatePlugin.class.getName(), 200);
        rebuild(html, late);
        JPopupMenu menu = (JPopupMenu) field(panel, "sortMenu");
        onEdt(() -> ((JMenuItem) menu.getComponent(2)).doClick(0));
        assertEquals("RECENTLY_USED", config.get("pluginListSort"));
        assertEquals(Arrays.asList("Late Fixture", HTML_NAME), shownNames());

        // Every start counts, through the same hook PluginManager calls for scripts and the switch alike.
        onEdt(() -> MicrobotPluginUsage.recordStart(html));
        onEdt(() -> { });
        onEdt(panel::refresh);
        assertEquals("Rows never jump while visible", Arrays.asList("Late Fixture", HTML_NAME), shownNames());

        onEdt(panel::onActivate);
        assertEquals(Arrays.asList(HTML_NAME, "Late Fixture"), shownNames());
    }

    private MicrobotPluginManifest hubManifest(String internalName, String displayName)
    {
        MicrobotPluginManifest manifest = new MicrobotPluginManifest();
        manifest.setInternalName(internalName);
        manifest.setDisplayName(displayName);
        manifest.setVersion("1.0.0");
        return manifest;
    }

    private Map<String, MicrobotPluginManifest> hubManifests(MicrobotPluginManifest... manifests)
    {
        Map<String, MicrobotPluginManifest> map = new HashMap<>();
        for (MicrobotPluginManifest manifest : manifests)
        {
            map.put(manifest.getInternalName(), manifest);
        }
        when(microbotPluginManager.getManifestMap()).thenReturn(map);
        return map;
    }

    private List<String> shownHubRows() throws Exception
    {
        return Arrays.stream(mainPanel().getComponents())
            .filter(MicrobotPluginListPanel.HubRow.class::isInstance)
            .map(c -> ((MicrobotPluginListPanel.HubRow) c).getManifest().getInternalName())
            .collect(Collectors.toList());
    }

    private MicrobotPluginListPanel.HubRow hubRow(String internalName) throws Exception
    {
        return Arrays.stream(mainPanel().getComponents())
            .filter(MicrobotPluginListPanel.HubRow.class::isInstance)
            .map(MicrobotPluginListPanel.HubRow.class::cast)
            .filter(row -> row.getManifest().getInternalName().equals(internalName))
            .findFirst().get();
    }

    @Test
    public void searchAlsoOffersHubPluginsThatAreNotInstalled() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        when(microbotPluginManager.getHubInternalName(plugin)).thenReturn("ZulrahInstalled");
        hubManifests(hubManifest("ZulrahInstalled", "Zulrah Installed"),
            hubManifest("ZulrahHelper", "<html>[<font color=red>M</font>] Zulrah Helper"),
            hubManifest("Fisher", "Fisher"));
        rebuild(plugin);
        assertFalse(inList("hubHeader"));

        search("zul");

        assertTrue(inList("hubHeader"));
        assertEquals("Microbot Hub: 1 match", text("hubHeader"));
        assertEquals(Collections.singletonList("ZulrahHelper"), shownHubRows());
        assertTrue("Coloured author tag, like installed rows",
            ((JLabel) hubRow("ZulrahHelper").getComponent(0)).getText().contains("<font color=red>M</font>"));
        assertTrue("Installed results come first", Arrays.asList(mainPanel().getComponents()).indexOf(field(panel, "hubHeader"))
            > Arrays.asList(mainPanel().getComponents()).indexOf(row(HTML_NAME)));
        assertEquals(Collections.singletonList(HTML_NAME), shownNames());

        search("");
        assertFalse(inList("hubHeader"));
        assertTrue(shownHubRows().isEmpty());
    }

    @Test
    public void hubOffersNothingInSafeModeOrBeforeManifestsLoad() throws Exception
    {
        rebuild(new HtmlPlugin());
        search("zul");
        assertFalse(inList("hubHeader"));

        hubManifests(hubManifest("ZulrahHelper", "Zulrah Helper"));
        safeMode = true;
        search("zulr");
        assertFalse(inList("hubHeader"));
    }

    @Test
    public void hubInstallUsesTheHubInstallPathAndSaysInstallingThenShowsFailure() throws Exception
    {
        MicrobotPluginManifest manifest = hubManifest("ZulrahHelper", "Zulrah Helper");
        hubManifests(manifest);
        CompletableFuture<LivePluginManager.OperationResult> running = new CompletableFuture<>();
        when(microbotPluginManager.installPlugin(any(), any())).thenReturn(running);
        rebuild(new HtmlPlugin());
        search("zul");
        AbstractButton install = hubRow("ZulrahHelper").getInstallButton();
        assertEquals("Install", install.getText());

        onEdt(() -> install.doClick(0));
        assertEquals("Installing...", install.getText());
        assertFalse(install.isEnabled());
        onEdt(() -> install.doClick(0));
        verify(microbotPluginManager, times(1)).installPlugin(manifest, "1.0.0");

        running.complete(LivePluginManager.OperationResult.failure("Couldn't reach the Plugin Hub"));
        onEdt(() -> { });
        onEdt(() -> { });
        assertEquals(Collections.singletonList("Couldn't reach the Plugin Hub"), errors);
        assertEquals("Install", hubRow("ZulrahHelper").getInstallButton().getText());
        assertTrue(hubRow("ZulrahHelper").getInstallButton().isEnabled());
    }

    private MicrobotPluginManifest installedHubPlugin(HtmlPlugin plugin, String healthStatus)
    {
        when(microbotPluginManager.getHubInternalName(plugin)).thenReturn("HtmlHub");
        MicrobotPluginManifest manifest = hubManifest("HtmlHub", "<html>[M] Html Hub");
        MicrobotPluginHealth.Metadata health = new MicrobotPluginHealth.Metadata();
        health.setStatus(healthStatus);
        health.setAffectedVersions(Collections.singletonList("*"));
        manifest.setHealth(health);
        hubManifests(manifest);
        return manifest;
    }

    private static MicrobotPluginManifest withHealth(MicrobotPluginManifest manifest, String status)
    {
        MicrobotPluginHealth.Metadata health = new MicrobotPluginHealth.Metadata();
        health.setStatus(status);
        health.setAffectedVersions(Collections.singletonList("*"));
        manifest.setHealth(health);
        return manifest;
    }

    @Test
    public void aSecondHealthChangeJoinsTheBannerInsteadOfReplacingIt() throws Exception
    {
        HtmlPlugin html = new HtmlPlugin();
        LatePlugin late = new LatePlugin();
        when(microbotPluginManager.getHubInternalName(html)).thenReturn("HtmlHub");
        when(microbotPluginManager.getHubInternalName(late)).thenReturn("LateHub");
        MicrobotPluginManifest htmlHub = withHealth(hubManifest("HtmlHub", "Html Hub"), MicrobotPluginHealth.STATUS_OK);
        MicrobotPluginManifest lateHub = withHealth(hubManifest("LateHub", "Late Hub"), MicrobotPluginHealth.STATUS_OK);
        hubManifests(htmlHub, lateHub);
        rebuild(html, late);

        withHealth(htmlHub, MicrobotPluginHealth.STATUS_BROKEN);
        rebuild(html, late);
        withHealth(lateHub, MicrobotPluginHealth.STATUS_BROKEN);
        rebuild(html, late);

        List<AbstractButton> links = ((MicrobotPluginListPanel.HealthBanner) field(panel, "healthBanner")).getLinks();
        assertEquals("Html Hub's undismissed line stays beside Late Hub's", 2, links.size());
        assertTrue(links.get(0).getText(), links.get(0).getText().contains("Html Fixture"));
        assertTrue(links.get(1).getText(), links.get(1).getText().contains("Late Fixture"));

        withHealth(htmlHub, MicrobotPluginHealth.STATUS_OK);
        rebuild(html, late);
        List<AbstractButton> reverted = ((MicrobotPluginListPanel.HealthBanner) field(panel, "healthBanner")).getLinks();
        assertEquals("A change that reverted before it was seen leaves the banner", 1, reverted.size());
        assertTrue(reverted.get(0).getText(), reverted.get(0).getText().contains("Late Fixture"));
        assertFalse(reverted.stream().anyMatch(link -> link.getText().contains("Html Fixture") && link.getText().contains("Confirmed broken")));
    }

    @Test
    public void noInstalledMatchWithHubMatchesSaysOnlyThatAndSitsHigh() throws Exception
    {
        hubManifests(hubManifest("ZulrahHelper", "Zulrah Helper"));
        rebuild(new HtmlPlugin());

        search("zul");

        Object message = field(panel, "emptyMessage");
        assertEquals("", ((JLabel) field(message, "noResultsTitle")).getText());
        assertTrue(((JLabel) field(message, "noResultsDescription")).getText().contains("No installed plugins match \"zul\""));
        assertTrue(((javax.swing.JComponent) message).getInsets().top < 20);
        assertFalse(visible("emptyButton"));
    }

    @Test
    public void hubInstallShowsTheHubPanelsPreInstallChecksFirst() throws Exception
    {
        MicrobotPluginManifest manifest = hubManifest("ZulrahHelper", "Zulrah Helper");
        hubManifests(manifest);
        when(microbotPluginManager.installPlugin(any(), any())).thenReturn(new CompletableFuture<>());
        rebuild(new HtmlPlugin());
        search("zul");

        confirmInstall = false;
        AbstractButton install = hubRow("ZulrahHelper").getInstallButton();
        onEdt(() -> install.doClick(0));
        assertEquals(Collections.singletonList(manifest), installChecks);
        verify(microbotPluginManager, never()).installPlugin(any(), any());
        assertEquals("Install", hubRow("ZulrahHelper").getInstallButton().getText());

        confirmInstall = true;
        onEdt(() -> install.doClick(0));
        verify(microbotPluginManager).installPlugin(manifest, "1.0.0");
    }

    @Test
    public void installedPluginReportedBrokenSaysSoOnItsRowAndOpensTheHub() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        MicrobotPluginManifest manifest = installedHubPlugin(plugin, MicrobotPluginHealth.STATUS_BROKEN);
        manifest.getHealth().setReason("Stops after banking");
        List<String> hubSearches = new ArrayList<>();
        panel.setOpenHub(hubSearches::add);
        rebuild(plugin);

        assertEquals("Confirmed broken: Stops after banking", row(HTML_NAME).getStatus().getText());
        assertEquals(ColorScheme.PROGRESS_ERROR_COLOR, statusButton(row(HTML_NAME)).getForeground());
        assertEquals("1 to fix", text("fixChip"));

        clickStatus(HTML_NAME);
        assertEquals(Collections.singletonList("Html Hub"), hubSearches);
    }

    @Test
    public void installedPluginWithUnconfirmedReportsSaysReportedIssues() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        installedHubPlugin(plugin, MicrobotPluginHealth.STATUS_UNVERIFIED);
        rebuild(plugin);

        assertEquals("Reported issues", row(HTML_NAME).getStatus().getText());
        assertEquals(ColorScheme.PROGRESS_INPROGRESS_COLOR, statusButton(row(HTML_NAME)).getForeground());
        assertEquals("1 to fix", text("fixChip"));
    }

    @Test
    public void healthChangeShowsADismissibleBannerThatOpensTheRow() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        installedHubPlugin(plugin, MicrobotPluginHealth.STATUS_OK);
        rebuild(plugin, new LatePlugin());
        assertFalse("First run only records", inList("healthBanner"));

        installedHubPlugin(plugin, MicrobotPluginHealth.STATUS_BROKEN);
        rebuild(plugin, new LatePlugin());

        assertTrue(inList("healthBanner"));
        assertEquals(0, Arrays.asList(mainPanel().getComponents()).indexOf(field(panel, "healthBanner")));
        List<AbstractButton> links = ((MicrobotPluginListPanel.HealthBanner) field(panel, "healthBanner")).getLinks();
        assertEquals(1, links.size());
        assertTrue("The row's own name, as the list shows it", links.get(0).getText().contains("Html Fixture"));
        assertTrue(links.get(0).getText(), links.get(0).getText().contains("Confirmed broken"));
        assertEquals("Show this plugin in the list", links.get(0).getToolTipText());

        onEdt(() -> links.get(0).doClick(0));
        assertEquals("Html Fixture", searchText());
        assertEquals(Collections.singletonList(HTML_NAME), shownNames());
        search("");
        assertFalse("An opened line leaves the banner", inList("healthBanner"));
        rebuild(plugin, new LatePlugin());
        assertFalse("and is saved as seen", inList("healthBanner"));
    }

    @Test
    public void dismissSavesEveryLineAsSeenButAnUnopenedChangeComesBack() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        installedHubPlugin(plugin, MicrobotPluginHealth.STATUS_OK);
        rebuild(plugin, new LatePlugin());
        installedHubPlugin(plugin, MicrobotPluginHealth.STATUS_BROKEN);
        rebuild(plugin, new LatePlugin());

        rebuild(plugin, new LatePlugin());
        assertTrue("Not opened or dismissed yet, so still shown", inList("healthBanner"));

        onEdt(() -> ((MicrobotPluginListPanel.HealthBanner) unchecked("healthBanner")).getDismissButton().doClick(0));
        assertFalse(inList("healthBanner"));
        rebuild(plugin, new LatePlugin());
        assertFalse("Seen changes stay dismissed", inList("healthBanner"));
    }

    private String countLine() throws Exception
    {
        return text("countLabel") + text("clearButton").replaceAll("<[^>]*>", "");
    }

    @Test
    public void firstRunHidesTheChipsAndOpensAlwaysOnAndSettings() throws Exception
    {
        addMicrobotRow();
        rebuild(new AlwaysPlugin());

        assertTrue(inList("emptyPanel"));
        assertFalse(visible("chipRow"));
        assertFalse(visible("onChip"));
        assertEquals(Arrays.asList("Always Fixture", "Microbot"), shownNames());
        assertTrue(inList("builtInHeader"));
        assertNull(config.get("pluginListBuiltInOpen"));
    }

    @Test
    public void pluginDisabledByItsAuthorSitsUnderAlwaysOnAndSettings() throws Exception
    {
        rebuild(new DisabledPlugin(), new HtmlPlugin());

        assertEquals(Collections.singletonList(HTML_NAME), shownNames());
        assertTrue(text("builtInHeader").endsWith("Always on & settings (1)"));
        JLabel label = (JLabel) field(row("Disabled Fixture"), "sideLabel");
        assertEquals("Turned off", label.getText());
        assertEquals("Turned off by its author", label.getToolTipText());
        assertNull("No greyed-out switch", ((Component) field(row("Disabled Fixture"), "onOffToggle")).getParent());
    }

    @Test
    public void anyPluginBesidesBuiltInsEndsTheFirstRun() throws Exception
    {
        addMicrobotRow();
        rebuild(new AlwaysPlugin(), new HtmlPlugin());

        assertFalse(inList("emptyPanel"));
        assertTrue(visible("onChip"));
        assertFalse(visible("chipRow"));
    }

    @Test
    public void rowUpdateShowsTheHubPreInstallChecksFirst() throws Exception
    {
        VersionedPlugin plugin = new VersionedPlugin();
        MicrobotPluginManifest manifest = outdated(plugin);
        rebuild(plugin);
        confirmInstall = false;

        clickStatus("Versioned Fixture");

        assertEquals(Collections.singletonList(manifest), installChecks);
        verify(microbotPluginManager, never()).updatePlugin(any(), any());
        assertEquals("Update 1.4 to 1.5", row("Versioned Fixture").getStatus().getText());
    }

    @Test
    public void enterOnARowWithSettingsOpensThemEvenWhenItNeedsFixing() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        when(pluginManager.getPluginConfigProxy(plugin)).thenReturn(mock(net.runelite.client.config.Config.class));
        when(configManager.getConfigDescriptor(any())).thenReturn(mock(net.runelite.client.config.ConfigDescriptor.class));
        rebuild(plugin);
        fail(plugin, "Missing config");
        focusOwner = row(HTML_NAME);

        dispatchKey(row(HTML_NAME), KeyEvent.VK_ENTER);

        assertEquals(Collections.singletonList(row(HTML_NAME).getPluginConfig()), openedSettings);
        assertTrue(failureDialogs.isEmpty());
    }

    @Test
    public void statusLineIsATabStopThatEnterAndSpaceActivate() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        rebuild(plugin);
        fail(plugin, "Missing config");
        AbstractButton status = statusButton(row(HTML_NAME));

        assertTrue(status.isFocusable());
        assertNotNull(status.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)));
        Object enter = status.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        assertNotNull(enter);
        onEdt(() -> status.getActionMap().get(enter).actionPerformed(null));
        onEdt(() -> { });

        assertEquals(Collections.singletonList("IllegalStateException: Missing config"), failureDialogs);
    }

    @Test
    public void closingAFailedAlwaysOnPluginsDialogClearsItSoBuiltInCanClose() throws Exception
    {
        AlwaysPlugin always = new AlwaysPlugin();
        rebuild(new HtmlPlugin(), always);
        fail(always, "Broke");
        assertTrue(shownNames().contains("Always Fixture"));

        clickStatus("Always Fixture");

        assertEquals(1, failureDialogs.size());
        assertNull(row("Always Fixture").getStatus());
        assertFalse(shownNames().contains("Always Fixture"));
    }

    @Test
    public void healthBannerHidesWhileSearchingOrFiltering() throws Exception
    {
        HtmlPlugin plugin = new HtmlPlugin();
        installedHubPlugin(plugin, MicrobotPluginHealth.STATUS_OK);
        rebuild(plugin, new LatePlugin());
        installedHubPlugin(plugin, MicrobotPluginHealth.STATUS_BROKEN);
        rebuild(plugin, new LatePlugin());
        assertTrue(inList("healthBanner"));

        search("late");
        assertFalse(inList("healthBanner"));
        search("");
        assertTrue(inList("healthBanner"));
        click("fixChip");
        assertFalse(inList("healthBanner"));
    }

    @Test
    public void hubInstallButtonsJoinTheArrowKeyOrder() throws Exception
    {
        hubManifests(hubManifest("ZulrahHelper", "Zulrah Helper"));
        rebuild(new HtmlPlugin());

        search("zul");

        assertTrue(((List<?>) field(panel, "focusTargets")).contains(hubRow("ZulrahHelper").getInstallButton()));
    }

    @Test
    public void clientShutdownSavesAStartMadeMomentsBefore() throws Exception
    {
        usage.started(HtmlPlugin.class.getName(), 100);

        panel.onClientShutdown(new net.runelite.client.events.ClientShutdown());

        assertEquals(100, new MicrobotPluginUsage(folder.getRoot().toPath().resolve("plugin-usage.json"),
            mock(ScheduledExecutorService.class)).lastStarted(HtmlPlugin.class.getName()));
    }

	// A plugin can fail or start before the first rebuild has built any rows.
	@Test
	public void refreshBeforeTheFirstRebuildDoesNothing() throws Exception
	{
		onEdt(panel::refresh);
	}
}