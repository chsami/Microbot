package net.runelite.client.plugins.microbot.util.reflection;

import lombok.SneakyThrows;
import net.runelite.api.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
            final String MENU_ACTION_DESCRIPTOR_VANILLA = "(IIIIIILjava/lang/String;Ljava/lang/String;IIB)V";
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

    /**
     * Gets the animation of an NPC by using reflection.
     * @param npc
     * @return
     */
    @SneakyThrows
    @Deprecated(since="1.9.8.7 - Runelite exposes all animations", forRemoval=true)
    public static int getAnimation(NPC npc) {
        if (npc == null) {
            return -1;
        }
        return npc.getAnimation();
    }

    /**
     * Gets the head icons of an NPC by using reflection.
     * @param npc
     * @return
     */
    @SneakyThrows
    @Deprecated(since="1.9.8.7 - Runelite exposes overheads on npcs", forRemoval = true)
    public static HeadIcon getHeadIcon(Rs2NpcModel npc) {
        if (npc == null) {
            return null;
        }

        if (npc.getOverheadSpriteIds() == null) {
            Microbot.log("Failed to find the correct overhead prayer.");
            return null;
        }

        for (int i = 0; i < npc.getOverheadSpriteIds().length; i++) {
            int overheadSpriteId = npc.getOverheadSpriteIds()[i];

            if (overheadSpriteId == -1) continue;

            return HeadIcon.values()[overheadSpriteId];
        }

        Microbot.log("Found overheadSpriteIds: " + Arrays.toString(npc.getOverheadSpriteIds()) + " but failed to find valid overhead prayer.");

        return null;
    }

    @SneakyThrows
    public static String[] getGroundItemActions(ItemComposition item) {
        List<Field> fields = Arrays.stream(item.getClass().getFields()).filter(x -> x.getType().isArray()).collect(Collectors.toList());
        for (Field field : fields) {
            if (field.getType().getComponentType().getName().equals("java.lang.String")) {
                String[] actions = (String[]) field.get(item);
                if (Arrays.stream(actions).anyMatch(x -> x != null && x.equalsIgnoreCase("take"))) {
                    field.setAccessible(true);
                    return actions;
                }
            }
        }
        return new String[]{};
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

