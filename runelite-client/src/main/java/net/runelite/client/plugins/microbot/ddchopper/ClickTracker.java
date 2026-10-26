package net.runelite.client.plugins.microbot.ddchopper;

import java.util.Objects;

/**
 * Counts clicks on a tree that did not start chopping. A click that starts chopping or walking resets the count, so a
 * long-lived tree that is clicked again and again is never blacklisted; only {@link #LIMIT} non-starting clicks in a
 * row on the same tree are. Pure.
 */
public final class ClickTracker<T>
{
	public static final int LIMIT = 4;

	private T last;
	private int failed;

	/** Records a click on {@code target}; returns true when it should be blacklisted now. */
	public boolean record(T target, boolean started)
	{
		if (!Objects.equals(target, last))
		{
			last = target;
			failed = 0;
		}
		if (started)
		{
			failed = 0;
			return false;
		}
		failed++;
		if (failed >= LIMIT)
		{
			failed = 0;
			return true;
		}
		return false;
	}

	public void reset()
	{
		last = null;
		failed = 0;
	}
}
