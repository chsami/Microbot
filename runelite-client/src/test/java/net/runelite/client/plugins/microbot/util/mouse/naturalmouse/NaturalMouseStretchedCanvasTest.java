package net.runelite.client.plugins.microbot.util.mouse.naturalmouse;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.input.PointerState;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.Dimension;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NaturalMouseStretchedCanvasTest
{
	private static final int W = 765;
	private static final int H = 503;
	private static final int STRETCHED_W = 1530;
	private static final int STRETCHED_H = 1006;

	private Object previousClient;
	private Object previousNaturalMouse;

	@Before
	public void before() throws Exception
	{
		Canvas canvas = new Canvas();
		canvas.setSize(STRETCHED_W, STRETCHED_H);

		Client client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(false);
		when(client.isStretchedEnabled()).thenReturn(true);
		when(client.getStretchedDimensions()).thenReturn(new Dimension(STRETCHED_W, STRETCHED_H));
		when(client.getRealDimensions()).thenReturn(new Dimension(W, H));
		when(client.getCanvasWidth()).thenReturn(W);
		when(client.getCanvasHeight()).thenReturn(H);

		previousClient = swapStatic("client", client);
		previousNaturalMouse = swapStatic("naturalMouse", null);

		PointerState.reset();
		InputArbiter.resetForTest();
	}

	@After
	public void after() throws Exception
	{
		swapStatic("client", previousClient);
		swapStatic("naturalMouse", previousNaturalMouse);
		PointerState.reset();
		InputArbiter.resetForTest();
		while (BotEventGuard.isSynthetic())
		{
			BotEventGuard.end();
		}
	}

	@Test
	public void theMotionBoundsAreTheCanvasNotTheStretchedComponent()
	{
		Dimension bounds = new NaturalMouse().nature.getSystemCalls().getScreenSize();

		assertEquals(new Dimension(W, H), bounds);
	}

	@Test(timeout = 10_000)
	public void aTargetPastTheEdgeStopsAtTheEdgeInsteadOfLoopingOffCanvas() throws Exception
	{
		PointerState.setFromBot(W - 60, 300);

		new NaturalMouse().getFactory().build(W + 1, 300).move();

		Point at = PointerState.get();
		assertEquals(W - 1, at.getX());
		assertEquals(300, at.getY());
		assertFalse(PointerState.isOutside());
	}

	private static Object swapStatic(String name, Object value) throws Exception
	{
		Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, value);
		return previous;
	}
}
