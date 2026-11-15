package net.runelite.client.plugins.microbot.mouserecorder;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.Plugin;

@Slf4j
final class DiscordCatalog
{
	static final String EVENT_TYPE = "net.runelite.client.plugins.discord.DiscordGameEventType";
	static final String IN_GAME = "IN_GAME";

	private static final Map<String, String> AREA_KINDS = Map.of(
		"BOSSES", "boss",
		"CITIES", "city",
		"DUNGEONS", "dungeon",
		"MINIGAMES", "minigame",
		"RAIDS", "raid",
		"REGIONS", "region");
	private static final DiscordCatalog UNAVAILABLE = new DiscordCatalog(null, null, Map.of(), null);
	private static volatile DiscordCatalog shared;

	@Value
	static class Activity
	{
		String id;
		String name;
		String kind;
		int priority;
		boolean shouldClear;
		boolean shouldTimeout;
		boolean shouldBeCleared;
	}

	private final Method fromRegion;
	private final Method fromSkill;
	private final Map<Object, Activity> activities;
	private final Activity inGame;

	private DiscordCatalog(Method fromRegion, Method fromSkill, Map<Object, Activity> activities, Activity inGame)
	{
		this.fromRegion = fromRegion;
		this.fromSkill = fromSkill;
		this.activities = activities;
		this.inGame = inGame;
	}

	static DiscordCatalog shared()
	{
		DiscordCatalog catalog = shared;
		if (catalog == null)
		{
			catalog = load(EVENT_TYPE, Plugin.class.getClassLoader());
			shared = catalog;
		}
		return catalog;
	}

	static DiscordCatalog load(String className, ClassLoader loader)
	{
		try
		{
			Class<?> type = Class.forName(className, true, loader);
			Method fromRegion = open(type, "fromRegion", int.class);
			Method fromSkill = open(type, "fromSkill", Skill.class);
			Method state = open(type, "getState");
			Method details = open(type, "getDetails");
			Method priority = open(type, "getPriority");
			Method shouldClear = open(type, "isShouldClear");
			Method shouldTimeout = open(type, "isShouldTimeout");
			Method shouldBeCleared = open(type, "isShouldBeCleared");
			Method areaType = open(type, "getDiscordAreaType");
			Map<Object, Activity> activities = new HashMap<>();
			Activity inGame = null;
			for (Object constant : type.getEnumConstants())
			{
				String id = ((Enum<?>) constant).name();
				String detail = (String) details.invoke(constant);
				Object area = areaType.invoke(constant);
				String kind = area != null ? AREA_KINDS.get(((Enum<?>) area).name()) : detail != null ? "training" : null;
				Activity activity = new Activity(id, detail != null ? detail : (String) state.invoke(constant), kind,
					(int) priority.invoke(constant), (boolean) shouldClear.invoke(constant),
					(boolean) shouldTimeout.invoke(constant), (boolean) shouldBeCleared.invoke(constant));
				activities.put(constant, activity);
				if (IN_GAME.equals(id))
				{
					inGame = activity;
				}
			}
			return new DiscordCatalog(fromRegion, fromSkill, activities, inGame);
		}
		catch (ReflectiveOperationException | RuntimeException | LinkageError e)
		{
			log.warn("Discord activity catalogue is unavailable, input sessions get no content segments: {}", e.toString());
			return UNAVAILABLE;
		}
	}

	boolean available()
	{
		return fromRegion != null;
	}

	Activity region(int regionId)
	{
		return lookup(fromRegion, regionId);
	}

	Activity skill(Skill skill)
	{
		return lookup(fromSkill, skill);
	}

	Activity inGame()
	{
		return inGame;
	}

	private Activity lookup(Method method, Object argument)
	{
		if (method == null)
		{
			return null;
		}
		try
		{
			Object type = method.invoke(null, argument);
			return type == null ? null : activities.get(type);
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			return null;
		}
	}

	private static Method open(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException
	{
		Method method = type.getDeclaredMethod(name, parameters);
		method.setAccessible(true);
		return method;
	}
}
