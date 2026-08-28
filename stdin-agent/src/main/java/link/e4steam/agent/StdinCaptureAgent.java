package link.e4steam.agent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Dependency-free premain agent that preserves Prism/MultiMC's consumed stdin
 * hand-off so an early e4steam replacement JVM can receive the same bytes.
 */
public final class StdinCaptureAgent {
    public static final String CAPTURED_STDIN_FILE_PROPERTY = "e4steam.capturedStdinFile";
    public static final String CAPTURED_STDIN_TRUNCATED_PROPERTY =
            "e4steam.capturedStdinTruncated";
    private static final long MAX_CAPTURE_BYTES = 1_048_576L;

    private StdinCaptureAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        try {
            Path capture = createOwnerOnlyTempFile();
            capture.toFile().deleteOnExit();
            InputStream original = System.in;
            OutputStream sink = Files.newOutputStream(
                    capture,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            System.setIn(new BoundedTeeInputStream(original, sink));
            System.setProperty(CAPTURED_STDIN_FILE_PROPERTY, capture.toAbsolutePath().toString());
            System.setProperty(CAPTURED_STDIN_TRUNCATED_PROPERTY, "false");
        } catch (IOException | SecurityException unavailable) {
            // Preserve the launcher's original stdin behavior if capture setup fails.
        }
    }

    private static Path createOwnerOnlyTempFile() throws IOException {
        Path path;
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
            FileAttribute<Set<PosixFilePermission>> attribute =
                    PosixFilePermissions.asFileAttribute(permissions);
            path = Files.createTempFile("e4steam-stdin-", ".bin", attribute);
        } catch (UnsupportedOperationException unsupported) {
            path = Files.createTempFile("e4steam-stdin-", ".bin");
        }
        File file = path.toFile();
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
        return path;
    }

    private static final class BoundedTeeInputStream extends InputStream {
        private final InputStream source;
        private final OutputStream sink;
        private long captured;
        private boolean truncated;

        private BoundedTeeInputStream(InputStream source, OutputStream sink) {
            this.source = source;
            this.sink = sink;
        }

        @Override public int read() throws IOException {
            int value = source.read();
            if (value >= 0) writeBounded(new byte[] { (byte) value }, 0, 1);
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = source.read(buffer, offset, length);
            if (count > 0) writeBounded(buffer, offset, count);
            return count;
        }

        @Override public int available() throws IOException {
            return source.available();
        }

        @Override public void close() throws IOException {
            sink.flush();
        }

        private void writeBounded(byte[] buffer, int offset, int length) throws IOException {
            if (truncated) return;
            long remaining = MAX_CAPTURE_BYTES - captured;
            int writable = (int) Math.min((long) length, Math.max(0L, remaining));
            if (writable > 0) {
                sink.write(buffer, offset, writable);
                sink.flush();
                captured += writable;
            }
            if (writable != length) {
                truncated = true;
                System.setProperty(CAPTURED_STDIN_TRUNCATED_PROPERTY, "true");
            }
        }
    }
}
