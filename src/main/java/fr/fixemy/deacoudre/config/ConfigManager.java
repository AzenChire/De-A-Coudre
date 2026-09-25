package fr.fixemy.deacoudre.config;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.gui.BlockSelectorMenu;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads config.yml into an immutable {@link Settings} object and validates values.
 * An invalid value never prevents the plugin from starting: it is reported with a
 * clear warning and replaced by a safe default.
 */
public final class ConfigManager {

    private static final List<String> DEFAULT_BLOCKS = List.of(
            "WHITE_CONCRETE", "ORANGE_CONCRETE", "MAGENTA_CONCRETE", "LIGHT_BLUE_CONCRETE",
            "YELLOW_CONCRETE", "LIME_CONCRETE", "PINK_CONCRETE", "GRAY_CONCRETE",
            "LIGHT_GRAY_CONCRETE", "CYAN_CONCRETE", "PURPLE_CONCRETE", "BLUE_CONCRETE",
            "BROWN_CONCRETE", "GREEN_CONCRETE", "RED_CONCRETE", "BLACK_CONCRETE");

    /** Full solid blocks that are still not suitable as player blocks. */
    private static final Set<String> DENIED_BLOCKS = Set.of(
            "TNT", "BEDROCK", "BARRIER", "LIGHT", "STRUCTURE_VOID", "REINFORCED_DEEPSLATE",
            "END_PORTAL_FRAME", "BUDDING_AMETHYST", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
            "REPEATING_COMMAND_BLOCK", "STRUCTURE_BLOCK", "JIGSAW", "TEST_BLOCK", "TEST_INSTANCE_BLOCK",
            "SPAWNER", "TRIAL_SPAWNER", "VAULT", "INFESTED_STONE", "INFESTED_COBBLESTONE",
            "INFESTED_STONE_BRICKS", "INFESTED_MOSSY_STONE_BRICKS", "INFESTED_CRACKED_STONE_BRICKS",
            "INFESTED_CHISELED_STONE_BRICKS", "INFESTED_DEEPSLATE", "REDSTONE_BLOCK", "OBSERVER",
            "PISTON", "STICKY_PISTON", "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR", "SCULK_SHRIEKER",
            "SCULK_CATALYST", "RESPAWN_ANCHOR", "MAGMA_BLOCK", "ICE", "PACKED_ICE", "BLUE_ICE", "FROSTED_ICE");

    private final DeACoudrePlugin plugin;
    private Settings settings;
    private final Map<GameSound, SoundEffect> sounds = new EnumMap<>(GameSound.class);

    public ConfigManager(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        int minPlayers = Math.max(1, config.getInt("game.min-players", 2));
        int maxPlayers = Math.max(1, config.getInt("game.max-players", 12));
        if (minPlayers > maxPlayers) {
            plugin.getLogger().warning("game.min-players (" + minPlayers + ") is greater than game.max-players ("
                    + maxPlayers + "); using " + maxPlayers + " for both.");
            minPlayers = maxPlayers;
        }

        Settings.ExternalTeleportMode externalTeleport = parseEnum(Settings.ExternalTeleportMode.class,
                config.getString("protection.external-teleport"), Settings.ExternalTeleportMode.CANCEL,
                "protection.external-teleport");

        int maxLives = Math.clamp(config.getInt("game.max-lives", 3), 1, 10);
        int startingLives = Math.clamp(config.getInt("game.starting-lives", 1), 1, maxLives);
        if (startingLives != config.getInt("game.starting-lives", 1)) {
            plugin.getLogger().warning("game.starting-lives must be between 1 and game.max-lives (" + maxLives
                    + "); using " + startingLives + ".");
        }

        Set<String> allowedCommands = new LinkedHashSet<>();
        for (String command : config.getStringList("protection.allowed-commands")) {
            allowedCommands.add(command.toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        }
        allowedCommands.add("dac");
        allowedCommands.add("deacoudre");

        List<Material> blocks = loadBlocks(config);
        boolean uniqueBlocks = config.getBoolean("block-selector.unique-blocks", true);
        if (uniqueBlocks && blocks.size() < maxPlayers) {
            plugin.getLogger().warning("block-selector.unique-blocks is true but only " + blocks.size()
                    + " blocks are available for game.max-players = " + maxPlayers
                    + ". Arenas allowing more players than blocks cannot be enabled.");
        }

        Material lobbyItemMaterial = Material.matchMaterial(config.getString("block-selector.lobby-item-material", "NETHER_STAR"));
        if (lobbyItemMaterial == null || !lobbyItemMaterial.isItem() || lobbyItemMaterial.isAir()) {
            plugin.getLogger().warning("Invalid block-selector.lobby-item-material, using NETHER_STAR.");
            lobbyItemMaterial = Material.NETHER_STAR;
        }
        int lobbyItemSlot = config.getInt("block-selector.lobby-item-slot", 4);
        if (lobbyItemSlot < 0 || lobbyItemSlot > 8) {
            plugin.getLogger().warning("block-selector.lobby-item-slot must be between 0 and 8 (hotbar), using 4.");
            lobbyItemSlot = 4;
        }

        this.settings = new Settings(
                minPlayers,
                maxPlayers,
                Math.max(1, config.getInt("game.starting-countdown", 10)),
                Math.max(0, config.getInt("game.full-countdown", 5)),
                Set.copyOf(config.getIntegerList("game.countdown-chat-seconds")),
                Set.copyOf(config.getIntegerList("game.countdown-title-seconds")),
                Math.max(3, config.getInt("game.turn-duration", 15)),
                Math.max(1, config.getInt("game.fall-grace", 5)),
                Math.max(0, config.getInt("game.turn-freeze-ticks", 10)),
                Math.max(1, config.getInt("game.turn-delay-ticks", 30)),
                Math.max(1, config.getInt("game.ending-duration", 8)),
                config.getBoolean("game.restore-inventory", true),
                config.getBoolean("game.sounds", true),
                config.getBoolean("game.fireworks", true),
                !"ORDERED".equalsIgnoreCase(config.getString("game.color-assignment", "RANDOM")),
                blocks,
                Math.max(1, config.getInt("game.jump-zone-margin", 4)),
                startingLives,
                maxLives,
                config.getBoolean("block-selector.enabled", true),
                uniqueBlocks,
                config.getBoolean("block-selector.lobby-item", true),
                lobbyItemMaterial,
                lobbyItemSlot,
                config.getBoolean("turn-bossbar.enabled", false),
                parseEnum(BossBar.Color.class, config.getString("turn-bossbar.color"), BossBar.Color.YELLOW, "turn-bossbar.color"),
                parseEnum(BossBar.Overlay.class, config.getString("turn-bossbar.overlay"), BossBar.Overlay.PROGRESS, "turn-bossbar.overlay"),
                Math.max(1, config.getInt("pool.max-width", 64)),
                Math.max(1, config.getInt("pool.max-length", 64)),
                Math.max(1, config.getInt("pool.max-depth", 32)),
                config.getBoolean("scoreboard.enabled", true),
                config.getString("scoreboard.server-name", "play.example.fr"),
                config.getBoolean("protection.block-commands", false),
                Set.copyOf(allowedCommands),
                Math.max(5, config.getInt("protection.max-distance", 40)),
                externalTeleport,
                config.getString("language", "en").toLowerCase(Locale.ROOT).trim(),
                config.getBoolean("debug.enabled", false));

        loadSounds(config.getConfigurationSection("sounds"));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, @Nullable String value, E fallback, String path) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid value '" + value + "' for " + path + ", using " + fallback.name() + ".");
            return fallback;
        }
    }

    // ------------------------------------------------------------------ blocks

    /**
     * Whitelist of player blocks (block-selector.materials). The former game.colors
     * list is still read when it is the only one explicitly set (migration).
     */
    private List<Material> loadBlocks(FileConfiguration config) {
        List<String> names;
        if (!config.isSet("block-selector.materials") && config.isSet("game.colors")) {
            plugin.getLogger().warning("game.colors is deprecated, move your list to block-selector.materials.");
            names = config.getStringList("game.colors");
        } else {
            names = config.getStringList("block-selector.materials");
        }
        List<Material> blocks = new ArrayList<>();
        for (String name : names.isEmpty() ? DEFAULT_BLOCKS : names) {
            Material material = Material.matchMaterial(name);
            String problem = blockProblem(material);
            if (problem != null) {
                plugin.getLogger().warning("Ignoring block '" + name + "' in block-selector.materials: " + problem + ".");
                continue;
            }
            if (!blocks.contains(material)) {
                blocks.add(material);
            }
        }
        if (blocks.isEmpty()) {
            plugin.getLogger().warning("No valid block in block-selector.materials, using the default concrete list.");
            DEFAULT_BLOCKS.forEach(name -> blocks.add(Material.valueOf(name)));
        }
        if (blocks.size() > BlockSelectorMenu.MAX_SLOTS) {
            plugin.getLogger().warning("block-selector.materials contains " + blocks.size() + " blocks, only the first "
                    + BlockSelectorMenu.MAX_SLOTS + " are used.");
            return List.copyOf(blocks.subList(0, BlockSelectorMenu.MAX_SLOTS));
        }
        return List.copyOf(blocks);
    }

    /**
     * @return why the material cannot be used as a player block, or null if it can
     */
    private static @Nullable String blockProblem(@Nullable Material material) {
        if (material == null) {
            return "unknown material";
        }
        if (!material.isBlock() || material.isAir()) {
            return "not a block";
        }
        if (material.hasGravity()) {
            return "affected by gravity";
        }
        if (!material.isSolid() || !material.isOccluding()) {
            return "not a full solid block";
        }
        if (DENIED_BLOCKS.contains(material.name())) {
            return "technical or problematic block";
        }
        try {
            if (material.createBlockData().createBlockState() instanceof TileState) {
                return "block with an inventory or a block entity";
            }
        } catch (RuntimeException exception) {
            return "block cannot be created";
        }
        return null;
    }

    // ------------------------------------------------------------------ sounds

    private void loadSounds(ConfigurationSection section) {
        sounds.clear();
        Registry<org.bukkit.Sound> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT);
        for (GameSound type : GameSound.values()) {
            ConfigurationSection entry = section == null ? null : section.getConfigurationSection(type.configKey());
            if (entry == null || !entry.getBoolean("enabled", true)) {
                continue;
            }
            // No explicit fallback: a sound missing from an older config.yml uses the default of the jar.
            String name = entry.getString("sound");
            if (name == null) {
                continue;
            }
            Key key;
            try {
                key = Key.key(name.toLowerCase(Locale.ROOT));
            } catch (InvalidKeyException exception) {
                plugin.getLogger().warning("Invalid sound key '" + name + "' for sounds." + type.configKey());
                continue;
            }
            if (registry.get(key) == null) {
                // Could be a resource pack sound: keep it but warn the administrator.
                plugin.getLogger().warning("Unknown sound '" + key.asString() + "' for sounds." + type.configKey()
                        + " (not a vanilla sound, it will only work with a resource pack).");
            }
            float volume = (float) (entry.contains("volume") ? entry.getDouble("volume") : 1.0);
            float pitch = clampPitch(entry.contains("pitch") ? entry.getDouble("pitch") : 1.0);
            Map<Integer, Float> pitchBySecond = new HashMap<>();
            ConfigurationSection steps = entry.getConfigurationSection("pitch-by-second");
            if (steps != null) {
                for (String step : steps.getKeys(false)) {
                    try {
                        pitchBySecond.put(Integer.parseInt(step.trim()), clampPitch(steps.getDouble(step)));
                    } catch (NumberFormatException exception) {
                        plugin.getLogger().warning("Invalid second '" + step + "' in sounds." + type.configKey() + ".pitch-by-second");
                    }
                }
            }
            sounds.put(type, SoundEffect.of(key, volume, pitch, pitchBySecond));
        }
    }

    private static float clampPitch(double pitch) {
        return (float) Math.clamp(pitch, 0.5, 2.0);
    }

    public Settings settings() {
        return settings;
    }

    /**
     * Plays a sound to the given audience only (a single Player for personal feedback).
     */
    public void playSound(Audience audience, GameSound type) {
        playSound(audience, type, -1);
    }

    /**
     * Same as {@link #playSound(Audience, GameSound)} with the pitch configured for {@code step}
     * in {@code pitch-by-second} (e.g. the countdown second), if any.
     */
    public void playSound(Audience audience, GameSound type, int step) {
        if (!settings.sounds()) {
            return;
        }
        SoundEffect effect = sounds.get(type);
        if (effect != null) {
            effect.play(audience, step);
        }
    }
}
