package fr.fixemy.deacoudre.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import java.util.Map;

/**
 * A configured sound, stored as an Adventure {@link Sound} referencing the
 * sound event by its namespaced key (no dependency on the Bukkit Sound enum).
 * Optional per-step pitches (e.g. per countdown second) come from
 * {@code pitch-by-second} in config.yml.
 */
public record SoundEffect(Sound sound, Map<Integer, Float> pitchBySecond) {

    public static SoundEffect of(Key key, float volume, float pitch, Map<Integer, Float> pitchBySecond) {
        return new SoundEffect(Sound.sound(key, Sound.Source.MASTER, volume, pitch), Map.copyOf(pitchBySecond));
    }

    /**
     * Plays to the audience only (each player hears it on themselves, nobody else does).
     */
    public void play(Audience audience, int step) {
        Float pitch = pitchBySecond.get(step);
        Sound played = pitch == null ? sound : Sound.sound(sound).pitch(pitch).build();
        audience.playSound(played, Sound.Emitter.self());
    }
}
