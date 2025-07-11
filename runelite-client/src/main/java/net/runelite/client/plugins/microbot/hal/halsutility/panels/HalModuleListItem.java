package net.runelite.client.plugins.microbot.hal.halsutility.panels;

import lombok.Getter;
import net.runelite.client.plugins.config.SearchablePlugin;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalSubPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class HalModuleListItem extends JPanel implements SearchablePlugin
{
    private static final ImageIcon ON_STAR;
    private static final ImageIcon OFF_STAR;

    private final HalPanel halPanel;
    private final HalSubPlugin module;

    @Getter
    private final List<String> keywords = new ArrayList<>();

    private final JToggleButton pinButton;
    private final HalPluginToggleButton onOffToggle;

    static
    {
        BufferedImage onStar = ImageUtil.loadImageResource(HalConfigPanel.class, "star_on.png");
        ON_STAR = new ImageIcon(onStar);

        BufferedImage offStar = ImageUtil.luminanceScale(
            ImageUtil.grayscaleImage(onStar),
            0.77f
        );
        OFF_STAR = new ImageIcon(offStar);
    }

    HalModuleListItem(HalPanel halPanel, HalSubPlugin module)
    {
        this.halPanel = halPanel;
        this.module = module;

        // Add keywords for search functionality
        Collections.addAll(keywords, module.getDisplayName().toLowerCase().split(" "));
        Collections.addAll(keywords, module.getCategory().name().toLowerCase().split(" "));
        keywords.add("module");
        keywords.add("hal");

        setLayout(new BorderLayout(3, 0));
        setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 20));

        JLabel nameLabel = new JLabel(module.getDisplayName());
        nameLabel.setForeground(getCategoryColor(module.getCategory()));

        // Add tooltip with description
        nameLabel.setToolTipText("<html>" + module.getDisplayName() + ":<br>" + 
            module.getCategory().name() + " module</html>");

        // Star pin button (left side)
        pinButton = new JToggleButton(OFF_STAR);
        pinButton.setSelectedIcon(ON_STAR);
        SwingUtil.removeButtonDecorations(pinButton);
        SwingUtil.addModalTooltip(pinButton, "Unpin module", "Pin module");
        pinButton.setPreferredSize(new Dimension(21, 0));
        
        // Set initial pinned state
        pinButton.setSelected(halPanel.isModulePinned(module.getDisplayName()));
        
        add(pinButton, BorderLayout.LINE_START);

        pinButton.addActionListener(e ->
        {
            halPanel.toggleModulePinned(module.getDisplayName());
        });

        // Button panel (right side)
        final JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 2));
        add(buttonPanel, BorderLayout.LINE_END);

        // Config button
        JButton configButton = new JButton(HalConfigPanel.CONFIG_ICON);
        SwingUtil.removeButtonDecorations(configButton);
        configButton.setPreferredSize(new Dimension(25, 0));
        configButton.setToolTipText("Edit module configuration");
        buttonPanel.add(configButton);

        configButton.addActionListener(e ->
        {
            configButton.setIcon(HalConfigPanel.CONFIG_ICON);
            halPanel.showModuleConfig(module);
        });

        // Add popup menu to name label
        JMenuItem configMenuItem = new JMenuItem("Configure");
        configMenuItem.addActionListener(e -> halPanel.showModuleConfig(module));
        
        addLabelPopupMenu(nameLabel, configMenuItem);
        add(nameLabel, BorderLayout.CENTER);

        // Toggle button
        onOffToggle = new HalPluginToggleButton();
        buttonPanel.add(onOffToggle);
        
        onOffToggle.setSelected(module.isRunning());
        onOffToggle.addActionListener(i ->
        {
            if (onOffToggle.isSelected())
            {
                module.start();
            }
            else
            {
                module.stop();
            }
        });
    }

    @Override
    public String getSearchableName()
    {
        return module.getDisplayName();
    }

    @Override
    public boolean isPinned()
    {
        return pinButton.isSelected();
    }

    void setPinned(boolean pinned)
    {
        pinButton.setSelected(pinned);
    }

    void setModuleEnabled(boolean enabled)
    {
        onOffToggle.setSelected(enabled);
    }

    /**
     * Updates the pinned state of this module item
     */
    void updatePinnedState()
    {
        pinButton.setSelected(halPanel.isModulePinned(module.getDisplayName()));
    }

    private Color getCategoryColor(net.runelite.client.plugins.microbot.hal.halsutility.modules.HalModuleCategory category) {
        switch (category) {
            case SKILLING:
                return new Color(46, 204, 113); // Green
            case MONEY:
                return new Color(241, 196, 15); // Gold
            case ACTIVITY:
                return new Color(52, 152, 219); // Blue
            case BOSSING:
                return new Color(231, 76, 60); // Red
            case UTILITY:
                return new Color(155, 89, 182); // Purple
            default:
                return Color.WHITE;
        }
    }

    /**
     * Adds a mouseover effect to change the text of the passed label to {@link ColorScheme#BRAND_ORANGE} color, and
     * adds the passed menu items to a popup menu shown when the label is clicked.
     *
     * @param label     The label to attach the mouseover and click effects to
     * @param menuItems The menu items to be shown when the label is clicked
     */
    static void addLabelPopupMenu(JLabel label, JMenuItem... menuItems)
    {
        final JPopupMenu menu = new JPopupMenu();
        final Color labelForeground = label.getForeground();
        menu.setBorder(new EmptyBorder(5, 5, 5, 5));

        for (final JMenuItem menuItem : menuItems)
        {
            if (menuItem == null)
            {
                continue;
            }

            // Some machines register mouseEntered through a popup menu, and do not register mouseExited when a popup
            // menu item is clicked, so reset the label's color when we click one of these options.
            menuItem.addActionListener(e -> label.setForeground(labelForeground));
            menu.add(menuItem);
        }

        label.addMouseListener(new MouseAdapter()
        {
            private Color lastForeground;

            @Override
            public void mouseClicked(MouseEvent mouseEvent)
            {
                Component source = (Component) mouseEvent.getSource();
                Point location = java.awt.MouseInfo.getPointerInfo().getLocation();
                SwingUtilities.convertPointFromScreen(location, source);
                menu.show(source, location.x, location.y);
            }

            @Override
            public void mouseEntered(MouseEvent mouseEvent)
            {
                lastForeground = label.getForeground();
                label.setForeground(ColorScheme.BRAND_ORANGE);
            }

            @Override
            public void mouseExited(MouseEvent mouseEvent)
            {
                label.setForeground(lastForeground);
            }
        });
    }
} 