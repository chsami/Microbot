package net.runelite.client.plugins.microbot.profilemanager;

import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;

@PluginDescriptor(
        name = "Profile Snapshot",
        description = "Save/load plugin profile states",
        tags = {"profile", "snapshot", "microbot"},
        enabledByDefault = true
)
public class ProfileSnapshotPlugin extends Plugin
{
    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ConfigManager configManager;

    @Inject
    private PluginManager pluginManager;

    private NavigationButton navButton;

    @Getter
    private ProfileSnapshotPanel panel;

    @Override
    protected void startUp()
    {
        panel = new ProfileSnapshotPanel(configManager, pluginManager);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(),
                "/net/runelite/client/plugins/microbot/profilemanager/profileIcon.png");

        navButton = NavigationButton.builder()
                .tooltip("Profile Snapshot")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown()
    {
        clientToolbar.removeNavigation(navButton);
    }
}
