package net.dvmn2.resourcecontrolplugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Библиотека паков: plugins/ResourceControlPlugin/packs.yml.
 * <pre>
 * packs:
 *   panorama-winter:
 *     url: https://github.com/.../releases/download/v1/panorama-winter.zip
 *     sha1: ...
 *     size: 1234567
 *     priority: 10
 *     persistent: true
 *     shaders: false
 * </pre>
 * Файл можно править руками, затем /resourcecontrol reload. Но sha1/size надёжнее получать
 * командами /resourcecontrol pack add|refresh — они сами скачивают и проверяют пак.
 */
public final class PackLibrary {

    private final ResourceControlPlugin plugin;
    private final File file;
    private final Map<String, PackInfo> packs = new LinkedHashMap<>();

    public PackLibrary(ResourceControlPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "packs.yml");
    }

    public void load() {
        packs.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("packs");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            String url = s.getString("url", "");
            String sha1 = s.getString("sha1", "").toLowerCase();
            if (!PackInfo.isValidName(name)
                    || !url.startsWith("https://") || url.length() > PackInfo.MAX_URL_LENGTH
                    || !sha1.matches("[0-9a-f]{40}")) {
                plugin.getLogger().warning("packs.yml: пак '" + name + "' пропущен (неверное имя, url или sha1).");
                continue;
            }
            packs.put(name, new PackInfo(name, url, sha1, s.getLong("size", 0L), s.getInt("priority", 0),
                    s.getBoolean("persistent", false), s.getBoolean("shaders", false)));
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (PackInfo p : packs.values()) {
            String base = "packs." + p.name() + ".";
            cfg.set(base + "url", p.url());
            cfg.set(base + "sha1", p.sha1());
            cfg.set(base + "size", p.size());
            cfg.set(base + "priority", p.priority());
            cfg.set(base + "persistent", p.persistent());
            cfg.set(base + "shaders", p.shaders());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить packs.yml", ex);
        }
    }

    public PackInfo get(String name) {
        return packs.get(name);
    }

    public void put(PackInfo info) {
        packs.put(info.name(), info);
        save();
    }

    public boolean remove(String name) {
        boolean removed = packs.remove(name) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Collection<PackInfo> all() {
        return List.copyOf(packs.values());
    }

    public List<String> names() {
        List<String> names = new ArrayList<>(packs.keySet());
        names.sort(String::compareTo);
        return names;
    }
}
