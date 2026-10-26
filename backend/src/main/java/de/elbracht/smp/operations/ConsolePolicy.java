package de.elbracht.smp.operations;

import de.elbracht.smp.api.ApiException;
import java.util.Set;

/** Structured operator templates and explicitly acknowledged admin Minecraft commands. Never OS commands. */
public final class ConsolePolicy {
    private ConsolePolicy() {}
    private static final Set<String> OPERATOR = Set.of("list", "time query daytime", "time set day", "time set night", "weather clear", "weather rain", "save-all flush");
    public static void validate(String command, boolean admin) {
        if (command == null || command.isBlank() || command.length() > 512 || !command.equals(command.strip())
                || command.chars().anyMatch(c -> c < 32 || c == 127) || command.startsWith("/"))
            throw new ApiException(422, "INVALID_COMMAND", "Ein einzelnes Minecraft-Kommando ohne führenden Schrägstrich ist erforderlich.");
        // Membership/configuration and lifecycle must retain their verified, dedicated job flow.
        String root = command.split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT);
        if (Set.of("op", "deop", "whitelist", "stop", "reload", "datapack", "save-off").contains(root))
            throw new ApiException(422, "DEDICATED_OPERATION_REQUIRED", "Diese Aktion benötigt ihren eigenen geprüften Ablauf.");
        if (!admin && !OPERATOR.contains(command))
            throw new org.springframework.security.access.AccessDeniedException("Kommando erfordert Adminrechte");
    }
}
