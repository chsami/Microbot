package net.runelite.client.config;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import net.runelite.api.Client;
import net.runelite.client.RuneLite;
import net.runelite.client.account.SessionManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigManagerBankPinTest
{
	@Bind
	EventBus eventBus = mock(EventBus.class);
	@Bind
	ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
	@Bind
	RuneLiteConfig runeliteConfig = mock(RuneLiteConfig.class);
	@Bind
	Gson gson = RuneLiteAPI.GSON;
	@Bind
	@Named("sessionfile")
	File sessionfile = RuneLite.DEFAULT_SESSION_FILE;
	@Bind
	@Named("profile")
	@Nullable
	String profile;
	@Bind
	Client client = mock(Client.class);
	@Bind
	ConfigClient configClient = mock(ConfigClient.class);
	@Bind
	SessionManager sessionManager = mock(SessionManager.class);
	@Bind
	ProfileManager profileManager = mock(ProfileManager.class);
	@Inject
	ConfigManager manager;

	private String saved;

	@Before
	public void before()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
		ProfileManager.Lock lock = mock(ProfileManager.Lock.class);
		when(lock.createProfile(anyString())).thenAnswer(a -> named(System.nanoTime()));
		when(lock.createProfile(anyString(), anyLong())).thenAnswer(a -> named(a.getArgument(1)));
		when(profileManager.lock()).thenReturn(lock);
		manager.load();
		// Every lock reads the profiles from disk, so it hands out new objects.
		when(lock.findProfile(anyLong())).thenAnswer(a ->
		{
			ConfigProfile read = new ConfigProfile((long) a.getArgument(0))
			{
				@Override
				public void setBankPin(String pin)
				{
					super.setBankPin(pin);
					saved = pin;
				}
			};
			read.setName("test");
			read.bankPin = saved;
			return read;
		});
	}

	@Test
	public void aSavedPinIsUsedAtOnce()
	{
		manager.setBankPin(manager.getProfile(), "encrypted");
		assertEquals("encrypted", manager.getProfile().getBankPin());
	}

	@Test
	public void aRemovedPinIsGoneAtOnce()
	{
		manager.setBankPin(manager.getProfile(), "encrypted");
		manager.sendConfig();
		manager.setBankPin(manager.getProfile(), null);
		assertNull(saved);
		assertNull(manager.getProfile().getBankPin());
	}

	@Test
	public void savingAnotherProfilesPinLeavesTheActiveOneAlone()
	{
		long active = manager.getProfile().getId();
		manager.setBankPin(named(active + 1), "other");
		assertEquals(active, manager.getProfile().getId());
	}

	private static ConfigProfile named(long id)
	{
		ConfigProfile profile = new ConfigProfile(id);
		profile.setName("test");
		return profile;
	}
}
