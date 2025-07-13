package net.runelite.client.plugins.microbot.util.grandexchange;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Objects;
import java.util.function.Predicate;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.MenuAction;
import net.runelite.api.VarClientStr;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Encryption;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.api.annotations.Component;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.apache.commons.lang3.tuple.Pair;

import java.awt.*;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.runelite.client.plugins.microbot.util.Global.*;

public class Rs2GrandExchange
{
	@Component
	private static final int COLLECT_ALL_BUTTON = 30474246;
	@Component
	private static final int GE_FRAME = InterfaceID.GeOffers.FRAME;
	private static final String GE_TRACKER_API_URL = "https://www.ge-tracker.com/api/items/";

	/**
	 * close the grand exchange interface
	 */
	public static void closeExchange()
	{
		Microbot.status = "Closing Grand Exchange";
		if (!isOpen())
		{
			return;
		}
		Rs2Widget.clickChildWidget(GE_FRAME, 11);
		sleepUntil(() -> !isOpen());
	}

	/**
	 * Back button. Goes back from buy/sell offer screen to all slots overview.
	 */
	public static void backToOverview()
	{
		Microbot.status = "Back to overview";
		if (!isOpen() && !isOfferScreenOpen())
		{
			return;
		}
		Rs2Widget.clickWidget(30474244);
		sleepUntil(() -> !isOfferScreenOpen());
	}

	/**
	 * check if the grand exchange screen is open
	 *
	 * @return
	 */
	public static boolean isOpen()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.GE_OFFERS, 1);
	}

	/**
	 * Check if the offer screen is open (buy/sell offer screen)
	 *
	 * @return
	 */
	public static boolean isOfferScreenOpen()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.GE_OFFERS, 23);
	}

	/**
	 * Opens the grand exchange
	 *
	 * @return
	 */
	public static boolean openExchange()
	{
		Microbot.status = "Opening Grand Exchange";
		try
		{
			if (Rs2Inventory.isItemSelected())
			{
				Microbot.getMouse().click();
			}
			if (isOpen())
			{
				return true;
			}
			Rs2NpcModel npc = Rs2Npc.getNpc("Grand Exchange Clerk");
			if (npc == null)
			{
				return false;
			}
			Rs2Npc.interact(npc, "exchange");
			if (Rs2Bank.isBankPinWidgetVisible())
			{
				if ((Login.activeProfile.getBankPin() == null || Login.activeProfile.getBankPin().isEmpty()) || Login.activeProfile.getBankPin().equalsIgnoreCase("**bankpin**"))
				{
					return false;
				}

				Rs2Bank.handleBankPin(Encryption.decrypt(Login.activeProfile.getBankPin()));
			}
			return sleepUntil(Rs2GrandExchange::isOpen, 5000);
		}
		catch (Exception ex)
		{
			Microbot.logStackTrace("Rs2GrandExchange", ex);
		}
		return false;
	}

	/**
	 * Processes a Grand Exchange offer request based on the action specified in the {@link GrandExchangeRequest}.
	 * <p>
	 * This method supports three types of actions:
	 * <ul>
	 *   <li>{@code COLLECT} - Attempts to collect an offer from a specific slot or all offers if no slot is provided.</li>
	 *   <li>{@code BUY} - Initiates a buy offer for a specified item, with optional price and quantity customization.</li>
	 *   <li>{@code SELL} - Initiates a sell offer for an item in the player's inventory, with optional price and quantity customization.</li>
	 * </ul>
	 * The method validates the request and ensures the Grand Exchange interface is usable before attempting to execute
	 * the request. If the offer is successfully processed and the request indicates the interface should be closed, it
	 * closes the Grand Exchange afterwards.
	 *
	 * @param request the {@link GrandExchangeRequest} containing the action type, item name, slot, price, quantity,
	 *                and other flags like exact name matching and whether to bank collected items.
	 * @return {@code true} if the offer was successfully processed, otherwise {@code false}
	 */
	public static boolean processOffer(GrandExchangeRequest request) {
		if (!isValidRequest(request) || !useGrandExchange()) {
			return false;
		}

		boolean success = false;

		switch (request.getAction()) {
			case COLLECT:
				Widget offerSlot = request.getSlot() != null ? GrandExchangeWidget.getSlot(request.getSlot()) : null;
				if (offerSlot == null) {
					success = collectAll(request.isToBank());
					break;
				}

				Widget itemNameWidget = offerSlot.getChild(19);
				if (itemNameWidget == null || itemNameWidget.getText() == null) {
					success = collectAll(request.isToBank());
					break;
				}

				String currentItemName = itemNameWidget.getText();
				boolean doesItemMatch = request.isExact()
					? currentItemName.equalsIgnoreCase(request.getItemName())
					: currentItemName.toLowerCase().contains(request.getItemName().toLowerCase());

				if (!doesItemMatch) {
					break;
				}

				viewOffer(offerSlot);
				sleepUntil(Rs2GrandExchange::isOfferScreenOpen);
				success = collectOffer(request.isToBank());
				break;

			case BUY:
				Widget buyOffer = GrandExchangeWidget.getOfferBuyButton(
					request.getSlot() != null ? request.getSlot() : getAvailableSlot());
				if (buyOffer == null) break;

				Rs2Widget.clickWidgetFast(buyOffer);
				sleepUntil(GrandExchangeWidget::isOfferTextVisible);

				Rs2Widget.sleepUntilHasWidgetText("Start typing the name of an item to search for it", 162, 51, false, 5000);
				Rs2Keyboard.typeString(request.getItemName());

				if (!Rs2Widget.sleepUntilHasWidgetText(request.getItemName(), 162, 43, request.isExact(), 5000)) break;

				sleep(1800); // TODO: make this conditional.

				Pair<Widget, Integer> itemResult = getSearchResultWidget(request.getItemName(), request.isExact());
				if (itemResult == null) break;

				Rs2Widget.clickWidgetFast(itemResult.getLeft(), itemResult.getRight(), 1);
				sleepUntil(() -> GrandExchangeWidget.getPricePerItemButton_X() != null);

				setPrice(request.getPrice());
				if (request.getPercent() != 0) {
					adjustPriceByPercent(request.getPercent());
				}
				setQuantity(request.getQuantity());
				confirm();
				success = sleepUntil(() -> !isOfferScreenOpen());
				break;

			case SELL:
				if (!Rs2Inventory.hasItem(request.getItemName(), request.isExact())) break;
				if (getAvailableSlots().length == 0) break;

				if (!Rs2Inventory.interact(request.getItemName(), "Offer", request.isExact())) break;

				sleepUntil(GrandExchangeWidget::isOfferTextVisible);

				if (request.getPrice() > 0) {
					setPrice(request.getPrice());
				}
				if (request.getPercent() != 0) {
					adjustPriceByPercent(request.getPercent());
				}
				if (request.getQuantity() > 0) {
					setQuantity(request.getQuantity());
				}

				confirm();
				success = sleepUntil(() -> !isOfferScreenOpen());
				break;
		}

		if (success && request.isCloseAfterCompletion()) {
			closeExchange();
		}

		return success;
	}


	/**
	 * Validates a {@link GrandExchangeRequest} based on its action type and required fields.
	 * <p>
	 * This method ensures that the request is not null, has a defined action, and meets specific criteria
	 * depending on the action:
	 * <ul>
	 *   <li>{@code BUY} - Must have a non-blank item name and a quantity greater than 0.</li>
	 *   <li>{@code SELL} - Must meet the {@code BUY} requirements, and also have a price greater than 0.</li>
	 *   <li>{@code COLLECT} - Always considered valid.</li>
	 *   <li>Other or unknown actions - Considered invalid.</li>
	 * </ul>
	 *
	 * @param request the {@link GrandExchangeRequest} to validate
	 * @return {@code true} if the request is valid for the specified action; otherwise, {@code false}
	 */
	private static boolean isValidRequest(GrandExchangeRequest request)
	{
		if (request == null || request.getAction() == null)
		{
			return false;
		}

		Predicate<GrandExchangeRequest> DEFAULT_PREDICATE = gxr -> gxr.getItemName() != null && !gxr.getItemName().isBlank() && request.getQuantity() > 0;
		Predicate<GrandExchangeRequest> PRICE_PREDICATE = gxr -> gxr.getPrice() > 0;

		switch (request.getAction())
		{
			case BUY:
				return DEFAULT_PREDICATE.test(request);
			case SELL:
				Predicate<GrandExchangeRequest> combined = DEFAULT_PREDICATE.and(PRICE_PREDICATE);
				return combined.test(request);
			case COLLECT:
				return true;
			default:
				return false;
		}
	}

	/**
	 * Sends a menu action to view a specific Grand Exchange offer by interacting with the given widget.
	 * <p>
	 * This simulates a "View offer" menu entry click on the widget, typically representing a Grand Exchange slot.
	 * If the widget is {@code null}, the method exits early without performing any action.
	 *
	 * @param widget the {@link Widget} representing the Grand Exchange offer slot to view
	 */
	private static void viewOffer(Widget widget)
	{
		if (widget == null)
		{
			return;
		}
		// MenuEntryImpl(getOption=View offer, getTarget=, getIdentifier=1, getType=CC_OP, getParam0=2, getParam1=30474247, getItemId=-1, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
		NewMenuEntry menuEntry = new NewMenuEntry("View offer", "", 1, MenuAction.CC_OP, 2, widget.getId(), false);
		Rectangle bounds = widget.getBounds();
		Microbot.doInvoke(menuEntry, bounds);
	}


	/**
	 * Collects items or coins from a Grand Exchange offer currently being viewed.
	 * <p>
	 * This method interacts with the appropriate "Collect", "Collect-items", "Collect-notes", or "Bank" buttons
	 * on the offer screen, based on the provided {@code toBank} flag and the number of available actions on each
	 * widget. It assumes the Grand Exchange offer screen is open and will assert if it is not.
	 * <p>
	 * If {@code toBank} is {@code true}, the method attempts to send items directly to the bank using the "Bank" option.
	 * Otherwise, it uses "Collect", "Collect-items", or "Collect-notes" based on the item quantity.
	 * <p>
	 * The method waits until the offer screen is closed after collecting to confirm success.
	 *
	 * @param toBank {@code true} to collect the offer into the bank, {@code false} to collect into the inventory
	 * @return {@code true} if the collection was successful (offer screen closed after collection); {@code false} otherwise
	 * @throws AssertionError if the offer screen is not open when the method is called
	 */
	private static boolean collectOffer(boolean toBank)
	{

		assert isOfferScreenOpen() : "Offer screen is not open, cannot collect offer.";

		Widget[] children = GrandExchangeWidget.getCollectButtons();
		String desiredAction;
		int identifier;
		int param0;
		if (children.length == 0)
		{
			return false;
		}

		// MenuEntryImpl(getOption=Bank, getTarget=<col=ff9040>Coins</col>, getIdentifier=3, getType=CC_OP, getParam0=3, getParam1=30474264, getItemId=995, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
		// MenuEntryImpl(getOption=Collect-notes, getTarget=<col=ff9040>Pure essence</col>, getIdentifier=1, getType=CC_OP, getParam0=2, getParam1=30474264, getItemId=7936, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
		// MenuEntryImpl(getOption=Collect-items, getTarget=<col=ff9040>Pure essence</col>, getIdentifier=2, getType=CC_OP, getParam0=2, getParam1=30474264, getItemId=7936, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
		// MenuEntryImpl(getOption=Collect, getTarget=<col=ff9040>Coins</col>, getIdentifier=2, getType=CC_OP, getParam0=2, getParam1=30474264, getItemId=995, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
		// MenuEntryImpl(getOption=Bank, getTarget=<col=ff9040>Coins</col>, getIdentifier=3, getType=CC_OP, getParam0=2, getParam1=30474264, getItemId=995, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
		for (int i = 0; i < children.length; i++)
		{
			Widget child = children[i];
			String[] actions = child.getActions();
			if (actions == null || actions.length == 0)
			{
				continue;
			}

			int length = Math.toIntExact(Arrays.stream(actions).filter(Objects::nonNull).count());

			if (length > 3)
			{
				identifier = toBank ? 3 : child.getItemQuantity() == 1 ? 2 : 1;
				desiredAction = toBank ? "Bank" : child.getItemQuantity() == 1 ? "Collect-items" : "Collect-notes";
			}
			else
			{
				identifier = toBank ? 3 : 2;
				desiredAction = toBank ? "Bank" : "Collect";
			}
			param0 = i == 0 ? 2 : 3;
			NewMenuEntry menuEntry = new NewMenuEntry(desiredAction, "", identifier, MenuAction.CC_OP, param0, child.getId(), false);
			Rectangle bounds = child.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(child.getBounds()) ? child.getBounds() : Rs2UiHelper.getDefaultRectangle();
			Microbot.doInvoke(menuEntry, bounds);
			if (!Rs2AntibanSettings.naturalMouse)
			{
				sleep(250, 750);
			}
		}
		return sleepUntil(() -> !isOfferScreenOpen());
	}

	/**
	 * Adjusts the Grand Exchange offer price by a specified percentage.
	 * <p>
	 * This method uses either the predefined +5% / -5% buttons or the customizable +X% / -X% button to increase
	 * or decrease the offer price by the given percentage. It supports percentages that are both divisible by 5
	 * and custom percentages (e.g., 7%).
	 * <p>
	 * If a custom percentage is required, the method simulates interaction with the "Customise" option, sets the
	 * desired value, and then applies it. After adjusting, it waits until the offer price reflects the change
	 * before continuing.
	 *
	 * @param percent the percentage by which to adjust the offer price; positive to increase, negative to decrease,
	 *                and {@code 0} will result in no action
	 */
	private static void adjustPriceByPercent(int percent)
	{
		if (percent == 0)
		{
			return;
		}

		boolean isIncrease = percent > 0;
		int absPercent = Math.abs(percent);
		int basePrice = Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE);

		if (absPercent % 5 == 0)
		{
			Widget adjust5Widget = isIncrease
				? GrandExchangeWidget.getPricePerItemButton_Plus5Percent()
				: GrandExchangeWidget.getPricePerItemButton_Minus5Percent();

			if (adjust5Widget == null)
			{
				Microbot.log("Unable to find +-5% button widget.");
				return;
			}

			int times = absPercent / 5;
			IntStream.range(0, times).forEach(i -> {
				Rs2Widget.clickWidget(adjust5Widget);
				sleepUntil(() -> GrandExchangeWidget.hasOfferPriceChanged(basePrice), 1600);
			});
		}
		else
		{
			Widget adjustXWidget = isIncrease
				? GrandExchangeWidget.getPricePerItemButton_PlusXPercent()
				: GrandExchangeWidget.getPricePerItemButton_MinusXPercent();

			if (adjustXWidget == null)
			{
				Microbot.log("Unable to find +-X% button widget.");
				return;
			}

			int currentPercent = Rs2UiHelper.extractNumber(adjustXWidget.getText());

			if (currentPercent != absPercent)
			{
				if (currentPercent == -1)
				{
					Rs2Widget.clickWidget(adjustXWidget);
				}
				else
				{
//					MenuEntryImpl(getOption=Customise, getTarget=, getIdentifier=2, getType=CC_OP, getParam0=14, getParam1=30474266, getItemId=-1, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
//					MenuEntryImpl(getOption=Customise, getTarget=, getIdentifier=2, getType=CC_OP, getParam0=15, getParam1=30474266, getItemId=-1, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
					NewMenuEntry menuEntry = new NewMenuEntry("Customise", "", 2, MenuAction.CC_OP, isIncrease ? 15 : 14, adjustXWidget.getId(), false);
					Rectangle bounds = adjustXWidget.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(adjustXWidget.getBounds()) ? adjustXWidget.getBounds() : Rs2UiHelper.getDefaultRectangle();
					Microbot.doInvoke(menuEntry, bounds);
				}

				sleepUntil(() -> Rs2Widget.hasWidget("Set a percentage to decrease/increase"), 2000);
				Rs2Keyboard.typeString(Integer.toString(absPercent));
				Rs2Keyboard.enter();
				sleepUntil(() -> {
					Widget updatedWidget = isIncrease
						? GrandExchangeWidget.getPricePerItemButton_PlusXPercent()
						: GrandExchangeWidget.getPricePerItemButton_MinusXPercent();
					return updatedWidget != null && Rs2UiHelper.extractNumber(updatedWidget.getText()) != currentPercent;
				}, 2000);
			}

			Rs2Widget.clickWidget(adjustXWidget);
			sleepUntil(() -> GrandExchangeWidget.hasOfferPriceChanged(basePrice), 2000);
		}
	}


	/**
	 * Creates and processes a {@link GrandExchangeRequest} to buy an item on the Grand Exchange.
	 * <p>
	 * This method constructs a {@code BUY} type request using the specified item name, price, and quantity,
	 * and delegates the logic to {@link #processOffer(GrandExchangeRequest)} to execute the buy action.
	 *
	 * @param itemName the name of the item to buy
	 * @param price the price per item in coins
	 * @param quantity the number of items to buy
	 * @return {@code true} if the buy offer was successfully placed; {@code false} otherwise
	 */
	public static boolean buyItem(String itemName, int price, int quantity)
	{
		GrandExchangeRequest request = GrandExchangeRequest.builder()
			.action(GrandExchangeAction.BUY)
			.itemName(itemName)
			.price(price)
			.quantity(quantity)
			.build();
		return processOffer(request);
	}

	/**
	 * Confirms the current Grand Exchange offer.
	 * <p>
	 * This method clicks the confirm button and checks if a warning prompt appears,
	 * such as "Your offer is much higher". If such a prompt is detected, it will automatically
	 * click "Yes" to proceed with the offer.
	 */
	private static void confirm()
	{
		Rs2Widget.clickWidget(GrandExchangeWidget.getConfirm());
		sleepUntil(() -> Rs2Widget.hasWidget("Your offer is much"), 2000);
		if (Rs2Widget.hasWidget("Your offer is much"))
		{
			Rs2Widget.clickWidget("Yes");
		}
	}

	/**
	 * Sets the quantity for the current Grand Exchange offer.
	 * <p>
	 * If the desired quantity differs from the currently selected offer quantity, this method simulates a click
	 * on the "Quantity: X" button, waits for the quantity input prompt, enters the new quantity via the chatbox,
	 * and confirms it by pressing Enter.
	 *
	 * @param quantity the number of items to set for the offer
	 */
	private static void setQuantity(int quantity)
	{
		if (quantity != getOfferQuantity())
		{
			Widget quantityButtonX = GrandExchangeWidget.getQuantityButton_X();
			Microbot.getMouse().click(quantityButtonX.getBounds());
			sleepUntil(() -> Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null); //GE Enter Price/Quantity
			sleep(600, 1000);
			setChatboxValue(quantity);
			sleep(500, 750);
			Rs2Keyboard.enter();
			sleep(1000);
		}
	}

	/**
	 * Sets the price per item for the current Grand Exchange offer.
	 * <p>
	 * If the specified price differs from the currently set offer price, this method clicks on the
	 * "Price per item: X" button, waits for the price input prompt to appear, enters the new price
	 * using the chatbox, and confirms it by pressing Enter.
	 *
	 * @param price the price per item to set for the offer
	 */
	private static void setPrice(int price)
	{
		if (price != getOfferPrice())
		{
			Widget pricePerItemButtonX = GrandExchangeWidget.getPricePerItemButton_X();
			Microbot.getMouse().click(pricePerItemButtonX.getBounds());
			sleepUntil(() -> Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null); //GE Enter Price
			sleep(600, 1000);
			setChatboxValue(price);
			sleep(500, 750);
			Rs2Keyboard.enter();
			sleep(1000);
		}
	}

	/**
	 * Ensures that the Grand Exchange interface is usable.
	 * <p>
	 * This method checks if the Grand Exchange is already open via {@link #isOpen()},
	 * attempts to open it with {@link #openExchange()}, or walks to it using {@link #walkToGrandExchange()}.
	 *
	 * @return {@code true} if the Grand Exchange is open or successfully made accessible; {@code false} otherwise
	 */
	private static boolean useGrandExchange()
	{
		return isOpen() || openExchange() || walkToGrandExchange();
	}

	/**
	 * Creates and processes a {@link GrandExchangeRequest} to sell an item on the Grand Exchange.
	 * <p>
	 * Constructs a {@code SELL} type request with the specified item name, quantity, and price,
	 * then delegates to {@link #processOffer(GrandExchangeRequest)} to execute the sell action.
	 *
	 * @param itemName the name of the item to sell
	 * @param quantity the number of items to sell
	 * @param price the price per item in coins
	 * @return {@code true} if the sell offer was successfully placed; {@code false} otherwise
	 */
	public static boolean sellItem(String itemName, int quantity, int price)
	{
		GrandExchangeRequest request = GrandExchangeRequest.builder()
			.action(GrandExchangeAction.SELL)
			.itemName(itemName)
			.quantity(quantity)
			.price(price)
			.build();
		return processOffer(request);
	}

	/**
	 * Collects all completed Grand Exchange offers either into the bank or inventory.
	 * <p>
	 * This method first checks if all Grand Exchange slots are empty; if so, it returns {@code true} immediately.
	 * If the inventory is full, it attempts to open the bank and deposit all items before continuing.
	 * Then, it ensures the Grand Exchange interface is open, locates the "Collect all" button,
	 * and invokes the appropriate menu action to collect all items either to the bank or inventory.
	 *
	 * @param collectToBank {@code true} to collect items directly to the bank, {@code false} to collect into inventory
	 * @return {@code true} if the collect action was successfully initiated; {@code false} if the collect button was not found
	 */
	public static boolean collectAll(boolean collectToBank)
	{
		if (isAllSlotsEmpty())
		{
			return true;
		}
		if (Rs2Inventory.isFull())
		{
			if (Rs2Bank.useBank())
			{
				Rs2Bank.depositAll();
			}
		}
		if (!isOpen())
		{
			openExchange();
		}
		sleepUntil(Rs2GrandExchange::isOpen);
		Widget collectButton = Rs2Widget.getWidget(COLLECT_ALL_BUTTON);
		if (collectButton == null)
		{
			return false;
		}
		// MenuEntryImpl(getOption=Collect to bank, getTarget=, getIdentifier=2, getType=CC_OP, getParam0=0, getParam1=30474246, getItemId=-1, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
		// MenuEntryImpl(getOption=Collect to inventory, getTarget=, getIdentifier=1, getType=CC_OP, getParam0=0, getParam1=30474246, getItemId=-1, isForceLeftClick=false, getWorldViewId=-1, isDeprioritized=false)
		NewMenuEntry entry = new NewMenuEntry(collectToBank ? "Collect to bank" : "Collect to inventory", "", collectToBank ? 2 : 1, MenuAction.CC_OP, 0, collectButton.getId(), false);
		Rectangle bounds = collectButton.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(collectButton.getBounds())
			? collectButton.getBounds()
			: Rs2UiHelper.getDefaultRectangle();
		Microbot.doInvoke(entry, bounds);
		return true;
	}

	/**
	 * Collects all completed Grand Exchange offers into the inventory.
	 * <p>
	 * This is a convenience method that calls {@link #collectAll(boolean)} with {@code false}
	 * to specify collection to inventory instead of the bank.
	 *
	 * @return {@code true} if the collect action was successfully initiated; {@code false} otherwise
	 */
	public static boolean collectAllToInventory()
	{
		return collectAll(false);
	}

	/**
	 * Collects all completed Grand Exchange offers directly into the bank.
	 * <p>
	 * This is a convenience method that calls {@link #collectAll(boolean)} with {@code true}
	 * to specify collection to the bank instead of the inventory.
	 *
	 * @return {@code true} if the collect action was successfully initiated; {@code false} otherwise
	 */
	public static boolean collectAllToBank()
	{
		return collectAll(true);
	}

	/**
	 * sells all the tradeable loot items from a specific npc name
	 *
	 * @param npcName
	 * @return true if there is no more loot to sell
	 */
	public static boolean sellLoot(String npcName, List<String> itemsToNotSell)
	{

		boolean withdrewLootItems = Rs2Bank.withdrawLootItems(npcName, itemsToNotSell);

		if (withdrewLootItems)
		{
			return sellInventory();
		}


		return false;
	}

	/**
	 * Attempts to sell all tradeable items currently in the player's inventory on the Grand Exchange.
	 * <p>
	 * For each tradeable item, this method:
	 * <ul>
	 *   <li>Checks if there is an available Grand Exchange slot; if not, collects all completed offers to the bank.</li>
	 *   <li>Creates a sell request at 5% below the current market offer price for the item.</li>
	 *   <li>Processes the sell offer through {@link #processOffer(GrandExchangeRequest)}.</li>
	 * </ul>
	 * The method returns {@code true} if the inventory is empty after attempting to sell all items.
	 *
	 * @return {@code true} if the inventory is empty after selling attempts; {@code false} otherwise
	 */
	public static boolean sellInventory()
	{
		Rs2Inventory.items().forEachOrdered(item -> {
			if (!item.isTradeable())
			{
				return;
			}

			if (Rs2GrandExchange.getAvailableSlot() == null && Rs2GrandExchange.hasSoldOffer())
			{
				Rs2GrandExchange.collectAllToBank();
				sleep(600);
			}

			// Sells at 5% under current offer price.
			GrandExchangeRequest request = GrandExchangeRequest.builder()
				.action(GrandExchangeAction.SELL)
				.itemName(item.getName())
				.percent(-5)
				.build();

			processOffer(request);
		});
		return Rs2Inventory.isEmpty();
	}

	/**
	 * Aborts an active Grand Exchange offer for the specified item name.
	 * <p>
	 * This method attempts to locate a non-available Grand Exchange slot containing an offer
	 * matching the given item name (case-insensitive). If found, it sends an "Abort offer" action
	 * on that slot's widget.
	 * <p>
	 * After aborting the offer, it collects all items from the Grand Exchange, either to the bank
	 * or inventory based on the {@code collectToBank} flag.
	 *
	 * @param name the name of the item whose offer should be aborted
	 * @param collectToBank {@code true} to collect aborted items to the bank; {@code false} to collect to inventory
	 * @return {@code true} if the offer was successfully aborted and collection was initiated; {@code false} otherwise
	 */
	public static boolean abortOffer(String name, boolean collectToBank)
	{
		if (!useGrandExchange())
		{
			return false;
		}

		Optional<GrandExchangeSlots> matchingSlot = Arrays.stream(GrandExchangeSlots.values())
			.filter(slot -> {
				Widget parent = GrandExchangeWidget.getSlot(slot);
				if (parent == null || isSlotAvailable(slot))
				{
					return false;
				}

				Widget child = parent.getChild(19);
				return child != null && child.getText().equalsIgnoreCase(name);
			})
			.findFirst();

		if (matchingSlot.isEmpty())
		{
			return false;
		}

		Widget parent = GrandExchangeWidget.getSlot(matchingSlot.get());
		NewMenuEntry menuEntry = new NewMenuEntry("Abort offer", "", 2, MenuAction.CC_OP, 2, parent.getId(), false);
		Rectangle bounds = parent.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(parent.getBounds())
			? parent.getBounds()
			: Rs2UiHelper.getDefaultRectangle();
		Microbot.doInvoke(menuEntry, bounds);
		return collectAll(collectToBank);
	}

	/**
	 * Aborts all active Grand Exchange offers.
	 * <p>
	 * Iterates through all Grand Exchange slots and attempts to abort any active offers.
	 * After aborting, collects all items from the Grand Exchange interface.
	 *
	 * @param collectToBank if {@code true}, collects items to the bank; otherwise collects to inventory
	 * @return {@code true} if all offers were aborted and collection was initiated successfully; {@code false} otherwise
	 */
	public static boolean abortAllOffers(boolean collectToBank)
	{
		if (!useGrandExchange())
		{
			return false;
		}

		Arrays.stream(GrandExchangeSlots.values())
			.filter(slot -> {
				Widget parent = GrandExchangeWidget.getSlot(slot);
				return parent != null && !isSlotAvailable(slot);
			})
			.forEach(slot -> {
				Widget parent = GrandExchangeWidget.getSlot(slot);
				NewMenuEntry menuEntry = new NewMenuEntry("Abort offer", "", 2, MenuAction.CC_OP, 2, parent.getId(), false);
				Rectangle bounds = parent.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(parent.getBounds())
					? parent.getBounds()
					: Rs2UiHelper.getDefaultRectangle();
				Microbot.doInvoke(menuEntry, bounds);
				if (!Rs2AntibanSettings.naturalMouse)
				{
					sleep(250, 750);
				}
			});

		sleep(1000);
		collectAll(collectToBank);
		return isAllSlotsEmpty();
	}

	/**
	 * Searches the Grand Exchange item search results widget for an entry matching the specified search text.
	 * <p>
	 * This method looks inside the chatbox scrollable widget that lists search results, then finds the first child
	 * widget whose text matches the search string exactly or partially (case-insensitive) depending on the {@code exact} flag.
	 * <p>
	 * If a matching widget is found, it returns a {@link Pair} containing the widget immediately before the matching
	 * widget in the list (often the clickable widget for selection) and its index.
	 * <p>
	 * Returns {@code null} if no matching widget is found or if the search results widget is not available.
	 *
	 * @param search the item name to search for
	 * @param exact if {@code true}, matches the item name exactly (case-insensitive); if {@code false}, matches partial containment
	 * @return a {@link Pair} of the clickable widget preceding the matching widget and its index, or {@code null} if not found
	 */
	public static Pair<Widget, Integer> getSearchResultWidget(String search, boolean exact)
	{
		Widget parent = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS)).orElse(null);

		if (parent == null || parent.getChildren() == null)
		{
			return null;
		}

		Widget child = Arrays.stream(parent.getChildren()).filter(x -> {
				String widgetText = Rs2UiHelper.stripColTags(x.getText());
				return exact ? widgetText.equalsIgnoreCase(search) : widgetText.toLowerCase().contains(search.toLowerCase());
			})
			.findFirst()
			.orElse(null);

		if (child != null)
		{
			List<Widget> children = Arrays.stream(parent.getChildren()).collect(Collectors.toList());
			int index = children.indexOf(child);
			int originalWidgetIndex = index - 1;
			return Pair.of(children.get(originalWidgetIndex), originalWidgetIndex);
		}
		return null;
	}

	/**
	 * Checks if a specified Grand Exchange slot is available for a new offer.
	 * <p>
	 * A slot is considered available if its corresponding widget exists and the child widget
	 * at index 2 is hidden (indicating the slot is free).
	 *
	 * @param slot the {@link GrandExchangeSlots} slot to check
	 * @return {@code true} if the slot is available; {@code false} if the slot is occupied or the widget is missing
	 */
	public static boolean isSlotAvailable(GrandExchangeSlots slot)
	{
		Widget parent = GrandExchangeWidget.getSlot(slot);
		return Optional.ofNullable(parent)
			.map(p -> {
				Widget child = p.getChild(2);
				if (child == null) return false;
				return child.isSelfHidden();
			})
			.orElse(false);
	}

	/**
	 * Returns all slots with active offers
	 *
	 * @return array of slots containing active offers
	 */
	public static GrandExchangeSlots[] getActiveOfferSlots()
	{
		return Arrays.stream(GrandExchangeSlots.values())
			.filter(slot -> {
				Widget parent = GrandExchangeWidget.getSlot(slot);
				return parent != null && !isSlotAvailable(slot);
			})
			.toArray(GrandExchangeSlots[]::new);
	}

	/**
	 * Checks if all Grand Exchange slots are currently empty (available).
	 * <p>
	 * This method counts the number of available slots and compares it to the maximum number of slots.
	 * If all slots are available, it returns {@code true}.
	 *
	 * @return {@code true} if all Grand Exchange slots are empty; {@code false} otherwise
	 */
	public static boolean isAllSlotsEmpty()
	{
		return Arrays.stream(getAvailableSlots()).count() == getMaxSlots();
	}

	public static boolean hasBoughtOffer()
	{
		return Arrays.stream(Microbot.getClient().getGrandExchangeOffers()).anyMatch(x -> x.getState() == GrandExchangeOfferState.BOUGHT);
	}

	public static boolean hasFinishedBuyingOffers()
	{
		GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
		boolean hasBought = Arrays.stream(offers)
			.anyMatch(offer -> offer.getState() == GrandExchangeOfferState.BOUGHT);
		boolean isBuying = Arrays.stream(offers)
			.anyMatch(offer -> offer.getState() == GrandExchangeOfferState.BUYING);
		return hasBought && !isBuying;
	}

	public static boolean hasSoldOffer()
	{
		return Arrays.stream(Microbot.getClient().getGrandExchangeOffers()).anyMatch(x -> x.getState() == GrandExchangeOfferState.SOLD);
	}

	public static boolean hasFinishedSellingOffers()
	{
		GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
		boolean hasSold = Arrays.stream(offers)
			.anyMatch(offer -> offer.getState() == GrandExchangeOfferState.SOLD);
		boolean isSelling = Arrays.stream(offers)
			.anyMatch(offer -> offer.getState() == GrandExchangeOfferState.SELLING);
		return hasSold && !isSelling;
	}


	private static int getMaxSlots()
	{
		return Rs2Player.isMember() ? 8 : 3;
	}

	public static boolean walkToGrandExchange()
	{
		return Rs2Walker.walkTo(BankLocation.GRAND_EXCHANGE.getWorldPoint());
	}


	public static int getOfferPrice(int itemId)
	{
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(GE_TRACKER_API_URL + itemId))
			.build();

		try
		{
			String jsonResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(HttpResponse::body)
				.join();

			JsonParser parser = new JsonParser();
			JsonObject jsonElement = parser.parse(new StringReader(jsonResponse)).getAsJsonObject();
			JsonObject data = jsonElement.getAsJsonObject("data");

			return data.get("buying").getAsInt();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return -1;
		}
	}

	public static int getSellPrice(int itemId)
	{
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(GE_TRACKER_API_URL + itemId))
			.build();

		try
		{
			String jsonResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(HttpResponse::body)
				.join();

			JsonParser parser = new JsonParser();
			JsonObject jsonElement = parser.parse(new StringReader(jsonResponse)).getAsJsonObject();
			JsonObject data = jsonElement.getAsJsonObject("data");

			return data.get("selling").getAsInt();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return -1;
		}
	}

	public static int getPrice(int itemId)
	{
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(GE_TRACKER_API_URL + itemId))
			.build();

		try
		{
			String jsonResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(HttpResponse::body)
				.join();

			JsonParser parser = new JsonParser();
			JsonObject jsonElement = parser.parse(new StringReader(jsonResponse)).getAsJsonObject();
			JsonObject data = jsonElement.getAsJsonObject("data");

			return data.get("overall").getAsInt();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return -1;
		}
	}

	public static int getBuyingVolume(int itemId)
	{
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(GE_TRACKER_API_URL + itemId))
			.build();

		try
		{
			String jsonResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(HttpResponse::body)
				.join();

			JsonParser parser = new JsonParser();
			JsonObject jsonElement = parser.parse(new StringReader(jsonResponse)).getAsJsonObject();
			JsonObject data = jsonElement.getAsJsonObject("data");

			return data.get("buyingQuantity").getAsInt();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return -1;
		}
	}

	public static int getSellingVolume(int itemId)
	{
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(GE_TRACKER_API_URL + itemId))
			.build();

		try
		{
			String jsonResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(HttpResponse::body)
				.join();

			JsonParser parser = new JsonParser();
			JsonObject jsonElement = parser.parse(new StringReader(jsonResponse)).getAsJsonObject();
			JsonObject data = jsonElement.getAsJsonObject("data");

			return data.get("sellingQuantity").getAsInt();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return -1;
		}
	}

	static int getOfferQuantity()
	{
		return Microbot.getVarbitValue(4396);
	}

	static int getOfferPrice()
	{
		return Microbot.getVarbitValue(4398);
	}

	public static void setChatboxValue(int value)
	{
		var chatboxInputWidget = Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2);
		if (chatboxInputWidget == null)
		{
			return;
		}
		chatboxInputWidget.setText(value + "*");
		Microbot.getClientThread().runOnClientThreadOptional(() -> {
			Microbot.getClient().setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(value));
			return null;
		});

	}

	/**
	 * Retrieves the first available Grand Exchange slot.
	 * <p>
	 * Returns {@code null} if no slots are available.
	 *
	 * @return the first available {@link GrandExchangeSlots} slot, or {@code null} if none are available
	 */
	public static GrandExchangeSlots getAvailableSlot()
	{
		GrandExchangeSlots[] result = getAvailableSlots();
		return Arrays.stream(result).findFirst().orElse(null);
	}

	/**
	 * Retrieves all currently available Grand Exchange slots.
	 * <p>
	 * Limits the search to the maximum number of slots as defined by {@link #getMaxSlots()}.
	 *
	 * @return an array of available {@link GrandExchangeSlots}
	 */
	public static GrandExchangeSlots[] getAvailableSlots()
	{
		int maxSlots = getMaxSlots();

		return Arrays.stream(GrandExchangeSlots.values())
			.limit(maxSlots)
			.filter(Rs2GrandExchange::isSlotAvailable).toArray(GrandExchangeSlots[]::new);
	}
}
