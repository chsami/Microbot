package net.runelite.client.plugins.microbot.mouserecorder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.api.Skill;

final class ContentDetector
{
	static final long ACTION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);
	static final int MAX_NAME = 64;

	private static final String NIGHTMARE_ZONE = "MG_NIGHTMARE_ZONE";
	private static final Pattern NOT_ALLOWED = Pattern.compile("[^\\p{L}\\p{N} '()\\-:.&,/]");
	private static final Pattern SPACES = Pattern.compile("\\s+");
	private static final Comparator<Event> ORDER = Comparator.<Event>comparingInt(e -> e.activity.getPriority())
		.thenComparingLong(e -> e.order)
		.reversed();

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
		private long updatedMs;
		private long order;

		private Event(DiscordCatalog.Activity activity)
		{
			this.activity = activity;
		}
	}

	private final DiscordCatalog catalog;
	private final List<Event> events = new ArrayList<>();
	private long triggers;
	private Integer lastRegion;
	private DiscordCatalog.Activity lastArea;
	private Content current;
	private long currentStartMs;

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
		checkForTimeout(nowMs);
		trackArea(tick, nowMs);
		for (Skill skill : tick.getXpSkills())
		{
			trigger(catalog.skill(skill), nowMs);
		}
		Content next = events.isEmpty() ? null : content(events.get(0).activity);
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
		events.clear();
		lastRegion = null;
		lastArea = null;
		return out;
	}

	Content current()
	{
		return current;
	}

	private void trackArea(Tick tick, long nowMs)
	{
		int region = tick.isInstanced() ? tick.getTemplateRegion() : tick.getRegion();
		if (region == 0)
		{
			return;
		}
		DiscordCatalog.Activity area = catalog.region(region);
		if (area != null && NIGHTMARE_ZONE.equals(area.getId()) && tick.getPlane() == 0)
		{
			area = null;
		}
		if (lastRegion != null && lastRegion == region && Objects.equals(area, lastArea))
		{
			return;
		}
		lastRegion = region;
		lastArea = area;
		trigger(area != null ? area : catalog.inGame(), nowMs);
	}

	private void trigger(DiscordCatalog.Activity activity, long nowMs)
	{
		if (activity == null)
		{
			return;
		}
		Event event = events.stream().filter(e -> e.activity.equals(activity)).findFirst().orElse(null);
		if (event == null)
		{
			event = new Event(activity);
			events.add(event);
		}
		event.updatedMs = nowMs;
		event.order = ++triggers;
		if (activity.isShouldClear())
		{
			Event triggered = event;
			events.removeIf(e -> e != triggered && e.activity.isShouldBeCleared());
		}
		events.sort(ORDER);
	}

	private void checkForTimeout(long nowMs)
	{
		events.removeIf(e -> e.activity.isShouldBeCleared() && e.activity.isShouldTimeout() && nowMs > e.updatedMs + ACTION_TIMEOUT_MS);
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
