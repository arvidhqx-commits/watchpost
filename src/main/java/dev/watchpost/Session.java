package dev.watchpost;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Der Zustand eines Spielers VOR dem Blick durch eine Kamera.
 *
 * Dieser Datensatz ist der eigentliche Kern des Plugins: Er liegt ab dem ersten
 * Moment auf der Platte, nicht nur im Arbeitsspeicher. Genau daran scheitern
 * Kamera-Plugins reihenweise -- nach einem Absturz haengt der Spieler im
 * Zuschauermodus irgendwo in der Welt, und niemand weiss mehr, wo er herkam.
 */
final class Session {

    final UUID player;
    final String worldName;
    final UUID worldId;
    final double x, y, z;
    final float yaw, pitch;
    final GameMode gameMode;
    final boolean allowFlight;
    final boolean flying;
    boolean nightVisionAdded;
    String camera;
    String group;
    final long startedMs;

    Session(UUID player, String worldName, UUID worldId, double x, double y, double z,
            float yaw, float pitch, GameMode gameMode, boolean allowFlight, boolean flying,
            boolean nightVisionAdded, String camera, String group, long startedMs) {
        this.player = player;
        this.worldName = worldName;
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.gameMode = gameMode;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.nightVisionAdded = nightVisionAdded;
        this.camera = camera;
        this.group = group;
        this.startedMs = startedMs;
    }

    static Session capture(org.bukkit.entity.Player p, String camera, String group, long now) {
        Location l = p.getLocation();
        return new Session(p.getUniqueId(), l.getWorld().getName(), l.getWorld().getUID(),
                l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(),
                p.getGameMode(), p.getAllowFlight(), p.isFlying(),
                false, camera, group, now);
    }

    World world() {
        World w = worldId == null ? null : Bukkit.getWorld(worldId);
        if (w == null && worldName != null) w = Bukkit.getWorld(worldName);
        return w;
    }

    /** null, wenn die Rueckkehrwelt fehlt -- der Aufrufer muss dann ausweichen. */
    Location location() {
        World w = world();
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
