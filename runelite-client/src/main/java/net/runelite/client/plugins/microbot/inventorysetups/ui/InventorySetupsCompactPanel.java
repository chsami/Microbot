/*
 * Copyright (c) 2019, dillydill123 <https://github.com/dillydill123>
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


import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsSection;
import net.runelite.client.ui.ClientTheme;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.FlatTextField;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

// The compact setup panel that only contains the name of the setup
public class InventorySetupsCompactPanel extends InventorySetupsPanel
{

	InventorySetupsCompactPanel(MInventorySetupsPlugin plugin, InventorySetupsPluginPanel panel, InventorySetup invSetup, InventorySetupsSection section)
	{
		this(plugin, panel, invSetup, section, true);
	}

	InventorySetupsCompactPanel(MInventorySetupsPlugin plugin, InventorySetupsPluginPanel panel, InventorySetup invSetup, InventorySetupsSection section, boolean allowEditable)
	{
		super(plugin, panel, invSetup, section, allowEditable);

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		setPreferredSize(new Dimension(0, 24));

		JPanel nameWrapper = new JPanel(new BorderLayout());
		nameWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final FlatTextField nameInput = new FlatTextField();
		nameInput.setText(inventorySetup.getName());
		nameInput.setBorder(new MatteBorder(0, 2, 0, 0, invSetup.getDisplayColor()));
		nameInput.setEditable(false);
		nameInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		nameInput.setPreferredSize(new Dimension(0, 24));
		nameInput.getTextField().setForeground(ClientTheme.classicOr(Color.WHITE, ColorScheme.TEXT_COLOR));
		nameInput.getTextField().setBorder(new EmptyBorder(0, 6, 0, 0));
		nameInput.getTextField().setCaretPosition(0);

		nameWrapper.add(nameInput, BorderLayout.CENTER);

		add(nameWrapper, BorderLayout.NORTH);

		nameInput.getTextField().setComponentPopupMenu(popupMenu);
		nameInput.getTextField().addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					panel.setCurrentInventorySetup(inventorySetup, true);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				nameInput.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				nameInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

	}
}
