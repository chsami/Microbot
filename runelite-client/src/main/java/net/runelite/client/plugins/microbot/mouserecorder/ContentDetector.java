package net.runelite.client.plugins.microbot.mouserecorder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.api.Skill;

final class ContentDetector
{
	static final long ACTION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);
	static final int MAX_NAME = 64;

	private static final Set<String> CONTENT_KINDS = Set.of("boss", "minigame", "raid", "dungeon");
	private static final String NIGHTMARE_ZONE = "MG_NIGHTMARE_ZONE";
	private static final Pattern NOT_ALLOWED = Pattern.compile("[^\\p{L}\\p{N} '()\\-:.&,/]");
	private static final Pattern SPACES = Pattern.compile("\\s+");
	private static final Comparator<Event> ORDER = Comparator.<Event>comparingInt(e -> -e.activity.getPriority())
		.thenComparingLong(e -> e.order);

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
		int region;
		int plane;
		List<Skill> xpSkills;
	}

	private static final class Event
	{
		private final DiscordCatalog.Activity activity;
		private final long order;
		private long updatedMs;

		private Event(DiscordCatalog.Activity activity, long order)
		{
			this.activity = activity;
			this.order = order;
		}
	}

	private final DiscordCatalog catalog;
	private final List<Event> trainings = new ArrayList<>();
	private long created;
	private boolean areaSeen;
	private DiscordCatalog.Activity area;
	private Content current;
	private long currentStartMs;
	private long pausedMs;
	private long pausedAt = -1;

	ContentDetector()
	{
		this(DiscordCatalog.shared());
	}

	ContentDetector(DiscordCatalog catalog)
	{
		this.catalog = catalog;
	}

	List<Transition> update(Tick tick, long nowMs)
	{
		resume(nowMs);
		long activeMs = nowMs - pausedMs;
		trainings.removeIf(e -> e.activity.isShouldBeCleared() && e.activity.isShouldTimeout() && activeMs > e.updatedMs + ACTION_TIMEOUT_MS);
		trackArea(tick);
		for (Skill skill : tick.getXpSkills())
		{
			train(catalog.skill(skill), activeMs);
		}
		Content next = pick();
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

	void pause(long nowMs)
	{
		if (pausedAt < 0)
		{
			pausedAt = nowMs;
		}
	}

	void resume(long nowMs)
	{
		if (pausedAt >= 0)
		{
			pausedMs += Math.max(0, nowMs - pausedAt);
			pausedAt = -1;
		}
	}

	List<Transition> end()
	{
		List<Transition> out = new ArrayList<>(1);
		if (current != null)
		{
			out.add(new Transition(false, current, currentStartMs));
		}
		current = null;
		trainings.clear();
		areaSeen = false;
		area = null;
		pausedMs = 0;
		pausedAt = -1;
		return out;
	}

	Content current()
	{
		return current;
	}

	private void trackArea(Tick tick)
	{
		int region = tick.isInstanced() ? tick.getTemplateRegion() : tick.getRegion();
		if (region == 0)
		{
			return;
		}
		DiscordCatalog.Activity next = catalog.region(region);
		if (next != null && (next.getKind() == null || NIGHTMARE_ZONE.equals(next.getId()) && tick.getPlane() == 0))
		{
			next = null;
		}
		if (areaSeen && Objects.equals(next, area))
		{
			return;
		}
		if (isContent(area) || isContent(next))
		{
			trainings.removeIf(e -> e.activity.isShouldBeCleared());
		}
		areaSeen = true;
		area = next;
	}

	private void train(DiscordCatalog.Activity activity, long activeMs)
	{
		if (activity == null)
		{
			return;
		}
		Event event = trainings.stream().filter(e -> e.activity.equals(activity)).findFirst().orElse(null);
		if (event == null)
		{
			event = new Event(activity, ++created);
			trainings.add(event);
			trainings.sort(ORDER);
		}
		event.updatedMs = activeMs;
	}

	private Content pick()
	{
		if (isContent(area))
		{
			return content(area);
		}
		if (!trainings.isEmpty())
		{
			return content(trainings.get(0).activity);
		}
		return area == null ? null : content(area);
	}

	private static boolean isContent(DiscordCatalog.Activity activity)
	{
		return activity != null && CONTENT_KINDS.contains(activity.getKind());
	}

	private static Content content(DiscordCatalog.Activity activity)
	{
		if (activity.getKind() == null)
		{
			return null;
		}
		String name = name(clean(activity.getName()));
		return name.isEmpty() ? null : new Content(name, activity.getKind());
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
