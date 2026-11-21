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

import com.google.common.base.Strings;
import com.google.common.html.HtmlEscapers;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.config.SearchablePlugin;
import net.runelite.client.plugins.microbot.externalplugins.LivePluginManager.OperationResult;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginClient;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginHealth;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import net.runelite.client.plugins.microbot.ui.search.MicrobotPluginNames;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.ui.*;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.File;
import java.awt.Desktop;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class MicrobotPluginHubPanel extends MicrobotPluginPanel {
    private static final ImageIcon MISSING_ICON;
    private static final ImageIcon HELP_ICON;
    private static final ImageIcon CONFIGURE_ICON;
    private static final Color PASTEL_GREEN = new Color(0x7CB987);
    // The Hub's update colour; the Installed list uses it for its update line too.
    static final Color PASTEL_ORANGE = new Color(0xD4A574);
    private static final Color HEALTH_RED = new Color(0xBE2828);
    private static final String SORT_CONFIG_KEY = "pluginHubSort";

    static {
        BufferedImage missingIcon = ImageUtil.loadImageResource(MicrobotPluginHubPanel.class, "pluginhub_missingicon.png");
        MISSING_ICON = new ImageIcon(missingIcon);

        BufferedImage helpIcon = ImageUtil.loadImageResource(MicrobotPluginHubPanel.class, "pluginhub_help.png");
        HELP_ICON = new ImageIcon(helpIcon);

        BufferedImage configureIcon = ImageUtil.loadImageResource(MicrobotPluginHubPanel.class, "pluginhub_configure.png");
        CONFIGURE_ICON = new ImageIcon(configureIcon);
    }

    private enum ActionState
    {
        DOWNLOAD,
        REFRESH,
        REMOVE
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
        private boolean installed;
        private MicrobotPluginManifest manifest;
        private String latestVersion;
        private VersionSelector versionSelector;
        // The border colour alone does not say what it means.
        private final JLabel stateLabel = new JLabel();

        PluginItem(MicrobotPluginManifest manifest, Collection<Plugin> loadedPlugins, int userCount, boolean installed) {
            this.manifest = manifest;
            this.userCount = userCount;
            this.installed = installed;
            this.latestVersion = manifest.getVersion();

            var currentVersion = loadedPlugins.isEmpty() ? manifest.getVersion() : loadedPlugins.iterator().next().getClass().getAnnotation(PluginDescriptor.class).version();

            keywords.addAll(MicrobotPluginHubOrder.keywords(manifest));

            if (manifest.isNewlyAdded()) {
                keywords.add("new");
                keywords.add("newly added");
            }

            String storedInstalledVersion = microbotPluginManager.getInstalledPluginVersion(manifest.getInternalName()).orElse(null);
            MicrobotPluginHealth health = microbotPluginManager.getPluginHealth(manifest,
                    installed && !Strings.isNullOrEmpty(storedInstalledVersion) ? storedInstalledVersion : currentVersion);
            keywords.addAll(healthKeywords(health));

            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setOpaque(true);

            GroupLayout layout = new GroupLayout(this);
            setLayout(layout);

            JLabel pluginName = new JLabel(manifest.getDisplayName());
            pluginName.setFont(FontManager.getRunescapeBoldFont());
            pluginName.setToolTipText(manifest.getDisplayName());
            pluginName.setHorizontalAlignment(JLabel.LEFT);
            stateLabel.setFont(FontManager.getRunescapeSmallFont());

            String[] authorsArr = manifest.getAuthors();
            String authorRaw = manifest.getAuthor();

            String authorText;
            String authorTooltip;
            if (authorsArr != null && authorsArr.length > 1) {
                authorText = "Multiple authors";
                authorTooltip = String.join(", ", authorsArr);
            } else {
                String a = authorRaw != null ? authorRaw.trim() : "";
                boolean isUnknown = a.isEmpty() || a.toLowerCase(Locale.ROOT).contains("unknown");
                authorText = isUnknown ? "Unknown" : a;
                authorTooltip = isUnknown ? "Unknown" : a;
            }
            JLabel author = new JLabel(authorText);
            author.setFont(FontManager.getRunescapeSmallFont());
            author.setToolTipText(authorTooltip);
            author.setHorizontalAlignment(JLabel.LEFT);
            author.setBorder(new EmptyBorder(0, 0, 0, 5));
            List<String> availableVersions = manifest.getAvailableVersions();
            String suggestedVersion = !Strings.isNullOrEmpty(manifest.getVersion()) ? manifest.getVersion() : currentVersion;
            if (Strings.isNullOrEmpty(suggestedVersion)) {
                suggestedVersion = "unknown";
            }
            String storedVersion = microbotPluginManager.getInstalledPluginVersion(manifest.getInternalName()).orElse(null);
            String installedVersion = installed
                    ? (!Strings.isNullOrEmpty(storedVersion) ? storedVersion : currentVersion)
                    : null;
            String initialSelectedVersion = installed ? installedVersion : null;
            this.versionSelector = new VersionSelector(
                    manifest,
                    availableVersions,
                    initialSelectedVersion,
                    suggestedVersion,
                    installed);

            String descriptionText = Strings.nullToEmpty(manifest.getDescription());

            if (!descriptionText.startsWith("<html>")) {
                descriptionText = "<html>" + HtmlEscapers.htmlEscaper().escape(descriptionText) + "</html>";
            }

            JLabel description = new JLabel(descriptionText);
            description.setVerticalAlignment(JLabel.TOP);
            description.setToolTipText(descriptionText);

            JLabel icon = new PluginIcon(Strings.nullToEmpty(manifest.getIconUrl()));
            icon.setHorizontalAlignment(JLabel.CENTER);

            JLabel badge = createHealthBadge(health);

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
                configure.addActionListener(l -> {
                    if (loadedPlugins.size() == 1) {
                        Plugin plugin = loadedPlugins.iterator().next();
                        if (pluginManager.getPluginConfigProxy(plugin) != null) {
                            topLevelConfigPanel.openConfigurationPanel(plugin);
                            return;
                        }
                    }

                    topLevelConfigPanel.openWithFilter(manifest.getInternalName());
                });
            } else {
                configure.setVisible(false);
            }

            GroupLayout.SequentialGroup bottomRow = layout.createSequentialGroup()
                    .addComponent(versionSelector, 100, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE);
            bottomRow.addComponent(help, 0, 24, 24)
                    .addComponent(configure, 0, 24, 24)
                    .addGap(5);

            layout.setHorizontalGroup(layout.createSequentialGroup()
                    .addGroup(layout.createParallelGroup()
                            .addComponent(badge, GroupLayout.Alignment.TRAILING)
                            .addComponent(icon, ICON_WIDTH, ICON_WIDTH, ICON_WIDTH))
                    .addGap(5)
                    .addGroup(layout.createParallelGroup()
                            .addGroup(layout.createSequentialGroup()
                                    .addComponent(pluginName, 0, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(author))
                            .addComponent(stateLabel)
                            .addComponent(description, 0, 0, Short.MAX_VALUE)
                            .addGroup(bottomRow)));

            int lineHeight = description.getFontMetrics(description.getFont()).getHeight();
            GroupLayout.ParallelGroup bottomRowVertical = layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(versionSelector, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT)
                    .addComponent(help, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT)
                    .addComponent(configure, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT, BOTTOM_LINE_HEIGHT);

            layout.setVerticalGroup(layout.createParallelGroup()
                    .addComponent(badge, GroupLayout.Alignment.TRAILING)
                    .addComponent(icon, HEIGHT, GroupLayout.DEFAULT_SIZE, HEIGHT + lineHeight)
                    .addGroup(layout.createSequentialGroup()
                            .addGap(5)
                            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                    .addComponent(pluginName)
                                    .addComponent(author))
                            .addComponent(stateLabel)
                            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
                            .addComponent(description, lineHeight * 2, lineHeight * 2, lineHeight * 2)
                            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
                            .addGroup(bottomRowVertical)
                            .addGap(5)));

            updateBorder(initialSelectedVersion);

            MouseAdapter doubleClickInstall = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && !PluginItem.this.installed) {
                        versionSelector.installSelectedVersion(latestVersion);
                    }
                }
            };
            addMouseListener(doubleClickInstall);
            icon.addMouseListener(doubleClickInstall);
            pluginName.addMouseListener(doubleClickInstall);
            author.addMouseListener(doubleClickInstall);
            description.addMouseListener(doubleClickInstall);
            badge.addMouseListener(doubleClickInstall);
        }

        private List<String> healthKeywords(MicrobotPluginHealth health) {
            switch (health.getState()) {
                case DISABLED:
                    return Arrays.asList("disabled", "broken");
                case BROKEN:
                    return Collections.singletonList("broken");
                case INCOMPATIBLE:
                    return Collections.singletonList("incompatible");
                case UNVERIFIED:
                    return Collections.singletonList("unverified");
                case VERIFIED:
                    return Collections.singletonList("verified");
                default:
                    return Collections.emptyList();
            }
        }

        private JLabel createHealthBadge(MicrobotPluginHealth health) {
            JLabel badge = new JLabel();
            if (health.getState() == MicrobotPluginHealth.State.UNKNOWN) {
                return badge;
            }

            badge.setText(health.isBlocking() ? "!" : health.isWarning() ? "?" : "\u2713");
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 14f));
            badge.setForeground(health.isBlocking() ? HEALTH_RED : health.isWarning() ? PASTEL_ORANGE : PASTEL_GREEN);
            badge.setToolTipText(healthHtml(health));
            badge.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            badge.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 1 && SwingUtilities.isLeftMouseButton(e)) {
                        showHealthDetails(MicrobotPluginHubPanel.this, manifest, health);
                    }
                }
            });
            return badge;
        }

        @Override
        public String getSearchableName() {
            return manifest.getDisplayName();
        }

		@Override
		public int installs() {
			return userCount;
		}

		private void updateBorder(String selectedVersion)
		{
			boolean outdated = installed
				&& !Strings.isNullOrEmpty(latestVersion)
				&& MicrobotPluginManager.isNewerVersion(latestVersion, selectedVersion)
				&& !microbotPluginManager.getPluginHealth(manifest, latestVersion).isBlocking();

			MicrobotPluginHubOrder.State state = MicrobotPluginHubOrder.State.of(installed,
				installed && microbotPluginManager.getPluginHealth(manifest, selectedVersion).isBlocking(), outdated);
			Color color = state == MicrobotPluginHubOrder.State.BLOCKED ? HEALTH_RED
				: state == MicrobotPluginHubOrder.State.UPDATE ? PASTEL_ORANGE : PASTEL_GREEN;
			setBorder(state == MicrobotPluginHubOrder.State.NONE
				? BorderFactory.createEmptyBorder(2, 2, 2, 2)
				: BorderFactory.createLineBorder(color, 2));
			stateLabel.setText(state.text(microbotPluginManager.getPluginHealth(manifest, selectedVersion), outdated));
			stateLabel.setForeground(color);
			stateLabel.setVisible(state != MicrobotPluginHubOrder.State.NONE);
		}

		private void setInstalled(boolean installed, String selectedVersion)
		{
			this.installed = installed;
			updateBorder(selectedVersion);
		}

		private final class VersionSelector extends JPanel
		{
			private final MicrobotPluginManifest manifest;
			private final JComboBox<String> comboBox;
			private final JButton actionButton;
			private boolean installed;
			private String installedVersion;

			private VersionSelector(MicrobotPluginManifest manifest, List<String> availableVersions,
				String initialSelectedVersion, String suggestedVersion, boolean installed)
			{
				this.manifest = manifest;
				this.installed = installed;
				this.installedVersion = installed ? initialSelectedVersion : null;

				setLayout(new BorderLayout(2, 0));
				setOpaque(false);

				List<String> versions = buildVersionList(availableVersions, suggestedVersion, initialSelectedVersion);
				comboBox = new JComboBox<>(versions.toArray(new String[0]));
				comboBox.setFont(FontManager.getRunescapeSmallFont());
				comboBox.setFocusable(false);

				if (!Strings.isNullOrEmpty(initialSelectedVersion) && versions.contains(initialSelectedVersion))
				{
					comboBox.setSelectedItem(initialSelectedVersion);
				}
				else if (!Strings.isNullOrEmpty(suggestedVersion) && versions.contains(suggestedVersion))
				{
					comboBox.setSelectedItem(suggestedVersion);
				}

				comboBox.addActionListener(e -> {
					if (e.getActionCommand().equals("comboBoxChanged"))
					{
						refreshActionButton();
					}
				});

				actionButton = new JButton();
				actionButton.setFont(actionButton.getFont().deriveFont(Font.BOLD, 16f));
				actionButton.setPreferredSize(new Dimension(24, 16));
				actionButton.setMargin(new Insets(0, 0, 0, 0));
				actionButton.setFocusPainted(false);
				SwingUtil.removeButtonDecorations(actionButton);
				actionButton.setBorder(null);
				actionButton.addActionListener(e -> handleActionButtonClick());

				add(comboBox, BorderLayout.CENTER);
				add(actionButton, BorderLayout.EAST);

				refreshActionButton();
			}

			private ActionState determineState()
			{
				String selected = (String) comboBox.getSelectedItem();
				if (!installed)
				{
					return ActionState.DOWNLOAD;
				}
				if (!Strings.isNullOrEmpty(selected) && !selected.equals(installedVersion))
				{
					return ActionState.DOWNLOAD;
				}
				if (!Strings.isNullOrEmpty(latestVersion) && MicrobotPluginManager.isNewerVersion(latestVersion, installedVersion)
					&& !microbotPluginManager.getPluginHealth(manifest, latestVersion).isBlocking())
				{
					return ActionState.REFRESH;
				}
				return ActionState.REMOVE;
			}

			private void refreshActionButton()
			{
				boolean pending = pendingOperations.contains(manifest.getInternalName());
				comboBox.setEnabled(!pending);
				actionButton.setEnabled(!pending);
				if (pending)
				{
					actionButton.setText("...");
					actionButton.setToolTipText("Plugin operation in progress");
					return;
				}
				ActionState state = determineState();
				String selected = (String) comboBox.getSelectedItem();
				switch (state)
				{
					case DOWNLOAD:
						actionButton.setText("\u2B07");
						actionButton.setForeground(PASTEL_GREEN);
						actionButton.setToolTipText(Strings.isNullOrEmpty(selected)
							? "Download plugin"
							: "Download version " + selected);
						break;
					case REFRESH:
						actionButton.setText("\u27F3");
						actionButton.setForeground(PASTEL_ORANGE);
						actionButton.setToolTipText("Update to latest version (" + latestVersion + ")");
						break;
					case REMOVE:
					default:
						actionButton.setText("\u2715");
						actionButton.setForeground(new Color(0xBE2828));
						actionButton.setToolTipText("Remove plugin");
						break;
				}
			}

			private void handleActionButtonClick()
			{
				if (pendingOperations.contains(manifest.getInternalName()))
				{
					return;
				}
				ActionState state = determineState();
				String selected = (String) comboBox.getSelectedItem();
				switch (state)
				{
					case DOWNLOAD:
						if (!Strings.isNullOrEmpty(selected))
						{
							installSelectedVersion(selected);
						}
						break;
					case REFRESH:
						updateSelectedVersion(latestVersion);
						break;
					case REMOVE:
						removePlugin();
						break;
				}
			}

			private List<String> buildVersionList(List<String> availableVersions, String suggested, String selected)
			{
				LinkedHashSet<String> unique = new LinkedHashSet<>();
				if (!Strings.isNullOrEmpty(selected))
				{
					unique.add(selected);
				}
				if (!Strings.isNullOrEmpty(suggested))
				{
					unique.add(suggested);
				}
				if (availableVersions != null)
				{
					for (String version : availableVersions)
					{
						if (!Strings.isNullOrEmpty(version))
						{
							unique.add(version);
						}
					}
				}
				return new ArrayList<>(unique);
			}

			private void installSelectedVersion(String version)
			{
				if (!confirmInstall(MicrobotPluginHubPanel.this, microbotPluginManager, manifest, version))
				{
					return;
				}
				runOperation(installed ? "update" : "install", () -> installed
					? microbotPluginManager.updatePlugin(manifest, version)
					: microbotPluginManager.installPlugin(manifest, version), () -> {
					installed = true;
					installedVersion = version;
					setInstalled(true, version);
					comboBox.setSelectedItem(version);
				});
			}

			private void updateSelectedVersion(String version)
			{
				if (!confirmInstall(MicrobotPluginHubPanel.this, microbotPluginManager, manifest, version))
				{
					return;
				}
				runOperation("update", () -> microbotPluginManager.updatePlugin(manifest, version), () -> {
					installedVersion = version;
					comboBox.setSelectedItem(version);
					updateBorder(version);
				});
			}

			private void removePlugin()
			{
				runOperation("remove", () -> microbotPluginManager.removePlugin(manifest), () -> {
					installed = false;
					installedVersion = null;
					setInstalled(false, null);
				});
			}

			private void runOperation(String action, Supplier<CompletableFuture<OperationResult>> operation, Runnable onSuccess)
			{
				if (!pendingOperations.add(manifest.getInternalName()))
				{
					return;
				}
				refreshActionButton();
				try
				{
					operation.get().whenComplete((result, error) -> SwingUtilities.invokeLater(
						() -> finishOperation(action, result, error, onSuccess)));
				}
				catch (RuntimeException error)
				{
					finishOperation(action, null, error, onSuccess);
				}
			}

			private String pluginName()
			{
				String name = manifest.getDisplayName();
				return name == null || name.trim().isEmpty() ? manifest.getInternalName() : name;
			}

			private void finishOperation(String action, OperationResult result, Throwable error, Runnable onSuccess)
			{
				pendingOperations.remove(manifest.getInternalName());
				if (error == null && result != null && result.isSuccess())
				{
					onSuccess.run();
					if (result.isNeedsRestart())
					{
						JOptionPane.showMessageDialog(MicrobotPluginHubPanel.this, result.getMessage(),
							"Restart needed", JOptionPane.INFORMATION_MESSAGE);
					}
				}
				else
				{
					String message = result == null ? "The plugin operation failed." : result.getMessage();
					if (error != null)
					{
						Throwable cause = error.getCause() == null ? error : error.getCause();
						if (cause.getMessage() != null)
						{
							message = cause.getMessage();
						}
					}
					JTextArea messageArea = new JTextArea(message, 4, 42);
					messageArea.setEditable(false);
					messageArea.setLineWrap(true);
					messageArea.setWrapStyleWord(true);
					messageArea.setOpaque(false);
					JOptionPane.showMessageDialog(MicrobotPluginHubPanel.this, messageArea,
						"Couldn't " + action + " " + pluginName(), JOptionPane.ERROR_MESSAGE);
				}
				refreshActionButton();
				MicrobotPluginHubPanel.this.reloadPluginList();
			}
		}
	}

    private final MicrobotTopLevelConfigPanel topLevelConfigPanel;
    private final MicrobotPluginManager microbotPluginManager;
    private final PluginManager pluginManager;
    private final MicrobotPluginClient microbotPluginClient;
    private final ScheduledExecutorService executor;

    private final Deque<PluginIcon> iconLoadQueue = new ArrayDeque<>();
    private final Set<String> pendingOperations = new HashSet<>();

    private final IconTextField searchBar;
    private final JLabel refreshing;
    private final JPanel mainPanel;
    private List<PluginItem> plugins = null;
    private boolean reloadQueued;
    private String pendingSearch = "";
    private final ConfigManager configManager;
    private final JPopupMenu sortMenu = new JPopupMenu();
    private final Map<MicrobotPluginHubOrder.Sort, JMenuItem> sortItems = new EnumMap<>(MicrobotPluginHubOrder.Sort.class);
    private MicrobotPluginHubOrder.Sort sort;

    @Inject
    MicrobotPluginHubPanel(
            MicrobotTopLevelConfigPanel topLevelConfigPanel,
            MicrobotPluginManager microbotPluginManager,
            PluginManager pluginManager,
            MicrobotPluginClient microbotPluginClient,
            ScheduledExecutorService executor,
            ConfigManager configManager) {
        super(false);
        this.topLevelConfigPanel = topLevelConfigPanel;
        this.microbotPluginManager = microbotPluginManager;
        this.pluginManager = pluginManager;
        this.microbotPluginClient = microbotPluginClient;
        this.executor = executor;
        this.configManager = configManager;
        this.sort = MicrobotPluginHubOrder.Sort.parse(configManager.getConfiguration(MicrobotPluginListPanel.RUNELITE_GROUP_NAME, SORT_CONFIG_KEY));

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
        searchBar.getSuggestionListModel().addElement(MicrobotPluginHubOrder.NEWLY_ADDED_FILTER_QUERY);
        searchBar.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }
        });

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
                    .addGap(7)
                    .addComponent(mainPanel, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(refreshing)
                    .addGap(0, 0, 0x7000));

            layout.setHorizontalGroup(layout.createParallelGroup()
                    .addComponent(mainPanel)
                    .addComponent(refreshing, 0, Short.MAX_VALUE, Short.MAX_VALUE));
        }

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // Can't use Short.MAX_VALUE like the docs say because of JDK-8079640
        scrollPane.setPreferredSize(new Dimension(0x7000, 0x7000));
        scrollPane.setViewportView(mainPanelWrapper);

        // The search field's own preferred width would push the row past the sidebar's 235px.
        searchBar.setPreferredSize(new Dimension(0, 30));
        JPanel searchRow = new JPanel(new BorderLayout(5, 0));
        searchRow.setOpaque(false);
        searchRow.add(searchBar, BorderLayout.CENTER);
        searchRow.add(createSortButton(), BorderLayout.EAST);

        {
            GroupLayout layout = new GroupLayout(this);
            setLayout(layout);

            layout.setVerticalGroup(layout.createSequentialGroup()
                    .addGap(10)
                    .addComponent(searchRow, 30, 30, 30)
                    .addGap(10)
                    .addComponent(scrollPane));

            layout.setHorizontalGroup(layout.createParallelGroup()
                    .addGroup(layout.createSequentialGroup()
                            .addGap(10)
                            .addComponent(searchRow)
                            .addGap(10))
                    .addComponent(scrollPane));
        }

        revalidate();

        refreshing.setVisible(false);
        reloadPluginList();
    }

    enum InstallBlock { NONE, HEALTH, CLIENT_VERSION }

    static InstallBlock installBlock(MicrobotPluginHealth health, boolean clientCompatible) {
        if (health.getState() == MicrobotPluginHealth.State.DISABLED || health.getState() == MicrobotPluginHealth.State.BROKEN) {
            return InstallBlock.HEALTH;
        }
        return clientCompatible ? InstallBlock.NONE : InstallBlock.CLIENT_VERSION;
    }

    /** Every Hub install asks this first, here and from the Installed list: it explains and refuses a broken version or a too-old client. */
    static boolean confirmInstall(Component parent, MicrobotPluginManager manager, MicrobotPluginManifest manifest, String version) {
        MicrobotPluginHealth health = manager.getPluginHealth(manifest, version);
        switch (installBlock(health, Rs2UiHelper.isClientVersionCompatible(manifest.getMinClientVersion()))) {
            case HEALTH:
                showHealthDetails(parent, manifest, health);
                return false;
            case CLIENT_VERSION:
                showClientVersionDialog(parent, manifest);
                return false;
            default:
                return true;
        }
    }

    private static void showClientVersionDialog(Component parent, MicrobotPluginManifest manifest) {
        String current = RuneLiteProperties.getMicrobotVersion();
        String required = manifest.getMinClientVersion();
        String message = String.format(
                "Cannot install plugin '%s'.\n\nRequired client version: %s\nCurrent client version: %s\n\nPlease update your Microbot client to use this plugin.",
                manifest.getDisplayName(),
                required != null ? required : "Unknown",
                current != null ? current : "Unknown"
        );

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Version Incompatibility", Dialog.ModalityType.APPLICATION_MODAL);
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
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private JButton createSortButton() {
        JMenuItem openFolder = new JMenuItem("Open plugin folder...");
        openFolder.setToolTipText("Open " + microbotPluginManager.getPluginDirectory().getAbsolutePath());
        openFolder.addActionListener(e -> {
            openFolder.setToolTipText("Open " + microbotPluginManager.getPluginDirectory().getAbsolutePath());
            openMicrobotPluginFolder();
        });
        JButton button = MicrobotPluginListPanel.createSortButton("Sort, or open your plugin folder", sortMenu, sortItems, MicrobotPluginHubOrder.Sort.values(),
                MicrobotPluginHubOrder.Sort::getLabel, sort, option -> {
                    sort = option;
                    configManager.setConfiguration(MicrobotPluginListPanel.RUNELITE_GROUP_NAME, SORT_CONFIG_KEY, option.name());
                    filter();
                }, openFolder);
        // A search lists by best match, so a sort choice only shows once the search box is empty.
        sortItems.values().forEach(item -> item.setToolTipText("While searching, results are listed by best match"));
        return button;
    }

    private static String healthHtml(MicrobotPluginHealth health) {
        StringBuilder html = new StringBuilder("<html>");
        for (String line : health.getDetails(RuneLiteProperties.getMicrobotVersion())) {
            html.append(HtmlEscapers.htmlEscaper().escape(line)).append("<br>");
        }
        if (health.getState() == MicrobotPluginHealth.State.DISABLED) {
            html.append("<br>This plugin cannot be installed. You can still remove it.");
        } else if (health.getState() == MicrobotPluginHealth.State.BROKEN) {
            html.append("<br>This version cannot be installed or started. Choose an unaffected version or remove it.");
        }
        return html.append("</html>").toString();
    }

    private static void showHealthDetails(Component parent, MicrobotPluginManifest manifest, MicrobotPluginHealth health) {
        String trackingUrl = health.getTrackingUrl();
        Object[] options = trackingUrl == null ? new Object[]{"OK"} : new Object[]{"Open tracking link", "OK"};
        int choice = JOptionPane.showOptionDialog(
                parent,
                healthHtml(health),
                manifest.getDisplayName() + " - " + health.getLabel(),
                JOptionPane.DEFAULT_OPTION,
                health.isBlocking() ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[options.length - 1]);
        if (trackingUrl != null && choice == 0) {
            LinkBrowser.browse(trackingUrl);
        }
    }

    private void openMicrobotPluginFolder() {
        File pluginDirectory = microbotPluginManager.getPluginDirectory();
        if (!pluginDirectory.exists() && !pluginDirectory.mkdirs()) {
            log.warn("Unable to create microbot plugin directory at {}", pluginDirectory.getAbsolutePath());
            return;
        }

        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                log.warn("Desktop browsing not supported; plugin folder: {}", pluginDirectory.getAbsolutePath());
                return;
            }
            Desktop.getDesktop().open(pluginDirectory);
        } catch (Exception ex) {
            log.warn("Failed to open microbot plugin folder {}", pluginDirectory.getAbsolutePath(), ex);
        }
    }

    private void reloadPluginList() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::reloadPluginList);
            return;
        }
        if (refreshing.isVisible()) {
            reloadQueued = true;
            return;
        }

        refreshing.setVisible(true);
        mainPanel.removeAll();

        executor.submit(() ->
        {
            Collection<MicrobotPluginManifest> manifestCollection = new ArrayList<>(microbotPluginManager.getManifestMap().values());
            if (manifestCollection.isEmpty()) {
                SwingUtilities.invokeLater(this::showManifestFailure);
                return;
            }

            Map<String, Integer> pluginCounts = Collections.emptyMap();
            try {
                pluginCounts = microbotPluginClient.getPluginCounts();
            } catch (IOException e) {
                log.warn("Unable to download plugin counts", e);
            }

            Map<String, Integer> counts = pluginCounts;
            SwingUtilities.invokeLater(() -> reloadPluginList(manifestCollection, counts));
        });
    }

    private void showManifestFailure() {
        refreshing.setVisible(false);
        reloadQueued = false;
        mainPanel.removeAll();
        JLabel failure = new JLabel("<html>Couldn't load the Plugin Hub.<br>Check your connection, then press Retry.</html>");
        mainPanel.add(failure);
        JButton retry = new JButton("Retry");
        retry.addActionListener(l -> {
            retry.setEnabled(false);
            failure.setText("Retrying...");
            executor.submit(() -> {
                microbotPluginManager.init();
                reloadPluginList();
            });
        });
        mainPanel.add(retry);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private void reloadPluginList(Collection<MicrobotPluginManifest> manifest, Map<String, Integer> pluginCounts) {

        Set<String> healthBlocked = microbotPluginManager.getBlockedPlugins().keySet().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> installedExternal = microbotPluginManager.getInstalledPlugins().stream()
                .map(p -> p.getClass().getSimpleName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        // Filter out disabled plugins before processing
        List<MicrobotPluginManifest> enabledManifest = manifest.stream()
                .filter(m -> !m.isDisable() || (m.getInternalName() != null
                        && (installedExternal.contains(m.getInternalName().toLowerCase(Locale.ROOT))
                        || healthBlocked.contains(m.getInternalName().toLowerCase(Locale.ROOT)))))
                .collect(Collectors.toList());

        Predicate<Plugin> isExternalPluginPredicate = plugin ->
                plugin.getClass().getAnnotation(PluginDescriptor.class).isExternal();

        List<Plugin> loadedPlugins = pluginManager.getPlugins()
                .stream()
                .filter(plugin -> !plugin.getClass().getAnnotation(PluginDescriptor.class).hidden())
                .filter(isExternalPluginPredicate)
                .collect(Collectors.toList());

        List<Plugin> installed = new ArrayList<>(microbotPluginManager.getInstalledPlugins());

        // Pre-index manifests by internalName (lowercased) - using filtered list
        Map<String, MicrobotPluginManifest> manifestByName = enabledManifest.stream()
                .filter(m -> m.getInternalName() != null)
                .collect(Collectors.toMap(
                        m -> m.getInternalName().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (a, b) -> a
                ));

        // Index loaded plugins by simple name (lowercased) - all instances for that name
        Map<String, Collection<Plugin>> pluginsByName = loadedPlugins.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getClass().getSimpleName().toLowerCase(Locale.ROOT),
                        LinkedHashMap::new,
                        Collectors.toCollection(LinkedHashSet::new)
                ));

        Set<String> installedNames = installed.stream()
                .map(im -> im.getClass().getSimpleName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        installedNames.addAll(healthBlocked);

        // Build PluginItem list by looping over manifests
        plugins = manifestByName.entrySet().stream()
                .map(e -> {
                    String key = e.getKey();
                    MicrobotPluginManifest m = e.getValue();
                    String simpleName = m.getInternalName();

                    Collection<Plugin> group = pluginsByName.getOrDefault(key, Collections.emptySet());
                    int count = pluginCounts.getOrDefault(simpleName, -1);
                    boolean isInstalled = simpleName != null && installedNames.contains(simpleName.toLowerCase(Locale.ROOT));

                    return new PluginItem(m, group, count, isInstalled);
                })
                .collect(Collectors.toList());


        SwingUtilities.invokeLater(() ->
        {
            if (!refreshing.isVisible()) {
                return;
            }

            refreshing.setVisible(false);
            filter();
            if (reloadQueued) {
                reloadQueued = false;
                reloadPluginList();
            }
        });
    }

    void filter() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::filter);
            return;
        }
        if (refreshing.isVisible() || plugins == null) {
            return;
        }

        List<PluginItem> pluginItems = MicrobotPluginHubOrder.order(
                plugins,
                searchBar.getText(),
                sort,
                plugin -> plugin.manifest,
                PluginItem::isInstalled,
                PluginItem::getUserCount);

        mainPanel.removeAll();
        pluginItems.forEach(mainPanel::add);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    /** Opens the Hub on {@code query}: activation clears the search, so the query waits for the next one. */
    void search(String query) {
        pendingSearch = query;
        searchBar.setText(query);
    }

    @Override
    public void onActivate() {
        revalidate();
        // Profile changes made on another tab never reach this panel, which is unregistered while hidden.
        readSort();
        reloadPluginList();
        searchBar.setText(pendingSearch);
        pendingSearch = "";
        searchBar.requestFocusInWindow();
    }

    @Override
    public void onDeactivate() {
        mainPanel.removeAll();
        refreshing.setVisible(false);
        plugins = null;
        reloadQueued = false;

        synchronized (iconLoadQueue) {
            for (PluginIcon pi; (pi = iconLoadQueue.poll()) != null; ) {
                pi.loadingStarted = false;
            }
        }
    }

    @Subscribe
    void onProfileChanged(ProfileChanged ev) {
        SwingUtilities.invokeLater(() -> {
            readSort();
            filter();
        });
    }

    private void readSort() {
        sort = MicrobotPluginHubOrder.Sort.parse(configManager.getConfiguration(MicrobotPluginListPanel.RUNELITE_GROUP_NAME, SORT_CONFIG_KEY));
        sortItems.get(sort).setSelected(true);
    }

    @Subscribe
    private void onExternalPluginsChanged(ExternalPluginsChanged ev) {
        SwingUtilities.invokeLater(this::reloadPluginList);
    }

    // A utility class copied from the original PluginHubPanel
    private static class FixedWidthPanel extends JPanel {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
        }
    }
}
