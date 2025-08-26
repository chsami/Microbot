package net.runelite.client.plugins.microbot.shortestpath;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.components.ComboBoxListRenderer;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.depositbox.DepositBoxLocation;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.enums.Allotments;
import net.runelite.client.plugins.microbot.util.walker.enums.Birds;
import net.runelite.client.plugins.microbot.util.walker.enums.Bushes;
import net.runelite.client.plugins.microbot.util.walker.enums.Chinchompas;
import net.runelite.client.plugins.microbot.util.walker.enums.CompostBins;
import net.runelite.client.plugins.microbot.util.walker.enums.Farming;
import net.runelite.client.plugins.microbot.util.walker.enums.FruitTrees;
import net.runelite.client.plugins.microbot.util.walker.enums.Herbs;
import net.runelite.client.plugins.microbot.util.walker.enums.Hops;
import net.runelite.client.plugins.microbot.util.walker.enums.HuntingAreas;
import net.runelite.client.plugins.microbot.util.walker.enums.Insects;
import net.runelite.client.plugins.microbot.util.walker.enums.Kebbits;
import net.runelite.client.plugins.microbot.util.walker.enums.Salamanders;
import net.runelite.client.plugins.microbot.util.walker.enums.SlayerMasters;
import net.runelite.client.plugins.microbot.util.walker.enums.SpecialHuntingAreas;
import net.runelite.client.plugins.microbot.util.walker.enums.Trees;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import net.runelite.client.plugins.microbot.questhelper.steps.DetailedQuestStep;
import net.runelite.client.plugins.microbot.questhelper.steps.ConditionalStep;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationsClueScroll;

public class ShortestPathPanel extends PluginPanel
{

	private final ShortestPathPlugin plugin;

	private JTextField xField, yField, zField;
	private JComboBox<BankLocation> bankComboBox;
	private JComboBox<DepositBoxLocation> depositBoxComboBox;
	private JComboBox<SlayerMasters> slayerMasterComboBox;
	private JComboBox<Farming> farmingComboBox;
	private JComboBox<Allotments> allotmentsComboBox;
	private JComboBox<Bushes> bushesComboBox;
	private JComboBox<FruitTrees> fruitTreesComboBox;
	private JComboBox<Herbs> herbsComboBox;
	private JComboBox<Hops> hopsComboBox;
	private JComboBox<Trees> treesComboBox;
	private JComboBox<CompostBins> compostBinsComboBox;
	private JComboBox<HuntingAreas> huntingAreasComboBox;
	private JComboBox<Birds> birdsComboBox;
	private JComboBox<Chinchompas> chinchompasComboBox;
	private JComboBox<Insects> insectsComboBox;
	private JComboBox<Kebbits> kebbitsJComboBox;
	private JComboBox<Salamanders> salamandersComboBox;
	private JComboBox<SpecialHuntingAreas> specialHuntingAreasJComboBox;
	private javax.swing.Timer questInfoTimer;
	private javax.swing.Timer clueInfoTimer;

	@Inject
	private ShortestPathPanel(ShortestPathPlugin plugin)
	{
		super();
		this.plugin = plugin;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		add(createCustomLocationPanel());
		add(Box.createRigidArea(new Dimension(0, 10)));
		add(createBankPanel());
		add(Box.createRigidArea(new Dimension(0, 10)));
		add(createDepositBoxPanel());
		add(Box.createRigidArea(new Dimension(0, 10)));
		add(createSlayerMasterPanel());
		add(Box.createRigidArea(new Dimension(0, 10)));
		add(createQuestLocationPanel());
		add(Box.createRigidArea(new Dimension(0, 10)));
		add(createClueLocationPanel());
		add(Box.createRigidArea(new Dimension(0, 10)));
		add(createFarmingPanel());
		add(Box.createRigidArea(new Dimension(0, 10)));
		add(createHunterCreaturePanel());
	}

	private Border createCenteredTitledBorder(String title, String iconPath)
	{
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), iconPath);
		ImageIcon imageIcon = new ImageIcon(icon);

		JLabel titleLabel = new JLabel("<html><b>" + title + "</b></html>", imageIcon, JLabel.CENTER);
		titleLabel.setHorizontalTextPosition(JLabel.RIGHT);
		titleLabel.setVerticalTextPosition(JLabel.CENTER);

		Border emptyBorder = BorderFactory.createEmptyBorder(5, 5, 5, 5);
		Border lineBorder = BorderFactory.createLineBorder(Color.GRAY);

		return BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(
				lineBorder,
				BorderFactory.createEmptyBorder(2, 2, 2, 2)
			),
			new TitledBorder(emptyBorder, title, TitledBorder.CENTER, TitledBorder.TOP, null, null)
			{
				@Override
				public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
				{
					Graphics2D g2d = (Graphics2D) g.create();
					g2d.translate(x + width / 2 - titleLabel.getPreferredSize().width / 2, y);
					titleLabel.setSize(titleLabel.getPreferredSize());
					titleLabel.paint(g2d);
					g2d.dispose();
				}
			}
		);
	}

	private JPanel createCustomLocationPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(createCenteredTitledBorder("Travel to Custom Location", "/net/runelite/client/plugins/microbot/shortestpath/Map_link_icon.png"));

		JPanel coordinatesPanel = new JPanel(new GridLayout(2, 3, 5, 5));

		JLabel xLabel = new JLabel("X");
		JLabel yLabel = new JLabel("Y");
		JLabel zLabel = new JLabel("Z");
		xLabel.setHorizontalAlignment(SwingConstants.CENTER);
		yLabel.setHorizontalAlignment(SwingConstants.CENTER);
		zLabel.setHorizontalAlignment(SwingConstants.CENTER);

		xField = new JTextField("0", 5);
		yField = new JTextField("0", 5);
		zField = new JTextField("0", 5);

		xField.setHorizontalAlignment(JTextField.CENTER);
		yField.setHorizontalAlignment(JTextField.CENTER);
		zField.setHorizontalAlignment(JTextField.CENTER);

		coordinatesPanel.add(xLabel);
		coordinatesPanel.add(yLabel);
		coordinatesPanel.add(zLabel);
		coordinatesPanel.add(xField);
		coordinatesPanel.add(yField);
		coordinatesPanel.add(zField);

		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));

		JPanel topRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
		JButton startButton = new JButton("Start");
		JButton stopButton = new JButton("Stop");
		startButton.addActionListener(e -> startWalking(getCustomLocation()));
		stopButton.addActionListener(e -> stopWalking());
		topRow.add(startButton);
		topRow.add(stopButton);

		JPanel bottomRow = new JPanel();
		bottomRow.setLayout(new BoxLayout(bottomRow, BoxLayout.X_AXIS));
		JButton testButton = new JButton("Test");

		int combinedWidth = startButton.getPreferredSize().width + stopButton.getPreferredSize().width + 10;
		int buttonHeight = testButton.getPreferredSize().height;

		testButton.setMaximumSize(new Dimension(combinedWidth, buttonHeight));
		testButton.setPreferredSize(new Dimension(combinedWidth, buttonHeight));
		testButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		testButton.addActionListener(e -> plugin.setTarget(getCustomLocation()));
		bottomRow.add(Box.createHorizontalGlue());
		bottomRow.add(testButton);
		bottomRow.add(Box.createHorizontalGlue());

		buttonPanel.add(topRow);
		buttonPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		buttonPanel.add(bottomRow);

		panel.add(coordinatesPanel);
		panel.add(Box.createRigidArea(new Dimension(0, 5)));
		panel.add(buttonPanel);

		return panel;
	}

	private JPanel createBankPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(createCenteredTitledBorder("Travel to Bank", "/net/runelite/client/plugins/microbot/shortestpath/Bank_icon.png"));

		bankComboBox = new JComboBox<>(BankLocation.values());
		bankComboBox.setRenderer(new ComboBoxListRenderer());
		bankComboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
		bankComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, bankComboBox.getPreferredSize().height));
		((JLabel) bankComboBox.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton startButton = new JButton("Start");
		JButton stopButton = new JButton("Stop");

		startButton.addActionListener(e -> startWalking(getSelectedBank().getWorldPoint()));
		stopButton.addActionListener(e -> stopWalking());

		//JPanel nearestBankPanel = new JPanel(new FlowLayout(FlowLayout.CENTER)); Old layout without Ge Button
		JPanel nearestBankPanel = new JPanel(new GridLayout(2, 1, 5, 5));
		JButton useNearestBankButton = new JButton("Go To Nearest Bank");

		JButton goToGrandExchangeButton = new JButton("Go To Grand Exchange");
		goToGrandExchangeButton.addActionListener(e -> {
			// Grand Exchange WorldPoint
			WorldPoint ge = new WorldPoint(3164, 3487, 0); // Varrock GE location
			startWalking(ge);
		});

		useNearestBankButton.addActionListener(e -> {
			CompletableFuture.supplyAsync(Rs2Bank::getNearestBank)
				.thenAccept(nearestBank -> {
					if (nearestBank != null)
					{
						startWalking(nearestBank.getWorldPoint());
					}
				})
				.exceptionally(ex -> {
					Microbot.log("Error while finding the nearest bank: " + ex.getMessage());
					return null;
				});
		});

		nearestBankPanel.add(useNearestBankButton);
		nearestBankPanel.add(goToGrandExchangeButton); // Go to GE button

		buttonPanel.add(startButton);
		buttonPanel.add(stopButton);

		panel.add(bankComboBox);
		panel.add(Box.createRigidArea(new Dimension(0, 5)));
		panel.add(buttonPanel);
		panel.add(Box.createRigidArea(new Dimension(0, 2)));
		panel.add(nearestBankPanel);

		return panel;
	}

	private JPanel createDepositBoxPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(createCenteredTitledBorder("Travel to DepositBox", "/net/runelite/client/plugins/microbot/shortestpath/Bank_icon.png"));

		depositBoxComboBox = new JComboBox<>(DepositBoxLocation.values());
		depositBoxComboBox.setRenderer(new ComboBoxListRenderer());
		depositBoxComboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
		depositBoxComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, bankComboBox.getPreferredSize().height));
		((JLabel) depositBoxComboBox.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton startButton = new JButton("Start");
		JButton stopButton = new JButton("Stop");

		startButton.addActionListener(e -> startWalking(getSelectedDepositBox().getWorldPoint()));
		stopButton.addActionListener(e -> stopWalking());

		JPanel nearestDepositBoxPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton useNearestDepositBoxButton = new JButton("Go To Nearest Deposit Box");

		useNearestDepositBoxButton.addActionListener(e -> {
			CompletableFuture.supplyAsync(Rs2DepositBox::getNearestDepositBox)
				.thenAccept(nearestDepositBox -> {
					if (nearestDepositBox != null)
					{
						startWalking(nearestDepositBox.getWorldPoint());
					}
				})
				.exceptionally(ex -> {
					Microbot.log("Error while finding the nearest deposit box: " + ex.getMessage());
					return null;
				});
		});

		nearestDepositBoxPanel.add(useNearestDepositBoxButton);

		buttonPanel.add(startButton);
		buttonPanel.add(stopButton);

		panel.add(depositBoxComboBox);
		panel.add(Box.createRigidArea(new Dimension(0, 5)));
		panel.add(buttonPanel);
		panel.add(Box.createRigidArea(new Dimension(0, 2)));
		panel.add(nearestDepositBoxPanel);

		return panel;
	}

	private JPanel createSlayerMasterPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(createCenteredTitledBorder("Travel to Slayer Master", "/net/runelite/client/plugins/microbot/shortestpath/Slayer_Master_icon.png"));

		slayerMasterComboBox = new JComboBox<>(SlayerMasters.values());
		slayerMasterComboBox.setRenderer(new ComboBoxListRenderer());
		slayerMasterComboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
		slayerMasterComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, slayerMasterComboBox.getPreferredSize().height));
		((JLabel) slayerMasterComboBox.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton startButton = new JButton("Start");
		JButton stopButton = new JButton("Stop");

		startButton.addActionListener(e -> startWalking(getSelectedSlayerMaster().getWorldPoint()));
		stopButton.addActionListener(e -> stopWalking());

		JPanel turaelSkipPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton turaelSkipButton = new JButton("        Turael Skip        ");

		turaelSkipButton.addActionListener(e -> startWalking(SlayerMasters.TURAEL.getWorldPoint()));

		turaelSkipPanel.add(turaelSkipButton);

		buttonPanel.add(startButton);
		buttonPanel.add(stopButton);

		panel.add(slayerMasterComboBox);
		panel.add(Box.createRigidArea(new Dimension(0, 5)));
		panel.add(buttonPanel);
		panel.add(Box.createRigidArea(new Dimension(0, 2)));
		panel.add(turaelSkipPanel);

		return panel;
	}

	private JPanel createQuestLocationPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(createCenteredTitledBorder("Travel to Quest Location", "/net/runelite/client/plugins/microbot/questhelper/quest_icon.png"));

		// Quest info label
		JLabel questInfoLabel = new JLabel("Loading quest info...");
		questInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);
		questInfoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		questInfoLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, questInfoLabel.getPreferredSize().height * 2));
		
		// Update quest info dynamically
		questInfoTimer = new javax.swing.Timer(1000, e -> {
			String questInfo = getCurrentQuestInfo();
			questInfoLabel.setText("<html><center>" + questInfo + "</center></html>");
		});
		questInfoTimer.start();

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton startButton = new JButton("Start");
		JButton stopButton = new JButton("Stop");

		startButton.addActionListener(e -> {
			WorldPoint questLocation = getCurrentQuestLocation();
			if (questLocation != null)
			{
				Microbot.log("Walking to quest objective location");
				startWalking(questLocation);
			}
			else
			{
				QuestHelperPlugin qhp = getQuestHelperPlugin();
				if (qhp == null)
				{
					Microbot.log("Cannot walk to quest location: QuestHelper plugin not enabled");
				}
				else if (qhp.getSelectedQuest() == null)
				{
					Microbot.log("Cannot walk to quest location: No quest selected in QuestHelper");
				}
				else
				{
					Microbot.log("Cannot walk to quest location: Current quest step has no location");
				}
			}
		});
		
		stopButton.addActionListener(e -> stopWalking());

		buttonPanel.add(startButton);
		buttonPanel.add(stopButton);

		JPanel helpPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JLabel helpLabel = new JLabel("<html><center><small>Requires QuestHelper plugin<br>with an active quest</small></center></html>");
		helpLabel.setHorizontalAlignment(SwingConstants.CENTER);
		helpPanel.add(helpLabel);

		panel.add(Box.createRigidArea(new Dimension(0, 5)));
		panel.add(questInfoLabel);
		panel.add(Box.createRigidArea(new Dimension(0, 10)));
		panel.add(buttonPanel);
		panel.add(Box.createRigidArea(new Dimension(0, 2)));
		panel.add(helpPanel);

		return panel;
	}

	private JPanel createFarmingPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(createCenteredTitledBorder("Travel to Farming Location", "/net/runelite/client/plugins/microbot/shortestpath/Farming_patch_icon.png"));

		farmingComboBox = new JComboBox<>(Farming.values());
		farmingComboBox.setRenderer(new ComboBoxListRenderer());
		farmingComboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
		farmingComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, farmingComboBox.getPreferredSize().height));
		((JLabel) farmingComboBox.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);

		allotmentsComboBox = new JComboBox<>(Allotments.values());
		bushesComboBox = new JComboBox<>(Bushes.values());
		fruitTreesComboBox = new JComboBox<>(FruitTrees.values());
		herbsComboBox = new JComboBox<>(Herbs.values());
		hopsComboBox = new JComboBox<>(Hops.values());
		treesComboBox = new JComboBox<>(Trees.values());
		compostBinsComboBox = new JComboBox<>(CompostBins.values());

		JComboBox<?>[] subComboBoxes = {allotmentsComboBox, bushesComboBox, fruitTreesComboBox, herbsComboBox, hopsComboBox, treesComboBox, compostBinsComboBox};

		for (JComboBox<?> comboBox : subComboBoxes)
		{
			comboBox.setRenderer(new ComboBoxListRenderer());
			comboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
			comboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, comboBox.getPreferredSize().height));
			((JLabel) comboBox.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);
			comboBox.setVisible(false);
		}

		farmingComboBox.addActionListener(e -> {
			Farming selectedFarming = (Farming) farmingComboBox.getSelectedItem();
			allotmentsComboBox.setVisible(selectedFarming == Farming.ALLOTMENTS);
			bushesComboBox.setVisible(selectedFarming == Farming.BUSHES);
			fruitTreesComboBox.setVisible(selectedFarming == Farming.FRUIT_TREES);
			herbsComboBox.setVisible(selectedFarming == Farming.HERBS);
			hopsComboBox.setVisible(selectedFarming == Farming.HOPS);
			treesComboBox.setVisible(selectedFarming == Farming.TREES);
			compostBinsComboBox.setVisible(selectedFarming == Farming.COMPOST_BINS);
		});

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton startButton = new JButton("Start");
		JButton stopButton = new JButton("Stop");

		startButton.addActionListener(e -> startWalking(getSelectedFarmingLocation()));
		stopButton.addActionListener(e -> stopWalking());

		buttonPanel.add(startButton);
		buttonPanel.add(stopButton);

		panel.add(farmingComboBox);
		for (JComboBox<?> comboBox : subComboBoxes)
		{
			panel.add(comboBox);
		}
		panel.add(Box.createRigidArea(new Dimension(0, 5)));
		panel.add(buttonPanel);

		return panel;
	}

	private JPanel createHunterCreaturePanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(createCenteredTitledBorder("Travel to Hunter Creature", "/net/runelite/client/plugins/microbot/shortestpath/Hunter_icon.png"));

		huntingAreasComboBox = new JComboBox<>(HuntingAreas.values());
		huntingAreasComboBox.setRenderer(new ComboBoxListRenderer());
		huntingAreasComboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
		huntingAreasComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, huntingAreasComboBox.getPreferredSize().height));
		((JLabel) huntingAreasComboBox.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);

		birdsComboBox = new JComboBox<>(Birds.values());
		chinchompasComboBox = new JComboBox<>(Chinchompas.values());
		insectsComboBox = new JComboBox<>(Insects.values());
		kebbitsJComboBox = new JComboBox<>(Kebbits.values());
		salamandersComboBox = new JComboBox<>(Salamanders.values());
		specialHuntingAreasJComboBox = new JComboBox<>(SpecialHuntingAreas.values());

		JComboBox<?>[] subComboBoxes = {birdsComboBox, chinchompasComboBox, insectsComboBox, kebbitsJComboBox, salamandersComboBox, specialHuntingAreasJComboBox};

		for (JComboBox<?> comboBox : subComboBoxes)
		{
			comboBox.setRenderer(new ComboBoxListRenderer());
			comboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
			comboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, comboBox.getPreferredSize().height));
			((JLabel) comboBox.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);
			comboBox.setVisible(false);
		}

		huntingAreasComboBox.addActionListener(e -> {
			HuntingAreas selectedHuntingArea = (HuntingAreas) huntingAreasComboBox.getSelectedItem();
			birdsComboBox.setVisible(selectedHuntingArea == HuntingAreas.BIRDS);
			chinchompasComboBox.setVisible(selectedHuntingArea == HuntingAreas.CHINCHOMPAS);
			insectsComboBox.setVisible(selectedHuntingArea == HuntingAreas.INSECTS);
			kebbitsJComboBox.setVisible(selectedHuntingArea == HuntingAreas.KEBBITS);
			salamandersComboBox.setVisible(selectedHuntingArea == HuntingAreas.SALAMANDERS);
			specialHuntingAreasJComboBox.setVisible(selectedHuntingArea == HuntingAreas.SPECIAL);
		});

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton startButton = new JButton("Start");
		JButton stopButton = new JButton("Stop");

		startButton.addActionListener(e -> startWalking(getSelectedHuntingArea()));
		stopButton.addActionListener(e -> stopWalking());

		buttonPanel.add(startButton);
		buttonPanel.add(stopButton);

		JPanel hunterGuildPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton hunterGuildButton = new JButton("      Hunter Guild      ");

		hunterGuildButton.addActionListener(e -> startWalking(new WorldPoint(1558, 3046, 0)));

		hunterGuildPanel.add(hunterGuildButton);

		panel.add(huntingAreasComboBox);
		for (JComboBox<?> comboBox : subComboBoxes)
		{
			panel.add(comboBox);
		}
		panel.add(Box.createRigidArea(new Dimension(0, 5)));
		panel.add(buttonPanel);
		panel.add(Box.createRigidArea(new Dimension(0, 2)));
		panel.add(hunterGuildPanel);

		return panel;
	}

	public WorldPoint getCustomLocation()
	{
		try
		{
			int x = Integer.parseInt(xField.getText());
			int y = Integer.parseInt(yField.getText());
			int z = Integer.parseInt(zField.getText());
			return new WorldPoint(x, y, z);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	public BankLocation getSelectedBank()
	{
		return (BankLocation) bankComboBox.getSelectedItem();
	}

	public DepositBoxLocation getSelectedDepositBox()
	{
		return (DepositBoxLocation) depositBoxComboBox.getSelectedItem();
	}

	public SlayerMasters getSelectedSlayerMaster()
	{
		return (SlayerMasters) slayerMasterComboBox.getSelectedItem();
	}

	public Farming getSelectedFarmingCategory()
	{
		return (Farming) farmingComboBox.getSelectedItem();
	}

	public WorldPoint getSelectedFarmingLocation()
	{
		Farming selectedFarming = getSelectedFarmingCategory();
		switch (selectedFarming)
		{
			case ALLOTMENTS:
				return ((Allotments) allotmentsComboBox.getSelectedItem()).getWorldPoint();
			case BUSHES:
				return ((Bushes) bushesComboBox.getSelectedItem()).getWorldPoint();
			case FRUIT_TREES:
				return ((FruitTrees) fruitTreesComboBox.getSelectedItem()).getWorldPoint();
			case HERBS:
				return ((Herbs) herbsComboBox.getSelectedItem()).getWorldPoint();
			case HOPS:
				return ((Hops) hopsComboBox.getSelectedItem()).getWorldPoint();
			case TREES:
				return ((Trees) treesComboBox.getSelectedItem()).getWorldPoint();
			case COMPOST_BINS:
				return ((CompostBins) compostBinsComboBox.getSelectedItem()).getWorldPoint();
			default:
				return null;
		}
	}

	public HuntingAreas getSelectedHunterArea()
	{
		return (HuntingAreas) huntingAreasComboBox.getSelectedItem();
	}

	public WorldPoint getSelectedHuntingArea()
	{
		HuntingAreas selectedHunting = getSelectedHunterArea();
		switch (selectedHunting)
		{
			case BIRDS:
				return ((Birds) birdsComboBox.getSelectedItem()).getWorldPoint();
			case INSECTS:
				return ((Insects) insectsComboBox.getSelectedItem()).getWorldPoint();
			case KEBBITS:
				return ((Kebbits) kebbitsJComboBox.getSelectedItem()).getWorldPoint();
			case CHINCHOMPAS:
				return ((Chinchompas) chinchompasComboBox.getSelectedItem()).getWorldPoint();
			case SALAMANDERS:
				return ((Salamanders) salamandersComboBox.getSelectedItem()).getWorldPoint();
			case SPECIAL:
				return ((SpecialHuntingAreas) specialHuntingAreasJComboBox.getSelectedItem()).getWorldPoint();
			default:
				return null;
		}
	}

	private void startWalking(WorldPoint point)
	{
		Microbot.log("Web walking starting. Traveling to Custom Location (" + point.getX() + ", " + point.getY() + ", " + point.getPlane() + ").");
		plugin.getShortestPathScript().setTriggerWalker(point);
	}

	private void stopWalking()
	{
		Microbot.log("Web walking stopping..");
		plugin.getShortestPathScript().setTriggerWalker(null);
		Rs2Walker.setTarget(null);
	}

	private QuestHelperPlugin getQuestHelperPlugin()
	{
		return (QuestHelperPlugin) Microbot.getPluginManager().getPlugins().stream()
			.filter(x -> x instanceof QuestHelperPlugin)
			.findFirst()
			.orElse(null);
	}

	private WorldPoint getCurrentQuestLocation()
	{
		QuestHelperPlugin questHelper = getQuestHelperPlugin();
		if (questHelper == null || questHelper.getSelectedQuest() == null)
		{
			return null;
		}

		try
		{
			QuestStep currentStep = questHelper.getSelectedQuest().getCurrentStep();
			if (currentStep == null)
			{
				return null;
			}

			// Get the active step (handles ConditionalStep)
			QuestStep activeStep = currentStep;
			if (currentStep instanceof ConditionalStep)
			{
				activeStep = ((ConditionalStep) currentStep).getActiveStep();
			}

			// Extract WorldPoint from DetailedQuestStep
			if (activeStep instanceof DetailedQuestStep)
			{
				return ((DetailedQuestStep) activeStep).getWorldPoint();
			}
		}
		catch (Exception e)
		{
			Microbot.log("Error getting quest location: " + e.getMessage());
		}

		return null;
	}

	private String getCurrentQuestInfo()
	{
		QuestHelperPlugin questHelper = getQuestHelperPlugin();
		if (questHelper == null)
		{
			return "QuestHelper plugin not enabled";
		}

		if (questHelper.getSelectedQuest() == null)
		{
			return "No quest selected";
		}

		try
		{
			QuestHelper quest = questHelper.getSelectedQuest();
			String questName = quest.getQuest() != null ? quest.getQuest().getName() : "Unknown Quest";
			
			QuestStep currentStep = quest.getCurrentStep();
			if (currentStep != null)
			{
				// Try to get step description
				String stepText = "Current objective";
				if (currentStep instanceof ConditionalStep)
				{
					QuestStep activeStep = ((ConditionalStep) currentStep).getActiveStep();
					if (activeStep instanceof DetailedQuestStep)
					{
						DetailedQuestStep detailedStep = (DetailedQuestStep) activeStep;
						if (detailedStep.getText() != null && !detailedStep.getText().isEmpty())
						{
							stepText = detailedStep.getText().get(0);
							// Truncate if too long for display
							if (stepText.length() > 40)
							{
								stepText = stepText.substring(0, 37) + "...";
							}
						}
					}
				}
				else if (currentStep instanceof DetailedQuestStep)
				{
					DetailedQuestStep detailedStep = (DetailedQuestStep) currentStep;
					if (detailedStep.getText() != null && !detailedStep.getText().isEmpty())
					{
						stepText = detailedStep.getText().get(0);
						// Truncate if too long for display
						if (stepText.length() > 40)
						{
							stepText = stepText.substring(0, 37) + "...";
						}
					}
				}
				
				return questName + " - " + stepText;
			}
			
			return questName + " - No active step";
		}
		catch (Exception e)
		{
			return "Error reading quest info";
		}
	}

	private JPanel createClueLocationPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(createCenteredTitledBorder("Travel to Clue Location", "/net/runelite/client/plugins/microbot/shortestpath/Clue_scroll_icon.png"));

		// Clue info label
		JLabel clueInfoLabel = new JLabel("Loading clue info...");
		clueInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);
		clueInfoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		clueInfoLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, clueInfoLabel.getPreferredSize().height * 2));
		
		// Update clue info dynamically
		clueInfoTimer = new javax.swing.Timer(1000, e -> {
			String clueInfo = getCurrentClueInfo();
			clueInfoLabel.setText("<html><center>" + clueInfo + "</center></html>");
		});
		clueInfoTimer.start();

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JButton startButton = new JButton("Start");
		JButton stopButton = new JButton("Stop");

		startButton.addActionListener(e -> {
			WorldPoint clueLocation = getCurrentClueLocation();
			if (clueLocation != null)
			{
				Microbot.log("Walking to clue scroll location");
				startWalking(clueLocation);
			}
			else
			{
				ClueScrollPlugin cluePlugin = getCluePlugin();
				if (cluePlugin == null)
				{
					Microbot.log("Cannot walk to clue location: ClueScroll plugin not enabled");
				}
				else if (cluePlugin.getClue() == null)
				{
					Microbot.log("Cannot walk to clue location: No active clue scroll");
				}
				else
				{
					Microbot.log("Cannot walk to clue location: Current clue has no location");
				}
			}
		});
		
		stopButton.addActionListener(e -> stopWalking());

		buttonPanel.add(startButton);
		buttonPanel.add(stopButton);

		JPanel helpPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JLabel helpLabel = new JLabel("<html><center><small>Requires ClueScroll plugin<br>with an active clue</small></center></html>");
		helpLabel.setHorizontalAlignment(SwingConstants.CENTER);
		helpPanel.add(helpLabel);

		panel.add(Box.createRigidArea(new Dimension(0, 5)));
		panel.add(clueInfoLabel);
		panel.add(Box.createRigidArea(new Dimension(0, 10)));
		panel.add(buttonPanel);
		panel.add(Box.createRigidArea(new Dimension(0, 2)));
		panel.add(helpPanel);

		return panel;
	}

	private ClueScrollPlugin getCluePlugin()
	{
		return (ClueScrollPlugin) Microbot.getPluginManager().getPlugins().stream()
			.filter(x -> x instanceof ClueScrollPlugin)
			.findFirst()
			.orElse(null);
	}

	private WorldPoint getCurrentClueLocation()
	{
		ClueScrollPlugin cluePlugin = getCluePlugin();
		if (cluePlugin == null)
		{
			return null;
		}

		ClueScroll clue = cluePlugin.getClue();
		if (clue == null)
		{
			return null;
		}

		// Check if clue implements LocationClueScroll (single location)
		if (clue instanceof LocationClueScroll)
		{
			WorldPoint location = ((LocationClueScroll) clue).getLocation(cluePlugin);
			if (location != null)
			{
				return location;
			}
		}

		// Check if clue implements LocationsClueScroll (multiple locations)
		if (clue instanceof LocationsClueScroll)
		{
			WorldPoint[] locations = ((LocationsClueScroll) clue).getLocations(cluePlugin);
			if (locations != null && locations.length > 0)
			{
				// Return the first location for now
				// Could be improved to find the nearest one
				return locations[0];
			}
		}

		return null;
	}

	private String getCurrentClueInfo()
	{
		ClueScrollPlugin cluePlugin = getCluePlugin();
		if (cluePlugin == null)
		{
			return "ClueScroll plugin not enabled";
		}

		ClueScroll clue = cluePlugin.getClue();
		if (clue == null)
		{
			return "No active clue scroll";
		}

		// Get clue type from class name
		String clueType = clue.getClass().getSimpleName();
		
		// Remove "Clue" suffix if present
		if (clueType.endsWith("Clue"))
		{
			clueType = clueType.substring(0, clueType.length() - 4);
		}
		
		// Add spaces between camelCase words
		clueType = clueType.replaceAll("([a-z])([A-Z])", "$1 $2");
		
		// Check if clue has a location
		WorldPoint location = getCurrentClueLocation();
		if (location == null)
		{
			return clueType + " - No location";
		}

		return clueType + " clue";
	}

	public void disposeTimers()
	{
		if (questInfoTimer != null)
		{
			questInfoTimer.stop();
			questInfoTimer = null;
		}
		if (clueInfoTimer != null)
		{
			clueInfoTimer.stop();
			clueInfoTimer = null;
		}
	}
}