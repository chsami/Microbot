package shortestpath.transport;

import java.util.EnumMap;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import shortestpath.ShortestPathConfig;
import shortestpath.ShortestPathPlugin;
import shortestpath.TeleportationItem;

/**
 * Manages the enabled/disabled state and cost thresholds of each TransportType
 * based on config.
 * This centralizes the config reading logic and automatically wires config
 * methods
 * from TransportType to ShortestPathConfig.
 *
 * <p>
 * When adding a new TransportType with config options, you only need to:
 * <ol>
 * <li>Add the config methods to ShortestPathConfig</li>
 * <li>Add the enum entry to TransportType with method references for the
 * enabledGetter and costGetter</li>
 * </ol>
 * This class will automatically pick up the new config getters.
 *
 * <p>
 * <b>Special cases:</b>
 * <ul>
 * <li>{@link TransportType#TELEPORTATION_ITEM} and
 * {@link TransportType#TELEPORTATION_BOX}
 * have no enabledGetter because they are controlled by the
 * {@link TeleportationItem} enum
 * via {@code useTeleportationItems} config. The per-transport filtering is
 * handled
 * in {@code PathfinderConfig.checkTeleportationItemRules()}.</li>
 * <li>{@link TransportType#TRANSPORT} has no enabledGetter because it's the
 * base transport
 * type and is always enabled.</li>
 * </ul>
 */
@Slf4j
public class TransportTypeConfig
{
	private final Map<TransportType, Boolean> enabledStates = new EnumMap<>(TransportType.class);
	private final Map<TransportType, Integer> costThresholds = new EnumMap<>(TransportType.class);
	private final ShortestPathConfig config;
	@Getter
	private TeleportationItem teleportationItemSetting;

	public TransportTypeConfig(ShortestPathConfig config)
	{
		this.config = config;
		refresh();
	}

	/**
	 * Refreshes all transport type enabled states and cost thresholds from config.
	 * Uses the functional getters defined in TransportType to read config values.
	 */
	public void refresh()
	{
		// Cache the teleportation item setting
		teleportationItemSetting = ShortestPathPlugin.override("useTeleportationItems", config.useTeleportationItems());

		for (TransportType type : TransportType.values())
		{
			enabledStates.put(type, getEnabledState(type));
			int cost = getCostThreshold(type);
			costThresholds.put(type, cost);
		}
	}

	/**
	 * Determines the enabled state for a transport type.
	 * Uses the enabledGetter function from TransportType to look up the config
	 * value.
	 *
	 * <p>
	 * Special handling for teleportation item types which are controlled by
	 * the TeleportationItem enum rather than a simple boolean.
	 */
	private boolean getEnabledState(TransportType type)
	{
		// Special handling for teleportation item types
		if (type == TransportType.TELEPORTATION_ITEM || type == TransportType.TELEPORTATION_BOX)
		{
			// These are enabled unless TeleportationItem is NONE
			// The detailed filtering (consumable, inventory, etc.) is done in
			// PathfinderConfig
			return teleportationItemSetting != TeleportationItem.NONE;
		}

		// No enabled getter means always enabled (controlled elsewhere or not
		// configurable)
		if (!type.hasEnabledGetter())
		{
			return true;
		}

		boolean configValue = type.getEnabledGetter().apply(config);
		return ShortestPathPlugin.override(type, configValue);
	}

	/**
	 * Determines the cost threshold for a transport type.
	 * Uses the costGetter function from TransportType to look up the config value.
	 */
	private int getCostThreshold(TransportType type)
	{
		// No cost getter means no additional cost
		if (!type.hasCostGetter())
		{
			return 0;
		}

		int configValue = type.getCostGetter().apply(config);
		return ShortestPathPlugin.override(type, configValue);
	}

	/**
	 * Checks if a transport type is enabled in config.
	 */
	public boolean isEnabled(TransportType type)
	{
		return enabledStates.getOrDefault(type, true);
	}

	/**
	 * Gets the cost threshold for a transport type.
	 */
	public int getCost(TransportType type)
	{
		return costThresholds.getOrDefault(type, 0);
	}

	/**
	 * Sets the enabled state for a transport type.
	 * Used for runtime modifications (e.g., disabling fairy rings without dramen
	 * staff).
	 */
	public void setEnabled(TransportType type, boolean enabled)
	{
		enabledStates.put(type, enabled);
	}

	/**
	 * Disables a transport type unless a condition is met.
	 * If the condition is false, the type is disabled.
	 * If the condition is true, the current enabled state is preserved (not
	 * changed).
	 * Useful for quest/item requirements that can only restrict, not enable.
	 */
	public void disableUnless(TransportType type, boolean condition)
	{
		if (!condition)
		{
			enabledStates.put(type, false);
		}
	}
}
