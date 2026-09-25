package fr.fixemy.deacoudre.arena;

import fr.fixemy.deacoudre.config.Settings;
import fr.fixemy.deacoudre.util.BlockPos;
import fr.fixemy.deacoudre.util.Cuboid;
import fr.fixemy.deacoudre.util.StoredLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Configuration and runtime state of one arena. Every point of an arena lives
 * in the same world ({@link #worldName()}).
 */
public final class Arena {

    private final String name;
    private @Nullable String worldName;
    private @Nullable StoredLocation lobby;
    private @Nullable StoredLocation jump;
    private @Nullable StoredLocation spectator;
    private @Nullable BlockPos pos1;
    private @Nullable BlockPos pos2;
    private @Nullable Integer minPlayers;
    private @Nullable Integer maxPlayers;
    private boolean enabled;
    private ArenaState state = ArenaState.DISABLED;

    public Arena(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public String key() {
        return key(name);
    }

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public @Nullable String worldName() {
        return worldName;
    }

    public void setWorldName(@Nullable String worldName) {
        this.worldName = worldName;
    }

    /**
     * @return the loaded world of the arena, or {@code null} if unset / not loaded
     */
    public @Nullable World world() {
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }

    public @Nullable StoredLocation lobby() {
        return lobby;
    }

    public void setLobby(@Nullable StoredLocation lobby) {
        this.lobby = lobby;
    }

    public @Nullable StoredLocation jump() {
        return jump;
    }

    public void setJump(@Nullable StoredLocation jump) {
        this.jump = jump;
    }

    public @Nullable StoredLocation spectator() {
        return spectator;
    }

    public void setSpectator(@Nullable StoredLocation spectator) {
        this.spectator = spectator;
    }

    public @Nullable BlockPos pos1() {
        return pos1;
    }

    public void setPos1(@Nullable BlockPos pos1) {
        this.pos1 = pos1;
    }

    public @Nullable BlockPos pos2() {
        return pos2;
    }

    public void setPos2(@Nullable BlockPos pos2) {
        this.pos2 = pos2;
    }

    public @Nullable Integer minPlayersOverride() {
        return minPlayers;
    }

    public @Nullable Integer maxPlayersOverride() {
        return maxPlayers;
    }

    public void setPlayerLimits(@Nullable Integer minPlayers, @Nullable Integer maxPlayers) {
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    public int minPlayers(Settings settings) {
        return minPlayers != null ? minPlayers : settings.minPlayers();
    }

    public int maxPlayers(Settings settings) {
        return maxPlayers != null ? maxPlayers : settings.maxPlayers();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ArenaState state() {
        return state;
    }

    public void setState(ArenaState state) {
        this.state = state;
    }

    /**
     * @return the pool region, or {@code null} while pos1/pos2 are not both defined
     */
    public @Nullable Cuboid poolRegion() {
        return pos1 == null || pos2 == null ? null : Cuboid.of(pos1, pos2);
    }

    /**
     * True when at least one position is configured (used to lock the world of the arena).
     */
    public boolean hasAnyPosition() {
        return lobby != null || jump != null || spectator != null || pos1 != null || pos2 != null;
    }

    public @Nullable Location lobbyLocation() {
        return resolve(lobby);
    }

    public @Nullable Location jumpLocation() {
        return resolve(jump);
    }

    public @Nullable Location spectatorLocation() {
        return resolve(spectator);
    }

    private @Nullable Location resolve(@Nullable StoredLocation location) {
        World world = world();
        return location == null || world == null ? null : location.toLocation(world);
    }
}
