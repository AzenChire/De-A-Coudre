package fr.fixemy.deacoudre.config;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of config.yml. A new instance is built on every reload, so
 * running games simply read the current values when they need them.
 */
public record Settings(
        // game
        int minPlayers,
        int maxPlayers,
        int startingCountdown,
        int fullCountdown,
        Set<Integer> countdownChatSeconds,
        Set<Integer> countdownTitleSeconds,
        int turnDuration,
        int fallGrace,
        int turnFreezeTicks,
        int turnDelayTicks,
        int endingDuration,
        boolean restoreInventory,
        boolean sounds,
        boolean fireworks,
        boolean randomColors,
        /* Whitelist of player blocks (block-selector.materials), already validated. */
        List<Material> colors,
        int jumpZoneMargin,
        int startingLives,
        int maxLives,
        // block selector
        boolean blockSelectorEnabled,
        boolean uniqueBlocks,
        boolean lobbyItem,
        Material lobbyItemMaterial,
        int lobbyItemSlot,
        // turn boss bar
        boolean turnBossBar,
        BossBar.Color turnBossBarColor,
        BossBar.Overlay turnBossBarOverlay,
        // pool
        int poolMaxWidth,
        int poolMaxLength,
        int poolMaxDepth,
        // scoreboard
        boolean scoreboardEnabled,
        String serverName,
        // protection
        boolean blockCommands,
        Set<String> allowedCommands,
        int maxDistance,
        ExternalTeleportMode externalTeleport,
        // messages
        String language,
        // debug
        boolean debug
) {

    /**
     * What happens when another plugin (or a command) teleports a participant.
     */
    public enum ExternalTeleportMode {
        /** The teleport is cancelled, the player stays in the game. */
        CANCEL,
        /** The teleport happens and the player leaves the game. */
        LEAVE
    }

    /**
     * Whether an arena allowing {@code maxPlayers} can give every player a different block.
     */
    public boolean hasEnoughBlocksFor(int maxPlayers) {
        return !uniqueBlocks || colors.size() >= maxPlayers;
    }
}
