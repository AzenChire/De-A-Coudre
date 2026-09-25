package fr.fixemy.deacoudre.player;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Saves, prepares and restores the Minecraft state of participants.
 * <p>
 * Also owns the "internal teleport" marker used by the teleport listener to
 * distinguish the plugin's own teleports from external ones (avoids loops).
 */
public final class PlayerManager {

    private final DeACoudrePlugin plugin;
    private final SnapshotStore store;
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> awaitingRespawn = new HashMap<>();
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();

    public PlayerManager(DeACoudrePlugin plugin, SnapshotStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    // ------------------------------------------------------------------ snapshot

    public void saveSnapshot(Player player) {
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player, plugin.settings().restoreInventory());
        snapshots.put(player.getUniqueId(), snapshot);
        store.save(snapshot);
    }

    public boolean hasSnapshot(UUID uuid) {
        return snapshots.containsKey(uuid);
    }

    /**
     * Restores the original state of a player and forgets the snapshot.
     *
     * @param restoreLocation false when the player is being teleported elsewhere by someone else
     */
    public void restore(Player player, boolean restoreLocation) {
        PlayerSnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot == null) {
            return;
        }
        if (player.isDead()) {
            // A dead player cannot be teleported: finish the restoration on respawn.
            awaitingRespawn.put(player.getUniqueId(), snapshot);
            return;
        }
        apply(player, snapshot, restoreLocation);
    }

    private void apply(Player player, PlayerSnapshot snapshot, boolean restoreLocation) {
        try {
            snapshot.restoreState(player);
            Location target = snapshot.location();
            boolean teleported = !restoreLocation || target == null || teleport(player, target);
            snapshot.restoreFlight(player);
            if (teleported) {
                store.delete(player.getUniqueId());
            } else {
                plugin.getLogger().warning("Unable to teleport " + player.getName()
                        + " back to their original location, it will be retried on their next login.");
                store.saveLocationOnly(player.getUniqueId(), target);
                store.markPending(player.getUniqueId());
            }
        } catch (Exception exception) {
            // Keep the file: the state will be restored on the next login.
            store.markPending(player.getUniqueId());
            plugin.getLogger().log(Level.SEVERE, "Error while restoring " + player.getName()
                    + ", their snapshot is kept on disk for the next login.", exception);
        }
    }

    /**
     * Crash / failure recovery when a player logs in.
     */
    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        if (!store.hasPending(uuid) || snapshots.containsKey(uuid)) {
            return;
        }
        SnapshotStore.Recovery recovery = store.loadPending(uuid);
        if (recovery == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (recovery.snapshot() != null) {
                apply(player, recovery.snapshot(), true);
            } else if (recovery.locationOnly() != null) {
                if (teleport(player, recovery.locationOnly())) {
                    store.delete(uuid);
                }
            } else {
                store.delete(uuid);
            }
            plugin.messages().send(player, "game.restored");
            plugin.debug("Recovered state of " + player.getName() + " on login.");
        });
    }

    /**
     * @return true when the respawn was handled (player waiting for a restoration)
     */
    public boolean handleRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerSnapshot snapshot = awaitingRespawn.remove(player.getUniqueId());
        if (snapshot == null) {
            return false;
        }
        if (snapshot.location() != null) {
            event.setRespawnLocation(snapshot.location());
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                apply(player, snapshot, false);
            } else {
                store.markPending(player.getUniqueId());
            }
        });
        return true;
    }

    public void handleQuit(Player player) {
        if (awaitingRespawn.remove(player.getUniqueId()) != null) {
            store.markPending(player.getUniqueId());
        }
    }

    // ------------------------------------------------------------------ states

    /**
     * Clean state used in the lobby and for players waiting for their turn.
     */
    public void prepareParticipant(Player player) {
        resetState(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    /**
     * Spectator state for eliminated players: they can watch without interacting.
     */
    public void prepareSpectator(Player player) {
        resetState(player);
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void resetState(Player player) {
        player.closeInventory();
        if (plugin.settings().restoreInventory()) {
            player.getInventory().clear();
            player.setItemOnCursor(ItemStack.empty());
        }
        player.clearActivePotionEffects();
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
        player.setAbsorptionAmount(0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0f);
        player.setRemainingAir(player.getMaximumAir());
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setCollidable(false);
        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setVelocity(new Vector());
    }

    // ------------------------------------------------------------------ teleports

    /**
     * Synchronous teleport flagged as internal (not intercepted by the teleport listener).
     */
    public boolean teleport(Player player, Location location) {
        UUID uuid = player.getUniqueId();
        internalTeleports.add(uuid);
        try {
            player.setFallDistance(0f);
            player.setVelocity(new Vector());
            return player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            internalTeleports.remove(uuid);
        }
    }

    /**
     * Asynchronous (chunk loading off the main thread) teleport flagged as internal.
     */
    public CompletableFuture<Boolean> teleportAsync(Player player, Location location) {
        UUID uuid = player.getUniqueId();
        internalTeleports.add(uuid);
        player.setFallDistance(0f);
        return player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN)
                .whenComplete((result, error) -> internalTeleports.remove(uuid));
    }

    public boolean isInternalTeleport(UUID uuid) {
        return internalTeleports.contains(uuid);
    }
}
