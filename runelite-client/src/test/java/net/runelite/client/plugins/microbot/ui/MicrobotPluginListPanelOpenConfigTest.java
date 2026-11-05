package net.runelite.client.plugins.microbot.ui;

import javax.inject.Provider;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class MicrobotPluginListPanelOpenConfigTest {
    @Test
    @SuppressWarnings("unchecked")
    public void openingPluginWithoutConfigDescriptorDoesNotBuildConfigPanel() {
        Provider<MicrobotConfigPanel> configPanelProvider = mock(Provider.class);
        Provider<LocalPluginsPanel> localPluginsPanelProvider = mock(Provider.class);
        MicrobotPluginListPanel panel = new MicrobotPluginListPanel(
                mock(ConfigManager.class),
                mock(PluginManager.class),
                mock(MicrobotPluginManager.class),
                mock(ExternalPluginManager.class),
                mock(EventBus.class),
                configPanelProvider,
                localPluginsPanelProvider,
                mock(MicrobotBugReporter.class));

        panel.openConfigurationPanel(new MicrobotPluginConfigurationDescriptor(
                "No Config", "Plugin without config", new String[0], null, null));

        verifyNoInteractions(configPanelProvider);
    }
}
