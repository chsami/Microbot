package net.runelite.client.plugins.microbot.runecrafting.ourania;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.inject.Inject;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.gpu.GpuPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.pouch.Pouch;
import net.runelite.client.plugins.microbot.runecrafting.ourania.enums.OuraniaState;
import net.runelite.client.plugins.microbot.runecrafting.ourania.enums.Path;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.RunePouchType;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

public class OuraniaScript extends Script
{

	public static OuraniaState state;
	private final OuraniaConfig config;
	private final List<Integer> massWorlds = List.of(327, 480);
	private final OuraniaPlugin plugin;
	private int selectedWorld = 0;

	@Inject
	public OuraniaScript(OuraniaPlugin plugin, OuraniaConfig config)
	{
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public void shutdown()
	{
		Rs2Antiban.resetAntibanSettings();
		super.shutdown();
	}

	public boolean run()
	{
		Microbot.enableAutoRunOn = false;
		Rs2Antiban.resetAntibanSettings();
		Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
		Rs2Antiban.setActivity(Activity.CRAFTING_RUNES_AT_OURANIA_ALTAR);
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
				long startTime = System.currentTimeMillis();

				if (!Rs2Magic.isLunar())
				{
					Microbot.showMessage("Not currently on Lunar Spellbook");
					Microbot.stopPlugin(plugin);
					return;
				}

				if (Rs2Inventory.anyPouchUnknown())
				{
					Rs2Inventory.checkPouches();
					return;
				}

				if (config.useMassWorld() && !isOnMassWorld())
				{
					if (selectedWorld == 0)
					{
						selectedWorld = massWorlds.get(Rs2Random.between(0, massWorlds.size()));
					}
					Microbot.hopToWorld(selectedWorld);
					sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
					return;
				}

				if (selectedWorld != 0)
				{
					selectedWorld = 0;
				}

				if (hasStateChanged())
				{
					state = updateState();
				}

				if (state == null)
				{
					Microbot.showMessage("Unable to evaluate state");
					Microbot.stopPlugin(plugin);
					return;
				}

				switch (state)
				{
					case CRAFTING:
						if (!Rs2Inventory.hasItem(config.essence().getItemId()) && Rs2Inventory.hasAnyPouch() && !Rs2Inventory.allPouchesEmpty())
						{
							Rs2Inventory.emptyPouches();
							return;
						}
						Rs2GameObject.interact(ObjectID.RC_ZMI_DUNGEON_CRACKED_CENTER_ALTAR, "craft-rune");
						Rs2Inventory.waitForInventoryChanges(5000);
						break;
					case RESETTING:
						if (Rs2Player.getWorldLocation().distanceTo(new WorldPoint(2468, 3246, 0)) > 24)
						{
							Rs2Magic.cast(MagicAction.OURANIA_TELEPORT);
						}
						sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(new WorldPoint(2468, 3246, 0)) < 24);

						if (plugin.isBreakHandlerEnabled())
						{
							BreakHandlerScript.setLockState(false);
						}

						if (Rs2Inventory.hasDegradedPouch() && Rs2Magic.hasRequiredRunes(Rs2Spells.NPC_CONTACT))
						{
							Rs2Magic.repairPouchesWithLunar();
							return;
						}

						if (config.directInteract() && Microbot.isPluginEnabled(GpuPlugin.class))
						{
							GameObject ladder = Rs2GameObject.getGameObject(ObjectID.RC_ZMI_DUNGEON_ENTRANCE);
							Rs2GameObject.interact(ladder, "Climb");
							sleepUntil(this::isNearEniola, 20000);
						}
						else
						{
							Rs2Walker.walkTo(new WorldPoint(3014, 5625, 0));
						}
						break;
					case BANKING:
						if (plugin.isRanOutOfAutoPay())
						{
							Microbot.showMessage("You have ran out of auto-pay runes, check runepouch!");
							Microbot.stopPlugin(plugin);
							return;
						}

						if (!Rs2Bank.isOpen())
						{
							Rs2NpcModel eniola = Rs2Npc.getNpc(NpcID.RC_ZMI_BANKER);
							if (eniola == null)
							{
								return;
							}
							Rs2Npc.interact(eniola, "bank");
							sleepUntil(Rs2Bank::isOpen, 3000);
							return;
						}

						if (!config.toggleProfitCalculator())
						{
							plugin.calcuateProfit();
						}

						boolean hasRunes = Rs2Inventory.items().stream().anyMatch(item -> item.getName().toLowerCase().contains("rune") && !item.getName().toLowerCase().contains("rune pouch"));

						if (hasRunes)
						{
							if (config.useDepositAll())
							{
								Rs2Bank.depositAll();
							}
							else
							{
								// Get all RunePouchType IDs
								Integer[] runePouchIds = Arrays.stream(RunePouchType.values())
									.map(RunePouchType::getItemId)
									.toArray(Integer[]::new);

								// Get all eligible pouch IDs based on Runecrafting level
								Integer[] eligiblePouchIds = Arrays.stream(Pouch.values())
									.filter(Pouch::hasRequiredRunecraftingLevel)
									.flatMap(pouch -> Arrays.stream(pouch.getItemIds()).boxed())
									.toArray(Integer[]::new);

								// Combine RunePouchType IDs and eligible pouch IDs into a single array
								Integer[] excludedIds = Stream.concat(Arrays.stream(runePouchIds), Arrays.stream(eligiblePouchIds))
									.toArray(Integer[]::new);

								Rs2Bank.depositAllExcept(excludedIds);
								Rs2Inventory.waitForInventoryChanges(1800);
							}
						}

						if (config.useEnergyRestorePotions() && Rs2Player.getRunEnergy() <= config.drinkAtPercent())
						{
							boolean hasStaminaPotion = Rs2Bank.hasItem(Rs2Potion.getStaminaPotion());
							boolean hasEnergyRestorePotion = Rs2Bank.hasItem(Rs2Potion.getRestoreEnergyPotionsVariants());

							if ((Rs2Player.hasStaminaBuffActive() && hasEnergyRestorePotion) || (!hasStaminaPotion && hasEnergyRestorePotion))
							{
								Rs2ItemModel energyRestoreItem = Rs2Bank.bankItems().stream()
									.filter(rs2Item -> Rs2Potion.getRestoreEnergyPotionsVariants().stream()
										.anyMatch(variant -> rs2Item.getName().toLowerCase().contains(variant.toLowerCase())))
									.min(Comparator.comparingInt(rs2Item -> getDoseFromName(rs2Item.getName())))
									.orElse(null);

								if (energyRestoreItem == null)
								{
									Microbot.showMessage("Unable to find Restore Energy Potion but hasItem?");
									Microbot.stopPlugin(plugin);
									return;
								}

								withdrawAndDrink(energyRestoreItem.getName());
							}
							else if (hasStaminaPotion)
							{
								Rs2ItemModel staminaPotionItem = Rs2Bank.bankItems().stream()
									.filter(rs2Item -> rs2Item.getName().toLowerCase().contains(Rs2Potion.getStaminaPotion().toLowerCase()))
									.min(Comparator.comparingInt(rs2Item -> getDoseFromName(rs2Item.getName())))
									.orElse(null);

								if (staminaPotionItem == null)
								{
									Microbot.showMessage("Unable to find Stamina Potion but hasItem?");
									Microbot.stopPlugin(plugin);
									return;
								}

								withdrawAndDrink(staminaPotionItem.getName());
							}
							else
							{
								Microbot.showMessage("Unable to find Stamina Potion OR Energy Restore Potions");
								Microbot.stopPlugin(plugin);
								return;
							}
						}

						if (Rs2Player.getHealthPercentage() <= config.eatAtPercent())
						{
							while (Rs2Player.getHealthPercentage() < 100 && isRunning())
							{
								if (!Rs2Bank.hasItem(config.food().getId()))
								{
									Microbot.showMessage("Missing Food in Bank!");
									Microbot.stopPlugin(plugin);
									break;
								}

								Rs2Bank.withdrawOne(config.food().getId());
								Rs2Inventory.waitForInventoryChanges(1800);
								Rs2Player.useFood();
								sleepUntil(() -> !Rs2Inventory.hasItem(config.food().getId()));
							}

							if (Rs2Inventory.hasItem(ItemID.JUG_EMPTY))
							{
								Rs2Bank.depositAll(ItemID.JUG_EMPTY);
								Rs2Inventory.waitForInventoryChanges(1800);
							}
						}

						int requiredEssence = Rs2Inventory.getEmptySlots() + Rs2Inventory.getRemainingCapacityInPouches();

						if (!Rs2Bank.hasBankItem(config.essence().getItemId(), requiredEssence))
						{
							Microbot.showMessage("Not enough essence to full run");
							Microbot.stopPlugin(plugin);
							return;
						}

						if (Rs2Inventory.hasAnyPouch())
						{
							while (!Rs2Inventory.allPouchesFull() && isRunning())
							{
								Rs2Bank.withdrawAll(config.essence().getItemId());
								Rs2Inventory.fillPouches();
								Rs2Inventory.waitForInventoryChanges(1800);
							}
						}

						Rs2Bank.withdrawAll(config.essence().getItemId());
						Rs2Inventory.waitForInventoryChanges(1800);

						Rs2Bank.closeBank();
						sleepUntil(() -> !Rs2Bank.isOpen());
						break;
					case RUNNING_TO_ALTAR:
						if (plugin.isBreakHandlerEnabled())
						{
							BreakHandlerScript.setLockState(true);
						}

						if (config.path().equals(Path.SHORT))
						{
							if (config.directInteract() && Microbot.isPluginEnabled(GpuPlugin.class))
							{
								GameObject altarObject = Rs2GameObject.getGameObject(ObjectID.RC_ZMI_DUNGEON_CRACKED_CENTER_ALTAR, Constants.SCENE_SIZE);
								if (Rs2Camera.getPitch() < 210 || Rs2Camera.getPitch() > 280)
								{
									int randomPitch = Rs2Random.nextInt(220, 260, 1, false);
									Rs2Camera.setPitch(randomPitch);
									sleepUntil(() -> Rs2Camera.getPitch() == randomPitch);
								}
								if (Rs2Camera.getZoom() != 128)
								{
									Rs2Camera.setZoom(128);
									sleepUntil(() -> Rs2Camera.getZoom() == 128);
								}

								Rs2GameObject.interact(altarObject, "craft-rune");
								sleepUntil(this::isNearAltar, 30000);
							}
							else
							{
								Rs2Walker.walkTo(config.path().getWorldPoint());
							}
						}
						else
						{
							Rs2GameObject.interact(ObjectID.RC_ZMI_DUNGEON_WALL_CRACK_ENTRANCE, "squeeze-through");
							sleepUntil(this::isNearAltar, 10000);
						}
						break;
				}

				long endTime = System.currentTimeMillis();
				long totalTime = endTime - startTime;
				System.out.println("Total time for loop " + totalTime);

			}
			catch (Exception ex)
			{
				Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
				Microbot.log("Error in Ourania Altar Script: " + ex.getMessage());
			}
		}, 0, 1000, TimeUnit.MILLISECONDS);
		return true;
	}

	private boolean hasStateChanged()
	{
		if (Microbot.isDebug())
		{
			Microbot.log("State: " + state);
		}
		if (state == null)
		{
			return true;
		}
		if (hasRequiredItems() && !isNearAltar())
		{
			return true;
		}
		if (hasRequiredItems() && isNearAltar())
		{
			return true;
		}
		if ((!hasRequiredItems() && isNearAltar()) || (!hasRequiredItems() && !isNearEniola()))
		{
			return true;
		}
		if (!hasRequiredItems() && isNearEniola())
		{
			return true;
		}
		return false;
	}

	private OuraniaState updateState()
	{
		if (hasRequiredItems() && !isNearAltar())
		{
			return OuraniaState.RUNNING_TO_ALTAR;
		}
		if (hasRequiredItems() && isNearAltar())
		{
			return OuraniaState.CRAFTING;
		}
		if ((!hasRequiredItems() && isNearAltar()) || (!hasRequiredItems() && !isNearEniola()))
		{
			return OuraniaState.RESETTING;
		}
		if (!hasRequiredItems() && isNearEniola())
		{
			return OuraniaState.BANKING;
		}
		return null;
	}

	private boolean hasRequiredItems()
	{
		if (Rs2Inventory.hasAnyPouch())
		{
			boolean pouchesContainEssence = !Rs2Inventory.allPouchesEmpty();
			boolean inventoryContainsEssence = Rs2Inventory.hasItem(config.essence().getItemId());
			return pouchesContainEssence || inventoryContainsEssence;
		}
		else
		{
			return Rs2Inventory.hasItem(config.essence().getItemId());
		}
	}

	private boolean isNearAltar()
	{
		return plugin.getOuraniaAltarArea().contains(Rs2Player.getWorldLocation());
	}

	private boolean isNearEniola()
	{
		Rs2NpcModel eniola = Rs2Npc.getNpc(NpcID.RC_ZMI_BANKER);
		if (eniola == null)
		{
			return false;
		}
		return Rs2Player.getWorldLocation().distanceTo2D(eniola.getWorldLocation()) < 12;
	}

	private void withdrawAndDrink(String potionItemName)
	{
		String simplifiedPotionName = potionItemName.replaceAll("\\s*\\(\\d+\\)", "").trim();
		Rs2Bank.withdrawOne(potionItemName);
		Rs2Inventory.waitForInventoryChanges(1800);
		Rs2Inventory.interact(potionItemName, "drink");
		Rs2Inventory.waitForInventoryChanges(1800);
		if (Rs2Inventory.hasItem(simplifiedPotionName))
		{
			Rs2Bank.depositOne(simplifiedPotionName);
			Rs2Inventory.waitForInventoryChanges(1800);
		}
		if (Rs2Inventory.hasItem(ItemID.VIAL_EMPTY))
		{
			Rs2Bank.depositOne(ItemID.VIAL_EMPTY);
			Rs2Inventory.waitForInventoryChanges(1800);
		}
	}

	private boolean isOnMassWorld()
	{
		return massWorlds.contains(Rs2Player.getWorld());
	}

	private int getDoseFromName(String potionItemName)
	{
		Pattern pattern = Pattern.compile("\\((\\d+)\\)$");
		Matcher matcher = pattern.matcher(potionItemName);
		if (matcher.find())
		{
			return Integer.parseInt(matcher.group(1));
		}
		return 0;
	}
}
