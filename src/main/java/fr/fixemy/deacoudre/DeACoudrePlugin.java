package fr.fixemy.deacoudre;

import fr.fixemy.deacoudre.arena.ArenaManager;
import fr.fixemy.deacoudre.arena.ArenaRepository;
import fr.fixemy.deacoudre.arena.ArenaValidator;
import fr.fixemy.deacoudre.arena.PoolBackupStore;
import fr.fixemy.deacoudre.command.DeACoudreCommand;
import fr.fixemy.deacoudre.config.ConfigManager;
import fr.fixemy.deacoudre.config.MessageManager;
import fr.fixemy.deacoudre.config.Settings;
import fr.fixemy.deacoudre.game.GameManager;
import fr.fixemy.deacoudre.listener.BlockListener;
import fr.fixemy.deacoudre.listener.BlockSelectorListener;
import fr.fixemy.deacoudre.listener.DamageListener;
import fr.fixemy.deacoudre.listener.InteractionListener;
import fr.fixemy.deacoudre.listener.PlayerDeathListener;
import fr.fixemy.deacoudre.listener.PlayerJoinListener;
import fr.fixemy.deacoudre.listener.PlayerMoveListener;
import fr.fixemy.deacoudre.listener.PlayerQuitListener;
import fr.fixemy.deacoudre.listener.TeleportListener;
import fr.fixemy.deacoudre.listener.WorldListener;
import fr.fixemy.deacoudre.player.PlayerManager;
import fr.fixemy.deacoudre.player.SnapshotStore;
import fr.fixemy.deacoudre.scoreboard.ScoreboardManager;
import fr.fixemy.deacoudre.util.AsyncFileWriter;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.List;

/**
 * Plugin entry point: only wires the services together. The logic lives in the
 * arena, game, player, listener, command, config and scoreboard packages.
 */
public final class DeACoudrePlugin extends JavaPlugin {

    private AsyncFileWriter fileWriter;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private ArenaManager arenaManager;
    private ArenaValidator arenaValidator;
    private PoolBackupStore poolBackupStore;
    private PlayerManager playerManager;
    private ScoreboardManager scoreboardManager;
    private GameManager gameManager;
    private NamespacedKey fireworkKey;
    private NamespacedKey selectorItemKey;

    @Override
    public void onEnable() {
        Path dataPath = getDataPath();
        fileWriter = new AsyncFileWriter(getLogger());
        fireworkKey = new NamespacedKey(this, "celebration");
        selectorItemKey = new NamespacedKey(this, "block_selector");

        configManager = new ConfigManager(this);
        configManager.load();
        messageManager = new MessageManager(this);
        messageManager.load();

        arenaValidator = new ArenaValidator(messageManager);
        arenaManager = new ArenaManager(new ArenaRepository(dataPath.resolve("arenas"), fileWriter, getLogger()), getLogger());
        arenaManager.loadAll();

        poolBackupStore = new PoolBackupStore(dataPath.resolve("data").resolve("pool-backups"), fileWriter, getLogger());
        poolBackupStore.recoverLoadedWorlds();

        SnapshotStore snapshotStore = new SnapshotStore(dataPath.resolve("data").resolve("snapshots"), fileWriter, getLogger());
        snapshotStore.init();
        playerManager = new PlayerManager(this, snapshotStore);
        scoreboardManager = new ScoreboardManager(this);
        gameManager = new GameManager(this);

        List<Listener> listeners = List.of(
                new PlayerJoinListener(this),
                new PlayerQuitListener(this),
                new PlayerMoveListener(this),
                new PlayerDeathListener(this),
                new BlockListener(this),
                new DamageListener(this),
                new InteractionListener(this),
                new TeleportListener(this),
                new WorldListener(this),
                new BlockSelectorListener(this));
        listeners.forEach(listener -> getServer().getPluginManager().registerEvents(listener, this));

        registerCommand("dac", "DeACoudre mini-game", List.of("deacoudre"), new DeACoudreCommand(this));

        // Players already online (plugin enabled after startup): recover pending snapshots.
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerManager.handleJoin(player);
        }
        getLogger().info("DeACoudre " + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        // Stops every session: tasks cancelled, players and pools restored synchronously.
        if (gameManager != null) {
            gameManager.shutdown();
        }
        getServer().getScheduler().cancelTasks(this);
        if (fileWriter != null) {
            fileWriter.shutdown();
        }
    }

    // ------------------------------------------------------------------ services

    public Settings settings() {
        return configManager.settings();
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public MessageManager messages() {
        return messageManager;
    }

    public ArenaManager arenas() {
        return arenaManager;
    }

    public ArenaValidator validator() {
        return arenaValidator;
    }

    public PoolBackupStore poolBackups() {
        return poolBackupStore;
    }

    public PlayerManager players() {
        return playerManager;
    }

    public ScoreboardManager scoreboards() {
        return scoreboardManager;
    }

    public GameManager games() {
        return gameManager;
    }

    public NamespacedKey fireworkKey() {
        return fireworkKey;
    }

    public NamespacedKey selectorItemKey() {
        return selectorItemKey;
    }

    /**
     * Runs on the main thread (immediately if already on it).
     */
    public void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else if (isEnabled()) {
            getServer().getScheduler().runTask(this, runnable);
        }
    }

    public void debug(String message) {
        if (configManager != null && configManager.settings().debug()) {
            getLogger().info("[debug] " + message);
        }
    }
}
