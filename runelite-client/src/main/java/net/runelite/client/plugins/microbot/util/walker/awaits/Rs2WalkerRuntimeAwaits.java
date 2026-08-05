package net.runelite.client.plugins.microbot.util.walker.awaits;

import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.function.BooleanSupplier;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public final class Rs2WalkerRuntimeAwaits {
    private Rs2WalkerRuntimeAwaits() {
    }

    public static boolean awaitCondition(BooleanSupplier condition, int pollMs, int timeoutMs) {
        if (condition == null) {
            return false;
        }
        return sleepUntilTrue(condition, pollMs, timeoutMs);
    }

    public static boolean awaitMovementSettled(int pollMs, int timeoutMs) {
        return sleepUntilTrue(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(), pollMs, timeoutMs);
    }
}
