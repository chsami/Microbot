package net.runelite.client.plugins.microbot.alerts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

final class DashboardGameState
{
	private DashboardGameState()
	{
	}

	static JsonObject capture(Client client, ItemManager itemManager, XpTracker xpTracker)
	{
		JsonObject state = new JsonObject();
		GameState gameState = client.getGameState();
		state.addProperty("status", gameState == null ? null : gameState.name());
		Player player = client.getLocalPlayer();
		if (gameState != GameState.LOGGED_IN || player == null)
		{
			return state;
		}

		state.addProperty("playerName", player.getName() == null ? null : Text.sanitize(player.getName()));
		state.addProperty("world", client.getWorld());
		state.addProperty("combatLevel", player.getCombatLevel());
		state.addProperty("hitpoints", client.getBoostedSkillLevel(Skill.HITPOINTS));
		state.addProperty("maxHitpoints", client.getRealSkillLevel(Skill.HITPOINTS));
		state.addProperty("prayer", client.getBoostedSkillLevel(Skill.PRAYER));
		state.addProperty("maxPrayer", client.getRealSkillLevel(Skill.PRAYER));
		state.addProperty("runEnergy", client.getEnergy() / 100);
		state.addProperty("specialAttack", client.getVarpValue(VarPlayerID.SA_ENERGY) / 10);
		state.addProperty("idle", player.getAnimation() == -1
			&& player.getInteracting() == null
			&& player.getPoseAnimation() == player.getIdlePoseAnimation());

		WorldPoint location = player.getLocalLocation() == null ? null : WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		if (location != null)
		{
			JsonObject point = new JsonObject();
			point.addProperty("x", location.getX());
			point.addProperty("y", location.getY());
			point.addProperty("plane", location.getPlane());
			point.addProperty("regionId", location.getRegionID());
			state.add("location", point);
		}

		state.add("skills", skills(client, xpTracker));
		state.addProperty("account", xpTracker.account());
		state.add("inventory", inventory(client, itemManager));
		return state;
	}

	private static JsonArray skills(Client client, XpTracker xpTracker)
	{
		long accountHash = client.getAccountHash();
		boolean statsLoaded = client.getSkillExperience(Skill.HITPOINTS) > 0;
		JsonArray skills = new JsonArray();
		for (Skill skill : Skill.values())
		{
			int xp = client.getSkillExperience(skill);
			JsonObject entry = new JsonObject();
			entry.addProperty("name", skill.getName());
			entry.addProperty("level", client.getRealSkillLevel(skill));
			entry.addProperty("boostedLevel", client.getBoostedSkillLevel(skill));
			entry.addProperty("xpGained", xpTracker.gained(accountHash, skill, xp, statsLoaded));
			skills.add(entry);
		}
		return skills;
	}

	private static JsonArray inventory(Client client, ItemManager itemManager)
	{
		JsonArray items = new JsonArray();
		ItemContainer container = client.getItemContainer(InventoryID.INV);
		if (container == null)
		{
			return items;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("id", item.getId());
			entry.addProperty("name", itemManager.getItemComposition(item.getId()).getName());
			entry.addProperty("quantity", item.getQuantity());
			items.add(entry);
		}
		return items;
	}
}
