package net.runelite.client.plugins.microbot.flipper;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import java.awt.Rectangle;
import net.runelite.api.widgets.Widget;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.awt.event.KeyEvent;

enum State {
    GOING_TO_GE,
    GETTING_COINS,
    MONITORING_COPILOT
}

public class FlipperScript extends Script {
    private double version = 1.0;
    
    private static final WorldPoint GE_AREA = new WorldPoint(3165, 3485, 0);
    State state = State.GOING_TO_GE;
    
    private Plugin flippingCopilot = null;
    private Object highlightController = null;
    private long lastActionTime = 0;
    private static final long ACTION_COOLDOWN = 1500; // 1.5 second cooldown between actions
    
    public boolean run() {
        Rs2AntibanSettings.naturalMouse = true;
        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
            mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                
                // Find FlippingCopilot plugin
                if(flippingCopilot == null){
                    flippingCopilot = Microbot.getPluginManager()
                            .getPlugins()
                            .stream()
                            .filter(x -> x.getClass()
                                        .getSimpleName()
                                        .equalsIgnoreCase("FlippingCopilotPlugin"))
                                        .findFirst()
                                        .orElse(null);
                    
                    // Also get the HighlightController
                    if (flippingCopilot != null) {
                        highlightController = getHighlightController(flippingCopilot);
                    }
                }
                
                if (flippingCopilot == null) {
                    Microbot.log("FlippingCopilot plugin not found. Please install it from the plugin hub.");
                    return;
                }

                switch (state) {
                    case GOING_TO_GE:
                        if(Rs2GrandExchange.isOpen()){
                             state = State.MONITORING_COPILOT;
                             return;
                        }
                        if (Microbot.getClient()
                                    .getLocalPlayer()
                                    .getWorldLocation()
                                    .distanceTo(GE_AREA) > 15) {
                            Rs2Walker.walkTo(GE_AREA);
                        }
                        state = State.GETTING_COINS;
                        break;

                    case GETTING_COINS:
                        if(Rs2Inventory.onlyContains(995)){
                            state = State.MONITORING_COPILOT;
                            return;
                        }
                        if (Rs2Bank.openBank()) {
                            Rs2Bank.depositAll();
                            sleepUntil(() -> Rs2Inventory.isEmpty());
                            Rs2Bank.withdrawAll(995);
                            sleepUntil(() -> Rs2Inventory.onlyContains(995));
                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen());
                            state = State.MONITORING_COPILOT;
                        }
                        break;

                    case MONITORING_COPILOT:
                        if (!Rs2GrandExchange.isOpen()) {
                            Rs2GrandExchange.openExchange();
                            return;
                        }
                        
                        // First check if we need to abort using reflection
                        if (checkAndAbortIfNeeded()) return;
                        
                        // Check for Copilot price/quantity messages in chat
                        if (checkAndPressCopilotKeybind()) return;

                        // Check for highlighted widgets
                        checkAndClickHighlightedWidgets();
                        break;
                }
            } catch (Exception ex) {
                Microbot.log("FlipperScript error: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        
        return true;
    }

    private Object getHighlightController(Plugin flippingCopilot) {
        try {
            Field highlightControllerField = flippingCopilot.getClass().getDeclaredField("highlightController");
            highlightControllerField.setAccessible(true);
            return highlightControllerField.get(flippingCopilot);
        } catch (Exception e) {
            Microbot.log("Could not access HighlightController: " + e.getMessage());
            return null;
        }
    }
    
private boolean checkAndAbortIfNeeded() {
    if (flippingCopilot == null) return false;
    
    try {
        Field suggestionManagerField = flippingCopilot.getClass().getDeclaredField("suggestionManager");
        suggestionManagerField.setAccessible(true);
        Object suggestionManagerObj = suggestionManagerField.get(flippingCopilot);
        
        if (suggestionManagerObj == null) return false;
        
        Field suggestionField = suggestionManagerObj.getClass().getDeclaredField("suggestion");
        suggestionField.setAccessible(true);
        Object currentSuggestion = suggestionField.get(suggestionManagerObj);
        
        if (currentSuggestion == null) return false;
        
        Field typeField = currentSuggestion.getClass().getDeclaredField("type");
        typeField.setAccessible(true);
        String suggestionType = (String) typeField.get(currentSuggestion);
        
        if (!"abort".equals(suggestionType)) {
            return false;
        }
        
        Microbot.log("Abort suggestion detected");
        
        if (highlightController != null) {
            try {
                // Get the list of highlighted overlays from FlippingCopilot
                Field highlightOverlaysField = highlightController.getClass().getDeclaredField("highlightOverlays");
                highlightOverlaysField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Object> highlightOverlays = (List<Object>) highlightOverlaysField.get(highlightController);
                
                if (highlightOverlays != null && !highlightOverlays.isEmpty()) {
                    // Look for highlighted GE slot widgets
                    for (Object overlay : highlightOverlays) {
                        Widget highlightedWidget = getWidgetFromOverlay(overlay);
                        if (highlightedWidget != null && isGrandExchangeSlotWidget(highlightedWidget)) {
                            Microbot.log("Found highlighted GE slot for abort, performing abort action");
                            
                            // Perform abort using the highlighted widget directly
                            NewMenuEntry menuEntry = new NewMenuEntry("Abort offer", "", 2, MenuAction.CC_OP, 2, highlightedWidget.getId(), false);
                            Rectangle bounds = highlightedWidget.getBounds() != null && Rs2UiHelper.isRectangleWithinCanvas(highlightedWidget.getBounds())
                                ? highlightedWidget.getBounds()
                                : Rs2UiHelper.getDefaultRectangle();
                            Microbot.doInvoke(menuEntry, bounds);
                            
                            lastActionTime = System.currentTimeMillis();
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                Microbot.log("Error using highlight-based abort: " + e.getMessage());
            }
        }
        
        // Fallback to existing name-matching approach
        // Get the item name to abort
        Field nameField = currentSuggestion.getClass().getDeclaredField("name");
        nameField.setAccessible(true);
        String itemName = (String) nameField.get(currentSuggestion);
        
        Microbot.log("Using fallback abort method for item: " + itemName);
        
        // Abort only the specific item, not all offers
        boolean success = Rs2GrandExchange.abortOffer(itemName, false);
        if (success) {
            Microbot.log("Successfully aborted offer for: " + itemName);
        } else {
            Microbot.log("Failed to abort offer for: " + itemName);
        }
        return true;
        
    } catch (Exception e) {
        Microbot.log("Error checking FlippingCopilot suggestion: " + e.getMessage());
    }
    
    return false;
}

// Add this small helper method
private boolean isGrandExchangeSlotWidget(Widget widget) {
    // GE slot widgets are in interface 465, children 7-14 (representing slots 0-7)
    return widget.getId() >= 30474247 && widget.getId() <= 30474254; // 465 << 16 | (7-14)
}
    
    private boolean checkAndPressCopilotKeybind() {
        // Execute on client thread to access widgets safely
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            try {
                // Check if the price/quantity input widget is open
                Widget inputWidget = Rs2Widget.getWidget(10616870);
                if (inputWidget != null && !inputWidget.isHidden()) {
                    Microbot.log("Found price/quantity input widget, pressing 'e' then Enter");
                    
                    // Press 'e' to trigger FlippingCopilot's keybind
                    Rs2Keyboard.keyPress(KeyEvent.VK_E);
                    sleep(250); // Small delay
                    
                    // Press Enter to confirm
                    Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
                    
                    lastActionTime = System.currentTimeMillis();
                    return true;
                }
                
                return false;
            } catch (Exception e) {
                Microbot.log("Error checking for input widget: " + e.getMessage());
                return false;
            }
        }).orElse(false);
    }

    private void checkAndClickHighlightedWidgets() {
        // Prevent spam clicking with cooldown
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActionTime < ACTION_COOLDOWN) {
            return;
        }

        if (highlightController == null) {
            return;
        }

        // Execute widget checks on client thread
        Microbot.getClientThread().invokeLater(() -> {
            try {
                // Get the list of highlighted overlays from FlippingCopilot
                Field highlightOverlaysField = highlightController.getClass().getDeclaredField("highlightOverlays");
                highlightOverlaysField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Object> highlightOverlays = (List<Object>) highlightOverlaysField.get(highlightController);
                
                if (highlightOverlays == null || highlightOverlays.isEmpty()) {
                    // No highlights active
                    return;
                }

                // Click the first highlighted widget
                Object firstOverlay = highlightOverlays.get(0);
                Widget highlightedWidget = getWidgetFromOverlay(firstOverlay);
                
                if (highlightedWidget != null && !highlightedWidget.isHidden()) {
                    Microbot.log("Clicking highlighted widget: " + highlightedWidget.getId());
                    Rs2Widget.clickWidget(highlightedWidget);
                    lastActionTime = System.currentTimeMillis();
                }

            } catch (Exception e) {
                Microbot.log("Error checking highlighted widgets: " + e.getMessage());
            }
        });
    }
    

    private Widget getWidgetFromOverlay(Object overlay) {
        try {
            Field widgetField = overlay.getClass().getDeclaredField("widget");
            widgetField.setAccessible(true);
            return (Widget) widgetField.get(overlay);
        } catch (Exception e) {
            Microbot.log("Could not get widget from overlay: " + e.getMessage());
            return null;
        }
    }

}