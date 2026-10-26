package de.elbracht.smp.agent;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BackupRetentionPolicyTest {
    final Instant now=Instant.parse("2026-10-26T12:00:00Z");
    BackupRetention.Entry entry(char id,Instant time,boolean verified,boolean referenced) {
        return new BackupRetention.Entry(String.valueOf(id).repeat(64),time,verified,referenced);
    }
    @Test void sevenFullDaysUseWallClockAndRetainBoundaryAndLatest() throws Exception {
        var fresh=entry('a',now.minusSeconds(60),true,false);
        var boundary=entry('b',now.minus(Duration.ofDays(7)),true,false);
        var old=entry('c',now.minus(Duration.ofDays(7)).minusSeconds(1),true,false);
        var plan=BackupRetention.decide(List.of(fresh,boundary,old),now);
        assertThat(plan.candidates()).containsExactly(old);
        assertThat(plan.retained()).isEqualTo(2);
        assertThat(plan.stale()).isFalse();
    }
    @Test void longOutageNeverDeletesTheLastVerifiedBackup() throws Exception {
        var latest=entry('a',now.minus(Duration.ofDays(12)),true,false);
        var older=entry('b',now.minus(Duration.ofDays(20)),true,false);
        var plan=BackupRetention.decide(List.of(older,latest),now);
        assertThat(plan.candidates()).containsExactly(older);
        assertThat(plan.latestSuccessfulId()).isEqualTo(latest.id());
        assertThat(plan.stale()).isTrue();
    }
    @Test void unknownAndReferencedSnapshotsAreRetainedEvenWhenOlder() throws Exception {
        var unknown=entry('b',now.minus(Duration.ofDays(30)),false,false);
        var referenced=entry('c',now.minus(Duration.ofDays(30)),true,true);
        var plan=BackupRetention.decide(List.of(entry('a',now,true,false),unknown,referenced),now);
        assertThat(plan.candidates()).isEmpty();
        assertThat(plan.unmanaged()).isEqualTo(1);
    }
    @Test void noVerifiedSnapshotNeverBecomesAnEmptySuccessClaim() throws Exception {
        var plan=BackupRetention.decide(List.of(entry('a',now.minus(Duration.ofDays(40)),false,false)),now);
        assertThat(plan.candidates()).isEmpty();assertThat(plan.latestSuccessfulId()).isNull();assertThat(plan.stale()).isTrue();
    }
    @Test void duplicateInvalidAndFutureIdsBlockTheDecision() {
        var fresh=entry('a',now,true,false);
        assertThatThrownBy(()->BackupRetention.decide(List.of(fresh,fresh),now)).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(()->BackupRetention.decide(List.of(entry('x',now,true,false)),now)).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(()->BackupRetention.decide(List.of(entry('a',now.plusSeconds(61),true,false)),now)).isInstanceOf(java.io.IOException.class);
    }
    @Test void inheritedPasswordsHooksProfilesAndProxiesDoNotReachRestic() {
        var clean=ResticCommand.environment(Map.of("PATH","fixture-path","RESTIC_PASSWORD_COMMAND","untrusted-hook",
            "AWS_PROFILE","untrusted-profile","HTTP_PROXY","untrusted-proxy","RESTIC_PASSWORD","untrusted-password","HOME","untrusted-home"),Map.of());
        assertThat(clean).containsExactlyEntriesOf(Map.of("PATH","fixture-path"));
    }
}
