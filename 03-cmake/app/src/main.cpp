// То же приложение log-stats, что в Java-проектах, — теперь на C++.
// CLI тот же: log_analyzer <file.log> [--top N]
// Сводка в консоль + отчёт в report.json (nlohmann/json — «Gson мира C++»,
// подключён через FetchContent).

#include <cstdlib>
#include <fstream>
#include <iostream>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "logstats/log_parser.hpp"
#include "logstats/log_stats.hpp"

namespace {

int printUsage() {
    std::cerr << "Использование: log_analyzer <file.log> [--top N]\n";
    return 1;
}

void printSummary(const std::string& file, const std::vector<logstats::LogEntry>& entries,
                  int skipped) {
    std::cout << "Файл:    " << file << "\n";
    std::cout << "Строк:   " << (entries.size() + skipped) << " (разобрано "
              << entries.size() << ", пропущено " << skipped << ")\n";
    std::cout << "Уровни:  ";
    for (const auto& [level, count] : logstats::LogStats().byLevel(entries)) {
        std::cout << level << " " << count << "   ";
    }
    std::cout << "\n";
}

void printTopErrors(const std::vector<logstats::LogEntry>& entries, std::size_t top) {
    auto topErrors = logstats::LogStats().topErrors(entries, top);
    if (topErrors.empty()) return;
    std::cout << "Топ ошибок (ERROR/WARN):\n";
    int i = 1;
    for (const auto& t : topErrors) {
        std::cout << "  " << i++ << ". " << t.message << " x" << t.count << "\n";
    }
}

void writeReport(const std::string& file, const std::vector<logstats::LogEntry>& entries,
                 int skipped, std::size_t top) {
    nlohmann::json byLevel = nlohmann::json::object();
    for (const auto& [level, count] : logstats::LogStats().byLevel(entries)) {
        byLevel[level] = count;
    }
    nlohmann::json topErrors = nlohmann::json::array();
    for (const auto& t : logstats::LogStats().topErrors(entries, top)) {
        topErrors.push_back({{"message", t.message}, {"count", t.count}});
    }

    nlohmann::json report;
    report["file"] = file;
    report["totalLines"] = entries.size() + skipped;
    report["parsed"] = entries.size();
    report["skipped"] = skipped;
    report["byLevel"] = byLevel;
    report["topErrors"] = topErrors;

    std::ofstream out("report.json");
    out << report.dump(2) << "\n";
    std::cout << "[INFO] Отчёт: report.json\n";
}

} // namespace

int main(int argc, char** argv) {
    std::cout << "[INFO] Запускаемся: log-stats на CMake\n";

    if (argc < 2) {
        return printUsage();
    }
    std::string file = argv[1];

    std::size_t top = 5;
    if (argc >= 4 && std::string(argv[2]) == "--top") {
        top = static_cast<std::size_t>(std::atoi(argv[3]));
    }

    std::vector<logstats::LogEntry> entries;
    int skipped = 0;
    try {
        logstats::ParseResult parsed = logstats::LogParser().parseFile(file);
        entries = std::move(parsed.entries);
        skipped = parsed.skipped;
    } catch (const std::exception& e) {
        std::cerr << "[ERROR] " << e.what() << "\n";
        return 1;
    }

    printSummary(file, entries, skipped);
    printTopErrors(entries, top);
    writeReport(file, entries, skipped, top);
    return 0;
}
