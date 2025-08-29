/*
 * Copyright (c) 2023 Microbot
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
package net.runelite.client.plugins.microbot.externalplugins;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
public class MicrobotPluginClient
{
    private static final HttpUrl MICROBOT_PLUGIN_HUB_URL = HttpUrl.parse("https://chsami.github.io/Microbot-Hub/");
    private static final String PLUGINS_JSON_PATH = "plugins.json";
    
    private final OkHttpClient okHttpClient;
    private final Gson gson;

    @Inject
    private MicrobotPluginClient(OkHttpClient okHttpClient, Gson gson)
    {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
    }

    /**
     * Downloads the plugin manifest from the Microbot Hub
     */
    public List<MicrobotPluginManifest> downloadManifest() throws IOException
    {
        HttpUrl manifestUrl = MICROBOT_PLUGIN_HUB_URL.newBuilder()
            .addPathSegment(PLUGINS_JSON_PATH)
            .build();

        Request request = new Request.Builder()
            .url(manifestUrl)
            .header("Cache-Control", "no-cache")
            .build();

        try (Response res = okHttpClient.newCall(request).execute())
        {
            if (res.code() != 200)
            {
                throw new IOException("Non-OK response code: " + res.code());
            }

            return gson.fromJson(res.body().string(), 
                new TypeToken<List<MicrobotPluginManifest>>(){}.getType());
        }
        catch (JsonSyntaxException ex)
        {
            throw new IOException("Failed to parse plugin manifest", ex);
        }
    }

    /**
     * Downloads plugin icon from the Microbot Hub
     */
    public BufferedImage downloadIcon(String iconUrl) throws IOException
    {
        HttpUrl url = HttpUrl.parse(iconUrl);
        if (url == null)
        {
            return null;
        }

        try (Response res = okHttpClient.newCall(new Request.Builder().url(url).build()).execute())
        {
            byte[] bytes = res.body().bytes();
            // We don't stream so the lock doesn't block the edt trying to load something at the same time
            synchronized (ImageIO.class)
            {
                return ImageIO.read(new ByteArrayInputStream(bytes));
            }
        }
    }

    /**
     * Returns the URL for downloading a plugin JAR
     */
    public HttpUrl getJarURL(MicrobotPluginManifest manifest)
    {
        return HttpUrl.parse(manifest.getUrl());
    }

    /**
     * Gets download counts for plugins
     */
    public Map<String, Integer> getPluginCounts() throws IOException
    {
        // This would need to be implemented if your backend supports tracking download counts
        // For now, we'll return an empty map
        return Map.of();
    }
}
