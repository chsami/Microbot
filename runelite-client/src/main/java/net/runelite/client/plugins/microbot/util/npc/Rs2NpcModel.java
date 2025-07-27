package net.runelite.client.plugins.microbot.util.npc;

import lombok.Getter;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.NpcOverrides;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.ActorModel;
import org.jetbrains.annotations.Nullable;

@Getter
public class Rs2NpcModel extends ActorModel implements NPC
{

	private final NPC runeliteNpc;

	public Rs2NpcModel(final NPC npc)
	{
		super(npc);
		this.runeliteNpc = npc;
	}

	@Override
	public int getId()
	{
		return runeliteNpc.getId();
	}

	@Override
	public int getIndex()
	{
		return runeliteNpc.getIndex();
	}

	@Override
	public NPCComposition getComposition()
	{
		return runeliteNpc.getComposition();
	}

	@Override
	public @Nullable NPCComposition getTransformedComposition()
	{
		return runeliteNpc.getTransformedComposition();
	}

	@Override
	public @Nullable NpcOverrides getModelOverrides()
	{
		return runeliteNpc.getModelOverrides();
	}

	@Override
	public @Nullable NpcOverrides getChatheadOverrides()
	{
		return runeliteNpc.getChatheadOverrides();
	}

	@Override
	public int @Nullable [] getOverheadArchiveIds()
	{
		return runeliteNpc.getOverheadArchiveIds();
	}

	@Override
	public short @Nullable [] getOverheadSpriteIds()
	{
		return runeliteNpc.getOverheadSpriteIds();
	}

	// Enhanced utility methods for cache operations

	/**
	 * Checks if this NPC is within a specified distance from the player.
	 * Uses client thread for safe access to player location.
	 * 
	 * @param maxDistance Maximum distance in tiles
	 * @return true if within distance, false otherwise
	 */
	public boolean isWithinDistanceFromPlayer(int maxDistance) {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			return this.getLocalLocation().distanceTo(
					Microbot.getClient().getLocalPlayer().getLocalLocation()) <= maxDistance;
		}).orElse(false);
	}

	/**
	 * Gets the distance from this NPC to the player.
	 * Uses client thread for safe access to player location.
	 * 
	 * @return Distance in tiles
	 */
	public int getDistanceFromPlayer() {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			return this.getLocalLocation().distanceTo(
					Microbot.getClient().getLocalPlayer().getLocalLocation());
		}).orElse(Integer.MAX_VALUE);
	}

	/**
	 * Checks if this NPC is within a specified distance from a given location.
	 * 
	 * @param anchor The anchor point
	 * @param maxDistance Maximum distance in tiles
	 * @return true if within distance, false otherwise
	 */
	public boolean isWithinDistance(WorldPoint anchor, int maxDistance) {
		if (anchor == null) return false;
		return this.getWorldLocation().distanceTo(anchor) <= maxDistance;
	}

	/**
	 * Checks if this NPC is currently interacting with the player.
	 * Uses client thread for safe access to player reference.
	 * 
	 * @return true if interacting with player, false otherwise
	 */
	public boolean isInteractingWithPlayer() {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			return this.getInteracting() == Microbot.getClient().getLocalPlayer();
		}).orElse(false);
	}

	/**
	 * Checks if this NPC is currently moving.
	 * 
	 * @return true if moving, false if idle
	 */
	public boolean isMoving() {
	
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
				this.getPoseAnimation() != this.getIdlePoseAnimation()
		).orElse(false);
	}

	/**
	 * Gets the health percentage of this NPC.
	 * 
	 * @return Health percentage (0-100), or -1 if unknown
	 */
	public double getHealthPercentage() {
		int ratio = this.getHealthRatio();
		int scale = this.getHealthScale();
		
		if (scale == 0) return -1;
		return (double) ratio / (double) scale * 100.0;
	}

	
}
