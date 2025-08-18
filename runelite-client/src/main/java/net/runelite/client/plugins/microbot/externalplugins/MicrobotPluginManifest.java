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

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * Represents a plugin in the Microbot Plugin Hub
 */
@Data
public class MicrobotPluginManifest {
    /**
     * Unique identifier for the plugin
     */
    private String internalName;

    /**
     * Display name of the plugin
     */
    @SerializedName("name")
    private String displayName;

    /**
     * Plugin description
     */
    private String description;

    /**
     * Plugin authors (optional)
     */
    private String[] authors;

    /**
     * Plugin version
     */
    private String version;

    /**
     * Minimum client version required for this plugin
     */
    private String minClientVersion;

    /**
     * SHA256 hash of the plugin JAR
     */
    private String sha256;

    /**
     * URL to download the plugin JAR
     */
    private String url;

    /**
     * URL to the plugin's icon (optional)
     */
    private String iconUrl;

    /**
     * URL to the plugin's card image (optional)
     */
    private String cardUrl;

	/**
	 * Flag indicating the plugin is disabled. (optional)
	 * This is used for plugins that are no longer functional or have been deprecated.
	 */
	private boolean disable;

	/**
     * Tags for the plugin (optional)
     */
    private String[] tags;

    /**
     * Gets a warning message for this plugin, if any
     */
    public String getWarning() {
        return null; // No warnings for now, could be added in future
    }

    /**
     * Gets a reason why this plugin is unavailable, if applicable
     */
    public String getUnavailableReason() {
        return null; // No unavailability reason for now
    }

    /**
     * Checks if this plugin has an icon
     */
    public boolean hasIcon() {
        return iconUrl != null && !iconUrl.isEmpty();
    }

    /**
     * Gets the author(s) as a single string.
     */
    public String getAuthor() {
        if (authors == null || authors.length == 0) {
            return "Unknown";
        }
        if (authors.length == 1) {
            return authors[0];
        }
        return String.join(", ", authors);
    }
}
