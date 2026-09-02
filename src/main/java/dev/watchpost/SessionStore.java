package dev.watchpost;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** sessions.yml -- die Rueckfahrkarte, die einen Serverabsturz ueberlebt. */
final class SessionStore {

    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();

    SessionStore(Plugin plugin) {
        this.plugin = plugin;
    }

    Map<UUID, Session> all() {
        return sessions;
    }

    Session get(UUID id) {
        return sessions.get(id);
    }

    void put(Session s) {
        sessions.put(s.player, s);
        save();
    }

    Session remove(UUID id) {
        Session s = sessions.remove(id);
        if (s != null) save();
        return s;
    }

    void load() {
        sessions.clear();
        File f = new File(plugin.getDataFolder(), "sessions.yml");
        if (!f.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            GameMode gm;
            try {
                gm = GameMode.valueOf(s.getString("gamemode", "SURVIVAL"));
            } catch (IllegalArgumentException e) {
                gm = GameMode.SURVIVAL;
            }
            UUID wid = null;
            String rawWid = s.getString("world-id");
            if (rawWid != null) {
                try {
                    wid = UUID.fromString(rawWid);
                } catch (IllegalArgumentException ignored) {
                }
            }
            sessions.put(id, new Session(id, s.getString("world"), wid,
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch"),
                    gm, s.getBoolean("allow-flight"), s.getBoolean("flying"),
                    s.getBoolean("night-vision-added"),
                    s.getString("camera"), s.getString("group"),
                    s.getLong("started")));
        }
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Session s : sessions.values()) {
            ConfigurationSection c = y.createSection(s.player.toString());
            c.set("world", s.worldName);
            c.set("world-id", s.worldId == null ? null : s.worldId.toString());
            c.set("x", s.x);
            c.set("y", s.y);
            c.set("z", s.z);
            c.set("yaw", (double) s.yaw);
            c.set("pitch", (double) s.pitch);
            c.set("gamemode", s.gameMode.name());
            c.set("allow-flight", s.allowFlight);
            c.set("flying", s.flying);
            c.set("night-vision-added", s.nightVisionAdded);
            c.set("camera", s.camera);
            c.set("group", s.group);
            c.set("started", s.startedMs);
        }
        try {
            File dir = plugin.getDataFolder();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                plugin.getLogger().warning("Datenordner nicht anlegbar: " + dir);
            }
            y.save(new File(dir, "sessions.yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte sessions.yml nicht schreiben", e);
        }
    }
}
