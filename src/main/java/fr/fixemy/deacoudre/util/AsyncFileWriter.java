package fr.fixemy.deacoudre.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes small files off the main thread, in submission order.
 * <p>
 * Content is always serialized on the main thread by the caller; only the disk
 * I/O happens here. Writes are atomic (temp file + move) so a crash never leaves
 * a half written file.
 */
public final class AsyncFileWriter {

    private final Logger logger;
    private final ExecutorService executor;

    public AsyncFileWriter(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "DeACoudre-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void write(Path path, String content) {
        submit(() -> writeNow(path, content));
    }

    public void delete(Path path) {
        submit(() -> deleteNow(path));
    }

    private void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            // Executor already stopped (plugin disabling): run synchronously.
            task.run();
        }
    }

    private void writeNow(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Unable to write " + path, exception);
        }
    }

    private void deleteNow(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Unable to delete " + path, exception);
        }
    }

    /**
     * Waits for the pending writes (used before re-reading files, e.g. /dac reload).
     */
    public void flush() {
        try {
            executor.submit(() -> {
            }).get(5, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ignored) {
            // Already stopped: nothing pending.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException exception) {
            logger.warning("Pending file writes did not complete in time: " + exception.getMessage());
        }
    }

    /**
     * Flushes pending writes. Called when the plugin is disabled.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Some files could not be written before shutdown.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
