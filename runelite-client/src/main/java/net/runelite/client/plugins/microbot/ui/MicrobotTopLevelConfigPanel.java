package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

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

    @Inject
    MicrobotTopLevelConfigPanel(
            EventBus eventBus,
            MicrobotPluginListPanel pluginListPanel
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
        pluginListPanelTab = addTab(pluginListPanel.getMuxer(), "microbot_config_icon_lg.png", "Microbot Plugins");

        tabGroup.select(pluginListPanelTab);

    }

    private MaterialTab addTab(PluginPanel panel, String image, String tooltip) {
        MaterialTab mt = new MaterialTab(
                new ImageIcon(ImageUtil.loadImageResource(MicrobotTopLevelConfigPanel.class, image)),
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

    private MaterialTab addTab(Provider<? extends PluginPanel> panelProvider, String image, String tooltip) {
        MaterialTab mt = new MaterialTab(
                new ImageIcon(ImageUtil.loadImageResource(MicrobotTopLevelConfigPanel.class, image)),
                tabGroup, null);
        mt.setToolTipText(tooltip);
        tabGroup.addTab(mt);

        mt.setOnSelectEvent(() ->
        {
            PluginPanel panel = panelProvider.get();
            content.add(image, panel.getWrappedPanel());
            eventBus.register(panel);
            switchTo(image, panel, true);
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
    }

    @Override
    public void onDeactivate() {
        active = false;
        current.onDeactivate();
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