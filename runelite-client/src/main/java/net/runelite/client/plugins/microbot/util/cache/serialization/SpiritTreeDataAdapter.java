/*
 * Copyright (c) 2024 Microbot
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
package net.runelite.client.plugins.microbot.util.cache.serialization;

import com.google.gson.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.util.cache.model.SpiritTreeData;
import net.runelite.client.plugins.microbot.util.farming.SpiritTree;

import java.lang.reflect.Type;

/**
 * Gson adapter for SpiritTreeData serialization/deserialization.
 * Handles safe serialization of spirit tree cache data for persistent storage.
 */
public class SpiritTreeDataAdapter implements JsonSerializer<SpiritTreeData>, JsonDeserializer<SpiritTreeData> {
    
    @Override
    public JsonElement serialize(SpiritTreeData src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        try {
            // Store patch as enum name for safe serialization
            json.addProperty("patch", src.getPatch().name());
            
            // Store crop state as enum name (nullable)
            if (src.getCropState() != null) {
                json.addProperty("cropState", src.getCropState().name());
            }
            
            json.addProperty("availableForTravel", src.isAvailableForTravel());
            json.addProperty("lastUpdated", src.getLastUpdated());
            
            // Store player location
            if (src.getPlayerLocation() != null) {
                JsonObject location = new JsonObject();
                location.addProperty("x", src.getPlayerLocation().getX());
                location.addProperty("y", src.getPlayerLocation().getY());
                location.addProperty("plane", src.getPlayerLocation().getPlane());
                json.add("playerLocation", location);
            }
            
            // Store detection method flags
            json.addProperty("detectedViaWidget", src.isDetectedViaWidget());
            json.addProperty("detectedViaGameObject", src.isDetectedViaGameObject());
            
            // Store farming level (nullable)
            if (src.getFarmingLevel() != null) {
                json.addProperty("farmingLevel", src.getFarmingLevel());
            }
            
            // Store nearby entity IDs (optional, for debugging purposes only)
            // Note: We don't serialize these as they're not persistent across sessions
            
        } catch (Exception e) {
            // Create minimal fallback serialization
            json.addProperty("patch", src.getPatch().name());
            json.addProperty("availableForTravel", src.isAvailableForTravel());
            json.addProperty("lastUpdated", src.getLastUpdated());
        }
        
        return json;
    }
    
    @Override
    public SpiritTreeData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        
        JsonObject jsonObject = json.getAsJsonObject();
        
        try {
            // Required fields
            String patchName = jsonObject.get("patch").getAsString();
            SpiritTree patch = SpiritTree.valueOf(patchName);
            
            boolean availableForTravel = jsonObject.get("availableForTravel").getAsBoolean();
            long lastUpdated = jsonObject.get("lastUpdated").getAsLong();
            
            // Optional fields
            CropState cropState = null;
            if (jsonObject.has("cropState") && !jsonObject.get("cropState").isJsonNull()) {
                cropState = CropState.valueOf(jsonObject.get("cropState").getAsString());
            }
            
            WorldPoint playerLocation = null;
            if (jsonObject.has("playerLocation") && !jsonObject.get("playerLocation").isJsonNull()) {
                JsonObject location = jsonObject.getAsJsonObject("playerLocation");
                playerLocation = new WorldPoint(
                    location.get("x").getAsInt(),
                    location.get("y").getAsInt(),
                    location.get("plane").getAsInt()
                );
            }
            
            boolean detectedViaWidget = jsonObject.has("detectedViaWidget") ? 
                jsonObject.get("detectedViaWidget").getAsBoolean() : false;
            boolean detectedViaGameObject = jsonObject.has("detectedViaGameObject") ? 
                jsonObject.get("detectedViaGameObject").getAsBoolean() : false;
            
            Integer farmingLevel = null;
            if (jsonObject.has("farmingLevel") && !jsonObject.get("farmingLevel").isJsonNull()) {
                farmingLevel = jsonObject.get("farmingLevel").getAsInt();
            }
            
            // Create SpiritTreeData with preserved timestamp and available fields
            return new SpiritTreeData(
                patch,
                cropState,
                availableForTravel,
                lastUpdated,
                playerLocation,
                detectedViaWidget,
                detectedViaGameObject,
                farmingLevel
            );
            
        } catch (Exception e) {
            throw new JsonParseException("Failed to deserialize SpiritTreeData: " + e.getMessage(), e);
        }
    }
}
