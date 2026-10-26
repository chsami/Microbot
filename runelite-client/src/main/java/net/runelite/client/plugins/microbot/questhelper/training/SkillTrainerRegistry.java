package net.runelite.client.plugins.microbot.questhelper.training;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The trainers by skill; the first one whose minimum level fits is used. Register your own for other skills. */
public final class SkillTrainerRegistry
{
	private static final Map<String, List<SkillTrainer>> TRAINERS = new LinkedHashMap<>();
	private static boolean builtIns;

	private SkillTrainerRegistry()
	{
	}

	public static synchronized void register(SkillTrainer trainer)
	{
		TRAINERS.computeIfAbsent(trainer.skill().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(trainer);
	}

	public static synchronized void clear()
	{
		TRAINERS.clear();
		builtIns = false;
	}

	private static synchronized void ensureBuiltIns()
	{
		if (!builtIns)
		{
			builtIns = true;
			BuiltInTrainers.registerAll();
		}
	}

	/** The trainer for the skill at this level, or null. */
	public static synchronized SkillTrainer find(String skill, int level)
	{
		ensureBuiltIns();
		List<SkillTrainer> list = TRAINERS.get(skill == null ? "" : skill.toUpperCase(Locale.ROOT));
		if (list == null)
		{
			return null;
		}
		SkillTrainer best = null;
		for (SkillTrainer trainer : list)
		{
			if (trainer.minLevel() <= level && (best == null || trainer.minLevel() > best.minLevel()))
			{
				best = trainer;
			}
		}
		return best;
	}

	public static synchronized boolean has(String skill)
	{
		ensureBuiltIns();
		List<SkillTrainer> list = TRAINERS.get(skill == null ? "" : skill.toUpperCase(Locale.ROOT));
		return list != null && !list.isEmpty();
	}
}
