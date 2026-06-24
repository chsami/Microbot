package net.runelite.client.plugins.microbot.util.reflection;

import groundactionfixture.GroundItemActionFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class Rs2ReflectionGroundItemActionsTest {
    @Before
    public void setUp() {
        Rs2Reflection.resetGroundItemActionCache();
    }

    @After
    public void tearDown() {
        Rs2Reflection.resetGroundItemActionCache();
    }

    @Test
    public void discoveryIgnoresMisleadingStaticFields() {
        String[] actions = Rs2Reflection.getGroundItemActionsFromObject(
                GroundItemActionFixture.create("Take", "Bury"));

        assertArrayEquals(new String[]{"Take", "Bury"}, actions);
    }

    @Test
    public void cachedInstanceFieldChainWorksAcrossItems() {
        Rs2Reflection.getGroundItemActionsFromObject(GroundItemActionFixture.create("Take"));

        String[] actions = Rs2Reflection.getGroundItemActionsFromObject(
                GroundItemActionFixture.create("Take", "Destroy"));

        assertArrayEquals(new String[]{"Take", "Destroy"}, actions);
    }

    @Test
    public void discoverySupportsListInterfaceAndNonArrayListImplementation() {
        String[] actions = Rs2Reflection.getGroundItemActionsFromObject(
                GroundItemActionFixture.createWithListInterface("Take", "Destroy"));

        assertArrayEquals(new String[]{"Take", "Destroy"}, actions);
    }
}
