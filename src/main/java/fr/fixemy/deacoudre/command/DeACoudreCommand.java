package fr.fixemy.deacoudre.command;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.arena.Arena;
import fr.fixemy.deacoudre.arena.ArenaRepository;
import fr.fixemy.deacoudre.arena.ArenaState;
import fr.fixemy.deacoudre.arena.ArenaValidator;
import fr.fixemy.deacoudre.config.MessageManager;
import fr.fixemy.deacoudre.game.GameSession;
import fr.fixemy.deacoudre.game.LeaveCause;
import fr.fixemy.deacoudre.util.BlockPos;
import fr.fixemy.deacoudre.util.Cuboid;
import fr.fixemy.deacoudre.util.Placeholders;
import fr.fixemy.deacoudre.util.StoredLocation;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * /dac (alias /deacoudre), registered through Paper's Brigadier based command API.
 */
public final class DeACoudreCommand implements BasicCommand {

    private final DeACoudrePlugin plugin;
    private final DeACoudreTabCompleter completer;

    public DeACoudreCommand(DeACoudrePlugin plugin) {
        this.plugin = plugin;
        this.completer = new DeACoudreTabCompleter(plugin);
    }

    private MessageManager messages() {
        return plugin.messages();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission(SubCommand.Permissions.PLAY) || sender.hasPermission(SubCommand.Permissions.ADMIN);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        return completer.suggest(source.getSender(), args);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        Player player = source.getExecutor() instanceof Player executor ? executor
                : sender instanceof Player self ? self : null;

        if (args.length == 0) {
            help(sender);
            return;
        }
        SubCommand command = SubCommand.find(args[0]);
        if (command == null) {
            messages().send(sender, "errors.unknown-command");
            return;
        }
        if (!command.isAllowed(sender)) {
            messages().send(sender, "errors.no-permission");
            return;
        }
        if (command.playerOnly() && player == null) {
            messages().send(sender, "errors.player-only");
            return;
        }

        switch (command) {
            case HELP -> help(sender);
            case JOIN -> join(player, args);
            case LEAVE -> leave(player);
            case BLOCK -> openBlockSelector(player);
            case LIST -> list(sender);
            case CREATE -> create(sender, player, args);
            case RELOAD -> reload(sender);
            default -> {
                Arena arena = requireArena(sender, command, args);
                if (arena != null) {
                    executeArenaCommand(command, sender, player, arena, args);
                }
            }
        }
    }

    private void executeArenaCommand(SubCommand command, CommandSender sender, @Nullable Player player, Arena arena, String[] args) {
        switch (command) {
            case DELETE -> delete(sender, arena);
            case SETLOBBY -> setPosition(sender, player, arena, "lobby", a -> a.setLobby(StoredLocation.of(player.getLocation())));
            case SETJUMP -> setPosition(sender, player, arena, "jump", a -> a.setJump(StoredLocation.of(player.getLocation())));
            case SETSPECTATOR -> setPosition(sender, player, arena, "spectator", a -> a.setSpectator(StoredLocation.of(player.getLocation())));
            case POS1 -> setPosition(sender, player, arena, "pos1", a -> a.setPos1(BlockPos.of(player.getLocation())));
            case POS2 -> setPosition(sender, player, arena, "pos2", a -> a.setPos2(BlockPos.of(player.getLocation())));
            case SETPLAYERS -> setPlayers(sender, arena, args);
            case ENABLE -> enable(sender, arena);
            case DISABLE -> disable(sender, arena);
            case INFO -> info(sender, arena);
            case STOP -> stop(sender, arena);
            case FORCESTART -> forceStart(sender, arena);
            default -> messages().send(sender, "errors.unknown-command");
        }
    }

    private String usage(SubCommand command) {
        return "/dac " + messages().raw("help.usages." + command.label());
    }

    private @Nullable Arena requireArena(CommandSender sender, SubCommand command, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "errors.usage", Placeholders.create().text("usage", usage(command)));
            return null;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            messages().send(sender, "errors.arena-not-found", Placeholders.create().text("arena", args[1]));
        }
        return arena;
    }

    // ------------------------------------------------------------------ player commands

    private void help(CommandSender sender) {
        messages().send(sender, "help.header");
        for (SubCommand command : SubCommand.values()) {
            if (command.isAllowed(sender)) {
                messages().send(sender, "help.line", Placeholders.create()
                        .text("usage", usage(command))
                        .raw("description", messages().raw("help.descriptions." + command.label())));
            }
        }
    }

    private void join(Player player, String[] args) {
        if (args.length < 2) {
            plugin.games().autoJoin(player);
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            messages().send(player, "errors.arena-not-found", Placeholders.create().text("arena", args[1]));
            return;
        }
        plugin.games().join(player, arena);
    }

    private void leave(Player player) {
        if (!plugin.games().isInGame(player.getUniqueId())) {
            messages().send(player, "errors.not-in-game");
            return;
        }
        plugin.games().leave(player, LeaveCause.COMMAND);
    }

    private void openBlockSelector(Player player) {
        GameSession session = plugin.games().session(player.getUniqueId());
        plugin.debug("/dac block invoked by " + player.getName() + ", session: "
                + (session != null ? session.arena().name() : "none"));
        if (session == null) {
            messages().send(player, "errors.not-in-game");
            return;
        }
        session.runSafely(() -> session.openBlockSelector(player));
    }

    private void list(CommandSender sender) {
        boolean admin = sender.hasPermission(SubCommand.Permissions.ADMIN);
        Collection<Arena> arenas = plugin.arenas().all().stream()
                .filter(arena -> admin || arena.isEnabled())
                .toList();
        if (arenas.isEmpty()) {
            messages().send(sender, "list.empty");
            return;
        }
        messages().send(sender, "list.header", Placeholders.create().text("count", arenas.size()));
        for (Arena arena : arenas) {
            messages().send(sender, "list.entry", Placeholders.create()
                    .text("arena", arena.name())
                    .text("current", plugin.games().playerCount(arena))
                    .text("max", arena.maxPlayers(plugin.settings()))
                    .raw("state", stateLabel(arena)));
        }
    }

    private String stateLabel(Arena arena) {
        if (arena.isEnabled() && arena.world() == null) {
            return messages().raw("states.UNAVAILABLE");
        }
        return messages().raw(arena.state().messageKey());
    }

    // ------------------------------------------------------------------ admin commands

    private void create(CommandSender sender, @Nullable Player player, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "errors.usage", Placeholders.create().text("usage", usage(SubCommand.CREATE)));
            return;
        }
        String name = args[1];
        Placeholders placeholders = Placeholders.create().text("arena", name);
        if (!ArenaRepository.VALID_NAME.matcher(name).matches()) {
            messages().send(sender, "errors.invalid-name", placeholders);
            return;
        }
        if (plugin.arenas().exists(name)) {
            messages().send(sender, "errors.arena-exists", placeholders);
            return;
        }
        plugin.arenas().create(name, player != null ? player.getWorld().getName() : null);
        messages().send(sender, "admin.created", placeholders);
        plugin.getLogger().info("Arena " + name + " created by " + sender.getName() + ".");
    }

    private void delete(CommandSender sender, Arena arena) {
        plugin.games().stop(arena, true);
        plugin.arenas().delete(arena);
        arena.setEnabled(false);
        arena.setState(ArenaState.DISABLED);
        messages().send(sender, "admin.deleted", Placeholders.create().text("arena", arena.name()));
        plugin.getLogger().info("Arena " + arena.name() + " deleted by " + sender.getName() + ".");
    }

    private void setPosition(CommandSender sender, Player player, Arena arena, String key, Consumer<Arena> setter) {
        Placeholders placeholders = Placeholders.create().text("arena", arena.name());
        if (plugin.games().hasSession(arena)) {
            messages().send(sender, "errors.arena-in-use", placeholders);
            return;
        }
        String worldName = player.getWorld().getName();
        if (arena.worldName() != null && !arena.worldName().equals(worldName) && arena.hasAnyPosition()) {
            messages().send(sender, "errors.different-world", placeholders.text("world", arena.worldName()));
            return;
        }
        arena.setWorldName(worldName);
        setter.accept(arena);
        plugin.arenas().save(arena);
        messages().send(sender, "admin." + key + "-set", placeholders
                .text("position", BlockPos.of(player.getLocation()).toString())
                .text("world", worldName));
        revalidateEnabled(sender, arena);
    }

    /**
     * An enabled arena whose configuration became invalid is disabled right away.
     */
    private void revalidateEnabled(CommandSender sender, Arena arena) {
        if (!arena.isEnabled()) {
            return;
        }
        ArenaValidator.Result result = plugin.validator().validate(arena, plugin.settings());
        if (!result.valid()) {
            arena.setEnabled(false);
            arena.setState(ArenaState.DISABLED);
            plugin.arenas().save(arena);
            messages().send(sender, "admin.auto-disabled", Placeholders.create().text("arena", arena.name()));
            sendIssues(sender, result);
        }
    }

    private void setPlayers(CommandSender sender, Arena arena, String[] args) {
        Placeholders placeholders = Placeholders.create().text("arena", arena.name());
        if (args.length >= 3 && "reset".equalsIgnoreCase(args[2])) {
            arena.setPlayerLimits(null, null);
            plugin.arenas().save(arena);
            messages().send(sender, "admin.players-reset", placeholders
                    .text("min", arena.minPlayers(plugin.settings()))
                    .text("max", arena.maxPlayers(plugin.settings())));
            return;
        }
        if (args.length < 4) {
            messages().send(sender, "errors.usage", Placeholders.create().text("usage", usage(SubCommand.SETPLAYERS)));
            return;
        }
        int min;
        int max;
        try {
            min = Integer.parseInt(args[2]);
            max = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            messages().send(sender, "errors.invalid-number");
            return;
        }
        if (min < 1 || max < 1 || max > 100 || min > max) {
            messages().send(sender, "errors.invalid-limits");
            return;
        }
        arena.setPlayerLimits(min, max);
        plugin.arenas().save(arena);
        messages().send(sender, "admin.players-set", placeholders.text("min", min).text("max", max));
    }

    private void enable(CommandSender sender, Arena arena) {
        Placeholders placeholders = Placeholders.create().text("arena", arena.name());
        if (arena.isEnabled()) {
            messages().send(sender, "admin.already-enabled", placeholders);
            return;
        }
        ArenaValidator.Result result = plugin.validator().validate(arena, plugin.settings());
        if (!result.valid()) {
            messages().send(sender, "admin.enable-failed", placeholders);
            sendIssues(sender, result);
            return;
        }
        arena.setEnabled(true);
        arena.setState(ArenaState.WAITING);
        plugin.arenas().save(arena);
        messages().send(sender, "admin.enabled", placeholders.text("cells", result.waterCells()).text("surface", result.surfaceY()));
        plugin.getLogger().info("Arena " + arena.name() + " enabled.");
    }

    private void sendIssues(CommandSender sender, ArenaValidator.Result result) {
        for (Component issue : result.issues()) {
            sender.sendMessage(messages().get("admin.validation-prefix").append(issue));
        }
    }

    private void disable(CommandSender sender, Arena arena) {
        Placeholders placeholders = Placeholders.create().text("arena", arena.name());
        if (!arena.isEnabled()) {
            messages().send(sender, "admin.already-disabled", placeholders);
            return;
        }
        arena.setEnabled(false);
        plugin.games().stop(arena, true);
        arena.setState(ArenaState.DISABLED);
        plugin.arenas().save(arena);
        messages().send(sender, "admin.disabled", placeholders);
        plugin.getLogger().info("Arena " + arena.name() + " disabled.");
    }

    private void info(CommandSender sender, Arena arena) {
        ArenaValidator.Result result = plugin.validator().validate(arena, plugin.settings());
        GameSession session = plugin.games().session(arena);
        Cuboid region = arena.poolRegion();
        String activePlayer = null;
        if (session != null && session.turns().activePlayer() != null) {
            Player active = Bukkit.getPlayer(session.turns().activePlayer());
            activePlayer = active != null ? active.getName() : null;
        }
        Placeholders placeholders = Placeholders.create()
                .text("arena", arena.name())
                .raw("state", stateLabel(arena))
                .raw("enabled", messages().yesNo(arena.isEnabled()))
                .text("world", arena.worldName() != null ? arena.worldName() : "-")
                .raw("world_loaded", messages().yesNo(arena.world() != null))
                .text("current", plugin.games().playerCount(arena))
                .text("min", arena.minPlayers(plugin.settings()))
                .text("max", arena.maxPlayers(plugin.settings()))
                .raw("lobby", positionLabel(arena.lobby()))
                .raw("jump", positionLabel(arena.jump()))
                .raw("spectator", positionLabel(arena.spectator()))
                .raw("pos1", positionLabel(arena.pos1()))
                .raw("pos2", positionLabel(arena.pos2()))
                .raw("pool", region != null ? messages().yesNo(true) : messages().yesNo(false))
                .text("pool_size", region != null ? region.width() + "x" + region.height() + "x" + region.length() : "-")
                .text("cells", result.waterCells())
                .text("surface", result.surfaceY() == Integer.MIN_VALUE ? "-" : String.valueOf(result.surfaceY()))
                .raw("game", messages().yesNo(session != null && arena.state() == ArenaState.PLAYING))
                .text("turn", activePlayer != null ? activePlayer : "-")
                .raw("valid", messages().yesNo(result.valid()));
        messages().sendList(sender, "admin.info", placeholders);
        if (!result.valid()) {
            sendIssues(sender, result);
        }
    }

    private String positionLabel(@Nullable Object position) {
        if (position == null) {
            return messages().raw("words.unset");
        }
        return messages().apply(messages().raw("words.set"), Placeholders.create().text("position", position));
    }

    private void stop(CommandSender sender, Arena arena) {
        Placeholders placeholders = Placeholders.create().text("arena", arena.name());
        if (plugin.games().stop(arena, true)) {
            messages().send(sender, "admin.stopped", placeholders);
            plugin.getLogger().info("Game in arena " + arena.name() + " stopped by " + sender.getName() + ".");
        } else {
            messages().send(sender, "admin.not-running", placeholders);
        }
    }

    private void forceStart(CommandSender sender, Arena arena) {
        Placeholders placeholders = Placeholders.create().text("arena", arena.name());
        if (plugin.games().forceStart(arena)) {
            messages().send(sender, "admin.forcestart", placeholders);
        } else {
            messages().send(sender, "admin.forcestart-failed", placeholders);
        }
    }

    private void reload(CommandSender sender) {
        plugin.configManager().load();
        messages().load();
        int arenas = plugin.arenas().reload(arena -> plugin.games().hasSession(arena));
        messages().send(sender, "admin.reloaded", Placeholders.create().text("arenas", arenas));
        plugin.getLogger().info("Configuration reloaded by " + sender.getName() + ".");
    }
}
