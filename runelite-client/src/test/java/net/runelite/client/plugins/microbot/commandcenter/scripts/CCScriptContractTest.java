package net.runelite.client.plugins.microbot.commandcenter.scripts;

import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.commandcenter.scripts.woodcutting.CCWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.commandcenter.scripts.mining.CCMiningPlugin;
import net.runelite.client.plugins.microbot.commandcenter.scripts.fishing.CCFishingPlugin;
import net.runelite.client.plugins.microbot.commandcenter.scripts.cooking.CCCookingPlugin;
import net.runelite.client.plugins.microbot.commandcenter.scripts.combat.CCCombatPlugin;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class CCScriptContractTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> scripts() {
        return Arrays.asList(new Object[][]{
            {"CC Woodcutter", CCWoodcuttingPlugin.class},
            {"CC Miner", CCMiningPlugin.class},
            {"CC Fisher", CCFishingPlugin.class},
            {"CC Cooker", CCCookingPlugin.class},
            {"CC Combat Trainer", CCCombatPlugin.class},
        });
    }

    private final String expectedName;
    private final Class<?> pluginClass;

    public CCScriptContractTest(String expectedName, Class<?> pluginClass) {
        this.expectedName = expectedName;
        this.pluginClass = pluginClass;
    }

    private PluginDescriptor getDescriptor() {
        return pluginClass.getAnnotation(PluginDescriptor.class);
    }

    @Test
    public void pluginDescriptor_exists() {
        assertNotNull("Missing @PluginDescriptor on " + pluginClass.getSimpleName(),
            getDescriptor());
    }

    @Test
    public void pluginDescriptor_nameStartsWithCC() {
        assertTrue("Name should start with 'CC ': " + getDescriptor().name(),
            getDescriptor().name().startsWith("CC "));
    }

    @Test
    public void pluginDescriptor_nameMatchesExpected() {
        assertEquals(expectedName, getDescriptor().name());
    }

    @Test
    public void pluginDescriptor_enabledByDefaultIsFalse() {
        assertFalse("enabledByDefault should be false",
            getDescriptor().enabledByDefault());
    }

    @Test
    public void pluginDescriptor_hasCommandCenterTag() {
        assertTrue("Tags should include 'commandcenter'",
            Arrays.asList(getDescriptor().tags()).contains("commandcenter"));
    }

    @Test
    public void pluginDescriptor_hasMicrobotTag() {
        assertTrue("Tags should include 'microbot'",
            Arrays.asList(getDescriptor().tags()).contains("microbot"));
    }

    @Test
    public void pluginClass_extendsMicrobotPlugin() {
        assertTrue(pluginClass.getSimpleName() + " should extend MicrobotPlugin",
            net.runelite.client.plugins.microbot.MicrobotPlugin.class.isAssignableFrom(pluginClass));
    }
}
