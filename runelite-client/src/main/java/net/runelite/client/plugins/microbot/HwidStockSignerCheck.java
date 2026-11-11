package net.runelite.client.plugins.microbot;

import lombok.extern.slf4j.Slf4j;

/**
 * Asks the HWID agent to check that stock RuneLite still sends the bundled signer hash. The agent opens
 * no connection by itself: this is called from {@code RuneLite.main} right after
 * {@code ProxyConfiguration.setupProxy}, so the request to runelite.net goes through the client's proxy
 * like the rest of its traffic. The agent is reached by name because the client does not depend on it.
 */
@Slf4j
public final class HwidStockSignerCheck
{
	static final String AGENT_CLASS = "com.microbot.hwid.HwidAgent";
	static final String AGENT_METHOD = "checkStockSigner";

	private HwidStockSignerCheck()
	{
	}

	/** Call only after the proxy settings are applied. Never throws. */
	public static void start(String userAgent)
	{
		start(userAgent, ClassLoader.getSystemClassLoader());
	}

	/** Returns whether the agent was asked. */
	static boolean start(String userAgent, ClassLoader agentLoader)
	{
		if (!HwidSpoofCheck.AGENT_LOADED.equals(System.getProperty(HwidSpoofCheck.AGENT_PROPERTY)))
		{
			return false;
		}
		try
		{
			Class.forName(AGENT_CLASS, true, agentLoader).getMethod(AGENT_METHOD, String.class).invoke(null, userAgent);
			return true;
		}
		catch (ReflectiveOperationException | LinkageError e)
		{
			// The signer status then stays as read from disk: unchecked, or a block already found earlier.
			log.warn("HWID agent could not start the stock signer check: {}", e.toString());
			return false;
		}
	}
}
