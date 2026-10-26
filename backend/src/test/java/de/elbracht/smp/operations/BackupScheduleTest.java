package de.elbracht.smp.operations;

import de.elbracht.smp.api.ApiException;
import de.elbracht.smp.servers.HostAgent;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackupScheduleTest {
    final JobService jobs = mock(JobService.class);
    final HostAgent agent = mock(HostAgent.class);
    BackupSchedule schedule(boolean enabled, String time, String instant) {
        return new BackupSchedule(new BackupScheduleProperties(enabled, "main", time, "Europe/Berlin", Duration.ofHours(2)),
            jobs, agent, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
    @Test void disabledOrUnavailableNeverDispatches() {
        schedule(false,"03:00","2026-10-26T02:01:00Z").dispatch();
        var disconnected = schedule(true,"03:00","2026-10-26T02:01:00Z");
        disconnected.dispatch();
        assertThat(disconnected.policy("main").state()).isEqualTo("SOURCE_NOT_CONFIGURED");
        verifyNoInteractions(jobs);
    }
    @Test void beforeTimeOrAfterWindowDoesNotCatchUp() {
        when(agent.backupsAvailable()).thenReturn(true);
        schedule(true,"03:00","2026-10-26T01:59:59Z").dispatch();
        var missed = schedule(true,"03:00","2026-10-26T04:00:00Z");
        missed.dispatch();
        assertThat(missed.policy("main").state()).isEqualTo("MISSED_WINDOW");
        assertThat(missed.policy("main").nextRunAt()).isEqualTo("2026-10-27T02:00:00Z");
        verifyNoInteractions(jobs);
    }
    @Test void dateKeyIsIdenticalAcrossNewSchedulerInstances() {
        when(agent.backupsAvailable()).thenReturn(true);
        var first = schedule(true,"03:00","2026-10-26T02:01:00Z");
        first.dispatch(); first.dispatch();
        var restarted = schedule(true,"03:00","2026-10-26T02:05:00Z");
        restarted.dispatch();
        verify(jobs,times(2)).submit("main","system:backup","daily-backup_2026-10-26",OperationType.BACKUP,"Tägliche geplante Sicherung (Europe/Berlin)");
        assertThat(first.policy("main").nextRunAt()).isEqualTo("2026-10-27T02:00:00Z");
        assertThat(first.policy("other").enabled()).isFalse();
    }
    @Test void busyServerCanRetryDispatchWithinWindowWithoutNewKey() {
        when(agent.backupsAvailable()).thenReturn(true);
        when(jobs.submit(anyString(),anyString(),anyString(),any(),anyString()))
            .thenThrow(new ApiException(409,"SERVER_BUSY","test busy"));
        var schedule = schedule(true,"03:00","2026-10-26T02:01:00Z");
        schedule.dispatch(); schedule.dispatch();
        verify(jobs,times(2)).submit("main","system:backup","daily-backup_2026-10-26",OperationType.BACKUP,"Tägliche geplante Sicherung (Europe/Berlin)");
        assertThat(schedule.policy("main").state()).isEqualTo("SERVER_BUSY");
    }
    @Test void dstOverlapUsesOneCalendarSlotAndGapMovesTimeForward() {
        when(agent.backupsAvailable()).thenReturn(true);
        schedule(true,"02:30","2026-10-25T00:35:00Z").dispatch();
        schedule(true,"02:30","2026-10-25T01:35:00Z").dispatch();
        verify(jobs,times(2)).submit("main","system:backup","daily-backup_2026-10-25",OperationType.BACKUP,"Tägliche geplante Sicherung (Europe/Berlin)");
        schedule(true,"02:30","2026-03-29T01:29:59Z").dispatch();
        verifyNoMoreInteractions(jobs);
        schedule(true,"02:30","2026-03-29T01:30:00Z").dispatch();
        verify(jobs).submit("main","system:backup","daily-backup_2026-03-29",OperationType.BACKUP,"Tägliche geplante Sicherung (Europe/Berlin)");
    }
    @Test void invalidScheduleFailsAtStartupWithoutDispatch() {
        assertThatThrownBy(()->schedule(true,"25:99","2026-10-26T00:00:00Z")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new BackupSchedule(new BackupScheduleProperties(true,"../other","03:00","Europe/Berlin",Duration.ofHours(2)),jobs,agent,Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jobs);
    }
}
