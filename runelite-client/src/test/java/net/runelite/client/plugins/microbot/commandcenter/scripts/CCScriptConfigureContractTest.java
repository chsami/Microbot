package net.runelite.client.plugins.microbot.commandcenter.scripts;

import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCBehavior;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCScript;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCScriptTestUtils;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors.DeathRecoveryBehavior;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors.StuckDetectionBehavior;
import net.runelite.client.plugins.microbot.commandcenter.scripts.woodcutting.CCWoodcuttingScript;
import net.runelite.client.plugins.microbot.commandcenter.scripts.mining.CCMiningScript;
import net.runelite.client.plugins.microbot.commandcenter.scripts.fishing.CCFishingScript;
import net.runelite.client.plugins.microbot.commandcenter.scripts.cooking.CCCookingScript;
import net.runelite.client.plugins.microbot.commandcenter.scripts.combat.CCCombatScript;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Verifies that every CC script's configure() method satisfies framework contracts.
 * Adding a new script means adding one line to the parameters list.
 */
@RunWith(Parameterized.class)
public class CCScriptConfigureContractTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> scripts() {
        return Arrays.asList(new Object[][]{
            {"CCWoodcuttingScript", CCWoodcuttingScript.class},
            {"CCMiningScript", CCMiningScript.class},
            {"CCFishingScript", CCFishingScript.class},
            {"CCCookingScript", CCCookingScript.class},
            {"CCCombatScript", CCCombatScript.class},
        });
    }

    private final String scriptName;
    private final Class<? extends CCScript<?>> scriptClass;

    @SuppressWarnings("unchecked")
    public CCScriptConfigureContractTest(String scriptName, Class<?> scriptClass) {
        this.scriptName = scriptName;
        this.scriptClass = (Class<? extends CCScript<?>>) scriptClass;
    }

    private CCScript<?> createAndConfigure() {
        try {
            CCScript<?> script = scriptClass.getDeclaredConstructor().newInstance();
            CCScriptTestUtils.callConfigure(script);
            return script;
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate " + scriptName, e);
        }
    }

    @Test
    public void configure_registersDeathRecoveryBehavior() {
        List<CCBehavior> behaviors = CCScriptTestUtils.getBehaviors(createAndConfigure());
        assertTrue(scriptName + " must register DeathRecoveryBehavior",
            behaviors.stream().anyMatch(b -> b instanceof DeathRecoveryBehavior));
    }

    @Test
    public void configure_registersStuckDetectionBehavior() {
        List<CCBehavior> behaviors = CCScriptTestUtils.getBehaviors(createAndConfigure());
        assertTrue(scriptName + " must register StuckDetectionBehavior",
            behaviors.stream().anyMatch(b -> b instanceof StuckDetectionBehavior));
    }

    @Test
    public void configure_stuckDetectionHasScriptReference() {
        CCScript<?> script = createAndConfigure();
        StuckDetectionBehavior stuck = CCScriptTestUtils.getBehaviors(script).stream()
            .filter(b -> b instanceof StuckDetectionBehavior)
            .map(b -> (StuckDetectionBehavior) b)
            .findFirst().orElse(null);
        assertNotNull("StuckDetection must be registered", stuck);

        // Verify setScript(this) was called by reading the private field
        try {
            Field f = StuckDetectionBehavior.class.getDeclaredField("script");
            f.setAccessible(true);
            assertNotNull(scriptName + " must call stuck.setScript(this)", f.get(stuck));
        } catch (Exception e) {
            throw new RuntimeException("Cannot read StuckDetection.script field", e);
        }
    }

    @Test
    public void configure_setsAntiBanTemplate() {
        assertTrue(scriptName + " must call setAntiBanTemplate()",
            CCScriptTestUtils.hasAntiBanTemplate(createAndConfigure()));
    }
}
