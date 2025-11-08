package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.enums.CombatStyle;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * detects and tracks overhead prayers on NPCs for automated weapon switching
 * supports multiple detection methods:
 * - sprite-based detection (works for most bosses)
 * - NPC ID changes (Hunllef, Demonic Gorillas)
 * - HeadIcon enum (unified prayer representation)
 */
@Slf4j
@Singleton
public class Rs2OverheadPrayerHandler {

    private static Rs2OverheadPrayerHandler instance;

    // sprite ID constants for overhead prayers
    // these values come from SpriteID.PRAYER_PROTECT_FROM_* constants
    private static final int PROTECT_FROM_MAGIC_SPRITE = 127;
    private static final int PROTECT_FROM_MISSILES_SPRITE = 128;
    private static final int PROTECT_FROM_MELEE_SPRITE = 129;

    // track NPC prayer states (indexed by NPC index)
    private final Map<Integer, HeadIcon> npcPrayerCache = new ConcurrentHashMap<>();

    // track last prayer change tick per NPC
    private final Map<Integer, Integer> lastPrayerChangeTick = new ConcurrentHashMap<>();

    // NPC ID to prayer mapping (for NPCs that use ID changes)
    // key: NPC ID, value: HeadIcon
    private final Map<Integer, HeadIcon> npcIdToPrayer = new HashMap<>();

    private Rs2OverheadPrayerHandler() {
        initializeNpcIdMappings();
    }

    public static synchronized Rs2OverheadPrayerHandler getInstance() {
        if (instance == null) {
            instance = new Rs2OverheadPrayerHandler();
        }
        return instance;
    }

    /**
     * initialize NPC ID to prayer mappings
     * these NPCs change their ID when they switch prayers
     */
    private void initializeNpcIdMappings() {
        // corrupted hunllef (the gauntlet)
        npcIdToPrayer.put(NpcID.CRYSTAL_HUNLLEF_MELEE, HeadIcon.MELEE);   // protect from melee
        npcIdToPrayer.put(NpcID.CRYSTAL_HUNLLEF_MELEE_HM, HeadIcon.MELEE);   // protect from melee
        npcIdToPrayer.put(NpcID.CRYSTAL_HUNLLEF_RANGED, HeadIcon.RANGED);  // protect from ranged
        npcIdToPrayer.put(NpcID.CRYSTAL_HUNLLEF_RANGED_HM, HeadIcon.RANGED);  // protect from ranged
        npcIdToPrayer.put(9023, HeadIcon.MAGIC);   // protect from magic

        // demonic gorilla (mm2)
        npcIdToPrayer.put(7147, HeadIcon.MELEE);   // protect from melee
        npcIdToPrayer.put(7148, HeadIcon.RANGED);  // protect from ranged
        npcIdToPrayer.put(7149, HeadIcon.MAGIC);   // protect from magic

        log.debug("Initialized NPC ID to prayer mappings for {} NPCs", npcIdToPrayer.size());
    }

    /**
     * register custom NPC ID to prayer mapping
     * useful for boss-specific plugins
     */
    public void registerNpcIdMapping(int npcId, HeadIcon prayer) {
        npcIdToPrayer.put(npcId, prayer);
        log.debug("Registered NPC ID {} -> {}", npcId, prayer);
    }

    /**
     * get overhead prayer for NPC using multiple detection methods
     * priority: NPC ID mapping -> sprite detection -> Rs2NpcModel.getHeadIcon()
     */
    @Nullable
    public HeadIcon getOverheadPrayer(NPC npc) {
        if (npc == null) {
            return null;
        }

        int npcIndex = npc.getIndex();
        int currentTick = Microbot.getClient().getTickCount();

        // method 1: check NPC ID mapping first (most reliable for specific NPCs)
        HeadIcon idBasedPrayer = npcIdToPrayer.get(npc.getId());
        if (idBasedPrayer != null) {
            updatePrayerCache(npcIndex, idBasedPrayer, currentTick);
            return idBasedPrayer;
        }

        // method 2: sprite-based detection
        HeadIcon spritePrayer = detectPrayerFromSprites(npc);
        if (spritePrayer != null) {
            updatePrayerCache(npcIndex, spritePrayer, currentTick);
            return spritePrayer;
        }

        // method 3: use Rs2NpcModel wrapper (fallback)
        try {
            Rs2NpcModel npcModel = new Rs2NpcModel(npc);
            HeadIcon modelPrayer = npcModel.getHeadIcon();
            if (modelPrayer != null) {
                updatePrayerCache(npcIndex, modelPrayer, currentTick);
                return modelPrayer;
            }
        } catch (Exception e) {
            log.error("Failed to get HeadIcon from Rs2NpcModel: {}", e.getMessage());
        }

        // return cached prayer if available (within reasonable timeframe)
        if (npcPrayerCache.containsKey(npcIndex)) {
            Integer lastChangeTick = lastPrayerChangeTick.get(npcIndex);
            if (lastChangeTick != null && (currentTick - lastChangeTick) < 100) {
                return npcPrayerCache.get(npcIndex);
            }
        }

        return null;
    }

    /**
     * detect prayer from sprite IDs
     * returns HeadIcon based on overhead sprite values
     */
    @Nullable
    private HeadIcon detectPrayerFromSprites(NPC npc) {
        short[] spriteIds = npc.getOverheadSpriteIds();
        if (spriteIds == null || spriteIds.length == 0) {
            return null;
        }

        // check for protection prayers
        for (short spriteId : spriteIds) {
            if (spriteId == -1) continue;

            if (spriteId == PROTECT_FROM_MELEE_SPRITE) {
                return HeadIcon.MELEE;
            } else if (spriteId == PROTECT_FROM_MISSILES_SPRITE) {
                return HeadIcon.RANGED;
            } else if (spriteId == PROTECT_FROM_MAGIC_SPRITE) {
                return HeadIcon.MAGIC;
            }
        }

        return null;
    }

    /**
     * update prayer cache and track change tick
     */
    private void updatePrayerCache(int npcIndex, HeadIcon prayer, int currentTick) {
        HeadIcon previousPrayer = npcPrayerCache.put(npcIndex, prayer);

        // log prayer changes
        if (previousPrayer != null && previousPrayer != prayer) {
            log.debug("NPC {} changed prayer: {} -> {}", npcIndex, previousPrayer, prayer);
        }

        lastPrayerChangeTick.put(npcIndex, currentTick);
    }

    /**
     * check if NPC is praying against specific combat style
     */
    public boolean isPrayingAgainst(NPC npc, CombatStyle style) {
        HeadIcon prayer = getOverheadPrayer(npc);
        if (prayer == null) {
            return false;
        }

        return isPrayerProtectingStyle(prayer, style);
    }

    /**
     * check if HeadIcon protects against combat style
     */
    public boolean isPrayerProtectingStyle(HeadIcon prayer, CombatStyle style) {
        switch (style) {
            case MELEE:
                return prayer == HeadIcon.MELEE 
                    || prayer == HeadIcon.RANGE_MELEE 
                    || prayer == HeadIcon.MAGE_MELEE 
                    || prayer == HeadIcon.RANGE_MAGE_MELEE;

            case RANGED:
                return prayer == HeadIcon.RANGED 
                    || prayer == HeadIcon.RANGE_MAGE 
                    || prayer == HeadIcon.RANGE_MELEE 
                    || prayer == HeadIcon.RANGE_MAGE_MELEE;

            case MAGIC:
                return prayer == HeadIcon.MAGIC 
                    || prayer == HeadIcon.RANGE_MAGE 
                    || prayer == HeadIcon.MAGE_MELEE 
                    || prayer == HeadIcon.RANGE_MAGE_MELEE;

            default:
                return false;
        }
    }

    /**
     * get combat styles NOT protected by prayer
     * useful for weapon switching (switch to unprotected style)
     */
    public List<CombatStyle> getUnprotectedStyles(HeadIcon prayer) {
        List<CombatStyle> unprotected = new ArrayList<>();

        if (!isPrayerProtectingStyle(prayer, CombatStyle.MELEE)) {
            unprotected.add(CombatStyle.MELEE);
        }
        if (!isPrayerProtectingStyle(prayer, CombatStyle.RANGED)) {
            unprotected.add(CombatStyle.RANGED);
        }
        if (!isPrayerProtectingStyle(prayer, CombatStyle.MAGIC)) {
            unprotected.add(CombatStyle.MAGIC);
        }

        return unprotected;
    }

    /**
     * get ticks since NPC changed prayer
     * returns -1 if never tracked
     */
    public int getTicksSincePrayerChange(NPC npc) {
        if (npc == null) {
            return -1;
        }

        Integer lastChangeTick = lastPrayerChangeTick.get(npc.getIndex());
        if (lastChangeTick == null) {
            return -1;
        }

        return Microbot.getClient().getTickCount() - lastChangeTick;
    }

    /**
     * check if NPC has any overhead prayer active
     */
    public boolean hasOverheadPrayer(NPC npc) {
        return getOverheadPrayer(npc) != null;
    }

    /**
     * get cached prayer for NPC (doesn't re-check, returns last known)
     */
    @Nullable
    public HeadIcon getCachedPrayer(int npcIndex) {
        return npcPrayerCache.get(npcIndex);
    }

    /**
     * clear specific NPC from cache (when NPC despawns)
     */
    public void clearNpcCache(int npcIndex) {
        npcPrayerCache.remove(npcIndex);
        lastPrayerChangeTick.remove(npcIndex);
    }

    /**
     * clear all cached prayer data
     */
    public void clearAll() {
        npcPrayerCache.clear();
        lastPrayerChangeTick.clear();
        log.debug("Cleared all overhead prayer cache");
    }

    /**
     * get prayer name for display
     */
    public String getPrayerName(HeadIcon prayer) {
        switch (prayer) {
            case MELEE: return "Protect from Melee";
            case RANGED: return "Protect from Missiles";
            case MAGIC: return "Protect from Magic";
            case RANGE_MAGE: return "Protect from Range/Mage";
            case RANGE_MELEE: return "Protect from Range/Melee";
            case MAGE_MELEE: return "Protect from Mage/Melee";
            case RANGE_MAGE_MELEE: return "Protect from All";
            case RETRIBUTION: return "Retribution";
            case SMITE: return "Smite";
            case REDEMPTION: return "Redemption";
            case WRATH: return "Wrath";
            case SOUL_SPLIT: return "Soul Split";
            case DEFLECT_MELEE: return "Deflect Melee";
            case DEFLECT_RANGE: return "Deflect Range";
            case DEFLECT_MAGE: return "Deflect Mage";
            default: return "Unknown";
        }
    }

    /**
     * get prayer short name for overlay display
     */
    public String getPrayerShortName(HeadIcon prayer) {
        switch (prayer) {
            case MELEE: return "Melee";
            case RANGED: return "Range";
            case MAGIC: return "Mage";
            case RANGE_MAGE: return "Rng/Mag";
            case RANGE_MELEE: return "Rng/Mel";
            case MAGE_MELEE: return "Mag/Mel";
            case RANGE_MAGE_MELEE: return "All";
            default: return prayer.name();
        }
    }
}
