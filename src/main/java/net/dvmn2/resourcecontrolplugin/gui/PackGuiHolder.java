package net.dvmn2.resourcecontrolplugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

/**
 * Маркер-держатель GUI выдачи паков игроку (см. {@link PackGui}).
 */
public final class PackGuiHolder implements InventoryHolder {

    private final UUID targetUuid;
    private final List<String> packNames;
    private Inventory inventory;

    public PackGuiHolder(UUID targetUuid, List<String> packNames) {
        this.targetUuid = targetUuid;
        this.packNames = packNames;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    /** Имя пака в слоте i (слот == индекс) либо null. */
    public String packAt(int slot) {
        return slot >= 0 && slot < packNames.size() ? packNames.get(slot) : null;
    }

    public List<String> getPackNames() {
        return packNames;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
