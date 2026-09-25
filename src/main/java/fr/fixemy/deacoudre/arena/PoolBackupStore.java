package fr.fixemy.deacoudre.arena;

import fr.fixemy.deacoudre.util.AsyncFileWriter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Crash safety for pools: the backup of a running game is written to disk when
 * the game starts and deleted once the pool has been restored. If the server
 * crashes in between, the pool is restored on the next start (or as soon as its
 * world is loaded).
 */
public final class PoolBackupStore {

    private final Path directory;
    private final AsyncFileWriter writer;
    private final Logger logger;

    public PoolBackupStore(Path directory, AsyncFileWriter writer, Logger logger) {
        this.directory = directory;
        this.writer = writer;
        this.logger = logger;
    }

    public void save(Arena arena, PoolBackup backup) {
        writer.write(file(arena), backup.serialize());
    }

    public void delete(Arena arena) {
        writer.delete(file(arena));
    }

    private Path file(Arena arena) {
        return directory.resolve(arena.key() + ".yml");
    }

    /**
     * Restores every pending backup whose world is loaded.
     */
    public void recoverLoadedWorlds() {
        for (World world : Bukkit.getWorlds()) {
            recover(world);
        }
    }

    /**
     * Restores pending backups belonging to {@code world}. Called at startup and on world load.
     */
    public void recover(World world) {
        File[] files = directory.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                PoolBackup backup = PoolBackup.deserialize(YamlConfiguration.loadConfiguration(file));
                if (backup == null) {
                    logger.warning("Invalid pool backup " + file.getName() + ", deleting it.");
                    writer.delete(file.toPath());
                    continue;
                }
                if (!backup.worldName().equals(world.getName())) {
                    continue;
                }
                backup.restore(world);
                writer.delete(file.toPath());
                logger.info("Restored pool of arena " + file.getName().replace(".yml", "")
                        + " after an unexpected shutdown (" + backup.size() + " blocks).");
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Unable to restore pool backup " + file.getName(), exception);
            }
        }
    }
}
