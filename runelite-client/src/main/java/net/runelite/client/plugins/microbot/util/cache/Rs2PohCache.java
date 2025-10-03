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
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
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
import static net.runelite.client.plugins.microbot.shortestpath.TransportType.SPIRIT_TREE;

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
     *
     * @param clazz    Class to use as key
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
     *
     * @param clazz    Class to use as key
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
     *
     * @param clazz     Class to use as key
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
     *
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
     * Connecting the PoH Fairy ring to all other fairy rings and vise versa
     *
     * @param pohFairyRing  position of the PoH fairy ring (exitPortal until mapping inside Poh)
     * @param transportsMap map from which currently present fairy rings are found
     * @return new map with PoH fairy rings transports added
     */
    public static Map<WorldPoint, Set<Transport>> createFairyRingMap(
            WorldPoint pohFairyRing,
            Map<WorldPoint, Set<Transport>> transportsMap
    ) {
        //Only used to build actual transports (needs ORIGIN and DESTINATION same)
        Transport pohFairyTransport = new Transport(pohFairyRing, pohFairyRing, "DIQ", FAIRY_RING, true, 5);
        return createTransportsToPoh(pohFairyTransport, transportsMap);
    }

    /**
     * Connecting the PoH spirit tree to all other spirit tree and vise versa
     *
     * @param pohSpiritTree position of the PoH spirit tree (exitPortal until mapping inside Poh)
     * @param transportsMap map from which currently present spirit tree transports are found
     * @return new map with PoH fairy spirit tree transports added
     */
    public static Map<WorldPoint, Set<Transport>> createSpiritTreeMap(
            WorldPoint pohSpiritTree,
            Map<WorldPoint, Set<Transport>> transportsMap
    ) {
        //Only used to build actual transports (needs ORIGIN and DESTINATION same)
        Transport pohSpiritTransport = new Transport(pohSpiritTree, pohSpiritTree, "C: Your house", SPIRIT_TREE, true, 5);
        return createTransportsToPoh(pohSpiritTransport, transportsMap);
    }

    /**
     * Uses a template (PoH) transport to connect all transports to
     * other transports of the same type, both by adding routes from the PoH transport to
     * the existing transports and vice versa.
     *
     * @param pohTempTransport the transport object representing the PoH transport to be connected.
     *                         The TransportType is used to filter for existing transports
     *                         The origin and destination are used to build the connecting transport
     * @param transportsMap a map of existing transports, where each key represents a WorldPoint
     *                      and the value is a set of transports originating or terminating at
     *                      that point
     * @return a new map containing the updated set of transports where connections have been
     *         added between the provided PoH transport and existing transports of the same type
     */
    public static Map<WorldPoint, Set<Transport>> createTransportsToPoh(Transport pohTempTransport, Map<WorldPoint, Set<Transport>> transportsMap) {
        WorldPoint pohExitPortal = pohTempTransport.getOrigin();
        TransportType type = pohTempTransport.getType();
        Map<WorldPoint, Set<Transport>> newTransportsMap = new HashMap<>();
        transportsMap.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(t -> t.getType() == type)).findFirst().ifPresent(e -> {
                    WorldPoint existingRingPoint = e.getKey();
                    for (Transport existingRingTransport : new HashSet<>(e.getValue())) {
                        if (existingRingTransport.getType() != type) continue;
                        // add from poh
                        newTransportsMap
                                .computeIfAbsent(pohExitPortal, k -> new HashSet<>())
                                .add(new Transport(pohTempTransport, existingRingTransport));

                        // add to poh
                        newTransportsMap
                                .computeIfAbsent(existingRingPoint, k -> new HashSet<>())
                                .add(new Transport(existingRingTransport, pohTempTransport));
                    }
                });

        return newTransportsMap;
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

