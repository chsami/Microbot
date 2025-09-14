package net.runelite.client.plugins.microbot.util.cache;

import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.cache.serialization.CacheSerializable;
import net.runelite.client.plugins.microbot.util.cache.util.LogOutputMode;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2CacheLoggingUtils;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.poh.data.*;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.shortestpath.TransportType.FAIRY_RING;

@Slf4j
public class Rs2PohCache extends Rs2Cache<String, List<PohTeleport>> implements CacheSerializable {

    public final static Type TYPE_TOKEN = new TypeToken<Map<String, List<PohTeleport>>>() {
    }.getType();
    public static final String POH_CACHE_KEY = "PohCache";
    public static final String POH_FAIRY_RINGS = "fairyRings";

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
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event) {
        DecorativeObject go = event.getDecorativeObject();
        if (go == null) return;
        if (MountedMythical.isMountedMythsCape(go)) {
            setTeleports(MountedMythical.class, List.of(MountedMythical.values()));
        } else if (MountedXerics.isMountedXerics(go)) {
            setTeleports(MountedXerics.class, List.of(MountedXerics.values()));
        } else if (MountedDigsite.isMountedDigsite(go)) {
            setTeleports(MountedDigsite.class, List.of(MountedDigsite.values()));
        } else if (MountedGlory.isMountedGlory(go)) {
            setTeleports(MountedGlory.class, List.of(MountedGlory.values()));
        }
    }

    @Subscribe
    public void onDecorativeObjectDespawned(DecorativeObjectDespawned event) {
        DecorativeObject go = event.getDecorativeObject();
        if (go == null) return;
        if (MountedMythical.isMountedMythsCape(go)) {
            remove(MountedMythical.class.getSimpleName());
        } else if (MountedXerics.isMountedXerics(go)) {
            remove(MountedXerics.class.getSimpleName());
        } else if (MountedDigsite.isMountedDigsite(go)) {
            remove(MountedDigsite.class.getSimpleName());
        } else if (MountedGlory.isMountedGlory(go)) {
            remove(MountedGlory.class.getSimpleName());
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        //TODO("Make sure we only add transport's when we're inside our own home.")
        GameObject go = event.getGameObject();
        if (go == null) return;
        PohPortal portal = PohPortal.getPohPortal(go);
        if (portal != null) {
            addTeleport(PohPortal.class, portal);
            return;
        }
        JewelleryBoxType jewelleryBoxType = JewelleryBoxType.getJewelleryBoxType(go);
        if (jewelleryBoxType != null) {
            setTeleports(JewelleryBox.class, jewelleryBoxType.getAvailableTeleports());
        } else if (NexusPortal.isNexusPortal(go)) {
            setTeleports(NexusPortal.class, NexusPortal.getAvailableTeleports());
        } else if (PohTeleports.isFairyRing(go)) {
            log.info("Found fairy rings in POH");
            put(POH_FAIRY_RINGS, Collections.emptyList());
        }
    }


    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        GameObject go = event.getGameObject();
        if (go == null) return;
        PohPortal portal = PohPortal.getPohPortal(go);
        if (portal != null) {
            removeTeleport(PohPortal.class, portal);
            return;
        }
        JewelleryBoxType jewelleryBoxType = JewelleryBoxType.getJewelleryBoxType(go);
        if (jewelleryBoxType != null) {
            remove(JewelleryBox.class.getSimpleName());
        } else if (NexusPortal.isNexusPortal(go)) {
            remove(NexusPortal.class.getSimpleName());
        } else if (PohTeleports.isFairyRing(go)) {
            log.info("Removing Fairy Rings from POH");
            remove(POH_FAIRY_RINGS);
        }
    }

    /**
     * Removes a teleport from cache using the given Class's simplename as key
     * @param clazz Class to use as key
     * @param teleport PohTeleports to remove from value
     */
    private void removeTeleport(Class<? extends Enum<? extends PohTeleport>> clazz, PohTeleport teleport) {
        String key = clazz.getSimpleName();
        List<PohTeleport> teleports = get(key, ArrayList::new);
        if (!teleports.contains(teleport)) {
            return;
        }
        teleports.remove(teleport);
        put(key, teleports);
        log.info("Removing {} Teleport from {}", teleport.name(), key);
    }

    /**
     * Adds a teleport to cache using the given Class's simplename as key
     * @param clazz Class to use as key
     * @param teleport PohTeleports to add to value
     */
    private void addTeleport(Class<? extends Enum<? extends PohTeleport>> clazz, PohTeleport teleport) {
        String key = clazz.getSimpleName();
        List<PohTeleport> teleports = get(key, ArrayList::new);
        if (teleports.contains(teleport)) {
            return;
        }
        teleports.add(teleport);
        put(key, teleports);
        log.info("Adding {} Teleport to {}", teleport.name(), key);
    }

    /**
     * Sets the teleports in cache using the given Class's simplename as key
     * @param clazz Class to use as key
     * @param teleports List of PohTeleports to put as value
     */
    private void setTeleports(Class<? extends Enum<? extends PohTeleport>> clazz, List<? extends PohTeleport> teleports) {
        String key = clazz.getSimpleName();
        put(key, Collections.unmodifiableList(teleports));
        log.info("Putting {} Teleports to {}", get(key).size(), key);
    }


    /**
     *
     * @return true if fairy ring is present in PoH
     */
    public static boolean hasFairyRings() {
        return getInstance().containsKey(POH_FAIRY_RINGS);
    }

    /**
     * Creates a Set with all available PoH transports directly extracted from cached data
     * Excludes things like Fairy rings and spirit trees as they are based on existing data
     * @return Set with all cached PoH transports present in Cache flattened down
     */
    public static Set<PohTransport> getAvailablePohTransports() {
        HouseStyle style = HouseStyle.getStyle();
        if (style == null) return Collections.emptySet();
        return getInstance().values().stream()
                .flatMap(Collection::stream).map(t -> new PohTransport(style.getPohExitWorldPoint(), t))
                .collect(Collectors.toSet());
    }

    /**
     * Creates a map with all available PoH transports based on cached data
     * @param allTransports map with all persistent transports extracted from TSV's
     * @return Map with all transports available from inside PoH and fairy ring transports towards PoH
     */
    public static Map<WorldPoint, Set<Transport>> getAvailableTransportsMap(Map<WorldPoint, Set<Transport>> allTransports) {
        Set<PohTransport> pohTransports = getAvailablePohTransports();
        Map<WorldPoint, Set<Transport>> transportsMap = new HashMap<>();
        if (pohTransports.isEmpty()) return transportsMap;
        WorldPoint exitPortal = pohTransports.stream().findFirst().get().getOrigin();
        //All the PohTransports start from the same WorldPoint, which is the exit portal inside the PoH
        if (hasFairyRings()) {
            transportsMap.putAll(connectFairyRings(allTransports, exitPortal));
        }
        transportsMap.computeIfAbsent(exitPortal, p -> new HashSet<>()).addAll(pohTransports);
        return transportsMap;
    }

    /**
     * Connecting the PoH Fairy ring to all other fairy rings and vise versa
     * @param transportsMap map from which currently present fairy rings are found
     * @param pohFairyRing position of the PoH fairy ring
     * @return map with PoH fairy rings transports added
     */
    private static Map<WorldPoint, Set<Transport>> connectFairyRings(
            Map<WorldPoint, Set<Transport>> transportsMap,
            WorldPoint pohFairyRing
    ) {
        Map<WorldPoint, Set<Transport>> fairyTransportsMap = new HashMap<>();
        Transport pohFairyTransport = new Transport(pohFairyRing, pohFairyRing, "DIQ", FAIRY_RING, true, 5);

        //Find a point where there is FAIRY_RINGS (because that point will have data for ALL fairy rings)
        transportsMap.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(t -> t.getType() == FAIRY_RING)).findFirst().ifPresent(e -> {
                    WorldPoint existingRingPoint = e.getKey();
                    for (Transport existingRingTransport : new HashSet<>(e.getValue())) {
                        if (existingRingTransport.getType() != FAIRY_RING) continue;
                        // add from poh
                        fairyTransportsMap
                                .computeIfAbsent(pohFairyRing, k -> new HashSet<>())
                                .add(new Transport(pohFairyTransport, existingRingTransport));

                        // add to poh
                        fairyTransportsMap
                                .computeIfAbsent(existingRingPoint, k -> new HashSet<>())
                                .add(new Transport(existingRingTransport, pohFairyTransport));
                    }
                });

        return fairyTransportsMap;
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

}

