package net.runelite.client.plugins.microbot.util.reflection;

import lombok.SneakyThrows;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import lombok.extern.slf4j.Slf4j;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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
        if (menuAction == null)
        {
            final String MENU_ACTION_DESCRIPTOR_VANILLA = "(IIIIIILjava/lang/String;Ljava/lang/String;III)V";
            final String MENU_ACTION_DESCRIPTOR_RUNELITE = "(IILnet/runelite/api/MenuAction;IILjava/lang/String;Ljava/lang/String;)V";

            final Class<?> clientClazz = Microbot.getClient().getClass();
            final ClassReader classReader = new ClassReader(clientClazz.getName());
            final ClassNode classNode = new ClassNode(Opcodes.ASM9);
            classReader.accept(classNode, ClassReader.SKIP_FRAMES);

            final MethodNode targetMethodNodeContainingMenuActionInvocation = classNode.methods.stream()
                    .filter(m -> m.access == Opcodes.ACC_PUBLIC
                            && (m.name.equals("menuAction") && m.desc.equals(MENU_ACTION_DESCRIPTOR_RUNELITE)
                            || m.name.equals("openWorldHopper") && m.desc.equals("()V")
                            || m.name.equals("hopToWorld") && m.desc.equals("(Lnet/runelite/api/World;)V")))
                    .findFirst()
                    .orElse(null);

            if (targetMethodNodeContainingMenuActionInvocation != null)
            {
                final InsnList instructions = targetMethodNodeContainingMenuActionInvocation.instructions;
                for (AbstractInsnNode insnNode : instructions)
                {
                    if ((insnNode instanceof LdcInsnNode || (insnNode instanceof IntInsnNode)) && insnNode.getNext() instanceof MethodInsnNode)
                    {
                        if (insnNode instanceof LdcInsnNode)
                        {
                            menuActionGarbageValue = ((LdcInsnNode) insnNode).cst;
                        }
                        else if (insnNode instanceof IntInsnNode)
                        {
                            if (insnNode.getOpcode() == Opcodes.BIPUSH)
                            {
                                menuActionGarbageValue = ((byte) ((IntInsnNode) insnNode).operand);
                            }
                            else if (insnNode.getOpcode() == Opcodes.SIPUSH)
                            {
                                menuActionGarbageValue = ((short) ((IntInsnNode) insnNode).operand);
                            }
                        }

                        final MethodInsnNode menuActionVanillaInsn = (MethodInsnNode) insnNode.getNext();
                        if (!menuActionVanillaInsn.desc.equals(MENU_ACTION_DESCRIPTOR_VANILLA))
                        {
                            throw new RuntimeException("Menu action descriptor vanilla has changed from: " + MENU_ACTION_DESCRIPTOR_VANILLA + " to: " + menuActionVanillaInsn.desc);
                        }
                        menuAction = Arrays.stream(Class.forName(menuActionVanillaInsn.owner).getDeclaredMethods())
                                .filter(m -> m.getName().equals(menuActionVanillaInsn.name))
                                .findFirst()
                                .orElse(null);
                        break;
                    }
                }
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

    @SneakyThrows
    public static String[] getGroundItemActions(ItemComposition item) {
        if (cachedOuterField != null && cachedListField != null) {
            try {
                return extractWithCache(item);
            } catch (Exception e) {
                log.warn("Ground item action cache invalidated, re-discovering");
                cachedOuterField = null;
                cachedListField = null;
                cachedStringField = null;
            }
        }

        for (Class<?> clazz = item.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field outerField : clazz.getDeclaredFields()) {
                Class<?> type = outerField.getType();
                if (type.isPrimitive() || type == String.class || type.isArray()
                        || type.getName().startsWith("java.") || type.getName().startsWith("net.runelite.")) continue;

                outerField.setAccessible(true);
                Object outerValue = outerField.get(item);
                outerField.setAccessible(false);
                if (outerValue == null) continue;

                for (Field listField : outerValue.getClass().getDeclaredFields()) {
                    if (listField.getType() != ArrayList.class) continue;

                    listField.setAccessible(true);
                    Object listObj = listField.get(outerValue);
                    listField.setAccessible(false);
                    if (!(listObj instanceof ArrayList)) continue;

                    ArrayList<?> list = (ArrayList<?>) listObj;
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
                        if (f.getType() == String.class) { stringField = f; break; }
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

    private static String[] extractWithCache(ItemComposition item) throws Exception {
        cachedOuterField.setAccessible(true);
        Object outer = cachedOuterField.get(item);
        cachedOuterField.setAccessible(false);
        if (outer == null) return new String[]{};

        cachedListField.setAccessible(true);
        Object listObj = cachedListField.get(outer);
        cachedListField.setAccessible(false);
        if (!(listObj instanceof ArrayList)) return new String[]{};

        ArrayList<?> list = (ArrayList<?>) listObj;
        if (cachedStringField == null) return toStringArray(list);
        return extractFromBeans(list, cachedStringField);
    }

    private static String[] toStringArray(ArrayList<?> list) {
        String[] result = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object el = list.get(i);
            result[i] = el instanceof String ? (String) el : null;
        }
        return result;
    }

    private static String[] extractFromBeans(ArrayList<?> list, Field stringField) throws Exception {
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

