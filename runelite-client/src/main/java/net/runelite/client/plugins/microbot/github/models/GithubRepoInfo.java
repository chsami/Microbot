package net.runelite.client.plugins.microbot.github.models;

import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GithubRepoInfo {
    @Getter
    private final String owner;
    @Getter
    private final String repo;

    public GithubRepoInfo(String url) {
        Pattern pattern = Pattern.compile("github\\.com/([^/]+)/([^/]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            String owner = matcher.group(1);
            String repo = matcher.group(2);
            this.owner = owner;
            this.repo = repo;
        } else {
            this.owner = "";
            this.repo = "";
        }
    }
}
