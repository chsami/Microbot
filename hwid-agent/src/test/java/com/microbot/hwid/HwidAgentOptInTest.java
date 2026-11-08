package com.microbot.hwid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HwidAgentOptInTest
{
	private String previousSpoof;
	private File credentialsFile;

	@Before
	public void setUp() throws Exception
	{
		previousSpoof = System.getProperty(HwidAgent.TOGGLE_PROPERTY);
		System.clearProperty(HwidAgent.AGENT_PROPERTY);
		credentialsFile = File.createTempFile("hwid-optin", ".credentials");
		credentialsFile.delete();
		HwidAgent.setCredentialsFileForTesting(credentialsFile);
	}

	@After
	public void tearDown()
	{
		if (previousSpoof == null)
		{
			System.clearProperty(HwidAgent.TOGGLE_PROPERTY);
		}
		else
		{
			System.setProperty(HwidAgent.TOGGLE_PROPERTY, previousSpoof);
		}
		System.clearProperty(HwidAgent.AGENT_PROPERTY);
		credentialsFile.delete();
		HwidAgent.setCredentialsFileForTesting(null);
	}

	@Test
	public void premainRegistersEveryTransformerWhenTheSpoofPropertyIsMissing()
	{
		System.clearProperty(HwidAgent.TOGGLE_PROPERTY);
		AtomicInteger transformers = new AtomicInteger();
		HwidAgent.premain(null, countingInstrumentation(transformers));
		assertEquals(3, transformers.get());
		assertEquals(HwidAgent.AGENT_LOADED, System.getProperty(HwidAgent.AGENT_PROPERTY));
		assertTrue(HwidAgent.spoofEnabled());
	}

	@Test
	public void premainStaysInertOnlyWhenSpoofingIsExplicitlyOff()
	{
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "false");
		AtomicInteger transformers = new AtomicInteger();
		HwidAgent.premain(null, countingInstrumentation(transformers));
		assertEquals(0, transformers.get());
		assertEquals(HwidAgent.AGENT_INERT, System.getProperty(HwidAgent.AGENT_PROPERTY));
	}

	@Test
	public void premainRegistersEveryTransformerForAnyValueOtherThanFalse()
	{
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "0");
		AtomicInteger transformers = new AtomicInteger();
		HwidAgent.premain(null, countingInstrumentation(transformers));
		assertEquals(3, transformers.get());
		assertEquals(HwidAgent.AGENT_LOADED, System.getProperty(HwidAgent.AGENT_PROPERTY));
	}

	@Test
	public void premainRegistersEveryTransformerWhenSpoofingIsOn()
	{
		System.setProperty(HwidAgent.TOGGLE_PROPERTY, "true");
		AtomicInteger transformers = new AtomicInteger();
		HwidAgent.premain(null, countingInstrumentation(transformers));
		assertEquals(3, transformers.get());
		assertEquals(HwidAgent.AGENT_LOADED, System.getProperty(HwidAgent.AGENT_PROPERTY));
	}

	static Instrumentation countingInstrumentation(AtomicInteger transformers)
	{
		return (Instrumentation) Proxy.newProxyInstance(HwidAgentOptInTest.class.getClassLoader(),
			new Class<?>[]{Instrumentation.class}, (proxy, method, args) ->
			{
				if (method.getName().equals("addTransformer"))
				{
					transformers.incrementAndGet();
				}
				return null;
			});
	}
}
