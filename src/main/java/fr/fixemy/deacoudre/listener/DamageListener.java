package fr.fixemy.deacoudre.listener;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.game.GameSession;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * Participants never take damage (lobby, waiting, jumping, spectating) and cannot
 * hurt anyone. Fall damage of the jumper is used as a "hard landing" signal
 * before being cancelled. Other players of the server are not affected.
 */
public final class DamageListener implements Listener {

    private final DeACoudrePlugin plugin;

    public DamageListener(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isParticipant(Entity entity) {
        return entity instanceof Player player && plugin.games().isInGame(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            GameSession session = plugin.games().session(player.getUniqueId());
            if (session != null) {
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    session.runSafely(() -> session.turns().handleFallDamage(player));
                }
                event.setCancelled(true);
                return;
            }
        }
        // Celebration fireworks never hurt anybody.
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(plugin.fireworkKey())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (isParticipant(damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (isParticipant(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && isParticipant(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (isParticipant(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
