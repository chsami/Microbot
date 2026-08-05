package shortestpath.transport.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import shortestpath.ItemVariations;
import shortestpath.Util;
import shortestpath.transport.requirement.ItemRequirement;
import shortestpath.transport.requirement.TransportItems;

/**
 * Parses item requirements from TSV field values.
 *
 * <p>
 * Format: {@code ITEM_NAME=quantity} with AND (&amp;) and OR (|) operators
 * </p>
 * <p>
 * Example: {@code AIR_RUNE=3&FIRE_RUNE=2} (need both)
 * </p>
 * <p>
 * Example: {@code DRAMEN_STAFF=1|LUNAR_STAFF=1} (need either)
 * </p>
 */
@Slf4j
public class ItemRequirementParser implements FieldParser<TransportItems>
{
	private static final String DELIM_STATE = "=";
	private static final String DELIM_AND = "&";
	private static final String DELIM_OR = "|";

	@Override
	public TransportItems parse(String value)
	{
		if (value == null || value.isEmpty())
		{
			return null;
		}

		// Normalize the input
		String normalized = value.replace(" ", "")
			.replace(DELIM_AND + DELIM_AND, DELIM_AND)
			.replace(DELIM_OR + DELIM_OR, DELIM_OR)
			.toUpperCase();

		List<ItemRequirement> requirements = new ArrayList<>();

		try
		{
			// Split by AND to get individual requirements
			String[] andParts = normalized.split(DELIM_AND);

			for (String andPart : andParts)
			{
				ItemRequirement requirement = parseRequirement(andPart);
				requirements.add(requirement);
			}

			return requirements.isEmpty() ? null : new TransportItems(requirements);
		}
		catch (NumberFormatException e)
		{
			log.error("Invalid item or quantity: {}", value);
			return null;
		}
	}

	/**
	 * Parses a single requirement which may have OR alternatives.
	 * Example: "AIR_RUNE=3|DUST_RUNE=3"
	 */
	private ItemRequirement parseRequirement(String part)
	{
		String[] orParts = part.split(Pattern.quote(DELIM_OR));

		List<int[]> itemIdsList = new ArrayList<>();
		List<int[]> stavesList = new ArrayList<>();
		List<int[]> offhandsList = new ArrayList<>();
		int maxQuantity = -1;

		for (String orPart : orParts)
		{
			String[] itemAndQuantity = orPart.split(DELIM_STATE);
			if (itemAndQuantity.length != 2)
			{
				throw new NumberFormatException("Invalid format: " + part);
			}

			String itemName = itemAndQuantity[0];
			int quantity = Integer.parseInt(itemAndQuantity[1]);
			maxQuantity = Math.max(maxQuantity, quantity);

			ItemVariations variation = ItemVariations.fromName(itemName);
			if (variation != null)
			{
				itemIdsList.add(variation.getIds());
				stavesList.add(ItemVariations.staves(variation));
				offhandsList.add(ItemVariations.offhands(variation));
			}
			else
			{
				// Try parsing as raw item ID
				itemIdsList.add(new int[]{Integer.parseInt(itemName)});
				stavesList.add(new int[0]);
				offhandsList.add(new int[0]);
			}
		}

		return new ItemRequirement(
			Util.concatenate(itemIdsList.toArray(new int[0][])),
			Util.concatenate(stavesList.toArray(new int[0][])),
			Util.concatenate(offhandsList.toArray(new int[0][])),
			maxQuantity);
	}
}
