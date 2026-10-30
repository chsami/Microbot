package net.runelite.client.plugins.microbot.questhelper;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * A menu click that was sent is not an interaction that happened: the server may ignore it (the NPC or object is out of
 * the camera's reach, a door is in the way) and the click still reports success. This sends the click, waits for a sign
 * that it took effect (movement, an animation, a dialogue, a floor change, the player interacting), and when there is none
 * adjusts the camera and clicks once more.
 */
public final class VerifiedInteraction
{
	/** How long to wait for a sign of effect after each click. */
	public static final long EFFECT_WAIT_MS = 1_200L;
	private static final long POLL_MS = 100L;

	private VerifiedInteraction()
	{
	}

	/**
	 * @param click         sends the click; returns false when it could not be sent
	 * @param tookEffect    whether the game now shows a sign of the interaction
	 * @param adjustCamera  rotates or zooms the camera before the second attempt
	 * @param clock         milliseconds
	 * @param sleeper       sleeps the given milliseconds
	 * @return true when an attempt had an effect
	 */
	public static boolean run(BooleanSupplier click, BooleanSupplier tookEffect, Runnable adjustCamera,
		LongSupplier clock, LongConsumer sleeper)
	{
		for (int attempt = 0; attempt < 2; attempt++)
		{
			if (attempt == 1)
			{
				adjustCamera.run();
			}
			if (click.getAsBoolean() && awaitEffect(tookEffect, clock, sleeper))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean awaitEffect(BooleanSupplier tookEffect, LongSupplier clock, LongConsumer sleeper)
	{
		long until = clock.getAsLong() + EFFECT_WAIT_MS;
		while (true)
		{
			if (tookEffect.getAsBoolean())
			{
				return true;
			}
			if (clock.getAsLong() >= until)
			{
				return false;
			}
			sleeper.accept(POLL_MS);
		}
	}
}
