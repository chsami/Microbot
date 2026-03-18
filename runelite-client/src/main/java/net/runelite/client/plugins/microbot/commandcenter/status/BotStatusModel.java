package net.runelite.client.plugins.microbot.commandcenter.status;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the JSON status response by reading game state from the RuneLite Client API.
 * Thread-safe: reads are done on the calling thread (HttpServer executor).
 * All fields are additive-only — new fields may appear, existing fields are never removed.
 */
@Slf4j
public class BotStatusModel {
    private static final Gson GSON = new GsonBuilder().create();
    private static final int SCHEMA_VERSION = 1;

    private final long startTimeMs = System.currentTimeMillis();

    // Injected by MicrobotPlugin when a script starts/stops
    private volatile String activeScriptName;
    private volatile boolean scriptRunning;
    private volatile long scriptStartTimeMs;

    // Injected at construction
    private final int characterId;
    private final String characterName;

    public BotStatusModel(int characterId, String characterName) {
        this.characterId = characterId;
        this.characterName = characterName;
    }

    public void setActiveScript(String name, boolean running) {
        this.activeScriptName = name;
        this.scriptRunning = running;
        if (running) {
            this.scriptStartTimeMs = System.currentTimeMillis();
        }
    }

    public String toJson() {
        Map<String, Object> root = new HashMap<>();
        root.put("version", SCHEMA_VERSION);
        root.put("characterId", characterId);
        root.put("characterName", characterName);

        Client client = Microbot.getClient();
        boolean loggedIn = client != null &&
            client.getGameState() == GameState.LOGGED_IN;

        root.put("loggedIn", loggedIn);
        root.put("status", loggedIn ? (scriptRunning ? "running" : "idle") : "login_screen");

        // Script info
        Map<String, Object> script = new HashMap<>();
        script.put("name", activeScriptName);
        script.put("running", scriptRunning);
        script.put("runtime", scriptRunning
            ? (int) ((System.currentTimeMillis() - scriptStartTimeMs) / 1000)
            : 0);
        root.put("script", script);

        // Player info (safe even when not logged in — returns defaults)
        Map<String, Object> player = new HashMap<>();
        if (loggedIn && client != null) {
            player.put("world", client.getWorld());
            // Location
            var localPlayer = client.getLocalPlayer();
            if (localPlayer != null) {
                var pos = localPlayer.getWorldLocation();
                Map<String, Integer> location = new HashMap<>();
                location.put("x", pos.getX());
                location.put("y", pos.getY());
                player.put("location", location);
            }
            player.put("hitpoints", client.getBoostedSkillLevel(Skill.HITPOINTS));
            player.put("prayer", client.getBoostedSkillLevel(Skill.PRAYER));
            player.put("runEnergy", client.getEnergy() / 100); // RuneLite returns 0-10000
        }
        root.put("player", player);

        // XP info
        Map<String, Object> xp = new HashMap<>();
        // XP tracking requires baseline storage — simplified for now
        xp.put("totalGained", 0);
        xp.put("perHour", 0);
        xp.put("skills", new HashMap<>());
        root.put("xp", xp);

        root.put("uptime", (int) ((System.currentTimeMillis() - startTimeMs) / 1000));

        return GSON.toJson(root);
    }
}
