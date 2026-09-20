package net.runelite.client.plugins.microbot.util.menu;

import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.plugins.microbot.Microbot;

/** One menu attempt, owned while the input loop's gesture lock is held. */
public final class PendingMenuAction implements AutoCloseable {
    private static final AtomicReference<PendingMenuAction> ACTIVE = new AtomicReference<>();
    private final NewMenuEntry entry;
    private volatile boolean prepared;
    private volatile boolean acknowledged;

    public PendingMenuAction(NewMenuEntry entry) {
        this.entry = entry;
        if (!ACTIVE.compareAndSet(null, this)) throw new IllegalStateException("Menu attempt already active");
        Microbot.targetMenu = entry;
    }

    public boolean isAcknowledged() { return acknowledged; }
    public boolean isPrepared() { return prepared; }

    public static void prepared(MenuEntry entry) {
        PendingMenuAction pending = ACTIVE.get();
        if (pending != null && pending.entry == entry) pending.prepared = true;
    }

    public static void observe(MenuOptionClicked event) {
        PendingMenuAction pending = ACTIVE.get();
        if (pending == null || event.isConsumed()) return;
        MenuEntry actual = event.getMenuEntry();
        NewMenuEntry expected = pending.entry;
        if (actual.getType() == expected.getType() && actual.getIdentifier() == expected.getIdentifier()
                && actual.getParam0() == expected.getParam0() && actual.getParam1() == expected.getParam1()
                && actual.getWorldViewId() == expected.getWorldViewId()) pending.acknowledged = true;
    }

    @Override public void close() {
        ACTIVE.compareAndSet(this, null);
        if (Microbot.targetMenu == entry) Microbot.targetMenu = null;
    }
}
