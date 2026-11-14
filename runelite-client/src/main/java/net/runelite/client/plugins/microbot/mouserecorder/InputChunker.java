package net.runelite.client.plugins.microbot.mouserecorder;

import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

final class InputChunker
{
	static final long CHUNK_MS = 30_000;
	static final int MAX_EVENTS = 20_000;
	static final int MAX_RAW_BYTES = 2 * 1024 * 1024;

	private final StringBuilder lines = new StringBuilder();
	private int events;
	private long firstMs = -1;

	synchronized void add(JsonObject event, long ms)
	{
		if (firstMs < 0)
		{
			firstMs = ms;
		}
		lines.append(event).append('\n');
		events++;
	}

	synchronized boolean isDue(long nowMs)
	{
		return events > 0 && (nowMs - firstMs >= CHUNK_MS || events >= MAX_EVENTS || lines.length() >= MAX_RAW_BYTES);
	}

	synchronized String drain()
	{
		if (events == 0)
		{
			return null;
		}
		String text = lines.toString();
		lines.setLength(0);
		events = 0;
		firstMs = -1;
		return text;
	}

	synchronized int size()
	{
		return events;
	}

	static byte[] gzip(String text)
	{
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(buffer))
		{
			out.write(text.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
		return buffer.toByteArray();
	}
}
