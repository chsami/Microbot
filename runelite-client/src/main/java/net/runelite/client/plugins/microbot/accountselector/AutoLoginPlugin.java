package net.runelite.client.plugins.microbot.accountselector;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ProfileManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "AutoLogin",
        description = "Microbot autologin plugin",
        tags = {"account", "microbot", "login"},
        enabledByDefault = false
)
@Slf4j
public class AutoLoginPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    Notifier notifier;
    @Inject
    ProfileManager profileManager;
    @Inject
    WorldService worldService;
    @Inject
    AutoLoginScript accountSelectorScript;

    @Inject
    AutoLoginConfig autoLoginConfig;
    @Provides
    AutoLoginConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoLoginConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts = false;
        Microbot.setClient(client);
        Microbot.setClientThread(clientThread);
        Microbot.setNotifier(notifier);
        Microbot.setMouse(new VirtualMouse());
        Microbot.setProfileManager(profileManager);
        Microbot.setWorldService(worldService);
        accountSelectorScript.run(autoLoginConfig);
    }

    protected void shutDown() {
        accountSelectorScript.shutdown();
    }

}
