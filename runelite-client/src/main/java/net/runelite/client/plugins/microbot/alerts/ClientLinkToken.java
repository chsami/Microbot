package net.runelite.client.plugins.microbot.alerts;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotConfig;

@Singleton
public class ClientLinkToken
{
	public static final String ENV_VAR = "MICROBOT_CLIENT_TOKEN";
	public static final String HEADER = "X-Microbot-Client-Token";
	private static final Pattern FORMAT = Pattern.compile("mbc_[A-Za-z0-9_-]{43}");

	private final ConfigManager configManager;
	private final boolean disableTelemetry;
	private volatile String rejected;

	@Inject
	ClientLinkToken(ConfigManager configManager, @Named("disableTelemetry") boolean disableTelemetry)
	{
		this.configManager = configManager;
		this.disableTelemetry = disableTelemetry;
	}

	public Optional<String> get()
	{
		if (isTelemetryDisabled())
		{
			return Optional.empty();
		}
		String revoked = rejected;
		return configured().filter(token -> !token.equals(revoked));
	}

	public Optional<String> configured()
	{
		return resolve(System.getenv(ENV_VAR), configManager.getConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyAlertLinkToken));
	}

	public boolean isFromEnvironment()
	{
		return resolve(System.getenv(ENV_VAR), null).isPresent();
	}

	public boolean isTelemetryDisabled()
	{
		return disableTelemetry || Microbot.isTelemetryDisabled();
	}

	public boolean reject(String token)
	{
		if (token.equals(rejected))
		{
			return false;
		}
		rejected = token;
		return true;
	}

	static Optional<String> resolve(String envValue, String configValue)
	{
		return Stream.of(envValue, configValue)
			.map(value -> value == null ? "" : value.trim())
			.filter(value -> FORMAT.matcher(value).matches())
			.findFirst();
	}
}
