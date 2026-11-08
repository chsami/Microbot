/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.ui;

import com.google.common.base.Enums;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.html.HtmlEscapers;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.AlwaysOnPlugins;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.externalplugins.LivePluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginHealth;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import net.runelite.client.plugins.microbot.recovery.StartupRecovery;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginListOrder.EmptyState;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginListOrder.Filter;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginListOrder.Result;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginListOrder.Sort;
import net.runelite.client.plugins.microbot.ui.search.MicrobotPluginNames;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.ContainableFrame;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.SwingUtil;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Singleton
public class MicrobotPluginListPanel extends MicrobotPluginPanel {
    private static final long BLOCKED_NOTICE_WAIT_MS = 300_000;
    static final int LIST_VIEW_WIDTH = PluginPanel.PANEL_WIDTH - PluginPanel.SCROLLBAR_WIDTH;
    static final int LIST_ITEM_WIDTH = LIST_VIEW_WIDTH - 20;

    static final String RUNELITE_GROUP_NAME = MicrobotConfig.class.getAnnotation(ConfigGroup.class).value();
    // Display names, kept for rows with no plugin and for old pins whose plugin has not loaded yet.
    private static final String PINNED_PLUGINS_CONFIG_KEY = "pinnedPlugins";
    private static final String PINNED_PLUGIN_CLASSES_CONFIG_KEY = "pinnedPluginClasses";
    private static final String SORT_CONFIG_KEY = "pluginListSort";
    private static final String BUILT_IN_OPEN_CONFIG_KEY = "pluginListBuiltInOpen";
    private static final int TOP_BUTTON_SIZE = 30;
    private static final MicrobotPluginListOrder.Rows<MicrobotPluginListItem> ROWS = new MicrobotPluginListOrder.Rows<>() {
        @Override
        public boolean isOn(MicrobotPluginListItem row) {
            return row.isOn();
        }

        @Override
        public boolean isBuiltIn(MicrobotPluginListItem row) {
            return row.isNoSwitch();
        }

        @Override
        public boolean needsFix(MicrobotPluginListItem row) {
            return row.getStatus() != null;
        }

        @Override
        public long sortTime(MicrobotPluginListItem row, Sort sort) {
            return sort == Sort.RECENTLY_INSTALLED ? row.getInstallTime() : sort == Sort.RECENTLY_USED ? row.getUsedTime() : 0;
        }
    };
    private static final ImmutableList<String> CATEGORY_TAGS = ImmutableList.of(
            "Combat",
            "Chat",
            "Item",
            "Minigame",
            "Notification",
            "Plugin Hub",
            "Skilling",
            "XP"
    );

    private final ConfigManager configManager;
    private final PluginManager pluginManager;
    private final MicrobotPluginManager microbotPluginManager;
    private final Provider<MicrobotConfigPanel> configPanelProvider;
    @Getter
    private final MicrobotBugReporter bugReporter;
    private final List<MicrobotPluginConfigurationDescriptor> fakePlugins = new ArrayList<>();

    private final ExternalPluginManager externalPluginManager;

    @Getter
    private final MicrobotMultiplexingPluginPanel muxer;

    private final IconTextField searchBar;
    private final JScrollPane scrollPane;
    private final MicrobotFixedWidthPanel mainPanel;
    private List<MicrobotPluginListItem> pluginList;
    private final Set<String> disabledOnStartUp = new HashSet<>();
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private final Set<String> updating = new HashSet<>();
    private final Provider<LocalPluginsPanel> localPluginsPanelProvider;

    private final JPopupMenu sortMenu = new JPopupMenu();
    private final Map<Sort, JMenuItem> sortItems = new EnumMap<>(Sort.class);
    private final JCheckBoxMenuItem onChip = new JCheckBoxMenuItem("Only on");
    private final JPopupMenu.Separator onChipSeparator = new JPopupMenu.Separator();
    private final Map<String, JLabel> authorHeaders = new HashMap<>();
    private final JToggleButton fixChip = createChip(Filter.TO_FIX);
    private final JPanel chipRow = new JPanel(new BorderLayout(BORDER_OFFSET, 0));
    // One line, "x of y \u00b7 Clear": the label carries the dot and its spaces, so the gaps match.
    private final JPanel countPanel = new JPanel(new BorderLayout());
    private final JLabel countLabel = new JLabel();
    private final JButton clearButton = new JButton("<html><u>Clear</u></html>");
    private final JButton builtInHeader = new JButton();
    private final JPanel pinnedDivider = new JPanel();
    private final JPanel emptyPanel = new JPanel(new BorderLayout(0, 10));
    private final PluginErrorPanel emptyMessage = new PluginErrorPanel();
    private final JButton emptyButton = new JButton();
    private final FocusAdapter scrollToFocused = new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
            JComponent target = (JComponent) e.getComponent();
            target.scrollRectToVisible(new Rectangle(target.getSize()));
        }
    };

    private Sort sort = Sort.AZ;
    private Filter filter = Filter.ALL;
    private boolean builtInOpen;
    private EmptyState emptyState = EmptyState.NONE;
    private final Set<MicrobotPluginListItem> kept = new HashSet<>();
    private List<MicrobotPluginListItem> shownRows = Collections.emptyList();
    private List<JComponent> focusTargets = Collections.emptyList();
    @Setter
    private Consumer<String> openHub = query -> { };

    private static final int HUB_RESULTS = 5;
    private final MicrobotPluginUsage usage;
    private final JLabel hubHeader = new JLabel();
    private final Map<String, HubRow> hubRows = new HashMap<>();
    private final Set<String> installing = new HashSet<>();
    // Lowercased: Hub names of rows, plugin class names and blocked jars, so the Hub section offers none of them.
    private final Set<String> installedNames = new HashSet<>();
    private final HealthBanner healthBanner = new HealthBanner();

    @Inject
    public MicrobotPluginListPanel(
            ConfigManager configManager,
            PluginManager pluginManager,
            MicrobotPluginManager microbotPluginManager,
            ExternalPluginManager externalPluginManager,
            EventBus eventBus,
            Provider<MicrobotConfigPanel> configPanelProvider,
            Provider<LocalPluginsPanel> localPluginsPanelProvider,
            MicrobotBugReporter bugReporter,
            MicrobotPluginUsage usage) {
        super(false);

        this.bugReporter = bugReporter;
        this.localPluginsPanelProvider = localPluginsPanelProvider;
        this.configManager = configManager;
        this.pluginManager = pluginManager;
        this.externalPluginManager = externalPluginManager;
        this.configPanelProvider = configPanelProvider;
        this.microbotPluginManager = microbotPluginManager;
        this.usage = usage;

        muxer = new MicrobotMultiplexingPluginPanel(this) {
            @Override
            protected void onAdd(MicrobotPluginPanel p) {
                eventBus.register(p);
            }

            @Override
            protected void onRemove(MicrobotPluginPanel p) {
                eventBus.unregister(p);
            }

            // Only leaving the Installed tab lands here: opening settings swaps panels inside the muxer.
            @Override
            public void onDeactivate() {
                super.onDeactivate();
                setFilter(Filter.ALL);
            }
        };

        searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20 - TOP_BUTTON_SIZE, TOP_BUTTON_SIZE));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateList();
            }
        });
        CATEGORY_TAGS.forEach(searchBar.getSuggestionListModel()::addElement);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel topPanel = new JPanel();
        topPanel.setBorder(new EmptyBorder(10, 10, 4, 10));
        topPanel.setLayout(new BorderLayout(0, BORDER_OFFSET));
        topPanel.add(createSearchRow(), BorderLayout.CENTER);
        topPanel.add(createFilterRow(), BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        mainPanel = new MicrobotFixedWidthPanel(LIST_VIEW_WIDTH);
        mainPanel.setBorder(new EmptyBorder(4, 10, 10, 10));
        mainPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel northPanel = new MicrobotFixedWidthPanel(LIST_VIEW_WIDTH);
        northPanel.setLayout(new BorderLayout());
        northPanel.add(mainPanel, BorderLayout.NORTH);

        scrollPane = new JScrollPane(northPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        // The scroll pane's own Up/Down would swallow the keys that move between rows.
        InputMap scrollKeys = scrollPane.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        scrollKeys.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "none");
        scrollKeys.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "none");
        add(scrollPane, BorderLayout.CENTER);

        setUpListParts();
        bindKeys();
        MicrobotPluginFailures.setListener(this::queueRefresh);
        MicrobotPluginUsage.setStartRecorder(plugin -> usage.started(plugin.getClass().getName()));
    }

    private JPanel createSearchRow() {
        JMenuItem localPlugins = new JMenuItem("Local Plugins...");
        localPlugins.setToolTipText("Add, update, reload or remove plugin JARs while Microbot is running");
        localPlugins.addActionListener(e -> localPluginsPanelProvider.get().open(this));

        JPanel searchRow = new JPanel(new BorderLayout(BORDER_OFFSET, 0));
        searchRow.setOpaque(false);
        searchRow.add(searchBar, BorderLayout.CENTER);
        searchRow.add(createSortButton("Sort, filter and more", sortMenu, sortItems, Sort.values(), Sort::getLabel, sort,
                this::setSort, localPlugins), BorderLayout.EAST);
        // Sits between the sorts and Local Plugins, behind its own separator.
        onChip.addActionListener(e -> setFilter(filter == Filter.ON ? Filter.ALL : Filter.ON));
        sortMenu.insert(onChipSeparator, Sort.values().length);
        sortMenu.insert(onChip, Sort.values().length + 1);
        return searchRow;
    }

    /**
     * A menu button: one radio item per sort, then {@code extra}. {@code name} says everything the menu holds, not
     * just "Sort". Tab reaches it; a click does not focus it.
     */
    static <S> JButton createSortButton(String name, JPopupMenu menu, Map<S, JMenuItem> items, S[] options, Function<S, String> label,
            S selected, Consumer<S> onSort, JMenuItem extra) {
        ButtonGroup group = new ButtonGroup();
        for (S option : options) {
            JMenuItem item = new JRadioButtonMenuItem(label.apply(option), option == selected);
            item.addActionListener(e -> onSort.accept(option));
            group.add(item);
            items.put(option, item);
            menu.add(item);
        }
        menu.addSeparator();
        menu.add(extra);

        JButton button = new JButton(createSortIcon());
        SwingUtil.removeButtonDecorations(button);
        button.setPreferredSize(new Dimension(TOP_BUTTON_SIZE, TOP_BUTTON_SIZE));
        button.setRequestFocusEnabled(false);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        button.addActionListener(e -> menu.show(button, button.getWidth() - menu.getPreferredSize().width, button.getHeight()));
        return button;
    }

    private static ImageIcon createSortIcon() {
        BufferedImage image = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(ColorScheme.LIGHT_GRAY_COLOR);
        graphics.fillPolygon(new int[]{0, 3, 7}, new int[]{5, 1, 5}, 3);
        graphics.fillRect(3, 4, 1, 9);
        graphics.fillPolygon(new int[]{7, 11, 14}, new int[]{9, 13, 9}, 3);
        graphics.fillRect(10, 1, 1, 9);
        graphics.dispose();
        return new ImageIcon(image);
    }

    private JToggleButton createChip(Filter target) {
        JToggleButton chip = new JToggleButton();
        SwingUtil.removeButtonDecorations(chip);
        chip.setFont(FontManager.getRunescapeSmallFont());
        chip.setRequestFocusEnabled(false);
        chip.addActionListener(e -> setFilter(filter == target ? Filter.ALL : target));
        chip.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                styleChip(chip, chip.isSelected());
            }

            @Override
            public void focusLost(FocusEvent e) {
                styleChip(chip, chip.isSelected());
            }
        });
        styleChip(chip, false);
        return chip;
    }

    private static void styleChip(JToggleButton chip, boolean active) {
        chip.setSelected(active);
        chip.setForeground(active ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
        chip.setBorder(new CompoundBorder(
                new LineBorder(active || chip.isFocusOwner() ? ColorScheme.BRAND_ORANGE : ColorScheme.MEDIUM_GRAY_COLOR),
                new EmptyBorder(2, 6, 2, 6)));
    }

    private JPanel createFilterRow() {
        chipRow.setOpaque(false);
        chipRow.add(fixChip, BorderLayout.WEST);

        countLabel.setFont(FontManager.getRunescapeSmallFont());
        countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        SwingUtil.removeButtonDecorations(clearButton);
        clearButton.setBorder(new EmptyBorder(0, 0, 0, 0));
        clearButton.setFont(FontManager.getRunescapeSmallFont());
        clearButton.setForeground(ColorScheme.BRAND_ORANGE);
        clearButton.setRequestFocusEnabled(false);
        clearButton.addActionListener(e -> clearFilters());
        countPanel.setOpaque(false);
        countPanel.setVisible(false);
        clearButton.setHorizontalAlignment(SwingConstants.LEFT);
        countPanel.add(countLabel, BorderLayout.WEST);
        countPanel.add(clearButton, BorderLayout.CENTER);

        JPanel filterRow = new JPanel(new BorderLayout(0, 4));
        filterRow.setOpaque(false);
        filterRow.add(chipRow, BorderLayout.WEST);
        filterRow.add(countPanel, BorderLayout.SOUTH);
        return filterRow;
    }

    private void setUpListParts() {
        SwingUtil.removeButtonDecorations(builtInHeader);
        builtInHeader.setHorizontalAlignment(SwingConstants.LEFT);
        builtInHeader.setFont(FontManager.getRunescapeSmallFont());
        builtInHeader.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        builtInHeader.setRequestFocusEnabled(false);
        builtInHeader.addFocusListener(scrollToFocused);
        builtInHeader.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                builtInHeader.setForeground(ColorScheme.BRAND_ORANGE);
            }

            @Override
            public void focusLost(FocusEvent e) {
                builtInHeader.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            }
        });
        builtInHeader.addActionListener(e -> {
            builtInOpen = !builtInOpen;
            configManager.setConfiguration(RUNELITE_GROUP_NAME, BUILT_IN_OPEN_CONFIG_KEY, String.valueOf(builtInOpen));
            updateList();
        });

        pinnedDivider.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        pinnedDivider.setPreferredSize(new Dimension(LIST_ITEM_WIDTH, 1));

        emptyButton.addActionListener(e -> {
            if (emptyState == EmptyState.NO_MATCHES) {
                clearFilters();
            } else {
                openHub.accept("");
            }
        });
        emptyPanel.setOpaque(false);
        emptyPanel.add(emptyMessage, BorderLayout.CENTER);
        emptyPanel.add(emptyButton, BorderLayout.SOUTH);

        hubHeader.setFont(FontManager.getRunescapeSmallFont());
        hubHeader.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        hubHeader.setBorder(new EmptyBorder(6, 0, 0, 0));
        healthBanner.getDismissButton().addActionListener(e -> {
            usage.markHealthSeen(healthBanner.clear());
            updateList();
        });
    }

    private void bindKeys() {
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), () -> true, () -> searchBar.requestFocusInWindow());
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), () -> inSearch() || focusedTarget() != null, () -> moveFocus(1));
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), () -> focusedTarget() != null, () -> moveFocus(-1));
        // On release, like a button, so holding Space cannot flip a plugin repeatedly.
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), () -> focusOwner() instanceof MicrobotPluginListItem,
                () -> ((MicrobotPluginListItem) focusOwner()).toggle());
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), () -> focusOwner() instanceof MicrobotPluginListItem,
                () -> activate((MicrobotPluginListItem) focusOwner()));
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), () -> true, this::onEscape);
    }

    // A disabled action lets the key through, so Space and Enter still work in the search field.
    private void bindKey(KeyStroke key, BooleanSupplier applies, Runnable action) {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(key, key);
        getActionMap().put(key, new AbstractAction() {
            @Override
            public boolean isEnabled() {
                return applies.getAsBoolean();
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    Component focusOwner() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }

    boolean isSafeMode() {
        return StartupRecovery.mode().isSafeMode();
    }

    private boolean inSearch() {
        Component owner = focusOwner();
        return owner != null && SwingUtilities.isDescendingFrom(owner, searchBar);
    }

    private JComponent focusedTarget() {
        Component owner = focusOwner();
        return owner == null ? null : focusTargets.stream()
                .filter(target -> SwingUtilities.isDescendingFrom(owner, target))
                .findFirst()
                .orElse(null);
    }

    void moveFocus(int delta) {
        JComponent next = MicrobotPluginListOrder.step(focusTargets, focusedTarget(), delta);
        (next == null ? searchBar : next).requestFocusInWindow();
    }

    private void onEscape() {
        if (MicrobotPluginListOrder.isFiltering(searchBar.getText(), filter)) {
            clearFilters();
        } else if (focusedTarget() != null) {
            searchBar.requestFocusInWindow();
        } else {
            Client client = Microbot.getClient();
            if (client != null && client.getCanvas() != null) {
                client.getCanvas().requestFocusInWindow();
            }
        }
    }

    private void setSort(Sort next) {
        sort = next;
        snapshotUsage();
        configManager.setConfiguration(RUNELITE_GROUP_NAME, SORT_CONFIG_KEY, next.name());
        updateList();
    }

    private void setFilter(Filter next) {
        if (filter != next) {
            filter = next;
            kept.clear();
            shownRows = Collections.emptyList();
            updateList();
        }
    }

    private void clearFilters() {
        setFilter(Filter.ALL);
        searchBar.setText("");
    }

    public void rebuildPluginList() {
        Set<String> pinnedNames = readPins(PINNED_PLUGINS_CONFIG_KEY);
        Set<String> pinnedClasses = readPins(PINNED_PLUGIN_CLASSES_CONFIG_KEY);

        Map<String, Long> installTimes = microbotPluginManager.getInstallTimes();
        Predicate<Plugin> isMicrobotPlugin = plugin ->
                plugin.getClass().getPackage().getName().toLowerCase().contains("microbot");

        // Blocked plugins never loaded, so they get a row with no plugin, switch or cog: only the reason.
        Map<MicrobotPluginConfigurationDescriptor, String> blocked = new IdentityHashMap<>();
        Map<String, MicrobotPluginManifest> manifests = microbotPluginManager.getManifestMap();
        microbotPluginManager.getBlockedPlugins().forEach((name, reason) -> {
            MicrobotPluginManifest manifest = manifests.get(name);
            String displayName = manifest == null || Strings.isNullOrEmpty(manifest.getDisplayName()) ? name : manifest.getDisplayName();
            // A Hub name means the Hub can fix it; without one it is a jar the player added, fixed in Local Plugins.
            blocked.put(new MicrobotPluginConfigurationDescriptor(displayName, "", new String[]{name}, null, null, null, null,
                    manifest == null ? null : name), reason);
        });

        // populate pluginList with all non-hidden plugins
        pluginList = Stream.concat(
                        Stream.concat(fakePlugins.stream(), blocked.keySet().stream()),
                        pluginManager.getPlugins().stream()
                                .filter(plugin -> {
                                    PluginDescriptor d = plugin.getClass().getAnnotation(PluginDescriptor.class);
                                    return !d.hidden() && (isMicrobotPlugin.test(plugin) || d.isExternal());
                                })
                                .map(plugin ->
                                {
                                    PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                                    Config config = pluginManager.getPluginConfigProxy(plugin);
                                    ConfigDescriptor configDescriptor = config == null ? null : configManager.getConfigDescriptor(config);
                                    List<String> conflicts = pluginManager.conflictsForPlugin(plugin).stream()
                                            .map(Plugin::getName)
                                            .collect(Collectors.toList());

                                    return new MicrobotPluginConfigurationDescriptor(
                                            descriptor.name(),
                                            descriptor.description(),
                                            descriptor.tags(),
                                            plugin,
                                            config,
                                            configDescriptor,
                                            conflicts,
                                            microbotPluginManager.getHubInternalName(plugin));
                                })
                )
                .map(desc ->
                {
                    MicrobotPluginListItem listItem = new MicrobotPluginListItem(this, desc);
                    listItem.setSettingsOnly(fakePlugins.contains(desc));
                    Plugin plugin = desc.getPlugin();
                    listItem.setPinned(isNamePinned(pinnedNames, desc.getName())
                            || (plugin != null && pinnedClasses.contains(plugin.getClass().getName())));
                    if (plugin != null) {
                        String className = plugin.getClass().getName();
                        listItem.setInstallTime(usage.installedSince(className, installTimes.getOrDefault(className, 0L)));
                    } else if (blocked.containsKey(desc)) {
                        listItem.setStatus(MicrobotPluginRowStatus.of(null, blocked.get(desc), null, null, null, false));
                    }
                    listItem.addFocusListener(scrollToFocused);
                    return listItem;
                })
                .collect(Collectors.toList());

        boolean hasOldPins = pluginList.stream().map(MicrobotPluginListItem::getPluginConfig)
                .anyMatch(desc -> desc.getPlugin() != null && isNamePinned(pinnedNames, desc.getName()));
        if (hasOldPins) {
            savePinnedPlugins();
        }

        sort = Sort.parse(configManager.getConfiguration(RUNELITE_GROUP_NAME, SORT_CONFIG_KEY));
        sortItems.get(sort).setSelected(true);
        builtInOpen = Boolean.parseBoolean(configManager.getConfiguration(RUNELITE_GROUP_NAME, BUILT_IN_OPEN_CONFIG_KEY));
        kept.clear();
        shownRows = Collections.emptyList();

        installedNames.clear();
        hubRows.clear();
        pluginList.stream().map(MicrobotPluginListItem::getPluginConfig).forEach(desc -> {
            if (desc.getMicrobotHubName() != null) {
                installedNames.add(desc.getMicrobotHubName().toLowerCase(Locale.ROOT));
            }
            if (desc.getPlugin() != null) {
                installedNames.add(desc.getPlugin().getClass().getSimpleName().toLowerCase(Locale.ROOT));
            }
        });
        microbotPluginManager.getBlockedPlugins().keySet().forEach(name -> installedNames.add(name.toLowerCase(Locale.ROOT)));

        snapshotUsage();
        checkHealth();
        applyStartupRules();
        mainPanel.removeAll();
        refresh();
        SwingUtilities.invokeLater(this::showBlockedPluginsNotice);
    }

    private void applyStartupRules() {
        for (MicrobotPluginListItem listItem : pluginList) {
            Plugin plugin = listItem.getPluginConfig().getPlugin();
            if (plugin == null) {
                continue;
            }

            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            if (AlwaysOnPlugins.isLocked(descriptor)) {
                startPlugin(plugin);
            }
            if (descriptor.disableOnStartUp() && disabledOnStartUp.add(plugin.getClass().getName())) {
                stopPlugin(plugin);
            }
            if (descriptor.disable()) {
                stopPlugin(plugin);
            }
        }
    }

    public void addFakePlugin(MicrobotPluginConfigurationDescriptor... descriptor) {
        Collections.addAll(fakePlugins, descriptor);
    }

    void refresh() {
        if (pluginList == null) {
            return;
        }
        Map<Plugin, MicrobotPluginManager.OutdatedPluginUpdate> updates = microbotPluginManager.findUpdates(pluginList.stream()
                .map(row -> row.getPluginConfig().getPlugin())
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        Map<String, MicrobotPluginManifest> manifests = microbotPluginManager.getManifestMap();
        // update enabled / disabled status of all items
        pluginList.forEach(listItem ->
        {
            final Plugin plugin = listItem.getPluginConfig().getPlugin();
            if (plugin != null) {
                listItem.setPluginEnabled(pluginManager.isPluginActive(plugin));
                Throwable failure = MicrobotPluginFailures.failure(plugin);
                MicrobotPluginManager.OutdatedPluginUpdate update = updates.get(plugin);
                listItem.setStatus(MicrobotPluginRowStatus.of(failure == null ? null : MicrobotPluginFailures.describe(failure), null,
                        healthOf(listItem.getPluginConfig(), manifests),
                        update == null ? null : update.getInstalledVersion(),
                        update == null ? null : update.getLatestVersion(),
                        update != null && updating.contains(update.getInternalName())));
            }
        });

        int scrollBarPosition = scrollPane.getVerticalScrollBar().getValue();

        updateList();
        validate();

        scrollPane.getVerticalScrollBar().setValue(scrollBarPosition);
    }

    void openWithFilter(String query) {
        setFilter(Filter.ALL);
        searchBar.setText(query);
        updateList();
        muxer.pushState(this);
    }

    private void updateList() {
        // Typing, a sort or a chip can arrive before the first rebuild at startup.
        if (pluginList == null) {
            return;
        }
        final String query = searchBar.getText();
        if (filter == Filter.ON) {
            MicrobotPluginListOrder.keepSwitchedOff(kept, shownRows, MicrobotPluginListItem::isOn);
        }

        Result<MicrobotPluginListItem> result = MicrobotPluginListOrder.order(pluginList, query, sort, filter, ROWS, kept);
        pluginList.forEach(row -> row.setDimmed(kept.contains(row)));

        onChip.setSelected(filter == Filter.ON);
        styleChip(fixChip, filter == Filter.TO_FIX);
        onChip.setText("Only on (" + result.getOnCount() + ")");
        fixChip.setText(result.getToFixCount() + " to fix");
        // Shown only while something needs fixing, so the top stays clear the rest of the time.
        boolean fixShown = result.getToFixCount() > 0 || filter == Filter.TO_FIX;
        fixChip.setVisible(fixShown);
        boolean filtering = MicrobotPluginListOrder.isFiltering(query, filter);
        countLabel.setText(result.getShown() + " of " + result.getTotal() + " · ");
        countPanel.setVisible(filtering);

        List<MicrobotPluginManifest> hubHits = isSafeMode() ? Collections.emptyList()
                : MicrobotPluginHubOrder.hubMatches(microbotPluginManager.getManifestMap().values(), query,
                        name -> installedNames.contains(name.toLowerCase(Locale.ROOT)),
                        manifest -> microbotPluginManager.getPluginHealth(manifest, manifest.getVersion()).isBlocking(),
                        HUB_RESULTS);
        // First run: only built-ins and the Microbot settings row, so there is nothing for the chips to count.
        boolean hasPlugins = pluginList.stream().anyMatch(row -> !row.isNoSwitch());
        chipRow.setVisible(hasPlugins && fixShown);
        onChip.setVisible(hasPlugins);
        onChipSeparator.setVisible(hasPlugins);
        emptyState = MicrobotPluginListOrder.emptyState(result.getShown(), filtering, hasPlugins, isSafeMode());
        updateEmptyPanel(query, !hubHits.isEmpty());

        List<Component> children = new ArrayList<>();
        // It names rows the search or chip may be hiding, so it waits until they are cleared.
        if (healthBanner.hasLinks() && !filtering) {
            children.add(healthBanner);
        }
        children.addAll(result.getPinned());
        if (!result.getPinned().isEmpty() && !result.getNormal().isEmpty()) {
            children.add(pinnedDivider);
        }
        if (sort == Sort.AUTHOR && query.trim().isEmpty()) {
            String group = null;
            for (MicrobotPluginListItem row : result.getNormal()) {
                String key = MicrobotPluginListOrder.authorKey(row);
                if (!key.equals(group)) {
                    group = key;
                    children.add(authorHeaders.computeIfAbsent(key, k -> createAuthorHeader(row)));
                }
                children.add(row);
            }
        } else {
            children.addAll(result.getNormal());
        }
        if (emptyState != EmptyState.NONE) {
            children.add(emptyPanel);
        }
        if (!result.getBuiltIn().isEmpty()) {
            // First run has nothing else to show, so the Microbot settings row must not hide behind a closed header.
            boolean open = !hasPlugins || MicrobotPluginListOrder.builtInOpen(builtInOpen, query, filter,
                    result.getBuiltIn().stream().anyMatch(ROWS::needsFix));
            builtInHeader.setText((open ? "\u25BE" : "\u25B8") + " Always on & settings (" + result.getBuiltIn().size() + ")");
            children.add(builtInHeader);
            if (open) {
                children.addAll(result.getBuiltIn());
            }
        }
        if (!hubHits.isEmpty()) {
            hubHeader.setText(hubHeaderText(hubHits.size(), HUB_RESULTS));
            children.add(hubHeader);
            hubHits.forEach(manifest -> children.add(hubRows.computeIfAbsent(manifest.getInternalName(),
                    name -> new HubRow(manifest))));
            hubRows.values().forEach(HubRow::update);
        }

        shownRows = Stream.of(result.getPinned(), result.getNormal(), result.getBuiltIn())
                .flatMap(List::stream)
                .collect(Collectors.toList());
        focusTargets = children.stream()
                .filter(child -> child instanceof MicrobotPluginListItem || child == builtInHeader || child instanceof HubRow)
                .map(child -> child instanceof HubRow ? ((HubRow) child).getInstallButton() : (JComponent) child)
                .collect(Collectors.toList());
        showChildren(children);
    }

    // Cached per author by the caller, so a refresh reuses the same label.
    private static JLabel createAuthorHeader(MicrobotPluginListItem row) {
        String tag = MicrobotPluginNames.authorTag(row.getSearchableName());
        JLabel header = new JLabel(tag.isEmpty() ? "No author tag" : tag);
        header.setFont(FontManager.getRunescapeSmallFont());
        header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        header.setBorder(new EmptyBorder(6, 0, 0, 0));
        return header;
    }

    // setComponentZOrder moves a child without removing it, so a focused row keeps focus.
    private void showChildren(List<Component> children) {
        Set<Component> wanted = new HashSet<>(children);
        for (Component child : mainPanel.getComponents()) {
            if (!wanted.contains(child)) {
                mainPanel.remove(child);
            }
        }
        for (int i = 0; i < children.size(); i++) {
            Component child = children.get(i);
            if (child.getParent() != mainPanel) {
                mainPanel.add(child, i);
            } else if (mainPanel.getComponentZOrder(child) != i) {
                mainPanel.setComponentZOrder(child, i);
            }
        }
        revalidate();
        repaint();
    }

    private void updateEmptyPanel(String query, boolean hubMatches) {
        // Sits high under a search, where Hub results may follow; the first-run notices keep their space.
        emptyMessage.setBorder(new EmptyBorder(emptyState == EmptyState.NO_MATCHES ? 8 : 50, 10, 0, 10));
        switch (emptyState) {
            case NO_MATCHES:
                String target = query.trim().isEmpty() ? "this filter"
                        : "\"" + HtmlEscapers.htmlEscaper().escape(StringUtils.abbreviate(query.trim(), 24)) + "\"";
                emptyMessage.setContent(hubMatches ? "" : "No matches", wrapped("No installed plugins match " + target));
                // Only filtering gets here, so the count line's Clear is already showing.
                emptyButton.setVisible(false);
                break;
            case NO_PLUGINS:
                emptyMessage.setContent("No plugins installed yet", wrapped("Plugins you install from the Microbot Hub show up here"));
                emptyButton.setText("Open Microbot Hub");
                emptyButton.setVisible(true);
                break;
            case SAFE_MODE:
                emptyMessage.setContent("Safe mode", wrapped("External plugins are off"));
                emptyButton.setVisible(false);
                break;
            default:
                break;
        }
    }

    // A fixed width makes the label report its wrapped height, so DynamicGridLayout does not clip it.
    private static String wrapped(String html) {
        return wrapped(html, LIST_ITEM_WIDTH - 50, "center");
    }

    private static String wrapped(String html, int width, String align) {
        return "<div style='width:" + width + "px;text-align:" + align + "'>" + html + "</div>";
    }

    /** An underlined, wrapping line of plain text, for the row status line and the health banner links. */
    static String linkHtml(String text) {
        return "<html>" + wrapped("<u>" + HtmlEscapers.htmlEscaper().escape(text) + "</u>", LIST_ITEM_WIDTH - 70, "left");
    }

    void uninstall(MicrobotPluginConfigurationDescriptor plugin) {
        String hubName = plugin.getMicrobotHubName();
        if (hubName == null) {
            externalPluginManager.remove(plugin.getInternalPluginHubName());
            return;
        }

        // Built by hand: the Hub manifest may not have loaded, and removal only needs the name.
        MicrobotPluginManifest manifest = new MicrobotPluginManifest();
        manifest.setInternalName(hubName);
        microbotPluginManager.removePlugin(manifest).whenComplete((result, error) ->
                SwingUtilities.invokeLater(() -> showOperationResult("Couldn't uninstall plugin", result, error)));
    }

    // Enter opens settings; a row without any, like a blocked one, runs its status line instead. It never starts a script.
    private void activate(MicrobotPluginListItem row) {
        if (row.getPluginConfig().getConfigDescriptor() == null && row.getStatus() != null) {
            onStatusClicked(row);
        } else {
            row.openGroupConfigPanel();
        }
    }

    void onStatusClicked(MicrobotPluginListItem row) {
        MicrobotPluginRowStatus status = row.getStatus();
        Plugin plugin = row.getPluginConfig().getPlugin();
        String name = MicrobotPluginNames.plainName(row.getPluginConfig().getName());
        if (status == null) {
            return;
        }
        switch (status.getKind()) {
            case FAILED:
                Throwable failure = MicrobotPluginFailures.failure(plugin);
                // shortcut: the bug reporter takes no description, so the stack trace reaches it only through its log section.
                if (failure == null) {
                    break;
                }
                if (askToReport(name, MicrobotPluginFailures.describe(failure), row.isAlwaysOn())) {
                    bugReporter.open(this, plugin);
                } else {
                    // Seen and closed: the switch already shows it is off, and an always-on plugin has no switch to clear it.
                    MicrobotPluginFailures.clear(plugin);
                }
                break;
            case HEALTH:
                // The Hub searches its own display name, which may differ from the plugin's.
                MicrobotPluginManifest manifest = microbotPluginManager.getManifestMap().get(row.getPluginConfig().getMicrobotHubName());
                String hubName = manifest == null ? "" : MicrobotPluginNames.plainName(manifest.getDisplayName());
                openHub.accept(hubName.isEmpty() ? name : hubName);
                break;
            case BLOCKED:
                if (row.getPluginConfig().getMicrobotHubName() != null) {
                    openHub.accept(name);
                } else {
                    localPluginsPanelProvider.get().open(this);
                }
                break;
            case UPDATE:
                update(plugin);
                break;
        }
    }

    boolean askToReport(String name, String reason, boolean alwaysOn) {
        Object[] options = {"Report", "Close"};
        return JOptionPane.showOptionDialog(this, wrappedMessage(failureMessage(name, reason, alwaysOn)), name + " failed to start",
                JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[1]) == 0;
    }

    // Says it is the best few, not the Hub's total, once the list is cut at the limit.
    static String hubHeaderText(int shown, int limit) {
        return "Microbot Hub: " + (shown >= limit ? "top " + shown + " matches" : shown + (shown == 1 ? " match" : " matches"));
    }

    // What happened and what to do first; the raw error last, for a report. An always-on plugin has no switch to flip.
    static String failureMessage(String name, String reason, boolean alwaysOn) {
        return name + (alwaysOn
                ? " hit an error while starting. Restart the client to try again, or press Report to send a bug report. Close hides this message."
                : " hit an error while starting and is switched off. Switch it on to try again, or press Report to send a bug report.")
                + "\n\nError: " + reason;
    }

    // Same checks, dialogs and path as the Hub's Update.
    private void update(Plugin plugin) {
        MicrobotPluginManager.OutdatedPluginUpdate update = microbotPluginManager.findUpdate(plugin).orElse(null);
        MicrobotPluginManifest manifest = update == null ? null : microbotPluginManager.getManifestMap().get(update.getInternalName());
        if (manifest == null || updating.contains(update.getInternalName()) || !confirmInstall(manifest, update.getLatestVersion())
                || !updating.add(update.getInternalName())) {
            return;
        }
        refresh();
        microbotPluginManager.updatePlugin(manifest, update.getLatestVersion()).whenComplete((result, error) ->
                SwingUtilities.invokeLater(() -> {
                    updating.remove(update.getInternalName());
                    refresh();
                    showOperationResult("Couldn't update " + update.getDisplayName(), result, error);
                }));
    }

    private static JTextArea wrappedMessage(String message) {
        JTextArea area = new JTextArea(message, 6, 42);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        return area;
    }

    private void showOperationResult(String failureTitle, LivePluginManager.OperationResult result, Throwable error) {
        if (error == null && result.isSuccess()) {
            if (result.isNeedsRestart()) {
                JOptionPane.showMessageDialog(this, result.getMessage(), "Restart needed", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }

        Throwable cause = error == null || error.getCause() == null ? error : error.getCause();
        String message = cause == null ? result.getMessage() : cause.getMessage();
        showError(failureTitle, message);
    }

    void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    void openConfigurationPanel(String configGroup) {
        for (MicrobotPluginListItem pluginListItem : pluginList) {
            if (pluginListItem.getPluginConfig().getName().equals(configGroup)) {
                openConfigurationPanel(pluginListItem.getPluginConfig());
                break;
            }
        }
    }

    void openConfigurationPanel(Plugin plugin) {
        for (MicrobotPluginListItem pluginListItem : pluginList) {
            if (pluginListItem.getPluginConfig().getPlugin() == plugin) {
                openConfigurationPanel(pluginListItem.getPluginConfig());
                break;
            }
        }
    }

    void openConfigurationPanel(MicrobotPluginConfigurationDescriptor plugin) {
        if (plugin.getConfigDescriptor() == null) {
            return;
        }

        MicrobotConfigPanel panel = configPanelProvider.get();
        panel.init(plugin);
        muxer.pushState(this);
        muxer.pushState(panel);
    }

    void startPlugin(Plugin plugin) {
        pluginManager.setPluginEnabled(plugin, true);

        try {
            pluginManager.startPlugin(plugin);
        } catch (PluginInstantiationException ex) {
            log.warn("Error when starting plugin {}", plugin.getClass().getSimpleName(), ex);
        }
    }

    void stopPlugin(Plugin plugin) {
        if (pluginManager.isPluginAlwaysOn(plugin)) {
            return;
        }

        pluginManager.setPluginEnabled(plugin, false);

        try {
            pluginManager.stopPlugin(plugin);
        } catch (PluginInstantiationException ex) {
            log.warn("Error when stopping plugin {}", plugin.getClass().getSimpleName(), ex);
        }
    }

    private Set<String> readPins(String key) {
        final String config = configManager.getConfiguration(RUNELITE_GROUP_NAME, key);
        return config == null ? new LinkedHashSet<>() : new LinkedHashSet<>(Text.fromCSV(config));
    }

    // The old list was split on commas, so a name with a comma is stored as its parts.
    private static boolean isNamePinned(Set<String> pinnedNames, String name) {
        List<String> parts = Text.fromCSV(name);
        return !parts.isEmpty() && pinnedNames.containsAll(parts);
    }

    private void showBlockedPluginsNotice() {
        Map<String, String> blocked = microbotPluginManager.takeBlockedPluginsToNotify();
        if (blocked.isEmpty()) {
            return;
        }

        Map<String, MicrobotPluginManifest> manifests = microbotPluginManager.getManifestMap();
        String message = blockedPluginsMessage(blocked, name -> {
            MicrobotPluginManifest manifest = manifests.get(name);
            return manifest == null || manifest.getDisplayName() == null ? name : manifest.getDisplayName();
        });

        long deadline = System.currentTimeMillis() + BLOCKED_NOTICE_WAIT_MS;
        Timer timer = new Timer(1000, null);
        timer.setInitialDelay(0);
        timer.addActionListener(e -> {
            Window window = Arrays.stream(Window.getWindows())
                    .filter(w -> w instanceof ContainableFrame && w.isShowing())
                    .findFirst()
                    .orElse(null);
            if (window == null) {
                if (System.currentTimeMillis() > deadline) {
                    timer.stop();
                }
                return;
            }
            timer.stop();
            JDialog dialog = new JOptionPane(message, JOptionPane.WARNING_MESSAGE)
                    .createDialog(window, "Microbot Plugins Not Loaded");
            dialog.setModal(false);
            dialog.setVisible(true);
        });
        timer.start();
    }

    static String blockedPluginsMessage(Map<String, String> blocked, Function<String, String> displayName) {
        StringBuilder message = new StringBuilder("<html>These Microbot Hub plugins were not loaded:<br>");
        blocked.forEach((name, reason) -> message.append("<br>&bull; <b>")
                .append(HtmlEscapers.htmlEscaper().escape(displayName.apply(name)))
                .append("</b>: ")
                .append(HtmlEscapers.htmlEscaper().escape(reason)));
        return message.append("<br><br>Open the <strong>Microbot Hub</strong> to see details, remove them or install an unaffected version. For a JAR you added yourself, fix or remove the JAR in <strong>Local Plugins</strong>.</html>")
                .toString();
    }

    void savePinnedPlugins() {
        Set<String> names = readPins(PINNED_PLUGINS_CONFIG_KEY);
        Set<String> classes = readPins(PINNED_PLUGIN_CLASSES_CONFIG_KEY);

        // Entries belonging to no row stay: their plugin is simply not loaded right now.
        for (MicrobotPluginListItem listItem : pluginList) {
            MicrobotPluginConfigurationDescriptor desc = listItem.getPluginConfig();
            names.removeAll(Text.fromCSV(desc.getName()));
            if (desc.getPlugin() != null) {
                classes.remove(desc.getPlugin().getClass().getName());
            }
        }
        for (MicrobotPluginListItem listItem : pluginList) {
            MicrobotPluginConfigurationDescriptor desc = listItem.getPluginConfig();
            if (!listItem.isPinned()) {
                continue;
            }
            if (desc.getPlugin() != null) {
                classes.add(desc.getPlugin().getClass().getName());
            } else {
                names.addAll(Text.fromCSV(desc.getName()));
            }
        }

        configManager.setConfiguration(RUNELITE_GROUP_NAME, PINNED_PLUGINS_CONFIG_KEY, Text.toCSV(names));
        configManager.setConfiguration(RUNELITE_GROUP_NAME, PINNED_PLUGIN_CLASSES_CONFIG_KEY, Text.toCSV(classes));
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        queueRefresh();
    }

    private void queueRefresh() {
        if (refreshQueued.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(() -> {
                refreshQueued.set(false);
                refresh();
            });
        }
    }

    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, 24);
        return d;
    }

    @Override
    public void onActivate() {
        super.onActivate();
        if (pluginList != null) {
            snapshotUsage();
            checkHealth();
            updateList();
        }

        if (searchBar.getParent() != null) {
            searchBar.requestFocusInWindow();
        }
    }

    @Subscribe
    void onClientShutdown(ClientShutdown ev) {
        usage.flush();
    }

    @Subscribe
    private void onExternalPluginsChanged(ExternalPluginsChanged ev) {
        SwingUtilities.invokeLater(this::rebuildPluginList);
    }

    @Subscribe
    private void onProfileChanged(ProfileChanged ev) {
        SwingUtilities.invokeLater(this::rebuildPluginList);
    }

    // Read on activation and rebuild only, so a plugin starting while the list is visible does not jump.
    private void snapshotUsage() {
        if (pluginList == null) {
            return;
        }
        for (MicrobotPluginListItem row : pluginList) {
            Plugin plugin = row.getPluginConfig().getPlugin();
            row.setUsedTime(plugin == null ? 0 : usage.lastStarted(plugin.getClass().getName()));
        }
    }

    /** Hub health of an installed Hub plugin's version, or null when its manifest has not loaded. */
    private MicrobotPluginHealth healthOf(MicrobotPluginConfigurationDescriptor desc, Map<String, MicrobotPluginManifest> manifests) {
        MicrobotPluginManifest manifest = desc.getMicrobotHubName() == null ? null : manifests.get(desc.getMicrobotHubName());
        if (manifest == null) {
            return null;
        }
        String version = microbotPluginManager.getInstalledPluginVersion(manifest.getInternalName()).orElse(desc.getPlugin() == null
                ? null : desc.getPlugin().getClass().getAnnotation(PluginDescriptor.class).version());
        return microbotPluginManager.getPluginHealth(manifest, version);
    }

    private void checkHealth() {
        Map<String, MicrobotPluginManifest> manifests = microbotPluginManager.getManifestMap();
        if (pluginList == null || manifests.isEmpty()) {
            return;
        }
        Map<String, MicrobotPluginHealth> health = new HashMap<>();
        Map<String, String> states = new HashMap<>();
        Map<String, String> rowNames = new HashMap<>();
        for (MicrobotPluginListItem row : pluginList) {
            MicrobotPluginHealth pluginHealth = healthOf(row.getPluginConfig(), manifests);
            if (pluginHealth != null) {
                String hubName = row.getPluginConfig().getMicrobotHubName();
                health.put(hubName, pluginHealth);
                states.put(hubName, pluginHealth.getState().name());
                rowNames.put(hubName, MicrobotPluginNames.plainName(row.getPluginConfig().getName()));
            }
        }
        List<String> changed = usage.healthChanges(states, MicrobotPluginListPanel::worthReporting);
        healthBanner.keepOnly(changed);
        if (!changed.isEmpty()) {
            healthBanner.add(changed.stream().map(name -> {
                // The row's own name, as the list and the search box show it.
                String rowName = rowNames.get(name).isEmpty() ? name : rowNames.get(name);
                return new HealthChange(name, states.get(name), rowName, rowName + ": " + health.get(name).getLabel());
            }).collect(Collectors.toList()));
        }
    }

    // Worth a banner: a change into or out of a state that warns (turned off, needs a newer client, broken or an unverified report).
    // Verified and not reported move with every release and say nothing new.
    static boolean worthReporting(String state) {
        MicrobotPluginHealth.State known = Enums.getIfPresent(MicrobotPluginHealth.State.class, state).orNull();
        return known != null && (known.isBlocking() || known == MicrobotPluginHealth.State.UNVERIFIED);
    }

    // The Hub panel's own pre-install checks and dialogs, so no safety prompt is skipped here.
    boolean confirmInstall(MicrobotPluginManifest manifest, String version) {
        return MicrobotPluginHubPanel.confirmInstall(this, microbotPluginManager, manifest, version);
    }

    private void install(MicrobotPluginManifest manifest) {
        String name = manifest.getInternalName();
        if (installing.contains(name) || !confirmInstall(manifest, manifest.getVersion()) || !installing.add(name)) {
            return;
        }
        updateList();
        microbotPluginManager.installPlugin(manifest, manifest.getVersion()).whenComplete((result, error) ->
                SwingUtilities.invokeLater(() -> {
                    installing.remove(name);
                    updateList();
                    String plain = MicrobotPluginNames.plainName(manifest.getDisplayName());
                    showOperationResult("Couldn't install " + (plain.isEmpty() ? name : plain), result, error);
                }));
    }

    /** A not-installed Hub plugin offered under the Installed search results. */
    final class HubRow extends JPanel {
        @Getter
        private final MicrobotPluginManifest manifest;
        @Getter
        private final JButton installButton = new JButton();

        HubRow(MicrobotPluginManifest manifest) {
            super(new BorderLayout(5, 0));
            this.manifest = manifest;
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setBorder(new EmptyBorder(3, 6, 3, 4));

            // The display name as the Hub sends it, coloured author tag included, like an installed row.
            String plain = MicrobotPluginNames.plainName(manifest.getDisplayName());
            JLabel name = new JLabel(plain.isEmpty() ? manifest.getInternalName()
                    : "<html>" + wrapped(MicrobotPluginListItem.htmlBody(manifest.getDisplayName()), LIST_ITEM_WIDTH - 90, "left"));
            name.setToolTipText(Strings.isNullOrEmpty(manifest.getDescription()) ? null
                    : "<html>" + HtmlEscapers.htmlEscaper().escape(MicrobotPluginNames.plainText(manifest.getDescription())) + "</html>");
            add(name, BorderLayout.CENTER);

            SwingUtil.removeButtonDecorations(installButton);
            installButton.setFont(FontManager.getRunescapeSmallFont());
            installButton.setForeground(ColorScheme.BRAND_ORANGE);
            installButton.setBorder(new CompoundBorder(new LineBorder(ColorScheme.BRAND_ORANGE), new EmptyBorder(1, 6, 1, 6)));
            installButton.addActionListener(e -> install(manifest));
            installButton.addFocusListener(scrollToFocused);
            // Centred at its own height when a long name wraps to two lines.
            JPanel buttonCell = new JPanel(new GridBagLayout());
            buttonCell.setOpaque(false);
            buttonCell.add(installButton);
            add(buttonCell, BorderLayout.EAST);
            update();
        }

        void update() {
            boolean busy = installing.contains(manifest.getInternalName());
            installButton.setText(busy ? "Installing..." : "Install");
            installButton.setEnabled(!busy);
        }
    }

    @lombok.Value
    static class HealthChange {
        // Hub internal name: one banner line per plugin.
        String hubName;
        // The health state this line reports, saved as seen once the player opens or dismisses it.
        String state;
        // The row's display name, which is what the search box shows and matches.
        String query;
        String text;
    }

    /** Names installed Hub plugins whose health changed since last seen. Each link opens that row. */
    final class HealthBanner extends JPanel {

        private final JPanel linkPanel = new JPanel(new GridLayout(0, 1, 0, 2));
        @Getter
        private final List<AbstractButton> links = new ArrayList<>();
        @Getter
        private final JButton dismissButton = new JButton("✕");

        HealthBanner() {
            super(new BorderLayout(4, 2));
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setBorder(new CompoundBorder(new LineBorder(ColorScheme.PROGRESS_INPROGRESS_COLOR), new EmptyBorder(4, 6, 4, 4)));
            JLabel title = new JLabel("Hub status changed for your plugins");
            title.setFont(FontManager.getRunescapeSmallFont());
            title.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
            add(title, BorderLayout.NORTH);
            linkPanel.setOpaque(false);
            add(linkPanel, BorderLayout.CENTER);
            SwingUtil.removeButtonDecorations(dismissButton);
            dismissButton.setToolTipText("Dismiss");
            dismissButton.getAccessibleContext().setAccessibleName("Dismiss");
            dismissButton.setVerticalAlignment(SwingConstants.TOP);
            add(dismissButton, BorderLayout.EAST);
        }

        // Keyed by Hub internal name: a newer change for one plugin replaces its line, others stay until dismissed.
        private final Map<String, HealthChange> shown = new LinkedHashMap<>();

        boolean hasLinks() {
            return !links.isEmpty();
        }

        void add(List<HealthChange> changes) {
            changes.forEach(change -> shown.put(change.getHubName(), change));
            rebuild();
        }

        void keepOnly(Collection<String> hubNames) {
            shown.keySet().retainAll(hubNames);
            rebuild();
        }

        /** Empties the banner and returns what it showed (Hub name to state), for saving as seen. */
        Map<String, String> clear() {
            Map<String, String> seen = new HashMap<>();
            shown.values().forEach(change -> seen.put(change.getHubName(), change.getState()));
            shown.clear();
            rebuild();
            return seen;
        }

        // Opening a line is seeing it: it leaves the banner, and the rest stay until dismissed.
        private void open(HealthChange change) {
            shown.remove(change.getHubName());
            usage.markHealthSeen(Collections.singletonMap(change.getHubName(), change.getState()));
            rebuild();
            openWithFilter(change.getQuery());
        }

        private void rebuild() {
            links.clear();
            linkPanel.removeAll();
            for (HealthChange change : shown.values()) {
                JButton link = new JButton(linkHtml(change.getText()));
                SwingUtil.removeButtonDecorations(link);
                link.setHorizontalAlignment(SwingConstants.LEFT);
                link.setBorder(new EmptyBorder(0, 0, 0, 0));
                link.setFont(FontManager.getRunescapeSmallFont());
                link.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                link.setToolTipText("Show this plugin in the list");
                link.addActionListener(e -> open(change));
                links.add(link);
                linkPanel.add(link);
            }
        }
    }
}
