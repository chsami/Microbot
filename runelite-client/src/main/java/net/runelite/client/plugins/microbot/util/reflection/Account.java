package net.runelite.client.plugins.microbot.util.reflection;

import lombok.Getter;

public class Account {
    @Getter
    String sessionId;
    @Getter
    String accountId;
    @Getter
    String displayName;

    public Account(String sessionId, String accountId, String displayName) {
        this.sessionId = sessionId;
        this.accountId = accountId;
        this.displayName = displayName;
    }
}
