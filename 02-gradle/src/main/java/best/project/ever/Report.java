package best.project.ever;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Report {

    private final String file;
    private final long totalLines;
    private final long parsed;
    private final long skipped;
    private final Map<String, Long> byLevel;
    private final List<TopError> topErrors;

    public Report(String file, long totalLines, long parsed, long skipped,
                  Map<String, Long> byLevel, List<TopError> topErrors) {
        this.file = file;
        this.totalLines = totalLines;
        this.parsed = parsed;
        this.skipped = skipped;
        this.byLevel = byLevel;
        this.topErrors = topErrors;
    }

    public static Report from(String file, LogStats.Stats stats) {
        Map<String, Long> byLevel = new LinkedHashMap<>();
        stats.byLevel().forEach((level, count) -> byLevel.put(level.name(), count));

        List<TopError> topErrors = new ArrayList<>();
        for (LogStats.TopMessage t : stats.topErrors()) {
            topErrors.add(new TopError(t.message(), t.count()));
        }

        return new Report(file, stats.parsed() + stats.skipped(),
                stats.parsed(), stats.skipped(), byLevel, topErrors);
    }

    public static class TopError {

        private final String message;
        private final long count;

        public TopError(String message, long count) {
            this.message = message;
            this.count = count;
        }
    }
}
