package net.runelite.client.ui.components;

import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PanelFonts;

/**
 * "Key: value" text with a muted key and a white value, in the panel font. Two plain labels rather than an HTML
 * label, because HTML text cannot use the panel fonts (they are not registered by name).
 */
public class KeyValueLabel extends JPanel
{
	// one line: the key keeps its width, the value gets the rest and is cut with "..." when the cell is too narrow
	private final JPanel row = new JPanel(new BorderLayout());
	private final JLabel key = new JLabel();
	private final JLabel value = new JLabel();

	public KeyValueLabel()
	{
		this(ColorScheme.LIGHT_GRAY_COLOR, Color.WHITE);
	}

	public KeyValueLabel(Color keyColor, Color valueColor)
	{
		super(new GridBagLayout());
		setOpaque(false);
		row.setOpaque(false);
		key.setFont(PanelFonts.small());
		value.setFont(PanelFonts.small());
		key.setForeground(keyColor);
		value.setForeground(valueColor);
		row.add(key, BorderLayout.WEST);
		row.add(value, BorderLayout.CENTER);
		setVerticalAlignment(SwingConstants.CENTER);
	}

	public void set(String key, String value)
	{
		this.key.setText(key);
		this.value.setText(value);
	}

	public String getKey()
	{
		return key.getText();
	}

	public String getValue()
	{
		return value.getText();
	}

	/** SwingConstants.TOP, CENTER or BOTTOM, like JLabel. */
	public void setVerticalAlignment(int alignment)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.weightx = 1;
		c.weighty = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = alignment == SwingConstants.TOP ? GridBagConstraints.NORTHWEST
			: alignment == SwingConstants.BOTTOM ? GridBagConstraints.SOUTHWEST : GridBagConstraints.WEST;
		removeAll();
		add(row, c);
		revalidate();
	}

	@Override
	public void setToolTipText(String text)
	{
		// the labels take the mouse events, so they need the tooltip too
		super.setToolTipText(text);
		key.setToolTipText(text);
		value.setToolTipText(text);
	}
}
