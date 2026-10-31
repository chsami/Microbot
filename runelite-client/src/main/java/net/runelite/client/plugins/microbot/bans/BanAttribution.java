package net.runelite.client.plugins.microbot.bans;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.Builder;
import lombok.Value;

final class BanAttribution
{
	@Value
	@Builder
	static class Context
	{
		String identityKey;
		String identityKeyAtLastLogin;
		String lastLoggedInKey;
		int accountSwitches;
		int accountSwitchesAtLastLogin;
	}

	private BanAttribution()
	{
	}

	static boolean identityTrusted(int accountSwitches)
	{
		return accountSwitches == 0;
	}

	static Optional<String> resolve(Context context, Function<String, IdentityMatch> lookup)
	{
		if (context.getLastLoggedInKey() != null
			&& context.getAccountSwitches() == context.getAccountSwitchesAtLastLogin()
			&& Objects.equals(context.getIdentityKey(), context.getIdentityKeyAtLastLogin()))
		{
			return Optional.of(context.getLastLoggedInKey());
		}
		if (context.getIdentityKey() == null || !identityTrusted(context.getAccountSwitches()))
		{
			return Optional.empty();
		}
		IdentityMatch match = lookup.apply(context.getIdentityKey());
		return match.getState() == IdentityMatch.State.MATCHED ? Optional.of(match.getAccountKey()) : Optional.empty();
	}
}
