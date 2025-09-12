package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.ui.FontManager;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

@Singleton
public class MicrobotTopLevelConfigPanel extends PluginPanel {
    private final MaterialTabGroup tabGroup;
    private final CardLayout layout;
    private final JPanel content;

    private final EventBus eventBus;
    private final MicrobotPluginListPanel pluginListPanel;
    private final MaterialTab pluginListPanelTab;

    private boolean active = false;
    private PluginPanel current;
    private boolean removeOnTabChange;

    // -- BEGIN: NEW badge ( to be removed once we migrate all plugins to Hub) --
    private final MaterialTab hubTab;
    private JPanel glassPane;
    private JLabel newBadgeOverlay;
    private Timer newBadgeTimer;
    private Component previousGlassPane;

    private void createNewBadgeOverlay() {
        if (hubTab == null) return;

        glassPane = new JPanel() {
            @Override
            public boolean contains(int x, int y) {
                return false;
            }
            @Override
            protected void paintChildren(Graphics g) {
                super.paintChildren(g);
            }
        };
        glassPane.setOpaque(false);
        glassPane.setLayout(null);

        newBadgeOverlay = new JLabel() {
            private final BufferedImage newBadge = ImageUtil.loadImageResource(MicrobotTopLevelConfigPanel.class, "NEW.png");
            private final long startTime = System.currentTimeMillis();

            @Override
            protected void paintComponent(Graphics g) {
                if (newBadge == null) {
                    System.out.println("DEBUG: newBadge image is null, cannot paint");
                    SwingUtilities.invokeLater(MicrobotTopLevelConfigPanel.this::cleanupNewBadge);
                    return;
                }

                Graphics2D g2d = (Graphics2D) g.create();
                try {
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                    double time = (System.currentTimeMillis() - startTime) % 3000;
                    double scale;

                    if (time < 300) {
                        double pulsePhase = (time / 300.0) * Math.PI;
                        double pulseAmount = Math.sin(pulsePhase) * 0.06;
                        scale = 0.7 + pulseAmount;
                    } else {
                        scale = 0.7;
                    }

                    double scaledWidth = newBadge.getWidth() * scale;
                    double scaledHeight = newBadge.getHeight() * scale;

                    double x = (getWidth() - scaledWidth) / 2.0;
                    double y = (getHeight() - scaledHeight) / 2.0;

                    g2d.rotate(Math.toRadians(20), getWidth() / 2.0, getHeight() / 2.0);
                    g2d.drawImage(newBadge, (int)x, (int)y, (int)(x + scaledWidth), (int)(y + scaledHeight),
                                  0, 0, newBadge.getWidth(), newBadge.getHeight(), null);

                } finally {
                    g2d.dispose();
                }
            }
        };

        newBadgeOverlay.setOpaque(false);
        newBadgeOverlay.setSize(32, 32);
        newBadgeOverlay.setVisible(false);
        glassPane.add(newBadgeOverlay);

        SwingUtilities.invokeLater(() -> {
            JRootPane rootPane = SwingUtilities.getRootPane(this);
            if (rootPane != null) {
                if (previousGlassPane == null) {
                    previousGlassPane = rootPane.getGlassPane();
                }
                rootPane.setGlassPane(glassPane);
                glassPane.setVisible(true);
                updateBadgePosition();
            }
        });

        newBadgeTimer = new Timer(50, e -> {
            if (hubTab != null && hubTab.isShowing() && glassPane.isVisible()) {
                updateBadgePosition();
                newBadgeOverlay.setVisible(true);
                newBadgeOverlay.repaint();
            } else {
                newBadgeOverlay.setVisible(false);
            }
        });
        newBadgeTimer.start();
    }

    private void updateBadgePosition() {
        if (newBadgeOverlay == null || hubTab == null || glassPane == null) return;

        try {
            Point topRight = SwingUtilities.convertPoint(hubTab, hubTab.getWidth(), 0, glassPane);
            int x = topRight.x - 25;
            int y = topRight.y - 12;
            newBadgeOverlay.setLocation(x, y);
        } catch (Exception ex) {
            System.out.println("DEBUG: Exception in updateBadgePosition: " + ex);
        }
    }

    private void cleanupNewBadge() {
        if (newBadgeTimer != null) {
            newBadgeTimer.stop();
            newBadgeTimer = null;
        }
        if (glassPane != null) {
            glassPane.setVisible(false);
            JRootPane rootPane = SwingUtilities.getRootPane(this);
            if (rootPane != null && previousGlassPane != null) {
                rootPane.setGlassPane(previousGlassPane);
                previousGlassPane = null;
            }
            glassPane = null;
        }
        newBadgeOverlay = null;
    }
    // -- END: NEW badge ( to be removed once we migrate all plugins to Hub) --

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
            Provider<MicrobotPluginHubPanel> microbotPluginHubPanelProvider
    ) {
        super(false);

        this.eventBus = eventBus;

        tabGroup = new MaterialTabGroup();
        tabGroup.setLayout(new GridLayout(1, 0, 7, 7));
        tabGroup.setBorder(new EmptyBorder(10, 10, 0, 10));

        content = new JPanel();
        layout = new CardLayout();
        content.setLayout(layout);

        setLayout(new BorderLayout());
        add(tabGroup, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);

        this.pluginListPanel = pluginListPanel;

        // Create text-based icons instead of using image files for better clarity
        ImageIcon installedIcon = createTextIcon("Installed", Color.YELLOW, null, 80, 32);
        ImageIcon hubIcon = createTextIcon("Plugin Hub", Color.YELLOW, null, 80, 32);

        pluginListPanelTab = addTab(pluginListPanel.getMuxer(), installedIcon, "Installed Microbot Plugins");
        hubTab = addTab(microbotPluginHubPanelProvider, hubIcon, "Microbot Hub");


        tabGroup.select(pluginListPanelTab);

        // Create NEW badge overlay after UI is initialized (remove after migrating all plugins to hub)
        SwingUtilities.invokeLater(this::createNewBadgeOverlay);
    }

    private MaterialTab addTab(PluginPanel panel, ImageIcon icon, String tooltip) {
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

    private MaterialTab addTab(Provider<? extends PluginPanel> panelProvider, ImageIcon icon, String tooltip) {
        MaterialTab mt = new MaterialTab(icon, tabGroup, null);
        mt.setToolTipText(tooltip);
        tabGroup.addTab(mt);

        mt.setOnSelectEvent(() ->
        {
            PluginPanel panel = panelProvider.get();
            content.add(tooltip, panel.getWrappedPanel());
            eventBus.register(panel);
            switchTo(tooltip, panel, true);
            return true;
        });
        return mt;
    }

    private void switchTo(String cardName, PluginPanel panel, boolean removeOnTabChange) {
        boolean doRemove = this.removeOnTabChange;
        PluginPanel prevPanel = current;
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
        // BEGIN: NEW badge readd code (remove once we migrate all plugins to hub)
        if (newBadgeTimer == null || glassPane == null) {
            SwingUtilities.invokeLater(this::createNewBadgeOverlay);
        }
        // END: NEW badge readd code (remove once we migrate all plugins to hub)
    }

    @Override
    public void onDeactivate() {
        active = false;
        current.onDeactivate();
        // BEGIN: NEW badge clean up code (remove once we migrate all plugins to hub)
        cleanupNewBadge();
        // END: NEW badge clean up code (remove once we migrate all plugins to hub)
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
