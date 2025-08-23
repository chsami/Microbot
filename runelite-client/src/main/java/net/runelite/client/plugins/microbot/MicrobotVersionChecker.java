package net.runelite.client.plugins.microbot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.ui.ClientUI;

@Slf4j
@Singleton
public class MicrobotVersionChecker
{
	private final AtomicBoolean newVersionAvailable = new AtomicBoolean(false);
	private final AtomicBoolean scheduled = new AtomicBoolean(false);
	private volatile ScheduledFuture<?> future;
	private final String REMOTE_VERSION_URL = "https://microbot.cloud/api/version/client";
	private static final String NEW_CLIENT_MARKER = "(NEW CLIENT AVAILABLE)";

	private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, MicrobotVersionChecker.class.getSimpleName());
		t.setDaemon(true);
		t.setUncaughtExceptionHandler((th, e) -> log.warn("Version checker thread error", e));
		return t;
	});

	private void runVersionCheck()
	{
		if (newVersionAvailable.get())
		{
			appendToTitle();
			return;
		}

		try
		{
			String remoteVersion = fetchRemoteVersion();
			String localVersion = RuneLiteProperties.getMicrobotVersion();
			String remote = remoteVersion == null ? null : remoteVersion.trim();
			String local = localVersion == null ? "" : localVersion.trim();
			if (remote != null && !remote.isEmpty() && !remote.equals(local))
			{
				newVersionAvailable.set(true);
				notifyNewVersionAvailable(remote, local);
			}
			else
			{
				log.debug("Microbshot client is up to date: {}", local);
			}
		}
		catch (Exception e)
		{
			log.warn("Could not check Microbot client version", e);
		}
	}

	private String fetchRemoteVersion() throws Exception
	{
		var url = new URL(REMOTE_VERSION_URL);
		var conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(5_000);
		conn.setReadTimeout(5_000);
		conn.setRequestMethod("GET");
		conn.setInstanceFollowRedirects(true);
		try (var reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)))
		{
			if (conn.getResponseCode() != 200)
			{
				log.debug("Version check responded with HTTP {}", conn.getResponseCode());
				return null;
			}
			String line = reader.readLine();
			return line != null ? line.trim() : null;
		}
		finally
		{
			conn.disconnect();
		}
	}

	private void notifyNewVersionAvailable(String remoteVersion, String localVersion)
	{
		appendToTitle();
		log.info("New Microbot client version available: {} (current: {})", remoteVersion, localVersion);
	}

	private void appendToTitle()
	{
		SwingUtilities.invokeLater(() -> {
			try
			{
				var frame = ClientUI.getFrame();
				if (frame == null)
				{
					return;
				}
				String oldTitle = String.valueOf(frame.getTitle());
				if (!oldTitle.contains(NEW_CLIENT_MARKER))
				{
					frame.setTitle(oldTitle + " " + NEW_CLIENT_MARKER);
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to update client title", e);
			}
		});
	}

	public void checkForUpdate()
	{
		if (scheduled.compareAndSet(false, true))
		{
			future = scheduledExecutorService.scheduleWithFixedDelay(this::runVersionCheck, 0, 10, TimeUnit.MINUTES);
		}
	}

	public void shutdown()
	{
		try
		{
			if (future != null)
			{
				future.cancel(true);
			}
		}
		finally
		{
			scheduledExecutorService.shutdownNow();
			newVersionAvailable.set(false);
			scheduled.set(false);
		}
	}
}
