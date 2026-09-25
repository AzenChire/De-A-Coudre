package fr.fixemy.deacoudre.player;

import fr.fixemy.deacoudre.util.LocationSerializer;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full Minecraft state of a player before joining the mini-game.
 * <p>
 * Kept in memory while the player is online and mirrored on disk so it can be
 * restored after a crash or a failed restoration.
 */
public final class PlayerSnapshot {

    private final UUID uuid;
    private final String name;
    /** Null when the inventory is not managed (game.restore-inventory: false). */
    private final ItemStack @Nullable [] storage;
    private final ItemStack @Nullable [] armor;
    private final @Nullable ItemStack offHand;
    private final GameMode gameMode;
    private final int level;
    private final float exp;
    private final int totalExperience;
    private final double health;
    private final double absorption;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final List<PotionEffect> effects;
    private final @Nullable Location location;
    private final boolean allowFlight;
    private final boolean flying;
    private final float walkSpeed;
    private final float flySpeed;
    private final int remainingAir;
    private final boolean collidable;
    private final boolean invulnerable;
    private final boolean invisible;

    private PlayerSnapshot(UUID uuid, String name, ItemStack @Nullable [] storage, ItemStack @Nullable [] armor,
                           @Nullable ItemStack offHand, GameMode gameMode, int level, float exp, int totalExperience,
                           double health, double absorption, int foodLevel, float saturation, float exhaustion,
                           List<PotionEffect> effects, @Nullable Location location, boolean allowFlight,
                           boolean flying, float walkSpeed, float flySpeed, int remainingAir, boolean collidable,
                           boolean invulnerable, boolean invisible) {
        this.uuid = uuid;
        this.name = name;
        this.storage = storage;
        this.armor = armor;
        this.offHand = offHand;
        this.gameMode = gameMode;
        this.level = level;
        this.exp = exp;
        this.totalExperience = totalExperience;
        this.health = health;
        this.absorption = absorption;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
        this.effects = effects;
        this.location = location;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.walkSpeed = walkSpeed;
        this.flySpeed = flySpeed;
        this.remainingAir = remainingAir;
        this.collidable = collidable;
        this.invulnerable = invulnerable;
        this.invisible = invisible;
    }

    public static PlayerSnapshot capture(Player player, boolean includeInventory) {
        PlayerInventory inventory = player.getInventory();
        return new PlayerSnapshot(
                player.getUniqueId(),
                player.getName(),
                includeInventory ? copy(inventory.getStorageContents()) : null,
                includeInventory ? copy(inventory.getArmorContents()) : null,
                includeInventory ? inventory.getItemInOffHand().clone() : null,
                player.getGameMode(),
                player.getLevel(),
                player.getExp(),
                player.getTotalExperience(),
                player.getHealth(),
                player.getAbsorptionAmount(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                List.copyOf(player.getActivePotionEffects()),
                player.getLocation().clone(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getWalkSpeed(),
                player.getFlySpeed(),
                player.getRemainingAir(),
                player.isCollidable(),
                player.isInvulnerable(),
                player.isInvisible());
    }

    private static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? ItemStack.empty() : items[i].clone();
        }
        return copy;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public @Nullable Location location() {
        return location;
    }

    /**
     * Restores every value except the location (the caller handles teleportation
     * so it can flag it as an internal teleport).
     */
    public void restoreState(Player player) {
        player.closeInventory();
        player.setGameMode(gameMode);

        if (storage != null && armor != null) {
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setStorageContents(copy(storage));
            inventory.setArmorContents(copy(armor));
            inventory.setItemInOffHand(offHand == null ? ItemStack.empty() : offHand.clone());
            player.setItemOnCursor(ItemStack.empty());
        }

        player.clearActivePotionEffects();
        player.addPotionEffects(effects);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        player.setHealth(health <= 0 ? max : Math.min(health, max));
        player.setAbsorptionAmount(Math.max(0, absorption));
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        player.setLevel(level);
        player.setExp(Math.clamp(exp, 0f, 1f));
        player.setTotalExperience(totalExperience);
        player.setWalkSpeed(Math.clamp(walkSpeed, -1f, 1f));
        player.setFlySpeed(Math.clamp(flySpeed, -1f, 1f));
        player.setRemainingAir(remainingAir);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0f);
        player.setCollidable(collidable);
        player.setInvulnerable(invulnerable);
        player.setInvisible(invisible);
    }

    /**
     * Flight must be restored after the teleport: a world change can reset it.
     */
    public void restoreFlight(Player player) {
        player.setAllowFlight(allowFlight || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
        if (player.getAllowFlight()) {
            player.setFlying(flying || gameMode == GameMode.SPECTATOR);
        }
    }

    // ------------------------------------------------------------------ persistence

    public String serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", uuid.toString());
        yaml.set("name", name);
        if (storage != null && armor != null) {
            yaml.set("inventory.storage", encode(storage));
            yaml.set("inventory.armor", encode(armor));
            yaml.set("inventory.offhand", encode(new ItemStack[]{offHand == null ? ItemStack.empty() : offHand}));
        }
        yaml.set("gamemode", gameMode.name());
        yaml.set("level", level);
        yaml.set("exp", (double) exp);
        yaml.set("total-experience", totalExperience);
        yaml.set("health", health);
        yaml.set("absorption", absorption);
        yaml.set("food", foodLevel);
        yaml.set("saturation", (double) saturation);
        yaml.set("exhaustion", (double) exhaustion);
        List<Map<String, Object>> serializedEffects = new ArrayList<>();
        for (PotionEffect effect : effects) {
            Map<String, Object> map = new HashMap<>();
            map.put("type", effect.getType().key().asString());
            map.put("duration", effect.getDuration());
            map.put("amplifier", effect.getAmplifier());
            map.put("ambient", effect.isAmbient());
            map.put("particles", effect.hasParticles());
            map.put("icon", effect.hasIcon());
            serializedEffects.add(map);
        }
        yaml.set("effects", serializedEffects);
        if (location != null) {
            LocationSerializer.writeFull(yaml, "location", location);
        }
        yaml.set("allow-flight", allowFlight);
        yaml.set("flying", flying);
        yaml.set("walk-speed", (double) walkSpeed);
        yaml.set("fly-speed", (double) flySpeed);
        yaml.set("remaining-air", remainingAir);
        yaml.set("collidable", collidable);
        yaml.set("invulnerable", invulnerable);
        yaml.set("invisible", invisible);
        return yaml.saveToString();
    }

    public static PlayerSnapshot deserialize(YamlConfiguration yaml) {
        UUID uuid = UUID.fromString(yaml.getString("uuid", ""));
        ItemStack[] storage = null;
        ItemStack[] armor = null;
        ItemStack offHand = null;
        ConfigurationSection inventory = yaml.getConfigurationSection("inventory");
        if (inventory != null) {
            storage = decode(inventory.getString("storage"));
            armor = decode(inventory.getString("armor"));
            ItemStack[] off = decode(inventory.getString("offhand"));
            offHand = off.length > 0 ? off[0] : ItemStack.empty();
        }
        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(yaml.getString("gamemode", "SURVIVAL"));
        } catch (IllegalArgumentException exception) {
            gameMode = GameMode.SURVIVAL;
        }
        List<PotionEffect> effects = new ArrayList<>();
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
        for (Map<?, ?> map : yaml.getMapList("effects")) {
            PotionEffectType type = registry.get(Key.key(String.valueOf(map.get("type"))));
            if (type == null) {
                continue;
            }
            effects.add(new PotionEffect(type, toInt(map.get("duration")), toInt(map.get("amplifier")),
                    toBool(map.get("ambient")), toBool(map.get("particles")), toBool(map.get("icon"))));
        }
        Location location = LocationSerializer.readFull(yaml, "location");
        if (location == null) {
            // Original world does not exist anymore: fall back to the main spawn.
            location = Bukkit.getWorlds().getFirst().getSpawnLocation();
        }
        return new PlayerSnapshot(uuid, yaml.getString("name", "?"), storage, armor, offHand, gameMode,
                yaml.getInt("level"), (float) yaml.getDouble("exp"), yaml.getInt("total-experience"),
                yaml.getDouble("health", 20), yaml.getDouble("absorption"), yaml.getInt("food", 20),
                (float) yaml.getDouble("saturation", 5), (float) yaml.getDouble("exhaustion"),
                List.copyOf(effects), location, yaml.getBoolean("allow-flight"), yaml.getBoolean("flying"),
                (float) yaml.getDouble("walk-speed", 0.2), (float) yaml.getDouble("fly-speed", 0.1),
                yaml.getInt("remaining-air", 300), yaml.getBoolean("collidable", true),
                yaml.getBoolean("invulnerable"), yaml.getBoolean("invisible"));
    }

    private static String encode(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    private static ItemStack[] decode(@Nullable String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(data));
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) {
                items[i] = ItemStack.empty();
            }
        }
        return items;
    }

    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static boolean toBool(Object value) {
        return value instanceof Boolean bool && bool;
    }
}
