package dev.watchpost;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Umzugshilfe von CCTV (SpigotMC 60310).
 *
 * Gelesen werden plugins/CCTV/cameras.yml, cameragroups.yml und computers.yml.
 * Die fremden Dateien werden NUR gelesen, nie geschrieben. Was nicht uebernommen
 * werden kann, wird gemeldet statt still verschluckt -- konkret die Kamerakoepfe
 * ("skin"), die es hier absichtlich nicht gibt, und Welten, die auf diesem Server
 * nicht existieren.
 */
final class Importer {

    private Importer() {}

    static void run(WatchPostPlugin plugin, CommandSender to) {
        File dir = new File(plugin.getDataFolder().getParentFile(), "CCTV");
        if (!dir.isDirectory()) {
            plugin.msg(to, "import-nothing", "path", dir.getPath());
            return;
        }
        List<String> notes = new ArrayList<>();
        int cameras = importCameras(plugin, new File(dir, "cameras.yml"), notes);
        int groups = importGroups(plugin, new File(dir, "cameragroups.yml"), notes);
        int monitors = importComputers(plugin, new File(dir, "computers.yml"), notes);

        to.sendMessage(Msg.parse(plugin.text("import-done")
                .replace("%cameras%", String.valueOf(cameras))
                .replace("%groups%", String.valueOf(groups))
                .replace("%monitors%", String.valueOf(monitors))));
        for (String n : notes) {
            to.sendMessage(Msg.parse("<yellow>  " + n));
        }
    }

    private static int importCameras(WatchPostPlugin plugin, File file, List<String> notes) {
        if (!file.isFile()) return 0;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        int done = 0;
        int skins = 0;
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            String name = Store.sanitize(key);
            if (!name.equals(key)) {
                notes.add("Kamera '" + key + "' heisst jetzt '" + name
                        + "' -- Sonderzeichen sind in der Datenhaltung nicht sicher.");
            }
            if (plugin.store().camera(name) != null) {
                notes.add("Kamera '" + name + "' war schon vorhanden -- unveraendert gelassen.");
                continue;
            }
            String worldName = s.getString("world");
            if (worldName == null) {
                // Kein Weltfeld heisst: das ist gar kein vollstaendiger Kameraeintrag.
                // Haeufigster Grund ist ein Punkt im Kameranamen -- YAML macht daraus
                // Unterabschnitte, schon in CCTVs eigener Datei. Das ehrlich benennen,
                // statt "Welt 'null' existiert nicht" zu melden.
                notes.add("Eintrag '" + key + "' hat keine Weltangabe und ist keine Kamera"
                        + (s.getKeys(false).isEmpty() ? "" : " (enthaelt Unterabschnitte: "
                        + String.join(", ", s.getKeys(false)) + " -- vermutlich ein Punkt im Namen)")
                        + " -- uebersprungen.");
                continue;
            }
            World w = Bukkit.getWorld(worldName);
            if (w == null) {
                notes.add("Kamera '" + name + "': Welt '" + worldName + "' existiert hier nicht -- uebersprungen.");
                continue;
            }
            if (s.getString("skin") != null) skins++;
            plugin.store().addCamera(new Camera(name, w.getName(), w.getUID(),
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch"),
                    uuid(s.getString("owner")), s.getBoolean("enabled", true)));
            done++;
        }
        if (skins > 0) {
            notes.add(skins + " Kamera(s) hatten einen eigenen Kopf (\"skin\"). WatchPost setzt keine "
                    + "Spielerkoepfe -- genau daran scheitert CCTV auf aktuellen Versionen.");
        }
        return done;
    }

    private static int importGroups(WatchPostPlugin plugin, File file, List<String> notes) {
        if (!file.isFile()) return 0;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        int done = 0;
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            String name = Store.sanitize(key);
            if (plugin.store().group(name) != null) {
                notes.add("Gruppe '" + name + "' war schon vorhanden -- unveraendert gelassen.");
                continue;
            }
            Store.Group g = new Store.Group(name, uuid(s.getString("owner")));
            for (String cam : s.getStringList("cameras")) {
                String camName = Store.sanitize(cam);
                if (plugin.store().camera(camName) != null) g.cameras.add(camName);
                else notes.add("Gruppe '" + name + "': Kamera '" + cam + "' gibt es nicht -- ausgelassen.");
            }
            plugin.store().addGroup(g);
            done++;
        }
        return done;
    }

    private static int importComputers(WatchPostPlugin plugin, File file, List<String> notes) {
        if (!file.isFile()) return 0;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        int done = 0;
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            String worldName = s.getString("world");
            World w = worldName == null ? null : Bukkit.getWorld(worldName);
            if (w == null) {
                notes.add("Computer '" + key + "': Welt '" + worldName + "' existiert hier nicht -- uebersprungen.");
                continue;
            }
            // CCTV legt die Gruppe je nach Version unter "group" oder "cameras" ab.
            String group = s.getString("group");
            if (group == null) {
                Object raw = s.get("cameras");
                if (raw instanceof String str) group = str;
            }
            if (group != null) group = Store.sanitize(group);
            if (group != null && plugin.store().group(group) == null) {
                notes.add("Computer '" + key + "': Gruppe '" + group + "' gibt es nicht -- Monitor bleibt ohne Gruppe.");
                group = null;
            }
            Store.Monitor m = new Store.Monitor(w.getName(), w.getUID(),
                    (int) Math.floor(s.getDouble("x")), (int) Math.floor(s.getDouble("y")),
                    (int) Math.floor(s.getDouble("z")), group, s.getBoolean("public", true));
            for (String p : s.getStringList("allowedPlayers")) {
                UUID id = uuid(p);
                if (id != null) m.allowed.add(id);
            }
            plugin.store().addMonitor(m);
            done++;
        }
        return done;
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
