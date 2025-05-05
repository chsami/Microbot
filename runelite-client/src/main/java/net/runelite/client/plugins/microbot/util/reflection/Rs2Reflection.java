package net.runelite.client.plugins.microbot.util.reflection;

import lombok.SneakyThrows;
import net.runelite.api.HeadIcon;
import net.runelite.api.ItemComposition;
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
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Rs2Reflection {
    static String animationField = null;
    static Method doAction = null;
    static long animationMultiplier;

    /**
     * Credits to EthanApi
     * @param npc
     * @return
     */
    @SneakyThrows
    public static int getAnimation(Rs2NpcModel npc) {
        if (npc == null) {
            return -1;
        }
        try {
            // Discover animation field and multiplier if not yet known
            if (animationField == null || animationMultiplier == 0) {
                // Gather all candidate int fields
                Field[] fields = Arrays.stream(
                                npc.getRuneliteNpc().getClass()
                                        .getSuperclass()
                                        .getDeclaredFields()
                        )
                        .filter(f -> f.getType() == int.class)
                        .filter(f -> !Modifier.isFinal(f.getModifiers()))
                        .filter(f -> !Modifier.isStatic(f.getModifiers()))
                        .toArray(Field[]::new);

                boolean[] changed = new boolean[fields.length];
                int[] originalValues = new int[fields.length];

                // Record original values
                for (int i = 0; i < fields.length; i++) {
                    fields[i].setAccessible(true);
                    originalValues[i] = fields[i].getInt(npc.getRuneliteNpc());
                    changed[i] = false;
                }


                // Probe with random animations
                for (int i = 0; i < 5; i++) {
                    int testAnim = (int)Rs2Random.nzRandom();
                    npc.getRuneliteNpc().setAnimation(testAnim);
                    for (int j = 0; j < fields.length; j++) {
                        int newVal = fields[j].getInt(npc.getRuneliteNpc());
                        if (newVal != originalValues[j]) {
                            changed[j] = true;
                        }
                    }
                }

                // Identify the single changed field
                int fieldIndex = -1;
                for (int i = 0; i < changed.length; i++) {
                    if (changed[i]) {
                        if (fieldIndex != -1) {
                            Microbot.log("Too many fields changed when detecting animation field.");
                            return -1;
                        }
                        fieldIndex = i;
                    }
                }
                if (fieldIndex < 0) {
                    Microbot.log("Failed to detect animation field.");
                    return -1;
                }

                // Cache field name
                Field animFieldCandidate = fields[fieldIndex];
                animationField = animFieldCandidate.getName();

                // Discover multiplier by setting field to 1
                animFieldCandidate.setInt(npc.getRuneliteNpc(), 1);
                long discoveredMult = npc.getRuneliteNpc().getAnimation();
                animationMultiplier = discoveredMult;
                Microbot.log("Discovered animation multiplier: " + discoveredMult);

                // Cleanup accessibility
                for (Field f : fields) {
                    f.setAccessible(false);
                }
            }

            // Read and compute actual animation
            Field animation = npc.getRuneliteNpc()
                    .getClass()
                    .getSuperclass()
                    .getDeclaredField(animationField);
            animation.setAccessible(true);
            int rawValue = animation.getInt(npc.getRuneliteNpc());
            animation.setAccessible(false);

            return (int) (rawValue * animationMultiplier);

        } catch (Exception ex) {
            Microbot.log("Failed to get animation: " + ex.getMessage());
        }
        return -1000;
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

    @SneakyThrows
    public static void invokeMenu(int param0, int param1, int opcode, int identifier, int itemId, String option, String target, int x,
                              int y) {
        if (doAction == null) {
            doAction = Arrays.stream(Microbot.getClient().getClass().getDeclaredMethods())
                    .filter(m -> m.getReturnType().getName().equals("void") && m.getParameters().length == 9 && Arrays.stream(m.getParameters())
                            .anyMatch(p -> p.getType() == String.class))
                    .findFirst()
                    .orElse(null);

            if (doAction == null) {
                Microbot.showMessage("InvokeMenuAction method is broken!");
                return;
            }
        }

        doAction.setAccessible(true);
        Microbot.getClientThread().runOnClientThreadOptional(() -> doAction.invoke(null, param0, param1, opcode, identifier, itemId, option, target, x, y));
        if (Microbot.getClient().getKeyboardIdleTicks() > Rs2Random.between(5000, 10000)) {
            Rs2Keyboard.keyPress(KeyEvent.VK_BACK_SPACE);
        }
        System.out.println("[INVOKE] => param0: " + param0 + " param1: " + param1 + " opcode: " + opcode + " id: " + identifier + " itemid: " + itemId);
        doAction.setAccessible(false);
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

