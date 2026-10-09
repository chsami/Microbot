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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Singleton
@Slf4j
public class ClientThread
{
	private final ConcurrentLinkedQueue<BooleanSupplier> invokes = new ConcurrentLinkedQueue<>();
	private final ConcurrentLinkedQueue<BooleanSupplier> invokesAtTickEnd = new ConcurrentLinkedQueue<>();

	protected ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
	public Future<?> scheduledFuture;

	private static final long TIMEOUT_WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);
	private final AtomicLong lastTimeoutWarningNanos = new AtomicLong();
	long clientThreadTimeoutMillis = 10000;

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

	public boolean isClientThread()
	{
		return client.isClientThread();
	}

	/**
	 * Run a method on the client thread, returning an optional of the result.
	 * @param method
	 * @return
	 * @param <T>
	 */
	@SneakyThrows
	public <T> Optional<T> runOnClientThreadOptional(Callable<T> method) {
		if (client.isClientThread()) {
			try {
				return Optional.ofNullable(method.call());
			} catch (Exception e) {
				if (!Microbot.isDebug()) {
					log.error("Exception in client thread execution: {}\n{}", e.getMessage(),e);
				}
				return Optional.empty();
			}
		}
		final FutureTask<T> task = new FutureTask<>(method);
		invoke(task);
		try {
			return Optional.ofNullable(task.get(clientThreadTimeoutMillis, TimeUnit.MILLISECONDS));
		} catch (InterruptedException | TimeoutException | ExecutionException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
				return Optional.empty();
			}			
			task.cancel(true);
			if (e instanceof TimeoutException) {
				logClientThreadTimeout();
				return Optional.empty();
			}
			if (!Microbot.isDebug()) {
				log.error("Exception during task execution: {}: {}\n{}", e.getClass().getSimpleName(), e.getMessage(),e);
			}
			return Optional.empty();
		}
	}

	private void logClientThreadTimeout() {
		long now = System.nanoTime();
		long last = lastTimeoutWarningNanos.get();
		if ((last == 0 || now - last >= TIMEOUT_WARNING_INTERVAL_NANOS) && lastTimeoutWarningNanos.compareAndSet(last, now)) {
			log.warn("Client thread did not run task within {}ms (caller thread {}); further timeouts suppressed for {}s",
				clientThreadTimeoutMillis, Thread.currentThread().getName(), TimeUnit.NANOSECONDS.toSeconds(TIMEOUT_WARNING_INTERVAL_NANOS));
		} else {
			log.debug("Client thread did not run task within {}ms (caller thread {})", clientThreadTimeoutMillis, Thread.currentThread().getName());
		}
	}

	/**
	 * Run a method on the client thread, returning the result.
	 * @param method
	 */
	@SneakyThrows
	public void runOnSeperateThread(Callable<?> method) {
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

    public <T> T invoke(Supplier<T> supplier) {
        if (client.isClientThread()) {
            return supplier.get();
        }

        CompletableFuture<T> f = new CompletableFuture<>();
        invoke(() -> {
            try {
                f.complete(supplier.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });

        try {
            return f.get(10, TimeUnit.SECONDS); // configurable timeout
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for client thread", ie);
        } catch (TimeoutException te) {
            throw new RuntimeException("Timed out waiting for client thread", te);
        } catch (ExecutionException ee) {
            // unwrap original cause
            Throwable cause = ee.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        }
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
