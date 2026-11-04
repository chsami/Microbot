package net.runelite.client.plugins.microbot.diagnostics;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.gpu.GpuPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
@Singleton
public class DiagnosticReportCollector
{
	private final Client client;
	private final ConfigManager configManager;
	private final PluginManager pluginManager;
	private final MicrobotPluginManager microbotPluginManager;
	private final boolean safeMode;

	@Inject
	DiagnosticReportCollector(Client client, ConfigManager configManager, PluginManager pluginManager,
		MicrobotPluginManager microbotPluginManager, @Named("safeMode") boolean safeMode)
	{
		this.client = client;
		this.configManager = configManager;
		this.pluginManager = pluginManager;
		this.microbotPluginManager = microbotPluginManager;
		this.safeMode = safeMode;
	}

	public String collectReport()
	{
		return DiagnosticReport.render(collect());
	}

	DiagnosticSnapshot collect()
	{
		DiagnosticSnapshot.DiagnosticSnapshotBuilder builder = DiagnosticSnapshot.builder()
			.microbotVersion(RuneLiteProperties.getMicrobotVersion())
			.runeliteVersion(RuneLiteProperties.getVersion())
			.microbotCommit(RuneLiteProperties.getMicrobotCommit())
			.sourceCommit(RuneLiteProperties.getCommit())
			.sourceDirty(RuneLiteProperties.isDirty())
			.buildChannel(RuneLiteProperties.getMicrobotBuildChannel())
			.buildRepository(RuneLiteProperties.getMicrobotBuildRepository())
			.launcherVersion(RuneLiteProperties.getLauncherVersion())
			.javaVersion(System.getProperty("java.version"))
			.javaVendor(System.getProperty("java.vendor"))
			.osName(System.getProperty("os.name"))
			.osArch(System.getProperty("os.arch"))
			.safeMode(safeMode)
			.walker(Rs2Walker.class.getSimpleName() + " (built into client)")
			.mouse(Microbot.getMouse() == null ? null : Microbot.getMouse().getClass().getSimpleName())
			.naturalMouse(Rs2AntibanSettings.naturalMouse);

		try
		{
			builder.gameRevision(client.getRevision());
			builder.gpuRendererActive(client.isGpu());
		}
		catch (RuntimeException e)
		{
			log.debug("Diagnostics could not read client state", e);
		}

		pluginManager.getPlugins().stream()
			.filter(GpuPlugin.class::isInstance)
			.findFirst()
			.ifPresent(gpu -> builder.gpuPluginEnabled(pluginManager.isPluginEnabled(gpu)));

		try
		{
			ShortestPathConfig walker = configManager.getConfig(ShortestPathConfig.class);
			MicrobotConfig microbot = configManager.getConfig(MicrobotConfig.class);
			builder.plannerMode(String.valueOf(walker.plannerSelectionMode()))
				.walkerSetting("autoRun", onOff(microbot.enableAutoRunOn()))
				.walkerSetting("staminaPots", onOff(microbot.useStaminaPotsIfNeeded()))
				.walkerSetting("inputYielding", onOff(!microbot.disableInputYielding()))
				.walkerSetting("bankedTransports", onOff(walker.walkWithBankedTransports()))
				.walkerSetting("minBankRouteSavings", String.valueOf(walker.minBankRouteSavings()))
				.walkerSetting("preferTransportToTarget", onOff(walker.preferTransportToTarget()))
				.walkerSetting("obstaclesAtRange", onOff(walker.interactWithRouteObstaclesAtRange()))
				.walkerSetting("randomizeFinalTile", onOff(walker.randomizeFinalTile()))
				.walkerSetting("liveCollision", onOff(walker.useLiveCollision()))
				.walkerSetting("avoidWilderness", onOff(walker.avoidWilderness()))
				.walkerSetting("teleportItems", String.valueOf(walker.useTeleportationItems()))
				.walkerSetting("teleportSpells", onOff(walker.useTeleportationSpells()))
				.walkerSetting("agilityShortcuts", onOff(walker.useAgilityShortcuts()))
				.walkerSetting("teleportDistance", String.valueOf(walker.distanceBeforeUsingTeleport()))
				.walkerSetting("recalculateDistance", String.valueOf(walker.recalculateDistance()))
				.walkerSetting("finishDistance", String.valueOf(walker.reachedDistance()))
				.walkerSetting("calculationCutoff", String.valueOf(walker.calculationCutoff()));
		}
		catch (RuntimeException e)
		{
			log.debug("Diagnostics could not read walker settings", e);
		}

		builder.plugins(collectPlugins());
		return builder.build();
	}

	private List<DiagnosticSnapshot.Plugin> collectPlugins()
	{
		return microbotPluginManager.getInstalledPlugins().stream()
			.map(this::describe)
			.sorted(Comparator.comparing(DiagnosticSnapshot.Plugin::getInternalName, String.CASE_INSENSITIVE_ORDER))
			.collect(Collectors.toList());
	}

	private DiagnosticSnapshot.Plugin describe(Plugin plugin)
	{
		PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
		String internalName = plugin.getClass().getSimpleName();
		MicrobotPluginManifest manifest = microbotPluginManager.getManifestMap().get(internalName);
		String installedVersion = microbotPluginManager.getInstalledPluginVersion(internalName).orElse(null);
		return DiagnosticSnapshot.Plugin.builder()
			.name(descriptor.name())
			.internalName(internalName)
			.descriptorVersion(descriptor.version())
			.installedVersion(installedVersion)
			.latestVersion(manifest == null ? null : manifest.getVersion())
			.hubInstalled(microbotPluginManager.isHubInstalled(internalName))
			.active(pluginManager.isPluginActive(plugin))
			.build();
	}

	private static String onOff(boolean value)
	{
		return value ? "on" : "off";
	}
}
