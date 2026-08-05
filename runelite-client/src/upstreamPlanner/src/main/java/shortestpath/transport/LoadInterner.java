package shortestpath.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import shortestpath.transport.parser.VarRequirement;
import shortestpath.transport.requirement.TransportItems;

/**
 * Load-scoped deduplication pools for transport requirement objects and display strings (issue
 * #491). Identical requirements and labels are extremely common across the permuted transport rows,
 * so a single canonical instance is shared. The interner is local to a {@code loadAllFromResources}
 * call and discarded afterwards, so the pools themselves are never retained.
 */
final class LoadInterner
{
	private final Map<TransportItems, TransportItems> items = new HashMap<>();
	private final Map<VarRequirement, VarRequirement> vars = new HashMap<>();
	private final Map<Set<VarRequirement>, Set<VarRequirement>> varSets = new HashMap<>();
	private final Map<Set<Quest>, Set<Quest>> questSets = new HashMap<>();
	private final Map<String, String> strings = new HashMap<>();

	TransportItems intern(TransportItems value)
	{
		return value == null ? null : items.computeIfAbsent(value, v -> v);
	}

	VarRequirement intern(VarRequirement value)
	{
		return vars.computeIfAbsent(value, v -> v);
	}

	Set<VarRequirement> internVarSet(Set<VarRequirement> value)
	{
		return varSets.computeIfAbsent(value, v -> v);
	}

	Set<Quest> internQuestSet(Set<Quest> value)
	{
		return questSets.computeIfAbsent(value, v -> v);
	}

	String internString(String value)
	{
		return value == null ? null : strings.computeIfAbsent(value, v -> v);
	}
}
