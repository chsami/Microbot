package net.runelite.client.plugins.microbot.bans;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BanLedgerTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private MutableClock clock;
	private BanLedger ledger;
	private final String account = BanLedger.accountKey(123456789L);
	private final String identity = BanLedger.identityKey("Zezima").orElseThrow();

	@Before
	public void setUp()
	{
		clock = new MutableClock(Instant.parse("2026-10-31T12:00:00Z"));
		ledger = new BanLedger(folder.getRoot().toPath(), new Gson(), clock);
	}

	@Test
	public void keysAreHexHashesThatDoNotContainTheIdentity()
	{
		assertTrue(account.matches("[0-9a-f]{64}"));
		assertTrue(identity.matches("[0-9a-f]{64}"));
		assertEquals(identity, BanLedger.identityKey("  zezima ").orElseThrow());
		assertFalse(BanLedger.identityKey(" ").isPresent());
		assertFalse(BanLedger.identityKey(null).isPresent());
	}

	@Test
	public void nothingIsWrittenWhileNoScriptRuns() throws Exception
	{
		ledger.record(account, identity, 60, List.of(), true, 1200, false);

		try (Stream<Path> files = Files.walk(folder.getRoot().toPath()))
		{
			assertEquals(1, files.count());
		}
		assertFalse(ledger.report(account, "unknown", "2.6.48").isPresent());
	}

	@Test
	public void ledgerFilesNeverContainTheAccountName() throws Exception
	{
		ledger.record(account, identity, 60, List.of("AIOFighterPlugin"), true, 1200, false);

		try (Stream<Path> files = Files.walk(folder.getRoot().toPath()))
		{
			for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator)
			{
				assertFalse(file.toString().toLowerCase().contains("zezima"));
				assertFalse(Files.readString(file, StandardCharsets.UTF_8).toLowerCase().contains("zezima"));
			}
		}
	}

	@Test
	public void identityLeadsBackToTheAccount()
	{
		ledger.record(account, identity, 60, List.of("AIOFighterPlugin"), true, 1200, false);

		assertEquals(Optional.of(account), ledger.accountFor(identity));
		assertFalse(ledger.accountFor(BanLedger.identityKey("someone else").orElseThrow()).isPresent());
	}

	@Test
	public void reportSplitsTheLastWeekFromTheLastMonth()
	{
		ledger.record(account, identity, 3600, List.of("OldPlugin"), false, 900, false);
		clock.advance(Duration.ofDays(10));
		ledger.record(account, identity, 60, List.of("AIOFighterPlugin", "PluginX"), true, 1200, true);
		clock.advance(Duration.ofMinutes(1));
		ledger.record(account, identity, 60, List.of("AIOFighterPlugin"), true, 1201, true);
		clock.advance(Duration.ofHours(2));

		JsonObject report = ledger.report(account, "permanent", "2.6.48").orElseThrow();

		assertEquals(account, report.get("accountKey").getAsString());
		assertEquals("permanent", report.get("banKind").getAsString());
		assertTrue(report.get("member").getAsBoolean());
		assertEquals(1201, report.get("totalLevel").getAsInt());
		assertEquals(10, report.get("trackedDays").getAsInt());
		assertEquals(2, report.get("activeDays30").getAsInt());
		assertEquals(120, report.get("botSeconds7d").getAsLong());
		assertEquals(3720, report.get("botSeconds30d").getAsLong());
		assertEquals(3600, report.get("longestSessionSeconds").getAsLong());
		assertTrue(report.get("breakHandlerUsed").getAsBoolean());
		assertEquals(7200, report.get("secondsSinceLastBotting").getAsLong());
		assertEquals(3, report.getAsJsonArray("plugins").size());
		JsonObject top = report.getAsJsonArray("plugins").get(0).getAsJsonObject();
		assertEquals("OldPlugin", top.get("plugin").getAsString());
		assertEquals(0, top.get("seconds7d").getAsLong());
		assertEquals(3600, top.get("seconds30d").getAsLong());
	}

	@Test
	public void sessionLengthGrowsAcrossSamplesAndResetsAfterAGap()
	{
		for (int i = 0; i < 30; i++)
		{
			ledger.record(account, identity, 60, List.of("PluginX"), true, 1200, false);
			clock.advance(Duration.ofMinutes(1));
		}
		clock.advance(Duration.ofMinutes(30));
		ledger.record(account, identity, 60, List.of("PluginX"), true, 1200, false);

		JsonObject report = ledger.report(account, "unknown", "2.6.48").orElseThrow();
		assertEquals(30 * 60, report.get("longestSessionSeconds").getAsLong());
	}

	@Test
	public void daysOlderThanTheWindowAreDropped()
	{
		ledger.record(account, identity, 600, List.of("OldPlugin"), true, 1200, false);
		clock.advance(Duration.ofDays(BanLedger.WINDOW_DAYS + 1));
		ledger.record(account, identity, 60, List.of("PluginX"), true, 1200, false);

		JsonObject report = ledger.report(account, "unknown", "2.6.48").orElseThrow();
		assertEquals(60, report.get("botSeconds30d").getAsLong());
		assertEquals(1, report.getAsJsonArray("plugins").size());
	}

	@Test
	public void aReportedAccountIsQuietForTheCooldown()
	{
		ledger.record(account, identity, 60, List.of("PluginX"), true, 1200, false);
		ledger.markReported(account);

		assertTrue(ledger.recentlyReported(account));
		clock.advance(Duration.ofMillis(BanLedger.REPORT_COOLDOWN_MILLIS + 1));
		assertFalse(ledger.recentlyReported(account));
	}

	@Test
	public void aCorruptFileIsIgnored() throws Exception
	{
		ledger.record(account, identity, 60, List.of("PluginX"), true, 1200, false);
		Files.writeString(folder.getRoot().toPath().resolve("accounts").resolve(account + ".json"), "{not json", StandardCharsets.UTF_8);

		assertFalse(ledger.report(account, "unknown", "2.6.48").isPresent());
		ledger.record(account, identity, 60, List.of("PluginX"), true, 1200, false);
		assertEquals(60, ledger.report(account, "unknown", "2.6.48").orElseThrow().get("botSeconds30d").getAsLong());
	}

	@Test
	public void keysThatAreNotHashesAreRefused()
	{
		ledger.record("../../evil", identity, 60, List.of("PluginX"), true, 1200, false);

		assertFalse(ledger.report("../../evil", "unknown", "2.6.48").isPresent());
		assertFalse(ledger.accountFor(identity).isPresent());
	}

	private static final class MutableClock extends Clock
	{
		private Instant now;

		MutableClock(Instant now)
		{
			this.now = now;
		}

		void advance(Duration by)
		{
			now = now.plus(by);
		}

		@Override
		public ZoneId getZone()
		{
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone)
		{
			return this;
		}

		@Override
		public Instant instant()
		{
			return now;
		}
	}
}
