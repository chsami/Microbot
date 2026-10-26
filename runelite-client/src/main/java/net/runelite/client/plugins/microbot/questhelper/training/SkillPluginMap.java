package net.runelite.client.plugins.microbot.questhelper.training;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Skill to Hub plugin name, stored as "Fishing=Auto Fishing;Mining=Motherlode Mine". Pure. */
public final class SkillPluginMap
{
	public static final String CONFIG_KEY = "skillPlugins";

	private final Map<String, String> map = new LinkedHashMap<>();

	public static SkillPluginMap parse(String text)
	{
		SkillPluginMap out = new SkillPluginMap();
		if (text != null)
		{
			for (String part : text.split("[;\n]"))
			{
				int eq = part.indexOf('=');
				if (eq > 0 && eq < part.length() - 1)
				{
					out.map.put(part.substring(0, eq).trim().toUpperCase(Locale.ROOT), part.substring(eq + 1).trim());
				}
			}
		}
		return out;
	}

	public static SkillPluginMap of(Map<String, ?> values)
	{
		SkillPluginMap out = new SkillPluginMap();
		for (Map.Entry<String, ?> entry : values.entrySet())
		{
			out.map.put(entry.getKey().trim().toUpperCase(Locale.ROOT), String.valueOf(entry.getValue()).trim());
		}
		return out;
	}

	/** The plugin name for the skill, or null. */
	public String pluginFor(String skill)
	{
		String name = skill == null ? null : map.get(skill.toUpperCase(Locale.ROOT));
		return name == null || name.isEmpty() ? null : name;
	}

	public String serialise()
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> entry : map.entrySet())
		{
			sb.append(sb.length() == 0 ? "" : ";").append(entry.getKey()).append('=').append(entry.getValue());
		}
		return sb.toString();
	}
}
