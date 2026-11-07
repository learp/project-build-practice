#include "logstats/log_stats.hpp"

#include <algorithm>
#include <unordered_set>
#include <cctype>

namespace logstats {

std::map<std::string, long> LogStats::byLevel(const std::vector<LogEntry>& entries) const {
    std::map<std::string, long> result;
    for (const LogEntry& e : entries) {
        ++result[e.level];
    }
    return result;
}

std::vector<TopMessage> LogStats::topErrors(const std::vector<LogEntry>& entries,
                                            std::size_t limit) const {
    std::map<std::string, long> counts;
    for (const LogEntry& e : entries) {
        if (e.level == "ERROR" || e.level == "WARN") {
            ++counts[fingerprint(e.message)];
        }
    }

    std::vector<TopMessage> top;
    for (const auto& [message, count] : counts) {
        top.push_back({message, count});
    }
    // сначала по частоте (убыв.), при равенстве — по алфавиту: стабильный,
    // воспроизводимый порядок, как в Java-версии
    std::sort(top.begin(), top.end(), [](const TopMessage& a, const TopMessage& b) {
        if (a.count != b.count) return a.count > b.count;
        return a.message < b.message;
    });
    if (top.size() > limit) {
        top.resize(limit);
    }
    return top;
}

// Отпечаток сообщения: первые kFingerprintWords значащих слов без пунктуации.
// Адреса, id и коды меняются от строки к строке, слова — остаются: по ним
// и считаем «одинаковые» ошибки.
std::string LogStats::fingerprint(const std::string& message) {
    static const std::unordered_set<std::string> stopwords = {"a", "an", "the"};
    constexpr std::size_t kFingerprintWords = 4;

    std::string fingerprint;
    std::size_t words = 0;
    std::size_t start = 0;
    while (start < message.size()) {
        std::size_t end = message.find(' ', start);
        if (end == std::string::npos) end = message.size();
        std::string word = message.substr(start, end - start);
        start = end + 1;
        if (word.empty()) continue;

        // key=value-обрывки и [cid=...]-идентификаторы — мусор для отпечатка
        if (word.find('=') != std::string::npos || word.front() == '[') continue;

        // срезать пунктуацию по краям: "exceptionCaught()" -> "exceptionCaught"
        static const std::string letters =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.$";
        std::size_t b = word.find_first_of(letters);
        std::size_t e = word.find_last_of(letters);
        if (b == std::string::npos) continue;
        std::string cleaned = word.substr(b, e - b + 1);

        std::string lower = cleaned;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        if (stopwords.count(lower)) continue; // "An exceptionCaught() ..." -> "exceptionCaught"

        if (words > 0) fingerprint += ' ';
        fingerprint += cleaned;
        if (++words == kFingerprintWords) break;
    }
    return words > 0 ? fingerprint : message;
}

} // namespace logstats
