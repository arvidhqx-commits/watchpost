package dev.watchpost;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** Eine Kamera ist reine Datenhaltung: KEINE Entity, kein Armorstand, kein Kopf. */
final class Camera {

    final String name;
    final String worldName;
    final UUID worldId;
    final double x, y, z;
    final float yaw, pitch;
    final UUID owner;
    boolean enabled;

    Camera(String name, String worldName, UUID worldId, double x, double y, double z,
           float yaw, float pitch, UUID owner, boolean enabled) {
        this.name = name;
        this.worldName = worldName;
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.owner = owner;
        this.enabled = enabled;
    }

    static Camera at(String name, Location loc, UUID owner) {
        return new Camera(name, loc.getWorld().getName(), loc.getWorld().getUID(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), owner, true);
    }

    /**
     * Welt zuerst ueber die UUID, dann ueber den Namen aufloesen.
     * Eine neu erzeugte Welt gleichen Namens hat eine andere UUID; ein umbenannter
     * Ordner behaelt die UUID. Beide Faelle werden so aufgefangen.
     */
    World world() {
        World w = worldId == null ? null : Bukkit.getWorld(worldId);
        if (w == null && worldName != null) w = Bukkit.getWorld(worldName);
        return w;
    }

    /** null, wenn die Welt gerade nicht geladen ist. Aufrufer MUESSEN das pruefen. */
    Location location() {
        World w = world();
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    String key() {
        return name.toLowerCase(java.util.Locale.ROOT);
    }
}
