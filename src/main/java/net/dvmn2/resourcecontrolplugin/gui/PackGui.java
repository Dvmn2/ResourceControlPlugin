package net.dvmn2.resourcecontrolplugin.gui;

import net.dvmn2.resourcecontrolplugin.Lang;
import net.dvmn2.resourcecontrolplugin.PackInfo;
import net.dvmn2.resourcecontrolplugin.ResourceControlPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * GUI-обзор паков игрока (сундук 54 слота, до 45 паков).
 * Зелёный блок — выдан индивидуально, голубой — выдан группой, серый — не выдан.
 * Клик по блоку выдаёт/снимает пак.
 */
public final class PackGui {

    private static final int MAX_PACKS = 45;

    private PackGui() {
    }

    public static void open(ResourceControlPlugin plugin, Player admin, Player target) {
        List<String> names = new ArrayList<>(plugin.getLibrary().names());
        boolean truncated = names.size() > MAX_PACKS;
        if (truncated) {
            names = new ArrayList<>(names.subList(0, MAX_PACKS));
        }
        PackGuiHolder holder = new PackGuiHolder(target.getUniqueId(), names);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text(Lang.get(Lang.Key.GUI_TITLE, admin, target.getName())));
        holder.setInventory(inventory);
        render(plugin, admin, holder, target);
        admin.openInventory(inventory);
        if (truncated) {
            admin.sendMessage(Component.text(Lang.get(Lang.Key.GUI_TRUNCATED, admin), NamedTextColor.YELLOW));
        }
    }

    public static void render(ResourceControlPlugin plugin, Player viewer, PackGuiHolder holder, Player target) {
        Inventory inventory = holder.getInventory();
        UUID uuid = holder.getTargetUuid();
        Set<String> individual = plugin.getAssignments().get(uuid);
        Set<String> groups = plugin.getSyncManager().groupPackNames(target);

        for (int slot = 0; slot < 54; slot++) {
            String name = holder.packAt(slot);
            PackInfo info = name == null ? null : plugin.getLibrary().get(name);
            if (info == null) {
                inventory.setItem(slot, null);
                continue;
            }
            boolean isIndividual = individual.contains(name);
            boolean isGroup = groups.contains(name);
            Material material = isIndividual ? Material.LIME_CONCRETE
                    : isGroup ? Material.LIGHT_BLUE_CONCRETE : Material.GRAY_CONCRETE;
            String state = Lang.get(isIndividual ? Lang.Key.LORE_STATE_INDIVIDUAL
                    : isGroup ? Lang.Key.LORE_STATE_GROUP : Lang.Key.LORE_STATE_OFF, viewer);

            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(line(name, NamedTextColor.WHITE));
            List<Component> lore = new ArrayList<>();
            lore.add(line(state, isIndividual ? NamedTextColor.GREEN : isGroup ? NamedTextColor.AQUA : NamedTextColor.GRAY));
            lore.add(line(Lang.get(Lang.Key.LORE_PRIORITY, viewer, info.priority()), NamedTextColor.GRAY));
            lore.add(line(Lang.get(Lang.Key.LORE_PERSISTENT, viewer, Lang.yesNo(info.persistent(), viewer)), NamedTextColor.GRAY));
            lore.add(line(Lang.get(Lang.Key.LORE_SIZE, viewer, info.sizeMegabytes()), NamedTextColor.GRAY));
            lore.add(line(Lang.get(Lang.Key.LORE_SHADERS, viewer, Lang.yesNo(info.shaders(), viewer)), NamedTextColor.GRAY));
            lore.add(line(Lang.get(Lang.Key.LORE_CLICK, viewer), NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            stack.setItemMeta(meta);
            inventory.setItem(slot, stack);
        }
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
