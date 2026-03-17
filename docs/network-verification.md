# Network Verification

## Allowed outbound connections
1. Jagex game servers (*.jagex.com, *.runescape.com)
2. localhost (127.0.0.1 — Status API, future)
3. RuneLite cache (repo.runelite.net — map data, item icons)
4. GE price APIs (prices.runescape.wiki, ge-tracker.com — public, no auth)
5. IP echo services (api4.my-ip.io, ipv4.icanhazip.com — for proxy verification)

## How to verify
1. Build the shaded JAR: `./gradlew :runelite-client:build -x test`
2. Launch with Wireshark/tcpdump capturing
3. Confirm only the above destinations appear
4. Check for absence of: microbot.cloud, discord.com, any analytics domains, openai.com

## Audit history
- 2026-03-17: Initial audit — see docs/security-audit.md
  - Removed: MicrobotVersionChecker, RandomFactClient, MicrobotApi (all phoned home to microbot.cloud)
  - Replaced: ProxyChecker IP echo (microbot.cloud → neutral public services)
  - Kept: Rs2Discord (user-configured webhooks), Rs2GrandExchange (public price APIs)
