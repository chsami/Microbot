package net.runelite.client.plugins.microbot.util.reflection;

import lombok.SneakyThrows;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import net.runelite.api.Actor;
import net.runelite.api.HeadIcon;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

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

    @SneakyThrows
    public static int getAnimation(Rs2NpcModel npc)
    {
        return getAnimation(npc.getRuneliteNpc());
    }

    /**
     * get animation id
     *
     * @param actor
     * @return uncensored animation id
     */
    private static Field sequenceField;
    private static int sequenceFieldMultiplierValue;
    @SneakyThrows
    public static int getAnimation(Actor actor)
    {
        if (actor == null)
        {
            return -1;
        }

        try
        {
            if (sequenceField == null)
            {
                final Class<?> actorSubClazz = actor.getClass();
                final Class<?> actorClazz = actorSubClazz.getSuperclass();
                //log.info("Actor class: {} | Actor sub class: {}", actorClazz.getName(), actorSubClazz.getName());
                final ClassReader classReader = new ClassReader(actorClazz.getName());
                final ClassNode classNode = new ClassNode(Opcodes.ASM9);
                classReader.accept(classNode, ClassReader.SKIP_FRAMES);
                final MethodNode getAnimationMethodNode = classNode.methods.stream()
                        .filter(m -> m.name.equals("getAnimation") && m.desc.equals("()I"))
                        .findFirst()
                        .orElse(null);
                if (getAnimationMethodNode != null)
                {
                    final InsnList instructions = getAnimationMethodNode.instructions;
                    for (AbstractInsnNode insnNode : instructions)
                    {
                        if (insnNode instanceof FieldInsnNode && ((FieldInsnNode) insnNode).desc.equals("I")
                                && insnNode.getNext() instanceof LdcInsnNode)
                        {
                            final FieldInsnNode sequenceFieldInsn = (FieldInsnNode) insnNode;
                            final LdcInsnNode multiplierInsn = (LdcInsnNode) insnNode.getNext();
                            //log.info("Found sequence field: {}.{} * {}", sequenceFieldInsn.owner, sequenceFieldInsn.name, multiplierInsn.cst);

                            sequenceField = actorClazz.getDeclaredField(sequenceFieldInsn.name);
                            sequenceFieldMultiplierValue = (int) multiplierInsn.cst;
                        }
                    }
                }
            }

            if (sequenceField == null)
            {
                Microbot.showMessage("getAnimation method is broken!");
                return -1;
            }

            sequenceField.setAccessible(true);
            final int animationId = sequenceField.getInt(actor) * sequenceFieldMultiplierValue;
            sequenceField.setAccessible(false);
            return animationId;
        }
        catch (Exception e)
        {
            Microbot.log("getAnimation method is broken! " + e.getMessage());
        }
        return -1;
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

    /**
     * Credits to EthanApi
     * @param npc
     * @return
     */
    public static HeadIcon headIconThruLengthEightArrays(NPC npc) throws IllegalAccessException {
        Class<?>[] trying = new Class<?>[]{npc.getClass(),npc.getComposition().getClass()};
        for (Class<?> aClass : trying) {
            for (Field declaredField : aClass.getDeclaredFields()) {
                Field[] decFields = declaredField.getType().getDeclaredFields();
                if(decFields.length==2){
                    if(decFields[0].getType().isArray()&&decFields[1].getType().isArray()){
                        for (Field decField : decFields) {
                            decField.setAccessible(true);
                        }
                        Object[] array1 = (Object[]) decFields[0].get(npc);
                        Object[] array2 = (Object[]) decFields[1].get(npc);
                        for (Field decField : decFields) {
                            decField.setAccessible(false);
                        }
                        if(array1.length==8&array2.length==8){
                            if(decFields[0].getType()==short[].class){
                                if((short)array1[0]==-1){
                                    return null;
                                }
                                return HeadIcon.values()[(short)array1[0]];
                            }
                            if((short)array2[0]==-1){
                                return null;
                            }
                            return HeadIcon.values()[(short)array2[0]];
                        }
                    }
                }
            }
        }
        return null;
    }

    @SneakyThrows
    public static HeadIcon getHeadIcon(Rs2NpcModel npc) {
        if(npc==null) return null;
        HeadIcon icon = getOldHeadIcon(npc.getRuneliteNpc());
        if(icon!=null){
            //System.out.println("Icon returned using oldHeadIcon");
            return icon;
        }
        icon = getOlderHeadicon(npc.getRuneliteNpc());
        if(icon!=null){
            //System.out.println("Icon returned using OlderHeadicon");
            return icon;
        }
        //System.out.println("Icon returned using headIconThruLengthEightArrays");
        icon = headIconThruLengthEightArrays(npc.getRuneliteNpc());
        return icon;
    }

    @SneakyThrows
    public static HeadIcon getOlderHeadicon(NPC npc){
        Method getHeadIconMethod = null;
        for (Method declaredMethod : npc.getComposition().getClass().getDeclaredMethods()) {
            if (declaredMethod.getName().length() == 2 && declaredMethod.getReturnType() == short.class && declaredMethod.getParameterCount() == 1) {
                getHeadIconMethod = declaredMethod;
                getHeadIconMethod.setAccessible(true);
                short headIcon = -1;
                try {
                    headIcon = (short) getHeadIconMethod.invoke(npc.getComposition(), 0);
                }catch (Exception e){
                    //nothing
                }
                getHeadIconMethod.setAccessible(false);

                if (headIcon == -1) {
                    continue;
                }
                return HeadIcon.values()[headIcon];
            }
        }
        return null;
    }

    @SneakyThrows
    public static HeadIcon getOldHeadIcon(NPC npc) {
        Method getHeadIconMethod;
        for (Method declaredMethod : npc.getClass().getDeclaredMethods()) {
            if (declaredMethod.getName().length() == 2 && declaredMethod.getReturnType() == short[].class && declaredMethod.getParameterCount() == 0) {
                getHeadIconMethod = declaredMethod;
                getHeadIconMethod.setAccessible(true);
                short[] headIcon = null;
                try {
                    headIcon = (short[]) getHeadIconMethod.invoke(npc);
                } catch (Exception e) {
                    //nothing
                }
                getHeadIconMethod.setAccessible(false);

                if (headIcon == null) {
                    continue;
                }
                return HeadIcon.values()[headIcon[0]];
            }
        }
        return null;
    }
}

