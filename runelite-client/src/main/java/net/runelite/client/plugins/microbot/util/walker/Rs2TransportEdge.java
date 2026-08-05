package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable execution-facing description of the exact transport selected for a route edge. */
public final class Rs2TransportEdge
{
	private final WorldPoint origin;
	private final WorldPoint destination;
	private final Rs2TransportType type;
	private final Rs2TransportExecutor executor;
	private final Rs2TerminalTravelMode terminalTravelMode;
	private final String displayInfo;
	private final String action;
	private final String target;
	private final int objectId;
	private final int duration;
	private final boolean teleport;
	private final boolean consumable;
	private final boolean members;
	private final boolean skillGated;
	private final boolean questGated;
	private final boolean stateGated;
	private final int maxWildernessLevel;
	private final String currencyName;
	private final int currencyAmount;
	private final List<Rs2TransportItemRequirement> itemRequirements;
	/** Opaque local-engine identity; never exposed by the public planner-independent contract. */
	private final Object sourceIdentity;

	public Rs2TransportEdge(
		WorldPoint origin,
		WorldPoint destination,
		Rs2TransportType type,
		Rs2TransportExecutor executor,
		Rs2TerminalTravelMode terminalTravelMode,
		String displayInfo,
		String action,
		String target,
		int objectId,
		int duration,
		boolean teleport,
		boolean consumable,
		boolean members,
		int maxWildernessLevel,
		String currencyName,
		int currencyAmount,
		List<Rs2TransportItemRequirement> itemRequirements)
	{
		this(origin, destination, type, executor, terminalTravelMode, displayInfo, action, target,
			objectId, duration, teleport, consumable, members, maxWildernessLevel, currencyName,
			currencyAmount, itemRequirements, false, false, false, null);
	}

	Rs2TransportEdge(
		WorldPoint origin,
		WorldPoint destination,
		Rs2TransportType type,
		Rs2TransportExecutor executor,
		Rs2TerminalTravelMode terminalTravelMode,
		String displayInfo,
		String action,
		String target,
		int objectId,
		int duration,
		boolean teleport,
		boolean consumable,
		boolean members,
		int maxWildernessLevel,
		String currencyName,
		int currencyAmount,
		List<Rs2TransportItemRequirement> itemRequirements,
		boolean skillGated,
		boolean questGated,
		boolean stateGated,
		Object sourceIdentity)
	{
		this.origin = origin;
		this.destination = Objects.requireNonNull(destination, "destination");
		this.type = Objects.requireNonNull(type, "type");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.terminalTravelMode = Objects.requireNonNull(terminalTravelMode, "terminalTravelMode");
		if ((executor == Rs2TransportExecutor.TERMINAL_TRAVEL)
			!= (terminalTravelMode != Rs2TerminalTravelMode.UNSUPPORTED))
		{
			throw new IllegalArgumentException("terminal travel executor and mode must agree");
		}
		this.displayInfo = displayInfo;
		this.action = action;
		this.target = target;
		this.objectId = objectId;
		this.duration = duration;
		this.teleport = teleport;
		this.consumable = consumable;
		this.members = members;
		this.skillGated = skillGated;
		this.questGated = questGated;
		this.stateGated = stateGated;
		this.maxWildernessLevel = maxWildernessLevel;
		this.currencyName = currencyName == null ? "" : currencyName;
		this.currencyAmount = currencyAmount;
		this.itemRequirements = itemRequirements == null
			? Collections.emptyList()
			: List.copyOf(itemRequirements);
		this.sourceIdentity = sourceIdentity;
	}

	public WorldPoint getOrigin() { return origin; }
	public WorldPoint getDestination() { return destination; }
	public Rs2TransportType getType() { return type; }
	public Rs2TransportExecutor getExecutor() { return executor; }
	public Rs2TerminalTravelMode getTerminalTravelMode() { return terminalTravelMode; }
	public String getDisplayInfo() { return displayInfo; }
	public String getAction() { return action; }
	public String getTarget() { return target; }
	public int getObjectId() { return objectId; }
	public int getDuration() { return duration; }
	public boolean isTeleport() { return teleport; }
	public boolean isConsumable() { return consumable; }
	public boolean isMembers() { return members; }
	public boolean isSkillGated() { return skillGated; }
	public boolean isQuestGated() { return questGated; }
	public boolean isStateGated() { return stateGated; }
	public int getMaxWildernessLevel() { return maxWildernessLevel; }
	public String getCurrencyName() { return currencyName; }
	public int getCurrencyAmount() { return currencyAmount; }
	public List<Rs2TransportItemRequirement> getItemRequirements() { return itemRequirements; }
	Object getSourceIdentity() { return sourceIdentity; }
}
