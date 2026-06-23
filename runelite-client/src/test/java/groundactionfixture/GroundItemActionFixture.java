package groundactionfixture;

import java.util.ArrayList;
import java.util.Arrays;

public final class GroundItemActionFixture {
    private GroundItemActionFixture() {
    }

    public static Object create(String... actions) {
        return new FakeItem(actions);
    }

    private static final class FakeItem {
        private static final MisleadingOuter STATIC_OUTER = new MisleadingOuter();
        private final GroundOps groundOps;

        private FakeItem(String[] actions) {
            groundOps = new GroundOps(actions);
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

    private static final class ActionBean {
        private static final String STATIC_ACTION = "Wrong static string action";
        private final String action;

        private ActionBean(String action) {
            this.action = action;
        }
    }
}
