package net.runelite.client.plugins.microbot.util.reflection;

import lombok.SneakyThrows;
import net.runelite.api.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.security.Login;

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

    /**
     * sequence maps to an actor animation
     * actor can be an npc/player
     */
    static int animationMultiplier = 527657827; //can be found in actor.java (int sequence)
    static final byte INDEX_GARBAGE = -28; // found in Varcs.java
    static final String INDEX_FIELD = "ab"; // Varcs.java
    static final String INDEX_CLASS = "es"; // login.java
    public static final String SESSION_FIELD = "gv"; //AsyncHttpResponse.java
    public static final String SESSION_CLASS = "ag"; // AsyncHttpResponse.java
    public static final String CHAR_FIELD = "gl"; //DevicePcmPlayerProvider.java
    public static final String CHAR_CLASS = "am"; //DevicePcmPlayerProvider.java
    public static final String DISPLAY_FIELD = "cw"; //Login.java
    public static final String DISPLAY_CLASS = "dh"; //Login.java


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
            if (animationField == null) {
                for (Field declaredField : npc.getRuneliteNpc().getClass().getSuperclass().getDeclaredFields()) {
                    if (declaredField == null) {
                        continue;
                    }
                    declaredField.setAccessible(true);
                    if (declaredField.getType() != int.class) {
                        continue;
                    }
                    if (Modifier.isFinal(declaredField.getModifiers())) {
                        continue;
                    }
                    if (Modifier.isStatic(declaredField.getModifiers())) {
                        continue;
                    }
                    int value = declaredField.getInt(npc.getRuneliteNpc());
                    declaredField.setInt(npc.getRuneliteNpc(), 4795789);
                    if (npc.getRuneliteNpc().getAnimation() == animationMultiplier * 4795789) {
                        animationField = declaredField.getName();
                        declaredField.setInt(npc.getRuneliteNpc(), value);
                        declaredField.setAccessible(false);
                        break;
                    }
                    declaredField.setInt(npc.getRuneliteNpc(), value);
                    declaredField.setAccessible(false);
                }
            }
            if (animationField == null) {
                return -1;
            }
            Field animation = npc.getRuneliteNpc().getClass().getSuperclass().getDeclaredField(animationField);
            animation.setAccessible(true);
            int anim = animation.getInt(npc.getRuneliteNpc()) * animationMultiplier;
            animation.setAccessible(false);
            return anim;
        } catch(Exception ex) {
            Microbot.log("Failed to get animation : " + ex.getMessage());
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
        Microbot.getClientThread().runOnClientThread(() -> doAction.invoke(null, param0, param1, opcode, identifier, itemId, option, target, x, y));
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



    /**
     * Login with another jagex account without restarting client
     * @param login
     * @param account
     */
    public static void setLoginWithJagexAccount(boolean login, Account account) {
        Microbot.getClientThread().invokeLater(() -> {
            if (Microbot.getClient().getGameState() != GameState.LOGIN_SCREEN) {
                return;
            }

            try {
                //set loginIndex to login screen
                Class<?> paramComposition = Class.forName(INDEX_CLASS, true, Microbot.getClient().getClass().getClassLoader());
                Method updateLoginIndex = paramComposition.getDeclaredMethod(INDEX_FIELD, int.class, byte.class);
                updateLoginIndex.setAccessible(true);
                updateLoginIndex.invoke(null,  10, INDEX_GARBAGE);

                Class<?> AsyncHttpResponseClass = Class.forName(SESSION_CLASS, true, Microbot.getClient().getClass().getClassLoader());
                Field sessionIdField = AsyncHttpResponseClass.getDeclaredField(SESSION_FIELD);
                sessionIdField.setAccessible(true);
                sessionIdField.set(null, account.getSessionId());

                Class<?> DevicePcmPlayerProviderClass = Class.forName(CHAR_CLASS, true, Microbot.getClient().getClass().getClassLoader());
                Field characterIdField = DevicePcmPlayerProviderClass.getDeclaredField(CHAR_FIELD);
                characterIdField.setAccessible(true);
                characterIdField.set(null, account.getAccountId());

                Class<?> LoginClass = Class.forName(DISPLAY_CLASS, true, Microbot.getClient().getClass().getClassLoader());
                Field displayNameField = LoginClass.getDeclaredField(DISPLAY_FIELD);
                displayNameField.setAccessible(true);
                displayNameField.set(null, account.getDisplayName());

                System.setProperty("JX_CHARACTER_ID", account.getAccountId());
                System.setProperty("JX_SESSION_ID", account.getSessionId());
                System.setProperty("JX_DISPLAY_NAME", account.getDisplayName());

            } catch (Exception e) {
                e.printStackTrace();
            }

            if (login) {
                new Login("", "");
            }
        });
    }

}

