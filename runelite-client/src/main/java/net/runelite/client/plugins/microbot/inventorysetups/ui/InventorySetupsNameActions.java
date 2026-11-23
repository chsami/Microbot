/*
 * Copyright (c) 2022, dillydill123 <https://github.com/dillydill123>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.inventorysetups.ui;

import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsDisplayAttributes;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsValidName;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import lombok.Getter;
import net.runelite.client.ui.ClientTheme;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PanelFonts;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.components.FlatTextField;

import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsStandardPanel.DISPLAY_COLOR_HOVER_ICON;
import static net.runelite.client.plugins.microbot.inventorysetups.ui.InventorySetupsStandardPanel.DISPLAY_COLOR_ICON;

public class InventorySetupsNameActions<T extends InventorySetupsDisplayAttributes> extends JPanel
{
	private static final Border NAME_BOTTOM_BORDER = new CompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR));


	public final T datum;
	private final JLabel save = new JLabel("Save");
	private final JLabel cancel = new JLabel("Cancel");
	private final JLabel edit = new JLabel("Edit");
	private final JLabel displayColorIndicator = new JLabel();
	@Getter
	private final FlatTextField nameInput = new FlatTextField();

	private final InventorySetupsValidName validNameImplementer;

	public InventorySetupsNameActions(final T datum,
										final MInventorySetupsPlugin plugin,
										final InventorySetupsPluginPanel panel,
										final InventorySetupsValidName validNameImplementer,
										final JPopupMenu movePopupMenu, int maxLength,
										final Color panelColor, boolean allowEditable,
										final MouseAdapter flatTextFieldMouseAdapter)
	{
		setLayout(new BorderLayout());

		this.datum = datum;
		this.validNameImplementer = validNameImplementer;

		setBackground(panelColor);

		Color currentDisplayColor;
		if (datum.getDisplayColor() == null)
		{
			setBorder(NAME_BOTTOM_BORDER);
			currentDisplayColor = null;
		}
		else
		{
			setBorder(new CompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 2, 0, datum.getDisplayColor()),
					BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR)));
			currentDisplayColor = datum.getDisplayColor();
		}

		JPanel nameActions = new JPanel(new BorderLayout(3, 0));
		nameActions.setBorder(new EmptyBorder(0, 0, 0, 8));
		nameActions.setBackground(panelColor);

		// Limit character input
		AbstractDocument doc = (AbstractDocument)nameInput.getDocument();
		doc.setDocumentFilter(new DocumentFilter()
		{
			@Override
			public void insertString(FilterBypass fb, int offset, String str, AttributeSet a) throws BadLocationException
			{
				if ((fb.getDocument().getLength() + str.length()) <= maxLength)
				{
					super.insertString(fb, offset, str, a);
				}
			}

			// Replace handles pasting
			@Override
			public void replace(FilterBypass fb, int offset, int length, String str, AttributeSet a) throws BadLocationException
			{
				if (str != null && (fb.getDocument().getLength() + str.length() - length) >= maxLength)
				{
					// If the user pastes a huge amount of text, cut it out until the maximum length is achieved
					int chars_available = maxLength - (fb.getDocument().getLength() - length);
					int chars_to_cut = str.length() - chars_available;
					str = str.substring(0, str.length() - chars_to_cut);
				}
				super.replace(fb, offset, length, str, a);
			}
		});

		// Add document listener to disable save button when the name isn't valid
		nameInput.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateSaveButtonDuringEditing();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateSaveButtonDuringEditing();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateSaveButtonDuringEditing();
			}
		});
		
		nameInput.getTextField().addMouseListener(flatTextFieldMouseAdapter);

		save.setVisible(false);
		save.setFont(PanelFonts.small());
		save.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		save.setBackground(panelColor);
		save.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				if (SwingUtilities.isLeftMouseButton(mouseEvent) && save.isEnabled())
				{
					nameInput.getTextField().addMouseListener(flatTextFieldMouseAdapter);
					validNameImplementer.updateName(nameInput.getText());
					Color newDisplayColor = null;
					if (displayColorIndicator.getBorder() != null)
					{
						Color currentDisplayColor = ((MatteBorder)((CompoundBorder) displayColorIndicator.getBorder()).getInsideBorder()).getMatteColor();
						if (currentDisplayColor != JagexColors.MENU_TARGET)
						{
							newDisplayColor = currentDisplayColor;
						}
					}

					datum.setDisplayColor(newDisplayColor);

					plugin.getDataManager().updateConfig(true, true);

					nameInput.setEditable(false);
					updateNameActions(false);
					requestFocusInWindow();
					panel.redrawOverviewPanel(false);
				}
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent)
			{
				if (save.isEnabled())
				{
					save.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR.darker());
				}
				else
				{
					save.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
				}
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent)
			{
				if (save.isEnabled())
				{
					save.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
				}
				else
				{
					save.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
				}
			}
		});

		cancel.setVisible(false);
		cancel.setFont(PanelFonts.small());
		cancel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		cancel.setBackground(panelColor);
		cancel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				if (SwingUtilities.isLeftMouseButton(mouseEvent))
				{
					nameInput.getTextField().addMouseListener(flatTextFieldMouseAdapter);
					nameInput.setEditable(false);
					nameInput.setText(datum.getName());
					nameInput.getTextField().setCaretPosition(0);
					updateNameActions(false);
					requestFocusInWindow();
					updateDisplayColorLabel(currentDisplayColor);
				}
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent)
			{
				cancel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR.darker());
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent)
			{
				cancel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			}
		});

		edit.setFont(PanelFonts.small());
		edit.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
		edit.setBackground(panelColor);
		edit.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				if (SwingUtilities.isLeftMouseButton(mouseEvent))
				{
					// Remove the mouse listener so clicking it doesn't exit the edit screen
					nameInput.getTextField().removeMouseListener(flatTextFieldMouseAdapter);
					nameInput.setEditable(true);
					updateNameActions(true);
				}
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent)
			{
				edit.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker().darker());
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent)
			{
				edit.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
			}
		});

		nameActions.add(save, BorderLayout.EAST);
		nameActions.add(cancel, BorderLayout.WEST);
		nameActions.add(edit, BorderLayout.CENTER);

		nameInput.setText(datum.getName());
		nameInput.setBorder(null);
		nameInput.setEditable(false);
		nameInput.setBackground(panelColor);
		nameInput.setPreferredSize(new Dimension(0, 24));
		nameInput.getTextField().setForeground(ClientTheme.classicOr(Color.WHITE, ColorScheme.TEXT_COLOR));
		nameInput.getTextField().setBackground(panelColor);
		nameInput.getTextField().setBorder(new EmptyBorder(0, 6, 0, 0));
		if (allowEditable)
		{
			nameInput.getTextField().setComponentPopupMenu(movePopupMenu);
		}
		nameInput.getTextField().setCaretPosition(0);

		displayColorIndicator.setToolTipText("Edit the color of the name");
		displayColorIndicator.setIcon(DISPLAY_COLOR_ICON);
		displayColorIndicator.setBackground(panelColor);
		displayColorIndicator.setVisible(false);

		// Right click menu to remove the color on the setup
		JPopupMenu displayColorMenu = new JPopupMenu();
		JMenuItem removeColor = new JMenuItem("Remove the color of the name");
		displayColorMenu.add(removeColor);
		removeColor.addActionListener(e -> updateDisplayColorLabel(null));

		displayColorIndicator.setComponentPopupMenu(displayColorMenu);

		updateDisplayColorLabel(currentDisplayColor);
		displayColorIndicator.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				if (SwingUtilities.isLeftMouseButton(mouseEvent))
				{
					plugin.openColorPicker("Choose a Display color", currentDisplayColor == null ? JagexColors.MENU_TARGET : currentDisplayColor,
							c ->
							{
								Color newColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
								updateDisplayColorLabel(newColor);
							}
					);
				}
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent)
			{
				displayColorIndicator.setIcon(DISPLAY_COLOR_HOVER_ICON);
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent)
			{
				displayColorIndicator.setIcon(DISPLAY_COLOR_ICON);
			}
		});

		final JPanel wrapper = new JPanel();
		wrapper.setBackground(panelColor);
		wrapper.setLayout(new BorderLayout());
		wrapper.add(nameInput, BorderLayout.CENTER);
		wrapper.add(displayColorIndicator, BorderLayout.WEST);
		if (allowEditable)
		{
			wrapper.add(nameActions, BorderLayout.EAST);
		}
		add(wrapper, BorderLayout.CENTER);

		// Any modifications to the name actions can be done with border layouts

	}

	private void updateNameActions(boolean saveAndCancel)
	{
		save.setVisible(saveAndCancel);
		cancel.setVisible(saveAndCancel);
		edit.setVisible(!saveAndCancel);
		displayColorIndicator.setVisible(saveAndCancel);

		if (saveAndCancel)
		{
			nameInput.getTextField().requestFocusInWindow();
			nameInput.getTextField().selectAll();
		}
	}

	private void updateDisplayColorLabel(Color color)
	{
		displayColorIndicator.setBorder(new CompoundBorder(
				new EmptyBorder(0, 4, 0, 0),
				new MatteBorder(0, 0, 3, 0, color)));

		// Update the save button
		updateSaveButtonDuringEditing();
	}

	private void updateSaveButtonDuringEditing()
	{
		Color newDisplayColor = null;
		if (displayColorIndicator.getBorder() != null)
		{
			newDisplayColor = ((MatteBorder)((CompoundBorder) displayColorIndicator.getBorder()).getInsideBorder()).getMatteColor();
		}

		if (!validNameImplementer.isNameValid(nameInput.getText(), newDisplayColor))
		{
			save.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
			save.setEnabled(false);
			return;
		}

		save.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		save.setEnabled(true);

	}

}
