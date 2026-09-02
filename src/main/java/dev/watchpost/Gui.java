package dev.watchpost;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Monitor-Oberflaeche.
 *
 * Bewusst OHNE Spielerkoepfe: der Vorgaenger baut seine Menuesymbole ueber
 * com.mojang.authlib und stirbt genau dort auf jeder aktuellen Version.
 * Hier stehen gewoehnliche Gegenstaende, ueber Mats.first nachgeschlagen.
 */
final class Gui implements InventoryHolder {

    private final String group;
    private final List<String> cameras = new ArrayList<>();
    private Inventory inventory;

    private Gui(String group) {
        this.group = group;
    }

    String group() {
        return group;
    }

    List<String> cameras() {
        return cameras;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    static Gui open(org.bukkit.entity.Player p, WatchPostPlugin plugin, Store.Group g) {
        List<Camera> list = plugin.store().camerasOf(g);
        int rows = Math.max(1, Math.min(6, (list.size() + 8) / 9));
        Gui gui = new Gui(g.name);
        String title = plugin.text("gui-title").replace("%group%", g.name);
        Inventory inv = Bukkit.createInventory(gui, rows * 9, Msg.parse(title));
        gui.inventory = inv;
        Material item = Mats.first("SPYGLASS", "ENDER_EYE", "COMPASS", "PAPER");
        Material broken = Mats.first("BARRIER", "GRAY_DYE", "PAPER");
        for (int i = 0; i < list.size() && i < rows * 9; i++) {
            Camera c = list.get(i);
            boolean reachable = c.location() != null;
            ItemStack stack = new ItemStack(reachable ? item : broken);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(Msg.parse(plugin.text("gui-camera").replace("%camera%", c.name)));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Msg.parse("<gray>" + c.worldName + " " + Math.round(c.x) + " "
                        + Math.round(c.y) + " " + Math.round(c.z)));
                if (!reachable) lore.add(Msg.parse(plugin.text("gui-world-missing")));
                meta.lore(lore);
                stack.setItemMeta(meta);
            }
            inv.setItem(i, stack);
            gui.cameras.add(c.name);
        }
        p.openInventory(inv);
        return gui;
    }
}
