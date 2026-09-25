package fr.fixemy.deacoudre.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * Reads and writes locations in YAML sections.
 */
public final class LocationSerializer {

    private LocationSerializer() {
    }

    public static void write(ConfigurationSection parent, String path, @Nullable StoredLocation location) {
        if (location == null) {
            parent.set(path, null);
            return;
        }
        ConfigurationSection section = parent.createSection(path);
        section.set("x", round(location.x()));
        section.set("y", round(location.y()));
        section.set("z", round(location.z()));
        section.set("yaw", Math.round(location.yaw() * 10d) / 10d);
        section.set("pitch", Math.round(location.pitch() * 10d) / 10d);
    }

    public static @Nullable StoredLocation read(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null || !section.contains("x") || !section.contains("y") || !section.contains("z")) {
            return null;
        }
        return new StoredLocation(
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    public static void writeBlock(ConfigurationSection parent, String path, @Nullable BlockPos pos) {
        if (pos == null) {
            parent.set(path, null);
            return;
        }
        ConfigurationSection section = parent.createSection(path);
        section.set("x", pos.x());
        section.set("y", pos.y());
        section.set("z", pos.z());
    }

    public static @Nullable BlockPos readBlock(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null || !section.contains("x") || !section.contains("y") || !section.contains("z")) {
            return null;
        }
        return new BlockPos(section.getInt("x"), section.getInt("y"), section.getInt("z"));
    }

    /**
     * Writes a full location including its world name.
     */
    public static void writeFull(ConfigurationSection parent, String path, Location location) {
        write(parent, path, StoredLocation.of(location));
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section != null && location.getWorld() != null) {
            section.set("world", location.getWorld().getName());
        }
    }

    /**
     * Reads a full location. Returns {@code null} when the stored world is not loaded anymore.
     */
    public static @Nullable Location readFull(ConfigurationSection parent, String path) {
        StoredLocation stored = read(parent, path);
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (stored == null || section == null) {
            return null;
        }
        String worldName = section.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        return world == null ? null : stored.toLocation(world);
    }

    private static double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}
