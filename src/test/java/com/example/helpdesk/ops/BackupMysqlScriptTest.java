package com.example.helpdesk.ops;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BackupMysqlScriptTest {

    private static final Path SCRIPT = Path.of("scripts/backup-mysql.sh").toAbsolutePath();

    @TempDir
    Path tempDir;

    @Test
    void successfulBackupReplacesExistingFileWithPrivateDump() throws Exception {
        Path output = tempDir.resolve("backup.sql");
        Files.writeString(output, "old backup\n");
        Files.setPosixFilePermissions(output, readableByEveryone());
        Path fakeBin = installFakeDocker();

        CommandResult result = runBackup(output, fakeBin, "0");

        assertEquals(0, result.exitCode(), result.output());
        assertEquals("SQL dump content\n", Files.readString(output));
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(output));
    }

    @Test
    void failedDumpPreservesExistingBackupAndRemovesTemporaryFile() throws Exception {
        Path output = tempDir.resolve("backup.sql");
        Files.writeString(output, "known good backup\n");
        Files.setPosixFilePermissions(output, readableByEveryone());
        Path fakeBin = installFakeDocker();

        CommandResult result = runBackup(output, fakeBin, "1");

        assertFalse(result.exitCode() == 0, result.output());
        assertEquals("known good backup\n", Files.readString(output));
        try (var files = Files.list(tempDir)) {
            assertEquals(2, files.count(), "the directory should contain only the fake docker and target backup");
        }
    }

    private Path installFakeDocker() throws IOException {
        Path fakeBin = Files.createDirectory(tempDir.resolve("bin"));
        Path docker = fakeBin.resolve("docker");
        Files.writeString(docker, """
                #!/usr/bin/env bash
                if [[ "$FAKE_DOCKER_EXIT" != "0" ]]; then
                  printf 'fake dump failure\\n' >&2
                  exit "$FAKE_DOCKER_EXIT"
                fi
                printf 'SQL dump content\\n'
                """);
        Files.setPosixFilePermissions(docker, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        return fakeBin;
    }

    private CommandResult runBackup(Path output, Path fakeBin, String fakeExitCode) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("bash", SCRIPT.toString(), output.toString());
        builder.redirectErrorStream(true);
        builder.environment().put("PATH", fakeBin + System.getProperty("path.separator")
                + builder.environment().getOrDefault("PATH", ""));
        builder.environment().put("FAKE_DOCKER_EXIT", fakeExitCode);
        Process process = builder.start();
        String commandOutput = new String(process.getInputStream().readAllBytes());
        return new CommandResult(process.waitFor(), commandOutput);
    }

    private static Set<PosixFilePermission> readableByEveryone() {
        return Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ);
    }

    private record CommandResult(int exitCode, String output) {}
}
