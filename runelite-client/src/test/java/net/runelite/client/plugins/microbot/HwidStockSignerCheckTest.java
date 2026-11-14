package net.runelite.client.plugins.microbot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HwidStockSignerCheckTest
{
	private String previousAgent;

	@Before
	public void setUp()
	{
		previousAgent = System.getProperty(HwidSpoofCheck.AGENT_PROPERTY);
	}

	@After
	public void tearDown()
	{
		if (previousAgent == null)
		{
			System.clearProperty(HwidSpoofCheck.AGENT_PROPERTY);
		}
		else
		{
			System.setProperty(HwidSpoofCheck.AGENT_PROPERTY, previousAgent);
		}
	}

	@Test
	public void nothingIsAskedWithoutALoadedAgent()
	{
		System.clearProperty(HwidSpoofCheck.AGENT_PROPERTY);
		assertFalse(HwidStockSignerCheck.start("RuneLite/test", getClass().getClassLoader()));
		System.setProperty(HwidSpoofCheck.AGENT_PROPERTY, HwidSpoofCheck.AGENT_INERT);
		assertFalse(HwidStockSignerCheck.start("RuneLite/test", getClass().getClassLoader()));
	}

	@Test
	public void aMissingAgentClassIsNotAnError()
	{
		System.setProperty(HwidSpoofCheck.AGENT_PROPERTY, HwidSpoofCheck.AGENT_LOADED);
		assertFalse(HwidStockSignerCheck.start("RuneLite/test", getClass().getClassLoader()));
	}

	@Test
	public void theCheckIsStartedOnlyAfterTheProxyIsSetUp() throws Exception
	{
		Path source = Paths.get("src/main/java/net/runelite/client/RuneLite.java");
		assertTrue("run from the runelite-client project directory", Files.isRegularFile(source));
		String main = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
		int proxy = main.indexOf("ProxyConfiguration.setupProxy(");
		int check = main.indexOf("HwidStockSignerCheck.start(");
		assertTrue(proxy >= 0 && check >= 0);
		assertTrue("the stock signer check must come after the proxy setup", check > proxy);
		assertEquals("exactly one call", check, main.lastIndexOf("HwidStockSignerCheck.start("));
	}
}
