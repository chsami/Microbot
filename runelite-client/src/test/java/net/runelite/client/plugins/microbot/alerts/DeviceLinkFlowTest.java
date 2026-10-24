package net.runelite.client.plugins.microbot.alerts;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Value;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeviceLinkFlowTest
{
	private static final String DEVICE_CODE = "mbd_" + "d".repeat(43);
	private static final String USER_CODE = "TEST-CODE";
	private static final String URL = "https://dashboard.example.test/link?code=" + USER_CODE;
	private static final String TOKEN = ClientLinkTokenTest.CONFIG_TOKEN;

	@Value
	private static class Scheduled
	{
		Runnable task;
		long delaySeconds;
		ScheduledFuture<?> future;
	}

	private final Deque<Scheduled> scheduled = new ArrayDeque<>();
	private final AtomicLong now = new AtomicLong(1_000_000);
	private MicrobotApi api;
	private ClientLinkToken token;
	private ConfigManager configManager;
	private DeviceLinkDialog dialog;
	private DeviceLinkFlow flow;

	@Before
	public void setUp() throws IOException
	{
		api = mock(MicrobotApi.class);
		when(api.startDeviceLink(anyString())).thenReturn(new DeviceLinkStart(DEVICE_CODE, USER_CODE, URL, 600, 5));
		when(api.cancelDeviceLink(anyString())).thenReturn(CompletableFuture.completedFuture(null));
		token = mock(ClientLinkToken.class);
		when(token.get()).thenReturn(Optional.empty());
		configManager = mock(ConfigManager.class);
		dialog = mock(DeviceLinkDialog.class);
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(executor).execute(any());
		when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.SECONDS))).thenAnswer(invocation ->
		{
			ScheduledFuture<?> future = mock(ScheduledFuture.class);
			scheduled.addLast(new Scheduled(invocation.getArgument(0), invocation.getArgument(1), future));
			return future;
		});
		flow = new DeviceLinkFlow(api, token, configManager, dialog, executor, now::get);
	}

	private void polls(DeviceLinkPoll.Status... statuses) throws IOException
	{
		DeviceLinkPoll first = DeviceLinkPoll.of(statuses[0]);
		DeviceLinkPoll[] rest = new DeviceLinkPoll[statuses.length - 1];
		for (int i = 1; i < statuses.length; i++)
		{
			rest[i - 1] = DeviceLinkPoll.of(statuses[i]);
		}
		when(api.pollDeviceLink(DEVICE_CODE)).thenReturn(first, rest);
	}

	private void runNext(long expectedDelaySeconds)
	{
		Scheduled next = scheduled.removeFirst();
		assertEquals(expectedDelaySeconds, next.getDelaySeconds());
		now.addAndGet(TimeUnit.SECONDS.toMillis(next.getDelaySeconds()));
		next.getTask().run();
	}

	@Test
	public void startOpensTheBrowserShowsTheCodeAndPollsAtTheInterval() throws IOException
	{
		polls(DeviceLinkPoll.Status.PENDING);
		flow.start();

		verify(api).startDeviceLink(DeviceLinkFlow.DEFAULT_LABEL);
		verify(dialog).browse(URL);
		verify(dialog).showWaiting(contains("Code: " + USER_CODE), eq(URL), any());
		assertTrue(flow.isLinking());

		runNext(5);
		runNext(5);
		runNext(5);
		verify(api, times(3)).pollDeviceLink(DEVICE_CODE);
		assertEquals(1, scheduled.size());
	}

	@Test
	public void slowDownBacksOffByFiveSecondsEachTime() throws IOException
	{
		polls(DeviceLinkPoll.Status.SLOW_DOWN, DeviceLinkPoll.Status.SLOW_DOWN, DeviceLinkPoll.Status.PENDING);
		flow.start();

		runNext(5);
		runNext(10);
		runNext(15);
		runNext(15);
	}

	@Test
	public void approvalStoresTheTokenAndShowsTheLabel() throws IOException
	{
		when(api.pollDeviceLink(DEVICE_CODE)).thenReturn(DeviceLinkPoll.of(DeviceLinkPoll.Status.PENDING),
			new DeviceLinkPoll(DeviceLinkPoll.Status.LINKED, TOKEN, "Desk PC"));
		flow.start();

		runNext(5);
		verify(configManager, never()).setConfiguration(anyString(), anyString(), anyString());
		runNext(5);

		verify(configManager).setConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyAlertLinkToken, TOKEN);
		verify(dialog).showMessage(contains("Linked as Desk PC"));
		assertTrue(scheduled.isEmpty());
		assertFalse(flow.isLinking());
	}

	@Test
	public void declinedStopsPollingAndCanBeRetried() throws IOException
	{
		polls(DeviceLinkPoll.Status.DENIED);
		flow.start();
		runNext(5);

		verify(dialog).showMessage(DeviceLinkFlow.DENIED);
		assertTrue(scheduled.isEmpty());
		assertFalse(flow.isLinking());

		flow.start();
		verify(api, times(2)).startDeviceLink(anyString());
		assertEquals(1, scheduled.size());
	}

	@Test
	public void expiredCodeFromTheServerStopsPolling() throws IOException
	{
		polls(DeviceLinkPoll.Status.PENDING, DeviceLinkPoll.Status.EXPIRED);
		flow.start();
		runNext(5);
		runNext(5);

		verify(dialog).showMessage(DeviceLinkFlow.EXPIRED);
		assertTrue(scheduled.isEmpty());
		assertFalse(flow.isLinking());
	}

	@Test
	public void pollingGivesUpAfterTenMinutesEvenIfTheServerAllowsLonger() throws IOException
	{
		when(api.startDeviceLink(anyString())).thenReturn(new DeviceLinkStart(DEVICE_CODE, USER_CODE, URL, 3600, 5));
		polls(DeviceLinkPoll.Status.PENDING);
		flow.start();

		int polled = 0;
		while (!scheduled.isEmpty())
		{
			runNext(5);
			polled++;
		}

		assertEquals(DeviceLinkFlow.MAX_WAIT_SECONDS / 5, polled);
		verify(dialog).showMessage(DeviceLinkFlow.EXPIRED);
		assertFalse(flow.isLinking());
	}

	@Test
	public void networkErrorsWhilePollingKeepWaiting() throws IOException
	{
		when(api.pollDeviceLink(DEVICE_CODE)).thenThrow(new IOException("offline"))
			.thenReturn(new DeviceLinkPoll(DeviceLinkPoll.Status.LINKED, TOKEN, "Desk PC"));
		flow.start();
		runNext(5);
		runNext(5);

		verify(configManager).setConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyAlertLinkToken, TOKEN);
	}

	@Test
	public void fullAccountKeepsPollingAndSaysSoOnce() throws IOException
	{
		polls(DeviceLinkPoll.Status.TOO_MANY_LINKS, DeviceLinkPoll.Status.TOO_MANY_LINKS, DeviceLinkPoll.Status.PENDING);
		flow.start();
		runNext(5);
		runNext(5);
		runNext(5);

		verify(dialog, times(1)).showWaiting(contains(DeviceLinkFlow.TOO_MANY_LINKS), anyString(), any());
		assertEquals(1, scheduled.size());
	}

	@Test
	public void clientShutdownCancelsPolling() throws IOException
	{
		polls(DeviceLinkPoll.Status.PENDING);
		flow.start();
		Scheduled pending = scheduled.peekFirst();
		CompletableFuture<Void> cancelled = new CompletableFuture<>();
		when(api.cancelDeviceLink(DEVICE_CODE)).thenReturn(cancelled);
		ClientShutdown event = new ClientShutdown();

		flow.onClientShutdown(event);

		verify(pending.getFuture()).cancel(false);
		verify(api, times(1)).cancelDeviceLink(DEVICE_CODE);
		assertTrue(event.getTasks().contains(cancelled));
		runNext(5);
		verify(api, never()).pollDeviceLink(anyString());
		assertTrue(scheduled.isEmpty());
		assertFalse(flow.isLinking());
	}

	@Test
	public void cancellingFromTheDialogStopsPolling() throws IOException
	{
		polls(DeviceLinkPoll.Status.PENDING);
		flow.start();
		ArgumentCaptor<Runnable> onCancel = ArgumentCaptor.forClass(Runnable.class);
		verify(dialog).showWaiting(anyString(), anyString(), onCancel.capture());

		onCancel.getValue().run();
		onCancel.getValue().run();
		flow.stop();
		runNext(5);

		verify(api, never()).pollDeviceLink(anyString());
		verify(api, times(1)).cancelDeviceLink(DEVICE_CODE);
		assertFalse(flow.isLinking());
	}

	@Test
	public void cancelFailuresAreSwallowed() throws IOException
	{
		polls(DeviceLinkPoll.Status.PENDING);
		when(api.cancelDeviceLink(anyString())).thenThrow(new IllegalStateException("offline"));
		flow.start();
		ArgumentCaptor<Runnable> onCancel = ArgumentCaptor.forClass(Runnable.class);
		verify(dialog).showWaiting(anyString(), anyString(), onCancel.capture());

		onCancel.getValue().run();
		assertFalse(flow.isLinking());

		flow.start();
		ClientShutdown event = new ClientShutdown();
		flow.onClientShutdown(event);

		verify(api, times(2)).cancelDeviceLink(DEVICE_CODE);
		assertTrue(event.getTasks().isEmpty());
		assertFalse(flow.isLinking());
	}

	@Test
	public void nothingIsCancelledOnceTheLinkIsSettled() throws IOException
	{
		when(api.pollDeviceLink(DEVICE_CODE)).thenReturn(new DeviceLinkPoll(DeviceLinkPoll.Status.LINKED, TOKEN, "Desk PC"),
			DeviceLinkPoll.of(DeviceLinkPoll.Status.DENIED), DeviceLinkPoll.of(DeviceLinkPoll.Status.EXPIRED));
		ArgumentCaptor<Runnable> onCancel = ArgumentCaptor.forClass(Runnable.class);

		for (int i = 0; i < 3; i++)
		{
			flow.relink();
			runNext(5);
			assertFalse(flow.isLinking());
			flow.stop();
			flow.onClientShutdown(new ClientShutdown());
		}
		verify(dialog, times(3)).showWaiting(anyString(), anyString(), onCancel.capture());
		onCancel.getAllValues().forEach(Runnable::run);

		verify(api, never()).cancelDeviceLink(anyString());
	}

	@Test
	public void nothingIsCancelledWhenTheCodeRunsOut() throws IOException
	{
		when(api.startDeviceLink(anyString())).thenReturn(new DeviceLinkStart(DEVICE_CODE, USER_CODE, URL, 10, 5));
		polls(DeviceLinkPoll.Status.PENDING);
		flow.start();
		runNext(5);
		runNext(5);
		assertFalse(flow.isLinking());

		flow.onClientShutdown(new ClientShutdown());

		verify(dialog).showMessage(DeviceLinkFlow.EXPIRED);
		verify(api, never()).cancelDeviceLink(anyString());
	}

	@Test
	public void aCodeIssuedAfterStoppingIsCancelled() throws IOException
	{
		when(api.startDeviceLink(anyString())).thenAnswer(invocation ->
		{
			flow.stop();
			return new DeviceLinkStart(DEVICE_CODE, USER_CODE, URL, 600, 5);
		});

		flow.start();

		verify(api, times(1)).cancelDeviceLink(DEVICE_CODE);
		verify(dialog, never()).browse(anyString());
		assertTrue(scheduled.isEmpty());
		assertFalse(flow.isLinking());
	}

	@Test
	public void stoppingWithNothingPendingSendsNoCancel()
	{
		flow.onClientShutdown(new ClientShutdown());
		flow.stop();

		verify(api, never()).cancelDeviceLink(anyString());
	}

	@Test
	public void approvalArrivingAfterCancelIsIgnored() throws IOException
	{
		flow.start();
		ArgumentCaptor<Runnable> onCancel = ArgumentCaptor.forClass(Runnable.class);
		verify(dialog).showWaiting(anyString(), anyString(), onCancel.capture());
		when(api.pollDeviceLink(DEVICE_CODE)).thenAnswer(invocation ->
		{
			onCancel.getValue().run();
			return new DeviceLinkPoll(DeviceLinkPoll.Status.LINKED, TOKEN, "Desk PC");
		});

		runNext(5);

		verify(configManager, never()).setConfiguration(anyString(), anyString(), anyString());
		assertTrue(scheduled.isEmpty());
	}

	@Test
	public void onlyOneFlowRunsAtATime() throws IOException
	{
		polls(DeviceLinkPoll.Status.PENDING);
		flow.start();
		flow.start();

		verify(api, times(1)).startDeviceLink(anyString());
		verify(dialog, times(2)).showWaiting(contains(USER_CODE), anyString(), any());
		assertEquals(1, scheduled.size());
	}

	@Test
	public void turningOnTelemetryClearsTheSettingWithoutLinking() throws IOException
	{
		when(token.isTelemetryDisabled()).thenReturn(true, false);
		flow.start();

		verify(configManager).setConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyDisableTelemetry, false);
		verify(dialog, never()).showMessage(anyString());
		verify(api, never()).startDeviceLink(anyString());
		assertFalse(flow.isLinking());
	}

	@Test
	public void telemetryForcedOffByLaunchOptionSaysHowToTurnItOn() throws IOException
	{
		when(token.isTelemetryDisabled()).thenReturn(true);
		flow.start();

		verify(dialog).showMessage(DeviceLinkFlow.TELEMETRY_OFF);
		verify(api, never()).startDeviceLink(anyString());
		assertFalse(flow.isLinking());
	}

	@Test
	public void alreadyLinkedClientsOnlyRelinkWhenAsked() throws IOException
	{
		when(token.get()).thenReturn(Optional.of(TOKEN));
		flow.start();

		ArgumentCaptor<Runnable> onRelink = ArgumentCaptor.forClass(Runnable.class);
		verify(dialog).showLinked(eq(DeviceLinkFlow.ALREADY_LINKED), eq(DeviceLinkFlow.ALERTS_PAGE), onRelink.capture());
		verify(api, never()).startDeviceLink(anyString());
		assertFalse(flow.isLinking());

		polls(DeviceLinkPoll.Status.PENDING);
		onRelink.getValue().run();
		onRelink.getValue().run();

		verify(api, times(1)).startDeviceLink(DeviceLinkFlow.DEFAULT_LABEL);
		verify(dialog).showWaiting(contains("Code: " + USER_CODE), eq(URL), any());
		assertTrue(flow.isLinking());
	}

	@Test
	public void relinkingReplacesTheStoredToken() throws IOException
	{
		when(token.get()).thenReturn(Optional.of(TOKEN));
		String replacement = "mbc_" + "r".repeat(43);
		when(api.pollDeviceLink(DEVICE_CODE)).thenReturn(new DeviceLinkPoll(DeviceLinkPoll.Status.LINKED, replacement, "Desk PC"));

		flow.relink();
		runNext(5);

		verify(configManager).setConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyAlertLinkToken, replacement);
		assertFalse(flow.isLinking());
	}

	@Test
	public void launcherLinkedClientsCannotRelinkFromTheClient() throws IOException
	{
		when(token.get()).thenReturn(Optional.of(TOKEN));
		when(token.isFromEnvironment()).thenReturn(true);
		flow.start();
		flow.relink();

		verify(dialog).showLinked(DeviceLinkFlow.LINKED_BY_LAUNCHER, DeviceLinkFlow.ALERTS_PAGE, null);
		verify(api, never()).startDeviceLink(anyString());
	}

	@Test
	public void aRevokedLauncherTokenIsNotShadowedByANewLink() throws IOException
	{
		when(token.isFromEnvironment()).thenReturn(true);
		flow.start();

		verify(dialog).showMessage(DeviceLinkFlow.LAUNCHER_TOKEN);
		verify(api, never()).startDeviceLink(anyString());
		assertFalse(flow.isLinking());
	}

	@Test
	public void aBrowserThatCannotOpenStillLeavesTheFlowPolling() throws IOException
	{
		polls(DeviceLinkPoll.Status.PENDING);
		doThrow(new IllegalArgumentException("bad url")).when(dialog).browse(anyString());

		try
		{
			flow.start();
		}
		catch (IllegalArgumentException expected)
		{
		}

		assertTrue(flow.isLinking());
		runNext(5);
		verify(api).pollDeviceLink(DEVICE_CODE);
		assertEquals(1, scheduled.size());
	}

	@Test
	public void startFailuresExplainWhatHappened() throws IOException
	{
		when(api.startDeviceLink(anyString())).thenThrow(new IOException("offline"));
		flow.start();
		verify(dialog).showMessage(DeviceLinkFlow.UNREACHABLE);
		assertFalse(flow.isLinking());

		doThrow(new DeviceLinkStart.ThrottledException()).when(api).startDeviceLink(anyString());
		flow.start();
		verify(dialog).showMessage(DeviceLinkFlow.TOO_MANY_PENDING);
		verify(dialog, never()).browse(anyString());
		assertTrue(scheduled.isEmpty());
	}

	@Test
	public void statusFollowsTheLinkState() throws IOException
	{
		assertEquals(BotAlertStatus.NOT_LINKED, flow.status());

		polls(DeviceLinkPoll.Status.PENDING);
		flow.start();
		assertEquals(BotAlertStatus.LINKING, flow.status());
		flow.stop();

		when(token.configured()).thenReturn(Optional.of(TOKEN));
		assertEquals(BotAlertStatus.REVOKED, flow.status());

		when(token.get()).thenReturn(Optional.of(TOKEN));
		assertEquals(BotAlertStatus.LINKED, flow.status());

		when(token.isFromEnvironment()).thenReturn(true);
		assertEquals(BotAlertStatus.LINKED_BY_LAUNCHER, flow.status());

		when(token.isTelemetryDisabled()).thenReturn(true);
		assertEquals(BotAlertStatus.TELEMETRY_OFF, flow.status());
	}

	@Test
	public void labelsAreShownAsPlainSingleLineText()
	{
		assertEquals("Desk PC", DeviceLinkFlow.displayLabel(" Desk PC "));
		assertEquals("a <html>b",DeviceLinkFlow.displayLabel("a\n<html>b"));
		assertEquals(DeviceLinkFlow.DEFAULT_LABEL, DeviceLinkFlow.displayLabel(null));
		assertEquals(DeviceLinkFlow.MAX_LABEL_LENGTH, DeviceLinkFlow.displayLabel("x".repeat(200)).length());
	}
}
