/*
 * Copyright (c) 2018 Abex
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
package net.runelite.client.callback;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Singleton;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

@Singleton
@Slf4j
public class ClientThread
{
	private final ConcurrentLinkedQueue<BooleanSupplier> invokes = new ConcurrentLinkedQueue<>();
	private final ConcurrentLinkedQueue<BooleanSupplier> invokesAtTickEnd = new ConcurrentLinkedQueue<>();

	protected ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
	public Future<?> scheduledFuture;

	@Inject
	private Client client;

	public void invoke(Runnable r)
	{
		invoke(() ->
		{
			r.run();
			return true;
		});
	}

	/**
	 * Run a method on the client thread, returning the result.
	 * @param method
	 * @return
	 * @param <T>
	 */
	@SneakyThrows
	@Deprecated(since = "1.7.9", forRemoval = true)
	public <T> T runOnClientThread(Callable<T> method) {
		if (Microbot.getClient().isClientThread()) {
			return method.call();
		}
		final FutureTask<T> task = new FutureTask<>(method);
		invoke(task);
		try {
			return task.get(10000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException | TimeoutException | ExecutionException e) {
			if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
				return null;
            }
			if (!Microbot.isDebug()) {
				Microbot.log("Exception during task execution: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			}
			return null;
		}
	}

	/**
	 * Run a method on the client thread, returning an optional of the result.
	 * @param method
	 * @return
	 * @param <T>
	 */
	@SneakyThrows
	public <T> Optional<T> runOnClientThreadOptional(Callable<T> method) {
		if (Microbot.getClient().isClientThread()) {
			try {
				return Optional.ofNullable(method.call());
			} catch (Exception e) {
				if (!Microbot.isDebug()) {
					Microbot.log("Exception in client thread execution: " + e.getMessage());
				}
				return Optional.empty();
			}
		}
		final FutureTask<T> task = new FutureTask<>(method);
		invoke(task);
		try {
			return Optional.ofNullable(task.get(10000, TimeUnit.MILLISECONDS));
		} catch (InterruptedException | TimeoutException | ExecutionException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
				return Optional.empty();
			}
			if (!Microbot.isDebug()) {
				Microbot.log("Exception during task execution: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			}
			return Optional.empty();
		}
	}

	/**
	 * Run a method on the client thread, returning the result.
	 * @param method
	 */
	@SneakyThrows
	public void runOnSeperateThread(Callable method) {
		if (scheduledFuture != null && !scheduledFuture.isDone()) return;
		scheduledFuture = scheduledExecutorService.submit(() -> method.call());
	}


	/**
	 * Will run r on the game thread, at an unspecified point in the future.
	 * If r returns false, r will be ran again, at a later point
	 */
	public void invoke(BooleanSupplier r)
	{
		if (client.isClientThread())
		{
			if (!r.getAsBoolean())
			{
				invokes.add(r);
			}
			return;
		}

		invokeLater(r);
	}

	/**
	 * Will run r on the game thread after this method returns
	 * If r returns false, r will be ran again, at a later point
	 */
	public void invokeLater(Runnable r)
	{
		invokeLater(() ->
		{
			r.run();
			return true;
		});
	}

	public void invokeLater(BooleanSupplier r)
	{
		invokes.add(r);
	}

	public void invokeAtTickEnd(Runnable r)
	{
		invokesAtTickEnd.add(() ->
		{
			r.run();
			return true;
		});
	}

	void invoke()
	{
		invokeList(invokes);
	}

	void invokeTickEnd()
	{
		invokeList(invokesAtTickEnd);
	}

	private void invokeList(ConcurrentLinkedQueue<BooleanSupplier> invokes)
	{
		assert client.isClientThread();
		Iterator<BooleanSupplier> ir = invokes.iterator();
		while (ir.hasNext())
		{
			BooleanSupplier r = ir.next();
			boolean remove = true;
			try
			{
				remove = r.getAsBoolean();
			}
			catch (ThreadDeath d)
			{
				throw d;
			}
			catch (Throwable e)
			{
				log.error("Exception in invoke", e);
			}
			if (remove)
			{
				ir.remove();
			}
			else
			{
				log.trace("Deferring task {}", r);
			}
		}
	}
}
