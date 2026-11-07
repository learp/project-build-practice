package best.project.ever;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogParserTest {

    private static final String REAL_LINE =
            "Nov  7 12:12:10 1.signaler.dc.odkl.ru odnoklassniki-webrtc[213]: "
            + "INFO  [o.w.t.w.WebTransportStream2] Closing session, error: null";

    @Test
    void parseLineRecognizesStandardFormat() {
        Optional<LogParser.LogEntry> e = new LogParser().parseLine(REAL_LINE);
        assertTrue(e.isPresent());
        assertEquals("Nov  7 12:12:10", e.get().timestamp());
        assertEquals(LogParser.Level.INFO, e.get().level());
        assertEquals("o.w.t.w.WebTransportStream2", e.get().logger());
        assertEquals("Closing session, error: null", e.get().message());
    }

    @Test
    void parseLineRejectsGarbage() {
        LogParser parser = new LogParser();
        assertTrue(parser.parseLine("garbage line without timestamp").isEmpty());
        // строка исключения без уровня — как в настоящем логе
        assertTrue(parser.parseLine(
                "io.netty.channel.unix.Errors$NativeIoException: recvAddress(..) failed: Connection reset by peer").isEmpty());
        assertTrue(parser.parseLine(
                "Nov  7 12:12:10 host proc[213]: VERBOSE [x.y.Z] нет такого уровня").isEmpty());
    }

    @Test
    void parseSkipsAndCountsMalformedLines(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("app.log");
        Files.write(log, List.of(
                REAL_LINE,
                "Nov  7 12:12:10 1.signaler.dc.odkl.ru odnoklassniki-webrtc[213]: "
                + "WARN  [o.w.t.w.InboundHandler] QUIC exception from src /123.123.123.123",
                "io.netty.channel.unix.Errors$NativeIoException: recvAddress(..) failed",
                "!!!! NETWORK FLAP DETECTED ????"));

        LogParser.ParseResult result = new LogParser().parse(log);

        assertEquals(2, result.entries().size());
        assertEquals(2, result.skipped());
    }
}
