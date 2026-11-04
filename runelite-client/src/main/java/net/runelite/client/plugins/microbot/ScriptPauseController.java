package net.runelite.client.plugins.microbot;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.microbot.util.input.AwtEmitter;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

/**
 * User-owned pause, independent of break handlers and their temporary pauseAllScripts flag.
 * Keeps scripts and their executors alive; only background workers may wait for resume.
 */
public final class ScriptPauseController
{
	private static final Object LOCK = new Object();
	private static final ExecutorService CLEANUP = Executors.newSingleThreadExecutor(task ->
	{
		Thread thread = new Thread(task, "Microbot-PauseCleanup");
		thread.setDaemon(true);
		return thread;
	});
	private static volatile boolean paused;
	private static volatile long generation;
	private static final Set<String> announcedPauseWaits = ConcurrentHashMap.newKeySet();
	private static long request;
	private static long pausedAt;
	private static long pausedNanos;
	private static CompletableFuture<Void> cleanup = CompletableFuture.completedFuture(null);

	private ScriptPauseController()
	{
	}

	public static boolean isPaused()
	{
		return paused;
	}

	/** Invalidates inputs queued before either edge of a pause. */
	public static long getGeneration()
	{
		return generation;
	}

	/** Returns true the first time this display name announces a wait in the current pause. */
	static boolean markPauseWaitAnnounced(String name)
	{
		return announcedPauseWaits.add(name);
	}

	/** Returns after held input is released (pause), or workers can proceed (resume). */
	public static CompletableFuture<Void> setPaused(boolean value)
	{
		synchronized (LOCK)
		{
			long currentRequest = ++request;
			if (value)
			{
				if (!paused)
				{
					announcedPauseWaits.clear();
					pausedAt = System.nanoTime();
					paused = true;
					generation++;
					cleanup = releaseInput();
				}
				else if (cleanup.isCompletedExceptionally()) cleanup = releaseInput();
				return cleanup;
			}
			// A failed listener must keep pause active, but a later click can retry cleanup.
			if (cleanup.isCompletedExceptionally()) cleanup = releaseInput();
			// Serialize resume behind release: an old release must never hit newly resumed input.
			return cleanup.thenRunAsync(() ->
			{
				synchronized (LOCK)
				{
					if (request == currentRequest && paused)
					{
						pausedNanos += System.nanoTime() - pausedAt;
						generation++;
						paused = false;
						LOCK.notifyAll();
					}
				}
			}, CLEANUP);
		}
	}

	private static CompletableFuture<Void> releaseInput()
	{
		return CompletableFuture.runAsync(() ->
		{
			// Do not take gesture locks or dispatch AWT events on the EDT/client thread.
			Microbot.targetMenu = null;
			try
			{
				Rs2Keyboard.releaseHeldKeys();
			}
			finally
			{
				AwtEmitter.releaseHeldButtons();
			}
		}, CLEANUP);
	}

	/** Monotonic time excluding manual pauses, for cooperative wait deadlines. */
	public static long activeTimeNanos()
	{
		synchronized (LOCK)
		{
			return (paused ? pausedAt : System.nanoTime()) - pausedNanos;
		}
	}

	/**
	 * Preserve the worker's stack and local variables until resume. Cancellation still works.
	 * UI/client callbacks must return instead of waiting, so the game and caches keep updating.
	 */
	public static boolean awaitResume()
	{
		if (Thread.currentThread().isInterrupted())
		{
			return false;
		}
		if (!paused)
		{
			return true;
		}
		if (SwingUtilities.isEventDispatchThread()
			|| (Microbot.getClient() != null && Microbot.getClient().isClientThread()))
		{
			return false;
		}
		synchronized (LOCK)
		{
			while (paused)
			{
				try
				{
					LOCK.wait();
				}
				catch (InterruptedException ex)
				{
					Thread.currentThread().interrupt();
					return false;
				}
			}
		}
		return !Thread.currentThread().isInterrupted();
	}
}
