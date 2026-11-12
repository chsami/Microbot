package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SystemCalls;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Where moveOffScreen leaves the canvas and where moveRandom goes must be random on every calling
 * thread, including one that has never used ThreadLocalRandom. A ThreadLocalRandom instance draws
 * from the calling thread's own state, and only ThreadLocalRandom.current() seeds that state, so an
 * instance obtained on another thread hands an unseeded thread a fixed sequence: the same one on
 * every such thread on JDK 11, and one fixed per thread id on JDK 17.
 *
 * <p>Nothing on a subject thread may seed it before the draws, so the client here is a plain
 * {@link Proxy} rather than a Mockito mock, whose invocation handling is not ours to vouch for, and
 * every class on the path to the draws is loaded on the test thread first.
 */
public class NaturalMouseRandomTargetTest
{
	private static final int CANVAS_W = 765;
	private static final int CANVAS_H = 503;
	/** Fresh threads per method in one JVM. */
	private static final int THREADS_PER_KIND = 4;
	/** Fresh threads per method in each child JVM. */
	private static final int CHILD_THREADS_PER_KIND = 6;
	/** Matched (method, thread id) pairs the cross-launch comparison needs before it says anything. */
	private static final int MIN_MATCHED = 6;
	private static final long CHILD_TIMEOUT_MS = 90_000L;
	private static final String TARGET_LINE = "TARGET ";

	enum Kind
	{
		OFF_SCREEN,
		OFF_SCREEN_WITH_CHANCE,
		RANDOM
	}

	private Rig rig;
	private Object previousClient;
	private Object previousNaturalMouse;
	private ActivityIntensity previousIntensity;
	private boolean previousDynamicIntensity;

	@Before
	public void before() throws Exception
	{
		previousIntensity = Rs2Antiban.getActivityIntensity();
		previousDynamicIntensity = Rs2AntibanSettings.dynamicIntensity;
		previousClient = readStatic("client");
		previousNaturalMouse = readStatic("naturalMouse");
		PointerState.reset();
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		InputLoop.setLockTimeoutForTest(5_000L);
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		rig = new Rig();
	}

	@After
	public void after() throws Exception
	{
		writeStatic("client", previousClient);
		writeStatic("naturalMouse", previousNaturalMouse);
		Rs2Antiban.setActivityIntensity(previousIntensity);
		Rs2AntibanSettings.dynamicIntensity = previousDynamicIntensity;
		Microbot.targetMenu = null;
		PointerState.reset();
		InputArbiter.resetForTest();
		AwtEmitter.resetHeldButtonsForTest();
		InputLoop.setLockTimeoutForTest(5_000L);
		ScriptPauseController.setPaused(false).get(2, TimeUnit.SECONDS);
		while (BotEventGuard.isSynthetic())
		{
			BotEventGuard.end();
		}
	}

	@Test(timeout = 30_000)
	public void moveOffScreenLeavesAtDifferentPointsOnFreshThreads() throws Exception
	{
		assertFreshThreadsDisagree(Kind.OFF_SCREEN, "moveOffScreen() left the canvas");
	}

	@Test(timeout = 30_000)
	public void moveOffScreenWithAChanceLeavesAtDifferentPointsOnFreshThreads() throws Exception
	{
		assertFreshThreadsDisagree(Kind.OFF_SCREEN_WITH_CHANCE, "moveOffScreen(99.9999999) left the canvas");
	}

	@Test(timeout = 30_000)
	public void moveRandomGoesToDifferentPointsOnFreshThreads() throws Exception
	{
		assertFreshThreadsDisagree(Kind.RANDOM, "moveRandom() went");
	}

	/**
	 * Holds on any JVM: an unseeded thread's sequence is a fixed function of its thread id on JDK 17
	 * and of nothing at all on JDK 11, and two launches of the same program give their threads the
	 * same ids. So the same method on the same thread id must not land on the same point in two
	 * launches of the same program.
	 */
	@Test(timeout = 240_000)
	public void targetsAreNotRepeatedByTheSameThreadInAnotherLaunch() throws Exception
	{
		Map<String, String> first = launchChild();
		Map<String, String> second = launchChild();

		List<String> matched = new ArrayList<>();
		int identical = 0;
		for (Map.Entry<String, String> entry : first.entrySet())
		{
			String other = second.get(entry.getKey());
			if (other == null)
			{
				continue;
			}
			matched.add(entry.getKey() + " -> " + entry.getValue() + " / " + other);
			if (other.equals(entry.getValue()))
			{
				identical++;
			}
		}
		assertTrue("only " + matched.size() + " (method, thread id) pairs appear in both launches, fewer than "
			+ MIN_MATCHED + ", so this comparison cannot tell a fixed sequence from a random one: first " + first
			+ ", second " + second, matched.size() >= MIN_MATCHED);
		assertFalse("every one of " + matched.size() + " fresh threads chose the same target as the thread with its"
				+ " id in another launch, so the targets are fixed for that thread, not random: " + matched,
			identical == matched.size());
	}

	private void assertFreshThreadsDisagree(Kind kind, String what) throws Exception
	{
		// Measures the JVM, not the code under test: whether two threads that never seeded
		// themselves draw the same values from an instance obtained elsewhere.
		Assume.assumeTrue("this JVM (java " + System.getProperty("java.version") + ") gives unseeded threads"
				+ " different sequences, so one launch cannot show a fixed sequence;"
				+ " targetsAreNotRepeatedByTheSameThreadInAnotherLaunch covers it",
			unseededThreadsRepeat());

		List<String> targets = new ArrayList<>();
		for (int i = 0; i < THREADS_PER_KIND; i++)
		{
			targets.add(rig.targetOnFreshThread(kind));
		}
		assertFalse(what + " at the same point on all " + THREADS_PER_KIND + " fresh threads: " + targets,
			new HashSet<>(targets).size() == 1);
	}

	private static boolean unseededThreadsRepeat() throws InterruptedException
	{
		ThreadLocalRandom captured = ThreadLocalRandom.current();
		long[] draws = new long[2];
		for (int i = 0; i < draws.length; i++)
		{
			int slot = i;
			Thread thread = new Thread(() -> draws[slot] = captured.nextLong(), "unseeded-probe-" + i);
			thread.setDaemon(true);
			thread.start();
			thread.join(5_000L);
			assertFalse("the unseeded probe thread did not finish", thread.isAlive());
		}
		return draws[0] == draws[1];
	}

	private static Map<String, String> launchChild() throws Exception
	{
		String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		// An argument file, because the test classpath is far longer than a Windows command line.
		File args = File.createTempFile("random-target-child", ".args");
		args.deleteOnExit();
		String classpath = System.getProperty("java.class.path").replace("\\", "\\\\").replace("\"", "\\\"");
		List<String> lines = new ArrayList<>();
		lines.add("-cp");
		lines.add("\"" + classpath + "\"");
		String headless = System.getProperty("java.awt.headless");
		if (headless != null)
		{
			lines.add("-Djava.awt.headless=" + headless);
		}
		lines.add(Child.class.getName());
		Files.write(args.toPath(), lines, StandardCharsets.UTF_8);

		Process process = new ProcessBuilder(java, "@" + args.getAbsolutePath()).redirectErrorStream(true).start();
		List<String> output = Collections.synchronizedList(new ArrayList<>());
		Thread reader = new Thread(() -> {
			try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = in.readLine()) != null)
				{
					output.add(line);
				}
			}
			catch (Exception ignored)
			{
				// The process output ends either way; a short read shows up as missing targets below.
			}
		}, "child-output");
		reader.setDaemon(true);
		reader.start();
		if (!process.waitFor(CHILD_TIMEOUT_MS, TimeUnit.MILLISECONDS))
		{
			process.destroyForcibly();
			fail("the child JVM did not finish within " + CHILD_TIMEOUT_MS + "ms: " + output);
		}
		reader.join(5_000L);

		Map<String, String> targets = new HashMap<>();
		for (String line : new ArrayList<>(output))
		{
			if (line.startsWith(TARGET_LINE))
			{
				String[] parts = line.substring(TARGET_LINE.length()).split(" ");
				targets.put(parts[0] + "@" + parts[1], parts[2] + "," + parts[3]);
			}
		}
		assertTrue("the child JVM exited with " + process.exitValue() + " and reported "
				+ targets.size() + " of " + (3 * CHILD_THREADS_PER_KIND) + " targets: " + output,
			process.exitValue() == 0 && targets.size() == 3 * CHILD_THREADS_PER_KIND);
		return targets;
	}

	/** Runs in a fresh JVM, so its threads get the same ids from one launch to the next. */
	public static final class Child
	{
		public static void main(String[] args) throws Exception
		{
			Rig rig = new Rig();
			for (Kind kind : Kind.values())
			{
				for (int i = 0; i < CHILD_THREADS_PER_KIND; i++)
				{
					AtomicReference<Long> id = new AtomicReference<>();
					String target = rig.targetOnFreshThread(kind, id);
					System.out.println(TARGET_LINE + kind + " " + id.get() + " " + target.replace(",", " "));
				}
			}
			System.out.flush();
			System.exit(0);
		}
	}

	/**
	 * A canvas, a plain proxy client and a NaturalMouse whose steps do not wait, so a subject call is
	 * milliseconds. Records every canvas event with the thread that dispatched it.
	 */
	static final class Rig
	{
		private final NaturalMouse naturalMouse;
		private final List<Object[]> events = Collections.synchronizedList(new ArrayList<>());

		Rig() throws Exception
		{
			Canvas canvas = new Canvas();
			MouseAdapter recorder = new MouseAdapter()
			{
				@Override
				public void mouseMoved(MouseEvent e)
				{
					events.add(new Object[]{Thread.currentThread(), e.getID(), e.getX(), e.getY()});
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					events.add(new Object[]{Thread.currentThread(), e.getID(), e.getX(), e.getY()});
				}
			};
			canvas.addMouseListener(recorder);
			canvas.addMouseMotionListener(recorder);

			Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
				(proxy, method, arguments) -> {
					switch (method.getName())
					{
						case "getCanvas":
							return canvas;
						case "getCanvasWidth":
							return CANVAS_W;
						case "getCanvasHeight":
							return CANVAS_H;
						case "hashCode":
							return System.identityHashCode(proxy);
						case "equals":
							return proxy == arguments[0];
						case "toString":
							return "proxy client";
						default:
							return defaultFor(method.getReturnType());
					}
				});
			writeStatic("client", client);

			naturalMouse = new NaturalMouse();
			Field injected = NaturalMouse.class.getDeclaredField("client");
			injected.setAccessible(true);
			injected.set(naturalMouse, client);
			writeStatic("naturalMouse", naturalMouse);
			Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);

			// Public API on the motion nature: the steps still go out, they just do not wait.
			SystemCalls real = naturalMouse.nature.getSystemCalls();
			naturalMouse.nature.setSystemCalls(new SystemCalls()
			{
				@Override
				public long currentTimeMillis()
				{
					return real.currentTimeMillis();
				}

				@Override
				public void sleep(long time)
				{
				}

				@Override
				public Dimension getScreenSize()
				{
					return real.getScreenSize();
				}

				@Override
				public void setMousePosition(int x, int y)
				{
					real.setMousePosition(x, y);
				}
			});

			// Loads and initialises every class on the way to the draws here, on a thread that is not
			// a subject, so no class initialiser runs on a subject thread ahead of its draws.
			for (Kind kind : Kind.values())
			{
				resetPointer();
				call(kind);
			}
		}

		String targetOnFreshThread(Kind kind) throws Exception
		{
			return targetOnFreshThread(kind, new AtomicReference<>());
		}

		String targetOnFreshThread(Kind kind, AtomicReference<Long> id) throws Exception
		{
			resetPointer();
			AtomicReference<Throwable> failure = new AtomicReference<>();
			// Nothing but the call itself runs on this thread before the draws.
			Thread subject = new Thread(() -> {
				try
				{
					call(kind);
				}
				catch (Throwable t)
				{
					failure.set(t);
				}
			}, "fresh-subject");
			subject.setDaemon(true);
			subject.start();
			subject.join(10_000L);
			if (subject.isAlive())
			{
				fail(kind + " did not return on a fresh thread within 10000ms");
			}
			if (failure.get() != null)
			{
				throw new AssertionError(kind + " threw on a fresh thread", failure.get());
			}
			id.set(subject.getId());

			int wanted = kind == Kind.RANDOM ? MouseEvent.MOUSE_MOVED : MouseEvent.MOUSE_EXITED;
			String target = null;
			synchronized (events)
			{
				for (Object[] event : events)
				{
					if (event[0] == subject && (int) event[1] == wanted)
					{
						target = event[2] + "," + event[3];
					}
				}
			}
			assertNotNull(kind + " on a fresh thread dispatched no " + (kind == Kind.RANDOM ? "MOVED" : "EXITED")
				+ ", so its target could not be observed", target);
			return target;
		}

		private void call(Kind kind)
		{
			switch (kind)
			{
				case OFF_SCREEN:
					naturalMouse.moveOffScreen();
					break;
				case OFF_SCREEN_WITH_CHANCE:
					// Below 100, so the chance roll runs first; 1 in a billion it declines to move.
					naturalMouse.moveOffScreen(99.9999999);
					break;
				default:
					naturalMouse.moveRandom();
					break;
			}
		}

		private static void resetPointer()
		{
			// reset() also clears the outside flag a previous exit left, so the next exit is dispatched.
			PointerState.reset();
			PointerState.setFromBot(CANVAS_W / 2, CANVAS_H / 2);
		}

		private static Object defaultFor(Class<?> type)
		{
			if (type == boolean.class)
			{
				return false;
			}
			if (type == int.class)
			{
				return 0;
			}
			if (type == short.class)
			{
				return (short) 0;
			}
			if (type == byte.class)
			{
				return (byte) 0;
			}
			if (type == char.class)
			{
				return (char) 0;
			}
			if (type == long.class)
			{
				return 0L;
			}
			if (type == float.class)
			{
				return 0f;
			}
			if (type == double.class)
			{
				return 0d;
			}
			return null;
		}
	}

	private static Object readStatic(String name) throws Exception
	{
		Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(null);
	}

	private static void writeStatic(String name, Object value) throws Exception
	{
		Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}
}
