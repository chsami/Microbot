package net.runelite.client.plugins.microbot.diagnostics;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import net.runelite.client.Notifier;
import net.runelite.client.config.Notification;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import org.mockito.ArgumentCaptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BugReportTrackerTest
{
	private final Map<String, String> config = new HashMap<>();
	private final AtomicLong now = new AtomicLong(TimeUnit.DAYS.toMillis(20_000));
	private ConfigManager configManager;
	private MicrobotApi api;
	private Notifier notifier;
	private MicrobotPluginManager plugins;
	private ScheduledExecutorService executor;
	private BugReportTracker tracker;

	@Before
	public void setUp()
	{
		configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(eq(MicrobotConfig.configGroup), anyString())).thenAnswer(i -> config.get(i.getArgument(1)));
		doAnswer(i -> config.put(i.getArgument(1), i.getArgument(2)))
			.when(configManager).setConfiguration(eq(MicrobotConfig.configGroup), anyString(), anyString());
		api = mock(MicrobotApi.class);
		notifier = mock(Notifier.class);
		plugins = mock(MicrobotPluginManager.class);
		when(plugins.getInstalledPluginVersion(anyString())).thenReturn(Optional.empty());
		executor = mock(ScheduledExecutorService.class);
		tracker = tracker(false);
	}

	private BugReportTracker tracker(boolean disableTelemetry)
	{
		return new BugReportTracker(configManager, api, plugins, notifier, executor, new Gson(), disableTelemetry, now::get);
	}

	private static BugReportReceipt receipt(String code)
	{
		return new BugReportReceipt(code, "token-" + code);
	}

	private static BugReportStatus status(String code, String status, String fixedIn, String note)
	{
		BugReportStatus result = new BugReportStatus();
		result.setCode(code);
		result.setStatus(status);
		result.setFixedInVersion(fixedIn);
		result.setNote(note);
		return result;
	}

	@Test
	public void trackedReportsAreNewestFirstWithAShortSummary()
	{
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "first");
		tracker.track(receipt("MB-BBBBBB"), null, "Blast Furnace", "  stands   still\nat the belt " + "x".repeat(200));

		List<TrackedBugReport> reports = tracker.reports();
		assertEquals("MB-BBBBBB", reports.get(0).getCode());
		assertEquals("Blast Furnace", reports.get(0).getPlugin());
		assertTrue(reports.get(0).getSummary().startsWith("stands still at the belt x"));
		assertEquals(BugReportTracker.MAX_SUMMARY_CHARS, reports.get(0).getSummary().length());
		assertEquals(BugReportStatus.OPEN, reports.get(1).getStatus());
		assertEquals(0, tracker.unseenCount());
	}

	@Test
	public void onlyTheNewestReportsAreKept()
	{
		for (int i = 0; i < BugReportTracker.MAX_TRACKED + 3; i++)
		{
			tracker.track(receipt("MB-" + i), null, "Web Walker", "report " + i);
		}

		List<TrackedBugReport> reports = tracker.reports();
		assertEquals(BugReportTracker.MAX_TRACKED, reports.size());
		assertEquals("MB-" + (BugReportTracker.MAX_TRACKED + 2), reports.get(0).getCode());
	}

	@Test
	public void reportsOlderThanTheServerKeepsThemAreDropped()
	{
		tracker.track(receipt("MB-OLD111"), null, "Web Walker", "old");
		now.addAndGet(BugReportTracker.MAX_AGE_MILLIS + 1);
		tracker.track(receipt("MB-NEW111"), null, "Web Walker", "new");

		assertEquals(1, tracker.reports().size());
		assertEquals("MB-NEW111", tracker.reports().get(0).getCode());
	}

	@Test
	public void changedStatusesAreMarkedNewUntilSeen()
	{
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "stuck");
		tracker.track(receipt("MB-BBBBBB"), null, "Blast Furnace", "stuck");
		AtomicInteger changes = new AtomicInteger();
		tracker.onChange(changes::incrementAndGet);

		List<TrackedBugReport> changed = tracker.apply(List.of(
			status("MB-AAAAAA", BugReportStatus.FIXED, "2.6.43", "Fixed the door check."),
			status("MB-BBBBBB", BugReportStatus.OPEN, null, null),
			status("MB-UNKNWN", BugReportStatus.FIXED, "2.6.43", null)));

		assertEquals(1, changed.size());
		assertEquals("MB-AAAAAA", changed.get(0).getCode());
		assertEquals(1, tracker.unseenCount());
		assertEquals(1, changes.get());
		TrackedBugReport stored = tracker.reports().get(0);
		assertEquals("MB-AAAAAA", stored.getCode());
		assertEquals("2.6.43", stored.getFixedInVersion());
		assertEquals("Fixed the door check.", stored.getNote());

		assertTrue(tracker.apply(List.of(status("MB-AAAAAA", BugReportStatus.FIXED, "2.6.43", "Fixed the door check."))).isEmpty());
		tracker.markSeen();
		assertEquals(0, tracker.unseenCount());
	}

	@Test
	public void noticesTellThePlayerWhatToDo()
	{
		TrackedBugReport report = new TrackedBugReport();
		report.setPlugin("Web Walker");
		report.setStatus(BugReportStatus.FIXED);
		report.setFixedInVersion("2.6.43");

		assertEquals("The Web Walker problem you reported is fixed in version 2.6.43. Restart Microbot from the launcher to get the fix.",
			BugReportTracker.noticeFor(report, "2.6.42"));
		assertEquals("The Web Walker problem you reported is fixed, and you already have the fix (version 2.6.43).",
			BugReportTracker.noticeFor(report, "2.6.43"));
		assertEquals("Fixed in Microbot 2.6.43. Restart Microbot from the launcher to get the fix", BugReportTracker.statusLabel(report, "2.6.42"));
		assertEquals("Fixed in Microbot 2.6.43, which you have", BugReportTracker.statusLabel(report, "2.6.43"));

		report.setStatus(BugReportStatus.WONT_FIX);
		report.setFixedInVersion(null);
		report.setCode("MB-AAAAAA");
		assertEquals("The Web Walker problem you reported won't be fixed. Ask on Discord and mention MB-AAAAAA if you want to know more.",
			BugReportTracker.noticeFor(report, "2.6.43"));
		report.setNote("It is a game limit.");
		assertEquals("The Web Walker problem you reported won't be fixed. Open My reports from the red bug button in the sidebar to see why.",
			BugReportTracker.noticeFor(report, "2.6.43"));
		report.setNote(null);

		report.setStatus(BugReportStatus.INVESTIGATING);
		assertNull(BugReportTracker.noticeFor(report, "2.6.43"));
		assertEquals("Being looked at", BugReportTracker.statusLabel(report, "2.6.43"));
		report.setNote("We can reproduce it.");
		assertNull(BugReportTracker.noticeFor(report, "2.6.43"));
		assertEquals("Being looked at", BugReportTracker.statusLabel(report, "2.6.43"));
		report.setNote("Which bank?");
		report.setQuestion(true);
		assertEquals("We have a question about the Web Walker problem you reported. Open My reports from the red bug button in the sidebar to answer it.",
			BugReportTracker.noticeFor(report, "2.6.43"));
		assertEquals("We have a question for you", BugReportTracker.statusLabel(report, "2.6.43"));

		report.setStatus(BugReportStatus.FIXED);
		report.setQuestion(false);
		report.setFixedInVersion(null);
		assertEquals("The Web Walker problem you reported is fixed. Restart Microbot from the launcher to get the fix.", BugReportTracker.noticeFor(report, "2.6.43"));
		assertEquals("Fixed. Restart Microbot from the launcher to get the fix", BugReportTracker.statusLabel(report, "2.6.43"));

		report.setStatus(BugReportStatus.OPEN);
		assertNull(BugReportTracker.noticeFor(report, "2.6.43"));
		assertEquals("Received", BugReportTracker.statusLabel(report, "2.6.43"));
	}

	@Test
	public void refreshNotifiesOnlyAboutChanges()
	{
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "stuck");
		when(api.getBugReportStatuses(Map.of("MB-AAAAAA", "token-MB-AAAAAA"))).thenReturn(CompletableFuture.completedFuture(
			List.of(status("MB-AAAAAA", BugReportStatus.FIXED, "999.0.0", null))));

		tracker.refresh().join();
		tracker.refresh().join();

		ArgumentCaptor<Notification> type = ArgumentCaptor.forClass(Notification.class);
		verify(notifier).notify(type.capture(), eq("The Web Walker problem you reported is fixed in version 999.0.0. Restart Microbot from the launcher to get the fix."));
		assertTrue(type.getValue().isSendWhenFocused());
		assertTrue(type.getValue().isGameMessage());
		assertTrue(type.getValue().isTray());
		assertTrue(type.getValue().isOverride());
	}

	@Test
	public void aFailedCheckIsQuiet()
	{
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "stuck");
		CompletableFuture<List<BugReportStatus>> failed = new CompletableFuture<>();
		failed.completeExceptionally(new IOException("offline"));
		when(api.getBugReportStatuses(any())).thenReturn(failed);

		try
		{
			tracker.refresh().join();
		}
		catch (CompletionException expected)
		{
		}

		verify(notifier, never()).notify(any(Notification.class), anyString());
		assertEquals(BugReportStatus.OPEN, tracker.reports().get(0).getStatus());
	}

	@Test
	public void nothingIsCheckedWithoutReportsOrWithTelemetryOffAtLaunch()
	{
		tracker.refresh().join();
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "stuck");
		BugReportTracker offline = tracker(true);
		offline.refresh().join();
		offline.start();

		verify(api, never()).getBugReportStatuses(any());
		verify(executor, never()).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
	}

	@Test
	public void startSchedulesPeriodicChecksOnce()
	{
		doReturn(mock(ScheduledFuture.class)).when(executor).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());

		tracker.start();
		tracker.start();

		verify(executor).scheduleWithFixedDelay(any(), eq(BugReportTracker.FIRST_REFRESH_SECONDS),
			eq(TimeUnit.HOURS.toSeconds(BugReportTracker.REFRESH_HOURS)), eq(TimeUnit.SECONDS));
	}

	@Test
	public void aHubPluginFixIsComparedWithTheInstalledPluginVersion()
	{
		tracker.track(receipt("MB-AAAAAA"), "BlastFurnacePlugin", "Blast Furnace", "stuck");
		tracker.apply(List.of(pluginFix("MB-AAAAAA", "1.4.3")));
		TrackedBugReport report = tracker.reports().get(0);

		when(plugins.getInstalledPluginVersion("BlastFurnacePlugin")).thenReturn(Optional.of("1.4.2"));
		assertEquals("Fixed in Blast Furnace 1.4.3. Update Blast Furnace in the Microbot Plugin Hub to get the fix", tracker.statusLabel(report));
		assertEquals("The Blast Furnace problem you reported is fixed in version 1.4.3. Update Blast Furnace in the Microbot Plugin Hub to get the fix.",
			BugReportTracker.noticeFor(report, tracker.installedVersion(report)));

		when(plugins.getInstalledPluginVersion("BlastFurnacePlugin")).thenReturn(Optional.of("1.4.3"));
		assertEquals("Fixed in Blast Furnace 1.4.3, which you have", tracker.statusLabel(report));

		when(plugins.getInstalledPluginVersion("BlastFurnacePlugin")).thenReturn(Optional.empty());
		assertEquals("Fixed in Blast Furnace 1.4.3. Update Blast Furnace in the Microbot Plugin Hub to get the fix", tracker.statusLabel(report));
	}

	@Test
	public void reportsWithNewsComeFirst()
	{
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "older");
		tracker.track(receipt("MB-BBBBBB"), null, "Web Walker", "newer");

		tracker.apply(List.of(question("MB-AAAAAA", "Which bank?")));

		assertEquals("MB-AAAAAA", tracker.reports().get(0).getCode());
		assertEquals("MB-BBBBBB", tracker.reports().get(1).getCode());
	}

	@Test
	public void editingOnlyTheNoteUpdatesItQuietly()
	{
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "stuck");
		assertEquals(1, tracker.apply(List.of(status("MB-AAAAAA", BugReportStatus.FIXED, "2.6.43", "Fixd the door."))).size());
		tracker.markSeen();

		List<TrackedBugReport> changed = tracker.apply(List.of(status("MB-AAAAAA", BugReportStatus.FIXED, "2.6.43", "Fixed the door.")));

		assertTrue(changed.isEmpty());
		assertEquals(0, tracker.unseenCount());
		assertEquals("Fixed the door.", tracker.reports().get(0).getNote());
	}

	@Test
	public void aQuestionIsNewsEvenWhenTheStatusStaysTheSame()
	{
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "stuck");
		tracker.apply(List.of(status("MB-AAAAAA", BugReportStatus.INVESTIGATING, null, null)));
		tracker.markSeen();

		assertEquals(1, tracker.apply(List.of(question("MB-AAAAAA", "Which bank?"))).size());
		assertTrue(BugReportTracker.asksQuestion(tracker.reports().get(0)));
	}

	@Test
	public void theTelemetrySettingStopsChecks()
	{
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "stuck");
		System.setProperty("microbot.disableTelemetry", "true");
		try
		{
			tracker.refresh().join();

			assertTrue(tracker.isDisabled());
			assertEquals("No updates: 'Disable telemetry' is turned on in the Microbot settings.", tracker.disabledReason());
			verify(api, never()).getBugReportStatuses(any());
		}
		finally
		{
			System.clearProperty("microbot.disableTelemetry");
		}
		assertEquals("No updates: Microbot was started with the --disable-telemetry option.", tracker(true).disabledReason());
		assertNull(tracker.disabledReason());
	}

	@Test
	public void reportsWithoutAFollowUpTokenAreNotChecked()
	{
		tracker.track(new BugReportReceipt("MB-AAAAAA", null), null, "Web Walker", "stuck");

		tracker.refresh().join();

		verify(api, never()).getBugReportStatuses(any());
	}

	@Test
	public void answeringAQuestionSendsItWithTheTokenAndRemembersIt()
	{
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "stuck");
		tracker.apply(List.of(question("MB-AAAAAA", "Which bank?")));
		when(api.replyToBugReport("MB-AAAAAA", "token-MB-AAAAAA", "The GE bank too.")).thenReturn(CompletableFuture.completedFuture(null));

		tracker.answer("MB-AAAAAA", "  The GE bank too. ").join();

		TrackedBugReport report = tracker.reports().get(0);
		assertEquals("The GE bank too.", report.getAnswer());
		assertFalse(BugReportTracker.asksQuestion(report));
		assertEquals("Being looked at. Thanks for your answer", tracker.statusLabel(report));
	}

	@Test
	public void aFailedAnswerKeepsTheQuestionOpen()
	{
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "stuck");
		tracker.apply(List.of(question("MB-AAAAAA", "Which bank?")));
		CompletableFuture<Void> failed = new CompletableFuture<>();
		failed.completeExceptionally(new IOException("offline"));
		when(api.replyToBugReport(any(), any(), any())).thenReturn(failed);

		assertTrue(tracker.answer("MB-AAAAAA", "GE").isCompletedExceptionally());
		assertTrue(BugReportTracker.asksQuestion(tracker.reports().get(0)));
		assertTrue(tracker.answer("MB-UNKNWN", "GE").isCompletedExceptionally());
	}

	@Test
	public void aClosedReportIsLabelledClosedWithoutANotice()
	{
		TrackedBugReport report = new TrackedBugReport();
		report.setPlugin("Web Walker");
		report.setCode("MB-AAAAAA");
		report.setStatus(BugReportStatus.CLOSED);

		assertEquals("Closed", BugReportTracker.statusLabel(report, "2.6.43"));
		assertNull(BugReportTracker.noticeFor(report, "2.6.43"));
		report.setNote("We didn't hear back. Send a new report if it still happens.");
		assertEquals("Closed", BugReportTracker.statusLabel(report, "2.6.43"));
		assertNull(BugReportTracker.noticeFor(report, "2.6.43"));
	}

	@Test
	public void aClosedQuestionNoLongerAsksForAnAnswer()
	{
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "stuck");
		tracker.apply(List.of(question("MB-AAAAAA", "Which bank?")));
		BugReportStatus closed = status("MB-AAAAAA", BugReportStatus.CLOSED, null, "We didn't hear back. Send a new report if it still happens.");
		closed.setQuestion(true);

		assertEquals(1, tracker.apply(List.of(closed)).size());

		TrackedBugReport report = tracker.reports().get(0);
		assertEquals(BugReportStatus.CLOSED, report.getStatus());
		assertFalse(BugReportTracker.asksQuestion(report));
		assertEquals("Closed", tracker.statusLabel(report));
	}

	@Test
	public void closingAReportShowsUpInMyReportsWithoutAPopup()
	{
		tracker.track(receipt("MB-AAAAAA"), null, "Web Walker", "stuck");
		tracker.markSeen();
		when(api.getBugReportStatuses(Map.of("MB-AAAAAA", "token-MB-AAAAAA"))).thenReturn(CompletableFuture.completedFuture(
			List.of(status("MB-AAAAAA", BugReportStatus.CLOSED, null, "This is a feature request."))));

		assertEquals(1, (int) tracker.refresh().join());

		verify(notifier, never()).notify(any(Notification.class), anyString());
		assertEquals(1, tracker.unseenCount());
		assertEquals("This is a feature request.", tracker.reports().get(0).getNote());
	}

	private static BugReportStatus question(String code, String note)
	{
		BugReportStatus status = status(code, BugReportStatus.INVESTIGATING, null, note);
		status.setQuestion(true);
		return status;
	}

	private static BugReportStatus pluginFix(String code, String version)
	{
		BugReportStatus status = status(code, BugReportStatus.FIXED, version, null);
		status.setFixedInPlugin(true);
		return status;
	}

	@Test
	public void unreadableStoredListStartsEmpty()
	{
		config.put(BugReportTracker.CONFIG_KEY, "{not json");

		assertTrue(tracker.reports().isEmpty());
	}
}
