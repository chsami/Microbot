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
package net.runelite;

import lombok.extern.slf4j.Slf4j;
import java.lang.instrument.Instrumentation;
import net.runelite.transformers.ActorGetAnimationTransformer;
import net.runelite.transformers.DeviceIDTransformer;
import net.runelite.transformers.MenuActionPrintTransformer;
import net.runelite.transformers.MenuActionTransformer;
import net.runelite.transformers.RandomDatTransformer;
import net.runelite.transformers.SendStackTraceTransformer;

@Slf4j
public class Agent
{
	public static final boolean debugEnabled = false;

	/**
	 * Pre main
	 *
	 * The premain method is the entry point for a Java agent when the agent is specified at the time of starting the JVM
	 *
	 * @param agentArgs, arguments
	 * @param inst, instrumentation
	 */
	public static void premain(String agentArgs, Instrumentation inst)
	{
		instrument(agentArgs, inst);
	}

	/**
	 * Agent main
	 *
	 * The agentmain method is an entry point for a Java agent when the agent is dynamically loaded into an already running JVM
	 *
	 * @param agentArgs, arguments
	 * @param inst, instrumentation
	 */
	public static void agentmain(String agentArgs, Instrumentation inst)
	{
		instrument(agentArgs, inst);
	}

	/**
	 * Instrument
	 *
	 * Add transformers
	 *
	 * @param agentArgs, arguments
	 * @param inst, instrumentation
	 */
	private static void instrument(String agentArgs, Instrumentation inst)
	{
		log.info("RuneLite Agent loaded");

		debugMode(agentArgs, inst);

		try
		{
			inst.addTransformer(new ActorGetAnimationTransformer());
			inst.addTransformer(new MenuActionTransformer());
			inst.addTransformer(new MenuActionPrintTransformer());
			inst.addTransformer(new DeviceIDTransformer());
			inst.addTransformer(new RandomDatTransformer());
			inst.addTransformer(new SendStackTraceTransformer());
		}
		catch (Throwable e)
		{
			log.error("Instrumentation failed during transformer addition: {}", e.getMessage(), e);
		}
	}

	/**
	 * Debug mode
	 *
	 * This is used for some debugging
	 *
	 * @param agentArgs, arguments
	 * @param inst, instrumentation
	 */
	private static void debugMode(String agentArgs, Instrumentation inst)
	{
		if (!debugEnabled)
		{
			return;
		}

		log.info("Is redefine classes supported? {}", inst.isRedefineClassesSupported());
		log.info("Is retransform classes supported? {}", inst.isRetransformClassesSupported());
		for (Class<?> clazz : inst.getAllLoadedClasses())
		{
			log.info("Loaded class: {}", clazz);
		}
	}
}