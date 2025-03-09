package net.runelite.client.plugins.microbot.autoBuyer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Percentage {
    PERCENT_5("+5%", 5),
    PERCENT_10("+10%", 10),
    PERCENT_50("+50%", 50),
    PERCENT_99("+99%", 99); // GE does not accept higher value

    private final String name;
    private final int value;

    @Override
    public String toString() {
        return name;
    }
}
