package net.runelite.client.plugins.microbot.bans;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class BanLedger
{
	static final int WINDOW_DAYS = 30;
	static final int RECENT_DAYS = 7;
	static final long SESSION_GAP_MILLIS = TimeUnit.MINUTES.toMillis(5);
	static final long REPORT_COOLDOWN_MILLIS = TimeUnit.DAYS.toMillis(14);
	static final int LEVEL_STEP = 50;
	static final long IDLE_STEP_SECONDS = 15 * 60;
	static final String AMBIGUOUS = "ambiguous";
	private static final Pattern KEY = Pattern.compile("[0-9a-f]{64}");

	private final Path accountsDir;
	private final Path identitiesDir;
	private final Gson gson;
	private final Clock clock;
	private final Map<String, String> writtenIdentities = new HashMap<>();

	BanLedger(Path dir, Gson gson, Clock clock)
	{
		this.accountsDir = dir.resolve("accounts");
		this.identitiesDir = dir.resolve("identities");
		this.gson = gson;
		this.clock = clock;
	}

	static String accountKey(long accountHash)
	{
		return sha256("microbot-ban-report|" + accountHash);
	}

	static Optional<String> identityKey(String identity)
	{
		if (identity == null || identity.isBlank())
		{
			return Optional.empty();
		}
		return Optional.of(sha256("microbot-ban-identity|" + identity.trim().toLowerCase(Locale.ROOT)));
	}

	synchronized void record(String accountKey, String identityKey, long elapsedSeconds, Collection<String> plugins,
		Boolean member, Integer totalLevel, boolean breaks)
	{
		if (plugins.isEmpty() || elapsedSeconds <= 0 || !KEY.matcher(accountKey).matches())
		{
			return;
		}
		long now = clock.millis();
		AccountActivity activity = load(accountKey).orElseGet(AccountActivity::new);
		if (activity.firstSeen == 0)
		{
			activity.firstSeen = now;
		}
		if (activity.lastBot == 0 || now - activity.lastBot > SESSION_GAP_MILLIS + TimeUnit.SECONDS.toMillis(elapsedSeconds))
		{
			activity.sessionStart = now - TimeUnit.SECONDS.toMillis(elapsedSeconds);
		}
		activity.lastBot = now;
		if (member != null)
		{
			activity.member = member;
		}
		if (totalLevel != null)
		{
			activity.totalLevel = totalLevel;
		}

		AccountActivity.Day day = activity.days.computeIfAbsent(today().toString(), d -> new AccountActivity.Day());
		day.botSeconds += elapsedSeconds;
		day.longestSession = Math.max(day.longestSession, (now - activity.sessionStart) / 1000);
		day.breaks |= breaks;
		split(plugins, elapsedSeconds).forEach((plugin, seconds) -> day.plugins.merge(plugin, seconds, Long::sum));
		prune(activity);
		save(accountsDir.resolve(accountKey + ".json"), gson.toJson(activity));
		rememberIdentity(identityKey, accountKey);
	}

	static Map<String, Long> split(Collection<String> plugins, long elapsedSeconds)
	{
		Map<String, Long> shares = new LinkedHashMap<>();
		List<String> distinct = plugins.stream().distinct().collect(Collectors.toList());
		if (distinct.isEmpty() || elapsedSeconds <= 0)
		{
			return shares;
		}
		long share = elapsedSeconds / distinct.size();
		long remainder = elapsedSeconds % distinct.size();
		for (int i = 0; i < distinct.size(); i++)
		{
			long seconds = share + (i < remainder ? 1 : 0);
			if (seconds > 0)
			{
				shares.put(distinct.get(i), seconds);
			}
		}
		return shares;
	}

	private void rememberIdentity(String identityKey, String accountKey)
	{
		if (identityKey == null || !KEY.matcher(identityKey).matches())
		{
			return;
		}
		String known = writtenIdentities.computeIfAbsent(identityKey, k -> readIdentity(k).orElse(""));
		if (known.equals(accountKey) || known.equals(AMBIGUOUS))
		{
			return;
		}
		String value = known.isEmpty() ? accountKey : AMBIGUOUS;
		save(identitiesDir.resolve(identityKey), value);
		writtenIdentities.put(identityKey, value);
	}

	synchronized IdentityMatch accountFor(String identityKey)
	{
		Optional<String> value = KEY.matcher(identityKey).matches() ? readIdentity(identityKey) : Optional.empty();
		if (value.isEmpty())
		{
			return IdentityMatch.UNKNOWN;
		}
		if (value.get().equals(AMBIGUOUS))
		{
			return IdentityMatch.AMBIGUOUS;
		}
		return KEY.matcher(value.get()).matches() ? IdentityMatch.of(value.get()) : IdentityMatch.UNKNOWN;
	}

	private Optional<String> readIdentity(String identityKey)
	{
		try
		{
			Path file = identitiesDir.resolve(identityKey);
			if (!Files.isRegularFile(file))
			{
				return Optional.empty();
			}
			return Optional.of(Files.readString(file, StandardCharsets.UTF_8).trim());
		}
		catch (IOException e)
		{
			log.debug("Could not read the ban ledger identity", e);
			return Optional.empty();
		}
	}

	synchronized boolean recentlyReported(String accountKey)
	{
		return load(accountKey).map(a -> clock.millis() - a.reportedAt < REPORT_COOLDOWN_MILLIS).orElse(false);
	}

	synchronized void markReported(String accountKey)
	{
		load(accountKey).ifPresent(activity ->
		{
			activity.reportedAt = clock.millis();
			save(accountsDir.resolve(accountKey + ".json"), gson.toJson(activity));
		});
	}

	synchronized Optional<JsonObject> report(String accountKey, String banKind, String microbotVersion)
	{
		Optional<AccountActivity> loaded = load(accountKey);
		if (loaded.isEmpty())
		{
			return Optional.empty();
		}
		AccountActivity activity = loaded.get();
		prune(activity);
		LocalDate today = today();
		LocalDate recentStart = today.minusDays(RECENT_DAYS - 1);
		long bot7d = 0;
		long bot30d = 0;
		long longest = 0;
		int activeDays = 0;
		boolean breaks = false;
		Map<String, long[]> plugins = new HashMap<>();
		for (Map.Entry<String, AccountActivity.Day> entry : activity.days.entrySet())
		{
			AccountActivity.Day day = entry.getValue();
			boolean recent = !LocalDate.parse(entry.getKey()).isBefore(recentStart);
			bot30d += day.botSeconds;
			bot7d += recent ? day.botSeconds : 0;
			longest = Math.max(longest, day.longestSession);
			activeDays += day.botSeconds > 0 ? 1 : 0;
			breaks |= day.breaks;
			day.plugins.forEach((plugin, seconds) ->
			{
				long[] totals = plugins.computeIfAbsent(plugin, p -> new long[2]);
				totals[0] += recent ? seconds : 0;
				totals[1] += seconds;
			});
		}
		if (bot30d <= 0)
		{
			return Optional.empty();
		}

		JsonObject json = new JsonObject();
		json.addProperty("accountKey", accountKey);
		json.addProperty("banKind", banKind);
		json.addProperty("microbotVersion", microbotVersion);
		json.addProperty("member", activity.member);
		json.addProperty("totalLevel", activity.totalLevel == null ? null : activity.totalLevel / LEVEL_STEP * LEVEL_STEP);
		json.addProperty("trackedDays", ChronoUnit.DAYS.between(dayOf(activity.firstSeen), today));
		json.addProperty("activeDays30", activeDays);
		json.addProperty("botSeconds7d", bot7d);
		json.addProperty("botSeconds30d", bot30d);
		json.addProperty("longestSessionSeconds", longest);
		json.addProperty("breakHandlerUsed", breaks);
		long idle = Math.max(0, (clock.millis() - activity.lastBot) / 1000);
		json.addProperty("secondsSinceLastBotting", idle / IDLE_STEP_SECONDS * IDLE_STEP_SECONDS);
		JsonArray pluginArray = new JsonArray();
		plugins.entrySet().stream()
			.sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
			.forEach(e ->
			{
				JsonObject plugin = new JsonObject();
				plugin.addProperty("plugin", e.getKey());
				plugin.addProperty("seconds7d", e.getValue()[0]);
				plugin.addProperty("seconds30d", e.getValue()[1]);
				pluginArray.add(plugin);
			});
		json.add("plugins", pluginArray);
		return Optional.of(json);
	}

	private void prune(AccountActivity activity)
	{
		String cutoff = today().minusDays(WINDOW_DAYS - 1).toString();
		activity.days.keySet().removeIf(day -> day.compareTo(cutoff) < 0);
	}

	private Optional<AccountActivity> load(String accountKey)
	{
		if (!KEY.matcher(accountKey).matches())
		{
			return Optional.empty();
		}
		Path file = accountsDir.resolve(accountKey + ".json");
		if (!Files.isRegularFile(file))
		{
			return Optional.empty();
		}
		try
		{
			AccountActivity activity = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), AccountActivity.class);
			if (activity == null)
			{
				return Optional.empty();
			}
			if (activity.days == null)
			{
				activity.days = new TreeMap<>();
			}
			activity.days.values().removeIf(day -> day == null || day.plugins == null);
			return Optional.of(activity);
		}
		catch (IOException | JsonParseException e)
		{
			log.debug("Could not read the ban ledger for an account", e);
			return Optional.empty();
		}
	}

	private void save(Path file, String content)
	{
		try
		{
			Files.createDirectories(file.getParent());
			Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
			Files.writeString(temp, content, StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			log.debug("Could not write the ban ledger", e);
		}
	}

	private LocalDate today()
	{
		return LocalDate.now(clock.withZone(ZoneOffset.UTC));
	}

	private static LocalDate dayOf(long millis)
	{
		return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
	}

	private static String sha256(String value)
	{
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest)
			{
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
