package net.runelite.client.plugins.microbot;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.client.plugins.microbot.HwidSpoofCheck.Problem;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HwidSpoofCheckTest
{
	private final Map<String, String> properties = new HashMap<>();
	private final AtomicLong clock = new AtomicLong(1_000);
	private final HwidSpoofCheck check = new HwidSpoofCheck(properties::get, clock::get);

	private void agentLoadedWithDeviceHook()
	{
		properties.put(HwidSpoofCheck.AGENT_PROPERTY, HwidSpoofCheck.AGENT_LOADED);
		properties.put(HwidSpoofCheck.DEVICE_HOOK_PROPERTY, "true");
		properties.put(HwidSpoofCheck.PLATFORM_HOOK_PROPERTY, "true");
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
		assertEquals(Optional.of(Problem.AGENT_MISSING), check.loggedIn(true));
	}

	@Test
	public void reportsMissingAgent()
	{
		check.loginStarted();
		assertEquals(Optional.of(Problem.AGENT_MISSING), check.loggedIn(true));
		assertEquals(Optional.of(HwidSpoofCheck.AGENT_MISSING_CHAT), Problem.AGENT_MISSING.chatMessage());
	}

	@Test
	public void reportsInertAgent()
	{
		properties.put(HwidSpoofCheck.AGENT_PROPERTY, HwidSpoofCheck.AGENT_INERT);
		check.loginStarted();
		assertEquals(Optional.of(Problem.AGENT_INERT), check.loggedIn(true));
		assertEquals(Optional.of(HwidSpoofCheck.AGENT_INERT_CHAT), Problem.AGENT_INERT.chatMessage());
	}

	@Test
	public void reportsMissingDeviceHook()
	{
		properties.put(HwidSpoofCheck.AGENT_PROPERTY, HwidSpoofCheck.AGENT_LOADED);
		properties.put(HwidSpoofCheck.RANDOM_DAT_CALL_PROPERTY, "1500");
		check.loginStarted();
		assertEquals(Optional.of(Problem.DEVICE_HOOK_MISSING), check.loggedIn(true));
	}

	@Test
	public void reportsRandomDatHookThatOnlyRanBeforeThisLogin()
	{
		agentLoadedWithDeviceHook();
		properties.put(HwidSpoofCheck.RANDOM_DAT_CALL_PROPERTY, "900");
		check.loginStarted();
		assertEquals(Optional.of(Problem.RANDOM_DAT_HOOK_MISSED), check.loggedIn(true));
	}

	@Test
	public void reportsMissingPlatformHookOnlyAfterTheOtherHooksRan()
	{
		properties.put(HwidSpoofCheck.AGENT_PROPERTY, HwidSpoofCheck.AGENT_LOADED);
		properties.put(HwidSpoofCheck.DEVICE_HOOK_PROPERTY, "true");
		properties.put(HwidSpoofCheck.RANDOM_DAT_CALL_PROPERTY, "1500");
		check.loginStarted();
		assertEquals(Optional.of(Problem.PLATFORM_HOOK_MISSING), check.loggedIn(true));
		assertEquals(Optional.of(HwidSpoofCheck.PLATFORM_HOOK_MISSING_CHAT), Problem.PLATFORM_HOOK_MISSING.chatMessage());
		assertTrue(Problem.PLATFORM_HOOK_MISSING.logMessage().contains(HwidSpoofCheck.PLATFORM_HOOK_PROPERTY));
	}

	@Test
	public void missedRandomDatHookWinsOverAMissingPlatformHook()
	{
		properties.put(HwidSpoofCheck.AGENT_PROPERTY, HwidSpoofCheck.AGENT_LOADED);
		properties.put(HwidSpoofCheck.DEVICE_HOOK_PROPERTY, "true");
		properties.put(HwidSpoofCheck.RANDOM_DAT_CALL_PROPERTY, "900");
		check.loginStarted();
		assertEquals(Optional.of(Problem.RANDOM_DAT_HOOK_MISSED), check.loggedIn(true));
	}

	@Test
	public void bothHookProblemsShareOneChatMessageButKeepTheirOwnLogDetail()
	{
		assertEquals(Optional.of(HwidSpoofCheck.HOOK_FAILED_CHAT), Problem.DEVICE_HOOK_MISSING.chatMessage());
		assertEquals(Optional.of(HwidSpoofCheck.HOOK_FAILED_CHAT), Problem.RANDOM_DAT_HOOK_MISSED.chatMessage());
		assertTrue(Problem.DEVICE_HOOK_MISSING.logMessage().contains(HwidSpoofCheck.DEVICE_HOOK_PROPERTY));
		assertTrue(Problem.RANDOM_DAT_HOOK_MISSED.logMessage().contains(HwidSpoofCheck.RANDOM_DAT_CALL_PROPERTY));
	}

	@Test
	public void silentWhenTheLauncherTurnedSpoofingOff()
	{
		properties.put(HwidSpoofCheck.LAUNCHER_DISABLED_PROPERTY, "true");
		check.loginStarted();
		assertFalse(check.loggedIn(true).isPresent());
		check.loginStarted();
		assertFalse(check.loggedIn(false).isPresent());
	}

	@Test
	public void chatTextsTellThePlayerWhatHappenedAndWhatToDo()
	{
		assertEquals(Optional.of("Spoofing is not active, so this login sent your real device details. Restart the client from the Microbot launcher and check that Spoof hardware IDs is ticked. Already ticked? Report it in the Microbot Discord."),
			Problem.AGENT_MISSING.chatMessage());
		assertEquals(Optional.of("This client started with spoofing off, so this login sent your real device details. Restart from the Microbot launcher, or turn off Spoof device ID in Microbot > General."),
			Problem.AGENT_INERT.chatMessage());
		String hookFailed = "Spoof device ID is on, but spoofing did not work in this client version, so this login may have sent the real device details. Report it in the Microbot Discord, or turn off Spoof device ID in Microbot > General.";
		assertEquals(Optional.of(hookFailed), Problem.DEVICE_HOOK_MISSING.chatMessage());
		assertEquals(Optional.of(hookFailed), Problem.RANDOM_DAT_HOOK_MISSED.chatMessage());
	}

	@Test
	public void rawLaunchFlagsStayInTheLogOnly()
	{
		for (Problem problem : Problem.values())
		{
			problem.chatMessage().ifPresent(message ->
				assertFalse(message, message.contains("-javaagent") || message.contains("-D") || message.contains("microbot.hwid")));
			problem.chatMessage().ifPresent(message -> assertTrue(problem.logMessage().startsWith(message)));
		}
		assertTrue(Problem.AGENT_MISSING.logMessage().contains("-javaagent"));
		assertTrue(Problem.AGENT_INERT.logMessage().contains("-Dmicrobot.hwid.spoof=false"));
	}

	@Test
	public void silentWhenSpoofingIsOff()
	{
		check.loginStarted();
		assertFalse(check.loggedIn(false).isPresent());
	}

	@Test
	public void settingOffWithTheAgentLoadedIsLoggedOnceAndNeverShownInChat()
	{
		agentLoadedWithDeviceHook();
		check.loginStarted();
		assertEquals(Optional.of(Problem.SETTING_OFF_WITH_LAUNCHER_ON), check.loggedIn(false));
		assertFalse(Problem.SETTING_OFF_WITH_LAUNCHER_ON.chatMessage().isPresent());
		check.loginStarted();
		assertFalse(check.loggedIn(false).isPresent());
	}

	@Test
	public void silentWhenTheSettingIsOffAndTheAgentIsInert()
	{
		properties.put(HwidSpoofCheck.AGENT_PROPERTY, HwidSpoofCheck.AGENT_INERT);
		check.loginStarted();
		assertFalse(check.loggedIn(false).isPresent());
	}

	@Test
	public void checksOnlyTheFirstLoggedInAfterALoginStarts()
	{
		check.loginStarted();
		assertEquals(Optional.of(Problem.AGENT_MISSING), check.loggedIn(true));
		agentLoadedWithDeviceHook();
		assertFalse(check.loggedIn(true).isPresent());
	}

	@Test
	public void reportsEachProblemOncePerSession()
	{
		check.loginStarted();
		assertEquals(Optional.of(Problem.AGENT_MISSING), check.loggedIn(true));
		clock.set(2_000);
		check.loginStarted();
		assertFalse(check.loggedIn(true).isPresent());
	}

	@Test
	public void loginIsBlockedWhenStockRuneLiteSignsWithAnotherCertificate()
	{
		agentLoadedWithDeviceHook();
		properties.put(HwidSpoofCheck.SIGNER_STATUS_PROPERTY, "changed");
		assertEquals(Optional.of(HwidSpoofCheck.LoginBlock.SIGNER_CHANGED), check.loginBlock(true));
	}

	@Test
	public void loginIsBlockedWhenTheCertificateRanOutAndStockCouldNotBeChecked()
	{
		agentLoadedWithDeviceHook();
		properties.put(HwidSpoofCheck.SIGNER_STATUS_PROPERTY, "expired");
		assertEquals(Optional.of(HwidSpoofCheck.LoginBlock.SIGNER_EXPIRED), check.loginBlock(true));
	}

	@Test
	public void loginIsBlockedWhenASignerCallCouldNotBeHidden()
	{
		agentLoadedWithDeviceHook();
		properties.put(HwidSpoofCheck.SIGNER_STATUS_PROPERTY, "confirmed");
		properties.put(HwidSpoofCheck.PLATFORM_MISSED_PROPERTY, "true");
		assertEquals(Optional.of(HwidSpoofCheck.LoginBlock.PLATFORM_DETAILS_MIXED), check.loginBlock(true));
	}

	@Test
	public void loginIsAllowedWhenStockIsConfirmedOrCouldNotBeReachedYet()
	{
		agentLoadedWithDeviceHook();
		assertFalse(check.loginBlock(true).isPresent());
		properties.put(HwidSpoofCheck.SIGNER_STATUS_PROPERTY, "confirmed");
		assertFalse(check.loginBlock(true).isPresent());
		properties.put(HwidSpoofCheck.SIGNER_STATUS_PROPERTY, "unchecked");
		assertFalse(check.loginBlock(true).isPresent());
	}

	@Test
	public void loginIsNeverBlockedWhenThePlayerIsNotSpoofing()
	{
		properties.put(HwidSpoofCheck.SIGNER_STATUS_PROPERTY, "changed");
		properties.put(HwidSpoofCheck.PLATFORM_MISSED_PROPERTY, "true");
		// No agent attached: nothing is spoofed, so nothing sent can be a wrong spoof.
		assertFalse(check.loginBlock(true).isPresent());
		properties.put(HwidSpoofCheck.AGENT_PROPERTY, HwidSpoofCheck.AGENT_INERT);
		assertFalse(check.loginBlock(true).isPresent());

		agentLoadedWithDeviceHook();
		assertTrue(check.loginBlock(true).isPresent());
		// Spoof device ID turned off is the stated way to log in anyway.
		assertFalse(check.loginBlock(false).isPresent());
		properties.put(HwidSpoofCheck.LAUNCHER_DISABLED_PROPERTY, "true");
		assertFalse(check.loginBlock(true).isPresent());
	}
}
