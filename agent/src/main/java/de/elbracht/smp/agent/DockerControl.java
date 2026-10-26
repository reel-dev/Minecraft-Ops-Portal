package de.elbracht.smp.agent;

public interface DockerControl {
    enum PlayerFile {
        WHITELIST("/data/whitelist.json"), OPERATORS("/data/ops.json");
        final String path;
        PlayerFile(String path) { this.path = path; }
    }
    void verifyTarget() throws Exception;
    default void verifyRconTarget(int port) throws Exception { throw new IllegalStateException("Test-RCON-Zuordnung nicht geprüft"); }
    default byte[] readTestPlayerFile(PlayerFile file) throws Exception { throw new IllegalStateException("Testdateiquelle nicht angebunden"); }
    default String readTestStats() throws Exception { throw new IllegalStateException("Messquelle fehlt"); }
    default String readTestLogs() throws Exception { throw new IllegalStateException("Logquelle fehlt"); }
    default void copyTestWorld(java.nio.file.Path destination) throws Exception { throw new IllegalStateException("Testwelt nicht angebunden"); }
    default void verifyCleanTestStop()throws Exception{throw new IllegalStateException("Sauberer Stop nicht bestätigt");}
    default void bootTestRestore(java.nio.file.Path directory,java.util.UUID operation)throws Exception{throw new IllegalStateException("Restore-Weltstart nicht freigeschaltet");}
    default String testTargetIdentity()throws Exception{throw new IllegalStateException("Testidentität nicht bestätigt");}
    record DataSwap(java.util.UUID operation,java.util.List<String> originalNames,java.util.List<String> replacementNames){}
    final class DataSwapFailure extends Exception {
        public final boolean originalRestored;
        public DataSwapFailure(boolean originalRestored){super("Datenwechsel fehlgeschlagen");this.originalRestored=originalRestored;}
    }
    default DataSwap swapTestData(java.nio.file.Path directory,java.util.UUID operation)throws Exception{throw new IllegalStateException("Datenwechsel nicht freigeschaltet");}
    default void rollbackTestData(DataSwap swap)throws Exception{throw new IllegalStateException("Rückweg nicht freigeschaltet");}
    default byte[] readTestProperties() throws Exception { throw new IllegalStateException("Konfiguration nicht angebunden"); }
    default java.util.Set<String> managedTestProperties() throws Exception { throw new IllegalStateException("Konfigurationsquelle ungeprüft"); }
    default void writeTestProperties(byte[] content) throws Exception { throw new IllegalStateException("Konfiguration nicht schreibbar"); }
    default String readTestMods() throws Exception { throw new IllegalStateException("Modinventar nicht angebunden"); }
    record RuntimeProfile(String minecraftVersion,String loader,String loaderVersion){}
    default RuntimeProfile readTestRuntimeProfile()throws Exception{return null;}
    default String readTestJavaVersion()throws Exception{throw new IllegalStateException("Java-Laufzeit nicht gemessen");}
    default java.util.Map<String,String> readTestModHashes()throws Exception{throw new IllegalStateException("Moddateien nicht gemessen");}
    default byte[] readTestModManifest()throws Exception{throw new IllegalStateException("Modmanifest nicht angebunden");}
    void start() throws Exception;
    void stop() throws Exception;
}
