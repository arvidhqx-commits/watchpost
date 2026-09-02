package dev.watchpost;

import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;

/**
 * Nachtsicht nachschlagen, ohne sich auf EINEN Weg zu verlassen.
 *
 * Die Konstante PotionEffectType.NIGHT_VISION, die Registry und getByName haben
 * ueber die Versionen hinweg abwechselnd funktioniert. Ein fehlendes Feld wirft
 * beim ERSTEN Zugriff einen NoSuchFieldError -- der wird hier gefangen, statt das
 * Plugin beim Aktivieren zu zerlegen. Genau daran ist der Vorgaenger gestorben
 * (NoSuchMethodError aus com.mojang.authlib beim Bauen eines Kamerakopfes).
 */
final class Effects {

    private static boolean resolved;
    private static PotionEffectType nightVision;

    private Effects() {}

    static synchronized PotionEffectType nightVision() {
        if (resolved) return nightVision;
        resolved = true;
        try {
            nightVision = PotionEffectType.NIGHT_VISION;
        } catch (Throwable ignored) {
            // Feld existiert in dieser Version nicht -- weiter zum naechsten Weg.
        }
        if (nightVision == null) {
            try {
                nightVision = org.bukkit.Registry.EFFECT.get(NamespacedKey.minecraft("night_vision"));
            } catch (Throwable ignored) {
            }
        }
        if (nightVision == null) {
            try {
                nightVision = PotionEffectType.getByName("NIGHT_VISION");
            } catch (Throwable ignored) {
            }
        }
        return nightVision;
    }
}
