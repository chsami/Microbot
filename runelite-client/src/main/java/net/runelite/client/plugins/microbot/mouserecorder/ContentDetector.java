package net.runelite.client.plugins.microbot.mouserecorder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.api.Skill;

final class ContentDetector
{
	static final int COMBAT_LINGER_TICKS = 10;
	static final int SKILLING_LINGER_TICKS = 100;
	static final int MAX_NAME = 64;

	private static final Pattern NOT_ALLOWED = Pattern.compile("[^\\p{L}\\p{N} '()\\-:.&,/]");
	private static final Pattern SPACES = Pattern.compile("\\s+");

	private static final Map<Integer, Content> INSTANCE_TEMPLATES = Map.of(
		7222, new Content("Tithe Farm", "minigame"));
	private static final Set<Skill> COMBAT_SKILLS = EnumSet.of(
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS, Skill.PRAYER);

	@Value
	static class Content
	{
		String name;
		String kind;
	}

	@Value
	static class Transition
	{
		boolean start;
		Content content;
		long startMs;
	}

	@Value
	static class Tick
	{
		boolean instanced;
		int templateRegion;
		String fightingNpc;
		Skill xpSkill;
	}

	private Content current;
	private long currentStartMs;
	private String lastNpc;
	private int npcTicksLeft;
	private Skill lastSkill;
	private int skillTicksLeft;

	List<Transition> update(Tick tick, long nowMs)
	{
		trackCombat(tick);
		trackSkilling(tick);
		Content next = pick(tick);
		List<Transition> out = new ArrayList<>(2);
		if (Objects.equals(next, current))
		{
			return out;
		}
		if (current != null)
		{
			out.add(new Transition(false, current, currentStartMs));
		}
		current = next;
		currentStartMs = nowMs;
		if (next != null)
		{
			out.add(new Transition(true, next, nowMs));
		}
		return out;
	}

	List<Transition> end()
	{
		List<Transition> out = new ArrayList<>(1);
		if (current != null)
		{
			out.add(new Transition(false, current, currentStartMs));
		}
		current = null;
		lastNpc = null;
		npcTicksLeft = 0;
		lastSkill = null;
		skillTicksLeft = 0;
		return out;
	}

	Content current()
	{
		return current;
	}

	private void trackCombat(Tick tick)
	{
		if (tick.getFightingNpc() != null)
		{
			lastNpc = tick.getFightingNpc();
			npcTicksLeft = COMBAT_LINGER_TICKS;
		}
		else if (npcTicksLeft > 0 && --npcTicksLeft == 0)
		{
			lastNpc = null;
		}
	}

	private void trackSkilling(Tick tick)
	{
		Skill skill = tick.getXpSkill();
		if (skill != null && !COMBAT_SKILLS.contains(skill))
		{
			lastSkill = skill;
			skillTicksLeft = SKILLING_LINGER_TICKS;
		}
		else if (skillTicksLeft > 0 && --skillTicksLeft == 0)
		{
			lastSkill = null;
		}
	}

	private Content pick(Tick tick)
	{
		if (tick.isInstanced())
		{
			Content known = INSTANCE_TEMPLATES.get(tick.getTemplateRegion());
			if (known != null)
			{
				return known;
			}
		}
		if (lastNpc != null)
		{
			String npc = clean(lastNpc);
			if (!npc.isEmpty())
			{
				return new Content(name("Combat: " + npc), "combat");
			}
		}
		if (lastSkill != null)
		{
			return new Content(name("Skilling: " + lastSkill.getName()), "skilling");
		}
		return null;
	}

	static String clean(String raw)
	{
		if (raw == null)
		{
			return "";
		}
		return SPACES.matcher(NOT_ALLOWED.matcher(raw).replaceAll(" ")).replaceAll(" ").trim();
	}

	private static String name(String text)
	{
		return text.length() > MAX_NAME ? text.substring(0, MAX_NAME).trim() : text;
	}
}
