package net.runelite.client.plugins.microbot.autoBuyer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Percentage {
    PERCENT_5("+5%"),
    PERCENT_10("+10%");

    private final String name;

    @Override
    public String toString() {
        return name;
    }
}
