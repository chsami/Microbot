package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * immutable model for tracking boss attack patterns
 * supports 4:1, 5:1, and custom attack ratios
 * tracks both player and boss attack counts
 *
 * usage:
 * - 4:1 pattern: player attacks 4 times, boss attacks once
 * - 5:1 pattern: player attacks 5 times, boss attacks once
 * - excludes special mechanics (stomp attacks, phase changes)
 */
@Value
@Builder(toBuilder = true)
public class BossAttackPattern {
    int bossNpcId;
    int bossNpcIndex;
    int playerAttackCount;
    int bossAttackCount;
    int targetRatio; // player:boss ratio (4 for 4:1, 5 for 5:1)
    int lastPlayerAttackTick;
    int lastBossAttackTick;
    List<Integer> excludedAnimations; // animations to exclude from count (stomp, etc)
    boolean patternActive;

    /**
     * check if pattern is complete (boss should attack next)
     * example: 4:1 pattern completes when player has 4 attacks
     */
    public boolean isPatternComplete() {
        return playerAttackCount >= targetRatio;
    }

    /**
     * check if ready for next player attack
     * pattern not complete and boss hasn't attacked recently
     */
    public boolean isReadyForPlayerAttack(int currentTick, int bossAttackDelay) {
        if (isPatternComplete()) {
            return false; // wait for boss attack
        }

        // check if boss attacked recently (would reset pattern)
        int ticksSinceBossAttack = currentTick - lastBossAttackTick;
        return ticksSinceBossAttack >= bossAttackDelay;
    }

    /**
     * check if expecting boss attack next
     */
    public boolean isExpectingBossAttack() {
        return isPatternComplete();
    }

    /**
     * get attacks remaining before pattern completes
     */
    public int getAttacksRemaining() {
        return Math.max(0, targetRatio - playerAttackCount);
    }

    /**
     * get pattern completion percentage
     */
    public double getPatternProgress() {
        return (double) playerAttackCount / targetRatio;
    }

    /**
     * increment player attack count
     * returns new pattern with updated count
     */
    public BossAttackPattern withPlayerAttack(int currentTick) {
        return this.toBuilder()
            .playerAttackCount(playerAttackCount + 1)
            .lastPlayerAttackTick(currentTick)
            .build();
    }

    /**
     * record boss attack and reset pattern
     * returns new pattern with counts reset
     */
    public BossAttackPattern withBossAttack(int currentTick, int animationId) {
        // check if this animation should be excluded (stomp, etc)
        if (excludedAnimations != null && excludedAnimations.contains(animationId)) {
            // don't reset pattern for excluded animations
            return this.toBuilder()
                .bossAttackCount(bossAttackCount + 1)
                .lastBossAttackTick(currentTick)
                .build();
        }

        // normal attack - reset pattern
        return this.toBuilder()
            .playerAttackCount(0)
            .bossAttackCount(bossAttackCount + 1)
            .lastBossAttackTick(currentTick)
            .build();
    }

    /**
     * reset pattern counts
     */
    public BossAttackPattern withReset() {
        return this.toBuilder()
            .playerAttackCount(0)
            .bossAttackCount(0)
            .patternActive(false)
            .build();
    }

    /**
     * activate pattern tracking
     */
    public BossAttackPattern withActivated() {
        return this.toBuilder()
            .patternActive(true)
            .build();
    }

    /**
     * deactivate pattern tracking
     */
    public BossAttackPattern withDeactivated() {
        return this.toBuilder()
            .patternActive(false)
            .build();
    }

    /**
     * create 4:1 pattern for boss
     */
    public static BossAttackPattern create4To1Pattern(int bossNpcId, int bossNpcIndex) {
        return BossAttackPattern.builder()
            .bossNpcId(bossNpcId)
            .bossNpcIndex(bossNpcIndex)
            .playerAttackCount(0)
            .bossAttackCount(0)
            .targetRatio(4)
            .lastPlayerAttackTick(-1)
            .lastBossAttackTick(-1)
            .excludedAnimations(new ArrayList<>())
            .patternActive(true)
            .build();
    }

    /**
     * create 5:1 pattern for boss
     */
    public static BossAttackPattern create5To1Pattern(int bossNpcId, int bossNpcIndex) {
        return BossAttackPattern.builder()
            .bossNpcId(bossNpcId)
            .bossNpcIndex(bossNpcIndex)
            .playerAttackCount(0)
            .bossAttackCount(0)
            .targetRatio(5)
            .lastPlayerAttackTick(-1)
            .lastBossAttackTick(-1)
            .excludedAnimations(new ArrayList<>())
            .patternActive(true)
            .build();
    }

    /**
     * create custom ratio pattern
     */
    public static BossAttackPattern createCustomPattern(int bossNpcId, int bossNpcIndex, int ratio) {
        return BossAttackPattern.builder()
            .bossNpcId(bossNpcId)
            .bossNpcIndex(bossNpcIndex)
            .playerAttackCount(0)
            .bossAttackCount(0)
            .targetRatio(ratio)
            .lastPlayerAttackTick(-1)
            .lastBossAttackTick(-1)
            .excludedAnimations(new ArrayList<>())
            .patternActive(true)
            .build();
    }

    /**
     * add animation to exclude from boss attack count
     * example: Hunllef stomp attack (doesn't count toward 4:1 cycle)
     */
    public BossAttackPattern withExcludedAnimation(int animationId) {
        List<Integer> newExcluded = new ArrayList<>(excludedAnimations != null ? excludedAnimations : new ArrayList<>());
        if (!newExcluded.contains(animationId)) {
            newExcluded.add(animationId);
        }

        return this.toBuilder()
            .excludedAnimations(newExcluded)
            .build();
    }
}
