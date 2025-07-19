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

				if (handleFood())
				{
					return;
				}
				if (handleSummerPies())
				{
					return;
				}

				if (plugin.getCourseHandler().getCurrentObstacleIndex() > 0)
				{
					if (Rs2Player.isMoving() || Rs2Player.isAnimating())
					{
						return;
					}
				}

				if (lootMarksOfGrace())
				{
					return;
				}

				if (config.alchemy())
				{
					getAlchItem().ifPresent(item -> Rs2Magic.alch(item, 50, 75));
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

				if (Rs2GameObject.interact(gameObject))
				{
					plugin.getCourseHandler().waitForCompletion(agilityExp, Microbot.getClient().getLocalPlayer().getWorldLocation().getPlane());
					Rs2Antiban.actionCooldown();
					Rs2Antiban.takeMicroBreakByChance();
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
