package net.runelite.client.plugins.microbot.bans;

import java.util.Optional;
import java.util.function.Function;
import net.runelite.client.rs.auth.JagexAuthService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BanAttributionTest
{
	private static final String A = BanLedger.accountKey(1);
	private static final String B = BanLedger.accountKey(2);
	private static final String CHAR_A = key(BanAttribution.identity("111", null));
	private static final String CHAR_B = key(BanAttribution.identity("222", null));
	private static final String LEGACY_ALICE = key(BanAttribution.identity(JagexAuthService.LEGACY_SELECTION, "alice@example.com"));

	private static String key(String identity)
	{
		return BanLedger.identityKey(identity).orElseThrow();
	}

	private static Function<String, IdentityMatch> ledger(String identity, IdentityMatch match)
	{
		return k -> k.equals(identity) ? match : IdentityMatch.UNKNOWN;
	}

	@Test
	public void identityComesFromTheSelectedCharacterOrTheLegacyUsername()
	{
		assertEquals("jagex|111", BanAttribution.identity("111", "stale@example.com"));
		assertEquals("legacy|alice@example.com", BanAttribution.identity(JagexAuthService.LEGACY_SELECTION, "alice@example.com"));
		assertNull(BanAttribution.identity(JagexAuthService.LEGACY_SELECTION, " "));
		assertNull(BanAttribution.identity(null, "alice@example.com"));
	}

	@Test
	public void aRelogOfTheSameCharacterBlamesTheAccountThatWasLoggedIn()
	{
		BanAttribution.Context context = BanAttribution.Context.builder()
			.identityKey(CHAR_A).identityKeyAtLastLogin(CHAR_A).lastLoggedInKey(A).build();

		assertEquals(Optional.of(A), BanAttribution.resolve(context, ledger(CHAR_A, IdentityMatch.of(B))));
	}

	@Test
	public void anotherCharacterUsesTheLedger()
	{
		BanAttribution.Context context = BanAttribution.Context.builder()
			.identityKey(CHAR_B).identityKeyAtLastLogin(CHAR_A).lastLoggedInKey(A).build();

		assertEquals(Optional.of(B), BanAttribution.resolve(context, ledger(CHAR_B, IdentityMatch.of(B))));
		assertEquals(Optional.empty(), BanAttribution.resolve(context, ledger(CHAR_A, IdentityMatch.of(A))));
	}

	@Test
	public void freshStartUsesTheLedger()
	{
		BanAttribution.Context context = BanAttribution.Context.builder().identityKey(LEGACY_ALICE).build();

		assertEquals(Optional.of(A), BanAttribution.resolve(context, ledger(LEGACY_ALICE, IdentityMatch.of(A))));
	}

	@Test
	public void noIdentityOrAnAmbiguousOneBlamesNobody()
	{
		BanAttribution.Context unknown = BanAttribution.Context.builder().lastLoggedInKey(A).build();
		BanAttribution.Context ambiguous = BanAttribution.Context.builder().identityKey(CHAR_A).build();

		assertEquals(Optional.empty(), BanAttribution.resolve(unknown, ledger(CHAR_A, IdentityMatch.of(A))));
		assertEquals(Optional.empty(), BanAttribution.resolve(ambiguous, ledger(CHAR_A, IdentityMatch.AMBIGUOUS)));
	}
}
