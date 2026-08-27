package util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal stderr logger for failures that must not terminate the bot.
 *
 * <p>The bot has no logging framework. sc-docker and the SSCAI tournament module capture the JVM's stderr into
 * {@code logs_0/bot.log}, so writing there is what makes a suppressed failure visible after the fact. Every line is
 * prefixed with {@link #PREFIX} so the batch harness can find them.
 *
 * <p>A failure inside a per-frame handler recurs every frame, so identical failures are deduplicated: the first
 * occurrence logs a full stack trace and later occurrences log a single counted line every {@link #REPEAT_INTERVAL}
 * hits. Deduplication is keyed on the context, the throwable type and the throwing frame.
 */
public final class BotLogger {

    /** Marker prefix on every line, so logs can be scanned for suppressed failures. */
    public static final String PREFIX = "[BOT-ERROR] ";

    private static final long REPEAT_INTERVAL = 500;

    private static final ConcurrentMap<String, AtomicLong> OCCURRENCES = new ConcurrentHashMap<>();

    private BotLogger() {}

    /**
     * Logs a failure that was caught and suppressed.
     *
     * @param context where the failure happened, typically the event handler name.
     * @param throwable the suppressed failure.
     */
    public static void error(String context, Throwable throwable) {
        if (throwable == null) {
            error(context, "failed with no throwable");
            return;
        }
        long occurrences = OCCURRENCES.computeIfAbsent(signature(context, throwable), key -> new AtomicLong()).incrementAndGet();
        if (occurrences == 1) {
            System.err.println(PREFIX + context + ": " + throwable);
            throwable.printStackTrace(System.err);
            System.err.flush();
            return;
        }
        if (occurrences % REPEAT_INTERVAL == 0) {
            System.err.println(PREFIX + context + ": " + throwable + " (repeated " + occurrences + " times)");
            System.err.flush();
        }
    }

    /**
     * Logs a failure that has no throwable to report.
     *
     * @param context where the failure happened.
     * @param message what went wrong.
     */
    public static void error(String context, String message) {
        System.err.println(PREFIX + context + ": " + message);
        System.err.flush();
    }

    private static String signature(String context, Throwable throwable) {
        StackTraceElement[] frames = throwable.getStackTrace();
        String origin = frames.length > 0 ? frames[0].toString() : "unknown";
        return context + "|" + throwable.getClass().getName() + "|" + origin;
    }
}
