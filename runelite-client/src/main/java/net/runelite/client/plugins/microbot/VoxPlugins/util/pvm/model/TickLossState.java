package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model;

/**
 * represents combat tick loss states
 * used for determining if player can perform actions this tick
 */
public enum TickLossState {
    /**
     * no tick loss - player can attack
     */
    NONE,

    /**
     * potential tick loss - action may prevent attack
     * (eating, drinking, weapon switch in progress)
     */
    POTENTIAL,

    /**
     * confirmed tick loss - player cannot attack this tick
     * (just ate food, just drank potion, weapon not equipped)
     */
    LOSING
}
