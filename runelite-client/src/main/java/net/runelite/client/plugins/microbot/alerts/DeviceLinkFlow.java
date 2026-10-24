package net.runelite.client.plugins.microbot.alerts;

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.util.RunnableExceptionLogger;

@Slf4j
@Singleton
public class DeviceLinkFlow
{
	static final String DEFAULT_LABEL = "Microbot client";
	static final long MAX_WAIT_SECONDS = 600;
	static final long DEFAULT_INTERVAL_SECONDS = 5;
	static final long SLOW_DOWN_SECONDS = 5;
	static final int MAX_LABEL_LENGTH = 60;

	static final String ALERTS_PAGE = "https://dashboard.microbot.cloud/alerts";

	static final String TELEMETRY_OFF = "Telemetry is turned off by a launch option, so this client cannot be linked.\n"
		+ "Remove --disable-telemetry or -Dmicrobot.disableTelemetry=true from how you start the client, then restart it.";
	static final String ALREADY_LINKED = "This client is linked to a Microbot account.\n\n"
		+ "To rename or remove it, go to " + ALERTS_PAGE + "\n"
		+ "To link it to a different account, click Link to another account.";
	static final String LINKED_BY_LAUNCHER = "The launcher linked this client to your Microbot account.\n\n"
		+ "To rename or remove it, go to " + ALERTS_PAGE;
	static final String LAUNCHER_TOKEN = "The launcher started this client with a link that no longer works.\nStart it again from the launcher while signed in to link it.";
	static final String UNREACHABLE = "Could not reach microbot.cloud.\nCheck your connection and try again.";
	static final String TOO_MANY_PENDING = "Too many clients are waiting to be linked from this network.\nApprove one of them or try again in 10 minutes.";
	static final String DENIED = "Linking was declined on microbot.cloud.\nYou can link again from Microbot Cloud at the bottom of the Microbot sidebar.";
	static final String EXPIRED = "The link code expired before it was approved.\nYou can link again from Microbot Cloud at the bottom of the Microbot sidebar.";
	static final String TOO_MANY_LINKS = "Your account already has 50 linked clients.\nRemove one on the Alerts page and this client links on its own.";

	private final MicrobotApi microbotApi;
	private final ClientLinkToken clientLinkToken;
	private final ConfigManager configManager;
	private final DeviceLinkDialog dialog;
	private final ScheduledExecutorService executor;
	private final LongSupplier clock;
	private Attempt attempt;

	@Inject
	DeviceLinkFlow(MicrobotApi microbotApi, ClientLinkToken clientLinkToken, ConfigManager configManager,
		DeviceLinkDialog dialog, ScheduledExecutorService executor)
	{
		this(microbotApi, clientLinkToken, configManager, dialog, executor, System::currentTimeMillis);
	}

	DeviceLinkFlow(MicrobotApi microbotApi, ClientLinkToken clientLinkToken, ConfigManager configManager,
		DeviceLinkDialog dialog, ScheduledExecutorService executor, LongSupplier clock)
	{
		this.microbotApi = microbotApi;
		this.clientLinkToken = clientLinkToken;
		this.configManager = configManager;
		this.dialog = dialog;
		this.executor = executor;
		this.clock = clock;
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		CompletableFuture<Void> cancelled = abandon();
		if (cancelled != null)
		{
			event.waitFor(cancelled);
		}
	}

	public synchronized void start()
	{
		if (clientLinkToken.isTelemetryDisabled())
		{
			configManager.setConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyDisableTelemetry, false);
			if (clientLinkToken.isTelemetryDisabled())
			{
				dialog.showMessage(TELEMETRY_OFF);
			}
			return;
		}
		if (attempt != null)
		{
			if (attempt.link != null)
			{
				showWaiting(attempt);
			}
			return;
		}
		if (clientLinkToken.get().isPresent())
		{
			if (clientLinkToken.isFromEnvironment())
			{
				dialog.showLinked(LINKED_BY_LAUNCHER, ALERTS_PAGE, null);
			}
			else
			{
				dialog.showLinked(ALREADY_LINKED, ALERTS_PAGE, this::relink);
			}
			return;
		}
		if (clientLinkToken.isFromEnvironment())
		{
			dialog.showMessage(LAUNCHER_TOKEN);
			return;
		}
		startAttempt();
	}

	synchronized void relink()
	{
		if (attempt == null && !clientLinkToken.isTelemetryDisabled() && !clientLinkToken.isFromEnvironment())
		{
			startAttempt();
		}
	}

	private void startAttempt()
	{
		Attempt next = new Attempt();
		attempt = next;
		executor.execute(RunnableExceptionLogger.wrap(() -> begin(next)));
	}

	public void stop()
	{
		abandon();
	}

	private CompletableFuture<Void> abandon()
	{
		String deviceCode;
		synchronized (this)
		{
			if (attempt == null)
			{
				return null;
			}
			deviceCode = cancel(attempt);
			dialog.close();
		}
		return cancelOnServer(deviceCode);
	}

	synchronized boolean isLinking()
	{
		return attempt != null;
	}

	public synchronized BotAlertStatus status()
	{
		if (clientLinkToken.isTelemetryDisabled())
		{
			return BotAlertStatus.TELEMETRY_OFF;
		}
		if (attempt != null)
		{
			return BotAlertStatus.LINKING;
		}
		if (clientLinkToken.get().isPresent())
		{
			return clientLinkToken.isFromEnvironment() ? BotAlertStatus.LINKED_BY_LAUNCHER : BotAlertStatus.LINKED;
		}
		return clientLinkToken.configured().isPresent() ? BotAlertStatus.REVOKED : BotAlertStatus.NOT_LINKED;
	}

	private void begin(Attempt current)
	{
		DeviceLinkStart link;
		try
		{
			link = microbotApi.startDeviceLink(DEFAULT_LABEL);
		}
		catch (DeviceLinkStart.ThrottledException e)
		{
			finish(current, TOO_MANY_PENDING);
			return;
		}
		catch (IOException e)
		{
			log.debug("Unable to start linking this client", e);
			finish(current, UNREACHABLE);
			return;
		}
		synchronized (this)
		{
			if (attempt != current)
			{
				cancelOnServer(link.getDeviceCode());
				return;
			}
			long waitSeconds = link.getExpiresIn() > 0 ? Math.min(link.getExpiresIn(), MAX_WAIT_SECONDS) : MAX_WAIT_SECONDS;
			current.link = link;
			current.deadline = clock.getAsLong() + TimeUnit.SECONDS.toMillis(waitSeconds);
			current.intervalSeconds = link.getInterval() > 0 ? link.getInterval() : DEFAULT_INTERVAL_SECONDS;
			log.info("Waiting for this client to be approved for bot alerts in the browser");
			showWaiting(current);
			schedule(current);
			dialog.browse(link.getVerificationUrl());
		}
	}

	private void poll(Attempt current)
	{
		synchronized (this)
		{
			if (attempt != current)
			{
				return;
			}
		}
		DeviceLinkPoll result;
		try
		{
			result = microbotApi.pollDeviceLink(current.link.getDeviceCode());
		}
		catch (IOException e)
		{
			log.debug("Unable to check whether this client was approved", e);
			result = DeviceLinkPoll.of(DeviceLinkPoll.Status.PENDING);
		}
		switch (result.getStatus())
		{
			case LINKED:
				link(current, result);
				return;
			case DENIED:
				finish(current, DENIED);
				return;
			case EXPIRED:
				finish(current, EXPIRED);
				return;
			default:
				break;
		}
		synchronized (this)
		{
			if (attempt != current)
			{
				return;
			}
			if (result.getStatus() == DeviceLinkPoll.Status.SLOW_DOWN)
			{
				current.intervalSeconds += SLOW_DOWN_SECONDS;
			}
			else if (result.getStatus() == DeviceLinkPoll.Status.TOO_MANY_LINKS && !current.full)
			{
				current.full = true;
				showWaiting(current);
			}
			if (clock.getAsLong() >= current.deadline)
			{
				attempt = null;
				dialog.showMessage(EXPIRED);
				return;
			}
			schedule(current);
		}
	}

	private void link(Attempt current, DeviceLinkPoll result)
	{
		synchronized (this)
		{
			if (attempt != current)
			{
				return;
			}
			attempt = null;
		}
		configManager.setConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyAlertLinkToken, result.getToken());
		log.info("This client is now linked for bot alerts");
		dialog.showMessage("Linked as " + displayLabel(result.getLabel()) + ".\nBot alerts are on for this client.");
	}

	private synchronized void finish(Attempt current, String message)
	{
		if (attempt != current)
		{
			return;
		}
		attempt = null;
		dialog.showMessage(message);
	}

	private synchronized String cancel(Attempt current)
	{
		if (attempt != current)
		{
			return null;
		}
		attempt = null;
		if (current.future != null)
		{
			current.future.cancel(false);
		}
		return current.link != null ? current.link.getDeviceCode() : null;
	}

	private CompletableFuture<Void> cancelOnServer(String deviceCode)
	{
		if (Strings.isNullOrEmpty(deviceCode))
		{
			return null;
		}
		try
		{
			return microbotApi.cancelDeviceLink(deviceCode);
		}
		catch (RuntimeException e)
		{
			log.debug("Unable to cancel the pending link for this client", e);
			return null;
		}
	}

	private void schedule(Attempt current)
	{
		current.future = executor.schedule(RunnableExceptionLogger.wrap(() -> poll(current)), current.intervalSeconds, TimeUnit.SECONDS);
	}

	private void showWaiting(Attempt current)
	{
		DeviceLinkStart link = current.link;
		String message = "Approve this client in the browser page that just opened.\n\n"
			+ "Code: " + link.getUserCode() + "\n\n"
			+ "If no page opened, go to " + link.getVerificationUrl() + "\n"
			+ "The code expires in 10 minutes.";
		if (current.full)
		{
			message += "\n\n" + TOO_MANY_LINKS;
		}
		dialog.showWaiting(message, link.getVerificationUrl(), () -> cancelOnServer(cancel(current)));
	}

	static String displayLabel(String label)
	{
		String clean = Strings.nullToEmpty(label).replaceAll("\\p{Cntrl}", " ").trim();
		if (clean.isEmpty())
		{
			return DEFAULT_LABEL;
		}
		return clean.length() > MAX_LABEL_LENGTH ? clean.substring(0, MAX_LABEL_LENGTH) : clean;
	}

	private static class Attempt
	{
		private DeviceLinkStart link;
		private long deadline;
		private long intervalSeconds;
		private boolean full;
		private ScheduledFuture<?> future;
	}
}
