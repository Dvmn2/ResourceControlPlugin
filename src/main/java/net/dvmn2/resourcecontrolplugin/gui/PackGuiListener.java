package net.dvmn2.resourcecontrolplugin.gui;

import net.dvmn2.resourcecontrolplugin.ResourceControlPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class PackGuiListener implements Listener {

    private final ResourceControlPlugin plugin;

    public PackGuiListener(ResourceControlPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PackGuiHolder holder)) {
            return;
        }
        // Внутри окна ничего нельзя двигать, в том числе шифт-кликом из своего инвентаря.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        String pack = holder.packAt(event.getRawSlot());
        if (pack == null) {
            return;
        }
        Player target = Bukkit.getPlayer(holder.getTargetUuid());
        if (target == null) {
            admin.closeInventory();
            return;
        }
        if (plugin.getAssignments().get(target.getUniqueId()).contains(pack)) {
            plugin.getAssignments().remove(target.getUniqueId(), pack);
        } else {
            plugin.getAssignments().add(target.getUniqueId(), pack);
        }
        plugin.getSyncManager().sync(target, false);
        PackGui.render(plugin, admin, holder, target);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PackGuiHolder) {
            event.setCancelled(true);
        }
    }
}
