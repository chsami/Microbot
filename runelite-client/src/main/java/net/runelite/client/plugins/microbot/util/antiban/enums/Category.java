package net.runelite.client.plugins.microbot.util.antiban.enums;

import net.runelite.api.AnimationID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * The Category enum represents different categories of player activities, such as combat, skilling, and processing.
 *
 * <p>
 * Each category is associated with a specific type of activity and contains logic to determine whether the player is "busy"
 * based on the current game state. This is used to control bot behavior and ensure it adapts to the player's activity,
 * pausing or adjusting actions when the player is engaged in a task.
 * </p>
 *
 * <h3>Main Features:</h3>
 * <ul>
 *   <li>Categories for Various Activities: Includes categories such as combat, skilling (e.g., fishing, cooking, magic),
 *   processing, and collecting, each with its own logic to determine if the player is busy.</li>
 *   <li>Custom Busy Logic: Each category overrides the <code>isBusyInternal
 *  ()</code> method, providing custom logic
 *   for determining if the player is engaged in the respective activity.</li>
 *   <li>Bot Activity Control: The bot uses these categories to manage when it should take action or pause, based
 *   on whether the player is currently busy performing an activity.</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <p>
 * The <code>Category</code> enum is used within the bot's logic to monitor the player's activities and determine
 * if the bot should continue executing actions or wait for the player to finish their current task. Each category
 * provides specific logic to handle different types of activities such as combat, skilling, and crafting.
 * This class is used by the anti-ban system to make sure the action cooldown is not counting down while the player is busy.
 * </p>
 *
 * <h3>Example:</h3>
 * <pre>
 * Category currentCategory = Category.SKILLING_COOKING;
 * if (currentCategory.isBusyInternal
 *()) {
 *     // The player is busy cooking, so the bot may pause actions.
 * } else {
 *     // The player is idle, and the bot can continue with the next task.
 * }
 * </pre>
 *
 * <h3>Customization:</h3>
 * <p>
 * Each category overrides the <code>isBusyInternal
 *()</code> method to implement custom logic for checking if the player is engaged
 * in a specific task. For example, the <code>COMBAT_MID</code> category checks if the player is in combat, while the
 * <code>SKILLING_COOKING</code> category checks if the player is currently cooking. Some categories are not fully implemented
 * and include TODO notes for further customization based on game-specific conditions.
 * </p>
 *
 * <h3>Development Notes:</h3>
 * <p>
 * Several categories contain TODO notes indicating that additional logic may be required to accurately determine if the player is busy.
 * These categories currently rely on simple checks, such as whether the player is animating or if the inventory is full,
 * but may need further refinement based on specific interactions or game mechanics.
 * </p>
 */

public enum Category {
    COMBAT_MID("Combat/Mid") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Combat.inCombat();
        }
    },
    SKILLING_AGILITY("Skilling/Agility") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Player.isMoving();
        }
    },
    PROCESSING("Processing") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Player.isAnimating() || Microbot.isGainingExp;
        }
    },
    COLLECTING("Collecting") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Player.isMoving() || Rs2Player.isInteracting();
        }
    },
    SKILLING_CRAFTING("Skilling/Crafting") {
        @Override
        public boolean isBusyInternal() {
            return !Rs2Antiban.isIdle();
        }
    },
    SKILLING_MAGIC("Skilling/Magic") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Player.isAnimating() || Microbot.isGainingExp;
        }
    },
    COMBAT_LOW("Combat/Low") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Combat.inCombat();
        }
    },
    SKILLING_HERBLORE("Skilling/Herblore") {
        @Override
        public boolean isBusyInternal() {
            return !Rs2Antiban.isIdle() || Microbot.isGainingExp;
        }
    },
    SKILLING_FLETCHING("Skilling/Fletching") {
        @Override
        public boolean isBusyInternal() {
            return !Rs2Antiban.isIdle();
        }
    },
    SKILLING_FISHING("Skilling/Fishing") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Player.isInteracting();
        }
    },
    PROCESSING_MAGIC("Processing/Magic") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Player.isAnimating() && Microbot.isGainingExp;
        }
    },
    SKILLING_COOKING("Skilling/Cooking") {
        @Override
        public boolean isBusyInternal() {
            return AntibanPlugin.isCooking();
        }
    },
    SKILLING_FIREMAKING("Skilling/Firemaking") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Player.getPoseAnimation() == AnimationID.FIREMAKING;
        }
    },
    SKILLING_THIEVING("Skilling/Thieving") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Player.isAnimating();
        }
    },
    SKILLING("Skilling") {
        @Override
        public boolean isBusyInternal() {
            return !Rs2Player.isAnimating() || !Rs2Inventory.isFull();
        }
    },
    COLLECTING_NONE("Collecting/None") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Player.isMoving();
        }
    },
    COMBAT_HIGH("Combat/High") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Combat.inCombat();
        }
    },
    SKILLING_WOODCUTTING("Skilling/Woodcutting") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Antiban.isWoodcutting();
        }
    },
    SKILLING_MINING("Skilling/Mining") {
        @Override
        public boolean isBusyInternal() {
            return AntibanPlugin.isMining();
        }
    },
    SKILLING_RUNECRAFT("Skilling/Runecraft") {
        @Override
        public boolean isBusyInternal() {
            return Rs2Inventory.contains("pure essence", "rune essence", "Daeyalt essence", "Dark essence fragment");
        }
    },
    SKILLING_SMITHING("Skilling/Smithing") {
        /**
         * Checks if the player is busy.
         * <p>
         * TODO: This method has not been implemented correctly yet.
         * It currently only checks if the player is animating or if the inventory is full.
         * Additional conditions may need to be added to accurately determine if the player is busy.
         * Consider adding checks for:
         * <ul>
         *   <li>Whether the player is interacting with specific game objects.</li>
         *   <li>Other animations that might indicate a busy state.</li>
         *   <li>Game-specific conditions that represent the player being busy.</li>
         * </ul>
         *
         * @return {@code true} if the player is animating or the inventory is full; {@code false} otherwise.
         */
        @Override
        public boolean isBusyInternal() {
            return !Rs2Antiban.isIdle();
        }
    },
    SKILLING_HUNTER("Skilling/Hunter") {
        /**
         * Checks if the player is busy.
         * <p>
         * TODO: This method has not been implemented correctly yet.
         * It currently only checks if the player is animating or if the inventory is full.
         * Additional conditions may need to be added to accurately determine if the player is busy.
         * Consider adding checks for:
         * <ul>
         *   <li>Whether the player is interacting with specific game objects.</li>
         *   <li>Other animations that might indicate a busy state.</li>
         *   <li>Game-specific conditions that represent the player being busy.</li>
         * </ul>
         *
         * @return {@code true} if the player is animating or the inventory is full; {@code false} otherwise.
         */
        @Override
        public boolean isBusyInternal() {
            return !Rs2Antiban.isIdle();
        }
    },
    SKILLING_FARMING("Skilling/Farming") {
        /**
         * Checks if the player is busy.
         * <p>
         * TODO: This method has not been implemented correctly yet.
         * It currently only checks if the player is animating or if the inventory is full.
         * Additional conditions may need to be added to accurately determine if the player is busy.
         * Consider adding checks for:
         * <ul>
         *   <li>Whether the player is interacting with specific game objects.</li>
         *   <li>Other animations that might indicate a busy state.</li>
         *   <li>Game-specific conditions that represent the player being busy.</li>
         * </ul>
         *
         * @return {@code true} if the player is animating or the inventory is full; {@code false} otherwise.
         */
        @Override
        public boolean isBusyInternal() {
            return Rs2Player.isAnimating();
        }
    },
    SKILLING_PRAYER("Skilling/Prayer") {
        /**
         * Checks if the player is busy.
         * <p>
         * TODO: This method has not been implemented correctly yet.
         * It currently only checks if the player is animating or if the inventory is full.
         * Additional conditions may need to be added to accurately determine if the player is busy.
         * Consider adding checks for:
         * <ul>
         *   <li>Whether the player is interacting with specific game objects.</li>
         *   <li>Other animations that might indicate a busy state.</li>
         *   <li>Game-specific conditions that represent the player being busy.</li>
         * </ul>
         *
         * @return {@code true} if the player is animating or the inventory is full; {@code false} otherwise.
         */
        @Override
        public boolean isBusyInternal() {
            return Rs2Player.isAnimating();
        }
    },
    SKILLING_CONSTRUCTION("Skilling/Construction") {
        /**
         * Checks if the player is busy.
         * <p>
         * TODO: This method has not been implemented correctly yet.
         * It currently only checks if the player is animating or if the inventory is full.
         * Additional conditions may need to be added to accurately determine if the player is busy.
         * Consider adding checks for:
         * <ul>
         *   <li>Whether the player is interacting with specific game objects.</li>
         *   <li>Other animations that might indicate a busy state.</li>
         *   <li>Game-specific conditions that represent the player being busy.</li>
         * </ul>
         *
         * @return {@code true} if the player is animating or the inventory is full; {@code false} otherwise.
         */
        @Override
        public boolean isBusyInternal() {
            return Rs2Player.isAnimating();
        }
    };

    private final String name;

    Category(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isBusy() {
        if (!Microbot.isLoggedIn()) {
            return false;
        }
        return isBusyInternal();
    }

    public abstract boolean isBusyInternal();
}
