package net.runelite.client.plugins.microbot.github.models;

public class FileInfo {
    private final String name;
    private final String url;

    public FileInfo(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return name; // this is what gets shown in the JList
    }
}
