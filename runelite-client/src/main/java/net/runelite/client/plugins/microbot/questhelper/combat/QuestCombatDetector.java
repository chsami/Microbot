package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPCComposition;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestInventoryRules;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.BasicQuestHelper;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirements;
import net.runelite.client.plugins.microbot.questhelper.steps.DetailedQuestStep;
import net.runelite.client.plugins.microbot.questhelper.steps.NpcStep;
import net.runelite.client.plugins.microbot.questhelper.steps.OwnerStep;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;

/** Decides whether a quest has fights, which ones, and whether a step is one. */
public final class QuestCombatDetector
{
	private static final Pattern TARGET = Pattern.compile(
		"\\b(?:kill|defeat|fight|slay)\\s+(?:the\\s+|a\\s+|an\\s+|some\\s+|all\\s+|any\\s+)?(?:\\d+\\s+)?(.+?)"
			+ "(?=\\s*\\(|[.,;!:]|\\s+(?:to|for|with|in|at|on|using|until|and|so|from|by|inside|outside|near"
			+ "|before|after|while|if|when)\\b|$)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern LEVEL = Pattern.compile("\\(level\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Map<Integer, Boolean> STACKABLE = new ConcurrentHashMap<>();
	private static final int RESERVE_HEADROOM = 4;

	/** One distinct fight of a quest: every kill step against the same target. */
	public static final class Fight
	{
		public final String key;
		public final String label;
		/** Combat notes naming the target, else the step text; shown next to the fight. */
		public final List<String> notes;
		/** Notes plus step text; what the style restriction check reads. */
		public final List<String> restrictionTexts;
		/** True when the fight text or requirements say it needs a combat style swap (target prayers). */
		public boolean needsSwap;
		/** The enemy level named by the step or the combat notes, 0 when none is named. */
		public int level;

		Fight(String key, String label, List<String> notes, List<String> restrictionTexts)
		{
			this.key = key;
			this.label = label;
			this.notes = notes;
			this.restrictionTexts = restrictionTexts;
		}
	}

	private QuestCombatDetector()
	{
	}

	/** True when the step text asks the player to kill something. */
	public static boolean isKillText(List<String> text)
	{
		return net.runelite.client.plugins.microbot.questhelper.KillStepRule.isKillText(text);
	}

	/** True for an NPC step whose text asks the player to kill something. */
	public static boolean isKillStep(QuestStep step)
	{
		return step instanceof NpcStep && isKillText(step.getText());
	}

	/** True when the quest has any fight, from its combat requirements or its kill steps. */
	public static boolean hasFights(QuestHelper quest)
	{
		List<String> requirements = quest.getCombatRequirements();
		if (requirements != null && !requirements.isEmpty())
		{
			return true;
		}

		Set<QuestStep> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		if (quest instanceof BasicQuestHelper && ((BasicQuestHelper) quest).getStepList() != null
			&& containsKillStep(((BasicQuestHelper) quest).getStepList().values(), seen))
		{
			return true;
		}
		QuestStep current = quest.getCurrentStep();
		return current != null && containsKillStep(Collections.singleton(current), seen);
	}

	private static QuestHelper remainingQuest;
	private static int remainingProgress = Integer.MIN_VALUE;
	private static boolean remainingResult;

	/**
	 * Whether a kill step is still ahead: any step keyed at or above the quest's current progress (varp or varbit, read
	 * through {@link QuestHelper#getVar()}), including the substeps of conditional steps. True when the structure or the
	 * progress cannot be read. The answer is cached per quest and progress value.
	 */
	public static synchronized boolean hasFightsRemaining(QuestHelper quest)
	{
		if (quest == null)
		{
			return true;
		}
		Integer progress = progressOf(quest);
		Map<Integer, QuestStep> steps = quest instanceof BasicQuestHelper ? ((BasicQuestHelper) quest).getStepList() : null;
		if (progress == null || steps == null)
		{
			return true;
		}
		if (quest == remainingQuest && progress == remainingProgress)
		{
			return remainingResult;
		}
		Set<QuestStep> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		boolean result = containsKillStep(valuesFrom(steps, progress), seen);
		remainingQuest = quest;
		remainingProgress = progress;
		remainingResult = result;
		return result;
	}

	/**
	 * Item ids required by the quest's steps that are still ahead (the current one included), alternates too, or null
	 * when the step map or the progress cannot be read.
	 */
	public static Set<Integer> remainingStepItemIds(QuestHelper quest)
	{
		Map<Integer, QuestStep> all = quest instanceof BasicQuestHelper ? ((BasicQuestHelper) quest).getStepList() : null;
		Integer progress = all == null ? null : progressOf(quest);
		if (progress == null)
		{
			return null;
		}
		Set<Integer> ids = new HashSet<>();
		Collection<QuestStep> steps = valuesFrom(all, progress);
		Set<QuestStep> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		collectItemIds(steps, seen, ids);
		if (quest.getCurrentStep() != null)
		{
			collectItemIds(Collections.singleton(quest.getCurrentStep()), seen, ids);
		}
		return ids;
	}

	/**
	 * The detailed steps still ahead (the current one included), walked like {@link #remainingStepItemIds}, or null
	 * when the step map or the progress cannot be read.
	 */
	public static List<DetailedQuestStep> remainingDetailedSteps(QuestHelper quest)
	{
		Map<Integer, QuestStep> all = quest instanceof BasicQuestHelper ? ((BasicQuestHelper) quest).getStepList() : null;
		Integer progress = all == null ? null : progressOf(quest);
		if (progress == null)
		{
			return null;
		}
		List<DetailedQuestStep> out = new ArrayList<>();
		Set<QuestStep> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		collectDetailedSteps(valuesFrom(all, progress), seen, out);
		if (quest.getCurrentStep() != null)
		{
			collectDetailedSteps(Collections.singleton(quest.getCurrentStep()), seen, out);
		}
		return out;
	}

	private static void collectDetailedSteps(Collection<QuestStep> steps, Set<QuestStep> seen, List<DetailedQuestStep> out)
	{
		for (QuestStep step : steps)
		{
			if (step == null || !seen.add(step))
			{
				continue;
			}
			if (step instanceof DetailedQuestStep)
			{
				out.add((DetailedQuestStep) step);
			}
			if (step instanceof OwnerStep)
			{
				collectDetailedSteps(((OwnerStep) step).getSteps(), seen, out);
			}
		}
	}

	/** Adds the ids of the item requirements, including the children of an "X and Y" / "X or Y" requirement. */
	public static void addItemIds(Collection<? extends Requirement> requirements, Set<Integer> out)
	{
		if (requirements == null)
		{
			return;
		}
		for (Requirement requirement : requirements)
		{
			if (!(requirement instanceof ItemRequirement))
			{
				continue;
			}
			for (Integer id : ((ItemRequirement) requirement).getAllIds())
			{
				if (id != null && id > 0)
				{
					out.add(id);
				}
			}
			if (requirement instanceof ItemRequirements)
			{
				addItemIds(((ItemRequirements) requirement).getItemRequirements(), out);
			}
		}
	}

	private static void collectItemIds(Collection<QuestStep> steps, Set<QuestStep> seen, Set<Integer> out)
	{
		for (QuestStep step : steps)
		{
			if (step == null || !seen.add(step))
			{
				continue;
			}
			if (step instanceof DetailedQuestStep)
			{
				addItemIds(((DetailedQuestStep) step).getRequirements(), out);
			}
			if (step instanceof OwnerStep)
			{
				collectItemIds(((OwnerStep) step).getSteps(), seen, out);
			}
		}
	}

	private static Integer progressOf(QuestHelper quest)
	{
		try
		{
			Integer progress = Microbot.getClientThread().runOnClientThreadOptional(quest::getVar).orElse(null);
			return progress == null || progress < 0 ? null : progress;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	/** Entries of a progress-keyed step map whose key is at or above the current progress. */
	static <T> List<T> valuesFrom(Map<Integer, T> steps, int progress)
	{
		List<T> out = new ArrayList<>();
		for (Map.Entry<Integer, T> entry : steps.entrySet())
		{
			if (entry.getKey() != null && entry.getKey() >= progress && entry.getValue() != null)
			{
				out.add(entry.getValue());
			}
		}
		return out;
	}

	private static boolean containsKillStep(Collection<QuestStep> steps, Set<QuestStep> seen)
	{
		for (QuestStep step : steps)
		{
			if (step == null || !seen.add(step))
			{
				continue;
			}
			if (isKillStep(step))
			{
				return true;
			}
			if (step instanceof OwnerStep && containsKillStep(((OwnerStep) step).getSteps(), seen))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The highest enemy level over the quest's fights, or 0 when there are no fights or any fight has no known level
	 * (an unknown enemy is not treated as weak).
	 */
	public static int maxEnemyLevel(QuestHelper quest)
	{
		int max = 0;
		for (Fight fight : enumerateFights(quest))
		{
			if (fight.level <= 0)
			{
				return 0;
			}
			max = Math.max(max, fight.level);
		}
		return max;
	}

	/** Default food count for the quest: none when every enemy is weak, else a few. */
	public static int defaultFoodQty(QuestHelper quest)
	{
		return QuestInventoryRules.foodQtyForMaxLevel(maxEnemyLevel(quest));
	}

	/** Target name in a kill step text ("Kill the Temple Guardian (level 30)." gives "Temple Guardian"), or null. */
	static String parseTarget(List<String> text)
	{
		if (text == null)
		{
			return null;
		}
		for (String line : text)
		{
			if (line == null)
			{
				continue;
			}
			Matcher matcher = TARGET.matcher(line.replaceAll("<[^>]*>", ""));
			if (matcher.find())
			{
				String name = matcher.group(1).trim();
				if (!name.isEmpty())
				{
					return name;
				}
			}
		}
		return null;
	}

	/** The first level number found in the text, or 0 when none. */
	static int parseLevel(List<String> text)
	{
		if (text != null)
		{
			for (String line : text)
			{
				Matcher matcher = line == null ? null : LEVEL.matcher(line);
				if (matcher != null && matcher.find())
				{
					return Integer.parseInt(matcher.group(1));
				}
			}
		}
		return 0;
	}

	private static String targetName(NpcStep step)
	{
		if (step.getNpcName() != null && !step.getNpcName().trim().isEmpty())
		{
			return step.getNpcName().trim();
		}
		String parsed = parseTarget(step.getText());
		if (parsed != null)
		{
			return parsed;
		}
		try
		{
			NPCComposition composition = Microbot.getClientThread()
				.runOnClientThreadOptional(() -> Microbot.getClient().getNpcDefinition(step.getNpcID())).orElse(null);
			if (composition != null && composition.getName() != null && !composition.getName().isEmpty())
			{
				return composition.getName();
			}
		}
		catch (RuntimeException ignored)
		{
			// fall through to the generic label
		}
		return "Fight";
	}

	/**
	 * True when a step asks for a combat style swap: its text says so, or one of its requirements is the
	 * "Two combat styles" placeholder.
	 */
	public static boolean stepNeedsSwap(QuestStep step)
	{
		if (step == null)
		{
			return false;
		}
		if (QuestStyleSwap.textNeedsSwap(step.getText()))
		{
			return true;
		}
		if (step instanceof DetailedQuestStep && ((DetailedQuestStep) step).getRequirements() != null)
		{
			for (Requirement requirement : ((DetailedQuestStep) step).getRequirements())
			{
				if (requirement instanceof ItemRequirement
					&& QuestStyleSwap.isStylePlaceholder(((ItemRequirement) requirement).getName()))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Key of the fight a kill step belongs to, or null when the step is not a kill step. */
	public static String fightKeyOf(QuestStep step)
	{
		if (!isKillStep(step))
		{
			return null;
		}
		return QuestCombatLoadout.fightKey(targetName((NpcStep) step));
	}

	/** Every distinct fight in the quest's kill steps, in step order. */
	public static List<Fight> enumerateFights(QuestHelper quest)
	{
		List<NpcStep> kills = new ArrayList<>();
		Set<QuestStep> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		if (quest instanceof BasicQuestHelper && ((BasicQuestHelper) quest).getStepList() != null)
		{
			collectKillSteps(((BasicQuestHelper) quest).getStepList().values(), seen, kills);
		}
		if (quest.getCurrentStep() != null)
		{
			collectKillSteps(Collections.singleton(quest.getCurrentStep()), seen, kills);
		}

		List<String> requirements = quest.getCombatRequirements() == null
			? Collections.<String>emptyList() : quest.getCombatRequirements();
		Map<String, Fight> fights = new LinkedHashMap<>();
		for (NpcStep step : kills)
		{
			String name = targetName(step);
			String key = QuestCombatLoadout.fightKey(name);
			if (key.isEmpty())
			{
				continue;
			}
			if (fights.containsKey(key))
			{
				if (stepNeedsSwap(step))
				{
					fights.get(key).needsSwap = true;
				}
				continue;
			}
			int level = parseLevel(step.getText());
			String label = level > 0 ? name + " (level " + level + ")" : name;
			List<String> notes = new ArrayList<>();
			for (String line : requirements)
			{
				if (line != null && line.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
				{
					notes.add(line);
				}
			}
			List<String> stepText = step.getText() == null ? Collections.<String>emptyList() : step.getText();
			List<String> restriction = new ArrayList<>(notes);
			restriction.addAll(stepText);
			if (notes.isEmpty())
			{
				notes.addAll(stepText);
			}
			Fight fight = new Fight(key, label, notes, restriction);
			fight.level = level > 0 ? level : parseLevel(notes);
			fight.needsSwap = stepNeedsSwap(step) || QuestStyleSwap.textNeedsSwap(notes);
			fights.put(key, fight);
		}
		return new ArrayList<>(fights.values());
	}

	private static void collectKillSteps(Collection<QuestStep> steps, Set<QuestStep> seen, List<NpcStep> out)
	{
		for (QuestStep step : steps)
		{
			if (step == null || !seen.add(step))
			{
				continue;
			}
			if (isKillStep(step))
			{
				out.add((NpcStep) step);
			}
			if (step instanceof OwnerStep)
			{
				collectKillSteps(((OwnerStep) step).getSteps(), seen, out);
			}
		}
	}

	/** Text the style restriction check reads for one fight: matching combat notes plus the step text. */
	public static List<String> restrictionTextsFor(QuestHelper quest, QuestStep step, String key)
	{
		List<String> out = new ArrayList<>();
		if (quest != null && key != null)
		{
			for (Fight fight : enumerateFights(quest))
			{
				if (fight.key.equals(key))
				{
					out.addAll(fight.restrictionTexts);
				}
			}
		}
		if (out.isEmpty() && step != null && step.getText() != null)
		{
			out.addAll(step.getText());
		}
		return out;
	}

	/**
	 * Suggested free slots to keep for quest items: the distinct unstackable, non-equipped quest item requirements
	 * plus a little headroom for items picked up along the way.
	 */
	public static int defaultReservedSlots(QuestHelper quest)
	{
		int count = 0;
		List<ItemRequirement> requirements = quest == null ? null : quest.getItemRequirements();
		if (requirements != null)
		{
			for (ItemRequirement requirement : requirements)
			{
				if (requirement == null || requirement.mustBeEquipped() || requirement.getQuantity() <= 0
					|| requirement.getAllIds().isEmpty())
				{
					continue;
				}
				Integer id = requirement.getAllIds().get(0);
				if (id != null && id > 0 && !isStackable(id))
				{
					count++;
				}
			}
		}
		return Math.min(27, count + RESERVE_HEADROOM);
	}

	private static boolean isStackable(int id)
	{
		Boolean cached = STACKABLE.get(id);
		if (cached == null)
		{
			cached = Microbot.getClientThread().runOnClientThreadOptional(() ->
			{
				ItemComposition def = Microbot.getClient().getItemDefinition(id);
				return def != null && def.isStackable();
			}).orElse(null);
			if (cached == null)
			{
				return true;
			}
			STACKABLE.put(id, cached);
		}
		return cached;
	}
}
