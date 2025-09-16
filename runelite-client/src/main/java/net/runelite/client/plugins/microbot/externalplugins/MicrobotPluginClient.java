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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@Slf4j
public class MicrobotPluginClient
{
    private static final HttpUrl MICROBOT_PLUGIN_HUB_URL = HttpUrl.parse("https://chsami.github.io/Microbot-Hub/");
    private static final String PLUGINS_JSON_PATH = "plugins.json";
    private static final String LOCAL_PLUGIN_FOLDER = System.getProperty("microbot.local.plugins", null);
    
    private final OkHttpClient okHttpClient;
    private final Gson gson;

    @Inject
    private MicrobotPluginClient(OkHttpClient okHttpClient, Gson gson)
    {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
    }

    /**
     * Downloads the plugin manifest from the Microbot Hub or loads from local folder
     */
    public List<MicrobotPluginManifest> downloadManifest() throws IOException
    {
        // Check if we should load from local folder
        if (LOCAL_PLUGIN_FOLDER != null && !LOCAL_PLUGIN_FOLDER.isEmpty())
        {
            return loadLocalManifest();
        }

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
     * Loads plugin manifest from local folder
     */
    private List<MicrobotPluginManifest> loadLocalManifest() throws IOException
    {
        File manifestFile = new File(LOCAL_PLUGIN_FOLDER, PLUGINS_JSON_PATH);
        
        if (!manifestFile.exists())
        {
            throw new IOException("Local manifest file not found: " + manifestFile.getAbsolutePath());
        }

        try
        {
            String manifestContent = Files.readString(manifestFile.toPath());
            return gson.fromJson(manifestContent, new TypeToken<List<MicrobotPluginManifest>>(){}.getType());
        }
        catch (JsonSyntaxException ex)
        {
            throw new IOException("Failed to parse local plugin manifest", ex);
        }
    }

    /**
     * Downloads plugin icon from the Microbot Hub or loads from local folder
     */
    public BufferedImage downloadIcon(String iconUrl) throws IOException
    {
        // If using local plugins, try to load icon from local folder first
        if (LOCAL_PLUGIN_FOLDER != null && !LOCAL_PLUGIN_FOLDER.isEmpty())
        {
            BufferedImage localIcon = loadLocalIcon(iconUrl);
            if (localIcon != null)
            {
                return localIcon;
            }
        }

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
     * Loads plugin icon from local folder
     */
    private BufferedImage loadLocalIcon(String iconUrl) throws IOException
    {
        try
        {
            // Extract filename from URL
            String filename = iconUrl.substring(iconUrl.lastIndexOf('/') + 1);
            File iconFile = new File(LOCAL_PLUGIN_FOLDER, "icons/" + filename);
            
            if (!iconFile.exists())
            {
                // Try without icons subfolder
                iconFile = new File(LOCAL_PLUGIN_FOLDER, filename);
            }
            
            if (iconFile.exists())
            {
                synchronized (ImageIO.class)
                {
                    return ImageIO.read(iconFile);
                }
            }
        }
        catch (Exception e)
        {
            log.debug("Failed to load local icon: {}", iconUrl, e);
        }
        
        return null;
    }

    /**
     * Returns the URL for downloading a plugin JAR
     */
    public HttpUrl getJarURL(MicrobotPluginManifest manifest)
    {
        // If using local plugins, construct file path
        if (LOCAL_PLUGIN_FOLDER != null && !LOCAL_PLUGIN_FOLDER.isEmpty())
        {
            File jarFile = new File(LOCAL_PLUGIN_FOLDER, manifest.getInternalName() + ".jar");
            if (jarFile.exists())
            {
                return HttpUrl.parse(jarFile.toURI().toString());
            }
        }
        
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
