package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.TransportVarPlayer;
import net.runelite.client.plugins.microbot.shortestpath.TransportVarbit;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PathfinderConfigTransportRefreshHashTest {

    private static final int[] NO_VARBITS = new int[0];
    private static final int[] NO_VARPLAYERS = new int[0];

    @Test
    public void bankedFareCanBeSplitBetweenInventoryAndBank() {
        assertTrue(PathfinderConfig.hasAvailableCurrencyForFare(400, 100, 500, true));
        assertFalse(PathfinderConfig.hasAvailableCurrencyForFare(400, 100, 500, false));
        assertFalse(PathfinderConfig.hasAvailableCurrencyForFare(400, 99, 500, true));
    }

    @Test
    public void knownFareCurrenciesUseTheRefreshQuantitySnapshot() {
        Map<Integer, Integer> inventoryOnly = Map.of(ItemID.COINS, 400);
        Map<Integer, Integer> withBank = Map.of(ItemID.COINS, 500, ItemID.ECTOTOKEN, 3);

        assertEquals(400, PathfinderConfig.knownCurrencyQuantity("Coins", inventoryOnly));
        assertEquals(500, PathfinderConfig.knownCurrencyQuantity("Coins", withBank));
        assertEquals(3, PathfinderConfig.knownCurrencyQuantity("Ecto-token", withBank));
        assertEquals(0, PathfinderConfig.knownCurrencyQuantity("Ecto-token", inventoryOnly));
        assertEquals("unknown currency must use the name-based fallback", -1,
                PathfinderConfig.knownCurrencyQuantity("custom token", withBank));
    }

    @Test
    public void chronicleChargeKeyChangesOnlyWhenUsabilityChanges() {
        assertEquals(-1, PathfinderConfig.chronicleChargeState(null));
        assertEquals(-1, PathfinderConfig.chronicleChargeState("invalid"));
        assertEquals(0, PathfinderConfig.chronicleChargeState("0"));
        assertEquals(1, PathfinderConfig.chronicleChargeState("1"));
        assertEquals(1, PathfinderConfig.chronicleChargeState("12"));
    }

    @Test
    public void restoringEarlierPolicyRestoresItsBlockedEdges() {
        PathfinderConfig config = new PathfinderConfig(null, new HashMap<>(), List.of(), null, null);
        WorldPoint from = new WorldPoint(3200, 3200, 0);
        WorldPoint to = new WorldPoint(3201, 3200, 0);
        int fromPacked = WorldPointUtil.packWorldPoint(from);
        int toPacked = WorldPointUtil.packWorldPoint(to);
        long edge = PathfinderConfig.transportEdgeKey(fromPacked, toPacked);
        PathfinderConfig.TransportRefreshSnapshot blocked =
                PathfinderConfig.TransportRefreshSnapshot.capture(1, 1, new int[0],
                        new int[0], new int[0], new int[0], new int[0],
                        new HashMap<>(), Set.of(), Set.of(edge));
        PathfinderConfig.TransportRefreshSnapshot open =
                PathfinderConfig.TransportRefreshSnapshot.capture(2, 2, new int[0],
                        new int[0], new int[0], new int[0], new int[0],
                        new HashMap<>(), Set.of(), Set.of());

        blocked.restoreInto(config);
        assertTrue(config.isBlockedTransportEdge(fromPacked, toPacked));
        open.restoreInto(config);
        assertFalse(config.isBlockedTransportEdge(fromPacked, toPacked));
        blocked.restoreInto(config);
        assertTrue("A/B/A cache hit must restore A's edge policy",
                config.isBlockedTransportEdge(fromPacked, toPacked));

        WorldPoint learnedFrom = new WorldPoint(3300, 3300, 0);
        WorldPoint learnedTo = new WorldPoint(3301, 3300, 0);
        int learnedFromPacked = WorldPointUtil.packWorldPoint(learnedFrom);
        int learnedToPacked = WorldPointUtil.packWorldPoint(learnedTo);
        config.learnBlockedEdge(learnedFrom, learnedTo, "test");
        open.restoreInto(config);
        assertTrue("restoring a snapshot must retain blocks learned after it was captured",
                config.isBlockedTransportEdge(learnedFromPacked, learnedToPacked));
        config.unlearnBlockedEdge(learnedFrom, learnedTo, "test");
        open.restoreInto(config);
        assertFalse(config.isBlockedTransportEdge(learnedFromPacked, learnedToPacked));
    }

    @Test
    public void verificationHashDiffersForNotStartedVsInProgressQuestState() {
        int[] boostedLevels = new int[Skill.values().length];
        int[] sortedSkillOrdinals = new int[0];
        int trackedQuestId = 987654;
        int clientOfKourendId = Quest.CLIENT_OF_KOUREND.getId();
        int[] sortedQuestIds = new int[]{trackedQuestId, clientOfKourendId};

        int hashNotStarted = PathfinderConfig.computeTransportRefreshVerificationHash(
                boostedLevels,
                sortedSkillOrdinals,
                NO_VARBITS,
                NO_VARPLAYERS,
                sortedQuestIds,
                questId -> {
                    if (questId == trackedQuestId) {
                        return QuestState.NOT_STARTED;
                    }
                    if (questId == clientOfKourendId) {
                        return QuestState.FINISHED;
                    }
                    return QuestState.NOT_STARTED;
                });

        int hashInProgress = PathfinderConfig.computeTransportRefreshVerificationHash(
                boostedLevels,
                sortedSkillOrdinals,
                NO_VARBITS,
                NO_VARPLAYERS,
                sortedQuestIds,
                questId -> {
                    if (questId == trackedQuestId) {
                        return QuestState.IN_PROGRESS;
                    }
                    if (questId == clientOfKourendId) {
                        return QuestState.FINISHED;
                    }
                    return QuestState.NOT_STARTED;
                });

        assertNotEquals("Quest state transition should invalidate cached transport refresh snapshot",
                hashNotStarted, hashInProgress);
    }

    private static int hashWithLevels(int[] sortedSkillOrdinals, int[] boostedLevels) {
        return PathfinderConfig.computeTransportRefreshVerificationHash(
                boostedLevels,
                sortedSkillOrdinals,
                NO_VARBITS,
                NO_VARPLAYERS,
                new int[0],
                questId -> QuestState.NOT_STARTED);
    }

    /**
     * Hitpoints regenerating (or prayer draining) must not invalidate the transport cache. No
     * transport gates on those skills, so they cannot change any transport's usability — yet hashing
     * every skill meant a single point of HP regen forced a full ~2.6s re-evaluation of all
     * transports, which the walker blocks on at route start.
     */
    @Test
    public void hitpointsAndPrayerDriftDoNotInvalidateWhenNoTransportRequiresThem() {
        int[] sortedSkillOrdinals = new int[]{Skill.AGILITY.ordinal()};

        int[] before = new int[Skill.values().length];
        before[Skill.AGILITY.ordinal()] = 70;
        before[Skill.HITPOINTS.ordinal()] = 45;
        before[Skill.PRAYER.ordinal()] = 43;

        int[] after = before.clone();
        after[Skill.HITPOINTS.ordinal()] = 46; // regenerated a point
        after[Skill.PRAYER.ordinal()] = 41;    // prayer drained

        assertEquals("HP regen / prayer drain must not invalidate the transport refresh cache",
                hashWithLevels(sortedSkillOrdinals, before),
                hashWithLevels(sortedSkillOrdinals, after));
    }

    /** A skill a transport does gate on must still invalidate — e.g. an agility boost/drain. */
    @Test
    public void requiredSkillChangeStillInvalidates() {
        int[] sortedSkillOrdinals = new int[]{Skill.AGILITY.ordinal()};

        int[] before = new int[Skill.values().length];
        before[Skill.AGILITY.ordinal()] = 70;

        int[] after = before.clone();
        after[Skill.AGILITY.ordinal()] = 72; // boosted past a shortcut requirement

        assertNotEquals("A boosted level for a skill transports require must invalidate the cache",
                hashWithLevels(sortedSkillOrdinals, before),
                hashWithLevels(sortedSkillOrdinals, after));
    }

    @Test
    public void requiredSpecialLevelChangeInvalidates() {
        int[] tracked = new int[]{
                Transport.TOTAL_LEVEL_INDEX,
                Transport.COMBAT_LEVEL_INDEX,
                Transport.QUEST_POINTS_INDEX,
        };
        int[] before = new int[Transport.REQUIREMENT_LEVEL_COUNT];
        before[Transport.TOTAL_LEVEL_INDEX] = 2000;
        before[Transport.COMBAT_LEVEL_INDEX] = 39;
        before[Transport.QUEST_POINTS_INDEX] = 100;

        for (int ordinal : tracked) {
            int[] after = before.clone();
            after[ordinal]++;
            assertNotEquals("special requirement changes must invalidate ordinal " + ordinal,
                    hashWithLevels(tracked, before), hashWithLevels(tracked, after));
        }
    }

    /**
     * A cooldown gate must not churn the cache while it ticks. {@code COOLDOWN_MINUTES} compares
     * against wall-clock minutes, so hashing its raw varplayer value invalidated the transport cache
     * continuously — and casting Home Teleport writes {@code LAST_HOME_TELEPORT}, so the teleport
     * invalidated the cache simply by being used, costing a full ~2.6s re-evaluation right after
     * every teleport. Only the satisfied/not-satisfied verdict may participate.
     */
    @Test
    public void cooldownVerdictIsStableWhileTicking_butFlipsWhenItExpires() {
        int varplayerId = 12345;
        int cooldownMinutes = 30;
        int cooldownOp = TransportVarPlayer.Operator.COOLDOWN_MINUTES.ordinal();
        int[] conditions = new int[]{varplayerId, cooldownOp, cooldownMinutes};
        long nowMinutes = System.currentTimeMillis() / 60000L;

        // Two different raw values, both still inside the 30 minute cooldown => same verdict.
        int justTeleported = PathfinderConfig.hashVarplayerConditionVerdicts(
                conditions, id -> (int) (nowMinutes - 1));
        int fiveMinutesLater = PathfinderConfig.hashVarplayerConditionVerdicts(
                conditions, id -> (int) (nowMinutes - 6));

        assertEquals("a cooldown ticking must NOT invalidate the transport cache — this is what made "
                        + "casting Home Teleport invalidate the cache by writing LAST_HOME_TELEPORT",
                justTeleported, fiveMinutesLater);

        // Past the threshold the transport becomes usable, so the verdict flips and must invalidate.
        int cooldownExpired = PathfinderConfig.hashVarplayerConditionVerdicts(
                conditions, id -> (int) (nowMinutes - (cooldownMinutes + 1)));

        assertNotEquals("an expiring cooldown changes usability and must invalidate",
                justTeleported, cooldownExpired);
    }

    /** Condition triples must be deduplicated and ordered so the hash is position-independent. */
    @Test
    public void varplayerConditionEncodingIsDeterministicAndDeduplicated() {
        java.util.List<int[]> unordered = java.util.Arrays.asList(
                new int[]{99, 1, 5},
                new int[]{10, 0, 2},
                new int[]{99, 1, 5}, // duplicate
                new int[]{10, 0, 1});
        java.util.List<int[]> shuffled = java.util.Arrays.asList(
                new int[]{10, 0, 1},
                new int[]{99, 1, 5},
                new int[]{10, 0, 2});

        int[] a = PathfinderConfig.encodeSortedConditionTriples(unordered);
        int[] b = PathfinderConfig.encodeSortedConditionTriples(shuffled);

        assertArrayEquals("insertion order must not change the encoding", a, b);
        assertEquals("duplicates must collapse (3 distinct conditions x 3 ints)", 9, a.length);
    }

    /**
     * The walk-start cold start was attributed to {@code changed=varbits}. A varbit whose raw value
     * moves without flipping any condition verdict must not invalidate the transport cache — that is
     * a full ~2.6s re-evaluation the walker blocks on before it can issue its first action.
     */
    @Test
    public void varbitValueChangeWithoutVerdictFlipDoesNotInvalidate() {
        int varbitId = 4242;
        int threshold = 5;
        int greaterThan = TransportVarbit.Operator.GREATER_THAN.ordinal();
        int[] conditions = new int[]{varbitId, greaterThan, threshold};

        // 7 -> 9: both satisfy "> 5", so usability is unchanged.
        assertEquals("a varbit moving without flipping its verdict must not invalidate",
                PathfinderConfig.hashVarbitConditionVerdicts(conditions, id -> 7),
                PathfinderConfig.hashVarbitConditionVerdicts(conditions, id -> 9));

        // 7 -> 3 crosses the threshold, so the transport becomes unusable and must invalidate.
        assertNotEquals("crossing the threshold changes usability and must invalidate",
                PathfinderConfig.hashVarbitConditionVerdicts(conditions, id -> 7),
                PathfinderConfig.hashVarbitConditionVerdicts(conditions, id -> 3));
    }

    /** Ordinals outside the supplied levels array must be ignored rather than throwing. */
    @Test
    public void outOfRangeSkillOrdinalsAreIgnored() {
        int[] boostedLevels = new int[]{1, 2, 3};
        int[] sortedSkillOrdinals = new int[]{-1, 1, 9999};

        assertEquals(hashWithLevels(sortedSkillOrdinals, boostedLevels),
                hashWithLevels(new int[]{1}, boostedLevels));
    }

    // ---- transport-relevant item filter (transport-refresh cache key) --------------------------------

    /** Ids a transport gates on, plus Coins resolved from the currency column at collection time. */
    private static final Set<Integer> RELEVANT_IDS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(1856, 954, net.runelite.api.gameval.ItemID.COINS)));

    /**
     * The whole point: an item no transport gates on must not touch the key, or ordinary inventory
     * churn forces a full ~5,700-transport re-evaluation. Measured at 0 hits / 22 misses in a
     * questing session before this filter existed.
     */
    @Test
    public void irrelevantItemDoesNotAffectTransportUsability() {
        assertFalse(PathfinderConfig.itemAffectsTransportUsability(995000, RELEVANT_IDS));
    }

    @Test
    public void declaredItemRequirementAffectsTransportUsability() {
        assertTrue(PathfinderConfig.itemAffectsTransportUsability(1856, RELEVANT_IDS));
    }

    /**
     * Currency must still invalidate, but by ID — comparing item NAMES here called
     * Rs2ItemModel.getName(), which lazily loads the composition on the client thread, so
     * fingerprinting a full bank fired hundreds of round-trips and every script's queued task timed
     * out at once. Currency is resolved to ids once at collection time instead.
     */
    @Test
    public void currencyAffectsUsabilityByResolvedId() {
        assertTrue(PathfinderConfig.itemAffectsTransportUsability(
                net.runelite.api.gameval.ItemID.COINS, RELEVANT_IDS));
    }

    @Test
    public void everySpellRuneAffectsTransportUsability() {
        Set<Integer> relevantIds = new HashSet<>();
        PathfinderConfig.addSpellRuneItemIds(relevantIds);

        for (Runes rune : Runes.values()) {
            assertTrue(rune + " must invalidate teleport-spell usability",
                    PathfinderConfig.itemAffectsTransportUsability(rune.getItemId(), relevantIds));
        }
    }

    /** Null set — sets not built yet, or a currency that would not resolve — must fingerprint all. */
    @Test
    public void unknownRelevantSetFingerprintsEverything() {
        assertTrue(PathfinderConfig.itemAffectsTransportUsability(995000, null));
    }

    @Test
    public void catalogVariantsNeverDropPreviouslyRelevantItems() {
        Set<Integer> baseCatalog = Set.of(1856);
        Set<Integer> pohCatalog = Set.of(954);
        Set<Integer> expanded = PathfinderConfig.expandTransportRelevantItemState(
                baseCatalog, pohCatalog);

        assertTrue(PathfinderConfig.itemAffectsTransportUsability(1856, expanded));
        assertTrue(PathfinderConfig.itemAffectsTransportUsability(954, expanded));
        assertEquals("switching back to an earlier catalog must retain the wider fingerprint",
                expanded, PathfinderConfig.expandTransportRelevantItemState(expanded, baseCatalog));
        assertEquals("an unknown currency must keep the safe all-item fallback",
                null, PathfinderConfig.expandTransportRelevantItemState(expanded, null));
    }
}
