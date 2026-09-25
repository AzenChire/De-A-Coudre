package fr.fixemy.deacoudre.command;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Every /dac sub command with its permission. Usages and descriptions are
 * translatable texts (help.usages.* and help.descriptions.* in messages.yml).
 */
enum SubCommand {
    HELP(Permissions.PLAY, false, ArenaArgument.NONE),
    JOIN(Permissions.PLAY, true, ArenaArgument.JOINABLE),
    LEAVE(Permissions.PLAY, true, ArenaArgument.NONE),
    BLOCK(Permissions.PLAY, true, ArenaArgument.NONE),
    LIST(Permissions.PLAY, false, ArenaArgument.NONE),
    CREATE(Permissions.ADMIN, false, ArenaArgument.NONE),
    DELETE(Permissions.ADMIN, false, ArenaArgument.EXISTING),
    SETLOBBY(Permissions.ADMIN, true, ArenaArgument.EXISTING),
    SETJUMP(Permissions.ADMIN, true, ArenaArgument.EXISTING),
    SETSPECTATOR(Permissions.ADMIN, true, ArenaArgument.EXISTING),
    POS1(Permissions.ADMIN, true, ArenaArgument.EXISTING),
    POS2(Permissions.ADMIN, true, ArenaArgument.EXISTING),
    SETPLAYERS(Permissions.ADMIN, false, ArenaArgument.EXISTING),
    ENABLE(Permissions.ADMIN, false, ArenaArgument.EXISTING),
    DISABLE(Permissions.ADMIN, false, ArenaArgument.EXISTING),
    INFO(Permissions.ADMIN, false, ArenaArgument.EXISTING),
    STOP(Permissions.ADMIN, false, ArenaArgument.EXISTING),
    FORCESTART(Permissions.ADMIN, false, ArenaArgument.EXISTING),
    RELOAD(Permissions.ADMIN, false, ArenaArgument.NONE);

    enum ArenaArgument { NONE, EXISTING, JOINABLE }

    private final String permission;
    private final boolean playerOnly;
    private final ArenaArgument arenaArgument;

    SubCommand(String permission, boolean playerOnly, ArenaArgument arenaArgument) {
        this.permission = permission;
        this.playerOnly = playerOnly;
        this.arenaArgument = arenaArgument;
    }

    String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    boolean playerOnly() {
        return playerOnly;
    }

    ArenaArgument arenaArgument() {
        return arenaArgument;
    }

    boolean isAllowed(CommandSender sender) {
        return sender.hasPermission(permission) || sender.hasPermission(Permissions.ADMIN);
    }

    static @Nullable SubCommand find(String label) {
        for (SubCommand command : values()) {
            if (command.label().equalsIgnoreCase(label)) {
                return command;
            }
        }
        return null;
    }

    static final class Permissions {
        static final String PLAY = "deacoudre.play";
        static final String ADMIN = "deacoudre.admin";

        private Permissions() {
        }
    }
}
