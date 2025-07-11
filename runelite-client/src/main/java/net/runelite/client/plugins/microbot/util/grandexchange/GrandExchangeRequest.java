package net.runelite.client.plugins.microbot.util.grandexchange;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents a request to interact with the Grand Exchange,
 * encapsulating the desired action, item, and trade details.
 * <p>
 * This class is typically used to define a buy or sell order
 * for an item, including preferences such as price and quantity,
 * and whether the offer should be closed after completion.
 */
@Getter
@Builder
public class GrandExchangeRequest
{
	/**
	 * The Grand Exchange slot this request is targeting.
	 */
	private final GrandExchangeSlots slot;

	/**
	 * The action to perform on the Grand Exchange (e.g., buy or sell).
	 */
	private final GrandExchangeAction action;

	/**
	 * The name of the item involved in the request.
	 */
	private final String itemName;

	/**
	 * Whether to match the item name exactly.
	 */
	private final boolean exact;

	/**
	 * The number of items to buy or sell.
	 */
	private final int quantity;

	/**
	 * The price per item for the request.
	 */
	private final int price;

	/**
	 * An optional percentage adjustment to the price (e.g., 5, -5, 22, -22).
	 * This can be used to increase or decrease the base price.
	 */
	private final int percent;

	/**
	 * Whether to close the offer slot after the request has been completed.
	 */
	private final boolean closeAfterCompletion;

	/**
	 * Whether to collect the items directly to the bank instead of the inventory.
	 */
	private final boolean toBank;
}