package net.runelite.client.plugins.microbot;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import net.runelite.api.ChatMessageType;

public class GameChatAppender extends AppenderBase<ILoggingEvent> {
    public static final String DETAILED_PATTERN = "%d{HH:mm:ss} [%thread] %-5level %logger{36} - %msg%ex{0}%n";
    public static final String SIMPLE_PATTERN = "[%d{HH:mm:ss}] %msg%ex{0}%n";

    private final PatternLayout layout = new PatternLayout();

    public GameChatAppender(String pattern) {
        addFilter(new DebugFilter());
        // filter for log messages from the microbot folder
        // addFilter(new OnlyMicrobotLoggingFilter());
        layout.setPattern(pattern);
    }

    public GameChatAppender() {
        this(SIMPLE_PATTERN);
    }

    @Override
    public void setContext(Context context) {
        super.setContext(context);
        layout.setContext(context);
    }

    @Override
    public void start() {
        layout.start();
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        layout.stop();
    }

    public void setPattern(String pattern) {
        final boolean started = layout.isStarted();
        if (started) layout.stop();
        layout.setPattern(pattern);
        if (started) layout.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!Microbot.isLoggedIn()) return;

        final String formatted = layout.doLayout(event);
        // use invoke so we don't stall the calling thread
        Microbot.getClientThread().invoke(() ->
                Microbot.getClient().addChatMessage(ChatMessageType.ENGINE, "", formatted, "", false)
        );
    }

    private static class DebugFilter extends Filter<ILoggingEvent> {
        @Override
        public FilterReply decide(ILoggingEvent event) {
            return event.getLevel() == Level.DEBUG && !Microbot.isDebug() ? FilterReply.DENY : FilterReply.ACCEPT;
        }
    }

    private static class OnlyMicrobotLoggingFilter extends Filter<ILoggingEvent> {
        @Override
        public FilterReply decide(ILoggingEvent event) {
            return event.getLoggerName().startsWith("net.runelite.client.plugins.microbot") ? FilterReply.ACCEPT : FilterReply.DENY;
        }
    }
}
