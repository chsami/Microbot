package net.runelite.client.plugins.microbot.util.reflection;

import lombok.SneakyThrows;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import lombok.extern.slf4j.Slf4j;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class Rs2Reflection {
    @SneakyThrows
    public static void invokeMenu(int param0, int param1, int opcode, int identifier, int itemId, String option, String target, int canvasX, int canvasY)
    {
        invokeMenu(param0, param1, opcode, identifier, itemId, -1, option, target, canvasX, canvasY);
    }
    private static Method menuAction;
    private static Object menuActionGarbageValue;
    @SneakyThrows
    public static void invokeMenu(int param0, int param1, int opcode, int identifier, int itemId, int worldViewId, String option, String target, int canvasX, int canvasY)
    {
        // Cache-first path: when the on-disk cache resolves, MenuActionAsmResolver is never
        // referenced, so the JVM has no cause to class-load it — and therefore no cause to
        // link the ASM library. That's the point of P7-b: ASM becomes a first-launch-only
        // runtime signature instead of a steady-state one.
        if (menuAction == null)
        {
            MenuActionAsmResolver.Resolution cached = MenuActionInfoCache.load();
            if (cached != null)
            {
                menuAction = cached.method;
                menuActionGarbageValue = cached.garbageValue;
            }
        }
        if (menuAction == null)
        {
            MenuActionAsmResolver.Resolution resolution = MenuActionAsmResolver.resolve(Microbot.getClient().getClass());
            if (resolution != null)
            {
                menuAction = resolution.method;
                menuActionGarbageValue = resolution.garbageValue;
                MenuActionInfoCache.store(resolution);
            }
        }

        if (menuAction == null || menuActionGarbageValue == null)
        {
            Microbot.showMessage("invokeMenu method is broken! Falling back to runelite menu action");
            Microbot.getClientThread().invoke(() -> Microbot.getClient().menuAction(param0, param1, MenuAction.of(opcode), identifier, itemId, option, target));
            return;
        }

        menuAction.setAccessible(true);
        Microbot.getClientThread().runOnClientThreadOptional(() -> menuAction.invoke(null, param0, param1, opcode, identifier, itemId, worldViewId, option, target, canvasX, canvasY, menuActionGarbageValue));
        menuAction.setAccessible(false);

        if (Microbot.getClient().getKeyboardIdleTicks() > Rs2Random.between(5000, 10000))
        {
            Rs2Keyboard.keyPress(KeyEvent.VK_BACK_SPACE);
        }
        System.out.println("[INVOKE] => param0: " + param0 + " param1: " + param1 + " opcode: " + opcode + " id: " + identifier + " itemid: " + itemId);
    }

    private static volatile Field cachedOuterField;
    private static volatile Field cachedListField;
    private static volatile Field cachedStringField;

    public static String[] getGroundItemActions(ItemComposition item) {
        return getGroundItemActionsFromObject(item);
    }

    @SneakyThrows
    static String[] getGroundItemActionsFromObject(Object item) {
        if (item == null) return new String[]{};

        if (cachedOuterField != null && cachedListField != null) {
            try {
                return extractWithCache(item);
            } catch (Exception e) {
                log.warn("Ground item action cache invalidated, re-discovering");
                resetGroundItemActionCache();
            }
        }

        for (Class<?> clazz = item.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field outerField : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(outerField.getModifiers()) || outerField.isSynthetic()) continue;

                Class<?> type = outerField.getType();
                if (type.isPrimitive() || type == String.class || type.isArray()
                        || type.getName().startsWith("java.") || type.getName().startsWith("net.runelite.")) continue;

                outerField.setAccessible(true);
                Object outerValue = outerField.get(item);
                outerField.setAccessible(false);
                if (outerValue == null) continue;

                for (Field listField : outerValue.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(listField.getModifiers()) || listField.isSynthetic()) continue;
                    if (!List.class.isAssignableFrom(listField.getType())) continue;

                    listField.setAccessible(true);
                    Object listObj = listField.get(outerValue);
                    listField.setAccessible(false);
                    if (!(listObj instanceof List)) continue;

                    List<?> list = (List<?>) listObj;
                    if (list.isEmpty()) continue;

                    Object first = null;
                    for (Object el : list) {
                        if (el != null) { first = el; break; }
                    }
                    if (first == null) continue;

                    if (first instanceof String) {
                        cachedOuterField = outerField;
                        cachedListField = listField;
                        cachedStringField = null;
                        return toStringArray(list);
                    }

                    Field stringField = null;
                    for (Field f : first.getClass().getDeclaredFields()) {
                        if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic() && f.getType() == String.class) {
                            stringField = f;
                            break;
                        }
                    }
                    if (stringField == null) continue;

                    cachedOuterField = outerField;
                    cachedListField = listField;
                    cachedStringField = stringField;
                    return extractFromBeans(list, stringField);
                }
            }
        }

        return new String[]{};
    }

    static void resetGroundItemActionCache() {
        cachedOuterField = null;
        cachedListField = null;
        cachedStringField = null;
    }

    private static String[] extractWithCache(Object item) throws Exception {
        cachedOuterField.setAccessible(true);
        Object outer = cachedOuterField.get(item);
        cachedOuterField.setAccessible(false);
        if (outer == null) return new String[]{};

        cachedListField.setAccessible(true);
        Object listObj = cachedListField.get(outer);
        cachedListField.setAccessible(false);
        if (!(listObj instanceof List)) return new String[]{};

        List<?> list = (List<?>) listObj;
        if (cachedStringField == null) return toStringArray(list);
        return extractFromBeans(list, cachedStringField);
    }

    private static String[] toStringArray(List<?> list) {
        String[] result = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object el = list.get(i);
            result[i] = el instanceof String ? (String) el : null;
        }
        return result;
    }

    private static String[] extractFromBeans(List<?> list, Field stringField) throws Exception {
        String[] result = new String[list.size()];
        stringField.setAccessible(true);
        for (int i = 0; i < list.size(); i++) {
            Object bean = list.get(i);
            if (bean != null) {
                Object val = stringField.get(bean);
                result[i] = val instanceof String ? (String) val : null;
            }
        }
        stringField.setAccessible(false);
        return result;
    }

    @SneakyThrows
    public static void setItemId(MenuEntry menuEntry, int itemId) throws IllegalAccessException, InvocationTargetException {
        var list =  Arrays.stream(menuEntry.getClass().getMethods())
                .filter(x -> x.getName().equals("setItemId"))
                .collect(Collectors.toList());

         list.get(0)
                .invoke(menuEntry, itemId); //use the setItemId method through reflection
    }
}
