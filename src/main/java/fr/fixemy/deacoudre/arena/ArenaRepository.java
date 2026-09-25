package fr.fixemy.deacoudre.arena;

import fr.fixemy.deacoudre.util.AsyncFileWriter;
import fr.fixemy.deacoudre.util.LocationSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Persists arenas in {@code plugins/DeACoudre/arenas/<name>.yml}, one file per arena.
 */
public final class ArenaRepository {

    public static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final Path directory;
    private final AsyncFileWriter writer;
    private final Logger logger;

    public ArenaRepository(Path directory, AsyncFileWriter writer, Logger logger) {
        this.directory = directory;
        this.writer = writer;
        this.logger = logger;
    }

    public List<Arena> loadAll() {
        writer.flush();
        List<Arena> arenas = new ArrayList<>();
        File[] files = directory.toFile().listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return arenas;
        }
        for (File file : files) {
            String name = file.getName().substring(0, file.getName().length() - ".yml".length());
            if (!VALID_NAME.matcher(name).matches()) {
                logger.warning("Ignoring arena file with invalid name: " + file.getName());
                continue;
            }
            try {
                arenas.add(read(name, YamlConfiguration.loadConfiguration(file)));
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Unable to load arena " + file.getName(), exception);
            }
        }
        return arenas;
    }

    private Arena read(String name, YamlConfiguration yaml) {
        Arena arena = new Arena(name);
        arena.setEnabled(yaml.getBoolean("enabled", false));
        arena.setWorldName(yaml.getString("world"));
        arena.setLobby(LocationSerializer.read(yaml, "lobby"));
        arena.setJump(LocationSerializer.read(yaml, "jump"));
        arena.setSpectator(LocationSerializer.read(yaml, "spectator"));
        arena.setPos1(LocationSerializer.readBlock(yaml, "pool.pos1"));
        arena.setPos2(LocationSerializer.readBlock(yaml, "pool.pos2"));
        ConfigurationSection settings = yaml.getConfigurationSection("settings");
        Integer min = settings != null && settings.isInt("min-players") ? settings.getInt("min-players") : null;
        Integer max = settings != null && settings.isInt("max-players") ? settings.getInt("max-players") : null;
        arena.setPlayerLimits(min, max);
        return arena;
    }

    /**
     * Serializes the arena on the calling (main) thread and writes it asynchronously.
     */
    public void save(Arena arena) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("DeACoudre arena '" + arena.name() + "'",
                "Editable in game with /dac. The settings section is optional."));
        yaml.set("enabled", arena.isEnabled());
        yaml.set("world", arena.worldName());
        LocationSerializer.write(yaml, "lobby", arena.lobby());
        LocationSerializer.write(yaml, "jump", arena.jump());
        LocationSerializer.write(yaml, "spectator", arena.spectator());
        LocationSerializer.writeBlock(yaml, "pool.pos1", arena.pos1());
        LocationSerializer.writeBlock(yaml, "pool.pos2", arena.pos2());
        if (arena.minPlayersOverride() != null) {
            yaml.set("settings.min-players", arena.minPlayersOverride());
        }
        if (arena.maxPlayersOverride() != null) {
            yaml.set("settings.max-players", arena.maxPlayersOverride());
        }
        writer.write(file(arena.name()), yaml.saveToString());
    }

    public void delete(Arena arena) {
        writer.delete(file(arena.name()));
    }

    private Path file(String name) {
        return directory.resolve(name + ".yml");
    }
}
