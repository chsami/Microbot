package net.runelite.client.plugins.microbot.bans;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.Builder;
import lombok.Value;
import net.runelite.client.rs.auth.JagexAuthService;

final class BanAttribution
{
	@Value
	@Builder
	static class Context
	{
		String identityKey;
		String identityKeyAtLastLogin;
		String lastLoggedInKey;
	}

	private BanAttribution()
	{
	}

	static String identity(String selection, String username)
	{
		if (selection == null)
		{
			return null;
		}
		if (JagexAuthService.LEGACY_SELECTION.equals(selection))
		{
			return username == null || username.isBlank() ? null : "legacy|" + username;
		}
		return "jagex|" + selection;
	}

	static Optional<String> resolve(Context context, Function<String, IdentityMatch> lookup)
	{
		if (context.getIdentityKey() == null)
		{
			return Optional.empty();
		}
		if (context.getLastLoggedInKey() != null && Objects.equals(context.getIdentityKey(), context.getIdentityKeyAtLastLogin()))
		{
			return Optional.of(context.getLastLoggedInKey());
		}
		IdentityMatch match = lookup.apply(context.getIdentityKey());
		return match.getState() == IdentityMatch.State.MATCHED ? Optional.of(match.getAccountKey()) : Optional.empty();
	}
}
