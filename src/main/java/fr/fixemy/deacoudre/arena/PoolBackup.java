package fr.fixemy.deacoudre.arena;

import fr.fixemy.deacoudre.util.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Original state (BlockData) of every block the game is allowed to modify.
 * Only the playable surface cells are stored, never the whole region.
 */
public final class PoolBackup {

    public record Entry(BlockPos pos, BlockData data) {
    }

    private final String worldName;
    private final List<Entry> entries;

    private PoolBackup(String worldName, List<Entry> entries) {
        this.worldName = worldName;
        this.entries = entries;
    }

    public static PoolBackup capture(World world, Collection<BlockPos> positions) {
        List<Entry> entries = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            entries.add(new Entry(pos, pos.toBlock(world).getBlockData().clone()));
        }
        return new PoolBackup(world.getName(), List.copyOf(entries));
    }

    public String worldName() {
        return worldName;
    }

    public int size() {
        return entries.size();
    }

    /**
     * Puts every block back. Physics are not applied so the water does not flow
     * while the pool is being rebuilt.
     */
    public void restore(World world) {
        for (Entry entry : entries) {
            entry.pos().toBlock(world).setBlockData(entry.data(), false);
        }
    }

    public String serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", worldName);
        List<String> lines = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            BlockPos pos = entry.pos();
            lines.add(pos.x() + "," + pos.y() + "," + pos.z() + "|" + entry.data().getAsString());
        }
        yaml.set("blocks", lines);
        return yaml.saveToString();
    }

    public static @Nullable PoolBackup deserialize(YamlConfiguration yaml) {
        String world = yaml.getString("world");
        if (world == null) {
            return null;
        }
        List<Entry> entries = new ArrayList<>();
        for (String line : yaml.getStringList("blocks")) {
            int separator = line.indexOf('|');
            if (separator < 0) {
                continue;
            }
            String[] coordinates = line.substring(0, separator).split(",");
            if (coordinates.length != 3) {
                continue;
            }
            BlockPos pos = new BlockPos(
                    Integer.parseInt(coordinates[0].trim()),
                    Integer.parseInt(coordinates[1].trim()),
                    Integer.parseInt(coordinates[2].trim()));
            entries.add(new Entry(pos, Bukkit.createBlockData(line.substring(separator + 1))));
        }
        return new PoolBackup(world, List.copyOf(entries));
    }
}
