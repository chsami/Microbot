package net.runelite.client.plugins.microbot.util.cache;

import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.cache.serialization.CacheSerializable;
import net.runelite.client.plugins.microbot.util.cache.util.LogOutputMode;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2CacheLoggingUtils;
import net.runelite.client.plugins.microbot.util.poh.data.*;

import java.lang.reflect.Type;
import java.util.*;

@Slf4j
public class Rs2PohCache extends Rs2Cache<String, List<PohTeleport>> implements CacheSerializable {

    public final static Type TYPE_TOKEN = new TypeToken<Map<String, List<PohTeleport>>>() {
    }.getType();
    public static final String POH_CACHE_KEY = "PohCache";

    private static Rs2PohCache instance;

    private Rs2PohCache() {
        super(POH_CACHE_KEY, CacheMode.MANUAL_ONLY);
        this.withPersistence(POH_CACHE_KEY);
    }

    public static synchronized Rs2PohCache getInstance() {
        if (instance == null) {
            instance = new Rs2PohCache();
        }
        return instance;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (Arrays.stream(NexusPortal.VARBITS).anyMatch(v -> v == event.getVarpId())) {
            setTeleports(NexusPortal.class, NexusPortal.getAvailableTeleports());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (!Rs2Cache.isInPOH() || event.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        //At this point teleport objects still aren't loaded
        //But we can update the nexus portal's teleports
        setTeleports(NexusPortal.class, NexusPortal.getAvailableTeleports());
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject go = event.getGameObject();
        PohPortal portal = PohPortal.getPohPortal(go);
        if (portal != null) {
            addTeleport(PohPortal.class, portal);
            return;
        }
        JewelleryBoxType jewelleryBoxType = JewelleryBoxType.getJewelleryBoxType(go);
        if (jewelleryBoxType != null) {
            setTeleports(JewelleryBox.class, jewelleryBoxType.getAvailableTeleports());
            return;
        }
        if (MountedDigsite.isMountedDigsite(go)) {
            setTeleports(MountedDigsite.class, List.of(MountedDigsite.values()));
        } else if (MountedGlory.isMountedGlory(go)) {
            setTeleports(MountedGlory.class, List.of(MountedGlory.values()));
        } else if (NexusPortal.isNexusPortal(go)) {
            setTeleports(NexusPortal.class, NexusPortal.getAvailableTeleports());
        }
    }

    private void addTeleport(Class<? extends Enum<? extends PohTeleport>> type, PohTeleport transport) {
        String key = type.getSimpleName();
        List<PohTeleport> transports = get(key, ArrayList::new);
        if (transports.contains(transport)) {
            return;
        }
        transports.add(transport);
        put(key, transports);
        log.info("Putting {} Teleport to {}", get(key).size(), key);
    }

    private void setTeleports(Class<? extends Enum<? extends PohTeleport>> type, List<? extends PohTeleport> transports) {
        String key = type.getSimpleName();
        put(key, Collections.unmodifiableList(transports));
        log.info("Putting {} Teleports to {}", get(key).size(), key);
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {

    }

    @Override
    public void update() {
        //Unused
    }

    @Override
    public String getConfigKey() {
        return POH_CACHE_KEY;
    }

    @Override
    public boolean shouldPersist() {
        return true;
    }

    public static boolean isTransportUsable(Transport transport) {
        return getTeleport(transport) != null;
    }

    public static PohTeleport getTeleport(Transport transport) {
        String key = transport.getEnumClass();
        String name = transport.getEnumValue();
        List<PohTeleport> obj = getInstance().get(key);
        if (obj == null) return null;

        PohTeleport teleport = obj.stream()
                .filter(pt -> pt.name().equals(name))
                .findFirst().orElse(null);

        return teleport;
    }


    public static void logState(LogOutputMode mode) {
        Rs2PohCache cache = getInstance();
        StringBuilder logContent = new StringBuilder();

        logContent.append("=== POH Cache States ===\n");
        logContent.append(String.format("%-20s %-8s %-50s\n",
                "Cache Key", "Count", "Available Teleports"));
        logContent.append("-".repeat(80)).append("\n");

        cache.keyStream().forEach(key -> {
            List<PohTeleport> tt = cache.get(key);
            logContent.append(String.format("%-20s %-8s \n",
                    key,
                    tt.size(),
                    tt.stream().map(PohTeleport::name).reduce("", (a, b) -> a + ", " + b)
            ));
        });


        // Summary statistics
        logContent.append("-".repeat(80)).append("\n");

        logContent.append(String.format("Total Teleports: %d \n",
                cache.keyStream().count()));

        logContent.append("=== End POH Cache States ===\n");

        Rs2CacheLoggingUtils.outputCacheLog(
                POH_CACHE_KEY,
                logContent.toString(),
                mode
        );
    }

}

