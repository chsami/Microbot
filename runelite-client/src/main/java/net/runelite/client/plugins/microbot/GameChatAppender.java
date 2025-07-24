package net.runelite.client.plugins.microbot;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class GameChatAppender extends AppenderBase<ILoggingEvent> {
    private final PatternLayout layout = new PatternLayout();
    
    // Cache the current configuration to avoid config lookups during filtering
    @Getter
    private final List<String> whitelist = new CopyOnWriteArrayList<>();
    @Getter
    private final List<String> blacklist = new CopyOnWriteArrayList<>();
    private static volatile Level minimumLevel = Level.WARN;

    public GameChatAppender(String pattern) {
        // Order matters! Level filter should run first to deny based on log level
        addFilter(new GameChatLevelFilter());
        addFilter(new PathFilter(whitelist, FilterReply.NEUTRAL, FilterReply.DENY));
        addFilter(new PathFilter(blacklist, FilterReply.DENY, FilterReply.NEUTRAL));
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
        log.info("Started GameChat Appender");
    }

    @Override
    public void stop() {
        super.stop();
        layout.stop();
        log.info("Stopped GameChat Appender");
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
    public static void updateConfiguration(Level level) {
        minimumLevel = level;
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
            // In debug mode, show all levels (overrides configuration)
            if (Microbot.isDebug()) {
                return FilterReply.NEUTRAL;
            }
            
            // Use cached minimum level to filter (includes DEBUG if configured)
            return event.getLevel().isGreaterOrEqual(minimumLevel) ? FilterReply.NEUTRAL : FilterReply.DENY;
        }
    }

    @AllArgsConstructor
    private static class PathFilter extends Filter<ILoggingEvent> {
        private final List<String> list;
        private final FilterReply match;
        private final FilterReply noMatch;

        @Override
        public FilterReply decide(ILoggingEvent event) {
            if (list.isEmpty()) return FilterReply.NEUTRAL;
            return list.stream().anyMatch(path -> event.getLoggerName().startsWith(path)) ? match : noMatch;
        }
    }
}
