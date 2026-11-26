package net.runelite.client.plugins.microbot.util.walker.lifecycle;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerLifecycleRuntimeRouteRequestTest
{
	private final CountDownLatch release = new CountDownLatch(1);

	@After
	public void tearDown()
	{
		release.countDown();
	}

	private Future<?> submitBlockingRequest() throws InterruptedException
	{
		CountDownLatch started = new CountDownLatch(1);
		Future<?> running = Rs2WalkerLifecycleRuntime.submitRouteRequest(() ->
		{
			started.countDown();
			return release.await(10, TimeUnit.SECONDS);
		});
		assertTrue(started.await(5, TimeUnit.SECONDS));
		return running;
	}

	@Test
	public void requestSubmittedWhileAnotherRunsIsNotDropped() throws Exception
	{
		Future<?> running = submitBlockingRequest();
		AtomicInteger ran = new AtomicInteger();

		Future<?> next = Rs2WalkerLifecycleRuntime.submitRouteRequest(ran::incrementAndGet);
		release.countDown();

		next.get(5, TimeUnit.SECONDS);
		assertEquals(1, ran.get());
		assertTrue(running.isDone());
	}

	@Test
	public void newerRequestSupersedesOneThatHasNotStarted() throws Exception
	{
		submitBlockingRequest();
		AtomicInteger stale = new AtomicInteger();
		AtomicInteger latest = new AtomicInteger();

		Future<?> superseded = Rs2WalkerLifecycleRuntime.submitRouteRequest(stale::incrementAndGet);
		Future<?> next = Rs2WalkerLifecycleRuntime.submitRouteRequest(latest::incrementAndGet);
		release.countDown();

		next.get(5, TimeUnit.SECONDS);
		assertTrue(superseded.isCancelled());
		assertEquals(0, stale.get());
		assertEquals(1, latest.get());
	}

	@Test
	public void cancelPendingRouteRequestDropsQueuedRequest() throws Exception
	{
		submitBlockingRequest();
		AtomicInteger ran = new AtomicInteger();

		Future<?> queued = Rs2WalkerLifecycleRuntime.submitRouteRequest(ran::incrementAndGet);
		Rs2WalkerLifecycleRuntime.cancelPendingRouteRequest();
		release.countDown();

		Rs2WalkerLifecycleRuntime.submitRouteRequest(() -> null).get(5, TimeUnit.SECONDS);
		assertTrue(queued.isCancelled());
		assertEquals(0, ran.get());
	}
}
