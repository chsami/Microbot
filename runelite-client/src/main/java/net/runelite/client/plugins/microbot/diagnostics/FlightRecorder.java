package net.runelite.client.plugins.microbot.diagnostics;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
import net.runelite.client.util.Text;

@Singleton
public class FlightRecorder
{
	static final int MAX_CHARS = 240 * 1024;
	static final int MAX_LINE_CHARS = 300;
	static final int MAX_VARS_PER_TICK = 30;
	static final int STUCK_TICKS = 50;
	static final int MAX_STACK_FRAMES = 2;
	static final String LEGEND = "# tick time | p=x,y,plane a=animation t=target hp pr=prayer run gs=game state | inv/eq +id*n -id*n"
		+ " | v<id>/vp<id>=varbit/varp | w+/w- interface | msg game message | click option>target | st=Microbot status"
		+ " | scripts+/- | trace:<script>.<key> | out/err script prints | ! no game change while a script runs";
	private static final Pattern LOG_LINE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.S");
	private static final Set<ChatMessageType> GAME_MESSAGES = Set.of(ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM,
		ChatMessageType.ENGINE, ChatMessageType.MESBOX, ChatMessageType.DIALOG);

	private static volatile FlightRecorder active;

	private final Client client;
	private final EventBus eventBus;
	private final Deque<String> lines = new ArrayDeque<>();
	private final Map<String, Integer> vars = new LinkedHashMap<>();
	private final Map<String, String> traces = new HashMap<>();
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
		eventBus.register(this);
		originalOut = System.out;
		originalErr = System.err;
		teeOut = new PrintStream(new Tee(originalOut, "out"), true, StandardCharsets.UTF_8);
		teeErr = new PrintStream(new Tee(originalErr, "err"), true, StandardCharsets.UTF_8);
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
	}

	public static void trace(String key, Object value)
	{
		FlightRecorder recorder = active;
		if (recorder != null && key != null)
		{
			recorder.recordTrace(key, value);
		}
	}

	public synchronized String dump()
	{
		flushRepeats();
		StringBuilder out = new StringBuilder(chars + LEGEND.length() + 1).append(LEGEND).append('\n');
		for (String line : lines)
		{
			out.append(line).append('\n');
		}
		return out.toString();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick(client.getTickCount(), readPlayer(), Microbot.status, simpleNames(ScriptHeartbeatRegistry.getAllHealth().keySet()));
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
			record("msg=\"" + Text.removeTags(event.getMessage()) + "\"", true);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		recordClick(Text.removeTags(event.getMenuOption()), Text.removeTags(event.getMenuTarget()), event.getId(),
			event.getMenuAction() == null ? "?" : event.getMenuAction().name());
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

	synchronized void tick(int tick, PlayerState player, String status, Set<String> running)
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
			}
		}
		for (String name : scripts)
		{
			if (!running.contains(name))
			{
				parts.add("scripts-" + name);
			}
		}
		scripts = new TreeSet<>(running);
		if (!parts.isEmpty())
		{
			append(String.join(" ", parts));
		}
		if (!scripts.isEmpty() && !stuckMarked && tick - lastChangeTick >= STUCK_TICKS)
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
		append("click=" + option + ">" + (action.startsWith("PLAYER_") ? "player" : target) + "(" + id + "," + action + ")");
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

	synchronized void recordTrace(String key, Object value)
	{
		String text = String.valueOf(value);
		if (!text.equals(traces.put(key, text)))
		{
			append("trace:" + key + "=" + clip(text));
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
		append(entry);
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

	static boolean isLogLine(String chunk)
	{
		return LOG_LINE.matcher(chunk).find();
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
		vars.clear();
		append(out.toString());
	}

	private void flushRepeats()
	{
		if (repeats > 0)
		{
			append("(previous line repeated " + repeats + " more times)");
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
		String line = tick + " " + LocalTime.now().format(TIME) + " " + text;
		lines.addLast(line);
		chars += line.length() + 1;
		while (chars > MAX_CHARS && lines.size() > 1)
		{
			chars -= lines.removeFirst().length() + 1;
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
	static class PlayerState
	{
		String location;
		int animation;
		String target;
		int hitpoints;
		int prayer;
		int runEnergy;
	}

	private final class Tee extends OutputStream
	{
		private final PrintStream target;
		private final String stream;
		private final StringBuilder pending = new StringBuilder();
		private boolean skipping;

		Tee(PrintStream target, String stream)
		{
			this.target = target;
			this.stream = stream;
		}

		@Override
		public void write(int b)
		{
			target.write(b);
			capture(new String(new byte[]{(byte) b}, StandardCharsets.UTF_8));
		}

		@Override
		public void write(byte[] b, int off, int len)
		{
			target.write(b, off, len);
			capture(new String(b, off, len, StandardCharsets.UTF_8));
		}

		@Override
		public void flush()
		{
			target.flush();
		}

		private void capture(String chunk)
		{
			if (active != FlightRecorder.this)
			{
				return;
			}
			List<String> done = new ArrayList<>();
			synchronized (pending)
			{
				if (pending.length() == 0 && isLogLine(chunk))
				{
					skipping = true;
				}
				for (int i = 0; i < chunk.length(); i++)
				{
					char c = chunk.charAt(i);
					if (c == '\n')
					{
						if (!skipping)
						{
							done.add(pending.toString());
						}
						pending.setLength(0);
						skipping = i + 1 < chunk.length() && skipping;
					}
					else if (pending.length() < MAX_LINE_CHARS * 2)
					{
						pending.append(c);
					}
				}
			}
			for (String line : done)
			{
				recordPrint(stream, line);
			}
		}
	}
}
