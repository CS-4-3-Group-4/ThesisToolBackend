package cs43.group4.utils;

/**
 * A singleton console logger that supports different log levels and colored output.
 * <p>
 * This logger prints messages to the console with colors corresponding to their
 * severity level. Messages with the level {@link Level#ERROR} are printed to
 * {@link System#err}, while all other messages are printed to {@link System#out}.
 * </p>
 * <p>
 * Log levels are ordered as follows (from lowest to highest severity):
 * {@link Level#DEBUG}, {@link Level#INFO}, {@link Level#WARN}, {@link Level#ERROR}, {@link Level#OFF}.
 * Only messages with a level equal to or higher than the current log level are printed.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * SimpleLogger.setLevel(SimpleLogger.Level.DEBUG);
 * SimpleLogger.info("Application started");
 * SimpleLogger.error("An error occurred: %s", errorMessage);
 * </pre>
 * </p>
 */
public class Log {

    /**
     * Represents the logging level of the {@link Log}.
     */
    public enum Level {
        /** Debug-level messages, usually used for development or troubleshooting. */
        DEBUG,
        /** Informational messages about normal operations. */
        INFO,
        /** Warning messages indicating potential issues or important notices. */
        WARN,
        /** Error messages indicating failures or serious problems. */
        ERROR,
        /** No logging will occur if this level is set. */
        OFF
    }

    // ANSI escape codes for colors
    private static final String RESET = "\u001B[0m";
    private static final String GRAY = "\u001B[90m";
    private static final String BLUE = "\u001B[34m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";

    private static Level currentLevel = Level.DEBUG;

    // Private constructor to prevent instantiation
    private Log() {
        throw new UnsupportedOperationException("Log is a singleton and cannot be instantiated");
    }

    /**
     * Sets the global log level.
     *
     * @param level the new log level
     */
    public static void setLevel(Level level) {
        currentLevel = level;
    }

    /**
     * Gets the current global log level.
     *
     * @return the current log level
     */
    public static Level getLevel() {
        return currentLevel;
    }

    /**
     * Checks whether a message with the given level should be logged.
     *
     * @param level the log level of the message
     * @return true if the message should be logged, false otherwise
     */
    private static boolean shouldLog(Level level) {
        return currentLevel != Level.OFF && level.ordinal() >= currentLevel.ordinal();
    }

    /**
     * Returns the ANSI color code associated with a log level.
     *
     * @param level the log level
     * @return the ANSI color code as a {@link String}
     */
    private static String colorForLevel(Level level) {
        return switch (level) {
            case DEBUG -> GRAY;
            case INFO -> BLUE;
            case WARN -> YELLOW;
            case ERROR -> RED;
            default -> RESET;
        };
    }

    /**
     * Logs a message with the specified log level.
     *
     * @param level   the log level
     * @param message the message to log
     */
    private static void log(Level level, String message) {
        if (shouldLog(level)) {
            String color = colorForLevel(level);
            String output = String.format("%s[%s]%s %s", color, level.name(), RESET, message);
            if (level == Level.ERROR) {
                System.err.println(output);
            } else {
                System.out.println(output);
            }
        }
    }

    /**
     * Logs a formatted message with the specified log level.
     *
     * @param level  the log level
     * @param format the format string
     * @param args   the arguments for the format string
     */
    private static void logf(Level level, String format, Object... args) {
        if (shouldLog(level)) {
            log(level, String.format(format, args));
        }
    }

    /**
     * Turns logging off globally. No messages will be printed until logging is turned on again.
     */
    public static void turnOff() {
        currentLevel = Level.OFF;
    }

    /**
     * Turns logging on globally. Messages will be printed starting from the specified level.
     *
     * @param level the minimum level of messages to print
     */
    public static void turnOn(Level level) {
        currentLevel = level;
    }

    /**
     * Turns logging on globally using the default level {@link Level#INFO}.
     */
    public static void turnOn() {
        currentLevel = Level.INFO;
    }

    // Convenience methods

    /** Logs a debug message. */
    public static void debug(String msg) {
        log(Level.DEBUG, msg);
    }

    /** Logs a formatted debug message. */
    public static void debug(String fmt, Object... args) {
        logf(Level.DEBUG, fmt, args);
    }

    /** Logs an info message. */
    public static void info(String msg) {
        log(Level.INFO, msg);
    }

    /** Logs a formatted info message. */
    public static void info(String fmt, Object... args) {
        logf(Level.INFO, fmt, args);
    }

    /** Logs a warning message. */
    public static void warn(String msg) {
        log(Level.WARN, msg);
    }

    /** Logs a formatted warning message. */
    public static void warn(String fmt, Object... args) {
        logf(Level.WARN, fmt, args);
    }

    /** Logs an error message. */
    public static void error(String msg) {
        log(Level.ERROR, msg);
    }

    /** Logs a formatted error message. */
    public static void error(String fmt, Object... args) {
        logf(Level.ERROR, fmt, args);
    }
}
