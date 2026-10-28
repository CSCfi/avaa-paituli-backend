package fi.csc.avaa.paituli;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.jboss.logmanager.ExtLogRecord;

// Collects what a class logs, so tests can assert on log lines. Unit tests
// run with the JBoss log manager (see the surefire config in pom.xml), so
// records arrive as ExtLogRecords, which format the printf-style messages.
public class LogCapture extends Handler implements AutoCloseable {

    // Level is the JBoss level name: "DEBUG", "INFO", "WARN" or "ERROR"
    public record Line(String level, String message, Throwable thrown) {}

    private final Logger logger;
    private final List<Line> lines = new CopyOnWriteArrayList<>();

    private LogCapture(Logger logger) {
        this.logger = logger;
    }

    // Starts collecting what the class logs, until close() is called
    public static LogCapture of(Class<?> loggingClass) {
        LogCapture capture = new LogCapture(Logger.getLogger(loggingClass.getName()));
        capture.logger.addHandler(capture);
        return capture;
    }

    public List<Line> lines() {
        return lines;
    }

    @Override
    public void publish(LogRecord record) {
        String message = record instanceof ExtLogRecord ext
                ? ext.getFormattedMessage()
                : record.getMessage();
        lines.add(new Line(record.getLevel().getName(), message, record.getThrown()));
    }

    @Override
    public void flush() {}

    @Override
    public void close() {
        logger.removeHandler(this);
    }
}
