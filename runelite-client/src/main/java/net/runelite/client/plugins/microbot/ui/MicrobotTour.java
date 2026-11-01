package net.runelite.client.plugins.microbot.ui;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.plugins.microbot.alerts.DeviceLinkFlow;
import net.runelite.client.plugins.microbot.recovery.StartupRecovery;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ContainableFrame;
import net.runelite.client.ui.NavigationButton;

@Singleton
public class MicrobotTour
{
	static final int TOUR_VERSION = 2;
	static final String TOUR_PROPERTY = "microbot.tour";
	static final String ANTIBAN_TOOLTIP = "Antiban";
	static final String INVENTORY_SETUPS_TOOLTIP = "Inventory Setups";
	static final long SETTLE_MS = 2_500;
	static final long LOAD_FALLBACK_MS = 45_000;
	static final long GIVE_UP_MS = 5 * 60_000;
	private static final int TRACK_MS = 150;
	private static final int GATE_MS = 1_000;
	private static final int PREPARE_MS = 100;

	private final ClientToolbar clientToolbar;
	private final MicrobotHelpMenu helpMenu;
	private final Provider<MicrobotTopLevelConfigPanel> topLevelPanel;
	private final Provider<MicrobotCloudPanel> cloudPanel;
	private final DeviceLinkFlow deviceLinkFlow;
	private final ScheduledExecutorService executor;
	private final MicrobotTourState state;
	private final Timer tracker = new Timer(TRACK_MS, e -> track(false));
	private final Timer gate = new Timer(GATE_MS, e -> pollGate());
	private final WindowAdapter windowListener = new WindowAdapter()
	{
		@Override
		public void windowIconified(WindowEvent e)
		{
			hideOverlay();
		}

		@Override
		public void windowDeiconified(WindowEvent e)
		{
			track(true);
		}

	};

	private volatile boolean gameLoadedSeen;
	private NavigationButton navButton;
	private boolean installed;
	private long armedAt = -1;
	private long loadedAt = -1;
	private JFrame frame;
	private MicrobotTourCallout callout;
	private MicrobotTourSpotlight spotlight;
	private MicrobotTourSequence sequence;
	private MicrobotTourStep current;
	private boolean countAutoShow;
	private boolean userPlaced;
	private int navigation;
	private Rectangle lastTarget;
	private Rectangle lastSidebar;
	private Rectangle lastMenuBar;
	private Rectangle lastFrame;
	private boolean lastTargetKnown;

	@Inject
	MicrobotTour(ClientToolbar clientToolbar, MicrobotHelpMenu helpMenu, Provider<MicrobotTopLevelConfigPanel> topLevelPanel,
		Provider<MicrobotCloudPanel> cloudPanel, DeviceLinkFlow deviceLinkFlow, ScheduledExecutorService executor)
	{
		this.clientToolbar = clientToolbar;
		this.helpMenu = helpMenu;
		this.topLevelPanel = topLevelPanel;
		this.cloudPanel = cloudPanel;
		this.deviceLinkFlow = deviceLinkFlow;
		this.executor = executor;
		this.state = new MicrobotTourState(new File(new File(RuneLite.RUNELITE_DIR, "microbot"), "onboarding.properties").toPath());
		tracker.setCoalesce(true);
		gate.setCoalesce(true);
	}

	public void install(NavigationButton navButton)
	{
		onEdt(() ->
		{
			this.navButton = navButton;
			installed = true;
			cloudPanel.get().onUsed(this::cloudUsed);
			helpMenu.onShowTour(this::replay);
		});
		submit(() ->
		{
			state.load();
			boolean linked = deviceLinkFlow.status().isOn();
			SwingUtilities.invokeLater(() -> applyLoaded(linked));
		});
	}

	public void uninstall()
	{
		onEdt(() ->
		{
			installed = false;
			disarm();
			end();
			cloudPanel.get().onUsed(null);
			helpMenu.onShowTour(null);
			navButton = null;
		});
	}

	public void replay()
	{
		onEdt(() ->
		{
			if (!installed || findFrame() == null)
			{
				return;
			}
			disarm();
			begin(false);
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gameState = event.getGameState();
		if (gameLoadedSeen || (gameState != GameState.LOGIN_SCREEN && gameState != GameState.LOGIN_SCREEN_AUTHENTICATOR && gameState != GameState.LOGGED_IN))
		{
			return;
		}
		gameLoadedSeen = true;
		long now = System.currentTimeMillis();
		SwingUtilities.invokeLater(() ->
		{
			if (loadedAt < 0)
			{
				loadedAt = now;
			}
		});
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		SwingUtilities.invokeLater(() ->
		{
			disarm();
			end();
		});
	}

	static boolean shouldAutoStart(boolean headless, boolean testMode, String tourProperty, boolean safeMode, boolean shouldAutoShow)
	{
		return !headless
			&& !testMode
			&& !"false".equalsIgnoreCase(tourProperty)
			&& !safeMode
			&& shouldAutoShow;
	}

	static boolean isReady(boolean frameShowing, boolean iconified, boolean otherDialogShowing, boolean gameLoaded, long msSinceLoaded)
	{
		return frameShowing
			&& !iconified
			&& !otherDialogShowing
			&& gameLoaded
			&& msSinceLoaded >= SETTLE_MS;
	}

	private void applyLoaded(boolean linked)
	{
		if (!installed)
		{
			return;
		}
		if (linked && !state.isCloudBadgeSeen())
		{
			state.markCloudBadgeSeen();
			persist();
		}
		cloudPanel.get().setNewBadgeVisible(MicrobotTourState.cloudBadgeVisible(state.isCloudBadgeSeen(), linked));
		if (shouldAutoStart(GraphicsEnvironment.isHeadless(), Boolean.getBoolean("microbot.test.mode"),
			System.getProperty(TOUR_PROPERTY), StartupRecovery.mode().isSafeMode(), state.shouldAutoShow(TOUR_VERSION)))
		{
			armedAt = System.currentTimeMillis();
			gate.start();
		}
	}

	private void disarm()
	{
		gate.stop();
		armedAt = -1;
	}

	private void pollGate()
	{
		if (!installed || armedAt < 0)
		{
			disarm();
			return;
		}
		long now = System.currentTimeMillis();
		long since = loadedAt >= 0 ? loadedAt : (now - armedAt >= LOAD_FALLBACK_MS ? armedAt + LOAD_FALLBACK_MS : -1);
		if (since >= 0 && now - since > GIVE_UP_MS)
		{
			disarm();
			return;
		}
		JFrame target = findFrame();
		boolean showing = target != null;
		boolean iconified = showing && (target.getExtendedState() & Frame.ICONIFIED) != 0;
		boolean dialog = showing && otherDialogShowing(target);
		if (isReady(showing, iconified, dialog, since >= 0, since >= 0 ? now - since : 0) && navButton != null)
		{
			disarm();
			begin(true);
		}
	}

	private static boolean otherDialogShowing(Window owner)
	{
		return Arrays.stream(owner.getOwnedWindows()).anyMatch(w -> w instanceof Dialog && w.isShowing());
	}

	private static JFrame findFrame()
	{
		return (JFrame) Arrays.stream(Window.getWindows())
			.filter(w -> w instanceof ContainableFrame && w.isShowing())
			.findFirst()
			.orElse(null);
	}

	private void begin(boolean auto)
	{
		JFrame owner = findFrame();
		if (owner == null)
		{
			return;
		}
		end();
		frame = owner;
		countAutoShow = auto;
		sequence = new MicrobotTourSequence(Arrays.asList(MicrobotTourStep.values()),
			step -> (step != MicrobotTourStep.HELP && step != MicrobotTourStep.REPORT_PROBLEM) || helpMenu.getButton() != null);
		callout = new MicrobotTourCallout(frame, this::end, this::neverAgain, this::back, this::next, () -> userPlaced = true);
		spotlight = new MicrobotTourSpotlight(frame.getLayeredPane());
		frame.addWindowListener(windowListener);
		sequence.first().ifPresent(step -> go(step, true));
	}

	private void end()
	{
		if (current == MicrobotTourStep.MICROBOT_CLOUD)
		{
			cloudUsed();
		}
		navigation++;
		tracker.stop();
		if (frame != null)
		{
			frame.removeWindowListener(windowListener);
		}
		if (callout != null)
		{
			callout.dispose();
		}
		if (spotlight != null)
		{
			spotlight.remove();
		}
		callout = null;
		spotlight = null;
		sequence = null;
		current = null;
		frame = null;
		lastTargetKnown = false;
	}

	private void next()
	{
		if (sequence == null || current == null)
		{
			return;
		}
		MicrobotTourStep step = sequence.next(current).orElse(null);
		if (step == null)
		{
			state.markTourSeen(TOUR_VERSION);
			persist();
			end();
		}
		else
		{
			go(step, true);
		}
	}

	private void neverAgain()
	{
		state.markTourSeen(TOUR_VERSION);
		persist();
		end();
	}

	private void back()
	{
		if (sequence == null || current == null)
		{
			return;
		}
		sequence.previous(current).ifPresent(step -> go(step, false));
	}

	private void go(MicrobotTourStep step, boolean forward)
	{
		int token = ++navigation;
		prepare(step);
		Timer settle = new Timer(PREPARE_MS, e ->
		{
			if (token != navigation || sequence == null)
			{
				return;
			}
			if (step.isRequiresTarget() && locate(step) == null)
			{
				sequence.markSkipped(step);
				MicrobotTourStep following = (forward ? sequence.next(step) : sequence.previous(step)).orElse(null);
				if (following != null)
				{
					go(following, forward);
				}
				else if (current != null)
				{
					show(current);
				}
				else
				{
					end();
				}
				return;
			}
			show(step);
		});
		settle.setRepeats(false);
		settle.start();
	}

	private void show(MicrobotTourStep step)
	{
		if (current == MicrobotTourStep.MICROBOT_CLOUD && step != MicrobotTourStep.MICROBOT_CLOUD)
		{
			cloudUsed();
		}
		current = step;
		userPlaced = false;
		if (countAutoShow)
		{
			countAutoShow = false;
			state.recordAutoShow(TOUR_VERSION);
			persist();
		}
		callout.setStep(step, sequence.position(step), sequence.total(), sequence.previous(step).isPresent(), sequence.isLast(step));
		lastTargetKnown = false;
		track(true);
		tracker.start();
	}

	private void prepare(MicrobotTourStep step)
	{
		switch (step)
		{
			case WELCOME:
				break;
			case INSTALLED:
				openMicrobotPanel();
				topLevelPanel.get().showInstalledTab();
				break;
			case MICROBOT_CLOUD:
				openMicrobotPanel();
				cloudPanel.get().expand();
				break;
			case ANTIBAN:
			case INVENTORY_SETUPS:
			case REPORT_PROBLEM:
			case HELP:
				if (!clientToolbar.isSidebarOpen())
				{
					openMicrobotPanel();
				}
				break;
			default:
				openMicrobotPanel();
				break;
		}
	}

	private void openMicrobotPanel()
	{
		if (navButton != null && !clientToolbar.isPanelOpen(navButton))
		{
			clientToolbar.openPanel(navButton);
		}
	}

	private Rectangle locate(MicrobotTourStep step)
	{
		switch (step)
		{
			case SIDEBAR:
				return clientToolbar.getSidebarTabsBounds();
			case COMMUNITY_PLUGINS:
				return clientToolbar.getNavigationBounds(navButton);
			case INSTALLED:
				return screenBounds(topLevelPanel.get().getTabContent());
			case PLUGIN_HUB:
				return screenBounds(topLevelPanel.get().getPluginHubTab());
			case PROFILES:
				return screenBounds(topLevelPanel.get().getProfilesTab());
			case MICROBOT_CLOUD:
				return screenBounds(cloudPanel.get());
			case ANTIBAN:
				return sidebarTabBounds(ANTIBAN_TOOLTIP);
			case INVENTORY_SETUPS:
				return sidebarTabBounds(INVENTORY_SETUPS_TOOLTIP);
			case REPORT_PROBLEM:
			case HELP:
				NavigationButton help = helpMenu.getButton();
				return help == null ? null : clientToolbar.getNavigationBounds(help);
			default:
				return null;
		}
	}

	private Rectangle sidebarTabBounds(String tooltip)
	{
		NavigationButton button = clientToolbar.findSidebarNavigation(tooltip);
		return button == null ? null : clientToolbar.getNavigationBounds(button);
	}

	private void track(boolean force)
	{
		if (callout == null || current == null || frame == null)
		{
			return;
		}
		if (!frame.isShowing() || (frame.getExtendedState() & Frame.ICONIFIED) != 0)
		{
			hideOverlay();
			return;
		}
		Rectangle target = locate(current);
		Rectangle sidebar = clientToolbar.getSidebarBounds();
		Rectangle menuBar = menuBarBounds();
		Rectangle frameBounds = frame.getBounds();
		if (!force && lastTargetKnown && callout.isVisible()
			&& Objects.equals(target, lastTarget) && Objects.equals(sidebar, lastSidebar)
			&& Objects.equals(menuBar, lastMenuBar) && Objects.equals(frameBounds, lastFrame))
		{
			return;
		}
		lastTarget = target;
		lastSidebar = sidebar;
		lastMenuBar = menuBar;
		lastFrame = frameBounds;
		lastTargetKnown = true;

		spotlight.update(sidebar, menuBar, target);
		MicrobotTourPlacement.Side side = current.getPreferredSide();
		if (target != null && menuBar != null && menuBar.contains(target))
		{
			side = MicrobotTourPlacement.Side.BELOW;
		}
		if (!userPlaced)
		{
			MicrobotTourPlacement.Result placement = MicrobotTourPlacement.place(target, sidebar, callout.getSize(),
				frameBounds, usableScreen(frame), side, MicrobotTourPlacement.GAP);
			callout.setLocation(placement.getLocation());
		}
		if (!callout.isVisible())
		{
			callout.setVisible(true);
		}
	}

	private void hideOverlay()
	{
		lastTargetKnown = false;
		if (callout != null)
		{
			callout.setVisible(false);
		}
		if (spotlight != null)
		{
			spotlight.hide();
		}
	}

	private Rectangle menuBarBounds()
	{
		JMenuBar menuBar = frame.getJMenuBar();
		return screenBounds(menuBar);
	}

	private static Rectangle usableScreen(Window window)
	{
		GraphicsConfiguration gc = window.getGraphicsConfiguration();
		Rectangle bounds = new Rectangle(gc.getBounds());
		Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
		bounds.x += insets.left;
		bounds.y += insets.top;
		bounds.width -= insets.left + insets.right;
		bounds.height -= insets.top + insets.bottom;
		return bounds;
	}

	private static Rectangle screenBounds(Component component)
	{
		if (component == null || !component.isShowing())
		{
			return null;
		}
		return new Rectangle(component.getLocationOnScreen(), component.getSize());
	}

	private void cloudUsed()
	{
		cloudPanel.get().setNewBadgeVisible(false);
		if (!state.isCloudBadgeSeen())
		{
			state.markCloudBadgeSeen();
			persist();
		}
	}

	private void persist()
	{
		submit(state::save);
	}

	private void submit(Runnable task)
	{
		try
		{
			executor.execute(task);
		}
		catch (RejectedExecutionException ignored)
		{
		}
	}

	private static void onEdt(Runnable task)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			task.run();
		}
		else
		{
			SwingUtilities.invokeLater(task);
		}
	}
}
