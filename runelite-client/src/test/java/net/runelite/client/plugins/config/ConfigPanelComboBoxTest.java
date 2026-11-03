package net.runelite.client.plugins.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import javax.swing.JComboBox;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigPanelComboBoxTest
{
	enum Choice
	{
		FIRST,
		SECOND
	}

	@ConfigGroup("comboboxtest")
	interface ComboBoxTestConfig extends Config
	{
		@ConfigItem(
			keyName = "choice",
			name = "Choice",
			description = ""
		)
		default Choice choice()
		{
			return null;
		}
	}

	private ConfigManager configManager;
	private ConfigPanel panel;
	private ConfigDescriptor descriptor;
	private ConfigItemDescriptor itemDescriptor;

	@Before
	public void setUp() throws Exception
	{
		configManager = mock(ConfigManager.class);

		Constructor<ConfigPanel> constructor = ConfigPanel.class.getDeclaredConstructor(
			PluginListPanel.class,
			ConfigManager.class,
			PluginManager.class,
			ExternalPluginManager.class,
			ColorPickerManager.class,
			javax.inject.Provider.class,
			javax.inject.Provider.class);
		constructor.setAccessible(true);
		panel = constructor.newInstance(null, configManager, null, null, null, null, null);

		ConfigGroup group = ComboBoxTestConfig.class.getAnnotation(ConfigGroup.class);
		ConfigItem item = ComboBoxTestConfig.class.getMethod("choice").getAnnotation(ConfigItem.class);
		itemDescriptor = new ConfigItemDescriptor(item, Choice.class, null, null, null);
		descriptor = new ConfigDescriptor(group, Collections.emptyList(), Collections.singletonList(itemDescriptor), null);
	}

	@SuppressWarnings("unchecked")
	private JComboBox<Enum<?>> createComboBox() throws Exception
	{
		Method method = ConfigPanel.class.getDeclaredMethod("createComboBox", ConfigDescriptor.class, ConfigItemDescriptor.class);
		method.setAccessible(true);
		return (JComboBox<Enum<?>>) method.invoke(panel, descriptor, itemDescriptor);
	}

	@Test
	public void createsComboBoxWhenEnumItemHasNoStoredValue() throws Exception
	{
		when(configManager.getConfiguration("comboboxtest", "choice")).thenReturn(null);

		JComboBox<Enum<?>> box = createComboBox();

		assertNotNull(box);
		assertEquals(2, box.getItemCount());
	}

	@Test
	public void selectsStoredEnumValue() throws Exception
	{
		when(configManager.getConfiguration("comboboxtest", "choice")).thenReturn("SECOND");

		JComboBox<Enum<?>> box = createComboBox();

		assertEquals(Choice.SECOND, box.getSelectedItem());
	}

	@Test
	public void createsComboBoxWhenStoredValueIsNotAConstant() throws Exception
	{
		when(configManager.getConfiguration("comboboxtest", "choice")).thenReturn("REMOVED");

		JComboBox<Enum<?>> box = createComboBox();

		assertNotNull(box);
		assertEquals(2, box.getItemCount());
	}
}
