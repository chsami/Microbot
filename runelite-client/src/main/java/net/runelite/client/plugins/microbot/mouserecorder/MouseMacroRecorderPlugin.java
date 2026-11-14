package net.runelite.client.plugins.microbot.mouserecorder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.ScriptStarted;
import net.runelite.client.plugins.microbot.alerts.ClientAlertReporter;
import net.runelite.client.plugins.microbot.alerts.ClientLinkToken;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.ui.overlay.OverlayManager;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "Input Recorder",
	description = "Records your own mouse and game-key input, tagged with the content you are doing, and uploads it to your Microbot account",
	tags = {"mouse", "macro", "keyboard", "input", "recorder", "microbot"},
	version = "2.0.0"
)
@Slf4j
public class MouseMacroRecorderPlugin extends Plugin implements MouseListener, MouseWheelListener, KeyListener
{
	public static final String CONFIG_GROUP = MouseMacroRecorderConfig.CONFIG_GROUP;
	static final long HUMAN_INPUT_WINDOW_NANOS = 1_500_000_000L;
	static final long BOT_QUIET_NANOS = 30_000_000_000L;
	static final int SCRIPT_SCAN_TICKS = 5;

	enum Status
	{
		OFF,
		TELEMETRY_OFF,
		NOT_LINKED,
		LINK_REMOVED,
		LOGGED_OUT,
		RECORDING,
		PAUSED,
		RETRYING,
		DAILY_LIMIT
	}

	@Inject
	private Client client;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MouseMacroRecorderConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseMacroRecorderOverlay overlay;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ClientLinkToken clientLinkToken;

	@Inject
	private ClientSessionManager clientSessionManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	private final Object lock = new Object();
	private final InputChunker chunker = new InputChunker();
	private final ContentDetector detector = new ContentDetector();
	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
	private InputUploader uploader;
	private volatile boolean active;
	private volatile String pauseReason;
	private volatile String botName;
	private volatile boolean focused = true;
	private volatile boolean dialogueOptionsOpen;
	private volatile boolean linked;
	private volatile boolean telemetryOff;
	private volatile boolean botMenuPending;
	private volatile long lastRealInputNanos;
	private volatile long lastScriptStartNanos;
	private volatile long lastBotActionNanos;
	private volatile String hubScriptRunning;
	private long baseNanos;
	private long lastMoveNanos;
	private boolean lastFocused = true;
	private int ticksSinceScan;
	private String lastHover;
	private String lastPos;
	private String lastCam;
	private Skill xpSkillThisTick;

	@Provides
	MouseMacroRecorderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MouseMacroRecorderConfig.class);
	}

	@Override
	protected void startUp()
	{
		String apiUrl = System.getProperty("microbot.apiUrl", "https://microbot.cloud/api");
		uploader = new InputUploader(okHttpClient, gson, clientLinkToken::get, clientLinkToken::reject, apiUrl);
		uploader.startWorker();
		mouseManager.registerMouseListener(this);
		mouseManager.registerMouseWheelListener(this);
		keyManager.registerKeyListener(this);
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		mouseManager.unregisterMouseListener(this);
		mouseManager.unregisterMouseWheelListener(this);
		keyManager.unregisterKeyListener(this);
		overlayManager.remove(overlay);
		endSession();
		if (uploader != null)
		{
			uploader.flushAndStop();
		}
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		endSession();
		if (uploader != null)
		{
			event.waitFor(uploader.flushAndStop());
		}
	}

	Status status()
	{
		if (!config.uploadEnabled())
		{
			return Status.OFF;
		}
		if (telemetryOff)
		{
			return Status.TELEMETRY_OFF;
		}
		InputUploader.State state = uploader == null ? InputUploader.State.NOT_LINKED : uploader.state();
		if (state == InputUploader.State.LINK_REMOVED)
		{
			return Status.LINK_REMOVED;
		}
		if (!linked || state == InputUploader.State.NOT_LINKED)
		{
			return Status.NOT_LINKED;
		}
		if (state == InputUploader.State.DAILY_LIMIT)
		{
			return Status.DAILY_LIMIT;
		}
		if (!active)
		{
			return Status.LOGGED_OUT;
		}
		if (pauseReason != null)
		{
			return Status.PAUSED;
		}
		return state == InputUploader.State.RETRYING ? Status.RETRYING : Status.RECORDING;
	}

	String pauseReason()
	{
		return pauseReason;
	}

	String botName()
	{
		return botName;
	}

	String contentName()
	{
		synchronized (lock)
		{
			ContentDetector.Content content = detector.current();
			return content == null ? null : content.getName();
		}
	}

	int droppedParts()
	{
		return uploader == null ? 0 : uploader.dropped();
	}

	long limitUntilMs()
	{
		return uploader == null ? 0 : uploader.limitUntilMs();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CONFIG_GROUP.equals(event.getGroup()) && "uploadEnabled".equals(event.getKey()) && !config.uploadEnabled())
		{
			endSession();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			endSession();
		}
	}

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		focused = event.isFocused();
	}

	@Subscribe
	public void onScriptStarted(ScriptStarted event)
	{
		lastScriptStartNanos = System.nanoTime();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		telemetryOff = clientLinkToken.isTelemetryDisabled();
		linked = uploader != null && uploader.isLinked();
		if (++ticksSinceScan >= SCRIPT_SCAN_TICKS)
		{
			ticksSinceScan = 0;
			hubScriptRunning = runningHubScript();
		}
		if (!shouldRecord())
		{
			endSession();
			return;
		}
		if (!active)
		{
			beginSession();
		}
		long now = nowMs();
		if (pauseReason == null)
		{
			recordTick(now);
		}
		xpSkillThisTick = null;
		if (chunker.isDue(now))
		{
			uploader.submit(chunker.drain());
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (!active)
		{
			return;
		}
		dialogueOptionsOpen = isVisible(client.getWidget(InterfaceID.CHATMENU, 0));
		refreshPause();
		if (pauseReason != null || client.isMenuOpen())
		{
			return;
		}
		MenuEntry[] entries = client.getMenuEntries();
		if (entries == null || entries.length == 0)
		{
			return;
		}
		MenuEntry top = entries[entries.length - 1];
		JsonObject hover = entry(top);
		String key = hover.toString();
		synchronized (lock)
		{
			if (key.equals(lastHover))
			{
				return;
			}
			lastHover = key;
		}
		JsonObject hov = event("hov");
		hover.entrySet().forEach(e -> hov.add(e.getKey(), e.getValue()));
		add(hov);
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!recordingNow())
		{
			return;
		}
		MenuEntry[] entries = event.getMenuEntries();
		JsonObject menu = event("menu");
		JsonArray list = new JsonArray();
		for (int i = entries.length - 1; i >= 0 && list.size() < 20; i--)
		{
			list.add(entry(entries[i]));
		}
		menu.addProperty("n", entries.length);
		menu.add("entries", list);
		add(menu);
	}

	@Subscribe(priority = 1)
	public void onMenuOptionClickedEarly(MenuOptionClicked event)
	{
		botMenuPending = Microbot.targetMenu != null;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		boolean human = isHumanAction(System.nanoTime());
		botMenuPending = false;
		if (!human)
		{
			lastBotActionNanos = System.nanoTime();
			return;
		}
		if (!recordingNow())
		{
			return;
		}
		JsonObject act = event("act");
		entry(event.getMenuEntry()).entrySet().forEach(e -> act.add(e.getKey(), e.getValue()));
		add(act);
	}

	boolean isHumanAction(long nowNanos)
	{
		long sinceReal = nowNanos - lastRealInputNanos;
		boolean realIsNewer = BotEventGuard.nanosSinceLastSynthetic() > sinceReal;
		return !botMenuPending && lastRealInputNanos != 0 && sinceReal < HUMAN_INPUT_WINDOW_NANOS && realIsNewer;
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (!recordingNow() || actor == null || actor != client.getLocalPlayer())
		{
			return;
		}
		JsonObject anim = event("anim");
		anim.addProperty("id", actor.getAnimation());
		add(anim);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Integer previous = lastXp.put(event.getSkill(), event.getXp());
		if (previous == null || event.getXp() <= previous || !recordingNow())
		{
			return;
		}
		xpSkillThisTick = event.getSkill();
		JsonObject xp = event("xp");
		xp.addProperty("s", event.getSkill().getName());
		xp.addProperty("d", event.getXp() - previous);
		add(xp);
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent mouseEvent)
	{
		recordMove(mouseEvent);
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent mouseEvent)
	{
		recordMove(mouseEvent);
		return mouseEvent;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent mouseEvent)
	{
		if (!BotEventGuard.isSynthetic())
		{
			lastRealInputNanos = System.nanoTime();
		}
		recordButton(mouseEvent, "dn");
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent mouseEvent)
	{
		recordButton(mouseEvent, "up");
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent mouseEvent)
	{
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent mouseEvent)
	{
		return mouseEvent;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent mouseEvent)
	{
		return mouseEvent;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
	{
		if (acceptInput())
		{
			JsonObject wheel = event("wh");
			wheel.addProperty("x", event.getX());
			wheel.addProperty("y", event.getY());
			wheel.addProperty("r", event.getWheelRotation());
			add(wheel);
		}
		return event;
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (!BotEventGuard.isSynthetic())
		{
			lastRealInputNanos = System.nanoTime();
		}
		recordKey(e, "kd");
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		recordKey(e, "ku");
	}

	private void recordMove(MouseEvent mouseEvent)
	{
		if (!config.recordMouseMovement() || !acceptInput())
		{
			return;
		}
		long nanos = System.nanoTime();
		int interval = config.movementSampleIntervalMs();
		if (interval > 0 && nanos - lastMoveNanos < interval * 1_000_000L)
		{
			return;
		}
		lastMoveNanos = nanos;
		JsonObject move = event("mv");
		move.addProperty("x", mouseEvent.getX());
		move.addProperty("y", mouseEvent.getY());
		add(move);
	}

	private void recordButton(MouseEvent mouseEvent, String type)
	{
		if (!acceptInput())
		{
			return;
		}
		JsonObject button = event(type);
		button.addProperty("x", mouseEvent.getX());
		button.addProperty("y", mouseEvent.getY());
		button.addProperty("b", mouseEvent.getButton());
		int modifiers = (mouseEvent.isShiftDown() ? 1 : 0) | (mouseEvent.isControlDown() ? 2 : 0) | (mouseEvent.isAltDown() ? 4 : 0);
		if (modifiers != 0)
		{
			button.addProperty("m", modifiers);
		}
		add(button);
	}

	private void recordKey(KeyEvent e, String type)
	{
		if (!acceptInput())
		{
			return;
		}
		Integer code = InputPrivacy.gameKey(e.getKeyCode(), dialogueOptionsOpen);
		if (code == null)
		{
			return;
		}
		JsonObject key = event(type);
		key.addProperty("k", code);
		add(key);
	}

	private boolean acceptInput()
	{
		return recordingNow() && focused && !BotEventGuard.isSynthetic();
	}

	private boolean recordingNow()
	{
		return active && pauseReason == null;
	}

	private boolean shouldRecord()
	{
		return config.uploadEnabled() && !telemetryOff && linked
			&& uploader.state() != InputUploader.State.DAILY_LIMIT && uploader.state() != InputUploader.State.LINK_REMOVED
			&& client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
	}

	private void beginSession()
	{
		synchronized (lock)
		{
			baseNanos = System.nanoTime();
			lastHover = null;
			lastPos = null;
			lastCam = null;
			lastXp.clear();
			pauseReason = null;
			lastFocused = focused;
			JsonObject meta = new JsonObject();
			meta.addProperty("canvasWidth", client.getCanvasWidth());
			meta.addProperty("canvasHeight", client.getCanvasHeight());
			meta.addProperty("resizable", client.isResized());
			meta.addProperty("stretched", client.isStretchedEnabled());
			meta.addProperty("os", System.getProperty("os.name", ""));
			UUID clientSession = clientSessionManager.getMicrobotSessionId();
			uploader.beginSession(new InputUploader.SessionInfo(Instant.now(), clientSession, clientVersion(), meta));
			active = true;
		}
	}

	private void endSession()
	{
		synchronized (lock)
		{
			if (!active)
			{
				return;
			}
			endContent(nowMs());
			active = false;
			pauseReason = null;
			uploader.submit(chunker.drain());
			uploader.endSession();
		}
	}

	private void refreshPause()
	{
		String reason = pauseReasonNow();
		synchronized (lock)
		{
			if (!active)
			{
				return;
			}
			if (focused != lastFocused)
			{
				lastFocused = focused;
				JsonObject focus = event("focus");
				focus.addProperty("on", focused);
				chunker.add(focus, focus.get("t").getAsLong());
			}
			String previous = pauseReason;
			if (reason == null ? previous == null : reason.equals(previous))
			{
				return;
			}
			long now = nowMs();
			if (reason != null)
			{
				endContent(now);
				JsonObject pause = event("pause", now);
				pause.addProperty("why", reason);
				chunker.add(pause, now);
				pauseReason = reason;
				lastHover = null;
			}
			else
			{
				pauseReason = null;
				JsonObject resume = event("resume", now);
				resume.addProperty("why", previous);
				chunker.add(resume, now);
			}
		}
	}

	private String pauseReasonNow()
	{
		long now = System.nanoTime();
		String hub = hubScriptRunning;
		if (hub != null)
		{
			botName = hub;
			return "script";
		}
		if (BotEventGuard.nanosSinceLastSynthetic() < BOT_QUIET_NANOS || lastBotActionNanos != 0 && now - lastBotActionNanos < BOT_QUIET_NANOS
			|| lastScriptStartNanos != 0 && now - lastScriptStartNanos < BOT_QUIET_NANOS)
		{
			botName = null;
			return "script";
		}
		if (!focused)
		{
			return "focus";
		}
		if (isVisible(client.getWidget(InterfaceID.BANKPIN_KEYPAD, 0)))
		{
			return "bank-pin";
		}
		if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != 0 || chatboxPanelManager.getCurrentInput() != null)
		{
			return "prompt";
		}
		String chat = client.getVarcStrValue(VarClientID.CHATINPUT);
		if (chat != null && !chat.isEmpty())
		{
			return "typing";
		}
		return null;
	}

	private String runningHubScript()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (!ClientAlertReporter.isScript(plugin) || !pluginManager.isPluginActive(plugin))
			{
				continue;
			}
			if (hasRunningScript(plugin))
			{
				String name = InputPrivacy.plain(plugin.getName());
				return name.isEmpty() ? plugin.getClass().getSimpleName() : name;
			}
		}
		return null;
	}

	private static boolean hasRunningScript(Plugin plugin)
	{
		for (Class<?> type = plugin.getClass(); type != null && type != Plugin.class; type = type.getSuperclass())
		{
			for (Field field : type.getDeclaredFields())
			{
				if (!Script.class.isAssignableFrom(field.getType()))
				{
					continue;
				}
				try
				{
					field.setAccessible(true);
					Script script = (Script) field.get(plugin);
					if (script != null && script.isRunning())
					{
						return true;
					}
				}
				catch (ReflectiveOperationException | RuntimeException e)
				{
					log.debug("Could not read script field {} of {}", field.getName(), plugin.getClass().getSimpleName());
				}
			}
		}
		return false;
	}

	private void recordTick(long now)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		WorldPoint world = player.getWorldLocation();
		boolean instanced = client.isInInstancedRegion();
		int template = -1;
		LocalPoint local = player.getLocalLocation();
		if (instanced && local != null)
		{
			WorldPoint real = WorldPoint.fromLocalInstance(client, local);
			template = real == null ? -1 : real.getRegionID();
		}
		int yaw = client.getCameraYaw();
		int pitch = client.getCameraPitch();
		int zoom = client.getScale();
		String fighting = fightingNpc(player);
		synchronized (lock)
		{
			if (!active)
			{
				return;
			}
			JsonObject tick = event("tick", now);
			tick.addProperty("n", client.getTickCount());
			chunker.add(tick, now);
			String pos = world.getX() + "," + world.getY() + "," + world.getPlane() + "," + template;
			if (!pos.equals(lastPos))
			{
				lastPos = pos;
				JsonObject move = event("pos", now);
				move.addProperty("x", world.getX());
				move.addProperty("y", world.getY());
				move.addProperty("p", world.getPlane());
				move.addProperty("r", world.getRegionID());
				if (template >= 0)
				{
					move.addProperty("tr", template);
				}
				chunker.add(move, now);
			}
			String cam = yaw + "," + pitch + "," + zoom;
			if (!cam.equals(lastCam))
			{
				lastCam = cam;
				JsonObject camera = event("cam", now);
				camera.addProperty("yaw", yaw);
				camera.addProperty("pitch", pitch);
				camera.addProperty("zoom", zoom);
				chunker.add(camera, now);
			}
			List<ContentDetector.Transition> transitions = detector.update(new ContentDetector.Tick(instanced, template, fighting, xpSkillThisTick), now);
			for (ContentDetector.Transition transition : transitions)
			{
				JsonObject content = content(transition, now);
				chunker.add(content, content.get("t").getAsLong());
			}
		}
	}

	private void endContent(long now)
	{
		for (ContentDetector.Transition transition : detector.end())
		{
			JsonObject content = content(transition, now);
			chunker.add(content, now);
		}
	}

	private static String fightingNpc(Player player)
	{
		Actor target = player.getInteracting();
		if (!(target instanceof NPC))
		{
			return null;
		}
		NPC npc = (NPC) target;
		if (npc.getCombatLevel() <= 0 || npc.getName() == null)
		{
			return null;
		}
		String name = InputPrivacy.plain(npc.getName());
		return name.isEmpty() ? null : name;
	}

	private JsonObject content(ContentDetector.Transition transition, long now)
	{
		JsonObject content = event("content", transition.isStart() ? transition.getStartMs() : now);
		content.addProperty("s", transition.isStart() ? "start" : "end");
		content.addProperty("c", transition.getContent().getName());
		content.addProperty("k", transition.getContent().getKind());
		if (!transition.isStart())
		{
			content.addProperty("st", transition.getStartMs());
		}
		return content;
	}

	private static JsonObject entry(MenuEntry entry)
	{
		JsonObject json = new JsonObject();
		int type = entry.getType().getId();
		json.addProperty("o", InputPrivacy.option(entry.getOption()));
		json.addProperty("tg", InputPrivacy.target(entry.getTarget(), type, entry.getItemId()));
		json.addProperty("ty", type);
		json.addProperty("id", entry.getIdentifier());
		return json;
	}

	private JsonObject event(String type)
	{
		return event(type, nowMs());
	}

	private static JsonObject event(String type, long ms)
	{
		JsonObject json = new JsonObject();
		json.addProperty("t", ms);
		json.addProperty("e", type);
		return json;
	}

	private void add(JsonObject event)
	{
		synchronized (lock)
		{
			if (active)
			{
				chunker.add(event, event.get("t").getAsLong());
			}
		}
	}

	private long nowMs()
	{
		return (System.nanoTime() - baseNanos) / 1_000_000L;
	}

	private static boolean isVisible(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}

	private static String clientVersion()
	{
		String version = Optional.ofNullable(RuneLiteProperties.getMicrobotVersion()).orElse("unknown").replaceAll("[^0-9A-Za-z.+_-]", "");
		return version.isEmpty() ? "unknown" : version.length() > 32 ? version.substring(0, 32) : version;
	}
}
