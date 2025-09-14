package net.runelite.client.plugins.microbot.autoworldhopper.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorldMembershipFilter {
    FREE("Free Worlds Only"),
    MEMBERS("Members Worlds Only"),
    BOTH("Both Free and Members");

    private final String description;

    @Override
    public String toString() {
        return description;
    }
}
