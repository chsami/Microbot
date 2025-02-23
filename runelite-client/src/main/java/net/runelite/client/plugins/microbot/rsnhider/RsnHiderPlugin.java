package net.runelite.client.plugins.microbot.rsnhider;/*
 * Copyright (c) 2020, ThatGamerBlue <thatgamerblue@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

import javax.inject.Inject;

/*
Mental breakdown 2: electric boogaloo

Alexa, play sea shanty two.
Peace to:
	r189, he.cc
*/
@PluginDescriptor(
        name = PluginDescriptor.LT + "Cambia tu nombre",
        description = "Oculta tu rsn para streamers.",
        tags = {"twitch"},
        enabledByDefault = false
)
public class RsnHiderPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private RsnHiderConfig config;

    private String fakeRsn;
    private boolean forceUpdate = false;

    private static final String ALPHA_NUMERIC_STRING = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @Provides
    private RsnHiderConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(RsnHiderConfig.class);
    }

    @Override
    public void startUp() {
        setFakeRsn();
    }

    @Override
    public void shutDown() {
        clientThread.invokeLater(() -> client.runScript(ScriptID.CHAT_PROMPT_INIT));
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("rsnhider")) {
            return;
        }

        setFakeRsn();
    }

    @Subscribe
    private void onBeforeRender(BeforeRender event) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        if (config.hideWidgets()) {
            // do every widget
            for (Widget widgetRoot : client.getWidgetRoots()) {
                processWidget(widgetRoot);
            }
        } else {
            // just do the chatbox
            updateChatbox();
        }
    }

    private void setFakeRsn() {
        forceUpdate = true;
        fakeRsn = config.customRsn().equals("") ? randomAlphaNumeric(12) : config.customRsn();
    }

    /**
     * Recursively traverses widgets looking for text containing the players name, replacing it if necessary
     *
     * @param widget The root widget to process
     */
    private void processWidget(Widget widget) {
        if (widget == null) {
            return;
        }

        if (widget.getText() != null) {
            widget.setText(replaceRsn(widget.getText()));
        }

        for (Widget child : widget.getStaticChildren()) {
            processWidget(child);
        }

        for (Widget dynamicChild : widget.getDynamicChildren()) {
            processWidget(dynamicChild);
        }

        for (Widget nestedChild : widget.getNestedChildren()) {
            processWidget(nestedChild);
        }
    }

    private void updateChatbox() {
        Widget chatboxTypedText = client.getWidget(WidgetInfo.CHATBOX_INPUT);
        if (chatboxTypedText == null || chatboxTypedText.isHidden()) {
            return;
        }
        String[] chatbox = chatboxTypedText.getText().split(":", 2);

        //noinspection ConstantConditions
        String playerRsn = client.getLocalPlayer().getName();
        if (forceUpdate || Text.standardize(chatbox[0]).contains(Text.standardize(playerRsn))) {
            chatbox[0] = fakeRsn;
        }

        forceUpdate = false;
        chatboxTypedText.setText(chatbox[0] + ":" + (chatbox.length > 1 ? chatbox[1] : ""));
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        //noinspection ConstantConditions
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) {
            return;
        }

        String replaced = replaceRsn(event.getMessage());
        event.setMessage(replaced);
        event.getMessageNode().setValue(replaced);

        if (event.getName() == null) {
            return;
        }

        boolean isLocalPlayer =
                Text.standardize(event.getName()).equalsIgnoreCase(Text.standardize(client.getLocalPlayer().getName()));

        if (isLocalPlayer) {
            event.setName(fakeRsn);
            event.getMessageNode().setName(fakeRsn);
        }
    }

    @Subscribe
    private void onOverheadTextChanged(OverheadTextChanged event) {
        event.getActor().setOverheadText(replaceRsn(event.getOverheadText()));
    }

    private String replaceRsn(String textIn) {
        //noinspection ConstantConditions
        String playerRsn = client.getLocalPlayer().getName();
        String standardized = Text.standardize(playerRsn);
        while (Text.standardize(textIn).contains(standardized)) {
            int idx = textIn.replace("\u00A0", " ").toLowerCase().indexOf(playerRsn.toLowerCase());
            int length = playerRsn.length();
            String partOne = textIn.substring(0, idx);
            String partTwo = textIn.substring(idx + length);
            textIn = partOne + fakeRsn + partTwo;
        }
        return textIn;
    }

    private static String randomAlphaNumeric(int count) {
        StringBuilder builder = new StringBuilder();
        int i = count;
        while (i-- != 0) {
            int character = (int) (Math.random() * ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }
}