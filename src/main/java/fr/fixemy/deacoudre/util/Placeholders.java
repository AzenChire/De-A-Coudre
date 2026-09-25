package fr.fixemy.deacoudre.util;

import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal %placeholder% values. Plain text values are escaped so that player
 * provided text can never inject MiniMessage tags; {@link #raw} values are
 * trusted MiniMessage snippets coming from the configuration.
 */
public final class Placeholders {

    private final Map<String, String> values = new HashMap<>();

    private Placeholders() {
    }

    public static Placeholders create() {
        return new Placeholders();
    }

    public Placeholders text(String key, Object value) {
        values.put(key, MiniMessage.miniMessage().escapeTags(String.valueOf(value)));
        return this;
    }

    public Placeholders raw(String key, String miniMessage) {
        values.put(key, miniMessage);
        return this;
    }

    public String get(String key) {
        return values.get(key);
    }
}
