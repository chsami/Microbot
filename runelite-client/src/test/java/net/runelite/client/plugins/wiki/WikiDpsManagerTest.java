/*
 * Copyright (c) 2026, Microbot
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.wiki;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class WikiDpsManagerTest
{
	private static final int LAYER = 12345;

	@Mock
	@Bind
	private Client client;

	@Mock
	@Bind
	private ClientThread clientThread;

	@Mock
	@Bind
	private EventBus eventBus;

	@Mock
	@Bind
	private OkHttpClient okHttpClient;

	@Bind
	private Gson gson = new Gson();

	@Inject
	private WikiDpsManager wikiDpsManager;

	@Before
	public void before()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
		doAnswer(invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
	}

	@Test
	public void missingLayerIsIgnored()
	{
		fireSetup();

		verify(client).getWidget(LAYER);
	}

	@Test
	public void layerWithoutChildrenIsIgnored()
	{
		Widget parent = mock(Widget.class, RETURNS_SELF);
		Widget setBonus = mock(Widget.class, RETURNS_SELF);
		when(setBonus.getParent()).thenReturn(parent);
		when(setBonus.getChildren()).thenReturn(null);
		when(client.getWidget(LAYER)).thenReturn(setBonus);

		fireSetup();

		verify(setBonus, never()).setOriginalX(anyInt());
		verify(parent, never()).createChild(anyInt(), anyInt());
	}

	@Test
	public void layerWithTooFewChildrenIsIgnored()
	{
		Widget parent = mock(Widget.class, RETURNS_SELF);
		Widget setBonus = mock(Widget.class, RETURNS_SELF);
		when(setBonus.getParent()).thenReturn(parent);
		when(setBonus.getChildren()).thenReturn(children(9));
		when(client.getWidget(LAYER)).thenReturn(setBonus);

		fireSetup();

		verify(setBonus, never()).setOriginalX(anyInt());
		verify(parent, never()).createChild(anyInt(), anyInt());
	}

	@Test
	public void layerWithNullChildEntryIsIgnored()
	{
		Widget parent = mock(Widget.class, RETURNS_SELF);
		Widget setBonus = mock(Widget.class, RETURNS_SELF);
		Widget[] children = children(10);
		children[4] = null;
		when(setBonus.getParent()).thenReturn(parent);
		when(setBonus.getChildren()).thenReturn(children);
		when(client.getWidget(LAYER)).thenReturn(setBonus);

		fireSetup();

		verify(parent, never()).createChild(anyInt(), anyInt());
	}

	@Test
	public void layerWithChildrenGetsButton()
	{
		Widget parent = mock(Widget.class, RETURNS_SELF);
		Widget setBonus = mock(Widget.class, RETURNS_SELF);
		when(setBonus.getParent()).thenReturn(parent);
		when(setBonus.getChildren()).thenReturn(children(10));
		when(client.getWidget(LAYER)).thenReturn(setBonus);
		when(parent.createChild(anyInt(), anyInt())).thenAnswer(invocation -> mock(Widget.class, RETURNS_SELF));

		fireSetup();

		verify(parent, times(10)).createChild(anyInt(), anyInt());
		verify(parent).revalidate();
	}

	private void fireSetup()
	{
		ScriptEvent scriptEvent = mock(ScriptEvent.class);
		when(scriptEvent.getArguments()).thenReturn(new Object[]{ScriptID.EQUIPMENT_SET_STAT_BONUS_SETUP, LAYER});
		ScriptPreFired event = new ScriptPreFired(ScriptID.EQUIPMENT_SET_STAT_BONUS_SETUP);
		event.setScriptEvent(scriptEvent);
		wikiDpsManager.onScriptPreFired(event);
	}

	private static Widget[] children(int count)
	{
		Widget[] children = new Widget[count];
		for (int i = 0; i < count; i++)
		{
			children[i] = mock(Widget.class, RETURNS_SELF);
		}
		return children;
	}
}
