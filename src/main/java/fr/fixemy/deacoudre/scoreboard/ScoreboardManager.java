package fr.fixemy.deacoudre.scoreboard;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows the mini-game sidebar to participants and gives them their previous
 * scoreboard back when they leave.
 */
public final class ScoreboardManager {

    private final DeACoudrePlugin plugin;
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();
    private final Map<UUID, Scoreboard> previous = new HashMap<>();

    public ScoreboardManager(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    public void update(Player player, Component title, List<Component> lines) {
        if (!plugin.settings().scoreboardEnabled()) {
            remove(player);
            return;
        }
        UUID uuid = player.getUniqueId();
        Sidebar sidebar = sidebars.get(uuid);
        if (sidebar == null) {
            sidebar = new Sidebar(title);
            previous.put(uuid, player.getScoreboard());
            sidebars.put(uuid, sidebar);
            player.setScoreboard(sidebar.scoreboard());
        }
        sidebar.update(title, lines);
    }

    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        Sidebar sidebar = sidebars.remove(uuid);
        Scoreboard old = previous.remove(uuid);
        if (sidebar != null && player.isOnline() && player.getScoreboard() == sidebar.scoreboard()) {
            player.setScoreboard(old != null ? old : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}
