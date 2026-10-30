package net.runelite.client.plugins.microbot.questhelper.training;

import java.util.List;

/** Trains one skill in small steps; the session calls {@link #tick} every game tick until the target level. */
public interface SkillTrainer
{
	/** Skill enum name, upper case (FIREMAKING). */
	String skill();

	String name();

	/** Lowest level this trainer works at. */
	default int minLevel()
	{
		return 1;
	}

	/** What it carries at this level (for the cost estimate and the approval). */
	List<Supply> supplies(int level);

	/** Called when a session starts or ends. */
	default void reset()
	{
	}

	/** One step of work. */
	Outcome tick(TrainerContext context);

	/** An item the trainer needs: name, an id for pricing, and how many per bank trip. */
	final class Supply
	{
		public final String name;
		public final int id;
		public final int quantity;

		public Supply(String name, int id, int quantity)
		{
			this.name = name;
			this.id = id;
			this.quantity = quantity;
		}
	}

	/** What a tick achieved. */
	final class Outcome
	{
		public final boolean failed;
		public final String message;

		private Outcome(boolean failed, String message)
		{
			this.failed = failed;
			this.message = message == null ? "" : message;
		}

		public static Outcome working()
		{
			return new Outcome(false, "");
		}

		public static Outcome failed(String message)
		{
			return new Outcome(true, message);
		}
	}
}
