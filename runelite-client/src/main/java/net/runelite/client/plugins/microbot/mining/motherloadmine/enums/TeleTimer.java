package net.runelite.client.plugins.microbot.mining.motherloadmine.enums;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TeleTimer {
  HOME_TELEPORT("Home Teleport", Duration.ofMinutes(30)),
  MINIGAME_TELEPORT("Minigame Teleport", Duration.ofMinutes(20));

  private final String description;
  private final Duration duration;
}
