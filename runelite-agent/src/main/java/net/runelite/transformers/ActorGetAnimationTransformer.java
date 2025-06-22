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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remove animation restrictions
 *
 * Those restrictions were added by RuneLite dev to prevent getting animation from bosses and returns -1 for those instead
 * This transformer fixes that by directly returning the sequence field
 */
@Slf4j
public class ActorGetAnimationTransformer implements ClassFileTransformer
{
	private static final Set<String> transformedClasses = ConcurrentHashMap.newKeySet();
	//private static final Logger log = LoggerFactory.getLogger(ActorGetAnimationTransformer.class);

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer)
	{
		final ClassReader reader = new ClassReader(classFileBuffer);
		final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

		if (transformedClasses.contains(className)
			|| className.startsWith("net/runelite/api/")
			|| !Arrays.asList(reader.getInterfaces()).contains("net/runelite/api/Actor"))
		{
			return classFileBuffer; // No modifications
		}

		transformedClasses.add(className);

		log.info("Found vanilla actor class: {}", className);

		Stopwatch stopwatch = Stopwatch.createStarted();

		final ClassNode classNode = new ClassNode();
		reader.accept(classNode, ClassReader.SKIP_FRAMES);
		//classNode.accept(writer);

		reader.accept(new ClassVisitor(Opcodes.ASM9, writer)
		{
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions)
			{
				MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

				if (!name.equalsIgnoreCase("getAnimation") || !descriptor.equals("()I"))
				{
					return mv;
				}

				log.info("Found getAnimation method: {} {} {}", Modifier.toString(access), name, descriptor);

				mv = new MethodVisitor(Opcodes.ASM9, mv)
				{
					@Override
					public void visitCode()
					{

						final MethodNode getAnimationMethodNode = classNode.methods.stream()
							.filter(m -> m.name.equals("getAnimation") && m.desc.equals("()I"))
							.findFirst()
							.orElseThrow(() -> new NoSuchElementException("Method 'getAnimation' with descriptor '()I' not found"));

						final InsnList instructions = getAnimationMethodNode.instructions;

						mv.visitVarInsn(Opcodes.ALOAD, 0);

						for (AbstractInsnNode insnNode : instructions)
						{
							if (insnNode instanceof FieldInsnNode && ((FieldInsnNode) insnNode).desc.equals("I")
								&& insnNode.getNext() instanceof LdcInsnNode)
							{
								final FieldInsnNode sequenceField = (FieldInsnNode) insnNode;
								final LdcInsnNode multiplier = (LdcInsnNode) insnNode.getNext();
								log.info("Found sequence field: {}.{} * {}", sequenceField.owner, sequenceField.name, multiplier.cst);
								mv.visitFieldInsn(sequenceField.getOpcode(), sequenceField.owner, sequenceField.name, sequenceField.desc);
								mv.visitLdcInsn(multiplier.cst);
								mv.visitInsn(Opcodes.IMUL);
								break;
							}
						}
						mv.visitInsn(Opcodes.IRETURN);
						mv.visitMaxs(2, 1);
						mv.visitEnd();
					}
				};
				return mv;
			}
		}, ClassReader.SKIP_FRAMES);

		log.info("Took: {}", stopwatch);
		return writer.toByteArray();
	}
}