package net.runelite.client.plugins.microbot.questhelper.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.QuestPreflight;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestRequirementResolver.Candidate;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestRequirementResolver.Req;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.requirements.ComplexRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.conditional.Conditions;
import net.runelite.client.plugins.microbot.questhelper.requirements.player.CombatLevelRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.player.SkillRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.quest.QuestPointRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.quest.QuestRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.util.LogicType;
import net.runelite.client.plugins.microbot.questhelper.training.BoostCatalog;
import net.runelite.client.plugins.microbot.questhelper.training.SkillPluginMap;
import net.runelite.client.plugins.microbot.questhelper.training.SkillTrainerRegistry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Builds the requirement plan of a quest from Quest Helper's own requirement objects and turns plans into queue
 * entries. The game is read on the client thread; call the public methods off it. Plans never decide a purchase:
 * training steps buy through the same approval and max-spend rules as quests.
 */
public final class QuestRequirementPlanner
{
	/** Keys of plan steps the user switched off; survives the re-evaluation so a skipped step stays skipped. */
	private static final Set<String> SKIPPED = ConcurrentHashMap.newKeySet();

	private static final Pattern NUMBER = Pattern.compile("(\\d+)");

	private QuestRequirementPlanner()
	{
	}

	public static void setSkipped(String stepKey, boolean skipped)
	{
		if (skipped)
		{
			SKIPPED.add(stepKey);
		}
		else
		{
			SKIPPED.remove(stepKey);
		}
	}

	public static boolean isSkipped(String stepKey)
	{
		return SKIPPED.contains(stepKey);
	}

	public static void clearSkipped()
	{
		SKIPPED.clear();
	}

	/** Skill to Hub plugin name from the config. */
	public static SkillPluginMap skillPlugins()
	{
		ConfigManager manager = Microbot.getConfigManager();
		String text = manager == null ? null
			: manager.getConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, SkillPluginMap.CONFIG_KEY);
		return SkillPluginMap.parse(text);
	}

	/** The plan for one quest, with the user's skip toggles applied. Null when the quest is unknown. */
	public static QuestRequirementResolver.Plan plan(String questName)
	{
		QuestHelper helper = QuestHelperQuest.getByName(questName);
		if (helper == null || helper.getQuest() == null)
		{
			return null;
		}
		QuestRequirementResolver.Plan plan = QuestRequirementResolver.resolve(questName, new LiveWorld(), null);
		for (QuestRequirementResolver.Step step : plan.steps)
		{
			step.skipped = SKIPPED.contains(step.key());
		}
		return plan;
	}

	/** Queue entries and plans for a list of quests. */
	public static final class Expansion
	{
		public final List<QuestProfile> entries = new ArrayList<>();
		public final Map<String, QuestRequirementResolver.Plan> plans = new LinkedHashMap<>();
		public final List<String> blockers = new ArrayList<>();
	}

	/**
	 * Puts each quest's unmet requirements in front of it. A quest whose plan has blockers is kept out of the entries
	 * and its blockers are listed. Quests that are already in the list earlier are not added twice.
	 */
	public static Expansion expand(List<QuestProfile> wanted)
	{
		Expansion out = new Expansion();
		for (QuestProfile profile : wanted)
		{
			if (profile.isStep())
			{
				out.entries.addAll(wanted); // already expanded (the window or an earlier call did it)
				return out;
			}
		}
		Set<String> queued = new LinkedHashSet<>();
		for (QuestProfile profile : wanted)
		{
			if (profile.isStep())
			{
				out.entries.add(profile);
				continue;
			}
			QuestRequirementResolver.Plan plan = plan(profile.getQuestName());
			if (plan == null || plan.targetDone)
			{
				if (queued.add(profile.getQuestName()))
				{
					out.entries.add(profile);
				}
				continue;
			}
			out.plans.put(profile.getQuestName(), plan);
			if (plan.hasBlockers())
			{
				for (String blocker : plan.blockers)
				{
					out.blockers.add(profile.getQuestName() + ": " + blocker);
				}
				continue;
			}
			for (QuestProfile entry : entriesOf(plan, profile, queued))
			{
				out.entries.add(entry);
			}
		}
		return out;
	}

	/** The queue entries of one plan, the selected quest keeping its own profile. Skipped steps are left out. */
	public static List<QuestProfile> entriesOf(QuestRequirementResolver.Plan plan, QuestProfile targetProfile,
		Set<String> alreadyQueued)
	{
		List<QuestProfile> out = new ArrayList<>();
		for (QuestRequirementResolver.Step step : plan.steps)
		{
			if (step.skipped)
			{
				continue;
			}
			switch (step.kind)
			{
				case QUEST:
					if (alreadyQueued.add(step.name))
					{
						out.add(step.neededBy == null ? targetProfile : QuestPlannerLauncher.profileFor(step.name));
					}
					break;
				case TRAIN:
					out.add(QuestProfile.step("TRAIN", step.name, step.level, plan.target));
					break;
				case BOOST:
					out.add(QuestProfile.step("BOOST", step.name, step.level, plan.target));
					break;
				default:
					break;
			}
		}
		return out;
	}

	/** Approves the supplies of the training and boost entries, up to the max spend per quest each. */
	public static void extendApproval(QuestApproval approval, List<QuestProfile> entries)
	{
		long perStep = 100_000;
		ConfigManager manager = Microbot.getConfigManager();
		try
		{
			String value = manager == null ? null
				: manager.getConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, "maxSpendPerQuest");
			perStep = value == null ? perStep : Long.parseLong(value.trim());
		}
		catch (NumberFormatException e)
		{
			// keep the default
		}
		for (QuestProfile entry : entries)
		{
			if (!entry.isStep())
			{
				continue;
			}
			List<String> names = new ArrayList<>();
			if ("BOOST".equals(entry.getStepKind()))
			{
				BoostCatalog.Boost boost = BoostCatalog.of(entry.getStepSkill());
				if (boost != null)
				{
					names.add(boost.item);
				}
			}
			else
			{
				net.runelite.client.plugins.microbot.questhelper.training.SkillTrainer trainer =
					SkillTrainerRegistry.find(entry.getStepSkill(), 1);
				if (trainer != null)
				{
					for (net.runelite.client.plugins.microbot.questhelper.training.SkillTrainer.Supply supply
						: trainer.supplies(Math.max(1, entry.getStepLevel())))
					{
						names.add(supply.name);
					}
					// the supplies change with level: approve every trainer level band by name
					for (int level : new int[] {15, 30, 45, 60})
					{
						for (net.runelite.client.plugins.microbot.questhelper.training.SkillTrainer.Supply supply
							: trainer.supplies(level))
						{
							names.add(supply.name);
						}
					}
				}
			}
			approval.allow(names, perStep);
		}
	}

	/** JSON-ready view of a plan for the Agent Server. */
	public static Map<String, Object> toMap(QuestRequirementResolver.Plan plan)
	{
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("quest", plan.target);
		out.put("alreadyComplete", plan.targetDone);
		out.put("etaMinutes", plan.totalEtaMinutes());
		List<Map<String, Object>> steps = new ArrayList<>();
		for (QuestRequirementResolver.Step step : plan.steps)
		{
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("kind", step.kind.name());
			row.put("name", step.name);
			if (step.kind != QuestRequirementResolver.Kind.QUEST)
			{
				row.put("fromLevel", step.fromLevel);
				row.put("level", step.level);
			}
			row.put("neededBy", step.neededBy);
			row.put("reason", step.reason);
			row.put("etaMinutes", step.etaMinutes);
			row.put("skipped", step.skipped);
			steps.add(row);
		}
		out.put("steps", steps);
		out.put("tree", plan.tree());
		out.put("blockers", plan.blockers);
		out.put("satisfiable", !plan.hasBlockers());
		return out;
	}

	// ---- the live world ----

	private static final class LiveWorld implements QuestRequirementResolver.World
	{
		private final Map<String, List<Req>> requirementCache = new HashMap<>();

		private <T> T onClient(java.util.function.Supplier<T> action, T fallback)
		{
			return Microbot.getClientThread().runOnClientThreadOptional(action::get).orElse(fallback);
		}

		@Override
		public boolean questDone(String quest)
		{
			QuestHelper helper = QuestHelperQuest.getByName(quest);
			if (helper == null)
			{
				return true; // an unknown quest cannot be planned; treat as nothing to do
			}
			return onClient(() -> helper.getState(Microbot.getClient()) == QuestState.FINISHED, false);
		}

		@Override
		public int level(String skill)
		{
			if ("COMBAT".equals(skill))
			{
				return onClient(Rs2Player::getCombatLevel, 3);
			}
			try
			{
				Skill value = Skill.valueOf(skill);
				return onClient(() -> Microbot.getClient().getRealSkillLevel(value), 1);
			}
			catch (IllegalArgumentException e)
			{
				return 1;
			}
		}

		@Override
		public int questPoints()
		{
			return onClient(() -> Microbot.getClient().getVarpValue(VarPlayerID.QP), 0);
		}

		@Override
		public List<Req> requirementsOf(String quest)
		{
			return requirementCache.computeIfAbsent(quest, name ->
			{
				QuestHelper helper = QuestHelperQuest.getByName(name);
				if (helper == null)
				{
					return Collections.emptyList();
				}
				return onClient(() ->
				{
					List<Req> out = new ArrayList<>();
					try
					{
						helper.initializeRequirements();
						List<Requirement> general = helper.getGeneralRequirements();
						if (general != null)
						{
							for (Requirement requirement : general)
							{
								if (requirement != null && requirement.shouldConsiderForFilter())
								{
									out.add(convert(requirement));
								}
							}
						}
					}
					catch (RuntimeException e)
					{
						out.add(Req.blocker("could not read the requirements"));
					}
					return out;
				}, new ArrayList<Req>());
			});
		}

		@Override
		public int questPointReward(String quest)
		{
			QuestHelper helper = QuestHelperQuest.getByName(quest);
			return helper == null || helper.getQuestPointReward() == null ? 0 : helper.getQuestPointReward().getPoints();
		}

		@Override
		public List<Candidate> doableQuests()
		{
			List<Candidate> out = new ArrayList<>();
			for (QuestPlannerFilter.Row row : QuestPlannerLauncher.buildRows())
			{
				if (!row.available)
				{
					continue;
				}
				QuestHelper helper = QuestHelperQuest.getByName(row.name);
				int points = helper == null || helper.getQuestPointReward() == null ? 0
					: helper.getQuestPointReward().getPoints();
				if (points > 0 && blockerFor(row.name) == null)
				{
					out.add(new Candidate(row.name, points, questMinutes(row.name)));
				}
			}
			return out;
		}

		@Override
		public String blockerFor(String quest)
		{
			QuestHelper helper = QuestHelperQuest.getByName(quest);
			return helper == null || helper.getQuest() == null ? null
				: QuestPreflight.anotherPlayerFailure(helper.getQuest().name(), quest);
		}

		@Override
		public int maxBoost(String skill)
		{
			return BoostCatalog.maxBoost(skill);
		}

		@Override
		public boolean canTrain(String skill)
		{
			return skillPlugins().pluginFor(skill) != null || SkillTrainerRegistry.has(skill);
		}

		@Override
		public int questMinutes(String quest)
		{
			return 20;
		}
	}

	/** Reduces a Quest Helper requirement to a {@link Req}. Client thread. */
	static Req convert(Requirement requirement)
	{
		boolean met;
		try
		{
			met = requirement.check(Microbot.getClient());
		}
		catch (RuntimeException e)
		{
			met = false;
		}
		String text = requirement.getDisplayText();
		if (requirement instanceof QuestRequirement)
		{
			QuestRequirement quest = (QuestRequirement) requirement;
			return Req.quest(quest.getQuest().getName(), met);
		}
		if (requirement instanceof SkillRequirement)
		{
			SkillRequirement skill = (SkillRequirement) requirement;
			return Req.skill(skill.getSkill().name(), skill.getRequiredLevel(), skill.isCanBeBoosted(), met);
		}
		if (requirement instanceof QuestPointRequirement)
		{
			return Req.questPoints(((QuestPointRequirement) requirement).getRequiredQuestPoints(), met);
		}
		if (requirement instanceof CombatLevelRequirement)
		{
			return Req.combat(((CombatLevelRequirement) requirement).getRequiredLevel(), met);
		}
		if (requirement instanceof Conditions)
		{
			Conditions conditions = (Conditions) requirement;
			List<Req> children = new ArrayList<>();
			for (Requirement child : conditions.getConditions())
			{
				if (child != null)
				{
					children.add(convert(child));
				}
			}
			return group(conditions.getLogicType(), text, met, children);
		}
		if (requirement instanceof ComplexRequirement)
		{
			ComplexRequirement complex = (ComplexRequirement) requirement;
			List<Req> children = new ArrayList<>();
			for (Requirement child : complex.getRequirements())
			{
				if (child != null)
				{
					children.add(convert(child));
				}
			}
			return group(complex.getLogicType(), text, met, children);
		}
		return met ? Req.all(text, true, Collections.emptyList()) : Req.blocker(text);
	}

	private static Req group(LogicType logic, String text, boolean met, List<Req> children)
	{
		if (logic == LogicType.AND)
		{
			return Req.all(text, met, children);
		}
		if (logic == LogicType.OR)
		{
			return Req.any(text, met, children);
		}
		return met ? Req.all(text, true, children) : Req.blocker(text);
	}

	/** First number in a text, or -1. */
	static int firstNumber(String text)
	{
		Matcher matcher = NUMBER.matcher(text == null ? "" : text);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
	}
}
