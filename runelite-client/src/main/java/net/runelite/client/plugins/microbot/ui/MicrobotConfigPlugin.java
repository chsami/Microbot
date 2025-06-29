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

import com.google.inject.Provides;
import javax.inject.Singleton;
import net.runelite.api.MenuAction;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.*;
import java.awt.image.BufferedImage;

@PluginDescriptor(
        name = "MicrobotConfiguration",
        hidden = true // prevent users from disabling
)
public class MicrobotConfigPlugin extends Plugin {
    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private Provider<MicrobotPluginListPanel> pluginListPanelProvider;

    @Inject
    private Provider<MicrobotTopLevelConfigPanel> topLevelConfigPanelProvider;

    @Inject
    private ConfigManager configManager;

    @Inject
    private MicrobotConfig microbotConfig;

    private MicrobotTopLevelConfigPanel topLevelConfigPanel;

    private NavigationButton navButton;

	@Provides
	@Singleton
	MicrobotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MicrobotConfig.class);
	}

    @Override
    protected void startUp() throws Exception {
        MicrobotPluginListPanel pluginListPanel = pluginListPanelProvider.get();
        pluginListPanel.addFakePlugin(new MicrobotPluginConfigurationDescriptor(
                        "Microbot", "Microbot client settings",
                        new String[]{"client"},
                        microbotConfig, configManager.getConfigDescriptor(microbotConfig)
                ));
        pluginListPanel.rebuildPluginList();

        topLevelConfigPanel = topLevelConfigPanelProvider.get();

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "microbot_config_icon.png");

        navButton = NavigationButton.builder()
                .tooltip("Community Plugins")
                .icon(icon)
                .priority(0)
                .panel(topLevelConfigPanel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() throws Exception {
        clientToolbar.removeNavigation(navButton);
    }

    @Subscribe
    public void onOverlayMenuClicked(OverlayMenuClicked overlayMenuClicked) {
        OverlayMenuEntry overlayMenuEntry = overlayMenuClicked.getEntry();
        if (overlayMenuEntry.getMenuAction() == MenuAction.RUNELITE_OVERLAY_CONFIG) {
            Overlay overlay = overlayMenuClicked.getOverlay();
            Plugin plugin = overlay.getPlugin();
            if (plugin == null) {
                return;
            }

            // Expand config panel for plugin
            SwingUtilities.invokeLater(() ->
            {
                clientToolbar.openPanel(navButton);
                topLevelConfigPanel.openConfigurationPanel(plugin.getName());
            });
        }
    }
}
