/*
 * Copyright (c) 2025, Melxin <https://github.com/melxin>
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

import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Disable the vanilla RunException_sendStackTrace method
 */
@Slf4j
public class SendStackTraceTransformer implements ClassFileTransformer
{
	private static final Set<String> transformedClasses = ConcurrentHashMap.newKeySet();

	private String targetClassName;
	private final List<String> targetMethodNames = new ArrayList<>();

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer)
	{
		final ClassReader reader = new ClassReader(classFileBuffer);
		final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

		if (transformedClasses.contains(className) || className.length() > 2 && !className.equals("client"))
		{
			return classFileBuffer;
		}

		transformedClasses.add(className);

		if (targetClassName == null)
		{
			final ClassNode classNode = new ClassNode();
			reader.accept(classNode, ClassReader.SKIP_FRAMES);
			final List<MethodNode> methods = classNode.methods;
			for (MethodNode method : methods)
			{
				final InsnList instructions = method.instructions;

				for (AbstractInsnNode insnNode : instructions)
				{
					if (insnNode instanceof LdcInsnNode)
					{
						final Object value = ((LdcInsnNode) insnNode).cst;
						if (value.equals(".java:") || value.equals("clienterror.ws?cv="))
						{
							log.info("Found target instruction in class {} method {}", className, method.name);
							this.targetClassName = className;
							if (!targetMethodNames.contains(method.name))
							{
								targetMethodNames.add(method.name);
								break;
							}
						}
					}
				}
			}
		}

		// Not found yet
		if (targetClassName == null || targetMethodNames.isEmpty())
		{
			return classFileBuffer;
		}

		// Transform target methods
		reader.accept(new ClassVisitor(Opcodes.ASM9, writer)
		{
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions)
			{
				MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

				if (!targetClassName.equals(className) || !targetMethodNames.contains(name))
				{
					return mv;
				}

				log.info("Found RunException_sendStackTrace method: {} {} {}", Modifier.toString(access), name, descriptor);

				mv = new MethodVisitor(Opcodes.ASM9, mv)
				{
					@Override
					public void visitCode()
					{
						mv.visitInsn(Opcodes.RETURN);
						mv.visitEnd();
					}
				};
				return mv;
			}
		}, ClassReader.SKIP_FRAMES);

		return writer.toByteArray();
	}
}