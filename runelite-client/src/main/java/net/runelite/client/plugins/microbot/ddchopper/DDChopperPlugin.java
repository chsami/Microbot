package net.runelite.client.plugins.microbot.ddchopper;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
	name = PluginDescriptor.Default + "DD Chopper",
	description = "Trains Woodcutting to a target level: picks the best axe and the best XP/hour tree, power-chops or banks",
	tags = {"woodcutting", "microbot", "skilling", "dd", "chopper"},
	enabledByDefault = false
)
@Slf4j
public class DDChopperPlugin extends Plugin
{
	@Inject
	private DDChopperConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private DDChopperOverlay overlay;
	@Inject
	private DDChopperScript script;

	@Provides
	DDChopperConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DDChopperConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		script.run(config);
	}

	@Override
	protected void shutDown()
	{
		script.shutdown();
		overlayManager.remove(overlay);
	}
}
