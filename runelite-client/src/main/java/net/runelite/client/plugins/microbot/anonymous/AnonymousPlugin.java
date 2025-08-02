package net.runelite.client.plugins.microbot.anonymous;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;

@PluginDescriptor(
	name = PluginDescriptor.Bolado + "Anonymous Mode",
	description = "Hide your in-game identity.",
	tags = {"chat", "mask", "anonymous"},
	enabledByDefault = false
)

@Slf4j
public class AnonymousPlugin extends Plugin {
	@Inject
	private Client client;

	@Inject
	private AnonymousConfig config;

	@Provides
	AnonymousConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(AnonymousConfig.class);
	}

    @Inject
    AnonymousScript anonymousScript;


    @Override
	protected void startUp() throws Exception {
		this.client = Microbot.getClient();

        Microbot.enableAutoRunOn = false;

        anonymousScript.run(config, client);
	}

	@Override
	protected void shutDown() throws Exception {
		if (anonymousScript != null && anonymousScript.isRunning()) {
			anonymousScript.shutdown();
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event) {
		if (client.getGameState() != GameState.LOGGED_IN) return;
		if (anonymousScript != null && anonymousScript.isRunning()) anonymousScript.onBeforeRender(client, config);
	}
}
