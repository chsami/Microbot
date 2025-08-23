/*
 * Copyright (c) 2023 Microbot
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

import com.google.common.html.HtmlEscapers;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.config.PluginSearch;
import net.runelite.client.plugins.config.SearchablePlugin;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginClient;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.SwingUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class MicrobotPluginHubPanel extends PluginPanel {
    private static final ImageIcon MISSING_ICON;
    private static final ImageIcon HELP_ICON;
    private static final ImageIcon CONFIGURE_ICON;
    private static final Pattern SPACES = Pattern.compile(" +");

    static {
        BufferedImage missingIcon = ImageUtil.loadImageResource(MicrobotPluginHubPanel.class, "pluginhub_missingicon.png");
        MISSING_ICON = new ImageIcon(missingIcon);

        BufferedImage helpIcon = ImageUtil.loadImageResource(MicrobotPluginHubPanel.class, "pluginhub_help.png");
        HELP_ICON = new ImageIcon(helpIcon);

        BufferedImage configureIcon = ImageUtil.loadImageResource(MicrobotPluginHubPanel.class, "pluginhub_configure.png");
        CONFIGURE_ICON = new ImageIcon(configureIcon);
    }

    private class PluginIcon extends JLabel {
        private boolean loadingStarted;
        private String iconUrl;

        private boolean hasIcon() {
            return !iconUrl.isBlank();
        }

        PluginIcon(String iconUrl) {
            setIcon(MISSING_ICON);

            this.iconUrl = iconUrl;
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);

            if (!loadingStarted) {
                loadingStarted = true;
                synchronized (iconLoadQueue) {
                    iconLoadQueue.add(this);
                    if (iconLoadQueue.size() == 1) {
                        executor.submit(MicrobotPluginHubPanel.this::pumpIconQueue);
                    }
                }
            }
        }

        private void load() {
            try {
                BufferedImage img = microbotPluginClient.downloadIcon(iconUrl);
                if (img == null) {
                    return;
                }

                // Scale the image to fit within the icon area while maintaining aspect ratio
                int iconWidth = 48; // ICON_WIDTH as defined in PluginItem
                int iconHeight = 70; // HEIGHT as defined in PluginItem

                BufferedImage scaledImg = scaleImageToFit(img, iconWidth, iconHeight);
                SwingUtilities.invokeLater(() -> setIcon(new ImageIcon(scaledImg)));
            } catch (IOException e) {
                log.info("Cannot download icon \"{}\"", iconUrl, e);
            }
        }

        /**
         * Scales an image to fit within the specified dimensions while maintaining aspect ratio
         */
        private BufferedImage scaleImageToFit(BufferedImage originalImage, int maxWidth, int maxHeight) {
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            // Calculate the scaling factor to fit the image within the bounds
            double scaleX = (double) maxWidth / originalWidth;
            double scaleY = (double) maxHeight / originalHeight;
            double scale = Math.min(scaleX, scaleY); // Use the smaller scale to ensure it fits

            // Calculate the new dimensions
            int newWidth = (int) (originalWidth * scale);
            int newHeight = (int) (originalHeight * scale);

            // Create a new scaled image
            BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaledImage.createGraphics();

            // Enable high-quality rendering
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw the scaled image
            g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
            g2d.dispose();

            return scaledImage;
        }
    }

    private void pumpIconQueue() {
        PluginIcon pi;
        synchronized (iconLoadQueue) {
            pi = iconLoadQueue.poll();
        }

        if (pi == null) {
            return;
        }

        pi.load();

        synchronized (iconLoadQueue) {
            if (iconLoadQueue.isEmpty()) {
                return;
            }
        }

        // re add ourselves to the executor queue so we don't block the executor for a long time
        executor.submit(this::pumpIconQueue);
    }

    private class PluginItem extends JPanel implements SearchablePlugin {
        private static final int HEIGHT = 70;
        private static final int ICON_WIDTH = 48;
        private static final int BOTTOM_LINE_HEIGHT = 16;

        @Getter
        private final List<String> keywords = new ArrayList<>();

        @Getter
        private final int userCount;

        @Getter
        private final boolean installed;
        private MicrobotPluginManifest manifest;

        PluginItem(MicrobotPluginManifest manifest, Collection<Plugin> loadedPlugins, int userCount, boolean installed) {
            this.manifest = manifest;
            this.userCount = userCount;
            this.installed = installed;

            var currentVersion = loadedPlugins.isEmpty() ? manifest.getVersion() : loadedPlugins.iterator().next().getClass().getAnnotation(PluginDescriptor.class).version();

            Collections.addAll(keywords, SPACES.split(manifest.getDisplayName()));

            Collections.addAll(keywords, SPACES.split(manifest.getDescription()));

            Collections.addAll(keywords, manifest.getAuthors());

            Collections.addAll(keywords, manifest.getTags());

            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setOpaque(true);

            GroupLayout layout = new GroupLayout(this);
            setLayout(layout);

            JLabel pluginName = new JLabel(manifest.getDisplayName());
            pluginName.setFont(FontManager.getRunescapeBoldFont());
            pluginName.setToolTipText(manifest.getDisplayName());

            JLabel author = new JLabel(manifest.getAuthors().length > 1 ? "Multiple authors" : manifest.getAuthor());
            author.setFont(FontManager.getRunescapeSmallFont());
            author.setToolTipText(manifest.getAuthor());

            JLabel version = new JLabel(currentVersion);
            version.setFont(FontManager.getRunescapeSmallFont());
            version.setToolTipText(currentVersion);

            String descriptionText = manifest.getDescription();

            if (!descriptionText.startsWith("<html>")) {
                descriptionText = "<html>" + HtmlEscapers.htmlEscaper().escape(descriptionText) + "</html>";
            }

            JLabel description = new JLabel(descriptionText);
            description.setVerticalAlignment(JLabel.TOP);
            description.setToolTipText(descriptionText);

            JLabel icon = new PluginIcon(manifest.getIconUrl());
            icon.setHorizontalAlignment(JLabel.CENTER);

            JLabel badge = new JLabel();

            JButton help = new JButton(HELP_ICON);
            SwingUtil.removeButtonDecorations(help);
            help.setBorder(null);
            help.setToolTipText("Open help");
            help.addActionListener(ev -> LinkBrowser.browse("https://chsami.github.io/Microbot-Hub/" + manifest.getInternalName()));

            JButton configure = new JButton(CONFIGURE_ICON);
            SwingUtil.removeButtonDecorations(configure);
            configure.setToolTipText("Configure");
            configure.setBorder(null);
            if (!loadedPlugins.isEmpty()) {
                String search = null;
                if (loadedPlugins.size() > 1) {
                    search = manifest.getInternalName();
                } else {
                    Plugin plugin = loadedPlugins.iterator().next();
                    configure.addActionListener(l -> topLevelConfigPanel.openConfigurationPanel(plugin));
                }

                if (search != null) {
                    final String _search = search;
                    configure.addActionListener(l -> topLevelConfigPanel.openWithFilter(_search));
                }
            } else {
                configure.setVisible(false);
            }

            JButton addrm = new JButton();
            if (!installed) {
                addrm.setText("Install");
                addrm.setBackground(new Color(0x28BE28));
                addrm.addActionListener(l -> {
                    // Check version compatibility before installing
                    if (!microbotPluginManager.isClientVersionCompatible(manifest.getMinClientVersion())) {
                        String _currentMicrobotVersion = RuneLiteProperties.getMicrobotVersion();
                        String requiredVersion = manifest.getMinClientVersion();

                        String message = String.format(
                                "Cannot install plugin '%s'.\n\n" +
                                        "Required client version: %s\n" +
                                        "Current client version: %s\n\n" +
                                        "Please update your Microbot client to install this plugin.",
                                manifest.getDisplayName(),
                                requiredVersion != null ? requiredVersion : "Unknown",
                                _currentMicrobotVersion != null ? _currentMicrobotVersion : "Unknown"
                        );
                        // Create a custom dialog
                        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Version Incompatibility", true);
                        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                        dialog.setResizable(false);
                        dialog.setIconImages(Arrays.asList(ClientUI.ICON_128, ClientUI.ICON_16));

                        JPanel messagePanel = new JPanel(new BorderLayout(10, 0));
                        messagePanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

                        JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.warningIcon"));
                        iconLabel.setVerticalAlignment(SwingConstants.TOP);
                        messagePanel.add(iconLabel, BorderLayout.WEST);

                        JLabel messageLabel = new JLabel("<html>" + message.replace("\n", "<br>") + "</html>");
                        messageLabel.setHorizontalAlignment(SwingConstants.LEFT);
                        messagePanel.add(messageLabel, BorderLayout.CENTER);

                        JButton okButton = new JButton("OK");
                        okButton.setPreferredSize(new Dimension(67, 22));
                        okButton.setBackground(ColorScheme.BRAND_ORANGE);
                        okButton.setForeground(ColorScheme.DARKER_GRAY_COLOR);
                        okButton.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createEmptyBorder(1, 1, 1, 1),
                                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1)
                        ));
                        okButton.setFocusPainted(false);
                        okButton.setFont(okButton.getFont().deriveFont(Font.BOLD));
                        okButton.addActionListener(e -> dialog.dispose());

                        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 20, 20));
                        buttonPanel.add(okButton);

                        dialog.setLayout(new BorderLayout());
                        dialog.add(messagePanel, BorderLayout.CENTER);
                        dialog.add(buttonPanel, BorderLayout.SOUTH);

                        dialog.pack();
                        dialog.setLocationRelativeTo(this);
                        dialog.setVisible(true);
                        return;
                    }

                    addrm.setText("Installing");
                    addrm.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
                    microbotPluginManager.install(manifest);
                });
            } else if (installed) {
                // Check if update is available
                boolean updateAvailable = false;
                if (!loadedPlugins.isEmpty()) {
                    Plugin loadedPlugin = loadedPlugins.iterator().next();
                    PluginDescriptor descriptor = loadedPlugin.getClass().getAnnotation(PluginDescriptor.class);
                    String loadedVersion = descriptor != null ? descriptor.version() : "0";
                    String manifestVersion = manifest.getVersion();

                    updateAvailable = !loadedVersion.equals(manifestVersion);
                }

                if (updateAvailable) {
                    addrm.setText("Update");
                    addrm.setBackground(new Color(0x1E90FF)); // Dodger Blue
                    addrm.addActionListener(l -> {
                        addrm.setText("Updating");
                        addrm.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
                        microbotPluginManager.remove(manifest.getInternalName());
                        microbotPluginManager.install(manifest); // This will update the plugin
                        reloadPluginList();
                    });
                } else {
                    addrm.setText("Remove");
                    addrm.setBackground(new Color(0xBE2828));
                    addrm.addActionListener(l -> {
                        addrm.setText("Removing");
                        addrm.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
                        microbotPluginManager.remove(manifest.getInternalName());
                    });
                }
            } else {
                addrm.setText("Unavailable");
                addrm.setBackground(Color.GRAY);
                addrm.setEnabled(false);
            }
            addrm.setBorder(new LineBorder(addrm.getBackground().darker()));
            addrm.setFocusPainted(false);

            layout.setHorizontalGroup(layout.createSequentialGroup()
                    .addGroup(layout.createParallelGroup()
                            .addComponent(badge, GroupLayout.Alignment.TRAILING)
                            .addComponent(icon, ICON_WIDTH, ICON_WIDTH, ICON_WIDTH))
                    .addGap(5)
                    .addGroup(layout.createParallelGroup()
                            .addGroup(layout.createSequentialGroup()
                                    .addComponent(pluginName, 0, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
                                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
                                    .addComponent(author, 0, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE))
                            .addComponent(description, 0, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
                            .addGroup(layout.createSequentialGroup()
                                    .addComponent(version, 0, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
                                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.PREFERRED_SIZE, 100)
                                    .addComponent(help, 0, 24, 24)
                                    .addComponent(configure, 0, 24, 24)
                                    .addComponent(addrm, 0, 57, GroupLayout.PREFERRED_SIZE)
                                    .addGap(5))));

            int lineHeight = description.getFontMetrics(description.getFont()).getHeight();
            layout.setVerticalGroup(layout.createParallelGroup()
                    .addComponent(badge, GroupLayout.Alignment.TRAILING)
                    .addComponent(icon, HEIGHT, GroupLayout.DEFAULT_SIZE, HEIGHT + lineHeight)
                    .addGroup(layout.createSequentialGroup()
                            .addGap(5)
                            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                    .addComponent(pluginName)
                                    .addComponent(author))
                            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
                            .addComponent(description, lineHeight, GroupLayout.PREFERRED_SIZE, lineHeight * 2)
                            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
                            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                    .addComponent(version, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT)
                                    .addComponent(help, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT)
                                    .addComponent(configure, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT)
                                    .addComponent(addrm, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT))
                            .addGap(5)));
        }

        @Override
        public String getSearchableName() {
            return manifest.getDisplayName();
        }

        @Override
        public int installs() {
            return userCount;
        }
    }

    private final MicrobotTopLevelConfigPanel topLevelConfigPanel;
    private final MicrobotPluginManager microbotPluginManager;
    private final PluginManager pluginManager;
    private final MicrobotPluginClient microbotPluginClient;
    private final ScheduledExecutorService executor;

    private final Deque<PluginIcon> iconLoadQueue = new ArrayDeque<>();

    private final IconTextField searchBar;
    private final JLabel refreshing;
    private final JPanel mainPanel;
    private List<PluginItem> plugins = null;

    @Inject
    MicrobotPluginHubPanel(
            MicrobotTopLevelConfigPanel topLevelConfigPanel,
            MicrobotPluginManager microbotPluginManager,
            PluginManager pluginManager,
            MicrobotPluginClient microbotPluginClient,
            ScheduledExecutorService executor) {
        super(false);
        this.topLevelConfigPanel = topLevelConfigPanel;
        this.microbotPluginManager = microbotPluginManager;
        this.pluginManager = pluginManager;
        this.microbotPluginClient = microbotPluginClient;
        this.executor = executor;

        {
            Object refresh = "this could just be a lambda, but no, it has to be abstracted";
            getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), refresh);
            getActionMap().put(refresh, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    reloadPluginList();
                }
            });
        }

        setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchBar = new IconTextField();
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                executor.execute(MicrobotPluginHubPanel.this::filter);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                executor.execute(MicrobotPluginHubPanel.this::filter);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                executor.execute(MicrobotPluginHubPanel.this::filter);
            }
        });

        JLabel externalPluginWarning1 = new JLabel("<html>Microbot plugins are verified to not be " +
                "malicious, but are not " +
                "maintained by the Microbot developers. " +
                "They may cause bugs or instability.</html>");
        externalPluginWarning1.setBackground(new Color(0xFFBB33));
        externalPluginWarning1.setForeground(Color.BLACK);
        externalPluginWarning1.setBorder(new EmptyBorder(5, 5, 5, 2));
        externalPluginWarning1.setOpaque(true);

        JLabel externalPluginWarning2 = new JLabel("Use at your own risk!");
        externalPluginWarning2.setHorizontalAlignment(JLabel.CENTER);
        externalPluginWarning2.setFont(FontManager.getRunescapeBoldFont());
        externalPluginWarning2.setBackground(externalPluginWarning1.getBackground());
        externalPluginWarning2.setForeground(externalPluginWarning1.getForeground());
        externalPluginWarning2.setBorder(new EmptyBorder(0, 5, 5, 5));
        externalPluginWarning2.setOpaque(true);

        mainPanel = new JPanel();
        mainPanel.setBorder(BorderFactory.createEmptyBorder(0, 7, 7, 7));
        mainPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
        mainPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        refreshing = new JLabel("Loading...");
        refreshing.setHorizontalAlignment(JLabel.CENTER);

        JPanel mainPanelWrapper = new FixedWidthPanel();

        {
            GroupLayout layout = new GroupLayout(mainPanelWrapper);
            mainPanelWrapper.setLayout(layout);

            layout.setVerticalGroup(layout.createSequentialGroup()
                    .addComponent(externalPluginWarning1)
                    .addComponent(externalPluginWarning2)
                    .addGap(7)
                    .addComponent(mainPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(refreshing)
                    .addGap(0, 0, 0x7000));

            layout.setHorizontalGroup(layout.createParallelGroup()
                    .addComponent(externalPluginWarning1, 0, Short.MAX_VALUE, Short.MAX_VALUE)
                    .addComponent(externalPluginWarning2, 0, Short.MAX_VALUE, Short.MAX_VALUE)
                    .addComponent(mainPanel)
                    .addComponent(refreshing, 0, Short.MAX_VALUE, Short.MAX_VALUE));
        }

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // Can't use Short.MAX_VALUE like the docs say because of JDK-8079640
        scrollPane.setPreferredSize(new Dimension(0x7000, 0x7000));
        scrollPane.setViewportView(mainPanelWrapper);

        {
            GroupLayout layout = new GroupLayout(this);
            setLayout(layout);

            layout.setVerticalGroup(layout.createSequentialGroup()
                    .addGap(10)
                    .addComponent(searchBar, 30, 30, 30)
                    .addGap(10)
                    .addComponent(scrollPane));

            layout.setHorizontalGroup(layout.createParallelGroup()
                    .addGroup(layout.createSequentialGroup()
                            .addGap(10)
                            .addComponent(searchBar)
                            .addGap(10))
                    .addComponent(scrollPane));
        }

        revalidate();

        refreshing.setVisible(false);
        reloadPluginList();
    }

	private void reloadPluginList()
	{
		if (refreshing.isVisible())
		{
			return;
		}

		refreshing.setVisible(true);
		mainPanel.removeAll();

		executor.submit(() ->
		{
			Collection<MicrobotPluginManifest> manifestCollection = microbotPluginManager.getManifestMap().values();

			Map<String, Integer> pluginCounts = Collections.emptyMap();
			try
			{
				pluginCounts = microbotPluginClient.getPluginCounts();
			}
			catch (IOException e)
			{
				log.warn("Unable to download plugin counts", e);
				SwingUtilities.invokeLater(() ->
				{
					refreshing.setVisible(false);
					mainPanel.add(new JLabel("Downloading the plugin manifest failed"));

					JButton retry = new JButton("Retry");
					retry.addActionListener(l -> reloadPluginList());
					mainPanel.add(retry);
					mainPanel.revalidate();
				});
			}

			reloadPluginList(manifestCollection, pluginCounts);
		});
	}

    private void reloadPluginList(Collection<MicrobotPluginManifest> manifest, Map<String, Integer> pluginCounts) {

        // Filter out disabled plugins before processing
        List<MicrobotPluginManifest> enabledManifest = manifest.stream()
                .filter(m -> !m.isDisable())
                .collect(Collectors.toList());

        Predicate<Plugin> isExternalPluginPredicate = plugin ->
                plugin.getClass().getAnnotation(PluginDescriptor.class).isExternal();

        List<Plugin> loadedPlugins = pluginManager.getPlugins()
                .stream()
                .filter(plugin -> !plugin.getClass().getAnnotation(PluginDescriptor.class).hidden())
                .filter(isExternalPluginPredicate)
                .collect(Collectors.toList());

        Set<String> installed = new HashSet<>(microbotPluginManager.getInstalledPlugins());

        // Pre-index manifests by internalName (lowercased) - using filtered list
        Map<String, MicrobotPluginManifest> manifestByName = enabledManifest.stream()
                .filter(m -> m.getInternalName() != null)
                .collect(Collectors.toMap(
                        m -> m.getInternalName().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (a, b) -> a // keep first on duplicates
                ));

        // Index loaded plugins by simple name (lowercased) → all instances for that name
        Map<String, Collection<Plugin>> pluginsByName = loadedPlugins.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getClass().getSimpleName().toLowerCase(Locale.ROOT),
                        LinkedHashMap::new,
                        Collectors.toCollection(LinkedHashSet::new) // stable, no dups
                ));

        // Build PluginItem list by looping over manifests
        plugins = manifestByName.entrySet().stream()
                .map(e -> {
                    String key = e.getKey();                       // lowercased internalName
                    MicrobotPluginManifest m = e.getValue();
                    String simpleName = m.getInternalName();       // original case

                    Collection<Plugin> group = pluginsByName.getOrDefault(key, Collections.emptySet());
                    int count = pluginCounts.getOrDefault(simpleName, -1);
                    boolean isInstalled = installed.contains(simpleName);

                    return new PluginItem(m, group, count, isInstalled);
                })
                .collect(Collectors.toList());


        SwingUtilities.invokeLater(() ->
        {
            if (!refreshing.isVisible()) {
                return;
            }

            refreshing.setVisible(false);
            executor.execute(MicrobotPluginHubPanel.this::filter);
        });
    }

    void filter() {
        if (refreshing.isVisible() || plugins == null) {
            return;
        }

        String query = searchBar.getText();
        boolean isSearching = query != null && !query.trim().isEmpty();
        List<PluginItem> pluginItems;
        if (isSearching) {
            pluginItems = PluginSearch.search(plugins, query);
        } else {
            pluginItems = plugins.stream()
                    .sorted(Comparator.comparing(PluginItem::isInstalled)
                            .thenComparingInt(PluginItem::getUserCount)
                            .reversed()
                            .thenComparing(p -> p.manifest.getInternalName())
                    )
                    .collect(Collectors.toList());
        }

        SwingUtilities.invokeLater(() ->
        {
            mainPanel.removeAll();
            pluginItems.forEach(mainPanel::add);
            mainPanel.revalidate();
        });
    }

    @Override
    public void onActivate() {
        revalidate();
        reloadPluginList();
        searchBar.setText("");
        searchBar.requestFocusInWindow();
    }

    @Override
    public void onDeactivate() {
        mainPanel.removeAll();
        refreshing.setVisible(false);
        plugins = null;

        synchronized (iconLoadQueue) {
            for (PluginIcon pi; (pi = iconLoadQueue.poll()) != null; ) {
                pi.loadingStarted = false;
            }
        }
    }

	@Subscribe
	private void onExternalPluginsChanged(ExternalPluginsChanged ev) {
		reloadPluginList();
	}

    // A utility class copied from the original PluginHubPanel
    private static class FixedWidthPanel extends JPanel {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
        }
    }
}
