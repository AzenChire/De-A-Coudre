package fr.fixemy.deacoudre.util;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Helpers around player colors / blocks.
 */
public final class ColorUtil {

    /** Dye colors sorted by name length (longest first) so LIGHT_BLUE wins over BLUE. */
    private static final DyeColor[] DYES_BY_LENGTH = Arrays.stream(DyeColor.values())
            .sorted(Comparator.comparingInt((DyeColor dye) -> dye.name().length()).reversed())
            .toArray(DyeColor[]::new);

    private ColorUtil() {
    }

    /**
     * Guesses the dye color of a colored block (concrete, wool, terracotta...).
     */
    public static DyeColor dyeOf(Material material) {
        String name = material.name();
        for (DyeColor dye : DYES_BY_LENGTH) {
            if (name.startsWith(dye.name() + "_")) {
                return dye;
            }
        }
        return DyeColor.WHITE;
    }

    public static Color fireworkColor(Material material) {
        return dyeOf(material).getFireworkColor();
    }
}
