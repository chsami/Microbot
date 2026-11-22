package net.runelite.client.plugins.microbot.statemachine;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StateMachineScriptRestartTest {
    private static final String NAME = Machine.class.getSimpleName();

    @Test public void restartReRegistersAndBeginsFromInitialState() {
        Machine machine = new Machine();
        try (MockedStatic<Microbot> ignored = mockStatic(Microbot.class)) {
            machine.advance = true;
            assertTrue(machine.step());
            assertEquals(Machine.State.WORKING, machine.getCurrentState());
            assertSame(machine, StateMachineScript.getRegistry().get(NAME));

            machine.shutdown();
            assertFalse(StateMachineScript.getRegistry().containsKey(NAME));
            assertNull(machine.getSnapshot());

            machine.advance = false;
            assertTrue(machine.step());
            assertEquals(Machine.State.IDLE, machine.getCurrentState());
            assertSame(machine, StateMachineScript.getRegistry().get(NAME));
            StateSnapshot<Machine.State> snapshot = machine.getSnapshot();
            assertEquals(Machine.State.IDLE, snapshot.currentState());
            assertNull(snapshot.previousState());
            assertEquals(1, snapshot.loopCount());
            assertEquals(0, snapshot.transitionCount());
            assertTrue(snapshot.recentTrace().isEmpty());
            assertEquals(List.of(Machine.State.WORKING, Machine.State.IDLE), machine.visited);

            machine.shutdown();
            assertFalse(StateMachineScript.getRegistry().containsKey(NAME));
        } finally {
            machine.shutdown();
            machine.dispose();
        }
    }

    @Test public void staleInstanceStopDoesNotUnregisterItsReplacement() {
        Machine first = new Machine();
        Machine second = new Machine();
        try (MockedStatic<Microbot> ignored = mockStatic(Microbot.class)) {
            first.step();
            second.step();
            first.shutdown();
            assertSame(second, StateMachineScript.getRegistry().get(NAME));
            second.shutdown();
            assertFalse(StateMachineScript.getRegistry().containsKey(NAME));
        } finally {
            first.shutdown();
            second.shutdown();
            first.dispose();
            second.dispose();
        }
    }

    @Test public void cancelledStepAfterStopDoesNotReRegister() {
        Machine machine = new Machine();
        try (MockedStatic<Microbot> ignored = mockStatic(Microbot.class)) {
            machine.step();
            machine.shutdown();
            Thread.currentThread().interrupt();
            try {
                assertFalse(machine.step());
            } finally {
                Thread.interrupted();
            }
            assertFalse(StateMachineScript.getRegistry().containsKey(NAME));
            assertNull(machine.getSnapshot());
        } finally {
            machine.shutdown();
            machine.dispose();
        }
    }

    @Test public void stopDuringStateActionDoesNotRepublishSnapshot() {
        Machine machine = new Machine();
        try (MockedStatic<Microbot> ignored = mockStatic(Microbot.class)) {
            machine.stopInAction = true;
            machine.step();
            assertFalse(StateMachineScript.getRegistry().containsKey(NAME));
            assertNull(machine.getSnapshot());
        } finally {
            machine.shutdown();
            machine.dispose();
        }
    }

    private static final class Machine extends StateMachineScript<Machine.State> {
        enum State { IDLE, WORKING }

        boolean advance;
        boolean stopInAction;
        final List<State> visited = new ArrayList<>();

        @Override protected State onError(State state, Exception e) {
            return State.WORKING;
        }

        @Override protected boolean runsWhileLoggedOut() {
            return true;
        }

        @Override protected State initialState() {
            return State.IDLE;
        }

        @Override protected List<Transition<State>> defineTransitions() {
            return List.of(Transition.from(State.IDLE).when(() -> advance, "advance")
                    .because("advance").goTo(State.WORKING));
        }

        @Override protected void onState(State state) {
            visited.add(state);
            if (stopInAction) {
                shutdown();
                throw new IllegalStateException("stopped");
            }
        }

        void dispose() {
            scheduledExecutorService.shutdownNow();
        }
    }
}
