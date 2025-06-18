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
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.instrument.ClassFileTransformer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached random.dat bytes per account so each have their own random.dat
 *
 * It currently only works with client#getUsername (non-jagex launcher accounts)
 * because client#getAccountHash returns -1 at this stage
 *
 * A possible workaround would be to check for characterId
 */
@Slf4j
public class RandomDatTransformer implements ClassFileTransformer
{
	private static final Set<String> transformedClasses = ConcurrentHashMap.newKeySet();

	private String targetGetRandomDatClassName;
	private final List<String> targetGetRandomDatMethodNames = new ArrayList<>();

	private String targetWriteRandomDatClassName;
	private final List<String> targetWriteRandomDatMethodNames = new ArrayList<>();

	private FieldInsnNode getClient;

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

		if (getClient == null && className.equals("client"))
		{
			final ClassNode classNode = new ClassNode();
			reader.accept(classNode, ClassReader.SKIP_FRAMES);
			final List<MethodNode> methods = classNode.methods;
			outer:
			for (MethodNode m : methods)
			{
				final InsnList instructions = m.instructions;
				for (AbstractInsnNode insn : instructions)
				{
					if (insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.GETSTATIC)
					{
						final FieldInsnNode getStaticInsn = (FieldInsnNode) insn;
						if (getStaticInsn.desc.equals("Lclient;"))
						{
							this.getClient = getStaticInsn;
							break outer;
						}
					}
				}
			}
		}

		if (targetGetRandomDatClassName == null)
		{
			final ClassNode classNode = new ClassNode();
			reader.accept(classNode, ClassReader.SKIP_FRAMES);
			final List<MethodNode> methods = classNode.methods;
			for (MethodNode method : methods)
			{
				if (method.desc.equals("(I)[B"))
				{
					final InsnList instructions = method.instructions;
					for (AbstractInsnNode insn : instructions)
					{
						if (insn instanceof IntInsnNode && insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) insn).operand == 24
							&& insn.getNext() instanceof IntInsnNode && insn.getNext().getOpcode() == Opcodes.NEWARRAY && ((IntInsnNode) insn.getNext()).operand == 8)
						{
							this.targetGetRandomDatClassName = className;
							if (!targetGetRandomDatMethodNames.contains(method.name))
							{
								targetGetRandomDatMethodNames.add(method.name);
								break;
							}
						}
					}
				}
			}
		}

		if (targetWriteRandomDatClassName == null)
		{
			final ClassNode classNode = new ClassNode();
			reader.accept(classNode, ClassReader.SKIP_FRAMES);
			final List<MethodNode> methods = classNode.methods;
			for (MethodNode method : methods)
			{
				if (method.desc.equals("([BI[BII)V"))
				{
					this.targetWriteRandomDatClassName = className;
					if (!targetWriteRandomDatMethodNames.contains(method.name))
					{
						targetWriteRandomDatMethodNames.add(method.name);
						break;
					}
				}
			}
		}

		if (getClient == null)
		{
			return classFileBuffer;
		}

		// Transform targets
		reader.accept(new ClassVisitor(Opcodes.ASM9, writer)
		{
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions)
			{
				MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

				if (targetGetRandomDatClassName != null && targetGetRandomDatClassName.equals(className) && targetGetRandomDatMethodNames.contains(name))
				{
					return new MethodVisitor(Opcodes.ASM9, mv)
					{
						@Override
						public void visitCode()
						{
							mv.visitFieldInsn(getClient.getOpcode(), getClient.owner, getClient.name, getClient.desc);
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "client", "getUsername", "()Ljava/lang/String;", false);
							mv.visitMethodInsn(Opcodes.INVOKESTATIC, CachedRandomDat.class.getName().replace(".", "/"), "getCachedRandomDat", "(Ljava/lang/String;)[B", false);
							mv.visitVarInsn(Opcodes.ASTORE, 1); // Store cached byte[] data as local var 1

							mv.visitLdcInsn("[GamePack] Using cached random.dat: ");
							mv.visitVarInsn(Opcodes.ALOAD, 1); // byte[] data
							mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "toString", "([B)Ljava/lang/String;", false);
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
							mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
							mv.visitInsn(Opcodes.SWAP); // Swap print stream below the message
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

							// Reference cached byte[] data & return
							mv.visitVarInsn(Opcodes.ALOAD, 1);
							mv.visitInsn(Opcodes.ARETURN);

							mv.visitEnd();
						}
					};
				}

				if (targetWriteRandomDatClassName != null && targetWriteRandomDatClassName.equals(className) && targetWriteRandomDatMethodNames.contains(name))
				{
					return new AdviceAdapter(Opcodes.ASM9, mv, access, name, descriptor)
					{
						@Override
						protected void onMethodExit(int opcode)
						{
							mv.visitFieldInsn(getClient.getOpcode(), getClient.owner, getClient.name, getClient.desc);
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "client", "getUsername", "()Ljava/lang/String;", false);
							mv.visitVarInsn(Opcodes.ASTORE, 1); // store username in local var 1

							mv.visitVarInsn(Opcodes.ALOAD, 1); // load username
							mv.visitVarInsn(Opcodes.ALOAD, 2); // load byte[] data var2
							mv.visitMethodInsn(Opcodes.INVOKESTATIC, CachedRandomDat.class.getName().replace(".", "/"), "writeCachedRandomDat", "(Ljava/lang/String;[B)V", false);

							mv.visitLdcInsn("[GamePack] New random dat: ");
							mv.visitVarInsn(Opcodes.ALOAD, 2);
							mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "toString", "([B)Ljava/lang/String;", false);
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
							mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
							mv.visitInsn(Opcodes.SWAP);
							mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
							mv.visitEnd();
						}
					};
				}

				return mv;
			}
		}, ClassReader.SKIP_FRAMES);

		return writer.toByteArray();
	}

	@Slf4j
	public static class CachedRandomDat
	{
		private static File cachedRandomDatFile;
		private static Properties cachedRandomDatProperties;

		static
		{
			cachedRandomDatFile = new File(System.getProperty("user.home") + File.separator + ".runelite", "random.dat-cached.properties");
			if (!cachedRandomDatFile.exists())
			{
				try
				{
					cachedRandomDatFile.createNewFile();
				}
				catch (IOException e)
				{
					log.error("Failed to create: {}", cachedRandomDatFile.getAbsolutePath(), e);
				}
			}

			if (cachedRandomDatProperties == null)
			{
				cachedRandomDatProperties = new Properties();
			}
		}

		public static byte[] getCachedRandomDat(String username)
		{
			if (cachedRandomDatProperties.isEmpty() && cachedRandomDatFile.exists())
			{
				try (InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(cachedRandomDatFile), StandardCharsets.UTF_8))
				{
					cachedRandomDatProperties.load(inputStreamReader);
				}
				catch (IOException e)
				{
					log.warn("Unable to load cached random.dat profiles from disk", e);
				}

				if (cachedRandomDatProperties.size() > 0)
				{
					log.info("Read {} cached random.dat profiles from disk", cachedRandomDatProperties.size());
				}
			}

			byte[] data = null;
			String property = cachedRandomDatProperties.getProperty(username);
			if (property != null)
			{
				data = Base64.getDecoder().decode(property);
			}

			if (data == null)
			{
				data = new byte[24];
				for (byte i = 0; i < 24; i++)
				{
					data[i] = -1;
				}
			}

			return data;
		}

		public static void writeCachedRandomDat(String username, byte[] data)
		{
			cachedRandomDatProperties.setProperty(username, Base64.getEncoder().encodeToString(data));
			try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(Files.newOutputStream(cachedRandomDatFile.toPath()), StandardCharsets.UTF_8))
			{
				log.info("Writing random.dat {} for user {} to disk", data, username);
				cachedRandomDatProperties.store(outputStreamWriter, "Cached random.dat");
			}
			catch (IOException e)
			{
				log.warn("Unable to write cached random.dat to disk", e);
			}
		}
	}
}