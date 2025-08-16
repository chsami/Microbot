package net.runelite.client.plugins.microbot.agility;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.Config;
import net.runelite.client.plugins.agility.AgilityPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.agility.courses.GnomeStrongholdCourse;
import net.runelite.client.plugins.microbot.agility.courses.PrifddinasCourse;
import net.runelite.client.plugins.microbot.agility.courses.WerewolfCourse;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.models.RS2Item;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

public class AgilityScript extends Script
{

	public static String version = "1.2.1";
	final MicroAgilityPlugin plugin;
	final MicroAgilityConfig config;

	WorldPoint startPoint = null;
	int lastAgilityXp = 0;
	long lastMovingTime = 0;
	int waitDelay = 0;  // Random delay between 700-1100ms

	@Inject
	public AgilityScript(MicroAgilityPlugin plugin, MicroAgilityConfig config)
	{
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public void shutdown()
	{
		super.shutdown();
	}

	public boolean run()
	{
		Microbot.enableAutoRunOn = true;
		Rs2Antiban.resetAntibanSettings();
		Rs2Antiban.antibanSetupTemplates.applyAgilitySetup();
		startPoint = plugin.getCourseHandler().getStartPoint();
		lastAgilityXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
			try
			{
				if (!Microbot.isLoggedIn())
				{
					return;
				}
				if (!super.run())
				{
					return;
				}
				if (!plugin.hasRequiredLevel())
				{
					Microbot.showMessage("You do not have the required level for this course.");
					shutdown();
					return;
				}
				if (Rs2AntibanSettings.actionCooldownActive)
				{
					return;
				}
				if (startPoint == null)
				{
					Microbot.showMessage("Agility course: " + config.agilityCourse().getTooltip() + " is not supported.");
					sleep(10000);
					return;
				}

				final WorldPoint playerWorldLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
				final int currentAgilityXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);

				if (handleFood())
				{
					return;
				}
				if (handleSummerPies())
				{
					return;
				}

				// Check if we're still completing an obstacle
				if (plugin.getCourseHandler().getCurrentObstacleIndex() > 0)
				{
					// Disable XP checks for Colossal Wyrm courses (they have multi-XP drop obstacles)
					boolean isColossalWyrm = config.agilityCourse().name().contains("COLOSSAL_WYRM");
					
					// If we gained XP and not Colossal Wyrm, obstacle is complete
					if (!isColossalWyrm && currentAgilityXp > lastAgilityXp) {
						lastAgilityXp = currentAgilityXp;
						// Small humanization delay after XP drop (100-300ms)
						sleep(100, 300);
					} else if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
						// Still moving, update timestamp and generate new random wait delay
						lastMovingTime = System.currentTimeMillis();
						waitDelay = 700 + (int)(Math.random() * 401);  // 700-1100ms
						return;
					} else if (System.currentTimeMillis() - lastMovingTime < waitDelay) {
						// Not moving but haven't waited long enough yet
						return;
					}
				}

				if (lootMarksOfGrace())
				{
					return;
				}

				if (plugin.getCourseHandler() instanceof PrifddinasCourse)
				{
					PrifddinasCourse course = (PrifddinasCourse) plugin.getCourseHandler();
					if (course.handlePortal())
					{
						return;
					}

					if (course.handleWalkToStart(playerWorldLocation))
					{
						return;
					}
				}
				else if(plugin.getCourseHandler() instanceof WerewolfCourse)
				{
					WerewolfCourse course = (WerewolfCourse) plugin.getCourseHandler();
					if(course.handleFirstSteppingStone(playerWorldLocation))
					{
						return;
					}
					if(course.handleStickPickup(playerWorldLocation))
					{
						return;
					}
					else if(course.handleSlide())
					{
						return;
					}
					else if(course.handleStickReturn(playerWorldLocation))
					{
						return;
					}
				}
				else if (!(plugin.getCourseHandler() instanceof GnomeStrongholdCourse))
				{
					if (plugin.getCourseHandler().handleWalkToStart(playerWorldLocation))
					{
						return;
					}
				}

				final int agilityExp = Microbot.getClient().getSkillExperience(Skill.AGILITY);

				TileObject gameObject = plugin.getCourseHandler().getCurrentObstacle();

				if (gameObject == null)
				{
					Microbot.log("No agility obstacle found. Report this as a bug if this keeps happening.");
					return;
				}

				if (!Rs2Camera.isTileOnScreen(gameObject))
				{
					Rs2Walker.walkMiniMap(gameObject.getWorldLocation());
				}

				// Handle alchemy if enabled
				if (config.alchemy()) {
					// Check if we should skip alching based on configured chance
					if (Math.random() * 100 < config.alchSkipChance()) {
						// Skip alching this obstacle
					} else {
						Optional<String> alchItem = getAlchItem();
						if (alchItem.isPresent()) {
						// Check for efficient alching conditions
						if (config.efficientAlching() && 
							gameObject.getWorldLocation().distanceTo(playerWorldLocation) >= 5) {
							// Efficient alching: click, alch, click
							if (Rs2GameObject.interact(gameObject)) {
								sleep(100, 200);
								Rs2Magic.alch(alchItem.get(), 50, 75);
								Rs2GameObject.interact(gameObject);
								plugin.getCourseHandler().waitForCompletion(agilityExp, 
									Microbot.getClient().getLocalPlayer().getWorldLocation().getPlane());
								Rs2Antiban.actionCooldown();
								Rs2Antiban.takeMicroBreakByChance();
								// Update last XP after completing obstacle
								lastAgilityXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);
								return;
							}
						} else {
							// Normal alching - but skip if at first obstacle and still mid-obstacle
							if (plugin.getCourseHandler().getCurrentObstacleIndex() == 0) {
								// Disable XP checks for Colossal Wyrm courses
								boolean isColossalWyrm = config.agilityCourse().name().contains("COLOSSAL_WYRM");
								
								// Skip if we haven't gained XP (unless Colossal Wyrm) AND (still moving OR haven't waited long enough)
								if (isColossalWyrm || currentAgilityXp == lastAgilityXp) {
									if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
										lastMovingTime = System.currentTimeMillis();
										waitDelay = 700 + (int)(Math.random() * 401);  // 700-1100ms
										// Skip alching, we're mid-obstacle
									} else if (System.currentTimeMillis() - lastMovingTime < waitDelay) {
										// Skip alching, still waiting
									} else {
										// Waited long enough, alch
										Rs2Magic.alch(alchItem.get(), 50, 75);
									}
								} else {
									// Gained XP (and not Colossal Wyrm), safe to alch
									Rs2Magic.alch(alchItem.get(), 50, 75);
								}
							} else {
								// Not first obstacle, just alch normally
								Rs2Magic.alch(alchItem.get(), 50, 75);
							}
						}
					}
					}
				}
				
				// Normal obstacle interaction
				if (Rs2GameObject.interact(gameObject)) {
					plugin.getCourseHandler().waitForCompletion(agilityExp, 
						Microbot.getClient().getLocalPlayer().getWorldLocation().getPlane());
					Rs2Antiban.actionCooldown();
					Rs2Antiban.takeMicroBreakByChance();
					// Update last XP after completing obstacle
					lastAgilityXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);
				}
			}
			catch (Exception ex)
			{
				Microbot.log("An error occurred: " + ex.getMessage(), ex);
			}
		}, 0, 100, TimeUnit.MILLISECONDS);
		return true;
	}

	private Optional<String> getAlchItem()
	{
		String itemsInput = config.itemsToAlch().trim();
		if (itemsInput.isEmpty())
		{
			Microbot.log("No items specified for alching or none available.");
			return Optional.empty();
		}

		List<String> itemsToAlch = Arrays.stream(itemsInput.split(","))
			.map(String::trim)
			.map(String::toLowerCase)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());

		if (itemsToAlch.isEmpty())
		{
			Microbot.log("No valid items specified for alching.");
			return Optional.empty();
		}

		for (String itemName : itemsToAlch)
		{
			if (Rs2Inventory.hasItem(itemName))
			{
				return Optional.of(itemName);
			}
		}

		return Optional.empty();
	}

	private boolean lootMarksOfGrace()
	{
		final List<RS2Item> marksOfGrace = AgilityPlugin.getMarksOfGrace();
		final int lootDistance = plugin.getCourseHandler().getLootDistance();
		if (!marksOfGrace.isEmpty() && !Rs2Inventory.isFull())
		{
			for (RS2Item markOfGraceTile : marksOfGrace)
			{
				if (Microbot.getClient().getTopLevelWorldView().getPlane() != markOfGraceTile.getTile().getPlane())
				{
					continue;
				}
				if (!Rs2GameObject.canReach(markOfGraceTile.getTile().getWorldLocation(), lootDistance, lootDistance, lootDistance, lootDistance))
				{
					continue;
				}
				Rs2GroundItem.loot(markOfGraceTile.getItem().getId());
				Rs2Player.waitForWalking();
				return true;
			}
		}
		return false;
	}

	private boolean handleFood()
	{
		if (Rs2Player.getHealthPercentage() > config.hitpoints())
		{
			return false;
		}

		List<Rs2ItemModel> foodItems = plugin.getInventoryFood();
		if (foodItems.isEmpty())
		{
			return false;
		}
		Rs2ItemModel foodItem = foodItems.get(0);

		Rs2Inventory.interact(foodItem, foodItem.getName().toLowerCase().contains("jug of wine") ? "drink" : "eat");
		Rs2Inventory.waitForInventoryChanges(1800);

		if (Rs2Inventory.contains(ItemID.JUG_EMPTY))
		{
			Rs2Inventory.dropAll(ItemID.JUG_EMPTY);
		}
		return true;
	}

	private boolean handleSummerPies()
	{
		if (plugin.getCourseHandler().getCurrentObstacleIndex() > 0)
		{
			return false;
		}
		if (Rs2Player.getBoostedSkillLevel(Skill.AGILITY) > plugin.getCourseHandler().getRequiredLevel())
		{
			return false;
		}

		List<Rs2ItemModel> summerPies = plugin.getSummerPies();
		if (summerPies.isEmpty())
		{
			return false;
		}
		Rs2ItemModel summerPie = summerPies.get(0);

		Rs2Inventory.interact(summerPie, "eat");
		Rs2Inventory.waitForInventoryChanges(1800);
		if (Rs2Inventory.contains(ItemID.PIEDISH))
		{
			Rs2Inventory.dropAll(ItemID.PIEDISH);
		}
		return true;
	}
}
