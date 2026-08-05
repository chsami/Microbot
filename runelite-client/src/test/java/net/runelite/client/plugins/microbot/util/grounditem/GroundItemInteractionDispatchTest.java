package net.runelite.client.plugins.microbot.util.grounditem;

import java.io.IOException;
import java.io.InputStream;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GroundItemInteractionDispatchTest
{
    @Test
    public void legacyGroundItemUsesSyntheticTargetMenu() throws IOException
    {
        assertSyntheticTargetMenuDispatch(
                Rs2GroundItem.class,
                "interact",
                Type.getMethodDescriptor(Type.BOOLEAN_TYPE,
                        Type.getType(InteractModel.class), Type.getType(String.class)));
    }

    @Test
    public void tileItemUsesSyntheticTargetMenu() throws IOException
    {
        assertSyntheticTargetMenuDispatch(
                Rs2TileItemModel.class,
                "click",
                Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(String.class)));
    }

    private static void assertSyntheticTargetMenuDispatch(Class<?> type, String methodName,
                                                          String methodDescriptor) throws IOException
    {
        DispatchCalls calls = readDispatchCalls(type, methodName, methodDescriptor);

        assertEquals(type.getSimpleName() + " must contain the expected interaction method",
                1, calls.matchedMethods);
        assertTrue(type.getSimpleName() + " must dispatch through Microbot.doInvoke", calls.doInvoke > 0);
        assertEquals(type.getSimpleName() + " must not dispatch through Rs2Reflection.invokeMenu",
                0, calls.reflectionInvokeMenu);
    }

    private static DispatchCalls readDispatchCalls(Class<?> type, String expectedName,
                                                   String expectedDescriptor) throws IOException
    {
        String resource = "/" + Type.getInternalName(type) + ".class";
        DispatchCalls calls = new DispatchCalls();
        try (InputStream input = type.getResourceAsStream(resource))
        {
            if (input == null)
            {
                throw new IOException("Unable to load " + resource);
            }

            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9)
            {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions)
                {
                    boolean expectedMethod = name.equals(expectedName)
                            && descriptor.equals(expectedDescriptor);
                    if (expectedMethod)
                    {
                        calls.matchedMethods++;
                    }
                    return new MethodVisitor(Opcodes.ASM9)
                    {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface)
                        {
                            if (expectedMethod && owner.equals(Type.getInternalName(Microbot.class))
                                    && methodName.equals("doInvoke"))
                            {
                                calls.doInvoke++;
                            }
                            // Scan the whole class so a lambda$... or same-class helper cannot hide a
                            // reintroduced reflection dispatch from the expected interaction method.
                            if (owner.equals(Type.getInternalName(Rs2Reflection.class)) && methodName.equals("invokeMenu"))
                            {
                                calls.reflectionInvokeMenu++;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
        }
        return calls;
    }

    private static final class DispatchCalls
    {
        private int matchedMethods;
        private int doInvoke;
        private int reflectionInvokeMenu;
    }
}
