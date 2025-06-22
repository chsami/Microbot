/*
 * Copyright (c) 2024, Melxin <https://github.com/melxin/>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.transformers;

import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Add menuAction method in vanilla client invoking the original resulting in menuAction being accessible from the runelite-api
 */
@Slf4j
public class MenuActionTransformer implements ClassFileTransformer
{
	private static final Set<String> transformedClasses = ConcurrentHashMap.newKeySet();

	private static final String VANILLA_CLIENT_NAME = "client";

	private static final int MENU_ACTION_ACCESS_FLAGS_VANILLA = (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);
	private static final String MENU_ACTION_DESCRIPTOR_VANILLA = "(IIIIIILjava/lang/String;Ljava/lang/String;IIB)V"; // This is with the garbage value, last integer
	private static final String MENU_ACTION_DESCRIPTOR_WITHOUT_GARBAGE_VALUE = "(IIIIIILjava/lang/String;Ljava/lang/String;II)V";
	private static final String MENU_ACTION_DESCRIPTOR_RUNELITE = "(IILnet/runelite/api/MenuAction;IILjava/lang/String;Ljava/lang/String;)V";

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer)
	{
		if (transformedClasses.contains(className) || !className.equals(VANILLA_CLIENT_NAME))
		{
			return classFileBuffer;
		}

		transformedClasses.add(className);

		final ClassReader reader = new ClassReader(classFileBuffer);
		final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

		//log.info("Found vanilla client class access: {} name: {} superName: {} interfaces: {}", Modifier.toString(reader.getAccess()), reader.getClassName(), reader.getSuperName(), reader.getInterfaces());

		Stopwatch stopWatch = Stopwatch.createStarted();

		final ClassNode classNode = new ClassNode();
		reader.accept(classNode, ClassReader.SKIP_FRAMES);

		final MethodNode targetMethodNodeContainingMenuActionInvocation = classNode.methods.stream()
			.filter(m -> m.access == Opcodes.ACC_PUBLIC
				&& (m.name.equals("menuAction") && m.desc.equals(MENU_ACTION_DESCRIPTOR_RUNELITE)
				|| m.name.equals("openWorldHopper") && m.desc.equals("()V")
				|| m.name.equals("hopToWorld") && m.desc.equals("(Lnet/runelite/api/World;)V")))
			.findFirst()
			.orElse(null);

		if (targetMethodNodeContainingMenuActionInvocation == null)
		{
			throw new RuntimeException("No method was found containing menu action invocation!");
		}

		//log.info("Found method containing menu action invocation: {} {} {}", Modifier.toString(targetMethodNodeContainingMenuActionInvocation.access), targetMethodNodeContainingMenuActionInvocation.name, targetMethodNodeContainingMenuActionInvocation.desc);

		final InsnList targetMethodInstructionsContainingMenuActionInvocationRuneLite = targetMethodNodeContainingMenuActionInvocation.instructions;

		reader.accept(new ClassVisitor(Opcodes.ASM9, writer)
		{
			/**
			 * Create non static method
			 */
			@Override
			public void visitEnd()
			{
				super.visitEnd();

				// Create method
				final MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "menuAction", MENU_ACTION_DESCRIPTOR_WITHOUT_GARBAGE_VALUE, null, null);
				mv.visitCode();

				mv.visitVarInsn(Opcodes.ALOAD, 0); // self

				// Load arguments onto the stack
				mv.visitVarInsn(Opcodes.ILOAD, 1); // var0
				mv.visitVarInsn(Opcodes.ILOAD, 2); // var1
				mv.visitVarInsn(Opcodes.ILOAD, 3); // var2
				mv.visitVarInsn(Opcodes.ILOAD, 4); // var3
				mv.visitVarInsn(Opcodes.ILOAD, 5); // var4
				mv.visitVarInsn(Opcodes.ILOAD, 6); // var5

				mv.visitVarInsn(Opcodes.ALOAD, 7); // var6 (String)
				mv.visitVarInsn(Opcodes.ALOAD, 8); // var7 (String)

				mv.visitVarInsn(Opcodes.ILOAD, 9); // var8
				mv.visitVarInsn(Opcodes.ILOAD, 10); // var9

				for (AbstractInsnNode insnNode : targetMethodInstructionsContainingMenuActionInvocationRuneLite)
				{
					if ((insnNode instanceof LdcInsnNode || (insnNode instanceof IntInsnNode)) && insnNode.getNext() instanceof MethodInsnNode)
					{
						if (insnNode instanceof LdcInsnNode)
						{
							mv.visitLdcInsn(((LdcInsnNode) insnNode).cst); // var10
						}
						else if (insnNode instanceof IntInsnNode)
						{
							if (insnNode.getOpcode() == Opcodes.BIPUSH)
							{
								mv.visitIntInsn(Opcodes.BIPUSH, ((byte) ((IntInsnNode) insnNode).operand)); // var 10
							}
							else if (insnNode.getOpcode() == Opcodes.SIPUSH)
							{
								mv.visitIntInsn(Opcodes.SIPUSH, ((short) ((IntInsnNode) insnNode).operand)); // var10
							}
						}

						final MethodInsnNode menuActionVanillaInsn = (MethodInsnNode) insnNode.getNext();
						if (!menuActionVanillaInsn.desc.equals(MENU_ACTION_DESCRIPTOR_VANILLA))
						{
							throw new RuntimeException("Menu action descriptor vanilla has changed from: " + MENU_ACTION_DESCRIPTOR_VANILLA + " to: " + menuActionVanillaInsn.desc);
						}

						mv.visitMethodInsn(menuActionVanillaInsn.getOpcode(), menuActionVanillaInsn.owner, menuActionVanillaInsn.name, menuActionVanillaInsn.desc, menuActionVanillaInsn.itf); // var11
						break;
					}
				}
				mv.visitInsn(Opcodes.RETURN);
				mv.visitMaxs(10, 10);
				mv.visitEnd();
			}
		}, ClassReader.SKIP_FRAMES);

		log.info("Took: {}", stopWatch);
		return writer.toByteArray();
	}
}