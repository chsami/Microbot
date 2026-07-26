package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.shortestpath.TransportVarPlayer;
import net.runelite.client.plugins.microbot.shortestpath.TransportVarbit;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PathfinderConfigTransportRefreshHashTest {

    private static final int[] NO_VARBITS = new int[0];
    private static final int[] NO_VARPLAYERS = new int[0];

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
}
