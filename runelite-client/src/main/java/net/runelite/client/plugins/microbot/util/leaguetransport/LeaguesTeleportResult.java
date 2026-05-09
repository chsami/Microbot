package net.runelite.client.plugins.microbot.util.leaguetransport;

import java.util.EnumSet;
import java.util.Objects;

import javax.annotation.Nullable;

public final class LeaguesTeleportResult
{
	private final boolean success;
	private final @Nullable LeaguesTeleportFailureReason failureReason;
	private final String message;
	private final LeaguesRegion target;
	private final EnumSet<LeaguesRegion> unlockedRegionsSnapshot;

	private LeaguesTeleportResult(
			boolean success,
			@Nullable LeaguesTeleportFailureReason failureReason,
			String message,
			LeaguesRegion target,
			EnumSet<LeaguesRegion> unlockedRegionsSnapshot)
	{
		this.success = success;
		this.failureReason = success
				? null
				: (failureReason != null ? failureReason : LeaguesTeleportFailureReason.UNKNOWN);
		this.message = message != null ? message : "";
		this.target = target;
		this.unlockedRegionsSnapshot = unlockedRegionsSnapshot != null ? EnumSet.copyOf(unlockedRegionsSnapshot) : null;
	}

	public static LeaguesTeleportResult ok(LeaguesRegion target, EnumSet<LeaguesRegion> unlockedRegionsSnapshot)
	{
		return new LeaguesTeleportResult(true, null, "", target, unlockedRegionsSnapshot);
	}

	public static LeaguesTeleportResult failure(
			LeaguesTeleportFailureReason reason,
			String message,
			LeaguesRegion target,
			EnumSet<LeaguesRegion> unlockedRegionsSnapshot)
	{
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(message, "message");
		if (message.isEmpty())
		{
			throw new IllegalArgumentException("failure message must not be empty");
		}
		return new LeaguesTeleportResult(false, reason, message, target, unlockedRegionsSnapshot);
	}

	public boolean isSuccess()
	{
		return success;
	}

	/**
	 * @return failure reason when {@link #isSuccess()} is {@code false}; {@code null} on success (not {@link LeaguesTeleportFailureReason#UNKNOWN}).
	 */
	public @Nullable LeaguesTeleportFailureReason getFailureReason()
	{
		return failureReason;
	}

	public String getMessage()
	{
		return message;
	}

	public LeaguesRegion getTarget()
	{
		return target;
	}

	public EnumSet<LeaguesRegion> getUnlockedRegionsSnapshot()
	{
		return unlockedRegionsSnapshot != null ? EnumSet.copyOf(unlockedRegionsSnapshot) : null;
	}
}

