package net.runelite.client.plugins.microbot.util.grandexchange;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.MenuAction;
import net.runelite.api.VarClientStr;
import net.runelite.api.annotations.Component;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.grandexchange.models.ItemMappingData;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesAnalysis;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesDataPoint;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesInterval;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
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
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.apache.commons.lang3.tuple.Pair;

import java.awt.*;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
@Slf4j
public class Rs2GrandExchange
{
	@Component
	private static final int COLLECT_ALL_BUTTON = 30474246;
	@Component
	private static final int GE_FRAME = InterfaceID.GeOffers.FRAME;
	private static final String GE_TRACKER_API_URL = "https://www.ge-tracker.com/api/items/";
	
	// Wiki API for real-time prices (Alternative source)
	private static final String WIKI_API_URL = "https://prices.runescape.wiki/api/v1/osrs/latest?id=";
	private static final String WIKI_TIMESERIES_URL = "https://prices.runescape.wiki/api/v1/osrs/timeseries";
	private static final String WIKI_MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping";
	
	// Caches for different data types
	private static final Map<Integer, WikiPrice> priceCache = new HashMap<>();
	private static final Map<Integer, ItemMappingData> mappingCache = new HashMap<>();
	private static final long PRICE_CACHE_DURATION = 60000; // 1 minutes
	
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
				String searchName = request.getItemName();
				if (searchName.length() >= 26) {
					searchName = searchName.substring(0, 25); // Grand Exchange item names are limited to 25 characters.
				}
				Rs2Keyboard.typeString(request.getItemName());

				if (!Rs2Widget.sleepUntilHasWidgetText(searchName, 162, 43, false, 5000)) break;

				sleepUntil(() -> getSearchResultWidget(request.getItemName(), request.isExact()) != null, 2200);

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
		if(!isOfferScreenOpen())
		{
			log.info("Grand Exchange offer screen is not open, cannot collect offer.");
			return false;
		}
		Widget[] children = GrandExchangeWidget.getCollectButtons();
		String desiredAction;
		int identifier;
		int param0;
		if (children.length == 0)
		{
			log.info("No collect buttons found on the offer screen.");
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
			if (Rs2Bank.openBank())
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
	
	/**
	 * Gets the index (0-7) of the slot in the GE interface.
	 *
	 * @param slot the GrandExchangeSlots enum value
	 * @return the index of the slot (0-7)
	 */
	public static int getSlotIndex(GrandExchangeSlots slot) {
		return slot.ordinal();
	}
	
	/**
	 * Checks if a specific Grand Exchange slot has a completed buy offer ready to collect.
	 *
	 * @param slot The GrandExchangeSlots to check for a completed buy offer
	 * @return true if the specified slot has a completed buy offer, false otherwise
	 */
	public static boolean hasBoughtOffer(GrandExchangeSlots slot) {
		if (slot == null) {
			return false;
		}
		
		// Get the GrandExchangeOffer for this slot from the client
		GrandExchangeOffer offer = Microbot.getClient().getGrandExchangeOffers()[slot.ordinal()];
		
		// Check if the offer exists and is in BOUGHT state
		return offer != null && offer.getState() == GrandExchangeOfferState.BOUGHT;
	}

	/**
	 * Checks if a specific Grand Exchange slot has a completed sell offer ready to collect.
	 *
	 * @param slot The GrandExchangeSlots to check for a completed sell offer
	 * @return true if the specified slot has a completed sell offer, false otherwise
	 */
	public static boolean hasSoldOffer(GrandExchangeSlots slot) {
		if (slot == null) {
			return false;
		}
		
		// Get the GrandExchangeOffer for this slot from the client
		GrandExchangeOffer offer = Microbot.getClient().getGrandExchangeOffers()[slot.ordinal()];
		
		// Check if the offer exists and is in SOLD state
		return offer != null && offer.getState() == GrandExchangeOfferState.SOLD;
	}
	
	/**
	 * Finds the Grand Exchange slot currently holding an offer for the specified item.
	 *
	 * @param itemId The ID of the item to find in the Grand Exchange offers
	 * @param isSelling true to look for a sell offer, false for a buy offer
	 * @return The GrandExchangeSlots containing the item, or null if not found
	 */
	public static GrandExchangeSlots findSlotForItem(int itemId, boolean isSelling) {
		GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
		
		for (int i = 0; i < offers.length; i++) {
			GrandExchangeOffer offer = offers[i];
			
			// Skip empty slots
			if (offer == null || offer.getItemId() == 0) {
				continue;
			}
			
			// Check if this is a selling or buying offer based on state
			boolean offerIsSelling = offer.getState() == GrandExchangeOfferState.SELLING || 
								     offer.getState() == GrandExchangeOfferState.SOLD;
								     
			// Skip if the selling state doesn't match what we're looking for
			if (offerIsSelling != isSelling) {
				continue;
			}
			
			// Check if this offer contains our item
			if (offer.getItemId() == itemId) {
				return GrandExchangeSlots.values()[i];
			}
		}
		
		return null;
	}

	/**
	 * Finds the Grand Exchange slot currently holding an offer for the specified item name.
	 * This is a convenience method that looks up the item ID by name first.
	 *
	 * @param itemName The name of the item to find in the Grand Exchange offers
	 * @param isSelling true to look for a sell offer, false for a buy offer
	 * @return The GrandExchangeSlots containing the item, or null if not found
	 */
	public static GrandExchangeSlots findSlotForItem(String itemName, boolean isSelling) {
		int itemId = Microbot.getRs2ItemManager().getItemId(itemName);
		if (itemId == -1) {
			return null;
		}
		
		return findSlotForItem(itemId, isSelling);
	}
	
	/**
	 * Gets detailed information about a Grand Exchange offer in the specified slot.
	 *
	 * @param slot The GrandExchangeSlots to check
	 * @return A GrandExchangeOfferDetails object with comprehensive offer information, or null if no offer exists
	 */
	public static GrandExchangeOfferDetails getOfferDetails(GrandExchangeSlots slot) {
		if (slot == null) {
			return null;
		}
		
		GrandExchangeOffer offer = Microbot.getClient().getGrandExchangeOffers()[slot.ordinal()];
		if (offer == null || offer.getItemId() == 0) {
			return null;
		}
		
		// Determine if selling based on state
		boolean isSelling = offer.getState() == GrandExchangeOfferState.SELLING || 
						    offer.getState() == GrandExchangeOfferState.SOLD;
		
		return new GrandExchangeOfferDetails(
			offer.getItemId(),
			offer.getQuantitySold(),
			offer.getTotalQuantity(),
			offer.getPrice(),
			offer.getSpent(),
			offer.getState(),
			isSelling
		);
	}

	
	
	/**
	 * Collects items from a specific Grand Exchange slot.
	 *
	 * @param slot The GrandExchangeSlots to collect from
	 * @param toBank true to send items directly to the bank, false for inventory
	 * @return true if collection was successful, false otherwise
	 */
	public static boolean collectOffer(GrandExchangeSlots slot, boolean toBank) {
		if (slot == null) {
			log.info("Slot is null, cannot collect offer.");
			return false;
		}
		
		Widget slotWidget = GrandExchangeWidget.getSlot(slot);
		if (slotWidget == null) {
			log.info("Slot widget not found for slot: " + slot);
			return false;
		}
		Widget itemNameWidget = slotWidget.getChild(19);
		if (itemNameWidget == null || itemNameWidget.getText() == null) {
			log.error("has no item name widget for slot: " + slot);
			return false;
		}
		String currentItemName = itemNameWidget.getText();
		log.info("Collecting offer for item: " + currentItemName + " in slot: " + slot);

		viewOffer(slotWidget);
		if (!sleepUntil(Rs2GrandExchange::isOfferScreenOpen, 3000)) {
			log.info("Failed to open offer screen for slot: " + slot);
			return false;
		}
		
		// Now collect using the existing private collectOffer method
		return collectOffer(toBank);
	}		
	
	/**
	 * Helper method to get a GrandExchangeSlots enum value from its index.
	 * 
	 * @param index The index of the slot (0-7)
	 * @return The corresponding GrandExchangeSlots enum value, or null if the index is out of range
	 */
	public static GrandExchangeSlots getSlotFromIndex(int index) {
		if (index < 0 || index >= GrandExchangeSlots.values().length) {
			return null;
		}
		return GrandExchangeSlots.values()[index];
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

	/**
	 * Gets real-time price data with caching from OSRS Wiki API (primary) or GE Tracker (fallback).
	 * This provides the most current market prices for better trading decisions.
	 * 
	 * @param itemId The item ID to get prices for
	 * @return CachedPrice object with buy/sell prices and volume, or null if unavailable
	 */
	public static WikiPrice getRealTimePrices(int itemId) {
		// Check cache first
		WikiPrice cached = priceCache.get(itemId);
		if (cached != null && !cached.isExpired(PRICE_CACHE_DURATION)) {
			return cached;
		}
		
		// Try Wiki API first (more reliable and current)
		WikiPrice wikiPrice = getWikiPrices(itemId);
		if (wikiPrice != null) {
			priceCache.put(itemId, wikiPrice);
			return wikiPrice;
		}
		
		// Fallback to GE Tracker
		try {
			int buyPrice = getPrice(itemId);
			int sellPrice = getSellPrice(itemId);
			int volume = getBuyingVolume(itemId);
			
			if (buyPrice > 0 && sellPrice > 0) {
				WikiPrice price = new WikiPrice(buyPrice, sellPrice, volume);
				priceCache.put(itemId, price);
				return price;
			}
		} catch (Exception e) {
			// Log but don't fail completely
		}
		
		return null;
	}
	
	/**
	 * Gets price data from OSRS Wiki API (real-time market data).
	 * 
	 * @param itemId The item ID
	 * @return CachedPrice with current market prices or null if failed
	 */
	private static WikiPrice getWikiPrices(int itemId) {
		try {
			HttpClient httpClient = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(WIKI_API_URL + itemId))
				.header("User-Agent", "OSRS - Price Fetcher")
				.build();

			String jsonResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(HttpResponse::body)
				.join();

			JsonParser parser = new JsonParser();
			JsonObject jsonElement = parser.parse(new StringReader(jsonResponse)).getAsJsonObject();
			JsonObject data = jsonElement.getAsJsonObject("data");
			
			if (data != null && data.has(String.valueOf(itemId))) {
				JsonObject itemData = data.getAsJsonObject(String.valueOf(itemId));
				
				int buyPrice = itemData.has("high") ? itemData.get("high").getAsInt() : -1;
				int sellPrice = itemData.has("low") ? itemData.get("low").getAsInt() : -1;
				int volume = itemData.has("highVolume") ? itemData.get("highVolume").getAsInt() : 0;
				
				if (buyPrice > 0 && sellPrice > 0) {
					return new WikiPrice(buyPrice, sellPrice, volume);
				}
			}else{
				log.warn("No price data found for item ID: " + itemId);
			}

		} catch (Exception e) {
			log.error("Failed to fetch prices from OSRS Wiki API for item ID: " + itemId, e);
			// Wiki API failed, will fall back to GE Tracker
		}
		
		return null;
	}
	
	/**
	 * Calculates an intelligent buy price based on market conditions and retry attempts.
	 * Increases price aggressively on retries to ensure successful purchases.
	 * 
	 * @param itemId The item ID
	 * @param basePercentage Base percentage multiplier (e.g., 1.1 for 110%)
	 * @param retryAttempt Number of previous failed attempts (0 for first attempt)
	 * @return Calculated buy price
	 */
	public static int getAdaptiveBuyPrice(int itemId, double basePercentage, int retryAttempt) {
		WikiPrice priceData = getRealTimePrices(itemId);
		
		if (priceData != null) {
			// Use real-time high price as base
			int basePrice = priceData.buyPrice;
			
			// Adjust based on retry attempts - get more aggressive each time
			double retryMultiplier = 1.0 + (retryAttempt * 0.05); // +5% per retry
			double finalMultiplier = basePercentage * retryMultiplier;
			
			// Consider market volume for additional adjustment
			if (priceData.volume < 100) {
				finalMultiplier *= 1.1; // +10% for low volume items
			}
			
			return (int) Math.ceil(basePrice * finalMultiplier);
		}
		
		// Fallback to old method if price data unavailable
		int gePrice = getPrice(itemId);
		if (gePrice > 0) {
			double retryMultiplier = 1.0 + (retryAttempt * 0.05);
			return (int) Math.ceil(gePrice * basePercentage * retryMultiplier);
		}
		
		return -1;
	}
	
	/**
	 * Calculates an intelligent sell price based on market conditions and retry attempts.
	 * Decreases price on retries to ensure successful sales.
	 * 
	 * @param itemId The item ID
	 * @param basePercentage Base percentage multiplier (e.g., 0.9 for 90%)
	 * @param retryAttempt Number of previous failed attempts (0 for first attempt)
	 * @return Calculated sell price
	 */
	public static int getAdaptiveSellPrice(int itemId, double basePercentage, int retryAttempt) {
		WikiPrice priceData = getRealTimePrices(itemId);
		
		if (priceData != null) {
			// Use real-time low price as base
			int basePrice = priceData.sellPrice;
			
			// Adjust based on retry attempts - get more aggressive each time
			double retryMultiplier = 1.0 - (retryAttempt * 0.05); // -5% per retry
			double finalMultiplier = basePercentage * retryMultiplier;
			
			// Consider market volume for additional adjustment
			if (priceData.volume < 100) {
				finalMultiplier *= 0.95; // -5% for low volume items
			}
			
			return (int) Math.floor(basePrice * finalMultiplier);
		}
		
		// Fallback to old method if price data unavailable
		int gePrice = getSellPrice(itemId);
		if (gePrice > 0) {
			double retryMultiplier = 1.0 - (retryAttempt * 0.05);
			return (int) Math.floor(gePrice * basePercentage * retryMultiplier);
		}
		
		return -1;
	}

	
	/**
	 * Gets time-series price data from OSRS Wiki API.
	 * Retrieves historical price data at specified intervals.
	 * 
	 * @param itemId The item ID to get time-series data for
	 * @param interval The time interval for data points
	 * @param fromTimestamp Optional timestamp to start from (null for current time)
	 * @return TimeSeriesAnalysis with historical price data and averages
	 */
	public static TimeSeriesAnalysis getTimeSeriesData(int itemId, TimeSeriesInterval interval, Long fromTimestamp) {
		try {
			// Build the URL with parameters
			StringBuilder urlBuilder = new StringBuilder(WIKI_TIMESERIES_URL);
			urlBuilder.append("?id=").append(itemId);
			urlBuilder.append("&timestep=").append(interval.getApiValue());
			
			// Add timestamp parameter if specified
			if (fromTimestamp != null) {
				urlBuilder.append("&timestamp=").append(fromTimestamp);
			}
			
			HttpClient httpClient = HttpClient.newHttpClient();
			String finalUrl = urlBuilder.toString();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(finalUrl))
					.header("User-Agent", "Time Series Price Analysis")
					.build();
			
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			
			if (response.statusCode() == 200) {
				JsonParser parser = new JsonParser();
				JsonObject root = parser.parse(response.body()).getAsJsonObject();
				
				if (root.has("data")) {
					JsonArray itemDataArray = root.getAsJsonArray("data");
					List<TimeSeriesDataPoint> dataPoints = new ArrayList<>();
					
					for (int i = 0; i < itemDataArray.size(); i++) {
						JsonObject dataPoint = itemDataArray.get(i).getAsJsonObject();
						
						long timestamp = dataPoint.has("timestamp") ? dataPoint.get("timestamp").getAsLong() : 0;
						
						// Handle null values in JSON response
						int highPrice = 0;
						int lowPrice = 0;
						int highPriceVolume = 0;
						int lowPriceVolume = 0;
						
						if (dataPoint.has("avgHighPrice") && !dataPoint.get("avgHighPrice").isJsonNull()) {
							highPrice = dataPoint.get("avgHighPrice").getAsInt();
						}
						
						if (dataPoint.has("avgLowPrice") && !dataPoint.get("avgLowPrice").isJsonNull()) {
							lowPrice = dataPoint.get("avgLowPrice").getAsInt();
						}
						
						if (dataPoint.has("highPriceVolume") && !dataPoint.get("highPriceVolume").isJsonNull()) {
							highPriceVolume = dataPoint.get("highPriceVolume").getAsInt();
						}
						
						if (dataPoint.has("lowPriceVolume") && !dataPoint.get("lowPriceVolume").isJsonNull()) {
							lowPriceVolume = dataPoint.get("lowPriceVolume").getAsInt();
						}
						
						log.debug("Time series data point: timestamp={}, highPrice={}, lowPrice={}, highVol={}, lowVol={}", 
								timestamp, highPrice, lowPrice, highPriceVolume, lowPriceVolume);
						if (highPrice > 0 && lowPrice > 0) {
							dataPoints.add(new TimeSeriesDataPoint(timestamp, highPrice, lowPrice, highPriceVolume, lowPriceVolume));
						}
					}
					
					return new TimeSeriesAnalysis(dataPoints, interval);
				}
			}else{
				log.warn("Failed to fetch time-series data for item {}: HTTP {} - URL: {}", 
						itemId, response.statusCode(), finalUrl);
				log.debug("Response body: {}", response.body());
			}
		} catch (Exception e) {
			log.error("Failed to fetch time-series data for item {}: {}", itemId, e.getMessage(), e);
		}
		
		// Return empty analysis if failed
		return new TimeSeriesAnalysis(new ArrayList<>(), interval);
	}
	
	/**
	 * Gets time-series data starting from current time going back.
	 * This is a convenience method for the most common use case.
	 * 
	 * @param itemId The item ID
	 * @param interval The time interval
	 * @return TimeSeriesAnalysis with recent historical data
	 */
	public static TimeSeriesAnalysis getTimeSeriesData(int itemId, TimeSeriesInterval interval) {
		return getTimeSeriesData(itemId, interval, null);
	}
	
	/**
	 * Gets average price over a specific time period using time-series data.
	 * This provides more accurate pricing than single-point GE prices.
	 * 
	 * @param itemId The item ID
	 * @param interval The time interval for historical data (default: 1 hour)
	 * @return Average price over the time period, or -1 if unavailable
	 */
	public static int getAveragePrice(int itemId, TimeSeriesInterval interval) {
		TimeSeriesAnalysis analysis = getTimeSeriesData(itemId, interval);
		return analysis.averagePrice > 0 ? analysis.averagePrice : -1;
	}
	
	/**
	 * Gets average price with default 1-hour interval.
	 * 
	 * @param itemId The item ID
	 * @return Average price over the last hour, or -1 if unavailable
	 */
	public static int getAveragePrice(int itemId) {
		return getAveragePrice(itemId, TimeSeriesInterval.ONE_HOUR);
	}
	
	/**
	 * Gets item mapping data including trade limits from OSRS Wiki API.
	 * 
	 * @param itemId The item ID to get mapping data for
	 * @return ItemMappingData with trade limits and metadata, or null if unavailable
	 */
	public static ItemMappingData getItemMappingData(int itemId) {
		// Check cache first
		ItemMappingData cached = mappingCache.get(itemId);
		if (cached != null && !isMappingExpired(cached)) {
			return cached;
		}
		
		// Fetch from Wiki API
		ItemMappingData mappingData = fetchItemMappingData(itemId);
		if (mappingData != null) {
			mappingCache.put(itemId, mappingData);
		}
		
		return mappingData;
	}
	
	/**
	 * Fetches item mapping data from OSRS Wiki API.
	 * 
	 * @param itemId The item ID
	 * @return ItemMappingData or null if failed
	 */
	private static ItemMappingData fetchItemMappingData(int itemId) {
		try {
			HttpClient httpClient = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(WIKI_MAPPING_URL))
				.header("User-Agent", "OSRS Item Mapping Fetcher")
				.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() == 200) {
				JsonParser parser = new JsonParser();
				JsonArray mappingArray = parser.parse(response.body()).getAsJsonArray();
				
				// Find the item in the mapping array
				for (int i = 0; i < mappingArray.size(); i++) {
					JsonObject item = mappingArray.get(i).getAsJsonObject();
					
					if (item.has("id") && item.get("id").getAsInt() == itemId) {
						// Extract data with null checks
						String name = item.has("name") && !item.get("name").isJsonNull() ? 
							item.get("name").getAsString() : "";
						String examine = item.has("examine") && !item.get("examine").isJsonNull() ? 
							item.get("examine").getAsString() : "";
						boolean members = item.has("members") && item.get("members").getAsBoolean();
						int limit = item.has("limit") && !item.get("limit").isJsonNull() ? 
							item.get("limit").getAsInt() : -1;
						int value = item.has("value") && !item.get("value").isJsonNull() ? 
							item.get("value").getAsInt() : 0;
						int lowalch = item.has("lowalch") && !item.get("lowalch").isJsonNull() ? 
							item.get("lowalch").getAsInt() : 0;
						int highalch = item.has("highalch") && !item.get("highalch").isJsonNull() ? 
							item.get("highalch").getAsInt() : 0;
						String icon = item.has("icon") && !item.get("icon").isJsonNull() ? 
							item.get("icon").getAsString() : "";
						
						return new ItemMappingData(itemId, name, examine, members, limit, value, lowalch, highalch, icon);
					}
				}
			} else {
				log.warn("Failed to fetch item mapping data: HTTP {} for URL: {}", 
						response.statusCode(), WIKI_MAPPING_URL);
			}
		} catch (Exception e) {
			log.error("Failed to fetch item mapping data for item {}: {}", itemId, e.getMessage(), e);
		}
		
		return null;
	}
	
	/**
	 * Checks if mapping data is expired (items mapping rarely changes).
	 */
	private static boolean isMappingExpired(ItemMappingData mappingData) {
		// For simplicity, assume mapping data never expires during a session
		// In a real implementation, you might want to add a timestamp to ItemMappingData
		return false;
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
			if (!jsonElement.has("data"))
			{
				log.debug("No data found for item ID: " + itemId);
				return -1;
			}
			JsonObject data = jsonElement.getAsJsonObject("data");
			if (!data.has("overall"))
			{
				log.debug("No overall price found for item ID: " + itemId);
				return -1;
			}
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

	/**
	 * Returns the count of currently available Grand Exchange slots.
	 * <p>
	 * This method counts the number of slots that are available for new offers.
	 *
	 * @return the number of available Grand Exchange slots
	 */
	public static int getAvailableSlotsCount()
	{
		return (int) Arrays.stream(getAvailableSlots()).count();
	}
	
	/**
	 * Gets the exact number of items bought from a specific Grand Exchange slot offer.
	 * This method extracts the actual quantity purchased from the offer's current state.
	 *
	 * @param slot The GrandExchangeSlots to check for bought items
	 * @return The number of items actually bought (quantitySold for buy offers), or 0 if no items bought
	 */
	public static int getItemsBoughtFromOffer(GrandExchangeSlots slot) {
		if (slot == null) {
			return 0;
		}
		
		GrandExchangeOffer offer = Microbot.getClient().getGrandExchangeOffers()[slot.ordinal()];
		if (offer == null || offer.getItemId() == 0) {
			return 0;
		}
		
		// For buy offers, quantitySold actually represents items purchased
		// Only count for buying states (BUYING, BOUGHT, CANCELLED_BUY)
		boolean isBuyOffer = offer.getState() == GrandExchangeOfferState.BUYING || 
							 offer.getState() == GrandExchangeOfferState.BOUGHT ||
							 offer.getState() == GrandExchangeOfferState.CANCELLED_BUY;
		
		if (isBuyOffer) {
			return offer.getQuantitySold(); // This is actually items bought for buy offers
		}
		
		return 0;
	}
	
	/**
	 * Gets the exact number of items sold from a specific Grand Exchange slot offer.
	 * This method extracts the actual quantity sold from the offer's current state.
	 *
	 * @param slot The GrandExchangeSlots to check for sold items
	 * @return The number of items actually sold, or 0 if no items sold
	 */
	public static int getItemsSoldFromOffer(GrandExchangeSlots slot) {
		if (slot == null) {
			return 0;
		}
		
		GrandExchangeOffer offer = Microbot.getClient().getGrandExchangeOffers()[slot.ordinal()];
		if (offer == null || offer.getItemId() == 0) {
			return 0;
		}
		
		// For sell offers, quantitySold represents items sold
		// Only count for selling states (SELLING, SOLD, CANCELLED_SELL)
		boolean isSellOffer = offer.getState() == GrandExchangeOfferState.SELLING || 
							  offer.getState() == GrandExchangeOfferState.SOLD ||
							  offer.getState() == GrandExchangeOfferState.CANCELLED_SELL;
		
		if (isSellOffer) {
			return offer.getQuantitySold();
		}
		
		return 0;
	}
	
	/**
	 * Collects a specific offer and returns the exact number of items obtained.
	 * This is a wrapper around collectOffer that tracks the actual quantity collected.
	 *
	 * @param slot The GrandExchangeSlots to collect from
	 * @param toBank true to send items directly to the bank, false for inventory
	 * @param itemId The ID of the item being collected (for tracking purposes)
	 * @return The number of items actually collected, or 0 if collection failed
	 */
	public static int collectOfferAndGetQuantity(GrandExchangeSlots slot, boolean toBank, int itemId) {
		if (slot == null || itemId == 0) {
			log.error("Invalid slot or item ID for collecting offer");
			return 0;
		}
		
		// Get the offer details before collection
		GrandExchangeOfferDetails details = getOfferDetails(slot);
		if (details == null) {
			log.error("No offer details found for slot: " + slot);
			return 0;
		}						

		
		
		// Collect the offer
		boolean collected = collectOffer(slot, toBank);
		if (!collected) {
			log.info("is offerScreenOpen: " + isOfferScreenOpen()+ ", hasBoughtOffer: " + hasBoughtOffer(slot) + 
					", itemId: " + itemId + ", slot: " + slot);
			log.error("Failed to collect offer for slot: " + slot + ", item ID: " + itemId);
			return 0;
		}				
		return details.getQuantitySold(); // Return the quantity sold from the offer details
	}
	
	/**
	 * Finds and returns all slots that have completed offers ready for collection.
	 * This includes both bought and sold offers that need to be collected.
	 *
	 * @return Map of GrandExchangeSlots to their corresponding offer details for completed offers
	 */
	public static Map<GrandExchangeSlots, GrandExchangeOfferDetails> getCompletedOffers() {
		Map<GrandExchangeSlots, GrandExchangeOfferDetails> completedOffers = new HashMap<>();
		
		GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
		for (int i = 0; i < offers.length; i++) {
			GrandExchangeOffer offer = offers[i];
			
			if (offer == null || offer.getItemId() == 0) {
				continue;
			}
			
			// Check if offer is completed (bought, sold, or cancelled with items)
			boolean isCompleted = offer.getState() == GrandExchangeOfferState.BOUGHT ||
								  offer.getState() == GrandExchangeOfferState.SOLD ||
								  (offer.getState() == GrandExchangeOfferState.CANCELLED_BUY && offer.getQuantitySold() > 0) ||
								  (offer.getState() == GrandExchangeOfferState.CANCELLED_SELL && offer.getQuantitySold() > 0);
			
			if (isCompleted) {
				GrandExchangeSlots slot = GrandExchangeSlots.values()[i];
				GrandExchangeOfferDetails details = getOfferDetails(slot);
				if (details != null) {
					completedOffers.put(slot, details);
				}
			}
		}
		
		return completedOffers;
	}
	
	/**
	 * Checks if an offer was cancelled and still has items that can be collected.
	 * This is important for tracking partial purchases/sales from cancelled offers.
	 *
	 * @param slot The GrandExchangeSlots to check
	 * @return true if the offer was cancelled but still has collectable items
	 */
	public static boolean isCancelledOfferWithItems(GrandExchangeSlots slot) {
		if (slot == null) {
			return false;
		}
		
		GrandExchangeOffer offer = Microbot.getClient().getGrandExchangeOffers()[slot.ordinal()];
		if (offer == null || offer.getItemId() == 0) {
			return false;
		}
		
		boolean isCancelled = offer.getState() == GrandExchangeOfferState.CANCELLED_BUY ||
							  offer.getState() == GrandExchangeOfferState.CANCELLED_SELL;
		
		return isCancelled && offer.getQuantitySold() > 0;
	}
	

}
