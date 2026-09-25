package fr.fixemy.deacoudre.arena;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Registry of all arenas (configuration side). Game logic lives in the game package.
 */
public final class ArenaManager {

    private final ArenaRepository repository;
    private final Logger logger;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(ArenaRepository repository, Logger logger) {
        this.repository = repository;
        this.logger = logger;
    }

    public void loadAll() {
        arenas.clear();
        for (Arena arena : repository.loadAll()) {
            register(arena);
        }
        logger.info("Loaded " + arenas.size() + " arena" + (arenas.size() > 1 ? "s" : "") + ".");
    }

    /**
     * Reloads arena files, keeping the arenas matched by {@code inUse} untouched.
     *
     * @return number of arenas loaded
     */
    public int reload(Predicate<Arena> inUse) {
        Map<String, Arena> kept = new LinkedHashMap<>();
        for (Arena arena : arenas.values()) {
            if (inUse.test(arena)) {
                kept.put(arena.key(), arena);
            }
        }
        arenas.clear();
        arenas.putAll(kept);
        for (Arena arena : repository.loadAll()) {
            if (!kept.containsKey(arena.key())) {
                register(arena);
            }
        }
        return arenas.size();
    }

    private void register(Arena arena) {
        if (arenas.containsKey(arena.key())) {
            logger.warning("Duplicate arena name '" + arena.name() + "' (names are case insensitive), ignored.");
            return;
        }
        arena.setState(arena.isEnabled() ? ArenaState.WAITING : ArenaState.DISABLED);
        arenas.put(arena.key(), arena);
    }

    public @Nullable Arena get(String name) {
        return arenas.get(Arena.key(name));
    }

    public boolean exists(String name) {
        return arenas.containsKey(Arena.key(name));
    }

    public Arena create(String name, @Nullable String worldName) {
        Arena arena = new Arena(name);
        arena.setWorldName(worldName);
        arena.setState(ArenaState.DISABLED);
        arenas.put(arena.key(), arena);
        repository.save(arena);
        return arena;
    }

    public void delete(Arena arena) {
        arenas.remove(arena.key());
        repository.delete(arena);
    }

    public void save(Arena arena) {
        repository.save(arena);
    }

    public Collection<Arena> all() {
        List<Arena> sorted = new ArrayList<>(arenas.values());
        sorted.sort(Comparator.comparing(Arena::key));
        return sorted;
    }

    public List<String> names() {
        return all().stream().map(Arena::name).toList();
    }
}
