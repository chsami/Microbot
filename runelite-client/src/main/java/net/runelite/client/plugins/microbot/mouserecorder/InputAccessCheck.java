package net.runelite.client.plugins.microbot.mouserecorder;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
final class InputAccessCheck
{
	static final long RECHECK_MS = 6 * 60 * 60_000L;
	static final long RETRY_MS = 5 * 60_000L;

	private final OkHttpClient http;
	private final Supplier<Optional<String>> token;
	private final Consumer<Boolean> onDecision;
	private final String url;
	private final AtomicBoolean inFlight = new AtomicBoolean();
	private volatile String checkedToken;
	private volatile long nextCheckMs;

	InputAccessCheck(OkHttpClient http, Supplier<Optional<String>> token, Consumer<Boolean> onDecision, String apiUrl)
	{
		this.http = http;
		this.token = token;
		this.onDecision = onDecision;
		String base = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
		this.url = base + "/input/access";
	}

	static Boolean decide(int code)
	{
		if (code == 200 || code == 204)
		{
			return Boolean.TRUE;
		}
		if (code == 403)
		{
			return Boolean.FALSE;
		}
		return null;
	}

	void recheckSoon()
	{
		nextCheckMs = 0;
	}

	void poll()
	{
		Optional<String> value = token.get();
		if (value.isEmpty())
		{
			checkedToken = null;
			return;
		}
		long now = System.currentTimeMillis();
		if (value.get().equals(checkedToken) && now < nextCheckMs || !inFlight.compareAndSet(false, true))
		{
			return;
		}
		String sent = value.get();
		Request request = new Request.Builder().url(url).header(InputUploader.TOKEN_HEADER, sent).get().build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Input recorder access check failed: {}", e.getMessage());
				finish(sent, null);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					finish(sent, decide(response.code()));
				}
			}
		});
	}

	private void finish(String sent, Boolean allowed)
	{
		checkedToken = sent;
		nextCheckMs = System.currentTimeMillis() + (allowed == null ? RETRY_MS : RECHECK_MS);
		inFlight.set(false);
		if (allowed != null)
		{
			onDecision.accept(allowed);
		}
	}
}
