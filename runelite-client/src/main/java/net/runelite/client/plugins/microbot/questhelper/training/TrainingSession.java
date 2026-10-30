package net.runelite.client.plugins.microbot.questhelper.training;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * Runs one training step: ticks the trainer until the skill reaches the target, then reports REACHED. Gives up when
 * the trainer fails or the time limit passes. Pure over its suppliers so it can be tested with fakes.
 */
public final class TrainingSession
{
	public enum State
	{
		RUNNING, REACHED, FAILED
	}

	private final String skill;
	private final int target;
	private final SkillTrainer trainer;
	private final IntSupplier level;
	private final LongSupplier clock;
	private final long maxMs;
	private final TrainerContext context;
	private final long startedMs;
	private State state = State.RUNNING;
	private String message = "";

	public TrainingSession(String skill, int target, SkillTrainer trainer, IntSupplier level, LongSupplier clock,
		long maxMs, TrainerContext context)
	{
		this.skill = skill;
		this.target = target;
		this.trainer = trainer;
		this.level = level;
		this.clock = clock;
		this.maxMs = maxMs;
		this.context = context;
		this.startedMs = clock.getAsLong();
		trainer.reset();
	}

	public State tick()
	{
		if (state != State.RUNNING)
		{
			return state;
		}
		if (level.getAsInt() >= target)
		{
			state = State.REACHED;
			trainer.reset();
			return state;
		}
		if (clock.getAsLong() - startedMs > maxMs)
		{
			return fail(skill + " training timed out before level " + target);
		}
		SkillTrainer.Outcome outcome = trainer.tick(context);
		if (outcome.failed)
		{
			return fail(outcome.message);
		}
		if (level.getAsInt() >= target)
		{
			state = State.REACHED;
			trainer.reset();
		}
		return state;
	}

	private State fail(String why)
	{
		state = State.FAILED;
		message = why;
		trainer.reset();
		return state;
	}

	/** Stops the trainer (and any Hub plugin it started) without reaching the target. */
	public void abort()
	{
		if (state == State.RUNNING)
		{
			state = State.FAILED;
			message = "stopped";
			trainer.reset();
		}
	}

	public String message()
	{
		return message;
	}

	public String skill()
	{
		return skill;
	}

	public int target()
	{
		return target;
	}
}
