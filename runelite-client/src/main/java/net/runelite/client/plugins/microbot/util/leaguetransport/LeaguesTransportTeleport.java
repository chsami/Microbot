package net.runelite.client.plugins.microbot.util.leaguetransport;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.WorldType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WebWalkLog;
import net.runelite.api.coords.WorldPoint;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

/**
 * Leagues Area teleport UI, calibration worker, and client-thread-safe arrival waits.
 */
@Slf4j
final class LeaguesTransportTeleport
{
	private LeaguesTransportTeleport()
	{
	}

	private static final AtomicBoolean CALIBRATION_RUNNING = new AtomicBoolean(false);
	private static final AtomicBoolean CALIBRATION_CANCEL_REQUESTED = new AtomicBoolean(false);
	private static final AtomicLong CALIBRATION_PROBE_MS = new AtomicLong(0L);
	private static final long CALIBRATION_PROBE_MIN_INTERVAL_MS = 5000L;
	private static final AtomicBoolean CALIBRATION_COMPLETE_PROMPT_QUEUED = new AtomicBoolean(false);
	private static final AtomicBoolean CALIBRATION_COMPLETE_PROMPT_SHOWN = new AtomicBoolean(false);
	private static final AtomicLong CALIBRATION_COMPLETE_RETRY_AFTER_MS = new AtomicLong(0L);
	private static final AtomicBoolean TELEPORT_IN_PROGRESS = new AtomicBoolean(false);
	private static final AtomicLong WIDGET_VISIBILITY_CAP_HIT_LOG_MS = new AtomicLong(0L);
	private static final AtomicLong WIDGET_VISIBILITY_CHECK_TIMEOUT_LOG_MS = new AtomicLong(0L);
	private static final AtomicLong CALIBRATION_COMPLETE_DIALOG_FAIL_LOG_MS = new AtomicLong(0L);
	private static final AtomicBoolean LOGGED_TELEPORT_ROW_NAME_MISMATCH = new AtomicBoolean(false);

	private static final int[] LEAGUE_AREA_SELECTION_VARBITS = {
			VarbitID.LEAGUE_AREA_SELECTION_0,
			VarbitID.LEAGUE_AREA_SELECTION_1,
			VarbitID.LEAGUE_AREA_SELECTION_2,
			VarbitID.LEAGUE_AREA_SELECTION_3,
			VarbitID.LEAGUE_AREA_SELECTION_4,
			VarbitID.LEAGUE_AREA_SELECTION_5,
	};

	private static final int LEAGUE_TRANSPORT_CC_OP_IDENTIFIER = 1;
	private static final int LEAGUE_TRANSPORT_CC_OP_PARAM0 = -1;

	private static final int DEFAULT_TIMEOUT_MS = 60_000;
	private static final int POLL_MS = 100;

	static boolean isTeleportInProgress()
	{
		return TELEPORT_IN_PROGRESS.get();
	}

	static void onLogout()
	{
		CALIBRATION_CANCEL_REQUESTED.set(true);
		LeaguesTransportPersistence.resetCalibrationConsentPromptStateOnLogout();
		CALIBRATION_COMPLETE_PROMPT_QUEUED.set(false);
		CALIBRATION_COMPLETE_PROMPT_SHOWN.set(false);
		CALIBRATION_COMPLETE_RETRY_AFTER_MS.set(0L);
		CALIBRATION_PROBE_MS.set(0L);
		TELEPORT_IN_PROGRESS.set(false);
		WIDGET_VISIBILITY_CAP_HIT_LOG_MS.set(0L);
		WIDGET_VISIBILITY_CHECK_TIMEOUT_LOG_MS.set(0L);
	}

	static void tickLeaguesCalibration()
	{
		Client c = Microbot.getClient();
		if (!isClientReadyForCalibration(c))
		{
			return;
		}

		if (!isLeaguesActive())
		{
			return;
		}
		long now = System.currentTimeMillis();
		long prev = CALIBRATION_PROBE_MS.get();
		if (prev != 0L && (now - prev) < CALIBRATION_PROBE_MIN_INTERVAL_MS)
		{
			return;
		}
		if (!CALIBRATION_PROBE_MS.compareAndSet(prev, now))
		{
			return;
		}

		EnumSet<LeaguesRegion> unlocked = unlockedRegions();
		if (unlocked.isEmpty())
		{
			return;
		}
		calibrateMissingLandingsAsync(unlocked);
	}

	private static boolean isClientReadyForCalibration(Client client)
	{
		if (!Microbot.isLoggedIn())
		{
			return false;
		}
		if (client == null)
		{
			return false;
		}
		boolean welcomeVisible;
		if (client.isClientThread())
		{
			Widget w = client.getWidget(InterfaceID.WelcomeScreen.PLAY);
			welcomeVisible = isWidgetEffectivelyVisible(w);
		}
		else
		{
			net.runelite.client.callback.ClientThread clientThread = Microbot.getClientThread();
			if (clientThread == null)
			{
				return false;
			}
			Optional<Boolean> vis = clientThread.runOnClientThreadOptional(() ->
			{
				Widget w = client.getWidget(InterfaceID.WelcomeScreen.PLAY);
				return isWidgetEffectivelyVisible(w);
			});
			if (vis.isEmpty())
			{
				long now = System.currentTimeMillis();
				long prev = WIDGET_VISIBILITY_CHECK_TIMEOUT_LOG_MS.get();
				if (prev == 0L || (now - prev) >= 3_600_000L)
				{
					if (WIDGET_VISIBILITY_CHECK_TIMEOUT_LOG_MS.compareAndSet(prev, now))
					{
						log.debug("[Leagues] widget visibility check timed out/empty; gating calibration as not-ready");
					}
				}
				return false;
			}
			welcomeVisible = vis.get();
		}
		if (welcomeVisible)
		{
			return false;
		}
		return client.getGameState() == net.runelite.api.GameState.LOGGED_IN && client.getLocalPlayer() != null;
	}

	private static boolean isWidgetEffectivelyVisible(Widget w)
	{
		if (w == null)
		{
			return false;
		}
		Widget slow = w;
		Widget fast = w;
		final int cap = 20;
		for (int i = 0; i < cap && slow != null; i++)
		{
			Widget cur = slow;
			if (cur.isHidden())
			{
				return false;
			}
			Widget parent = cur.getParent();
			if (parent == cur)
			{
				return false;
			}
			slow = parent;
			fast = fast != null ? fast.getParent() : null;
			fast = fast != null ? fast.getParent() : null;
			if (slow != null && slow == fast)
			{
				return false;
			}
		}
		if (slow == null)
		{
			return true;
		}
		if (log.isDebugEnabled())
		{
			long now = System.currentTimeMillis();
			long prev = WIDGET_VISIBILITY_CAP_HIT_LOG_MS.get();
			if (prev == 0L || (now - prev) >= 3_600_000L)
			{
				if (WIDGET_VISIBILITY_CAP_HIT_LOG_MS.compareAndSet(prev, now))
				{
					log.debug("[Leagues] widget visibility parent chain exceeded cap={}", cap);
				}
			}
		}
		return false;
	}

	private static void promptCalibrationConsentIfNeeded()
	{
		LeaguesTransportPersistence.ensureLoaded();
		if (LeaguesTransportPersistence.isCalibrationConsentAllowed()
				|| LeaguesTransportPersistence.isCalibrationConsentDenied())
		{
			return;
		}
		long now = System.currentTimeMillis();
		long retryAfter = LeaguesTransportPersistence.getCalibrationConsentRetryAfterMs();
		if (retryAfter != 0L && now < retryAfter)
		{
			return;
		}
		if (!LeaguesTransportPersistence.compareAndSetCalibrationConsentPromptQueued(false, true))
		{
			return;
		}

		SwingUtilities.invokeLater(() ->
		{
			try
			{
				if (LeaguesTransportPersistence.isCalibrationConsentAllowed()
						|| LeaguesTransportPersistence.isCalibrationConsentDenied())
				{
					return;
				}
				Client client = Microbot.getClient();
				if (!isClientReadyForCalibration(client))
				{
					LeaguesTransportPersistence.setCalibrationConsentRetryAfterMs(System.currentTimeMillis() + 5000L);
					return;
				}

				int res = JOptionPane.showConfirmDialog(
						null,
						"Calibrating Leagues teleports required.\n"
								+ "This will teleport your character briefly to learn landing tiles.\n\n"
								+ "Start calibration now?",
						"Microbot: Calibrate Leagues teleports",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.INFORMATION_MESSAGE);

				if (res == JOptionPane.YES_OPTION)
				{
					LeaguesTransportPersistence.setCalibrationConsentAllowed(true);
					LeaguesTransportPersistence.flush();
				}
				else
				{
					LeaguesTransportPersistence.setCalibrationConsentDenied(true);
					LeaguesTransportPersistence.flush();
				}
			}
			finally
			{
				LeaguesTransportPersistence.setCalibrationConsentPromptQueued(false);
			}
		});
	}

	private static void promptCalibrationComplete(EnumSet<LeaguesRegion> unlockedRegions)
	{
		if (unlockedRegions == null || unlockedRegions.isEmpty())
		{
			return;
		}
		long now = System.currentTimeMillis();
		long retryAfter = CALIBRATION_COMPLETE_RETRY_AFTER_MS.get();
		if (retryAfter != 0L && now < retryAfter)
		{
			return;
		}
		if (CALIBRATION_COMPLETE_PROMPT_SHOWN.get())
		{
			return;
		}
		if (!CALIBRATION_COMPLETE_PROMPT_QUEUED.compareAndSet(false, true))
		{
			return;
		}

		LeaguesTransportPersistence.ensureLoaded();
		Map<LeaguesRegion, WorldPoint> landings = LeaguesTransportPersistence.copyRegionLandingsSnapshot();

		int known = 0;
		int missing = 0;
		StringBuilder sb = new StringBuilder(1024);
		sb.append("Leagues teleport calibration complete.\n\n");
		sb.append("Unlocked regions: ").append(unlockedRegions.size()).append('\n');

		for (LeaguesRegion r : unlockedRegions)
		{
			WorldPoint wp = landings.get(r);
			if (wp != null)
			{
				known++;
			}
			else
			{
				missing++;
			}
		}
		sb.append("Learned landings: ").append(known).append('\n');
		sb.append("Missing landings: ").append(missing).append("\n\n");
		sb.append("Unlocked Leagues Area teleports:\n");

		for (LeaguesRegion r : unlockedRegions)
		{
			WorldPoint wp = landings.get(r);
			sb.append("- ").append(r.getDisplayName());
			if (wp != null)
			{
				sb.append(" -> ").append(wp.toString());
			}
			else
			{
				sb.append(" -> (not learned)");
			}
			sb.append('\n');
		}

		final int maxChars = 8000;
		final String msg = sb.length() > maxChars ? sb.substring(0, maxChars) + "\n…(truncated)" : sb.toString();

		SwingUtilities.invokeLater(() ->
		{
			try
			{
				Client client = Microbot.getClient();
				if (!isClientReadyForCalibration(client))
				{
					CALIBRATION_COMPLETE_RETRY_AFTER_MS.set(System.currentTimeMillis() + 5000L);
					return;
				}
				if (!CALIBRATION_COMPLETE_PROMPT_SHOWN.compareAndSet(false, true))
				{
					return;
				}
				try
				{
					JOptionPane.showMessageDialog(
							null,
							msg,
							"Microbot: Leagues calibration complete",
							JOptionPane.INFORMATION_MESSAGE);
				}
				catch (Exception e)
				{
					CALIBRATION_COMPLETE_RETRY_AFTER_MS.set(System.currentTimeMillis() + 5000L);
					CALIBRATION_COMPLETE_PROMPT_SHOWN.set(false);
					if (log.isDebugEnabled())
					{
						long nowMs = System.currentTimeMillis();
						long prev = CALIBRATION_COMPLETE_DIALOG_FAIL_LOG_MS.get();
						if (prev == 0L || (nowMs - prev) >= 3_600_000L)
						{
							if (CALIBRATION_COMPLETE_DIALOG_FAIL_LOG_MS.compareAndSet(prev, nowMs))
							{
								log.debug("[Leagues] completion dialog failed", e);
							}
						}
					}
				}
			}
			finally
			{
				CALIBRATION_COMPLETE_PROMPT_QUEUED.set(false);
			}
		});
	}

	static void calibrateMissingLandingsAsync(EnumSet<LeaguesRegion> unlockedRegions)
	{
		calibrateMissingLandingsAsync(unlockedRegions, false);
	}

	static void calibrateMissingLandingsAsync(EnumSet<LeaguesRegion> unlockedRegions,
			boolean logNoOpWhenFullyCalibrated)
	{
		if (unlockedRegions == null || unlockedRegions.isEmpty())
		{
			return;
		}
		if (!isLeaguesActive())
		{
			return;
		}
		LeaguesTransportPersistence.ensureLoaded();

		int missingCount = 0;
		for (LeaguesRegion r : unlockedRegions)
		{
			if (!LeaguesTransportPersistence.hasRegionLanding(r))
			{
				missingCount++;
			}
		}
		if (missingCount == 0)
		{
			if (logNoOpWhenFullyCalibrated)
			{
				WebWalkLog.leaguesInfo(
						"calibrate noop | all landing tiles already cached | unlockedRegions={}",
						unlockedRegions.size());
			}
			return;
		}

		WebWalkLog.leagues("calibrate queue | missingLandings={} unlockedRegions={}",
				missingCount, unlockedRegions.size());

		promptCalibrationConsentIfNeeded();
		if (!LeaguesTransportPersistence.isCalibrationConsentAllowed())
		{
			return;
		}

		if (!CALIBRATION_RUNNING.compareAndSet(false, true))
		{
			return;
		}

		CALIBRATION_CANCEL_REQUESTED.set(false);

		final EnumSet<LeaguesRegion> unlockedSnapshot = EnumSet.copyOf(unlockedRegions);
		WebWalkLog.leaguesInfo("calibration worker starting | missingLandings={}", missingCount);
		Thread t = new Thread(() ->
		{
			try
			{
				int ok = 0;
				int fail = 0;
				int tried = 0;

				for (LeaguesRegion target : unlockedSnapshot)
				{
					if (CALIBRATION_CANCEL_REQUESTED.get())
					{
						dismissOpenMenusAfterCalibrationCancel();
						break;
					}
					if (target == null)
					{
						continue;
					}
					if (LeaguesTransportPersistence.hasRegionLanding(target))
					{
						continue;
					}
					tried++;

					log.info("[Leagues] calibrate landing start: {}", target);
					if (CALIBRATION_CANCEL_REQUESTED.get())
					{
						dismissOpenMenusAfterCalibrationCancel();
						break;
					}
					final WorldPoint before = Rs2Player.getWorldLocation();
					LeaguesTeleportResult res = leaguesTeleport(target);
					if (!res.isSuccess())
					{
						fail++;
						log.info("[Leagues] calibrate landing failed: {} reason={} msg='{}'",
								target, res.getFailureReason(), res.getMessage());
						continue;
					}

					boolean moved = sleepUntilTrue(() ->
					{
						WorldPoint now = Rs2Player.getWorldLocation();
						return now != null && before != null && !now.equals(before);
					}, 100, 8000);
					if (!moved)
					{
						fail++;
						continue;
					}

					WorldPoint after = Rs2Player.getWorldLocation();
					if (after != null)
					{
						ok++;
						LeaguesTransportPersistence.persistRegionLanding(target, after);
						log.info("[Leagues] calibrate landing ok: {} -> {}", target, after);
					}
					else
					{
						fail++;
					}
				}

				if (tried > 0)
				{
					promptCalibrationComplete(unlockedSnapshot);
				}
			}
			catch (Exception e)
			{
				log.debug("[Leagues] calibrate landing thread exc: type={} msg={}",
						e.getClass().getName(), e.getMessage());
			}
			finally
			{
				CALIBRATION_RUNNING.set(false);
			}
		}, "microbot-leagues-landing-calibration");
		t.setDaemon(true);
		t.setUncaughtExceptionHandler((thread, ex) ->
				log.warn("[Leagues] calibrate landing uncaught on {}", thread != null ? thread.getName() : "?", ex));
		t.start();
	}

	static EnumSet<LeaguesRegion> unlockedRegions()
	{
		Optional<EnumSet<LeaguesRegion>> unlockedOpt = Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			if (!leaguesContextRejectOrEmptySuccess().isEmpty())
			{
				return EnumSet.noneOf(LeaguesRegion.class);
			}
			return readUnlockedRegionsFromSelectionVarbits();
		});

		return unlockedOpt.orElse(EnumSet.noneOf(LeaguesRegion.class));
	}

	static LeaguesTeleportResult leaguesTeleport(LeaguesRegion region)
	{
		return leaguesTeleport(region, DEFAULT_TIMEOUT_MS);
	}

	static LeaguesTeleportResult leaguesTeleport(LeaguesRegion region, int timeoutMs)
	{
		Objects.requireNonNull(region, "region");
		if (timeoutMs <= 0)
		{
			throw new IllegalArgumentException("timeoutMs must be > 0");
		}

		Client client = Microbot.getClient();
		if (client == null)
		{
			String msg = "Client not available.";
			Microbot.status = msg;
			return LeaguesTeleportResult.failure(
					LeaguesTeleportFailureReason.CLIENT_UNAVAILABLE,
					msg,
					region,
					null);
		}
		if (client.isClientThread())
		{
			String msg = "leaguesTeleport must not run on the RuneLite client thread.";
			Microbot.status = msg;
			return LeaguesTeleportResult.failure(
					LeaguesTeleportFailureReason.INVOKED_ON_CLIENT_THREAD,
					msg,
					region,
					null);
		}

		final long startedAtMs = System.currentTimeMillis();
		final WorldPoint before = Rs2Player.getWorldLocation();

		Optional<TeleportGateSnapshot> gateOpt = evaluateTeleportGates(region);
		if (!gateOpt.isPresent())
		{
			String msg = "Leagues teleport gates: empty client-thread gate result ("
					+ LeaguesTeleportFailureReason.CLIENT_THREAD_UNAVAILABLE.name()
					+ "; not null Client / not wrong thread).";
			Microbot.status = msg;
			return LeaguesTeleportResult.failure(
					LeaguesTeleportFailureReason.CLIENT_THREAD_UNAVAILABLE,
					msg,
					region,
					null);
		}

		TeleportGateSnapshot gate = gateOpt.get();
		if (gate.contextFailureReason != null)
		{
			Microbot.status = gate.contextFailureMessage;
			return LeaguesTeleportResult.failure(
					gate.contextFailureReason,
					gate.contextFailureMessage,
					region,
					null);
		}

		if (!gate.unlockedRegions.contains(region))
		{
			String msg = "Region not unlocked: " + region.getDisplayName() + ".";
			Microbot.status = msg;
			return LeaguesTeleportResult.failure(
					LeaguesTeleportFailureReason.REGION_LOCKED,
					msg,
					region,
					gate.unlockedRegions);
		}

		boolean marked = TELEPORT_IN_PROGRESS.compareAndSet(false, true);
		try
		{
			if (!performTeleportSequence(region, timeoutMs))
			{
				String msg = "Leagues transport: UI timeout.";
				Microbot.status = msg;
				return LeaguesTeleportResult.failure(
						LeaguesTeleportFailureReason.UI_TIMEOUT,
						msg,
						region,
						gate.unlockedRegions);
			}

			int remaining = remainingMs(startedAtMs, timeoutMs);
			final boolean arrived;
			if (remaining <= 0)
			{
				arrived = false;
			}
			else
			{
				final int rem = remaining;
				final WorldPoint bef = before;
				final LeaguesRegion reg = region;
				boolean[] box = {false};
				Rs2Walker.runWithWalkerLockReleased(() -> box[0] = waitForTeleportArrival(reg, bef, rem));
				arrived = box[0];
			}
			if (!arrived)
			{
				String msg = "Leagues transport: teleport timeout.";
				Microbot.status = msg;
				return LeaguesTeleportResult.failure(
						LeaguesTeleportFailureReason.TELEPORT_TIMEOUT,
						msg,
						region,
						gate.unlockedRegions);
			}

			return LeaguesTeleportResult.ok(region, gate.unlockedRegions);
		}
		finally
		{
			if (marked)
			{
				TELEPORT_IN_PROGRESS.set(false);
			}
		}
	}

	private static boolean waitForTeleportArrival(LeaguesRegion region, WorldPoint before, int timeoutMs)
	{
		Objects.requireNonNull(region, "region");
		final long startedAtMs = System.currentTimeMillis();
		final int teleportDistanceThreshold = 20;

		final boolean animatingStarted = sleepUntilTrue(Rs2Player::isAnimating, POLL_MS, remainingMs(startedAtMs, timeoutMs));
		final long moveWaitStartedAtMs = System.currentTimeMillis();
		return sleepUntilTrue(() ->
		{
			WorldPoint now = Rs2Player.getWorldLocation();
			if (now == null || before == null)
			{
				return false;
			}
			if (now.equals(before))
			{
				return false;
			}
			if (now.getRegionID() != before.getRegionID())
			{
				return true;
			}
			return now.distanceTo(before) > teleportDistanceThreshold;
		}, POLL_MS, remainingMs(moveWaitStartedAtMs, animatingStarted ? remainingMs(startedAtMs, timeoutMs) : remainingMs(startedAtMs, timeoutMs)));
	}

	private static boolean performTeleportSequence(LeaguesRegion region, int timeoutMs)
	{
		final long startedAtMs = System.currentTimeMillis();

		if (isFixedTeleportRowReady(region))
		{
			return invokeTeleportToRegion(region);
		}

		if (!Rs2Widget.isWidgetVisible(LeagueTransportWidgets.LEAGUES_GROUP, LeagueTransportWidgets.LEAGUES_CHILD))
		{
			invokeCcOp(LeagueTransportWidgets.pack(LeagueTransportWidgets.ACTIVITIES_GROUP, LeagueTransportWidgets.ACTIVITIES_CHILD), "Leagues", "");
			if (!sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(LeagueTransportWidgets.LEAGUES_GROUP, LeagueTransportWidgets.LEAGUES_CHILD), POLL_MS, remainingMs(startedAtMs, timeoutMs)))
			{
				return false;
			}
		}

		if (!Rs2Widget.isWidgetVisible(LeagueTransportWidgets.VIEW_AREAS_GROUP, LeagueTransportWidgets.VIEW_AREAS_CHILD))
		{
			invokeCcOp(LeagueTransportWidgets.pack(LeagueTransportWidgets.LEAGUES_GROUP, LeagueTransportWidgets.LEAGUES_CHILD), "Leagues", "");
			if (!sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(LeagueTransportWidgets.VIEW_AREAS_GROUP, LeagueTransportWidgets.VIEW_AREAS_CHILD), POLL_MS, remainingMs(startedAtMs, timeoutMs)))
			{
				return false;
			}
		}

		if (!ensureAreasMenuShowsTargetRow(region, startedAtMs, timeoutMs))
		{
			return false;
		}

		return invokeTeleportToRegion(region);
	}

	private static boolean ensureAreasMenuShowsTargetRow(LeaguesRegion region, long startedAtMs, int timeoutMs)
	{
		Objects.requireNonNull(region, "region");

		int viewAreasPacked = LeagueTransportWidgets.pack(LeagueTransportWidgets.VIEW_AREAS_GROUP, LeagueTransportWidgets.VIEW_AREAS_CHILD);
		if (!Global.sleepUntil(
				() -> Rs2Widget.isWidgetVisible(LeagueTransportWidgets.AREAS_PANEL_GROUP, LeagueTransportWidgets.AREAS_PANEL_CHILD),
				() -> invokeCcOp(viewAreasPacked, "View Areas", ""),
				remainingMs(startedAtMs, timeoutMs),
				POLL_MS))
		{
			return false;
		}

		LeaguesRegion.AreasMenuShield shield = region.getAreasMenuShield();
		if (shield.isActive())
		{
			invokeCcOp(LeagueTransportWidgets.pack(shield.getGroup(), shield.getChild()), shield.getCcOpOption(), shield.getCcOpTarget());
		}

		int listRootPacked = areasListRootPacked(region);
		if (!Global.sleepUntil(
				() -> Rs2Widget.isWidgetVisible(areasListRootGroup(region), areasListRootChild(region)),
				() -> {
					if (shield.isActive())
					{
						invokeCcOp(LeagueTransportWidgets.pack(shield.getGroup(), shield.getChild()), shield.getCcOpOption(), shield.getCcOpTarget());
					}
				},
				remainingMs(startedAtMs, timeoutMs),
				POLL_MS))
		{
			return false;
		}

		return Global.sleepUntil(
				() -> isFixedTeleportRowReady(region),
				() -> {
					if (shield.isActive())
					{
						invokeCcOp(LeagueTransportWidgets.pack(shield.getGroup(), shield.getChild()), shield.getCcOpOption(), shield.getCcOpTarget());
					}
				},
				remainingMs(startedAtMs, timeoutMs),
				POLL_MS);
	}

	private static boolean isFixedTeleportRowReady(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		int packed = LeagueTransportWidgets.pack(LeagueTransportWidgets.TELEPORT_ROW_GROUP, LeagueTransportWidgets.TELEPORT_ROW_CHILD);
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget row = Rs2Widget.getWidget(packed);
			if (row == null || row.isHidden())
			{
				return false;
			}
			String name = row.getName();
			if (name == null || !name.equals(region.toMenuTarget()))
			{
				return false;
			}
			return true;
		}).orElse(false);
	}

	private static boolean isTargetRowVisible(LeaguesRegion region, int listContainerPackedId)
	{
		Objects.requireNonNull(region, "region");
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget listContainer = Rs2Widget.getWidget(listContainerPackedId);
			if (listContainer == null || listContainer.isHidden())
			{
				return false;
			}
			Widget row = resolveTeleportRowWidget(listContainer, region);
			return row != null && !row.isHidden();
		}).orElse(false);
	}

	private static Widget resolveTeleportRowWidget(Widget listContainer, LeaguesRegion region)
	{
		Objects.requireNonNull(listContainer, "listContainer");
		Objects.requireNonNull(region, "region");
		int idx = region.getTeleportListRowDynamicIndex();
		if (idx < 0)
		{
			return null;
		}
		Widget[] dynamic = listContainer.getDynamicChildren();
		if (dynamic != null && idx < dynamic.length)
		{
			Widget w = dynamic[idx];
			if (w != null)
			{
				return w;
			}
		}
		Widget[] nested = listContainer.getNestedChildren();
		if (nested != null && idx < nested.length)
		{
			return nested[idx];
		}
		Widget[] children = listContainer.getChildren();
		if (children != null && idx < children.length)
		{
			return children[idx];
		}
		return null;
	}

	private static int areasListRootGroup(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		int g = region.getAreasListRootGroup();
		return g != 0 ? g : LeagueTransportWidgets.AREAS_LIST_CONTAINER_GROUP;
	}

	private static int areasListRootChild(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		return region.getAreasListRootGroup() != 0 ? region.getAreasListRootChild() : LeagueTransportWidgets.AREAS_LIST_CONTAINER_CHILD;
	}

	private static int areasListRootPacked(LeaguesRegion region)
	{
		return LeagueTransportWidgets.pack(areasListRootGroup(region), areasListRootChild(region));
	}

	private static boolean invokeTeleportToRegion(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");

		if (region.getTeleportCcOpGroup() != 0)
		{
			invokeCcOp(
					LeagueTransportWidgets.pack(region.getTeleportCcOpGroup(), region.getTeleportCcOpChild()),
					region.getTeleportCcOpOption(),
					region.toMenuTarget());
			scheduleDebugCheckTeleportRowNameMatches(region);
			return true;
		}

		invokeCcOp(
				LeagueTransportWidgets.pack(LeagueTransportWidgets.TELEPORT_ROW_GROUP, LeagueTransportWidgets.TELEPORT_ROW_CHILD),
				region.getTeleportCcOpOption(),
				region.toMenuTarget());
		scheduleDebugCheckTeleportRowNameMatches(region);
		return true;
	}

	private static void scheduleDebugCheckTeleportRowNameMatches(LeaguesRegion region)
	{
		if (region == null || !log.isDebugEnabled())
		{
			return;
		}
		Microbot.getClientThread().invokeLater(() ->
		{
			int packed = LeagueTransportWidgets.pack(LeagueTransportWidgets.TELEPORT_ROW_GROUP, LeagueTransportWidgets.TELEPORT_ROW_CHILD);
			Widget row = Rs2Widget.getWidget(packed);
			if (row == null || row.isHidden())
			{
				return;
			}
			String name = row.getName();
			String expect = region.toMenuTarget();
			if (name != null && expect != null && !name.equals(expect) && LOGGED_TELEPORT_ROW_NAME_MISMATCH.compareAndSet(false, true))
			{
				log.debug("[Leagues] TELEPORT_ROW name mismatch after click: region={} expected={} actual={}", region, expect, name);
			}
		});
	}

	private static void dismissOpenMenusAfterCalibrationCancel()
	{
		var ct = Microbot.getClientThread();
		if (ct == null)
		{
			return;
		}
		ct.invokeLater(() -> Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE));
	}

	private static int remainingMs(long startedAtMs, int timeoutMs)
	{
		long elapsed = System.currentTimeMillis() - startedAtMs;
		long remaining = timeoutMs - elapsed;
		if (remaining <= 0)
		{
			return 1;
		}
		return remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
	}

	public static class LeaguesTeleportDriver
	{
		private final LeaguesRegion targetRegion;
		private boolean active = true;
		private int step;
		private boolean areasMenuShieldClicked;

		protected LeaguesTeleportDriver(LeaguesRegion targetRegion)
		{
			this.targetRegion = targetRegion;
		}

		public boolean isActive()
		{
			return active;
		}

		public void stop()
		{
			active = false;
		}

		public void tick()
		{
			if (!active)
			{
				return;
			}
			if (Microbot.getClient() == null)
			{
				active = false;
				return;
			}

			if (!passVisibilityGate(step))
			{
				return;
			}

			switch (step)
			{
				case 0:
					if (Rs2Widget.isWidgetVisible(LeagueTransportWidgets.LEAGUES_GROUP, LeagueTransportWidgets.LEAGUES_CHILD))
					{
						step = 1;
						return;
					}
					Microbot.status = "Leagues: open activities";
					invokeCcOp(LeagueTransportWidgets.pack(LeagueTransportWidgets.ACTIVITIES_GROUP, LeagueTransportWidgets.ACTIVITIES_CHILD), "Leagues", "");
					step = 1;
					return;
				case 1:
					if (Rs2Widget.isWidgetVisible(LeagueTransportWidgets.VIEW_AREAS_GROUP, LeagueTransportWidgets.VIEW_AREAS_CHILD))
					{
						step = 2;
						return;
					}
					Microbot.status = "Leagues: open leagues";
					invokeCcOp(LeagueTransportWidgets.pack(LeagueTransportWidgets.LEAGUES_GROUP, LeagueTransportWidgets.LEAGUES_CHILD), "Leagues", "");
					step = 2;
					return;
				case 2:
					if (Rs2Widget.isWidgetVisible(LeagueTransportWidgets.AREAS_PANEL_GROUP, LeagueTransportWidgets.AREAS_PANEL_CHILD))
					{
						step = 3;
						return;
					}
					Microbot.status = "Leagues: open areas";
					invokeCcOp(LeagueTransportWidgets.pack(LeagueTransportWidgets.VIEW_AREAS_GROUP, LeagueTransportWidgets.VIEW_AREAS_CHILD), "View Areas", "");
					step = 3;
					return;
				case 3:
				{
					if (isFixedTeleportRowReady(targetRegion))
					{
						Microbot.status = "Leagues: teleport " + targetRegion.getDisplayName();
						if (!invokeTeleportToRegion(targetRegion))
						{
							log.warn("LeaguesTeleportDriver: teleport invoke failed for {}", targetRegion);
						}
						areasMenuShieldClicked = false;
						active = false;
						step = 0;
						return;
					}
					LeaguesRegion.AreasMenuShield shield = targetRegion.getAreasMenuShield();
					int listRootPacked = areasListRootPacked(targetRegion);
					if (shield.isActive() && !areasMenuShieldClicked)
					{
						Microbot.status = "Leagues: areas menu shield";
						invokeCcOp(LeagueTransportWidgets.pack(shield.getGroup(), shield.getChild()), shield.getCcOpOption(), shield.getCcOpTarget());
						areasMenuShieldClicked = true;
						return;
					}
					if (!isTargetRowVisible(targetRegion, listRootPacked))
					{
						return;
					}
					Microbot.status = "Leagues: teleport " + targetRegion.getDisplayName();
					if (!invokeTeleportToRegion(targetRegion))
					{
						log.warn("LeaguesTeleportDriver: teleport invoke failed for {}", targetRegion);
					}
					areasMenuShieldClicked = false;
					active = false;
					step = 0;
					return;
				}
				default:
					log.warn("LeaguesTeleportDriver unexpected step {}", step);
					active = false;
			}
		}

		private boolean passVisibilityGate(int step)
		{
			switch (step)
			{
				case 0:
					return true;
				case 1:
					return Rs2Widget.isWidgetVisible(LeagueTransportWidgets.LEAGUES_GROUP, LeagueTransportWidgets.LEAGUES_CHILD);
				case 2:
					return Rs2Widget.isWidgetVisible(LeagueTransportWidgets.VIEW_AREAS_GROUP, LeagueTransportWidgets.VIEW_AREAS_CHILD);
				case 3:
					return Rs2Widget.isWidgetVisible(areasListRootGroup(targetRegion), areasListRootChild(targetRegion));
				default:
					return false;
			}
		}
	}

	private static final class TeleportGateSnapshot
	{
		private final LeaguesTeleportFailureReason contextFailureReason;
		private final String contextFailureMessage;
		private final EnumSet<LeaguesRegion> unlockedRegions;

		private TeleportGateSnapshot(
				LeaguesTeleportFailureReason contextFailureReason,
				String contextFailureMessage,
				EnumSet<LeaguesRegion> unlockedRegions)
		{
			this.contextFailureReason = contextFailureReason;
			this.contextFailureMessage = contextFailureMessage;
			this.unlockedRegions = unlockedRegions != null ? EnumSet.copyOf(unlockedRegions) : EnumSet.noneOf(LeaguesRegion.class);
		}
	}

	private static Optional<TeleportGateSnapshot> evaluateTeleportGates(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			String ctxReject = leaguesContextRejectOrEmptySuccess();
			if (!ctxReject.isEmpty())
			{
				return new TeleportGateSnapshot(mapContextFailureReason(ctxReject), ctxReject, null);
			}
			return new TeleportGateSnapshot(null, null, readUnlockedRegionsFromSelectionVarbits());
		});
	}

	private static EnumSet<LeaguesRegion> readUnlockedRegionsFromSelectionVarbits()
	{
		EnumSet<LeaguesRegion> unlocked = EnumSet.noneOf(LeaguesRegion.class);
		for (int vb : LEAGUE_AREA_SELECTION_VARBITS)
		{
			int areaId = Microbot.getVarbitValue(vb);
			LeaguesRegion r = byAreaIdOrNull(areaId);
			if (r != null)
			{
				unlocked.add(r);
			}
		}
		return unlocked;
	}

	private static LeaguesRegion byAreaIdOrNull(int areaId)
	{
		if (areaId <= 0)
		{
			return null;
		}
		for (LeaguesRegion r : LeaguesRegion.values())
		{
			if (r.getAreaId() == areaId)
			{
				return r;
			}
		}
		return null;
	}

	private static LeaguesTeleportFailureReason mapContextFailureReason(String message)
	{
		if ("Client not available.".equals(message))
		{
			return LeaguesTeleportFailureReason.CLIENT_UNAVAILABLE;
		}
		if ("Not on a Leagues / seasonal world.".equals(message))
		{
			return LeaguesTeleportFailureReason.NOT_SEASONAL_WORLD;
		}
		if ("League account not active.".equals(message))
		{
			return LeaguesTeleportFailureReason.LEAGUE_ACCOUNT_INACTIVE;
		}
		if ("Leagues context: client thread unavailable.".equals(message))
		{
			return LeaguesTeleportFailureReason.CLIENT_THREAD_UNAVAILABLE;
		}
		return LeaguesTeleportFailureReason.UNKNOWN;
	}

	static String leaguesContextRejectOrEmptySuccess()
	{
		Client c = Microbot.getClient();
		if (c == null)
		{
			return "Client not available.";
		}
		EnumSet<WorldType> types = c.getWorldType();
		if (types == null || !types.contains(WorldType.SEASONAL))
		{
			return "Not on a Leagues / seasonal world.";
		}
		if (Microbot.getVarbitValue(VarbitID.LEAGUE_ACCOUNT) <= 0)
		{
			return "League account not active.";
		}
		return "";
	}

	static String verifyLeaguesContextOrNull()
	{
		Optional<String> msgOpt = Microbot.getClientThread().runOnClientThreadOptional(LeaguesTransportTeleport::leaguesContextRejectOrEmptySuccess);
		if (!msgOpt.isPresent())
		{
			return "Leagues context: client thread unavailable.";
		}
		String msg = msgOpt.get();
		return msg.isEmpty() ? null : msg;
	}

	private static boolean isLeaguesActive()
	{
		return Microbot.getVarbitValue(VarbitID.LEAGUE_TYPE) > 0;
	}

	private static void invokeCcOp(int packedWidgetId, String option, String target)
	{
		Objects.requireNonNull(option, "option");
		String targetNonNull = target != null ? target : "";

		Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget w = Rs2Widget.getWidget(packedWidgetId);
			return w != null ? w.getBounds() : null;
		}).orElse(null);

		Rectangle clickRect = bounds != null ? bounds : new Rectangle(1, 1, 1, 1);
		Microbot.doInvoke(new NewMenuEntry()
				.option(option)
				.target(targetNonNull)
				.identifier(LEAGUE_TRANSPORT_CC_OP_IDENTIFIER)
				.opcode(MenuAction.CC_OP.getId())
				.param0(LEAGUE_TRANSPORT_CC_OP_PARAM0)
				.param1(packedWidgetId)
				.itemId(-1),
				clickRect);
	}
}
