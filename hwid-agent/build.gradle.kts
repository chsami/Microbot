plugins {
    `java-library`
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
    options.encoding = "UTF-8"
}

dependencies {
    compileOnly(libs.asm.core)
    compileOnly(libs.asm.tree)
    compileOnly(libs.asm.commons)

    testImplementation(libs.asm.core)
    testImplementation(libs.asm.tree)
    testImplementation(libs.asm.commons)
    testImplementation(libs.junit)
    testImplementation("net.runelite:injected-client:${providers.gradleProperty("runelite.injected-client.version").get()}")
}

val historicalClients: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    file("historical-clients.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
        historicalClients("net.runelite:injected-client:$it")
    }
}

tasks.test {
    inputs.files(historicalClients)
    // Tests never contact RuneLite; StockSignerHashTest drives the lookup with a local remote.
    systemProperty("microbot.hwid.signer.lookup", "false")
    // PlatformScanTest calls the scan directly; other tests must not start it from a transformer call.
    systemProperty("microbot.hwid.platform.scan", "false")
    systemProperty("microbot.hwid.signer.store", layout.buildDirectory.file("tmp/stock-signer-test.properties").get().asFile.path)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dhwid.historicalClients=" + historicalClients.files.joinToString(File.pathSeparator))
    })
}

tasks.jar {
    manifest {
        attributes["Premain-Class"] = "com.microbot.hwid.HwidAgent"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
    }
    archiveBaseName = "hwid-agent"
    archiveVersion = providers.gradleProperty("microbot.version").orElse(providers.gradleProperty("microbot.commit.sha")).orElse("0.0.0").get()
}

tasks.register<JavaExec>("obfuscationReport") {
    group = "verification"
    description = "Locate the obfuscated names the agent hooks in the current injected-client and rewrite the generated values in docs. Re-run when the injected-client dependency bumps."

    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.microbot.hwid.ObfuscationReport")
    args(
        rootProject.file("docs/DETECTION_HARDENING.md").absolutePath,
        rootProject.file("docs/obfuscation/injected-getters.tsv").absolutePath,
        rootProject.file("runelite-client/src/main/resources/net/runelite/client/plugins/microbot/util/reflection/menu-action-info.properties").absolutePath
    )
    mustRunAfter(":client:seedMenuActionInfo")
}
