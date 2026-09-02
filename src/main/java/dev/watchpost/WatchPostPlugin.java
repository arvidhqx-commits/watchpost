package dev.watchpost;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class WatchPostPlugin extends org.bukkit.plugin.java.JavaPlugin implements TabCompleter {

    private Store store;
    private SessionStore sessions;
    private Viewing viewing;
    private BukkitTask watchdog;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new Store(this);
        store.load();
        sessions = new SessionStore(this);
        sessions.load();
        viewing = new Viewing(this, sessions);

        // Erst die Altlasten aufloesen, dann Betrieb aufnehmen: wer den letzten
        // Neustart im Zuschauermodus erlebt hat, ist vor dem ersten Tick zurueck.
        int leftover = sessions.all().size();
        if (leftover > 0) {
            getLogger().info(leftover + " offene Kamerasitzung(en) aus sessions.yml -- "
                    + "Online-Spieler werden sofort zurueckgesetzt, der Rest beim Einloggen.");
            viewing.restoreOnline();
        }

        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        watchdog = getServer().getScheduler().runTaskTimer(this, viewing::tick, 20L, 10L);
        getLogger().info("WatchPost " + getPluginMeta().getVersion() + " aktiv: "
                + store.cameras().size() + " Kameras, " + store.groups().size() + " Gruppen, "
                + store.monitors().size() + " Monitore.");
    }

    @Override
    public void onDisable() {
        if (watchdog != null) watchdog.cancel();
        if (viewing != null) viewing.endAll(Viewing.Reason.SHUTDOWN);
    }

    Store store() {
        return store;
    }

    Viewing viewing() {
        return viewing;
    }

    SessionStore sessions() {
        return sessions;
    }

    List<String> commandNames() {
        return Arrays.asList("watchpost", "cams", "cctv");
    }

    // ------------------------------------------------------------ Nachrichten

    String text(String key) {
        String raw = getConfig().getString("messages." + key);
        return raw == null ? "<gray>[WatchPost] " + key : raw;
    }

    void msg(CommandSender to, String key, String placeholder, String value) {
        String raw = text(key);
        if (placeholder != null) raw = raw.replace("%" + placeholder + "%", value == null ? "" : value);
        to.sendMessage(Msg.parse(raw));
    }

    // -------------------------------------------------------------- Befehle

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload":
                if (!sender.hasPermission("watchpost.admin")) return deny(sender);
                reloadConfig();
                store.load();
                msg(sender, "reloaded", null, null);
                return true;
            case "import":
                if (!sender.hasPermission("watchpost.admin")) return deny(sender);
                Importer.run(this, sender);
                return true;
            case "camera":
                return camera(sender, args);
            case "group":
                return group(sender, args);
            case "monitor":
                return monitor(sender, args);
            case "view":
                return view(sender, args);
            case "leave":
                if (!(sender instanceof Player p)) return notPlayer(sender);
                if (!viewing.isViewing(p.getUniqueId())) {
                    msg(sender, "not-viewing", null, null);
                    return true;
                }
                viewing.end(p, Viewing.Reason.COMMAND, true);
                return true;
            case "next":
            case "prev":
                if (!(sender instanceof Player pl)) return notPlayer(sender);
                String next = viewing.cycle(pl, sub.equals("next") ? 1 : -1);
                if (next == null) msg(sender, "no-cycle", null, null);
                else msg(sender, "switched", "camera", next);
                return true;
            default:
                help(sender);
                return true;
        }
    }

    private boolean camera(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "usage-camera", null, null);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            if (!sender.hasPermission("watchpost.camera.list")) return deny(sender);
            msg(sender, "camera-list-head", "count", String.valueOf(store.cameras().size()));
            for (Camera c : store.cameras()) {
                sender.sendMessage(Msg.parse(text("camera-list-line")
                        .replace("%camera%", c.name)
                        .replace("%world%", String.valueOf(c.worldName))
                        .replace("%x%", String.valueOf(Math.round(c.x)))
                        .replace("%y%", String.valueOf(Math.round(c.y)))
                        .replace("%z%", String.valueOf(Math.round(c.z)))
                        .replace("%state%", c.enabled ? text("state-on") : text("state-off"))));
            }
            return true;
        }
        if (args.length < 3) {
            msg(sender, "usage-camera", null, null);
            return true;
        }
        String name = args[2];
        switch (action) {
            case "create": {
                if (!sender.hasPermission("watchpost.camera.create")) return deny(sender);
                if (!(sender instanceof Player p)) return notPlayer(sender);
                if (!Store.validName(name)) {
                    msg(sender, "bad-name", "name", name);
                    return true;
                }
                if (store.camera(name) != null) {
                    msg(sender, "camera-exists", "camera", name);
                    return true;
                }
                Location at = p.getEyeLocation();
                store.addCamera(Camera.at(name, at, p.getUniqueId()));
                msg(sender, "camera-created", "camera", name);
                // Eine Kamera im Stein zeigt schwarz. Das faellt sonst erst dem
                // ersten Zuschauer auf -- lieber sofort sagen.
                if (at.getBlock().getType().isOccluding()) {
                    msg(sender, "camera-in-block", "camera", name);
                }
                return true;
            }
            case "delete": {
                if (!sender.hasPermission("watchpost.camera.delete")) return deny(sender);
                if (!store.removeCamera(name)) {
                    msg(sender, "no-such-camera", "camera", name);
                    return true;
                }
                msg(sender, "camera-deleted", "camera", name);
                return true;
            }
            case "enable":
            case "disable": {
                if (!sender.hasPermission("watchpost.camera.create")) return deny(sender);
                Camera c = store.camera(name);
                if (c == null) {
                    msg(sender, "no-such-camera", "camera", name);
                    return true;
                }
                c.enabled = action.equals("enable");
                store.saveCameras();
                msg(sender, c.enabled ? "camera-enabled" : "camera-disabled-ok", "camera", name);
                return true;
            }
            default:
                msg(sender, "usage-camera", null, null);
                return true;
        }
    }

    private boolean group(CommandSender sender, String[] args) {
        if (!sender.hasPermission("watchpost.group.manage")) return deny(sender);
        if (args.length < 2) {
            msg(sender, "usage-group", null, null);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            msg(sender, "group-list-head", "count", String.valueOf(store.groups().size()));
            for (Store.Group g : store.groups()) {
                sender.sendMessage(Msg.parse(text("group-list-line")
                        .replace("%group%", g.name)
                        .replace("%count%", String.valueOf(g.cameras.size()))));
            }
            return true;
        }
        if (args.length < 3) {
            msg(sender, "usage-group", null, null);
            return true;
        }
        String name = args[2];
        switch (action) {
            case "create": {
                if (!Store.validName(name)) {
                    msg(sender, "bad-name", "name", name);
                    return true;
                }
                if (store.group(name) != null) {
                    msg(sender, "group-exists", "group", name);
                    return true;
                }
                store.addGroup(new Store.Group(name,
                        sender instanceof Player p ? p.getUniqueId() : null));
                msg(sender, "group-created", "group", name);
                return true;
            }
            case "delete": {
                if (!store.removeGroup(name)) {
                    msg(sender, "no-such-group", "group", name);
                    return true;
                }
                msg(sender, "group-deleted", "group", name);
                return true;
            }
            case "add":
            case "remove": {
                if (args.length < 4) {
                    msg(sender, "usage-group", null, null);
                    return true;
                }
                Store.Group g = store.group(name);
                if (g == null) {
                    msg(sender, "no-such-group", "group", name);
                    return true;
                }
                Camera c = store.camera(args[3]);
                if (c == null) {
                    msg(sender, "no-such-camera", "camera", args[3]);
                    return true;
                }
                if (action.equals("add")) {
                    if (g.cameras.stream().noneMatch(n -> n.equalsIgnoreCase(c.name))) {
                        g.cameras.add(c.name);
                    }
                } else {
                    g.cameras.removeIf(n -> n.equalsIgnoreCase(c.name));
                }
                store.saveGroups();
                sender.sendMessage(Msg.parse(text(action.equals("add") ? "group-added" : "group-removed")
                        .replace("%camera%", c.name).replace("%group%", g.name)));
                return true;
            }
            default:
                msg(sender, "usage-group", null, null);
                return true;
        }
    }

    private boolean monitor(CommandSender sender, String[] args) {
        if (!sender.hasPermission("watchpost.monitor.create")) return deny(sender);
        if (!(sender instanceof Player p)) return notPlayer(sender);
        if (args.length < 2) {
            msg(sender, "usage-monitor", null, null);
            return true;
        }
        Block target = p.getTargetBlockExact(6);
        if (target == null || target.getType().isAir()) {
            msg(sender, "monitor-no-block", null, null);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("delete")) {
            if (!store.removeMonitor(target)) {
                msg(sender, "monitor-none-here", null, null);
                return true;
            }
            msg(sender, "monitor-removed", null, null);
            return true;
        }
        if (!action.equals("create") || args.length < 3) {
            msg(sender, "usage-monitor", null, null);
            return true;
        }
        Store.Group g = store.group(args[2]);
        if (g == null) {
            msg(sender, "no-such-group", "group", args[2]);
            return true;
        }
        Store.Monitor m = new Store.Monitor(target.getWorld().getName(), target.getWorld().getUID(),
                target.getX(), target.getY(), target.getZ(), g.name,
                getConfig().getBoolean("monitor.public-by-default", true));
        store.addMonitor(m);
        msg(sender, "monitor-created", "group", g.name);
        return true;
    }

    private boolean view(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return notPlayer(sender);
        if (!p.hasPermission("watchpost.view")) return deny(sender);
        if (args.length < 2) {
            msg(sender, "usage-view", null, null);
            return true;
        }
        Camera c = store.camera(args[1]);
        String error = viewing.begin(p, c, null);
        if (error != null) msg(sender, error, "camera", args[1]);
        else msg(sender, "viewing", "camera", c.name);
        return true;
    }

    private void help(CommandSender sender) {
        for (String line : getConfig().getStringList("messages.help")) {
            sender.sendMessage(Msg.parse(line));
        }
    }

    private boolean deny(CommandSender sender) {
        msg(sender, "no-permission", null, null);
        return true;
    }

    private boolean notPlayer(CommandSender sender) {
        sender.sendMessage(Component.text("Nur im Spiel nutzbar."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(Arrays.asList("camera", "group", "monitor", "view", "leave", "next", "prev",
                    "import", "reload"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "camera" -> out.addAll(Arrays.asList("create", "delete", "list", "enable", "disable"));
                case "group" -> out.addAll(Arrays.asList("create", "delete", "add", "remove", "list"));
                case "monitor" -> out.addAll(Arrays.asList("create", "delete"));
                case "view" -> {
                    for (Camera c : store.cameras()) out.add(c.name);
                }
                default -> {
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("camera") && !args[1].equalsIgnoreCase("create")) {
                for (Camera c : store.cameras()) out.add(c.name);
            } else if (args[0].equalsIgnoreCase("group") && !args[1].equalsIgnoreCase("create")) {
                for (Store.Group g : store.groups()) out.add(g.name);
            } else if (args[0].equalsIgnoreCase("monitor")) {
                for (Store.Group g : store.groups()) out.add(g.name);
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("group")) {
            for (Camera c : store.cameras()) out.add(c.name);
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
