package net.runelite.client.plugins.microbot.bans;

import java.util.Optional;
import java.util.function.Function;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BanAttributionTest
{
	private static final String A = BanLedger.accountKey(1);
	private static final String B = BanLedger.accountKey(2);
	private static final String NAME_A = BanLedger.identityKey("alice@example.com").orElseThrow();
	private static final String NAME_B = BanLedger.identityKey("bob@example.com").orElseThrow();

	private static Function<String, IdentityMatch> ledger(String identity, IdentityMatch match)
	{
		return key -> key.equals(identity) ? match : IdentityMatch.UNKNOWN;
	}

	@Test
	public void bannedWhileBottingBlamesTheAccountThatWasLoggedIn()
	{
		BanAttribution.Context context = BanAttribution.Context.builder()
			.identityKey(NAME_A).identityKeyAtLastLogin(NAME_A).lastLoggedInKey(A)
			.accountSwitches(2).accountSwitchesAtLastLogin(2).build();

		assertEquals(Optional.of(A), BanAttribution.resolve(context, ledger(NAME_A, IdentityMatch.of(B))));
	}

	@Test
	public void aJagexCharacterSwitchSinceTheLastLoginBlamesNobody()
	{
		BanAttribution.Context context = BanAttribution.Context.builder()
			.identityKey(NAME_A).identityKeyAtLastLogin(NAME_A).lastLoggedInKey(A)
			.accountSwitches(3).accountSwitchesAtLastLogin(2).build();

		assertEquals(Optional.empty(), BanAttribution.resolve(context, ledger(NAME_A, IdentityMatch.of(A))));
	}

	@Test
	public void aDifferentLoginNameUsesTheLedgerWhenNoSwitchHappenedThisRun()
	{
		BanAttribution.Context context = BanAttribution.Context.builder()
			.identityKey(NAME_B).identityKeyAtLastLogin(NAME_A).lastLoggedInKey(A)
			.accountSwitches(0).accountSwitchesAtLastLogin(0).build();

		assertEquals(Optional.of(B), BanAttribution.resolve(context, ledger(NAME_B, IdentityMatch.of(B))));
	}

	@Test
	public void aLoginNameIsNotTrustedAfterAnySwitch()
	{
		BanAttribution.Context context = BanAttribution.Context.builder()
			.identityKey(NAME_B).identityKeyAtLastLogin(NAME_A).lastLoggedInKey(A)
			.accountSwitches(1).accountSwitchesAtLastLogin(1).build();

		assertEquals(Optional.empty(), BanAttribution.resolve(context, ledger(NAME_B, IdentityMatch.of(B))));
	}

	@Test
	public void freshStartUsesTheLedger()
	{
		BanAttribution.Context context = BanAttribution.Context.builder().identityKey(NAME_A).build();

		assertEquals(Optional.of(A), BanAttribution.resolve(context, ledger(NAME_A, IdentityMatch.of(A))));
	}

	@Test
	public void ambiguousOrUnknownNamesBlameNobody()
	{
		BanAttribution.Context context = BanAttribution.Context.builder().identityKey(NAME_A).build();

		assertEquals(Optional.empty(), BanAttribution.resolve(context, ledger(NAME_A, IdentityMatch.AMBIGUOUS)));
		assertEquals(Optional.empty(), BanAttribution.resolve(context, ledger(NAME_B, IdentityMatch.of(B))));
		assertEquals(Optional.empty(), BanAttribution.resolve(BanAttribution.Context.builder().build(), ledger(NAME_A, IdentityMatch.of(A))));
	}
}
