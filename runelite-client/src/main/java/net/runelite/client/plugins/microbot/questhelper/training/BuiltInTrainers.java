package net.runelite.client.plugins.microbot.questhelper.training;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Simple trainers for common low-effort skills, built on the Microbot utilities (Rs2Inventory, Rs2Bank through the
 * supply buyer, the object and NPC caches). They are deliberately plain: the right tool, the nearest target, no
 * clever routing. Game-bound; the stop-at-level logic lives in {@link TrainingSession}.
 */
public final class BuiltInTrainers
{
	private BuiltInTrainers()
	{
	}

	static void registerAll()
	{
		SkillTrainerRegistry.register(new Firemaking());
		SkillTrainerRegistry.register(new Cooking());
		SkillTrainerRegistry.register(new Smithing());
		SkillTrainerRegistry.register(new Fletching());
		SkillTrainerRegistry.register(new Crafting());
		SkillTrainerRegistry.register(new MagicSplash());
		SkillTrainerRegistry.register(new Thieving());
		SkillTrainerRegistry.register(new Mining());
		SkillTrainerRegistry.register(new Woodcutting());
	}

	/** Names of the built-in trainers. */
	public static List<String> names()
	{
		List<String> out = new ArrayList<>();
		out.add("Firemaking: burn logs in a line at the Grand Exchange");
		out.add("Cooking: raw food on the Al Kharid range");
		out.add("Smithing: bronze daggers at the Varrock west anvil");
		out.add("Fletching: cut logs with a knife");
		out.add("Crafting: leather with needle and thread");
		out.add("Magic: Wind Strike on chickens");
		out.add("Thieving: pickpocket men then farmers");
		out.add("Mining: copper, tin then iron rocks, dropping ore");
		out.add("Woodcutting: trees, oaks from 15, dropping logs");
		return out;
	}

	private static void makeAll()
	{
		Global.sleepUntil(() -> Rs2Widget.hasWidget("How many"), 3_000);
		Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
	}

	private abstract static class Base implements SkillTrainer
	{
		private final SupplyBuyer buyer = new SupplyBuyer();
		private final String skill;
		private final String name;

		Base(String skill, String name)
		{
			this.skill = skill;
			this.name = name;
		}

		@Override
		public String skill()
		{
			return skill;
		}

		@Override
		public String name()
		{
			return name;
		}

		@Override
		public void reset()
		{
			buyer.reset();
		}

		abstract Outcome work(TrainerContext context);

		/** Walks to the spot when farther than {@code radius}; true while still walking. */
		boolean goTo(WorldPoint spot, int radius)
		{
			WorldPoint me = Rs2Player.getWorldLocation();
			if (me != null && me.getPlane() == spot.getPlane() && me.distanceTo(spot) <= radius)
			{
				return false;
			}
			Microbot.status = "Training " + skill + ": walking";
			Rs2Walker.walkTo(spot, 2);
			return true;
		}

		boolean busy()
		{
			return Rs2Player.isAnimating() || Rs2Player.isMoving();
		}

		@Override
		public Outcome tick(TrainerContext context)
		{
			SupplyBuyer.Status status = buyer.ensure(supplies(context.level()), context);
			if (status == SupplyBuyer.Status.FAILED)
			{
				return Outcome.failed(name + ": " + buyer.message());
			}
			if (status == SupplyBuyer.Status.WORKING)
			{
				return Outcome.working();
			}
			Microbot.status = "Training " + skill;
			return work(context);
		}
	}

	private static String logsFor(int level)
	{
		return level >= 60 ? "Yew logs" : level >= 45 ? "Maple logs" : level >= 30 ? "Willow logs"
			: level >= 15 ? "Oak logs" : "Logs";
	}

	private static int logsId(int level)
	{
		return level >= 60 ? 6333 : level >= 45 ? 1517 : level >= 30 ? 1519 : level >= 15 ? 1521 : 1511;
	}

	private static final class Firemaking extends Base
	{
		private static final WorldPoint LINE = new WorldPoint(3180, 3488, 0);

		Firemaking()
		{
			super("FIREMAKING", "Firemaking at the Grand Exchange");
		}

		@Override
		public List<Supply> supplies(int level)
		{
			List<Supply> out = new ArrayList<>();
			out.add(new Supply("Tinderbox", 590, 1));
			out.add(new Supply(logsFor(level), logsId(level), 27));
			return out;
		}

		@Override
		Outcome work(TrainerContext context)
		{
			if (goTo(LINE, 12) || busy())
			{
				return Outcome.working();
			}
			Rs2Inventory.combine("Tinderbox", logsFor(context.level()));
			Global.sleepUntil(Rs2Player::isAnimating, 2_000);
			return Outcome.working();
		}
	}

	private static final class Cooking extends Base
	{
		private static final WorldPoint RANGE = new WorldPoint(3273, 3180, 0);

		Cooking()
		{
			super("COOKING", "Cooking on the Al Kharid range");
		}

		private static String food(int level)
		{
			return level >= 40 ? "Raw lobster" : level >= 30 ? "Raw salmon" : level >= 15 ? "Raw trout" : "Raw shrimps";
		}

		private static int foodId(int level)
		{
			return level >= 40 ? 377 : level >= 30 ? 331 : level >= 15 ? 335 : 317;
		}

		@Override
		public List<Supply> supplies(int level)
		{
			List<Supply> out = new ArrayList<>();
			out.add(new Supply(food(level), foodId(level), 28));
			return out;
		}

		@Override
		Outcome work(TrainerContext context)
		{
			if (goTo(RANGE, 8) || busy())
			{
				return Outcome.working();
			}
			Rs2TileObjectModel range = Microbot.getRs2TileObjectCache().query().withName("Range").nearestOnClientThread(12);
			if (range == null)
			{
				return Outcome.failed("Cooking: no range found near the Al Kharid bank");
			}
			Rs2Inventory.interact(food(context.level()), "Use");
			if (range.click())
			{
				makeAll();
			}
			return Outcome.working();
		}
	}

	private static final class Smithing extends Base
	{
		private static final WorldPoint ANVIL = new WorldPoint(3188, 3426, 0);

		Smithing()
		{
			super("SMITHING", "Smithing bronze at Varrock west");
		}

		@Override
		public List<Supply> supplies(int level)
		{
			List<Supply> out = new ArrayList<>();
			out.add(new Supply("Hammer", 2347, 1));
			out.add(new Supply("Bronze bar", 2349, 27));
			return out;
		}

		@Override
		Outcome work(TrainerContext context)
		{
			if (goTo(ANVIL, 6) || busy())
			{
				return Outcome.working();
			}
			Rs2TileObjectModel anvil = Microbot.getRs2TileObjectCache().query().withName("Anvil").nearestOnClientThread(10);
			if (anvil == null)
			{
				return Outcome.failed("Smithing: no anvil found at Varrock west");
			}
			if (anvil.click("Smith") || anvil.click())
			{
				if (Global.sleepUntil(() -> Rs2Widget.hasWidget("Dagger"), 4_000))
				{
					Rs2Widget.clickWidget("Dagger");
					Global.sleepUntil(Rs2Player::isAnimating, 2_000);
				}
			}
			return Outcome.working();
		}
	}

	private static final class Fletching extends Base
	{
		Fletching()
		{
			super("FLETCHING", "Fletching logs with a knife");
		}

		@Override
		public List<Supply> supplies(int level)
		{
			List<Supply> out = new ArrayList<>();
			out.add(new Supply("Knife", 946, 1));
			out.add(new Supply("Logs", 1511, 27));
			return out;
		}

		@Override
		Outcome work(TrainerContext context)
		{
			if (busy())
			{
				return Outcome.working();
			}
			if (Rs2Inventory.combine("Knife", "Logs"))
			{
				makeAll();
			}
			return Outcome.working();
		}
	}

	private static final class Crafting extends Base
	{
		Crafting()
		{
			super("CRAFTING", "Crafting leather");
		}

		@Override
		public List<Supply> supplies(int level)
		{
			List<Supply> out = new ArrayList<>();
			out.add(new Supply("Needle", 1733, 1));
			out.add(new Supply("Thread", 1734, 10));
			out.add(new Supply("Leather", 1741, 25));
			return out;
		}

		@Override
		Outcome work(TrainerContext context)
		{
			if (busy())
			{
				return Outcome.working();
			}
			if (Rs2Inventory.combine("Needle", "Leather"))
			{
				makeAll();
			}
			return Outcome.working();
		}
	}

	private static final class MagicSplash extends Base
	{
		private static final WorldPoint CHICKENS = new WorldPoint(3235, 3295, 0);

		MagicSplash()
		{
			super("MAGIC", "Magic: Wind Strike on chickens");
		}

		@Override
		public List<Supply> supplies(int level)
		{
			List<Supply> out = new ArrayList<>();
			out.add(new Supply("Air rune", 556, 600));
			out.add(new Supply("Mind rune", 558, 600));
			return out;
		}

		@Override
		Outcome work(TrainerContext context)
		{
			if (goTo(CHICKENS, 12) || Rs2Player.isInteracting() || Rs2Player.isAnimating())
			{
				return Outcome.working();
			}
			Rs2NpcModel target = Microbot.getRs2NpcCache().query().withName("Chicken").nearestOnClientThread(15);
			if (target == null)
			{
				return Outcome.working();
			}
			Rs2Magic.castOn(Rs2CombatSpells.WIND_STRIKE, target);
			Global.sleepUntil(Rs2Player::isAnimating, 2_000);
			return Outcome.working();
		}
	}

	private static final class Thieving extends Base
	{
		private static final WorldPoint MEN = new WorldPoint(3222, 3218, 0);
		private static final WorldPoint FARMERS = new WorldPoint(3080, 3251, 0);
		private long lastPickMs;

		Thieving()
		{
			super("THIEVING", "Thieving: men then farmers");
		}

		@Override
		public List<Supply> supplies(int level)
		{
			return new ArrayList<>();
		}

		@Override
		Outcome work(TrainerContext context)
		{
			boolean farmers = context.level() >= 10;
			if (goTo(farmers ? FARMERS : MEN, 12))
			{
				return Outcome.working();
			}
			long now = System.currentTimeMillis();
			if (now - lastPickMs < 1_500 || Rs2Player.isAnimating())
			{
				return Outcome.working();
			}
			Rs2NpcModel target = Microbot.getRs2NpcCache().query().withName(farmers ? "Farmer" : "Man")
				.nearestOnClientThread(12);
			if (target != null)
			{
				target.click("Pickpocket");
				lastPickMs = now;
			}
			return Outcome.working();
		}
	}

	private static final class Mining extends Base
	{
		private static final WorldPoint MINE = new WorldPoint(3285, 3366, 0);

		Mining()
		{
			super("MINING", "Mining copper, tin and iron");
		}

		@Override
		public List<Supply> supplies(int level)
		{
			List<Supply> out = new ArrayList<>();
			out.add(new Supply("Bronze pickaxe", 1265, 1));
			return out;
		}

		@Override
		Outcome work(TrainerContext context)
		{
			if (Rs2Inventory.isFull())
			{
				Rs2Inventory.dropAll(item -> item.getName() != null
					&& item.getName().toLowerCase(Locale.ROOT).endsWith(" ore"));
				return Outcome.working();
			}
			if (goTo(MINE, 14) || busy())
			{
				return Outcome.working();
			}
			String rock = context.level() >= 15 ? "Iron rocks"
				: (System.currentTimeMillis() / 60_000) % 2 == 0 ? "Copper rocks" : "Tin rocks";
			Rs2TileObjectModel found = Microbot.getRs2TileObjectCache().query().withName(rock).nearestOnClientThread(14);
			if (found != null)
			{
				found.click("Mine");
			}
			return Outcome.working();
		}
	}

	private static final class Woodcutting extends Base
	{
		private static final WorldPoint TREES = new WorldPoint(3203, 3245, 0);

		Woodcutting()
		{
			super("WOODCUTTING", "Woodcutting trees and oaks");
		}

		@Override
		public List<Supply> supplies(int level)
		{
			List<Supply> out = new ArrayList<>();
			out.add(new Supply("Bronze axe", 1351, 1));
			return out;
		}

		@Override
		Outcome work(TrainerContext context)
		{
			if (Rs2Inventory.isFull())
			{
				Rs2Inventory.dropAll(item -> item.getName() != null
					&& item.getName().toLowerCase(Locale.ROOT).endsWith("logs"));
				return Outcome.working();
			}
			if (busy())
			{
				return Outcome.working();
			}
			String tree = context.level() >= 15 ? "Oak tree" : "Tree";
			Rs2TileObjectModel found = Microbot.getRs2TileObjectCache().query().withName(tree).nearestOnClientThread(20);
			if (found == null)
			{
				goTo(TREES, 3);
				return Outcome.working();
			}
			found.click("Chop down");
			return Outcome.working();
		}
	}
}
