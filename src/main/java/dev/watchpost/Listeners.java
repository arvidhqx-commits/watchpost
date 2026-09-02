package dev.watchpost;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Locale;

final class Listeners implements Listener {

    private final WatchPostPlugin plugin;

    Listeners(WatchPostPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------- Blick festhalten

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Session s = plugin.viewing().session(e.getPlayer().getUniqueId());
        if (s == null) return;
        Location to = e.getTo();
        Location from = e.getFrom();
        if (to == null) return;
        // Drehen ja, bewegen nein.
        if (to.getX() == from.getX() && to.getY() == from.getY() && to.getZ() == from.getZ()) return;
        Location fixed = from.clone();
        fixed.setYaw(to.getYaw());
        fixed.setPitch(to.getPitch());
        e.setTo(fixed);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        if (!plugin.getConfig().getBoolean("view.exit-on-sneak", true)) return;
        if (plugin.viewing().isViewing(e.getPlayer().getUniqueId())) {
            plugin.viewing().end(e.getPlayer(), Viewing.Reason.SNEAK, true);
        }
    }

    /** Hotbar-Rad: naechste bzw. vorige Kamera derselben Gruppe. */
    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        if (!plugin.getConfig().getBoolean("view.switch-with-hotbar", true)) return;
        Session s = plugin.viewing().session(e.getPlayer().getUniqueId());
        if (s == null || s.group == null) return;
        String name = plugin.viewing().cycle(e.getPlayer(),
                Viewing.hotbarDirection(e.getPreviousSlot(), e.getNewSlot()));
        if (name != null) plugin.msg(e.getPlayer(), "switched", "camera", name);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("view.block-commands", true)) return;
        if (!plugin.viewing().isViewing(e.getPlayer().getUniqueId())) return;
        String cmd = e.getMessage().toLowerCase(Locale.ROOT);
        for (String allowed : plugin.commandNames()) {
            if (cmd.equals("/" + allowed) || cmd.startsWith("/" + allowed + " ")) return;
        }
        e.setCancelled(true);
        plugin.msg(e.getPlayer(), "commands-blocked", null, null);
    }

    // -------------------------------------------------- Rueckweg sichern

    // Beim Verlassen wird ABSICHTLICH nicht zurueckgesetzt: ein Teleport im
    // Quit-Event wird nicht zuverlaessig mitgespeichert. Der Eintrag bleibt in
    // sessions.yml stehen und greift beim naechsten Einloggen -- derselbe Weg
    // wie nach einem Serverabsturz, also nur EIN Pfad, der stimmen muss.

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.viewing().isViewing(p.getUniqueId())) {
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.viewing().end(p, Viewing.Reason.RESTORED_AFTER_RESTART, true));
        }
    }

    // -------------------------------------------------- Monitore

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Store.Monitor m = plugin.store().monitor(e.getClickedBlock());
        if (m == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission("watchpost.monitor.use")) {
            plugin.msg(p, "no-permission", null, null);
            return;
        }
        if (!m.open && !m.allowed.contains(p.getUniqueId()) && !p.hasPermission("watchpost.admin")) {
            plugin.msg(p, "monitor-locked", null, null);
            return;
        }
        Store.Group g = plugin.store().group(m.group);
        if (g == null) {
            plugin.msg(p, "monitor-no-group", null, null);
            return;
        }
        Gui.open(p, plugin, g);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (plugin.store().monitor(b) == null) return;
        if (!e.getPlayer().hasPermission("watchpost.monitor.create")) {
            e.setCancelled(true);
            plugin.msg(e.getPlayer(), "monitor-protected", null, null);
            return;
        }
        plugin.store().removeMonitor(b);
        plugin.msg(e.getPlayer(), "monitor-removed", null, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Gui gui)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= gui.cameras().size()) return;
        String cameraName = gui.cameras().get(slot);
        p.closeInventory();
        Camera cam = plugin.store().camera(cameraName);
        String error = plugin.viewing().begin(p, cam, gui.group());
        if (error != null) {
            plugin.msg(p, error, "camera", cameraName);
        } else {
            plugin.msg(p, "viewing", "camera", cameraName);
        }
    }
}
