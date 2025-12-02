package net.runelite.client.plugins.microbot.util.widget;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.annotations.Component;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.util.Global.*;

@Slf4j
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
			return foundWidget != null && !foundWidget.isHidden();
		}).orElse(false);
	}

    public static boolean hasWidgetText(String text, int widgetId, int childId, boolean exact) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget rootWidget = getWidget(widgetId, childId);
            if (rootWidget == null) return false;

            // Use findWidget to perform the search on all child types
            Widget foundWidget = findWidget(text, List.of(rootWidget), exact);
            return foundWidget != null && !foundWidget.isHidden();
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
        Microbot.doInvoke(new NewMenuEntry()
                .param0(param0 != -1 ? param0 : widget.getType())
                .param1(param1)
                .opcode(menuAction.getId())
                .identifier(identifier)
                .itemId(widget.getItemId())
                .target(target)
                ,
                widget.getBounds());
    }

    public static void clickWidgetFast(Widget widget, int param0) {
        clickWidgetFast(widget, param0, 1);
    }

    public static void clickWidgetFast(Widget widget) {
        clickWidgetFast(widget, -1, 1);
    }

    // check if production widget is open
    public static boolean isProductionWidgetOpen() {
        return isWidgetVisible(InterfaceID.SKILLMULTI, 0);
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

    // === WIDGET KEY MAPPING AND PROCESSING INTERFACE METHODS ===
    
    /**
     * gets keyboard shortcut keys for widgets in processing interfaces
     * @param widgetGroupId the widget group id (usually 270)
     * @param widgetSubGroupId the widget sub group id
     * @return map of widget index to keyevent code
     */
    public static Map<Integer,Integer> getWidgetsKeyMap(int widgetGroupId, int widgetSubGroupId) {
        Widget widgetWithKeyInfo = getWidget(widgetGroupId, widgetSubGroupId);
        if (widgetWithKeyInfo == null) return new HashMap<>();
        
        Widget[] dynamicChildren = widgetWithKeyInfo.getDynamicChildren();
        if (dynamicChildren == null) return new HashMap<>();
        
        Map<Integer, Integer> keyMap = new HashMap<>();
        for (int i = 0; i < dynamicChildren.length; i++) {
            Widget child = dynamicChildren[i];
            if (child == null) continue;
            
            String keyText = Rs2UiHelper.stripColTags(child.getText());
            switch (keyText) {
                case "1":
                    keyMap.put(i, KeyEvent.VK_1);
                    break;
                case "2":
                    keyMap.put(i, KeyEvent.VK_2);
                    break;
                case "3":
                    keyMap.put(i, KeyEvent.VK_3);
                    break;
                case "4":
                    keyMap.put(i, KeyEvent.VK_4);
                    break;
                case "5":
                    keyMap.put(i, KeyEvent.VK_5);
                    break;
                case "6":
                    keyMap.put(i, KeyEvent.VK_6);
                    break;
                case "7":
                    keyMap.put(i, KeyEvent.VK_7);
                    break;
                case "Space":
                    
                    keyMap.put(i, KeyEvent.VK_SPACE);
                    break;
                default:
                    // handle additional keys as needed
                    break;
            }
        }
        return keyMap;
    }

    /**
     * gets keyboard shortcut key for a widget in processing interfaces
     * @param widget the widget to get the key for
     * @param keyParentGroupId parent widget group id containing keys
     * @param keyParentChildId parent widget child id containing keys
     * @return keyevent code for the shortcut, or null if not found
     */

     private static Integer getProcessingWidgetKeyCode(String actionText) {
        log.debug("Searching for processing widget with action text: {}", actionText);
        Widget optionWidget = findWidget(actionText, List.of(getWidget(InterfaceID.SKILLMULTI, 0)), false);    
        if (optionWidget == null) return null;
        return getProcessingWidgetKeyCode(optionWidget);
     }
    private static Integer getProcessingWidgetKeyCode(Widget optionWidget) {
        if (optionWidget == null) return null;
        
        Widget keyParent = getWidget(InterfaceID.SKILLMULTI, 13);
        if (keyParent == null) return null;

        Widget[] staticChildren = keyParent.getStaticChildren();
        if (staticChildren == null) return null;
            
        Map<Integer, Integer> keyMap = getWidgetsKeyMap(InterfaceID.SKILLMULTI, 13);
        if (keyMap.isEmpty()) return null;
        // find index of widget in static children
        for (int i = 0; i < staticChildren.length; i++) {
            Widget child = staticChildren[i];            
            if (child == optionWidget || (child != null && child.getName() != null &&
                child.getName().equals(optionWidget.getName()))) {

                int widgetId = child.getId();
                int groupId = widgetId >>> 16;
                String actions = child.getActions() != null ? Arrays.toString(child.getActions()) : "null";
                log.debug("Found processing widget at index {}, key: {}, child text: {}, id: {}, groupId: {}, actions: {}",
                    i, keyMap.get(i), child.getName(), widgetId, groupId, actions);
                return keyMap.get(i);
            }
        }
        
        return null;
    }

    /**
     * finds all widgets with specific action text
     * @param actionText the action text to search for
     * @param widgetGroupId the widget group id to search in
     * @param widgetSubGroupId the widget sub group id to search in
     * @param clickWidget whether to click the widget if found
     * @return map of widgets to action text
     */
    public static Map<Widget, String> findWidgetsWithAction(String actionText, int widgetGroupId, int widgetSubGroupId, boolean clickWidget) {
        Map<Widget, String> widgetActions = new HashMap<>();
        Widget child = getWidget(widgetGroupId, widgetSubGroupId);
        if (child == null) return widgetActions;
        
        List<Widget[]> childGroups = Stream.of(child.getChildren(), child.getNestedChildren(), 
                                             child.getDynamicChildren(), child.getStaticChildren())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        for (Widget[] childGroup : childGroups) {
            if (childGroup != null) {
                for (Widget nestedChild : Arrays.stream(childGroup)
                        .filter(w -> w != null && !w.isHidden())
                        .collect(Collectors.toList())) {
                    if (matchesWildCardText(nestedChild, actionText, false, false)) {
                        if (clickWidget) {
                            clickWidget(nestedChild);
                        }
                        widgetActions.put(nestedChild, actionText.toLowerCase());
                    }
                }
            }
        }
        return widgetActions;
    }

    /**
     * finds widgets with action text in a group
     * @param actionText the action text to search for
     * @param widgetGroupId the widget group id
     * @param clickWidget whether to click the widget
     * @return map of widgets to action text
     */
    public static Map<Widget, String> findWidgetsWithAction(String actionText, int widgetGroupId, boolean clickWidget) {
        return findWidgetsWithAction(actionText, widgetGroupId, 0, clickWidget);
    }

    /**
     * handles generic processing interface interactions with quantity selection
     * @param widgetGroupId main widget group id (usually 270)
     * @param actionText text/action to search for
     * @param validateInterface supplier to validate correct interface is open
     * @return true if interaction was successful
     */
    public static boolean handleProcessingInterface( String actionText) {
        if (!isWidgetVisible(InterfaceID.SKILLMULTI, 0)) {
            log.error("Processing interface not open");
            return false;
        }
        Widget mainWidget = getWidget(InterfaceID.SKILLMULTI, 0);
     

        // enable quantity option if available
        //enableQuantityOption(widgetGroupId);
        log.debug("Searching for processing widget with action text: {}", actionText);
        Widget optionWidget = findWidget(actionText, new ArrayList<Widget>(List.of(mainWidget)), false);        
        
        
        int widgetId = optionWidget != null ? optionWidget.getId() : -1;
        int groupId = widgetId >>> 16; // upper 16 bits
        int childId = widgetId & 0xFFFF; // lower 16 bits
        log.debug("Widget details: \n\tid={}, groupId={}, childId={}, actions={}, name ={}, text={}",
            widgetId,
            groupId,
            childId,
            optionWidget != null && optionWidget.getActions() != null ? Arrays.toString(optionWidget.getActions()) : "null"
            , optionWidget != null ? optionWidget.getName() : "null"
            , optionWidget != null ? optionWidget.getText() : "null"
        );
        if (optionWidget == null) {
            return false;
        }
        
        // try keyboard shortcut first for faster interaction

        Integer shortcutKey = getProcessingWidgetKeyCode(optionWidget);
        log.debug("Found processing widget shortcut key: {}", shortcutKey);
        if (shortcutKey != null) {
            sleep(600);
            Rs2Keyboard.keyPress(shortcutKey);
            sleep(600); // Short delay to ensure prompt processing
            log.debug("Pressed shortcut key: {}", shortcutKey);
            sleepUntil(() -> isProductionWidgetOpen() == false, Rs2Random.between(1200, 1600));
            if(Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                sleepUntil(() -> !Rs2Dialogue.hasContinue(), 2000);
                return false; // we should not see it, only when we have not the req. for porcessing
            }
            if(isProductionWidgetOpen() == false){                
                return true;
            }
        }
        log.debug("No shortcut key found, clicking widget instead");
        // fall back to clicking widget
        return clickWidget(optionWidget);
    }

    /**
     * enables quantity option in processing interfaces ("all" option)
     * @param widgetGroupId the widget group id
     * @return true if enabled successfully
     */
    public static boolean enableQuantityOption(String quantity) {
        return Microbot.getClientThread().runOnClientThreadOptional(()->{
                Widget mainWidget = getWidget(InterfaceID.SKILLMULTI, 0);
                if (mainWidget == null || mainWidget.isHidden()) {
                    return false;
                }
                Widget child = searchChildren(quantity, mainWidget,false );
              
                if (child != null && !child.isHidden() && child.getText() != null){
                    String[] actions = child.getActions();
                    if (actions != null && Arrays.asList(actions).contains(quantity)) {
                        log.info("Enabling quantity option: {}", quantity);
                        clickWidget(child);
                        return true;
                    }                                    
                }
                log.info("Could not find quantity option: {}", quantity);
                return false;
        }).orElse(false);
    }

    /**
     * handles chat/trade dialogue confirmations
     * @param widgetGroupId widget group id
     * @return true if confirmed successfully
     */
    public static boolean handleProcessConfirmation(int widgetGroupId) {
        if (!isWidgetVisible(widgetGroupId, 0)) {
            return false;
        }
        
        if (Rs2Dialogue.hasContinue()) {
            Rs2Dialogue.clickContinue();
            sleep(400, 600);
        }
        
        return sleepUntil(() -> !Rs2Dialogue.hasContinue(), 2000);
    }

    /**
     * waits for widget to appear with specified text
     * @param text text to wait for
     * @param timeout timeout in milliseconds
     * @return true if widget appeared within timeout
     */
    public static boolean waitForWidget(String text, int timeout) {
        return sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> hasVisibleWidgetText(text)).orElse(false), timeout);
    }

    /**
     * checks if specific widget text exists and is visible
     * @param text the text to search for
     * @return true if widget exists and is visible
     */
    public static boolean hasVisibleWidgetText(String text) {
        Widget widget = findWidget(text, null, true);
        return widget != null && !widget.isHidden();
    }

    /**
     * finds best matching widget based on exact match, contains match, or word similarity
     * @param widgetId parent widget id
     * @param targetText text to match
     * @return best matching widget or null if none found
     */
    public static Widget findBestMatchingWidget(int widgetId, String targetText) {
        Widget parent = getWidget(widgetId);
        if (parent == null) return null;
        
        Widget[] dynamicChildren = parent.getDynamicChildren();
        if (dynamicChildren == null) return null;
        
        List<Widget> children = Arrays.stream(dynamicChildren)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        // try exact match first
        Widget exactMatch = findExactMatch(children, targetText);
        if (exactMatch != null) return exactMatch;
        
        // try contains match second
        Widget containsMatch = findContainsMatch(children, targetText);
        if (containsMatch != null) return containsMatch;
        
        // finally try word similarity matching
        return findBestWordSimilarityMatch(children, targetText);
    }

    /**
     * finds widget with exact text match
     */
    private static Widget findExactMatch(List<Widget> widgets, String targetText) {
        return widgets.stream()
            .filter(w -> w.getText() != null && w.getText().toLowerCase().equals(targetText.toLowerCase()))
            .findFirst()
            .orElse(null);
    }

    /**
     * finds widget containing the target text
     */
    private static Widget findContainsMatch(List<Widget> widgets, String targetText) {
        return widgets.stream()
            .filter(w -> w.getText() != null && w.getText().toLowerCase().contains(targetText.toLowerCase()))
            .findFirst()
            .orElse(null);
    }

    /**
     * finds the widget with the highest number of matching words
     */
    private static Widget findBestWordSimilarityMatch(List<Widget> widgets, String targetText) {
        String[] targetWords = targetText.toLowerCase().split("\\s+");
        
        Map<Widget, Integer> matchScores = new HashMap<>();
        
        for (Widget widget : widgets) {
            if (widget.getText() == null) continue;
            
            String widgetText = widget.getText().toLowerCase();
            String[] widgetWords = widgetText.split("\\s+");
            
            int matchCount = countMatchingWords(targetWords, widgetWords);
            if (matchCount > 0) {
                matchScores.put(widget, matchCount);
            }
        }
        
        return matchScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    /**
     * counts how many words from source array exist in target array
     */
    private static int countMatchingWords(String[] sourceWords, String[] targetWords) {
        return (int) Arrays.stream(sourceWords)
            .filter(sourceWord -> 
                Arrays.stream(targetWords)
                    .anyMatch(targetWord -> 
                        targetWord.contains(sourceWord) || sourceWord.contains(targetWord)
                    )
            )
            .count();
    }

    /**
     * calculates text similarity score between two strings
     * @param source source text
     * @param target target text 
     * @return similarity score 0-1
     */
    public static double calculateTextSimilarity(String source, String target) {
        if (source == null || target == null) {
            return 0.0;
        }
        
        String[] sourceWords = source.toLowerCase().split("\\s+");
        String[] targetWords = target.toLowerCase().split("\\s+");
        
        int matchingWords = countMatchingWords(sourceWords, targetWords);
        int totalWords = Math.max(sourceWords.length, targetWords.length);
        
        return totalWords > 0 ? (double) matchingWords / totalWords : 0.0;
    }

    /**
     * finds all widgets that match above a similarity threshold
     * @param widgets list of widgets to search
     * @param targetText text to match against
     * @param threshold minimum similarity score (0-1)
     * @return list of matching widgets sorted by similarity
     */
    public static List<Widget> findSimilarWidgets(List<Widget> widgets, String targetText, double threshold) {
        return widgets.stream()
            .filter(w -> w.getText() != null)
            .map(w -> new AbstractMap.SimpleEntry<>(w, calculateTextSimilarity(w.getText(), targetText)))
            .filter(entry -> entry.getValue() >= threshold)
            .sorted(Map.Entry.<Widget, Double>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * checks if text or action matches with wildcard support
     */
    private static boolean matchesWildCardText(Widget widget, String text, boolean exact, boolean onlyAction) {
        if (widget == null) return false;
        
        String cleanText = Rs2UiHelper.stripColTags(widget.getText());
        String cleanName = Rs2UiHelper.stripColTags(widget.getName());
        
        if (!onlyAction) {
            if (exact) {
                if ((cleanText != null && cleanText.equalsIgnoreCase(text)) || 
                    (cleanName != null && cleanName.equalsIgnoreCase(text))) return true;
            } else {
                if ((cleanText != null && cleanText.toLowerCase().contains(text.toLowerCase())) || 
                    (cleanName != null && cleanName.toLowerCase().contains(text.toLowerCase()))) return true;
            }
        }
        
        if (widget.getActions() != null) {
            String[] actions = widget.getActions();

            for (String action : widget.getActions()) {
                if (action != null) {
                    String cleanAction = Rs2UiHelper.stripColTags(action);
                    if (exact ? cleanAction.equalsIgnoreCase(text) : 
                        cleanAction.toLowerCase().contains(text.toLowerCase())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean checkBoundsOverlapWidgetInMainModal( Rectangle overlayBoundsCanvas, int viewportXOffset, int viewportYOffset) {
        final int MAIN_MODAL_TOPLEVEL_CHILD_ID = 40; // Main modal child ID
        final int MAIN_MODAL_STRECH_CHILD_ID = 16; // Main modal child ID
        Widget mainModalWidget = getWidget(net.runelite.api.gameval.InterfaceID.TOPLEVEL, MAIN_MODAL_TOPLEVEL_CHILD_ID);
        if (mainModalWidget == null || mainModalWidget.isHidden()) {
            mainModalWidget  = getWidget(net.runelite.api.gameval.InterfaceID.TOPLEVEL_OSRS_STRETCH, MAIN_MODAL_STRECH_CHILD_ID);
            
        }
        if (mainModalWidget == null ) {            
            mainModalWidget  = getWidget(net.runelite.api.gameval.InterfaceID.TOPLEVEL_PRE_EOC, MAIN_MODAL_STRECH_CHILD_ID);
        }
        return checkWidgetAndDescendantsForOverlapCanvas(mainModalWidget, overlayBoundsCanvas, viewportXOffset, viewportYOffset);
    }
    /**
	* Recursively iterates all descendants, but only checks bounds for nested containers 
	* This matches the requirement: only nested containers within the static container are checked for overlap.
	*/    
    private static boolean checkWidgetAndDescendantsForOverlapCanvas(Widget widget, Rectangle overlayBoundsCanvas, int viewportXOffset, int viewportYOffset) {
	    if (widget == null || widget.isHidden()) {
		   return false;
	    }       	   
	    List<Widget[]> nestedAndDynamicWidgets = new java.util.ArrayList<>();
	    if (widget.getDynamicChildren() != null) nestedAndDynamicWidgets.add(widget.getDynamicChildren());
		if (widget.getNestedChildren() != null) nestedAndDynamicWidgets.add(widget.getNestedChildren());
	    for (Widget[] widgetArray : nestedAndDynamicWidgets) {
		   for (Widget nestedOrDynamic : widgetArray) {
			   if (nestedOrDynamic == null || nestedOrDynamic.isHidden()) {
				   continue;
			   }
               int groupId = nestedOrDynamic.getId() >>> 16; // upper 16 bits
			   if(  nestedOrDynamic.getCanvasLocation() == null) {				   
				   continue;
			   }
			   Rectangle widgetBounds = nestedOrDynamic.getBounds();
			   if (widgetBounds != null) {
				   Rectangle widgetCanvasBounds = new Rectangle(
					   widgetBounds.x + viewportXOffset,
					   widgetBounds.y + viewportYOffset,
					   widgetBounds.width,
					   widgetBounds.height
				   );
				   if (widgetCanvasBounds.intersects(overlayBoundsCanvas)) {
					   Rectangle intersection = widgetCanvasBounds.intersection(overlayBoundsCanvas);
					   if (intersection.width > 8 && intersection.height > 8) {
                            log.debug("Widget with group ID {} and child ID {} overlaps with the overlay bounds.\n" +
                                 "Widget ID: {}, Title: {}, Canvas Location: {}, Bounds: {}, Intersection: {}",
                                 groupId, nestedOrDynamic.getId() & 0xFFFF, nestedOrDynamic.getId(),
                                 nestedOrDynamic.getName(), nestedOrDynamic.getCanvasLocation(),
                                 widgetCanvasBounds, intersection);
						   return true;
					   }
				   }
			   }
		   }
	   }
	   

	   // Recursively check all children for nested containers
	   List<Widget[]> childGroups = new java.util.ArrayList<>();
	   
	   if (widget.getStaticChildren() != null) childGroups.add(widget.getStaticChildren());
	   

	   for (Widget[] childGroup : childGroups) {
		   for (Widget child : childGroup) {
			   if (child != null && !child.isHidden()) {					
					int widgetId = child.getId();
					int groupId = widgetId >>> 16; // upper 16 bits
					int childId = widgetId & 0xFFFF; // lower 16 bits	
                    if (child.getCanvasLocation() == null || (child.getCanvasLocation().getX() == 0 && child.getCanvasLocation().getY() == 0)) {
                        continue;
                    }				
				   if (checkWidgetAndDescendantsForOverlapCanvas(child, overlayBoundsCanvas, viewportXOffset, viewportYOffset)) {
                        Widget parentWidget = child.getParent();
                        String title = parentWidget != null ? parentWidget.getName() : "Unknown";
                        int parentId = parentWidget != null ? parentWidget.getId() : -1;
                        int parentGoupID = parentId >>> 16; // upper 16 bits
                        int parentChildID = parentId & 0xFFFF; // lower 16 bits

                        log.debug("Widget with group ID {} and child ID {} overlaps with the overlay bounds.\n" +
                                 "Parent Widget ID: {}, Group ID: {}, Child ID: {}, Title: {}",
                                 groupId, childId, parentId, parentGoupID, parentChildID, title);
					   return true;
				   }
			   }
		   }
	   }
	   return false;
   }

}
