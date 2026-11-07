package best.project.ever;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogStatsTest {

    private static LogParser.LogEntry entry(LogParser.Level level, String message) {
        return new LogParser.LogEntry("Nov  7 12:12:10", level, "o.w.t.w.Test", message);
    }

    @Test
    void byLevelCountsEachLevel() {
        List<LogParser.LogEntry> entries = List.of(
                entry(LogParser.Level.INFO, "a"),
                entry(LogParser.Level.INFO, "b"),
                entry(LogParser.Level.ERROR, "c"));

        assertEquals(2, new LogStats().byLevel(entries).get(LogParser.Level.INFO));
        assertEquals(1, new LogStats().byLevel(entries).get(LogParser.Level.ERROR));
    }

    @Test
    void topErrorsCountsErrorAndWarnByFingerprint() {
        List<LogParser.LogEntry> entries = List.of(
                entry(LogParser.Level.WARN, "QUIC exception from src /123.123.123.123, code=386759528: STREAM_RESET"),
                entry(LogParser.Level.WARN, "QUIC exception from src /123.123.123.123, code=386759528: STREAM_RESET"),
                entry(LogParser.Level.ERROR, "ConnectionReset by peer"),
                entry(LogParser.Level.INFO, "Closing session, error: null"));   // INFO не считается

        List<LogStats.TopMessage> top = new LogStats().topErrors(entries, 5);

        assertEquals(2, top.size());
        assertEquals("QUIC exception from src", top.get(0).message());
        assertEquals(2, top.get(0).count());
        assertEquals("ConnectionReset by peer", top.get(1).message());
    }

    @Test
    void fingerprintTakesSeveralSignificantWords() {
        assertEquals("exceptionCaught event was fired", LogStats.fingerprint("An exceptionCaught() event was fired"));
        // переменные части (IP, code=) в отпечаток не попадают:
        assertEquals("QUIC exception from src", LogStats.fingerprint(
                "QUIC exception from src /123.123.123.123, code=386759528: STREAM_RESET"));
        assertEquals("Invalid access token for", LogStats.fingerprint(
                "[cid=fb2bcc5c-5e22, eid=1677358432, pid=WEB_SOCKET:125] Invalid access token for /ws2"));
    }

    @Test
    void topErrorsRespectsLimit() {
        List<LogParser.LogEntry> entries = List.of(
                entry(LogParser.Level.ERROR, "A"),
                entry(LogParser.Level.ERROR, "B"),
                entry(LogParser.Level.ERROR, "C"));

        assertEquals(2, new LogStats().topErrors(entries, 2).size());
    }
}
