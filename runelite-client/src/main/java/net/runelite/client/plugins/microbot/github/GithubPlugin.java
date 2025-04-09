package net.runelite.client.plugins.microbot.github;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;

@PluginDescriptor(
        name = "Github plugin",
        description = "Allows to download plugins from a github and sideload them",
        tags = {"github", "microbot"},
        enabledByDefault = false,
        hidden = false
)
@Slf4j
public class GithubPlugin extends Plugin {

    GithubPanel panel;
    NavigationButton navButton;
    @Inject
    ClientToolbar clientToolbar;

    @Inject
    public GithubConfig config;

    @Provides
    GithubConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GithubConfig.class);
    }

    @Override
    protected void startUp() {
        panel = injector.getInstance(GithubPanel.class);
        final BufferedImage icon = ImageUtil.loadImageResource(GithubPlugin.class, "github_icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Github Repository")
                .icon(icon)
                .priority(8)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
        System.out.println("Github plugin stopped");
    }
}
