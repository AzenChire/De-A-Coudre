package fr.fixemy.deacoudre.game;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.Map;

/**
 * Every task started by a session goes through this registry, in a named slot.
 * Scheduling a task in a slot cancels the previous one, so an old turn timer can
 * never survive the next turn, and {@link #close()} guarantees nothing keeps
 * running once the session is destroyed.
 */
public final class TaskRegistry {

    public enum Slot {
        COUNTDOWN,
        TURN_START,
        TURN_TIMER,
        TURN_UNFREEZE,
        SCOREBOARD,
        ENDING,
        EFFECTS
    }

    private final Plugin plugin;
    private final Map<Slot, BukkitTask> tasks = new EnumMap<>(Slot.class);
    private boolean closed;

    public TaskRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    public void runLater(Slot slot, long delayTicks, Runnable runnable) {
        if (closed) {
            return;
        }
        cancel(slot);
        tasks.put(slot, plugin.getServer().getScheduler().runTaskLater(plugin, runnable, Math.max(1, delayTicks)));
    }

    public void runTimer(Slot slot, long delayTicks, long periodTicks, Runnable runnable) {
        if (closed) {
            return;
        }
        cancel(slot);
        tasks.put(slot, plugin.getServer().getScheduler().runTaskTimer(plugin, runnable, Math.max(1, delayTicks), periodTicks));
    }

    public void cancel(Slot slot) {
        BukkitTask task = tasks.remove(slot);
        if (task != null) {
            task.cancel();
        }
    }

    public void cancelAll() {
        for (BukkitTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }

    /**
     * Cancels everything and refuses any new task.
     */
    public void close() {
        cancelAll();
        closed = true;
    }
}
