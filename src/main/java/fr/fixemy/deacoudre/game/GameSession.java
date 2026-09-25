package fr.fixemy.deacoudre.game;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.arena.Arena;
import fr.fixemy.deacoudre.arena.ArenaState;
import fr.fixemy.deacoudre.arena.PoolBackup;
import fr.fixemy.deacoudre.config.GameSound;
import fr.fixemy.deacoudre.config.MessageManager;
import fr.fixemy.deacoudre.config.Settings;
import fr.fixemy.deacoudre.game.TaskRegistry.Slot;
import fr.fixemy.deacoudre.gui.BlockSelectorItem;
import fr.fixemy.deacoudre.gui.BlockSelectorMenu;
import fr.fixemy.deacoudre.util.ColorUtil;
import fr.fixemy.deacoudre.util.Cuboid;
import fr.fixemy.deacoudre.util.Placeholders;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Runtime of one arena, from the first player joining the lobby to the reset.
 * <p>
 * A session is created on the first join and destroyed by {@link #reset()}:
 * every task, player and block it touched is restored at that point, so a new
 * game always starts from a clean object.
 */
public final class GameSession {

    private enum EndResult { WINNER, NO_WINNER, DRAW }

    private static final Pattern LEADING_TAGS = Pattern.compile("^((?:<[^>]+>)*)");
    private static final Title.Times COUNTDOWN_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(250));

    private final DeACoudrePlugin plugin;
    private final GameManager manager;
    private final Arena arena;
    private final Map<UUID, GamePlayer> players = new LinkedHashMap<>();
    private final TaskRegistry tasks;
    private final TurnManager turns;
    private @Nullable PoolGrid grid;
    private @Nullable PoolBackup backup;
    private int countdown = -1;
    private boolean forced;
    private int initialPlayers;
    private boolean disposed;

    GameSession(DeACoudrePlugin plugin, GameManager manager, Arena arena) {
        this.plugin = plugin;
        this.manager = manager;
        this.arena = arena;
        this.tasks = new TaskRegistry(plugin);
        this.turns = new TurnManager(plugin, this);
        tasks.runTimer(Slot.SCOREBOARD, 20, 20, guarded(this::refreshScoreboards));
    }

    // ------------------------------------------------------------------ accessors

    public Arena arena() {
        return arena;
    }

    public TurnManager turns() {
        return turns;
    }

    TaskRegistry tasks() {
        return tasks;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public @Nullable GamePlayer gamePlayer(UUID uuid) {
        return players.get(uuid);
    }

    public Collection<GamePlayer> players() {
        return Collections.unmodifiableCollection(players.values());
    }

    public int size() {
        return players.size();
    }

    public int aliveCount() {
        int alive = 0;
        for (GamePlayer player : players.values()) {
            if (player.isAlive()) {
                alive++;
            }
        }
        return alive;
    }

    public int countdown() {
        return countdown;
    }

    public @Nullable PoolGrid grid() {
        return grid;
    }

    private Settings settings() {
        return plugin.settings();
    }

    private MessageManager messages() {
        return plugin.messages();
    }

    private void setState(ArenaState state) {
        if (arena.state() != state) {
            plugin.debug("[" + arena.name() + "] State " + arena.state() + " -> " + state);
            arena.setState(state);
        }
    }

    // ------------------------------------------------------------------ safety

    /**
     * Wraps a task / event action: an unexpected exception stops the game cleanly
     * (players and pool restored) instead of leaving the arena stuck.
     */
    Runnable guarded(Runnable action) {
        return () -> {
            if (disposed) {
                return;
            }
            try {
                action.run();
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error in arena " + arena.name()
                        + ", the game is stopped and restored.", exception);
                try {
                    broadcast("game.error", Placeholders.create());
                } catch (Exception ignored) {
                    // best effort
                }
                reset();
            }
        };
    }

    public void runSafely(Runnable action) {
        guarded(action).run();
    }

    void abort(String reason) {
        plugin.getLogger().warning("Game in arena " + arena.name() + " aborted: " + reason);
        broadcast("game.error", Placeholders.create());
        reset();
    }

    // ------------------------------------------------------------------ audiences

    List<Player> onlinePlayers() {
        List<Player> online = new ArrayList<>(players.size());
        for (GamePlayer gamePlayer : players.values()) {
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        return online;
    }

    public Audience audience() {
        return Audience.audience(onlinePlayers());
    }

    Audience audienceExcept(UUID excluded) {
        List<Player> online = onlinePlayers();
        online.removeIf(player -> player.getUniqueId().equals(excluded));
        return Audience.audience(online);
    }

    void broadcast(String path, Placeholders placeholders) {
        messages().send(audience(), path, placeholders);
    }

    private Placeholders basePlaceholders() {
        return Placeholders.create()
                .text("arena", arena.name())
                .text("current", players.size())
                .text("max", arena.maxPlayers(settings()))
                .text("min", arena.minPlayers(settings()))
                .text("alive", aliveCount());
    }

    // ------------------------------------------------------------------ lobby

    void addPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        GamePlayer gamePlayer = new GamePlayer(uuid, player.getName());
        players.put(uuid, gamePlayer);
        manager.bind(uuid, this);

        player.leaveVehicle();
        plugin.players().saveSnapshot(player);
        plugin.players().prepareParticipant(player);
        giveLobbyItems(player);
        Location lobby = arena.lobbyLocation();
        if (lobby != null) {
            plugin.players().teleportAsync(player, lobby).whenComplete((success, error) -> plugin.runSync(() -> {
                if (!Boolean.TRUE.equals(success) && players.containsKey(uuid) && player.isOnline()) {
                    messages().send(player, "errors.join-failed");
                    removePlayer(player, LeaveCause.JOIN_FAILED);
                }
            }));
        }

        Placeholders placeholders = basePlaceholders().text("player", player.getName());
        messages().send(player, "game.joined", placeholders);
        if (settings().blockSelectorEnabled()) {
            boolean withItem = settings().lobbyItem() && settings().restoreInventory();
            messages().send(player, withItem ? "selector.hint" : "selector.hint-command", placeholders);
        }
        messages().send(audienceExcept(uuid), "game.player-joined", placeholders);
        plugin.configManager().playSound(audience(), GameSound.JOIN);
        plugin.debug("[" + arena.name() + "] " + player.getName() + " joined (" + players.size() + ")");

        if (arena.state() == ArenaState.WAITING && players.size() >= arena.minPlayers(settings())) {
            startCountdown(false);
        } else if (arena.state() == ArenaState.STARTING) {
            shortenCountdownIfFull();
        }
        refreshScoreboards();
    }

    void removePlayer(Player player, LeaveCause cause) {
        UUID uuid = player.getUniqueId();
        GamePlayer gamePlayer = players.remove(uuid);
        if (gamePlayer == null) {
            return;
        }
        manager.unbind(uuid);
        plugin.scoreboards().remove(player);
        turns.hideBossBar(player);
        boolean wasAlive = gamePlayer.isAlive();
        plugin.players().restore(player, cause.restoreLocation());
        if (cause == LeaveCause.COMMAND) {
            messages().send(player, "game.left", basePlaceholders());
        }
        broadcast("game.player-left", basePlaceholders().text("player", gamePlayer.name()));
        plugin.debug("[" + arena.name() + "] " + gamePlayer.name() + " left (" + cause + ")");

        switch (arena.state()) {
            case WAITING, STARTING -> {
                if (players.isEmpty()) {
                    reset();
                    return;
                }
                if (arena.state() == ArenaState.STARTING && !forced && players.size() < arena.minPlayers(settings())) {
                    cancelCountdown(true);
                }
                // Their block is free again.
                refreshBlockSelectors();
            }
            case PLAYING -> {
                if (wasAlive) {
                    if (turns.isActive(uuid)) {
                        turns.failActive(uuid, FailReason.QUIT);
                    } else {
                        gamePlayer.setStatus(PlayerStatus.ELIMINATED);
                        checkEnd();
                    }
                }
            }
            case ENDING -> {
                if (players.isEmpty()) {
                    reset();
                    return;
                }
            }
            default -> {
            }
        }
        refreshScoreboards();
    }

    // ------------------------------------------------------------------ countdown

    private void startCountdown(boolean forcedStart) {
        forced = forcedStart;
        countdown = settings().startingCountdown();
        setState(ArenaState.STARTING);
        shortenCountdownIfFull();
        tasks.runTimer(Slot.COUNTDOWN, 1, 20, guarded(this::tickCountdown));
    }

    private void shortenCountdownIfFull() {
        int full = settings().fullCountdown();
        if (full > 0 && players.size() >= arena.maxPlayers(settings()) && countdown > full) {
            countdown = full;
            broadcast("game.countdown.shortened", basePlaceholders().text("seconds", countdown));
        }
    }

    private void tickCountdown() {
        if (players.isEmpty()) {
            reset();
            return;
        }
        if (!forced && players.size() < arena.minPlayers(settings())) {
            cancelCountdown(true);
            return;
        }
        if (countdown <= 0) {
            startGame();
            return;
        }
        // The color of the number goes from green to red as the start gets closer.
        String color = messages().rawOrNull("game.countdown.colors." + countdown);
        Placeholders placeholders = basePlaceholders().text("seconds", countdown)
                .raw("countdown_color", color != null ? color : messages().raw("game.countdown.colors.default"));
        Audience audience = audience();
        if (settings().countdownChatSeconds().contains(countdown)) {
            messages().send(audience, "game.countdown.start", placeholders);
        }
        if (settings().countdownTitleSeconds().contains(countdown)) {
            messages().title(audience, "game.countdown.title", "game.countdown.subtitle", placeholders, COUNTDOWN_TIMES);
            // Participants only, pitch rising second after second (sounds.countdown.pitch-by-second).
            plugin.configManager().playSound(audience, GameSound.COUNTDOWN, countdown);
        }
        messages().actionBar(audience, "game.countdown.actionbar", placeholders);
        countdown--;
    }

    private void cancelCountdown(boolean announce) {
        tasks.cancel(Slot.COUNTDOWN);
        countdown = -1;
        forced = false;
        setState(ArenaState.WAITING);
        if (announce) {
            broadcast("game.countdown.cancelled", basePlaceholders());
        }
        refreshScoreboards();
    }

    /**
     * Admin force start: starts in a few seconds even below the minimum.
     */
    boolean forceStart() {
        if (!arena.state().isJoinable() || players.isEmpty()) {
            return false;
        }
        if (arena.state() == ArenaState.WAITING) {
            startCountdown(true);
        }
        forced = true;
        countdown = Math.min(countdown, 3);
        return true;
    }

    // ------------------------------------------------------------------ game start

    private void startGame() {
        tasks.cancel(Slot.COUNTDOWN);
        countdown = -1;
        World world = arena.world();
        Location jump = arena.jumpLocation();
        Location spectator = arena.spectatorLocation();
        Cuboid region = arena.poolRegion();
        if (world == null || jump == null || spectator == null || region == null) {
            abort("arena world not loaded or configuration incomplete");
            return;
        }
        PoolGrid scanned = PoolGrid.scan(world, region);
        if (scanned == null) {
            abort("the pool of the arena does not contain any water");
            return;
        }
        List<GamePlayer> order = new ArrayList<>(players.values());
        Collections.shuffle(order, ThreadLocalRandom.current());
        // Players who did not pick a block get one now (before anything is modified).
        if (!assignColors(order)) {
            plugin.getLogger().warning("Game in arena " + arena.name() + " cannot start: block-selector.unique-blocks is"
                    + " true but there are fewer allowed blocks than players. Add blocks to block-selector.materials.");
            broadcast("game.not-enough-blocks", basePlaceholders());
            reset();
            return;
        }
        grid = scanned;
        // Save the original blocks before touching anything (in memory + on disk for crash safety).
        backup = PoolBackup.capture(world, scanned.cellPositions());
        plugin.poolBackups().save(arena, backup);

        setState(ArenaState.PLAYING);
        initialPlayers = players.size();

        Placeholders orderPlaceholders = basePlaceholders()
                .raw("order", order.stream().map(this::coloredName).collect(Collectors.joining("<gray>, ")));
        for (GamePlayer gamePlayer : order) {
            gamePlayer.setStatus(PlayerStatus.ALIVE);
            gamePlayer.setLives(settings().startingLives());
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player == null) {
                continue;
            }
            plugin.players().prepareParticipant(player);
            plugin.players().teleport(player, spectator);
            PlayerColor color = gamePlayer.color();
            Placeholders placeholders = basePlaceholders().text("player", player.getName())
                    .raw("color", color != null ? color.displayName() : "");
            messages().title(player, "game.start.title", "game.start.subtitle", placeholders);
            messages().send(player, "game.start.color", placeholders);
        }
        broadcast("game.start.message", orderPlaceholders);
        plugin.configManager().playSound(audience(), GameSound.START);

        turns.start(order.stream().map(GamePlayer::uuid).toList(), scanned,
                new JumpDetector(scanned, jump, settings().jumpZoneMargin()));
        plugin.getLogger().info("Game started in arena " + arena.name() + " with " + initialPlayers + " players ("
                + scanned.totalCells() + " water cells).");
        refreshScoreboards();
    }

    /**
     * Gives a block to every player who did not choose one in the selector, at
     * random (or in list order) among the blocks nobody chose. With unique blocks,
     * a block is never given twice; otherwise the list wraps around.
     *
     * @return false if unique blocks are required but there are not enough of them
     */
    private boolean assignColors(List<GamePlayer> order) {
        Settings settings = settings();
        List<Material> available = new ArrayList<>(settings.colors());
        for (GamePlayer gamePlayer : order) {
            if (gamePlayer.color() != null) {
                available.remove(gamePlayer.color().block());
            }
        }
        if (available.isEmpty() && !settings.uniqueBlocks()) {
            available.addAll(settings.colors());
        }
        if (settings.randomColors()) {
            Collections.shuffle(available, ThreadLocalRandom.current());
        }
        int next = 0;
        for (GamePlayer gamePlayer : order) {
            if (gamePlayer.color() != null) {
                continue;
            }
            Material block;
            if (next < available.size()) {
                block = available.get(next++);
            } else if (!settings.uniqueBlocks() && !available.isEmpty()) {
                block = available.get(next++ % available.size());
            } else {
                return false;
            }
            gamePlayer.setColor(new PlayerColor(block, messages().colorName(block)));
        }
        return true;
    }

    // ------------------------------------------------------------------ block selector

    public enum BlockChoice { SELECTED, TAKEN, NOT_ALLOWED, NOT_IN_LOBBY }

    /**
     * Name of the other player of this session using {@code block}, or null.
     * Always null when unique blocks are disabled.
     */
    public @Nullable String blockOwner(Material block, UUID except) {
        if (!settings().uniqueBlocks()) {
            return null;
        }
        for (GamePlayer gamePlayer : players.values()) {
            PlayerColor color = gamePlayer.color();
            if (!gamePlayer.uuid().equals(except) && color != null && color.block() == block) {
                return gamePlayer.name();
            }
        }
        return null;
    }

    /**
     * Choice made in the selector menu. Kept in memory for this session (= this game) only.
     */
    public BlockChoice selectBlock(Player player, Material block) {
        GamePlayer gamePlayer = players.get(player.getUniqueId());
        if (gamePlayer == null || canSelectBlock(player) != SelectorAccess.ALLOWED) {
            return BlockChoice.NOT_IN_LOBBY;
        }
        if (!settings().colors().contains(block)) {
            return BlockChoice.NOT_ALLOWED;
        }
        if (blockOwner(block, player.getUniqueId()) != null) {
            return BlockChoice.TAKEN;
        }
        gamePlayer.setColor(new PlayerColor(block, messages().colorName(block)));
        plugin.debug("[" + arena.name() + "] " + player.getName() + " chose " + block);
        refreshBlockSelectors();
        refreshScoreboards();
        return BlockChoice.SELECTED;
    }

    public enum SelectorAccess { ALLOWED, DISABLED, NOT_IN_LOBBY }

    /**
     * Single rule used by /dac block, the lobby item and the menu clicks: the
     * selector is available to a participant while the arena is in its lobby
     * (WAITING or STARTING, i.e. before and during the countdown).
     */
    public SelectorAccess canSelectBlock(Player player) {
        if (!settings().blockSelectorEnabled()) {
            return SelectorAccess.DISABLED;
        }
        if (!players.containsKey(player.getUniqueId()) || !arena.state().isJoinable()) {
            return SelectorAccess.NOT_IN_LOBBY;
        }
        return SelectorAccess.ALLOWED;
    }

    /**
     * Opens the selector (lobby only).
     */
    public void openBlockSelector(Player player) {
        SelectorAccess access = canSelectBlock(player);
        plugin.debug("[" + arena.name() + "] Block selector requested by " + player.getName() + ": state="
                + arena.state() + ", enabled=" + settings().blockSelectorEnabled() + ", blocks="
                + settings().colors().size() + " -> " + access);
        switch (access) {
            case DISABLED -> messages().send(player, "selector.disabled");
            case NOT_IN_LOBBY -> messages().send(player, "selector.not-in-lobby");
            case ALLOWED -> {
                BlockSelectorMenu menu = new BlockSelectorMenu(plugin, this, player.getUniqueId());
                player.openInventory(menu.getInventory());
                if (player.getOpenInventory().getTopInventory().getHolder(false) != menu) {
                    // A listener cancelled the InventoryOpenEvent: say it instead of failing silently.
                    plugin.getLogger().warning("The block selector of " + player.getName()
                            + " could not be opened: its InventoryOpenEvent was cancelled by a listener"
                            + " (another plugin protecting inventories?).");
                } else {
                    plugin.debug("[" + arena.name() + "] Block selector opened for " + player.getName()
                            + " (" + menu.getInventory().getSize() + " slots)");
                }
            }
        }
    }

    /**
     * Re-renders the open menus of this lobby (a block was taken or freed).
     */
    private void refreshBlockSelectors() {
        for (Player player : onlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof BlockSelectorMenu menu
                    && menu.session() == this) {
                menu.render();
            }
        }
    }

    /**
     * Hotbar item opening the selector, given in the lobby only. Never given when
     * the inventory is not managed by the plugin (game.restore-inventory: false).
     */
    private void giveLobbyItems(Player player) {
        Settings settings = settings();
        if (settings.blockSelectorEnabled() && settings.lobbyItem() && settings.restoreInventory()
                && arena.state().isJoinable()) {
            player.getInventory().setItem(settings.lobbyItemSlot(), BlockSelectorItem.create(plugin));
        }
    }

    /**
     * Player name using the leading color tags of their color display name
     * (e.g. {@code <aqua>Bleu clair} gives {@code <aqua>Loan}).
     */
    private String coloredName(GamePlayer player) {
        PlayerColor color = player.color();
        String name = MiniMessage.miniMessage().escapeTags(player.name());
        if (color == null) {
            return "<white>" + name;
        }
        Matcher matcher = LEADING_TAGS.matcher(color.displayName());
        return (matcher.find() ? matcher.group(1) : "") + name + "<reset>";
    }

    // ------------------------------------------------------------------ eliminations

    /**
     * Marks a player as eliminated and turns them into a spectator. Does not
     * decide what happens next: callers check the end of the game.
     */
    void eliminate(GamePlayer gamePlayer, FailReason reason) {
        if (gamePlayer.status() == PlayerStatus.ELIMINATED) {
            return;
        }
        gamePlayer.setStatus(PlayerStatus.ELIMINATED);
        Placeholders placeholders = basePlaceholders().text("player", gamePlayer.name());
        if (reason.messageKey() != null) {
            broadcast(reason.messageKey(), placeholders);
            plugin.configManager().playSound(audience(), GameSound.ELIMINATION);
        }
        Player player = players.containsKey(gamePlayer.uuid()) ? Bukkit.getPlayer(gamePlayer.uuid()) : null;
        if (player != null && player.isOnline()) {
            messages().title(player, "game.eliminated-title", "game.eliminated-subtitle", placeholders);
            if (!player.isDead()) {
                applySpectator(player);
            }
        }
        plugin.debug("[" + arena.name() + "] " + gamePlayer.name() + " eliminated (" + reason + ")");
    }

    /**
     * A missed jump costs a life; the player is eliminated at 0 life. Leaving the
     * game (QUIT) always eliminates. Does not decide what happens next: callers
     * check the end of the game.
     */
    void handleFailedJump(GamePlayer gamePlayer, FailReason reason) {
        Player player = players.containsKey(gamePlayer.uuid()) ? Bukkit.getPlayer(gamePlayer.uuid()) : null;
        if (reason == FailReason.QUIT || player == null || !player.isOnline()) {
            eliminate(gamePlayer, reason);
            return;
        }
        int remaining = gamePlayer.loseLife();
        Placeholders placeholders = livesPlaceholders(gamePlayer);
        if (remaining <= 0) {
            eliminate(gamePlayer, reason);
            messages().send(player, "game.eliminated-no-lives", placeholders);
            return;
        }
        messages().send(player, "game.life-lost", placeholders);
        messages().send(audienceExcept(gamePlayer.uuid()), "game.life-lost-broadcast", placeholders);
        messages().title(player, "game.life-lost-title", "game.life-lost-subtitle", placeholders);
        plugin.configManager().playSound(audience(), GameSound.LIFE_LOST);
        // Still alive: back to the waiting area for the next turns (a dead player is placed on respawn).
        if (!player.isDead()) {
            plugin.players().prepareParticipant(player);
            Location spectator = arena.spectatorLocation();
            if (spectator != null) {
                plugin.players().teleport(player, spectator);
            }
        }
        plugin.debug("[" + arena.name() + "] " + gamePlayer.name() + " lost a life (" + reason + "), "
                + remaining + " left");
    }

    /**
     * Successful jump into an isolated 1x1 hole: +1 life, capped at max-lives.
     */
    void rewardPerfectJump(GamePlayer gamePlayer, Player player) {
        boolean gained = gamePlayer.gainLife(settings().maxLives());
        Placeholders placeholders = livesPlaceholders(gamePlayer);
        messages().send(audienceExcept(gamePlayer.uuid()), "game.perfect-jump-broadcast", placeholders);
        if (gained) {
            messages().send(player, "game.life-gained", placeholders);
            messages().title(player, "game.life-gained-title", "game.life-gained-subtitle", placeholders);
        } else {
            messages().send(player, "game.lives-max", placeholders);
            messages().title(player, "game.success-title", "game.success-subtitle", placeholders);
        }
        // Personal "Dé à Coudre" sound: played to this Player only (never world.playSound);
        // the others hear the usual success sound.
        plugin.configManager().playSound(player, GameSound.PERFECT_JUMP);
        plugin.configManager().playSound(audienceExcept(gamePlayer.uuid()), GameSound.SUCCESS);
        plugin.debug("[" + arena.name() + "] " + gamePlayer.name() + " perfect jump, lives: " + gamePlayer.lives());
    }

    private Placeholders livesPlaceholders(GamePlayer gamePlayer) {
        PlayerColor color = gamePlayer.color();
        return basePlaceholders()
                .text("player", gamePlayer.name())
                .raw("color", color != null ? color.displayName() : "")
                .text("lives", gamePlayer.lives())
                .text("max_lives", settings().maxLives());
    }

    private void applySpectator(Player player) {
        plugin.players().prepareSpectator(player);
        Location spectator = arena.spectatorLocation();
        if (spectator != null) {
            plugin.players().teleport(player, spectator);
        }
        messages().send(player, "game.spectator");
    }

    /**
     * Checks the end conditions.
     *
     * @return true if the game is over (or not running anymore)
     */
    boolean checkEnd() {
        if (arena.state() != ArenaState.PLAYING) {
            return true;
        }
        List<GamePlayer> alive = players.values().stream().filter(GamePlayer::isAlive).toList();
        if (alive.isEmpty()) {
            endGame(EndResult.NO_WINNER, null, alive);
            return true;
        }
        if (initialPlayers > 1 && alive.size() == 1) {
            endGame(EndResult.WINNER, alive.getFirst(), alive);
            return true;
        }
        if (grid != null && grid.freeCount() == 0) {
            broadcast("game.pool-full", basePlaceholders());
            if (alive.size() == 1) {
                endGame(EndResult.WINNER, alive.getFirst(), alive);
            } else {
                endGame(EndResult.DRAW, null, alive);
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ end

    private void endGame(EndResult result, @Nullable GamePlayer winner, List<GamePlayer> survivors) {
        turns.cancel();
        setState(ArenaState.ENDING);
        Audience audience = audience();
        Placeholders placeholders = basePlaceholders();
        switch (result) {
            case WINNER -> {
                if (winner != null) {
                    PlayerColor color = winner.color();
                    placeholders.text("player", winner.name()).raw("color", color != null ? color.displayName() : "");
                }
                broadcast("game.winner", placeholders);
                messages().title(audience, "game.victory-title", "game.victory-subtitle", placeholders);
                plugin.configManager().playSound(audience, GameSound.VICTORY);
                if (winner != null) {
                    launchFireworks(winner);
                }
            }
            case NO_WINNER -> {
                broadcast("game.no-winner", placeholders);
                messages().title(audience, "game.no-winner-title", "game.no-winner-subtitle", placeholders);
                plugin.configManager().playSound(audience, GameSound.END);
            }
            case DRAW -> {
                placeholders.raw("players", survivors.stream().map(this::coloredName).collect(Collectors.joining("<gray>, ")));
                broadcast("game.draw", placeholders);
                broadcast("game.draw-survivors", placeholders);
                messages().title(audience, "game.draw-title", "game.draw-subtitle", placeholders);
                plugin.configManager().playSound(audience, GameSound.END);
            }
        }
        plugin.getLogger().info("Game ended in arena " + arena.name()
                + (winner != null ? " (winner: " + winner.name() + ")." : result == EndResult.DRAW ? " (draw)." : " (no winner)."));

        if (players.isEmpty()) {
            reset();
            return;
        }
        broadcast("game.ending", basePlaceholders().text("seconds", settings().endingDuration()));
        tasks.runLater(Slot.ENDING, settings().endingDuration() * 20L, guarded(this::reset));
        refreshScoreboards();
    }

    private void launchFireworks(GamePlayer winner) {
        if (!settings().fireworks()) {
            return;
        }
        PlayerColor playerColor = winner.color();
        Color color = playerColor != null ? ColorUtil.fireworkColor(playerColor.block()) : Color.YELLOW;
        FireworkEffect effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(color)
                .withFade(Color.WHITE)
                .trail(true)
                .flicker(true)
                .build();
        int[] remaining = {5};
        tasks.runTimer(Slot.EFFECTS, 1, 15, guarded(() -> {
            Player player = Bukkit.getPlayer(winner.uuid());
            if (remaining[0]-- <= 0 || player == null || !players.containsKey(winner.uuid())) {
                tasks.cancel(Slot.EFFECTS);
                return;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location location = player.getLocation().add(random.nextDouble(-2, 2), 1, random.nextDouble(-2, 2));
            player.getWorld().spawn(location, Firework.class, firework -> {
                FireworkMeta meta = firework.getFireworkMeta();
                meta.addEffect(effect);
                meta.setPower(1);
                firework.setFireworkMeta(meta);
                firework.getPersistentDataContainer().set(plugin.fireworkKey(), PersistentDataType.BOOLEAN, true);
            });
        }));
    }

    /**
     * Admin stop: players and pool restored immediately.
     */
    void stop(boolean announce) {
        if (disposed) {
            return;
        }
        if (announce) {
            broadcast("game.stopped", basePlaceholders());
        }
        reset();
    }

    /**
     * Restores everything and destroys the session. Safe to call several times.
     */
    void reset() {
        if (disposed) {
            return;
        }
        disposed = true;
        setState(ArenaState.RESETTING);
        tasks.close();
        turns.cancel();

        for (GamePlayer gamePlayer : new ArrayList<>(players.values())) {
            manager.unbind(gamePlayer.uuid());
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player == null) {
                continue;
            }
            try {
                plugin.scoreboards().remove(player);
                plugin.players().restore(player, true);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "Unable to restore " + gamePlayer.name(), exception);
            }
        }
        players.clear();
        restorePool();
        manager.removeSession(this);
        setState(arena.isEnabled() ? ArenaState.WAITING : ArenaState.DISABLED);
        plugin.debug("[" + arena.name() + "] Session reset.");
    }

    private void restorePool() {
        PoolBackup saved = backup;
        backup = null;
        grid = null;
        if (saved == null) {
            return;
        }
        World world = arena.world();
        if (world == null) {
            // The backup file stays on disk and is applied when the world is loaded again.
            plugin.getLogger().warning("World of arena " + arena.name() + " is not loaded, its pool will be restored on load.");
            return;
        }
        saved.restore(world);
        plugin.poolBackups().delete(arena);
        plugin.debug("[" + arena.name() + "] Pool restored (" + saved.size() + " blocks).");
    }

    // ------------------------------------------------------------------ in-game events

    /**
     * Where a participant must stay when it is not their turn.
     */
    public @Nullable Location anchor() {
        return arena.state().isJoinable() ? arena.lobbyLocation() : arena.spectatorLocation();
    }

    /**
     * Keeps waiting players and spectators in the arena area.
     */
    public void enforceArea(Player player, Location to) {
        GamePlayer gamePlayer = players.get(player.getUniqueId());
        if (gamePlayer == null || turns.isActive(player.getUniqueId())) {
            return;
        }
        Location anchor = anchor();
        if (anchor == null) {
            return;
        }
        int maxDistance = settings().maxDistance();
        boolean outside = to.getWorld() != anchor.getWorld() || to.distanceSquared(anchor) > (double) maxDistance * maxDistance;
        // Alive players waiting for their turn must not stand in the pool or on the platform
        // (ignored if the anchor itself is there, to never loop).
        boolean blocking = !outside && arena.state() == ArenaState.PLAYING && gamePlayer.isAlive()
                && isReservedArea(to) && !isReservedArea(anchor);
        if (outside || blocking) {
            plugin.players().teleport(player, anchor);
            if (outside) {
                messages().send(player, "game.area-limit");
            }
        }
    }

    /**
     * The pool (plus one block around, up to two blocks above the surface) and the jump platform.
     */
    private boolean isReservedArea(Location location) {
        Cuboid region = arena.poolRegion();
        PoolGrid pool = grid;
        if (region != null && pool != null) {
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            if (x >= region.minX() - 1 && x <= region.maxX() + 1 && z >= region.minZ() - 1
                    && z <= region.maxZ() + 1 && y >= region.minY() - 1 && y <= pool.surfaceY() + 2) {
                return true;
            }
        }
        Location jump = arena.jumpLocation();
        return jump != null && jump.getWorld() == location.getWorld() && location.distanceSquared(jump) < 2.25;
    }

    public void handleDeath(Player player) {
        GamePlayer gamePlayer = players.get(player.getUniqueId());
        if (gamePlayer == null) {
            return;
        }
        if (arena.state() == ArenaState.PLAYING && gamePlayer.isAlive()) {
            if (turns.isActive(player.getUniqueId())) {
                turns.failActive(player.getUniqueId(), FailReason.DEATH);
            } else {
                eliminate(gamePlayer, FailReason.DEATH);
                checkEnd();
            }
        }
        // Skip the death screen.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        });
    }

    /**
     * @return the respawn location, or null if the player is not in this session
     */
    public @Nullable Location handleRespawn(Player player) {
        if (!players.containsKey(player.getUniqueId())) {
            return null;
        }
        plugin.getServer().getScheduler().runTask(plugin, guarded(() -> {
            GamePlayer gamePlayer = players.get(player.getUniqueId());
            if (gamePlayer == null || !player.isOnline()) {
                return;
            }
            if (gamePlayer.status() == PlayerStatus.ELIMINATED) {
                applySpectator(player);
            } else {
                plugin.players().prepareParticipant(player);
                giveLobbyItems(player);
            }
        }));
        return anchor();
    }

    // ------------------------------------------------------------------ scoreboard

    void refreshScoreboards() {
        if (disposed) {
            return;
        }
        Settings settings = settings();
        String path = switch (arena.state()) {
            case WAITING, STARTING -> "scoreboard.lobby";
            case PLAYING -> "scoreboard.game";
            default -> "scoreboard.ending";
        };
        UUID active = turns.activePlayer();
        GamePlayer activePlayer = active == null ? null : players.get(active);
        int secondsLeft = turns.secondsLeft();
        int needed = Math.max(0, arena.minPlayers(settings) - players.size());
        String countdownText = countdown >= 0
                ? messages().apply(messages().raw("scoreboard.countdown"), Placeholders.create().text("seconds", countdown))
                : messages().apply(messages().raw("scoreboard.waiting-players"), Placeholders.create().text("needed", needed));
        PoolGrid pool = grid;
        String heartFull = messages().raw("scoreboard.heart-full");
        String heartEmpty = messages().raw("scoreboard.heart-empty");

        for (GamePlayer gamePlayer : players.values()) {
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player == null) {
                continue;
            }
            PlayerColor color = gamePlayer.color();
            Placeholders placeholders = basePlaceholders()
                    .text("player", player.getName())
                    .text("total", initialPlayers)
                    .text("server", settings.serverName())
                    .raw("countdown", countdownText)
                    .raw("turn", activePlayer != null ? coloredName(activePlayer) : messages().raw("words.none"))
                    .text("time", secondsLeft >= 0 ? secondsLeft + "s" : "-")
                    .text("free", pool != null ? pool.freeCount() : 0)
                    .text("jumps", gamePlayer.successfulJumps())
                    .raw("block", color != null ? color.displayName() : messages().raw("scoreboard.block-random"))
                    .text("lives", gamePlayer.lives())
                    .text("max_lives", settings.maxLives())
                    .raw("hearts", heartFull.repeat(gamePlayer.lives())
                            + heartEmpty.repeat(Math.max(0, settings.maxLives() - gamePlayer.lives())))
                    .raw("color", color != null ? color.displayName() : messages().raw("words.none"))
                    .raw("status", messages().raw("scoreboard.status." + gamePlayer.status().name()));
            Component title = messages().get("scoreboard.title", placeholders);
            plugin.scoreboards().update(player, title, messages().getList(path, placeholders));
        }
    }
}
