package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AntibanNaturalMouseChoiceTest {

	private Object previousConfigManager;

	@Before
	public void setUp() throws Exception {
		previousConfigManager = swapConfigManager(null);
		Rs2Antiban.resetAntibanSettings(true);
		Rs2AntibanSettings.overwriteScriptSettings = false;
	}

	@After
	public void tearDown() throws Exception {
		Rs2AntibanSettings.overwriteScriptSettings = false;
		Rs2Antiban.resetAntibanSettings(true);
		swapConfigManager(previousConfigManager);
	}

	@Test
	public void templatesKeepThePlayersChoice() throws Exception {
		AntibanSetupTemplates templates = new AntibanSetupTemplates();
		int applied = 0;
		for (Method method : AntibanSetupTemplates.class.getDeclaredMethods()) {
			if (!method.getName().startsWith("apply") || method.getParameterCount() != 0) {
				continue;
			}
			Rs2AntibanSettings.naturalMouse = false;
			method.invoke(templates);
			assertFalse(method.getName() + " turned the player's setting back on", Rs2AntibanSettings.naturalMouse);
			applied++;
		}
		assertTrue("expected the antiban templates to be exercised", applied > 0);
	}

	@Test
	public void scriptResetRestoresTheSavedChoice() throws Exception {
		savedSettings("{\"naturalMouse\":false}");
		Rs2AntibanSettings.naturalMouse = true;

		Rs2Antiban.resetAntibanSettings();

		assertFalse(Rs2AntibanSettings.naturalMouse);
	}

	@Test
	public void scriptResetFallsBackToDefaultWhenNothingIsSaved() throws Exception {
		savedSettings(null);
		Rs2AntibanSettings.naturalMouse = false;

		Rs2Antiban.resetAntibanSettings();

		assertTrue(Rs2AntibanSettings.naturalMouse);
	}

	@Test
	public void resetButtonStillRestoresTheDefault() throws Exception {
		savedSettings("{\"naturalMouse\":false}");
		Rs2AntibanSettings.naturalMouse = false;

		Rs2Antiban.resetAntibanSettings(true);

		assertTrue(Rs2AntibanSettings.naturalMouse);
	}

	private static void savedSettings(String json) throws Exception {
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration("MicrobotAntiban", "settings")).thenReturn(json);
		swapConfigManager(configManager);
	}

	private static Object swapConfigManager(Object value) throws Exception {
		Field field = Microbot.class.getDeclaredField("configManager");
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, value);
		return previous;
	}
}
