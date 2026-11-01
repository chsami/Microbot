package net.runelite.client.plugins.microbot.diagnostics;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.util.Text;

@Singleton
public class FlightRecorder
{
	static final int MAX_CHARS = 240 * 1024;
	static final long MAX_AGE_MILLIS = 20 * 60 * 1000L;
	static final int MAX_LINE_CHARS = 300;
	static final int MAX_VARS_PER_TICK = 30;
	static final int MAX_TRACES = 500;
	static final int STUCK_TICKS = 50;
	static final int MAX_STACK_FRAMES = 2;
	static final int VARS_REBUILD_SIZE = 256;
	static final String LEGEND = "# tick time | p=x,y,plane a=animation t=target hp pr=prayer run gs=game state | inv/eq +id*n -id*n"
		+ " | v<id>/vp<id>=varbit/varp | w+/w- interface | msg game message | click option>target (w<group> = interface click, player = another player)"
		+ " | st=Microbot status | scripts+/- | trace:<script>.<key> | out/err script prints | ! no game change while a script runs";
	private static final Pattern LOG_LINE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.S");
	private static final Set<ChatMessageType> GAME_MESSAGES = Set.of(ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM,
		ChatMessageType.ENGINE, ChatMessageType.MESBOX, ChatMessageType.DIALOG);
	private static final String[] NAMED_TARGETS = {"NPC_", "GAME_OBJECT_", "GROUND_ITEM_", "EXAMINE_NPC", "EXAMINE_OBJECT",
		"EXAMINE_ITEM_GROUND", "WIDGET_TARGET_ON_NPC", "WIDGET_TARGET_ON_GAME_OBJECT", "WIDGET_TARGET_ON_GROUND_ITEM", "WALK"};

	private static volatile FlightRecorder active;

	private final Client client;
	private final EventBus eventBus;
	private final Deque<Line> lines = new ArrayDeque<>();
	Clock clock = Clock.systemDefaultZone();
	private Map<String, Integer> vars = new LinkedHashMap<>();
	private final Map<String, String> traces = new LinkedHashMap<String, String>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest)
		{
			return size() > MAX_TRACES;
		}
	};
	private final Map<Integer, Map<Integer, Integer>> containers = new HashMap<>();
	private int chars;
	private int tick;
	private PlayerState last;
	private String status;
	private Set<String> scripts = new TreeSet<>();
	private int lastChangeTick;
	private int clicksSinceChange;
	private boolean stuckMarked;
	private String lastPrint;
	private int repeats;
	private int stackFrames;
	private PrintStream originalOut;
	private PrintStream originalErr;
	private PrintStream teeOut;
	private PrintStream teeErr;

	@Inject
	FlightRecorder(Client client, EventBus eventBus)
	{
		this.client = client;
		this.eventBus = eventBus;
	}

	public void start()
	{
		reset();
		eventBus.register(this);
		originalOut = System.out;
		originalErr = System.err;
		teeOut = new PrintStream(new Tee(originalOut, "out", StandardCharsets.UTF_8), true, StandardCharsets.UTF_8);
		Charset errCharset = stderrCharset();
		teeErr = new PrintStream(new Tee(originalErr, "err", errCharset), true, errCharset);
		System.setOut(teeOut);
		System.setErr(teeErr);
		active = this;
	}

	public void stop()
	{
		active = null;
		eventBus.unregister(this);
		if (System.out == teeOut)
		{
			System.setOut(originalOut);
		}
		if (System.err == teeErr)
		{
			System.setErr(originalErr);
		}
		reset();
	}

	public static void trace(String key, Object value)
	{
		FlightRecorder recorder = active;
		if (recorder != null && key != null)
		{
			recorder.recordTrace(key, clip(String.valueOf(value)));
		}
	}

	public synchronized String dump()
	{
		flushRepeats();
		dropOld();
		StringBuilder out = new StringBuilder(chars + LEGEND.length() + 1).append(LEGEND).append('\n');
		for (Line line : lines)
		{
			out.append(line.text).append('\n');
		}
		return out.toString();
	}

	public static String withoutLegend(String dump)
	{
		return dump.startsWith("#") && dump.indexOf('\n') >= 0 ? dump.substring(dump.indexOf('\n') + 1) : dump;
	}

	public static String withoutErrors(String dump)
	{
		StringBuilder out = new StringBuilder(dump.length());
		boolean dropped = false;
		int start = 0;
		while (start < dump.length())
		{
			int end = dump.indexOf('\n', start);
			int next = end < 0 ? dump.length() : end + 1;
			int text = textStart(dump, start, end < 0 ? dump.length() : end);
			boolean error = text >= 0 && dump.startsWith("err=\"", text);
			boolean repeat = text >= 0 && dump.startsWith("(previous line repeated ", text);
			if (start == (end < 0 ? dump.length() : end))
			{
				start = next;
				continue;
			}
			if (error || (repeat && dropped))
			{
				dropped = true;
			}
			else
			{
				dropped = false;
				out.append(dump, start, end < 0 ? dump.length() : end).append('\n');
			}
			start = next;
		}
		return out.toString();
	}

	private static int textStart(String dump, int start, int end)
	{
		int tick = start;
		while (tick < end && Character.isDigit(dump.charAt(tick)))
		{
			tick++;
		}
		if (tick == start || tick >= end || dump.charAt(tick) != ' ')
		{
			return -1;
		}
		int time = dump.indexOf(' ', tick + 1);
		return time < 0 || time >= end ? -1 : time + 1;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick(client.getTickCount(), readPlayer(), Microbot.status, simpleNames(ScriptHeartbeatRegistry.scriptNames()),
			Microbot.pauseAllScripts.get() || InputArbiter.isHuman());
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		recordVar(event.getVarbitId() != -1 ? "v" + event.getVarbitId() : "vp" + event.getVarpId(), event.getValue());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (GAME_MESSAGES.contains(event.getType()))
		{
			record("msg=\"" + clip(Text.removeTags(event.getMessage())) + "\"", true);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String action = event.getMenuAction() == null ? "?" : event.getMenuAction().name();
		recordClick(Text.removeTags(event.getMenuOption()), clickTarget(action, event.getItemId(), event.getParam1(),
			Text.removeTags(event.getMenuTarget())), event.getId(), action);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		record("gs=" + event.getGameState(), true);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		record("w+" + event.getGroupId(), true);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		record("w-" + event.getGroupId(), true);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			recordContainer(id == InventoryID.INV ? "inv" : "eq", id, quantities(event.getItemContainer()));
		}
	}

	static String clickTarget(String action, int itemId, int widgetId, String target)
	{
		if (action.contains("PLAYER"))
		{
			return "player";
		}
		for (String prefix : NAMED_TARGETS)
		{
			if (action.startsWith(prefix))
			{
				return target;
			}
		}
		if (itemId > 0)
		{
			return target;
		}
		return widgetId > 0 ? "w" + (widgetId >>> 16) : "-";
	}

	synchronized void tick(int tick, PlayerState player, String status, Set<String> running, boolean paused)
	{
		this.tick = tick;
		flushVars();
		List<String> parts = new ArrayList<>();
		if (player != null)
		{
			PlayerState before = last;
			boolean first = before == null;
			boolean changed = false;
			if (first || !Objects.equals(before.location, player.location))
			{
				parts.add("p=" + player.location);
				changed = true;
			}
			if (first || before.animation != player.animation)
			{
				parts.add("a=" + player.animation);
				changed = true;
			}
			if (first ? player.target != null : !Objects.equals(before.target, player.target))
			{
				parts.add("t=" + (player.target == null ? "-" : player.target));
				changed = true;
			}
			if (first || before.hitpoints != player.hitpoints)
			{
				parts.add("hp=" + player.hitpoints);
				changed = true;
			}
			if (first || before.prayer != player.prayer)
			{
				parts.add("pr=" + player.prayer);
			}
			if (first || Math.abs(before.runEnergy - player.runEnergy) >= 5 || (player.runEnergy == 0) != (before.runEnergy == 0))
			{
				parts.add("run=" + player.runEnergy);
			}
			if (changed)
			{
				markChange();
			}
			last = player;
		}
		if (!Objects.equals(this.status, status))
		{
			parts.add("st=\"" + clip(status) + "\"");
			this.status = status;
		}
		for (String name : running)
		{
			if (!scripts.contains(name))
			{
				parts.add("scripts+" + name);
				markChange();
			}
		}
		for (String name : scripts)
		{
			if (!running.contains(name))
			{
				parts.add("scripts-" + name);
				traces.keySet().removeIf(key -> key.startsWith(name + "."));
			}
		}
		scripts = new TreeSet<>(running);
		if (!parts.isEmpty())
		{
			append(String.join(" ", parts));
		}
		if (paused)
		{
			markChange();
		}
		else if (!scripts.isEmpty() && !stuckMarked && tick - lastChangeTick >= STUCK_TICKS)
		{
			append("! no game change for " + (tick - lastChangeTick) + " ticks while " + String.join(",", scripts) + " runs, "
				+ clicksSinceChange + " clicks meanwhile");
			stuckMarked = true;
		}
	}

	synchronized void recordVar(String key, int value)
	{
		vars.remove(key);
		vars.put(key, value);
	}

	synchronized void recordClick(String option, String target, int id, String action)
	{
		clicksSinceChange++;
		append("click=" + option + ">" + target + "(" + id + "," + action + ")");
	}

	synchronized void recordContainer(String label, int containerId, Map<Integer, Integer> now)
	{
		Map<Integer, Integer> before = containers.put(containerId, now);
		if (before == null)
		{
			append(label + "=" + now.entrySet().stream().map(e -> e.getKey() + "*" + e.getValue()).reduce((a, b) -> a + "," + b).orElse("empty"));
			return;
		}
		String diff = diff(before, now);
		if (!diff.isEmpty())
		{
			append(label + " " + diff);
			markChange();
		}
	}

	synchronized void recordTrace(String key, String text)
	{
		if (!text.equals(traces.put(key, text)))
		{
			append("trace:" + key + "=" + text);
		}
	}

	synchronized void record(String text, boolean change)
	{
		append(text);
		if (change)
		{
			markChange();
		}
	}

	synchronized void recordPrint(String stream, String line)
	{
		String text = line.strip();
		if (text.isEmpty())
		{
			return;
		}
		if (text.startsWith("at ") || text.startsWith("... "))
		{
			if (++stackFrames > MAX_STACK_FRAMES)
			{
				return;
			}
		}
		else
		{
			stackFrames = 0;
		}
		String entry = stream + "=\"" + clip(text) + "\"";
		if (entry.equals(lastPrint))
		{
			repeats++;
			return;
		}
		flushRepeats();
		lastPrint = entry;
		appendLine(entry);
	}

	static String diff(Map<Integer, Integer> before, Map<Integer, Integer> now)
	{
		StringBuilder out = new StringBuilder();
		Set<Integer> ids = new TreeSet<>(before.keySet());
		ids.addAll(now.keySet());
		for (int id : ids)
		{
			int delta = now.getOrDefault(id, 0) - before.getOrDefault(id, 0);
			if (delta != 0)
			{
				out.append(out.length() == 0 ? "" : " ").append(delta > 0 ? "+" : "-").append(id).append('*').append(Math.abs(delta));
			}
		}
		return out.toString();
	}

	private static Charset stderrCharset()
	{
		try
		{
			String name = System.getProperty("sun.stderr.encoding");
			return name == null ? Charset.defaultCharset() : Charset.forName(name);
		}
		catch (RuntimeException e)
		{
			return Charset.defaultCharset();
		}
	}

	static boolean isFrame(byte[] line, int length)
	{
		int i = 0;
		while (i < length && (line[i] == ' ' || line[i] == '\t'))
		{
			i++;
		}
		return (i + 3 <= length && line[i] == 'a' && line[i + 1] == 't' && line[i + 2] == ' ')
			|| (i + 4 <= length && line[i] == '.' && line[i + 1] == '.' && line[i + 2] == '.' && line[i + 3] == ' ');
	}

	static boolean isLogLine(String chunk)
	{
		return LOG_LINE.matcher(chunk).find();
	}

	private synchronized void reset()
	{
		vars.clear();
		traces.clear();
		containers.clear();
		last = null;
		status = null;
		scripts = new TreeSet<>();
		lastChangeTick = tick;
		clicksSinceChange = 0;
		stuckMarked = false;
		flushRepeats();
		lastPrint = null;
		stackFrames = 0;
	}

	private void flushVars()
	{
		if (vars.isEmpty())
		{
			return;
		}
		StringBuilder out = new StringBuilder();
		int shown = 0;
		for (Map.Entry<String, Integer> e : vars.entrySet())
		{
			if (shown++ == MAX_VARS_PER_TICK)
			{
				out.append(" +").append(vars.size() - MAX_VARS_PER_TICK).append(" vars");
				break;
			}
			out.append(out.length() == 0 ? "" : " ").append(e.getKey()).append('=').append(e.getValue());
		}
		if (vars.size() > VARS_REBUILD_SIZE)
		{
			vars = new LinkedHashMap<>();
		}
		else
		{
			vars.clear();
		}
		append(out.toString());
	}

	private void flushRepeats()
	{
		if (repeats > 0)
		{
			appendLine("(previous line repeated " + repeats + " more times)");
			repeats = 0;
		}
	}

	private void markChange()
	{
		lastChangeTick = tick;
		clicksSinceChange = 0;
		stuckMarked = false;
	}

	private void append(String text)
	{
		flushRepeats();
		lastPrint = null;
		appendLine(text);
	}

	private void appendLine(String text)
	{
		long now = clock.millis();
		Line line = new Line(now, tick + " " + LocalTime.ofInstant(Instant.ofEpochMilli(now), clock.getZone()).format(TIME) + " " + text);
		lines.addLast(line);
		chars += line.text.length() + 1;
		while (chars > MAX_CHARS && lines.size() > 1)
		{
			chars -= lines.removeFirst().text.length() + 1;
		}
		dropOld();
	}

	private void dropOld()
	{
		long cutoff = clock.millis() - MAX_AGE_MILLIS;
		while (!lines.isEmpty() && lines.peekFirst().at < cutoff)
		{
			chars -= lines.removeFirst().text.length() + 1;
		}
	}

	private static String clip(String text)
	{
		if (text == null)
		{
			return "";
		}
		String flat = text.replace('\n', ' ').replace('\r', ' ');
		return flat.length() > MAX_LINE_CHARS ? flat.substring(0, MAX_LINE_CHARS) + "..." : flat;
	}

	private PlayerState readPlayer()
	{
		Player player = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || player == null || player.getLocalLocation() == null)
		{
			return null;
		}
		WorldPoint point = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		Actor target = player.getInteracting();
		String targetText = target instanceof NPC ? Text.removeTags(target.getName()) + "#" + ((NPC) target).getId()
			: target != null ? "player" : null;
		return new PlayerState(point == null ? null : point.getX() + "," + point.getY() + "," + point.getPlane(), player.getAnimation(), targetText,
			client.getBoostedSkillLevel(Skill.HITPOINTS), client.getBoostedSkillLevel(Skill.PRAYER), client.getEnergy() / 100);
	}

	private static Map<Integer, Integer> quantities(ItemContainer container)
	{
		Map<Integer, Integer> items = new LinkedHashMap<>();
		if (container == null)
		{
			return items;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				items.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return items;
	}

	private static Set<String> simpleNames(Set<String> classNames)
	{
		Set<String> names = new TreeSet<>();
		for (String name : classNames)
		{
			names.add(name.substring(name.lastIndexOf('.') + 1));
		}
		return names;
	}

	@Value
	private static class Line
	{
		long at;
		String text;
	}

	@Value
	static class PlayerState
	{
		String location;
		int animation;
		String target;
		int hitpoints;
		int prayer;
		int runEnergy;
	}

	private static final class LineBuffer extends ByteArrayOutputStream
	{
		byte[] bytes()
		{
			return buf;
		}
	}

	private final class Tee extends OutputStream
	{
		private final PrintStream target;
		private final String stream;
		private final Charset charset;
		private final ThreadLocal<LineBuffer> pending = ThreadLocal.withInitial(LineBuffer::new);
		private final ThreadLocal<Boolean> skipping = ThreadLocal.withInitial(() -> false);
		private final ThreadLocal<int[]> frames = ThreadLocal.withInitial(() -> new int[1]);

		Tee(PrintStream target, String stream, Charset charset)
		{
			this.target = target;
			this.stream = stream;
			this.charset = charset;
		}

		@Override
		public void write(int b)
		{
			target.write(b);
			capture(new byte[]{(byte) b}, 0, 1);
		}

		@Override
		public void write(byte[] b, int off, int len)
		{
			target.write(b, off, len);
			capture(b, off, len);
		}

		@Override
		public void flush()
		{
			target.flush();
		}

		private void capture(byte[] b, int off, int len)
		{
			if (active != FlightRecorder.this)
			{
				return;
			}
			LineBuffer line = pending.get();
			List<String> done = null;
			if (len >= 19 && b[off] >= '0' && b[off] <= '9' && b[off + 4] == '-'
				&& isLogLine(new String(b, off, Math.min(len, 32), StandardCharsets.ISO_8859_1)))
			{
				if (line.size() > 0)
				{
					done = new ArrayList<>();
					done.add(line.toString(charset));
					line.reset();
				}
				skipping.set(true);
			}
			int end = off + len;
			int from = off;
			while (from < end)
			{
				int newline = from;
				while (newline < end && b[newline] != '\n')
				{
					newline++;
				}
				int room = MAX_LINE_CHARS * 4 - line.size();
				if (room > 0)
				{
					line.write(b, from, Math.min(room, newline - from));
				}
				if (newline == end)
				{
					break;
				}
				if (!skipping.get() && keep(line))
				{
					if (done == null)
					{
						done = new ArrayList<>();
					}
					done.add(line.toString(charset));
				}
				line.reset();
				skipping.set(skipping.get() && newline + 1 < end);
				from = newline + 1;
			}
			if (done != null)
			{
				for (String text : done)
				{
					recordPrint(stream, text);
				}
			}
		}

		private boolean keep(LineBuffer line)
		{
			int[] count = frames.get();
			if (!isFrame(line.bytes(), line.size()))
			{
				count[0] = 0;
				return true;
			}
			return ++count[0] <= MAX_STACK_FRAMES;
		}
	}
}
