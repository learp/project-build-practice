package best.project.ever;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LogStats {

    private static final Set<String> FINGERPRINT_STOPWORDS = Set.of("a", "an", "the");
    private static final int FINGERPRINT_WORDS = 4;

    public Stats summarize(List<LogParser.LogEntry> entries, int skipped, int topLimit) {
        return new Stats(entries.size(), skipped,
                byLevel(entries), topErrors(entries, topLimit));
    }

    public Map<LogParser.Level, Long> byLevel(List<LogParser.LogEntry> entries) {
        return entries.stream().collect(Collectors.groupingBy(
                LogParser.LogEntry::level,
                () -> new EnumMap<>(LogParser.Level.class),
                Collectors.counting()));
    }

    public List<TopMessage> topErrors(List<LogParser.LogEntry> entries, int limit) {
        return entries.stream()
                .filter(e -> e.level() == LogParser.Level.ERROR || e.level() == LogParser.Level.WARN)
                .map(e -> fingerprint(e.message()))
                .collect(Collectors.groupingBy(m -> m, Collectors.counting()))
                .entrySet().stream()
                .map(en -> new TopMessage(en.getKey(), en.getValue()))
                .sorted(Comparator.comparingLong(TopMessage::count).reversed()
                        .thenComparing(TopMessage::message))
                .limit(limit)
                .toList();
    }

    static String fingerprint(String message) {
        StringBuilder fingerprint = new StringBuilder();
        int words = 0;
        for (String word : message.split(" ")) {
            if (word.contains("=") || word.startsWith("[")) {
                continue; // key=value-обрывки и [cid=...]-идентификаторы — мусор
            }
            String cleaned = word.replaceAll("[^\\w$.]", "");
            if (cleaned.isEmpty() || FINGERPRINT_STOPWORDS.contains(cleaned.toLowerCase())) {
                continue;
            }
            if (words > 0) {
                fingerprint.append(' ');
            }
            fingerprint.append(cleaned);
            if (++words == FINGERPRINT_WORDS) {
                break;
            }
        }
        return words > 0 ? fingerprint.toString() : message;
    }

    public record Stats(long parsed, long skipped,
                        Map<LogParser.Level, Long> byLevel,
                        List<TopMessage> topErrors) {
    }

    public record TopMessage(String message, long count) {
    }
}
