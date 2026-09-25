package fr.fixemy.deacoudre.command;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.arena.Arena;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Suggestions for /dac, filtered by permission and by what was already typed.
 */
final class DeACoudreTabCompleter {

    private final DeACoudrePlugin plugin;

    DeACoudreTabCompleter(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    Collection<String> suggest(CommandSender sender, String[] args) {
        int index = Math.max(0, args.length - 1);
        String current = args.length == 0 ? "" : args[args.length - 1];

        if (index == 0) {
            List<String> labels = new ArrayList<>();
            for (SubCommand command : SubCommand.values()) {
                if (command.isAllowed(sender)) {
                    labels.add(command.label());
                }
            }
            return filter(labels, current);
        }

        SubCommand command = SubCommand.find(args[0]);
        if (command == null || !command.isAllowed(sender)) {
            return List.of();
        }
        if (index == 1) {
            return switch (command.arenaArgument()) {
                case EXISTING -> filter(plugin.arenas().names(), current);
                case JOINABLE -> filter(plugin.arenas().all().stream()
                        .filter(arena -> plugin.games().isJoinable(arena))
                        .map(Arena::name)
                        .toList(), current);
                case NONE -> List.of();
            };
        }
        if (command == SubCommand.SETPLAYERS) {
            if (index == 2) {
                return filter(List.of("reset", "1", "2", "4"), current);
            }
            if (index == 3 && !"reset".equalsIgnoreCase(args[2])) {
                return filter(List.of("4", "8", "12", "16"), current);
            }
        }
        return List.of();
    }

    private static List<String> filter(Collection<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .toList();
    }
}
