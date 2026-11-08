/*
 * Copyright (c) 2018, Daniel Teo <https://github.com/takuyakanbr>
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
package net.runelite.client.plugins.microbot.ui;

import com.google.common.html.HtmlEscapers;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.AlwaysOnPlugins;
import net.runelite.client.plugins.config.SearchablePlugin;
import net.runelite.client.plugins.microbot.ui.search.MicrobotPluginNames;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

class MicrobotPluginListItem extends JPanel implements SearchablePlugin
{
	private static final ImageIcon ON_STAR;
	private static final ImageIcon OFF_STAR;
	private static final int MIN_ITEM_HEIGHT = 20;
	private static final int SWITCH_WIDTH = 34;
	private static final float DIMMED_ALPHA = 0.6f;

	private final MicrobotPluginListPanel pluginListPanel;

	@Getter
	private final MicrobotPluginConfigurationDescriptor pluginConfig;

	@Getter
	private final List<String> keywords = new ArrayList<>();

	private final JToggleButton pinButton;
	private final MicrobotPluginToggleButton onOffToggle;
	// "Always on" or "Turned off" in place of the switch, when the player cannot flip it.
	private final JLabel sideLabel;

	// Locked core plugins.
	@Getter
	private final boolean alwaysOn;

	private final boolean disabledByAuthor;

	// A settings row such as Microbot's; set by the panel, since a blocked plugin also has no plugin behind it.
	@Setter
	private boolean settingsOnly;

	@Getter
	@Setter
	private long installTime;

	@Getter
	@Setter
	private long usedTime;

	private boolean dimmed;

	@Getter
	private MicrobotPluginRowStatus status;
	private final JButton statusButton = new JButton();
	private final JLabel nameLabel;
	private final int baseHeight;

	static
	{
		BufferedImage onStar = ImageUtil.loadImageResource(MicrobotConfigPanel.class, "star_on.png");
		ON_STAR = new ImageIcon(onStar);

		BufferedImage offStar = ImageUtil.luminanceScale(
			ImageUtil.grayscaleImage(onStar),
			0.77f
		);
		OFF_STAR = new ImageIcon(offStar);
	}

	MicrobotPluginListItem(MicrobotPluginListPanel pluginListPanel, MicrobotPluginConfigurationDescriptor pluginConfig)
	{
		this.pluginListPanel = pluginListPanel;
		this.pluginConfig = pluginConfig;

		Collections.addAll(keywords, MicrobotPluginNames.sortKey(pluginConfig.getName()).split(" "));
		Collections.addAll(keywords, MicrobotPluginNames.searchText(pluginConfig.getDescription()).split(" "));
		Collections.addAll(keywords, pluginConfig.getTags());
		String internalName = pluginConfig.getInternalPluginHubName();
		if (internalName != null)
		{
			keywords.add("pluginhub");
			keywords.add(internalName);
		}
		else
		{
			keywords.add("plugin"); // we don't want searching plugin to only show hub plugins
		}

		setLayout(new BorderLayout(3, 0));

		PluginDescriptor pluginDescriptor = pluginConfig.getPlugin() == null ? null
			: pluginConfig.getPlugin().getClass().getAnnotation(PluginDescriptor.class);
		alwaysOn = AlwaysOnPlugins.isLocked(pluginDescriptor);
		disabledByAuthor = pluginDescriptor != null && pluginDescriptor.disable();
		sideLabel = alwaysOn ? createSideLabel("Always on", AlwaysOnPlugins.TOOLTIP)
			: disabledByAuthor ? createSideLabel("Turned off", "Turned off by its author") : null;
		nameLabel = createNameLabel(pluginConfig, sideLabel != null ? sideLabel.getPreferredSize().width : SWITCH_WIDTH,
			pluginDescriptor == null ? "" : pluginDescriptor.version());
		baseHeight = Math.max(MIN_ITEM_HEIGHT, nameLabel.getPreferredSize().height);
		setPreferredSize(new Dimension(MicrobotPluginListPanel.LIST_ITEM_WIDTH, baseHeight));

		SwingUtil.removeButtonDecorations(statusButton);
		statusButton.setFont(FontManager.getRunescapeSmallFont());
		statusButton.setHorizontalAlignment(SwingConstants.LEFT);
		statusButton.setBorder(new EmptyBorder(0, 24, 2, 0));
		// A Tab stop, not focused by a click: Space, or Enter below, runs it, so keyboard users can update or see why.
		statusButton.setRequestFocusEnabled(false);
		statusButton.setVisible(false);
		statusButton.addActionListener(e -> pluginListPanel.onStatusClicked(this));
		statusButton.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activate");
		statusButton.getActionMap().put("activate", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				statusButton.doClick(0);
			}
		});
		statusButton.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				statusButton.setForeground(ColorScheme.BRAND_ORANGE);
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				statusButton.setForeground(statusColor(status));
			}
		});
		add(statusButton, BorderLayout.SOUTH);

		pinButton = new JToggleButton(OFF_STAR);
		pinButton.setSelectedIcon(ON_STAR);
		SwingUtil.removeButtonDecorations(pinButton);
		SwingUtil.addModalTooltip(pinButton, "Unpin plugin", "Pin plugin");
		pinButton.setPreferredSize(new Dimension(21, 0));
		add(pinButton, BorderLayout.LINE_START);

		pinButton.addActionListener(e ->
		{
			pluginListPanel.savePinnedPlugins();
			pluginListPanel.refresh();
		});

		final JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(sideLabel != null ? new BorderLayout(3, 0) : new GridLayout(1, 2));
		buttonPanel.setOpaque(false);
		add(buttonPanel, BorderLayout.LINE_END);

		JMenuItem configMenuItem = null;
		if (pluginConfig.getConfigDescriptor() != null)
		{
			JButton configButton = new JButton(MicrobotConfigPanel.CONFIG_ICON);
			SwingUtil.removeButtonDecorations(configButton);
			configButton.setPreferredSize(new Dimension(25, 0));
			configButton.setVisible(false);
			buttonPanel.add(configButton);

			configButton.addActionListener(e ->
			{
				configButton.setIcon(MicrobotConfigPanel.CONFIG_ICON);
				openGroupConfigPanel();
			});

			configButton.setVisible(true);
			configButton.setToolTipText("Edit plugin configuration");

			configMenuItem = new JMenuItem("Configure");
			configMenuItem.addActionListener(e -> openGroupConfigPanel());
		}

		JMenuItem uninstallItem = null;
		if (internalName != null)
		{
			uninstallItem = new JMenuItem("Uninstall");
			uninstallItem.addActionListener(ev -> pluginListPanel.uninstall(pluginConfig));
		}

		addLabelPopupMenu(nameLabel, configMenuItem, pluginConfig.createSupportMenuItem(pluginConfig.getPlugin()),
			pluginListPanel.getBugReporter().menuItem(this, pluginConfig.getPlugin()), uninstallItem);
		add(nameLabel, BorderLayout.CENTER);

		onOffToggle = new MicrobotPluginToggleButton();
		onOffToggle.setConflicts(pluginConfig.getConflicts());
		if (sideLabel != null)
		{
			buttonPanel.add(sideLabel, BorderLayout.LINE_END);
		}
		else
		{
			// Kept but hidden on the Microbot row so its cog lines up with the others.
			buttonPanel.add(onOffToggle);
			onOffToggle.setVisible(pluginConfig.getPlugin() != null);
		}
		if (!alwaysOn && pluginConfig.getPlugin() != null)
		{
			onOffToggle.addActionListener(i ->
			{
				if (onOffToggle.isSelected())
				{
					pluginListPanel.startPlugin(pluginConfig.getPlugin());
				}
				else
				{
					pluginListPanel.stopPlugin(pluginConfig.getPlugin());
				}
			});

			if (pluginDescriptor.disable()) {
				onOffToggle.setEnabled(false);
				onOffToggle.setSelected(false);
			}
		}

		Color background = getBackground();
		setFocusable(true);
		getAccessibleContext().setAccessibleName(MicrobotPluginNames.plainName(pluginConfig.getName()));
		// Space does nothing on these rows, so a screen reader says why.
		getAccessibleContext().setAccessibleDescription(sideLabel == null ? null : sideLabel.getText());
		addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				setBackground(background);
			}
		});
	}

	private static JLabel createSideLabel(String text, String tooltip)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setToolTipText(tooltip);
		return label;
	}

	@NotNull
	private static JLabel createNameLabel(MicrobotPluginConfigurationDescriptor pluginConfig, int switchWidth, String version) {
		int buttons = 21 /*pin*/ + 25 /*config, if present*/ + switchWidth + 12 /*margins*/;
		int textWidth = MicrobotPluginListPanel.LIST_ITEM_WIDTH - buttons;
		JLabel nameLabel = new JLabel("<html><div style='width:" + textWidth + "px'>" + htmlBody(pluginConfig.getName()) + "</div></html>");
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setHorizontalAlignment(JLabel.LEFT);
		nameLabel.setVerticalAlignment(JLabel.CENTER);

		String details = (pluginConfig.getDescription().isEmpty() ? "" : "<br>" + htmlBody(pluginConfig.getDescription()))
			+ (version.isEmpty() ? "" : "<br>Version " + HtmlEscapers.htmlEscaper().escape(version));
		if (!details.isEmpty())
		{
			nameLabel.setToolTipText("<html>" + htmlBody(pluginConfig.getName()) + ":" + details + "</html>");
		}
		return nameLabel;
	}

	void setStatus(MicrobotPluginRowStatus status)
	{
		if (Objects.equals(this.status, status))
		{
			return;
		}
		this.status = status;
		statusButton.setVisible(status != null);
		if (status != null)
		{
			statusButton.setText(MicrobotPluginListPanel.linkHtml(status.getText()));
			statusButton.setForeground(statusButton.isFocusOwner() ? ColorScheme.BRAND_ORANGE : statusColor(status));
			statusButton.setToolTipText(status.getKind() == MicrobotPluginRowStatus.Kind.FAILED ? "See why" : status.getFullText());
		}
		int height = baseHeight + (status == null ? 0 : statusButton.getPreferredSize().height);
		setPreferredSize(new Dimension(MicrobotPluginListPanel.LIST_ITEM_WIDTH, height));
		revalidate();
	}

	// Orange for an update, as in the Hub, where green means installed.
	private static Color statusColor(MicrobotPluginRowStatus status)
	{
		if (status == null)
		{
			return ColorScheme.LIGHT_GRAY_COLOR;
		}
		return status.isSevere() ? ColorScheme.PROGRESS_ERROR_COLOR
			: status.getKind() == MicrobotPluginRowStatus.Kind.UPDATE ? MicrobotPluginHubPanel.PASTEL_ORANGE : ColorScheme.PROGRESS_INPROGRESS_COLOR;
	}

	static String htmlBody(String html)
	{
		return html
			.replaceFirst("(?i)^\\s*<html>", "")
			.replaceFirst("(?i)</html>\\s*$", "");
	}

	@Override
	public String getSearchableName()
	{
		return pluginConfig.getName();
	}

	@Override
	public boolean isPinned()
	{
		return pinButton.isSelected();
	}

	void setPinned(boolean pinned)
	{
		pinButton.setSelected(pinned);
	}

	void setPluginEnabled(boolean enabled)
	{
		onOffToggle.setSelected(enabled);
	}

	/** No switch a user can flip: always on, a settings row, or disabled by its author. Blocked plugins are not included. */
	boolean isNoSwitch()
	{
		return alwaysOn || settingsOnly || disabledByAuthor;
	}

	boolean isOn()
	{
		return onOffToggle.isSelected();
	}

	void toggle()
	{
		if (!alwaysOn && pluginConfig.getPlugin() != null)
		{
			onOffToggle.doClick(0);
		}
	}

	void setDimmed(boolean dimmed)
	{
		if (this.dimmed != dimmed)
		{
			this.dimmed = dimmed;
			repaint();
		}
	}

	@Override
	public void paint(Graphics g)
	{
		if (!dimmed)
		{
			super.paint(g);
			return;
		}

		Graphics2D dimmedGraphics = (Graphics2D) g.create();
		dimmedGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DIMMED_ALPHA));
		super.paint(dimmedGraphics);
		dimmedGraphics.dispose();
	}

	void openGroupConfigPanel()
	{
		pluginListPanel.openConfigurationPanel(pluginConfig);
	}

	/**
	 * Adds a mouseover effect to change the text of the passed label to {@link ColorScheme#BRAND_ORANGE} color, and
	 * adds the passed menu items to a popup menu shown when the label is clicked.
	 *
	 * @param label     The label to attach the mouseover and click effects to
	 * @param menuItems The menu items to be shown when the label is clicked
	 */
	static void addLabelPopupMenu(JLabel label, JMenuItem... menuItems)
	{
		final JPopupMenu menu = new JPopupMenu();
		final Color labelForeground = label.getForeground();
		menu.setBorder(new EmptyBorder(5, 5, 5, 5));

		for (final JMenuItem menuItem : menuItems)
		{
			if (menuItem == null)
			{
				continue;
			}

			// Some machines register mouseEntered through a popup menu, and do not register mouseExited when a popup
			// menu item is clicked, so reset the label's color when we click one of these options.
			menuItem.addActionListener(e -> label.setForeground(labelForeground));
			menu.add(menuItem);
		}

		label.addMouseListener(new MouseAdapter()
		{
			private Color lastForeground;

			@Override
			public void mouseClicked(MouseEvent mouseEvent)
			{
				Component source = (Component) mouseEvent.getSource();
				Point location = MouseInfo.getPointerInfo().getLocation();
				SwingUtilities.convertPointFromScreen(location, source);
				menu.show(source, location.x, location.y);
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent)
			{
				lastForeground = label.getForeground();
				label.setForeground(ColorScheme.BRAND_ORANGE);
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent)
			{
				label.setForeground(lastForeground);
			}
		});
	}
}
