package net.runelite.client.plugins.microbot.util.grounditem;

import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.ApiTestClient;
import net.runelite.client.plugins.microbot.util.input.AwtEmitter;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.menu.PendingMenuAction;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class PickupDispatchTest {
    @Test public void microbotMenuSubscriberRegisters() {
        net.runelite.client.eventbus.EventBus bus = new net.runelite.client.eventbus.EventBus();
        net.runelite.client.plugins.microbot.MicrobotPlugin plugin = new net.runelite.client.plugins.microbot.MicrobotPlugin();
        bus.register(plugin);
        bus.unregister(plugin);
    }

    private NewMenuEntry entry() {
        return new NewMenuEntry().identifier(100).param0(10).param1(20)
                .worldViewId(-1).opcode(MenuAction.GROUND_ITEM_THIRD_OPTION.getId()).option("Take");
    }

    @Test public void acknowledgedClickCleansUpItsMenu() throws Exception {
        try (ApiTestClient env = new ApiTestClient(); MockedStatic<AwtEmitter> awt = mockStatic(AwtEmitter.class)) {
            NewMenuEntry entry = entry();
            awt.when(() -> AwtEmitter.moved(anyInt(), anyInt()))
                    .thenAnswer(i -> { PendingMenuAction.prepared(entry); return null; });
            awt.when(() -> AwtEmitter.clicked(anyInt(), anyInt(), anyInt()))
                    .thenAnswer(i -> { PendingMenuAction.observe(new MenuOptionClicked(entry)); return null; });
            assertTrue(new VirtualMouse().tryClick(new Point(1, 1), entry, () -> true));
            assertNull(Microbot.targetMenu);
        }
    }

    @Test public void unpreparedMenuDoesNotPressAndIsCleared() throws Exception {
        try (ApiTestClient env = new ApiTestClient(); MockedStatic<AwtEmitter> awt = mockStatic(AwtEmitter.class)) {
            assertFalse(new VirtualMouse().tryClick(new Point(1, 1), entry(), () -> true));
            awt.verify(() -> AwtEmitter.pressed(anyInt(), anyInt(), anyInt()), never());
            assertNull(Microbot.targetMenu);
        }
    }

    @Test public void staleTargetAndInterruptedCallerDoNotEmit() throws Exception {
        try (ApiTestClient env = new ApiTestClient(); MockedStatic<AwtEmitter> awt = mockStatic(AwtEmitter.class)) {
            VirtualMouse mouse = new VirtualMouse();
            assertFalse(mouse.tryClick(new Point(1, 1), entry(), () -> false));
            Thread.currentThread().interrupt();
            try { assertFalse(mouse.tryClick(new Point(1, 1), entry(), () -> true)); }
            finally { Thread.interrupted(); }
            awt.verifyNoInteractions();
        }
    }

    @Test public void unexpectedEmitterFailureCannotLeaveMenuArmed() throws Exception {
        try (ApiTestClient env = new ApiTestClient(); MockedStatic<AwtEmitter> awt = mockStatic(AwtEmitter.class)) {
            NewMenuEntry entry = entry();
            awt.when(() -> AwtEmitter.moved(anyInt(), anyInt()))
                    .thenAnswer(i -> { PendingMenuAction.prepared(entry); return null; });
            awt.when(() -> AwtEmitter.pressed(anyInt(), anyInt(), anyInt())).thenThrow(new IllegalStateException("expected"));
            try { new VirtualMouse().tryClick(new Point(1, 1), entry, () -> true); fail(); }
            catch (IllegalStateException expected) { }
            assertNull(Microbot.targetMenu);
        }
    }

    @Test public void acknowledgementRequiresUnconsumedMatchingViewAndTarget() {
        NewMenuEntry requested = entry();
        try (PendingMenuAction pending = new PendingMenuAction(requested)) {
            PendingMenuAction.observe(new MenuOptionClicked(entry().worldViewId(12)));
            assertFalse(pending.isAcknowledged());
            MenuOptionClicked consumed = new MenuOptionClicked(requested);
            consumed.consume();
            PendingMenuAction.observe(consumed);
            assertFalse(pending.isAcknowledged());
            PendingMenuAction.observe(new MenuOptionClicked(requested));
            assertTrue(pending.isAcknowledged());
        }
    }
}
