package net.runelite.client.plugins.microbot.questhelper.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.questhelper.training.TrainingEta;

/**
 * Turns "this quest needs X" into an ordered plan: the unfinished prerequisite quests (expanded recursively,
 * deduplicated, dependencies first), the skills to train or boost, and quest points filled with the cheapest doable
 * quests. What cannot be automated becomes a blocker. The selected quest is always the last step. Pure: the game is
 * reached through {@link World}, the requirement objects through {@link Req}.
 */
public final class QuestRequirementResolver
{
	/** A requirement, reduced to what the planner needs. {@code met} is whether the player satisfies it right now. */
	public static final class Req
	{
		public enum Kind
		{
			QUEST, SKILL, QUEST_POINTS, COMBAT_LEVEL, BLOCKER, ALL, ANY
		}

		public final Kind kind;
		public final boolean met;
		public final String text;
		/** Quest name, or skill enum name. */
		public final String name;
		public final int amount;
		public final boolean boostable;
		public final List<Req> children;

		private Req(Kind kind, boolean met, String text, String name, int amount, boolean boostable, List<Req> children)
		{
			this.kind = kind;
			this.met = met;
			this.text = text == null ? "" : text;
			this.name = name == null ? "" : name;
			this.amount = amount;
			this.boostable = boostable;
			this.children = children == null ? Collections.emptyList() : children;
		}

		public static Req quest(String quest, boolean met)
		{
			return new Req(Kind.QUEST, met, "Complete " + quest, quest, 0, false, null);
		}

		public static Req skill(String skill, int level, boolean boostable, boolean met)
		{
			return new Req(Kind.SKILL, met, level + " " + display(skill), skill.toUpperCase(Locale.ROOT), level,
				boostable, null);
		}

		public static Req questPoints(int points, boolean met)
		{
			return new Req(Kind.QUEST_POINTS, met, points + " quest points", "", points, false, null);
		}

		public static Req combat(int level, boolean met)
		{
			return new Req(Kind.COMBAT_LEVEL, met, "Combat level " + level, "COMBAT", level, false, null);
		}

		public static Req blocker(String text)
		{
			return new Req(Kind.BLOCKER, false, text, "", 0, false, null);
		}

		public static Req all(String text, boolean met, List<Req> children)
		{
			return new Req(Kind.ALL, met, text, "", 0, false, children);
		}

		public static Req any(String text, boolean met, List<Req> children)
		{
			return new Req(Kind.ANY, met, text, "", 0, false, children);
		}
	}

	/** A cheap quest the quest-point fill may add. */
	public static final class Candidate
	{
		public final String name;
		public final int questPoints;
		/** Smaller is cheaper: guessed minutes. */
		public final int effort;

		public Candidate(String name, int questPoints, int effort)
		{
			this.name = name;
			this.questPoints = questPoints;
			this.effort = effort;
		}
	}

	/** The game, as the resolver needs it. */
	public interface World
	{
		boolean questDone(String quest);

		/** Real level of a skill (enum name); "COMBAT" is the combat level. */
		int level(String skill);

		int questPoints();

		/** The general requirements of a quest (met or not). */
		List<Req> requirementsOf(String quest);

		int questPointReward(String quest);

		/** Quests that are not done and whose own requirements are all met right now. */
		List<Candidate> doableQuests();

		/** Why the quest cannot be automated (needs another player...), or null. */
		String blockerFor(String quest);

		/** Levels the best cheap boost gives for the skill; 0 when none. */
		int maxBoost(String skill);

		/** Whether something (a trainer or a Hub plugin) can train the skill. */
		boolean canTrain(String skill);

		/** Guessed minutes for the quest. */
		int questMinutes(String quest);
	}

	/** Planner settings. */
	public static final class Options
	{
		/** A boost is used instead of training when it covers a gap of at most this many levels. */
		public int boostThreshold = 3;
		public boolean allowBoost = true;
	}

	public enum Kind
	{
		QUEST, TRAIN, BOOST
	}

	/** One step of the plan, in run order. */
	public static final class Step
	{
		public final Kind kind;
		/** Quest name for QUEST, skill enum name for TRAIN and BOOST. */
		public final String name;
		/** Target level for TRAIN and BOOST. */
		public final int level;
		public final int fromLevel;
		/** The quest that needs this step; null for the selected quest. */
		public final String neededBy;
		public final String reason;
		public final int etaMinutes;
		/** Distance from the selected quest in the tree (the selected quest is 0). */
		public int depth;
		/** The user switched this step off in the window. */
		public boolean skipped;

		Step(Kind kind, String name, int level, int fromLevel, String neededBy, String reason, int etaMinutes)
		{
			this.kind = kind;
			this.name = name;
			this.level = level;
			this.fromLevel = fromLevel;
			this.neededBy = neededBy;
			this.reason = reason == null ? "" : reason;
			this.etaMinutes = etaMinutes;
		}

		public String label()
		{
			switch (kind)
			{
				case TRAIN:
					return "Train " + display(name) + " " + fromLevel + " -> " + level;
				case BOOST:
					return "Boost " + display(name) + " to " + level;
				default:
					return name;
			}
		}

		/** Stable id used for skip toggles. */
		public String key()
		{
			return kind + ":" + name + ":" + level;
		}
	}

	/** The result. */
	public static final class Plan
	{
		public final String target;
		public final List<Step> steps = new ArrayList<>();
		public final List<String> blockers = new ArrayList<>();
		public boolean targetDone;

		Plan(String target)
		{
			this.target = target;
		}

		/** True when something has to be done before the selected quest. */
		public boolean hasPrerequisites()
		{
			return steps.size() > 1 || (steps.size() == 1 && steps.get(0).kind != Kind.QUEST);
		}

		public boolean hasBlockers()
		{
			return !blockers.isEmpty();
		}

		public int totalEtaMinutes()
		{
			int sum = 0;
			for (Step step : steps)
			{
				if (!step.skipped)
				{
					sum += step.etaMinutes;
				}
			}
			return sum;
		}

		/** The step list as an indented tree: the selected quest first, what it needs below it. */
		public List<String> tree()
		{
			List<String> out = new ArrayList<>();
			Map<String, List<Step>> children = new LinkedHashMap<>();
			Step root = null;
			for (Step step : steps)
			{
				if (step.neededBy == null)
				{
					root = step;
				}
				else
				{
					children.computeIfAbsent(step.neededBy, k -> new ArrayList<>()).add(step);
				}
			}
			if (root != null)
			{
				render(root, children, 0, out, new HashSet<>());
			}
			return out;
		}

		private static void render(Step step, Map<String, List<Step>> children, int depth, List<String> out,
			Set<String> seen)
		{
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < depth; i++)
			{
				sb.append("  ");
			}
			sb.append(step.label());
			if (step.etaMinutes > 0)
			{
				sb.append("  (~").append(step.etaMinutes).append(" min)");
			}
			out.add(sb.toString());
			if (step.kind == Kind.QUEST && seen.add(step.name))
			{
				List<Step> kids = children.get(step.name);
				if (kids != null)
				{
					for (Step kid : kids)
					{
						render(kid, children, depth + 1, out, seen);
					}
				}
			}
		}
	}

	private QuestRequirementResolver()
	{
	}

	/** True when a boost covers the gap and should be preferred to training. */
	public static boolean preferBoost(int level, int required, boolean boostable, int maxBoost, int threshold)
	{
		int gap = required - level;
		return boostable && gap >= 1 && gap <= Math.min(maxBoost, threshold);
	}

	public static String display(String skill)
	{
		if (skill == null || skill.isEmpty())
		{
			return "";
		}
		return skill.charAt(0) + skill.substring(1).toLowerCase(Locale.ROOT);
	}

	private static final class SkillNeed
	{
		int target;
		int count;
		boolean allBoostable = true;
		String firstQuest;
	}

	private static final class QuestNode
	{
		String parent;
		final Map<String, SkillNeed> skills = new LinkedHashMap<>();
		int questPointsNeeded;
	}

	/** Plans the quest. */
	public static Plan resolve(String target, World world, Options options)
	{
		Options opts = options == null ? new Options() : options;
		Plan plan = new Plan(target);
		if (world.questDone(target))
		{
			plan.targetDone = true;
			return plan;
		}
		Map<String, QuestNode> nodes = new LinkedHashMap<>();
		List<String> order = new ArrayList<>();
		visit(target, null, world, nodes, order, new HashSet<>(), plan);
		if (!order.contains(target))
		{
			order.add(target);
		}

		// quest points are filled in run order so each quest sees what the earlier ones gave
		Map<String, List<Candidate>> fills = new LinkedHashMap<>();
		Set<String> planned = new LinkedHashSet<>(order);
		List<Candidate> pool = null;
		int points = world.questPoints();
		for (String quest : order)
		{
			QuestNode node = nodes.get(quest);
			if (node != null && node.questPointsNeeded > points)
			{
				if (pool == null)
				{
					pool = new ArrayList<>(world.doableQuests());
					pool.sort(Comparator.comparingInt((Candidate c) -> c.effort).thenComparing(c -> c.name));
				}
				List<Candidate> chosen = new ArrayList<>();
				for (Candidate candidate : pool)
				{
					if (points >= node.questPointsNeeded)
					{
						break;
					}
					if (planned.contains(candidate.name) || candidate.questPoints <= 0)
					{
						continue;
					}
					planned.add(candidate.name);
					chosen.add(candidate);
					points += candidate.questPoints;
				}
				if (points < node.questPointsNeeded)
				{
					plan.blockers.add(quest + " needs " + node.questPointsNeeded + " quest points; only " + points
						+ " are reachable with the doable quests");
				}
				fills.put(quest, chosen);
			}
			points += world.questPointReward(quest);
		}

		for (String quest : order)
		{
			QuestNode node = nodes.get(quest);
			if (node != null)
			{
				for (Map.Entry<String, SkillNeed> entry : node.skills.entrySet())
				{
					SkillNeed need = entry.getValue();
					if (quest.equals(need.firstQuest))
					{
						addSkillSteps(plan, entry.getKey(), need, quest, world, opts);
					}
				}
			}
			for (Candidate candidate : fills.getOrDefault(quest, Collections.emptyList()))
			{
				plan.steps.add(new Step(Kind.QUEST, candidate.name, 0, 0, quest,
					"quest points for " + quest + " (" + candidate.questPoints + " QP)", candidate.effort));
			}
			boolean isTarget = quest.equals(target);
			String parent = node == null ? null : node.parent;
			plan.steps.add(new Step(Kind.QUEST, quest, 0, 0, isTarget ? null : parent,
				isTarget ? "" : "required by " + parent, world.questMinutes(quest)));
		}
		// the selected quest runs last
		Step last = null;
		for (Step step : plan.steps)
		{
			if (step.kind == Kind.QUEST && step.neededBy == null)
			{
				last = step;
			}
		}
		if (last != null)
		{
			plan.steps.remove(last);
			plan.steps.add(last);
		}
		setDepths(plan);
		return plan;
	}

	private static void addSkillSteps(Plan plan, String skill, SkillNeed need, String quest, World world, Options opts)
	{
		int level = world.level(skill);
		if (level >= need.target)
		{
			return;
		}
		boolean boost = opts.allowBoost && need.count == 1
			&& preferBoost(level, need.target, need.allBoostable, world.maxBoost(skill), opts.boostThreshold);
		if (boost)
		{
			plan.steps.add(new Step(Kind.BOOST, skill, need.target, level, quest,
				display(skill) + " " + need.target + " for " + quest + " (boost +" + (need.target - level) + ")", 2));
			return;
		}
		if (!world.canTrain(skill))
		{
			plan.blockers.add("No trainer or Hub plugin for " + display(skill) + " (need " + need.target + ", have "
				+ level + "): map it to a Hub plugin in the Quest Helper settings or train it by hand");
			return;
		}
		plan.steps.add(new Step(Kind.TRAIN, skill, need.target, level, quest,
			display(skill) + " " + need.target + " for " + quest, TrainingEta.minutes(skill, level, need.target)));
	}

	private static void setDepths(Plan plan)
	{
		Map<String, Integer> depthOfQuest = new LinkedHashMap<>();
		for (int i = plan.steps.size() - 1; i >= 0; i--)
		{
			Step step = plan.steps.get(i);
			if (step.kind == Kind.QUEST)
			{
				step.depth = step.neededBy == null ? 0 : depthOfQuest.getOrDefault(step.neededBy, 0) + 1;
				depthOfQuest.put(step.name, step.depth);
			}
		}
		for (Step step : plan.steps)
		{
			if (step.kind != Kind.QUEST)
			{
				step.depth = depthOfQuest.getOrDefault(step.neededBy, 0) + 1;
			}
		}
	}

	private static void visit(String quest, String parent, World world, Map<String, QuestNode> nodes,
		List<String> order, Set<String> visiting, Plan plan)
	{
		if (nodes.containsKey(quest) || !visiting.add(quest))
		{
			return; // seen already, or a cycle: the first visit owns it
		}
		QuestNode node = new QuestNode();
		node.parent = parent;
		nodes.put(quest, node);
		String blocked = world.blockerFor(quest);
		if (blocked != null)
		{
			plan.blockers.add(blocked);
		}
		List<Req> reqs = world.requirementsOf(quest);
		if (reqs != null)
		{
			for (Req req : reqs)
			{
				handle(req, quest, node, world, nodes, order, visiting, plan);
			}
		}
		order.add(quest);
		visiting.remove(quest);
	}

	private static void handle(Req req, String quest, QuestNode node, World world, Map<String, QuestNode> nodes,
		List<String> order, Set<String> visiting, Plan plan)
	{
		if (req == null || req.met)
		{
			return;
		}
		switch (req.kind)
		{
			case QUEST:
				if (!world.questDone(req.name))
				{
					visit(req.name, quest, world, nodes, order, visiting, plan);
				}
				break;
			case SKILL:
			case COMBAT_LEVEL:
			{
				SkillNeed need = node.skills.computeIfAbsent(req.name, k -> new SkillNeed());
				need.target = Math.max(need.target, req.amount);
				need.count++;
				need.allBoostable &= req.boostable;
				if (need.firstQuest == null)
				{
					need.firstQuest = quest;
				}
				break;
			}
			case QUEST_POINTS:
				node.questPointsNeeded = Math.max(node.questPointsNeeded, req.amount);
				break;
			case BLOCKER:
				plan.blockers.add(quest + ": " + req.text);
				break;
			case ALL:
				for (Req child : req.children)
				{
					handle(child, quest, node, world, nodes, order, visiting, plan);
				}
				break;
			case ANY:
			{
				Req pick = null;
				for (Req child : req.children)
				{
					if (child.met)
					{
						return;
					}
					if (pick == null && child.kind != Req.Kind.BLOCKER)
					{
						pick = child;
					}
				}
				if (pick == null)
				{
					plan.blockers.add(quest + ": " + req.text);
				}
				else
				{
					handle(pick, quest, node, world, nodes, order, visiting, plan);
				}
				break;
			}
			default:
				break;
		}
	}
}
