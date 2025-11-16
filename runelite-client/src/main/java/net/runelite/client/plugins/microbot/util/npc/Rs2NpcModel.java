package net.runelite.client.plugins.microbot.util.npc;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.NpcOverrides;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.util.ActorModel;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Predicate;

@Getter
@EqualsAndHashCode(callSuper = true) // Ensure equality checks include ActorModel fields
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

	public static Predicate<Rs2NpcModel> matches(boolean exact, String... names) {
		return npc -> {
			String npcName = npc.getName();
			if (npcName == null) return false;
			if (exact) npcName = npcName.toLowerCase();
			final String name = npcName;
			return exact ? Arrays.stream(names).anyMatch(name::equalsIgnoreCase) :
					Arrays.stream(names).anyMatch(s -> name.contains(s.toLowerCase()));
		};
	}

	/**
	 * Gets the overhead prayer icon of the NPC, if any.
	 * @return
	 */
	public HeadIcon getHeadIcon() {
		if (runeliteNpc == null) {
			return null;
		}

		if (runeliteNpc.getOverheadSpriteIds() == null) {
			Microbot.log("Failed to find the correct overhead prayer.");
			return null;
		}

		for (int i = 0; i < runeliteNpc.getOverheadSpriteIds().length; i++) {
			int overheadSpriteId = runeliteNpc.getOverheadSpriteIds()[i];

			if (overheadSpriteId == -1) continue;

			return HeadIcon.values()[overheadSpriteId];
		}

		Microbot.log("Found overheadSpriteIds: " + Arrays.toString(runeliteNpc.getOverheadSpriteIds()) + " but failed to find valid overhead prayer.");

		return null;
	}
}
