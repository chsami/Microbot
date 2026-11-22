package net.runelite.client.plugins.microbot.shortestpath.components;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;
import lombok.Setter;
import net.runelite.client.ui.ClientTheme;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.Text;

public final class ComboBoxListRenderer extends JLabel implements ListCellRenderer
{
    @Setter
    private String defaultText = "Select an option...";

    @Override
    public Component getListCellRendererComponent(JList list, Object o, int index, boolean isSelected, boolean cellHasFocus)
    {
        if (isSelected)
        {
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            setForeground(ClientTheme.classicOr(Color.WHITE, ColorScheme.TEXT_COLOR));
        }
        else
        {
            setBackground(list.getBackground());
            setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        }

        setBorder(new EmptyBorder(5, 10, 5, 10));
        setIcon(null);

        String text;
        // If using setSelectedItem(null) or setSelectedIndex(-1) show default text until a selection is made
        if (index == -1 && o == null)
        {
            text = defaultText;
        }
        else if (o instanceof Enum)
        {
            text = Text.titleCase((Enum) o);
        }
        else if (o instanceof ComboBoxIconEntry)
        {
            ComboBoxIconEntry e = (ComboBoxIconEntry) o;
            text = e.getText();
            setIcon(e.getIcon());
        }
        else
        {
            text = o.toString();
        }

        setText(text);

        return this;
    }
}