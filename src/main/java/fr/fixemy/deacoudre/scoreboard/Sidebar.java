package fr.fixemy.deacoudre.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Personal sidebar of one player.
 * <p>
 * Each line is a fixed score entry whose visible text is its {@code customName}
 * and whose number is hidden ({@link NumberFormat#blank()}). Only the lines whose
 * text changed are sent again, so there is no flicker and no rebuild.
 */
final class Sidebar {

    private static final int MAX_LINES = 15;

    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<Component> lines = new ArrayList<>();
    private Component title;

    Sidebar(Component title) {
        this.title = title;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("deacoudre", Criteria.DUMMY, title, RenderType.INTEGER);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.objective.numberFormat(NumberFormat.blank());
    }

    Scoreboard scoreboard() {
        return scoreboard;
    }

    void update(Component newTitle, List<Component> newLines) {
        if (!newTitle.equals(title)) {
            title = newTitle;
            objective.displayName(newTitle);
        }
        int size = Math.min(newLines.size(), MAX_LINES);
        boolean resized = size != lines.size();
        for (int i = 0; i < size; i++) {
            Component line = newLines.get(i);
            if (!resized && line.equals(lines.get(i))) {
                continue;
            }
            Score score = objective.getScore(entry(i));
            score.setScore(size - i);
            score.customName(line);
        }
        for (int i = size; i < lines.size(); i++) {
            scoreboard.resetScores(entry(i));
        }
        lines.clear();
        lines.addAll(newLines.subList(0, size));
    }

    private static String entry(int index) {
        return "line_" + index;
    }
}
