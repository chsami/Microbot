package net.runelite.client.plugins.microbot;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Pause-aware scheduling, including auxiliary tasks that do not call Script.run(). */
final class ScriptScheduledExecutor extends ScheduledThreadPoolExecutor
{
	private final Runnable beforePeriodicSchedule;

	ScriptScheduledExecutor(int threads, ThreadFactory threadFactory)
	{
		this(threads, threadFactory, () -> { });
	}

	ScriptScheduledExecutor(int threads, ThreadFactory threadFactory, Runnable beforePeriodicSchedule)
	{
		super(threads, threadFactory);
		this.beforePeriodicSchedule = beforePeriodicSchedule;
		// Match the upstream executor policy: idle script threads must not stay
		// alive for the lifetime of the client.
		setKeepAliveTime(Script.EXECUTOR_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS);
		allowCoreThreadTimeOut(true);
		setRemoveOnCancelPolicy(true);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit)
	{
		beforePeriodicSchedule.run();
		// Skip paused ticks instead of parking them and building a catch-up backlog.
		return super.scheduleAtFixedRate(new Runnable()
		{
			private long generation = ScriptPauseController.getGeneration();
			private long resumeAfter;
			private boolean recovering;

			@Override
			public void run()
			{
				if (generation != ScriptPauseController.getGeneration())
				{
					generation = ScriptPauseController.getGeneration();
					resumeAfter = System.nanoTime() + unit.toNanos(period);
					recovering = true;
				}
				if (ScriptPauseController.isPaused() || (recovering && System.nanoTime() - resumeAfter < 0)) return;
				recovering = false;
				command.run();
			}
		}, initialDelay, period, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit)
	{
		beforePeriodicSchedule.run();
		return super.scheduleWithFixedDelay(() ->
		{
			if (!ScriptPauseController.isPaused()) command.run();
		}, initialDelay, delay, unit);
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit)
	{
		return super.schedule(() ->
		{
			if (ScriptPauseController.awaitResume()) command.run();
		}, delay, unit);
	}

	@Override
	public <V> ScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit)
	{
		return super.schedule(() ->
		{
			if (!ScriptPauseController.awaitResume()) throw new InterruptedException("Script cancelled while paused");
			return command.call();
		}, delay, unit);
	}
}
