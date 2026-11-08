package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.RuneLiteProperties;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.config.TopLevelConfigPanel;
import net.runelite.client.plugins.microbot.recovery.StartupRecovery;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class MicrobotTopLevelConfigPanel extends PluginPanel {
    private final MaterialTabGroup tabGroup;
    private final CardLayout layout;
    private final JPanel content;

    private final EventBus eventBus;
    private final MicrobotCloudPanel cloudPanel;
    private final MicrobotHelpMenu helpMenu;
    private final MicrobotPluginListPanel pluginListPanel;
    private final MaterialTab pluginListPanelTab;
    private final MaterialTab profilePanelTab;
    private final MaterialTab pluginHubTab;

    private boolean active = false;
    private MicrobotPluginPanel current;
    private boolean removeOnTabChange;

    /**
     * Creates a simple text-based icon for tabs.
     * @param text {@link String} Text to display
     * @param textColor {@link Color} Color of the text
     * @param backgroundColor {@link Color} Background color (can be null for transparent)
     * @param width Width of the icon
     * @param height  Height of the icon
     * @return ImageIcon {@link ImageIcon} with rendered text
     */
    private ImageIcon createTextIcon(String text, Color textColor, Color backgroundColor, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Enable antialiasing for smoother text
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Set background (optional, can be transparent)
        if (backgroundColor != null) {
            g2d.setColor(backgroundColor);
            g2d.fillRect(0, 0, width, height);
        }

        // Set font and text color
        g2d.setFont(FontManager.getRunescapeBoldFont());
        g2d.setColor(textColor);

        // Calculate text position to center it
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getAscent();

        int x = (width - textWidth) / 2;
        int y = (height - textHeight) / 2 + textHeight;

        g2d.drawString(text, x, y);
        g2d.dispose();

        return new ImageIcon(image);
    }

    @Inject
    MicrobotTopLevelConfigPanel(
            EventBus eventBus,
            MicrobotPluginListPanel pluginListPanel,
            MicrobotProfilePanel profilePanel,
            Provider<MicrobotPluginHubPanel> microbotPluginHubPanelProvider,
            MicrobotCloudPanel cloudPanel,
            MicrobotHelpMenu helpMenu
    ) {
        super(false);

        this.eventBus = eventBus;
        this.cloudPanel = cloudPanel;
        this.helpMenu = helpMenu;

        tabGroup = new MaterialTabGroup();
        tabGroup.setLayout(new GridLayout(1, 0, 7, 7));
        tabGroup.setBorder(new EmptyBorder(10, 10, 0, 10));

        content = new JPanel();
        layout = new CardLayout();
        content.setLayout(layout);

        setLayout(new BorderLayout());
        add(tabGroup, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        add(buildVersionFooter(), BorderLayout.SOUTH);

        this.pluginListPanel = pluginListPanel;

        // Create text-based icons instead of using image files for better clarity
        ImageIcon installedIcon = createTextIcon("Installed", Color.YELLOW, null, 80, 32);
        ImageIcon hubIcon = createTextIcon("Plugin Hub", Color.YELLOW, null, 80, 32);

        pluginListPanelTab = addTab(pluginListPanel.getMuxer(), installedIcon, "Installed Microbot Plugins");
        profilePanelTab = addTab(profilePanel, "profile_icon.png", "Profiles");
        pluginHubTab = addTab(microbotPluginHubPanelProvider, hubIcon, "Microbot Hub");
        pluginListPanel.setOpenHub(query -> {
            microbotPluginHubPanelProvider.get().search(query);
            tabGroup.select(pluginHubTab);
        });

        tabGroup.select(pluginListPanelTab);
    }

    private JPanel buildVersionFooter() {
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBorder(new EmptyBorder(0, 0, 4, 0));
        footer.add(cloudPanel);

        String version = RuneLiteProperties.getMicrobotVersion();
        JLabel versionLabel = new JLabel(version == null || version.isBlank() ? "microbot" : "microbot v" + version, SwingConstants.CENTER);
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        versionLabel.setFont(FontManager.getRunescapeSmallFont());
        versionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        versionLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
        footer.add(versionLabel);

        if (StartupRecovery.mode().isSafeMode()) {
            footer.add(buildSafeModeNotice());
        }

        JLabel nextStart = new JLabel("Safe mode is on for the next start", SwingConstants.CENTER);
        nextStart.setAlignmentX(Component.CENTER_ALIGNMENT);
        nextStart.setFont(FontManager.getRunescapeSmallFont());
        nextStart.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
        nextStart.setBorder(new EmptyBorder(2, 6, 0, 6));
        nextStart.setVisible(StartupRecovery.nextStartSafeMode());
        helpMenu.onSafeModeChanged(() -> nextStart.setVisible(StartupRecovery.nextStartSafeMode()));
        footer.add(nextStart);

        String proxy = ClientUI.proxyMessage;
        if (proxy != null && !proxy.isBlank()) {
            JLabel proxyLabel = new JLabel(proxy, SwingConstants.CENTER);
            proxyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            proxyLabel.setFont(FontManager.getRunescapeSmallFont());
            proxyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            footer.add(proxyLabel);
        }

        return footer;
    }

    private JLabel buildSafeModeNotice() {
        String text = StartupRecovery.mode().isTemporary()
                ? "<html><center>Safe mode: external plugins are off for this session. Your plugins and settings are kept. Restart to return to normal.</center></html>"
                : "<html><center>Safe mode (--safe-mode): external plugins are off. Remove the launch option to return to normal.</center></html>";
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
        label.setBorder(new EmptyBorder(2, 6, 2, 6));
        List<String> previous = StartupRecovery.previousSessionSummary();
        if (!previous.isEmpty()) {
            label.setToolTipText("<html>Previous session:<br>" + previous.stream()
                    .map(line -> line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                    .collect(Collectors.joining("<br>")) + "</html>");
        }
        return label;
    }

    private MaterialTab addTab(MicrobotPluginPanel panel, ImageIcon icon, String tooltip) {
        MaterialTab mt = new MaterialTab(icon, tabGroup, null);
        mt.setToolTipText(tooltip);
        tabGroup.addTab(mt);

        content.add(tooltip, panel.getWrappedPanel()); // Use tooltip as unique key instead of image name
        eventBus.register(panel);

        mt.setOnSelectEvent(() ->
        {
            switchTo(tooltip, panel, false);
            return true;
        });
        return mt;
    }

    private MaterialTab addTab(MicrobotPluginPanel panel, String image, String tooltip)
    {
        MaterialTab mt = new MaterialTab(
                new ImageIcon(ImageUtil.loadImageResource(TopLevelConfigPanel.class, image)),
                tabGroup, null);
        mt.setToolTipText(tooltip);
        tabGroup.addTab(mt);

        content.add(image, panel.getWrappedPanel());
        eventBus.register(panel);

        mt.setOnSelectEvent(() ->
        {
            switchTo(image, panel, false);
            return true;
        });
        return mt;
    }

    private MaterialTab addTab(Provider<? extends MicrobotPluginPanel> panelProvider, ImageIcon icon, String tooltip) {
        MaterialTab mt = new MaterialTab(icon, tabGroup, null);
        mt.setToolTipText(tooltip);
        tabGroup.addTab(mt);

        mt.setOnSelectEvent(() ->
        {
            MicrobotPluginPanel panel = panelProvider.get();
            content.add(tooltip, panel.getWrappedPanel());
            eventBus.register(panel);
            switchTo(tooltip, panel, true);
            return true;
        });
        return mt;
    }

    private void switchTo(String cardName, MicrobotPluginPanel panel, boolean removeOnTabChange) {
        boolean doRemove = this.removeOnTabChange;
        MicrobotPluginPanel prevPanel = current;
        if (active) {
            prevPanel.onDeactivate();
            panel.onActivate();
        }

        current = panel;
        this.removeOnTabChange = removeOnTabChange;

        layout.show(content, cardName);

        if (doRemove) {
            content.remove(prevPanel.getWrappedPanel());
            eventBus.unregister(prevPanel);
        }

        content.revalidate();
    }

    @Override
    public void onActivate() {
        active = true;
        current.onActivate();
        cloudPanel.setActive(true);
    }

    @Override
    public void onDeactivate() {
        active = false;
        current.onDeactivate();
        cloudPanel.setActive(false);
    }

    Component getProfilesTab() {
        return profilePanelTab;
    }

    Component getPluginHubTab() {
        return pluginHubTab;
    }

    Component getTabContent() {
        return content;
    }

    void showInstalledTab() {
        tabGroup.select(pluginListPanelTab);
        pluginListPanel.getMuxer().pushState(pluginListPanel);
    }

    public void openConfigurationPanel(String name) {
        tabGroup.select(pluginListPanelTab);
        pluginListPanel.openConfigurationPanel(name);
    }

    public void openConfigurationPanel(Plugin plugin) {
        tabGroup.select(pluginListPanelTab);
        pluginListPanel.openConfigurationPanel(plugin);
    }

    public void openWithFilter(String filter) {
        tabGroup.select(pluginListPanelTab);
        pluginListPanel.openWithFilter(filter);
    }
}
