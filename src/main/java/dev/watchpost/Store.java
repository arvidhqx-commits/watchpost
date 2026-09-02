package dev.watchpost;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Kameras, Gruppen und Monitore auf der Platte.
 *
 * Bewusst drei getrennte Dateien wie beim Vorgaenger, damit ein Umstieg
 * nachvollziehbar bleibt. Geschrieben wird nach jeder Aenderung sofort --
 * ein Serverabsturz darf keine Kamera kosten.
 */
final class Store {

    private final Plugin plugin;
    private final Map<String, Camera> cameras = new LinkedHashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<String, Monitor> monitors = new LinkedHashMap<>();

    Store(Plugin plugin) {
        this.plugin = plugin;
    }

    static final class Group {
        final String name;
        final UUID owner;
        final List<String> cameras = new ArrayList<>();

        Group(String name, UUID owner) {
            this.name = name;
            this.owner = owner;
        }

        String key() {
            return name.toLowerCase(Locale.ROOT);
        }
    }

    static final class Monitor {
        final String worldName;
        final UUID worldId;
        final int x, y, z;
        String group;
        final Set<UUID> allowed = new LinkedHashSet<>();
        boolean open;

        Monitor(String worldName, UUID worldId, int x, int y, int z, String group, boolean open) {
            this.worldName = worldName;
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.group = group;
            this.open = open;
        }

        /**
         * Der Schluessel MUSS derselbe sein, den ein angeklickter Block erzeugt.
         * Frueher stand hier "worldId, sonst worldName" -- bei einem Datensatz ohne
         * world-id (etwa aus der Uebernahme) kam ein namensbasierter Schluessel
         * heraus, und der Monitor liess sich nie wieder oeffnen. Jetzt wird die
         * geladene Welt befragt; nur wenn es sie nicht gibt, bleibt der Name.
         */
        String key() {
            org.bukkit.World w = worldId == null ? null : org.bukkit.Bukkit.getWorld(worldId);
            if (w == null && worldName != null) w = org.bukkit.Bukkit.getWorld(worldName);
            Object id = w != null ? w.getUID() : (worldId != null ? worldId : worldName);
            return id + ":" + x + ":" + y + ":" + z;
        }

        static String keyOf(org.bukkit.block.Block b) {
            return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        }
    }

    // ---------------------------------------------------------------- laden

    void load() {
        cameras.clear();
        groups.clear();
        monitors.clear();
        loadCameras(read("cameras.yml"));
        loadGroups(read("groups.yml"));
        loadMonitors(read("monitors.yml"));
    }

    private YamlConfiguration read(String file) {
        File f = new File(plugin.getDataFolder(), file);
        return f.isFile() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
    }

    private void loadCameras(YamlConfiguration y) {
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            Camera c = new Camera(
                    s.getString("name", key),
                    s.getString("world"),
                    uuid(s.getString("world-id")),
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch"),
                    uuid(s.getString("owner")),
                    s.getBoolean("enabled", true));
            cameras.put(c.key(), c);
        }
    }

    private void loadGroups(YamlConfiguration y) {
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            Group g = new Group(s.getString("name", key), uuid(s.getString("owner")));
            g.cameras.addAll(s.getStringList("cameras"));
            groups.put(g.key(), g);
        }
    }

    private void loadMonitors(YamlConfiguration y) {
        for (String key : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(key);
            if (s == null) continue;
            Monitor m = new Monitor(s.getString("world"), uuid(s.getString("world-id")),
                    s.getInt("x"), s.getInt("y"), s.getInt("z"),
                    s.getString("group"), s.getBoolean("open", false));
            for (String u : s.getStringList("allowed")) {
                UUID id = uuid(u);
                if (id != null) m.allowed.add(id);
            }
            monitors.put(m.key(), m);
        }
    }

    private static UUID uuid(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // -------------------------------------------------------------- sichern

    void saveCameras() {
        YamlConfiguration y = new YamlConfiguration();
        for (Camera c : cameras.values()) {
            ConfigurationSection s = y.createSection(c.key());
            s.set("name", c.name);
            s.set("world", c.worldName);
            s.set("world-id", c.worldId == null ? null : c.worldId.toString());
            s.set("x", c.x);
            s.set("y", c.y);
            s.set("z", c.z);
            s.set("yaw", (double) c.yaw);
            s.set("pitch", (double) c.pitch);
            s.set("owner", c.owner == null ? null : c.owner.toString());
            s.set("enabled", c.enabled);
        }
        write(y, "cameras.yml");
    }

    void saveGroups() {
        YamlConfiguration y = new YamlConfiguration();
        for (Group g : groups.values()) {
            ConfigurationSection s = y.createSection(g.key());
            s.set("name", g.name);
            s.set("owner", g.owner == null ? null : g.owner.toString());
            s.set("cameras", new ArrayList<>(g.cameras));
        }
        write(y, "groups.yml");
    }

    void saveMonitors() {
        YamlConfiguration y = new YamlConfiguration();
        for (Monitor m : monitors.values()) {
            ConfigurationSection s = y.createSection(m.key().replace(':', '_'));
            s.set("world", m.worldName);
            s.set("world-id", m.worldId == null ? null : m.worldId.toString());
            s.set("x", m.x);
            s.set("y", m.y);
            s.set("z", m.z);
            s.set("group", m.group);
            s.set("open", m.open);
            List<String> allowed = new ArrayList<>();
            for (UUID u : m.allowed) allowed.add(u.toString());
            s.set("allowed", allowed);
        }
        write(y, "monitors.yml");
    }

    private void write(YamlConfiguration y, String file) {
        try {
            File dir = plugin.getDataFolder();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                plugin.getLogger().warning("Datenordner nicht anlegbar: " + dir);
            }
            y.save(new File(dir, file));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte " + file + " nicht schreiben", e);
        }
    }

    // -------------------------------------------------------------- zugriff

    /**
     * Erlaubte Namen. Ein Punkt im Namen ist in YAML ein Pfadtrenner: "hof.tor"
     * wuerde beim Speichern zu einem Unterabschnitt und beim Laden als "hof"
     * zurueckkommen -- die Kamera waere weg. Ausserdem koennte ein Name mit
     * spitzen Klammern Farbschnipsel in Menuetitel schmuggeln.
     */
    static boolean validName(String name) {
        if (name == null || name.isEmpty() || name.length() > 32) return false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-';
            if (!ok) return false;
        }
        return true;
    }

    /** Fuer die Uebernahme: macht aus einem fremden Namen einen zulaessigen. */
    static String sanitize(String name) {
        if (name == null) return "camera";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length() && sb.length() < 32; i++) {
            char ch = name.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-';
            sb.append(ok ? ch : '_');
        }
        return sb.length() == 0 ? "camera" : sb.toString();
    }

    Camera camera(String name) {
        return name == null ? null : cameras.get(name.toLowerCase(Locale.ROOT));
    }

    Collection<Camera> cameras() {
        return cameras.values();
    }

    void addCamera(Camera c) {
        cameras.put(c.key(), c);
        saveCameras();
    }

    boolean removeCamera(String name) {
        Camera c = camera(name);
        if (c == null) return false;
        cameras.remove(c.key());
        boolean touched = false;
        for (Group g : groups.values()) {
            touched |= g.cameras.removeIf(n -> n.equalsIgnoreCase(c.name));
        }
        saveCameras();
        if (touched) saveGroups();
        return true;
    }

    Group group(String name) {
        return name == null ? null : groups.get(name.toLowerCase(Locale.ROOT));
    }

    Collection<Group> groups() {
        return groups.values();
    }

    void addGroup(Group g) {
        groups.put(g.key(), g);
        saveGroups();
    }

    boolean removeGroup(String name) {
        Group g = group(name);
        if (g == null) return false;
        groups.remove(g.key());
        boolean touched = false;
        for (Monitor m : monitors.values()) {
            if (m.group != null && m.group.equalsIgnoreCase(g.name)) {
                m.group = null;
                touched = true;
            }
        }
        saveGroups();
        if (touched) saveMonitors();
        return true;
    }

    /** Die Kameras einer Gruppe in Reihenfolge, ohne geloeschte oder abgeschaltete. */
    List<Camera> camerasOf(Group g) {
        List<Camera> out = new ArrayList<>();
        if (g == null) return out;
        for (String n : g.cameras) {
            Camera c = camera(n);
            if (c != null && c.enabled) out.add(c);
        }
        return out;
    }

    Monitor monitor(org.bukkit.block.Block b) {
        return monitors.get(Monitor.keyOf(b));
    }

    Collection<Monitor> monitors() {
        return monitors.values();
    }

    void addMonitor(Monitor m) {
        monitors.put(m.key(), m);
        saveMonitors();
    }

    boolean removeMonitor(org.bukkit.block.Block b) {
        if (monitors.remove(Monitor.keyOf(b)) == null) return false;
        saveMonitors();
        return true;
    }

    static Location loc(Camera c) {
        return c.location();
    }
}
