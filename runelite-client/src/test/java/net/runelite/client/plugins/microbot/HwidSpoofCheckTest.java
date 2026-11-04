package net.runelite.client.plugins.microbot;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class HwidSpoofCheckTest
{
	private final Map<String, String> properties = new HashMap<>();
	private final AtomicLong clock = new AtomicLong(1_000);
	private final HwidSpoofCheck check = new HwidSpoofCheck(properties::get, clock::get);

	private void agentLoadedWithDeviceHook()
	{
		properties.put(HwidSpoofCheck.AGENT_PROPERTY, "loaded");
		properties.put(HwidSpoofCheck.DEVICE_HOOK_PROPERTY, "true");
	}

	@Test
	public void silentWhenEveryHookRanDuringLogin()
	{
		agentLoadedWithDeviceHook();
		check.loginStarted();
		properties.put(HwidSpoofCheck.RANDOM_DAT_CALL_PROPERTY, "1500");
		assertFalse(check.loggedIn(true).isPresent());
	}

	@Test
	public void firstLoginBeforeAnyLoginScreenEventIsChecked()
	{
		assertEquals(Optional.of(HwidSpoofCheck.AGENT_MISSING), check.loggedIn(true));
	}

	@Test
	public void reportsMissingAgent()
	{
		check.loginStarted();
		assertEquals(Optional.of(HwidSpoofCheck.AGENT_MISSING), check.loggedIn(true));
	}

	@Test
	public void reportsMissingDeviceHook()
	{
		properties.put(HwidSpoofCheck.AGENT_PROPERTY, "loaded");
		properties.put(HwidSpoofCheck.RANDOM_DAT_CALL_PROPERTY, "1500");
		check.loginStarted();
		assertEquals(Optional.of(HwidSpoofCheck.DEVICE_HOOK_MISSING), check.loggedIn(true));
	}

	@Test
	public void reportsRandomDatHookThatOnlyRanBeforeThisLogin()
	{
		agentLoadedWithDeviceHook();
		properties.put(HwidSpoofCheck.RANDOM_DAT_CALL_PROPERTY, "900");
		check.loginStarted();
		assertEquals(Optional.of(HwidSpoofCheck.RANDOM_DAT_HOOK_MISSED), check.loggedIn(true));
	}

	@Test
	public void silentWhenTheLauncherTurnedSpoofingOff()
	{
		properties.put(HwidSpoofCheck.LAUNCHER_DISABLED_PROPERTY, "true");
		check.loginStarted();
		assertFalse(check.loggedIn(true).isPresent());
	}

	@Test
	public void silentWhenSpoofingIsOff()
	{
		check.loginStarted();
		assertFalse(check.loggedIn(false).isPresent());
	}

	@Test
	public void checksOnlyTheFirstLoggedInAfterALoginStarts()
	{
		check.loginStarted();
		assertEquals(Optional.of(HwidSpoofCheck.AGENT_MISSING), check.loggedIn(true));
		agentLoadedWithDeviceHook();
		assertFalse(check.loggedIn(true).isPresent());
	}

	@Test
	public void reportsEachProblemOncePerSession()
	{
		check.loginStarted();
		assertEquals(Optional.of(HwidSpoofCheck.AGENT_MISSING), check.loggedIn(true));
		clock.set(2_000);
		check.loginStarted();
		assertFalse(check.loggedIn(true).isPresent());
	}
}
