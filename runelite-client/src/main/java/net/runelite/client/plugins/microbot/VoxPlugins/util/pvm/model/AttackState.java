package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model;

/**
 * player attack state for attack delay management
 * tracks weapon cooldown state for tick-perfect combat
 *
 * used by Rs2CombatHandler to determine when player can attack
 */
public enum AttackState {
    /**
     * no attack initiated, weapon ready
     */
    NOT_ATTACKING,

    /**
     * first tick after attack initiated
     * weapon has fired but animation just started
     */
    DELAYED_FIRST_TICK,

    /**
     * weapon on cooldown
     * waiting for attack speed delay to complete
     */
    DELAYED
}
