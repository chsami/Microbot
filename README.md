![image](https://github.com/user-attachments/assets/7c08e053-c84f-41f8-bc97-f55130100419)

<a href="https://www.youtube.com/@themicrobot" target="_blank">
  <img src="https://img.shields.io/badge/YouTube-Subscribe-FF0000?style=for-the-badge&logo=youtube&logoColor=white">
</a>
<a href="https://themicrobot.com" target="_blank">
  <img src="https://img.shields.io/badge/Microbot-Website-0A66C2?style=for-the-badge&logo=google-chrome&logoColor=white">
</a>

# Microbot
Microbot is a RuneLite fork with an always-on Microbot plugin for learning, building, and running automation scripts.

This README is intentionally short. Durable details live in the docs below so high-level context does not rot when implementation details move.

## Start Here
- Install or run a release: `docs/installation.md`
- Set up a development environment: `docs/development.md`
- Understand the runtime: `docs/ARCHITECTURE.md`
- Build scripts safely: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/AGENTS.md`
- Use entity caches/queryables: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/QUERYABLE_API.md`
- Drive a running client: `docs/MICROBOT_CLI.md`

## Common Commands
- Compile client: `./gradlew :client:compileJava`
- Run unit tests: `./gradlew :client:runUnitTests`
- Build all projects: `./gradlew buildAll`
- Build shaded jar: `./gradlew :client:assemble`

## Code Map
- Microbot plugin and scripts: `runelite-client/src/main/java/net/runelite/client/plugins/microbot`
- Reusable helpers: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util`
- Queryable caches: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/api`
- Runtime agent tooling: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/agentserver`

## Discord
[![Discord Banner 1](https://discord.com/api/guilds/1087718903985221642/widget.png?style=banner1)](https://discord.gg/zaGrfqFEWE)

If you have any questions, please join our [Discord](https://discord.gg/zaGrfqFEWE) server. 
