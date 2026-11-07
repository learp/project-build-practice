#pragma once
// Агрегация разобранного лога — аналог LogStats из Java-версии.
// Сколько строк на каждом уровне и какие ошибки встречаются чаще всего.

#include "logstats/log_parser.hpp"

#include <cstddef>
#include <map>
#include <string>
#include <vector>

namespace logstats {

struct TopMessage {
    std::string message;
    long count;
};

class LogStats {
public:
    // Сколько строк на каждом уровне. Ключи — только встречающиеся уровни,
    // порядок — алфавитный (std::map), а не порядок enum как в Java.
    std::map<std::string, long> byLevel(const std::vector<LogEntry>& entries) const;

    // Топ-N частых инцидентов (ERROR и WARN): отпечаток — первое значащее
    // слово сообщения (артикли a/an/the пропускаются).
    std::vector<TopMessage> topErrors(const std::vector<LogEntry>& entries,
                                      std::size_t limit = 5) const;

    // "TimeoutException while calling bank-api" -> "TimeoutException"
    static std::string fingerprint(const std::string& message);
};

} // namespace logstats
