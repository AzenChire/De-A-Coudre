package fr.fixemy.deacoudre.config;

import fr.fixemy.deacoudre.DeACoudrePlugin;
import fr.fixemy.deacoudre.util.Placeholders;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads messages.yml and turns MiniMessage templates into Adventure components.
 * <p>
 * Every visible text of the plugin goes through this class. Internal
 * placeholders use the {@code %name%} syntax and are replaced before the
 * MiniMessage parsing.
 */
public final class MessageManager {

    /** Bundled translations (src/main/resources/lang/messages_<code>.yml). */
    private static final List<String> LANGUAGES = List.of("en", "fr");
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_-]+)%");
    private static final Title.Times DEFAULT_TIMES =
            Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(500));

    private final DeACoudrePlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration messages = new YamlConfiguration();
    private String prefix = "";

    public MessageManager(DeACoudrePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * messages.yml is the file used by the server. It is created from the translation
     * selected by {@code language} in config.yml, which also provides the default value
     * of any key missing from messages.yml (e.g. after an update). Every bundled
     * translation is written to {@code lang/} as a reference for administrators.
     */
    public void load() {
        for (String language : LANGUAGES) {
            try {
                plugin.saveResource("lang/messages_" + language + ".yml", true);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Missing bundled translation " + language + ": " + exception.getMessage());
            }
        }
        String language = plugin.settings().language();
        String resource = "lang/messages_" + language + ".yml";
        if (plugin.getResource(resource) == null) {
            plugin.getLogger().warning("Unknown language '" + language + "' (available: " + String.join(", ", LANGUAGES)
                    + "), using en.");
            resource = "lang/messages_en.yml";
        }

        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            try (InputStream stream = plugin.getResource(resource)) {
                if (stream != null) {
                    Files.copy(stream, file.toPath());
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Unable to create messages.yml: " + exception.getMessage());
            }
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream != null) {
                loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Unable to read default messages: " + exception.getMessage());
        }
        this.messages = loaded;
        this.prefix = loaded.getString("prefix", "");
    }

    /**
     * Raw template, or a visible error text when the key is missing everywhere.
     */
    public String raw(String path) {
        String value = messages.getString(path);
        return value != null ? value : "<red>[DeACoudre] Missing message: " + path;
    }

    /**
     * Raw template, or null when the key does not exist (optional messages).
     */
    public String rawOrNull(String path) {
        return messages.getString(path);
    }

    public List<String> rawList(String path) {
        return messages.getStringList(path);
    }

    public String apply(String template, Placeholders placeholders) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder builder = new StringBuilder(template.length() + 32);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = "prefix".equals(key) ? prefix : placeholders.get(key);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    public Component parse(String template, Placeholders placeholders) {
        return miniMessage.deserialize(apply(template, placeholders));
    }

    public Component get(String path, Placeholders placeholders) {
        return parse(raw(path), placeholders);
    }

    public Component get(String path) {
        return get(path, Placeholders.create());
    }

    public List<Component> getList(String path, Placeholders placeholders) {
        List<Component> lines = new ArrayList<>();
        for (String line : rawList(path)) {
            lines.add(parse(line, placeholders));
        }
        return lines;
    }

    /**
     * Sends a chat message. An empty template disables the message.
     */
    public void send(Audience audience, String path, Placeholders placeholders) {
        String template = raw(path);
        if (!template.isEmpty()) {
            audience.sendMessage(parse(template, placeholders));
        }
    }

    public void send(Audience audience, String path) {
        send(audience, path, Placeholders.create());
    }

    public void sendList(Audience audience, String path, Placeholders placeholders) {
        getList(path, placeholders).forEach(audience::sendMessage);
    }

    public void actionBar(Audience audience, String path, Placeholders placeholders) {
        String template = raw(path);
        if (!template.isEmpty()) {
            audience.sendActionBar(parse(template, placeholders));
        }
    }

    public void title(Audience audience, String titlePath, String subtitlePath, Placeholders placeholders) {
        title(audience, titlePath, subtitlePath, placeholders, DEFAULT_TIMES);
    }

    public void title(Audience audience, String titlePath, String subtitlePath, Placeholders placeholders, Title.Times times) {
        Component title = get(titlePath, placeholders);
        Component subtitle = subtitlePath == null ? Component.empty() : get(subtitlePath, placeholders);
        audience.showTitle(Title.title(title, subtitle, times));
    }

    /**
     * MiniMessage display name of a color block, e.g. {@code <aqua>Bleu clair}.
     */
    public String colorName(Material material) {
        String value = messages.getString("colors." + material.name());
        // Not listed: the vanilla block name, translated by the client into its own language.
        return value != null ? value : "<white><lang:" + material.translationKey() + ">";
    }

    /**
     * Text for an item name / lore line: same as {@link #get} but not italic by default.
     */
    public Component item(String template, Placeholders placeholders) {
        return parse(template, placeholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public List<Component> itemLines(String path, Placeholders placeholders) {
        List<Component> lines = new ArrayList<>();
        for (String line : rawList(path)) {
            lines.add(item(line, placeholders));
        }
        return lines;
    }

    public String yesNo(boolean value) {
        return raw(value ? "words.yes" : "words.no");
    }
}
