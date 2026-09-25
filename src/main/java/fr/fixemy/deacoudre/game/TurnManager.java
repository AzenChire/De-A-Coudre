package fr.fixemy.deacoudre.game;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.arena.ArenaState;
import fr.fixemy.deacoudre.config.GameSound;
import fr.fixemy.deacoudre.config.Settings;
import fr.fixemy.deacoudre.game.TaskRegistry.Slot;
import fr.fixemy.deacoudre.util.BlockPos;
import fr.fixemy.deacoudre.util.Placeholders;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turn order and jump resolution of one session.
 * <p>
 * Flow of a turn: teleport on the platform (briefly frozen) → READY → the
 * player drops below the platform → FALLING → water cell (success) or anything
 * else (failure) → next turn. The timer and every event handler go through
 * {@link Turn#resolve()}, so a jump is validated at most once.
 */
public final class TurnManager {

    /** Distance under the platform from which the player is considered falling. */
    private static final double FALL_THRESHOLD = 1.0;

    private final DeACoudrePlugin plugin;
    private final GameSession session;
    private final List<UUID> order = new ArrayList<>();
    private int cursor = -1;
    private long counter;
    private @Nullable Turn current;
    private @Nullable JumpDetector detector;
    private @Nullable PoolGrid grid;
    private @Nullable BossBar bossBar;
    private final Set<UUID> bossBarViewers = new HashSet<>();

    TurnManager(DeACoudrePlugin plugin, GameSession session) {
        this.plugin = plugin;
        this.session = session;
    }

    void start(List<UUID> playerOrder, PoolGrid grid, JumpDetector detector) {
        this.order.clear();
        this.order.addAll(playerOrder);
        this.cursor = -1;
        this.grid = grid;
        this.detector = detector;
        scheduleNext(plugin.settings().turnDelayTicks());
    }

    public List<UUID> order() {
        return List.copyOf(order);
    }

    public @Nullable UUID activePlayer() {
        Turn turn = current;
        return turn == null || turn.isResolved() ? null : turn.playerId();
    }

    public boolean isActive(UUID uuid) {
        return uuid.equals(activePlayer());
    }

    /**
     * True while the active player must not move (start of the turn).
     */
    public boolean isFrozen(UUID uuid) {
        Turn turn = current;
        return turn != null && turn.phase() == Turn.Phase.PREPARING && turn.playerId().equals(uuid);
    }

    public int secondsLeft() {
        Turn turn = current;
        return turn == null || turn.isResolved() ? -1 : turn.secondsLeft();
    }

    void cancel() {
        Turn turn = current;
        if (turn != null) {
            turn.resolve();
        }
        current = null;
        session.tasks().cancel(Slot.TURN_START);
        session.tasks().cancel(Slot.TURN_TIMER);
        session.tasks().cancel(Slot.TURN_UNFREEZE);
        hideBossBar();
    }

    // ------------------------------------------------------------------ boss bar

    /**
     * Optional turn timer shown as a boss bar to the participants of this session
     * only (turn-bossbar.enabled). The action bar already shows the same time, so
     * it is disabled by default.
     */
    private void showBossBar(Turn turn) {
        hideBossBar();
        Settings settings = plugin.settings();
        if (!settings.turnBossBar()) {
            return;
        }
        BossBar bar = BossBar.bossBar(Component.empty(), 1f, settings.turnBossBarColor(), settings.turnBossBarOverlay());
        bossBar = bar;
        for (Player viewer : session.onlinePlayers()) {
            viewer.showBossBar(bar);
            bossBarViewers.add(viewer.getUniqueId());
        }
        updateBossBar(turn);
    }

    private void updateBossBar(Turn turn) {
        BossBar bar = bossBar;
        if (bar == null) {
            return;
        }
        bar.name(plugin.messages().get("game.turn.bossbar", placeholders(turn.player()).text("seconds", turn.secondsLeft())));
        bar.progress(Math.clamp((float) turn.secondsLeft() / Math.max(1, turn.duration()), 0f, 1f));
    }

    /**
     * Removes the boss bar from everybody who saw it (end of turn, end of game, reset).
     */
    void hideBossBar() {
        BossBar bar = bossBar;
        bossBar = null;
        if (bar == null) {
            return;
        }
        for (UUID uuid : bossBarViewers) {
            Player viewer = Bukkit.getPlayer(uuid);
            if (viewer != null) {
                viewer.hideBossBar(bar);
            }
        }
        bossBarViewers.clear();
    }

    /**
     * A player leaving the session in the middle of a turn.
     */
    void hideBossBar(Player player) {
        BossBar bar = bossBar;
        if (bar != null && bossBarViewers.remove(player.getUniqueId())) {
            player.hideBossBar(bar);
        }
    }

    private void scheduleNext(long delayTicks) {
        session.tasks().runLater(Slot.TURN_START, delayTicks, session.guarded(this::beginNextTurn));
    }

    // ------------------------------------------------------------------ turn start

    private void beginNextTurn() {
        if (session.arena().state() != ArenaState.PLAYING || session.checkEnd()) {
            return;
        }
        Settings settings = plugin.settings();
        GamePlayer next = null;
        Player player = null;
        for (int i = 0; i < order.size() && next == null; i++) {
            cursor = (cursor + 1) % order.size();
            GamePlayer candidate = session.gamePlayer(order.get(cursor));
            if (candidate == null || !candidate.isAlive()) {
                continue;
            }
            Player online = Bukkit.getPlayer(candidate.uuid());
            if (online == null || !online.isOnline()) {
                // Should never happen (quit is handled), but never block the game.
                session.eliminate(candidate, FailReason.QUIT);
                continue;
            }
            next = candidate;
            player = online;
        }
        if (next == null) {
            session.checkEnd();
            return;
        }
        Location jump = session.arena().jumpLocation();
        if (jump == null) {
            session.abort("jump location unavailable");
            return;
        }

        Turn turn = new Turn(++counter, next, settings.turnDuration(), settings.fallGrace());
        current = turn;
        plugin.players().teleport(player, jump);
        if (settings.turnFreezeTicks() > 0) {
            session.tasks().runLater(Slot.TURN_UNFREEZE, settings.turnFreezeTicks(), () -> {
                if (current == turn && turn.phase() == Turn.Phase.PREPARING) {
                    turn.setPhase(Turn.Phase.READY);
                }
            });
        } else {
            turn.setPhase(Turn.Phase.READY);
        }

        Placeholders placeholders = placeholders(next).text("seconds", turn.secondsLeft());
        plugin.messages().title(player, "game.turn.your-turn-title", "game.turn.your-turn-subtitle", placeholders);
        plugin.configManager().playSound(player, GameSound.TURN);
        Audience others = session.audienceExcept(next.uuid());
        plugin.messages().send(others, "game.turn.other-player", placeholders);

        session.tasks().runTimer(Slot.TURN_TIMER, 20, 20, session.guarded(() -> tick(turn)));
        showBossBar(turn);
        sendTimer(turn, player);
        session.refreshScoreboards();
        plugin.debug("[" + session.arena().name() + "] Turn #" + turn.id() + " started for " + next.name());
    }

    // ------------------------------------------------------------------ timer

    private void tick(Turn turn) {
        if (current != turn || turn.isResolved()) {
            session.tasks().cancel(Slot.TURN_TIMER);
            return;
        }
        Player player = Bukkit.getPlayer(turn.playerId());
        if (player == null || !player.isOnline()) {
            fail(turn, FailReason.QUIT);
            return;
        }
        // Safety net in case a move event was missed (e.g. cancelled by another plugin).
        if (turn.phase() != Turn.Phase.PREPARING && player.isInWater() && detector != null) {
            handleOutcome(turn, player, detector.analyzePosition(player.getWorld(), player.getLocation()));
            if (turn.isResolved()) {
                return;
            }
        }

        turn.decrementSeconds();
        if (turn.secondsLeft() <= 0) {
            if (turn.phase() != Turn.Phase.FALLING || turn.consumeGrace()) {
                plugin.debug("[" + session.arena().name() + "] " + turn.player().name() + " timed out");
                fail(turn, FailReason.TIMEOUT);
                return;
            }
        }
        sendTimer(turn, player);
        if (turn.secondsLeft() > 0 && turn.secondsLeft() <= 3) {
            plugin.configManager().playSound(player, GameSound.TICK);
        }
    }

    private void sendTimer(Turn turn, Player player) {
        Placeholders placeholders = placeholders(turn.player()).text("seconds", turn.secondsLeft());
        plugin.messages().actionBar(player, "game.turn.actionbar-self", placeholders);
        plugin.messages().actionBar(session.audienceExcept(turn.playerId()), "game.turn.actionbar-others", placeholders);
        updateBossBar(turn);
    }

    // ------------------------------------------------------------------ detection inputs

    /**
     * Called for every position change of the active player (after other plugins had their say).
     */
    public void handleMove(Player player, Location from, Location to) {
        Turn turn = current;
        if (turn == null || turn.isResolved() || !turn.playerId().equals(player.getUniqueId()) || detector == null) {
            return;
        }
        if (turn.phase() == Turn.Phase.PREPARING) {
            return;
        }
        if (turn.phase() == Turn.Phase.READY) {
            Location jump = session.arena().jumpLocation();
            if (jump != null && to.getY() < jump.getY() - FALL_THRESHOLD) {
                turn.setPhase(Turn.Phase.FALLING);
                plugin.debug("[" + session.arena().name() + "] " + player.getName() + " started falling");
            }
        }
        Location current = player.getLocation();
        BoundingBox box = player.getBoundingBox().shift(
                to.getX() - current.getX(), to.getY() - current.getY(), to.getZ() - current.getZ());
        World world = to.getWorld() != null ? to.getWorld() : player.getWorld();
        handleOutcome(turn, player, detector.analyzeMove(world, from, to, box, turn.phase() == Turn.Phase.FALLING));
    }

    /**
     * Fall damage means the player hit something solid (water never deals fall damage).
     */
    public void handleFallDamage(Player player) {
        Turn turn = current;
        if (turn != null && !turn.isResolved() && turn.playerId().equals(player.getUniqueId())
                && turn.phase() == Turn.Phase.FALLING && detector != null) {
            handleOutcome(turn, player, detector.classifyLanding(player.getLocation()));
        }
    }

    /**
     * The active player died, disconnected or left.
     */
    public void failActive(UUID uuid, FailReason reason) {
        Turn turn = current;
        if (turn != null && !turn.isResolved() && turn.playerId().equals(uuid)) {
            fail(turn, reason);
        }
    }

    private void handleOutcome(Turn turn, Player player, JumpDetector.Outcome outcome) {
        switch (outcome.kind()) {
            case WATER -> {
                BlockPos cell = outcome.cell();
                if (cell != null) {
                    succeed(turn, player, cell);
                }
            }
            case HIT_BLOCK -> fail(turn, FailReason.HIT_BLOCK);
            case OUT_OF_POOL -> fail(turn, FailReason.OUT_OF_POOL);
            case OUT_OF_ZONE -> fail(turn, FailReason.OUT_OF_ZONE);
            case NONE -> {
            }
        }
    }

    // ------------------------------------------------------------------ resolution

    private void succeed(Turn turn, Player player, BlockPos cell) {
        PoolGrid pool = grid;
        if (pool == null || !turn.resolve()) {
            return;
        }
        stopTurnTasks();
        GamePlayer gamePlayer = turn.player();
        // 1x1 hole bonus: evaluated before the cell is occupied and before its block is placed.
        // The turn is already resolved, so a single jump can never give two lives.
        boolean perfect = pool.isIsolatedHole(cell.x(), cell.z());
        if (!pool.occupy(cell.x(), cell.z())) {
            // Cannot happen (the detector checks it), but never place two blocks in one cell.
            failAndContinue(turn, FailReason.HIT_BLOCK);
            return;
        }
        plugin.debug("[" + session.arena().name() + "] " + gamePlayer.name() + " validated cell " + cell
                + (perfect ? " (1x1 hole)" : ""));

        // 1. Secure the player first: teleport out of the water before the block appears.
        Location spectator = session.arena().spectatorLocation();
        if (spectator != null) {
            plugin.players().teleport(player, spectator);
        }
        player.setFireTicks(0);

        // 2. Then place the block of the player's color.
        World world = session.arena().world();
        PlayerColor color = gamePlayer.color();
        if (world != null && color != null) {
            world.getBlockAt(cell.x(), cell.y(), cell.z()).setType(color.block(), false);
            world.spawnParticle(Particle.SPLASH, cell.x() + 0.5, cell.y() + 1.1, cell.z() + 0.5, 25, 0.3, 0.1, 0.3, 0.0);
        }
        gamePlayer.addSuccessfulJump();

        Placeholders placeholders = placeholders(gamePlayer);
        session.broadcast("game.success", placeholders);
        if (perfect) {
            session.rewardPerfectJump(gamePlayer, player);
        } else {
            plugin.messages().title(player, "game.success-title", "game.success-subtitle", placeholders);
            plugin.configManager().playSound(session.audience(), GameSound.SUCCESS);
        }

        if (!session.checkEnd()) {
            scheduleNext(plugin.settings().turnDelayTicks());
        }
        session.refreshScoreboards();
    }

    private void fail(Turn turn, FailReason reason) {
        if (!turn.resolve()) {
            return;
        }
        stopTurnTasks();
        plugin.debug("[" + session.arena().name() + "] " + turn.player().name() + " failed: " + reason);
        failAndContinue(turn, reason);
    }

    /**
     * Costs a life (elimination at 0) then moves on. The player keeps their place
     * in {@link #order}: an alive player is simply picked again on their next turn.
     */
    private void failAndContinue(Turn turn, FailReason reason) {
        session.handleFailedJump(turn.player(), reason);
        if (!session.checkEnd()) {
            scheduleNext(plugin.settings().turnDelayTicks());
        }
        session.refreshScoreboards();
    }

    private void stopTurnTasks() {
        session.tasks().cancel(Slot.TURN_TIMER);
        session.tasks().cancel(Slot.TURN_UNFREEZE);
        hideBossBar();
    }

    private Placeholders placeholders(GamePlayer player) {
        PlayerColor color = player.color();
        return Placeholders.create()
                .text("player", player.name())
                .text("arena", session.arena().name())
                .raw("color", color != null ? color.displayName() : "")
                .text("alive", session.aliveCount());
    }
}
