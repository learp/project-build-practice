package best.project.ever;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.atInfo().log("Запускаемся: log-stats без системы сборки");

        if (args.length < 1 || args[0].isBlank()) {
            log.atWarn().log("Укажите файл лога: log-stats <file.log> [--top N]");
            return;
        }
        Path file = Path.of(args[0]);

        int top = 5;
        if (args.length >= 3 && "--top".equals(args[1])) {
            top = Integer.parseInt(args[2]);
        } else if (args.length > 1) {
            log.atWarn().log("Не понял аргумент '{}', доступно: <file.log> [--top N]", args[1]);
        }

        LogParser.ParseResult result = parseLogFile(file);
        LogStats.Stats stats = new LogStats().summarize(result.entries(), result.skipped(), top);

        printLogSummary(file, stats);
        printJsonReport(file, stats);
    }

    private static LogParser.ParseResult parseLogFile(Path file) {
        LogParser.ParseResult result;
        try {
            result = new LogParser().parse(file);
        } catch (Exception e) {
            log.atError().log("Не удалось прочитать {}", file, e);
            throw new RuntimeException(e);
        }
        return result;
    }

    private static void printJsonReport(Path file, LogStats.Stats stats) {
        try {
            new JsonReportWriter().write(Report.from(file.toString(), stats), Path.of("report.json"));
            log.atInfo().log("Отчёт: report.json");
        } catch (Exception e) {
            log.atError().log("Не удалось записать report.json", e);
        }
    }

    private static void printLogSummary(Path file, LogStats.Stats stats) {
        StringBuilder summary = new StringBuilder();
        summary.append("Файл:    ").append(file).append('\n');
        summary.append("Строк:   ").append(stats.parsed() + stats.skipped())
                .append(" (разобрано ").append(stats.parsed())
                .append(", пропущено ").append(stats.skipped()).append(")\n");
        summary.append("Уровни:  ");
        for (Map.Entry<LogParser.Level, Long> e : stats.byLevel().entrySet()) {
            summary.append(String.format("%-5s %3d   ", e.getKey(), e.getValue()));
        }
        if (!stats.topErrors().isEmpty()) {
            summary.append("\nТоп ошибок (ERROR/WARN):\n");
            int i = 1;
            for (LogStats.TopMessage t : stats.topErrors()) {
                summary.append(String.format("  %d. %-40s x%d%n", i++, t.message(), t.count()));
            }
        }
        log.atInfo().log(summary.toString().stripTrailing());
    }
}
