package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestInfo;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestProfile;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestQueue;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestRequirementPlanner;
import net.runelite.client.plugins.microbot.questhelper.training.BoostCatalog;
import net.runelite.client.plugins.microbot.questhelper.training.HubPluginTrainer;
import net.runelite.client.plugins.microbot.questhelper.training.SkillTrainer;
import net.runelite.client.plugins.microbot.questhelper.training.SkillTrainerRegistry;
import net.runelite.client.plugins.microbot.questhelper.training.SupplyBuyer;
import net.runelite.client.plugins.microbot.questhelper.training.TrainerContext;
import net.runelite.client.plugins.microbot.questhelper.training.TrainingSession;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.slf4j.event.Level;

/**
 * Runs the training and boost entries a requirement plan put into the quest queue. A step is DONE when its level is
 * reached (also when it already was: the plan is re-evaluated at every step) and FAILED when nothing can train the
 * skill, a purchase is not allowed or the time limit passes. Purchases follow the queue's approval and, in Fully
 * automated mode, the max spend per quest.
 */
public final class RequirementStepRunner
{
	public enum Result
	{
		WORKING, DONE, FAILED
	}

	/** Longest a single training step may take. */
	static final long MAX_TRAINING_MS = 6L * 60 * 60 * 1000;

	private final QuestQueueRunner runner;
	private String activeKey = "";
	private TrainingSession session;
	private SupplyBuyer boostBuyer = new SupplyBuyer();
	private String failure = "";
	private long spentOnStep;

	public RequirementStepRunner(QuestQueueRunner runner)
	{
		this.runner = runner;
	}

	public String failure()
	{
		return failure;
	}

	/** One tick of the step entry. */
	public Result tick(QuestProfile step)
	{
		String key = step.getStepKind() + ":" + step.getStepSkill() + ":" + step.getStepLevel() + ":" + step.getStepFor();
		if (!key.equals(activeKey))
		{
			activeKey = key;
			session = null;
			boostBuyer = new SupplyBuyer();
			spentOnStep = 0;
			failure = "";
		}
		if (levelOf(step.getStepSkill(), "BOOST".equals(step.getStepKind())) >= step.getStepLevel())
		{
			Microbot.log("Quest queue: " + step.getQuestName() + " is already met", Level.INFO);
			finishStep();
			return Result.DONE;
		}
		return "BOOST".equals(step.getStepKind()) ? boost(step) : train(step);
	}

	/** The queue stopped: stop whatever the current step started. */
	public void abort()
	{
		if (session != null)
		{
			session.abort();
			session = null;
		}
		activeKey = "";
	}

	private void finishStep()
	{
		if (session != null)
		{
			session.tick(); // lets the trainer (and a Hub plugin) stop at the target
		}
		session = null;
		activeKey = "";
	}

	private Result train(QuestProfile step)
	{
		String skill = step.getStepSkill();
		if (session == null)
		{
			SkillTrainer trainer = trainerFor(skill, levelOf(skill, false));
			if (trainer == null)
			{
				failure = "No trainer or Hub plugin for " + skill + ": map one in the Quest Helper settings";
				return Result.FAILED;
			}
			Microbot.log("Quest queue: " + step.getQuestName() + " with " + trainer.name(), Level.INFO);
			session = new TrainingSession(skill, step.getStepLevel(), trainer, () -> levelOf(skill, false),
				System::currentTimeMillis, MAX_TRAINING_MS, context(step));
		}
		Microbot.status = "Quest queue: " + step.getQuestName();
		TrainingSession.State state = session.tick();
		if (state == TrainingSession.State.REACHED)
		{
			Microbot.log("Quest queue: " + step.getQuestName() + " reached", Level.INFO);
			session = null;
			activeKey = "";
			return Result.DONE;
		}
		if (state == TrainingSession.State.FAILED)
		{
			failure = session.message();
			session = null;
			return Result.FAILED;
		}
		return Result.WORKING;
	}

	private Result boost(QuestProfile step)
	{
		BoostCatalog.Boost boost = BoostCatalog.of(step.getStepSkill());
		if (boost == null)
		{
			failure = "No boost known for " + step.getStepSkill();
			return Result.FAILED;
		}
		if (!Rs2Inventory.contains(boost.item))
		{
			List<SkillTrainer.Supply> want = new ArrayList<>();
			want.add(new SkillTrainer.Supply(boost.item, boost.id, 1));
			SupplyBuyer.Status status = boostBuyer.ensure(want, context(step));
			if (status == SupplyBuyer.Status.FAILED)
			{
				failure = boostBuyer.message();
				return Result.FAILED;
			}
			return Result.WORKING;
		}
		Rs2Inventory.interact(boost.item, "Drink");
		net.runelite.client.plugins.microbot.util.Global.sleepUntil(
			() -> levelOf(step.getStepSkill(), true) >= step.getStepLevel(), 3_000);
		if (levelOf(step.getStepSkill(), true) >= step.getStepLevel())
		{
			activeKey = "";
			return Result.DONE;
		}
		failure = "The " + boost.item + " did not raise " + step.getStepSkill() + " to " + step.getStepLevel();
		return Result.FAILED;
	}

	private SkillTrainer trainerFor(String skill, int level)
	{
		String plugin = QuestRequirementPlanner.skillPlugins().pluginFor(skill);
		if (plugin != null)
		{
			return new HubPluginTrainer(skill, plugin, HubPluginTrainer.LIVE);
		}
		return SkillTrainerRegistry.find(skill, level);
	}

	/** Purchases follow the queue approval; in Fully automated mode also the max spend per quest. */
	private TrainerContext context(QuestProfile step)
	{
		return new TrainerContext()
		{
			@Override
			public int level()
			{
				return levelOf(step.getStepSkill(), false);
			}

			@Override
			public boolean mayBuy(List<String> itemNames, long totalGp)
			{
				QuestQueue queue = QuestQueue.shared();
				QuestProfile target = queue.savedProfile(step.getStepFor());
				if (target != null && !target.geAllowed())
				{
					return false;
				}
				if (Rs2Player.isIronman() || !runner.hasApproval() || !runner.covers(itemNames, totalGp))
				{
					return false;
				}
				if (configBool("fullyAutomated"))
				{
					long limit = configLong("maxSpendPerQuest", 100_000);
					QuestSpendRules.Decision decision = QuestSpendRules.decide(spentOnStep + totalGp, limit,
						QuestInfo.coins());
					if (decision != QuestSpendRules.Decision.BUY)
					{
						Microbot.log("Quest queue: " + step.getQuestName() + " would cost about " + totalGp
							+ " gp (" + decision + "), not buying", Level.WARN);
						return false;
					}
				}
				return true;
			}

			@Override
			public void spent(long totalGp)
			{
				spentOnStep += totalGp;
				runner.recordSpend(totalGp);
			}
		};
	}

	private static int levelOf(String skill, boolean boosted)
	{
		if ("COMBAT".equals(skill))
		{
			return Rs2Player.getCombatLevel();
		}
		try
		{
			Skill value = Skill.valueOf(skill);
			return boosted ? Rs2Player.getBoostedSkillLevel(value) : Rs2Player.getRealSkillLevel(value);
		}
		catch (IllegalArgumentException e)
		{
			return 1;
		}
	}

	private static boolean configBool(String key)
	{
		net.runelite.client.config.ConfigManager manager = Microbot.getConfigManager();
		return manager != null
			&& "true".equals(manager.getConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, key));
	}

	private static long configLong(String key, long fallback)
	{
		net.runelite.client.config.ConfigManager manager = Microbot.getConfigManager();
		String value = manager == null ? null : manager.getConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, key);
		try
		{
			return value == null ? fallback : Long.parseLong(value.trim());
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}
}
