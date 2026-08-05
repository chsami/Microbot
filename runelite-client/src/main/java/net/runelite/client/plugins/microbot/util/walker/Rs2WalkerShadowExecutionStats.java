package net.runelite.client.plugins.microbot.util.walker;

/** Coordinate-free terminal outcomes for blocking walks observed while shadow mode was enabled. */
public final class Rs2WalkerShadowExecutionStats
{
	private final long arrived;
	private final long unreachable;
	private final long exited;
	private final long recoveryArrived;
	private final long recoveryUnreachable;
	private final long recoveryExited;

	Rs2WalkerShadowExecutionStats(
		long arrived,
		long unreachable,
		long exited,
		long recoveryArrived,
		long recoveryUnreachable,
		long recoveryExited)
	{
		this.arrived = requireNonNegative(arrived, "arrived");
		this.unreachable = requireNonNegative(unreachable, "unreachable");
		this.exited = requireNonNegative(exited, "exited");
		this.recoveryArrived = requireNonNegative(recoveryArrived, "recoveryArrived");
		this.recoveryUnreachable = requireNonNegative(
			recoveryUnreachable, "recoveryUnreachable");
		this.recoveryExited = requireNonNegative(recoveryExited, "recoveryExited");
	}

	private static long requireNonNegative(long value, String name)
	{
		if (value < 0)
		{
			throw new IllegalArgumentException(name + " must be non-negative");
		}
		return value;
	}

	public long getArrived() { return arrived; }
	public long getUnreachable() { return unreachable; }
	public long getExited() { return exited; }
	public long getRecoveryArrived() { return recoveryArrived; }
	public long getRecoveryUnreachable() { return recoveryUnreachable; }
	public long getRecoveryExited() { return recoveryExited; }
	public long getTerminal() { return arrived + unreachable + exited; }
	public long getRecoveryTerminal()
	{
		return recoveryArrived + recoveryUnreachable + recoveryExited;
	}
}
