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
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enhance the menuAction method and create printMenuActions setter/getter to include print messages
 * This allows the client to use client.setPrintMenuActions(boolean printMenuActions) to toggle the printing of menu actions
 */
@Slf4j
public class MenuActionPrintTransformer implements ClassFileTransformer
{
	private static final Set<String> transformedClasses = ConcurrentHashMap.newKeySet();

	private static final String VANILLA_CLIENT_NAME = "client";

	private static final int MENU_ACTION_ACCESS_FLAGS_VANILLA = (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);
	private static final String MENU_ACTION_DESCRIPTOR_VANILLA = "(IIIIIILjava/lang/String;Ljava/lang/String;IIB)V";

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer)
	{
		if (transformedClasses.contains(className) || (!className.equals(VANILLA_CLIENT_NAME) && className.length() > 2))
		{
			return classFileBuffer;
		}

		transformedClasses.add(className);

		final Stopwatch stopWatch = Stopwatch.createStarted();

		final ClassReader reader = new ClassReader(classFileBuffer);
		final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

		final AtomicBoolean isInserted = new AtomicBoolean();

		reader.accept(new ClassVisitor(Opcodes.ASM9, writer)
		{
			@Override
			public void visitEnd()
			{
				super.visitEnd();

				if (className.equals(VANILLA_CLIENT_NAME))
				{
					// Define the static field 'printMenuActions'
					writer.visitField(Opcodes.ACC_STATIC, "printMenuActions", "Z", null, false).visitEnd();

					// Create the setter method for the static field
					MethodVisitor setterMv = writer.visitMethod(Opcodes.ACC_PUBLIC, "setPrintMenuActions", "(Z)V", null, null);

					// Method body: set the static field
					setterMv.visitCode();

					// Load the boolean parameter (at index 1) onto the stack
					setterMv.visitVarInsn(Opcodes.ILOAD, 1); // Load the boolean parameter
					// Set the static field
					setterMv.visitFieldInsn(Opcodes.PUTSTATIC, className, "printMenuActions", "Z");

					// Return from the method
					setterMv.visitInsn(Opcodes.RETURN);
					setterMv.visitMaxs(1, 2);
					setterMv.visitEnd();

					// Create the getter method for the static field
					MethodVisitor getterMv = writer.visitMethod(Opcodes.ACC_PUBLIC, "isPrintMenuActions", "()Z", null, null);

					// Method body: return the static field
					getterMv.visitCode();

					// Get the value of the static field
					getterMv.visitFieldInsn(Opcodes.GETSTATIC, className, "printMenuActions", "Z");

					// Return the boolean value
					getterMv.visitInsn(Opcodes.IRETURN);
					getterMv.visitMaxs(1, 1);
					getterMv.visitEnd();
				}
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions)
			{
				MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

				if (access != MENU_ACTION_ACCESS_FLAGS_VANILLA || !descriptor.equals(MENU_ACTION_DESCRIPTOR_VANILLA))
				{
					return mv;
				}

				log.info("Found menu action: {}.{} {}", className, name, descriptor);

				mv = new MethodVisitor(Opcodes.ASM9, mv)
				{
					@Override
					public void visitCode()
					{
						if (!isInserted.get())
						{
							// Prepare for the check on printMenuActions
							mv.visitFieldInsn(Opcodes.GETSTATIC, VANILLA_CLIENT_NAME, "printMenuActions", "Z");

							// Create a label for the jump target
							Label endLabel = new Label();

							// Jump to the end of the log construction if printMenuActions is false
							mv.visitJumpInsn(Opcodes.IFEQ, endLabel);

							// Prepare to build the log message using StringBuilder
							mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
							mv.visitInsn(Opcodes.DUP);
							mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

							// Push parameters to the stack and append them to the StringBuilder
							mv.visitLdcInsn("[MenuAction] Param0=");
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

							mv.visitVarInsn(Opcodes.ILOAD, 0); // param0
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

							mv.visitLdcInsn(" Param1=");
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

							mv.visitVarInsn(Opcodes.ILOAD, 1); // param1
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

							mv.visitLdcInsn(" opcode=");
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

							mv.visitVarInsn(Opcodes.ILOAD, 2); // opcode
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

							mv.visitLdcInsn(" identifier=");
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

							mv.visitVarInsn(Opcodes.ILOAD, 3); // identifier
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

							mv.visitLdcInsn(" itemId=");
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

							mv.visitVarInsn(Opcodes.ILOAD, 4); // itemId
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

							mv.visitLdcInsn(" worldViewId=");
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

							mv.visitVarInsn(Opcodes.ILOAD, 5); // worldViewId
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

							mv.visitLdcInsn(" option=");
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

							mv.visitVarInsn(Opcodes.ALOAD, 6); // option
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

							mv.visitLdcInsn(" target=");
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

							mv.visitVarInsn(Opcodes.ALOAD, 7); // target
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

							mv.visitLdcInsn(" canvasX=");
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

							mv.visitVarInsn(Opcodes.ILOAD, 8); // canvasX
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

							mv.visitLdcInsn(" canvasY=");
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

							mv.visitVarInsn(Opcodes.ILOAD, 9); // canvasY
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);

							// Finalize string and print to console
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
							// Now we should have a String on the stack, and we need to ensure we get the PrintStream.
							mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
							mv.visitInsn(Opcodes.SWAP); // Swap the top two stack items, so the String is on top for println.
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

							// Mark the end label
							mv.visitLabel(endLabel);

							isInserted.set(true);
						}
						super.visitCode();
					}
				};
				return mv;
			}
		}, ClassReader.SKIP_FRAMES);

		if (isInserted.get())
		{
			log.info("Took: {}", stopWatch);
		}
		return writer.toByteArray();
	}
}