package net.runelite.client.plugins.microbot.hal.blessedwine;

public enum BlessedWineState {
    INITIALIZING,         // Run setup, determine blessing capacity
    WALK_TO_ALTAR,        // Travel to Exposed Altar
    BLESS_AT_ALTAR,       // Interact to begin blessing wines
    WALK_TO_BOWL,         // Travel to Libation Bowl
    USE_LIBATION_BOWL,    // Drain prayer by blessing wines at bowl
    WALK_TO_SHRINE,       // Go to Shrine of Ralos
    RESTORE_PRAYER,       // Replenish prayer points
    RETURN_TO_BOWL,       // Return to bowl for next libation loop
    CHECK_CONTINUE,       // Decide: repeat or bank
    TELEPORT_TO_BANK,     // Use moth to teleport to Cam Torum
    BANKING,              // Deposit, withdraw, reload inventory
    FINISHED              // Run completed or resources exhausted
}