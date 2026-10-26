package de.elbracht.smp.servers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
class LogStreamTest {
 @Test void cursorResumesAfterObservedLineAndSignalsExpiredWindow(){var lines=List.of("2026-10-25T20:00:00Z first","2026-10-25T20:00:01Z second");assertThat(LogStreamController.resumeIndex(lines,null)).isZero();assertThat(LogStreamController.resumeIndex(lines,LogStreamController.cursor(lines.get(0)))).isEqualTo(1);assertThat(LogStreamController.resumeIndex(lines,"a".repeat(64))).isEqualTo(-1);}
 @Test void invalidatedSessionCannotContinueStreaming(){var session=new MockHttpSession();assertThat(LogStreamController.validSession(session)).isTrue();session.invalidate();assertThat(LogStreamController.validSession(session)).isFalse();}
}
