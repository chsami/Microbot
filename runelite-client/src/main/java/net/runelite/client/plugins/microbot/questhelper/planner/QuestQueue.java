package net.runelite.client.plugins.microbot.questhelper.planner;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The quests the Quest Helper runs one after another (a fixed order, or picked at random from the doable ones), with
 * the cursor, the per-quest profiles, the purchase pre-approval and the random-mode settings. Persisted as JSON
 * through a {@link Store}; the shared instance stores into the questhelper config group.
 */
public final class QuestQueue
{
	/** Where the JSON lives. */
	public interface Store
	{
		String read();

		void write(String json);
	}

	/** How the next quest is chosen. */
	public enum Mode
	{
		FIXED, RANDOM
	}

	/** Config key of the queue. */
	public static final String CONFIG_KEY = "plannerQueue";

	private static final Gson GSON = new Gson();

	private static final class Data
	{
		List<QuestProfile> entries = new ArrayList<>();
		int index;
		boolean active;
		Mode mode = Mode.FIXED;
		/** Random mode: the quests to choose from; empty means every doable quest. */
		List<String> pool = new ArrayList<>();
		/** Quests that failed or were skipped; the random mode never picks them again. */
		Set<String> skipped = new LinkedHashSet<>();
		int pauseMinMinutes = 1;
		int pauseMaxMinutes = 10;
		/** Global default for "keep at least N stamina potions". */
		int defaultMinStamina;
		QuestApproval approval;
		/** Every profile the user edited, by quest name, so the random mode finds it again. */
		Map<String, QuestProfile> profiles = new LinkedHashMap<>();
		/** Random mode: the quest being run. */
		QuestProfile randomCurrent;
		/** Supplies new quests start with, by profile type (TRAVEL or COMBAT). */
		Map<String, QuestProfile.Supplies> templates = new LinkedHashMap<>();
	}

	private final Store store;
	private Data data = new Data();
	/** Runtime only: the combined preflight of this run finished. */
	private volatile boolean preflightDone;
	/** Runtime only: put a quest's unmet requirements in front of it (the Agent Server can turn this off). */
	private volatile boolean resolveRequirements = true;
	/** Runtime only: random mode rests until this time (epoch ms). */
	private volatile long pauseUntilMs;

	public QuestQueue(Store store)
	{
		this.store = store;
	}

	private static volatile QuestQueue shared;

	/** The queue backed by the Quest Helper config. */
	public static synchronized QuestQueue shared()
	{
		if (shared == null)
		{
			shared = new QuestQueue(new ConfigStore());
			shared.load();
		}
		return shared;
	}

	public synchronized void load()
	{
		Data loaded;
		try
		{
			String json = store == null ? null : store.read();
			loaded = json == null || json.isEmpty() ? null : GSON.fromJson(json, Data.class);
		}
		catch (RuntimeException e)
		{
			loaded = null;
		}
		data = loaded == null ? new Data() : loaded;
		if (data.entries == null)
		{
			data.entries = new ArrayList<>();
		}
		data.entries.removeIf(entry -> entry == null);
		for (QuestProfile entry : data.entries)
		{
			entry.normalised();
		}
		data.mode = data.mode == null ? Mode.FIXED : data.mode;
		data.pool = data.pool == null ? new ArrayList<>() : data.pool;
		data.skipped = data.skipped == null ? new LinkedHashSet<>() : data.skipped;
		data.profiles = data.profiles == null ? new LinkedHashMap<>() : data.profiles;
		for (QuestProfile profile : data.profiles.values())
		{
			if (profile != null)
			{
				profile.normalised();
			}
		}
		if (data.randomCurrent != null)
		{
			data.randomCurrent.normalised();
		}
		data.templates = data.templates == null ? new LinkedHashMap<>() : data.templates;
		data.pauseMinMinutes = Math.max(0, data.pauseMinMinutes);
		data.pauseMaxMinutes = Math.max(data.pauseMinMinutes, data.pauseMaxMinutes);
		data.defaultMinStamina = Math.max(0, Math.min(QuestProfile.MAX_MIN_STAMINA, data.defaultMinStamina));
		data.index = Math.max(0, Math.min(data.index, data.entries.size()));
	}

	public synchronized void save()
	{
		if (store != null)
		{
			store.write(GSON.toJson(data));
		}
	}

	public synchronized List<QuestProfile> entries()
	{
		return Collections.unmodifiableList(new ArrayList<>(data.entries));
	}

	/** Replaces the queue contents and rewinds; the queue stays stopped. Every entry is also remembered as a profile. */
	public synchronized void setEntries(List<QuestProfile> entries)
	{
		data.entries = new ArrayList<>(entries);
		for (QuestProfile entry : entries)
		{
			if (!entry.isStep())
			{
				data.profiles.put(entry.getQuestName(), entry.copy());
			}
		}
		data.index = 0;
		data.active = false;
		data.randomCurrent = null;
		preflightDone = false;
		save();
	}

	public synchronized Mode mode()
	{
		return data.mode;
	}

	public synchronized void setMode(Mode mode)
	{
		data.mode = mode == null ? Mode.FIXED : mode;
		save();
	}

	public synchronized List<String> pool()
	{
		return new ArrayList<>(data.pool);
	}

	public synchronized void setPool(List<String> pool)
	{
		data.pool = new ArrayList<>(pool);
		save();
	}

	public synchronized Set<String> skipped()
	{
		return new LinkedHashSet<>(data.skipped);
	}

	/** Remembers a quest that failed or was skipped so the random mode leaves it alone. */
	public synchronized void markSkipped(String questName)
	{
		if (questName != null && data.skipped.add(questName))
		{
			save();
		}
	}

	public synchronized void clearSkipped()
	{
		data.skipped.clear();
		save();
	}

	public synchronized int pauseMinMinutes()
	{
		return data.pauseMinMinutes;
	}

	public synchronized int pauseMaxMinutes()
	{
		return data.pauseMaxMinutes;
	}

	public synchronized void setPause(int minMinutes, int maxMinutes)
	{
		data.pauseMinMinutes = Math.max(0, minMinutes);
		data.pauseMaxMinutes = Math.max(data.pauseMinMinutes, maxMinutes);
		save();
	}

	public synchronized int defaultMinStamina()
	{
		return data.defaultMinStamina;
	}

	public synchronized void setDefaultMinStamina(int value)
	{
		data.defaultMinStamina = Math.max(0, Math.min(QuestProfile.MAX_MIN_STAMINA, value));
		save();
	}

	public synchronized QuestApproval approval()
	{
		return data.approval;
	}

	public synchronized void setApproval(QuestApproval approval)
	{
		data.approval = approval;
		save();
	}

	/** The profile the user saved for the quest, or null. */
	public synchronized QuestProfile savedProfile(String questName)
	{
		QuestProfile profile = data.profiles.get(questName);
		return profile == null ? null : profile.copy();
	}

	public synchronized void saveProfile(QuestProfile profile)
	{
		data.profiles.put(profile.getQuestName(), profile.copy());
		save();
	}

	/** The saved profile, else the suggested default (Combat when the quest has fights, Travel otherwise). */
	public synchronized QuestProfile profileOrDefault(String questName, boolean hasCombat)
	{
		QuestProfile saved = savedProfile(questName);
		return saved != null ? saved : applyTemplate(QuestProfile.defaultFor(questName, hasCombat));
	}

	/** Remembers the supplies new quests of this type start with. */
	public synchronized void setTemplate(QuestProfile.Type type, QuestProfile.Supplies supplies)
	{
		QuestProfile holder = new QuestProfile();
		holder.setSupplies(supplies);
		data.templates.put(type.name(), holder.copy().getSupplies());
		save();
	}

	/** Gives the profile the saved template supplies for its type, when there is one. Returns the profile. */
	public synchronized QuestProfile applyTemplate(QuestProfile profile)
	{
		QuestProfile.Supplies template = data.templates.get(profile.getType().name());
		if (template != null)
		{
			QuestProfile holder = new QuestProfile();
			holder.setSupplies(template);
			profile.setSupplies(holder.copy().getSupplies());
		}
		return profile;
	}

	public synchronized boolean isActive()
	{
		if (!data.active)
		{
			return false;
		}
		return data.mode == Mode.RANDOM || data.index < data.entries.size();
	}

	public synchronized boolean isRandom()
	{
		return data.mode == Mode.RANDOM;
	}

	/** Starts running. A fixed queue starts from its first quest and needs at least one; random needs none. */
	public synchronized boolean start()
	{
		if (data.mode == Mode.FIXED && data.entries.isEmpty())
		{
			return false;
		}
		data.index = 0;
		data.active = true;
		data.randomCurrent = null;
		preflightDone = false;
		pauseUntilMs = 0;
		save();
		return true;
	}

	public synchronized void stop()
	{
		if (data.active)
		{
			data.active = false;
			data.randomCurrent = null;
			save();
		}
	}

	/** The profile of the quest being run, or null when stopped, finished or (random mode) between quests. */
	public synchronized QuestProfile current()
	{
		if (!isActive())
		{
			return null;
		}
		return data.mode == Mode.RANDOM ? data.randomCurrent : data.entries.get(data.index);
	}

	public synchronized int index()
	{
		return data.index;
	}

	/** Random mode: the quest just picked. */
	public synchronized void setRandomCurrent(QuestProfile profile)
	{
		data.randomCurrent = profile;
		save();
	}

	/**
	 * Finishes the current quest. A fixed queue moves to the next quest and stops after the last; the random mode
	 * clears its current quest so the next one is picked. Returns the new current profile or null.
	 */
	public synchronized QuestProfile advance()
	{
		if (!isActive())
		{
			return null;
		}
		if (data.mode == Mode.RANDOM)
		{
			data.randomCurrent = null;
			save();
			return null;
		}
		data.index++;
		if (data.index >= data.entries.size())
		{
			data.active = false;
		}
		save();
		return current();
	}

	/** The profile of a queued quest by name, or null. */
	public synchronized QuestProfile profileFor(String questName)
	{
		for (QuestProfile entry : data.entries)
		{
			if (entry.getQuestName().equals(questName))
			{
				return entry;
			}
		}
		return null;
	}

	/** Quests from the cursor on (the current one included), in order. */
	public synchronized List<QuestProfile> remaining()
	{
		if (!isActive() || data.mode == Mode.RANDOM)
		{
			return new ArrayList<>();
		}
		List<QuestProfile> out = new ArrayList<>();
		for (QuestProfile entry : data.entries.subList(data.index, data.entries.size()))
		{
			if (!entry.isStep())
			{
				out.add(entry);
			}
		}
		return out;
	}

	/** Inserts entries just before the one being run (fixed mode), so they run first. False in random mode. */
	public synchronized boolean insertBeforeCurrent(List<QuestProfile> steps)
	{
		if (!data.active || data.mode == Mode.RANDOM || steps.isEmpty())
		{
			return false;
		}
		data.entries.addAll(data.index, steps);
		save();
		return true;
	}

	public boolean resolveRequirements()
	{
		return resolveRequirements;
	}

	public void setResolveRequirements(boolean value)
	{
		resolveRequirements = value;
	}

	public boolean isPreflightDone()
	{
		return preflightDone;
	}

	public void setPreflightDone(boolean done)
	{
		preflightDone = done;
	}

	public long pauseUntilMs()
	{
		return pauseUntilMs;
	}

	public void setPauseUntilMs(long epochMs)
	{
		pauseUntilMs = epochMs;
	}

	/** Moves entry {@code from} by {@code delta} places (negative is up); false when it would leave the list. */
	public static <T> boolean move(List<T> list, int from, int delta)
	{
		int to = from + delta;
		if (from < 0 || from >= list.size() || to < 0 || to >= list.size())
		{
			return false;
		}
		T item = list.remove(from);
		list.add(to, item);
		return true;
	}

	private static final class ConfigStore implements Store
	{
		@Override
		public String read()
		{
			net.runelite.client.config.ConfigManager manager =
				net.runelite.client.plugins.microbot.Microbot.getConfigManager();
			return manager == null ? null : manager.getConfiguration(
				net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig.QUEST_HELPER_GROUP, CONFIG_KEY);
		}

		@Override
		public void write(String json)
		{
			net.runelite.client.config.ConfigManager manager =
				net.runelite.client.plugins.microbot.Microbot.getConfigManager();
			if (manager != null)
			{
				manager.setConfiguration(
					net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig.QUEST_HELPER_GROUP, CONFIG_KEY, json);
			}
		}
	}
}
