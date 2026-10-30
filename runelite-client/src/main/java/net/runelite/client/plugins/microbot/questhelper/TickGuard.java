package net.runelite.client.plugins.microbot.questhelper;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Lets one thread at a time into a section. Used for the script loop (a second loop after a plugin restart must never
 * run beside the first) and for the preflight and Grand Exchange buying (one buyer at a time). A thread that is
 * already inside may enter again; any other thread is turned away instead of waiting.
 */
public final class TickGuard
{
	private final ReentrantLock lock = new ReentrantLock();
	private volatile Thread owner;

	/** The runnable runs only when nobody else is inside; otherwise the call does nothing. */
	public Runnable guard(Runnable body)
	{
		return () -> callOrElse(() ->
		{
			body.run();
			return Boolean.TRUE;
		}, Boolean.FALSE);
	}

	/** The result of {@code body}, or {@code busyValue} straight away when another thread is inside. */
	public <T> T callOrElse(Supplier<T> body, T busyValue)
	{
		if (!lock.tryLock())
		{
			return busyValue;
		}
		Thread previous = owner;
		owner = Thread.currentThread();
		try
		{
			return body.get();
		}
		finally
		{
			owner = previous;
			lock.unlock();
		}
	}

	/** True while some thread is inside. */
	public boolean isBusy()
	{
		return lock.isLocked();
	}

	/** The thread inside, or null. */
	public Thread owner()
	{
		return owner;
	}
}
