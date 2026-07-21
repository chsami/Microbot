package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;

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

    /** Ordinals outside the supplied levels array must be ignored rather than throwing. */
    @Test
    public void outOfRangeSkillOrdinalsAreIgnored() {
        int[] boostedLevels = new int[]{1, 2, 3};
        int[] sortedSkillOrdinals = new int[]{-1, 1, 9999};

        assertEquals(hashWithLevels(sortedSkillOrdinals, boostedLevels),
                hashWithLevels(new int[]{1}, boostedLevels));
    }
}
