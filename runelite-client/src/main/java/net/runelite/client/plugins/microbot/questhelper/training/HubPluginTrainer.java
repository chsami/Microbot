package net.runelite.client.plugins.microbot.questhelper.training;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * Trains a skill by running a Hub plugin the user mapped to it, and stops that plugin when the target level is reached
 * (the session calls {@link #reset()}). Starts and stops it the way the Agent Server's /scripts/start and /scripts/stop do.
 */
public final class HubPluginTrainer implements SkillTrainer
{
	/** Starts and stops plugins by display name. */
	public interface PluginControl
	{
		/** True when the plugin is running afterwards. */
		boolean start(String pluginName);

		void stop(String pluginName);
	}

	/** The real plugin manager. */
	public static final PluginControl LIVE = new PluginControl()
	{
		@Override
		public boolean start(String pluginName)
		{
			return switchPlugin(pluginName, true);
		}

		@Override
		public void stop(String pluginName)
		{
			switchPlugin(pluginName, false);
		}

		private boolean switchPlugin(String pluginName, boolean on)
		{
			PluginManager manager = Microbot.getPluginManager();
			if (manager == null)
			{
				return false;
			}
			Plugin found = null;
			for (Plugin plugin : manager.getPlugins())
			{
				PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
				if (descriptor != null && descriptor.name().toLowerCase(Locale.ROOT).equals(pluginName.toLowerCase(Locale.ROOT)))
				{
					found = plugin;
					break;
				}
			}
			if (found == null)
			{
				return false;
			}
			final Plugin plugin = found;
			if (manager.isActive(plugin) == on)
			{
				return on;
			}
			try
			{
				SwingUtilities.invokeAndWait(() ->
				{
					try
					{
						manager.setPluginEnabled(plugin, on);
						if (on)
						{
							manager.startPlugin(plugin);
						}
						else
						{
							manager.stopPlugin(plugin);
						}
					}
					catch (Exception e)
					{
						throw new IllegalStateException(e);
					}
				});
			}
			catch (Exception e)
			{
				return false;
			}
			return manager.isActive(plugin);
		}
	};

	private final String skill;
	private final String pluginName;
	private final PluginControl control;
	private boolean started;

	public HubPluginTrainer(String skill, String pluginName, PluginControl control)
	{
		this.skill = skill;
		this.pluginName = pluginName;
		this.control = control;
	}

	@Override
	public String skill()
	{
		return skill;
	}

	@Override
	public String name()
	{
		return "Hub plugin " + pluginName;
	}

	@Override
	public List<Supply> supplies(int level)
	{
		return Collections.emptyList();
	}

	@Override
	public Outcome tick(TrainerContext context)
	{
		if (!started)
		{
			if (!control.start(pluginName))
			{
				return Outcome.failed("Could not start the Hub plugin '" + pluginName + "' for " + skill);
			}
			started = true;
		}
		return Outcome.working();
	}

	@Override
	public void reset()
	{
		if (started)
		{
			started = false;
			control.stop(pluginName);
		}
	}
}
