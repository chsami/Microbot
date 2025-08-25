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
	long lastTimeoutWarning = 0;  // For throttled timeout warnings

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

				if (lootMarksOfGrace())
				{
					return;
				}

				if (handleCourseSpecificActions(playerWorldLocation))
				{
					return;
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

				// Check if we should click (handles animation/XP logic)
				if (!plugin.getCourseHandler().shouldClickObstacle(currentAgilityXp, lastAgilityXp))
				{
					return; // Not ready to click yet
				}
				
				// Update XP if we got it while animating
				if (currentAgilityXp > lastAgilityXp)
				{
					lastAgilityXp = currentAgilityXp;
				}

				// Handle alchemy if enabled
				if (shouldPerformAlch())
				{
					Optional<String> alchItem = getAlchItem();
					if (alchItem.isPresent())
					{
						// Check if we should skip inefficient alchs
						if (config.skipInefficient())
						{
							// Only alch if obstacle is far enough for efficient alching
							if (gameObject.getWorldLocation().distanceTo(playerWorldLocation) >= 5)
							{
								if (config.efficientAlching())
								{
									if (performEfficientAlch(gameObject, alchItem.get(), agilityExp))
									{
										return;
									}
								}
								else
								{
									// Still do normal alch if far enough but efficient alching is disabled
									performNormalAlch(alchItem.get());
								}
							}
							// Skip alching if obstacle is too close
						}
						else
						{
							// Normal behavior when skipInefficient is disabled
							if (config.efficientAlching())
							{
								if (performEfficientAlch(gameObject, alchItem.get(), agilityExp))
								{
									return;
								}
							}
							// Fall back to normal alching
							performNormalAlch(alchItem.get());
						}
					}
				}
				
				// Normal obstacle interaction
				if (Rs2GameObject.interact(gameObject)) {
					// Wait for completion - this now returns quickly on XP drop
					boolean completed = plugin.getCourseHandler().waitForCompletion(agilityExp, 
						Microbot.getClient().getLocalPlayer().getWorldLocation().getPlane());
					
					if (!completed) {
						// Timeout occurred - log warning (throttled to once per 30 seconds)
						long now = System.currentTimeMillis();
						if (now - lastTimeoutWarning > 30000) {
							Microbot.log("Obstacle completion timed out - retrying on next iteration");
							lastTimeoutWarning = now;
						}
						return;  // Bail early to avoid acting on stale state
					}
					
					// XP tracking is already updated before clicking (line 137)
					// Don't update here to avoid losing early action state
					
					// If we're still animating after XP, don't add delays - proceed immediately
					if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
						// Only add delays if we're not animating
						Rs2Antiban.actionCooldown();
						Rs2Antiban.takeMicroBreakByChance();
					}
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
			// Microbot.log("No items specified for alching or none available.");
			return Optional.empty();
		}

		List<String> itemsToAlch = Arrays.stream(itemsInput.split(","))
			.map(String::trim)
			.map(String::toLowerCase)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());

		if (itemsToAlch.isEmpty())
		{
			// Microbot.log("No valid items specified for alching.");
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

	private boolean shouldPerformAlch()
	{
		if (!config.alchemy())
		{
			return false;
		}
		
		// Check if we should skip alching based on configured chance
		if (Math.random() * 100 < config.alchSkipChance())
		{
			return false;
		}
		
		return true;
	}

	private boolean performEfficientAlch(TileObject gameObject, String alchItem, int agilityExp)
	{
		WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
		
		if (gameObject.getWorldLocation().distanceTo(playerLocation) >= 5)
		{
			// Efficient alching: click, alch, click
			if (Rs2GameObject.interact(gameObject))
			{
				sleep(100, 200);
				Rs2Magic.alch(alchItem, 50, 75);
				Rs2GameObject.interact(gameObject);
				boolean completed = plugin.getCourseHandler().waitForCompletion(agilityExp,
					Microbot.getClient().getLocalPlayer().getWorldLocation().getPlane());
				
				if (!completed) {
					// Timeout during efficient alching - log warning
					long now = System.currentTimeMillis();
					if (now - lastTimeoutWarning > 30000) {
						Microbot.log("Obstacle completion timed out during efficient alching");
						lastTimeoutWarning = now;
					}
					return false;  // Return false to indicate alch sequence failed
				}
				
				Rs2Antiban.actionCooldown();
				Rs2Antiban.takeMicroBreakByChance();
				lastAgilityXp = Microbot.getClient().getSkillExperience(Skill.AGILITY);
				return true;
			}
		}
		return false;
	}

	private void performNormalAlch(String alchItem)
	{
		// Simple alch - waitForCompletion handles all timing
		Rs2Magic.alch(alchItem, 50, 75);
	}

	private boolean handleCourseSpecificActions(WorldPoint playerWorldLocation)
	{
		if (plugin.getCourseHandler() instanceof PrifddinasCourse)
		{
			PrifddinasCourse course = (PrifddinasCourse) plugin.getCourseHandler();
			return course.handlePortal() || course.handleWalkToStart(playerWorldLocation);
		}
		else if (plugin.getCourseHandler() instanceof WerewolfCourse)
		{
			WerewolfCourse course = (WerewolfCourse) plugin.getCourseHandler();
			return course.handleFirstSteppingStone(playerWorldLocation)
				|| course.handleStickPickup(playerWorldLocation)
				|| course.handleSlide()
				|| course.handleStickReturn(playerWorldLocation);
		}
		else if (!(plugin.getCourseHandler() instanceof GnomeStrongholdCourse))
		{
			return plugin.getCourseHandler().handleWalkToStart(playerWorldLocation);
		}
		return false;
	}
}
