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
    private final PatternLayout layout = new PatternLayout();
    
    // Cache the current configuration to avoid config lookups during filtering
    private static volatile boolean loggingEnabled = true;
    private static volatile Level minimumLevel = Level.WARN;
    private static volatile boolean onlyMicrobotLogging = true;

    public GameChatAppender(String pattern) {
        // Order matters! Level filter should run first to deny based on log level
        addFilter(new GameChatLevelFilter());
        addFilter(new OnlyMicrobotLoggingFilter());
        layout.setPattern(pattern);
    }

    public GameChatAppender() {
        this("[%d{HH:mm:ss}] %msg%ex{0}%n"); // Default simple pattern
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
    
    /**
     * Updates the cached configuration for filtering
     */
    public static void updateConfiguration(boolean enabled, Level level, boolean microbotOnly) {
        loggingEnabled = enabled;
        minimumLevel = level;
        onlyMicrobotLogging = microbotOnly;
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

    /**
     * Filter to control which log levels appear in game chat based on configuration
     */
    private static class GameChatLevelFilter extends Filter<ILoggingEvent> {
        @Override
        public FilterReply decide(ILoggingEvent event) {
            // Check if logging is enabled
            if (!loggingEnabled) {
                return FilterReply.DENY;
            }
            
            // In debug mode, show all levels (overrides configuration)
            if (Microbot.isDebug()) {
                return FilterReply.NEUTRAL;
            }
            
            // Use cached minimum level to filter (includes DEBUG if configured)
            return event.getLevel().isGreaterOrEqual(minimumLevel) ? FilterReply.NEUTRAL : FilterReply.DENY;
        }
    }

    private static class OnlyMicrobotLoggingFilter extends Filter<ILoggingEvent> {
        @Override
        public FilterReply decide(ILoggingEvent event) {
            // If only microbot logging is disabled, accept all logs
            if (!onlyMicrobotLogging) {
                return FilterReply.ACCEPT;
            }
            
            // Otherwise, only accept microbot logs
            return event.getLoggerName().startsWith("net.runelite.client.plugins.microbot") ? FilterReply.ACCEPT : FilterReply.DENY;
        }
    }
}
