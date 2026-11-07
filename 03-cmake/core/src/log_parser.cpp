#include "logstats/log_parser.hpp"

#include <fstream>
#include <regex>
#include <stdexcept>
#include <unordered_set>

namespace logstats {
namespace {

// "Nov  7 12:12:10 host proc[213]: INFO  [o.w.t.w.Stream2] Closing session, error: null"
//   ^^^^^^^^^^^^^^^^^^^^ ^^^^ ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ^^^^^ ^^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^^^^^^^^^^^^
//   timestamp            host process[pid]:                 level logger            сообщение
const std::regex kLine(R"(^(\w{3} +\d{1,2} \d{2}:\d{2}:\d{2}) (\S+) (\S+?): +(\S+) +\[([^\]]*)\] (.*)$)");

const std::unordered_set<std::string> kLevels = {
    "ERROR", "WARN", "INFO", "DEBUG", "TRACE"};

} // namespace

ParseResult LogParser::parseFile(const std::string& path) {
    std::ifstream in(path);
    if (!in) {
        throw std::runtime_error("не удалось открыть файл: " + path);
    }

    ParseResult result;
    std::string line;
    LogEntry entry;
    while (std::getline(in, line)) {
        if (parseLine(line, entry)) {
            result.entries.push_back(entry);
        } else {
            ++result.skipped;
        }
    }
    return result;
}

bool LogParser::parseLine(const std::string& line, LogEntry& out) {
    std::smatch m;
    if (!std::regex_match(line, m, kLine)) {
        return false;
    }
    if (kLevels.find(m[4]) == kLevels.end()) {
        return false;
    }
    out.timestamp = m[1];
    out.level = m[4];
    out.logger = m[5];
    out.message = m[6];
    return true;
}

} // namespace logstats
