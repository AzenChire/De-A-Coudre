package fr.fixemy.deacoudre.util;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/**
 * World-less location. The world is owned by the arena, which guarantees that
 * every point of an arena lives in the same world.
 */
public record StoredLocation(double x, double y, double z, float yaw, float pitch) {

    public static StoredLocation of(Location location) {
        return new StoredLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public BlockPos toBlockPos() {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", x, y, z);
    }
}
