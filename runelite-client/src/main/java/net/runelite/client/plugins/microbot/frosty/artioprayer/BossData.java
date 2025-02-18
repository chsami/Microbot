package net.runelite.client.plugins.microbot.frosty.artioprayer;

import lombok.Getter;

@Getter
public enum BossData {
    ARTIO(11992, 2350, 133, 10013, 10014, 10015, -1);

    private final int npcId;
    private final int rangeProjectile;
    private final int mageProjectile;
    private final int rangeAnimation;
    private final int mageAnimation;
    private final int meleeAnimation;
    private final Integer betweenAnimation;

    BossData(int npcId, int rangeProjectile, int mageProjectile, int rangeAnimation, int mageAnimation, int meleeAnimation, int betweenAnimation) {
        this.npcId = npcId;
        this.rangeProjectile = rangeProjectile;
        this.mageProjectile = mageProjectile;
        this.rangeAnimation = rangeAnimation;
        this.mageAnimation = mageAnimation;
        this.meleeAnimation = meleeAnimation;
        this.betweenAnimation = betweenAnimation;
    }

    public static BossData fromNpcId(int id) {
        for (BossData boss : values()) {
            if (boss.npcId == id) {
                return boss;
            }
        }
        return null;
    }

}