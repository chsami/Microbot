package com.microbot.hwid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * premain runs before the client has read its --proxy option, so a connection opened there would leave
 * from the real address. The lookup may only start when the client asks for it.
 */
public class StockSignerLookupTimingTest
{
	private final List<URI> connections = new CopyOnWriteArrayList<>();
	private final AtomicInteger remotesBuilt = new AtomicInteger();
	private final List<String> userAgents = new CopyOnWriteArrayList<>();
	private ProxySelector previousSelector;
	private String previousSpoof;
	private String previousLookup;
	private File credentialsFile;

	@Before
	public void setUp() throws Exception
	{
		previousSpoof = System.getProperty(HwidAgent.TOGGLE_PROPERTY);
		previousLookup = System.getProperty(StockSignerHash.LOOKUP_PROPERTY);
		previousSelector = ProxySelector.getDefault();
		System.clearProperty(HwidAgent.AGENT_PROPERTY);
		System.clearProperty(HwidAgent.TOGGLE_PROPERTY);
		// The build turns the lookup off for every other test; here it is on, as in a real launch.
		System.setProperty(StockSignerHash.LOOKUP_PROPERTY, "true");
		StockSignerHash.resetForTesting();
		credentialsFile = File.createTempFile("hwid-timing", ".credentials");
		credentialsFile.delete();
		HwidAgent.setCredentialsFileForTesting(credentialsFile);

		// Every java.net connection, HttpURLConnection included, asks the default selector first.
		ProxySelector.setDefault(new ProxySelector()
		{
			@Override
			public List<Proxy> select(URI uri)
			{
				connections.add(uri);
				throw new IllegalStateException("no connection is allowed in this test: " + uri);
			}

			@Override
			public void connectFailed(URI uri, SocketAddress address, IOException e)
			{
			}
		});
		StockSignerHash.setRemoteFactoryForTesting(userAgent ->
		{
			remotesBuilt.incrementAndGet();
			userAgents.add(String.valueOf(userAgent));
			return new StockSignerHash.Remote()
			{
				@Override
				public byte[] bootstrap() throws IOException
				{
					throw new IOException("unreachable");
				}

				@Override
				public void download(String url, File target, long maxBytes) throws IOException
				{
					throw new IOException("unreachable");
				}
			};
		});
	}

	@After
	public void tearDown()
	{
		ProxySelector.setDefault(previousSelector);
		restore(HwidAgent.TOGGLE_PROPERTY, previousSpoof);
		restore(StockSignerHash.LOOKUP_PROPERTY, previousLookup);
		System.clearProperty(HwidAgent.AGENT_PROPERTY);
		StockSignerHash.resetForTesting();
		credentialsFile.delete();
		HwidAgent.setCredentialsFileForTesting(null);
	}

	@Test
	public void premainOpensNoConnectionAndStartsNoLookup() throws Exception
	{
		HwidAgent.premain(null, instrumentation());

		assertEquals(HwidAgent.AGENT_LOADED, System.getProperty(HwidAgent.AGENT_PROPERTY));
		assertNotNull("the stored check is still applied in premain", System.getProperty(StockSignerHash.STATUS_PROPERTY));
		assertNull("premain started the lookup thread", lookupThread());
		// Give anything premain might have started in the background time to try.
		Thread.sleep(300);
		assertNull(lookupThread());
		assertEquals("premain built a remote", 0, remotesBuilt.get());
		assertTrue("premain opened a connection: " + connections, connections.isEmpty());
	}

	@Test
	public void theLookupStartsOnlyWhenTheClientAsksAndOnlyOnce() throws Exception
	{
		HwidAgent.premain(null, instrumentation());
		assertEquals(0, remotesBuilt.get());

		HwidAgent.checkStockSigner("RuneLite/test");
		HwidAgent.checkStockSigner("RuneLite/test");

		Thread thread = lookupThread();
		if (thread != null)
		{
			thread.join(5000);
		}
		assertEquals(1, remotesBuilt.get());
		assertEquals(List.of("RuneLite/test"), userAgents);
		assertTrue(connections.isEmpty());
	}

	@Test
	public void anInertAgentNeverStartsTheLookup()
	{
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "false");
		HwidAgent.premain(null, instrumentation());
		assertEquals(HwidAgent.AGENT_INERT, System.getProperty(HwidAgent.AGENT_PROPERTY));

		HwidAgent.checkStockSigner("RuneLite/test");

		assertEquals(0, remotesBuilt.get());
		assertNull(System.getProperty(StockSignerHash.STATUS_PROPERTY));
	}

	@Test
	public void theRealLookupGoesThroughTheJvmProxySettings() throws Exception
	{
		// With the real remote, the request must consult the JVM's proxy selection, which is where the
		// client's SOCKS settings take effect. The selector here refuses, so nothing leaves the machine.
		StockSignerHash.resetForTesting();
		HwidAgent.premain(null, instrumentation());
		assertTrue(connections.isEmpty());

		Thread thread = StockSignerHash.startLookup("RuneLite/test");
		assertNotNull(thread);
		thread.join(10000);

		assertEquals(1, connections.size());
		assertEquals("static.runelite.net", connections.get(0).getHost());
	}

	private static Thread lookupThread()
	{
		for (Thread thread : Thread.getAllStackTraces().keySet())
		{
			if (StockSignerHash.LOOKUP_THREAD.equals(thread.getName()) && thread.isAlive())
			{
				return thread;
			}
		}
		return null;
	}

	private static Instrumentation instrumentation()
	{
		return (Instrumentation) java.lang.reflect.Proxy.newProxyInstance(
			StockSignerLookupTimingTest.class.getClassLoader(), new Class<?>[]{Instrumentation.class}, (proxy, method, args) -> null);
	}

	private static void restore(String key, String value)
	{
		if (value == null)
		{
			System.clearProperty(key);
		}
		else
		{
			System.setProperty(key, value);
		}
	}
}
