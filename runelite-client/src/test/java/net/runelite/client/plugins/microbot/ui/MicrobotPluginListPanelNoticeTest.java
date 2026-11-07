package net.runelite.client.plugins.microbot.ui;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MicrobotPluginListPanelNoticeTest {
    @Test
    public void noticeListsEachBlockedPluginWithEscapedReason() {
        Map<String, String> blocked = new LinkedHashMap<>();
        blocked.put("AutoMiningPlugin", "version 1.0.12 is confirmed broken upstream: <stalls>");
        blocked.put("UnknownPlugin", "disabled upstream");

        String message = MicrobotPluginListPanel.blockedPluginsMessage(blocked,
                name -> name.equals("AutoMiningPlugin") ? "Auto Mining" : name);

        assertEquals("<html>These Microbot Plugin Hub plugins were not loaded:<br>"
                + "<br>&bull; <b>Auto Mining</b>: version 1.0.12 is confirmed broken upstream: &lt;stalls&gt;"
                + "<br>&bull; <b>UnknownPlugin</b>: disabled upstream"
                + "<br><br>Open the <strong>Microbot Plugin Hub</strong> to see details, remove them or install an unaffected version. For a JAR you added yourself, fix or remove the JAR in <strong>Local Plugins</strong>.</html>",
                message);
    }
}
