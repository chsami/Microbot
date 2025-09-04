package net.runelite.client.plugins.microbot.util.grandexchange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
@Slf4j
public class GrandExchangeWidget
{

	/**
	 * Retrieves the widget representing the Grand Exchange offers container.
	 *
	 * @return the {@link Widget} for the Grand Exchange offers container, or {@code null} if not available
	 */
	static Widget getOfferContainer()
	{
		return Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 26);
	}

	/**
	 * Checks if the Grand Exchange offer description text is currently visible.
	 *
	 * @return {@code true} if the offer description widget is visible; {@code false} otherwise
	 */
	static boolean isOfferTextVisible()
	{
		return Rs2Widget.isWidgetVisible(ComponentID.GRAND_EXCHANGE_OFFER_DESCRIPTION);
	}

	/**
	 * Retrieves the "Buy" button widget for a specific Grand Exchange slot.
	 *
	 * @param slot the {@link GrandExchangeSlots} to get the buy button for
	 * @return the {@link Widget} representing the buy button in the specified slot, or {@code null} if unavailable
	 */
	static Widget getOfferBuyButton(GrandExchangeSlots slot)
	{
		return getSlotChild(slot, 0);
	}

	/**
	 * Retrieves the "Sell" button widget for a specific Grand Exchange slot.
	 *
	 * @param slot the {@link GrandExchangeSlots} to get the sell button for
	 * @return the {@link Widget} representing the sell button in the specified slot, or {@code null} if unavailable
	 */
	static Widget getOfferSellButton(GrandExchangeSlots slot)
	{
		return getSlotChild(slot, 1);
	}

	/**
	 * Retrieves the widget for the "Minus" quantity button on the current Grand Exchange offer.
	 * <p>
	 * This button is used to decrement the quantity by 1.
	 *
	 * @return the {@link Widget} representing the minus quantity button, or {@code null} if unavailable
	 */
	static Widget getQuantityButton_Minus()
	{
		return getOfferChild(1);
	}

	/**
	 * Retrieves the widget for the "Plus" quantity button on the current Grand Exchange offer.
	 * <p>
	 * This button is used to increment the quantity by 1.
	 *
	 * @return the {@link Widget} representing the plus quantity button, or {@code null} if unavailable
	 */
	static Widget getQuantityButton_Plus()
	{
		return getOfferChild(2);
	}

	/**
	 * Retrieves the widget for the quantity button that adds 1 to the current quantity on the Grand Exchange offer.
	 *
	 * @return the {@link Widget} representing the "+1" quantity button, or {@code null} if unavailable
	 */
	static Widget getQuantityButton_1()
	{
		return getOfferChild(3);
	}

	/**
	 * Retrieves the widget for the quantity button that adds 10 to the current quantity on the Grand Exchange offer.
	 *
	 * @return the {@link Widget} representing the "+10" quantity button, or {@code null} if unavailable
	 */
	static Widget getQuantityButton_10()
	{
		return getOfferChild(4);
	}

	/**
	 * Retrieves the widget for the quantity button that adds 100 to the current quantity on the Grand Exchange offer.
	 *
	 * @return the {@link Widget} representing the "+100" quantity button, or {@code null} if unavailable
	 */
	static Widget getQuantityButton_100()
	{
		return getOfferChild(5);
	}

	/**
	 * Retrieves the widget for the quantity button that adds 1000 to the current quantity on the Grand Exchange offer.
	 *
	 * @return the {@link Widget} representing the "+1000" quantity button, or {@code null} if unavailable
	 */
	static Widget getQuantityButton_1000()
	{
		return getOfferChild(6);
	}

	/**
	 * Retrieves the widget for the custom quantity ("X") button on the Grand Exchange offer.
	 * <p>
	 * This button allows entering a custom quantity manually.
	 *
	 * @return the {@link Widget} representing the custom quantity button, or {@code null} if unavailable
	 */
	static Widget getQuantityButton_X()
	{
		return getOfferChild(7);
	}

	/**
	 * Retrieves the widget for the "Minus" price per item button on the Grand Exchange offer.
	 * <p>
	 * This button is used to decrease the price per item.
	 *
	 * @return the {@link Widget} representing the minus price per item button, or {@code null} if unavailable
	 */
	static Widget getPricePerItemButton_Minus()
	{
		return getOfferChild(8);
	}

	/**
	 * Retrieves the widget for the "Plus" price per item button on the Grand Exchange offer.
	 * <p>
	 * This button is used to increase the price per item.
	 *
	 * @return the {@link Widget} representing the plus price per item button, or {@code null} if unavailable
	 */
	static Widget getPricePerItemButton_Plus()
	{
		return getOfferChild(9);
	}

	/**
	 * Retrieves the widget for the "Guide Price" button on the Grand Exchange offer.
	 * <p>
	 * This button sets the price per item to the current market guide price.
	 *
	 * @return the {@link Widget} representing the guide price button, or {@code null} if unavailable
	 */
	static Widget getPricePerItemButton_GuidePrice()
	{
		return getOfferChild(11);
	}

	/**
	 * Retrieves the widget for the custom price adjustment ("X%") button on the Grand Exchange offer.
	 * <p>
	 * This button allows entering a custom percentage to increase or decrease the price.
	 *
	 * @return the {@link Widget} representing the custom price adjustment button, or {@code null} if unavailable
	 */
	static Widget getPricePerItemButton_X()
	{
		return getOfferChild(12);
	}

	/**
	 * Retrieves the widget for the "-5%" price adjustment button on the Grand Exchange offer.
	 * <p>
	 * This button decreases the price per item by 5%.
	 *
	 * @return the {@link Widget} representing the minus 5% price adjustment button, or {@code null} if unavailable
	 */
	static Widget getPricePerItemButton_Minus5Percent()
	{
		return getOfferChild(10);
	}

	/**
	 * Retrieves the widget for the "-X%" custom price adjustment button on the Grand Exchange offer.
	 * <p>
	 * This button allows decreasing the price per item by a custom percentage.
	 *
	 * @return the {@link Widget} representing the minus X% price adjustment button, or {@code null} if unavailable
	 */
	static Widget getPricePerItemButton_MinusXPercent()
	{
		return getOfferChild(14);
	}

	/**
	 * Retrieves the widget for the "+5%" price adjustment button on the Grand Exchange offer.
	 * <p>
	 * This button increases the price per item by 5%.
	 *
	 * @return the {@link Widget} representing the plus 5% price adjustment button, or {@code null} if unavailable
	 */
	static Widget getPricePerItemButton_Plus5Percent()
	{
		return getOfferChild(13);
	}

	/**
	 * Retrieves the widget for the "+X%" custom price adjustment button on the Grand Exchange offer.
	 * <p>
	 * This button allows increasing the price per item by a custom percentage.
	 *
	 * @return the {@link Widget} representing the plus X% price adjustment button, or {@code null} if unavailable
	 */
	static Widget getPricePerItemButton_PlusXPercent()
	{
		return getOfferChild(15);
	}

	/**
	 * Retrieves the widget for choosing an item on the current Grand Exchange offer.
	 *
	 * @return the {@link Widget} representing the item selection widget, or {@code null} if unavailable
	 */
	static Widget getChooseItem()
	{
		return getOfferChild(0);
	}

	/**
	 * Retrieves the "Confirm" button widget in the Grand Exchange offer interface.
	 * <p>
	 * This button is used to confirm the offer details and submit the offer.
	 *
	 * @return the {@link Widget} representing the confirm button, or {@code null} if not found
	 */
	static Widget getConfirm()
	{
		var parent = getOfferContainer();

		return Rs2Widget.findWidget("Confirm", Arrays.stream(parent.getDynamicChildren()).collect(Collectors.toList()), true);
	}

	/**
	 * Checks whether the current offer price has changed compared to a given base price.
	 *
	 * @param basePrice the price to compare against
	 * @return {@code true} if the current offer price differs from the base price; {@code false} otherwise
	 */
	static boolean hasOfferPriceChanged(int basePrice)
	{
		return basePrice != getItemPrice();
	}

	/**
	 * Retrieves the widget displaying the current item price in the Grand Exchange offer interface.
	 *
	 * @return the {@link Widget} representing the item price display, or {@code null} if unavailable
	 */
	static Widget getItemPriceWidget()
	{
		return getOfferChild(41);
	}

	/**
	 * Gets the current price of the item in the Grand Exchange offer interface.
	 * <p>
	 * Parses the text of the item price widget to extract the numeric price value.
	 *
	 * @return the current item price as an integer
	 * @throws NumberFormatException if the widget text cannot be parsed as an integer
	 */
	static int getItemPrice()
	{
		try
		{
			return Integer.parseInt(getItemPriceWidget().getText().replace(" coins", ""));
		}
		catch (NumberFormatException e)
		{
			Microbot.log("Invailid item price format in Grand Exchange: " + getItemPriceWidget().getText());
			return -1;
		}
	}

	/**
	 * Retrieves the widget corresponding to a specific Grand Exchange offer slot.
	 * <p>
	 * Each slot corresponds to a distinct child widget of the Grand Exchange offers interface.
	 *
	 * @param slot the {@link GrandExchangeSlots} enum value representing the desired slot
	 * @return the {@link Widget} for the specified slot, or {@code null} if the slot is invalid or not found
	 */
	static Widget getSlot(GrandExchangeSlots slot)
	{
		switch (slot)
		{
			case ONE:
				return Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 7);
			case TWO:
				return Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 8);
			case THREE:
				return Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 9);
			case FOUR:
				return Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 10);
			case FIVE:
				return Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 11);
			case SIX:
				return Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 12);
			case SEVEN:
				return Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 13);
			case EIGHT:
				return Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 14);
			default:
				return null;
		}
	}

	/**
	 * Retrieves all widgets within the Grand Exchange offers interface that have actions related to collecting items.
	 * <p>
	 * Typically returns buttons for collecting items, notes, or coins from completed offers.
	 *
	 * @return an array of {@link Widget} objects representing collect buttons; empty if none found
	 */
	static Widget[] getCollectButtons()
	{		
		if (!Rs2Widget.isWidgetVisible(InterfaceID.GE_OFFERS,24))
		{
			log.info("Grand Exchange offers interface is not open, cannot retrieve collect buttons.");
			return new Widget[0];
		}
		Widget parent = Rs2Widget.getWidget(InterfaceID.GE_OFFERS, 24);
		List<Widget> buttons = new ArrayList<>();
		if (parent != null && parent.getChildren() != null)
		{
			for (Widget child : parent.getChildren())
			{
				if (child.getActions() == null)
				{
					continue;
				}
				

				if (Arrays.stream(child.getActions()).filter(Objects::nonNull).anyMatch(act -> act.toLowerCase().contains("collect")))
				{
					buttons.add(child);
				}
			}
		}
		return buttons.toArray(new Widget[0]);
	}

	/**
	 * Retrieves the child widget at the specified index from the given parent widget.
	 *
	 * @param parent the parent {@link Widget} from which to get the child
	 * @param childIndex the index of the child widget
	 * @return the child {@link Widget} at the given index, or {@code null} if parent is {@code null} or index is invalid
	 */
	private static Widget getWidgetChild(Widget parent, int childIndex)
	{
		return Optional.ofNullable(parent)
			.map(p -> p.getChild(childIndex))
			.orElse(null);
	}

	/**
	 * Retrieves the child widget at the specified index from the Grand Exchange offers container widget.
	 *
	 * @param childIndex the index of the child widget to retrieve
	 * @return the child {@link Widget} at the given index, or {@code null} if not found
	 */
	private static Widget getOfferChild(int childIndex)
	{
		return getWidgetChild(getOfferContainer(), childIndex);
	}

	/**
	 * Retrieves the child widget at the specified index from a specific Grand Exchange slot widget.
	 *
	 * @param slot the {@link GrandExchangeSlots} specifying which slot widget to use
	 * @param childIndex the index of the child widget within the slot
	 * @return the child {@link Widget} at the given index in the slot widget, or {@code null} if not found
	 */
	private static Widget getSlotChild(GrandExchangeSlots slot, int childIndex)
	{
		return getWidgetChild(getSlot(slot), childIndex);
	}
}