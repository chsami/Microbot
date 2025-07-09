/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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
package net.runelite.client;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.util.RunnableExceptionLogger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class ClientSessionManager
{
	private final ScheduledExecutorService executorService;
	private final Client client;
	private final SessionClient sessionClient;
	private final boolean disableTelemetry;

	private ScheduledFuture<?> scheduledFuture;
	private ScheduledFuture<?> scheduledFutureMicroBot;

	private UUID sessionId;
	private UUID microbotSessionId;
	private MicrobotApi microbotApi;

	@Inject
	ClientSessionManager(ScheduledExecutorService executorService,
		Client client,
		SessionClient sessionClient, MicrobotApi microbotApi,
        @Named("disableTelemetry") boolean disableTelemetry)
	{
		this.executorService = executorService;
		this.client = client;
		this.sessionClient = sessionClient;
		this.microbotApi = microbotApi;
		this.disableTelemetry = disableTelemetry;
	}

	public void start()
	{
        if (disableTelemetry) {
            log.info("Telemetry is disabled. ClientSessionManager will not start.");
            return;
        }
		executorService.execute(() ->
		{
			try
			{
				sessionId = sessionClient.open();
				microbotSessionId = microbotApi.microbotOpen();
				log.debug("Opened session {}", sessionId);
			}
			catch (IOException ex)
			{
				log.warn("error opening session", ex);
			}
		});

		scheduledFuture = executorService.scheduleWithFixedDelay(
				RunnableExceptionLogger.wrap(this::ping), 1, 10, TimeUnit.MINUTES);
		scheduledFutureMicroBot = executorService.scheduleWithFixedDelay(
				RunnableExceptionLogger.wrap(this::microbotPing), 1, 10, TimeUnit.MINUTES);
	}

	@Subscribe
	private void onClientShutdown(ClientShutdown e)
	{
		if (disableTelemetry) return;
		scheduledFuture.cancel(true);
		scheduledFutureMicroBot.cancel(true);
		e.waitFor(executorService.submit(() ->
		{
			try
			{
				UUID localUuid = sessionId;
				if (localUuid != null)
				{
					sessionClient.delete(localUuid);
				}
				UUID localMicrobotUuid = microbotSessionId;
				if (localMicrobotUuid != null)
				{
					microbotApi.microbotDelete(localMicrobotUuid);
				}
			}
			catch (IOException ex)
			{
				log.warn(null, ex);
			}
			sessionId = null;
		}));
	}

	private void ping()
	{
		if (!isWorldHostValid())
		{
			return;
		}

		try
		{
			if (sessionId == null)
			{
				sessionId = sessionClient.open();
				log.debug("Opened session {}", sessionId);
				return;
			}
		}
		catch (IOException ex)
		{
			log.warn("unable to open session", ex);
			return;
		}

		GameState gameState = client.getGameState();
		boolean loggedIn = gameState.getState() >= GameState.LOADING.getState();

		try
		{
			sessionClient.ping(sessionId, loggedIn);
		}
		catch (IOException ex)
		{
			log.warn("Resetting session", ex);
			sessionId = null;
		}
	}

	private void microbotPing()
	{
		try
		{
			if (microbotSessionId == null) {
				microbotSessionId = microbotApi.microbotOpen();
				return;
			}
		}
		catch (IOException ex)
		{
			log.warn("unable to open session", ex);
			return;
		}

		boolean loggedIn = false;
		if (client != null)
		{
			GameState gameState = client.getGameState();
			loggedIn = gameState.getState() >= GameState.LOADING.getState();
		}

		try
		{
			microbotApi.microbotPing(microbotSessionId, loggedIn);
		}
		catch (IOException ex)
		{
			log.warn("Resetting session", ex);
			sessionId = null;
		}

	}

	private boolean isWorldHostValid()
	{
		String host = client.getWorldHost();
		return host != null && host.endsWith(".runescape.com");
	}
}
