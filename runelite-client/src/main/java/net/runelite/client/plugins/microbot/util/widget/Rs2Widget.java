package net.runelite.client.plugins.microbot.util.widget;

import net.runelite.api.MenuAction;
import net.runelite.api.annotations.Component;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public class Rs2Widget {

    public static boolean sleepUntilHasWidgetText(String text, int widgetId, int childId, boolean exact, int sleep) {
        return sleepUntilTrue(() -> hasWidgetText(text, widgetId, childId, exact), 300, sleep);
    }

    public static boolean sleepUntilHasNotWidgetText(String text, int widgetId, int childId, boolean exact, int sleep) {
        return sleepUntilTrue(() -> !hasWidgetText(text, widgetId, childId, exact), 300, sleep);
    }

    public static boolean sleepUntilHasWidget(String text) {
        sleepUntil(() -> findWidget(text, null, false) != null);
        return findWidget(text, null, false) != null;
    }

    public static boolean clickWidget(String text, Optional<Integer> widgetId, int childId, boolean exact) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {

            Widget widget;
            if (!widgetId.isPresent()) {
                widget = findWidget(text, null, exact);
            } else {
                Widget rootWidget = getWidget(widgetId.get(), childId);
                List<Widget> rootWidgets = new ArrayList<>();
                rootWidgets.add(rootWidget);
                widget = findWidget(text, rootWidgets, exact);
            }

            if (widget != null) {
                clickWidget(widget);
            }

            return widget != null;

        }).orElse(false);
    }

    public static boolean clickWidget(Widget widget) {
        if (widget != null) {
            Microbot.getMouse().click(widget.getBounds());
            return true;
        }
        return false;
    }

    public static boolean clickWidget(String text) {
        return clickWidget(text, Optional.empty(), 0, false);
    }

    public static boolean clickWidget(String text, boolean exact) {
        return clickWidget(text, Optional.empty(), 0, exact);
    }

    public static boolean clickWidget(int parentId, int childId) {
        Widget widget = getWidget(parentId, childId);
        return clickWidget(widget);
    }

    public static boolean isWidgetVisible(@Component int id) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget widget = getWidget(id);
            if (widget == null) return false;
            return !widget.isHidden();
        }).orElse(false);
    }

    public static boolean isWidgetVisible(int widgetId, int childId) {
       return  Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget widget = getWidget(widgetId, childId);
            if (widget == null) return false;
            return !widget.isHidden();
        }).orElse(false);
    }

    public static Widget getWidget(@Component int id) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getWidget(id)).orElse(null);
    }

    public static boolean isHidden(int parentId, int childId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget widget = Microbot.getClient().getWidget(parentId, childId);
            if (widget == null) return true;
            return widget.isHidden();
        }).orElse(false);
    }

    public static boolean isHidden(@Component int id) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget widget = Microbot.getClient().getWidget(id);
            if (widget == null) return true;
            return widget.isHidden();
        }).orElse(false);
    }

    public static Widget getWidget(int id, int child) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getWidget(id, child))
                .orElse(null);
    }

    public static int getChildWidgetSpriteID(int id, int childId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getWidget(id, childId).getSpriteId())
                .orElse(0);
    }

    public static String getChildWidgetText(int id, int childId) {
        Widget widget = getWidget(id, childId);
        if (widget != null) {
            return widget.getText();
        }
        return "";
    }

    public static boolean clickWidget(int id) {
        Widget widget = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getWidget(id)).orElse(null);;
        if (widget == null || isHidden(id)) return false;
        Microbot.getMouse().click(widget.getBounds());
        return true;
    }

    public static boolean clickChildWidget(int id, int childId) {
        Widget widget = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getWidget(id)).orElse(null);;
        if (widget == null) return false;
        Microbot.getMouse().click(widget.getChild(childId).getBounds());
        return true;
    }

    public static Widget findWidget(String text, List<Widget> children) {
        return findWidget(text, children, false);
    }

	public static boolean hasWidgetText(String text, int componentId, boolean exact) {
		return Microbot.getClientThread().runOnClientThreadOptional(() -> {
			Widget rootWidget = getWidget(componentId);
			if (rootWidget == null) return false;

			// Use findWidget to perform the search on all child types
			Widget foundWidget = findWidget(text, List.of(rootWidget), exact);
			return foundWidget != null;
		}).orElse(false);
	}

    public static boolean hasWidgetText(String text, int widgetId, int childId, boolean exact) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget rootWidget = getWidget(widgetId, childId);
            if (rootWidget == null) return false;

            // Use findWidget to perform the search on all child types
            Widget foundWidget = findWidget(text, List.of(rootWidget), exact);
            return foundWidget != null;
        }).orElse(false);
    }

    public static Widget findWidget(String text) {
        return findWidget(text, null, false);
    }

    public static Widget findWidget(String text, boolean exact) {
        return findWidget(text, null, exact);
    }

    public static boolean hasWidget(String text) {
        return findWidget(text, null, false) != null;
    }

    /**
     * Searches for a widget with text that matches the specified criteria, either in the provided child widgets
     * or across all root widgets if children are not specified.
     *
     * @param text     The text to search for within the widgets.
     * @param children A list of child widgets to search within. If null, searches through all root widgets.
     * @param exact    Whether the search should match the text exactly or allow partial matches.
     * @return The widget containing the specified text, or null if no match is found.
     */
    public static Widget findWidget(String text, List<Widget> children, boolean exact) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget foundWidget = null;
            if (children == null) {
                // Search through root widgets if no specific children are provided
                List<Widget> rootWidgets = Arrays.stream(Microbot.getClient().getWidgetRoots())
                        .filter(x -> x != null && !x.isHidden()).collect(Collectors.toList());
                for (Widget rootWidget : rootWidgets) {
                    if (rootWidget == null) continue;
                    if (matchesText(rootWidget, text, exact)) {
                        return rootWidget;
                    }
                    foundWidget = searchChildren(text, rootWidget, exact);
                    if (foundWidget != null) return foundWidget;
                }
            } else {
                // Search within provided child widgets
                for (Widget child : children) {
                    foundWidget = searchChildren(text, child, exact);
                    if (foundWidget != null) break;
                }
            }
            return foundWidget;
        }).orElse(null);
    }

    /**
     * Recursively searches through all child widgets of the specified widget for a match with the given text.
     *
     * @param text  The text to search for within the widget and its children.
     * @param child The widget to search within.
     * @param exact Whether the search should match the text exactly or allow partial matches.
     * @return The widget containing the specified text, or null if no match is found.
     */
    public static Widget searchChildren(String text, Widget child, boolean exact) {
        if (matchesText(child, text, exact)) return child;

        List<Widget[]> childGroups = Stream.of(child.getChildren(), child.getNestedChildren(), child.getDynamicChildren(), child.getStaticChildren())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        for (Widget[] childGroup : childGroups) {
            if (childGroup != null) {
                for (Widget nestedChild : Arrays.stream(childGroup).filter(w -> w != null && !w.isHidden()).collect(Collectors.toList())) {
                    Widget found = searchChildren(text, nestedChild, exact);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /**
     * Checks if the text or any action in the widget matches the search criteria.
     *
     * @param widget The widget to check for the specified text or action.
     * @param text   The text to match within the widget’s content.
     * @param exact  Whether the match should be exact or allow partial matches.
     * @return True if the widget's text or any action matches the search criteria, false otherwise.
     */
    private static boolean matchesText(Widget widget, String text, boolean exact) {
        String cleanText = Rs2UiHelper.stripColTags(widget.getText());
        String cleanName = Rs2UiHelper.stripColTags(widget.getName());

        if (exact) {
            if (cleanText.equalsIgnoreCase(text) || cleanName.equalsIgnoreCase(text)) return true;
        } else {
            if (cleanText.toLowerCase().contains(text.toLowerCase()) || cleanName.toLowerCase().contains(text.toLowerCase()))
                return true;
        }

        if (widget.getActions() != null) {
            for (String action : widget.getActions()) {
                if (action != null) {
                    String cleanAction = Rs2UiHelper.stripColTags(action);
                    if (exact ? cleanAction.equalsIgnoreCase(text) : cleanAction.toLowerCase().contains(text.toLowerCase())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Searches for a widget with the specified sprite ID among root widgets or the specified child widgets.
     *
     * @param spriteId The sprite ID to search for.
     * @param children A list of child widgets to search within. If null, searches root widgets.
     * @return The widget with the specified sprite ID, or null if not found.
     */
    public static Widget findWidget(int spriteId, List<Widget> children) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget foundWidget = null;

            if (children == null) {
                // Search through root widgets if no specific children are provided
                List<Widget> rootWidgets = Arrays.stream(Microbot.getClient().getWidgetRoots())
                        .filter(widget -> widget != null && !widget.isHidden())
                        .collect(Collectors.toList());
                for (Widget rootWidget : rootWidgets) {
                    if (rootWidget == null) continue;
                    if (matchesSpriteId(rootWidget, spriteId)) {
                        return rootWidget;
                    }
                    foundWidget = searchChildren(spriteId, rootWidget);
                    if (foundWidget != null) return foundWidget;
                }
            } else {
                // Search within provided child widgets
                for (Widget child : children) {
                    foundWidget = searchChildren(spriteId, child);
                    if (foundWidget != null) break;
                }
            }
            return foundWidget;
        }).orElse(null);
    }

    /**
     * Recursively searches through the child widgets of the given widget for a match with the specified sprite ID.
     *
     * @param spriteId The sprite ID to search for.
     * @param child    The widget to search within.
     * @return The widget with the specified sprite ID, or null if not found.
     */
    public static Widget searchChildren(int spriteId, Widget child) {
        if (matchesSpriteId(child, spriteId)) return child;

        List<Widget[]> childGroups = Stream.of(child.getChildren(), child.getNestedChildren(), child.getDynamicChildren(), child.getStaticChildren())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        for (Widget[] childGroup : childGroups) {
            if (childGroup != null) {
                for (Widget nestedChild : Arrays.stream(childGroup).filter(w -> w != null && !w.isHidden()).collect(Collectors.toList())) {
                    Widget found = searchChildren(spriteId, nestedChild);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /**
     * Checks if a widget's sprite ID matches the specified sprite ID.
     *
     * @param widget   The widget to check.
     * @param spriteId The sprite ID to match.
     * @return True if the widget's sprite ID matches the specified sprite ID, false otherwise.
     */
    private static boolean matchesSpriteId(Widget widget, int spriteId) {
        return widget != null && widget.getSpriteId() == spriteId;
    }

    public static void clickWidgetFast(int packetId, int identifier) {
        Widget widget = getWidget(packetId);
        clickWidgetFast(widget, -1, identifier);
    }

    public static void clickWidgetFast(Widget widget, int param0, int identifier) {
        int param1 = widget.getId();
        String target = "";
        MenuAction menuAction = MenuAction.CC_OP;
        Microbot.doInvoke(new NewMenuEntry(param0 != -1 ? param0 : widget.getType(), param1, menuAction.getId(), identifier, widget.getItemId(), target), widget.getBounds());
    }

    public static void clickWidgetFast(Widget widget, int param0) {
        clickWidgetFast(widget, param0, 1);
    }

    public static void clickWidgetFast(Widget widget) {
        clickWidgetFast(widget, -1, 1);
    }

    // check if production widget is open
    public static boolean isProductionWidgetOpen() {
        return isWidgetVisible(270, 0);
    }

    // check if GoldCrafting widget is open
    public static boolean isGoldCraftingWidgetOpen() {
        return isWidgetVisible(446, 0);
    }

    // check if SilverCrafting widget is open
    public static boolean isSilverCraftingWidgetOpen() {
        return isWidgetVisible(6, 0);
    }

    // check if smithing widget is open
    public static boolean isSmithingWidgetOpen() {
        return isWidgetVisible(InterfaceID.SMITHING, 0);
    }

    // check if deposit box widget is open
    public static boolean isDepositBoxWidgetOpen() {
        return isWidgetVisible(192, 0);
    }

    public static boolean isWildernessInterfaceOpen() {
        return isWidgetVisible(475, 11);
    }

    public static boolean enterWilderness() {
        if (!isWildernessInterfaceOpen()) return false;

        Microbot.log("Detected Wilderness warning, interacting...");
        Rs2Widget.clickWidget(475, 11);

        return true;
    }

}
