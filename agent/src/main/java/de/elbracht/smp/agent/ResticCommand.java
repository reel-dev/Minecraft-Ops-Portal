package de.elbracht.smp.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Bounded process transport. No shell, hooks, inherited credentials, stderr or command logging. */
final class ResticCommand {
    private ResticCommand() {}
    static String local(BackupProperties properties, Path directory, List<String> arguments) throws Exception {
        List<String> options = new ArrayList<>(List.of("--repo", properties.repository().toRealPath().toString(),
            "--password-file", properties.passwordFile().toRealPath().toString(), "--no-cache"));
        options.addAll(arguments);
        return run(properties.executable(), directory, options, Map.of(), Duration.ofMinutes(3));
    }

    static String run(Path executable, Path directory, List<String> options, Map<String,String> credentials, Duration timeout) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(executable.toRealPath().toString()); command.addAll(options);
        var builder = new ProcessBuilder(command).directory(directory.toRealPath().toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD);
        Map<String,String> clean = environment(builder.environment(),credentials);
        builder.environment().clear();
        builder.environment().putAll(clean);
        var process = builder.start(); process.getOutputStream().close();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var result = pool.submit(() -> process.getInputStream().readNBytes(2_097_153));
            try {
                byte[] output = result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (output.length > 2_097_152 || !process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0)
                    throw new IOException("Restic-Prüfung fehlgeschlagen");
                return new String(output, StandardCharsets.UTF_8);
            } finally {
                process.destroyForcibly(); process.getInputStream().close(); result.cancel(true);
            }
        }
    }
    static Map<String,String> environment(Map<String,String> inherited,Map<String,String> credentials) {
        Map<String,String> clean=new HashMap<>();
        for (String key : List.of("PATH", "Path", "SystemRoot", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "TMPDIR", "LANG", "LC_ALL"))
            if (inherited.containsKey(key)) clean.put(key,inherited.get(key));
        clean.putAll(credentials);return clean;
    }
}
