package best.project.ever;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ожидаемый формат строки
 *   Nov  7 12:12:10 signaler.dc.odkl.ru odnoklassniki-webrtc[213]: INFO  [o.w.t.w.Stream2] Closing session, error: null
 */
public class LogParser {

    public record LogEntry(String timestamp, Level level, String logger, String message) {
    }

    public enum Level { ERROR, WARN, INFO, DEBUG, TRACE }

    public record ParseResult(List<LogEntry> entries, int skipped) {
    }

    private static final Pattern LINE = Pattern.compile(
            "^(\\w{3} +\\d{1,2} \\d{2}:\\d{2}:\\d{2}) (\\S+) (\\S+?): +(\\S+) +\\[([^]]*)] (.*)$");

    public ParseResult parse(Path file) throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        int skipped = 0;
        for (String line : Files.readAllLines(file)) {
            Optional<LogEntry> entry = parseLine(line);
            if (entry.isPresent()) {
                entries.add(entry.get());
            } else {
                skipped++;
            }
        }
        return new ParseResult(entries, skipped);
    }

    public Optional<LogEntry> parseLine(String line) {
        Matcher m = LINE.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        Level level;
        try {
            level = Level.valueOf(m.group(4));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.of(new LogEntry(m.group(1), level, m.group(5), m.group(6)));
    }
}
