package fr.fixemy.deacoudre.arena;

import fr.fixemy.deacoudre.config.MessageManager;
import fr.fixemy.deacoudre.config.Settings;
import fr.fixemy.deacoudre.util.Cuboid;
import fr.fixemy.deacoudre.util.Placeholders;
import fr.fixemy.deacoudre.util.StoredLocation;
import net.kyori.adventure.text.Component;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Checks that an arena is complete and playable. Every problem is reported
 * (not only the first one) so the administrator knows exactly what to fix.
 */
public final class ArenaValidator {

    public record Result(List<Component> issues, int surfaceY, int waterCells) {
        public boolean valid() {
            return issues.isEmpty();
        }
    }

    private final MessageManager messages;

    public ArenaValidator(MessageManager messages) {
        this.messages = messages;
    }

    public Result validate(Arena arena, Settings settings) {
        List<Component> issues = new ArrayList<>();
        int surfaceY = Integer.MIN_VALUE;
        int waterCells = 0;

        World world = null;
        if (arena.worldName() == null) {
            issues.add(issue("world-missing", Placeholders.create()));
        } else {
            world = arena.world();
            if (world == null) {
                issues.add(issue("world-not-loaded", Placeholders.create().text("world", arena.worldName())));
            }
        }
        if (arena.lobby() == null) {
            issues.add(issue("lobby-missing", Placeholders.create().text("arena", arena.name())));
        }
        if (arena.jump() == null) {
            issues.add(issue("jump-missing", Placeholders.create().text("arena", arena.name())));
        }
        if (arena.spectator() == null) {
            issues.add(issue("spectator-missing", Placeholders.create().text("arena", arena.name())));
        }
        if (arena.pos1() == null) {
            issues.add(issue("pos1-missing", Placeholders.create().text("arena", arena.name())));
        }
        if (arena.pos2() == null) {
            issues.add(issue("pos2-missing", Placeholders.create().text("arena", arena.name())));
        }

        int min = arena.minPlayers(settings);
        int max = arena.maxPlayers(settings);
        if (min < 1 || max < 1) {
            issues.add(issue("invalid-limits", Placeholders.create().text("min", min).text("max", max)));
        } else if (min > max) {
            issues.add(issue("min-greater-than-max", Placeholders.create().text("min", min).text("max", max)));
        }
        if (max >= 1 && !settings.hasEnoughBlocksFor(max)) {
            issues.add(issue("not-enough-blocks", Placeholders.create()
                    .text("blocks", settings.colors().size()).text("max", max)));
        }

        Cuboid region = arena.poolRegion();
        if (world != null && region != null) {
            boolean sizeOk = true;
            if (region.width() > settings.poolMaxWidth() || region.length() > settings.poolMaxLength()) {
                sizeOk = false;
                issues.add(issue("pool-too-large", Placeholders.create()
                        .text("width", region.width()).text("length", region.length())
                        .text("max_width", settings.poolMaxWidth()).text("max_length", settings.poolMaxLength())));
            }
            if (region.height() > settings.poolMaxDepth()) {
                sizeOk = false;
                issues.add(issue("pool-too-deep", Placeholders.create()
                        .text("depth", region.height()).text("max_depth", settings.poolMaxDepth())));
            }
            if (region.minY() < world.getMinHeight() || region.maxY() >= world.getMaxHeight()) {
                sizeOk = false;
                issues.add(issue("pool-out-of-world", Placeholders.create()));
            }
            if (sizeOk) {
                OptionalInt surface = PoolScanner.findSurfaceY(world, region);
                if (surface.isEmpty()) {
                    issues.add(issue("pool-no-water", Placeholders.create()));
                } else {
                    surfaceY = surface.getAsInt();
                    waterCells = PoolScanner.waterCells(world, region, surfaceY).size();
                }
            }
            checkPoint(issues, arena.jump(), region, "jump-inside-pool");
            checkPoint(issues, arena.lobby(), region, "lobby-inside-pool");
            checkPoint(issues, arena.spectator(), region, "spectator-inside-pool");
            if (arena.jump() != null && surfaceY != Integer.MIN_VALUE && arena.jump().y() < surfaceY + 2) {
                issues.add(issue("jump-too-low", Placeholders.create().text("surface", surfaceY)));
            }
        }
        return new Result(List.copyOf(issues), surfaceY, waterCells);
    }

    private void checkPoint(List<Component> issues, StoredLocation location, Cuboid region, String key) {
        if (location != null && region.contains(location.toBlockPos())) {
            issues.add(issue(key, Placeholders.create()));
        }
    }

    private Component issue(String key, Placeholders placeholders) {
        return messages.get("validation." + key, placeholders);
    }
}
