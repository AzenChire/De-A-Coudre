package fr.fixemy.deacoudre.player;

import fr.fixemy.deacoudre.util.AsyncFileWriter;
import fr.fixemy.deacoudre.util.LocationSerializer;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Disk mirror of player snapshots ({@code plugins/DeACoudre/data/snapshots/<uuid>.yml}).
 * <p>
 * A file exists only while a player is inside the mini-game, or when a
 * restoration could not be completed. The set of "pending" files is read once
 * at startup so joining players never trigger a disk access unless they really
 * have something to recover.
 */
public final class SnapshotStore {

    /** Result of a pending recovery file. */
    public record Recovery(@Nullable PlayerSnapshot snapshot, @Nullable Location locationOnly) {
    }

    private final Path directory;
    private final AsyncFileWriter writer;
    private final Logger logger;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public SnapshotStore(Path directory, AsyncFileWriter writer, Logger logger) {
        this.directory = directory;
        this.writer = writer;
        this.logger = logger;
    }

    public void init() {
        pending.clear();
        File[] files = directory.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                pending.add(UUID.fromString(file.getName().replace(".yml", "")));
            } catch (IllegalArgumentException ignored) {
                logger.warning("Ignoring unknown file in snapshots folder: " + file.getName());
            }
        }
        if (!pending.isEmpty()) {
            logger.info(pending.size() + " player snapshot(s) will be restored when their owners log in.");
        }
    }

    public void save(PlayerSnapshot snapshot) {
        writer.write(file(snapshot.uuid()), snapshot.serialize());
    }

    /**
     * Used when only the teleportation failed: the rest of the state is already restored.
     */
    public void saveLocationOnly(UUID uuid, Location location) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("location-only", true);
        LocationSerializer.writeFull(yaml, "location", location);
        writer.write(file(uuid), yaml.saveToString());
    }

    public void delete(UUID uuid) {
        pending.remove(uuid);
        writer.delete(file(uuid));
    }

    public void markPending(UUID uuid) {
        pending.add(uuid);
    }

    public boolean hasPending(UUID uuid) {
        return pending.contains(uuid);
    }

    public @Nullable Recovery loadPending(UUID uuid) {
        File file = file(uuid).toFile();
        if (!file.exists()) {
            pending.remove(uuid);
            return null;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (yaml.getBoolean("location-only")) {
                return new Recovery(null, LocationSerializer.readFull(yaml, "location"));
            }
            return new Recovery(PlayerSnapshot.deserialize(yaml), null);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Unable to read snapshot of " + uuid + " (file kept for manual recovery)", exception);
            pending.remove(uuid);
            return null;
        }
    }

    private Path file(UUID uuid) {
        return directory.resolve(uuid + ".yml");
    }
}
