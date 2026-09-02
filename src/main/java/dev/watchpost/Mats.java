package dev.watchpost;

import org.bukkit.Material;

/**
 * Materialsuche ohne feste Konstanten.
 *
 * Grund: Materialkonstanten verschwinden oder wandern zwischen Minecraft-Versionen.
 * Ein Plugin, das MATERIAL.X direkt referenziert, laesst sich auf einer Version, die
 * X nicht mehr kennt, nicht einmal laden. Hier wird zur Laufzeit gesucht und auf
 * einen garantiert vorhandenen Notnagel zurueckgefallen.
 */
final class Mats {

    private Mats() {}

    static Material first(String... names) {
        for (String n : names) {
            Material m = Material.matchMaterial(n);
            if (m != null && m.isItem()) return m;
        }
        return Material.PAPER;
    }
}
