package fr.fixemy.deacoudre.game;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.arena.Arena;
import fr.fixemy.deacoudre.arena.ArenaState;
import fr.fixemy.deacoudre.util.Placeholders;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entry point of the game logic: owns the sessions and the player → session index.
 */
public final class GameManager {

    private final DeACoudrePlugin plugin;
    private final Map<String, GameSession> sessions = new HashMap<>();
    private final Map<UUID, GameSession> byPlayer = new HashMap<>();

    public GameManager(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lookups

    public @Nullable GameSession session(UUID player) {
        return byPlayer.get(player);
    }

    public @Nullable GameSession session(Arena arena) {
        return sessions.get(arena.key());
    }

    public boolean isInGame(UUID player) {
        return byPlayer.containsKey(player);
    }

    public int playerCount(Arena arena) {
        GameSession session = session(arena);
        return session == null ? 0 : session.size();
    }

    public boolean hasSession(Arena arena) {
        return sessions.containsKey(arena.key());
    }

    void bind(UUID player, GameSession session) {
        byPlayer.put(player, session);
    }

    void unbind(UUID player) {
        byPlayer.remove(player);
    }

    void removeSession(GameSession session) {
        sessions.remove(session.arena().key(), session);
    }

    // ------------------------------------------------------------------ join / leave

    /**
     * Whether a new player may join right now (used by /dac join and the tab completion).
     */
    public boolean isJoinable(Arena arena) {
        return arena.isEnabled() && arena.state().isJoinable() && arena.world() != null
                && playerCount(arena) < arena.maxPlayers(plugin.settings());
    }

    public boolean join(Player player, Arena arena) {
        Placeholders placeholders = Placeholders.create().text("arena", arena.name());
        if (isInGame(player.getUniqueId())) {
            plugin.messages().send(player, "errors.already-playing", placeholders);
            return false;
        }
        if (!arena.isEnabled() || arena.state() == ArenaState.DISABLED) {
            plugin.messages().send(player, "errors.arena-disabled", placeholders);
            return false;
        }
        World world = arena.world();
        if (world == null) {
            plugin.messages().send(player, "errors.world-not-loaded", placeholders.text("world", String.valueOf(arena.worldName())));
            return false;
        }
        if (!arena.state().isJoinable()) {
            plugin.messages().send(player, "errors.game-started", placeholders);
            return false;
        }
        if (playerCount(arena) >= arena.maxPlayers(plugin.settings())) {
            plugin.messages().send(player, "errors.arena-full", placeholders);
            return false;
        }
        if (player.isDead()) {
            return false;
        }
        GameSession session = sessions.computeIfAbsent(arena.key(), key -> new GameSession(plugin, this, arena));
        session.runSafely(() -> session.addPlayer(player));
        return true;
    }

    /**
     * /dac join without arena: the most advanced joinable arena (starting first, then fullest).
     */
    public boolean autoJoin(Player player) {
        if (isInGame(player.getUniqueId())) {
            plugin.messages().send(player, "errors.already-playing");
            return false;
        }
        List<Arena> candidates = new ArrayList<>();
        for (Arena arena : plugin.arenas().all()) {
            if (isJoinable(arena)) {
                candidates.add(arena);
            }
        }
        if (candidates.isEmpty()) {
            plugin.messages().send(player, "errors.no-arena-available");
            return false;
        }
        candidates.sort(Comparator
                .comparing((Arena arena) -> arena.state() != ArenaState.STARTING)
                .thenComparing(arena -> -playerCount(arena))
                .thenComparing(Arena::key));
        return join(player, candidates.getFirst());
    }

    public void leave(Player player, LeaveCause cause) {
        GameSession session = session(player.getUniqueId());
        if (session != null) {
            session.runSafely(() -> session.removePlayer(player, cause));
            // Whatever happened in the session, never keep a stale binding.
            unbind(player.getUniqueId());
        }
    }

    // ------------------------------------------------------------------ admin

    /**
     * Stops the session of an arena (lobby or game) and restores everything.
     *
     * @return false if nothing was running
     */
    public boolean stop(Arena arena, boolean announce) {
        GameSession session = session(arena);
        if (session == null) {
            return false;
        }
        session.stop(announce);
        return true;
    }

    public boolean forceStart(Arena arena) {
        GameSession session = session(arena);
        return session != null && session.forceStart();
    }

    /**
     * Plugin disable / server stop: every session is restored synchronously.
     */
    public void shutdown() {
        for (GameSession session : new ArrayList<>(sessions.values())) {
            try {
                session.stop(true);
            } catch (Exception exception) {
                plugin.getLogger().severe("Error while stopping arena " + session.arena().name() + ": " + exception);
            }
        }
        sessions.clear();
        byPlayer.clear();
    }
}
