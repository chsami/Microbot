package net.runelite.client.plugins.microbot.mouserecorder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
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
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.alerts.ClientAlertReporter;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.alerts.ClientLinkToken;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.ui.overlay.OverlayManager;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "Mouse Macro Recorder",
	description = "Records your own mouse and game-key input, tagged with the content you are doing, and uploads it to your Microbot account",
	tags = {"mouse", "keyboard", "input", "recorder", "microbot"}
)
@Slf4j
public class MouseMacroRecorderPlugin extends Plugin implements MouseListener, MouseWheelListener, KeyListener
{
	public static final String CONFIG_GROUP = MouseMacroRecorderConfig.CONFIG_GROUP;
	static final long HUMAN_CLICK_WINDOW_NANOS = 1_500_000_000L;

	enum Status
	{
		OFF,
		NOT_LINKED,
		LOGGED_OUT,
		RECORDING,
		PAUSED,
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

	private final Object lock = new Object();
	private final InputChunker chunker = new InputChunker();
	private final ContentDetector detector = new ContentDetector();
	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
	private InputUploader uploader;
	private volatile boolean active;
	private volatile String pauseReason;
	private volatile boolean scriptRunning;
	private volatile boolean focused = true;
	private volatile boolean dialogueOptionsOpen;
	private long baseNanos;
	private long lastMoveNanos;
	private volatile long lastRealPressNanos;
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
		uploader = new InputUploader(okHttpClient, gson, clientLinkToken::get, apiUrl);
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
			uploader.stopWorker();
		}
	}

	Status status()
	{
		if (!config.recordingEnabled())
		{
			return Status.OFF;
		}
		if (uploader == null || !uploader.isLinked() || uploader.state() == InputUploader.State.NOT_LINKED)
		{
			return Status.NOT_LINKED;
		}
		if (uploader.state() == InputUploader.State.DAILY_LIMIT)
		{
			return Status.DAILY_LIMIT;
		}
		if (!active)
		{
			return Status.LOGGED_OUT;
		}
		return pauseReason == null ? Status.RECORDING : Status.PAUSED;
	}

	String pauseReason()
	{
		return pauseReason;
	}

	String contentName()
	{
		ContentDetector.Content content = detector.current();
		return content == null ? null : content.getName();
	}

	long uploadedParts()
	{
		return uploader == null ? 0 : uploader.uploadedParts();
	}

	int droppedParts()
	{
		return uploader == null ? 0 : uploader.dropped();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CONFIG_GROUP.equals(event.getGroup()) && "recordingEnabled".equals(event.getKey()) && !config.recordingEnabled())
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
	public void onGameTick(GameTick event)
	{
		if (!shouldRecord())
		{
			endSession();
			return;
		}
		if (!active)
		{
			beginSession();
		}
		scriptRunning = isAnyScriptRunning();
		dialogueOptionsOpen = isVisible(client.getWidget(InterfaceID.CHATMENU, 0));
		refreshPause();
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
		if (!recordingNow() || client.isMenuOpen())
		{
			return;
		}
		MenuEntry[] entries = client.getMenuEntries();
		if (entries == null || entries.length == 0)
		{
			return;
		}
		MenuEntry top = entries[entries.length - 1];
		String option = InputPrivacy.option(top.getOption());
		String target = InputPrivacy.target(top.getTarget(), top.getType().getId());
		String key = option + '|' + target + '|' + top.getType().getId() + '|' + top.getIdentifier();
		if (key.equals(lastHover))
		{
			return;
		}
		lastHover = key;
		JsonObject hover = event("hov");
		hover.addProperty("o", option);
		hover.addProperty("tg", target);
		hover.addProperty("ty", top.getType().getId());
		hover.addProperty("id", top.getIdentifier());
		add(hover);
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!recordingNow() || BotEventGuard.isSynthetic())
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

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!recordingNow())
		{
			return;
		}
		boolean human = Microbot.targetMenu == null && !BotEventGuard.isSynthetic()
			&& System.nanoTime() - lastRealPressNanos < HUMAN_CLICK_WINDOW_NANOS;
		JsonObject act = entry(event.getMenuEntry());
		JsonObject wrapped = event("act");
		act.entrySet().forEach(e -> wrapped.add(e.getKey(), e.getValue()));
		wrapped.addProperty("h", human);
		add(wrapped);
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
			lastRealPressNanos = System.nanoTime();
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
		JsonObject key = event(type);
		Integer code = InputPrivacy.keyCode(e.getKeyCode(), dialogueOptionsOpen);
		if (code != null)
		{
			key.addProperty("k", code);
		}
		else
		{
			key.addProperty("c", 1);
		}
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
		return config.recordingEnabled() && uploader != null && uploader.isLinked()
			&& uploader.state() != InputUploader.State.DAILY_LIMIT
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
			long now = nowMs();
			for (ContentDetector.Transition transition : detector.end())
			{
				add(content(transition, now));
			}
			active = false;
			pauseReason = null;
			uploader.submit(chunker.drain());
			uploader.endSession();
		}
	}

	private void refreshPause()
	{
		String reason = null;
		if (scriptRunning)
		{
			reason = "script";
		}
		else if (!focused)
		{
			reason = "focus";
		}
		else if (isVisible(client.getWidget(InterfaceID.BANKPIN_KEYPAD, 0)))
		{
			reason = "bank-pin";
		}
		else if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != 0)
		{
			reason = "text-input";
		}
		String previous = pauseReason;
		if (reason == null ? previous == null : reason.equals(previous))
		{
			return;
		}
		if (reason != null)
		{
			JsonObject pause = event("pause");
			pause.addProperty("why", reason);
			add(pause);
			pauseReason = reason;
			lastHover = null;
		}
		else
		{
			pauseReason = null;
			JsonObject resume = event("resume");
			resume.addProperty("why", previous);
			add(resume);
		}
	}

	private void recordTick(long now)
	{
		JsonObject tick = event("tick");
		tick.addProperty("n", client.getTickCount());
		add(tick);
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
		String pos = world.getX() + "," + world.getY() + "," + world.getPlane() + "," + template;
		if (!pos.equals(lastPos))
		{
			lastPos = pos;
			JsonObject move = event("pos");
			move.addProperty("x", world.getX());
			move.addProperty("y", world.getY());
			move.addProperty("p", world.getPlane());
			move.addProperty("r", world.getRegionID());
			if (template >= 0)
			{
				move.addProperty("tr", template);
			}
			add(move);
		}
		String cam = client.getCameraYaw() + "," + client.getCameraPitch() + "," + client.getScale();
		if (!cam.equals(lastCam))
		{
			lastCam = cam;
			JsonObject camera = event("cam");
			camera.addProperty("yaw", client.getCameraYaw());
			camera.addProperty("pitch", client.getCameraPitch());
			camera.addProperty("zoom", client.getScale());
			add(camera);
		}
		ContentDetector.Tick input = new ContentDetector.Tick(instanced, template, fightingNpc(player), xpSkillThisTick);
		List<ContentDetector.Transition> transitions = detector.update(input, now);
		for (ContentDetector.Transition transition : transitions)
		{
			add(content(transition, now));
		}
	}

	private boolean isAnyScriptRunning()
	{
		boolean hubScript = pluginManager.getPlugins().stream()
			.anyMatch(plugin -> ClientAlertReporter.isScript(plugin) && pluginManager.isPluginActive(plugin));
		return hubScript || Microbot.getActiveScripts().stream().anyMatch(Script::isRunning);
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
		String name = InputPrivacy.target(npc.getName(), -1);
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

	private JsonObject entry(MenuEntry entry)
	{
		JsonObject json = new JsonObject();
		int type = entry.getType().getId();
		json.addProperty("o", InputPrivacy.option(entry.getOption()));
		json.addProperty("tg", InputPrivacy.target(entry.getTarget(), type));
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
		chunker.add(event, event.get("t").getAsLong());
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
