# Development

Local setup, build commands, and script authoring entry points.

## Prerequisites
- JDK 17+ for development. The code targets Java 11 bytecode where RuneLite does.
- Git and the included Gradle wrapper (`./gradlew`); no system Gradle needed.
- IntelliJ IDEA is recommended. Open the root `build.gradle.kts` as a Gradle project.

## Project Layout
- Core plugin: `runelite-client/src/main/java/net/runelite/client/plugins/microbot`
- Helpers/utilities: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util`
- Queryable API: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/api`
- Config UI: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/ui/MicrobotConfigPanel`
- Runtime agent server: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/agentserver`
- Composite build members: `cache`, `runelite-api`, `runelite-gradle-plugin`, `runelite-jshell`, and `:client`

## Build & Run
- Quick compile: `./gradlew :client:compileJava`
- Run client: `./gradlew :client:run`
- Full build, including included builds: `./gradlew buildAll`
- Clean everything: `./gradlew cleanAll`
- Unit tests: `./gradlew :client:runUnitTests`
- Live/integration tests: `./gradlew :client:runIntegrationTest` with a running client where required
- Shaded jar: `./gradlew :client:assemble`

## IDE Setup (IntelliJ)
1. Open the root `build.gradle.kts`.
2. Set Project SDK and Gradle JVM to JDK 17+.
3. Let IntelliJ import included builds.
4. Create a Gradle run configuration for `:client:run` when launching from the IDE.

## Developing Scripts
- Place new scripts inside the microbot plugin folder: `runelite-client/src/main/java/net/runelite/client/plugins/microbot`.
- Reusable helpers belong in `microbot/util`.
- Config UI goes in `microbot/ui/MicrobotConfigPanel`.
- Never instantiate caches or queryables directly. Use `Microbot.getRs2XxxCache().query()` or `.getStream()`.
- When a filter resolves live names or widget text, prefer the `*OnClientThread` terminal helpers.
- Use `runelite-client/src/main/java/net/runelite/client/plugins/microbot/AGENTS.md` for script/threading rules.
- Use `runelite-client/src/main/java/net/runelite/client/plugins/microbot/statemachine/AGENTS.md` for scripts with three or more phases.

## Guardrails
- Client-thread guardrail: `./gradlew :client:runUnitTests --tests net.runelite.client.plugins.microbot.threadsafety.ClientThreadGuardrailTest`
- Regenerate client-thread baseline only after reviewing the diff: `./gradlew :client:regenerateClientThreadGuardrailBaseline`
- Queryable terminal guardrail: `./gradlew :client:runUnitTests --tests net.runelite.client.plugins.microbot.threadsafety.QueryableTerminalGuardrailTest`
- Offline API lookup: `./microbot-cli ct <method>`

## Additional References
- Installation steps and launcher notes: `docs/installation.md`
- Client-wide script pause behavior and compatibility limits: `docs/manual-pause.md`
- API guide and examples: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/api`
- Example scripts: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/example/`
