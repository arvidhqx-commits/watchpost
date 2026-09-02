package dev.watchpost;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Der Blick durch die Kamera und -- wichtiger -- der Weg zurueck.
 *
 * Regel des Hauses: Jeder Einstieg schreibt den Rueckweg auf die Platte, BEVOR
 * am Spieler irgendetwas veraendert wird. Es gibt keinen Pfad, auf dem ein
 * Spieler im Zuschauermodus zurueckbleibt: Aussteigen, Verlassen des Servers,
 * Plugin-Neuladen, Serverabsturz und Weltverlust sind einzeln abgedeckt.
 */
final class Viewing {

    /** Warum eine Sitzung endete -- steht im Log und in der Spielernachricht. */
    enum Reason { COMMAND, SNEAK, TIMEOUT, QUIT, SHUTDOWN, WORLD_GONE, GAMEMODE, RESTORED_AFTER_RESTART }

    private final WatchPostPlugin plugin;
    private final SessionStore store;
    private final Set<UUID> teleporting = new HashSet<>();

    Viewing(WatchPostPlugin plugin, SessionStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    boolean isViewing(UUID id) {
        return store.get(id) != null;
    }

    Session session(UUID id) {
        return store.get(id);
    }

    boolean isOurTeleport(UUID id) {
        return teleporting.contains(id);
    }

    /**
     * Einstieg. Gibt eine Fehlermeldung zurueck, oder null bei Erfolg.
     * Der Rueckweg ist geschrieben, bevor der Spielmodus wechselt.
     */
    String begin(Player p, Camera cam, String group) {
        if (cam == null) return "no-such-camera";
        if (!cam.enabled) return "camera-disabled";
        Location target = cam.location();
        if (target == null) return "camera-world-missing";

        Session existing = store.get(p.getUniqueId());
        if (existing != null) {
            // Schon drin: nur umschalten, den urspruenglichen Rueckweg NICHT ueberschreiben.
            existing.camera = cam.name;
            if (group != null) existing.group = group;
            store.save();
            move(p, target);
            return null;
        }

        Session s = Session.capture(p, cam.name, group, System.currentTimeMillis());
        store.put(s);                       // erst die Rueckfahrkarte ...
        p.setGameMode(GameMode.SPECTATOR);  // ... dann der Eingriff
        move(p, target);
        if (plugin.getConfig().getBoolean("view.night-vision", true)) {
            PotionEffectType nv = Effects.nightVision();
            if (nv != null && !p.hasPotionEffect(nv)) {
                p.addPotionEffect(new PotionEffect(nv, 1_000_000, 0, false, false, false));
                s.nightVisionAdded = true;
                store.save();
            }
        }
        return null;
    }

    /** Wechselt innerhalb der Gruppe. Gibt den neuen Kameranamen zurueck oder null. */
    String cycle(Player p, int direction) {
        Session s = store.get(p.getUniqueId());
        if (s == null || s.group == null) return null;
        List<Camera> list = plugin.store().camerasOf(plugin.store().group(s.group));
        if (list.isEmpty()) return null;
        int idx = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equalsIgnoreCase(s.camera)) {
                idx = i;
                break;
            }
        }
        int size = list.size();
        for (int step = 1; step <= size; step++) {
            Camera next = list.get(wrapIndex(size, idx, direction * step));
            if (next.location() != null) {
                s.camera = next.name;
                store.save();
                move(p, next.location());
                return next.name;
            }
        }
        return null;
    }

    /**
     * Ausstieg. Stellt Ort, Spielmodus, Flugrechte und Nachtsicht wieder her.
     *
     * restoreGameMode=false nur dann, wenn ein anderer Beteiligter den Spielmodus
     * bereits umgestellt hat -- dann wird nichts zurueckgedreht, was jemand anderes
     * gerade absichtlich gesetzt hat.
     */
    void end(Player p, Reason reason, boolean restoreGameMode) {
        Session s = store.remove(p.getUniqueId());
        if (s == null) return;
        Location back = s.location();
        if (back == null) {
            back = plugin.getServer().getWorlds().isEmpty()
                    ? null : plugin.getServer().getWorlds().get(0).getSpawnLocation();
            plugin.getLogger().warning("Rueckkehrwelt '" + s.worldName + "' fehlt fuer "
                    + p.getName() + " -- setze auf den Hauptspawn.");
        }
        if (back != null) move(p, back);
        // Der Rueckweg darf nicht wehtun: wer beim Einstieg im Fall war, sammelt
        // waehrend des Kamerablicks im Zuschauermodus keine Fallhoehe an -- der
        // Zaehler des Servers wird trotzdem sicherheitshalber genullt.
        p.setFallDistance(0f);
        if (restoreGameMode) p.setGameMode(s.gameMode);
        p.setAllowFlight(s.allowFlight);
        if (s.allowFlight && s.flying) p.setFlying(true);
        if (s.nightVisionAdded) {
            PotionEffectType nv = Effects.nightVision();
            if (nv != null) p.removePotionEffect(nv);
        }
        plugin.msg(p, "left", "camera", s.camera == null ? "?" : s.camera);
        if (reason == Reason.RESTORED_AFTER_RESTART) {
            plugin.msg(p, "restored", "camera", s.camera == null ? "?" : s.camera);
        }
    }

    /** Beendet ALLE laufenden Sitzungen -- beim Abschalten des Plugins. */
    void endAll(Reason reason) {
        for (UUID id : new ArrayList<>(store.all().keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) end(p, reason, true);
        }
    }

    /**
     * Nach einem Neustart: wer noch in sessions.yml steht und online ist, kommt
     * sofort zurueck. Offline-Eintraege bleiben stehen und greifen beim Einloggen.
     */
    void restoreOnline() {
        for (UUID id : new ArrayList<>(store.all().keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) end(p, Reason.RESTORED_AFTER_RESTART, true);
        }
    }

    /** Ringschluss ueber die Kameraliste -- auch bei negativer Richtung. */
    static int wrapIndex(int size, int idx, int delta) {
        if (size <= 0) return 0;
        return ((idx + delta) % size + size) % size;
    }

    /**
     * Richtung aus dem Hotbar-Wechsel. Der Sprung von Feld 8 auf Feld 0 ist eine
     * Radbewegung nach vorne, nicht acht Felder zurueck -- ohne diese Umrechnung
     * laeuft die Kameraliste am Rand der Hotbar rueckwaerts.
     */
    static int hotbarDirection(int previous, int next) {
        int diff = next - previous;
        if (diff > 4) diff -= 9;
        if (diff < -4) diff += 9;
        return diff >= 0 ? 1 : -1;
    }

    void move(Player p, Location to) {
        teleporting.add(p.getUniqueId());
        try {
            p.teleport(to);
        } finally {
            teleporting.remove(p.getUniqueId());
        }
    }

    /**
     * Wachhund, alle halbe Sekunde: haelt den Blick an der Kamera fest, loest
     * das Anheften an fremde Entities und erzwingt die Zeitgrenze. Bewusst ein
     * Zeitgeber statt eines Events -- er faengt auch das, wofuer es kein Event gibt.
     */
    void tick() {
        long max = plugin.getConfig().getLong("view.max-seconds", 300L) * 1000L;
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(store.all().keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            Session s = store.get(id);
            if (s == null) continue;
            if (max > 0 && now - s.startedMs >= max) {
                end(p, Reason.TIMEOUT, true);
                continue;
            }
            Camera cam = plugin.store().camera(s.camera);
            if (cam == null || !cam.enabled) {
                end(p, Reason.COMMAND, true);
                continue;
            }
            Location target = cam.location();
            if (target == null) {
                end(p, Reason.WORLD_GONE, true);
                continue;
            }
            try {
                if (p.getSpectatorTarget() != null) p.setSpectatorTarget(null);
            } catch (Throwable ignored) {
                // Auf Servern ohne dieses Verhalten schlicht ueberspringen.
            }
            if (p.getGameMode() != GameMode.SPECTATOR) {
                end(p, Reason.GAMEMODE, false);
                continue;
            }
            Location at = p.getLocation();
            if (at.getWorld() == null || !at.getWorld().equals(target.getWorld())
                    || at.distanceSquared(target) > 0.5) {
                Location fix = target.clone();
                fix.setYaw(at.getYaw());
                fix.setPitch(at.getPitch());
                move(p, fix);
            }
        }
    }
}
