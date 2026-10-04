package net.dvmn2.resourcecontrolplugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Индивидуальные назначения паков игрокам: plugins/ResourceControlPlugin/players.yml
 * <pre>
 * &lt;uuid&gt;:
 *   packs: [panorama-winter, hud-minimal]
 * </pre>
 * Плюс временные «превью» в памяти (не сохраняются).
 */
public final class AssignmentManager {

    private final ResourceControlPlugin plugin;
    private final File file;
    private final Map<UUID, Set<String>> assigned = new HashMap<>();
    private final Map<UUID, Set<String>> previews = new HashMap<>();

    public AssignmentManager(ResourceControlPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        assigned.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Некорректный UUID в players.yml: " + key);
                continue;
            }
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Set<String> names = new LinkedHashSet<>(section.getStringList("packs"));
            if (!names.isEmpty()) {
                assigned.put(uuid, names);
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> e : assigned.entrySet()) {
            if (!e.getValue().isEmpty()) {
                cfg.set(e.getKey() + ".packs", new java.util.ArrayList<>(e.getValue()));
            }
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить players.yml", ex);
        }
    }

    public Set<String> get(UUID uuid) {
        return Set.copyOf(assigned.getOrDefault(uuid, Set.of()));
    }

    public boolean add(UUID uuid, String pack) {
        boolean changed = assigned.computeIfAbsent(uuid, k -> new LinkedHashSet<>()).add(pack);
        if (changed) {
            save();
        }
        return changed;
    }

    public boolean remove(UUID uuid, String pack) {
        Set<String> set = assigned.get(uuid);
        boolean changed = set != null && set.remove(pack);
        if (changed) {
            if (set.isEmpty()) {
                assigned.remove(uuid);
            }
            save();
        }
        return changed;
    }

    public void clear(UUID uuid) {
        boolean had = assigned.remove(uuid) != null;
        previews.remove(uuid);
        if (had) {
            save();
        }
    }

    /** Убирает пак у всех (при удалении пака из библиотеки). */
    public void removePackEverywhere(String pack) {
        boolean changed = false;
        for (Set<String> set : assigned.values()) {
            changed |= set.remove(pack);
        }
        assigned.values().removeIf(Set::isEmpty);
        previews.values().forEach(set -> set.remove(pack));
        if (changed) {
            save();
        }
    }

    // -------- превью --------

    public Set<String> previews(UUID uuid) {
        return Set.copyOf(previews.getOrDefault(uuid, Set.of()));
    }

    public void addPreview(UUID uuid, String pack) {
        previews.computeIfAbsent(uuid, k -> new LinkedHashSet<>()).add(pack);
    }

    public void removePreview(UUID uuid, String pack) {
        Set<String> set = previews.get(uuid);
        if (set != null) {
            set.remove(pack);
            if (set.isEmpty()) {
                previews.remove(uuid);
            }
        }
    }

    public void forget(UUID uuid) {
        previews.remove(uuid);
    }
}
