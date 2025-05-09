package net.runelite.client.plugins.microbot.util.keyboard;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;

import java.awt.*;
import java.awt.event.KeyEvent;

import static java.awt.event.KeyEvent.CHAR_UNDEFINED;
import static net.runelite.client.plugins.microbot.util.math.Random.random;

public class Rs2Keyboard {

    public static Canvas getCanvas() {
        return Microbot.getClient().getCanvas();
    }

    public static void typeString(final String word) {
        boolean originalFocusValue = Microbot.getClient().getCanvas().isFocusable();
        if (!originalFocusValue) {
            Microbot.getClient().getCanvas().setFocusable(!originalFocusValue);
        }

        for (int i = 0; i < word.length(); i++) {
            final int randomizer = random(20, 200);

            KeyEvent keyEvent = new KeyEvent(getCanvas(), KeyEvent.KEY_TYPED, System.currentTimeMillis() + randomizer, 0, KeyEvent.VK_UNDEFINED, word.charAt(i));

            Microbot.getClientThread().runOnClientThreadOptional(() -> {
                getCanvas().dispatchEvent(keyEvent);
                return true;
            });

            Global.sleep(100, 200);
        }

        if (!originalFocusValue) {
            Microbot.getClient().getCanvas().setFocusable(originalFocusValue);
        }
    }


    public static void keyPress(final char key) {
        boolean originalFocusValue = Microbot.getClient().getCanvas().isFocusable();
        if (!originalFocusValue) {
            Microbot.getClient().getCanvas().setFocusable(!originalFocusValue);
        }

        final int randomizer = random(20, 200);

        KeyEvent keyEvent = new KeyEvent(getCanvas(), KeyEvent.KEY_TYPED, System.currentTimeMillis() + randomizer, 0, KeyEvent.VK_UNDEFINED, key);

        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            getCanvas().dispatchEvent(keyEvent);
            return true;
        });

        if (!originalFocusValue) {
            Microbot.getClient().getCanvas().setFocusable(originalFocusValue);
        }

    }

    public static void holdShift() {
        boolean originalFocusValue = Microbot.getClient().getCanvas().isFocusable();
        if (!originalFocusValue) {
            Microbot.getClient().getCanvas().setFocusable(!originalFocusValue);
        }

        final int randomizer = random(20, 200);

        KeyEvent keyEvent = new KeyEvent(getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis() + randomizer, 0, KeyEvent.VK_SHIFT, CHAR_UNDEFINED);

        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            getCanvas().dispatchEvent(keyEvent);
            return true;
        });

        if (!originalFocusValue) {
            Microbot.getClient().getCanvas().setFocusable(originalFocusValue);
        }

    }

    public static void releaseShift() {
        boolean originalFocusValue = Microbot.getClient().getCanvas().isFocusable();
        if (!originalFocusValue) {
            Microbot.getClient().getCanvas().setFocusable(!originalFocusValue);
        }

        final int randomizer = random(20, 200);

        KeyEvent keyEvent = new KeyEvent(getCanvas(), KeyEvent.KEY_RELEASED, System.currentTimeMillis() + randomizer, 0, KeyEvent.VK_SHIFT);

        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            getCanvas().dispatchEvent(keyEvent);
            return true;
        });

        if (!originalFocusValue) {
            Microbot.getClient().getCanvas().setFocusable(originalFocusValue);
        }

    }

    public static void keyHold(int key) {
        boolean originalFocusValue = Microbot.getClient().getCanvas().isFocusable();
        if (!originalFocusValue) {
            Microbot.getClient().getCanvas().setFocusable(!originalFocusValue);
        }

        KeyEvent keyEvent = new KeyEvent(getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, key);

        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            getCanvas().dispatchEvent(keyEvent);
            return true;
        });

        if (!originalFocusValue) {
            Microbot.getClient().getCanvas().setFocusable(originalFocusValue);
        }

    }

    public static void keyRelease(int key) {
        boolean originalFocusValue = Microbot.getClient().getCanvas().isFocusable();
        if (!originalFocusValue) {
            Microbot.getClient().getCanvas().setFocusable(!originalFocusValue);
        }

        final int randomizer = random(20, 200);

        KeyEvent keyEvent = new KeyEvent(getCanvas(), KeyEvent.KEY_RELEASED, System.currentTimeMillis() + randomizer, 0, key);

        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            getCanvas().dispatchEvent(keyEvent);
            return true;
        });

        if (!originalFocusValue) {
            Microbot.getClient().getCanvas().setFocusable(originalFocusValue);
        }

    }

    public static void keyPress(int key) {
        keyHold(key);
        keyRelease(key);
    }

    public static void enter() {
        keyPress(KeyEvent.VK_ENTER);

        //FIX: this is to avoid automatically login with jagex account when you are on the login screen
        KeyEvent keyEvent = new KeyEvent(getCanvas(), KeyEvent.KEY_TYPED, System.currentTimeMillis() , 0, KeyEvent.VK_UNDEFINED, '\n');

        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            getCanvas().dispatchEvent(keyEvent);
            return true;
        });
    }
}
