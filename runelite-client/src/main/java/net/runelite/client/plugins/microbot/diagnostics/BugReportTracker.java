package net.runelite.client.plugins.microbot.diagnostics;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.FlashNotification;
import net.runelite.client.config.Notification;
import net.runelite.client.config.NotificationSound;
import net.runelite.client.config.RequestFocusType;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

@Slf4j
@Singleton
public class BugReportTracker
{
	static final String CONFIG_KEY = "bugReports";
	static final int MAX_TRACKED = 20;
	static final int MAX_SUMMARY_CHARS = 120;
	static final long MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(180);
	static final long FIRST_REFRESH_SECONDS = 90;
	static final long REFRESH_HOURS = 6;
	static final Notification NOTICE = Notification.ON
		.withOverride(true)
		.withInitialized(true)
		.withTray(true)
		.withGameMessage(true)
		.withSendWhenFocused(true)
		.withRequestFocus(RequestFocusType.OFF)
		.withSound(NotificationSound.OFF)
		.withFlash(FlashNotification.DISABLED);

	private final ConfigManager configManager;
	private final MicrobotApi microbotApi;
	private final MicrobotPluginManager microbotPluginManager;
	private final Notifier notifier;
	private final ScheduledExecutorService executor;
	private final Gson gson;
	private final boolean disableTelemetry;
	private final LongSupplier clock;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private ScheduledFuture<?> refreshTask;

	@Inject
	BugReportTracker(ConfigManager configManager, MicrobotApi microbotApi, MicrobotPluginManager microbotPluginManager, Notifier notifier,
		ScheduledExecutorService executor, Gson gson, @Named("disableTelemetry") boolean disableTelemetry)
	{
		this(configManager, microbotApi, microbotPluginManager, notifier, executor, gson, disableTelemetry, System::currentTimeMillis);
	}

	BugReportTracker(ConfigManager configManager, MicrobotApi microbotApi, MicrobotPluginManager microbotPluginManager, Notifier notifier,
		ScheduledExecutorService executor, Gson gson, boolean disableTelemetry, LongSupplier clock)
	{
		this.configManager = configManager;
		this.microbotApi = microbotApi;
		this.microbotPluginManager = microbotPluginManager;
		this.notifier = notifier;
		this.executor = executor;
		this.gson = gson;
		this.disableTelemetry = disableTelemetry;
		this.clock = clock;
	}

	public synchronized void start()
	{
		if (refreshTask != null || disableTelemetry)
		{
			return;
		}
		refreshTask = executor.scheduleWithFixedDelay(() -> refresh().exceptionally(e ->
		{
			log.debug("Could not check bug report statuses", e);
			return 0;
		}), FIRST_REFRESH_SECONDS, TimeUnit.HOURS.toSeconds(REFRESH_HOURS), TimeUnit.SECONDS);
	}

	public synchronized void stop()
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(false);
			refreshTask = null;
		}
	}

	public boolean isDisabled()
	{
		return disableTelemetry || Microbot.isTelemetryDisabled();
	}

	public String disabledReason()
	{
		if (disableTelemetry)
		{
			return "No updates: Microbot was started with the --disable-telemetry option.";
		}
		return Microbot.isTelemetryDisabled() ? "No updates: 'Disable telemetry' is turned on in the Microbot settings." : null;
	}

	public void onChange(Runnable listener)
	{
		listeners.add(listener);
	}

	public void removeListener(Runnable listener)
	{
		listeners.remove(listener);
	}

	public synchronized List<TrackedBugReport> reports()
	{
		List<TrackedBugReport> reports = load();
		reports.sort(Comparator.comparing(TrackedBugReport::isSeen));
		return reports;
	}

	public synchronized int unseenCount()
	{
		return (int) load().stream().filter(report -> !report.isSeen()).count();
	}

	public synchronized void markSeen()
	{
		List<TrackedBugReport> reports = load();
		if (reports.stream().allMatch(TrackedBugReport::isSeen))
		{
			return;
		}
		reports.forEach(report -> report.setSeen(true));
		save(reports);
		fireChange();
	}

	public void track(BugReportReceipt receipt, String pluginInternalName, String plugin, String description)
	{
		synchronized (this)
		{
			List<TrackedBugReport> reports = load();
			TrackedBugReport report = new TrackedBugReport();
			report.setCode(receipt.getCode());
			report.setFollowUpToken(receipt.getFollowUpToken());
			report.setPluginInternalName(pluginInternalName);
			report.setPlugin(plugin);
			String summary = description == null ? "" : description.trim().replaceAll("\\s+", " ");
			report.setSummary(summary.length() > MAX_SUMMARY_CHARS ? summary.substring(0, MAX_SUMMARY_CHARS - 3) + "..." : summary);
			report.setCreatedAt(clock.getAsLong());
			reports.add(0, report);
			save(reports);
		}
		fireChange();
	}

	public CompletableFuture<Integer> refresh()
	{
		Map<String, String> tokens = new LinkedHashMap<>();
		synchronized (this)
		{
			load().stream()
				.filter(report -> report.getFollowUpToken() != null)
				.forEach(report -> tokens.put(report.getCode(), report.getFollowUpToken()));
		}
		if (tokens.isEmpty() || isDisabled())
		{
			return CompletableFuture.completedFuture(0);
		}
		return microbotApi.getBugReportStatuses(tokens).thenApply(statuses ->
		{
			List<TrackedBugReport> changed = apply(statuses);
			for (TrackedBugReport report : changed)
			{
				String notice = noticeFor(report, installedVersion(report));
				if (notice != null)
				{
					notifier.notify(NOTICE, notice);
				}
			}
			return changed.size();
		});
	}

	public CompletableFuture<Void> answer(String code, String message)
	{
		TrackedBugReport report;
		synchronized (this)
		{
			report = load().stream().filter(r -> r.getCode().equals(code)).findFirst().orElse(null);
		}
		if (report == null || report.getFollowUpToken() == null || isDisabled())
		{
			CompletableFuture<Void> failed = new CompletableFuture<>();
			failed.completeExceptionally(new IllegalStateException("This report can't be answered from here"));
			return failed;
		}
		String text = message.trim();
		return microbotApi.replyToBugReport(code, report.getFollowUpToken(), text).thenRun(() ->
		{
			synchronized (this)
			{
				List<TrackedBugReport> reports = load();
				reports.stream().filter(r -> r.getCode().equals(code)).forEach(r ->
				{
					r.setAnswer(text);
					r.setQuestion(false);
				});
				save(reports);
			}
			fireChange();
		});
	}

	public String statusLabel(TrackedBugReport report)
	{
		return statusLabel(report, installedVersion(report));
	}

	List<TrackedBugReport> apply(List<BugReportStatus> statuses)
	{
		List<TrackedBugReport> changed = new ArrayList<>();
		boolean edited = false;
		synchronized (this)
		{
			Map<String, BugReportStatus> byCode = statuses.stream()
				.filter(status -> status.getCode() != null && status.getStatus() != null)
				.collect(Collectors.toMap(BugReportStatus::getCode, Function.identity(), (a, b) -> a));
			List<TrackedBugReport> reports = load();
			for (TrackedBugReport report : reports)
			{
				BugReportStatus status = byCode.get(report.getCode());
				if (status == null)
				{
					continue;
				}
				boolean news = !status.getStatus().equals(report.getStatus())
					|| !Objects.equals(status.getFixedInVersion(), report.getFixedInVersion())
					|| status.isFixedInPlugin() != report.isFixedInPlugin()
					|| status.isQuestion() != report.isQuestion()
					|| (report.getNote() == null && status.getNote() != null);
				boolean noteEdited = !Objects.equals(status.getNote(), report.getNote());
				if (!news && !noteEdited)
				{
					continue;
				}
				report.setStatus(status.getStatus());
				report.setFixedInVersion(status.getFixedInVersion());
				report.setFixedInPlugin(status.isFixedInPlugin());
				report.setQuestion(status.isQuestion());
				report.setNote(status.getNote());
				edited = true;
				if (news)
				{
					report.setSeen(false);
					changed.add(report);
				}
			}
			if (edited)
			{
				save(reports);
			}
		}
		if (edited)
		{
			fireChange();
		}
		return changed;
	}

	String installedVersion(TrackedBugReport report)
	{
		if (!report.isFixedInPlugin())
		{
			return RuneLiteProperties.getMicrobotVersion();
		}
		return report.getPluginInternalName() == null ? null
			: microbotPluginManager.getInstalledPluginVersion(report.getPluginInternalName()).orElse(null);
	}

	public static boolean asksQuestion(TrackedBugReport report)
	{
		return BugReportStatus.INVESTIGATING.equals(report.getStatus()) && report.isQuestion() && report.getNote() != null;
	}

	static String statusLabel(TrackedBugReport report, String installedVersion)
	{
		switch (report.getStatus())
		{
			case BugReportStatus.INVESTIGATING:
				if (asksQuestion(report))
				{
					return "We have a question for you";
				}
				return report.getAnswer() != null ? "Being looked at. Thanks for your answer" : "Being looked at";
			case BugReportStatus.FIXED:
				if (report.getFixedInVersion() == null)
				{
					return "Fixed. " + howToUpdate(report);
				}
				return "Fixed in " + fixedIn(report) + (hasFix(report, installedVersion) ? ", which you have" : ". " + howToUpdate(report));
			case BugReportStatus.WONT_FIX:
				return "Won't be fixed";
			case BugReportStatus.CLOSED:
				return "Closed";
			default:
				return "Received";
		}
	}

	static String noticeFor(TrackedBugReport report, String installedVersion)
	{
		String subject = "The " + report.getPlugin() + " problem you reported";
		switch (report.getStatus())
		{
			case BugReportStatus.FIXED:
				if (report.getFixedInVersion() == null)
				{
					return subject + " is fixed. " + howToUpdate(report) + ".";
				}
				return hasFix(report, installedVersion)
					? subject + " is fixed, and you already have the fix (version " + report.getFixedInVersion() + ")."
					: subject + " is fixed in version " + report.getFixedInVersion() + ". " + howToUpdate(report) + ".";
			case BugReportStatus.WONT_FIX:
				return report.getNote() != null
					? subject + " won't be fixed. Open My reports from the red bug button in the sidebar to see why."
					: subject + " won't be fixed. Ask on Discord and mention " + report.getCode() + " if you want to know more.";
			case BugReportStatus.INVESTIGATING:
				return asksQuestion(report)
					? "We have a question about the " + report.getPlugin() + " problem you reported. Open My reports from the red bug button in the sidebar to answer it."
					: null;
			case BugReportStatus.CLOSED:
			default:
				return null;
		}
	}

	private static String fixedIn(TrackedBugReport report)
	{
		return (report.isFixedInPlugin() ? report.getPlugin() : "Microbot") + " " + report.getFixedInVersion();
	}

	private static String howToUpdate(TrackedBugReport report)
	{
		return report.isFixedInPlugin()
			? "Update " + report.getPlugin() + " in the Microbot Plugin Hub to get the fix"
			: "Restart Microbot from the launcher to get the fix";
	}

	private static boolean hasFix(TrackedBugReport report, String installedVersion)
	{
		return report.getFixedInVersion() != null && installedVersion != null
			&& Rs2UiHelper.compareVersions(installedVersion, report.getFixedInVersion()) >= 0;
	}

	private List<TrackedBugReport> load()
	{
		String json = configManager.getConfiguration(MicrobotConfig.configGroup, CONFIG_KEY);
		List<TrackedBugReport> reports = new ArrayList<>();
		if (json == null || json.isEmpty())
		{
			return reports;
		}
		try
		{
			TrackedBugReport[] stored = gson.fromJson(json, TrackedBugReport[].class);
			if (stored != null)
			{
				long cutoff = clock.getAsLong() - MAX_AGE_MILLIS;
				Arrays.stream(stored)
					.filter(report -> report != null && report.getCode() != null && report.getCreatedAt() >= cutoff)
					.forEach(reports::add);
			}
		}
		catch (JsonParseException e)
		{
			log.debug("Ignoring unreadable bug report list", e);
		}
		return reports;
	}

	private void save(List<TrackedBugReport> reports)
	{
		List<TrackedBugReport> kept = reports.size() > MAX_TRACKED ? reports.subList(0, MAX_TRACKED) : reports;
		configManager.setConfiguration(MicrobotConfig.configGroup, CONFIG_KEY, gson.toJson(kept));
	}

	private void fireChange()
	{
		listeners.forEach(Runnable::run);
	}
}
