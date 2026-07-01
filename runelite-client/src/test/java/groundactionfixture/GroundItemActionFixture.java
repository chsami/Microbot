package groundactionfixture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public final class GroundItemActionFixture {
    private GroundItemActionFixture() {
    }

    public static Object create(String... actions) {
        return new FakeItem(actions);
    }

    public static Object createWithListInterface(String... actions) {
        return new FakeListItem(actions);
    }

    public static Object createWithoutGroundActions() {
        return new FakeItemWithoutGroundActions();
    }

    public static Object createWithNullGroundOps() {
        return new FakeItem(null);
    }

    public static Object createWithFlexibleActions(String... actions) {
        return new FakeFlexibleItem(new ArrayList<>(Arrays.asList(actions)));
    }

    public static Object createWithInvalidFlexibleActions() {
        return new FakeFlexibleItem("not a list");
    }

    private static final class FakeItemWithoutGroundActions {
        private final String name = "Amethyst";
    }

    private static final class FakeItem {
        private static final MisleadingOuter STATIC_OUTER = new MisleadingOuter();
        private final GroundOps groundOps;

        private FakeItem(String[] actions) {
            groundOps = actions == null ? null : new GroundOps(actions);
        }
    }

    private static final class FakeFlexibleItem {
        private final FlexibleGroundOps groundOps;

        private FakeFlexibleItem(Object actions) {
            groundOps = new FlexibleGroundOps(actions);
        }
    }

    private static final class FlexibleGroundOps {
        private final Object actions;

        private FlexibleGroundOps(Object actions) {
            this.actions = actions;
        }
    }

    private static final class MisleadingOuter {
        private final ArrayList<ActionBean> actions = new ArrayList<>(
                Arrays.asList(new ActionBean("Wrong static outer action")));
    }

    private static final class GroundOps {
        private static final ArrayList<ActionBean> STATIC_ACTIONS = new ArrayList<>(
                Arrays.asList(new ActionBean("Wrong static list action")));
        private final ArrayList<ActionBean> actions;

        private GroundOps(String[] actions) {
            this.actions = new ArrayList<>();
            for (String action : actions) {
                this.actions.add(new ActionBean(action));
            }
        }
    }

    private static final class FakeListItem {
        private static final GroundOpsList STATIC_OUTER = new GroundOpsList("Wrong static outer action");
        private final GroundOpsList groundOps;

        private FakeListItem(String[] actions) {
            groundOps = new GroundOpsList(actions);
        }
    }

    private static final class GroundOpsList {
        private static final List<ActionBean> STATIC_ACTIONS = new LinkedList<>(
                Arrays.asList(new ActionBean("Wrong static list action")));
        private final List<ActionBean> actions = new LinkedList<>();

        private GroundOpsList(String... actions) {
            for (String action : actions) this.actions.add(new ActionBean(action));
        }
    }

    private static final class ActionBean {
        private static final String STATIC_ACTION = "Wrong static string action";
        private final String action;

        private ActionBean(String action) {
            this.action = action;
        }
    }
}
