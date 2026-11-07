#pragma once
// Парсер лог-файла — «ядро» приложения, аналог LogParser из Java-версии.
// Обычный C++, никакой магии сборки: сборку описывает CMakeLists.txt рядом,
// а не сам код. Формат строки — syslog-стиль, как его пишет прод:
//   Nov  7 12:12:10 signaler.dc.odkl.ru odnoklassniki-webrtc[213]: INFO  [o.w.t.w.Stream2] Closing session, error: null

#include <string>
#include <vector>

namespace logstats {

struct LogEntry {
    std::string timestamp;
    std::string level;    // ERROR | WARN | INFO | DEBUG | TRACE
    std::string logger;
    std::string message;
};

// Результат разбора файла: записи + счётчик пропущенных строк.
struct ParseResult {
    std::vector<LogEntry> entries;
    int skipped = 0;
};

class LogParser {
public:
    // Разобрать файл целиком. Нечитаемые строки (обрывки stack trace, мусор)
    // не роняют разбор: они пропускаются и считаются в result.skipped.
    // Бросает std::runtime_error, если файл не открылся.
    ParseResult parseFile(const std::string& path);

    // Разобрать одну строку; false — формат не узнан.
    static bool parseLine(const std::string& line, LogEntry& out);
};

} // namespace logstats
