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
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spoof getDeviceId(int operatingSystem) (UUID)
 */
@Slf4j
public class DeviceIDTransformer implements ClassFileTransformer
{
	private static final Set<String> transformedClasses = ConcurrentHashMap.newKeySet();

	private String targetClassName; // PlatformInfo
	private final List<String> targetMethodNames = new ArrayList<>(); // Found 3 in RuneLite's GamePack as of rev 231
	private FieldInsnNode getClient; // Vanilla static client

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classFileBuffer)
	{
		final ClassReader reader = new ClassReader(classFileBuffer);
		final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

		if (transformedClasses.contains(className) || className.length() > 2 && !className.equals("client"))
		{
			return classFileBuffer; // No modifications
		}

		transformedClasses.add(className);

		// Find static client
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
							log.info("Found static client: {}.{} {}", getStaticInsn.owner, getStaticInsn.name, getStaticInsn.desc);
							this.getClient = getStaticInsn;
							break outer;
						}
					}
				}
			}
			return classFileBuffer;
		}

		// Find target String getDeviceId(int operatingSystem) methods
		// Apparently RuneLite has 3 of the same methods but only one is used, we change all of them
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
						if (value.equals("wmic csproduct get UUID") ||
							value.equals("system_profiler SPHardwareDataType | awk '/UUID/ { print $3; }'") ||
							value.equals("cat /etc/machine-id"))
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
		if (getClient == null || targetClassName == null || targetMethodNames.isEmpty())
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

				log.info("Found getDeviceId method: {} {} {} in class {}", Modifier.toString(access), name, descriptor, className);

				mv = new MethodVisitor(Opcodes.ASM9, mv)
				{
					@Override
					public void visitCode()
					{
						// Get cached device id (UUID)
						//mv.visitFieldInsn(getUsername.getOpcode(), getUsername.owner, getUsername.name, getUsername.desc);
						mv.visitFieldInsn(getClient.getOpcode(), getClient.owner, getClient.name, getClient.desc);
						mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "client", "getAccountHash", "()J", false);
						mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf", "(J)Ljava/lang/String;", false);
						mv.visitMethodInsn(Opcodes.INVOKESTATIC, CachedDeviceID.class.getName().replace(".", "/"), "getCachedUUID", "(Ljava/lang/String;)Ljava/lang/String;", false);

						// Store cached device id as local var 1
						mv.visitVarInsn(Opcodes.ASTORE, 1);

						// Print message
						mv.visitLdcInsn("[GamePack] Using cached deviceId (UUID): ");
						mv.visitVarInsn(Opcodes.ALOAD, 1); // UUID
						mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
						mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
						mv.visitInsn(Opcodes.SWAP); // Swap print stream below the message
						mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

						// Reference cached device id & return
						mv.visitVarInsn(Opcodes.ALOAD, 1);
						mv.visitInsn(Opcodes.ARETURN);

						mv.visitMaxs(0, 0); // Writer will compute
						mv.visitEnd();
					}
				};
				return mv;
			}
		}, ClassReader.SKIP_FRAMES);

		return writer.toByteArray();
	}

	@Slf4j
	public static class CachedDeviceID
	{
		private static File cachedUUIDFile;
		private static Properties cachedUUIDProperties;

		public static String getCachedUUID(String username)
		{
			if (cachedUUIDProperties == null)
			{
				cachedUUIDFile = new File(System.getProperty("user.home") + File.separator + ".runelite", "uuid-cached.properties");
				if (!cachedUUIDFile.exists())
				{
					try
					{
						cachedUUIDFile.createNewFile();
					}
					catch (IOException e)
					{
						log.error("Failed to create: {}", cachedUUIDFile.getAbsolutePath(), e);
					}
				}
				cachedUUIDProperties = new Properties();

				if (cachedUUIDProperties.isEmpty() && cachedUUIDFile.exists())
				{
					try (InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(cachedUUIDFile), StandardCharsets.UTF_8))
					{
						cachedUUIDProperties.load(inputStreamReader);
					}
					catch (IOException e)
					{
						log.warn("Unable to load cached UUID profiles from disk", e);
					}

					if (cachedUUIDProperties.size() > 0)
					{
						log.info("Read {} cached UUID profiles from disk", cachedUUIDProperties.size());
					}
				}
			}

			String uuid = cachedUUIDProperties.getProperty(username);
			if (uuid == null)
			{
				uuid = UUID.randomUUID().toString();
				writeCachedUUID(username, uuid);
			}
			return uuid;
		}

		private static void writeCachedUUID(String username, String UUID)
		{
			cachedUUIDProperties.setProperty(username, UUID);
			try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(Files.newOutputStream(cachedUUIDFile.toPath()), StandardCharsets.UTF_8))
			{
				log.info("Writing UUID {} for user {} to disk", UUID, username);
				cachedUUIDProperties.store(outputStreamWriter, "Cached UUID");
			}
			catch (IOException e)
			{
				log.warn("Unable to write cached UUID to disk", e);
			}
		}
	}
}