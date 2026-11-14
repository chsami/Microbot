package net.runelite.client.plugins.microbot.questhelper;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.questhelper.steps.ObjectStep;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QuestScriptStaleStepObjectTest
{
	private static final WorldPoint LEDGE = new WorldPoint(2374, 9644, 0);
	private static final WorldPoint SHIFTED_LEDGE = new WorldPoint(2334, 9636, 0);

	@Test
	public void objectStillHeldByTheStepKeepsItsLocation()
	{
		WallObject ledge = mock(WallObject.class);
		when(ledge.getWorldLocation()).thenReturn(LEDGE);
		ObjectStep step = stepHolding(ledge);

		assertEquals(LEDGE, QuestScript.currentStepObjectLocation(step, new Rs2TileObjectModel(ledge)));
	}

	@Test
	public void objectDroppedByASceneReloadHasNoLocation()
	{
		WallObject staleLedge = mock(WallObject.class);
		when(staleLedge.getWorldLocation()).thenReturn(SHIFTED_LEDGE);
		ObjectStep step = stepHolding();

		assertNull(QuestScript.currentStepObjectLocation(step, new Rs2TileObjectModel(staleLedge)));
	}

	@Test
	public void objectReplacedAfterASceneReloadHasNoLocation()
	{
		WallObject staleLedge = mock(WallObject.class);
		when(staleLedge.getWorldLocation()).thenReturn(SHIFTED_LEDGE);
		WallObject reloadedLedge = mock(WallObject.class);
		when(reloadedLedge.getWorldLocation()).thenReturn(LEDGE);
		ObjectStep step = stepHolding(reloadedLedge);

		assertNull(QuestScript.currentStepObjectLocation(step, new Rs2TileObjectModel(staleLedge)));
		assertEquals(LEDGE, QuestScript.currentStepObjectLocation(step, new Rs2TileObjectModel(reloadedLedge)));
	}

	private static ObjectStep stepHolding(TileObject... objects)
	{
		List<TileObject> held = new ArrayList<>(List.of(objects));
		ObjectStep step = mock(ObjectStep.class);
		when(step.getObjects()).thenReturn(held);
		return step;
	}
}
