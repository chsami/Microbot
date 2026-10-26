package net.runelite.client.plugins.microbot.questhelper.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestRequirementResolver.Candidate;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestRequirementResolver.Kind;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestRequirementResolver.Plan;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestRequirementResolver.Req;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestRequirementResolver.Step;
import net.runelite.client.plugins.microbot.questhelper.planner.QuestRequirementResolver.World;
import org.junit.Test;

public class QuestRequirementResolverTest
{
	private static final class FakeWorld implements World
	{
		final Map<String, List<Req>> reqs = new HashMap<>();
		final Set<String> done = new HashSet<>();
		final Map<String, Integer> levels = new HashMap<>();
		final Map<String, Integer> qpRewards = new HashMap<>();
		final Map<String, String> blockers = new HashMap<>();
		final Set<String> untrainable = new HashSet<>();
		final List<Candidate> doable = new ArrayList<>();
		int questPoints;
		int maxBoost = 3;
		int doableCalls;

		@Override
		public boolean questDone(String quest)
		{
			return done.contains(quest);
		}

		@Override
		public int level(String skill)
		{
			return levels.getOrDefault(skill, 1);
		}

		@Override
		public int questPoints()
		{
			return questPoints;
		}

		@Override
		public List<Req> requirementsOf(String quest)
		{
			return reqs.getOrDefault(quest, Collections.emptyList());
		}

		@Override
		public int questPointReward(String quest)
		{
			return qpRewards.getOrDefault(quest, 0);
		}

		@Override
		public List<Candidate> doableQuests()
		{
			doableCalls++;
			return doable;
		}

		@Override
		public String blockerFor(String quest)
		{
			return blockers.get(quest);
		}

		@Override
		public int maxBoost(String skill)
		{
			return maxBoost;
		}

		@Override
		public boolean canTrain(String skill)
		{
			return !untrainable.contains(skill);
		}

		@Override
		public int questMinutes(String quest)
		{
			return 20;
		}
	}

	private static List<String> names(Plan plan)
	{
		List<String> out = new ArrayList<>();
		for (Step step : plan.steps)
		{
			out.add(step.kind == Kind.QUEST ? step.name : step.kind + " " + step.name);
		}
		return out;
	}

	@Test
	public void ordersPrerequisitesFirstAndDeduplicates()
	{
		FakeWorld w = new FakeWorld();
		w.reqs.put("C", Arrays.asList(Req.quest("A", false), Req.quest("B", false)));
		w.reqs.put("B", Arrays.asList(Req.quest("A", false)));
		Plan plan = QuestRequirementResolver.resolve("C", w, null);
		assertEquals(Arrays.asList("A", "B", "C"), names(plan));
		assertTrue(plan.hasPrerequisites());
		assertFalse(plan.hasBlockers());
		assertEquals("C  (~20 min)", plan.tree().get(0));
		assertEquals(3, plan.tree().size());
	}

	@Test
	public void finishedPrerequisitesAndMetRequirementsAreLeftOut()
	{
		FakeWorld w = new FakeWorld();
		w.done.add("A");
		w.reqs.put("C", Arrays.asList(Req.quest("A", true), Req.skill("MINING", 15, false, true)));
		Plan plan = QuestRequirementResolver.resolve("C", w, null);
		assertEquals(Collections.singletonList("C"), names(plan));
		assertFalse(plan.hasPrerequisites());
	}

	@Test
	public void targetAlreadyDoneHasNoSteps()
	{
		FakeWorld w = new FakeWorld();
		w.done.add("C");
		Plan plan = QuestRequirementResolver.resolve("C", w, null);
		assertTrue(plan.targetDone);
		assertTrue(plan.steps.isEmpty());
	}

	@Test
	public void cyclesDoNotLoopForever()
	{
		FakeWorld w = new FakeWorld();
		w.reqs.put("A", Arrays.asList(Req.quest("B", false)));
		w.reqs.put("B", Arrays.asList(Req.quest("A", false)));
		Plan plan = QuestRequirementResolver.resolve("A", w, null);
		assertEquals("A", plan.steps.get(plan.steps.size() - 1).name);
	}

	@Test
	public void trainsTheHighestLevelAcrossQuestsBeforeTheFirstQuestThatNeedsIt()
	{
		FakeWorld w = new FakeWorld();
		w.levels.put("COOKING", 10);
		w.reqs.put("C", Arrays.asList(Req.quest("B", false), Req.skill("COOKING", 40, false, false)));
		w.reqs.put("B", Arrays.asList(Req.skill("COOKING", 30, false, false)));
		Plan plan = QuestRequirementResolver.resolve("C", w, null);
		assertEquals(Arrays.asList("TRAIN COOKING", "B", "TRAIN COOKING", "C"), names(plan));
		assertEquals(30, plan.steps.get(0).level);
		assertEquals(40, plan.steps.get(2).level);
	}

	@Test
	public void smallBoostableGapUsesABoost()
	{
		FakeWorld w = new FakeWorld();
		w.levels.put("ATTACK", 38);
		w.reqs.put("Q", Arrays.asList(Req.skill("ATTACK", 40, true, false)));
		Plan plan = QuestRequirementResolver.resolve("Q", w, null);
		assertEquals(Arrays.asList("BOOST ATTACK", "Q"), names(plan));
	}

	@Test
	public void bigGapNonBoostableOrSharedSkillsTrain()
	{
		FakeWorld w = new FakeWorld();
		w.levels.put("ATTACK", 30);
		w.reqs.put("Q", Arrays.asList(Req.skill("ATTACK", 40, true, false)));
		assertEquals(Arrays.asList("TRAIN ATTACK", "Q"), names(QuestRequirementResolver.resolve("Q", w, null)));

		w.levels.put("ATTACK", 38);
		w.reqs.put("Q", Arrays.asList(Req.skill("ATTACK", 40, false, false)));
		assertEquals(Arrays.asList("TRAIN ATTACK", "Q"), names(QuestRequirementResolver.resolve("Q", w, null)));

		w.reqs.put("Q", Arrays.asList(Req.skill("ATTACK", 40, true, false), Req.skill("ATTACK", 39, true, false)));
		Plan shared = QuestRequirementResolver.resolve("Q", w, null);
		assertFalse(names(shared).contains("BOOST ATTACK"));
	}

	@Test
	public void boostDecisionRespectsThresholdAndCatalogue()
	{
		assertTrue(QuestRequirementResolver.preferBoost(37, 40, true, 3, 3));
		assertFalse(QuestRequirementResolver.preferBoost(36, 40, true, 3, 3));
		assertFalse(QuestRequirementResolver.preferBoost(37, 40, true, 0, 3));
		assertFalse(QuestRequirementResolver.preferBoost(37, 40, false, 5, 3));
		assertFalse(QuestRequirementResolver.preferBoost(40, 40, true, 5, 3));
		FakeWorld w = new FakeWorld();
		w.levels.put("ATTACK", 38);
		w.reqs.put("Q", Arrays.asList(Req.skill("ATTACK", 40, true, false)));
		QuestRequirementResolver.Options off = new QuestRequirementResolver.Options();
		off.allowBoost = false;
		assertEquals(Arrays.asList("TRAIN ATTACK", "Q"), names(QuestRequirementResolver.resolve("Q", w, off)));
	}

	@Test
	public void questPointsAreFilledWithTheCheapestDoableQuests()
	{
		FakeWorld w = new FakeWorld();
		w.questPoints = 6;
		w.reqs.put("T", Arrays.asList(Req.questPoints(10, false)));
		w.doable.add(new Candidate("Big", 5, 30));
		w.doable.add(new Candidate("Two", 2, 3));
		w.doable.add(new Candidate("One", 1, 5));
		Plan plan = QuestRequirementResolver.resolve("T", w, null);
		assertEquals(Arrays.asList("Two", "One", "Big", "T"), names(plan));
		assertFalse(plan.hasBlockers());
	}

	@Test
	public void questPointFillIsNotNeededWhenPrerequisitesGiveEnough()
	{
		FakeWorld w = new FakeWorld();
		w.questPoints = 8;
		w.reqs.put("T", Arrays.asList(Req.quest("P", false), Req.questPoints(10, false)));
		w.qpRewards.put("P", 2);
		w.doable.add(new Candidate("P", 2, 1));
		w.doable.add(new Candidate("X", 1, 2));
		Plan plan = QuestRequirementResolver.resolve("T", w, null);
		assertEquals(Arrays.asList("P", "T"), names(plan));
		assertEquals("the doable list is only built when it is needed", 0, w.doableCalls);
	}

	@Test
	public void notEnoughQuestPointsIsABlocker()
	{
		FakeWorld w = new FakeWorld();
		w.questPoints = 6;
		w.reqs.put("T", Arrays.asList(Req.questPoints(40, false)));
		w.doable.add(new Candidate("One", 1, 5));
		Plan plan = QuestRequirementResolver.resolve("T", w, null);
		assertTrue(plan.hasBlockers());
		assertTrue(plan.blockers.get(0).contains("40 quest points"));
	}

	@Test
	public void unautomatableRequirementsAreBlockers()
	{
		FakeWorld w = new FakeWorld();
		w.reqs.put("T", Arrays.asList(Req.blocker("Medium Varrock diary"), Req.quest("P", false)));
		w.blockers.put("P", "P needs another player to hold the rope");
		Plan plan = QuestRequirementResolver.resolve("T", w, null);
		assertTrue(plan.hasBlockers());
		assertTrue(plan.blockers.contains("T: Medium Varrock diary"));
		assertTrue(plan.blockers.contains("P needs another player to hold the rope"));
	}

	@Test
	public void skillWithoutATrainerIsABlockerWithAClearMessage()
	{
		FakeWorld w = new FakeWorld();
		w.untrainable.add("FARMING");
		w.reqs.put("T", Arrays.asList(Req.skill("FARMING", 30, false, false)));
		Plan plan = QuestRequirementResolver.resolve("T", w, null);
		assertTrue(plan.hasBlockers());
		assertTrue(plan.blockers.get(0).contains("Farming"));
		assertTrue(plan.blockers.get(0).contains("Hub plugin"));
	}

	@Test
	public void alternativesPickTheFirstDoableOneOrSkipWhenOneIsMet()
	{
		FakeWorld w = new FakeWorld();
		Req any = Req.any("40 Agility or 40 Strength", false,
			Arrays.asList(Req.skill("AGILITY", 40, false, false), Req.skill("STRENGTH", 40, false, false)));
		w.reqs.put("T", Arrays.asList(any));
		assertEquals(Arrays.asList("TRAIN AGILITY", "T"), names(QuestRequirementResolver.resolve("T", w, null)));

		Req met = Req.any("either", false,
			Arrays.asList(Req.skill("AGILITY", 40, false, false), Req.skill("STRENGTH", 40, false, true)));
		w.reqs.put("T", Arrays.asList(met));
		assertEquals(Collections.singletonList("T"), names(QuestRequirementResolver.resolve("T", w, null)));

		w.reqs.put("T", Arrays.asList(Req.any("only blockers", false, Arrays.asList(Req.blocker("x")))));
		assertTrue(QuestRequirementResolver.resolve("T", w, null).hasBlockers());
	}

	@Test
	public void nestedAllRequirementsAreExpanded()
	{
		FakeWorld w = new FakeWorld();
		w.reqs.put("T", Arrays.asList(Req.all("both", false,
			Arrays.asList(Req.quest("A", false), Req.skill("MINING", 20, false, false)))));
		assertEquals(Arrays.asList("A", "TRAIN MINING", "T"), names(QuestRequirementResolver.resolve("T", w, null)));
	}

	@Test
	public void combatLevelIsTrainedLikeASkill()
	{
		FakeWorld w = new FakeWorld();
		w.levels.put("COMBAT", 20);
		w.reqs.put("T", Arrays.asList(Req.combat(40, false)));
		Plan plan = QuestRequirementResolver.resolve("T", w, null);
		assertEquals(Arrays.asList("TRAIN COMBAT", "T"), names(plan));
	}

	@Test
	public void theSelectedQuestIsAlwaysLastAndStepsHaveDepth()
	{
		FakeWorld w = new FakeWorld();
		w.reqs.put("T", Arrays.asList(Req.quest("A", false), Req.skill("MINING", 20, false, false)));
		Plan plan = QuestRequirementResolver.resolve("T", w, null);
		Step last = plan.steps.get(plan.steps.size() - 1);
		assertEquals("T", last.name);
		assertEquals(0, last.depth);
		assertTrue(plan.steps.get(0).depth >= 1);
		assertTrue(plan.totalEtaMinutes() > 0);
	}
}
