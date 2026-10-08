package net.runelite.client.plugins.kourendlibrary;

import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KourendLibraryPluginTest
{
	@Inject
	KourendLibraryPlugin plugin;

	@Mock
	@Bind
	Client client;

	@Mock
	@Bind
	ClientThread clientThread;

	@Mock
	@Bind
	ClientToolbar clientToolbar;

	@Mock
	@Bind
	Library library;

	@Mock
	@Bind
	OverlayManager overlayManager;

	@Mock
	@Bind
	KourendLibraryOverlay overlay;

	@Mock
	@Bind
	KourendLibraryTutorialOverlay tutorialOverlay;

	@Mock
	@Bind
	KourendLibraryConfig config;

	@Mock
	@Bind
	ItemManager itemManager;

	@Before
	public void before()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
	}

	@Test
	public void hideButtonReadsPlayerLocationOnClientThread() throws Exception
	{
		Player player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(1632, 3807, 0));
		when(client.getLocalPlayer()).thenReturn(player);
		when(config.hideButton()).thenReturn(true);

		ConfigChanged event = new ConfigChanged();
		event.setGroup(KourendLibraryConfig.GROUP_KEY);
		event.setKey("hideButton");
		plugin.onConfigChanged(event);

		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(clientThread).invokeLater(task.capture());
		SwingUtilities.invokeAndWait(() -> { });
		verify(client, never()).getLocalPlayer();

		task.getValue().run();
		SwingUtilities.invokeAndWait(() -> { });
		verify(clientToolbar).addNavigation(any());
	}
}
